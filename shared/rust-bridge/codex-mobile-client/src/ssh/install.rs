//! Install / update / sentinel-check for the remote `codex` binary.
//!
//! Two install paths exist:
//!   - **release tarball** (`install_latest_stable_codex`): macOS + Linux.
//!     Pulls a GitHub release tarball, extracts it under `~/.litter/codex/<tag>/`,
//!     and refreshes the `~/.litter/bin/codex` symlink.
//!   - **npm** (`install_codex_via_npm`): Windows, and POSIX fallback when
//!     no binary asset matches the platform. Installs `@openai/codex@latest`
//!     into `~/.litter/codex/`.
//!
//! `maybe_update_managed_codex` is a best-effort, once-per-day refresh
//! gated by the `~/.litter/codex/.last-update-check` sentinel. Failures
//! are intentionally swallowed — we'd rather keep the user on the
//! existing binary than block their connect on a flaky network.

use tracing::{info, trace, warn};

use super::{
    CODEX_UPDATE_CHECK_INTERVAL_SECS, CodexInstallOutcome, PROFILE_INIT, RemoteCodexBinary,
    RemotePlatform, RemoteShell, SshClient, SshError, append_bridge_info_log,
    fetch_latest_stable_codex_release, parse_install_status_and_path, parse_kv_lines,
    remote_platform_name, remote_shell_name, shell_quote,
};

impl SshClient {
    pub(crate) async fn install_latest_stable_codex(
        &self,
        platform: RemotePlatform,
    ) -> Result<(RemoteCodexBinary, CodexInstallOutcome), SshError> {
        info!(
            "ssh install codex start platform={}",
            remote_platform_name(platform)
        );
        if platform.is_windows() {
            info!(
                "ssh install codex using npm platform={}",
                remote_platform_name(platform)
            );
            return self.install_codex_via_npm(RemoteShell::PowerShell).await;
        }
        let release = fetch_latest_stable_codex_release(platform).await?;
        info!(
            "ssh install codex using release platform={} tag={} asset={}",
            remote_platform_name(platform),
            release.tag_name,
            release.asset_name
        );
        let install_script = crate::ssh_scripts::render(
            crate::ssh_scripts::posix::INSTALL_CODEX_RELEASE,
            &[
                ("TAG", &shell_quote(&release.tag_name)),
                ("ASSET_NAME", &shell_quote(&release.asset_name)),
                ("BINARY_NAME", &shell_quote(&release.binary_name)),
                ("DOWNLOAD_URL", &shell_quote(&release.download_url)),
            ],
        );
        let result = self.exec_posix(&install_script).await?;
        if result.exit_code != 0 {
            return Err(SshError::ExecFailed {
                exit_code: result.exit_code,
                stderr: if result.stderr.trim().is_empty() {
                    "failed to install Codex".to_string()
                } else {
                    result.stderr
                },
            });
        }
        let (status, installed_path) = parse_install_status_and_path(&result.stdout);
        let outcome = match status.as_deref() {
            Some("up-to-date") => CodexInstallOutcome::AlreadyAtLatestTag,
            _ => CodexInstallOutcome::Installed,
        };
        let path = installed_path.unwrap_or_else(|| "$HOME/.litter/bin/codex".to_string());
        info!(
            "ssh install codex completed platform={} path={} outcome={:?}",
            remote_platform_name(platform),
            path,
            outcome
        );
        Ok((RemoteCodexBinary::Codex(path), outcome))
    }

    /// Install Codex via npm into `~/.litter/codex/` (works on Windows and
    /// as a POSIX fallback when no binary release is available).
    pub(crate) async fn install_codex_via_npm(
        &self,
        shell: RemoteShell,
    ) -> Result<(RemoteCodexBinary, CodexInstallOutcome), SshError> {
        info!(
            "ssh npm install codex start shell={}",
            remote_shell_name(shell)
        );
        let script = match shell {
            RemoteShell::PowerShell => {
                crate::ssh_scripts::powershell::INSTALL_CODEX_NPM.to_string()
            }
            RemoteShell::Posix => crate::ssh_scripts::render(
                crate::ssh_scripts::posix::INSTALL_CODEX_NPM,
                &[("PROFILE_INIT", PROFILE_INIT)],
            ),
        };

        let result = self.exec_shell(&script, shell).await?;
        if result.exit_code != 0 {
            warn!(
                "ssh npm install codex failed shell={} exit_code={} stderr={}",
                remote_shell_name(shell),
                result.exit_code,
                result.stderr.trim()
            );
            return Err(SshError::ExecFailed {
                exit_code: result.exit_code,
                stderr: if result.stderr.trim().is_empty() {
                    "npm install @openai/codex failed".to_string()
                } else {
                    result.stderr
                },
            });
        }
        let installed_path = parse_kv_lines(&result.stdout)
            .get("CODEX_PATH")
            .map(|s| s.to_string());
        match installed_path {
            Some(path) if !path.is_empty() => {
                info!(
                    "ssh npm install codex completed shell={} path={}",
                    remote_shell_name(shell),
                    path
                );
                Ok((
                    RemoteCodexBinary::Codex(path),
                    CodexInstallOutcome::Installed,
                ))
            }
            _ => {
                append_bridge_info_log(&format!(
                    "ssh_npm_install_no_path stdout={:?} stderr={:?}",
                    result.stdout, result.stderr
                ));
                Err(SshError::ExecFailed {
                    exit_code: 1,
                    stderr: format!(
                        "codex binary path not returned after npm install. stdout: {}",
                        result.stdout.chars().take(200).collect::<String>()
                    ),
                })
            }
        }
    }

    /// Best-effort: if `binary` was installed by us under `~/.litter/` and
    /// the update sentinel is older than 24h, check for a newer release and
    /// install it. Any failure along the way is swallowed and logged — the
    /// caller continues to use the previously-resolved binary.
    pub(crate) async fn maybe_update_managed_codex(
        &self,
        binary: &RemoteCodexBinary,
        shell: RemoteShell,
    ) -> Option<(RemoteCodexBinary, CodexInstallOutcome)> {
        let path = binary.path();
        let is_managed = path.contains("/.litter/") || path.contains(r"\.litter\");
        if !is_managed {
            trace!("ssh codex update check: skipping non-managed path={}", path);
            return None;
        }

        match self.is_codex_update_check_due(shell).await {
            Ok(true) => {}
            Ok(false) => {
                trace!("ssh codex update check: sentinel fresh, skipping");
                return None;
            }
            Err(err) => {
                warn!("ssh codex update check: sentinel check failed: {err}");
                return None;
            }
        }

        info!(
            "ssh codex update check: probing for newer release path={} shell={}",
            path,
            remote_shell_name(shell)
        );

        let is_windows_shell = matches!(shell, RemoteShell::PowerShell);
        let looks_like_npm =
            path.contains("node_modules/.bin/codex") || path.contains(r"node_modules\.bin\codex");

        let result: Result<(RemoteCodexBinary, CodexInstallOutcome), SshError> =
            if looks_like_npm || is_windows_shell {
                self.install_codex_via_npm(shell).await
            } else {
                match self.detect_remote_platform_with_shell(Some(shell)).await {
                    Ok(platform) => self.install_latest_stable_codex(platform).await,
                    Err(err) => Err(err),
                }
            };

        // Always touch the sentinel — success, "up to date", or failure —
        // so we don't re-hit GitHub/npm on every reconnect within 24h.
        if let Err(err) = self.touch_codex_update_sentinel(shell).await {
            warn!("ssh codex update check: sentinel touch failed: {err}");
        }

        match result {
            Ok((new_binary, outcome)) => {
                info!(
                    "ssh codex update check: completed outcome={:?} path={}",
                    outcome,
                    new_binary.path()
                );
                Some((new_binary, outcome))
            }
            Err(err) => {
                warn!(
                    "ssh codex update check: update attempt failed, continuing with existing binary: {err}"
                );
                None
            }
        }
    }

    async fn is_codex_update_check_due(&self, shell: RemoteShell) -> Result<bool, SshError> {
        let interval = CODEX_UPDATE_CHECK_INTERVAL_SECS.to_string();
        let script = crate::ssh_scripts::render(
            shell.update_sentinel_check_template(),
            &[("INTERVAL", &interval)],
        );
        let result = self.exec_shell(&script, shell).await?;
        Ok(!result.stdout.trim().eq_ignore_ascii_case("FRESH"))
    }

    async fn touch_codex_update_sentinel(&self, shell: RemoteShell) -> Result<(), SshError> {
        let script = match shell {
            RemoteShell::Posix => r#"mkdir -p "$HOME/.litter/codex" 2>/dev/null || true
touch "$HOME/.litter/codex/.last-update-check" 2>/dev/null || true"#
                .to_string(),
            RemoteShell::PowerShell => {
                r#"$dir = Join-Path $env:USERPROFILE '.litter\codex'
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
$sentinel = Join-Path $dir '.last-update-check'
if (Test-Path $sentinel) { (Get-Item $sentinel).LastWriteTime = Get-Date } else { Set-Content -Path $sentinel -Value '' }"#
                    .to_string()
            }
        };
        self.exec_shell(&script, shell).await?;
        Ok(())
    }
}

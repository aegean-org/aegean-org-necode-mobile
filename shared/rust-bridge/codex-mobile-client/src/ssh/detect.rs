//! Detect the remote shell (POSIX vs. PowerShell) and platform
//! (`{macos,linux,windows}-{arm64,x64}`).
//!
//! Shell detection is heuristic: `echo %OS%` returns `Windows_NT` only on
//! cmd.exe, while POSIX shells return the literal `%OS%`. A second probe
//! (`echo $env:OS`) covers OpenSSH-on-Windows configs whose default shell
//! is already PowerShell.

use tracing::info;

use super::{
    RemotePlatform, RemoteShell, SshClient, SshError, append_bridge_info_log, remote_platform_name,
    remote_shell_name,
};

impl SshClient {
    pub(crate) async fn detect_remote_shell(&self) -> RemoteShell {
        if let Ok(result) = self.exec("echo %OS%").await {
            let out = result.stdout.trim();
            append_bridge_info_log(&format!(
                "ssh_detect_shell cmd_probe out={:?} exit={}",
                out, result.exit_code
            ));
            if out == "Windows_NT" {
                info!("ssh detect shell result=powershell via=cmd_probe");
                return RemoteShell::PowerShell;
            }
        }
        if let Ok(result) = self.exec("echo $env:OS").await {
            let out = result.stdout.trim();
            append_bridge_info_log(&format!(
                "ssh_detect_shell ps_probe out={:?} exit={}",
                out, result.exit_code
            ));
            if out.contains("Windows") {
                info!("ssh detect shell result=powershell via=ps_probe");
                return RemoteShell::PowerShell;
            }
        }
        append_bridge_info_log("ssh_detect_shell result=Posix");
        info!("ssh detect shell result=posix");
        RemoteShell::Posix
    }

    pub(crate) async fn detect_remote_platform_with_shell(
        &self,
        shell_hint: Option<RemoteShell>,
    ) -> Result<RemotePlatform, SshError> {
        let shell = match shell_hint {
            Some(s) => s,
            None => self.detect_remote_shell().await,
        };
        info!(
            "ssh detect platform start shell={}",
            remote_shell_name(shell)
        );

        match shell {
            RemoteShell::PowerShell => {
                let result = self
                    .exec_shell(
                        r#"Write-Output "$env:OS"; Write-Output "$env:PROCESSOR_ARCHITECTURE""#,
                        shell,
                    )
                    .await?;
                let mut lines = result.stdout.lines();
                let os = lines.next().unwrap_or_default().trim();
                let arch = lines.next().unwrap_or_default().trim();
                let platform = match (os, arch) {
                    ("Windows_NT", "AMD64") | ("Windows_NT", "x86_64") => {
                        Ok(RemotePlatform::WindowsX64)
                    }
                    ("Windows_NT", "ARM64") | ("Windows_NT", "aarch64") => {
                        Ok(RemotePlatform::WindowsArm64)
                    }
                    _ => Err(SshError::ExecFailed {
                        exit_code: 1,
                        stderr: format!("unsupported Windows platform: os={os} arch={arch}"),
                    }),
                }?;
                info!(
                    "ssh detect platform result shell={} os={} arch={} platform={}",
                    remote_shell_name(shell),
                    os,
                    arch,
                    remote_platform_name(platform)
                );
                Ok(platform)
            }
            RemoteShell::Posix => {
                let result = self
                    .exec_posix(
                        r#"uname_s="$(uname -s 2>/dev/null || true)"; uname_m="$(uname -m 2>/dev/null || true)"; printf '%s\n%s' "$uname_s" "$uname_m""#,
                    )
                    .await?;
                let mut lines = result.stdout.lines();
                let os = lines.next().unwrap_or_default().trim();
                let arch = lines.next().unwrap_or_default().trim();
                let platform = match (os, arch) {
                    ("Darwin", "arm64") | ("Darwin", "aarch64") => Ok(RemotePlatform::MacosArm64),
                    ("Darwin", "x86_64") | ("Darwin", "amd64") => Ok(RemotePlatform::MacosX64),
                    ("Linux", "aarch64") | ("Linux", "arm64") => Ok(RemotePlatform::LinuxArm64),
                    ("Linux", "x86_64") | ("Linux", "amd64") => Ok(RemotePlatform::LinuxX64),
                    _ => Err(SshError::ExecFailed {
                        exit_code: 1,
                        stderr: format!("unsupported remote platform: os={os} arch={arch}"),
                    }),
                }?;
                info!(
                    "ssh detect platform result shell={} os={} arch={} platform={}",
                    remote_shell_name(shell),
                    os,
                    arch,
                    remote_platform_name(platform)
                );
                Ok(platform)
            }
        }
    }
}

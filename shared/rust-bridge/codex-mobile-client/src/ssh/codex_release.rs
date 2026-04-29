//! Resolve a Codex release tarball download URL for a remote platform.
//!
//! `fetch_latest_stable_codex_release` hits the GitHub releases API for
//! `openai/codex`, picks the most recent non-draft non-prerelease entry,
//! and returns the asset URL + binary name we should fetch from the remote
//! host. The actual `curl | tar` runs in a remote shell (see
//! `crate::ssh_scripts::posix::INSTALL_CODEX_RELEASE`).

use serde::Deserialize;

use super::{RemotePlatform, ResolvedCodexRelease, SshError};

#[derive(Debug, Deserialize)]
struct GithubReleaseAsset {
    name: String,
    browser_download_url: String,
}

#[derive(Debug, Deserialize)]
struct GithubRelease {
    tag_name: String,
    draft: bool,
    prerelease: bool,
    assets: Vec<GithubReleaseAsset>,
}

fn platform_asset_name(platform: RemotePlatform) -> Option<&'static str> {
    match platform {
        RemotePlatform::MacosArm64 => Some("codex-aarch64-apple-darwin.tar.gz"),
        RemotePlatform::MacosX64 => Some("codex-x86_64-apple-darwin.tar.gz"),
        RemotePlatform::LinuxArm64 => Some("codex-aarch64-unknown-linux-musl.tar.gz"),
        RemotePlatform::LinuxX64 => Some("codex-x86_64-unknown-linux-musl.tar.gz"),
        // Windows uses npm install, not binary release assets.
        RemotePlatform::WindowsX64 | RemotePlatform::WindowsArm64 => None,
    }
}

fn platform_binary_name(platform: RemotePlatform) -> Option<&'static str> {
    match platform {
        RemotePlatform::MacosArm64 => Some("codex-aarch64-apple-darwin"),
        RemotePlatform::MacosX64 => Some("codex-x86_64-apple-darwin"),
        RemotePlatform::LinuxArm64 => Some("codex-aarch64-unknown-linux-musl"),
        RemotePlatform::LinuxX64 => Some("codex-x86_64-unknown-linux-musl"),
        RemotePlatform::WindowsX64 | RemotePlatform::WindowsArm64 => None,
    }
}

fn resolve_release_from_listing(
    releases: &[GithubRelease],
    platform: RemotePlatform,
) -> Result<ResolvedCodexRelease, SshError> {
    let asset_name = platform_asset_name(platform).ok_or_else(|| SshError::ExecFailed {
        exit_code: 1,
        stderr: "no binary release asset for this platform (use npm install)".to_string(),
    })?;
    let binary_name = platform_binary_name(platform).ok_or_else(|| SshError::ExecFailed {
        exit_code: 1,
        stderr: "no binary name for this platform (use npm install)".to_string(),
    })?;
    let release = releases
        .iter()
        .find(|release| !release.draft && !release.prerelease)
        .ok_or_else(|| SshError::ExecFailed {
            exit_code: 1,
            stderr: "no stable Codex release available".to_string(),
        })?;
    let asset = release
        .assets
        .iter()
        .find(|asset| asset.name == asset_name)
        .ok_or_else(|| SshError::ExecFailed {
            exit_code: 1,
            stderr: format!(
                "stable Codex release {} is missing asset {}",
                release.tag_name, asset_name
            ),
        })?;
    Ok(ResolvedCodexRelease {
        tag_name: release.tag_name.clone(),
        asset_name: asset.name.clone(),
        binary_name: binary_name.to_string(),
        download_url: asset.browser_download_url.clone(),
    })
}

pub(super) async fn fetch_latest_stable_codex_release(
    platform: RemotePlatform,
) -> Result<ResolvedCodexRelease, SshError> {
    let releases = reqwest::Client::new()
        .get("https://api.github.com/repos/openai/codex/releases?per_page=30")
        .header(reqwest::header::USER_AGENT, "litter-codex-mobile")
        .header(reqwest::header::ACCEPT, "application/vnd.github+json")
        .send()
        .await
        .map_err(|error| SshError::ExecFailed {
            exit_code: 1,
            stderr: format!("failed to query Codex releases: {error}"),
        })?
        .error_for_status()
        .map_err(|error| SshError::ExecFailed {
            exit_code: 1,
            stderr: format!("Codex releases API returned error: {error}"),
        })?
        .json::<Vec<GithubRelease>>()
        .await
        .map_err(|error| SshError::ExecFailed {
            exit_code: 1,
            stderr: format!("failed to parse Codex releases response: {error}"),
        })?;
    resolve_release_from_listing(&releases, platform)
}

//! Parsers for the structured key/value output emitted by the SSH
//! bootstrap shell scripts (see `crate::ssh_scripts`).
//!
//! Scripts emit lines like `STATUS:installed` or `CODEX_PATH:/foo/bar`.
//! Keep parsing here so the line format is owned by one place.

use std::collections::HashMap;

/// Parse `KEY:value` lines from a script's stdout into a map. Splits each
/// line on its first colon. Lines without a colon are ignored.
pub(super) fn parse_kv_lines(stdout: &str) -> HashMap<&str, &str> {
    stdout
        .lines()
        .filter_map(|line| {
            let line = line.trim();
            line.split_once(':').map(|(k, v)| (k.trim(), v.trim()))
        })
        .collect()
}

/// Parse the `STATUS:...` / `PATH:...` lines emitted by the tarball install
/// script. Either may be missing (older script output / truncation) —
/// callers must handle `None` gracefully.
pub(super) fn parse_install_status_and_path(stdout: &str) -> (Option<String>, Option<String>) {
    let kv = parse_kv_lines(stdout);
    let status = kv.get("STATUS").map(|s| s.to_string());
    let path = kv.get("PATH").map(|s| s.to_string());
    if status.is_none() && path.is_none() {
        // Backcompat: pre-STATUS scripts emitted the path bare on stdout.
        let trimmed = stdout.trim();
        if !trimmed.is_empty() {
            return (None, Some(trimmed.to_string()));
        }
    }
    (status, path)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_kv_lines_picks_up_status_and_path() {
        let stdout = "STATUS:installed\nPATH:/home/user/.litter/bin/codex\n";
        let kv = parse_kv_lines(stdout);
        assert_eq!(kv.get("STATUS").copied(), Some("installed"));
        assert_eq!(
            kv.get("PATH").copied(),
            Some("/home/user/.litter/bin/codex")
        );
    }

    #[test]
    fn parse_kv_lines_keeps_value_colons_intact() {
        let stdout = "CODEX_PATH:C:\\Users\\me\\.litter\\codex\\node_modules\\.bin\\codex.cmd";
        let kv = parse_kv_lines(stdout);
        assert_eq!(
            kv.get("CODEX_PATH").copied(),
            Some("C:\\Users\\me\\.litter\\codex\\node_modules\\.bin\\codex.cmd")
        );
    }

    #[test]
    fn parse_install_status_and_path_back_compat_treats_bare_path_as_path() {
        let (status, path) = parse_install_status_and_path("/home/user/.litter/bin/codex\n");
        assert_eq!(status, None);
        assert_eq!(path, Some("/home/user/.litter/bin/codex".to_string()));
    }

    #[test]
    fn parse_install_status_and_path_extracts_both() {
        let (status, path) = parse_install_status_and_path("STATUS:up-to-date\nPATH:/x\n");
        assert_eq!(status.as_deref(), Some("up-to-date"));
        assert_eq!(path.as_deref(), Some("/x"));
    }
}

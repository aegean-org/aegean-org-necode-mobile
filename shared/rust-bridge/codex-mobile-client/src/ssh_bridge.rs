use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::Mutex as StdMutex;
use std::time::{Duration, Instant, UNIX_EPOCH};

use alleycat_bridge_core::{Bridge, ProcessLauncher, serve_stream};
use alleycat_claude_bridge::index::{ClaudeSessionInfo, entry_from_claude};
use alleycat_claude_bridge::{ClaudeBridge, ClaudeSessionRef};
use alleycat_opencode_bridge::{OpencodeBridge, OpencodeRuntime};
use alleycat_pi_bridge::PiBridge;
use chrono::{DateTime, Utc};
use codex_app_server_client::{AppServerClient, RemoteAppServerClient, RemoteAppServerConnectArgs};
use serde::{Deserialize, Serialize};
use tokio::io::duplex;
use tracing::{debug, warn};

use crate::session::connection::{
    RuntimeRemoteSessionResource, SshReconnectTransport, connect_remote_client,
};
use crate::ssh::{PROFILE_INIT, RemoteShell, SshClient, SshError, shell_quote};
use crate::ssh_detached_launcher::SshDetachedLauncher;
use crate::ssh_launcher::SshLauncher;
use crate::types::{AgentRuntimeInfo, AgentRuntimeKind};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum SshBridgeTransport {
    Ephemeral,
    Detached,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, uniffi::Record)]
pub struct RemoteAgentAvailability {
    pub kind: AgentRuntimeKind,
    pub status: AgentAvailabilityStatus,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, uniffi::Enum)]
pub enum AgentAvailabilityStatus {
    Available,
    AgentCliMissing,
    WindowsNotYetSupported,
}

#[derive(Debug, thiserror::Error)]
pub enum SshBridgeError {
    #[error("agent CLI missing: {0}")]
    AgentCliMissing(String),
    #[error("bridge startup failed: {0}")]
    BridgeStartupFailed(String),
    #[error("handshake failed: {0}")]
    HandshakeFailed(String),
    #[error("transport error: {0}")]
    Transport(String),
    #[error("codex uses the existing direct SSH path")]
    UseExistingCodexPath,
    #[error("Windows SSH bridge remotes are not supported yet")]
    WindowsRemoteNotYetSupported,
    #[error("detached SSH bridge transport is not implemented yet")]
    DetachedNotYetImplemented,
}

impl From<SshError> for SshBridgeError {
    fn from(value: SshError) -> Self {
        Self::Transport(value.to_string())
    }
}

pub async fn probe_remote_agents(
    ssh: &Arc<SshClient>,
) -> Result<Vec<RemoteAgentAvailability>, SshBridgeError> {
    let shell = ssh.detect_remote_shell().await;
    let kinds = [
        AgentRuntimeKind::Claude,
        AgentRuntimeKind::Pi,
        AgentRuntimeKind::Opencode,
        AgentRuntimeKind::Codex,
    ];
    if shell == RemoteShell::PowerShell {
        return Ok(kinds
            .into_iter()
            .map(|kind| RemoteAgentAvailability {
                kind,
                status: AgentAvailabilityStatus::WindowsNotYetSupported,
            })
            .collect());
    }

    let script = format!(
        "{PROFILE_INIT}\n{}",
        r#"find_cmd() {
  cmd="$1"
  case "$cmd" in
    */*)
      if [ -x "$cmd" ]; then
        printf '%s\n' "$cmd"
        return 0
      fi
      ;;
    *)
      path=$(command -v "$cmd" 2>/dev/null || true)
      if [ -n "$path" ]; then
        printf '%s\n' "$path"
        return 0
      fi
      ;;
  esac
  return 1
}

probe_one() {
  label="$1"
  shift
  for cmd in "$@"; do
    path=$(find_cmd "$cmd" || true)
    if [ -n "$path" ]; then
      printf '%s\t%s\n' "$label" "$path"
      return
    fi
  done
  printf '%s\t\n' "$label"
}

probe_one_executes() {
  label="$1"
  shift
  for cmd in "$@"; do
    path=$(find_cmd "$cmd" || true)
    if [ -n "$path" ]; then
      if "$path" --version >/dev/null 2>&1; then
        printf '%s\t%s\n' "$label" "$path"
      else
        printf '%s\t\n' "$label"
      fi
      return
    fi
  done
  printf '%s\t\n' "$label"
}

probe_one claude claude
probe_one pi pi-coding-agent pi
probe_one_executes opencode opencode
probe_one codex codex"#
    );
    let result = ssh.exec_shell(&script, shell).await?;
    if result.exit_code != 0 {
        return Err(SshBridgeError::Transport(result.stderr));
    }
    Ok(parse_agent_probe(&result.stdout))
}

pub async fn connect_runtime_resources_via_ssh(
    ssh: Arc<SshClient>,
    state_root: impl AsRef<Path>,
    runtime_kinds: Vec<AgentRuntimeKind>,
    transport: SshBridgeTransport,
    prefer_ipv6: bool,
) -> Result<(Vec<RuntimeRemoteSessionResource>, Vec<AgentRuntimeInfo>), SshBridgeError> {
    let state_root = state_root.as_ref().to_path_buf();
    let mut resources = Vec::new();
    let mut infos = Vec::new();
    for kind in runtime_kinds {
        let (client, trait_transport) = if kind == AgentRuntimeKind::Codex {
            let (client, reconnect_transport) =
                connect_codex_via_ssh(Arc::clone(&ssh), prefer_ipv6).await?;
            let t: std::sync::Arc<dyn crate::session::remote_transport::RemoteTransport> =
                std::sync::Arc::new(reconnect_transport);
            (client, Some(t))
        } else {
            (
                connect_app_server_client_via_ssh(
                    Arc::clone(&ssh),
                    state_root.join(runtime_label(kind)),
                    kind,
                    None,
                    transport,
                )
                .await?,
                None,
            )
        };
        resources.push(RuntimeRemoteSessionResource {
            runtime_kind: kind,
            client,
            transport: trait_transport,
            keepalive: None,
        });
        infos.push(AgentRuntimeInfo {
            kind,
            name: runtime_label(kind).to_string(),
            display_name: runtime_display_name(kind).to_string(),
            available: true,
        });
    }
    Ok((resources, infos))
}

pub async fn connect_app_server_client_via_ssh(
    ssh: Arc<SshClient>,
    state_dir: impl AsRef<Path>,
    kind: AgentRuntimeKind,
    bin_override: Option<String>,
    transport: SshBridgeTransport,
) -> Result<AppServerClient, SshBridgeError> {
    let shell = ssh.detect_remote_shell().await;
    if shell == RemoteShell::PowerShell {
        return Err(SshBridgeError::WindowsRemoteNotYetSupported);
    }
    let state_dir = state_dir.as_ref().to_path_buf();
    let launcher: Arc<dyn ProcessLauncher> = match transport {
        SshBridgeTransport::Ephemeral => Arc::new(SshLauncher::new(Arc::clone(&ssh), shell)),
        SshBridgeTransport::Detached => Arc::new(SshDetachedLauncher::new(Arc::clone(&ssh), shell)),
    };
    let bridge: Arc<dyn Bridge> = match kind {
        AgentRuntimeKind::Claude => {
            let bin = resolve_remote_cli(
                &ssh,
                shell,
                &cli_candidates(&["claude"], bin_override.as_deref()),
            )
            .await?;
            hydrate_remote_claude_index(&ssh, shell, &state_dir).await;
            ClaudeBridge::builder()
                .agent_bin(bin)
                .launcher(Arc::clone(&launcher))
                .codex_home(state_dir)
                .pool_capacity(4)
                .trust_persisted_cwd(true)
                .build()
                .await
                .map_err(|error| SshBridgeError::BridgeStartupFailed(error.to_string()))?
        }
        AgentRuntimeKind::Pi => {
            let bin = resolve_remote_cli(
                &ssh,
                shell,
                &cli_candidates(&["pi-coding-agent", "pi"], bin_override.as_deref()),
            )
            .await?;
            PiBridge::builder()
                .agent_bin(bin)
                .launcher(Arc::clone(&launcher))
                .codex_home(state_dir)
                .pool_capacity(4)
                .trust_persisted_cwd(true)
                .rpc_session_listing_only(true)
                .build()
                .await
                .map_err(|error| SshBridgeError::BridgeStartupFailed(error.to_string()))?
        }
        AgentRuntimeKind::Opencode => {
            return connect_opencode_via_ssh(ssh, state_dir, bin_override).await;
        }
        AgentRuntimeKind::Codex => return Err(SshBridgeError::UseExistingCodexPath),
    };
    connect_bridge_stream(bridge, kind).await
}

async fn connect_bridge_stream(
    bridge: Arc<dyn Bridge>,
    kind: AgentRuntimeKind,
) -> Result<AppServerClient, SshBridgeError> {
    let (client_io, server_io) = duplex(64 * 1024);
    tokio::spawn(async move {
        if let Err(error) = serve_stream(bridge, server_io).await {
            warn!("ssh bridge stream ended: {error:#}");
        }
    });
    let label = format!("ssh-bridge://{}", runtime_label(kind));
    let args = RemoteAppServerConnectArgs {
        websocket_url: label.clone(),
        auth_token: None,
        client_name: "Litter".to_string(),
        client_version: "1.0".to_string(),
        experimental_api: true,
        opt_out_notification_methods: Vec::new(),
        channel_capacity: 256,
    };
    let remote = RemoteAppServerClient::connect_json_line_stream(client_io, args, label)
        .await
        .map_err(|error| SshBridgeError::HandshakeFailed(error.to_string()))?;
    Ok(AppServerClient::Remote(remote))
}

async fn connect_codex_via_ssh(
    ssh: Arc<SshClient>,
    prefer_ipv6: bool,
) -> Result<(AppServerClient, SshReconnectTransport), SshBridgeError> {
    let bootstrap = ssh.bootstrap_codex_server(None, prefer_ipv6).await?;
    let websocket_url = format!("ws://127.0.0.1:{}", bootstrap.tunnel_local_port);
    let args = RemoteAppServerConnectArgs {
        websocket_url: websocket_url.clone(),
        auth_token: None,
        client_name: "Litter".to_string(),
        client_version: "1.0".to_string(),
        experimental_api: true,
        opt_out_notification_methods: Vec::new(),
        channel_capacity: 256,
    };
    let client = connect_remote_client(&args)
        .await
        .map_err(|error| SshBridgeError::HandshakeFailed(error.to_string()))?;
    let ssh_pid = Arc::new(StdMutex::new(bootstrap.pid));
    let reconnect_transport = SshReconnectTransport {
        ssh_client: Arc::clone(&ssh),
        local_port: bootstrap.tunnel_local_port,
        remote_port: Arc::new(StdMutex::new(bootstrap.server_port)),
        prefer_ipv6,
        working_dir: None,
        ssh_pid: Some(ssh_pid),
    };
    debug!(
        "ssh codex runtime connected via direct bootstrap: websocket_url={} remote_port={} local_port={}",
        websocket_url, bootstrap.server_port, bootstrap.tunnel_local_port
    );
    Ok((client, reconnect_transport))
}

async fn connect_opencode_via_ssh(
    ssh: Arc<SshClient>,
    state_dir: PathBuf,
    bin_override: Option<String>,
) -> Result<AppServerClient, SshBridgeError> {
    let shell = ssh.detect_remote_shell().await;
    let bin = resolve_remote_cli(
        &ssh,
        shell,
        &cli_candidates(&["opencode"], bin_override.as_deref()),
    )
    .await?;
    validate_remote_cli_executes(&ssh, shell, &bin, "opencode").await?;
    let remote_port = pick_remote_port(&ssh, shell).await?;
    let session_id = format!("opencode-{}", now_millis());
    spawn_remote_opencode(&ssh, shell, &bin, remote_port, &session_id).await?;
    wait_until_remote_opencode_healthy(&ssh, shell, remote_port, &session_id).await?;
    let local_port = ssh.forward_port_to(0, "127.0.0.1", remote_port).await?;
    let base_url = format!("http://127.0.0.1:{local_port}");
    if let Err(error) = wait_until_opencode_healthy(&base_url).await {
        let logs = fetch_remote_opencode_logs(&ssh, shell, &session_id)
            .await
            .unwrap_or_else(|log_error| {
                format!("failed to fetch remote opencode logs: {log_error}")
            });
        return Err(SshBridgeError::BridgeStartupFailed(format!(
            "{error}; remote opencode logs:\n{logs}"
        )));
    }

    let bridge = OpencodeBridge::builder()
        .runtime(OpencodeRuntime::external(base_url, String::new()))
        .state_dir(state_dir)
        .build()
        .await
        .map_err(|error| SshBridgeError::BridgeStartupFailed(error.to_string()))?;
    connect_bridge_stream(bridge, AgentRuntimeKind::Opencode).await
}

fn cli_candidates(defaults: &[&str], bin_override: Option<&str>) -> Vec<String> {
    if let Some(bin) = bin_override
        && !bin.trim().is_empty()
    {
        return vec![bin.to_string()];
    }
    defaults
        .iter()
        .map(|candidate| candidate.to_string())
        .collect()
}

async fn resolve_remote_cli(
    ssh: &SshClient,
    shell: RemoteShell,
    candidates: &[String],
) -> Result<String, SshBridgeError> {
    if shell == RemoteShell::PowerShell {
        return Err(SshBridgeError::WindowsRemoteNotYetSupported);
    }
    let candidate_list = candidates
        .iter()
        .map(|candidate| shell_quote(candidate))
        .collect::<Vec<_>>()
        .join(" ");
    let script = format!(
        "{PROFILE_INIT}\n{}",
        format!(
            r#"for cmd in {candidate_list}; do
  case "$cmd" in
    */*)
      if [ -x "$cmd" ]; then
        printf '%s\n' "$cmd"
        exit 0
      fi
      ;;
    *)
      path=$(command -v "$cmd" 2>/dev/null || true)
      if [ -n "$path" ]; then
        printf '%s\n' "$path"
        exit 0
      fi
      ;;
  esac
done
exit 127"#
        )
    );
    let result = ssh.exec_shell(&script, shell).await?;
    if result.exit_code == 0 {
        let path = result.stdout.trim();
        if path.is_empty() {
            Err(SshBridgeError::AgentCliMissing(candidates.join(" or ")))
        } else {
            Ok(path.to_string())
        }
    } else {
        Err(SshBridgeError::AgentCliMissing(candidates.join(" or ")))
    }
}

async fn validate_remote_cli_executes(
    ssh: &SshClient,
    shell: RemoteShell,
    bin: &str,
    label: &str,
) -> Result<(), SshBridgeError> {
    let script = format!(
        "{PROFILE_INIT}\n{} --version >/dev/null 2>&1",
        shell_quote(bin)
    );
    let result = ssh.exec_shell(&script, shell).await?;
    if result.exit_code == 0 {
        return Ok(());
    }
    Err(SshBridgeError::AgentCliMissing(format!(
        "{label} ({bin}) is present but failed to execute"
    )))
}

async fn hydrate_remote_claude_index(ssh: &SshClient, shell: RemoteShell, state_dir: &Path) {
    match scan_remote_claude_sessions(ssh, shell).await {
        Ok(sessions) => {
            let index_path = state_dir.join("threads.json");
            let index = match alleycat_bridge_core::ThreadIndex::<ClaudeSessionRef>::open_at(
                index_path,
            )
            .await
            {
                Ok(index) => index,
                Err(error) => {
                    warn!(
                        state_dir = %state_dir.display(),
                        "ssh bridge failed to open claude thread index for remote hydration: {error:#}"
                    );
                    return;
                }
            };
            let mut upserted = 0usize;
            for session in sessions {
                if let Err(error) = index.insert(entry_from_claude(&session)).await {
                    warn!(
                        thread_id = %session.session_id,
                        "ssh bridge failed to insert hydrated claude session: {error:#}"
                    );
                    continue;
                }
                upserted += 1;
            }
            debug!(
                state_dir = %state_dir.display(),
                upserted,
                "ssh bridge hydrated remote claude sessions"
            );
        }
        Err(error) => {
            warn!("ssh bridge remote claude session scan failed: {error}");
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RemoteClaudeSession {
    path: String,
    session_id: String,
    cwd: String,
    created_ms: i64,
    modified_ms: i64,
    first_message: String,
}

async fn scan_remote_claude_sessions(
    ssh: &SshClient,
    shell: RemoteShell,
) -> Result<Vec<ClaudeSessionInfo>, SshBridgeError> {
    let script = format!("{PROFILE_INIT}\n{REMOTE_CLAUDE_SESSION_SCAN}");
    let result = ssh.exec_shell(&script, shell).await?;
    if result.exit_code != 0 {
        return Err(SshBridgeError::Transport(nonempty_stderr_or_stdout(result)));
    }
    Ok(parse_remote_claude_scan(&result.stdout)
        .into_iter()
        .map(|session| ClaudeSessionInfo {
            path: PathBuf::from(session.path),
            session_id: session.session_id,
            cwd: session.cwd,
            created: datetime_from_millis(session.created_ms),
            modified: datetime_from_millis(session.modified_ms),
            first_message: session.first_message,
        })
        .collect())
}

fn datetime_from_millis(ms: i64) -> DateTime<Utc> {
    if ms <= 0 {
        return Utc::now();
    }
    DateTime::<Utc>::from_timestamp_millis(ms).unwrap_or_else(Utc::now)
}

fn parse_remote_claude_scan(stdout: &str) -> Vec<RemoteClaudeSession> {
    stdout
        .lines()
        .filter_map(|line| {
            let fields = line.split('\t').collect::<Vec<_>>();
            if fields.len() < 7 || fields[0] != "C" {
                return None;
            }
            Some(RemoteClaudeSession {
                path: fields[1].to_string(),
                session_id: fields[2].to_string(),
                cwd: fields[3].to_string(),
                created_ms: parse_i64_field(fields[4]),
                modified_ms: parse_i64_field(fields[5]),
                first_message: default_first_message(fields[6]),
            })
        })
        .collect()
}

fn parse_i64_field(value: &str) -> i64 {
    value.parse().unwrap_or(0)
}

fn default_first_message(value: &str) -> String {
    let value = value.trim();
    if value.is_empty() {
        "(no messages)".to_string()
    } else {
        value.to_string()
    }
}

const REMOTE_CLAUDE_SESSION_SCAN: &str = r#"clean_field() {
  printf '%s' "$1" | tr '\t\r\n' '   '
}
mtime_ms() {
  if seconds=$(stat -c %Y "$1" 2>/dev/null); then
    :
  elif seconds=$(stat -f %m "$1" 2>/dev/null); then
    :
  else
    seconds=0
  fi
  case "$seconds" in
    ''|*[!0-9]*) seconds=0 ;;
  esac
  printf '%s000' "$seconds"
}
root="${CLAUDE_PROJECTS_DIR:-$HOME/.claude/projects}"
case "$root" in "~") root="$HOME" ;; "~/"*) root="$HOME/${root#~/}" ;; esac
[ -d "$root" ] || exit 0
find "$root" -type f -name '*.jsonl' 2>/dev/null | while IFS= read -r path; do
  [ -f "$path" ] || continue
  base=${path##*/}
  session_id=${base%.jsonl}
  modified_ms=$(mtime_ms "$path")
  meta=$(
    awk '
      function clean(s) {
        gsub(/\\n/, " ", s)
        gsub(/\\r/, " ", s)
        gsub(/\\t/, " ", s)
        gsub(/\t/, " ", s)
        gsub(/\r/, " ", s)
        gsub(/\n/, " ", s)
        gsub(/\\"/, "\"", s)
        return s
      }
      function field(line, key, pat, rest) {
        pat = "\"" key "\"[[:space:]]*:[[:space:]]*\""
        if (!match(line, pat)) return ""
        rest = substr(line, RSTART + RLENGTH)
        if (match(rest, /([^"\\]|\\.)*/)) return clean(substr(rest, RSTART, RLENGTH))
        return ""
      }
      {
        if (cwd == "") cwd = field($0, "cwd")
        if (first == "" && $0 ~ /"type"[[:space:]]*:[[:space:]]*"user"/) {
          text = field($0, "text")
          if (text == "") text = field($0, "content")
          if (text != "") first = text
        }
        if (cwd != "" && first != "") exit
      }
      END { printf "%s\t%s", cwd, first }
    ' "$path" 2>/dev/null
  ) || meta="$(printf '\t')"
  cwd=${meta%%	*}
  first=${meta#*	}
  printf 'C\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(clean_field "$path")" \
    "$(clean_field "$session_id")" \
    "$(clean_field "$cwd")" \
    "$modified_ms" \
    "$modified_ms" \
    "$(clean_field "$first")"
done"#;

async fn spawn_remote_opencode(
    ssh: &SshClient,
    shell: RemoteShell,
    bin: &str,
    port: u16,
    session_id: &str,
) -> Result<(), SshBridgeError> {
    let script = format!(
        r#"{profile_init}
session_dir="$HOME/.litter/sessions/{session_id}"
mkdir -p "$session_dir"
: >"$session_dir/out.log"
: >"$session_dir/err.log"
if command -v setsid >/dev/null 2>&1; then
  nohup setsid {bin} serve --port={port} </dev/null >"$session_dir/out.log" 2>"$session_dir/err.log" &
else
  nohup {bin} serve --port={port} </dev/null >"$session_dir/out.log" 2>"$session_dir/err.log" &
fi
pid=$!
echo "$pid" >"$session_dir/agent.pid"
sleep 0.05
if ! kill -0 "$pid" 2>/dev/null; then
  echo "opencode exited immediately after launch" >&2
  echo "--- out.log ---" >&2
  (tail -n 120 "$session_dir/out.log" 2>/dev/null || true) >&2
  echo "--- err.log ---" >&2
  (tail -n 120 "$session_dir/err.log" 2>/dev/null || true) >&2
  exit 1
fi
printf '%s\n' "$session_dir""#,
        profile_init = PROFILE_INIT,
        session_id = session_id,
        bin = shell_quote(bin),
        port = port,
    );
    let result = ssh.exec_shell(&script, shell).await?;
    if result.exit_code == 0 {
        Ok(())
    } else {
        Err(SshBridgeError::BridgeStartupFailed(
            nonempty_stderr_or_stdout(result),
        ))
    }
}

async fn wait_until_remote_opencode_healthy(
    ssh: &SshClient,
    shell: RemoteShell,
    port: u16,
    session_id: &str,
) -> Result<(), SshBridgeError> {
    let body = r#"port=__PORT__
session_dir="$HOME/.litter/sessions/__SESSION_ID__"
url="http://127.0.0.1:${port}/global/health"
has_curl=0
if command -v curl >/dev/null 2>&1; then
  has_curl=1
fi

i=0
while [ "$i" -lt 100 ]; do
  i=$((i + 1))
  if [ "$has_curl" -eq 1 ]; then
    body=$(curl -fsS --max-time 1 "$url" 2>/dev/null || true)
    case "$body" in
      *'"healthy":true'*|*'"healthy": true'*)
        exit 0
        ;;
    esac
  fi

  pid=$(cat "$session_dir/agent.pid" 2>/dev/null || true)
  if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
    echo "opencode exited before reporting healthy at $url" >&2
    echo "--- out.log ---" >&2
    (tail -n 120 "$session_dir/out.log" 2>/dev/null || true) >&2
    echo "--- err.log ---" >&2
    (tail -n 120 "$session_dir/err.log" 2>/dev/null || true) >&2
    exit 1
  fi
  if [ "$has_curl" -ne 1 ] && [ "$i" -ge 10 ]; then
    exit 0
  fi
  sleep 0.1
done

echo "opencode did not become healthy at $url" >&2
echo "--- out.log ---" >&2
(tail -n 120 "$session_dir/out.log" 2>/dev/null || true) >&2
echo "--- err.log ---" >&2
(tail -n 120 "$session_dir/err.log" 2>/dev/null || true) >&2
exit 1"#;
    let script = format!(
        "{PROFILE_INIT}\n{}",
        body.replace("__PORT__", &port.to_string())
            .replace("__SESSION_ID__", session_id)
    );
    let result = ssh.exec_shell(&script, shell).await?;
    if result.exit_code == 0 {
        Ok(())
    } else {
        Err(SshBridgeError::BridgeStartupFailed(
            nonempty_stderr_or_stdout(result),
        ))
    }
}

async fn wait_until_opencode_healthy(base_url: &str) -> Result<(), SshBridgeError> {
    let client = reqwest::Client::new();
    let url = format!("{}/global/health", base_url.trim_end_matches('/'));
    let deadline = Instant::now() + Duration::from_secs(10);
    loop {
        if let Ok(resp) = client.get(&url).send().await
            && resp.status().is_success()
            && let Ok(body) = resp.json::<serde_json::Value>().await
            && body.get("healthy").and_then(serde_json::Value::as_bool) == Some(true)
        {
            return Ok(());
        }
        if Instant::now() >= deadline {
            return Err(SshBridgeError::BridgeStartupFailed(format!(
                "opencode did not become healthy at {url}"
            )));
        }
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
}

async fn fetch_remote_opencode_logs(
    ssh: &SshClient,
    shell: RemoteShell,
    session_id: &str,
) -> Result<String, SshBridgeError> {
    let body = r#"session_dir="$HOME/.litter/sessions/__SESSION_ID__"
echo "--- out.log ---"
tail -n 120 "$session_dir/out.log" 2>/dev/null || true
echo "--- err.log ---"
tail -n 120 "$session_dir/err.log" 2>/dev/null || true"#;
    let script = format!(
        "{PROFILE_INIT}\n{}",
        body.replace("__SESSION_ID__", session_id)
    );
    let result = ssh.exec_shell(&script, shell).await?;
    Ok(nonempty_stdout_or_stderr(result))
}

fn parse_agent_probe(stdout: &str) -> Vec<RemoteAgentAvailability> {
    stdout
        .lines()
        .filter_map(|line| {
            let (cmd, path) = line.split_once('\t').unwrap_or((line, ""));
            let kind = match cmd {
                "claude" => AgentRuntimeKind::Claude,
                "pi" | "pi-coding-agent" => AgentRuntimeKind::Pi,
                "opencode" => AgentRuntimeKind::Opencode,
                "codex" => AgentRuntimeKind::Codex,
                _ => return None,
            };
            let status = if path.trim().is_empty() {
                AgentAvailabilityStatus::AgentCliMissing
            } else {
                AgentAvailabilityStatus::Available
            };
            Some(RemoteAgentAvailability { kind, status })
        })
        .collect()
}

async fn pick_remote_port(ssh: &SshClient, shell: RemoteShell) -> Result<u16, SshBridgeError> {
    let start = fallback_remote_port();
    for offset in 0..50 {
        let port = 17600 + ((start - 17600 + offset) % 2000);
        if remote_port_looks_free(ssh, shell, port).await? {
            return Ok(port);
        }
    }
    debug!(
        "remote free-port probe failed, falling back to time-derived port: {}",
        start
    );
    Ok(start)
}

async fn remote_port_looks_free(
    ssh: &SshClient,
    shell: RemoteShell,
    port: u16,
) -> Result<bool, SshBridgeError> {
    let script = format!(
        "{PROFILE_INIT}\n{}",
        format!(
            r#"port={port}
if command -v lsof >/dev/null 2>&1; then
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | grep . >/dev/null 2>&1; then
    exit 1
  fi
  exit 0
fi
if command -v ss >/dev/null 2>&1; then
  if ss -ltn "sport = :$port" 2>/dev/null | awk 'NR > 1 {{ found = 1 }} END {{ exit found ? 0 : 1 }}'; then
    exit 1
  fi
  exit 0
fi
if command -v netstat >/dev/null 2>&1; then
  if netstat -ltn 2>/dev/null | awk -v p="$port" '$4 ~ ("[:.]" p "$") {{ found = 1 }} END {{ exit found ? 0 : 1 }}'; then
    exit 1
  fi
  exit 0
fi
exit 0"#
        )
    );
    let result = ssh.exec_shell(&script, shell).await?;
    Ok(result.exit_code == 0)
}

fn fallback_remote_port() -> u16 {
    let span = now_millis() % 2000;
    17600 + span as u16
}

fn nonempty_stderr_or_stdout(result: crate::ssh::ExecResult) -> String {
    if result.stderr.trim().is_empty() {
        result.stdout
    } else if result.stdout.trim().is_empty() {
        result.stderr
    } else {
        format!("{}\n{}", result.stderr, result.stdout)
    }
}

fn nonempty_stdout_or_stderr(result: crate::ssh::ExecResult) -> String {
    if result.stdout.trim().is_empty() {
        result.stderr
    } else if result.stderr.trim().is_empty() {
        result.stdout
    } else {
        format!("{}\n{}", result.stdout, result.stderr)
    }
}

fn now_millis() -> u128 {
    std::time::SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis()
}

pub fn runtime_label(kind: AgentRuntimeKind) -> &'static str {
    match kind {
        AgentRuntimeKind::Codex => "codex",
        AgentRuntimeKind::Pi => "pi",
        AgentRuntimeKind::Opencode => "opencode",
        AgentRuntimeKind::Claude => "claude",
    }
}

fn runtime_display_name(kind: AgentRuntimeKind) -> &'static str {
    match kind {
        AgentRuntimeKind::Codex => "Codex",
        AgentRuntimeKind::Pi => "Pi",
        AgentRuntimeKind::Opencode => "OpenCode",
        AgentRuntimeKind::Claude => "Claude",
    }
}

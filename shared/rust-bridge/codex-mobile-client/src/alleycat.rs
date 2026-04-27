//! Internal alleycat tunnel client wrapper.
//!
//! Thin shim over [`alleycat_client::Session`] that owns the QUIC session
//! plus any bound loopback forwards. Mirrors the layering of `crate::ssh`:
//! internal types live here, the UniFFI surface lives in `crate::ffi::alleycat`.

use std::time::Duration;

use alleycat_client::{
    ConnectParams, ForwardHandle, ForwardSpec, Session, SessionError, Target, WIRE_PROTOCOL_VERSION,
};
use serde::Deserialize;
use tracing::{debug, info, warn};

/// Length in hex characters of an SHA-256 certificate fingerprint.
const FINGERPRINT_HEX_LEN: usize = 64;

/// Per-candidate timeout when racing host candidates. The QUIC handshake
/// itself takes ~1 RTT once UDP reachability is established, so anything
/// above 3s is almost certainly a dead candidate (wrong network, NAT, etc.).
const PER_CANDIDATE_TIMEOUT: Duration = Duration::from_secs(4);

/// Parsed connect-params payload from a paired QR code.
///
/// The QR may now also carry an ordered list of host candidates the phone
/// can race; old QRs without that field deserialize as an empty list and
/// the caller is expected to provide a host explicitly.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParsedPairPayload {
    pub protocol_version: u32,
    pub udp_port: u16,
    pub cert_fingerprint: String,
    pub token: String,
    /// Ranked candidate hostnames/IPs, most-likely-reachable first.
    /// Empty when scanning a pre-`hostCandidates` QR.
    pub host_candidates: Vec<String>,
}

/// A connected alleycat session plus the forwards we bound on its behalf.
///
/// Held by the FFI layer so the QUIC session outlives the WebSocket that's
/// tunneled through it; reconnect plumbing in `session::connection` keeps
/// `Arc<AlleycatSession>` to re-`ensure_forward` after WebSocket drops.
pub struct AlleycatSession {
    pub session: Session,
    pub forwards: Vec<BoundForward>,
    /// Whichever candidate host actually completed the handshake. Useful for
    /// the iOS layer to display ("connected to studio.tail.ts.net") and to
    /// stash on the saved-server record.
    pub connected_host: String,
}

/// A single bound forward: which target it points at, and the loopback port
/// we ended up listening on.
#[derive(Debug, Clone)]
pub struct BoundForward {
    pub target: Target,
    pub local_port: u16,
    /// Held to keep the listener task alive for as long as the session is.
    pub handle: ForwardHandle,
}

#[derive(Debug, thiserror::Error)]
pub enum AlleycatError {
    #[error("invalid pair payload: {0}")]
    InvalidPayload(String),
    #[error("protocol version mismatch: payload={payload} client={client}")]
    ProtocolMismatch { payload: u32, client: u32 },
    #[error("session error: {0}")]
    Session(String),
}

impl From<SessionError> for AlleycatError {
    fn from(value: SessionError) -> Self {
        AlleycatError::Session(value.to_string())
    }
}

/// Wire shape of the JSON payload encoded in the pair QR.
///
/// Mirrors the subset of `alleycat_protocol::ReadyFile` that mobile cares
/// about — the pid / allowlist fields aren't needed at connect time.
#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PairPayloadWire {
    protocol_version: u32,
    udp_port: u16,
    cert_fingerprint: String,
    token: String,
    /// Optional for back-compat with the original (pre–host-candidates) QR.
    #[serde(default)]
    host_candidates: Vec<String>,
}

/// Parse and validate the JSON payload encoded into a pair QR.
///
/// Returns the parsed struct on success. Validates that:
/// - the JSON has all four required fields,
/// - `cert_fingerprint` is exactly 64 lowercase hex chars (SHA-256),
/// - `protocol_version` matches [`WIRE_PROTOCOL_VERSION`],
/// - `token` is non-empty.
pub fn parse_pair_payload(json: &str) -> Result<ParsedPairPayload, AlleycatError> {
    let wire: PairPayloadWire = serde_json::from_str(json)
        .map_err(|error| AlleycatError::InvalidPayload(format!("malformed JSON: {error}")))?;

    if wire.protocol_version != WIRE_PROTOCOL_VERSION {
        return Err(AlleycatError::ProtocolMismatch {
            payload: wire.protocol_version,
            client: WIRE_PROTOCOL_VERSION,
        });
    }

    if wire.token.is_empty() {
        return Err(AlleycatError::InvalidPayload("empty token".into()));
    }

    let normalized_fp = wire.cert_fingerprint.trim().to_ascii_lowercase();
    if normalized_fp.len() != FINGERPRINT_HEX_LEN
        || !normalized_fp.chars().all(|c| c.is_ascii_hexdigit())
    {
        return Err(AlleycatError::InvalidPayload(format!(
            "certFingerprint must be {FINGERPRINT_HEX_LEN} hex chars (sha256), got {}",
            normalized_fp.len()
        )));
    }

    let host_candidates: Vec<String> = wire
        .host_candidates
        .into_iter()
        .map(|h| h.trim().to_string())
        .filter(|h| !h.is_empty())
        .collect();

    Ok(ParsedPairPayload {
        protocol_version: wire.protocol_version,
        udp_port: wire.udp_port,
        cert_fingerprint: normalized_fp,
        token: wire.token,
        host_candidates,
    })
}

/// Try each candidate host in order with a short per-attempt timeout, picking
/// the first that completes a QUIC handshake. Once connected, bind a local
/// forward for each requested target.
///
/// `hosts` must be non-empty — callers typically prepend a user-typed override
/// (if any) onto `params.host_candidates`. Errors from individual hosts are
/// collected and returned together if all candidates fail, so the user can
/// see why every one was unreachable.
pub async fn connect_and_forward(
    hosts: Vec<String>,
    params: ParsedPairPayload,
    targets: Vec<Target>,
) -> Result<AlleycatSession, AlleycatError> {
    if hosts.is_empty() {
        return Err(AlleycatError::InvalidPayload(
            "no host candidates supplied".into(),
        ));
    }

    let mut attempt_errors: Vec<String> = Vec::new();
    let mut session_with_host: Option<(Session, String)> = None;

    for host in hosts {
        let connect_params = ConnectParams {
            host: host.clone(),
            port: params.udp_port,
            cert_fingerprint: params.cert_fingerprint.clone(),
            token: params.token.clone(),
            protocol_version: params.protocol_version,
        };
        info!(
            "alleycat: attempting host={} udp_port={}",
            host, params.udp_port
        );
        match tokio::time::timeout(PER_CANDIDATE_TIMEOUT, Session::connect(connect_params)).await {
            Ok(Ok(session)) => {
                info!("alleycat: connected via host={}", host);
                session_with_host = Some((session, host));
                break;
            }
            Ok(Err(error)) => {
                warn!("alleycat: host={} failed: {error}", host);
                attempt_errors.push(format!("{host}: {error}"));
            }
            Err(_) => {
                warn!(
                    "alleycat: host={} timed out after {:?}",
                    host, PER_CANDIDATE_TIMEOUT
                );
                attempt_errors.push(format!("{host}: timed out after {PER_CANDIDATE_TIMEOUT:?}"));
            }
        }
    }

    let (session, connected_host) = session_with_host.ok_or_else(|| {
        AlleycatError::Session(format!(
            "all host candidates failed: {}",
            attempt_errors.join("; ")
        ))
    })?;

    let mut forwards = Vec::with_capacity(targets.len());
    for target in targets {
        let handle = session
            .ensure_forward(ForwardSpec {
                local_port: 0,
                target: target.clone(),
            })
            .await?;
        debug!(
            "alleycat: bound forward target={:?} local_port={}",
            target,
            handle.local_port()
        );
        forwards.push(BoundForward {
            target,
            local_port: handle.local_port(),
            handle,
        });
    }

    Ok(AlleycatSession {
        session,
        forwards,
        connected_host,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn good_payload() -> String {
        format!(
            r#"{{"protocolVersion":{ver},"udpPort":47123,"certFingerprint":"{fp}","token":"deadbeef"}}"#,
            ver = WIRE_PROTOCOL_VERSION,
            fp = "a".repeat(FINGERPRINT_HEX_LEN),
        )
    }

    #[test]
    fn parse_pair_payload_happy_path() {
        let parsed = parse_pair_payload(&good_payload()).expect("happy-path parse should succeed");
        assert_eq!(parsed.protocol_version, WIRE_PROTOCOL_VERSION);
        assert_eq!(parsed.udp_port, 47123);
        assert_eq!(parsed.cert_fingerprint, "a".repeat(FINGERPRINT_HEX_LEN));
        assert_eq!(parsed.token, "deadbeef");
        // host_candidates is optional in the wire format — back-compat with
        // pre-candidate QRs returns an empty list.
        assert!(parsed.host_candidates.is_empty());
    }

    #[test]
    fn parse_pair_payload_reads_host_candidates() {
        let json = format!(
            r#"{{"protocolVersion":{ver},"udpPort":47123,"certFingerprint":"{fp}","token":"deadbeef","hostCandidates":["studio.tail.ts.net","100.64.0.1"," ","192.168.1.5"]}}"#,
            ver = WIRE_PROTOCOL_VERSION,
            fp = "a".repeat(FINGERPRINT_HEX_LEN),
        );
        let parsed = parse_pair_payload(&json).expect("should parse");
        assert_eq!(
            parsed.host_candidates,
            vec![
                "studio.tail.ts.net".to_string(),
                "100.64.0.1".to_string(),
                "192.168.1.5".to_string(),
            ],
            "blank entries should be filtered, order preserved"
        );
    }

    #[test]
    fn parse_pair_payload_rejects_missing_field() {
        let json = format!(
            r#"{{"protocolVersion":{ver},"udpPort":47123,"token":"deadbeef"}}"#,
            ver = WIRE_PROTOCOL_VERSION
        );
        let err = parse_pair_payload(&json).expect_err("missing field should fail");
        assert!(
            matches!(err, AlleycatError::InvalidPayload(_)),
            "expected InvalidPayload, got {err:?}"
        );
    }

    #[test]
    fn parse_pair_payload_rejects_bad_fingerprint_hex() {
        let json = format!(
            r#"{{"protocolVersion":{ver},"udpPort":47123,"certFingerprint":"not-hex","token":"deadbeef"}}"#,
            ver = WIRE_PROTOCOL_VERSION
        );
        let err = parse_pair_payload(&json).expect_err("bad fingerprint should fail");
        assert!(matches!(err, AlleycatError::InvalidPayload(_)));
    }

    #[test]
    fn parse_pair_payload_rejects_wrong_protocol_version() {
        let bad_version = WIRE_PROTOCOL_VERSION.wrapping_add(7);
        let json = format!(
            r#"{{"protocolVersion":{ver},"udpPort":47123,"certFingerprint":"{fp}","token":"deadbeef"}}"#,
            ver = bad_version,
            fp = "a".repeat(FINGERPRINT_HEX_LEN),
        );
        let err = parse_pair_payload(&json).expect_err("wrong protocol should fail");
        match err {
            AlleycatError::ProtocolMismatch { payload, client } => {
                assert_eq!(payload, bad_version);
                assert_eq!(client, WIRE_PROTOCOL_VERSION);
            }
            other => panic!("expected ProtocolMismatch, got {other:?}"),
        }
    }

    #[test]
    fn parse_pair_payload_rejects_malformed_json() {
        let err = parse_pair_payload("{not valid json").expect_err("malformed should fail");
        assert!(matches!(err, AlleycatError::InvalidPayload(_)));
    }

    #[test]
    fn parse_pair_payload_rejects_empty_token() {
        let json = format!(
            r#"{{"protocolVersion":{ver},"udpPort":47123,"certFingerprint":"{fp}","token":""}}"#,
            ver = WIRE_PROTOCOL_VERSION,
            fp = "a".repeat(FINGERPRINT_HEX_LEN),
        );
        let err = parse_pair_payload(&json).expect_err("empty token should fail");
        assert!(matches!(err, AlleycatError::InvalidPayload(_)));
    }
}

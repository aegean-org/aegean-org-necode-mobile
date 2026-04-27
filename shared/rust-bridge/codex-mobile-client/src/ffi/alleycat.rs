//! UniFFI surface for the alleycat QUIC tunnel.
//!
//! Connect-and-forward used to live here as a fat `AlleycatBridge` object
//! holding session state. That handed Swift two tasks (open the tunnel,
//! then open the WebSocket) plus a registry to clean both up on disconnect.
//!
//! The actual connect now lives on `MobileClient::connect_remote_over_alleycat`
//! and is exposed via `ServerBridge::connect_remote_over_alleycat`, mirroring
//! how SSH works (`MobileClient::connect_remote_over_ssh` +
//! `SshBridge::ssh_connect_and_bootstrap`). The `ServerSession` retains the
//! QUIC `AlleycatSession` so it dies with the WebSocket — no Swift-side
//! registry needed.
//!
//! What's left here: the boundary types and a thin `AlleycatBridge::parse_pair_payload`
//! so platform UIs can validate a scanned QR before showing a connect button.

use crate::alleycat::{AlleycatError, ParsedPairPayload};
use crate::ffi::ClientError;

#[derive(uniffi::Object)]
pub struct AlleycatBridge;

#[derive(Debug, Clone, uniffi::Record)]
pub struct AppAlleycatParams {
    pub protocol_version: u32,
    pub udp_port: u16,
    pub cert_fingerprint: String,
    pub token: String,
    /// Ranked list of host candidates the relay suggested. Empty when
    /// scanning a pre-`hostCandidates` QR; iOS falls back to a typed host.
    pub host_candidates: Vec<String>,
}

/// Returned by `ServerBridge::connect_remote_over_alleycat` so the platform
/// can persist the host that won the candidate race.
#[derive(Debug, Clone, uniffi::Record)]
pub struct AppAlleycatConnectResult {
    pub server_id: String,
    pub connected_host: String,
}

#[uniffi::export]
impl AlleycatBridge {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self
    }

    /// Validates and decodes the JSON payload encoded into a pair QR.
    /// Pure parse — does not open any sockets.
    pub fn parse_pair_payload(&self, json: String) -> Result<AppAlleycatParams, ClientError> {
        let parsed = crate::alleycat::parse_pair_payload(&json).map_err(map_alleycat_error)?;
        Ok(parsed.into())
    }
}

fn map_alleycat_error(error: AlleycatError) -> ClientError {
    match error {
        AlleycatError::InvalidPayload(message) => ClientError::InvalidParams(message),
        AlleycatError::ProtocolMismatch { payload, client } => ClientError::InvalidParams(format!(
            "alleycat protocol mismatch: payload={payload} client={client}"
        )),
        AlleycatError::Session(message) => ClientError::Transport(message),
    }
}

impl From<AppAlleycatParams> for ParsedPairPayload {
    fn from(value: AppAlleycatParams) -> Self {
        ParsedPairPayload {
            protocol_version: value.protocol_version,
            udp_port: value.udp_port,
            cert_fingerprint: value.cert_fingerprint,
            token: value.token,
            host_candidates: value.host_candidates,
        }
    }
}

impl From<ParsedPairPayload> for AppAlleycatParams {
    fn from(value: ParsedPairPayload) -> Self {
        AppAlleycatParams {
            protocol_version: value.protocol_version,
            udp_port: value.udp_port,
            cert_fingerprint: value.cert_fingerprint,
            token: value.token,
            host_candidates: value.host_candidates,
        }
    }
}

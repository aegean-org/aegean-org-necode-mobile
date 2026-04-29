use crate::alleycat::{AgentInfo, AgentWire, AlleycatError, ParsedPairPayload};
use crate::ffi::ClientError;

#[derive(uniffi::Object)]
pub struct AlleycatBridge;

#[derive(Debug, Clone, uniffi::Record)]
pub struct AppAlleycatPairPayload {
    pub v: u32,
    pub node_id: String,
    pub token: String,
    pub relay: Option<String>,
    pub host_name: Option<String>,
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum AppAlleycatAgentWire {
    Websocket,
    Jsonl,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct AppAlleycatAgentInfo {
    pub name: String,
    pub display_name: String,
    pub runtime_kind: Option<crate::types::AgentRuntimeKind>,
    pub wire: AppAlleycatAgentWire,
    pub available: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct AppAlleycatConnectResult {
    pub server_id: String,
    pub node_id: String,
    pub agent_name: String,
}

#[uniffi::export]
impl AlleycatBridge {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self
    }

    pub fn parse_pair_payload(&self, json: String) -> Result<AppAlleycatPairPayload, ClientError> {
        let parsed = crate::alleycat::parse_pair_payload(&json).map_err(map_alleycat_error)?;
        Ok(parsed.into())
    }
}

pub(crate) fn map_alleycat_error(error: AlleycatError) -> ClientError {
    match error {
        AlleycatError::InvalidPayload(message) => ClientError::InvalidParams(message),
        AlleycatError::ProtocolMismatch { payload, client } => ClientError::InvalidParams(format!(
            "alleycat protocol mismatch: payload={payload} client={client}"
        )),
        AlleycatError::Transport(message) => ClientError::Transport(message),
    }
}

impl From<AppAlleycatPairPayload> for ParsedPairPayload {
    fn from(value: AppAlleycatPairPayload) -> Self {
        ParsedPairPayload {
            version: value.v,
            node_id: value.node_id,
            token: value.token,
            relay: value.relay,
            host_name: value.host_name,
        }
    }
}

impl From<ParsedPairPayload> for AppAlleycatPairPayload {
    fn from(value: ParsedPairPayload) -> Self {
        AppAlleycatPairPayload {
            v: value.version,
            node_id: value.node_id,
            token: value.token,
            relay: value.relay,
            host_name: value.host_name,
        }
    }
}

impl From<AppAlleycatAgentWire> for AgentWire {
    fn from(value: AppAlleycatAgentWire) -> Self {
        match value {
            AppAlleycatAgentWire::Websocket => Self::Websocket,
            AppAlleycatAgentWire::Jsonl => Self::Jsonl,
        }
    }
}

impl From<AgentWire> for AppAlleycatAgentWire {
    fn from(value: AgentWire) -> Self {
        match value {
            AgentWire::Websocket => Self::Websocket,
            AgentWire::Jsonl => Self::Jsonl,
        }
    }
}

impl From<AgentInfo> for AppAlleycatAgentInfo {
    fn from(value: AgentInfo) -> Self {
        let runtime_kind = crate::alleycat::agent_runtime_kind(&value.name, &value.display_name);
        AppAlleycatAgentInfo {
            name: value.name,
            display_name: value.display_name,
            runtime_kind,
            wire: value.wire.into(),
            available: value.available,
        }
    }
}

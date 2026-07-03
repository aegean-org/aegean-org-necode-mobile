use std::time::Duration;

use base64::Engine;
use serde::Deserialize;
use serde_json::{Value, json};

use super::{AlleycatRestartTarget, MobileClient};
use crate::transport::{RpcError, TransportError};
use crate::types::{AgentRuntimeInfo, AppVoiceTranscriptionRequest, AppVoiceTranscriptionResponse};

const VOICE_TRANSCRIBE_TIMEOUT: Duration = Duration::from_secs(90);
const DEFAULT_VOICE_RUNTIME: &str = "necode";

impl MobileClient {
    pub async fn transcribe_voice(
        &self,
        server_id: &str,
        request: AppVoiceTranscriptionRequest,
    ) -> Result<AppVoiceTranscriptionResponse, RpcError> {
        self.alleycat_voice_target(server_id)?;
        let runtimes = self.server_agent_runtimes(server_id);
        let runtime_kind =
            select_alleycat_voice_agent(&runtimes, request.agent_runtime_kind.as_deref())
                .ok_or_else(|| {
                    RpcError::Transport(TransportError::ConnectionFailed(
                        "voice transcription requires a connected NeCode runtime".to_string(),
                    ))
                })?;
        let session = self.get_session(server_id)?;
        let params = voice_transcribe_params(&request);
        let result = tokio::time::timeout(
            VOICE_TRANSCRIBE_TIMEOUT,
            session.request_raw_for_runtime(runtime_kind, "voice/transcribe", params),
        )
        .await
        .map_err(|_| RpcError::Timeout)??;
        decode_voice_result(result)
    }

    fn alleycat_voice_target(&self, server_id: &str) -> Result<AlleycatRestartTarget, RpcError> {
        let target = match self.alleycat_restart_targets.lock() {
            Ok(guard) => guard.get(server_id).cloned(),
            Err(error) => error.into_inner().get(server_id).cloned(),
        };
        target.ok_or_else(|| {
            RpcError::Transport(TransportError::ConnectionFailed(
                "voice transcription is only available for Alleycat paired servers".to_string(),
            ))
        })
    }

    fn server_agent_runtimes(&self, server_id: &str) -> Vec<AgentRuntimeInfo> {
        self.app_store
            .snapshot()
            .servers
            .get(server_id)
            .map(|server| server.agent_runtimes.clone())
            .unwrap_or_default()
    }
}

pub(super) fn select_alleycat_voice_agent(
    runtimes: &[AgentRuntimeInfo],
    requested_runtime: Option<&str>,
) -> Option<String> {
    let desired = requested_runtime
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or(DEFAULT_VOICE_RUNTIME)
        .to_ascii_lowercase();
    runtimes
        .iter()
        .find(|runtime| runtime.available && runtime.kind.eq_ignore_ascii_case(&desired))
        .map(|runtime| runtime.kind.clone())
}

pub(super) fn voice_transcribe_jsonrpc_request(
    id: i64,
    request: &AppVoiceTranscriptionRequest,
) -> Result<Value, RpcError> {
    if request.audio_bytes.is_empty() {
        return Err(RpcError::Deserialization(
            "voice transcription audio is empty".to_string(),
        ));
    }
    Ok(json!({
        "jsonrpc": "2.0",
        "id": id,
        "method": "voice/transcribe",
        "params": voice_transcribe_params(request),
    }))
}

fn voice_transcribe_params(request: &AppVoiceTranscriptionRequest) -> Value {
    let mut params = serde_json::Map::new();
    params.insert(
        "audioBase64".to_string(),
        Value::String(base64::engine::general_purpose::STANDARD.encode(&request.audio_bytes)),
    );
    insert_non_empty(&mut params, "mimeType", request.mime_type.as_deref());
    insert_non_empty(&mut params, "fileName", request.file_name.as_deref());
    insert_non_empty(&mut params, "model", request.model.as_deref());
    insert_non_empty(&mut params, "language", request.language.as_deref());
    Value::Object(params)
}

fn insert_non_empty(map: &mut serde_json::Map<String, Value>, key: &str, value: Option<&str>) {
    if let Some(trimmed) = value.map(str::trim).filter(|value| !value.is_empty()) {
        map.insert(key.to_string(), Value::String(trimmed.to_string()));
    }
}

fn decode_voice_result(result: Value) -> Result<AppVoiceTranscriptionResponse, RpcError> {
    serde_json::from_value::<VoiceTranscribeResult>(result)
        .map(|result| AppVoiceTranscriptionResponse { text: result.text })
        .map_err(|error| RpcError::Deserialization(format!("decode voice result: {error}")))
}

#[derive(Debug, Deserialize)]
struct VoiceTranscribeResult {
    text: String,
}

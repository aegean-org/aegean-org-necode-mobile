//! NE ASR for both native shells. Recording and microphone permissions stay native.
use crate::ffi::ClientError;
use reqwest::{Client, Url};
use serde_json::Value;
use std::time::Duration;

#[uniffi::export(async_runtime = "tokio")]
pub async fn transcribe_ne_voice(token: String, audio_bytes: Vec<u8>, model: Option<String>, language: Option<String>) -> Result<String, ClientError> {
    let client = Client::builder().redirect(reqwest::redirect::Policy::none()).build()
        .map_err(|error| ClientError::Transport(format!("无法初始化语音服务：{}", error.without_url())))?;
    transcribe(&client, Url::parse("https://gateway.inoteexpress.com/v1/").unwrap(), token, audio_bytes, model, language).await
}
const MAX_AUDIO: usize = 3 * 1024 * 1024;
const MAX_RESPONSE: usize = 2 * 1024 * 1024;

async fn transcribe(client: &Client, url: Url, token: String, audio: Vec<u8>, model: Option<String>, language: Option<String>) -> Result<String, ClientError> {
    if token.contains(['\r', '\n']) { return Err(ClientError::InvalidParams("语音登录凭据无效".into())); }
    let token = token.trim();
    let token = if token.get(..7).is_some_and(|prefix| prefix.eq_ignore_ascii_case("bearer ")) { token[7..].trim() } else { token };
    if token.is_empty() || token.contains(['\r', '\n']) { return Err(ClientError::InvalidParams("语音登录凭据无效".into())); }
    validate_wav(&audio)?;
    let bearer = if token.contains("##") { token.to_owned() } else { format!("necli##{token}") };
    let catalog = read_json(client.get(url.join("models").map_err(|e| ClientError::Rpc(e.to_string()))?)
        .bearer_auth(&bearer).timeout(Duration::from_secs(20)).send().await.map_err(|e| ClientError::Rpc(format!("读取语音模型失败：{e}")))?).await?;
    let model = select_asr(&catalog, model.as_deref())?;
    let language = language.as_deref().unwrap_or("zh").trim();
    if language.is_empty() || language.len() > 32 || !language.chars().all(|c| c.is_ascii_alphanumeric() || c == '-') {
        return Err(ClientError::InvalidParams("语音语言参数无效".into()));
    }
    let boundary = format!("necode-{}", uuid::Uuid::new_v4().simple());
    let mut body = format!("--{boundary}\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n{model}\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\n{language}\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n").into_bytes();
    body.extend_from_slice(&audio);
    body.extend_from_slice(format!("\r\n--{boundary}--\r\n").as_bytes());
    let response = read_json(client.post(url.join("audio/transcriptions").map_err(|e| ClientError::Rpc(e.to_string()))?)
        .bearer_auth(bearer).header("Content-Type", format!("multipart/form-data; boundary={boundary}"))
        .timeout(Duration::from_secs(60)).body(body).send().await.map_err(|e| ClientError::Rpc(format!("语音识别失败：{e}")))?).await?;
    response.get("text").or_else(|| response.get("data").and_then(|data| data.get("text")))
        .and_then(Value::as_str).map(str::trim).filter(|text| !text.is_empty()).map(str::to_owned)
        .ok_or_else(|| ClientError::Rpc("ASR 没有返回识别文字".into()))
}

fn validate_wav(audio: &[u8]) -> Result<(), ClientError> {
    let invalid = || ClientError::InvalidParams("录音必须为非空 PCM WAV，且不能超过 3 MiB".into());
    if audio.len() < 44 || audio.len() > MAX_AUDIO || &audio[..4] != b"RIFF" || &audio[8..12] != b"WAVE" { return Err(invalid()); }
    let u32_at = |offset: usize| u32::from_le_bytes(audio[offset..offset + 4].try_into().unwrap());
    let u16_at = |offset: usize| u16::from_le_bytes(audio[offset..offset + 2].try_into().unwrap());
    if u32_at(4) as usize != audio.len() - 8 { return Err(invalid()); }
    let mut offset = 12;
    let mut format = None;
    let mut data_size = None;
    while offset < audio.len() {
        if audio.len() - offset < 8 { return Err(invalid()); }
        let size = u32_at(offset + 4) as usize;
        let start = offset + 8;
        if size > audio.len() - start { return Err(invalid()); }
        match &audio[offset..offset + 4] {
            b"fmt " => {
                if size < 16 || format.is_some() { return Err(invalid()); }
                let channels = u16_at(start + 2);
                let sample_rate = u32_at(start + 4);
                let align = u16_at(start + 12);
                if u16_at(start) != 1 || !matches!(channels,1|2) || u16_at(start + 14) != 16
                    || !(8000..=192000).contains(&sample_rate) || align != channels * 2
                    || u32_at(start + 8) != sample_rate * u32::from(align) { return Err(invalid()); }
                format = Some(align as usize);
            }
            b"data" => { if data_size.is_some() || size == 0 { return Err(invalid()); } data_size = Some(size); }
            _ => {}
        }
        offset = start + size + size % 2;
        if offset > audio.len() { return Err(invalid()); }
    }
    match (format, data_size) { (Some(align),Some(size)) if size % align == 0 => Ok(()), _ => Err(invalid()) }
}

async fn read_json(mut response: reqwest::Response) -> Result<Value, ClientError> {
    let status = response.status();
    if response.content_length().is_some_and(|size| size > MAX_RESPONSE as u64) { return Err(ClientError::Rpc("语音服务响应过大".into())); }
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|e| ClientError::Rpc(format!("读取语音响应失败：{e}")))? {
        if bytes.len() + chunk.len() > MAX_RESPONSE { return Err(ClientError::Rpc("语音服务响应过大".into())); }
        bytes.extend_from_slice(&chunk);
    }
    if !status.is_success() { return Err(ClientError::Rpc(format!("语音服务返回 HTTP {}", status.as_u16()))); }
    let value: Value = serde_json::from_slice(&bytes).map_err(|e| ClientError::Rpc(format!("语音服务返回无效 JSON：{e}")))?;
    if value.get("error").is_some_and(|error| !error.is_null()) || value.get("success") == Some(&Value::Bool(false))
        || value.get("code").and_then(Value::as_i64).is_some_and(|code| code != 0 && code != 200) {
        return Err(ClientError::Rpc("语音服务拒绝了请求".into()));
    }
    Ok(value)
}

fn select_asr(catalog: &Value, picked: Option<&str>) -> Result<String, ClientError> {
    if catalog.get("error").is_some_and(|error| !error.is_null()) || catalog.get("code").and_then(Value::as_i64).is_some_and(|code| code != 0 && code != 200) {
        return Err(ClientError::Rpc("语音模型字典读取失败".into()));
    }
    let containers = [Some(catalog), catalog.get("data")];
    let rows = containers.iter().flatten().find_map(|value| value.as_array().or_else(|| ["models", "list", "items"].iter().find_map(|key| value.get(key).and_then(Value::as_array))))
        .ok_or_else(|| ClientError::Rpc("语音模型字典格式无效".into()))?;
    let models: Vec<(&str, &Value)> = rows.iter().filter_map(|row| {
        let id = ["id", "model", "model_id", "modelId", "slug"].iter().find_map(|key| row.get(key).and_then(Value::as_str))?;
        let is_asr = id.to_ascii_lowercase().split(|c: char| !c.is_ascii_alphanumeric()).any(|part| part == "asr")
            || ["type", "model_type", "modelType", "api", "mode", "category", "task"].iter().any(|key| row.get(key).and_then(Value::as_str).is_some_and(|kind| matches!(kind.to_ascii_lowercase().as_str(), "asr" | "speech" | "transcription" | "transcribe")));
        (is_asr && !id.is_empty() && !id.contains(['\r', '\n'])).then_some((id, row))
    }).collect();
    if let Some(picked) = picked.map(str::trim).filter(|id| !id.is_empty()) {
        return models.iter().find(|(id, _)| *id == picked).map(|(id, _)| id.to_string()).ok_or_else(|| ClientError::InvalidParams("所选模型不在 ASR 模型字典中".into()));
    }
    if models.len() == 1 { return Ok(models[0].0.to_owned()); }
    let defaults: Vec<_> = models.iter().filter(|(_, row)| ["default", "is_default", "isDefault"].iter().any(|key| row.get(key) == Some(&Value::Bool(true)))).collect();
    if defaults.len() == 1 { return Ok(defaults[0].0.to_owned()); }
    Err(ClientError::InvalidParams("语音服务没有唯一的默认 ASR 模型，请配置语音模型".into()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    #[test]
    fn asr_uses_the_dictionary_and_never_picks_a_chat_model_or_ambiguous_default() {
        let catalog = json!({"data":[{"id":"chat","type":"chat"},{"id":"new-asr","type":"asr"}]});
        assert_eq!(select_asr(&catalog,None).unwrap(), "new-asr");
        assert!(select_asr(&catalog,Some("chat")).is_err());
        let two = json!({"models":[{"id":"a","type":"asr"},{"id":"b","type":"asr"}]});
        assert!(select_asr(&two,None).is_err());
        assert_eq!(select_asr(&two,Some("b")).unwrap(),"b");
        assert!(select_asr(&json!({"code":401,"data":[]}), None).is_err());
    }
    fn pcm_wav() -> Vec<u8> {
        let mut wav = Vec::new();
        wav.extend_from_slice(b"RIFF"); wav.extend_from_slice(&40u32.to_le_bytes()); wav.extend_from_slice(b"WAVEfmt ");
        wav.extend_from_slice(&16u32.to_le_bytes()); wav.extend_from_slice(&1u16.to_le_bytes()); wav.extend_from_slice(&1u16.to_le_bytes());
        wav.extend_from_slice(&16000u32.to_le_bytes()); wav.extend_from_slice(&32000u32.to_le_bytes());
        wav.extend_from_slice(&2u16.to_le_bytes()); wav.extend_from_slice(&16u16.to_le_bytes());
        wav.extend_from_slice(b"data"); wav.extend_from_slice(&4u32.to_le_bytes()); wav.extend_from_slice(&[0;4]); wav
    }
    #[test]
    fn validates_pcm_chunks_and_rejects_empty_truncated_or_inconsistent_wav() {
        assert!(validate_wav(&pcm_wav()).is_ok());
        let mut truncated = pcm_wav(); truncated.pop(); assert!(validate_wav(&truncated).is_err());
        let mut empty = pcm_wav(); empty.truncate(44); empty[4..8].copy_from_slice(&36u32.to_le_bytes()); empty[40..44].copy_from_slice(&0u32.to_le_bytes());
        assert!(validate_wav(&empty).is_err());
        let mut float = pcm_wav(); float[20..22].copy_from_slice(&3u16.to_le_bytes()); assert!(validate_wav(&float).is_err());
        let mut rate = pcm_wav(); rate[28..32].copy_from_slice(&1u32.to_le_bytes()); assert!(validate_wav(&rate).is_err());
        let mut oversized = pcm_wav(); oversized[40..44].copy_from_slice(&u32::MAX.to_le_bytes()); assert!(validate_wav(&oversized).is_err());
    }
    #[tokio::test]
    async fn rejects_credential_newlines_before_normalization_or_network() {
        let url = Url::parse("http://127.0.0.1:1/v1/").unwrap();
        for token in ["test-token\r\n", "\ntest-token", "Bearer test-token\n"] {
            assert!(matches!(transcribe(&Client::new(), url.clone(), token.into(), pcm_wav(), None, None).await,
                Err(ClientError::InvalidParams(_))));
        }
    }

    #[tokio::test]
    async fn transcribes_pcm_wav_using_cli_gateway_bearer_and_bounded_multipart() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = Url::parse(&format!("http://{}/v1/",listener.local_addr().unwrap())).unwrap();
        let server = tokio::spawn(async move {
            for response in [r#"{"data":[{"id":"dictionary-asr","type":"asr"}]}"#,r#"{"text":"识别成功"}"#] {
                let (mut stream, _) = listener.accept().await.unwrap();
                let mut bytes = Vec::new();
                loop {
                    let mut chunk=[0;8192];
                    let n=stream.read(&mut chunk).await.unwrap();
                    assert!(n>0); bytes.extend_from_slice(&chunk[..n]);
                    let text=String::from_utf8_lossy(&bytes);
                    if let Some(end)=text.find("\r\n\r\n") {
                        let length=text[..end].lines().find_map(|line| {let (k,v)=line.split_once(':')?; k.eq_ignore_ascii_case("content-length").then(||v.trim().parse::<usize>().unwrap())}).unwrap_or(0);
                        if bytes.len()>=end+4+length {break;}
                    }
                }
                let request=String::from_utf8_lossy(&bytes);
                assert!(request.to_lowercase().contains("authorization: bearer necli##test-token"));
                if request.starts_with("POST") {
                    assert!(request.starts_with("POST /v1/audio/transcriptions "));
                    assert!(request.contains("dictionary-asr"));
                    assert!(request.contains("audio/wav"));
                    assert!(request.contains("zh"));
                } else { assert!(request.starts_with("GET /v1/models ")); }
                stream.write_all(format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",response.len(),response).as_bytes()).await.unwrap();
            }
        });
        let wav=pcm_wav();
        assert_eq!(transcribe(&Client::new(),url,"test-token".into(),wav,None,Some("zh".into())).await.unwrap(),"识别成功");
        server.await.unwrap();
    }
}

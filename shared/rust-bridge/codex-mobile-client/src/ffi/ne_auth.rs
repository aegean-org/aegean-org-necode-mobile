//! NE Auth V2. Native platforms only supply secure storage and human verification UI.
use super::ClientError;
use base64::Engine;
use reqwest::{Client, RequestBuilder, Response, Url};
use rsa::{Pkcs1v15Encrypt, RsaPublicKey, pkcs8::DecodePublicKey};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::time::Duration;
use tokio::sync::Mutex;

const CLIENT_ID: &str = "necode-mobile";
const REFRESH_WINDOW_MS: i64 = 10_000;

#[uniffi::export(callback_interface)]
pub trait NeSessionStore: Send + Sync {
    fn read_session(&self) -> Result<Option<String>, ClientError>;
    /// Atomic compare-and-swap in Keychain / encrypted preferences. Never log these strings.
    fn replace_session(
        &self,
        expected: Option<String>,
        value: Option<String>,
    ) -> Result<bool, ClientError>;
}

#[derive(Clone, uniffi::Record)]
pub struct NeLogin {
    pub session_id: String,
    pub account_id: String,
}

#[derive(Clone, Deserialize, Serialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct NeCaptchaConfig {
    pub enabled: String,
    pub provider: Option<String>,
    pub region: Option<String>,
    pub prefix: Option<String>,
    pub scene_id: Option<String>,
}

#[derive(Deserialize)]
struct LoginConfig {
    public_key: String,
    captcha: NeCaptchaConfig,
}

#[derive(Clone, Deserialize, Serialize)]
struct TokenPair {
    token_type: String,
    access_token: String,
    refresh_token: String,
    expires_at: i64,
    session_expires_at: i64,
}

#[derive(Clone, Deserialize, Serialize)]
struct Session {
    version: u8,
    client_id: String,
    user_center: String,
    login_id: String,
    account_id: String,
    pair: TokenPair,
    request_id: Option<String>,
    started_at: Option<i64>,
    retry_at: Option<i64>,
    invalid: bool,
}

enum IdentityCheck {
    Verified(String),
    Rejected,
}

struct PendingSave {
    previous: String,
    session: Session,
}

pub(super) struct NeAuth {
    client: Client,
    url: Url,
    store: Box<dyn NeSessionStore>,
    // Serializes login, refresh, logout; retains a delivered pair if secure persistence fails.
    delivered: Mutex<Option<PendingSave>>,
}

impl NeAuth {
    pub fn new(client: Client, url: Url, store: Box<dyn NeSessionStore>) -> Self {
        Self {
            client,
            url,
            store,
            delivered: Mutex::new(None),
        }
    }

    async fn config(&self) -> Result<LoginConfig, ClientError> {
        let data = read_response(
            self.client
                .get(self.url.join("user/dict").map_err(url_error)?)
                .send()
                .await
                .map_err(network_error)?,
        )
        .await?;
        let config: LoginConfig = serde_json::from_value(success_data(&data)?.clone())
            .map_err(|_| error("登录配置不完整，请联系管理员"))?;
        validate_captcha(&config.captcha)?;
        Ok(config)
    }

    pub async fn captcha_config(&self) -> Result<NeCaptchaConfig, ClientError> {
        Ok(self.config().await?.captcha)
    }

    pub async fn login(
        &self,
        username: String,
        password: String,
        proof: Option<String>,
    ) -> Result<NeLogin, ClientError> {
        if username.trim().is_empty() || password.is_empty() {
            return Err(error("请输入 NE 账号和密码"));
        }
        if proof
            .as_ref()
            .is_some_and(|value| value.trim().is_empty() || value.len() >= 65536)
        {
            return Err(error("人机验证结果无效，请重新验证"));
        }
        let mut delivered = self.delivered.lock().await;
        let previous = self.store.read_session()?;
        let config = self.config().await?;
        if config.captcha.enabled == "1" && proof.as_ref().is_none_or(|s| s.trim().is_empty()) {
            return Err(error("请先完成人机验证，再登录"));
        }
        let password = encrypt_password(&password, &config.public_key)?;
        let mut form = vec![
            ("auth_version", "2"),
            ("client_id", CLIENT_ID),
            ("target", username.trim()),
            ("password", password.as_str()),
            ("expire", "2592000"),
        ];
        if let Some(ref proof) = proof {
            form.push(("captcha_verify_param", proof));
        }
        let response = read_response(
            self.client
                .post(self.url.join("user/passwordlogin").map_err(url_error)?)
                .form(&form)
                .send()
                .await
                .map_err(network_error)?,
        )
        .await?;
        let pair = parse_pair(success_data(&response)?, now_ms() / 1000)?;
        let account_id = match self.verify_access(&pair.access_token).await? {
            IdentityCheck::Verified(account) => account,
            IdentityCheck::Rejected => return Err(error("登录服务拒绝了新凭据，请重新登录")),
        };
        let session = Session {
            version: 2,
            client_id: CLIENT_ID.into(),
            user_center: self.url.to_string(),
            login_id: uuid::Uuid::new_v4().to_string(),
            account_id,
            pair,
            request_id: None,
            started_at: None,
            retry_at: None,
            invalid: false,
        };
        self.replace(previous, Some(&session))?;
        *delivered = None;
        Ok(login_record(&session))
    }

    async fn verify_access(&self, token: &str) -> Result<IdentityCheck, ClientError> {
        for path in ["user/verify", "user/info"] {
            let response = self
                .client
                .get(self.url.join(path).map_err(url_error)?)
                .bearer_auth(token)
                .send()
                .await
                .map_err(network_error)?;
            if response.status() == reqwest::StatusCode::UNAUTHORIZED {
                return Ok(IdentityCheck::Rejected);
            }
            let server_failure = response.status().is_server_error();
            let response = read_response(response).await?;
            if !server_failure
                && response["code"] == 2001
                && response["data"]["error"] != "client_upgrade_required"
                && response["msg"] != "client_upgrade_required"
            {
                return Ok(IdentityCheck::Rejected);
            }
            let data = success_data(&response)?;
            if path == "user/info" {
                return match &data["account_id"] {
                    Value::String(id) if !id.trim().is_empty() => {
                        Ok(IdentityCheck::Verified(id.clone()))
                    }
                    Value::Number(id) => Ok(IdentityCheck::Verified(id.to_string())),
                    _ => Err(error("登录服务未返回有效账号信息，请重新登录")),
                };
            }
        }
        unreachable!()
    }

    fn read(&self) -> Result<(String, Session), ClientError> {
        let raw = self
            .store
            .read_session()?
            .ok_or_else(|| error("登录已升级，请重新登录 NE 账号"))?;
        let session: Session =
            serde_json::from_str(&raw).map_err(|_| error("登录凭据无法读取，请重新登录"))?;
        if session.version != 2
            || session.client_id != CLIENT_ID
            || session.user_center != self.url.as_str()
        {
            return Err(error("登录凭据与当前服务不匹配，请重新登录"));
        }
        Ok((raw, session))
    }

    fn replace(
        &self,
        previous: Option<String>,
        session: Option<&Session>,
    ) -> Result<(), ClientError> {
        let next = session
            .map(serde_json::to_string)
            .transpose()
            .map_err(|_| error("无法序列化登录凭据"))?;
        if !self.store.replace_session(previous, next)? {
            return Err(error("登录状态已变化，请重新操作"));
        }
        Ok(())
    }

    pub async fn current_login(&self) -> Result<Option<NeLogin>, ClientError> {
        if self.store.read_session()?.is_none() {
            return Ok(None);
        }
        let (_, session) = self.read()?;
        self.access_token(&session.login_id, None).await?;
        Ok(Some(login_record(&session)))
    }

    pub async fn verify(&self, session_id: &str) -> Result<String, ClientError> {
        let token = self.access_token(session_id, None).await?;
        let identity = self.verify_access(&token).await?;
        let account = match identity {
            IdentityCheck::Verified(account) => account,
            IdentityCheck::Rejected => {
                let refreshed = self.access_token(session_id, Some(&token)).await?;
                match self.verify_access(&refreshed).await? {
                    IdentityCheck::Verified(account) => account,
                    IdentityCheck::Rejected => return Err(error("登录已失效，请重新登录")),
                }
            }
        };
        let (_, session) = self.read()?;
        require_session(&session, session_id, now_ms())?;
        if account != session.account_id {
            return Err(error("账号已变更，请重新登录"));
        }
        Ok(account)
    }

    pub async fn access_token(
        &self,
        session_id: &str,
        rejected: Option<&str>,
    ) -> Result<String, ClientError> {
        let mut delivered = self.delivered.lock().await;
        let (mut raw, mut session) = self.read()?;
        require_session(&session, session_id, now_ms())?;
        if let Some(pending) = delivered.as_ref() {
            if pending.previous == raw {
                self.replace(Some(raw), Some(&pending.session))?;
                (raw, session) = self.read()?;
            }
            *delivered = None;
        }
        // Another bridge may replace the account between secure-store CAS and the re-read.
        require_session(&session, session_id, now_ms())?;
        let now = now_ms();
        if session.request_id.is_none()
            && session.pair.expires_at * 1000 > now + 30_000
            && rejected != Some(session.pair.access_token.as_str())
        {
            return Ok(session.pair.access_token);
        }
        if session.retry_at.is_some_and(|time| time > now) {
            return Err(error("登录服务请求频繁，请稍后重试"));
        }
        if session.request_id.is_none() {
            session.request_id = Some(uuid::Uuid::new_v4().to_string());
            session.started_at = Some(now);
            self.replace(Some(raw), Some(&session))?;
            raw = serde_json::to_string(&session).map_err(|_| error("无法保存刷新状态"))?;
        }
        let remaining = refresh_remaining(&session, now)?;
        let response = read_response(
            self.client
                .post(self.url.join("user/token").map_err(url_error)?)
                .timeout(Duration::from_millis(remaining.min(4000) as u64))
                .form(&[
                    ("grant_type", "refresh_token"),
                    ("client_id", CLIENT_ID),
                    ("refresh_token", session.pair.refresh_token.as_str()),
                    (
                        "request_id",
                        session.request_id.as_deref().expect("persisted request id"),
                    ),
                ])
                .send()
                .await
                .map_err(network_error)?,
        )
        .await?;
        if response["code"] != 0 {
            let kind = response["data"]["error"].as_str().unwrap_or("");
            if response["code"] == 2001 && kind == "invalid_grant" {
                session.invalid = true;
                self.replace(Some(raw), Some(&session))?;
            } else if response["code"] == 2000 && kind == "slow_down" {
                if let Some(delay) = response["data"]["retry_after"]
                    .as_f64()
                    .filter(|v| v.is_finite() && *v > 0.0 && *v < 86400.0)
                {
                    // A definitive rejection did not consume the refresh token.
                    session.retry_at = Some(now_ms() + (delay * 1000.0).ceil() as i64);
                    session.request_id = None;
                    session.started_at = None;
                    self.replace(Some(raw), Some(&session))?;
                }
            }
            return Err(protocol_error(&response));
        }
        let pair = parse_pair(success_data(&response)?, now_ms() / 1000)?;
        if pair.session_expires_at != session.pair.session_expires_at {
            return Err(error("登录服务返回了异常的会话期限，请重新登录"));
        }
        session.pair = pair;
        session.request_id = None;
        session.started_at = None;
        session.retry_at = None;
        // Keep the new pair before attempting native storage; never rotate the old token again.
        *delivered = Some(PendingSave {
            previous: raw.clone(),
            session: session.clone(),
        });
        self.replace(Some(raw), Some(&session))?;
        *delivered = None;
        Ok(session.pair.access_token)
    }

    pub async fn send(
        &self,
        session_id: &str,
        request: RequestBuilder,
    ) -> Result<Response, ClientError> {
        let retry = request.try_clone();
        let token = self.access_token(session_id, None).await?;
        let response = request
            .bearer_auth(&token)
            .send()
            .await
            .map_err(network_error)?;
        // Connect authenticates before admitting a write. Never replay timeout/5xx/ambiguous failures.
        if response.status() != reqwest::StatusCode::UNAUTHORIZED {
            return Ok(response);
        }
        let token = self.access_token(session_id, Some(&token)).await?;
        retry
            .ok_or_else(|| error("登录已刷新，请重新操作"))?
            .bearer_auth(token)
            .send()
            .await
            .map_err(network_error)
    }

    pub async fn logout(&self, session_id: &str) -> Result<(), ClientError> {
        let mut delivered = self.delivered.lock().await;
        if self.store.read_session()?.is_none() {
            *delivered = None;
            return Ok(());
        }
        let (raw, session) = self.read()?;
        if session.login_id != session_id {
            return Err(error("账号已变更，请重新操作"));
        }
        let session = delivered
            .as_ref()
            .filter(|pending| pending.previous == raw)
            .map(|pending| &pending.session)
            .unwrap_or(&session);
        let response = read_response(
            self.client
                .post(self.url.join("user/sessionlogout").map_err(url_error)?)
                .form(&[
                    ("client_id", CLIENT_ID),
                    ("refresh_token", session.pair.refresh_token.as_str()),
                ])
                .send()
                .await
                .map_err(network_error)?,
        )
        .await?;
        if response["code"] != 0
            && !(response["code"] == 2001 && response["data"]["error"] == "invalid_grant")
        {
            return Err(protocol_error(&response));
        }
        self.replace(Some(raw), None)?;
        *delivered = None;
        Ok(())
    }
}

fn login_record(session: &Session) -> NeLogin {
    NeLogin {
        session_id: session.login_id.clone(),
        account_id: session.account_id.clone(),
    }
}

fn require_session(session: &Session, id: &str, now: i64) -> Result<(), ClientError> {
    if id.is_empty()
        || session.login_id != id
        || session.invalid
        || session.account_id.is_empty()
        || session.pair.token_type != "Bearer"
        || session.pair.access_token.is_empty()
        || session.pair.refresh_token.is_empty()
        || session.pair.expires_at <= 0
        || session.pair.session_expires_at > i64::MAX / 1000
        || session.pair.expires_at > session.pair.session_expires_at
        || session.pair.session_expires_at <= now / 1000
    {
        return Err(error("登录已失效或账号已变更，请重新登录"));
    }
    Ok(())
}

fn refresh_remaining(session: &Session, now: i64) -> Result<i64, ClientError> {
    let started = session
        .started_at
        .ok_or_else(|| error("刷新状态异常，请重新登录"))?;
    let remaining = REFRESH_WINDOW_MS - now.saturating_sub(started);
    if now < started || remaining <= 0 {
        return Err(error("上次登录刷新结果未确认，请重新登录"));
    }
    Ok(remaining)
}

fn parse_pair(value: &Value, now: i64) -> Result<TokenPair, ClientError> {
    let pair: TokenPair = serde_json::from_value(value.clone())
        .map_err(|_| error("登录服务未返回 V2 凭据，请联系管理员"))?;
    if pair.token_type != "Bearer"
        || pair.access_token.trim().is_empty()
        || pair.refresh_token.trim().is_empty()
        || pair.expires_at <= now
        || pair.session_expires_at < pair.expires_at
        || pair.session_expires_at > i64::MAX / 1000
    {
        return Err(error("登录服务返回了无效的 V2 凭据，请重新登录"));
    }
    Ok(pair)
}

fn validate_captcha(config: &NeCaptchaConfig) -> Result<(), ClientError> {
    if config.enabled == "0" {
        return Ok(());
    }
    if config.enabled == "1"
        && config.provider.as_deref() == Some("aliyun")
        && [&config.region, &config.prefix, &config.scene_id]
            .iter()
            .all(|v| v.as_ref().is_some_and(|s| !s.trim().is_empty()))
    {
        return Ok(());
    }
    Err(error("人机验证配置不完整，请联系管理员"))
}

pub(super) fn encrypt_password(password: &str, public_key: &str) -> Result<String, ClientError> {
    let key =
        RsaPublicKey::from_public_key_pem(public_key).map_err(|_| error("登录加密公钥无效"))?;
    let bytes = key
        .encrypt(&mut rand_core::OsRng, Pkcs1v15Encrypt, password.as_bytes())
        .map_err(|_| error("密码加密失败"))?;
    Ok(base64::engine::general_purpose::STANDARD.encode(bytes))
}

async fn read_response(mut response: Response) -> Result<Value, ClientError> {
    let status = response.status();
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(network_error)? {
        if bytes.len() + chunk.len() > 1024 * 1024 {
            return Err(error("登录服务响应过大"));
        }
        bytes.extend_from_slice(&chunk);
    }
    let value: Value =
        serde_json::from_slice(&bytes).map_err(|_| error("登录服务响应格式异常，请稍后重试"))?;
    if !status.is_success() && value["code"].as_i64().is_none_or(|code| code == 0) {
        return Err(error(&format!(
            "登录服务返回 HTTP {}，请稍后重试",
            status.as_u16()
        )));
    }
    Ok(value)
}

fn success_data(value: &Value) -> Result<&Value, ClientError> {
    if value["code"] != 0 {
        return Err(protocol_error(value));
    }
    Ok(&value["data"])
}

fn protocol_error(value: &Value) -> ClientError {
    error(match value["data"]["error"].as_str() {
        Some("invalid_grant") => "登录已失效，请重新登录",
        Some("slow_down") => "登录服务请求频繁，请稍后重试",
        Some("invalid_client") => "移动端登录未启用，请管理员检查 necode-mobile 配置",
        Some("invalid_origin") => "登录来源未被允许，请联系管理员",
        Some("invalid_request" | "unsupported_grant_type") => {
            "登录请求与服务不兼容，请升级客户端或联系管理员"
        }
        Some("temporarily_unavailable") => "登录服务暂不可用，请稍后重试",
        _ => "登录未成功，请检查账号、密码和人机验证后重试；已有凭据已保留",
    })
}

fn now_ms() -> i64 {
    chrono::Utc::now().timestamp_millis()
}
fn error(message: &str) -> ClientError {
    ClientError::Rpc(message.into())
}
fn url_error(_: url::ParseError) -> ClientError {
    error("登录服务地址无效")
}
fn network_error(error: reqwest::Error) -> ClientError {
    ClientError::Transport(format!(
        "与登录服务通信失败，请稍后重试（{}）",
        error.without_url()
    ))
}

/// The page receives only public configuration, never credentials. Its result remains untrusted.
#[uniffi::export]
pub fn ne_captcha_html(config: NeCaptchaConfig) -> Result<String, ClientError> {
    validate_captcha(&config)?;
    if config.enabled != "1" {
        return Err(error("当前登录不需要人机验证"));
    }
    let config = serde_json::to_string(&config)
        .map_err(|_| error("无法加载验证配置"))?
        .replace('<', "\\u003c")
        .replace('>', "\\u003e")
        .replace('&', "\\u0026");
    Ok(include_str!("ne_captcha.html").replace("__PUBLIC_CONFIG__", &config))
}

#[cfg(test)]
pub(super) mod tests {
    use super::*;
    use serde_json::json;
    use std::sync::{
        Arc, Mutex as StdMutex,
        atomic::{AtomicBool, Ordering},
    };
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    #[derive(Clone, Default)]
    pub struct MemoryStore {
        value: Arc<StdMutex<Option<String>>>,
        fail_delivery: Arc<AtomicBool>,
        switch_after_save: Arc<AtomicBool>,
    }
    impl NeSessionStore for MemoryStore {
        fn read_session(&self) -> Result<Option<String>, ClientError> {
            Ok(self.value.lock().unwrap().clone())
        }
        fn replace_session(
            &self,
            expected: Option<String>,
            value: Option<String>,
        ) -> Result<bool, ClientError> {
            let mut saved = self.value.lock().unwrap();
            if *saved != expected {
                return Ok(false);
            }
            if value.as_ref().is_some_and(|v| v.contains("new-access"))
                && self.fail_delivery.load(Ordering::SeqCst)
            {
                return Err(error("storage unavailable"));
            }
            *saved = value;
            if self.switch_after_save.load(Ordering::SeqCst) {
                let mut changed: Session = serde_json::from_str(saved.as_deref().unwrap()).unwrap();
                changed.login_id = "another-login".into();
                *saved = Some(serde_json::to_string(&changed).unwrap());
            }
            Ok(true)
        }
    }

    static SESSION_END: std::sync::LazyLock<i64> =
        std::sync::LazyLock::new(|| now_ms() / 1000 + 7200);
    fn pair(expiry: i64) -> Value {
        json!({"token_type":"Bearer", "access_token":"new-access", "refresh_token":"new-refresh",
            "expires_at":expiry, "session_expires_at":*SESSION_END})
    }
    fn session(url: &str, expiry: i64) -> Session {
        Session {
            version: 2,
            client_id: CLIENT_ID.into(),
            user_center: url.into(),
            login_id: "test".into(),
            account_id: "account".into(),
            pair: TokenPair {
                token_type: "Bearer".into(),
                access_token: "old-access".into(),
                refresh_token: "old-refresh".into(),
                expires_at: expiry,
                session_expires_at: *SESSION_END,
            },
            request_id: None,
            started_at: None,
            retry_at: None,
            invalid: false,
        }
    }
    pub fn test_store(url: &str) -> Box<dyn NeSessionStore> {
        Box::new(saved(&session(
            Url::parse(url).unwrap().as_str(),
            now_ms() / 1000 + 3600,
        )))
    }
    fn saved(session: &Session) -> MemoryStore {
        MemoryStore {
            value: Arc::new(StdMutex::new(Some(serde_json::to_string(session).unwrap()))),
            ..Default::default()
        }
    }
    fn auth(url: &str, store: MemoryStore) -> NeAuth {
        NeAuth::new(
            Client::builder()
                .redirect(reqwest::redirect::Policy::none())
                .timeout(Duration::from_secs(2))
                .build()
                .unwrap(),
            Url::parse(url).unwrap(),
            Box::new(store),
        )
    }
    async fn server(
        responses: Vec<(u16, Value)>,
    ) -> (String, tokio::task::JoinHandle<Vec<String>>) {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}/", listener.local_addr().unwrap());
        let task = tokio::spawn(async move {
            let mut requests = Vec::new();
            for (status, body) in responses {
                let (mut socket, _) =
                    tokio::time::timeout(Duration::from_secs(5), listener.accept())
                        .await
                        .unwrap()
                        .unwrap();
                let mut bytes = Vec::new();
                loop {
                    let mut buffer = [0; 2048];
                    let n = socket.read(&mut buffer).await.unwrap();
                    if n == 0 {
                        break;
                    }
                    bytes.extend_from_slice(&buffer[..n]);
                    if let Some(end) = bytes.windows(4).position(|w| w == b"\r\n\r\n") {
                        let headers = String::from_utf8_lossy(&bytes[..end]).to_lowercase();
                        let length = headers
                            .lines()
                            .find_map(|l| l.strip_prefix("content-length: "))
                            .unwrap_or("0")
                            .parse::<usize>()
                            .unwrap();
                        if bytes.len() >= end + 4 + length {
                            break;
                        }
                    }
                }
                requests.push(String::from_utf8(bytes).unwrap());
                let body = body.to_string();
                socket.write_all(format!("HTTP/1.1 {status} Test\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len()).as_bytes()).await.unwrap();
            }
            requests
        });
        (url, task)
    }

    #[test]
    fn rejects_v1_and_malformed_pairs_and_captcha() {
        assert!(parse_pair(&json!({"token":"v1"}), 1).is_err());
        let mut data = pair(100);
        assert!(parse_pair(&data, 1).is_ok());
        data["token_type"] = json!("Basic");
        assert!(parse_pair(&data, 1).is_err());
        data["token_type"] = json!("Bearer");
        data["refresh_token"] = json!("");
        assert!(parse_pair(&data, 1).is_err());
        assert!(parse_pair(&pair(100), 100).is_err());
        for value in [
            json!({"enabled":"yes"}),
            json!({"enabled":"1","provider":"aliyun"}),
        ] {
            assert!(validate_captcha(&serde_json::from_value(value).unwrap()).is_err());
        }
        assert!(validate_captcha(&serde_json::from_value(json!({"enabled":"0"})).unwrap()).is_ok());
    }

    #[tokio::test]
    async fn password_login_is_v2_and_identity_uses_only_bearer() {
        use rsa::pkcs8::EncodePublicKey;
        let private = rsa::RsaPrivateKey::new(&mut rand_core::OsRng, 1024).unwrap();
        let public = private
            .to_public_key()
            .to_public_key_pem(Default::default())
            .unwrap();
        let (url, server) = server(vec![
            (
                200,
                json!({"code":0,"data":{"public_key":public,"captcha":{"enabled":"0"}}}),
            ),
            (200, json!({"code":0,"data":pair(now_ms()/1000+3600)})),
            (200, json!({"code":0,"data":{}})),
            (200, json!({"code":0,"data":{"account_id":123}})),
        ])
        .await;
        let store = MemoryStore::default();
        let auth = auth(&url, store.clone());
        let login = auth
            .login(" user ".into(), "secret".into(), None)
            .await
            .unwrap();
        assert_eq!(login.account_id, "123");
        assert_ne!(login.session_id, "new-access");
        let requests = server.await.unwrap();
        let form: std::collections::HashMap<_, _> =
            url::form_urlencoded::parse(requests[1].split("\r\n\r\n").nth(1).unwrap().as_bytes())
                .into_owned()
                .collect();
        assert_eq!(form["auth_version"], "2");
        assert_eq!(form["client_id"], "necode-mobile");
        assert_eq!(form["target"], "user");
        let decrypted = private
            .decrypt(
                Pkcs1v15Encrypt,
                &base64::engine::general_purpose::STANDARD
                    .decode(&form["password"])
                    .unwrap(),
            )
            .unwrap();
        assert_eq!(decrypted, b"secret");
        for request in &requests[2..] {
            assert!(
                request
                    .to_lowercase()
                    .contains("authorization: bearer new-access")
            );
            assert!(!request.contains("AUT") && !request.contains("token:"));
        }
        assert!(
            store
                .read_session()
                .unwrap()
                .unwrap()
                .contains("new-refresh")
        );
    }

    #[tokio::test]
    async fn concurrent_refresh_rotates_once_and_preserves_login_id() {
        let (url, server) = server(vec![(
            200,
            json!({"code":0,"data":pair(now_ms()/1000+3600)}),
        )])
        .await;
        let store = saved(&session(&url, now_ms() / 1000));
        let auth = auth(&url, store.clone());
        let (a, b) = tokio::join!(
            auth.access_token("test", None),
            auth.access_token("test", None)
        );
        assert_eq!(a.unwrap(), "new-access");
        assert_eq!(b.unwrap(), "new-access");
        let requests = server.await.unwrap();
        assert_eq!(requests.len(), 1);
        assert!(requests[0].contains("client_id=necode-mobile"));
        assert!(requests[0].contains("request_id="));
        let data: Session = serde_json::from_str(&store.read_session().unwrap().unwrap()).unwrap();
        assert_eq!(data.login_id, "test");
        assert!(data.request_id.is_none());
    }

    #[tokio::test]
    async fn restart_reuses_pending_request_only_within_window() {
        let (url, server) = server(vec![(
            200,
            json!({"code":0,"data":pair(now_ms()/1000+3600)}),
        )])
        .await;
        let mut session = session(&url, now_ms() / 1000 + 1000);
        session.request_id = Some("same-request".into());
        session.started_at = Some(now_ms() - 1000);
        let auth = auth(&url, saved(&session));
        assert_eq!(auth.access_token("test", None).await.unwrap(), "new-access");
        assert!(server.await.unwrap()[0].contains("request_id=same-request"));
        session.started_at = Some(now_ms() - 10_001);
        assert!(refresh_remaining(&session, now_ms()).is_err());
        session.started_at = Some(now_ms() + 1000);
        assert!(refresh_remaining(&session, now_ms()).is_err());
    }

    #[tokio::test]
    async fn failed_persistence_retries_saving_not_refreshing() {
        let (url, server) = server(vec![(
            200,
            json!({"code":0,"data":pair(now_ms()/1000+3600)}),
        )])
        .await;
        let store = saved(&session(&url, now_ms() / 1000));
        store.fail_delivery.store(true, Ordering::SeqCst);
        let auth = auth(&url, store.clone());
        assert!(auth.access_token("test", None).await.is_err());
        assert!(
            store
                .read_session()
                .unwrap()
                .unwrap()
                .contains("request_id\":\"")
        );
        store.fail_delivery.store(false, Ordering::SeqCst);
        assert_eq!(auth.access_token("test", None).await.unwrap(), "new-access");
        assert_eq!(server.await.unwrap().len(), 1);
    }

    #[tokio::test]
    async fn account_switch_during_save_retry_cannot_return_the_new_accounts_token() {
        let (url, server) = server(vec![(
            200,
            json!({"code":0,"data":pair(now_ms()/1000+3600)}),
        )])
        .await;
        let store = saved(&session(&url, now_ms() / 1000));
        store.fail_delivery.store(true, Ordering::SeqCst);
        let auth = auth(&url, store.clone());
        assert!(auth.access_token("test", None).await.is_err());
        store.fail_delivery.store(false, Ordering::SeqCst);
        store.switch_after_save.store(true, Ordering::SeqCst);
        assert!(auth.access_token("test", None).await.is_err());
        assert_eq!(server.await.unwrap().len(), 1);
    }

    #[tokio::test]
    async fn invalid_grant_blocks_refresh_but_slow_down_keeps_session() {
        for (code, kind) in [(2001, "invalid_grant"), (2000, "slow_down")] {
            let (url, server) = server(vec![(
                200,
                json!({"code":code,"data":{"error":kind,"retry_after":60}}),
            )])
            .await;
            let store = saved(&session(&url, now_ms() / 1000));
            let auth = auth(&url, store.clone());
            assert!(auth.access_token("test", None).await.is_err());
            assert!(auth.access_token("test", None).await.is_err());
            let data: Session =
                serde_json::from_str(&store.read_session().unwrap().unwrap()).unwrap();
            assert_eq!(data.invalid, kind == "invalid_grant");
            assert_eq!(data.pair.refresh_token, "old-refresh");
            assert_eq!(server.await.unwrap().len(), 1);
        }
    }

    #[tokio::test]
    async fn refuses_stale_account_wrong_client_and_extended_expiry() {
        let mut data = pair(now_ms() / 1000 + 3600);
        data["session_expires_at"] = json!(now_ms() / 1000 + 9000);
        let (url, server) = server(vec![(200, json!({"code":0,"data":data}))]).await;
        let store = saved(&session(&url, now_ms() / 1000));
        let auth = auth(&url, store.clone());
        assert!(auth.access_token("other-login", None).await.is_err());
        assert!(auth.access_token("test", None).await.is_err());
        assert_eq!(server.await.unwrap().len(), 1);
        let mut session = session(&url, now_ms() / 1000 + 3600);
        session.client_id = "necode-pc".into();
        *store.value.lock().unwrap() = Some(serde_json::to_string(&session).unwrap());
        assert!(auth.access_token("test", None).await.is_err());
    }

    #[tokio::test]
    async fn retries_only_401_not_business_server_failure() {
        let (url, server) = server(vec![
            (401, json!({})),
            (200, json!({"code":0,"data":pair(now_ms()/1000+3600)})),
            (200, json!({"ok":true})),
            (503, json!({})),
        ])
        .await;
        let auth = auth(&url, saved(&session(&url, now_ms() / 1000 + 3600)));
        assert_eq!(
            auth.send("test", auth.client.post(&url).body("business-write"))
                .await
                .unwrap()
                .status(),
            200
        );
        assert_eq!(
            auth.send("test", auth.client.post(&url).body("second-write"))
                .await
                .unwrap()
                .status(),
            503
        );
        let requests = server.await.unwrap();
        assert_eq!(requests.len(), 4);
        assert!(
            requests[2]
                .to_lowercase()
                .contains("authorization: bearer new-access")
        );
    }

    #[tokio::test]
    async fn uc_identity_rejection_refreshes_once_before_restore() {
        let (url, server) = server(vec![
            (200, json!({"code":2001})),
            (200, json!({"code":0,"data":pair(now_ms()/1000+3600)})),
            (200, json!({"code":0,"data":{}})),
            (200, json!({"code":0,"data":{"account_id":"account"}})),
        ])
        .await;
        let auth = auth(&url, saved(&session(&url, now_ms() / 1000 + 3600)));
        assert_eq!(auth.verify("test").await.unwrap(), "account");
        let requests = server.await.unwrap();
        assert_eq!(requests.len(), 4);
        assert!(requests[1].starts_with("POST /user/token "));
        assert!(
            requests[2]
                .to_lowercase()
                .contains("authorization: bearer new-access")
        );
    }

    #[tokio::test]
    async fn logout_failure_does_not_clear_and_success_revokes_then_clears() {
        let (url, server) = server(vec![
            (
                503,
                json!({"code":3000,"data":{"error":"temporarily_unavailable"}}),
            ),
            (200, json!({"code":0})),
        ])
        .await;
        let store = saved(&session(&url, now_ms() / 1000 + 3600));
        let auth = auth(&url, store.clone());
        assert!(auth.logout("test").await.is_err());
        assert!(store.read_session().unwrap().is_some());
        auth.logout("test").await.unwrap();
        assert!(store.read_session().unwrap().is_none());
        let requests = server.await.unwrap();
        assert!(
            requests
                .iter()
                .all(|r| r.starts_with("POST /user/sessionlogout ")
                    && r.contains("client_id=necode-mobile")
                    && r.contains("refresh_token=old-refresh"))
        );
    }

    #[tokio::test]
    async fn ambiguous_failure_keeps_durable_request_id_for_retry() {
        let (url, server) = server(vec![
            (
                503,
                json!({"code":3000,"data":{"error":"temporarily_unavailable"}}),
            ),
            (200, json!({"code":0,"data":pair(now_ms()/1000+3600)})),
        ])
        .await;
        let store = saved(&session(&url, now_ms() / 1000));
        let first = auth(&url, store.clone());
        assert!(first.access_token("test", None).await.is_err());
        let pending: Session =
            serde_json::from_str(&store.read_session().unwrap().unwrap()).unwrap();
        assert!(pending.request_id.is_some());
        drop(first);
        let restarted = auth(&url, store);
        assert_eq!(
            restarted.access_token("test", None).await.unwrap(),
            "new-access"
        );
        let requests = server.await.unwrap();
        let id = pending.request_id.unwrap();
        assert!(
            requests
                .iter()
                .all(|r| r.contains(&format!("request_id={id}")))
        );
    }

    #[tokio::test]
    async fn expired_pending_refresh_never_sends_a_new_request() {
        let mut session = session("http://127.0.0.1:1/", now_ms() / 1000);
        session.request_id = Some("lost-response".into());
        session.started_at = Some(now_ms() - 11000);
        let store = saved(&session);
        let before = store.read_session().unwrap();
        let auth = auth(&session.user_center, store.clone());
        let err = auth.access_token("test", None).await.err().unwrap();
        assert!(matches!(err, ClientError::Rpc(text) if text.contains("未确认")));
        assert_eq!(before, store.read_session().unwrap());
    }

    #[tokio::test]
    async fn enabled_captcha_requires_proof_before_password_request() {
        let (url, server) = server(vec![(
            200,
            json!({"code":0,"data":{"public_key":"unused", "captcha":{
            "enabled":"1","provider":"aliyun","region":"cn","prefix":"prefix","sceneId":"scene"}}}),
        )])
        .await;
        let auth = auth(&url, MemoryStore::default());
        let err = auth
            .login("user".into(), "password".into(), None)
            .await
            .err()
            .unwrap();
        assert!(matches!(err, ClientError::Rpc(text) if text.contains("人机验证")));
        assert_eq!(server.await.unwrap().len(), 1);
    }

    #[test]
    fn captcha_config_cannot_inject_script_or_credentials() {
        let config = NeCaptchaConfig {
            enabled: "1".into(),
            provider: Some("aliyun".into()),
            region: Some("cn".into()),
            prefix: Some("</script><script>alert(1)</script>".into()),
            scene_id: Some("scene".into()),
        };
        let html = ne_captcha_html(config).unwrap();
        assert!(!html.contains("</script><script>alert"));
        assert!(!html.contains("refresh_token") && !html.contains("access_token"));
    }
}

use crate::ffi::ClientError;
use base64::Engine;
use iroh::{Endpoint, EndpointAddr, EndpointId, RelayConfig, RelayMap, RelayMode, RelayUrl};
use reqwest::{Client, Url};
use rsa::{Pkcs1v15Encrypt, RsaPublicKey, pkcs8::DecodePublicKey};
use serde::Deserialize;
use serde_json::{Value, json};
use std::str::FromStr;
use std::sync::{
    Arc, Mutex,
    atomic::{AtomicBool, AtomicI64, Ordering},
};
use std::time::Duration;
#[cfg(test)]
use tokio::sync::oneshot;
use tokio::sync::watch;
use tokio::task::JoinHandle;
use tokio::time::timeout;

const DESKTOP_ALPN: &[u8] = b"necode-desktop/1";
const DESKTOP_REQUEST_TIMEOUT_MESSAGE: &str = "电脑响应超时，操作结果暂未确认，请刷新会话核对";

#[derive(Debug, Clone, uniffi::Record)]
pub struct DesktopHost {
    pub id: String,
    pub name: String,
    pub endpoint_id: String,
    pub relay_url: Option<String>,
    pub status: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct NeLogin {
    pub token: String,
    pub account_id: String,
}

#[derive(Debug, Clone, Deserialize, uniffi::Record)]
pub struct MobileDevice {
    pub id: String,
    pub secret: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DesktopConnection {
    pub desktop_id: String,
    pub mobile_endpoint_id: String,
    pub expires_at: i64,
    pub healthy: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DesktopResponse {
    pub status: u16,
    pub body_json: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopLease {
    id: String,
    ticket: String,
    desktop_id: String,
    endpoint_id: String,
    relay_url: Option<String>,
    expires_at: i64,
}

#[derive(Debug, Clone, Deserialize, uniffi::Record)]
pub struct DesktopProjectFolder {
    pub key: String,
    pub id: String,
    pub name: String,
    pub directory: String,
}

#[derive(Debug, Clone, Deserialize, uniffi::Record)]
pub struct DesktopDirectoryEntry {
    pub name: String,
    pub path: String,
}

#[derive(Debug, Clone, Deserialize, uniffi::Record)]
pub struct DesktopDirectoryListing {
    pub path: String,
    pub parent: Option<String>,
    pub roots: Vec<String>,
    pub directories: Vec<DesktopDirectoryEntry>,
}

struct ActiveDesktop {
    generation: u64,
    lease: DesktopLease,
    endpoint: Endpoint,
    connection: iroh::endpoint::Connection,
    mobile_endpoint_id: String,
    healthy: Arc<AtomicBool>,
    expires_at: Arc<AtomicI64>,
    renewal: JoinHandle<()>,
    token: String,
    mobile: MobileDevice,
}

type ConnectResult = Result<Option<DesktopConnection>, ClientError>;

#[derive(Default)]
struct DesktopState {
    generation: u64,
    intent: u64,
    recovery_suspended: bool,
    preferred: Option<(String, String)>,
    active: Option<ActiveDesktop>,
    pending: Option<PendingConnect>,
}

struct PendingConnect {
    generation: u64,
    desktop_id: Option<String>,
    token: String,
    mobile: MobileDevice,
    waiters: usize,
    cancel: watch::Sender<bool>,
    done: watch::Receiver<Option<ConnectResult>>,
}

// A page leaving must not cancel another page's joined recovery. Only the last
// waiter cancels acquisition; its worker still owns any late lease cleanup.
struct ConnectCancellation {
    state: Arc<Mutex<DesktopState>>,
    generation: u64,
}

impl Drop for ConnectCancellation {
    fn drop(&mut self) {
        let mut state = self.state.lock().expect("Desktop state poisoned");
        if let Some(pending) = state.pending.as_mut()
            && pending.generation == self.generation
        {
            pending.waiters -= 1;
            if pending.waiters == 0 {
                pending.cancel.send_replace(true);
            }
        }
    }
}

#[derive(uniffi::Object)]
pub struct DesktopConnectBridge {
    uc_url: Url,
    connect_url: Url,
    client: Client,
    state: Arc<Mutex<DesktopState>>,
}

#[derive(Deserialize)]
struct NeResult {
    code: serde_json::Value,
    data: Option<serde_json::Value>,
}

#[derive(Deserialize)]
struct TokenData {
    token: String,
}

#[derive(Deserialize)]
struct PublicKeyData {
    public_key: String,
}

#[derive(Deserialize)]
struct AccountData {
    account_id: serde_json::Value,
}

#[derive(Deserialize)]
struct DesktopList {
    desktops: Vec<DesktopHostWire>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopHostWire {
    id: String,
    name: String,
    endpoint_id: String,
    relay_url: Option<String>,
    status: String,
}

#[uniffi::export(async_runtime = "tokio")]
impl DesktopConnectBridge {
    #[uniffi::constructor]
    pub fn new(uc_url: String, connect_url: String) -> Result<Self, ClientError> {
        let uc_url = service_url(&uc_url)?;
        let connect_url = service_url(&connect_url)?;
        Ok(Self {
            uc_url,
            connect_url,
            client: Client::builder()
                .timeout(std::time::Duration::from_secs(10))
                .build()
                .map_err(|e| ClientError::Transport(e.to_string()))?,
            state: Arc::new(Mutex::new(DesktopState::default())),
        })
    }

    pub async fn login_with_password(
        &self,
        username: String,
        password: String,
    ) -> Result<NeLogin, ClientError> {
        if username.trim().is_empty() || password.is_empty() {
            return Err(ClientError::InvalidParams(
                "请输入 NE 账号和密码".into(),
            ));
        }
        let response = self
            .client
            .get(self.uc_url.join("user/dict").map_err(url_error)?)
            .send()
            .await
            .map_err(transport_error)?;
        let key: PublicKeyData = ne_data(response)
            .await?
            .ok_or_else(|| ClientError::Serialization("NE public key missing".into()))?;
        let password = encrypt_ne_password(&password, &key.public_key)?;
        let response = self
            .client
            .post(self.uc_url.join("user/passwordlogin").map_err(url_error)?)
            .form(&[
                ("target", username.trim()),
                ("password", password.as_str()),
                ("expire", "2592000"),
            ])
            .send()
            .await
            .map_err(transport_error)?;
        let token: TokenData = ne_data(response)
            .await?
            .ok_or_else(|| ClientError::Rpc("登录服务未返回有效凭据，请重新登录；若仍失败，请联系管理员".into()))?;
        if token.token.trim().is_empty() {
            return Err(ClientError::Rpc("登录服务未返回有效凭据，请重新登录；若仍失败，请联系管理员".into()));
        }
        let account_id = self.verify_login(token.token.clone()).await?;
        Ok(NeLogin {
            token: token.token,
            account_id,
        })
    }
    pub async fn verify_login(&self, token: String) -> Result<String, ClientError> {
        if token.trim().is_empty() {
            return Err(ClientError::InvalidParams("请先登录 NE 账号，再连接电脑".into()));
        }
        for path in ["user/verify", "user/info"] {
            let mut url = self.uc_url.join(path).map_err(url_error)?;
            url.query_pairs_mut().append_pair("AUT", &token);
            let response = self
                .client
                .get(url)
                .header("AUT", &token)
                .header("token", &token)
                .send()
                .await
                .map_err(transport_error)?;
            if path == "user/info" {
                let data: AccountData = ne_data(response)
                    .await?
                    .ok_or_else(|| ClientError::Rpc("登录服务未返回有效账号信息，请重新登录；若仍失败，请联系管理员".into()))?;
                return match data.account_id {
                    serde_json::Value::String(id) if !id.trim().is_empty() => Ok(id),
                    serde_json::Value::Number(id) => Ok(id.to_string()),
                    _ => Err(ClientError::Rpc("登录服务未返回有效账号信息，请重新登录；若仍失败，请联系管理员".into())),
                };
            }
            ne_data::<serde_json::Value>(response).await?;
        }
        Err(ClientError::Rpc("暂时无法获取账号信息，请重新登录".into()))
    }

    pub async fn register_mobile(
        &self,
        token: String,
        saved_id: Option<String>,
        saved_secret: Option<String>,
    ) -> Result<MobileDevice, ClientError> {
        if token.trim().is_empty() || saved_id.is_some() != saved_secret.is_some() {
            return Err(ClientError::InvalidParams(
                "NE login and matching saved mobile credentials required".into(),
            ));
        }
        let body = match (saved_id, saved_secret) {
            (Some(id), Some(secret)) if !id.is_empty() && !secret.is_empty() => {
                json!({"id": id, "secret": secret})
            }
            (None, None) => json!({}),
            _ => {
                return Err(ClientError::InvalidParams(
                    "Invalid saved mobile identity".into(),
                ));
            }
        };
        let response = self
            .client
            .post(self.connect_url.join("v1/mobiles").map_err(url_error)?)
            .bearer_auth(token)
            .json(&body)
            .send()
            .await
            .map_err(transport_error)?;
        connect_data(response).await
    }

    pub async fn connect_desktop(
        &self,
        token: String,
        mobile: MobileDevice,
        desktop_id: String,
    ) -> Result<DesktopConnection, ClientError> {
        self.connect_or_resume(token, mobile, Some(desktop_id), None, false)
            .await?
            .ok_or_else(|| ClientError::Rpc("这台电脑暂时无法连接，请确认电脑端 NeCode 已打开并保持联网".into()))
    }

    pub async fn resume_desktop(
        &self,
        token: String,
        mobile: MobileDevice,
    ) -> Result<Option<DesktopConnection>, ClientError> {
        self.connect_or_resume(token, mobile, None, None, false)
            .await
    }

    /// Automatic recovery never overrides a newer manual selection or disconnect.
    pub async fn recover_desktop(
        &self,
        token: String,
        mobile: MobileDevice,
        preferred_desktop_id: Option<String>,
        force_reconnect: bool,
    ) -> Result<Option<DesktopConnection>, ClientError> {
        self.connect_or_resume(token, mobile, None, preferred_desktop_id, force_reconnect)
            .await
    }

    pub async fn active_desktop(&self) -> Option<DesktopConnection> {
        self.state
            .lock()
            .expect("Desktop state poisoned")
            .active
            .as_ref()
            .map(connection_info)
    }

    pub async fn request_desktop(
        &self,
        method: String,
        path: String,
        body_json: String,
    ) -> Result<DesktopResponse, ClientError> {
        self.request_desktop_owned(None, method, path, body_json)
            .await
    }

    pub async fn request_desktop_for(
        &self,
        desktop_id: String,
        method: String,
        path: String,
        body_json: String,
    ) -> Result<DesktopResponse, ClientError> {
        if desktop_id.is_empty() {
            return Err(ClientError::InvalidParams("请求缺少电脑身份".into()));
        }
        self.request_desktop_owned(Some(desktop_id), method, path, body_json)
            .await
    }

    pub async fn desktop_projects(&self, desktop_id: String) -> Result<Vec<DesktopProjectFolder>, ClientError> {
        let response = self.request_desktop_for(desktop_id, "GET".into(), "/connect/projects".into(), "null".into()).await?;
        decode_directory_response(response)
    }

    pub async fn browse_desktop_directory(&self, desktop_id: String, path: Option<String>) -> Result<DesktopDirectoryListing, ClientError> {
        let query = path.map(|value| format!("?{}", url::form_urlencoded::Serializer::new(String::new()).append_pair("path", &value).finish())).unwrap_or_default();
        let response = self.request_desktop_for(desktop_id, "GET".into(), format!("/connect/directories{query}"), "null".into()).await?;
        decode_directory_response(response)
    }

    pub async fn create_desktop_directory(&self, desktop_id: String, parent: String, name: String) -> Result<String, ClientError> {
        #[derive(Deserialize)]
        struct Created { path: String }
        let response = self.request_desktop_for(desktop_id, "POST".into(), "/connect/directories".into(), json!({"parent": parent, "name": name}).to_string()).await?;
        Ok(decode_directory_response::<Created>(response)?.path)
    }

    pub async fn add_desktop_project(&self, desktop_id: String, path: String) -> Result<DesktopProjectFolder, ClientError> {
        let response = self.request_desktop_for(desktop_id, "POST".into(), "/connect/projects".into(), json!({"path": path}).to_string()).await?;
        decode_directory_response(response)
    }

    pub async fn disconnect_desktop(&self) -> Result<(), ClientError> {
        let (mut done, closing) = {
            let mut state = self.state.lock().expect("Desktop state poisoned");
            state.intent += 1;
            state.generation += 1;
            state.recovery_suspended = true;
            if let Some(pending) = &state.pending {
                pending.cancel.send_replace(true);
                (pending.done.clone(), None)
            } else if let Some(active) = state.active.take() {
                active.healthy.store(false, Ordering::Release);
                active.connection.close(0u8.into(), b"disconnect");
                let (completed, done) = watch::channel(None);
                let (cancel, _) = watch::channel(true);
                let generation = state.generation;
                state.pending = Some(PendingConnect {
                    generation,
                    desktop_id: None,
                    token: active.token.clone(),
                    mobile: active.mobile.clone(),
                    waiters: 0,
                    cancel,
                    done: done.clone(),
                });
                (done, Some((active, completed, generation)))
            } else {
                return Ok(());
            }
        };
        if let Some((active, completed, generation)) = closing {
            let state = self.state.clone();
            let client = self.client.clone();
            let url = self.connect_url.clone();
            // Cleanup is worker-owned, even when the native caller leaves the page.
            tokio::spawn(async move {
                let result = close_desktop(&client, &url, active).await.map(|_| None);
                let mut state = state.lock().expect("Desktop state poisoned");
                if state
                    .pending
                    .as_ref()
                    .is_some_and(|p| p.generation == generation)
                {
                    state.pending = None;
                }
                completed.send_replace(Some(result));
            });
        }
        match wait_connect(&mut done).await {
            Err(error) if is_connect_cancelled(&error) => Ok(()),
            result => result.map(|_| ()),
        }
    }

    pub async fn list_desktops(&self, token: String) -> Result<Vec<DesktopHost>, ClientError> {
        if token.trim().is_empty() {
            return Err(ClientError::InvalidParams("请先登录 NE 账号，再连接电脑".into()));
        }
        let response = self
            .client
            .get(self.connect_url.join("v1/desktops").map_err(url_error)?)
            .bearer_auth(token)
            .send()
            .await
            .map_err(transport_error)?;
        let list: DesktopList = connect_data(response).await?;
        Ok(list
            .desktops
            .into_iter()
            .map(|host| DesktopHost {
                id: host.id,
                name: host.name,
                endpoint_id: host.endpoint_id,
                relay_url: host.relay_url,
                status: host.status,
            })
            .collect())
    }
}

impl DesktopConnectBridge {
    async fn request_desktop_owned(
        &self,
        desktop_id: Option<String>,
        method: String,
        path: String,
        body_json: String,
    ) -> Result<DesktopResponse, ClientError> {
        let path = if method == "GET" && path.split('?').next() == Some("/connect/sessions") {
            let query = session_page_query(&path)?;
            let mut url = Url::parse("http://localhost/connect/sessions").map_err(url_error)?;
            url.query_pairs_mut().extend_pairs(query);
            format!("/connect/sessions?{}", url.query().unwrap_or(""))
        } else if method == "GET"
            && path.split('?').next().is_some_and(|route| {
                route.starts_with("/connect/session/") && route.ends_with("/message")
            })
        {
            let mut url = Url::parse(&format!("http://localhost{path}")).map_err(url_error)?;
            let query: Vec<(String, String)> = url.query_pairs().into_owned().collect();
            let limit = query
                .iter()
                .find(|(key, _)| key == "limit")
                .map(|(_, value)| value.as_str())
                .unwrap_or("30")
                .parse::<usize>()
                .map_err(|_| ClientError::InvalidParams("Invalid message page size".into()))?;
            if query.iter().filter(|(key, _)| key == "limit").count() > 1
                || !(1..=100).contains(&limit)
            {
                return Err(ClientError::InvalidParams(
                    "Invalid message page size".into(),
                ));
            }
            if !query.iter().any(|(key, _)| key == "limit") {
                url.query_pairs_mut()
                    .append_pair("limit", &limit.to_string());
            }
            format!("{}?{}", url.path(), url.query().unwrap_or(""))
        } else {
            path
        };
        let request = desktop_request(&method, &path, &body_json)?;
        let (generation, connection, lease, healthy) = {
            let state = self.state.lock().expect("Desktop state poisoned");
            let active = state
                .active
                .as_ref()
                .ok_or_else(|| ClientError::Rpc("尚未连接电脑，请先选择一台在线电脑".into()))?;
            if desktop_id
                .as_ref()
                .is_some_and(|id| *id != active.lease.desktop_id)
            {
                return Err(ClientError::InvalidParams(
                    "请求所属电脑已切换，未发送".into(),
                ));
            }
            if !active.healthy.load(Ordering::Acquire) {
                return Err(ClientError::Rpc(
                    "与电脑的连接已失效，请重新连接；已提交的问题请到会话中核对".into(),
                ));
            }
            (
                active.generation,
                active.connection.clone(),
                active.lease.clone(),
                active.healthy.clone(),
            )
        };
        let result = exchange(&connection, &lease, &method, &path, request).await;
        {
            let state = self.state.lock().expect("Desktop state poisoned");
            if state.generation != generation
                || !state
                    .active
                    .as_ref()
                    .is_some_and(|active| active.generation == generation)
            {
                return Err(ClientError::Rpc(
                    "Desktop connection changed; response discarded".into(),
                ));
            }
        }
        invalidate_transport_failure(&healthy, &result);
        let response = result?;
        if method == "GET"
            && path.split('?').next() == Some("/connect/sessions")
            && (200..300).contains(&response.status)
        {
            validate_session_page(&path, &response.body_json)?;
        }
        Ok(response)
    }

    async fn connect_or_resume(
        &self,
        token: String,
        mobile: MobileDevice,
        desktop_id: Option<String>,
        preferred_desktop_id: Option<String>,
        force_reconnect: bool,
    ) -> ConnectResult {
        if token.trim().is_empty()
            || mobile.id.is_empty()
            || mobile.secret.is_empty()
            || desktop_id.as_deref().is_some_and(str::is_empty)
            || preferred_desktop_id.as_deref().is_some_and(str::is_empty)
        {
            return Err(ClientError::InvalidParams(
                "NE login, mobile identity and desktop required".into(),
            ));
        }
        let intent = {
            let mut state = self.state.lock().expect("Desktop state poisoned");
            if let Some(id) = &desktop_id {
                let joining = state.pending.as_ref().is_some_and(|pending| {
                    !*pending.cancel.borrow()
                        && pending.desktop_id.as_ref() == Some(id)
                        && same_mobile(&pending.token, &pending.mobile, &token, &mobile)
                });
                if !joining {
                    state.intent += 1;
                    if let Some(pending) = &state.pending {
                        pending.cancel.send_replace(true);
                    }
                }
                state.preferred = Some((mobile.id.clone(), id.clone()));
                state.recovery_suspended = false;
            }
            if state.recovery_suspended {
                return Ok(None);
            }
            state.intent
        };
        loop {
            let (mut done, reservation, waiter) = {
                let mut state = self.state.lock().expect("Desktop state poisoned");
                if state.intent != intent {
                    return Err(connect_cancelled());
                }
                if let Some(active) = &state.active
                    && !force_reconnect
                    && connection_info(active).healthy
                    && same_mobile(&active.token, &active.mobile, &token, &mobile)
                    && desktop_id
                        .as_ref()
                        .is_none_or(|id| id == &active.lease.desktop_id)
                {
                    return Ok(Some(connection_info(active)));
                }
                if let Some(pending) = state.pending.as_mut() {
                    let joining = !*pending.cancel.borrow()
                        && same_mobile(&pending.token, &pending.mobile, &token, &mobile)
                        && desktop_id
                            .as_ref()
                            .is_none_or(|id| pending.desktop_id.as_ref() == Some(id));
                    let waiter = joining.then(|| {
                        pending.waiters += 1;
                        ConnectCancellation {
                            state: self.state.clone(),
                            generation: pending.generation,
                        }
                    });
                    (pending.done.clone(), None, waiter)
                } else {
                    let previous = state.active.take();
                    if let Some(active) = &previous {
                        active.healthy.store(false, Ordering::Release);
                        active.connection.close(0u8.into(), b"reconnect");
                    }
                    let preferred = state
                        .preferred
                        .as_ref()
                        .filter(|(id, _)| id == &mobile.id)
                        .map(|(_, id)| id.clone())
                        .or_else(|| preferred_desktop_id.clone());
                    state.generation += 1;
                    let generation = state.generation;
                    let (cancel, _) = watch::channel(false);
                    let (completed, done) = watch::channel(None);
                    state.pending = Some(PendingConnect {
                        generation,
                        desktop_id: desktop_id.clone(),
                        token: token.clone(),
                        mobile: mobile.clone(),
                        waiters: 1,
                        cancel: cancel.clone(),
                        done: done.clone(),
                    });
                    let waiter = ConnectCancellation {
                        state: self.state.clone(),
                        generation,
                    };
                    (
                        done,
                        Some((generation, cancel, completed, previous, preferred)),
                        Some(waiter),
                    )
                }
            };
            if let Some((generation, cancel, completed, previous, preferred)) = reservation {
                let state = self.state.clone();
                let client = self.client.clone();
                let url = self.connect_url.clone();
                let token = token.clone();
                let mobile = mobile.clone();
                let desktop_id = desktop_id.clone();
                tokio::spawn(async move {
                    let built = async {
                        if let Some(active) = previous {
                            if desktop_id.is_some()
                                || !same_mobile(&active.token, &active.mobile, &token, &mobile)
                            {
                                close_desktop(&client, &url, active)
                                    .await
                                    .map_err(|error| DesktopBuildError {
                                        cleanup: Some(error.clone()),
                                        error,
                                    })?;
                            } else {
                                // Network recovery reclaims the lease with a fresh endpoint/ticket;
                                // it does not DELETE the lease and race a second acquisition.
                                stop_desktop_transport(active).await;
                            }
                        }
                        if *cancel.borrow() {
                            return Err(connect_cancelled().into());
                        }
                        let active = establish_desktop(
                            client.clone(),
                            url.clone(),
                            token.clone(),
                            mobile.clone(),
                            desktop_id.clone(),
                            generation,
                            cancel.subscribe(),
                        )
                        .await?;
                        if active.is_some() || desktop_id.is_some() || preferred.is_none() {
                            return Ok(active);
                        }
                        // Only a definitive resume 404 admits a new lease. No blind retry on 409/5xx.
                        if *cancel.borrow() {
                            return Err(connect_cancelled().into());
                        }
                        establish_desktop(
                            client.clone(),
                            url.clone(),
                            token,
                            mobile,
                            preferred,
                            generation,
                            cancel.subscribe(),
                        )
                        .await
                    }
                    .await;
                    let mut candidate = built;
                    {
                        let mut state = state.lock().expect("Desktop state poisoned");
                        if state.generation == generation
                            && state.intent == intent
                            && !*cancel.borrow()
                        {
                            let result = match candidate {
                                Ok(active) => {
                                    let info = active.as_ref().map(connection_info);
                                    if let Some(active) = &active {
                                        state.preferred = Some((
                                            active.mobile.id.clone(),
                                            active.lease.desktop_id.clone(),
                                        ));
                                    }
                                    state.active = active;
                                    Ok(info)
                                }
                                Err(error) => Err(error.error),
                            };
                            state.pending = None;
                            completed.send_replace(Some(result));
                            return;
                        }
                    }
                    let cleanup = match candidate.as_mut() {
                        Ok(active) => match active.take() {
                            Some(active) => close_desktop(&client, &url, active).await,
                            None => Ok(()),
                        },
                        Err(error) => {
                            if !is_connect_cancelled(&error.error) {
                                tracing::debug!(
                                    "Superseded Desktop acquisition failed: {}",
                                    error.error
                                );
                            }
                            error.cleanup.clone().map_or(Ok(()), Err)
                        }
                    };
                    if let Err(error) = &cleanup {
                        tracing::warn!("Desktop cancelled connection cleanup failed: {error}");
                    }
                    let mut state = state.lock().expect("Desktop state poisoned");
                    if state
                        .pending
                        .as_ref()
                        .is_some_and(|p| p.generation == generation)
                    {
                        state.pending = None;
                    }
                    completed.send_replace(Some(cleanup.and(Err(connect_cancelled()))));
                });
            }
            let result = wait_connect(&mut done).await;
            if self.state.lock().expect("Desktop state poisoned").intent != intent {
                return Err(connect_cancelled());
            }
            if waiter.is_some() {
                return result;
            }
            // A superseded attempt owns cleanup until it completes. The new selection
            // waits outside the state lock, then starts only if it is still the latest intent.
            if let Err(error) = result
                && !is_connect_cancelled(&error)
            {
                return Err(error);
            }
        }
    }
}

fn same_mobile(
    saved_token: &str,
    saved: &MobileDevice,
    token: &str,
    mobile: &MobileDevice,
) -> bool {
    saved_token == token && saved.id == mobile.id && saved.secret == mobile.secret
}

async fn wait_connect(done: &mut watch::Receiver<Option<ConnectResult>>) -> ConnectResult {
    loop {
        if let Some(result) = done.borrow_and_update().clone() {
            return result;
        }
        done.changed()
            .await
            .map_err(|_| ClientError::Transport("连接电脑的任务意外中断，请重新连接".into()))?;
    }
}

fn is_connect_cancelled(error: &ClientError) -> bool {
    matches!(error, ClientError::Rpc(message) if message == "Desktop connection cancelled")
}

fn connect_cancelled() -> ClientError {
    ClientError::Rpc("Desktop connection cancelled".into())
}

fn connection_info(active: &ActiveDesktop) -> DesktopConnection {
    DesktopConnection {
        desktop_id: active.lease.desktop_id.clone(),
        mobile_endpoint_id: active.mobile_endpoint_id.clone(),
        expires_at: active.expires_at.load(Ordering::Acquire),
        healthy: active.healthy.load(Ordering::Acquire)
            && active.connection.close_reason().is_none()
            && active.expires_at.load(Ordering::Acquire) > epoch_millis(),
    }
}

struct DesktopBuildError {
    error: ClientError,
    cleanup: Option<ClientError>,
}

impl From<ClientError> for DesktopBuildError {
    fn from(error: ClientError) -> Self {
        Self {
            error,
            cleanup: None,
        }
    }
}

fn desktop_endpoint_builder() -> iroh::endpoint::Builder {
    // Enable relay transport without public defaults; the lease supplies the host's relay.
    let builder = Endpoint::builder(iroh::endpoint::presets::Minimal)
        .relay_mode(RelayMode::Custom(RelayMap::empty()));
    #[cfg(target_os = "android")]
    let builder = builder
        .dns_resolver(iroh::dns::DnsResolver::with_nameserver(
            std::net::SocketAddr::from(([8, 8, 8, 8], 53)),
        ))
        .ca_roots_config(iroh::tls::CaRootsConfig::embedded());
    builder
}

fn desktop_relay_map(relay: RelayUrl) -> RelayMap {
    // Desktop relays are exposed through HTTP ingress, without UDP address discovery.
    RelayMap::from(RelayConfig::new(relay, None))
}

async fn establish_desktop(
    client: Client,
    url: Url,
    token: String,
    mobile: MobileDevice,
    desktop_id: Option<String>,
    generation: u64,
    mut cancel: watch::Receiver<bool>,
) -> Result<Option<ActiveDesktop>, DesktopBuildError> {
    let endpoint = desktop_endpoint_builder()
        .bind()
        .await
        .map_err(|e| ClientError::Transport(e.to_string()))?;
    let mobile_endpoint_id = endpoint.id().to_string();
    let path = if desktop_id.is_some() {
        "v1/leases"
    } else {
        "v1/leases/resume"
    };
    let body = if let Some(desktop_id) = desktop_id {
        json!({"mobileId":mobile.id,"mobileSecret":mobile.secret,"desktopId":desktop_id,"mobileEndpointId":mobile_endpoint_id})
    } else {
        json!({"mobileId":mobile.id,"mobileSecret":mobile.secret,"mobileEndpointId":mobile_endpoint_id})
    };
    // Do not abort lease acquisition mid-response: the server may already have committed it.
    // The worker resolves the bounded HTTP request, then releases a cancelled attempt.
    let acquired = client
        .post(url.join(path).map_err(url_error)?)
        .bearer_auth(&token)
        .json(&body)
        .send()
        .await
        .map_err(transport_error);
    let lease: DesktopLease = match acquired {
        Ok(response)
            if path == "v1/leases/resume"
                && response.status() == reqwest::StatusCode::NOT_FOUND =>
        {
            endpoint.close().await;
            return Ok(None);
        }
        Ok(response) => match connect_data(response).await {
            Ok(lease) => lease,
            Err(error) => {
                endpoint.close().await;
                return Err(error.into());
            }
        },
        Err(error) => {
            endpoint.close().await;
            return Err(error.into());
        }
    };
    let connected = async {
        if *cancel.borrow() {
            return Err(connect_cancelled());
        }
        let id = EndpointId::from_str(&lease.endpoint_id)
            .map_err(|e| ClientError::InvalidParams(e.to_string()))?;
        let mut address = EndpointAddr::new(id);
        if let Some(relay) = &lease.relay_url {
            let relay =
                RelayUrl::from_str(relay).map_err(|e| ClientError::InvalidParams(e.to_string()))?;
            let map = desktop_relay_map(relay.clone());
            let config = map
                .get(&relay)
                .ok_or_else(|| ClientError::InvalidParams("Invalid relay".into()))?;
            endpoint.insert_relay(relay.clone(), config.clone()).await;
            address = address.with_relay_url(relay);
        }
        tokio::select! {
            biased;
            _ = cancel.changed() => Err(connect_cancelled()),
            result = timeout(Duration::from_secs(20), endpoint.connect(address, DESKTOP_ALPN)) => {
                result.map_err(|_| ClientError::Transport("连接电脑超时，请确认电脑端 NeCode 已打开且两端网络正常".into()))?
                    .map_err(|e| ClientError::Transport(format!("连接电脑失败，请确认电脑端 NeCode 已打开且两端网络正常（{e}）")))
            }
        }
    }
    .await;
    let connection = match connected {
        Ok(connection) => connection,
        Err(error) => {
            let released = release_lease(&client, &url, &token, &mobile, &lease).await;
            endpoint.close().await;
            return Err(match released {
                Ok(()) => error.into(),
                Err(release) => DesktopBuildError {
                    error: ClientError::Transport(format!(
                        "{error}; lease release failed: {release}"
                    )),
                    cleanup: Some(release),
                },
            });
        }
    };
    let healthy = Arc::new(AtomicBool::new(true));
    let expires_at = Arc::new(AtomicI64::new(lease.expires_at));
    let renewal = tokio::spawn(renew_lease(
        client.clone(),
        url.clone(),
        token.clone(),
        mobile.clone(),
        lease.clone(),
        healthy.clone(),
        expires_at.clone(),
    ));
    Ok(Some(ActiveDesktop {
        generation,
        lease,
        endpoint,
        connection,
        mobile_endpoint_id,
        healthy,
        expires_at,
        renewal,
        token,
        mobile,
    }))
}

fn epoch_millis() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .expect("System clock before epoch")
        .as_millis() as i64
}

async fn stop_desktop_transport(active: ActiveDesktop) {
    active.healthy.store(false, Ordering::Release);
    active.renewal.abort();
    let _ = active.renewal.await;
    active.connection.close(0u8.into(), b"reconnect");
    active.endpoint.close().await;
}

async fn close_desktop(
    client: &Client,
    url: &Url,
    active: ActiveDesktop,
) -> Result<(), ClientError> {
    active.healthy.store(false, Ordering::Release);
    active.renewal.abort();
    let _ = active.renewal.await;
    active
        .connection
        .close(iroh::endpoint::VarInt::from_u32(0), b"disconnect");
    let released = release_lease(client, url, &active.token, &active.mobile, &active.lease).await;
    active.endpoint.close().await;
    match released {
        Err(error) if already_released(&error) => Ok(()),
        result => result,
    }
}

async fn release_lease(
    client: &Client,
    url: &Url,
    token: &str,
    mobile: &MobileDevice,
    lease: &DesktopLease,
) -> Result<(), ClientError> {
    let response = client
        .delete(
            url.join(&format!("v1/leases/{}", lease.id))
                .map_err(url_error)?,
        )
        .bearer_auth(token)
        .json(&json!({"mobileId":mobile.id,"mobileSecret":mobile.secret,"ticket":lease.ticket}))
        .send()
        .await
        .map_err(transport_error)?;
    connect_data::<Value>(response).await.map(|_| ())
}

#[derive(Deserialize)]
struct DesktopResponseWire {
    status: u16,
    body: Value,
}

async fn renew_lease(
    client: Client,
    url: Url,
    token: String,
    mobile: MobileDevice,
    lease: DesktopLease,
    healthy: Arc<AtomicBool>,
    expires_at: Arc<AtomicI64>,
) {
    let mut interval = tokio::time::interval(Duration::from_secs(20));
    interval.tick().await;
    loop {
        interval.tick().await;
        let result = async {
            let response = client.post(url.join(&format!("v1/leases/{}/renew", lease.id)).map_err(url_error)?)
                .bearer_auth(&token)
                .json(&json!({"mobileId":mobile.id,"mobileSecret":mobile.secret,"ticket":lease.ticket}))
                .send().await.map_err(transport_error)?;
            connect_data::<LeaseRenewal>(response).await
        }.await;
        if let Err(error) = result {
            tracing::warn!("Desktop lease renewal failed: {error}");
            healthy.store(false, Ordering::Release);
            break;
        }
        if let Ok(renewed) = result {
            expires_at.store(renewed.expires_at, Ordering::Release);
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct LeaseRenewal {
    expires_at: i64,
}

fn already_released(error: &ClientError) -> bool {
    matches!(error, ClientError::Rpc(message) if message.ends_with(": Invalid lease"))
}

fn invalidate_transport_failure(
    healthy: &AtomicBool,
    result: &Result<DesktopResponse, ClientError>,
) {
    if matches!(result, Err(ClientError::Transport(message)) if message != DESKTOP_REQUEST_TIMEOUT_MESSAGE)
    {
        healthy.store(false, Ordering::Release);
    }
}

async fn exchange(
    connection: &iroh::endpoint::Connection,
    lease: &DesktopLease,
    method: &str,
    path: &str,
    body: Value,
) -> Result<DesktopResponse, ClientError> {
    let bytes = encode_desktop_exchange(&lease.id, &lease.ticket, method, path, body)?;
    let (mut send, mut recv) = timeout(Duration::from_secs(20), connection.open_bi())
        .await
        .map_err(|_| ClientError::Transport("与电脑的通信超时，请重新连接后查看会话".into()))?
        .map_err(|e| ClientError::Transport(format!("与电脑的通信中断，请重新连接后查看会话（{e}）")))?;
    timeout(Duration::from_secs(30), async {
        send.write_all(&bytes)
            .await
            .map_err(|e| ClientError::Transport(format!("与电脑的通信中断，请重新连接后查看会话（{e}）")))?;
        send.finish()
            .map_err(|e| ClientError::Transport(format!("与电脑的通信中断，请重新连接后查看会话（{e}）")))?;
        recv.read_to_end(8 * 1024 * 1024)
            .await
            .map_err(|e| ClientError::Transport(format!("与电脑的通信中断，请重新连接后查看会话（{e}）")))
    })
    .await
    .map_err(|_| ClientError::Transport(DESKTOP_REQUEST_TIMEOUT_MESSAGE.into()))?
    .and_then(|bytes| {
        let response: DesktopResponseWire = serde_json::from_slice(&bytes)
            .map_err(|e| ClientError::Serialization(e.to_string()))?;
        Ok(DesktopResponse {
            status: response.status,
            body_json: response.body.to_string(),
        })
    })
}

fn encode_desktop_exchange(
    lease_id: &str,
    ticket: &str,
    method: &str,
    path: &str,
    body: Value,
) -> Result<Vec<u8>, ClientError> {
    let bytes = serde_json::to_vec(
        &json!({"lease_id":lease_id,"ticket":ticket,"method":method,"path":path,"body":body}),
    )
    .map_err(|e| ClientError::Serialization(e.to_string()))?;
    if bytes.len() > 4 * 1024 * 1024 {
        return Err(ClientError::InvalidParams(
            "Desktop 附件请求超过 4 MiB，请选择更小的文件".into(),
        ));
    }
    Ok(bytes)
}
async fn connect_data<T: serde::de::DeserializeOwned>(
    response: reqwest::Response,
) -> Result<T, ClientError> {
    if !response.status().is_success() {
        let status = response.status();
        let body: Value = response.json().await.unwrap_or(Value::Null);
        return Err(connect_response_error(
            status,
            body.get("error").and_then(Value::as_str).unwrap_or("request failed"),
        ));
    }
    response
        .json()
        .await
        .map_err(|e| ClientError::Serialization(e.to_string()))
}

fn connect_response_error(status: reqwest::StatusCode, error: &str) -> ClientError {
    if status == reqwest::StatusCode::CONFLICT && error == "Desktop offline" {
        return ClientError::Rpc("这台电脑暂时离线，请在电脑上打开 NeCode 并保持联网，然后刷新列表".into());
    }
    let hint = match status {
        reqwest::StatusCode::UNAUTHORIZED => Some("登录状态已失效，请重新登录后连接电脑"),
        reqwest::StatusCode::TOO_MANY_REQUESTS => Some("请求过于频繁，请稍后重试"),
        status if status.is_server_error() => Some("连接服务暂时不可用，请稍后重试；若持续失败，请联系管理员"),
        _ => None,
    };
    match hint {
        Some(hint) => ClientError::Rpc(format!("{hint}（HTTP {}）：{error}", status.as_u16())),
        None => ClientError::Rpc(format!("Connect returned {status}: {error}")),
    }
}

// The shared mobile boundary owns page size, scope/cursor validation and page parsing.
// Native shells keep only the opaque cursor and render the returned rows.
fn session_page_query(path: &str) -> Result<Vec<(String, String)>, ClientError> {
    let invalid = || ClientError::InvalidParams("Invalid desktop session page query".into());
    let mut query = Vec::new();
    for (key, value) in url::form_urlencoded::parse(
        path.split_once('?')
            .map(|(_, q)| q)
            .unwrap_or("")
            .as_bytes(),
    ) {
        if !matches!(
            key.as_ref(),
            "project" | "directory" | "workspace" | "search" | "cursor" | "limit"
        ) || query.iter().any(|(existing, _)| existing == &key)
        {
            return Err(invalid());
        }
        if key == "limit" && value != "10" {
            return Err(invalid());
        }
        let value = if key == "search" {
            value.trim().to_owned()
        } else {
            value.into_owned()
        };
        if value.is_empty() {
            continue;
        }
        query.push((key.into_owned(), value));
    }
    if !query.iter().any(|(key, _)| key == "limit") {
        query.push(("limit".into(), "10".into()));
    }
    if let Some((_, cursor)) = query.iter().find(|(key, _)| key == "cursor") {
        validate_page_cursor(cursor, &query)?;
    }
    Ok(query)
}

fn validate_page_cursor(cursor: &str, query: &[(String, String)]) -> Result<Value, ClientError> {
    let invalid =
        || ClientError::InvalidParams("Invalid or mismatched desktop session cursor".into());
    let value: Value = serde_json::from_str(cursor).map_err(|_| invalid())?;
    if value.get("updated").and_then(Value::as_u64).is_none()
        || !value
            .get("id")
            .and_then(Value::as_str)
            .is_some_and(|id| !id.is_empty())
    {
        return Err(invalid());
    }
    for key in ["project", "directory", "workspace", "search"] {
        let expected = query
            .iter()
            .find(|(field, _)| field == key)
            .map(|(_, value)| value.as_str());
        if value.get(key).and_then(Value::as_str) != expected {
            return Err(invalid());
        }
    }
    Ok(value)
}

fn validate_session_page(path: &str, body: &str) -> Result<(), ClientError> {
    let invalid = || ClientError::Serialization("Invalid desktop session page response".into());
    let page: Value = serde_json::from_str(body).map_err(|_| invalid())?;
    let rows = page
        .get("data")
        .and_then(Value::as_array)
        .ok_or_else(invalid)?;
    if rows.len() > 10 {
        return Err(invalid());
    }
    let query = session_page_query(path)?;
    let project = query
        .iter()
        .find(|(key, _)| key == "project")
        .map(|(_, value)| value.as_str());
    let mut previous: Option<(u64, &str)> = None;
    let mut ids = std::collections::HashSet::new();
    for row in rows {
        let id = row
            .get("id")
            .and_then(Value::as_str)
            .filter(|id| !id.is_empty())
            .ok_or_else(invalid)?;
        let updated = row
            .pointer("/time/updated")
            .and_then(Value::as_u64)
            .ok_or_else(invalid)?;
        if row.get("title").and_then(Value::as_str).is_none()
            || row.get("directory").and_then(Value::as_str).is_none()
            || row.get("projectID").and_then(Value::as_str).is_none()
            || project.is_some_and(|project| {
                row.get("projectID").and_then(Value::as_str) != Some(project)
            })
            || row.get("parentID").is_some_and(|parent| !parent.is_null())
            || row
                .pointer("/time/archived")
                .is_some_and(|archived| !archived.is_null())
            || !ids.insert(id)
            || previous.is_some_and(|boundary| (updated, id) >= boundary)
        {
            return Err(invalid());
        }
        previous = Some((updated, id));
    }
    match page.get("nextCursor") {
        Some(Value::Null) => Ok(()),
        Some(Value::String(cursor)) if rows.len() == 10 => {
            let cursor = validate_page_cursor(cursor, &query)?;
            if previous
                != cursor
                    .get("updated")
                    .and_then(Value::as_u64)
                    .zip(cursor.get("id").and_then(Value::as_str))
            {
                return Err(invalid());
            }
            Ok(())
        }
        _ => Err(invalid()),
    }
}

fn desktop_request(method: &str, path: &str, body_json: &str) -> Result<Value, ClientError> {
    let route = path.split('?').next().unwrap_or("");
    if path.len() > 2048
        || path.contains('#')
        || route.contains('%')
        || route.contains("..")
        || route.contains("//")
    {
        return Err(ClientError::InvalidParams(
            "Desktop route not allowed".into(),
        ));
    }
    let parts: Vec<_> = route.split('/').collect();
    if parts.first() != Some(&"")
        || parts.iter().skip(1).any(|part| {
            part.is_empty()
                || !part
                    .bytes()
                    .all(|c| c.is_ascii_alphanumeric() || c == b'-' || c == b'_')
        })
    {
        return Err(ClientError::InvalidParams(
            "Desktop route not allowed".into(),
        ));
    }
    let allowed = match (method, parts.as_slice()) {
        (
            "GET",
            ["", "project"]
            | ["", "session"]
            | ["", "session", "status"]
            | ["", "permission"]
            | ["", "question"]
            | ["", "file", "content"]
            | ["", "connect", "models"]
            | ["", "connect", "projects"]
            | ["", "connect", "directories"]
            | ["", "connect", "sessions"]
            | ["", "api", "session"],
        ) => true,
        (
            "GET",
            ["", "project", _, "directories"]
            | ["", "session", _]
            | ["", "session", _, "message"]
            | ["", "connect", "session", _, "message"]
            | ["", "connect", "session", _, "live"]
            | ["", "connect", "session", _, "prompt", _]
            | ["", "connect", "session", _, "attention"]
            | ["", "session", _, "todo"]
            | ["", "session", _, "diff"]
            | ["", "api", "session", _]
            | ["", "api", "session", _, "message"]
            | ["", "api", "session", _, "permission"]
            | ["", "api", "session", _, "question"],
        ) => true,
        (
            "POST",
            ["", "session"]
            | ["", "connect", "projects"]
            | ["", "connect", "directories"]
            | ["", "api", "session"]
            | ["", "session", _, "prompt_async"]
            | ["", "session", _, "abort"]
            | ["", "permission", _, "reply"]
            | ["", "question", _, "reply"]
            | ["", "question", _, "reject"]
            | ["", "api", "session", _, "prompt"]
            | ["", "api", "session", _, "permission", _, "reply"]
            | ["", "api", "session", _, "question", _, "reply"]
            | ["", "api", "session", _, "question", _, "reject"],
        ) => true,
        ("PATCH", ["", "session", _]) => true,
        _ => false,
    };
    if !allowed
        || path.split_once('?').is_some_and(|(_, query)| {
            query.split('&').any(|pair| {
                !matches!(
                    pair.split('=').next(),
                    Some(
                        "directory"
                            | "workspace"
                            | "scope"
                            | "limit"
                            | "before"
                            | "roots"
                            | "search"
                            | "path"
                            | "start"
                            | "cursor"
                            | "order"
                            | "project"
                            | "subpath"
                    )
                )
            })
        })
    {
        return Err(ClientError::InvalidParams(
            "Desktop route not allowed".into(),
        ));
    }
    if method == "GET" && route == "/connect/sessions" {
        session_page_query(path)?;
    }
    serde_json::from_str(body_json)
        .map_err(|e| ClientError::InvalidParams(format!("Invalid JSON body: {e}")))
}

impl Drop for DesktopConnectBridge {
    fn drop(&mut self) {
        let mut state = self.state.lock().expect("Desktop state poisoned");
        state.generation += 1;
        if let Some(pending) = &state.pending {
            pending.cancel.send_replace(true);
        } else if let Some(active) = state.active.take() {
            active.healthy.store(false, Ordering::Release);
            active.renewal.abort();
            active.connection.close(0u8.into(), b"bridge dropped");
            // Process exit cannot await release; the server's lease expiry remains authoritative.
        }
    }
}

fn service_url(value: &str) -> Result<Url, ClientError> {
    let mut url = Url::parse(value).map_err(url_error)?;
    if url.scheme() != "https" && !(url.scheme() == "http" && url.host_str() == Some("127.0.0.1")) {
        return Err(ClientError::InvalidParams(
            "NE and Connect services require HTTPS".into(),
        ));
    }
    if !url.username().is_empty()
        || url.password().is_some()
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return Err(ClientError::InvalidParams("Invalid service URL".into()));
    }
    url.set_path("/");
    Ok(url)
}

async fn ne_data<T: serde::de::DeserializeOwned>(
    response: reqwest::Response,
) -> Result<Option<T>, ClientError> {
    if !response.status().is_success() {
        return Err(ClientError::Rpc(format!(
            "登录服务返回异常，请稍后重试；若持续失败，请联系管理员（HTTP {}）",
            response.status()
        )));
    }
    let result: NeResult = response
        .json()
        .await
        .map_err(|e| ClientError::Serialization(e.to_string()))?;
    if result.code != 0 && result.code != "0" {
        return Err(ClientError::Rpc("NE 登录失败或登录状态已失效，请检查账号密码或重新登录".into()));
    }
    result
        .data
        .map(serde_json::from_value)
        .transpose()
        .map_err(|e| ClientError::Serialization(e.to_string()))
}

fn encrypt_ne_password(password: &str, public_key: &str) -> Result<String, ClientError> {
    let key = RsaPublicKey::from_public_key_pem(public_key)
        .map_err(|e| ClientError::Serialization(format!("Invalid NE public key: {e}")))?;
    let encrypted = key
        .encrypt(&mut rand_core::OsRng, Pkcs1v15Encrypt, password.as_bytes())
        .map_err(|e| ClientError::InvalidParams(format!("NE password encryption failed: {e}")))?;
    Ok(base64::engine::general_purpose::STANDARD.encode(encrypted))
}
fn url_error(error: url::ParseError) -> ClientError {
    ClientError::InvalidParams(error.to_string())
}

fn transport_error(error: reqwest::Error) -> ClientError {
    let hint = if error.is_timeout() {
        "连接服务超时，请检查网络后重试"
    } else if error.is_connect() {
        "暂时无法连接服务，请检查网络；若持续失败，请联系管理员"
    } else {
        "与连接服务通信失败，请稍后重试"
    };
    // Identity requests carry the login token in their URL; never expose it in UI errors.
    ClientError::Transport(format!("{hint}（{}）", error.without_url()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn connect_response_error_explains_only_known_offline_conflict() {
        let offline = connect_response_error(reqwest::StatusCode::CONFLICT, "Desktop offline");
        assert!(matches!(offline, ClientError::Rpc(message) if message == "这台电脑暂时离线，请在电脑上打开 NeCode 并保持联网，然后刷新列表"));
        let unknown = connect_response_error(reqwest::StatusCode::CONFLICT, "Lease conflict");
        assert!(matches!(unknown, ClientError::Rpc(message) if message == "Connect returned 409 Conflict: Lease conflict"));
        let forbidden = connect_response_error(reqwest::StatusCode::FORBIDDEN, "Desktop offline");
        assert!(matches!(forbidden, ClientError::Rpc(message) if message == "Connect returned 403 Forbidden: Desktop offline"));
    }

    #[test]
    fn connect_response_error_explains_service_failures_without_hiding_details() {
        for (status, detail, hint) in [
            (reqwest::StatusCode::UNAUTHORIZED, "Unauthorized", "请重新登录"),
            (reqwest::StatusCode::TOO_MANY_REQUESTS, "rate limited", "请稍后重试"),
            (reqwest::StatusCode::BAD_GATEWAY, "upstream unavailable", "连接服务暂时不可用"),
        ] {
            let error = connect_response_error(status, detail);
            assert!(matches!(error, ClientError::Rpc(message) if message.contains(hint) && message.contains(detail) && message.contains(&status.as_u16().to_string())));
        }
        assert!(already_released(&connect_response_error(reqwest::StatusCode::FORBIDDEN, "Invalid lease")));
        assert!(!already_released(&connect_response_error(reqwest::StatusCode::FORBIDDEN, "Invalid mobile")));
    }

    #[test]
    fn transport_error_keeps_diagnostics_without_exposing_login_url() {
        let url = reqwest::Url::parse("http://127.0.0.1/?AUT=private-login-token").unwrap();
        let error = Client::new().get(url.clone()).header("AUT", "\n").build().unwrap_err().with_url(url);
        let raw = error.to_string();
        assert!(raw.contains("private-login-token"));
        let error = transport_error(error);
        assert!(matches!(error, ClientError::Transport(message) if message.contains("与连接服务通信失败") && message.contains("builder error") && !message.contains("private-login-token") && !message.contains("AUT=")));
    }

    #[tokio::test]
    async fn desktop_endpoint_accepts_lease_relay_without_public_defaults() {
        let endpoint = desktop_endpoint_builder()
            .clear_ip_transports()
            .bind()
            .await
            .expect("lease relay needs an enabled relay transport even before acquisition");
        assert!(endpoint.address_lookup().unwrap().is_empty());
        for relay in iroh::RelayMode::Default.relay_map().urls::<Vec<_>>() {
            assert!(endpoint.remove_relay(&relay).await.is_none());
        }
        let relay: RelayUrl = "http://127.0.0.1:9".parse().unwrap();
        let map = desktop_relay_map(relay.clone());
        assert!(
            map.get(&relay).unwrap().quic.is_none(),
            "K8s relay has no QUIC address discovery"
        );
        endpoint.insert_relay(relay.clone(), map.get(&relay).unwrap()).await;
        assert!(endpoint.remove_relay(&relay).await.is_some());
        endpoint.close().await;
    }

    #[tokio::test]
    #[ignore = "requires access to relay.inoteexpress.com"]
    async fn desktop_connection_works_through_self_hosted_relay_without_udp() {
        timeout(Duration::from_secs(30), async {
            let relay: RelayUrl = "https://relay.inoteexpress.com".parse().unwrap();
            let map = desktop_relay_map(relay.clone());
            let host = Endpoint::builder(iroh::endpoint::presets::Minimal)
                .relay_mode(RelayMode::Custom(map.clone()))
                .alpns(vec![DESKTOP_ALPN.to_vec()])
                .clear_ip_transports()
                .bind()
                .await
                .unwrap();
            let mobile = desktop_endpoint_builder()
                .clear_ip_transports()
                .bind()
                .await
                .unwrap();
            mobile.insert_relay(relay.clone(), map.get(&relay).unwrap()).await;
            tokio::join!(host.online(), mobile.online());
            let address = EndpointAddr::new(host.id()).with_relay_url(relay);
            let accept = async {
                let connection = host.accept().await.unwrap().accept().unwrap().await.unwrap();
                let (mut send, mut recv) = connection.accept_bi().await.unwrap();
                assert_eq!(recv.read_to_end(1024).await.unwrap(), b"relay-smoke");
                send.write_all(b"relay-ok").await.unwrap();
                send.finish().unwrap();
                connection.closed().await;
            };
            let connect = async {
                let connection = mobile.connect(address, DESKTOP_ALPN).await.unwrap();
                let (mut send, mut recv) = connection.open_bi().await.unwrap();
                send.write_all(b"relay-smoke").await.unwrap();
                send.finish().unwrap();
                assert_eq!(recv.read_to_end(1024).await.unwrap(), b"relay-ok");
                connection.close(0u8.into(), b"done");
            };
            tokio::join!(accept, connect);
            mobile.close().await;
            host.close().await;
        })
        .await
        .expect("self-hosted relay must carry a Desktop exchange without direct UDP");
    }

    #[test]
    fn encrypts_ne_password_with_pkcs1_v15() {
        use rsa::pkcs8::EncodePublicKey;
        let private = rsa::RsaPrivateKey::new(&mut rand_core::OsRng, 1024).unwrap();
        let public = private
            .to_public_key()
            .to_public_key_pem(Default::default())
            .unwrap();
        let cipher = encrypt_ne_password("secret", &public).unwrap();
        assert_eq!(
            private
                .decrypt(
                    rsa::Pkcs1v15Encrypt,
                    &base64::Engine::decode(&base64::engine::general_purpose::STANDARD, cipher)
                        .unwrap()
                )
                .unwrap(),
            b"secret"
        );
    }
    #[test]
    fn constructs_on_a_thread_without_tokio_runtime() {
        let bridge = std::thread::spawn(|| {
            DesktopConnectBridge::new(
                "https://uc.inoteexpress.com".into(),
                "https://necode.inoteexpress.com".into(),
            )
        })
        .join()
        .expect("constructor must not panic outside Tokio");
        assert!(bridge.is_ok());
    }
    #[test]
    fn rejects_insecure_remote_services_and_token_urls() {
        assert!(service_url("http://example.com").is_err());
        assert!(service_url("https://user:password@example.com").is_err());
        assert!(service_url("https://example.com?token=abc").is_err());
        assert!(service_url("http://127.0.0.1:8080").is_ok());
    }

    #[test]
    fn decodes_mobile_identity_and_lease_contract() {
        let device: MobileDevice =
            serde_json::from_str(r#"{"id":"mobile-1","secret":"private"}"#).unwrap();
        assert_eq!(device.id, "mobile-1");
        let lease: DesktopLease = serde_json::from_str(r#"{"id":"lease-1","ticket":"ticket","desktopId":"desktop-1","endpointId":"endpoint-1","relayUrl":null,"expiresAt":1234}"#).unwrap();
        assert_eq!(lease.endpoint_id, "endpoint-1");
        let request = serde_json::json!({"lease_id": lease.id, "ticket": lease.ticket, "method": "GET", "path": "/project", "body": null});
        assert!(request.get("mobileSecret").is_none());
    }

    #[test]
    fn artifact_content_route_is_read_only_and_keeps_request_guards() {
        let path = "/file/content?directory=D%3A%2Frepo&path=D%3A%2Frepo%2F%E8%B7%AF%E7%BA%BF.html";
        assert_eq!(desktop_request("GET", path, "null").unwrap(), Value::Null);
        for method in ["POST", "PUT", "PATCH", "DELETE"] {
            assert!(desktop_request(method, path, "null").is_err(), "{method}");
        }
        for path in [
            "/file/content?credential=secret",
            "/file/content?path=route.html#fragment",
            "/file/%63ontent?path=route.html",
            "/file/../content?path=route.html",
            "/file//content?path=route.html",
            "/file/content/private?path=route.html",
            "/file/write?path=route.html",
        ] {
            assert!(desktop_request("GET", path, "null").is_err(), "{path}");
        }
        assert!(desktop_request("GET", path, "not-json").is_err());
        assert!(desktop_request("GET", &format!("/file/content?path={}", "a".repeat(2048)), "null").is_err());
    }

    #[test]
    fn validates_desktop_request_before_sending() {
        assert!(desktop_request("GET", "/project", "null").is_ok());
        assert!(desktop_request("GET", "/connect/sessions?project=pro_1&limit=10", "null").is_ok());
        assert!(desktop_request("GET", "/connect/sessions?limit=1000", "null").is_err());
        assert!(desktop_request("GET", "/connect/sessions?project=a&project=b", "null").is_err());
        assert!(desktop_request("GET", "/connect/sessions?cursor=bad", "null").is_err());
        assert!(desktop_request("GET", "/connect/sessions?roots=false", "null").is_err());
        assert!(desktop_request("GET", "/connect/models?directory=D%3A%2Frepo", "null").is_ok());
        assert!(
            desktop_request(
                "GET",
                "/session/ses_1/message?directory=D%3A%2Frepo",
                "null"
            )
            .is_ok()
        );
        assert!(desktop_request("POST", "/session", r#"{"title":"test"}"#).is_ok());
        assert!(desktop_request("GET", "https://elsewhere.test", "null").is_err());
        assert!(desktop_request("GET", "/auth/ne", "null").is_err());
        assert!(desktop_request("GET", "/session", "not-json").is_err());
    }

    #[test]
    fn shared_session_pages_keep_the_cursor_opaque_and_validate_the_response() {
        let query =
            session_page_query("/connect/sessions?project=pro_1&search=%20test%20").unwrap();
        assert!(query.contains(&("limit".into(), "10".into())));
        assert!(query.contains(&("search".into(), "test".into())));
        let rows: Vec<Value> = (0..10)
            .rev()
            .map(|id| {
                json!({
                    "id": format!("ses_{id}"), "title": "test", "directory": "D:/repo",
                    "projectID": "pro_1", "time": { "updated": 1000 }
                })
            })
            .collect();
        let cursor = json!({"updated":1000,"id":"ses_0","project":"pro_1"}).to_string();
        let page = json!({"data": rows, "nextCursor":cursor});
        assert!(
            validate_session_page("/connect/sessions?project=pro_1", &page.to_string()).is_ok()
        );
        let mut url = Url::parse("http://localhost/connect/sessions?project=pro_1").unwrap();
        url.query_pairs_mut().append_pair("cursor", &cursor);
        assert!(session_page_query(&format!("{}?{}", url.path(), url.query().unwrap())).is_ok());
        assert!(
            validate_session_page("/connect/sessions?project=pro_2", &page.to_string()).is_err()
        );
        let wrong_project = json!({"data": [rows[0].clone()], "nextCursor": null});
        assert!(
            validate_session_page(
                "/connect/sessions?project=pro_2",
                &wrong_project.to_string()
            )
            .is_err(),
            "even a final page must belong to the requested project"
        );
        let mut invalid = page.clone();
        invalid["data"][1] = invalid["data"][0].clone();
        assert!(
            validate_session_page("/connect/sessions?project=pro_1", &invalid.to_string()).is_err()
        );
        invalid = page.clone();
        invalid["data"][0]["parentID"] = json!("ses_parent");
        assert!(
            validate_session_page("/connect/sessions?project=pro_1", &invalid.to_string()).is_err()
        );
        invalid = page.clone();
        invalid["data"][0]["time"]["archived"] = json!(1001);
        assert!(
            validate_session_page("/connect/sessions?project=pro_1", &invalid.to_string()).is_err()
        );
        assert!(validate_session_page("/connect/sessions", "<html>wrong route</html>").is_err());
        assert!(
            validate_session_page("/connect/sessions", r#"{"data":[],"nextCursor":null}"#).is_ok()
        );
    }

    #[tokio::test]
    async fn desktop_stream_uses_unframed_json_and_reads_status() {
        use iroh::endpoint::presets;
        let host = Endpoint::builder(presets::Minimal)
            .alpns(vec![DESKTOP_ALPN.to_vec()])
            .clear_ip_transports()
            .bind_addr("127.0.0.1:0")
            .unwrap()
            .bind()
            .await
            .unwrap();
        let mobile = Endpoint::builder(presets::Minimal)
            .clear_ip_transports()
            .bind_addr("127.0.0.1:0")
            .unwrap()
            .bind()
            .await
            .unwrap();
        let acceptor = host.clone();
        let server = tokio::spawn(async move {
            let conn = acceptor
                .accept()
                .await
                .unwrap()
                .accept()
                .unwrap()
                .await
                .unwrap();
            let (mut send, mut recv) = conn.accept_bi().await.unwrap();
            let bytes = recv.read_to_end(1024 * 1024).await.unwrap();
            let request: Value = serde_json::from_slice(&bytes).unwrap();
            assert_eq!(request["method"], "GET");
            assert_eq!(request["lease_id"], "lease-1");
            assert_eq!(request["body"], Value::Null);
            send.write_all(br#"{"status":200,"body":[{"id":"project-1"}]}"#)
                .await
                .unwrap();
            send.finish().unwrap();
            conn.closed().await;
        });
        let connection = tokio::time::timeout(
            Duration::from_secs(10),
            mobile.connect(host.addr(), DESKTOP_ALPN),
        )
        .await
        .unwrap()
        .unwrap();
        let lease: DesktopLease = serde_json::from_str(r#"{"id":"lease-1","ticket":"ticket","desktopId":"desktop-1","endpointId":"endpoint-1","relayUrl":null,"expiresAt":1234}"#).unwrap();
        let response = exchange(&connection, &lease, "GET", "/project", Value::Null)
            .await
            .unwrap();
        assert_eq!(response.status, 200);
        assert!(response.body_json.contains("project-1"));
        mobile.close().await;
        server.await.unwrap();
        host.close().await;
    }

    #[test]
    fn rejects_oversized_desktop_request_before_opening_stream() {
        let body = json!({"parts":[{"type":"file","url":format!("data:image/png;base64,{}", "A".repeat(4 * 1024 * 1024))}]});
        let result = encode_desktop_exchange(
            "lease",
            "ticket",
            "POST",
            "/session/ses_1/prompt_async",
            body,
        );
        assert!(matches!(result, Err(ClientError::InvalidParams(_))));
    }
    #[test]
    fn expired_lease_is_already_released_but_other_release_errors_are_visible() {
        assert!(already_released(&ClientError::Rpc(
            "Connect returned 403 Forbidden: Invalid lease".into()
        )));
        assert!(!already_released(&ClientError::Rpc(
            "Connect returned 403 Forbidden: Invalid mobile".into()
        )));
        assert!(!already_released(&ClientError::Transport(
            "network down".into()
        )));
    }

    #[test]
    fn transport_failure_invalidates_active_desktop_but_http_error_does_not() {
        let healthy = AtomicBool::new(true);
        let failed: Result<DesktopResponse, ClientError> =
            Err(ClientError::Transport("connection lost".into()));
        invalidate_transport_failure(&healthy, &failed);
        assert!(!healthy.load(Ordering::Acquire));

        healthy.store(true, Ordering::Release);
        let rejected: Result<DesktopResponse, ClientError> =
            Err(ClientError::Rpc("Desktop returned 400".into()));
        invalidate_transport_failure(&healthy, &rejected);
        assert!(healthy.load(Ordering::Acquire));

        let timed_out: Result<DesktopResponse, ClientError> =
            Err(ClientError::Transport("电脑响应超时，操作结果暂未确认，请刷新会话核对".into()));
        invalidate_transport_failure(&healthy, &timed_out);
        assert!(healthy.load(Ordering::Acquire));
    }

    #[test]
    fn decodes_multiple_desktops_and_their_occupancy() {
        let result: DesktopList = serde_json::from_str(r#"{"desktops":[{"id":"one","name":"One","endpointId":"node","relayUrl":null,"status":"busy"},{"id":"two","name":"Two","endpointId":"node2","relayUrl":"https://relay.example","status":"available"}]}"#).unwrap();
        assert_eq!(result.desktops.len(), 2);
        assert_eq!(result.desktops[0].status, "busy");
        assert_eq!(result.desktops[1].status, "available");
    }
    #[tokio::test]
    async fn stalled_request_does_not_block_connection_status() {
        let host = Endpoint::builder(iroh::endpoint::presets::Minimal)
            .alpns(vec![DESKTOP_ALPN.to_vec()])
            .clear_ip_transports()
            .bind_addr("127.0.0.1:0")
            .unwrap()
            .bind()
            .await
            .unwrap();
        let endpoint = Endpoint::builder(iroh::endpoint::presets::Minimal)
            .clear_ip_transports()
            .bind_addr("127.0.0.1:0")
            .unwrap()
            .bind()
            .await
            .unwrap();
        let (started, waiting) = tokio::sync::oneshot::channel();
        let acceptor = host.clone();
        let server = tokio::spawn(async move {
            let connection = acceptor
                .accept()
                .await
                .unwrap()
                .accept()
                .unwrap()
                .await
                .unwrap();
            let (_send, mut recv) = connection.accept_bi().await.unwrap();
            recv.read_to_end(4096).await.unwrap();
            started.send(()).unwrap();
            connection.closed().await;
        });
        let connection = endpoint.connect(host.addr(), DESKTOP_ALPN).await.unwrap();
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let release = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            assert!(
                http_request(&mut socket)
                    .await
                    .starts_with("DELETE /v1/leases/lease-1 ")
            );
            http_json(&mut socket, r#"{"ok":true}"#).await;
        });
        let bridge = Arc::new(DesktopConnectBridge::new(url.clone(), url).unwrap());
        bridge.state.lock().unwrap().active = Some(ActiveDesktop {
            generation: 0,
            lease: DesktopLease {
                id: "lease-1".into(),
                ticket: "test".into(),
                desktop_id: "desktop-1".into(),
                endpoint_id: host.id().to_string(),
                relay_url: None,
                expires_at: 1234,
            },
            endpoint,
            connection,
            mobile_endpoint_id: "mobile".into(),
            healthy: Arc::new(AtomicBool::new(true)),
            expires_at: Arc::new(AtomicI64::new(1234)),
            renewal: tokio::spawn(std::future::pending()),
            token: "test".into(),
            mobile: MobileDevice {
                id: "mobile".into(),
                secret: "test".into(),
            },
        });
        let wrong_host = timeout(
            Duration::from_millis(200),
            bridge.request_desktop_for(
                "other-host".into(),
                "POST".into(),
                "/session".into(),
                "{}".into(),
            ),
        )
        .await
        .unwrap();
        assert!(matches!(wrong_host, Err(ClientError::InvalidParams(_))));
        let requester = bridge.clone();
        let request = tokio::spawn(async move {
            requester
                .request_desktop("GET".into(), "/project".into(), "null".into())
                .await
        });
        waiting.await.unwrap();
        let status = timeout(Duration::from_millis(200), bridge.active_desktop()).await;
        let disconnected = timeout(Duration::from_secs(1), bridge.disconnect_desktop()).await;
        let result = timeout(Duration::from_secs(1), request).await;
        server.abort();
        host.close().await;
        assert!(status.is_ok(), "status must not wait for a stalled request");
        assert!(
            disconnected.is_ok(),
            "disconnect must not wait for a stalled request"
        );
        disconnected.unwrap().unwrap();
        release.await.unwrap();
        assert!(
            matches!(result.unwrap().unwrap(), Err(ClientError::Rpc(message)) if message.contains("response discarded")),
            "old-generation results must not reach a new connection"
        );
    }

    async fn http_request(socket: &mut tokio::net::TcpStream) -> String {
        use tokio::io::AsyncReadExt;
        let mut bytes = Vec::new();
        loop {
            let mut chunk = [0; 4096];
            let read = socket.read(&mut chunk).await.unwrap();
            assert!(read > 0);
            bytes.extend_from_slice(&chunk[..read]);
            let text = String::from_utf8_lossy(&bytes);
            if let Some(end) = text.find("\r\n\r\n") {
                let length = text[..end]
                    .lines()
                    .find_map(|line| {
                        let (name, value) = line.split_once(':')?;
                        name.eq_ignore_ascii_case("content-length")
                            .then(|| value.trim().parse::<usize>().unwrap())
                    })
                    .unwrap_or(0);
                if bytes.len() >= end + 4 + length {
                    return text.into_owned();
                }
            }
        }
    }

    async fn http_json(socket: &mut tokio::net::TcpStream, body: &str) {
        use tokio::io::AsyncWriteExt;
        socket.write_all(format!("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len()).as_bytes()).await.unwrap();
    }

    #[tokio::test]
    async fn cancelled_connect_releases_the_late_acquired_lease() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let bridge = Arc::new(DesktopConnectBridge::new(url.clone(), url).unwrap());
        let (started, waiting) = oneshot::channel();
        let (respond, response) = oneshot::channel();
        let server = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            assert!(
                http_request(&mut socket)
                    .await
                    .starts_with("POST /v1/leases ")
            );
            started.send(()).unwrap();
            response.await.unwrap();
            http_json(&mut socket, r#"{"id":"lease-late","ticket":"test","desktopId":"desktop-1","endpointId":"0000000000000000000000000000000000000000000000000000000000000000","relayUrl":null,"expiresAt":1234}"#).await;
            let (mut socket, _) = listener.accept().await.unwrap();
            assert!(
                http_request(&mut socket)
                    .await
                    .starts_with("DELETE /v1/leases/lease-late ")
            );
            http_json(&mut socket, r#"{"ok":true}"#).await;
            let (mut socket, _) = listener.accept().await.unwrap();
            let request = http_request(&mut socket).await;
            assert!(request.starts_with("POST /v1/leases ") && request.contains("desktop-2"));
            use tokio::io::AsyncWriteExt;
            socket
                .write_all(
                    b"HTTP/1.1 409 Conflict\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}",
                )
                .await
                .unwrap();
        });
        let connector = bridge.clone();
        let attempt = tokio::spawn(async move {
            connector
                .connect_desktop(
                    "test".into(),
                    MobileDevice {
                        id: "mobile".into(),
                        secret: "test".into(),
                    },
                    "desktop-1".into(),
                )
                .await
        });
        timeout(Duration::from_secs(2), waiting)
            .await
            .unwrap()
            .unwrap();
        attempt.abort();
        let _ = attempt.await;
        assert!(
            timeout(Duration::from_millis(200), bridge.active_desktop())
                .await
                .unwrap()
                .is_none()
        );
        let selecting = bridge.clone();
        let concurrent = tokio::spawn(async move {
            selecting
                .connect_desktop("test".into(), test_mobile(), "desktop-2".into())
                .await
        });
        tokio::time::sleep(Duration::from_millis(50)).await;
        assert!(
            !concurrent.is_finished(),
            "new selection must wait for lease cleanup"
        );
        respond.send(()).unwrap();
        timeout(Duration::from_secs(2), server)
            .await
            .unwrap()
            .unwrap();
        assert!(matches!(
            concurrent.await.unwrap(),
            Err(ClientError::Rpc(_))
        ));
        timeout(Duration::from_secs(2), bridge.disconnect_desktop())
            .await
            .unwrap()
            .unwrap();
        assert!(bridge.state.lock().unwrap().pending.is_none());
        assert!(bridge.active_desktop().await.is_none());
    }

    fn test_mobile() -> MobileDevice {
        MobileDevice {
            id: "mobile".into(),
            secret: "test".into(),
        }
    }

    #[tokio::test]
    async fn concurrent_resume_calls_share_one_acquisition() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let bridge = Arc::new(DesktopConnectBridge::new(url.clone(), url).unwrap());
        let (started, waiting) = oneshot::channel();
        let (respond, response) = oneshot::channel();
        let server = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            assert!(
                http_request(&mut socket)
                    .await
                    .starts_with("POST /v1/leases/resume ")
            );
            started.send(()).unwrap();
            response.await.unwrap();
            use tokio::io::AsyncWriteExt;
            socket
                .write_all(
                    b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                )
                .await
                .unwrap();
            assert!(
                timeout(Duration::from_millis(150), listener.accept())
                    .await
                    .is_err(),
                "resume must be single-flight"
            );
        });
        let first = bridge.clone();
        let first =
            tokio::spawn(async move { first.resume_desktop("test".into(), test_mobile()).await });
        waiting.await.unwrap();
        let second = bridge.clone();
        let second =
            tokio::spawn(async move { second.resume_desktop("test".into(), test_mobile()).await });
        tokio::time::sleep(Duration::from_millis(50)).await;
        respond.send(()).unwrap();
        assert!(first.await.unwrap().unwrap().is_none());
        assert!(second.await.unwrap().unwrap().is_none());
        server.await.unwrap();
    }

    #[tokio::test]
    async fn explicit_disconnect_suspends_automatic_resume() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let bridge = DesktopConnectBridge::new(url.clone(), url).unwrap();
        bridge.disconnect_desktop().await.unwrap();
        // A hidden page's recovery loop must not undo a user's disconnect.
        let result = timeout(
            Duration::from_millis(150),
            bridge.resume_desktop("test".into(), test_mobile()),
        )
        .await;
        assert!(matches!(result, Ok(Ok(None))));
        assert!(
            timeout(Duration::from_millis(50), listener.accept())
                .await
                .is_err()
        );
    }

    #[tokio::test]
    async fn dropping_one_joined_caller_keeps_recovery_alive() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let bridge = Arc::new(DesktopConnectBridge::new(url.clone(), url).unwrap());
        let (started, waiting) = oneshot::channel();
        let (respond, response) = oneshot::channel();
        let server = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            assert!(
                http_request(&mut socket)
                    .await
                    .starts_with("POST /v1/leases/resume ")
            );
            started.send(()).unwrap();
            response.await.unwrap();
            use tokio::io::AsyncWriteExt;
            socket
                .write_all(
                    b"HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                )
                .await
                .unwrap();
        });
        let first = bridge.clone();
        let first =
            tokio::spawn(async move { first.resume_desktop("test".into(), test_mobile()).await });
        waiting.await.unwrap();
        let second = bridge.clone();
        let second =
            tokio::spawn(async move { second.resume_desktop("test".into(), test_mobile()).await });
        timeout(Duration::from_secs(2), async {
            loop {
                if bridge
                    .state
                    .lock()
                    .unwrap()
                    .pending
                    .as_ref()
                    .is_some_and(|p| p.waiters == 2)
                {
                    break;
                }
                tokio::task::yield_now().await;
            }
        })
        .await
        .unwrap();
        first.abort();
        let _ = first.await;
        assert!(
            !*bridge
                .state
                .lock()
                .unwrap()
                .pending
                .as_ref()
                .unwrap()
                .cancel
                .borrow()
        );
        respond.send(()).unwrap();
        assert!(second.await.unwrap().unwrap().is_none());
        server.await.unwrap();
    }

    #[tokio::test]
    async fn newest_manual_selection_supersedes_a_queued_selection() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let bridge = Arc::new(DesktopConnectBridge::new(url.clone(), url).unwrap());
        let (started, waiting) = oneshot::channel();
        let (respond, response) = oneshot::channel();
        let server = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            let request = http_request(&mut socket).await;
            assert!(request.starts_with("POST /v1/leases ") && request.contains("desktop-1"));
            started.send(()).unwrap();
            response.await.unwrap();
            http_json(&mut socket, r#"{"id":"lease-old","ticket":"test","desktopId":"desktop-1","endpointId":"0000000000000000000000000000000000000000000000000000000000000000","relayUrl":null,"expiresAt":1234}"#).await;
            let (mut socket, _) = listener.accept().await.unwrap();
            assert!(
                http_request(&mut socket)
                    .await
                    .starts_with("DELETE /v1/leases/lease-old ")
            );
            http_json(&mut socket, r#"{"ok":true}"#).await;
            let (mut socket, _) = listener.accept().await.unwrap();
            let request = http_request(&mut socket).await;
            assert!(request.starts_with("POST /v1/leases ") && request.contains("desktop-3"));
            use tokio::io::AsyncWriteExt;
            socket
                .write_all(
                    b"HTTP/1.1 409 Conflict\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}",
                )
                .await
                .unwrap();
        });
        let first = bridge.clone();
        let first = tokio::spawn(async move {
            first
                .connect_desktop("test".into(), test_mobile(), "desktop-1".into())
                .await
        });
        waiting.await.unwrap();
        let second = bridge.clone();
        let second = tokio::spawn(async move {
            second
                .connect_desktop("test".into(), test_mobile(), "desktop-2".into())
                .await
        });
        timeout(Duration::from_secs(2), async {
            loop {
                if bridge
                    .state
                    .lock()
                    .unwrap()
                    .preferred
                    .as_ref()
                    .is_some_and(|(_, id)| id == "desktop-2")
                {
                    break;
                }
                tokio::task::yield_now().await;
            }
        })
        .await
        .unwrap();
        let third = bridge.clone();
        let third = tokio::spawn(async move {
            third
                .connect_desktop("test".into(), test_mobile(), "desktop-3".into())
                .await
        });
        timeout(Duration::from_secs(2), async {
            loop {
                if bridge
                    .state
                    .lock()
                    .unwrap()
                    .preferred
                    .as_ref()
                    .is_some_and(|(_, id)| id == "desktop-3")
                {
                    break;
                }
                tokio::task::yield_now().await;
            }
        })
        .await
        .unwrap();
        respond.send(()).unwrap();
        assert!(is_connect_cancelled(&first.await.unwrap().unwrap_err()));
        assert!(is_connect_cancelled(&second.await.unwrap().unwrap_err()));
        assert!(
            matches!(third.await.unwrap(), Err(ClientError::Rpc(message)) if message.contains("409"))
        );
        server.await.unwrap();
        assert!(bridge.active_desktop().await.is_none());
    }

    #[tokio::test]
    async fn superseded_acquisition_error_does_not_block_new_selection() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let bridge = Arc::new(DesktopConnectBridge::new(url.clone(), url).unwrap());
        let (started, waiting) = oneshot::channel();
        let (respond, response) = oneshot::channel();
        let server = tokio::spawn(async move {
            let (mut socket, _) = listener.accept().await.unwrap();
            assert!(http_request(&mut socket).await.contains("desktop-1"));
            started.send(()).unwrap();
            response.await.unwrap();
            use tokio::io::AsyncWriteExt;
            socket.write_all(b"HTTP/1.1 429 Too Many Requests\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}").await.unwrap();
            let (mut socket, _) = listener.accept().await.unwrap();
            assert!(http_request(&mut socket).await.contains("desktop-2"));
            socket.write_all(b"HTTP/1.1 422 Unprocessable Entity\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}").await.unwrap();
        });
        let first = bridge.clone();
        let first = tokio::spawn(async move {
            first
                .connect_desktop("test".into(), test_mobile(), "desktop-1".into())
                .await
        });
        waiting.await.unwrap();
        let second = bridge.clone();
        let second = tokio::spawn(async move {
            second
                .connect_desktop("test".into(), test_mobile(), "desktop-2".into())
                .await
        });
        timeout(Duration::from_secs(2), async {
            loop {
                if bridge
                    .state
                    .lock()
                    .unwrap()
                    .preferred
                    .as_ref()
                    .is_some_and(|(_, id)| id == "desktop-2")
                {
                    break;
                }
                tokio::task::yield_now().await;
            }
        })
        .await
        .unwrap();
        respond.send(()).unwrap();
        assert!(is_connect_cancelled(&first.await.unwrap().unwrap_err()));
        let selected = second.await.unwrap();
        server.abort();
        assert!(
            matches!(selected, Err(ClientError::Rpc(message)) if message.contains("422")),
            "new selection must not inherit an old acquisition error"
        );
    }
}

#[cfg(test)]
mod session_state_tests {
    use super::*;
    #[test]
    fn session_model_is_not_replaced_by_another_provider() {
        let catalog = r#"{"models":[{"providerID":"ne","modelID":"default","name":"Default"},{"providerID":"paid","modelID":"current","name":"Current"}],"default":{"providerID":"ne","modelID":"default"}}"#;
        let choice = select_desktop_model(
            catalog.into(),
            Some(r#"{"providerID":"paid","modelID":"current"}"#.into()),
            None,
        )
        .unwrap();
        assert_eq!(choice.selected.unwrap().provider_id, "paid");
        let missing = select_desktop_model(
            catalog.into(),
            Some(r#"{"providerID":"paid","modelID":"disabled"}"#.into()),
            None,
        )
        .unwrap();
        assert!(missing.selected.is_none());
        assert!(missing.warning.is_some());
    }
    #[test]
    fn new_session_requires_a_real_default_or_user_choice() {
        let catalog = r#"{"models":[{"providerID":"ne","modelID":"one","name":"One"},{"providerID":"paid","modelID":"two","name":"Two"}]}"#;
        assert!(
            select_desktop_model(catalog.into(), None, None)
                .unwrap()
                .selected
                .is_none()
        );
        assert_eq!(
            select_desktop_model(
                catalog.into(),
                None,
                Some(r#"{"providerID":"paid","modelID":"two"}"#.into())
            )
            .unwrap()
            .selected
            .unwrap()
            .model_id,
            "two"
        );
    }
    #[test]
    fn pending_prompts_reuse_exact_body_and_cannot_move_between_hosts_or_sessions() {
        let body = json!({"parts":[{"type":"text","text":"hello"}],"model":{"providerID":"paid","modelID":"model"}}).to_string();
        let first = prepare_desktop_prompt(
            "account".into(),
            "host".into(),
            "ses_1".into(),
            body.clone(),
            None,
        )
        .unwrap();
        assert!(first.message_id.starts_with("msg_"));
        let retry = prepare_desktop_prompt(
            "account".into(),
            "host".into(),
            "ses_1".into(),
            body.clone(),
            Some(first.journal_json.clone()),
        )
        .unwrap();
        assert_eq!(retry.message_id, first.message_id);
        assert_eq!(retry.body_json, first.body_json);
        let restored = restore_desktop_prompt(
            "account".into(),
            "host".into(),
            "ses_1".into(),
            first.journal_json.clone(),
        )
        .unwrap();
        assert_eq!(restored.message_id, first.message_id);
        assert!(
            restore_desktop_prompt(
                "other".into(),
                "host".into(),
                "ses_1".into(),
                first.journal_json.clone()
            )
            .is_err()
        );
        assert!(
            restore_desktop_prompt(
                "account".into(),
                "other".into(),
                "ses_1".into(),
                first.journal_json.clone()
            )
            .is_err()
        );
        assert!(
            restore_desktop_prompt(
                "account".into(),
                "host".into(),
                "ses_other".into(),
                first.journal_json.clone()
            )
            .is_err()
        );
        let mut changed: Value = serde_json::from_str(&body).unwrap();
        changed["parts"][0]["text"] = json!("different prompt");
        assert!(
            prepare_desktop_prompt(
                "account".into(),
                "host".into(),
                "ses_1".into(),
                changed.to_string(),
                Some(first.journal_json)
            )
            .is_err()
        );
    }

    #[test]
    fn accepted_prompt_only_clears_the_matching_draft() {
        let body = json!({"parts":[{"type":"text","text":"original"},{"type":"file","url":"data:image/png;base64,abc"}]}).to_string();
        assert!(
            desktop_prompt_matches_draft(
                body.clone(),
                "original".into(),
                vec!["data:image/png;base64,abc".into()]
            )
            .unwrap()
        );
        assert!(
            !desktop_prompt_matches_draft(
                body.clone(),
                "new text".into(),
                vec!["data:image/png;base64,abc".into()]
            )
            .unwrap()
        );
        assert!(!desktop_prompt_matches_draft(body, "original".into(), vec![]).unwrap());
        assert!(
            desktop_prompt_matches_draft(
                r#"{"parts":[{"type":"file","url":"file"}]}"#.into(),
                "   ".into(),
                vec!["file".into()]
            )
            .unwrap()
        );
    }

    #[test]
    fn only_confirmed_missing_non_retryable_rejections_release_pending_prompt() {
        let missing = json!({"sessionID":"s1","messageID":"m1","state":"missing","idempotent":true}).to_string();
        assert!(desktop_prompt_rejection_confirmed(400, missing.clone(), "s1".into(), "m1".into()).unwrap());
        for status in [200,408,409,429,500,502,503] { assert!(!desktop_prompt_rejection_confirmed(status, missing.clone(), "s1".into(), "m1".into()).unwrap()); }
        let admitted = json!({"sessionID":"s1","messageID":"m1","state":"admitted","idempotent":true}).to_string();
        assert!(!desktop_prompt_rejection_confirmed(403,admitted,"s1".into(),"m1".into()).unwrap());
        assert!(desktop_prompt_rejection_confirmed(400,missing,"s2".into(),"m1".into()).is_err());
    }

    #[test]
    fn prompt_receipts_require_the_exact_prompt_and_session_identity() {
        let receipt =
            json!({"sessionID":"ses_1","messageID":"msg_1","state":"admitted","idempotent":true});
        let parsed =
            parse_desktop_prompt_receipt(receipt.to_string(), "ses_1".into(), "msg_1".into())
                .unwrap();
        assert!(matches!(parsed.state, DesktopPromptState::Admitted));
        assert!(parsed.idempotent);
        assert!(
            parse_desktop_prompt_receipt(receipt.to_string(), "ses_2".into(), "msg_1".into())
                .is_err()
        );
        assert!(
            parse_desktop_prompt_receipt(receipt.to_string(), "ses_1".into(), "msg_2".into())
                .is_err()
        );
        assert!(parse_desktop_prompt_receipt("{}".into(), "ses_1".into(), "msg_1".into()).is_err());
    }

    #[test]
    fn message_pages_merge_by_stable_id_without_losing_loaded_history() {
        let prior = r#"[{"info":{"id":"msg_old"},"parts":[]},{"info":{"id":"msg_live"},"parts":[{"type":"text","text":"First"}]}]"#;
        let page = r#"{"data":[{"info":{"id":"msg_live"},"parts":[{"type":"text","text":"Updated"}]},{"info":{"id":"msg_next"},"parts":[]}],"nextCursor":"older"}"#;
        let result = merge_desktop_message_page(prior.into(), page.into(), false).unwrap();
        let rows: Value = serde_json::from_str(&result.messages_json).unwrap();
        assert_eq!(rows.as_array().unwrap().len(), 3);
        assert_eq!(rows[0]["info"]["id"], "msg_old");
        assert_eq!(rows[1]["parts"][0]["text"], "Updated");
        let older = r#"{"data":[{"info":{"id":"msg_ancient"},"parts":[]},{"info":{"id":"msg_old"},"parts":[]}],"nextCursor":null}"#;
        let result = merge_desktop_message_page(result.messages_json, older.into(), true).unwrap();
        let rows: Value = serde_json::from_str(&result.messages_json).unwrap();
        assert_eq!(rows.as_array().unwrap().len(), 4);
        assert_eq!(rows[0]["info"]["id"], "msg_ancient");
        assert!(result.next_cursor.is_none());
        assert!(
            merge_desktop_message_page("[]".into(), r#"{"data":[{"parts":[]}]}"#.into(), false)
                .is_err()
        );
    }

    #[test]
    fn projection_overlays_live_text_without_duplicates_or_overwriting_completed_history() {
        let rows = json!([{"info":{"id":"user","role":"user"},"parts":[{"id":"q","type":"text","text":"问题"}]},
            {"info":{"id":"reply","role":"assistant"},"parts":[{"id":"answer","type":"text","text":"旧"}]}]);
        let live = json!({"revision":"epoch:1","parts":[{"messageID":"reply","id":"answer","text":"真实增量", "active":true}]});
        let projected: Value = serde_json::from_str(&project_desktop_messages(json!({"messages":rows,"live":live}).to_string()).unwrap()).unwrap();
        assert_eq!(projected.as_array().unwrap().len(), 2);
        assert_eq!(projected[1]["id"], "reply:answer");
        assert_eq!(projected[1]["text"], "真实增量");
        let mut completed = rows.clone(); completed[1]["info"]["time"] = json!({"completed":100});
        let projected: Value = serde_json::from_str(&project_desktop_messages(json!({"messages":completed,"live":live}).to_string()).unwrap()).unwrap();
        assert_eq!(projected[1]["text"], "旧");
        let missing = json!({"revision":"epoch:2","parts":[{"messageID":"not-yet-saved","id":"new","text":"正在输出", "active":true},
            {"messageID":"older-than-page","id":"old","text":"旧内容", "active":false}]});
        let projected: Value = serde_json::from_str(&project_desktop_messages(json!({"messages":[rows[0]],"live":missing}).to_string()).unwrap()).unwrap();
        assert_eq!(projected.as_array().unwrap().len(), 2);
        assert_eq!(projected[1]["id"], "not-yet-saved:new");
    }

    #[test]
    fn projection_places_user_attachments_before_question_without_reordering_assistant() {
        let rows = json!([
            {"info":{"id":"user","role":"user"},"parts":[
                {"id":"question","type":"text","text":"这是什么？"},
                {"id":"photo","type":"file","mime":"image/png","filename":"图.png"},
                {"id":"pdf","type":"file","mime":"application/pdf","filename":"文档.pdf"}]},
            {"info":{"id":"reply","role":"assistant"},"parts":[
                {"id":"text","type":"text","text":"答复"},
                {"id":"file","type":"file","mime":"image/png","filename":"结果.png"}]}]);
        let projected: Value = serde_json::from_str(&project_desktop_messages(rows.to_string()).unwrap()).unwrap();
        assert_eq!(projected.as_array().unwrap().iter().map(|part| part["id"].as_str().unwrap()).collect::<Vec<_>>(),
            vec!["user:photo", "user:pdf", "user:question", "reply:text", "reply:file"]);
    }


    #[test]
    fn projection_preserves_completed_artifacts_next_to_grouped_tools() {
        let rows = json!([{"info":{"id":"a1","role":"assistant"},"parts":[
            {"id":"write","type":"tool","tool":"write","state":{"status":"completed"}},
            {"id":"art","type":"tool","tool":"artifact","state":{"status":"completed","metadata":{"artifacts":[
                {"path":"D:\\project\\路线.html","name":"路线.html","mime":"text/html","size":99},
                {"path":"D:/project/路线.html","name":"重复.html","mime":"text/html","size":99},
                {"path":"","name":"无效","mime":"text/html"}
            ]}}},
            {"id":"pending","type":"tool","tool":"artifact","state":{"status":"running","metadata":{"artifacts":[{"path":"x","name":"x","mime":"text/plain"}]}}},
            {"id":"text","type":"text","text":"完成"}
        ]}]);
        let projected: Value = serde_json::from_str(&project_desktop_messages(rows.to_string()).unwrap()).unwrap();
        assert_eq!(projected.as_array().unwrap().len(), 4);
        assert_eq!(projected[0]["details"].as_array().unwrap().len(), 2);
        assert_eq!(projected[1]["kind"], "file");
        assert_eq!(projected[1]["attachment"]["filename"], "路线.html");
        assert_eq!(projected[1]["attachment"]["path"], "D:\\project\\路线.html");
        assert!(projected[1]["attachment"]["data_base64"].is_null());
        assert!(projected[1]["attachment"]["unavailable_reason"].is_null());
        assert_eq!(projected[2]["kind"], "tool");
        assert_eq!(projected[3]["text"], "完成");
        let repeat: Value = serde_json::from_str(&project_desktop_messages(rows.to_string()).unwrap()).unwrap();
        assert_eq!(projected[1]["id"], repeat[1]["id"]);
    }

    #[test]
    fn projection_preserves_attachment_preview_without_changing_file_identity() {
        let rows = json!([{"info":{"id":"u1","role":"user"},"parts":[
            {"id":"pic","type":"file","filename":"photo.jpg","mime":"image/jpeg","url":"data:image/jpeg;base64,YWJj"},
            {"id":"txt","type":"file","filename":"note.txt","mime":"text/plain","url":"data:text/plain;base64,aGVsbG8="},
            {"id":"doc","type":"file","ignored":true,"filename":"report.pdf","mime":"application/pdf","url":"data:application/pdf;base64,YWJj"}
        ]}]);
        let projected: Value = serde_json::from_str(&project_desktop_messages(rows.to_string()).unwrap()).unwrap();
        assert_eq!(projected[0]["id"], "u1:pic");
        assert_eq!(projected[0]["kind"], "file");
        assert_eq!(projected[0]["attachment"]["data_base64"], "YWJj");
        assert_eq!(projected[0]["attachment"]["size"], 3);
        assert_eq!(projected[1]["attachment"]["mime"], "text/plain");
        assert_eq!(projected[2]["attachment"]["filename"], "report.pdf");
    }

    #[test]
    fn projection_reports_unavailable_attachments_without_exposing_local_urls() {
        let rows = json!([{"info":{"id":"u1","role":"user"},"parts":[
            {"id":"local","type":"file","filename":"report.pdf","mime":"application/pdf","url":"file:///private/report.pdf"},
            {"id":"broken","type":"file","filename":"broken.png","mime":"image/png","url":"data:image/png;base64,??"},
            {"id":"oversized","type":"file","filename":"large.pdf","mime":"application/pdf","url":format!("data:application/pdf;base64,{}", "A".repeat(6_000_000))},
            {"id":"hidden","type":"text","ignored":true,"text":"hidden"}
        ]}]);
        let projected: Value = serde_json::from_str(&project_desktop_messages(rows.to_string()).unwrap()).unwrap();
        assert_eq!(projected.as_array().unwrap().len(), 3);
        for row in projected.as_array().unwrap() {
            assert!(row["attachment"]["data_base64"].is_null());
            assert!(row["attachment"]["unavailable_reason"].as_str().unwrap().len() > 0);
        }
        assert!(!projected.to_string().contains("file:///private"));
    }

    #[test]
    fn projection_groups_tools_only_within_the_same_user_turn_and_preserves_failures() {
        let rows = json!([
            {"info":{"id":"u1","role":"user"},"parts":[{"id":"ut","type":"text","text":"one"}]},
            {"info":{"id":"a1","role":"assistant","parentID":"u1"},"parts":[{"id":"t1","type":"tool","tool":"read","state":{"status":"completed","title":"read file"}}]},
            {"info":{"id":"a2","role":"assistant","parentID":"u1"},"parts":[{"id":"t2","type":"tool","tool":"edit","state":{"status":"error","error":"permission denied"}}]},
            {"info":{"id":"a3","role":"assistant","parentID":"u2"},"parts":[{"id":"t3","type":"tool","tool":"list","state":{"status":"running"}}]},
            {"info":{"id":"a4","role":"assistant","parentID":"u2","error":{"name":"ProviderError","data":{"message":"quota exhausted"}}},"parts":[]}
        ]);
        let projected: Value = serde_json::from_str(&project_desktop_messages(rows.to_string()).unwrap()).unwrap();
        let projected = projected.as_array().unwrap();
        assert_eq!(projected.len(), 4);
        assert_eq!(projected[1]["kind"], "tool");
        assert_eq!(projected[1]["status"], "error");
        assert_eq!(projected[1]["details"].as_array().unwrap().len(), 2);
        assert!(projected[1]["details"][1].as_str().unwrap().contains("permission denied"));
        assert_ne!(projected[1]["id"], projected[2]["id"]);
        assert_eq!(projected[3]["text"], "quota exhausted");
        assert_eq!(project_desktop_messages(rows.to_string()).unwrap(), project_desktop_messages(rows.to_string()).unwrap());
    }

    #[test]
    fn session_attention_is_scoped_and_idle_never_claims_prompt_completion() {
        let state: Value = serde_json::from_str(&desktop_session_attention(
            r#"{"s1":{"type":"retry","message":"rate limit"},"s2":{"type":"busy"}}"#.into(),
            r#"[{"id":"p1","sessionID":"s1","action":"edit","resources":["file"],"requestSource":"v2"},{"id":"p2","sessionID":"s2"}]"#.into(),
            r#"[{"id":"q1","sessionID":"s1","questions":[{"question":"Which?","header":"choice","options":[{"label":"A","description":"a"}]}]}]"#.into(), "s1".into()).unwrap()).unwrap();
        assert_eq!(state["status"], "retry");
        assert_eq!(state["label"], "等待模型重试：rate limit");
        assert_eq!(state["permissions"].as_array().unwrap().len(), 1);
        assert_eq!(state["permissions"][0]["requestSource"], "v2");
        assert_eq!(state["permissions"][0]["permission"], "edit");
        assert_eq!(state["permissions"][0]["patterns"], json!(["file"]));
        assert_eq!(state["questions"][0]["requestSource"], "legacy");
        let idle: Value = serde_json::from_str(&desktop_session_attention("{}".into(),"[]".into(),"[]".into(),"s1".into()).unwrap()).unwrap();
        assert_eq!(idle["status"],"idle");
        assert!(idle.get("completed").is_none());
        assert!(desktop_session_attention("[]".into(),"[]".into(),"[]".into(),"s1".into()).is_err());
    }

    #[test]
    fn attention_rejects_present_malformed_status_and_request_source() {
        for status in ["null", "{}", r#"{"type":1}"#, r#"{"type":"unknown"}"#] {
            assert!(desktop_session_attention(format!(r#"{{"s1":{status}}}"#), "[]".into(), "[]".into(), "s1".into()).is_err());
        }
        for source in ["null", "2", r#""unknown""#] {
            assert!(desktop_session_attention("{}".into(), format!(r#"[{{"id":"p1","sessionID":"s1","requestSource":{source}}}]"#), "[]".into(), "s1".into()).is_err());
        }
    }

    #[test]
    fn message_tail_without_overlap_requires_backfill_before_display() {
        let previous = json!([{"info":{"id":"m1"},"parts":[]}]);
        let tail = json!({"data":[{"info":{"id":"m4"},"parts":[]}],"nextCursor":"m4"});
        assert_eq!(desktop_message_gap_cursor(previous.to_string(), tail.to_string()).unwrap(),Some("m4".into()));
        let overlap = json!({"data":[{"info":{"id":"m1"}},{"info":{"id":"m2"}}],"nextCursor":"m1"});
        assert!(desktop_message_gap_cursor(previous.to_string(),overlap.to_string()).unwrap().is_none());
        assert!(desktop_message_gap_cursor("[]".into(),tail.to_string()).unwrap().is_none());
        assert!(desktop_message_gap_cursor(previous.to_string(),r#"{"data":[],"nextCursor":null}"#.into()).unwrap().is_none());
    }

    #[test]
    fn large_message_gap_can_stage_across_multiple_bounded_batches_without_losing_rows() {
        let row = |index| json!({"info":{"id":format!("m{index:03}")},"parts":[]});
        let previous = serde_json::to_string(&(1..=10).map(row).collect::<Vec<_>>()).unwrap();
        let mut staged = json!({"data":(191..=220).map(row).collect::<Vec<_>>(),"nextCursor":"m191"});
        // After the first bounded batch the visible history is still intact, not joined across a hole.
        for (page, start) in [161, 131, 101, 71, 41, 11, 1].into_iter().enumerate() {
            let next = (start != 1).then(|| format!("m{start:03}"));
            let chunk = json!({"data":(start..start+30).map(row).collect::<Vec<_>>(),"nextCursor":next});
            let merged = merge_desktop_message_page(
                merge_desktop_message_page("[]".into(), staged.to_string(), false).unwrap().messages_json,
                chunk.to_string(), true).unwrap();
            staged = json!({"data":serde_json::from_str::<Value>(&merged.messages_json).unwrap(),"nextCursor":merged.next_cursor});
            let gap = desktop_message_gap_cursor(previous.clone(), staged.to_string()).unwrap();
            assert_eq!(gap.is_some(), page < 6);
            if page == 3 { assert_eq!(gap.as_deref(), Some("m071")); }
        }
        let merged = merge_desktop_message_page(previous, staged.to_string(), false).unwrap();
        let rows: Vec<Value> = serde_json::from_str(&merged.messages_json).unwrap();
        assert_eq!(rows.len(), 220);
        for (index, item) in rows.iter().enumerate() { assert_eq!(item["info"]["id"], row(index + 1)["info"]["id"]); }
    }

    #[test]
    fn tool_output_or_old_response_does_not_finish_a_new_prompt() {
        let rows = r#"[{"info":{"id":"msg_old","role":"assistant","parentID":"msg_old_user","finish":"stop","time":{"completed":1}},"parts":[]},{"info":{"id":"msg_new","role":"user"},"parts":[]},{"info":{"id":"msg_work","role":"assistant","parentID":"msg_new","finish":"tool-calls","time":{"completed":2}},"parts":[{"type":"tool","id":"prt_tool","tool":"read","state":{"status":"completed"}}]}]"#;
        assert!(!desktop_prompt_settled(rows.into(), "msg_new".into()).unwrap());
        let mut rows: Value = serde_json::from_str(rows).unwrap();
        rows.as_array_mut().unwrap().push(json!({"info":{"id":"msg_answer","role":"assistant","parentID":"msg_new","finish":"stop","time":{"completed":3}},"parts":[{"id":"prt_text","type":"text","text":"Done"}]}));
        assert!(desktop_prompt_settled(rows.to_string(), "msg_new".into()).unwrap());
        assert!(!desktop_prompt_settled(rows.to_string(), "msg_other".into()).unwrap());
    }
}

#[derive(Clone, uniffi::Record)]
pub struct DesktopModelOption {
    pub provider_id: String,
    pub model_id: String,
    pub name: String,
    pub image: bool,
    pub provider_name: String,
}
#[derive(uniffi::Record)]
pub struct DesktopModelSelection {
    pub options: Vec<DesktopModelOption>,
    pub selected: Option<DesktopModelOption>,
    pub warning: Option<String>,
}

#[uniffi::export]
pub fn select_desktop_model(
    models_json: String,
    session_model_json: Option<String>,
    picked_model_json: Option<String>,
) -> Result<DesktopModelSelection, ClientError> {
    let catalog: Value = serde_json::from_str(&models_json)
        .map_err(|e| ClientError::Serialization(e.to_string()))?;
    let rows = catalog
        .get("models")
        .and_then(Value::as_array)
        .ok_or_else(|| ClientError::Serialization("Invalid desktop model catalog".into()))?;
    let options = rows
        .iter()
        .map(|row| {
            let required = |key: &str| {
                row.get(key)
                    .and_then(Value::as_str)
                    .filter(|s| !s.is_empty())
                    .ok_or_else(|| {
                        ClientError::Serialization(format!("Desktop model missing {key}"))
                    })
            };
            Ok(DesktopModelOption {
                provider_id: required("providerID")?.into(),
                model_id: required("modelID")?.into(),
                name: required("name")?.into(),
                image: row["image"].as_bool().unwrap_or(false),
                provider_name: row["providerName"]
                    .as_str()
                    .unwrap_or(required("providerID")?)
                    .into(),
            })
        })
        .collect::<Result<Vec<_>, ClientError>>()?;
    let parse = |raw: String| {
        serde_json::from_str::<Value>(&raw).map_err(|e| ClientError::Serialization(e.to_string()))
    };
    let picked = picked_model_json.map(parse).transpose()?;
    let current = session_model_json.map(parse).transpose()?;
    let desired = picked
        .as_ref()
        .or(current.as_ref())
        .or_else(|| catalog.get("default").filter(|v| !v.is_null()));
    let selected = desired
        .and_then(|key| {
            options.iter().find(|model| {
                key["providerID"].as_str() == Some(model.provider_id.as_str())
                    && key
                        .get("modelID")
                        .or_else(|| key.get("id"))
                        .and_then(Value::as_str)
                        == Some(model.model_id.as_str())
            })
        })
        .cloned();
    // Never replace a configured session model with the first model from a different provider.
    let warning = if selected.is_none() && desired.is_some() {
        Some("当前会话模型未在桌面端开启，请选择可用模型".into())
    } else {
        None
    };
    Ok(DesktopModelSelection {
        options,
        selected,
        warning,
    })
}

#[uniffi::export]
pub fn desktop_prompt_settled(
    messages_json: String,
    prompt_id: String,
) -> Result<bool, ClientError> {
    let rows: Value = serde_json::from_str(&messages_json)
        .map_err(|e| ClientError::Serialization(e.to_string()))?;
    let rows = rows
        .as_array()
        .ok_or_else(|| ClientError::Serialization("Invalid desktop message list".into()))?;
    // parentID ties a response to this admitted prompt; old responses and tool turns are not completion.
    let last = rows
        .iter()
        .rev()
        .find(|row| row["info"]["role"] == "assistant" && row["info"]["parentID"] == prompt_id);
    Ok(last.is_some_and(|row| {
        let info = &row["info"];
        if info.get("error").is_some_and(|error| !error.is_null()) {
            return true;
        }
        info["time"]["completed"].as_u64().is_some()
            && info["finish"]
                .as_str()
                .is_some_and(|finish| !matches!(finish, "tool-calls" | "unknown"))
    }))
}

#[derive(uniffi::Record)]
pub struct DesktopMessagePage {
    pub messages_json: String,
    pub next_cursor: Option<String>,
}

#[uniffi::export]
pub fn merge_desktop_message_page(
    previous_json: String,
    page_json: String,
    older: bool,
) -> Result<DesktopMessagePage, ClientError> {
    let previous: Vec<Value> = serde_json::from_str(&previous_json)
        .map_err(|e| ClientError::Serialization(e.to_string()))?;
    let page: Value =
        serde_json::from_str(&page_json).map_err(|e| ClientError::Serialization(e.to_string()))?;
    let rows = page
        .get("data")
        .and_then(Value::as_array)
        .ok_or_else(|| ClientError::Serialization("Invalid desktop message page".into()))?;
    let next_cursor = match page.get("nextCursor") {
        Some(Value::Null) => None,
        Some(Value::String(cursor)) if !cursor.is_empty() => Some(cursor.clone()),
        _ => {
            return Err(ClientError::Serialization(
                "Invalid desktop message cursor".into(),
            ));
        }
    };
    let id = |row: &Value| {
        row["info"]["id"]
            .as_str()
            .filter(|s| !s.is_empty())
            .map(str::to_owned)
            .ok_or_else(|| ClientError::Serialization("Desktop message missing stable ID".into()))
    };
    let mut result = Vec::<Value>::new();
    let mut positions = std::collections::HashMap::<String, usize>::new();
    // Older pages can overlap newer rows. Preserve the newer snapshot on overlap.
    let first = if older {
        rows.as_slice()
    } else {
        previous.as_slice()
    };
    let second = if older {
        previous.as_slice()
    } else {
        rows.as_slice()
    };
    for row in first.iter().chain(second) {
        let key = id(row)?;
        if let Some(position) = positions.get(&key) {
            result[*position] = row.clone();
        } else {
            positions.insert(key, result.len());
            result.push(row.clone());
        }
    }
    Ok(DesktopMessagePage {
        messages_json: serde_json::to_string(&result)
            .map_err(|e| ClientError::Serialization(e.to_string()))?,
        next_cursor,
    })
}

#[derive(uniffi::Record)]
pub struct DesktopPromptAttempt {
    pub message_id: String,
    pub body_json: String,
    pub journal_json: String,
}
#[derive(Debug, serde::Deserialize, uniffi::Enum)]
#[serde(rename_all = "lowercase")]
pub enum DesktopPromptState {
    Missing,
    Admitted,
    Promoted,
    Discarded,
}
#[derive(serde::Deserialize, uniffi::Record)]
pub struct DesktopPromptReceipt {
    pub state: DesktopPromptState,
    pub idempotent: bool,
}
#[derive(serde::Serialize, serde::Deserialize)]
#[serde(deny_unknown_fields)]
struct DesktopPromptJournal {
    account_id: String,
    desktop_id: String,
    session_id: String,
    body: Value,
}

#[uniffi::export]
pub fn prepare_desktop_prompt(
    account_id: String,
    desktop_id: String,
    session_id: String,
    body_json: String,
    pending_json: Option<String>,
) -> Result<DesktopPromptAttempt, ClientError> {
    if account_id.is_empty() || desktop_id.is_empty() || !session_id.starts_with("ses_") {
        return Err(ClientError::InvalidParams("Invalid prompt owner".into()));
    }
    if body_json.len() >= 4_000_000 {
        return Err(ClientError::InvalidParams("Prompt exceeds 4 MB".into()));
    }
    let mut body: Value =
        serde_json::from_str(&body_json).map_err(|e| ClientError::Serialization(e.to_string()))?;
    if body["parts"].as_array().is_none_or(Vec::is_empty) || !body.is_object() {
        return Err(ClientError::InvalidParams(
            "Prompt parts are required".into(),
        ));
    }
    body.as_object_mut().unwrap().remove("messageID");
    if let Some(pending_json) = pending_json {
        let attempt = restore_desktop_prompt(account_id, desktop_id, session_id, pending_json)?;
        let mut previous: Value = serde_json::from_str(&attempt.body_json)
            .map_err(|e| ClientError::Serialization(e.to_string()))?;
        previous.as_object_mut().unwrap().remove("messageID");
        if previous != body {
            return Err(ClientError::InvalidParams(
                "上一条发送结果尚未确认，请先核对，不能用新内容覆盖待确认消息".into(),
            ));
        }
        return Ok(attempt);
    }
    let message_id = format!("msg_{}", uuid::Uuid::new_v4().simple());
    body["messageID"] = json!(message_id);
    let journal = DesktopPromptJournal {
        account_id,
        desktop_id,
        session_id,
        body,
    };
    Ok(DesktopPromptAttempt {
        message_id,
        body_json: serde_json::to_string(&journal.body)
            .map_err(|e| ClientError::Serialization(e.to_string()))?,
        journal_json: serde_json::to_string(&journal)
            .map_err(|e| ClientError::Serialization(e.to_string()))?,
    })
}

#[uniffi::export]
pub fn restore_desktop_prompt(
    account_id: String,
    desktop_id: String,
    session_id: String,
    pending_json: String,
) -> Result<DesktopPromptAttempt, ClientError> {
    if pending_json.len() >= 4_001_000 {
        return Err(ClientError::InvalidParams(
            "Invalid prompt journal size".into(),
        ));
    }
    let journal: DesktopPromptJournal = serde_json::from_str(&pending_json)
        .map_err(|e| ClientError::Serialization(e.to_string()))?;
    if journal.account_id != account_id
        || journal.desktop_id != desktop_id
        || journal.session_id != session_id
    {
        return Err(ClientError::InvalidParams(
            "待确认消息不属于当前账号、电脑或会话".into(),
        ));
    }
    let message_id = journal.body["messageID"]
        .as_str()
        .filter(|id| id.starts_with("msg_") && id.len() > 4)
        .ok_or_else(|| ClientError::Serialization("Invalid pending prompt ID".into()))?
        .to_owned();
    if journal.body["parts"].as_array().is_none_or(Vec::is_empty) {
        return Err(ClientError::Serialization(
            "Invalid pending prompt parts".into(),
        ));
    }
    Ok(DesktopPromptAttempt {
        message_id,
        body_json: serde_json::to_string(&journal.body)
            .map_err(|e| ClientError::Serialization(e.to_string()))?,
        journal_json: pending_json,
    })
}

#[uniffi::export]
pub fn parse_desktop_prompt_receipt(
    receipt_json: String,
    session_id: String,
    message_id: String,
) -> Result<DesktopPromptReceipt, ClientError> {
    let receipt: Value = serde_json::from_str(&receipt_json)
        .map_err(|e| ClientError::Serialization(e.to_string()))?;
    if receipt["sessionID"].as_str() != Some(&session_id)
        || receipt["messageID"].as_str() != Some(&message_id)
    {
        return Err(ClientError::Serialization(
            "Prompt receipt belongs to another session or prompt".into(),
        ));
    }
    serde_json::from_value(receipt).map_err(|e| ClientError::Serialization(e.to_string()))
}

#[uniffi::export]
pub fn desktop_prompt_matches_draft(
    body_json: String,
    text: String,
    file_urls: Vec<String>,
) -> Result<bool, ClientError> {
    let body: Value =
        serde_json::from_str(&body_json).map_err(|e| ClientError::Serialization(e.to_string()))?;
    let parts = body["parts"]
        .as_array()
        .ok_or_else(|| ClientError::Serialization("Invalid pending parts".into()))?;
    let sent_text = parts
        .iter()
        .filter(|p| p["type"] == "text")
        .filter_map(|p| p["text"].as_str())
        .collect::<Vec<_>>()
        .join("\n");
    let sent_files = parts
        .iter()
        .filter(|p| p["type"] == "file")
        .map(|p| p["url"].as_str().unwrap_or_default().to_owned())
        .collect::<Vec<_>>();
    Ok(sent_text == (if text.trim().is_empty() { "" } else { &text }) && sent_files == file_urls)
}

#[uniffi::export]
pub fn desktop_message_gap_cursor(previous_json: String, page_json: String) -> Result<Option<String>, ClientError> {
    let previous: Vec<Value> = serde_json::from_str(&previous_json).map_err(|e| ClientError::Serialization(e.to_string()))?;
    let page: Value = serde_json::from_str(&page_json).map_err(|e| ClientError::Serialization(e.to_string()))?;
    let rows = page["data"].as_array().ok_or_else(|| ClientError::Serialization("Invalid desktop message page".into()))?;
    let ids: std::collections::HashSet<&str> = previous.iter().filter_map(|row| row["info"]["id"].as_str()).collect();
    if previous.is_empty() || rows.iter().any(|row| row["info"]["id"].as_str().is_some_and(|id| ids.contains(id))) { return Ok(None); }
    match page.get("nextCursor") {
        Some(Value::Null) => Ok(None),
        Some(Value::String(cursor)) if !cursor.is_empty() => Ok(Some(cursor.clone())),
        _ => Err(ClientError::Serialization("Invalid desktop message cursor".into()))
    }
}

#[derive(serde::Serialize)]
struct DesktopProjectedAttachment {
    filename: String,
    mime: String,
    size: Option<usize>,
    data_base64: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    path: Option<String>,
    unavailable_reason: Option<&'static str>,
}

// Presentation only: never resolve a desktop path or fetch an external URL for a preview.
fn project_desktop_attachment(part: &Value) -> DesktopProjectedAttachment {
    let mut attachment = DesktopProjectedAttachment {
        filename: part["filename"].as_str().filter(|s| !s.is_empty()).unwrap_or("附件").to_owned(),
        mime: part["mime"].as_str().unwrap_or("application/octet-stream").to_owned(),
        size: None,
        data_base64: None,
        path: None,
        unavailable_reason: Some("附件内容未随消息同步，暂时无法在手机预览"),
    };
    let Some(url) = part["url"].as_str().filter(|url| url.starts_with("data:")) else {
        return attachment;
    };
    attachment.unavailable_reason = Some("附件内容格式异常，无法预览");
    let Some((header, data)) = url.split_once(',').filter(|(header, _)| header.ends_with(";base64")) else {
        return attachment;
    };
    if header[5..].split(';').next() != Some(attachment.mime.as_str()) {
        return attachment;
    }
    if data.len() > 5_333_336 {
        attachment.unavailable_reason = Some("附件过大，暂不支持在手机预览");
        return attachment;
    }
    let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(data) else {
        return attachment;
    };
    if bytes.len() > 4_000_000 {
        attachment.unavailable_reason = Some("附件过大，暂不支持在手机预览");
        return attachment;
    }
    attachment.size = Some(bytes.len());
    attachment.data_base64 = Some(data.to_owned());
    attachment.unavailable_reason = None;
    attachment
}

#[derive(serde::Serialize)]
struct DesktopProjectedMessage {
    id: String,
    role: String,
    text: String,
    kind: &'static str,
    status: Option<String>,
    details: Vec<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    attachment: Option<DesktopProjectedAttachment>,
    #[serde(skip)]
    turn: String,
}

#[uniffi::export]
pub fn project_desktop_messages(messages_json: String) -> Result<String, ClientError> {
    // A live snapshot is display-only; never merge it into durable history or prompt receipts.
    let input: Value = serde_json::from_str(&messages_json).map_err(|e| ClientError::Serialization(e.to_string()))?;
    let mut rows = input.as_array().or_else(|| input["messages"].as_array())
        .ok_or_else(|| ClientError::Serialization("Invalid desktop message history".into()))?.clone();
    if input.is_object() {
        if let Some(error) = input["live"]["error"].as_str() { return Err(ClientError::Serialization(error.into())); }
        let snapshots = input["live"]["parts"].as_array()
            .filter(|parts| parts.len() <= 32).ok_or_else(|| ClientError::Serialization("Invalid desktop live snapshot".into()))?;
        for part in snapshots {
            let message_id = part["messageID"].as_str().filter(|id| !id.is_empty()).ok_or_else(|| ClientError::Serialization("Missing live message ID".into()))?;
            let id = part["id"].as_str().filter(|id| !id.is_empty()).ok_or_else(|| ClientError::Serialization("Missing live part ID".into()))?;
            let text = part["text"].as_str().filter(|text| text.len() <= 1_048_576).ok_or_else(|| ClientError::Serialization("Invalid live text".into()))?;
            let active = part["active"].as_bool().ok_or_else(|| ClientError::Serialization("Invalid live text status".into()))?;
            let existing = rows.iter().position(|row| row["info"]["id"] == message_id);
            if existing.is_none() && !active { continue; }
            if existing.is_none() {
                rows.push(json!({"info":{"id":message_id,"role":"assistant"},"parts":[]}));
            }
            let index = existing.unwrap_or(rows.len() - 1);
            let info = &rows[index]["info"];
            if info["role"] != "assistant" { return Err(ClientError::Serialization("Live text cannot overwrite a user message".into())); }
            // Completed/error history is authoritative, including stopped and failed turns.
            if info["time"]["completed"].is_number() || info.get("error").is_some_and(|error| !error.is_null()) { continue; }
            let parts = rows[index]["parts"].as_array_mut().ok_or_else(|| ClientError::Serialization("Invalid desktop message parts".into()))?;
            if let Some(previous) = parts.iter_mut().find(|previous| previous["id"] == id) {
                if previous["type"] != "text" { return Err(ClientError::Serialization("Live text part type conflict".into())); }
                previous["text"] = json!(text);
            } else {
                parts.push(json!({"id":id,"type":"text","text":text}));
            }
        }
    }
    let mut result: Vec<DesktopProjectedMessage> = Vec::new();
    let mut current_user = String::new();
    for row in rows {
        let info = &row["info"];
        let id = info["id"].as_str().filter(|id| !id.is_empty()).ok_or_else(|| ClientError::Serialization("Desktop message missing stable ID".into()))?;
        let role = info["role"].as_str().filter(|role| matches!(*role, "user" | "assistant")).ok_or_else(|| ClientError::Serialization("Invalid desktop message role".into()))?;
        if role == "user" { current_user = id.to_owned(); }
        let turn = info["parentID"].as_str().filter(|s| !s.is_empty()).unwrap_or(if current_user.is_empty() { id } else { &current_user });
        let parts = row["parts"].as_array().ok_or_else(|| ClientError::Serialization("Invalid desktop message parts".into()))?;
        // Display-only ordering: keep source indices/IDs and assistant tool order intact.
        let mut ordered: Vec<_> = parts.iter().enumerate().collect();
        if role == "user" { ordered.sort_by_key(|(_, part)| part["type"] != "file"); }
        for (index, part) in ordered {
            if part["ignored"] == true && part["type"] != "file" { continue; }
            let key = format!("{id}:{}", part["id"].as_str().map(str::to_owned).unwrap_or_else(|| index.to_string()));
            let kind = part["type"].as_str().unwrap_or("");
            let text = match kind {
                "text" => part["text"].as_str().unwrap_or("").trim().to_owned(),
                "tool" => part["state"]["title"].as_str().filter(|s| !s.is_empty()).or_else(|| part["tool"].as_str()).unwrap_or("工具操作").to_owned(),
                "file" if part["mime"].as_str() != Some("application/x-directory") => part["filename"].as_str().filter(|s| !s.is_empty()).unwrap_or("附件").to_owned(),
                _ => continue,
            };
            if text.is_empty() { continue; }
            let status = part["state"]["status"].as_str().map(str::to_owned);
            let mut details = Vec::new();
            let mut grouped = false;
            if kind == "tool" {
                let failure = part["state"]["error"].as_str().filter(|s| !s.is_empty());
                details.push(failure.map(|error| format!("{text}：{error}")).unwrap_or_else(|| text.clone()));
                if let Some(previous) = result.last_mut().filter(|previous| previous.kind == "tool" && previous.role == role && previous.turn == turn) {
                    previous.details.append(&mut details);
                    previous.text = format!("{} 项操作", previous.details.len());
                    previous.status = Some(if previous.status.as_deref() == Some("error") || status.as_deref() == Some("error") { "error" }
                        else if previous.status.as_deref() == Some("running") || status.as_deref() == Some("running") { "running" }
                        else if previous.status.as_deref() == Some("pending") || status.as_deref() == Some("pending") { "pending" }
                        else { "completed" }.into());
                    grouped = true;
                }
            }
            if !grouped { result.push(DesktopProjectedMessage { id: key.clone(), role: role.to_owned(), text, kind: match kind { "tool" => "tool", "file" => "file", _ => "text" }, status, details, attachment: (kind == "file").then(|| project_desktop_attachment(part)), turn: turn.to_owned() }); }
            // Only registered, completed deliverables become cards; never infer paths from tool text.
            if kind == "tool" && part["state"]["status"] == "completed" {
                let mut seen = std::collections::HashSet::new();
                for artifact in part["state"]["metadata"]["artifacts"].as_array().into_iter().flatten() {
                    let Some(path) = artifact["path"].as_str().filter(|s| !s.trim().is_empty()) else { continue; };
                    let Some(name) = artifact["name"].as_str().filter(|s| !s.trim().is_empty()) else { continue; };
                    let Some(mime) = artifact["mime"].as_str().filter(|s| !s.trim().is_empty()) else { continue; };
                    let identity = path.replace('\\', "/");
                    let identity = if identity.as_bytes().get(1) == Some(&b':') { identity.to_lowercase() } else { identity };
                    if !seen.insert(identity.clone()) { continue; }
                    let size = artifact["size"].as_u64().and_then(|size| usize::try_from(size).ok());
                    result.push(DesktopProjectedMessage {
                        id: format!("{key}:artifact:{identity}"), role: role.to_owned(), text: name.to_owned(),
                        kind: "file", status: None, details: vec![], turn: turn.to_owned(),
                        attachment: Some(DesktopProjectedAttachment { filename: name.to_owned(), mime: mime.to_owned(), size,
                            path: Some(path.to_owned()), data_base64: None,
                            unavailable_reason: (size.is_some_and(|size| size > 4_000_000)).then_some("文件过大，请在电脑端预览（手机预览上限 4 MB）") }),
                    });
                }
            }
        }
        if let Some(error) = info.get("error").filter(|error| !error.is_null()) {
            let text = error["data"]["message"].as_str().or_else(|| error["message"].as_str()).or_else(|| error["name"].as_str()).unwrap_or("桌面模型执行失败");
            result.push(DesktopProjectedMessage { id: format!("{id}:error"), role: role.to_owned(), text: text.into(), kind: "text", status: Some("error".into()), details: vec![], attachment: None, turn: turn.to_owned() });
        }
    }
    serde_json::to_string(&result).map_err(|e| ClientError::Serialization(e.to_string()))
}

// Normalize the existing /file/content response without exposing a PC file URL to the UI.
#[uniffi::export]
pub fn resolve_desktop_artifact(attachment_json: String, content_json: String) -> Result<String, ClientError> {
    let attachment: Value = serde_json::from_str(&attachment_json).map_err(|e| ClientError::Serialization(e.to_string()))?;
    let content: Value = serde_json::from_str(&content_json).map_err(|e| ClientError::Serialization(e.to_string()))?;
    let fail = |message: &str| ClientError::Serialization(message.into());
    let filename = attachment["filename"].as_str().filter(|s| !s.is_empty()).ok_or_else(|| fail("文件名称无效"))?;
    let mime = attachment["mime"].as_str().filter(|s| !s.is_empty()).ok_or_else(|| fail("文件类型无效"))?;
    let raw = content["content"].as_str().ok_or_else(|| fail("电脑返回的文件内容无效"))?;
    if raw.len() > 5_333_336 { return Err(fail("文件过大，请在电脑端预览（手机预览上限 4 MB）")); }
    let bytes = match content["type"].as_str() {
        Some("text") => raw.as_bytes().to_vec(),
        Some("binary") if content["encoding"] == "base64" => base64::engine::general_purpose::STANDARD.decode(raw).map_err(|_| fail("电脑返回的文件编码无效"))?,
        _ => return Err(fail("电脑返回的文件格式暂不支持预览")),
    };
    if bytes.len() > 4_000_000 { return Err(fail("文件过大，请在电脑端预览（手机预览上限 4 MB）")); }
    if bytes.is_empty() && attachment["size"].as_u64().is_some_and(|size| size > 0) {
        return Err(fail("文件已被移动、删除或清空，请在电脑端确认"));
    }
    serde_json::to_string(&DesktopProjectedAttachment { filename: filename.to_owned(), mime: mime.to_owned(),
        size: Some(bytes.len()), path: None, data_base64: Some(base64::engine::general_purpose::STANDARD.encode(bytes)), unavailable_reason: None })
        .map_err(|e| ClientError::Serialization(e.to_string()))
}

#[uniffi::export]
pub fn desktop_session_attention(statuses_json: String, permissions_json: String, questions_json: String, session_id: String) -> Result<String, ClientError> {
    let statuses: Value = serde_json::from_str(&statuses_json).map_err(|e| ClientError::Serialization(e.to_string()))?;
    if !statuses.is_object() { return Err(ClientError::Serialization("Invalid session statuses".into())); }
    let status = match statuses.get(&session_id) {
        None => "idle",
        Some(value) => value.get("type").and_then(Value::as_str).ok_or_else(|| ClientError::Serialization("Invalid session status".into()))?,
    };
    if !matches!(status, "idle" | "busy" | "retry") { return Err(ClientError::Serialization("Invalid session status".into())); }
    let label = match status { "retry" => format!("等待模型重试：{}", statuses[&session_id]["message"].as_str().unwrap_or("请稍候")), "busy" => "思考中…".into(), _ => "".into() };
    let scoped = |raw: &str| -> Result<Vec<Value>, ClientError> {
        let rows: Vec<Value> = serde_json::from_str(raw).map_err(|e| ClientError::Serialization(e.to_string()))?;
        rows.into_iter().filter(|row| row["sessionID"] == session_id).map(|mut row| {
            if row["id"].as_str().is_none_or(|id| id.is_empty()) { return Err(ClientError::Serialization("Missing attention request ID".into())); }
            let source = match row.get("requestSource") {
                None => "legacy",
                Some(value) => value.as_str().ok_or_else(|| ClientError::Serialization("Invalid attention request source".into()))?,
            };
            if !matches!(source, "legacy" | "v2") { return Err(ClientError::Serialization("Invalid attention request source".into())); }
            row["requestSource"] = json!(source);
            Ok(row)
        }).collect()
    };
    let permissions = scoped(&permissions_json)?.into_iter().map(|mut row| {
        let current = row["requestSource"] == "v2";
        let permission = row[if current { "action" } else { "permission" }].as_str()
            .filter(|value| !value.is_empty()).ok_or_else(|| ClientError::Serialization("Invalid permission action".into()))?.to_owned();
        let patterns = row[if current { "resources" } else { "patterns" }].as_array()
            .filter(|values| values.iter().all(Value::is_string)).ok_or_else(|| ClientError::Serialization("Invalid permission resources".into()))?.clone();
        row["permission"] = json!(permission);
        row["patterns"] = json!(patterns);
        Ok(row)
    }).collect::<Result<Vec<_>, ClientError>>()?;
    let questions = scoped(&questions_json)?;
    for request in &questions {
        let items = request["questions"].as_array().filter(|items| !items.is_empty())
            .ok_or_else(|| ClientError::Serialization("Invalid question request".into()))?;
        for question in items {
            if question["question"].as_str().is_none_or(|value| value.is_empty()) ||
                question.get("multiple").is_some_and(|value| !value.is_boolean()) ||
                question.get("custom").is_some_and(|value| !value.is_boolean()) {
                return Err(ClientError::Serialization("Invalid question fields".into()));
            }
            if question["options"].as_array().is_none_or(|options| options.iter().any(|option|
                option["label"].as_str().is_none_or(|value| value.is_empty()) || !option["description"].is_string())) {
                return Err(ClientError::Serialization("Invalid question options".into()));
            }
        }
    }
    serde_json::to_string(&json!({"status":status,"label":label,"permissions":permissions,"questions":questions})).map_err(|e| ClientError::Serialization(e.to_string()))
}


// A failed POST alone cannot prove non-admission; the exact durable receipt must also be missing.
#[uniffi::export]
pub fn desktop_prompt_rejection_confirmed(status: u16, receipt_json: String, session_id: String, message_id: String) -> Result<bool, ClientError> {
    let receipt = parse_desktop_prompt_receipt(receipt_json, session_id, message_id)?;
    Ok(matches!(status,400|401|403|404|413|422) && matches!(receipt.state,DesktopPromptState::Missing))
}

#[cfg(test)]
mod project_directory_tests {
    use super::*;
    #[test]
    fn decodes_typed_directories_and_retains_server_errors() {
        let listing: DesktopDirectoryListing = decode_directory_response(DesktopResponse {
            status: 200, body_json: r#"{"path":"/home/user","parent":"/home","roots":["/"],"directories":[{"name":"项目","path":"/home/user/项目"}]}"#.into(),
        }).unwrap();
        assert_eq!(listing.directories[0].name, "项目");
        let error = decode_directory_response::<DesktopProjectFolder>(DesktopResponse {
            status: 409, body_json: r#"{"message":"目录已存在"}"#.into(),
        }).unwrap_err();
        assert!(error.to_string().contains("409"));
        assert!(error.to_string().contains("目录已存在"));
        assert!(matches!(decode_directory_response::<DesktopDirectoryListing>(DesktopResponse {
            status: 200, body_json: "{}".into(),
        }), Err(ClientError::Serialization(_))));
    }
    #[test]
    fn permits_project_directory_routes_without_shell_access() {
        for method in ["GET", "POST"] {
            assert!(desktop_request(method, "/connect/projects", "null").is_ok());
            assert!(desktop_request(method, "/connect/directories", "null").is_ok());
        }
        assert!(desktop_request("DELETE", "/connect/directories", "null").is_err());
        assert!(desktop_request("POST", "/connect/execute", "null").is_err());
    }
}

fn decode_directory_response<T: serde::de::DeserializeOwned>(response: DesktopResponse) -> Result<T, ClientError> {
    if !(200..300).contains(&response.status) {
        let message = serde_json::from_str::<Value>(&response.body_json).ok()
            .and_then(|value| value.get("message").and_then(Value::as_str).map(str::to_owned))
            .unwrap_or(response.body_json);
        return Err(ClientError::Rpc(format!("桌面返回 {}: {message}", response.status)));
    }
    serde_json::from_str(&response.body_json).map_err(|error| ClientError::Serialization(format!("目录响应格式错误: {error}")))
}

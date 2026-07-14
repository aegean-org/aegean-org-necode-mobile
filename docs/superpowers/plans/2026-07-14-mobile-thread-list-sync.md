# Mobile CLI 会话列表同步 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 远程 CLI 新建会话后，Android 与 iOS 在约 3 秒内自动把该会话加入列表。

**Architecture:** 复用共享 Rust 层现有 `refresh_thread_list_from_app_server`，在远程 `ServerSession` 连接后启动一个健康状态感知的 3 秒轮询任务。任务随 session 的 `Disconnected` 状态结束；请求失败时保留现有列表并输出明确 warning。

**Tech Stack:** Rust, Tokio, codex-mobile-client, AppStore reducer, UniFFI shared runtime

## Global Constraints

- 轮询间隔固定为 3 秒并提取为命名常量。
- 只轮询远程 session；本地 in-process runtime 不轮询。
- 同一 session 只启动一个列表同步任务。
- 失败不得清空现有会话列表，不添加 mock、fallback 或静默降级。
- Android 与 iOS 共用同一 Rust 实现。
- 不提交 Git。

---

### Task 1: 添加远程会话列表轮询回归测试

**Files:**
- Modify: `shared/rust-bridge/codex-mobile-client/src/mobile_client/tests.rs`
- Test: `shared/rust-bridge/codex-mobile-client/src/mobile_client/tests.rs`

**Interfaces:**
- Consumes: `ServerSession::test_stub_with_runtime_handlers`, `MobileClient::spawn_post_connect_warmup`
- Produces: 回归合同：远程 session 周期请求 `thread/list`、合并新会话、断开后停止请求

- [ ] **Step 1: 写失败测试**

新增 Tokio 测试，创建 `necode` runtime handler，返回包含 `cli-thread` 的 `ThreadListResponse`，调用 `spawn_post_connect_warmup` 后等待 store 出现该 thread；随后断开 session，并断言请求计数不再增长。

- [ ] **Step 2: 验证测试失败**

Run:

```powershell
cargo test --manifest-path 'D:\project\litter\shared\rust-bridge\Cargo.toml' -p codex-mobile-client remote_thread_list_sync
```

Expected: FAIL，因为当前 `spawn_post_connect_warmup` 不启动 `thread/list` 轮询。

---

### Task 2: 接入 3 秒远程列表同步生命周期

**Files:**
- Modify: `shared/rust-bridge/codex-mobile-client/src/mobile_client/mod.rs`
- Modify: `shared/rust-bridge/codex-mobile-client/src/mobile_client/thread_projection.rs`

**Interfaces:**
- Consumes: `refresh_thread_list_from_app_server(session, app_store, server_id)`
- Produces: `run_remote_thread_list_sync(app_store, server_id, session, interval)`；生产入口使用 `REMOTE_THREAD_LIST_SYNC_INTERVAL`

- [ ] **Step 1: 添加命名间隔常量**

在 Mobile client 模块定义：

```rust
const REMOTE_THREAD_LIST_SYNC_INTERVAL: Duration = Duration::from_secs(3);
```

- [ ] **Step 2: 实现健康状态感知循环**

循环使用 `tokio::time::interval` 和 `session.health()`：仅在 `Connected` 时调用 `refresh_thread_list_from_app_server`；使用 `MissedTickBehavior::Skip`；收到 `Disconnected` 或 watch 关闭时退出。

- [ ] **Step 3: 从连接后 warmup 启动**

`spawn_post_connect_warmup` 对 `session.config().is_local == false` 的 session 启动同步循环，然后保留原有 account warmup。

- [ ] **Step 4: 失败时保留列表**

调整 `refresh_thread_list_from_app_server`：任一 runtime 的 `thread/list` 失败时记录错误、跳过全局 `finalize_thread_list_sync` 并返回 `Err`；成功页仍可 upsert，但不会因空结果删除已有列表。

- [ ] **Step 5: 运行定向测试**

Run:

```powershell
cargo test --manifest-path 'D:\project\litter\shared\rust-bridge\Cargo.toml' -p codex-mobile-client remote_thread_list_sync
```

Expected: PASS。

---

### Task 3: 完整验证

**Files:**
- Verify: `shared/rust-bridge/codex-mobile-client/`
- Verify: `apps/android/app/`

- [ ] **Step 1: 运行共享 Rust 相关测试**

```powershell
cargo test --manifest-path 'D:\project\litter\shared\rust-bridge\Cargo.toml' -p codex-mobile-client mobile_client_tests
```

Expected: PASS，60 秒内完成。

- [ ] **Step 2: 运行 Rust 静态检查**

```powershell
cargo check --manifest-path 'D:\project\litter\shared\rust-bridge\Cargo.toml' -p codex-mobile-client
```

Expected: exit code 0，新增列表同步函数不再产生 dead-code warning。

- [ ] **Step 3: 运行 Android 单元测试**

```powershell
$env:JAVA_HOME='D:\tools\Android\Android Studio\jbr'
D:\project\litter\apps\android\gradlew.bat -p D:\project\litter\apps\android :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 检查 diff**

确认仅包含共享 Rust 列表同步、回归测试和设计/计划文档；不提交 Git。

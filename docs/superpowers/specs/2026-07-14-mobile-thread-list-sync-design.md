# Mobile CLI 会话列表同步设计

## 目标

CLI 新建会话后，无需刷新或重启 Mobile App，Android 与 iOS 的会话列表在约 3 秒内出现该会话。

## 当前问题

Alleycat daemon 的 `thread/list` 已能返回 CLI 新建会话，但共享 Rust Mobile 客户端没有周期调用现有的 `refresh_thread_list_from_app_server`，因此新会话不会进入 Rust `AppStore`，平台 UI 也无法显示。

## 设计

在 `codex-mobile-client` 共享 Rust 层增加会话列表同步循环：

- 复用现有 `refresh_thread_list_from_app_server`，不在 Swift 或 Kotlin 重复实现协议与合并逻辑。
- 对已连接的远程服务器每 3 秒刷新一次 `thread/list`。
- 同一服务器只运行一个同步循环；断开连接或销毁 session 时循环结束。
- 刷新成功后由现有 reducer 合并列表，使 Android 与 iOS 自动收到快照更新。
- 刷新失败时记录明确 warning，并保留当前列表；不返回模拟数据、不清空状态、不静默降级。
- 本地手机内置 runtime 不参与该轮询，避免无意义请求。

## 数据流

`Mobile session connected` → `3 秒定时循环` → `thread/list` → `thread_list_page_to_thread_infos` → `AppStore reducer` → `Android/iOS snapshot` → `会话列表更新`

## 测试

- 回归测试证明连接建立后会启动列表同步任务。
- 回归测试证明任务调用 `thread/list` 并把新会话合并进 store。
- 回归测试证明断开连接后同步任务停止。
- 运行共享 Rust 定向测试及现有 Android 单元测试。

## 构建影响

改动位于共享 Rust Mobile 层，Android 与 iOS 同时生效。验证 Android 需要重新构建 Rust JNI 和 APK；CLI 与 daemon 无需重新构建。

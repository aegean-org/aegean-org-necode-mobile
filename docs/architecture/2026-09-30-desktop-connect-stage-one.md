# Desktop Connect 第一阶段实施与验证

日期：2026-09-30。下方第一阶段记录是历史检查点；步骤 6 已继续实施，最新状态见收尾章节。不能将源码及本地构建通过等同于全链路真机或 iOS 验收。

## 范围

- 手机工作区：D:\project\litter-mobile-desktop-connect，mobile-desktop-connect 分支。
- 桌面工作区：D:\codex\.codex\worktrees\mobile-desktop-connect\necode-desktop-gitlab，mobile-desktop-connect 分支。
- 第一阶段检查点未修改主工作区 main，未构建 APK；后续最终 APK 已构建，见收尾章节。始终未提交、推送、合并或安装，保留原有 dirty/untracked。
- 未改 CLI 扫码链路或 Connect/MySQL 部署。

## 已实施

1. 手机 Rust 的连接状态改为短锁快照：网络 await 不持有状态锁。连接尝试有 reservation、取消清理和 generation fencing；旧连接返回不能归入新连接。断开先关闭传输，异步清理租约，即使调用方取消也继续清理。晚到的 acquisition 租约被释放，清理失败仍可观察。
2. Host 每连接最多 8 个 stream 并发，每个 stream 总 deadline 30 秒，断线时 abort/drain handlers，保留请求 4 MiB 和响应 8 MiB 限制。已经持久接纳的 sidecar 写请求不因传输取消而回滚。
3. Host 区分登录/租约/占用与服务不可用，不再将服务故障统一伪装成 403。无效 sidecar JSON 返回 502，不保留原来的 200；中途断流不误报为响应超限。日志包含请求关联 ID、lease_validate / sidecar_headers / sidecar_body / stream_total 耗时，不记录 token、ticket、请求体或查询路径。
4. 新增 GET /connect/sessions：默认 10 条，限制 1–50，按 updated desc、id desc 排序，排除归档和子会话。游标绑定 project/directory/workspace/search scope。项目元信息只读取本页所涉及项目，保留旧 /experimental/session 契约和置顶语义。游标保证静态数据集中的同时间戳分页完整性，不承诺实时变化中的快照分页。
5. Rust 统一手机分页参数/游标/响应校验，固定每页 10 条，验证项目归属、重复 ID、排序、root/archive 和页尾 cursor。不新增 UniFFI ABI。Android/iOS 使用该分页接口，移除按所有项目扫描 /session；项目列表懒加载，项目页不展示已加载条数冒充总数。首屏无内容才中心 loading，同 scope 刷新保留内容，标题旁指示刷新，追加加载单独表示。切页/搜索时对旧结果和部分旧加载错误做 fencing。Android 恢复探测使用 /session/status，不额外拉取 /project。

## 回归证据

- Host：cargo test --offline，8 passed、0 failed、1 ignored。ignored 为需要真实 sidecar 环境变量的测试，没有运行。
- 手机 Rust：临时轻量 harness include 实际 desktop_connect.rs 模块，13 passed、0 failed；覆盖真实本地 HTTP 和 loopback iroh。并非整个上游 Rust 内核构建或 Android 原生 .so 验证。
- 后端：在 packages/opencode 执行 bun test --timeout 30000 test/server/httpapi-connect-sessions.test.ts test/server/global-session-list.test.ts，5 passed、0 failed、54 assertions；真实数据库和 HTTP handler，不用 mock 复制分页逻辑。
- 后端：packages/opencode 的 bun typecheck exit 0。
- Android：compileDebugKotlin、compileDebugAndroidTestKotlin、testDebugUnitTest --tests '*Desktop*'，BUILD SUCCESSFUL。DesktopHostAction/MessageFollow/MessageParsing 共 9 个单测通过。instrumented tests 仅编译，没有在设备运行。
- 针对 sidecar HTML 200 的新增测试先观察到失败（实际 status 200、预期 502），修复后通过；共享分页项目末页归属测试先观察到失败，修复后通过。
- iOS 同步修改源代码；本 Windows 环境无 Xcode/Swift 编译及设备验证，不能宣称 iOS 构建通过。

## 本阶段还不能证明

- 多页面的 native 自动/手动恢复尚未收敛到唯一共享连接 owner，后台前台恢复、手动选电脑竞争仍需下一阶段修复与实机验收。
- 新建/模型加载仍需拆分；prompt 已持久接纳但回包丢失的核对、停止/权限/question 状态、消息尾页/增量更新仍未实施。
- iOS ASR 和 Android/CLI 同路径对齐仍未完成；整体 UI 精修未作视觉或设备验收。
- 未实测公网、relay、18 个项目冷启动或 p50/p95，不能声称首屏已经达成 1–2 秒目标。

## 下一阶段

优先把自动恢复、手动换电脑、断开/取消集中到 Rust 的唯一连接恢复入口；native 只报告生命周期和用户意图。随后拆分创建/模型准备/发送/执行状态，并处理写请求的持久接纳核对。不能只追加重试来掩盖底层错误。

测试包构建前需同步重建本次改过的 Rust 桥接库；桌面 sidecar 与 connect-host 改动也需要重启才生效，不依赖前端热更新。当前先保留源码阶段，不生成 APK。

## 全部源码修复收尾 — 2026-09-30

### 后续阶段已实施

- 唯一共享恢复 owner：同目标恢复 join、手选最新意图优先、先 resume，只有明确 404 才重新 acquire；主动断开暂停自动恢复，旧 generation 的回包及晚到租约被隔离和清理。
- 浏览入口分开：全局最近会话真实首屏 10 条及加载更多，固定项目入口，项目会话独立 scope；电脑缓存用 desktopId 而非主机显示名。项目列表懒加载，不再逐项目扫描会话。
- 页面动作按 host/session/route 隔离，不再整页全局阻塞。新建、模型加载、发送、执行、停止、回执确认各自展示真实状态。两端在多段 await 后检查原路由归属，旧错误不能污染新电脑/会话。返回列表不被旧请求强跳回详情。
- 每个 host/session 独立草稿、附件、所选模型、pending 与 awaiting；录音和附件回调只写原 owner。发送成功才收起键盘，恢复或重新进入不会清空已有待确认状态。
- prompt admission 使用受保护存储中的共享 journal，exact messageId/body/account/host/session；丢回包先核对 durable receipt，只有明确 missing 且支持 idempotent 才精确重试。明确 HTTP 拒绝也需 missing receipt 才解除 pending，草稿仍保留。assistant ID 不当作 user 已接纳。
- 消息尾页 30 条，稳定 ID 合并、加载更早。断线大缺口每轮最多补 4 页，无重叠时 staging 不拼接有洞的消息；补洞期间也更新真实权限/执行及回执状态。220 条跨两批次共享回归验证没有丢行。
- 共享 projection 合并同 user-turn 工具活动并保留错误；工具结束或 idle 不冒充 prompt 完成，要求 exact parentID 的 assistant terminal 状态。等待回复在消息位置用文字 shimmer，不使用中心遮罩或横向进度条；只在空内容时中心 spinner+说明，刷新保留已有内容。
- 停止走真实 abort；权限/问题 snapshot 按 session 聚合，legacy/v2 source-aware 回复。同 ID 不同来源不会复用表单。V2 action/resources 由 Rust 统一为 permission/patterns，两端不自行猜字段；畸形字段报错，不在 Compose 渲染时崩溃。
- 模型来源是电脑端实际可见且可用 catalog；优先 picked、当前 session、真实 default，不借其他会话。图片发送按发送时捕获的附件及模型图片能力校验。
- Android/iOS 语音统一共享 NE 模型目录和 ASR multipart；录音权限与 PCM WAV 采集留在原生，不走 Google speech。校验完整非空 WAV、大小、采样参数，凭据换行在 trim 之前拒绝。

### 最新聚焦验证

- 共享 Rust/ASR：临时 harness include 实际源码，35 passed、0 failed；含本地真实 HTTP、loopback iroh、恢复竞争、锁外 I/O、精确回执、projection、跨批次补洞与 ASR。不是上游全库测试。
- Host：11 passed、0 failed、1 ignored；ignored 需要真实 sidecar URL/password，未运行。
- 后端：真实数据库/HTTP 与 permission/question services，13 passed、0 failed、119 assertions；运行 httpapi-connect-sessions、global-session-list、v2-user-action 三个文件。
- packages/opencode 的 bun typecheck exit 0。
- Android 最后原生 fencing 和重新生成 Kotlin/Swift 绑定后，production/androidTest Kotlin 编译及 9 个 Desktop unit tests 重跑通过，BUILD SUCCESSFUL（46s）。
- 桌面 Host 实际 debug binary 已构建；controller 集成重跑通过：1 passed、0 failed、66 assertions。使用真实 Host 和本地 Connect，覆盖登录启动、退出登录及 stop 释放 desktop/lease；Electron import 适配不等同于真实桌面 UI 验收。
- 新增语音 token 换行回归观察到真实失败，修复后通过；V2 权限字段及此前 sidecar HTML 测试也有红绿证据。

### 构建与验收边界

最终 Android 原生库和 APK 已构建，未安装、提交、合并、推送或发布。仅构建 codex-mobile-client 目标及其所需依赖，没有执行更新子模块/重建 legacy CLI bridge 的全量脚本。Windows DLL 与唯一 Kotlin/Swift 绑定已生成；Android SO 的 656 个绑定导出符号已核对。

- Android SO：2026-09-30 20:43:22，81,791,608 bytes；target/JNI/merged SHA256 均为 `9737C3488BC40D57E5E44D789AF2D8C4E2F45A8C3F04605A9F17DB76BEA4E177`。
- 最终 `:app:assembleDebug`：BUILD SUCCESSFUL（49s）。APK：`D:\project\litter-mobile-desktop-connect\apps\android\app\build\outputs\apk\debug\app-debug.apk`，2026-09-30 20:45:09，186,167,971 bytes。
- APK SHA256：`46D29D81F908F40237FA0E456AA0C39DBA0F518F7AFEF1FE49E44A97213E0CBE`。
- APK 内 SO 与 Gradle stripped 中间产物 SHA256 相同：`B0B4C6BDDCE47958D6775F09031005A23D8A7823AEFFAEA71CA1D68F24321A93`；merged 与最新 JNI SO 一致，确认由本轮新库打包，strip 改变了二进制 hash。
- apksigner verify 通过（v2、Android Debug）；aapt2 核对包名 `com.aegean.necode.mobile`、应用名 NeCode、versionCode 1/versionName 1.0.0、minSdk 26/targetSdk 35、arm64-v8a。
- 本机构建工具修正：使用 Git/MSYS Perl、临时目录中的 GNU make 和正斜杠 NDK 工具路径，补足 cargo-ndk link wrapper 环境后构建成功。没有更改系统工具或上游子模块。

Windows 无 Xcode：iOS 是源码与生成绑定同步，不是 iOS 编译成功。无连接设备：instrumentation 仅编译，本轮尚未实测后台回前台、横竖屏、多主机切换、权限表单或 ANR。未记录真实公网 p50/p95，不能宣称项目冷启动/列表延迟目标达成。桌面 sidecar 和 connect-host 均需从对应工作区重启，不能只靠前端热更新。CLI 原扫码链路和 Connect/MySQL 部署未修改。

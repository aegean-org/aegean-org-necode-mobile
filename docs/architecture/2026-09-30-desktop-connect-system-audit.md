# NeCode Mobile ↔ Desktop 系统审计

日期：2026-09-30
状态：以下正文保留修复前审计证据，不代表当前代码；已完成本轮约定修复的源码实现及聚焦回归，最终 Android 桥接库与 APK 已构建并核对签名、包名和包内新库，未安装。当前进度见 `2026-09-30-desktop-connect-stage-one.md` 收尾章节。尚未进行本轮真机性能、ANR 或 iOS 构建验收。

## 1. 结论与边界

目前已经具备互联功能闭环的基础，但尚未形成稳定的手机端产品闭环。主要问题不是配色或间距，而是：传输双端串行、业务热路径重复公网鉴权、首屏跨项目扫会话、连接状态多处管理、消息有损投影，以及过大的全局操作锁。UI 补丁无法单独解决这些问题。

建议保留登录、Connect/MySQL、iroh、原 CLI 扫码链路，分层修复现有桌面适配，不整体重写、不迁移 CLI。

审计工作区：
- 手机：`D:\project\litter-mobile-desktop-connect`，分支 `mobile-desktop-connect`。
- 桌面互联：`D:\codex\.codex\worktrees\mobile-desktop-connect\necode-desktop-gitlab`，分支 `mobile-desktop-connect`，HEAD `7d09d191ea`。
- Connect/MySQL：查阅 `D:\project\necode-desktop-gitlab` 的 `main`。它与桌面互联分支中的旧 SQLite 服务代码不同；公网部署与此 main 的一致性仍待核对。

本轮没有修改业务代码，没有编译 Rust/Android、打 APK、提交或合并。已有未提交修改均保留。检查时 adb 无设备，未取得当前真机日志或请求分段耗时，因此不能把静态队头阻塞直接认定为 Android 主线程死锁，也不能宣称已定位此前 11 秒的唯一来源。

## 2. 已经合理的部分

- 用户中心仍仅负责身份；Connect 管理设备与租约，不应成为项目/消息存储服务。
- 身份解析使用 `data.account_id`。
- MySQL schema 对租约的 `desktop_id`、`mobile_id` 都有唯一约束；获取租约有事务及行锁基础，符合一对一约束。
- host 对路径和方法有 allowlist，并校验 ticket、设备、账号和远端 endpoint；这些边界不能为提速取消。
- 原 CLI 扫码连接可保留；桌面 REST 与 CLI RPC 不需要强行统一协议。
- 已有模型策略接口 `/connect/models`、V2 持久接纳能力、消息/会话分页能力可以利用，避免另造整套后端。
- 部分未知发送结果提示已经避免盲目重发；这是正确方向，但缺少结果核对闭环。

## 3. 实际链路与性能问题

```text
Compose / SwiftUI 页面
  → UniFFI desktop_connect
    → 持 active 连接锁等待网络
      → iroH 双向 stream
        → host 同连接逐 stream 处理
          → Connect /v1/leases/{id}/validate
            → UC /user/verify
            → UC /user/info
            → lease / device 数据检查
          → 本地 sidecar API
        → 整体 JSON 响应
    → 原生页面解析、重建列表
```

### P0：网络 await 占用连接状态锁

手机 `desktop_connect.rs:277–296` 的 `request_desktop` 在 `self.active.lock().await` 后，持锁等待 `exchange(...).await`。查询 active、断开与 connect/resume 也竞争同一把锁。connect/resume 自身同样持锁执行多个网络阶段。

结果：读请求堵住控制操作；上层并行 launch 仍然在此排队。exchange 的 stream-open 20 秒及收发 30 秒超时不包括锁排队时间，用户动作总等待不受这两个阶段超时单独约束。

方向：短锁获取可用连接/租约快照，网络不持状态锁；用连接 generation 校验结果，防止旧主机请求污染新连接。控制操作有独立状态和取消机制，不能仅扩大 timeout 或盲目重试。

### P0：host 同连接也串行

`desktop-connect-host/src/main.rs:180–199` 的循环在接收 stream 后，完整 read → handle → write，再接受下一条 stream。已有 spawn 是每条连接，不是每条请求。

方向：允许有界并发的独立读取；写操作按实际业务依赖协调。补每 stream 超时、配额、取消及任务回收，不无限 spawn，也不一口气并发所有项目。

### P1：每个请求额外走公网身份验证

host `main.rs:302–328` 每个业务请求先调 lease validate。Connect `app.ts:14` 调 identity；`identity.ts:7–24` 每次顺序访问 UC verify 和 info，各自 10 秒超时。当前代码没有身份缓存。

读取 18 个项目的会话至少带来 18 次 validate、36 次 UC 调用，不含项目列表、消息轮询、续租、heartbeat。上述链路能解释为什么小响应也可能很慢，但不能没有 trace 就把此前 11 秒全归因给 UC。

建议先加入相同 requestId 的分段计时：
- 手机：操作总时长、锁等待、stream-open、收发、JSON decode、条数/字节数。
- host：请求排队、lease validate、本地 sidecar、编码。
- Connect：UC verify、UC info、DB、结果分类。
- sidecar：instance 冷启动/加载、查询、序列化。

日志不记录 token、secret、ticket、附件内容、完整敏感路径。身份优化应明确短期凭证/有界缓存的有效期、账号绑定、撤销窗口和过期处置；仍执行必要的租约/endpoint/独占校验。

### P0：错误类型被折叠

host `main.rs:326–327` 将 validate 的网络失败、服务不可用、登录失效与真实租约拒绝统一返回 403 `Lease invalid or unavailable`。手机又把多数 FFI exception 统一置 `connectionFailed`。

业务不允许、认证失效、租约过期、网络中断、限额、解析/协议错误应保持区分。503 不应显示成操作无权限；业务 4xx 不应触发整条连接重建。

## 4. 列表读取和分页语义

### P1：最近 10 条没有落地

Android `DesktopSessionsScreen.kt:332–348,366–379`：先读 `/project`，再依次读每个项目 `/session?directory=...`，最终本地合并排序。iOS 有相同串行扫取。

准确说不是无限全量：legacy `/session` 默认每项目最多 100 条，且先 pinned、后 updated 排序。跨项目扫取既不是首屏 10 条，也不能保证代表全电脑真正的最近活跃会话；当前已加载条数不等于项目总会话数。

目标：
- 最近页独立跨项目查询，首屏 10 条，不依赖所有项目会话读取。
- 项目页只读项目，不预读每个项目的会话。
- 点击某项目，独立加载该项目首屏 10 条；加载更多明确作用于当前列表。
- 搜索覆盖服务端范围，不把缓存中的局部匹配伪装为完整搜索。
- 消息先读取最近一页，向上加载历史，避免每 2 秒重新下载整个对话。

### 现有接口不能只换 URL

1. legacy `/session` 的 `start` 是 `time_updated >= start`，不是向历史翻页游标。
2. `/experimental/session` 能跨项目按 updated 查询，支持 limit/cursor；但排序有 pinned/update/id，游标仅 timestamp，因此并非可靠的复合边界。手机与 host 尚未放行此路径。
3. `/api/session` 已有 JSON body 中的 `data` 和 `cursor.previous/next`，核心用 time/id 的复合边界；但是当前按 **created** 排序，不是最近活跃的 **updated**。不能把“最近创建”冒充“最近活跃”。
4. bridge 响应只有 status/body，legacy 消息及 experimental 列表响应头中的游标不能直接传到手机。

推荐在现有 API/适配边界中明确 recent 的 updated 排序和稳定复合游标，优先复用现有分页结构；项目列表及消息也采用明确的 typed page。若使用响应头，仅传白名单分页 metadata，不透传凭证或任意 headers。需要覆盖同时间多条数据、分页间更新、跨页去重与搜索条件绑定。

### 项目 11 秒的另一个待验证因素

InstanceContext middleware 会先 `InstanceStore.load(directory)`；cold load 包含 bootstrap，已有 cache。应分离冷/热耗时，不能猜成“18 条 SQL 很慢”或直接做 speculative DB 优化。

已有会话请求的 workspace middleware 会优先使用服务端 session.directory。因此手机用 project root 不能直接证明当前详情一定错路由，但手机的数据模型仍丢了会话的真实目录，展示、项目筛选和新建上下文应保留该信息。

## 5. 状态与生命周期

### P0：多个连接管理入口

Android 至少包括 root 自动恢复、登录 sheet 注册/恢复、会话页前后台探测/重连。不同入口共享 bridge，却没有一个统一的连接过程状态。screen 重连流程和手动选择电脑可能并发；多个按钮只检查 busy，不检查连接转换状态。

租约续租一次失败标 unhealthy；host heartbeat 一次失败也会标 offline。服务端在线窗口为 30 秒，租约窗口为 60 秒。需要区分短暂异常和真正失效，不能把一个 bool 同时当传输健康、身份有效、主机在线和页面可交互。

正确恢复不是保证后台永不断线，而是：前台恢复时确认旧租约/连接是否有效，按状态 resume 或 reacquire，保留原页面/草稿，恢复数据订阅；不自动重发已经接纳的消息。不可取消网络流程使用 NonCancellable 会削弱退出和切换主机的行为，只保留确有必要的短清理边界。

### P0：页面状态和操作状态混用

Android 页约 1100 行，iOS 同样。大量 remember/@State 管项目、会话、连接、模型、消息、草稿、附件和 busy。当前 act 的同步门闩能挡重复点击，但一个慢动作禁用全页面并静默丢弃其它操作；它不是完整的生命周期管理。

页面切换也缺少 host/session generation 验证：旧请求回包可更新当前页面；草稿、附件没有以 host/session 为稳定归属。manifest 的 orientation configChanges 已缓解旋转重建，但不是进程恢复与导航持久化的替代方案。

### 推荐的最小架构调整

遵守手机 AGENTS：共享 runtime 归 Rust，原生只管渲染和平台能力。

- 利用现有 MobileClient/AppStore 边界加入 typed desktop adapter/state，保持单一 UniFFI surface，不新增公共 bridge crate。
- 唯一连接协调者管理选中 host、generation、lease、健康、恢复任务。
- typed 错误：认证、租约、主机忙、网络、业务拒绝、协议/大小错误。
- 读取缓存与进行中的请求按 host/project/session/query/page 归属；过期回包丢弃或取消，失败仍可观察。
- 原生保留页面路径、焦点、键盘、选择器、权限、音频及必要的平台持久化。不要简单把巨型页面挪到两端各一套巨型 ViewModel。
- CLI 与 desktop 保持各自协议；适配成兼容 UI projection 后复用消息组件和交互模式，不伪装成相同 RPC。

连接状态（示意，不是已实现 enum）：Disconnected / Connecting / Ready / Degraded / Reconnecting / AuthExpired。
独立页面状态：列表初载/刷新/加载更多；模型加载；创建中；附件上传；发送接纳；回复执行。连接恢复期间保留内容，禁用确实不能执行的动作，不用一个全局 busy 封锁整页。

## 6. 会话详情不是完整会话状态

### P1：有损消息投影与错误的回复等待判断

`DesktopMessage` 只有 role/text/kind/status/details，缺 messageId/partId/activityId。parser 主要处理 text/tool/file，丢弃其它状态和部分文件信息；相邻工具归并发生在 flatten 后，缺少原始 message 边界。

回复等待依赖 UI assistant 条目数量是否增加。工具条目也算 assistant：可能只有工具出现就停止等待；同一回复更新又可能不增加条目。不能用气泡数量判定模型执行结束。

改为基于真实 admission/activity/execution 状态，保留稳定 message/part 身份，再做紧凑 projection。工具组不得跨活动/消息乱合并。权限、问题、终止虽然部分路径已 allowlist，当前页面没有完成对应操作闭环，不应以“仍在思考”掩盖等待用户处理。

流式需要明确事件/订阅协议、cursor/replay、断线恢复和有界背压。现有 bridge 是 read-to-end JSON，直接放行无限 SSE 不能完成流式能力。

### P0：写操作结果不明时缺少核对

发送没有稳定的客户端 prompt ID，超时后不能判断“未接纳”还是“已接纳但回包丢失”。新建与模型加载绑定在一个动作中，创建成功后的模型失败也容易呈现成整体失败。当前 new 后已不再全量刷新会话，不把已移除的问题当现状。

服务端 legacy prompt_async 在 V2 启用时已经转入 DesktopSessionV2.admit；并非完全绕过 V2。/api/session/{id}/prompt 也已有持久接纳响应。需要沿真实语义接入稳定 prompt ID、精确重复校验和结果核对，不做 blindly retry，也不只改路径。

拆开：已创建 / 模型配置失败；待接纳 / 已接纳 / 生成中 / 等待授权 / 完成 / 失败。重连不代表重新执行模型请求。

### P1：模型与 ASR 仍有不一致

打开会话时 Android 清空 selectedModel，但 loadModels 优先使用项目最近某个会话的模型，而非打开会话自身。应明确既有会话模型、桌面项目默认模型与可见模型策略的优先级，不偷偷 fallback 到其它模型。

Android 已走 ASR 请求，但当前硬编码一个模型；iOS DesktopSpeechInput 仍用 SFSpeechRecognizer，未满足与 CLI 一致的 ASR 要求。共享 ASR 配置/鉴权/转写语义，音频录制与权限继续归平台。

附件需有可观察的准备/上传/发送阶段及模型能力检查；host 请求有 4 MiB 上限、手机响应有 8 MiB 上限，不承诺任意大小文件支持。必要的分块/上传协议应单独设计，不随意扩大内存上限。

## 7. 交互评审与页面契约

目前不舒服的原因是：同一页面没有稳定语义；主操作重复；加载覆盖用户正在看的内容；连接状态过于占位；错误不告诉用户真正该怎么做。

导航保持简单且有固定入口：

```text
首页：未连接显示橘猫；已连接显示简洁主机状态
连接成功 → 最近会话
最近会话 ──固定项目入口──→ 项目列表 → 项目会话 → 会话详情
最近会话 ─────────────────────────────────→ 会话详情
选择电脑：独立选择器，不改变项目/会话列表的语义
```

- 不加左侧抽屉、不恢复含义混乱的 tabs；项目入口不放长列表尾部。
- 最近页永远是全电脑最近活跃会话；项目会话页永远是该项目，不自动改名成最近页。
- 同层页面标题对齐；顶部只留简洁的 host/状态入口，不每屏放大状态卡。
- 主机显示可读名称，UUID 仅诊断用途。
- 每屏明确一个主要新建入口；项目行的 + 作为次要快捷操作，不能形成重复弹层/重复创建。正在创建只禁用同一创建操作。
- 全局新建先确认项目；项目内新建使用当前项目，不突然换上下文。
- 隐藏不可操作的全局/占位项目；根据真实项目身份处理，不仅凭 directory == '/' 过滤合法路径。
- 标题为主要信息；路径是次级，缩略/按需展示。不拿已加载条数冒充总数。
- 初次没有内容才中心 spinner + 加载文案；刷新/加载更多不遮挡已有内容；回复等待只在回复位置做文字 shimmer。
- 工具活动按真实活动折叠为紧凑状态组，保留展开详情；避免每个底层事件一张 card。
- 模型/附件/语音是 composer 的相关操作；草稿、焦点、附件在切页面/重连后不无故消失。提交确认后收键盘。
- 保留现有浅暖色/橘色，不再另造暗色主题或下载无关参考素材。

## 8. 修复顺序与最小验收

推荐“保留链路、分层修复”，而不是继续逐截图打补丁，也不是全端推倒重写。

### 第一闭环：连接能恢复、操作不互相堵死（P0）

分段 trace → 短锁/generation → host 有界 stream 并发 → 错误分类 → 唯一恢复入口与取消规则。身份优化在 trace 证实与安全边界明确后实施。

验收：慢读期间仍可离开页面/断开；旧 host 回包不影响新 host；后台租约过期后前台可恢复；手动连接不和自动恢复争抢；错误原因可辨。

### 第二闭环：先看到内容，不扫全电脑（P1）

最近 updated 首屏 10 条及可靠 cursor → 项目懒加载 → 项目会话首屏 10 条 → 消息尾页/历史分页 → 按键缓存与结果归属。

验收：最近首屏不依赖 18 个 /session 请求；追加加载明确 10 条一页；同时间会话无静态分页漏项；不下载整段历史才显示详情。

### 第三闭环：新建/发送/执行状态可信（P0/P1）

创建与模型加载拆分 → 当前会话模型优先 → prompt ID/持久接纳核对 → 活动/权限/问题/停止 → 紧凑消息 projection → 可恢复增量更新；流式按独立协议评估落地。

验收：模型加载失败不产生重复新建；已接纳但回包丢失不重复发送；工具出现不误判回复完成；权限/问题有明确入口；附件错误落在具体阶段。

### 第四闭环：原生 UI 与平台对齐（P1/P2）

固定导航与标题 → 减少状态卡/重复加号 → 统一 loading/composer → Android 与 iOS ASR、恢复和消息显示对齐。UI 契约可以同步定，但不在旧状态机上堆更多动画。

验收：项目页连续点击两个 + 不死锁/不叠重复操作；旋转保留当前路由；进程重建恢复规则明确；重连保留草稿；浅色 UI 无非预期变化。

### 运行观测与验收目标（不是已经测到）

- 同一测试网络/主机下，最近首屏目标 1–2 秒；连接后不阻塞等待全部项目扫描。
- 有缓存时返回列表立即展示缓存，刷新状态独立且明确，不能伪造刷新成功。
- 手动取消/切页及时响应，不等待网络阶段全部 timeout；后台恢复按明确总 deadline 给出成功或可行动错误。
- 性能按冷/热、直连/relay、前台/恢复分别记录 p50/p95，不以单次成功判定。

本轮未达到这些目标，也未验证 iOS 编译。下一轮先做第一、第二闭环的代码与必要回归，再继续会话闭环；一次完整阶段完成后再构建给用户测试，避免反复让用户重装只包含外观补丁的 APK。

## 9. 关键证据索引

以下路径均相对于各自上述工作区，代码可能随后续修改移动行号。

| 工作区 | 文件及区段 | 证据 |
|---|---|---|
| 手机 | shared/rust-bridge/codex-mobile-client/src/ffi/desktop_connect.rs:264–299,372+,580–596 | 状态锁、连接阶段、超时及整包响应 |
| 桌面互联 | packages/desktop-connect-host/src/main.rs:180–199,302–349 | 同连接串行、每请求鉴权、错误折叠 |
| Connect main | packages/necode-connect/src/app.ts:10–14; identity.ts:7–24 | 每次身份验证、UC 两次顺序请求 |
| Connect main | packages/necode-connect/src/mysql-store.ts:16–24,93+; deploy/schema.sql:26–27 | 行锁与一对一约束 |
| 手机 | apps/android/app/src/main/java/com/litter/android/ui/desktop/DesktopSessionsScreen.kt:163–220,322–402,451–526,618–619,791–822 | 投影、列表、busy、恢复、模型、发送 |
| 手机 | apps/android/app/src/main/java/com/litter/android/ui/LitterApp.kt:143–174; ui/discovery/DesktopConnectSheet.kt:121–180 | 其它自动恢复/注册入口 |
| 手机 | apps/ios/Sources/Litter/Views/DesktopSessionsView.swift:232–281,893–905,1048–1105 | 另一套生命周期、串行会话列表、系统语音 |
| 桌面互联 | packages/opencode/src/session/session.ts:614–633,1119–1141 | 更新时间列表游标与 legacy start/limit |
| 桌面互联 | packages/server/src/groups/session.ts:90–107,144–160; handlers/session.ts:23–60 | V2 页结构、prompt 接纳、创建时间游标 |
| 桌面互联 | packages/core/src/session.ts:269–300 | V2 创建时间与 ID 复合分页 |
| 桌面互联 | packages/opencode/src/session/prompt.ts:1788–1796 | legacy async 对 V2 admission 的转接 |
| 桌面互联 | packages/desktop-connect-host/src/protocol.rs:14–18,63–103 | 响应无 headers、现有 allowlist |
| 桌面互联 | packages/opencode/src/server/routes/instance/httpapi/middleware/workspace-routing.ts:181–183,220–232; middleware/instance-context.ts; project/instance-store.ts:140,200+ | session 实际目录、instance load/cache/bootstrap |

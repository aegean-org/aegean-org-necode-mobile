# NeCode 手机端接入说明

本文记录当前已经跑通的 NeCode 手机端链路、具体命令、验证方式和常见问题。目标是让用户用 Litter Android App 作为 NeCode 的手机端入口，通过自建 relay 连接回自己的电脑，由本机 NeCode 真正执行代码分析和模型调用。

## 1. 整体链路

```text
手机 Litter App
  -> 自建 iroh relay: https://relay.inoteexpress.com
  -> 本机 kittylitter / alleycat daemon
  -> necode bridge / ACP 适配层
  -> 本机 NeCode CLI
  -> 模型后端 / 网关
```

返回链路相反：

```text
模型结果
  -> NeCode
  -> necode bridge
  -> kittylitter / alleycat
  -> relay.inoteexpress.com
  -> 手机 Litter App
```

关键点：

- 手机端不直接跑 NeCode。
- relay 只负责网络中继，不负责读代码、不跑模型、不保存项目逻辑。
- 真正读取项目目录、执行分析、调用模型的是用户本机的 NeCode。
- 手机端和电脑不需要在同一个局域网，只要手机和电脑都能访问 `https://relay.inoteexpress.com`。

## 2. 本地目录

当前涉及两个本地仓库：

```powershell
D:\project\litter
D:\project\alleycat
```

`D:\project\litter` 是手机端 App 仓库，`services\kittylitter` 是 daemon wrapper。

`D:\project\alleycat` 是实际 daemon / bridge 实现仓库。当前 `services\kittylitter\Cargo.toml` 通过本地 path 依赖它：

```toml
alleycat = { path = "../../../alleycat/crates/alleycat" }
```

所以在 `D:\project\litter\services\kittylitter` 执行 `cargo run -- serve` 时，实际用的是本机 `D:\project\alleycat` 里的 alleycat 代码。

## 3. 前置条件

### 3.1 Windows 本机

需要安装：

- Rust / Cargo
- Git
- NeCode CLI
- Android Studio
- Android SDK / platform-tools
- 一台 Android 手机或模拟器

检查基础命令：

```powershell
cargo --version
git --version
necode --version
```

如果 `necode --version` 不通，先把 NeCode CLI 放进 PATH。

### 3.2 Android 环境变量

当前本机建议使用 D 盘路径，避免 C 盘空间不足：

```powershell
$env:JAVA_HOME="D:\tools\Android\Android Studio\jbr"
$env:ANDROID_HOME="D:\Android"
$env:ANDROID_SDK_ROOT="D:\Android"
$env:ANDROID_USER_HOME="D:\.android"
$env:GRADLE_USER_HOME="D:\.gradle"
$env:CARGO_HOME="D:\.cargo"
```

验证 Android SDK 和 ADB：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" version
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
```

`adb devices` 能看到设备，才可以安装 APK。

## 4. 配置 kittylitter / alleycat daemon

### 4.1 查看 daemon 状态和配置文件位置

进入 kittylitter：

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- status
```

正常会输出类似：

```text
kittylitter daemon
  pid:               0
  version:           0.3.4
  node id:           e1cae31de248cb5373090ddd780e53e9cb45f1132bb904afe65df2f42e9a0682
  token (sha256/16): xxxx
  relay:             https://relay.inoteexpress.com
  config:            C:\Users\<你>\AppData\Roaming\sigkitten\kittylitter\config\host.toml
  uptime (s):        <daemon not running>
  agents:
    necode display="Necode" wire=jsonl available=true
```

重点看三项：

- `relay` 是否是 `https://relay.inoteexpress.com`
- `config` 的实际路径
- `agents` 里是否有 `necode`，并且 `available=true`

### 4.2 编辑 host.toml

用 status 输出里的 `config` 路径打开配置文件。Windows 默认类似：

```powershell
notepad "$env:APPDATA\sigkitten\kittylitter\config\host.toml"
```

确认至少包含以下配置：

```toml
relay = "https://relay.inoteexpress.com"

[agents.necode]
enabled = true
bin = "necode"
```

如果 `necode` 命令不在 PATH，可以把 `bin` 改成绝对路径，例如：

```toml
[agents.necode]
enabled = true
bin = "D:\\path\\to\\necode.exe"
```

`relay` 改完必须重启 daemon，因为 iroh endpoint 是启动时绑定的。只执行 reload 不会切换 relay。

## 5. 启动本机 daemon

在一个单独的 PowerShell 窗口里执行：

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- serve
```

成功启动时会看到类似日志：

```text
INFO loaded persistent identity node_id=e1cae31de248cb5373090ddd780e53e9cb45f1132bb904afe65df2f42e9a0682
INFO alleycat endpoint bound node_id=e1cae31de248cb5373090ddd780e53e9cb45f1132bb904afe65df2f42e9a0682
INFO alleycat endpoint online addr=EndpointAddr { id: PublicKey(...), addrs: {Relay(https://relay.inoteexpress.com/), Ip(...)} }
```

判断是否成功：

- 出现 `alleycat endpoint online`
- `addrs` 里出现 `Relay(https://relay.inoteexpress.com/)`
- 没有直接退出到命令行

这个窗口要保持运行。关掉窗口后，手机端连接会断。

## 6. 生成手机配对二维码

保持 daemon 运行，再开一个 PowerShell 窗口：

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- pair --qr
```

命令会先输出一段 JSON，再输出二维码。JSON 类似：

```json
{
  "v": 1,
  "node_id": "e1cae31de248cb5373090ddd780e53e9cb45f1132bb904afe65df2f42e9a0682",
  "token": "6c0cb97c3db051c98c4853a546bd548f488eddc80687ed657877ad371bbee9ef",
  "host_name": "你的电脑名",
  "relay": "https://relay.inoteexpress.com"
}
```

字段含义：

- `node_id`：本机 daemon 的 iroh 身份。
- `token`：手机连接本机 daemon 的认证 token。
- `host_name`：手机端展示用的主机名。
- `relay`：手机连接时使用的中继地址。

注意：

- 这段 JSON 和二维码都包含连接 token，不要发到公共群里。
- 如果要让旧二维码失效，执行 `cargo run -- rotate` 重新生成 token。

## 7. 手机端连接

在 Android 手机上打开 Litter App：

1. 点击添加远程服务器。
2. 选择扫码。
3. 扫描 `cargo run -- pair --qr` 输出的二维码。
4. 添加成功后进入该服务器。
5. 选择 agent：`necode` / `Necode`。
6. 新建会话，选择项目目录。
7. 发送一条消息，例如：

```text
分析一下当前项目结构
```

预期结果：

- 本机 daemon 窗口出现 JSON-RPC 请求日志。
- 手机端能收到 NeCode 的回复。
- 如果选择了项目目录，NeCode 会在该目录上下文里工作。

## 8. 本机命令行自测，不依赖手机

如果怀疑手机端问题，可以先用 `probe` 模拟手机连接。

### 8.1 查看远端可用 agents

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- probe
```

正常会看到 agents 列表，其中应该包含：

```text
necode
```

### 8.2 直接连接 necode 并列出会话

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- probe --agent necode --method thread/list
```

这条命令会走完整的 iroh / relay / bridge 流程，但不经过手机 UI。它能通，说明本机 daemon 到 NeCode 的链路基本没问题。

## 9. Android App 构建和安装

### 9.1 进入 Android 工程

```powershell
cd D:\project\litter\apps\android
```

### 9.2 设置环境变量

```powershell
$env:JAVA_HOME="D:\tools\Android\Android Studio\jbr"
$env:ANDROID_HOME="D:\Android"
$env:ANDROID_SDK_ROOT="D:\Android"
$env:ANDROID_USER_HOME="D:\.android"
$env:GRADLE_USER_HOME="D:\.gradle"
$env:CARGO_HOME="D:\.cargo"
```

### 9.3 编译检查

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

### 9.4 跑 Android 单测

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### 9.5 打 debug APK

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 位置：

```text
D:\project\litter\apps\android\app\build\outputs\apk\debug\app-debug.apk
```

### 9.6 安装到手机

先确认设备：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
```

安装：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r D:\project\litter\apps\android\app\build\outputs\apk\debug\app-debug.apk
```

启动 App：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.sigkitten.litter.android/com.litter.android.MainActivity
```

如果安装时报签名不一致：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" uninstall com.sigkitten.litter.android
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r D:\project\litter\apps\android\app\build\outputs\apk\debug\app-debug.apk
```

卸载会清掉手机端本地保存的服务器和会话状态。

## 10. NeCode 模型配置

### 10.1 检查 NeCode 是否已登录和选模型

在电脑上直接跑 NeCode：

```powershell
necode
```

在 NeCode 里确认模型：

```text
/model
```

如果 daemon 日志出现：

```text
No model selected.

Use /login, set an API key environment variable, or create C:\Users\<你>\.omp\agent\agent.db

Then use /model to select a model.
```

说明当前 NeCode 没有可用模型，或者 modelRoles 没有选中有效模型。先在本机 NeCode 里完成登录和选模型，再重启 `cargo run -- serve`。

### 10.2 当前 qwen3.5 视觉能力临时配置

如果后端模型支持识图，但 NeCode 显示“模型不支持图片”，需要让 NeCode 的模型元数据知道该模型支持 image input。

临时本地配置文件：

```powershell
notepad "$env:USERPROFILE\.omp\agent\models.yml"
```

内容示例：

```yaml
providers:
  ne:
    modelOverrides:
      qwen3.5:
        input:
          - text
          - image
```

同时确认默认模型配置：

```powershell
notepad "$env:USERPROFILE\.omp\agent\config.yml"
```

示例：

```yaml
modelRoles:
  default: ne/qwen3.5:high
  smol: ne/qwen3.5:high
  vision: ne/qwen3.5
```

改完后重启 daemon：

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- serve
```

长期更好的做法是让后端 `/models` 返回该模型支持图片，例如返回 `input: ["text", "image"]`，这样就不需要每台机器单独写 `models.yml`。

## 11. 验证清单

每次从头验证时按这个顺序走：

### 11.1 relay 可访问

```powershell
curl.exe -i https://relay.inoteexpress.com/generate_204
```

只要能返回 HTTP 响应，说明域名和入口基本可达。

### 11.2 daemon 配置正确

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- status
```

检查：

```text
relay: https://relay.inoteexpress.com
necode display="Necode" wire=jsonl available=true
```

### 11.3 daemon 在线

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- serve
```

检查日志：

```text
alleycat endpoint online
Relay(https://relay.inoteexpress.com/)
```

### 11.4 本机 probe 能通

另开窗口：

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- probe --agent necode --method thread/list
```

### 11.5 手机扫码能连

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- pair --qr
```

手机扫码后检查：

- 能看到服务器。
- 能看到 `necode`。
- 能创建会话。
- 能收到文字回复。

### 11.6 图片能力验证

手机端发一张图片并提问：

```text
看下这张图里报错是什么
```

预期：

- daemon 不再报 ACP 参数校验错误。
- NeCode 不再提示模型不支持图片。
- 模型能基于图片内容回复。

## 12. 常见问题

### 12.1 pkarr_publish tls handshake eof

日志示例：

```text
WARN pkarr_publish{me=...}: Failed to publish to pkarr err=... https://dns.iroh.link/pkarr/... tls handshake eof
```

当前链路使用二维码里的 `node_id + token + relay` 直连，已经固定 relay 为 `https://relay.inoteexpress.com`。这种 `pkarr_publish` 警告不影响当前手机连接，可以先忽略。

只要日志里有：

```text
Relay(https://relay.inoteexpress.com/)
```

并且手机能连上，就说明主链路是通的。

### 12.2 手机端断开，重新打开 App 后恢复

这是当前已观察到的体验问题。现象是手机端断开后，重新打开 App 经常能恢复。

处理方式：

1. 先确认本机 daemon 窗口还在运行。
2. 手机端完全退出 Litter App 后重新打开。
3. 如果仍不恢复，重启 daemon：

```powershell
cd D:\project\litter\services\kittylitter
cargo run -- serve
```

这个问题不代表主链路不通，但说明移动端重连体验还需要继续打磨。

### 12.3 No model selected

日志示例：

```text
No model selected.

Use /login, set an API key environment variable, or create C:\Users\<你>\.omp\agent\agent.db

Then use /model to select a model.
```

处理：

```powershell
necode
```

在 NeCode 内执行：

```text
/login
/model
```

确认能在本机 NeCode 里正常对话后，再重启 daemon。

### 12.4 发图后提示模型不支持图片

如果后端实际支持图片，但 NeCode 仍提示不支持，多半是模型元数据没声明 image input。

先加本地覆盖：

```powershell
notepad "$env:USERPROFILE\.omp\agent\models.yml"
```

写入：

```yaml
providers:
  ne:
    modelOverrides:
      qwen3.5:
        input:
          - text
          - image
```

然后重启 daemon。

### 12.5 ACP session not found

日志示例：

```text
ACP session not found: 019eede3-5f22-7000-92b2-4a8df6546ea8
```

通常是手机端还在恢复旧会话，但本机 NeCode / bridge 里的运行态已经没了。

处理：

1. 手机端新建一个会话。
2. 或重启 Litter App。
3. 必要时重启 daemon。

### 12.6 session 已绑定到另一个目录

日志示例：

```text
ACP session ... is already loaded for d:\project\qt-note, not D:\
```

说明手机端尝试用旧 session 去切换另一个 workspace。当前先按新目录新建会话处理。

### 12.7 选择目录交互不好用

当前手机端选择目录体验还不够贴合 NeCode。建议：

- 优先选择具体项目目录，例如 `D:\project\qt-note`。
- 不要选择盘符根目录，例如 `D:\`。
- 如果选择错了，直接新建会话并重新选择目录。

### 12.8 Android 安装不上

先确认设备：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
```

如果设备存在但安装失败，常见处理：

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" uninstall com.sigkitten.litter.android
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r D:\project\litter\apps\android\app\build\outputs\apk\debug\app-debug.apk
```

### 12.9 C 盘空间不够

把 Android / Gradle / Cargo 缓存放到 D 盘：

```powershell
$env:ANDROID_HOME="D:\Android"
$env:ANDROID_SDK_ROOT="D:\Android"
$env:ANDROID_USER_HOME="D:\.android"
$env:GRADLE_USER_HOME="D:\.gradle"
$env:CARGO_HOME="D:\.cargo"
```

如果需要长期生效，可以在 Windows 系统环境变量里设置同名变量。

## 13. relay 部署信息

当前 relay：

```text
https://relay.inoteexpress.com
```

alleycat 仓库里已有 relay 部署说明：

```text
D:\project\alleycat\deploy\iroh-relay\README.md
```

如果以后需要重新构建镜像：

```powershell
docker build -t ghcr.io/aegean-org/iroh-relay:0.98.0 D:\project\alleycat\deploy\iroh-relay
docker push ghcr.io/aegean-org/iroh-relay:0.98.0
```

如果有 `kubectl`：

```powershell
kubectl apply -f D:\project\alleycat\deploy\iroh-relay\k8s.yaml
kubectl -n necode-relay get svc iroh-relay-public
kubectl -n necode-relay logs deploy/iroh-relay -f
```

当前集群入口是 HAProxy -> Ingress -> Service。模板禁用了 QUIC 地址发现，走 HTTP/WebSocket relay，当前使用不受影响。

## 14. 当前已知限制

- Litter 是 GPLv3 项目，后续如果对外分发修改版 APK，需要认真处理开源合规问题。
- 当前手机端 UI 不是专门为 NeCode 设计的，目录选择、会话恢复、图片交互还有明显产品体验问题。
- relay 是自建基础设施，需要持续可用；如果 relay 域名或入口挂了，外网手机就连不回本机。
- NeCode 的图片能力依赖模型元数据和后端真实能力，两边都要声明支持才行。
- daemon 必须在用户电脑上运行；电脑关机或 daemon 退出，手机端无法继续控制 NeCode。



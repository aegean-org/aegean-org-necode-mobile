# 移动端 NE Auth V2

日期：2026-10-14。Android 与 iOS 共用 Rust 认证状态机，`client_id` 固定为 `necode-mobile`。

## 认证边界

- `shared/rust-bridge/codex-mobile-client/src/ffi/ne_auth.rs`：V2 密码登录、严格验证码配置、身份校验、轮换刷新、远端退出。
- 平台 `NeSessionStore` 回调只负责同步安全读写及 compare-and-swap。Android 使用独立加密偏好文件；iOS 使用 device-only Keychain。刷新凭据不进入普通偏好、网页或日志。
- `NeLogin.session_id` 是稳定的本地登录标识，不是 access token。设备发现、租约申请/续租/释放每次通过 Rust 获取当前令牌；语音先停录音，再获取当前令牌上传。
- 登录与身份校验不再发送 AUT 参数/头。旧 V1 身份只保留同账号设备信息，不能转换成 V2 令牌；首次升级需重新登录。
- 新令牌有效期、总会话期限必须有效，刷新不能延长总期限。到期前 30 秒刷新，合并同一认证对象的并发刷新。
- 刷新请求前持久化 request_id 与开始时间；结果不确定时仅在 10 秒内复用相同 request_id，单次最多等待 4 秒。过窗要求重新登录，不盲用旧 refresh token。存储失败保留已交付的新令牌并优先补保存。
- Connect 明确返回认证阶段 HTTP 401 时最多刷新重试一次。超时、5xx、其他不明确业务结果不自动重放。UC 身份接口的明确 code=2001 认证拒绝同样最多刷新一次。
- 退出先关闭桌面传输，再请求 sessionlogout；撤销失败保留凭据并显示错误。旧租约因凭据失效无法释放时，本地传输已关闭，由服务端租约 TTL 回收。

## 验证码

共用 `ne_captcha.html`，页面仅接收公开配置。Android WebView 与 iOS WKWebView 传回一次性 opaque proof，服务端做最终验证。不把密码/access token/refresh token 注入页面。不复用失败、超时、关闭页面后的验证结果。

服务端需启用 `necode-mobile` 客户端；如果开启 Aliyun 验证码，需要实际验证 H5/APP 场景及来源配置。未使用真实账号或自动完成验证码。

## 验证记录及打包注意

- 直接引用实际 Rust 模块的轻量 harness：62 passed，0 failed，1 ignored（公网 relay 测试）。包含 15 项 V2 认证测试以及原有 Connect/会话/附件/语音回归。
- 验证码 JavaScript：5 组本地模拟生命周期验证通过，非真实供应商联调。
- UniFFI Swift/Kotlin 绑定已由工具生成；未手写生成代码。
- Android `:app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --offline` 通过；没有运行设备 instrumentation 测试或安装 APK。
- Windows 主机完整库曾以低调试信息构建成功并用于生成绑定。最后增加 UC 认证拒绝及账号切换回归后，全量重编因页面文件不足（os error 1455）失败；最终源码由轻量 harness 验证，不将旧主机 DLL 当作最终产物。
- iOS 尚未在 Mac 编译，真实登录、验证码、长连接跨令牌过期及退出撤销仍需设备联调。
- FFI 签名已变化，下一次 Android/iOS 打包必须重新编译共享 Rust 库并生成绑定；不能只跑 Gradle/Xcode 链接旧 `.so` / `.a`，也不要使用 skip-bindings 跳过本次接口更新。iOS 新文件通过现有 `project.yml` 目录规则纳入，拉取后重新生成 Xcode 工程。
- 源码接入阶段未打包、未调整版本号、未提交或推送；后续按用户要求构建 debug APK，记录如下。

## Android debug 打包验证（2026-10-14）

- 最终源码交叉编译 `aarch64-linux-android` / `android-dev` 成功（6 分钟），此前 Windows 主机库的页面文件限制没有阻塞本次 Android 原生库构建。
- Kotlin 绑定从本次 Android `.rlib` 重新生成；不能从已 strip 的 `.so` 推断生成成功（可能零输出），也不能沿用 Windows 主机绑定。实测主机与 Android 的 `register_android_tools` API checksum 不同。
- 从最终 Android `.so` 读取并比对全部 301 个 UniFFI API checksum，与当前 Kotlin 绑定全部一致。
- 重新执行 `:app:assembleDebug --offline` 成功。未自动安装；真人登录、验证码及跨令牌过期连接仍待手机验证。
- 产物：`apps/android/app/build/outputs/apk/debug/necode-mobile-auth-v2-debug-20261014.apk`，186223606 bytes（177.6 MiB），与该目录 `app-debug.apk` 相同。
- 包名 `com.aegean.necode.mobile`，debuggable，arm64-v8a，版本 `1.0.0 (1)`；使用 debug 签名。
- APK Signature Scheme v2 与 ZIP alignment 校验通过。构建 `.so` → JNI → merged 匹配，stripped → APK 内条目哈希匹配。
- APK SHA-256：`EE3DA4E64286695E016D5E235D527855902CC45DC0616308DE8906EEB75F3BD2`。
- 本次仍未提交、推送或修改版本号。

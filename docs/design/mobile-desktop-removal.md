# 移除旧电脑登记（2026-10-14）

## 已确认方案

离线电脑右侧显示更多菜单，选择“移除电脑”后确认。仅删除本人账号的 Connect 设备登记及已过期租约，不接触电脑上的项目、文件或会话。在线电脑、当前连接的电脑和仍有有效租约的电脑不允许移除，不按同名合并或删除。

## 接口与并发保护

- `DELETE /v1/desktops/{desktopId}`，沿用用户 V2 Bearer 认证，不传设备 secret。
- 缺失或非本人设备返回 404；在线设备或有效租约返回 409；确认移除返回 `{ "ok": true }`。
- SQLite 使用事务。MySQL 在事务中先锁桌面行、再锁关联租约并观察当前时间，与申请租约、恢复租约、登记恢复及续租串行化。
- MySQL 的已有设备重新登记也纳入同一个桌面行锁，避免登记读取成功、随后被删除、却仍返回登记成功的竞态。
- 不改数据库表结构，不直接清理生产数据库。删除后的旧 id/secret 不再可用，再次使用需重新登记。

## 客户端

- Rust 校验电脑 ID，不允许删除正在连接/连接中的本机记录；每次通过 V2 会话获取当前令牌。
- 只有服务端确认 `{ "ok": true }` 才清理同 ID 的内存偏好；Android/iOS 同步清掉对应的最近连接记录并刷新列表。
- 网络失败、409、未确认结果都不假装成功。旧版服务的 404 明确提示先更新 Connect。
- Android 登录选择页与已连接后的切换页复用同一移除交互；iOS 两个入口同样支持更多菜单和确认框。在线状态变化后禁用确认，最终仍由服务端校验。

## 发布顺序

1. 后端改动位于桌面仓库的 `packages/necode-connect`；提交/推送后运行 main 流水线的 `connect image`，使用实际发布镜像更新 Deployment。
2. 手机源码位于 `litter-mobile-desktop-connect` 工作区的 `mobile-desktop-connect` 分支。新 FFI 必须重编共享 Rust 库并重新生成绑定，再构建 Android/iOS 包。
3. 不能只升级客户端后宣称线上已支持移除；旧服务会明确拒绝。源码实现阶段不自动提交、推送、部署、打 APK 或删除现有设备记录；后续已按用户要求构建 Android debug APK，交付记录见下方。

## 验证边界

- Connect 测试：10 passed，2 skipped（真实 MySQL、打包 Host 联调）；包级 typecheck 通过。
- 共享 Rust 实际模块回归：65 passed，1 skipped（公网 relay）。新增请求协议、非法 ID、冲突保留偏好、未确认结果及旧服务提示测试通过。
- MySQL 实库用例已补充跨账号、有效租约和登记/删除竞争断言，但当前没有独立测试库配置，未运行，不能将 SQLite 回归当成 MySQL 实库验证。
- Android 原生库 `android-dev` / arm64-v8a 重编成功。Kotlin 绑定从对应 Android `.rlib` 生成，并与实际 `.so` 的全部 302 个 API checksum 比对一致，包含 removeDesktop。
- Android 主应用及 androidTest Kotlin 编译通过；补了仅离线设备可移除、必须确认、取消不执行的 UI 测试，但没有运行设备 instrumentation；后续 debug APK 已构建并通过静态检查，未自动安装。
- Swift 新接口签名通过生成器核对为 `removeDesktop(sessionId:desktopId:)`。用于核对的 Android 元数据生成结果未复制成 iOS 运行绑定，以免混用平台 checksum；iOS 必须在 Mac 重生成适配自身原生库的绑定并编译。
- 手机视觉验收与线上接口联调仍待新包/新服务发布后执行；没有删除截图中的任何生产设备记录。

## Android APK 交付（2026-10-14）

- 构建工作区：`D:\project\litter-mobile-desktop-connect`，分支 `mobile-desktop-connect`。
- 执行 `:app:assembleDebug --offline`，BUILD SUCCESSFUL（38s）；复用已核对过源码快照的最新 Android 原生库，没有重新同步子模块。
- APK：`D:\project\litter-mobile-desktop-connect\apps\android\app\build\outputs\apk\debug\necode-mobile-remove-desktop-debug-20261014.apk`。
- 包名 `com.aegean.necode.mobile`，debug，arm64-v8a，versionName 1.0.0 / versionCode 1；未调整版本号。
- 大小：186234423 bytes（177.61 MiB）。SHA256：`FE2B00C9F66E8AC895D01CF07DF609D08EA3431D6DEEF4E4C23B86D7B3CD078F`。
- APK v2 签名、`zipalign -c -P 16 4`、命名副本与 app-debug.apk 哈希一致性、native target → JNI → merged 与 stripped → APK ZIP entry 两段原生库哈希链检查通过。
- 构建日志：`D:\codex\.codex\tmp\mobile-auth-v2-check\remove-desktop-assemble.log`；验证记录：`D:\codex\.codex\tmp\mobile-auth-v2-check\remove-desktop-apk-verification.json`。
- 未自动安装，未执行真机移除联调，未提交/推送/部署。构建时桌面仓库已有本功能提交 `a77e41a24e`；未验证线上 Deployment 是否已包含该版本，实际移除仍要求服务端支持新 DELETE 接口。

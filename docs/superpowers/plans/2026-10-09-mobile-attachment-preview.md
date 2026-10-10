# 手机附件卡片与预览

目标：保留现有 Base64、小附件限制和 durable prompt 逻辑，补充发送前与历史会话的附件卡片。

1. 共享 Rust 消息展示投影补充附件类型、名称、大小及可预览内容；不改 UniFFI 函数签名，不新增上传协议。使用真实投影函数的聚焦测试验证可预览/损坏/仅电脑本地的附件。
2. Android：图片缩略图与大图缩放，文本限量预览，其他文件用系统应用打开；独立删除按钮。内容解码、缓存与打开异常可见，缓存仅放独立 cache 子目录。
3. iOS：相同卡片、独立删除、图片与文本预览，其他格式走系统 Quick Look。
4. Android 聚焦测试和 Kotlin 编译，Rust host check；Windows 无法编译验证 iOS。此次不构建 APK、不开启耗时 Android 原生库交叉编译、不提交、不推送。后续历史卡片能力生效需打包更新原生库。

文件边界：DesktopSessionsScreen.kt、新增 DesktopAttachmentPreview.kt、DesktopSessionsView.swift、desktop_connect.rs、Android FileProvider 配置，以及必要的解析/投影测试。

## 本轮验证

- Android 编译通过；附件预览 5 项、消息适配 4 项、滚动跟随 6 项、发送/停止 4 项，共 19 项聚焦测试通过。
- 共享投影函数从实际源码提取到轻量 Rust 测试 harness；先验证新增 2 项失败，再验证新 2 项与原工具分组回归 1 项共 3 项通过。未重复链接整套大型 host 测试库。
- `cargo check --lib --no-default-features --offline -j1` 通过；有既存 dead-code 警告。
- 原附件读取/压缩函数与改动前逐字比对相同；发送大小校验和 durable prompt 调用保留。
- FileProvider 仅暴露专用缓存目录、非 exported、仅在用户点击打开后授予读取权限。
- iOS 已同步源码，使用系统 Quick Look 预览和分享；Windows 无 Xcode，尚未编译/真机验收。
- 未构建 APK、未更新 Android/iOS 原生库；历史会话的新增附件投影字段需要下次打包时重编一次共享原生库。Android 发送前预览只依赖 Kotlin 改动。

## 用户确认打包后的构建结果（2026-10-09）

- 增量 ARM64 `android-dev` 原生构建成功，3m39s；新库经 ELF/附件预览 marker 校验后复制到 JNI。
- Android `assembleDebug --offline --max-workers=1` 成功，1m01s。
- 本轮重新执行聚焦测试命令，Gradle 判定 19 项 Android 测试结果缓存有效；轻量 Rust 3 项投影测试重新通过。
- APK：`apps/android/app/build/outputs/apk/debug/necode-mobile-attachments-20261009.apk`，186193102 bytes。
- APK SHA256：`EB92E8B2DD4ACED8DDA5AA474461E10ED8731D4E27906916F561E2D1E6C775B0`。
- v2 签名验证通过；包名 `com.aegean.necode.mobile`，NeCode 1.0.0，ARM64，minSDK26/target35。
- 新原生库 SHA256：`356C5C4C7E25E08BD71F3E3388894431612871D4DEB12EC67ACE0DE7F9131AAD`，build/JNI/merged 一致。
- stripped/APK 内 SO SHA256：`C04F2A73A7A5C4515E6F92C1A63B8A0595B23B4DD59C1397F51FE2D4BC23FB6E`，一致；包内附件预览 marker 和 FileProvider 已核验。
- 未自动安装；尚未进行本版真机视觉验收；iOS 未构建。

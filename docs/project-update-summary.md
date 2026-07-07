# 项目配置更新总结

## 更新日期
2026-07-07

## 更新内容

### 1. 修复配对JSON乱码问题 ✅

**问题**: 用户在iOS应用的配对JSON输入框中输入正常JSON后，显示为乱码。

**根本原因**: UTF-16 LE（小端序）编码的字节被错误地解释为UTF-16 BE（大端序）字符。

**解决方案**:
- 文件: `apps/ios/Sources/Litter/Views/AlleycatAddServerSheet.swift`
- 新增: `PairPayloadInput.fixMisinterpretedUTF16()` 方法
- 功能: 自动检测和修复UTF-16编码错误
- 测试: `apps/ios/Tests/LitterTests/PairPayloadInputTests.swift`（8个测试用例）

**效果**: 用户粘贴乱码JSON时会自动转换为正确格式，完全透明，无需用户干预。

### 2. Bundle ID 统一更新 ✅

将iOS的Bundle ID从 `com.sigkitten.litter` 更新为 `com.aegean.necode.mobile`，与Android保持一致。

**更新的文件**:

#### 配置文件
- `apps/ios/project.yml` - 主项目配置
  - Litter (iOS主应用)
  - LitterMac (Mac Catalyst)
  - LitterLiveActivity
  - LitterWatch
  - LitterWatchComplications

#### Entitlements（6个文件）
- `Sources/Litter/Litter.entitlements`
- `Sources/Litter/Litter-Catalyst.entitlements`
- `Sources/Litter/Litter-Catalyst-DeveloperID.entitlements`
- `Sources/LitterLiveActivity/LitterLiveActivity.entitlements`
- `Sources/LitterWatch/LitterWatch.entitlements`
- `Sources/LitterWatchComplications/LitterWatchComplications.entitlements`

#### Info.plist（3个文件）
- `Sources/Litter/Info.plist`
- `Sources/Litter/Info-Catalyst.plist`
- `Sources/LitterWatch/Info.plist`

#### Swift代码（12个文件）
- CarPlay/CarPlaySceneDelegate.swift
- CarPlay/CarPlayVoiceManager.swift
- Models/AppLifecycleController.swift
- Models/ChatGPTOAuth.swift
- Models/LLog.swift
- Models/LitterPalette.swift
- Models/OpenAIApiKeyStore.swift
- Models/VoiceSessionControl.swift
- Models/WatchCompanionBridge.swift
- LitterWatch/Models/WatchSnapshotStore.swift
- LitterWatchComplications/LitterComplicationEntry.swift
- LitterWatchComplications/LitterServerListPayload.swift
- LitterWatchComplications/LitterRunningTurnPayload.swift

#### App Group更新
从 `group.com.sigkitten.litter` 更新为 `group.com.aegean.necode.mobile`

### 3. 版本号统一更新 ✅

将iOS和Android的版本号统一更新为 **1.0.0**

**iOS**:
- MARKETING_VERSION: 1.0.0
- CURRENT_PROJECT_VERSION: 1

**Android**:
- versionName: 1.0.0
- versionCode: 1

## 当前配置状态

### iOS
```
Bundle ID: com.aegean.necode.mobile
Product Name: NeCode
Version: 1.0.0
Build: 1
Team ID: UH66Q8ZAYG
Code Sign Style: Automatic
App Group: group.com.aegean.necode.mobile
```

### Android
```
Application ID: com.aegean.necode.mobile
Version Name: 1.0.0
Version Code: 1
Min SDK: 26
Target SDK: 35
Namespace: com.sigkitten.litter.android
```

## 新增文件

1. `apps/ios/Tests/LitterTests/PairPayloadInputTests.swift` - UTF-16修复的单元测试
2. `docs/fix-utf16-garbled-json.md` - 乱码问题修复文档
3. `docs/ios-bundle-id-update.md` - Bundle ID更新和打包配置指南

## 验证步骤

### 验证Bundle ID
```bash
xcodebuild -project apps/ios/Litter.xcodeproj -scheme Litter -configuration Release -showBuildSettings | grep PRODUCT_BUNDLE_IDENTIFIER
# 输出: PRODUCT_BUNDLE_IDENTIFIER = com.aegean.necode.mobile
```

### 验证版本号
```bash
xcodebuild -project apps/ios/Litter.xcodeproj -scheme Litter -configuration Release -showBuildSettings | grep -E "MARKETING_VERSION|CURRENT_PROJECT_VERSION"
# 输出:
# CURRENT_PROJECT_VERSION = 1
# MARKETING_VERSION = 1.0.0
```

### 运行单元测试
```bash
make test  # 运行所有测试
```

## 下一步操作

### 打包Release Testing

要成功打包TestFlight，需要完成以下Apple Developer Portal配置：

1. **创建App ID**: `com.aegean.necode.mobile`
   - 启用Push Notifications
   - 启用App Groups (group.com.aegean.necode.mobile)
   - 启用CarPlay

2. **创建App Group**: `group.com.aegean.necode.mobile`

3. **获取Distribution证书**
   - 在Keychain Access中生成CSR
   - 在Apple Developer Portal创建Apple Distribution证书
   - 下载并安装

4. **创建Provisioning Profiles**
   - App Store Profile (用于主应用)
   - 为各个扩展创建对应的Profile

5. **在App Store Connect创建App记录**

6. **运行打包命令**
   ```bash
   make testflight
   ```

详细步骤参见: `docs/ios-bundle-id-update.md`

## 注意事项

1. ⚠️ **当前缺少Distribution证书** - 无法直接打包App Store/TestFlight版本
2. ✅ **开发证书正常** - 可以正常开发和真机调试
3. ✅ **所有Bundle ID已统一** - iOS和Android使用相同的应用标识
4. ✅ **版本号已重置** - 从1.0.0开始作为正式版本
5. ⚠️ **需要在Apple Developer Portal配置** - 完成上述配置后才能打包

## 相关命令

```bash
# 清理构建
make clean

# 重新生成Xcode项目
make xcgen

# iOS模拟器快速构建
make ios-sim-fast

# iOS真机快速构建（需要开发证书）
make ios-device-fast

# 打包TestFlight（需要Distribution证书）
make testflight

# 查看证书
security find-identity -v -p codesigning

# 查看Provisioning Profiles
ls ~/Library/MobileDevice/Provisioning\ Profiles/
```

## Git 提交建议

```bash
git add .
git commit -m "feat: 修复配对JSON乱码问题并统一Bundle ID和版本号

- 修复UTF-16编码乱码问题，添加自动检测和修复逻辑
- 统一iOS和Android的Bundle ID为com.aegean.necode.mobile
- 更新App Group为group.com.aegean.necode.mobile
- 统一版本号为1.0.0
- 新增单元测试和文档"
```

## 文档链接

- [UTF-16乱码修复详细说明](./fix-utf16-garbled-json.md)
- [iOS Bundle ID更新和打包配置](./ios-bundle-id-update.md)

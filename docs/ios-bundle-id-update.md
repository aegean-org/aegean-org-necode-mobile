# iOS Bundle ID 更新和打包配置指南

## 已完成的更新

### Bundle ID 统一更新
已将所有iOS相关的Bundle ID从 `com.sigkitten.litter` 更新为 `com.aegean.necode.mobile`，与Android保持一致。

### 更新的文件清单
1. **project.yml** - 主配置文件
   - Litter (iOS): `com.aegean.necode.mobile`
   - LitterMac (Catalyst): `com.aegean.necode.mobile`
   - App Group: `group.com.aegean.necode.mobile`

2. **Entitlements文件**
   - Litter.entitlements
   - Litter-Catalyst.entitlements
   - Litter-Catalyst-DeveloperID.entitlements
   - LitterLiveActivity.entitlements
   - LitterWatch.entitlements
   - LitterWatchComplications.entitlements

3. **Info.plist文件**
   - Sources/Litter/Info.plist
   - Sources/Litter/Info-Catalyst.plist
   - Sources/LitterWatch/Info.plist

4. **Swift代码**
   - 12个文件中的硬编码Bundle ID引用已更新

5. **Xcode项目**
   - 已通过 `make xcgen` 重新生成

## 当前配置状态

- **Bundle ID**: `com.aegean.necode.mobile`
- **App Name**: NeCode
- **Version**: 1.5.0
- **Build**: 1
- **Team ID**: UH66Q8ZAYG
- **Code Sign Style**: Automatic
- **App Group**: `group.com.aegean.necode.mobile`

## 打包Release Testing (TestFlight) 的前置要求

### 1. Apple Developer Portal 配置

需要在 https://developer.apple.com 完成以下配置：

#### 1.1 创建App ID
1. 登录 Apple Developer Portal
2. 进入 Certificates, Identifiers & Profiles
3. 创建新的 Identifier (App ID)：
   - Bundle ID: `com.aegean.necode.mobile`
   - 启用的Capabilities：
     - Push Notifications (APS Environment)
     - App Groups (group.com.aegean.necode.mobile)
     - CarPlay (Voice-based Conversation)

#### 1.2 创建App Group
1. 在 Identifiers 中创建 App Group
2. Identifier: `group.com.aegean.necode.mobile`

#### 1.3 获取Distribution证书
你当前只有开发证书，需要创建Distribution证书：

1. 打开 Keychain Access
2. Keychain Access → Certificate Assistant → Request a Certificate from a Certificate Authority
3. 输入邮箱，选择 "Saved to disk"
4. 在 Apple Developer Portal → Certificates → Create
5. 选择 "Apple Distribution"
6. 上传刚才生成的 CSR 文件
7. 下载证书并双击安装到 Keychain

#### 1.4 创建Provisioning Profile
1. 在 Profiles 中创建新的 Profile
2. 选择 "App Store" (用于TestFlight和App Store)
3. 选择刚创建的 App ID: `com.aegean.necode.mobile`
4. 选择刚创建的 Distribution 证书
5. 下载并双击安装

#### 1.5 针对扩展创建Provisioning Profiles
同样需要为以下targets创建Profile：
- `com.aegean.necode.mobile.LiveActivity` (Live Activity Extension)
- `com.aegean.necode.mobile.watchkitapp` (Watch App)
- `com.aegean.necode.mobile.watchkitapp.complications` (Watch Complications)

### 2. App Store Connect 配置

1. 登录 https://appstoreconnect.apple.com
2. 创建新的 App：
   - Bundle ID: 选择 `com.aegean.necode.mobile`
   - App Name: NeCode Mobile
   - Primary Language: 简体中文或英语
   - SKU: 可以使用 `necode-mobile-ios`

### 3. 本地打包命令

配置完成后，可以使用以下命令打包：

```bash
# 方式1: 使用Makefile (推荐)
cd /Volumes/SanDisk/code/aegean-org-necode-mobile
make testflight

# 方式2: 手动Archive
cd apps/ios
xcodebuild archive \
  -project Litter.xcodeproj \
  -scheme Litter \
  -configuration Release \
  -archivePath build/NeCode.xcarchive

# 方式3: 在Xcode中操作
# Product → Archive
# 然后在Organizer中选择 "Distribute App" → "App Store Connect"
```

## 当前的证书状态

检测到的证书：
```
1) Apple Development: Wu Shuai (D3964C8F46)
2) Developer ID Application: chengguang shen (8GH4892P7F)
3) Apple Development: ne.ios@inoteexpress.com (K3VMK33FZ6)
```

⚠️ **缺少 Apple Distribution 证书** - 这是打包App Store/TestFlight必需的。

## 快速测试方案

如果只是想在设备上测试，可以使用Debug配置：

```bash
# Debug构建（使用开发证书）
cd /Volumes/SanDisk/code/aegean-org-necode-mobile
make ios-device-fast

# 然后在Xcode中运行到真机
```

## 注意事项

1. **Team ID 验证**: 当前使用的 Team ID `UH66Q8ZAYG` 需要与Apple Developer Portal中的Team ID一致
2. **自动签名**: 项目配置为自动签名，Xcode会自动选择合适的证书和Profile
3. **首次打包**: 首次打包可能需要在Xcode中手动选择Team和修复签名问题
4. **版本号同步**: iOS和Android的版本号已统一为 1.5.0 (Build 11 for Android, Build 1 for iOS)

## 下一步

1. 完成上述Apple Developer Portal配置
2. 获取Distribution证书
3. 创建所有必要的Provisioning Profiles
4. 在App Store Connect中创建App记录
5. 运行 `make testflight` 打包上传

## 相关命令

```bash
# 查看当前证书
security find-identity -v -p codesigning

# 查看Provisioning Profiles
ls ~/Library/MobileDevice/Provisioning\ Profiles/

# 清理构建
make clean

# 重新生成项目
make xcgen
```

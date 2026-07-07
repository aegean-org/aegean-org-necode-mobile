# Apple Developer Portal 配置清单

## 需要创建的App IDs

访问：https://developer.apple.com → Certificates, Identifiers & Profiles → Identifiers

### 1. 主应用 App ID
- **Bundle ID**: `com.aegean.necode.mobile`
- **Description**: NeCode Mobile - Main App
- **Capabilities** (启用以下功能):
  - ✅ Push Notifications (APS Environment: Production)
  - ✅ App Groups → 选择 `group.com.aegean.necode.mobile`
  - ✅ Associated Domains (如果需要Universal Links)
  - ✅ Background Modes (如果需要后台运行)

### 2. Live Activity 扩展 App ID
- **Bundle ID**: `com.aegean.necode.mobile.liveactivity`
- **Description**: NeCode Mobile - Live Activity Extension
- **Capabilities**:
  - ✅ App Groups → 选择 `group.com.aegean.necode.mobile`

### 3. Watch App ID
- **Bundle ID**: `com.aegean.necode.mobile.watchkitapp`
- **Description**: NeCode Mobile - Watch App
- **Platform**: watchOS
- **Capabilities**:
  - ✅ Push Notifications (APS Environment: Production)
  - ✅ App Groups → 选择 `group.com.aegean.necode.mobile`

### 4. Watch Complications App ID
- **Bundle ID**: `com.aegean.necode.mobile.watchkitapp.complications`
- **Description**: NeCode Mobile - Watch Complications
- **Platform**: watchOS
- **Capabilities**:
  - ✅ App Groups → 选择 `group.com.aegean.necode.mobile`

## App Group 配置

### 创建App Group (如果还没有创建)
1. 进入 Identifiers → App Groups
2. 点击 "+" 创建新的App Group
3. **Identifier**: `group.com.aegean.necode.mobile`
4. **Description**: NeCode Mobile Shared Data

## Provisioning Profiles 配置

在获得Distribution证书后，需要创建以下Provisioning Profiles：

### 1. 主应用 Profile
- **Type**: App Store
- **App ID**: com.aegean.necode.mobile
- **Certificate**: 选择你的Apple Distribution证书
- **Profile Name**: NeCode Mobile AppStore

### 2. Live Activity 扩展 Profile
- **Type**: App Store
- **App ID**: com.aegean.necode.mobile.liveactivity
- **Certificate**: 选择你的Apple Distribution证书
- **Profile Name**: NeCode Mobile LiveActivity AppStore

### 3. Watch App Profile
- **Type**: App Store
- **App ID**: com.aegean.necode.mobile.watchkitapp
- **Certificate**: 选择你的Apple Distribution证书
- **Profile Name**: NeCode Mobile Watch AppStore

### 4. Watch Complications Profile
- **Type**: App Store
- **App ID**: com.aegean.necode.mobile.watchkitapp.complications
- **Certificate**: 选择你的Apple Distribution证书
- **Profile Name**: NeCode Mobile Watch Complications AppStore

## Distribution 证书

如果还没有创建Distribution证书：

1. 在Mac上打开 **Keychain Access**
2. 菜单：Keychain Access → Certificate Assistant → Request a Certificate from a Certificate Authority
3. 填写信息：
   - User Email Address: 你的邮箱
   - Common Name: 你的名字或公司名
   - CA Email Address: 留空
   - Request is: **Saved to disk**
4. 点击Continue，保存CSR文件

5. 在Apple Developer Portal：
   - Certificates → 点击 "+"
   - 选择 **Apple Distribution**
   - 上传刚才保存的CSR文件
   - 下载生成的证书
   - 双击安装到Keychain

## 验证步骤

### 验证证书已安装
```bash
security find-identity -v -p codesigning | grep "Apple Distribution"
```
应该能看到类似：
```
X) XXXXXXXXXX "Apple Distribution: Your Name (TEAM_ID)"
```

### 验证Provisioning Profiles已下载
```bash
ls ~/Library/MobileDevice/Provisioning\ Profiles/
```

### 清理旧的Provisioning Profiles（可选）
```bash
rm ~/Library/MobileDevice/Provisioning\ Profiles/*.mobileprovision
```
然后在Xcode中重新下载：
Xcode → Settings → Accounts → 选择你的账号 → Download Manual Profiles

## 完成后测试Archive

```bash
cd /Volumes/SanDisk/code/aegean-org-necode-mobile/apps/ios
xcodebuild archive \
  -project Litter.xcodeproj \
  -scheme Litter \
  -configuration Release \
  -archivePath build/NeCode.xcarchive
```

如果成功，会看到：
```
** ARCHIVE SUCCEEDED **
```

## 在App Store Connect中配置Bundle IDs

主应用已经注册，还需要确保Watch App也关联：

1. 登录 https://appstoreconnect.apple.com
2. 进入你的App（NeCode Mobile）
3. 进入 App 信息
4. 查看是否有 watchOS App选项
   - 如果有，确保Bundle ID正确

## 当前Bundle ID层级

```
com.aegean.necode.mobile (主应用)
├── .liveactivity (Live Activity)
└── .watchkitapp (Watch App)
    └── .complications (Watch表盘组件)
```

⚠️ **重要**: 所有扩展的Bundle ID必须以主应用的Bundle ID为前缀！

## 遇到问题？

### 问题1: "No signing certificate found"
**解决**: 确保已安装Apple Distribution证书

### 问题2: "No provisioning profile found"
**解决**: 在Xcode中下载Provisioning Profiles，或在Portal中创建

### 问题3: "Bundle identifier mismatch"
**解决**: 确保project.yml中的Bundle ID与Portal中创建的一致

### 问题4: "Embedded binary's bundle identifier is not prefixed"
**解决**: 已修复！所有扩展Bundle ID现在都正确地以主应用ID为前缀

## 快速命令参考

```bash
# 重新生成Xcode项目
make xcgen

# 清理构建
make clean

# Archive（需要先配置好证书和Profiles）
make testflight

# 查看当前Bundle IDs
xcodebuild -project apps/ios/Litter.xcodeproj -scheme Litter -showBuildSettings | grep PRODUCT_BUNDLE_IDENTIFIER
```

## 检查清单

完成Portal配置前：
- [ ] 已创建4个App IDs（主应用+3个扩展）
- [ ] 已创建App Group
- [ ] 已获取Distribution证书
- [ ] 已创建4个Provisioning Profiles
- [ ] 证书和Profiles已下载安装
- [ ] 在Xcode中验证签名配置正确

完成后即可执行Archive！

# Apple Developer Portal 配置清单（无Watch版本）

## 当前项目配置

项目现在只支持 iPhone 和 iPad，已移除 Apple Watch 支持。

## 需要创建的App IDs

访问：https://developer.apple.com → Certificates, Identifiers & Profiles → Identifiers

### 1. 主应用 App ID ⭐️
- **Bundle ID**: `com.aegean.necode.mobile`
- **Description**: NeCode Mobile - Main App
- **Platform**: iOS
- **Capabilities** (启用以下功能):
  - ✅ Push Notifications
    - APS Environment: **Production**
  - ✅ App Groups
    - 点击配置，选择 `group.com.aegean.necode.mobile`
  - ✅ Associated Domains (如果需要Universal Links)
  - ✅ Background Modes (后台运行)

### 2. Live Activity 扩展 App ID
- **Bundle ID**: `com.aegean.necode.mobile.liveactivity`
- **Description**: NeCode Mobile - Live Activity Extension
- **Platform**: iOS
- **Capabilities**:
  - ✅ App Groups → 选择 `group.com.aegean.necode.mobile`

## App Group 配置

### 创建App Group
1. 进入 Identifiers → 左上角点击 "+" → 选择 **App Groups**
2. **Identifier**: `group.com.aegean.necode.mobile`
3. **Description**: NeCode Mobile Shared Data
4. 点击 **Continue** → **Register**

## Distribution 证书

### 检查是否已有证书
```bash
security find-identity -v -p codesigning | grep "Apple Distribution"
```

### 如果没有，创建新证书

#### 步骤1：生成证书签名请求(CSR)
1. 在Mac上打开 **Keychain Access** (钥匙串访问)
2. 菜单：**Keychain Access → Certificate Assistant → Request a Certificate from a Certificate Authority**
3. 填写信息：
   - **User Email Address**: 你的邮箱地址
   - **Common Name**: 你的名字或 "NeCode Distribution"
   - **CA Email Address**: 留空
   - **Request is**: 选择 **Saved to disk**
4. 点击 **Continue**，保存CSR文件到桌面

#### 步骤2：在Apple Developer Portal创建证书
1. 访问 https://developer.apple.com
2. 进入 **Certificates, Identifiers & Profiles**
3. 点击左侧 **Certificates**
4. 点击右上角 **"+"** 按钮
5. 选择 **Apple Distribution**
6. 点击 **Continue**
7. **上传刚才保存的CSR文件**
8. 点击 **Continue**
9. **下载生成的证书**（.cer文件）
10. **双击证书文件**安装到Keychain

#### 步骤3：验证证书已安装
```bash
security find-identity -v -p codesigning
```
应该能看到类似：
```
1) XXXXXXXX "Apple Distribution: Your Name (TEAM_ID)"
```

## Provisioning Profiles 配置

创建Distribution证书后，需要创建以下Provisioning Profiles：

### 1. 主应用 Profile ⭐️
1. 进入 **Profiles** → 点击 **"+"**
2. 选择 **App Store** (用于TestFlight和App Store发布)
3. 点击 **Continue**
4. **App ID**: 选择 `com.aegean.necode.mobile`
5. 点击 **Continue**
6. **Certificate**: 选择刚才创建的 Apple Distribution 证书
7. 点击 **Continue**
8. **Profile Name**: `NeCode Mobile AppStore`
9. 点击 **Generate**
10. **下载Profile** (.mobileprovision文件)
11. **双击Profile文件**安装

### 2. Live Activity 扩展 Profile
重复上述步骤，但是：
- **App ID**: 选择 `com.aegean.necode.mobile.liveactivity`
- **Profile Name**: `NeCode Mobile LiveActivity AppStore`

## 验证配置

### 1. 验证证书
```bash
security find-identity -v -p codesigning | grep "Apple Distribution"
```

### 2. 验证Provisioning Profiles
```bash
ls -la ~/Library/MobileDevice/Provisioning\ Profiles/
```

### 3. 在Xcode中刷新Profiles
1. 打开 Xcode
2. 菜单：**Xcode → Settings (Preferences)**
3. 选择 **Accounts** 标签
4. 选择你的Apple ID账号
5. 点击右侧的 **Download Manual Profiles** 按钮

## 支持的设备

当前项目配置：
- ✅ **iPhone** (iOS 18.0+)
- ✅ **iPad** (iOS 18.0+)
- ✅ **Mac Catalyst** (macOS 14.0+)

## 当前Bundle ID结构

```
com.aegean.necode.mobile               (主应用 - iPhone/iPad)
└── .liveactivity                      (Live Activity扩展)
```

## 完成Archive

配置完成后，执行以下命令：

### 方法1：使用Xcode (推荐)
1. 打开项目：
   ```bash
   open /Volumes/SanDisk/code/aegean-org-necode-mobile/apps/ios/Litter.xcodeproj
   ```
2. 选择 Scheme: **Litter**
3. 选择目标设备: **Any iOS Device**
4. 菜单：**Product → Archive**
5. 等待构建完成
6. 在 Organizer 中点击 **Distribute App**
7. 选择 **App Store Connect**
8. 选择 **Upload**
9. 按提示完成上传

### 方法2：使用命令行
```bash
cd /Volumes/SanDisk/code/aegean-org-necode-mobile
make testflight
```

## 常见问题

### Q1: "No signing certificate iOS Distribution found"
**解决方案**:
- 确保已创建并安装Apple Distribution证书
- 在Xcode Settings → Accounts中下载Profiles

### Q2: "Provisioning profile doesn't include the application-groups entitlement"
**解决方案**:
- 确保App Group已创建：`group.com.aegean.necode.mobile`
- 确保App ID已启用App Groups capability
- 重新生成Provisioning Profile

### Q3: "Bundle identifier mismatch"
**解决方案**:
- 确保Portal中的Bundle ID与代码中一致
- 主应用：`com.aegean.necode.mobile`
- Live Activity：`com.aegean.necode.mobile.liveactivity`

### Q4: Archive成功但无法上传
**解决方案**:
- 检查版本号是否符合规范（当前：1.0.0）
- 检查Build号是否唯一（当前：1）
- 确保网络连接正常

## 配置检查清单

在执行Archive前，确认以下项目：

- [ ] 已创建主应用App ID (`com.aegean.necode.mobile`)
- [ ] 已创建Live Activity App ID (`com.aegean.necode.mobile.liveactivity`)
- [ ] 已创建App Group (`group.com.aegean.necode.mobile`)
- [ ] 已获取Apple Distribution证书并安装
- [ ] 已创建主应用Provisioning Profile并安装
- [ ] 已创建Live Activity Provisioning Profile并安装
- [ ] 在Xcode中已下载所有Profiles
- [ ] 在App Store Connect中已注册App

## App Store Connect配置

确保已在 https://appstoreconnect.apple.com 完成：

- [ ] 创建了App（SKU: com.aegean.necode.mobile）
- [ ] 填写了App基本信息
- [ ] 上传了截图（iPhone 6.7"和6.5"必需）
- [ ] 填写了App描述和关键词
- [ ] 设置了隐私政策URL
- [ ] 完成了年龄分级
- [ ] 填写了审核信息

## 快速命令

```bash
# 查看当前targets
xcodebuild -project apps/ios/Litter.xcodeproj -list

# 查看Bundle IDs
xcodebuild -project apps/ios/Litter.xcodeproj -scheme Litter -showBuildSettings | grep PRODUCT_BUNDLE_IDENTIFIER

# 清理构建
make clean

# 重新生成项目
make xcgen

# Archive并上传
make testflight
```

## 需要的截图尺寸

上传到App Store Connect需要以下截图：

### 必需（至少两种）
1. **6.7英寸显示屏** (iPhone 15 Pro Max)
   - 分辨率：1290 x 2796 像素
   - 数量：3-10张

2. **6.5英寸显示屏** (iPhone 14 Plus)
   - 分辨率：1284 x 2778 像素
   - 数量：3-10张

### 可选
3. **12.9英寸iPad Pro**
   - 分辨率：2048 x 2732 像素
   - 数量：3-10张

### 生成截图
```bash
# 启动模拟器
make ios-sim-fast

# 在模拟器中按 Cmd+S 保存截图到桌面
```

---

完成以上配置后，你就可以成功Archive并上传到App Store Connect了！

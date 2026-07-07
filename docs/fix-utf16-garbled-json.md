# 配对JSON乱码问题修复报告

## 问题描述

用户在iOS应用的"添加设备"界面中，在配对JSON输入框中输入或粘贴正常的JSON后，文本显示为乱码。

### 用户报告的案例

**输入的正常JSON:**
```json
{"v":1,"node_id":"e1cae31de248cb5373090ddd780e53e9cb45f1132bb904afe65df2f42e9a0682","token":"6c0cb97c3db051c98c4853a546bd548f488eddc80687ed657877ad371bbee9ef","host_name":"废物牛","relay":"https://relay.inoteexpress.com"}
```

**显示的乱码:**
```
笀∀瘀∀㨀㄀Ⰰ∀渀漀搀攀开椀搀∀㨀∀攀㄀挀愀攀㌀㄀搀攀㈀㐀㠀挀戀㔀㌀㜀㌀　㤀　搀搀搀㜀㠀　攀㔀㌀攀㤀挀戀㐀㔀昀㄀㄀㌀㈀戀戀㤀　㐀愀昀攀㘀㔀搀昀㈀昀㐀㈀攀㤀愀　㘀㠀㈀∀Ⰰ∀琀漀欀攀渀∀㨀∀㘀挀　挀戀㤀㜀挀㌀搀戀　㔀㄀挀㤀㠀挀㐀㠀㔀㌀愀㔀㐀㘀戀搀㔀㐀㠀昀㐀㠀㠀攀搀搀挀㠀　㘀㠀㜀攀搀㘀㔀㜀㠀㜀㜀愀搀㌀㜀㄀戀戀攀攀㤀攀昀∀Ⰰ∀栀漀猀琀开渀愀洀攀∀㨀∀齞楲孲∀Ⰰ∀爀攀氀愀礀∀㨀∀栀琀琀瀀猀㨀⼀⼀爀攀氀愀礀⸀椀渀漀琀攀攀砀瀀爀攀猀猀⸀挀漀洀∀紀
```

## 根本原因

这是一个**字节序错误**问题：UTF-16 LE（小端序）编码的字节被错误地解释为UTF-16 BE（大端序）字符。

### 技术细节

1. **UTF-16 LE编码**: ASCII字符在UTF-16 LE中存储为 `[字符字节, 0x00]`
   - 例如: `{` = 0x7B → UTF-16 LE = `[0x7B, 0x00]`

2. **错误解释**: 当这两个字节被当作UTF-16 BE字符读取时：
   - `[0x7B, 0x00]` → U+7B00 → 显示为汉字 `笀`

3. **乱码模式分析**:
   ```
   原始字符: '{'  '"'  'v'  '"'  ':'  '1'
   UTF-16LE: 7B00 2200 7600 2200 3A00 3100
   误读为:   笀   ∀   瘀   ∀   㨀   ㄀
   ```

### 可能的来源

1. 用户从某个应用复制JSON时，该应用以错误的编码格式写入剪贴板
2. 跨设备或跨应用粘贴时发生编码转换错误
3. 某些第三方输入法或输入工具引入的编码问题

## 解决方案

在 `PairPayloadInput.normalized()` 方法中添加了自动检测和修复逻辑。

### 实现位置
- 文件: `apps/ios/Sources/Litter/Views/AlleycatAddServerSheet.swift`
- 方法: `PairPayloadInput.fixMisinterpretedUTF16()`

### 修复逻辑

1. **检测**: 检查文本首字符是否符合UTF-16误读的特征
   - 低字节 = 0x00
   - 高字节在ASCII范围内 (0x20-0x7E)

2. **修复**: 如果检测到问题，执行以下步骤：
   - 将每个字符的Unicode码点拆分为两个字节
   - 将这些字节作为UTF-16 LE编码重新解码
   - 返回正确的字符串

3. **保护**: 如果文本是正常的，直接返回原文本，不做任何修改

### 代码片段

```swift
private static func fixMisinterpretedUTF16(_ text: String) -> String {
    guard let firstChar = text.unicodeScalars.first else { return text }

    let codePoint = firstChar.value
    let highByte = (codePoint >> 8) & 0xFF
    let lowByte = codePoint & 0xFF

    // 检测是否是误读的UTF-16 LE
    guard lowByte == 0x00, (0x20...0x7E).contains(highByte) else {
        return text
    }

    // 重建原始字节并重新解码
    var utf16LEBytes = Data()
    for scalar in text.unicodeScalars {
        let cp = scalar.value
        let high = UInt8((cp >> 8) & 0xFF)
        let low = UInt8(cp & 0xFF)
        utf16LEBytes.append(high)
        utf16LEBytes.append(low)
    }

    if let fixed = String(data: utf16LEBytes, encoding: .utf16LittleEndian) {
        return fixed
    }

    return text
}
```

## 测试验证

### 单元测试
创建了完整的单元测试套件：`apps/ios/Tests/LitterTests/PairPayloadInputTests.swift`

测试覆盖：
- ✅ 正常JSON输入保持不变
- ✅ UTF-16乱码自动修复
- ✅ 用户报告的实际乱码完全修复
- ✅ BOM移除功能
- ✅ Markdown代码块处理
- ✅ JSON提取功能
- ✅ 空白字符处理
- ✅ 组合场景（多种规范化步骤）

### 验证结果

使用用户报告的实际乱码进行测试：

```
输入乱码: 笀∀瘀∀㨀㄀Ⰰ∀渀漀搀攀开椀搀∀...
修复后: {"v":1,"node_id":"e1cae31de248cb5373090ddd780e53e9cb45f1132bb904afe65df2f42e9a0682",...}

✅ 修复成功！乱码已完全恢复为原始JSON
```

## 影响范围

### 修改的文件
1. `apps/ios/Sources/Litter/Views/AlleycatAddServerSheet.swift` - 添加修复逻辑
2. `apps/ios/Tests/LitterTests/PairPayloadInputTests.swift` - 新增单元测试

### 影响的功能
- iOS应用的配对JSON输入处理
- 从剪贴板粘贴配对信息
- QR码扫描后的文本处理

### 向后兼容性
- ✅ 完全向后兼容
- ✅ 不影响正常输入
- ✅ 仅在检测到编码问题时才进行修复

## 部署建议

1. **立即部署**: 这是一个用户体验问题，建议尽快部署
2. **测试验证**: 在TestFlight版本中验证修复效果
3. **用户反馈**: 关注用户是否还遇到类似的编码问题

## 相关链接

- 问题文件: `apps/ios/Sources/Litter/Views/AlleycatAddServerSheet.swift:14-68`
- 测试文件: `apps/ios/Tests/LitterTests/PairPayloadInputTests.swift`
- Git提交: (待提交)

## 总结

这个问题是由UTF-16编码字节序错误引起的，通过在输入规范化流程中添加自动检测和修复逻辑，可以完全解决用户遇到的乱码问题，同时不影响正常的JSON输入。修复方案已通过完整的单元测试验证。

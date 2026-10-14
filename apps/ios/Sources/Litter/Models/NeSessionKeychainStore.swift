import Foundation
import Security

/// All protocol/state transitions live in Rust. This adapter only atomically stores an opaque blob.
final class NeSessionKeychainStore: NeSessionStore, @unchecked Sendable {
    private static let lock = NSLock()
    private static var query: [String: Any] { [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "com.aegean.necode.mobile.desktop-connect",
        kSecAttrAccount as String: "auth-v2",
    ] }

    func readSession() throws -> String? {
        Self.lock.lock(); defer { Self.lock.unlock() }
        return try readUnlocked()
    }

    private func readUnlocked() throws -> String? {
        var item: CFTypeRef?
        let status = SecItemCopyMatching(Self.query.merging([
            kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne,
        ]) { _, value in value } as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data,
              let value = String(data: data, encoding: .utf8) else {
            throw ClientError.Rpc("无法读取安全登录凭据（\(status)），请解锁设备后重试")
        }
        return value
    }

    func replaceSession(expected: String?, value: String?) throws -> Bool {
        Self.lock.lock(); defer { Self.lock.unlock() }
        guard try readUnlocked() == expected else { return false }
        let status: OSStatus
        if let value {
            let data = Data(value.utf8)
            let added = SecItemAdd(Self.query.merging([
                kSecValueData as String: data,
                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ]) { _, value in value } as CFDictionary, nil)
            status = added == errSecDuplicateItem
                ? SecItemUpdate(Self.query as CFDictionary, [kSecValueData: data] as CFDictionary) : added
        } else { status = SecItemDelete(Self.query as CFDictionary) }
        guard status == errSecSuccess || (value == nil && status == errSecItemNotFound) else {
            throw ClientError.Rpc("无法保存安全登录凭据（\(status)），请检查设备后重试")
        }
        return true
    }
}

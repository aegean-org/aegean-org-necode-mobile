import Foundation
import Security

enum AlleycatCredentialStoreError: LocalizedError {
    case encodingFailed
    case decodingFailed
    case keychain(OSStatus)

    var errorDescription: String? {
        switch self {
        case .encodingFailed:
            return "Failed to encode Alleycat token"
        case .decodingFailed:
            return "Failed to decode saved Alleycat token"
        case .keychain(let status):
            return "Keychain error (\(status))"
        }
    }
}

final class AlleycatCredentialStore {
    static let shared = AlleycatCredentialStore()

    private let service = "com.alleycat.token"

    private init() {}

    func loadToken(nodeId: String) throws -> String? {
        let query = baseQuery(nodeId: nodeId).merging([
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]) { _, new in new }

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess:
            guard let data = item as? Data else { throw AlleycatCredentialStoreError.decodingFailed }
            guard let token = String(data: data, encoding: .utf8), !token.isEmpty else {
                throw AlleycatCredentialStoreError.decodingFailed
            }
            return token
        case errSecItemNotFound:
            return nil
        default:
            throw AlleycatCredentialStoreError.keychain(status)
        }
    }

    func saveToken(_ token: String, nodeId: String) throws {
        guard let data = token.data(using: .utf8) else {
            throw AlleycatCredentialStoreError.encodingFailed
        }

        let query = baseQuery(nodeId: nodeId)
        let attributes = query.merging([
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: data
        ]) { _, new in new }

        let status = SecItemAdd(attributes as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let updates: [String: Any] = [
                kSecValueData as String: data,
                kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            ]
            let updateStatus = SecItemUpdate(query as CFDictionary, updates as CFDictionary)
            guard updateStatus == errSecSuccess else {
                throw AlleycatCredentialStoreError.keychain(updateStatus)
            }
            return
        }

        guard status == errSecSuccess else {
            throw AlleycatCredentialStoreError.keychain(status)
        }
    }

    func deleteToken(nodeId: String) throws {
        let status = SecItemDelete(baseQuery(nodeId: nodeId) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw AlleycatCredentialStoreError.keychain(status)
        }
    }

    private func baseQuery(nodeId: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: normalizedNodeId(nodeId)
        ]
    }

    private func normalizedNodeId(_ nodeId: String) -> String {
        nodeId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
}

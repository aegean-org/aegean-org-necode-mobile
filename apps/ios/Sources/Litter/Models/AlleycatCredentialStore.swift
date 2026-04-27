import Foundation
import Security

/// Encoded form of `AppAlleycatParams` for Keychain storage.
///
/// Note: tokens and certificate fingerprints are per-launch by design — this
/// cache exists so a saved alleycat server remembers `(host, udpPort)` and can
/// re-prompt the user for a fresh QR after a relay restart, not so it can
/// reconnect silently.
struct SavedAlleycatParams: Codable, Equatable {
    let protocolVersion: UInt32
    let udpPort: UInt16
    let certFingerprint: String
    let token: String
    /// Optional for back-compat with records saved before host candidates
    /// were added to the QR.
    let hostCandidates: [String]?

    init(_ params: AppAlleycatParams) {
        self.protocolVersion = params.protocolVersion
        self.udpPort = params.udpPort
        self.certFingerprint = params.certFingerprint
        self.token = params.token
        self.hostCandidates = params.hostCandidates
    }

    func toParams() -> AppAlleycatParams {
        AppAlleycatParams(
            protocolVersion: protocolVersion,
            udpPort: udpPort,
            certFingerprint: certFingerprint,
            token: token,
            hostCandidates: hostCandidates ?? []
        )
    }
}

enum AlleycatCredentialStoreError: LocalizedError {
    case encodingFailed
    case decodingFailed
    case keychain(OSStatus)

    var errorDescription: String? {
        switch self {
        case .encodingFailed:
            return "Failed to encode alleycat params"
        case .decodingFailed:
            return "Failed to decode saved alleycat params"
        case .keychain(let status):
            return "Keychain error (\(status))"
        }
    }
}

final class AlleycatCredentialStore {
    static let shared = AlleycatCredentialStore()

    private let service = "com.litter.alleycat.params"

    private init() {}

    func load(host: String, udpPort: UInt16) throws -> SavedAlleycatParams? {
        let query = baseQuery(host: host, udpPort: udpPort).merging([
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]) { _, new in new }

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        switch status {
        case errSecSuccess:
            guard let data = item as? Data else { throw AlleycatCredentialStoreError.decodingFailed }
            guard let decoded = try? JSONDecoder().decode(SavedAlleycatParams.self, from: data) else {
                throw AlleycatCredentialStoreError.decodingFailed
            }
            return decoded
        case errSecItemNotFound:
            return nil
        default:
            throw AlleycatCredentialStoreError.keychain(status)
        }
    }

    func save(_ params: SavedAlleycatParams, host: String) throws {
        guard let data = try? JSONEncoder().encode(params) else {
            throw AlleycatCredentialStoreError.encodingFailed
        }

        let account = serverAccount(host: host, udpPort: params.udpPort)
        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: data
        ]

        let status = SecItemAdd(attributes as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: account
            ]
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

    func delete(host: String, udpPort: UInt16) throws {
        let status = SecItemDelete(baseQuery(host: host, udpPort: udpPort) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw AlleycatCredentialStoreError.keychain(status)
        }
    }

    private func baseQuery(host: String, udpPort: UInt16) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: serverAccount(host: host, udpPort: udpPort)
        ]
    }

    private func serverAccount(host: String, udpPort: UInt16) -> String {
        "\(normalizedHost(host).lowercased()):\(udpPort)"
    }

    private func normalizedHost(_ host: String) -> String {
        var normalized = host
            .trimmingCharacters(in: CharacterSet(charactersIn: "[]"))
            .replacingOccurrences(of: "%25", with: "%")
        if !normalized.contains(":"), let scopeIndex = normalized.firstIndex(of: "%") {
            normalized = String(normalized[..<scopeIndex])
        }
        return normalized
    }
}

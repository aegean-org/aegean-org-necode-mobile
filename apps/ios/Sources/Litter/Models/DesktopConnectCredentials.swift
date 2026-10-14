import Foundation
import Security

struct DesktopConnectCredentials: Codable {
    let accountId: String
    let sessionId: String
    let mobileId: String?
    let mobileSecret: String?

    init(accountId: String, sessionId: String, mobileId: String?, mobileSecret: String?) {
        self.accountId = accountId; self.sessionId = sessionId
        self.mobileId = mobileId; self.mobileSecret = mobileSecret
    }

    private enum CodingKeys: String, CodingKey { case accountId, sessionId, mobileId, mobileSecret }
    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        accountId = try values.decode(String.self, forKey: .accountId)
        sessionId = try values.decodeIfPresent(String.self, forKey: .sessionId) ?? ""
        mobileId = try values.decodeIfPresent(String.self, forKey: .mobileId)
        mobileSecret = try values.decodeIfPresent(String.self, forKey: .mobileSecret)
    }
}

enum DesktopConnectCredentialStore {
    private static let service = "com.aegean.necode.mobile.desktop-connect"

    static func load() throws -> DesktopConnectCredentials? {
        var item: CFTypeRef?
        let status = SecItemCopyMatching([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: "identity",
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne,
        ] as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        return try JSONDecoder().decode(DesktopConnectCredentials.self, from: data)
    }

    static func save(_ credentials: DesktopConnectCredentials) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "identity",
        ]
        let data = try JSONEncoder().encode(credentials)
        let status = SecItemAdd(query.merging([
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: data,
        ]) { _, value in value } as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let updated = SecItemUpdate(query as CFDictionary, [kSecValueData: data] as CFDictionary)
            guard updated == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(updated)) }
            return
        }
        guard status == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(status)) }
    }

    static func preferredDesktopID(for accountId: String) -> String? {
        UserDefaults.standard.string(forKey: "\(service).preferred.\(accountId)")
    }

    static func setPreferredDesktopID(_ id: String?, for accountId: String) {
        UserDefaults.standard.set(id, forKey: "\(service).preferred.\(accountId)")
    }

    static func pendingPrompt(accountId: String, desktopId: String, sessionId: String) throws -> String? {
        var item: CFTypeRef?
        let status = SecItemCopyMatching([
            kSecClass: kSecClassGenericPassword, kSecAttrService: service,
            kSecAttrAccount: "prompt.\(accountId).\(desktopId).\(sessionId)",
            kSecReturnData: true, kSecMatchLimit: kSecMatchLimitOne,
        ] as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data, let journal = String(data: data, encoding: .utf8) else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
        return journal
    }

    static func savePendingPrompt(accountId: String, desktopId: String, sessionId: String, journal: String?) throws {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service, kSecAttrAccount as String: "prompt.\(accountId).\(desktopId).\(sessionId)"]
        guard let journal else {
            let status = SecItemDelete(query as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(status)) }
            return
        }
        let data = Data(journal.utf8)
        let status = SecItemAdd(query.merging([kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: data]) { _, value in value } as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let updated = SecItemUpdate(query as CFDictionary, [kSecValueData: data] as CFDictionary)
            guard updated == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(updated)) }
            return
        }
        guard status == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(status)) }
    }

    @discardableResult
    static func clearPendingPrompt(accountId: String, desktopId: String, sessionId: String, expected: String) throws -> Bool {
        guard try pendingPrompt(accountId: accountId, desktopId: desktopId, sessionId: sessionId) == expected else { return false }
        try savePendingPrompt(accountId: accountId, desktopId: desktopId, sessionId: sessionId, journal: nil)
        return true
    }

    static func clear() throws {
        let accountId = try load()?.accountId
        let status = SecItemDelete([
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: "identity",
        ] as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw NSError(domain: NSOSStatusErrorDomain, code: Int(status))
        }
    }
}

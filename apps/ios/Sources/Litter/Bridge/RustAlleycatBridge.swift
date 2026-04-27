import Foundation

// ---------------------------------------------------------------------------
// Swift wrapper around the Rust `AlleycatBridge` via UniFFI.
//
// The bridge is intentionally thin now — it only validates a scanned QR
// payload before the UI offers a Connect button. The actual QUIC + WebSocket
// dance lives on `MobileClient::connect_remote_over_alleycat`, exposed via
// `RustServerBridge.connectRemoteOverAlleycat`. Disconnect is handled by the
// shared `MobileClient.disconnect_server` because the QUIC session is now
// retained on the `ServerSession`.
// ---------------------------------------------------------------------------

final class RustAlleycatBridge: @unchecked Sendable {
    static let shared = RustAlleycatBridge()

    private let bridge: AlleycatBridge

    init(bridge: AlleycatBridge = AlleycatBridge()) {
        self.bridge = bridge
    }

    /// Validates and decodes a QR-scanned/pasted JSON payload into typed
    /// `AppAlleycatParams`. Throws `ClientError.InvalidParams` on bad payload
    /// or unsupported `protocolVersion`.
    func parsePairPayload(json: String) throws -> AppAlleycatParams {
        try bridge.parsePairPayload(json: json)
    }
}

/// Bridges the Rust reconnect controller's per-relay credential lookup to
/// our iOS Keychain-backed `AlleycatCredentialStore`. Wired into the
/// controller at app startup via `setAlleycatCredentialProvider`.
///
/// Returns nil when the `alleycat` experimental feature is off — the Rust
/// planner treats "no cached creds" as "fall through to other transports
/// or skip," so this acts as a kill-switch for auto-reconnect of saved
/// alleycat servers without needing a Rust-side flag.
final class IOSAlleycatCredentialProvider: AlleycatCredentialProvider {
    func loadCredential(host: String, udpPort: UInt16) -> AlleycatCredentialRecord? {
        guard ExperimentalFeatures.shared.isEnabled(.alleycat) else { return nil }
        let saved: SavedAlleycatParams?
        do {
            saved = try AlleycatCredentialStore.shared.load(host: host, udpPort: udpPort)
        } catch {
            NSLog(
                "[ALLEYCAT] credential lookup failed host=%@ udpPort=%u error=%@",
                host,
                UInt32(udpPort),
                error.localizedDescription
            )
            return nil
        }
        guard let saved else { return nil }
        return AlleycatCredentialRecord(
            protocolVersion: saved.protocolVersion,
            udpPort: saved.udpPort,
            certFingerprint: saved.certFingerprint,
            token: saved.token,
            hostCandidates: saved.hostCandidates ?? []
        )
    }
}

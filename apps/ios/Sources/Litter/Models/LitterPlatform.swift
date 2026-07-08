import Foundation
import SwiftUI
import UIKit

enum LitterPlatform {
#if targetEnvironment(macCatalyst)
    static let isCatalyst = true
#else
    static let isCatalyst = false
#endif

    /// `true` only on the unsandboxed Mac Catalyst lane (Developer ID
    /// notarized .dmg). Sandboxed Catalyst (Mac App Store) always sets
    /// `APP_SANDBOX_CONTAINER_ID`, so its absence on a Catalyst process
    /// is a reliable indicator that the App Sandbox is off and we can
    /// spawn child processes (codex app-server, etc.).
    static let isDirectDistMac: Bool = {
        guard isCatalyst else { return false }
        return ProcessInfo.processInfo.environment["APP_SANDBOX_CONTAINER_ID"] == nil
    }()

    /// `true` whenever the process renders as a Mac app — Catalyst or
    /// "Designed for iPad" on Apple Silicon. AppKit-bridge bugs hit
    /// both modes (NSVisualEffectView ignoring `fractionComplete=0`,
    /// NavigationSplitView Liquid Glass material being clobbered by
    /// gradient backdrops, menu-equivalent shortcuts not firing in-view),
    /// so UI workarounds gate on this rather than the compile-time
    /// `targetEnvironment(macCatalyst)` flag — the iOS lane in
    /// "Designed for iPad" mode hits the same AppKit bridge.
    static let rendersAsMacApp: Bool = {
        if isCatalyst { return true }
        return ProcessInfo.processInfo.isiOSAppOnMac
    }()

    static let supportsLocalRuntime = false
    static let supportsVoiceRuntime = false

    static func bootstrapLocalRuntimeIfNeeded() {
    }

    static func defaultLocalWorkingDirectory() -> String {
#if targetEnvironment(macCatalyst)
        return NSHomeDirectory()
#else
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first?.path ?? "/"
#endif
    }

    static func localRuntimeDisplayName() -> String {
#if targetEnvironment(macCatalyst)
        for candidate in [
            ProcessInfo.processInfo.hostName,
            ProcessInfo.processInfo.environment["HOSTNAME"],
            "本机 Mac"
        ] {
            if let displayName = normalizedHostDisplayName(candidate) {
                return displayName
            }
        }
        return "本机 Mac"
#else
        let device = UIDevice.current.name.trimmingCharacters(in: .whitespacesAndNewlines)
        return device.isEmpty ? "本机" : device
#endif
    }

#if targetEnvironment(macCatalyst)
    private static func normalizedHostDisplayName(_ raw: String?) -> String? {
        guard var value = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty else {
            return nil
        }

        if value.hasSuffix(".local") {
            value.removeLast(".local".count)
        } else if let dotIndex = value.firstIndex(of: ".") {
            value = String(value[..<dotIndex])
        }

        value = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
#endif

    static func isRegularSurface(horizontalSizeClass: UserInterfaceSizeClass?) -> Bool {
        isCatalyst || horizontalSizeClass == .regular
    }
}

import Foundation

/// Single source of truth for legacy user-facing `~` path formatting.
///
/// Used by `PathDisplay` to shorten older local `/root/foo` values to
/// `~/foo` in the UI. Remote-only iOS builds do not expose local iSH
/// directory navigation.
enum HomeAnchor {
    static let path: String = "/root"
}

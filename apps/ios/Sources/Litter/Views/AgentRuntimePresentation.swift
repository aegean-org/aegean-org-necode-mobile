import SwiftUI

extension AgentRuntimeKind {
    static let presentationOrder: [AgentRuntimeKind] = [
        .codex,
        .pi,
        .opencode,
        .claude,
    ]

    var displayLabel: String {
        switch self {
        case .codex:
            return "Codex"
        case .pi:
            return "Pi"
        case .opencode:
            return "opencode"
        case .claude:
            return "Claude"
        }
    }

    var titleDisplayLabel: String {
        switch self {
        case .opencode:
            return "Opencode"
        default:
            return displayLabel
        }
    }

    var assetName: String {
        switch self {
        case .codex:
            return "agent_codex"
        case .pi:
            return "agent_pi"
        case .opencode:
            return "agent_opencode"
        case .claude:
            return "agent_claude"
        }
    }

    var systemImageName: String {
        switch self {
        case .codex:
            return "terminal"
        case .pi:
            return "circle.hexagongrid"
        case .opencode:
            return "chevron.left.forwardslash.chevron.right"
        case .claude:
            return "sparkle"
        }
    }

    var presentationSortIndex: Int {
        Self.presentationOrder.firstIndex(of: self) ?? Int.max
    }
}

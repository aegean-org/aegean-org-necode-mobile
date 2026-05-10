import SwiftUI

extension AgentRuntimeKind {
    static let presentationOrder: [AgentRuntimeKind] = [
        .codex,
        .pi,
        .opencode,
        .claude,
        .droid,
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
        case .droid:
            return "Droid"
        }
    }

    var titleDisplayLabel: String {
        switch self {
        case .opencode:
            return "Opencode"
        case .droid:
            return "Factory Droid"
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
        case .droid:
            return "agent_droid"
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
        case .droid:
            return "gearshape"
        }
    }

    var presentationSortIndex: Int {
        Self.presentationOrder.firstIndex(of: self) ?? Int.max
    }

    var isBeta: Bool {
        switch self {
        case .claude, .pi, .opencode, .droid:
            return true
        case .codex:
            return false
        }
    }

    static func isBetaAgentName(_ name: String, displayName: String) -> Bool {
        let normalized = name.lowercased()
        let display = displayName.lowercased()
        let aliases: Set<String> = [
            "claude", "claude-code", "claude_code",
            "pi", "pi.dev", "pidev",
            "opencode", "open-code", "open_code", "open code",
            "droid", "factory", "factory-droid", "factory_droid", "factory droid",
        ]
        return aliases.contains(normalized) || aliases.contains(display)
    }
}

struct BetaBadge: View {
    var body: some View {
        Text("BETA")
            .litterFont(.caption2)
            .foregroundColor(LitterTheme.accent)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .overlay(
                RoundedRectangle(cornerRadius: 3)
                    .stroke(LitterTheme.accent.opacity(0.6), lineWidth: 0.5)
            )
    }
}

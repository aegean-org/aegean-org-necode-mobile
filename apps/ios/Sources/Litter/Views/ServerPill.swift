import SwiftUI

struct ServerPill: View {
    let server: HomeDashboardServer
    let isSelected: Bool
    let onTap: () -> Void
    let onReconnect: () -> Void
    let onRestartAppServer: () -> Void
    let onRename: () -> Void
    let onRemove: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 6) {
                StatusDot(state: server.statusDotState, size: 8)
                HStack(spacing: 2) {
                    Text(server.displayName)
                        .litterMonoFont(size: 13, weight: .semibold)
                        .foregroundStyle(LitterTheme.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.tail)
                    AgentRuntimeBadgeStack(runtimes: server.agentRuntimes)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .modifier(GlassCapsuleModifier(interactive: true))
        .overlay(
            Capsule(style: .continuous)
                .stroke(
                    isSelected ? LitterTheme.accent.opacity(0.75) : LitterTheme.textMuted.opacity(0.25),
                    lineWidth: isSelected ? 1.2 : 0.6
                )
                .allowsHitTesting(false)
        )
        .contextMenu {
            Button {
                onReconnect()
            } label: {
                Label("Reconnect", systemImage: "arrow.clockwise")
            }
            Button {
                onRestartAppServer()
            } label: {
                Label("Restart app server", systemImage: "arrow.triangle.2.circlepath")
            }
            if !server.isLocal {
                Button {
                    onRename()
                } label: {
                    Label("Rename", systemImage: "pencil")
                }
            }
            Button(role: .destructive) {
                onRemove()
            } label: {
                Label("Remove", systemImage: "trash")
            }
        }
    }
}

private struct AgentRuntimeBadgeStack: View {
    let runtimes: [AgentRuntimeInfo]
    private let badgeSize: CGFloat = 18
    private let badgeOffset: CGFloat = 11

    private var visibleRuntimes: [AgentRuntimeInfo] {
        var seenKinds: [AgentRuntimeKind] = []
        return runtimes
            .filter(\.available)
            .sorted { lhs, rhs in
                lhs.kind.presentationSortIndex < rhs.kind.presentationSortIndex
            }
            .filter { runtime in
                guard !seenKinds.contains(runtime.kind) else { return false }
                seenKinds.append(runtime.kind)
                return true
            }
    }

    var body: some View {
        let visible = visibleRuntimes
        if !visible.isEmpty {
            ZStack(alignment: .leading) {
                ForEach(Array(visible.enumerated()), id: \.offset) { index, runtime in
                    AgentRuntimeBadge(runtime: runtime)
                        .offset(x: CGFloat(index) * badgeOffset)
                        .zIndex(Double(index))
                }
            }
            .frame(
                width: badgeSize + CGFloat(max(visible.count - 1, 0)) * badgeOffset,
                height: badgeSize
            )
            .accessibilityLabel(visible.map(\.displayName).joined(separator: ", "))
        }
    }
}

private struct AgentRuntimeBadge: View {
    let runtime: AgentRuntimeInfo

    var body: some View {
        Image(runtime.kind.assetName)
            .resizable()
            .scaledToFit()
            .padding(runtime.kind == .codex ? 2 : 1)
            .frame(width: 18, height: 18)
            .background(
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .fill(Color.black.opacity(0.82))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 5, style: .continuous)
                    .stroke(LitterTheme.textPrimary.opacity(0.28), lineWidth: 0.55)
            )
            .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
            .shadow(color: .black.opacity(0.32), radius: 2, y: 1)
    }
}

struct AddServerPill: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 4) {
                Image(systemName: "plus")
                    .font(.system(size: 11, weight: .semibold))
                Text("server")
                    .litterMonoFont(size: 13, weight: .semibold)
            }
            .foregroundStyle(LitterTheme.accent)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .modifier(GlassCapsuleModifier(interactive: true))
        .overlay(
            Capsule(style: .continuous)
                .stroke(LitterTheme.accent.opacity(0.45), lineWidth: 0.8)
                .allowsHitTesting(false)
        )
        .coachmarkAnchor(.addServer)
    }
}

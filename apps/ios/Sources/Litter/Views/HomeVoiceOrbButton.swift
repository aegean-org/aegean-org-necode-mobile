import SwiftUI

/// Capsule voice button matching the `+` and search buttons in
/// `HomeBottomBar`. Size and glass treatment are identical; only the icon
/// and its tint differ.
struct HomeVoiceOrbButton: View {
    let isAvailable: Bool
    let isStarting: Bool
    let action: () -> Void

    private let buttonSize: CGFloat = 44

    private var isDisabled: Bool {
        !isAvailable || isStarting
    }

    private var accessibilityLabel: String {
        if !isAvailable { return "语音输入不可用" }
        if isStarting { return "正在启动语音输入" }
        return "语音输入"
    }

    private var iconColor: Color {
        LitterTheme.accent
    }

    private var strokeColor: Color {
        isStarting ? iconColor.opacity(0.5) : LitterTheme.textMuted.opacity(0.3)
    }

    private var strokeWidth: CGFloat {
        isStarting ? 0.8 : 0.6
    }

    var body: some View {
        Button(action: action) {
            Group {
                if isStarting {
                    ProgressView()
                        .controlSize(.small)
                        .tint(iconColor)
                } else {
                    Image(systemName: "mic.fill")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(iconColor)
                }
            }
            .frame(width: buttonSize, height: buttonSize)
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .modifier(GlassCapsuleModifier(interactive: true))
        .overlay(
            Capsule(style: .continuous)
                .stroke(strokeColor, lineWidth: strokeWidth)
                .allowsHitTesting(false)
        )
        .disabled(isDisabled)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHint("录音并转成文字填入输入框。")
        .coachmarkAnchor(.voice)
    }
}

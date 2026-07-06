import SwiftUI

struct AnimatedSplashView: View {
    let appReady: Bool
    var compact: Bool = false
    let onFinished: () -> Void

    var body: some View {
        ZStack {
            if !compact {
                LitterTheme.backgroundGradient.ignoresSafeArea()
            }

            VStack(spacing: compact ? 0 : 14) {
                BrandLogo(size: compact ? 44 : 132)

                if !compact {
                    Text("NeCode Mobile")
                        .litterMonoFont(size: 15, weight: .semibold)
                        .foregroundColor(LitterTheme.textSecondary)
                }
            }
            .opacity(appReady ? 1 : 0.72)
        }
        .onAppear {
            guard appReady else { return }
            onFinished()
        }
    }
}

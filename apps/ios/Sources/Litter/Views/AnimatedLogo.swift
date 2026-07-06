import SwiftUI

/// Compact logo wrapper used by the home dashboard.
struct AnimatedLogo: View {
    var size: CGFloat = 44

    var body: some View {
        AnimatedSplashView(appReady: true, compact: true) {}
            .frame(width: size, height: size)
            .clipped()
            .accessibilityHidden(true)
    }
}

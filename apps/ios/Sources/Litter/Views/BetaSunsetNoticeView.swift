import SwiftUI
import UIKit

enum BetaBuildEnvironment {
    static var isTestFlight: Bool {
        #if DEBUG
        if ProcessInfo.processInfo.environment["LITTER_FORCE_BETA_SUNSET"] == "1" {
            return true
        }
        #endif

        #if targetEnvironment(simulator)
        return false
        #else
        // Dev/AdHoc-signed builds carry a provisioning profile inside the
        // bundle. App Store and TestFlight installs do not. Without this
        // guard, locally signed Release builds would also be flagged.
        if Bundle.main.path(forResource: "embedded", ofType: "mobileprovision") != nil {
            return false
        }
        return Bundle.main.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt"
        #endif
    }
}

enum BetaSunsetNotice {
    /// Show on every cold launch while this build is still on TestFlight —
    /// no persistence, the message keeps nagging until the user installs the
    /// stable App Store version (or until we ship a non-TestFlight build).
    static var shouldShow: Bool {
        BetaBuildEnvironment.isTestFlight
    }
}

private enum BetaSunsetLinks {
    static let appStore = URL(string: "https://apps.apple.com/us/app/kittylitter/id6759521788")!
    static let website = URL(string: "https://kittylitter.app")!
}

struct BetaSunsetNoticeView: View {
    let onDismiss: () -> Void

    @Environment(\.openURL) private var openURL

    var body: some View {
        ZStack {
            LitterTheme.backgroundGradient.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Spacer().frame(height: 12)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Beta sunset")
                            .litterFont(.caption, weight: .semibold)
                            .foregroundColor(LitterTheme.accent)
                            .textCase(.uppercase)
                        Text("This TestFlight is ending soon")
                            .litterFont(.title2, weight: .bold)
                            .foregroundColor(LitterTheme.textPrimary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    VStack(alignment: .leading, spacing: 14) {
                        Text("KittyLitter is moving to a stable release on the App Store. Install the public version to keep getting updates without interruption.")
                            .litterFont(.body)
                            .foregroundColor(LitterTheme.textBody)
                            .fixedSize(horizontal: false, vertical: true)

                        Text("If you'd like to keep testing pre-release builds and sending feedback, you can opt in to the new TestFlight on the kittylitter.app website.")
                            .litterFont(.body)
                            .foregroundColor(LitterTheme.textBody)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    VStack(spacing: 10) {
                        Button {
                            openURL(BetaSunsetLinks.appStore)
                            onDismiss()
                        } label: {
                            Text("Get the App Store version")
                                .litterFont(.callout, weight: .semibold)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(LitterTheme.accent)

                        Button {
                            openURL(BetaSunsetLinks.website)
                            onDismiss()
                        } label: {
                            Text("Visit kittylitter.app")
                                .litterFont(.callout, weight: .semibold)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                        .buttonStyle(.bordered)
                        .tint(LitterTheme.accent)

                        Button {
                            onDismiss()
                        } label: {
                            Text("Remind me later")
                                .litterFont(.footnote)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                        }
                        .buttonStyle(.plain)
                        .foregroundColor(LitterTheme.textMuted)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 28)
                .frame(maxWidth: 520, alignment: .leading)
                .frame(maxWidth: .infinity)
            }
        }
    }
}

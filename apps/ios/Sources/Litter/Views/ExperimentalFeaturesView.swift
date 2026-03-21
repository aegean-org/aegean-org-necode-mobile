import SwiftUI

struct ExperimentalFeaturesView: View {
    @State private var experimentalFeatures = ExperimentalFeatures.shared

    var body: some View {
        ZStack {
            LitterTheme.backgroundGradient.ignoresSafeArea()
            Form {
                Section {
                    ForEach(LitterFeature.allCases) { feature in
                        Toggle(isOn: binding(for: feature)) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(feature.displayName)
                                    .litterFont(.subheadline)
                                    .foregroundColor(LitterTheme.textPrimary)
                                Text(feature.description)
                                    .litterFont(.caption)
                                    .foregroundColor(LitterTheme.textSecondary)
                            }
                        }
                        .tint(LitterTheme.accentStrong)
                        .listRowBackground(LitterTheme.surface.opacity(0.6))
                    }
                } header: {
                    Text("Features")
                        .foregroundColor(LitterTheme.textSecondary)
                } footer: {
                    Text("Experimental features may be unstable or change without notice.")
                        .foregroundColor(LitterTheme.textMuted)
                }
            }
            .scrollContentBackground(.hidden)
        }
        .navigationTitle("Experimental")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func binding(for feature: LitterFeature) -> Binding<Bool> {
        Binding(
            get: { experimentalFeatures.isEnabled(feature) },
            set: { newValue in
                experimentalFeatures.setEnabled(feature, newValue)
            }
        )
    }
}

#if DEBUG
#Preview("Experimental Features") {
    NavigationStack {
        ExperimentalFeaturesView()
    }
}
#endif

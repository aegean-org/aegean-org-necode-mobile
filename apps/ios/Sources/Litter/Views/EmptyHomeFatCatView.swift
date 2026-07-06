import SwiftUI
import UIKit

@MainActor
struct EmptyHomeFatCatView: View {
    @State private var showingLoop = false
    @State private var transmissionActive = false
    @State private var transmissionFrameIndex = 0
    @State private var pressTask: Task<Void, Never>?

    private static let entranceDurationNs: UInt64 = 11_100_000_000
    private static let frameDurationNs: UInt64 = 82_000_000
    private static let longPressDelayNs: UInt64 = 450_000_000
    private static let transmissionFrames = (1...6).map { index in
        "cat_transmission_\(String(format: "%02d", index))"
    }

    var body: some View {
        GeometryReader { proxy in
            let size = catSize(in: proxy.size)
            fatCatImage
                .frame(width: size.width, height: size.height)
                .position(x: proxy.size.width / 2, y: proxy.size.height * 0.42)
                .gesture(pressGesture)
        }
        .task { await playEntranceOnce() }
        .task(id: transmissionActive) { await animateTransmissionFrames() }
        .onDisappear { cancelPress() }
        .accessibilityHidden(true)
    }

    private var fatCatImage: some View {
        FatCatAssetView(name: currentAssetName, contentMode: transmissionActive ? .scaleAspectFill : .scaleAspectFit)
            .clipShape(Rectangle())
    }

    private var currentAssetName: String {
        if transmissionActive {
            return Self.transmissionFrames[transmissionFrameIndex]
        }
        return showingLoop ? "home_cat" : "home_cat_entrance"
    }

    private var pressGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in startPressIfNeeded() }
            .onEnded { _ in cancelPress() }
    }

    private func catSize(in container: CGSize) -> CGSize {
        let width = min(max(container.width * 0.55, 180), 260)
        return CGSize(width: width, height: width * 202 / 360)
    }

    private func playEntranceOnce() async {
        guard !showingLoop else { return }
        try? await Task.sleep(nanoseconds: Self.entranceDurationNs)
        guard !Task.isCancelled else { return }
        showingLoop = true
    }

    private func animateTransmissionFrames() async {
        transmissionFrameIndex = 0
        guard transmissionActive else { return }
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: Self.frameDurationNs)
            guard !Task.isCancelled else { return }
            transmissionFrameIndex = (transmissionFrameIndex + 1) % Self.transmissionFrames.count
        }
    }

    private func startPressIfNeeded() {
        guard pressTask == nil else { return }
        pressTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: Self.longPressDelayNs)
            guard !Task.isCancelled else { return }
            transmissionActive = true
        }
    }

    private func cancelPress() {
        pressTask?.cancel()
        pressTask = nil
        transmissionActive = false
    }
}

private struct FatCatAssetView: UIViewRepresentable {
    let name: String
    let contentMode: UIView.ContentMode

    func makeUIView(context: Context) -> UIImageView {
        let imageView = UIImageView()
        imageView.backgroundColor = .clear
        imageView.clipsToBounds = true
        imageView.isUserInteractionEnabled = false
        return imageView
    }

    func updateUIView(_ imageView: UIImageView, context: Context) {
        imageView.contentMode = contentMode
        imageView.image = UIImage(named: name)
        imageView.startAnimating()
    }
}

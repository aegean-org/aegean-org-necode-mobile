import SwiftUI
import UIKit

private let petFrameWidth = 192
private let petFrameHeight = 208
private let petColumns = 8
private let petRows = 9
private let petAtlasWidth = petFrameWidth * petColumns
private let petAtlasHeight = petFrameHeight * petRows

private struct PetSpriteAtlas {
    let image: UIImage
    let framesByRow: [[Int]]

    func frames(for state: PetAvatarState) -> [Int] {
        guard framesByRow.indices.contains(state.rawValue) else { return [0] }
        return framesByRow[state.rawValue]
    }
}

private struct PetAnimationProfile {
    let frameDurationsMs: [UInt64]

    func durationMs(for frameIndex: Int) -> UInt64 {
        guard !frameDurationsMs.isEmpty else { return 120 }
        return frameDurationsMs[min(frameIndex, frameDurationsMs.count - 1)]
    }

    static func profile(for state: PetAvatarState) -> PetAnimationProfile {
        switch state {
        case .idle:
            return PetAnimationProfile(frameDurationsMs: [1680, 660, 660, 840, 840, 1920])
        case .runningRight, .runningLeft:
            return PetAnimationProfile(frameDurationsMs: [120, 120, 120, 120, 120, 120, 120, 220])
        case .running:
            return PetAnimationProfile(frameDurationsMs: [120, 120, 120, 120, 120, 220])
        case .waiting:
            return PetAnimationProfile(frameDurationsMs: [150, 150, 150, 150, 150, 260])
        case .review:
            return PetAnimationProfile(frameDurationsMs: [150, 150, 150, 150, 150, 280])
        case .failed:
            return PetAnimationProfile(frameDurationsMs: [140, 140, 140, 140, 140, 140, 140, 240])
        case .jumping:
            return PetAnimationProfile(frameDurationsMs: [140, 140, 140, 140, 280])
        case .waving:
            return PetAnimationProfile(frameDurationsMs: [140, 140, 140, 280])
        }
    }
}

struct PetOverlayView: View {
    @State private var controller = PetOverlayController.shared
    let pet: CachedPetPackage
    let state: PetAvatarState
    let message: String?
    let reduceMotion: Bool
    @State private var lastDragTranslation = CGSize.zero

    var body: some View {
        ZStack(alignment: .topLeading) {
            PetSpriteView(
                spritesheetBytes: pet.spritesheetBytes,
                state: state,
                reduceMotion: reduceMotion
            )
            .frame(width: 112, height: 122)

            if let message {
                PetSpeechBubble(text: message)
                    .offset(x: 64, y: -10)
            }
        }
        .offset(controller.dragOffset)
        .gesture(
            DragGesture()
                .onChanged { value in
                    controller.startDrag()
                    let delta = value.translation - lastDragTranslation
                    lastDragTranslation = value.translation
                    controller.dragBy(delta)
                }
                .onEnded { _ in
                    lastDragTranslation = .zero
                    controller.endDrag()
                }
        )
    }
}

private struct PetSpeechBubble: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .medium, design: .monospaced))
            .foregroundStyle(LitterTheme.textPrimary)
            .lineLimit(2)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .frame(maxWidth: 180, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(LitterTheme.surface.opacity(0.94))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .stroke(LitterTheme.border.opacity(0.9), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.18), radius: 8, x: 0, y: 4)
    }
}

struct PetSpriteView: View {
    let spritesheetBytes: Data
    let state: PetAvatarState
    let reduceMotion: Bool
    @State private var atlas: PetSpriteAtlas?
    @State private var playbackState: PetAvatarState?
    @State private var frameIndex = 0

    var body: some View {
        let renderedState = playbackState ?? state
        let frames = atlas?.frames(for: renderedState) ?? [0]
        let atlasSignature = atlas?.framesByRow.map { row in
            row.map(String.init).joined(separator: ",")
        }.joined(separator: "|") ?? ""

        GeometryReader { proxy in
            if let atlas, let frameImage = frameImage(from: atlas, state: renderedState, frames: frames) {
                Image(uiImage: frameImage)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
                    .frame(width: proxy.size.width, height: proxy.size.height)
            }
        }
        .aspectRatio(CGFloat(petFrameWidth) / CGFloat(petFrameHeight), contentMode: .fit)
        .task(id: spritesheetBytes) {
            atlas = decodeAtlas(from: spritesheetBytes)
        }
        .task(id: "\(state.rawValue)-\(reduceMotion)-\(atlasSignature)") {
            playbackState = state
            frameIndex = 0
            guard !reduceMotion else { return }

            if state == .idle {
                await playLoop(for: .idle)
            } else {
                await playLoop(for: state, cycles: 3)
                guard !Task.isCancelled else { return }
                playbackState = .idle
                frameIndex = 0
                await playLoop(for: .idle)
            }
        }
    }

    private func frameImage(from atlas: PetSpriteAtlas, state: PetAvatarState, frames: [Int]) -> UIImage? {
        guard let cgImage = atlas.image.cgImage else { return nil }
        let frame = frames.indices.contains(frameIndex) ? frames[frameIndex] : frames.first ?? 0
        let rect = CGRect(
            x: frame * petFrameWidth,
            y: state.rawValue * petFrameHeight,
            width: petFrameWidth,
            height: petFrameHeight
        )
        guard let cropped = cgImage.cropping(to: rect) else { return nil }
        return UIImage(cgImage: cropped, scale: 1, orientation: .up)
    }

    private func playLoop(for state: PetAvatarState, cycles: Int? = nil) async {
        let frames = atlas?.frames(for: state) ?? [0]
        guard frames.count > 1 else { return }
        let profile = PetAnimationProfile.profile(for: state)
        var completedCycles = 0

        while cycles == nil || completedCycles < (cycles ?? 0) {
            for index in frames.indices {
                guard !Task.isCancelled else { return }
                playbackState = state
                frameIndex = index
                try? await Task.sleep(for: .milliseconds(profile.durationMs(for: index)))
            }
            completedCycles += 1
        }
    }

    private func decodeAtlas(from data: Data) -> PetSpriteAtlas? {
        guard let image = UIImage(data: data),
              let cgImage = image.cgImage,
              cgImage.width == petAtlasWidth,
              cgImage.height == petAtlasHeight
        else { return nil }
        return PetSpriteAtlas(image: image, framesByRow: detectNonTransparentFrames(in: cgImage))
    }
}

private func detectNonTransparentFrames(in image: CGImage) -> [[Int]] {
    (0..<petRows).map { row in
        let frames = (0..<petColumns).filter { column in
            frameHasVisiblePixels(in: image, row: row, column: column)
        }
        return frames.isEmpty ? [0] : frames
    }
}

private func frameHasVisiblePixels(in image: CGImage, row: Int, column: Int) -> Bool {
    let rect = CGRect(
        x: column * petFrameWidth,
        y: row * petFrameHeight,
        width: petFrameWidth,
        height: petFrameHeight
    )
    guard let frame = image.cropping(to: rect) else { return false }

    var pixels = [UInt8](repeating: 0, count: petFrameWidth * petFrameHeight * 4)
    guard let context = CGContext(
        data: &pixels,
        width: petFrameWidth,
        height: petFrameHeight,
        bitsPerComponent: 8,
        bytesPerRow: petFrameWidth * 4,
        space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    ) else { return true }

    context.draw(frame, in: CGRect(x: 0, y: 0, width: petFrameWidth, height: petFrameHeight))
    return stride(from: 3, to: pixels.count, by: 4).contains { pixels[$0] != 0 }
}

private func - (lhs: CGSize, rhs: CGSize) -> CGSize {
    CGSize(width: lhs.width - rhs.width, height: lhs.height - rhs.height)
}

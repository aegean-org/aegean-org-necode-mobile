import AVKit
import CoreMedia
import CoreVideo
import Observation
import SwiftUI
import UIKit

/// Top-level controller for the streaming-assistant Picture-in-Picture
/// window. Owns the `AVPictureInPictureController` + sample-buffer host
/// view and drives the render loop. Singleton instance lives on `AppDelegate`.
///
/// Rendering pipeline:
///   PiPContentView (SwiftUI) → ImageRenderer.cgImage → CVPixelBuffer
///                            → CMSampleBuffer → AVSampleBufferDisplayLayer
///
/// Push cadence: 4 Hz. We always push a fresh frame — the view body reads
/// `AppModel.shared.snapshot` directly so each render reflects current state.
@MainActor
@Observable
final class StreamingPiPController: NSObject {
    static let shared = StreamingPiPController()

    /// Mirrored to the toolbar button so it can render `pip.fill` while open.
    private(set) var isActive: Bool = false

    /// Surfaces a non-fatal startup error to UI (e.g. unsupported device).
    private(set) var lastErrorMessage: String?

    /// When set, PiP renders this specific thread regardless of which thread
    /// is currently active in the app. `nil` falls back to the active thread.
    /// Cleared when PiP closes.
    private(set) var pinnedThreadKey: ThreadKey?

    /// PiP canvas width is fixed; height grows with the card's content.
    /// PiP reads each sample buffer's format description to size the
    /// floating window, so changing dimensions per frame is how we resize.
    @ObservationIgnored private let renderWidth: CGFloat = 360
    /// Coarse vertical snap so the floating window doesn't jitter on every
    /// token. PiP-side animation handles the transition between steps.
    @ObservationIgnored private let heightStep: CGFloat = 24
    @ObservationIgnored private let pushIntervalSeconds: TimeInterval = 0.25
    @ObservationIgnored private var currentRenderSize = CGSize(width: 360, height: 160)
    /// When set, height is locked to this value (clamped to PiPContentView
    /// min/max) regardless of the card's intrinsic content height. Set by
    /// the PiP skip ⏪/⏩ controls and cleared on PiP close.
    @ObservationIgnored private var userHeightOverride: CGFloat?

    @ObservationIgnored private let audioKeeper = SilentAudioKeeper()
    @ObservationIgnored private var hostView: PiPHostView?
    @ObservationIgnored private var pipController: AVPictureInPictureController?
    @ObservationIgnored private var possibleObservation: NSKeyValueObservation?
    @ObservationIgnored private var renderTimer: Timer?
    @ObservationIgnored private var pixelBufferPool: CVPixelBufferPool?
    /// Set by `start()` when we want PiP to begin as soon as the controller's
    /// `isPictureInPicturePossible` flips true. Without this gate the first
    /// `startPictureInPicture()` after fresh setup is silently dropped because
    /// iOS hasn't acknowledged the new layer + audio session yet.
    @ObservationIgnored private var pendingStart = false

    /// SwiftUI body we ask `ImageRenderer` to rasterise every tick. The body
    /// reads `AppModel.shared` so each rasterisation reflects fresh state.
    @ObservationIgnored private lazy var renderer: ImageRenderer<PiPContentView> = {
        let r = ImageRenderer(content: PiPContentView())
        // Width is fixed; nil height lets ImageRenderer use the SwiftUI
        // content's intrinsic height (clamped by PiPContentView's frame).
        r.proposedSize = ProposedViewSize(width: renderWidth, height: nil)
        r.scale = 1
        return r
    }()

    // MARK: - Lifecycle

    var isSupported: Bool { AVPictureInPictureController.isPictureInPictureSupported() }

    func toggle() {
        if isActive { stop() } else { start() }
    }

    /// Open PiP pinned to a specific thread (used by the home-card menu).
    /// If PiP is already open for the same key, no-op; if open for a
    /// different key, repin without closing.
    func start(for threadKey: ThreadKey) {
        pinnedThreadKey = threadKey
        if isActive { return }
        start()
    }

    func start() {
        guard !isActive else { return }
        guard isSupported else {
            lastErrorMessage = "PiP not supported on this device."
            LLog.warn("pip", "start: device does not support PiP")
            return
        }
        guard ensureSetup() else {
            lastErrorMessage = "Could not initialize PiP."
            return
        }

        // Reset the display layer if it's lingering in a failed/idle state
        // from the previous session — without flushing, enqueue is a no-op
        // and `isPictureInPicturePossible` never flips true on reopen.
        hostView?.displayLayer.flushAndRemoveImage()
        audioKeeper.activate()
        // Push at least one frame so the layer has content before start.
        // PiP refuses to start against an empty display layer.
        pushFrame()
        startRenderTimer()
        pendingStart = true
        startIfPossible()
    }

    func stop() {
        pendingStart = false
        // didStop delegate handles per-session cleanup (timer + audio).
        // Host view + controller + pool persist for the next session.
        pipController?.stopPictureInPicture()
    }

    /// Calls `startPictureInPicture()` if (a) we have a pending start and
    /// (b) the controller currently reports `isPictureInPicturePossible`.
    /// Otherwise we wait — the KVO observation in `ensureSetup()` will call
    /// back here once iOS finishes wiring up the new layer + audio session.
    private func startIfPossible() {
        guard pendingStart, let controller = pipController else { return }
        guard controller.isPictureInPicturePossible else { return }
        pendingStart = false
        controller.startPictureInPicture()
        LLog.info("pip", "startPictureInPicture invoked")
    }

    /// Per-session cleanup invoked from `pictureInPictureControllerDidStop`.
    /// Intentionally keeps `hostView`, `pipController`, and `pixelBufferPool`
    /// alive — recreating them across sessions races with iOS's PiP slide-out
    /// animation and is the source of the second-tap crash.
    private func endSession() {
        renderTimer?.invalidate()
        renderTimer = nil
        audioKeeper.deactivate()
        isActive = false
        pinnedThreadKey = nil
        userHeightOverride = nil
    }

    // MARK: - One-time setup

    /// Lazily builds the persistent host view + controller + pool. Returns
    /// `false` only if there is no key window to host the layer.
    private func ensureSetup() -> Bool {
        if pipController != nil { return true }
        guard let window = keyWindow() else {
            LLog.warn("pip", "ensureSetup: key window unavailable")
            return false
        }
        let host = PiPHostView(frame: CGRect(x: -1, y: -1, width: 1, height: 1))
        window.addSubview(host)
        hostView = host
        ensurePixelBufferPool()

        let source = AVPictureInPictureController.ContentSource(
            sampleBufferDisplayLayer: host.displayLayer,
            playbackDelegate: self
        )
        let controller = AVPictureInPictureController(contentSource: source)
        controller.delegate = self
        // requiresLinearPlayback = false exposes the chevron skip buttons in
        // PiP's control bar. We repurpose them as shrink/grow controls via
        // the `skipByInterval` delegate callback.
        controller.requiresLinearPlayback = false
        pipController = controller
        // KVO so `start()` can defer until iOS reports readiness.
        possibleObservation = controller.observe(
            \.isPictureInPicturePossible,
            options: [.new]
        ) { [weak self] _, change in
            guard change.newValue == true else { return }
            Task { @MainActor [weak self] in self?.startIfPossible() }
        }
        return true
    }

    private func keyWindow() -> UIWindow? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
    }

    // MARK: - Render loop

    private func startRenderTimer() {
        renderTimer?.invalidate()
        let timer = Timer(timeInterval: pushIntervalSeconds, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.pushFrame() }
        }
        RunLoop.main.add(timer, forMode: .common)
        renderTimer = timer
    }

    private func pushFrame() {
        guard let host = hostView else { return }
        // Recover from a failed layer state — happens after some PiP
        // transitions / app lifecycle events. Without this, enqueue is a
        // silent no-op and the window appears frozen.
        if host.displayLayer.status == .failed {
            LLog.warn(
                "pip",
                "display layer failed; flushing",
                fields: [
                    "error": host.displayLayer.error?.localizedDescription ?? "unknown"
                ]
            )
            host.displayLayer.flushAndRemoveImage()
        }
        // Skip when the layer's queue is full; rendering + buffer alloc
        // are the expensive part, so bail before paying that cost.
        guard host.displayLayer.isReadyForMoreMediaData else { return }
        // Reassign content each tick so ImageRenderer doesn't reuse a cached
        // render — particularly important on a fresh session where the
        // observed state changed but the renderer instance is the same.
        renderer.content = PiPContentView()
        // Phase 1: measure the SwiftUI content's intrinsic height for our
        // fixed width.
        renderer.proposedSize = ProposedViewSize(width: renderWidth, height: nil)
        guard let measured = renderer.cgImage else { return }
        let target = adaptedSize(forIntrinsicHeight: CGFloat(measured.height))
        // Phase 2: re-render at the snapped target so the image exactly fills
        // the pixel buffer (no stretching, no offset). Skip if Phase 1
        // already happened to match.
        let finalImage: CGImage
        if CGFloat(measured.height) == target.height {
            finalImage = measured
        } else {
            renderer.proposedSize = ProposedViewSize(width: renderWidth, height: target.height)
            guard let snapped = renderer.cgImage else { return }
            finalImage = snapped
        }
        if target != currentRenderSize {
            // Format description is about to change. Without flushing,
            // the layer's queue may refuse to accept the new-format
            // buffer and `isReadyForMoreMediaData` stays false — which
            // is the "stops updating after I resize" symptom.
            host.displayLayer.flush()
            currentRenderSize = target
            pixelBufferPool = nil
        }
        ensurePixelBufferPool(size: target)
        guard let pixelBuffer = makePixelBuffer(from: finalImage) else { return }
        guard let sampleBuffer = makeSampleBuffer(from: pixelBuffer) else { return }
        host.displayLayer.enqueue(sampleBuffer)
    }

    /// Snaps the SwiftUI-measured height up to the next multiple of
    /// `heightStep` and clamps it to PiPContentView's min/max. When the
    /// user has tapped a skip control we honour that height verbatim
    /// (still clamped) instead of letting content drive growth.
    /// Returns the final pixel-buffer canvas size.
    private func adaptedSize(forIntrinsicHeight rawHeight: CGFloat) -> CGSize {
        let base = userHeightOverride ?? rawHeight
        let clamped = min(
            PiPContentView.maxHeight,
            max(PiPContentView.minHeight, base)
        )
        let snapped = (ceil(clamped / heightStep) * heightStep)
        return CGSize(width: renderWidth, height: snapped)
    }

    /// Skip-button handler — `delta > 0` grows, `delta < 0` shrinks. We
    /// quantise to two `heightStep`s per tap so the change is perceptible
    /// without making the user mash the button.
    fileprivate func adjustHeight(byTaps delta: Int) {
        let bump = CGFloat(delta) * heightStep * 2
        let current = userHeightOverride ?? currentRenderSize.height
        let target = min(
            PiPContentView.maxHeight,
            max(PiPContentView.minHeight, current + bump)
        )
        userHeightOverride = target
        // Push immediately so the resize feels instant rather than waiting
        // for the next timer tick.
        pushFrame()
    }

    // MARK: - Sample-buffer pipeline

    private func ensurePixelBufferPool(size: CGSize? = nil) {
        guard pixelBufferPool == nil else { return }
        let target = size ?? currentRenderSize
        let attrs: [String: Any] = [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: Int(target.width),
            kCVPixelBufferHeightKey as String: Int(target.height),
            kCVPixelBufferIOSurfacePropertiesKey as String: [:] as CFDictionary
        ]
        var pool: CVPixelBufferPool?
        let status = CVPixelBufferPoolCreate(
            kCFAllocatorDefault,
            nil,
            attrs as CFDictionary,
            &pool
        )
        if status == kCVReturnSuccess { pixelBufferPool = pool }
    }

    private func makePixelBuffer(from image: CGImage) -> CVPixelBuffer? {
        guard let pool = pixelBufferPool else { return nil }
        var buffer: CVPixelBuffer?
        let status = CVPixelBufferPoolCreatePixelBuffer(nil, pool, &buffer)
        guard status == kCVReturnSuccess, let pixelBuffer = buffer else { return nil }

        CVPixelBufferLockBaseAddress(pixelBuffer, [])
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, []) }

        guard let base = CVPixelBufferGetBaseAddress(pixelBuffer) else { return nil }
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGImageAlphaInfo.premultipliedFirst.rawValue
            | CGBitmapInfo.byteOrder32Little.rawValue
        guard let ctx = CGContext(
            data: base,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: bitmapInfo
        ) else { return nil }

        ctx.setFillColor(UIColor.black.cgColor)
        ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return pixelBuffer
    }

    private func makeSampleBuffer(from pixelBuffer: CVPixelBuffer) -> CMSampleBuffer? {
        var formatDescription: CMFormatDescription?
        CMVideoFormatDescriptionCreateForImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescriptionOut: &formatDescription
        )
        guard let formatDescription else { return nil }

        let pts = CMClockGetTime(CMClockGetHostTimeClock())
        var timing = CMSampleTimingInfo(
            duration: CMTime(value: 1, timescale: 4),
            presentationTimeStamp: pts,
            decodeTimeStamp: .invalid
        )

        var sample: CMSampleBuffer?
        let status = CMSampleBufferCreateReadyWithImageBuffer(
            allocator: kCFAllocatorDefault,
            imageBuffer: pixelBuffer,
            formatDescription: formatDescription,
            sampleTiming: &timing,
            sampleBufferOut: &sample
        )
        guard status == noErr, let sb = sample else { return nil }

        if let attachments = CMSampleBufferGetSampleAttachmentsArray(sb, createIfNecessary: true) {
            let dict = unsafeBitCast(
                CFArrayGetValueAtIndex(attachments, 0),
                to: CFMutableDictionary.self
            )
            CFDictionarySetValue(
                dict,
                Unmanaged.passUnretained(kCMSampleAttachmentKey_DisplayImmediately).toOpaque(),
                Unmanaged.passUnretained(kCFBooleanTrue).toOpaque()
            )
        }
        return sb
    }
}

// MARK: - AVPictureInPictureControllerDelegate

extension StreamingPiPController: AVPictureInPictureControllerDelegate {
    nonisolated func pictureInPictureControllerDidStartPictureInPicture(
        _ controller: AVPictureInPictureController
    ) {
        Task { @MainActor in
            self.isActive = true
            LLog.info("pip", "didStartPictureInPicture")
        }
    }

    nonisolated func pictureInPictureControllerDidStopPictureInPicture(
        _ controller: AVPictureInPictureController
    ) {
        Task { @MainActor in
            LLog.info("pip", "didStopPictureInPicture")
            self.endSession()
        }
    }

    nonisolated func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        failedToStartPictureInPictureWithError error: any Error
    ) {
        Task { @MainActor in
            LLog.warn("pip", "failedToStartPictureInPicture", fields: ["error": "\(error)"])
            self.lastErrorMessage = error.localizedDescription
            self.endSession()
        }
    }
}

// MARK: - AVPictureInPictureSampleBufferPlaybackDelegate

extension StreamingPiPController: AVPictureInPictureSampleBufferPlaybackDelegate {
    nonisolated func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        setPlaying playing: Bool
    ) {}

    nonisolated func pictureInPictureControllerTimeRangeForPlayback(
        _ controller: AVPictureInPictureController
    ) -> CMTimeRange {
        // iOS disables the skip chevrons when the playback range is
        // infinite (it treats the source as a live stream). We don't
        // actually have media to scrub; we just need iOS to think there
        // are seekable bounds with the playhead in the middle so both
        // chevrons enable — we repurpose them as shrink/grow controls
        // in `skipByInterval`.
        //
        // The window must follow the actual playhead: our PTSs come from
        // `CMClockGetHostTimeClock`, so anchor the window on that same
        // clock. A static [-1800, +1800] would put the (huge, host-time)
        // playhead past the end, which is why forward-skip was disabled.
        let now = CMClockGetTime(CMClockGetHostTimeClock())
        let halfWindow = CMTime(seconds: 1800, preferredTimescale: 1)
        return CMTimeRange(
            start: now - halfWindow,
            duration: halfWindow + halfWindow
        )
    }

    nonisolated func pictureInPictureControllerIsPlaybackPaused(
        _ controller: AVPictureInPictureController
    ) -> Bool {
        false
    }

    nonisolated func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        didTransitionToRenderSize newRenderSize: CMVideoDimensions
    ) {}

    nonisolated func pictureInPictureController(
        _ controller: AVPictureInPictureController,
        skipByInterval skipInterval: CMTime,
        completion completionHandler: @escaping () -> Void
    ) {
        // Fire the completion synchronously so iOS doesn't hold the PiP
        // control bar in a "skipping…" state while we do our resize work.
        completionHandler()
        // We don't care about the interval magnitude (iOS picks it for
        // hypothetical video content); just the sign — grow vs shrink.
        let delta = skipInterval.seconds >= 0 ? 1 : -1
        Task { @MainActor in
            self.adjustHeight(byTaps: delta)
        }
    }
}

import AVFoundation
import SwiftUI
import UIKit

/// Handed back to the discovery flow once `connectRemoteOverAlleycat` has
/// brought up both the QUIC tunnel AND the loopback Codex WebSocket — i.e.
/// the server is fully connected and just needs to be persisted +
/// navigated to. The Rust ServerSession owns the alleycat session lifetime
/// from this point on.
struct AlleycatConnectedTarget: Equatable {
    let serverId: String
    let connectedHost: String
    let displayName: String
    let params: AppAlleycatParams
}

struct AlleycatAddServerSheet: View {
    let appModel: AppModel
    let startScanningOnAppear: Bool
    let onConnected: (AlleycatConnectedTarget) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var hostOverride: String = ""
    @State private var showHostOverride = false
    @State private var displayName: String = ""
    @State private var parsedParams: AppAlleycatParams?
    @State private var parsedFromRaw: String?
    @State private var parseError: String?
    @State private var isConnecting = false
    @State private var connectError: String?
    @State private var showScanner = false
    @State private var didRequestInitialScan = false
    @State private var cameraDenied = false
    #if DEBUG
    @State private var pasteJSON: String = ""
    @State private var showPaste: Bool = false
    #endif

    private let alleycat = RustAlleycatBridge.shared

    init(
        appModel: AppModel,
        startScanningOnAppear: Bool = false,
        onConnected: @escaping (AlleycatConnectedTarget) -> Void
    ) {
        self.appModel = appModel
        self.startScanningOnAppear = startScanningOnAppear
        self.onConnected = onConnected
    }

    var body: some View {
        NavigationStack {
            ZStack {
                LitterTheme.backgroundGradient.ignoresSafeArea()
                Form {
                    pairingSection
                    if let params = parsedParams {
                        previewSection(params: params)
                        hostOverrideSection(params: params)
                    }
                    if let parseError {
                        errorSection(parseError, color: LitterTheme.warning)
                    }
                    connectSection
                    if let connectError {
                        errorSection(connectError, color: LitterTheme.danger)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("Add via Alleycat")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(LitterTheme.accent)
                }
            }
        }
        .onAppear {
            requestInitialScanIfNeeded()
        }
        .fullScreenCover(isPresented: $showScanner) {
            QRCaptureSheet(
                onScan: { scanned in
                    showScanner = false
                    handleScannedPayload(scanned)
                },
                onCancel: {
                    showScanner = false
                    if startScanningOnAppear, parsedParams == nil {
                        dismiss()
                    }
                },
                onPermissionDenied: {
                    showScanner = false
                    cameraDenied = true
                }
            )
            .ignoresSafeArea()
        }
        .alert(
            "Camera Access Needed",
            isPresented: $cameraDenied,
            actions: {
                Button("Open Settings") { openAppSettings() }
                Button("Cancel", role: .cancel) {}
            },
            message: {
                Text("Allow camera access in Settings to scan an alleycat pairing QR code.")
            }
        )
    }

    private func requestInitialScanIfNeeded() {
        guard startScanningOnAppear, !didRequestInitialScan, parsedParams == nil else { return }
        didRequestInitialScan = true
        Task { @MainActor in
            await Task.yield()
            requestCameraAndScan()
        }
    }

    private var pairingSection: some View {
        Section {
            Button {
                requestCameraAndScan()
            } label: {
                HStack {
                    Image(systemName: "qrcode.viewfinder")
                        .foregroundColor(LitterTheme.accent)
                    Text(parsedParams == nil ? "Scan Pairing QR" : "Rescan QR")
                        .litterFont(.subheadline)
                        .foregroundColor(LitterTheme.accent)
                }
            }

            #if DEBUG
            DisclosureGroup(
                isExpanded: $showPaste,
                content: {
                    TextEditor(text: $pasteJSON)
                        .litterFont(.caption)
                        .foregroundColor(LitterTheme.textPrimary)
                        .scrollContentBackground(.hidden)
                        .frame(minHeight: 90)
                        .overlay(alignment: .topLeading) {
                            if pasteJSON.isEmpty {
                                Text(#"{"protocolVersion":1,"udpPort":...,"certFingerprint":"...","token":"..."}"#)
                                    .litterFont(.caption)
                                    .foregroundColor(LitterTheme.textMuted)
                                    .padding(.top, 8)
                                    .padding(.leading, 4)
                                    .allowsHitTesting(false)
                            }
                        }
                    Button("Parse JSON") {
                        handleScannedPayload(pasteJSON)
                    }
                    .litterFont(.footnote)
                    .foregroundColor(LitterTheme.accent)
                    .disabled(pasteJSON.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                },
                label: {
                    Text("Paste JSON (debug)")
                        .litterFont(.footnote)
                        .foregroundColor(LitterTheme.textSecondary)
                }
            )
            #endif
        } header: {
            Text("Pairing")
                .foregroundColor(LitterTheme.textSecondary)
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    private func previewSection(params: AppAlleycatParams) -> some View {
        Section {
            previewRow(label: "udp port", value: String(params.udpPort))
            previewRow(label: "protocol", value: "v\(params.protocolVersion)")
            previewRow(label: "fingerprint", value: shortFingerprint(params.certFingerprint))
            if !params.hostCandidates.isEmpty {
                previewRow(
                    label: "hosts",
                    value: params.hostCandidates.joined(separator: ", ")
                )
            }
            TextField("display name (optional)", text: $displayName)
                .litterFont(.caption)
                .foregroundColor(LitterTheme.textPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
        } header: {
            Text("Scanned Params")
                .foregroundColor(LitterTheme.textSecondary)
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    @ViewBuilder
    private func hostOverrideSection(params: AppAlleycatParams) -> some View {
        let hasCandidates = !params.hostCandidates.isEmpty
        Section {
            if hasCandidates {
                DisclosureGroup(isExpanded: $showHostOverride) {
                    TextField("hostname or IP that this device can reach", text: $hostOverride)
                        .litterFont(.caption)
                        .foregroundColor(LitterTheme.textPrimary)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .keyboardType(.URL)
                } label: {
                    Text("Override host (optional)")
                        .litterFont(.footnote)
                        .foregroundColor(LitterTheme.textSecondary)
                }
            } else {
                TextField("relay.example.com or 100.64.0.5", text: $hostOverride)
                    .litterFont(.footnote)
                    .foregroundColor(LitterTheme.textPrimary)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .keyboardType(.URL)
            }
        } header: {
            Text(hasCandidates ? "Connect" : "Relay Host")
                .foregroundColor(LitterTheme.textSecondary)
        } footer: {
            if hasCandidates {
                Text("The phone races the candidates above and uses the first that connects. Override only if none work (e.g., the relay's auto-detected hostname isn't reachable from here).")
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.textMuted)
            } else {
                Text("This QR doesn't carry host candidates — enter a hostname or IP that this device can reach.")
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.textMuted)
            }
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    private func previewRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .litterFont(.caption)
                .foregroundColor(LitterTheme.textSecondary)
            Spacer()
            Text(value)
                .litterFont(.caption)
                .foregroundColor(LitterTheme.textPrimary)
        }
    }

    private var connectSection: some View {
        Section {
            Button {
                connect()
            } label: {
                HStack {
                    if isConnecting {
                        ProgressView().tint(LitterTheme.accent)
                    }
                    Text("Connect")
                        .foregroundColor(LitterTheme.accent)
                        .litterFont(.subheadline)
                }
            }
            .disabled(!canConnect)
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    private func errorSection(_ message: String, color: Color) -> some View {
        Section {
            Text(message)
                .litterFont(.caption)
                .foregroundColor(color)
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    private var canConnect: Bool {
        guard !isConnecting, let params = parsedParams else { return false }
        let hasOverride = !hostOverride.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        return hasOverride || !params.hostCandidates.isEmpty
    }

    private func handleScannedPayload(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        do {
            let params = try alleycat.parsePairPayload(json: trimmed)
            parsedParams = params
            parsedFromRaw = trimmed
            parseError = nil
            connectError = nil
            // Auto-expand the override row only when the QR didn't carry
            // candidates — otherwise keep the disclosure collapsed since the
            // common case is "just tap Connect."
            showHostOverride = params.hostCandidates.isEmpty
        } catch {
            parsedParams = nil
            parsedFromRaw = nil
            parseError = error.localizedDescription
        }
    }

    private func connect() {
        guard let params = parsedParams else { return }
        let override = hostOverride.trimmingCharacters(in: .whitespacesAndNewlines)
        var hosts: [String] = []
        if !override.isEmpty { hosts.append(override) }
        for candidate in params.hostCandidates where !hosts.contains(candidate) {
            hosts.append(candidate)
        }
        guard !hosts.isEmpty else { return }

        let trimmedDisplay = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let provisionalName = trimmedDisplay.isEmpty ? "alleycat" : trimmedDisplay
        let serverId = "alleycat:\(hosts[0].lowercased()):\(params.udpPort)"

        isConnecting = true
        connectError = nil

        Task {
            do {
                let result = try await appModel.serverBridge.connectRemoteOverAlleycat(
                    serverId: serverId,
                    displayName: provisionalName,
                    hosts: hosts,
                    params: params
                )
                let connectedHost = result.connectedHost
                let connectedServerId = result.serverId
                let resolvedName = trimmedDisplay.isEmpty
                    ? "\(connectedHost) (alleycat)"
                    : trimmedDisplay

                do {
                    try AlleycatCredentialStore.shared.save(
                        SavedAlleycatParams(params),
                        host: connectedHost
                    )
                } catch {
                    NSLog("[ALLEYCAT_CREDENTIALS] keychain save failed: %@", error.localizedDescription)
                }

                isConnecting = false
                onConnected(
                    AlleycatConnectedTarget(
                        serverId: connectedServerId,
                        connectedHost: connectedHost,
                        displayName: resolvedName,
                        params: params
                    )
                )
            } catch {
                isConnecting = false
                connectError = error.localizedDescription
            }
        }
    }

    private func requestCameraAndScan() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        switch status {
        case .authorized:
            showScanner = true
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                Task { @MainActor in
                    if granted {
                        showScanner = true
                    } else {
                        cameraDenied = true
                    }
                }
            }
        case .denied, .restricted:
            cameraDenied = true
        @unknown default:
            cameraDenied = true
        }
    }

    private func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func shortFingerprint(_ raw: String) -> String {
        let stripped = raw.replacingOccurrences(of: ":", with: "")
        if stripped.count <= 12 { return stripped }
        let prefix = stripped.prefix(12)
        return "\(prefix)..."
    }
}

// MARK: - QR Scanner

private struct QRCaptureSheet: UIViewControllerRepresentable {
    let onScan: (String) -> Void
    let onCancel: () -> Void
    let onPermissionDenied: () -> Void

    func makeUIViewController(context: Context) -> QRScannerViewController {
        let controller = QRScannerViewController()
        controller.onScan = onScan
        controller.onCancel = onCancel
        controller.onPermissionDenied = onPermissionDenied
        return controller
    }

    func updateUIViewController(_ uiViewController: QRScannerViewController, context: Context) {}
}

private final class QRScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onScan: ((String) -> Void)?
    var onCancel: (() -> Void)?
    var onPermissionDenied: (() -> Void)?

    private let captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let metadataQueue = DispatchQueue(label: "com.litter.alleycat.qrscanner")
    private var didReportScan = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configureSession()
        addOverlay()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        guard !captureSession.isRunning else { return }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession.startRunning()
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession.isRunning {
            captureSession.stopRunning()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
    }

    private func configureSession() {
        guard let device = AVCaptureDevice.default(for: .video) else {
            onPermissionDenied?()
            return
        }
        guard let input = try? AVCaptureDeviceInput(device: device) else {
            onPermissionDenied?()
            return
        }
        if captureSession.canAddInput(input) {
            captureSession.addInput(input)
        } else {
            onPermissionDenied?()
            return
        }

        let output = AVCaptureMetadataOutput()
        if captureSession.canAddOutput(output) {
            captureSession.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: metadataQueue)
            if output.availableMetadataObjectTypes.contains(.qr) {
                output.metadataObjectTypes = [.qr]
            }
        } else {
            onPermissionDenied?()
            return
        }

        let preview = AVCaptureVideoPreviewLayer(session: captureSession)
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)
        previewLayer = preview
    }

    private func addOverlay() {
        let cancelButton = UIButton(type: .system)
        cancelButton.setTitle("Cancel", for: .normal)
        cancelButton.tintColor = .white
        cancelButton.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        cancelButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)

        let hint = UILabel()
        hint.text = "Point the camera at the alleycat QR code"
        hint.textColor = .white.withAlphaComponent(0.85)
        hint.font = .systemFont(ofSize: 13, weight: .medium)
        hint.numberOfLines = 0
        hint.textAlignment = .center
        hint.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(cancelButton)
        view.addSubview(hint)
        NSLayoutConstraint.activate([
            cancelButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            cancelButton.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -16),
            hint.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -24),
            hint.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            hint.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24)
        ])
    }

    @objc private func cancelTapped() {
        onCancel?()
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !didReportScan else { return }
        guard let payload = metadataObjects
            .compactMap({ $0 as? AVMetadataMachineReadableCodeObject })
            .first(where: { $0.type == .qr })?
            .stringValue
        else { return }
        didReportScan = true
        DispatchQueue.main.async { [weak self] in
            self?.captureSession.stopRunning()
            self?.onScan?(payload)
        }
    }
}

import AVFoundation
import SwiftUI
import UIKit

struct AlleycatConnectedTarget: Equatable {
    let serverId: String
    let nodeId: String
    let displayName: String
    let params: AppAlleycatPairPayload
    let agentName: String
    let agentWire: AppAlleycatAgentWire
}

/// Normalizes pasted/scanned pairing payload text before it crosses into Rust.
enum PairPayloadInput {
    static func normalized(_ raw: String) -> String {
        let text = trimmedWithoutBom(raw)
        let fixed = fixMisinterpretedUTF16(text)
        return extractedJsonObject(from: strippedMarkdownFence(fixed))
    }

    private static func trimmedWithoutBom(_ raw: String) -> String {
        var text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.unicodeScalars.first?.value == 0xfeff {
            text.removeFirst()
        }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Detects and fixes UTF-16 LE bytes misinterpreted as UTF-16 BE characters.
    /// This happens when clipboard data contains UTF-16 LE encoded text that was
    /// incorrectly read as UTF-16 BE, causing ASCII characters like '{' (0x7B 0x00)
    /// to display as CJK characters like '笀' (U+7B00).
    private static func fixMisinterpretedUTF16(_ text: String) -> String {
        // Quick heuristic: if the text starts with a CJK character but should be JSON,
        // it's likely misinterpreted UTF-16
        guard let firstChar = text.unicodeScalars.first else { return text }

        // Check if first character looks like misinterpreted UTF-16 LE
        // '{' (0x7B) becomes 笀 (U+7B00) when 0x7B 0x00 is read as big-endian
        let codePoint = firstChar.value

        // If high byte is ASCII-like (0x20-0x7E) and low byte is 0x00,
        // this is likely misinterpreted UTF-16 LE
        let highByte = (codePoint >> 8) & 0xFF
        let lowByte = codePoint & 0xFF

        guard lowByte == 0x00, (0x20...0x7E).contains(highByte) else {
            return text
        }

        // Reconstruct original bytes by reversing the misinterpretation
        var utf16LEBytes = Data()
        for scalar in text.unicodeScalars {
            let cp = scalar.value
            let high = UInt8((cp >> 8) & 0xFF)
            let low = UInt8(cp & 0xFF)
            utf16LEBytes.append(high)
            utf16LEBytes.append(low)
        }

        // Decode as UTF-16 LE
        if let fixed = String(data: utf16LEBytes, encoding: .utf16LittleEndian) {
            return fixed
        }

        return text
    }

    private static func strippedMarkdownFence(_ text: String) -> String {
        let lines = text.components(separatedBy: .newlines)
        guard lines.count >= 3,
              isOpeningFence(lines[0]),
              isClosingFence(lines[lines.count - 1]) else {
            return text
        }
        return lines
            .dropFirst()
            .dropLast()
            .joined(separator: "\n")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func isOpeningFence(_ line: String) -> Bool {
        let normalized = line.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return normalized == "```" || normalized == "```json"
    }

    private static func isClosingFence(_ line: String) -> Bool {
        line.trimmingCharacters(in: .whitespacesAndNewlines) == "```"
    }

    private static func extractedJsonObject(from text: String) -> String {
        guard let start = text.firstIndex(of: "{") else { return text }
        var depth = 0
        var inString = false
        var isEscaped = false
        var index = start
        while index < text.endIndex {
            let char = text[index]
            if inString {
                if isEscaped {
                    isEscaped = false
                } else if char == "\\" {
                    isEscaped = true
                } else if char == "\"" {
                    inString = false
                }
            } else if char == "\"" {
                inString = true
            } else if char == "{" {
                depth += 1
            } else if char == "}" {
                depth -= 1
                if depth == 0 {
                    return String(text[start...index])
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }
            index = text.index(after: index)
        }
        return String(text[start...]).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

struct AlleycatAddServerSheet: View {
    let appModel: AppModel
    let startScanningOnAppear: Bool
    let onConnected: (AlleycatConnectedTarget) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var displayName: String = ""
    @State private var parsedParams: AppAlleycatPairPayload?
    @State private var agents: [AppAlleycatAgentInfo] = []
    @State private var selectedAgentNames: Set<String> = []
    @State private var isLoadingAgents = false
    @State private var parseError: String?
    @State private var agentError: String?
    @State private var isConnecting = false
    @State private var connectError: String?
    @State private var showScanner = false
    @State private var didRequestInitialScan = false
    @State private var cameraDenied = false
    // pasteJSON / showPaste are used by the Mac paste-JSON UI
    // (Catalyst + iOS-on-Mac) and the iOS QR fallback.
    @State private var pasteJSON: String = ""
    @State private var showPaste: Bool = false

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
                        agentSection
                    }
                    if let parseError {
                        errorSection(parseError, color: LitterTheme.warning)
                    }
                    if let agentError {
                        errorSection(agentError, color: LitterTheme.warning)
                    }
                    connectSection
                    if let connectError {
                        errorSection(connectError, color: LitterTheme.danger)
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("添加设备")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                        .foregroundColor(LitterTheme.accent)
                }
            }
        }
        .onAppear {
            requestInitialScanIfNeeded()
        }
        // QR scanner cover + camera-denied alert are applied
        // unconditionally; on Mac builds (Catalyst + iOS-on-Mac) the
        // pairing section never triggers `requestCameraAndScan`, so
        // neither presentation ever fires.
        .fullScreenCover(isPresented: $showScanner) {
            QRScannerScreen(
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
        }
        .alert(
            "需要相机权限",
            isPresented: $cameraDenied,
            actions: {
                Button("打开设置") { openAppSettings() }
                Button("取消", role: .cancel) {}
            },
            message: {
                Text("请在系统设置中允许相机权限，用于扫描 NeCode Mobile 配对二维码。")
            }
        )
    }

    private func requestInitialScanIfNeeded() {
        guard !LitterPlatform.rendersAsMacApp else { return }
        guard startScanningOnAppear, !didRequestInitialScan, parsedParams == nil else { return }
        didRequestInitialScan = true
        Task { @MainActor in
            await Task.yield()
            requestCameraAndScan()
        }
    }

    private var pairingSection: some View {
        Section {
            // Mac (Catalyst + iOS-on-Mac) shows paste-JSON only; iOS shows
            // QR scanning first, with paste available as a production fallback
            // for users who already copied the pairing payload.
            if LitterPlatform.rendersAsMacApp {
                pasteJSONPairingControls
            } else {
                qrPairingControls
            }
        } header: {
            Text("配对")
                .foregroundColor(LitterTheme.textSecondary)
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    @ViewBuilder
    private var pasteJSONPairingControls: some View {
        Text("在电脑端运行 \(Self.pairCommandLabel)，然后粘贴生成的配对 JSON。")
            .litterFont(.caption)
            .foregroundColor(LitterTheme.textSecondary)
            .fixedSize(horizontal: false, vertical: true)

        pasteJSONEntryControls(minHeight: 110)
    }

    @ViewBuilder
    private var qrPairingControls: some View {
        Button {
            requestCameraAndScan()
        } label: {
            HStack {
                Image(systemName: "qrcode.viewfinder")
                    .foregroundColor(LitterTheme.accent)
                Text(parsedParams == nil ? "扫描配对二维码" : "重新扫描二维码")
                    .litterFont(.subheadline)
                    .foregroundColor(LitterTheme.accent)
            }
        }

        DisclosureGroup(
            isExpanded: $showPaste,
            content: {
                pasteJSONEntryControls(minHeight: 90)
            },
            label: {
                Text("粘贴配对 JSON")
                    .litterFont(.footnote)
                    .foregroundColor(LitterTheme.textSecondary)
            }
        )
    }

    @ViewBuilder
    private func pasteJSONEntryControls(minHeight: CGFloat) -> some View {
        TextEditor(text: $pasteJSON)
            .litterFont(.caption)
            .foregroundColor(LitterTheme.textPrimary)
            .scrollContentBackground(.hidden)
            .frame(minHeight: minHeight)
            .overlay(alignment: .topLeading) {
                if pasteJSON.isEmpty {
                    Text(#"{"v":1,"node_id":"...","token":"...","relay":"https://..."}"#)
                        .litterFont(.caption)
                        .foregroundColor(LitterTheme.textMuted)
                        .padding(.top, 8)
                        .padding(.leading, 4)
                        .allowsHitTesting(false)
                }
            }

        HStack {
            Button("从剪贴板粘贴") {
                if let clipboard = UIPasteboard.general.string {
                    pasteJSON = PairPayloadInput.normalized(clipboard)
                }
            }
            .litterFont(.footnote)
            .foregroundColor(LitterTheme.accent)

            Spacer()

            Button(parsedParams == nil ? "解析 JSON" : "重新解析 JSON") {
                handleScannedPayload(pasteJSON)
            }
            .litterFont(.footnote)
            .foregroundColor(LitterTheme.accent)
            .disabled(PairPayloadInput.normalized(pasteJSON).isEmpty)
        }
    }

    private static let pairCommandLabel = "necode mobile"

    private func previewSection(params: AppAlleycatPairPayload) -> some View {
        Section {
            previewRow(label: "节点", value: shortNodeId(params.nodeId))
            previewRow(label: "协议", value: "v\(params.v)")
            if let relay = params.relay, !relay.isEmpty {
                previewRow(label: "中继", value: relay)
            }
            if let hostName = params.hostName, !hostName.isEmpty {
                previewRow(label: "主机", value: hostName)
            }
            TextField("显示名称（可选）", text: $displayName)
                .litterFont(.caption)
                .foregroundColor(LitterTheme.textPrimary)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
        } header: {
            Text("已扫描设备")
                .foregroundColor(LitterTheme.textSecondary)
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    private var agentSection: some View {
        Section {
            if isLoadingAgents {
                HStack {
                    ProgressView().tint(LitterTheme.accent)
                    Text("正在加载 Agent")
                        .litterFont(.caption)
                        .foregroundColor(LitterTheme.textSecondary)
                }
            } else if agents.isEmpty {
                Text("这台设备上暂无可用 Agent。")
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.textMuted)
            } else {
                ForEach(agents, id: \.name) { agent in
                    Button {
                        guard agent.available else { return }
                        toggleAgentSelection(agent)
                    } label: {
                        HStack(spacing: 10) {
                            AgentIconView(kind: agent.name.lowercased(), size: 22)
                                .opacity(agent.available ? 1 : 0.45)
                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(agent.displayName)
                                        .litterFont(.subheadline)
                                        .foregroundColor(agent.available ? LitterTheme.textPrimary : LitterTheme.textMuted)
                                    if AgentRuntimeKind.isBetaAgentName(agent.name, displayName: agent.displayName) {
                                        BetaBadge()
                                    }
                                }
                                Text(wireLabel(agent.wire))
                                    .litterFont(.caption)
                                    .foregroundColor(LitterTheme.textSecondary)
                            }
                            Spacer()
                            if selectedAgentNames.contains(agent.name) {
                                Image(systemName: "checkmark.square.fill")
                                    .foregroundColor(LitterTheme.accent)
                            } else if !agent.available {
                                Text("不可用")
                                    .litterFont(.caption)
                                    .foregroundColor(LitterTheme.textMuted)
                            } else {
                                Image(systemName: "square")
                                    .foregroundColor(LitterTheme.textMuted)
                            }
                        }
                    }
                    .disabled(!agent.available)
                }
            }
        } header: {
            HStack {
                        Text("Agent")
                Spacer()
                if !availableAgents.isEmpty {
                    Button(selectedAgents.count == availableAgents.count ? "全不选" : "全选") {
                        if selectedAgents.count == availableAgents.count {
                            selectedAgentNames = []
                        } else {
                            selectedAgentNames = Set(availableAgents.map(\.name))
                        }
                    }
                    .font(.caption)
                    .foregroundColor(LitterTheme.accent)
                }
            }
                .foregroundColor(LitterTheme.textSecondary)
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
                .lineLimit(1)
                .truncationMode(.middle)
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
                    Text("连接")
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

    private var availableAgents: [AppAlleycatAgentInfo] {
        agents.filter(\.available)
    }

    private var selectedAgents: [AppAlleycatAgentInfo] {
        agents.filter { $0.available && selectedAgentNames.contains($0.name) }
    }

    private var canConnect: Bool {
        !isConnecting && !isLoadingAgents && parsedParams != nil && !selectedAgents.isEmpty
    }

    private func toggleAgentSelection(_ agent: AppAlleycatAgentInfo) {
        if selectedAgentNames.contains(agent.name) {
            selectedAgentNames.remove(agent.name)
        } else {
            selectedAgentNames.insert(agent.name)
        }
    }

    private func handleScannedPayload(_ raw: String) {
        let trimmed = PairPayloadInput.normalized(raw)
        guard !trimmed.isEmpty else { return }
        guard trimmed.first == "{" else {
            parsedParams = nil
            agents = []
            selectedAgentNames = []
            parseError = "未找到配对 JSON。请复制以 { 开头的配对 JSON，或直接扫描二维码。"
            return
        }
        do {
            let params = try alleycat.parsePairPayload(json: trimmed)
            parsedParams = params
            displayName = suggestedDisplayName(for: params)
            parseError = nil
            connectError = nil
            agentError = nil
            agents = []
            selectedAgentNames = []
            loadAgents(params: params)
        } catch {
            parsedParams = nil
            agents = []
            selectedAgentNames = []
            parseError = error.localizedDescription
        }
    }

    private func loadAgents(params: AppAlleycatPairPayload) {
        isLoadingAgents = true
        Task {
            do {
                let loaded = try await appModel.serverBridge.listAlleycatAgents(params: params)
                let sorted = sortedAgentsForNeCode(loaded)
                await MainActor.run {
                    guard parsedParams?.nodeId == params.nodeId else { return }
                    agents = sorted
                    selectedAgentNames = Set(
                        sorted
                            .filter { $0.available && !AgentRuntimeKind.isBetaAgentName($0.name, displayName: $0.displayName) }
                            .map(\.name)
                    )
                    isLoadingAgents = false
                    agentError = nil
                }
            } catch {
                await MainActor.run {
                    guard parsedParams?.nodeId == params.nodeId else { return }
                    agents = []
                    selectedAgentNames = []
                    isLoadingAgents = false
                    agentError = error.localizedDescription
                }
            }
        }
    }

    private func connect() {
        guard let params = parsedParams, let fallbackAgent = selectedAgents.first else { return }
        let trimmedDisplay = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedName = trimmedDisplay.isEmpty ? suggestedDisplayName(for: params) : trimmedDisplay
        let selectedNames = selectedAgents.map(\.name)
        let serverId = "alleycat:\(params.nodeId)"

        isConnecting = true
        connectError = nil

        Task {
            do {
                let result = try await appModel.serverBridge.connectRemoteOverAlleycat(
                    serverId: serverId,
                    displayName: resolvedName,
                    params: params,
                    agentName: fallbackAgent.name,
                    selectedAgentNames: selectedNames,
                    wire: fallbackAgent.wire
                )
                do {
                    try AlleycatCredentialStore.shared.saveToken(params.token, nodeId: params.nodeId)
                } catch {
                    NSLog("[ALLEYCAT_CREDENTIALS] keychain save failed: %@", error.localizedDescription)
                }
                // First successful alleycat pair triggers the iroh
                // endpoint bind. Persist the freshly-generated device
                // secret key so the next cold launch reuses the same
                // `EndpointId`.
                await MainActor.run {
                    AppRuntimeController.shared.persistAlleycatSecretKeyIfNeeded()
                }

                await MainActor.run {
                    isConnecting = false
                    onConnected(
                        AlleycatConnectedTarget(
                            serverId: result.serverId,
                            nodeId: result.nodeId,
                            displayName: resolvedName,
                            params: params,
                            agentName: result.agentName,
                            agentWire: fallbackAgent.wire
                        )
                    )
                }
            } catch {
                await MainActor.run {
                    isConnecting = false
                    connectError = error.localizedDescription
                }
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

    private func suggestedDisplayName(for params: AppAlleycatPairPayload) -> String {
        let hostName = params.hostName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !hostName.isEmpty {
            return hostName
        }
        return "NeCode \(shortNodeId(params.nodeId))"
    }

    private func sortedAgentsForNeCode(_ agents: [AppAlleycatAgentInfo]) -> [AppAlleycatAgentInfo] {
        agents.sorted { lhs, rhs in
            let lhsPriority = agentPriority(lhs)
            let rhsPriority = agentPriority(rhs)
            if lhsPriority != rhsPriority { return lhsPriority < rhsPriority }
            return lhs.displayName.localizedCaseInsensitiveCompare(rhs.displayName) == .orderedAscending
        }
    }

    private func agentPriority(_ agent: AppAlleycatAgentInfo) -> Int {
        let name = agent.name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if name == "necode" { return 0 }
        if !agent.available { return 3 }
        return AgentRuntimeKind.isBetaAgentName(agent.name, displayName: agent.displayName) ? 2 : 1
    }

    private func shortNodeId(_ raw: String) -> String {
        if raw.count <= 16 { return raw }
        return "\(raw.prefix(8))...\(raw.suffix(8))"
    }

    private func wireLabel(_ wire: AppAlleycatAgentWire) -> String {
        switch wire {
        case .websocket:
            return "websocket"
        case .jsonl:
            return "jsonl"
        }
    }

}

// MARK: - QR Scanner

private struct QRScannerScreen: View {
    let onScan: (String) -> Void
    let onCancel: () -> Void
    let onPermissionDenied: () -> Void

    private static let pairCommand = "necode mobile"

    @State private var copied = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            QRCaptureSheet(
                onScan: onScan,
                onCancel: onCancel,
                onPermissionDenied: onPermissionDenied
            )
            .ignoresSafeArea()

            LinearGradient(
                colors: [Color.black.opacity(0.55), Color.black.opacity(0.0)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 320)
            .frame(maxHeight: .infinity, alignment: .top)
            .ignoresSafeArea()
            .allowsHitTesting(false)

            VStack(spacing: 16) {
                topBar
                instructionsCard
                Spacer()
                framingHint
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 24)
        }
    }

    private var topBar: some View {
        HStack {
            Spacer()
            Button(action: onCancel) {
                Text("取消")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(.black.opacity(0.45), in: Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("alleycat.scanner.cancelButton")
        }
    }

    private var instructionsCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("扫码连接 NeCode")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)

            stepRow(number: "1", title: "在电脑端运行 NeCode mobile，并生成配对二维码。")
            commandRow
            stepRow(number: "2", title: "用手机摄像头对准这个二维码。")
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color.black.opacity(0.55))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(.white.opacity(0.12), lineWidth: 0.8)
        )
    }

    private func stepRow(number: String, title: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(number)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.black)
                .frame(width: 20, height: 20)
                .background(LitterTheme.accent, in: Circle())
            Text(title)
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.92))
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var commandRow: some View {
        HStack(spacing: 10) {
            Text(Self.pairCommand)
                .font(.system(size: 14, weight: .semibold, design: .monospaced))
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(.white.opacity(0.12))
                )
            Button(action: copyCommand) {
                Image(systemName: copied ? "checkmark" : "doc.on.doc")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 36, height: 36)
                    .background(.white.opacity(0.14), in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("alleycat.scanner.copyCommandButton")
        }
        .padding(.leading, 30)
    }

    private var framingHint: some View {
        Text("保持稳定，二维码会自动识别。")
            .font(.system(size: 12))
            .foregroundColor(.white.opacity(0.75))
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background(.black.opacity(0.4), in: Capsule())
    }

    private func copyCommand() {
        UIPasteboard.general.string = Self.pairCommand
        withAnimation(.easeOut(duration: 0.15)) { copied = true }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.4))
            withAnimation(.easeOut(duration: 0.15)) { copied = false }
        }
    }
}

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
    private let metadataQueue = DispatchQueue(label: "com.alleycat.qrscanner")
    private var didReportScan = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configureSession()
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

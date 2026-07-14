import SwiftUI

struct SettingsView: View {
    @Environment(AppModel.self) private var appModel
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @Environment(\.textScale) private var textScale
    @AppStorage("fontFamily") private var fontFamily = FontFamilyOption.mono.rawValue
    @AppStorage("collapseTurns") private var collapseTurns = false
    @AppStorage(ConversationDisplayPreferenceKey.reasoning) private var reasoningDisplayMode = ConversationDetailDisplayMode.collapsed.rawValue
    @AppStorage(ConversationDisplayPreferenceKey.commands) private var commandDisplayMode = ConversationDetailDisplayMode.collapsed.rawValue
    @AppStorage(ConversationDisplayPreferenceKey.tools) private var toolDisplayMode = ConversationDetailDisplayMode.collapsed.rawValue
    @State private var activeServerSheet: SettingsServerSheet?
    @State private var serverEditError: String?
    var onServerRemoved: (String) -> Void = { _ in }

    private var localServer: AppServerSnapshot? {
        // Account management (ChatGPT login / API key) is local-only, always.
        // If the local Codex bridge hasn't spun up there's no login target, and
        // the caller falls through to `SettingsDisconnectedAccountSection`.
        appModel.snapshot?.servers.first(where: \.isLocal)
    }

    private var connectedServers: [HomeDashboardServer] {
        HomeDashboardSupport.sortedConnectedServers(
            from: appModel.snapshot?.servers ?? [],
            savedServers: SavedServerStore.rememberedServers(),
            activeServerId: appModel.snapshot?.activeThread?.serverId
        )
    }

    var body: some View {
        NavigationStack {
            ZStack {
                LitterTheme.backgroundGradient.ignoresSafeArea()
                Form {
                    appearanceSection
                    fontSection
                    conversationSection
                    serversSection
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                        .foregroundColor(LitterTheme.accent)
                }
            }
            .sheet(item: $activeServerSheet) { sheet in
                switch sheet {
                case .add:
                    NavigationStack {
                        DiscoveryView(onServerSelected: { _ in
                            activeServerSheet = nil
                        })
                    }
                    .environment(appModel)
                    .environment(appState)
                    .environment(\.textScale, textScale)
                case .edit(let server):
                    SettingsServerConnectionEditor(
                        server: server,
                        onSave: { configuration in
                            saveServerConfiguration(configuration, reconnect: false)
                            activeServerSheet = nil
                        },
                        onReconnect: { configuration in
                            activeServerSheet = nil
                            saveServerConfiguration(configuration, reconnect: true)
                        }
                    )
                    .environment(\.textScale, textScale)
                case .sshReconnect(let server):
                    SSHLoginSheet(server: server) { target in
                        activeServerSheet = nil
                        if case .sshThenRemote(let host, let credentials) = target {
                            Task { await reconnectViaSSH(server: server, host: host, credentials: credentials) }
                        }
                    }
                }
            }
            .alert("主机更新失败", isPresented: Binding(
                get: { serverEditError != nil },
                set: { if !$0 { serverEditError = nil } }
            )) {
                Button("好") { serverEditError = nil }
            } message: {
                Text(serverEditError ?? "无法更新这台主机。")
            }
        }
    }

    // MARK: - Appearance Section

    private var appearanceSection: some View {
        Section {
            NavigationLink {
                AppearanceSettingsView()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "paintbrush")
                        .foregroundColor(LitterTheme.accent)
                        .frame(width: 20)
                    Text("外观")
                        .litterFont(.subheadline)
                        .foregroundColor(LitterTheme.textPrimary)
                }
            }
            .listRowBackground(LitterTheme.surface.opacity(0.6))
        } header: {
            Text("主题")
                .foregroundColor(LitterTheme.textSecondary)
        }
    }

    // MARK: - Conversation Section

    private var conversationSection: some View {
        Section {
            Toggle(isOn: $collapseTurns) {
                HStack(spacing: 10) {
                    Image(systemName: "rectangle.compress.vertical")
                        .foregroundColor(LitterTheme.accent)
                        .frame(width: 20)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("折叠历史轮次")
                            .litterFont(.subheadline)
                            .foregroundColor(LitterTheme.textPrimary)
                        Text("把之前的对话轮次折叠为卡片")
                            .litterFont(.caption)
                            .foregroundColor(LitterTheme.textSecondary)
                    }
                }
            }
            .tint(LitterTheme.accent)
            .listRowBackground(LitterTheme.surface.opacity(0.6))

            transcriptDisplayPicker(
                title: "思考内容",
                subtitle: "推理和分析内容块",
                systemImage: "brain.head.profile",
                selection: $reasoningDisplayMode
            )

            transcriptDisplayPicker(
                title: "命令",
                subtitle: "Shell 命令和命令输出",
                systemImage: "terminal",
                selection: $commandDisplayMode
            )

            transcriptDisplayPicker(
                title: "工具",
                subtitle: "MCP、网页、图片和文件变更卡片",
                systemImage: "wrench.and.screwdriver",
                selection: $toolDisplayMode
            )
        } header: {
            Text("会话")
                .foregroundColor(LitterTheme.textSecondary)
        }
    }

    private func transcriptDisplayPicker(
        title: String,
        subtitle: String,
        systemImage: String,
        selection: Binding<String>
    ) -> some View {
        Picker(selection: selection) {
            ForEach(ConversationDetailDisplayMode.allCases) { mode in
                Text(mode.displayName).tag(mode.rawValue)
            }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .foregroundColor(LitterTheme.accent)
                    .frame(width: 20)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .litterFont(.subheadline)
                        .foregroundColor(LitterTheme.textPrimary)
                    Text(subtitle)
                        .litterFont(.caption)
                        .foregroundColor(LitterTheme.textSecondary)
                }
            }
        }
        .pickerStyle(.menu)
        .tint(LitterTheme.accent)
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    // MARK: - Font Section

    private var fontSection: some View {
        Section {
            ForEach(FontFamilyOption.allCases) { option in
                Button {
                    fontFamily = option.rawValue
                    ThemeManager.shared.syncFontPreference()
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(option.displayName)
                                .litterFont(.subheadline)
                                .foregroundColor(LitterTheme.textPrimary)
                            Text("你好，NeCode")
                                .font(LitterFont.sampleFont(family: option, size: 14))
                                .foregroundColor(LitterTheme.textSecondary)
                        }
                        Spacer()
                        if fontFamily == option.rawValue {
                            Image(systemName: "checkmark")
                                .litterFont(.subheadline, weight: .semibold)
                                .foregroundColor(LitterTheme.accentStrong)
                        }
                    }
                }
                .listRowBackground(LitterTheme.surface.opacity(0.6))
            }
        } header: {
            Text("字体")
                .foregroundColor(LitterTheme.textSecondary)
        }
    }

    // MARK: - Experimental Section

    private var experimentalSection: some View {
        Section {
            NavigationLink {
                ExperimentalFeaturesView()
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "flask")
                        .foregroundColor(LitterTheme.accent)
                        .frame(width: 20)
                    Text("实验功能")
                        .litterFont(.subheadline)
                        .foregroundColor(LitterTheme.textPrimary)
                }
            }
            .listRowBackground(LitterTheme.surface.opacity(0.6))
        } header: {
            Text("实验")
                .foregroundColor(LitterTheme.textSecondary)
        }
    }

    // MARK: - Account Section (inline, no nested sheet)

    private var accountSection: some View {
        Group {
            if let localServer {
                SettingsConnectionAccountSection(server: localServer)
            } else {
                SettingsDisconnectedAccountSection()
            }
        }
    }

    // MARK: - Servers Section

    private var serversSection: some View {
        Section {
            if connectedServers.isEmpty {
                Text("没有已连接主机")
                    .litterFont(.footnote)
                    .foregroundColor(LitterTheme.textMuted)
                    .listRowBackground(LitterTheme.surface.opacity(0.6))
            } else {
                ForEach(connectedServers, id: \.id) { conn in
                    HStack {
                        Button {
                            activeServerSheet = .edit(conn)
                        } label: {
                            HStack {
                                Image(systemName: conn.isLocal ? "iphone" : "server.rack")
                                    .foregroundColor(LitterTheme.accent)
                                    .frame(width: 20)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(conn.displayName)
                                        .litterFont(.footnote)
                                        .foregroundColor(LitterTheme.textPrimary)
                                    Text(conn.health.displayLabel)
                                        .litterFont(.caption)
                                        .foregroundColor(conn.health.accentColor)
                                }
                                Spacer()
                            }
                        }
                        .buttonStyle(.plain)
                        Button("移除") {
                            removeServer(conn)
                        }
                        .litterFont(.caption)
                        .foregroundColor(LitterTheme.danger)
                        .buttonStyle(.borderless)
                    }
                    .listRowBackground(LitterTheme.surface.opacity(0.6))
                }
            }

            Button {
                activeServerSheet = .add
            } label: {
                HStack {
                    Image(systemName: "plus.circle.fill")
                        .foregroundColor(LitterTheme.accent)
                        .frame(width: 20)
                    Text("添加主机")
                        .litterFont(.footnote)
                        .foregroundColor(LitterTheme.accent)
                    Spacer()
                }
            }
            .listRowBackground(LitterTheme.surface.opacity(0.6))
        } header: {
            Text("主机")
                .foregroundColor(LitterTheme.textSecondary)
        }
    }

    private func removeServer(_ server: HomeDashboardServer) {
        Task {
            do {
                try await appModel.removeServer(serverId: server.id)
                onServerRemoved(server.id)
            } catch {
                serverEditError = error.localizedDescription
            }
        }
    }

    private func saveServerConfiguration(
        _ configuration: SettingsServerConnectionConfiguration,
        reconnect: Bool
    ) {
        var saved = SavedServerStore.load()
        if let index = saved.firstIndex(where: { $0.id == configuration.savedServer.id }) {
            saved[index] = configuration.savedServer
        } else {
            saved.append(configuration.savedServer)
        }
        SavedServerStore.save(saved)
        appModel.reconnectController.setMultiClankerAndQuicEnabled(enabled: true)
        appModel.reconnectController.syncSavedServers(
            servers: SavedServerStore.reconnectRecords(
                localDisplayName: appModel.resolvedLocalServerDisplayName()
            )
        )
        appModel.store.renameServer(
            serverId: configuration.savedServer.id,
            displayName: configuration.savedServer.name
        )

        guard reconnect else { return }
        reconnectServer(using: configuration)
    }

    private func reconnectServer(using configuration: SettingsServerConnectionConfiguration) {
        let server = configuration.discoveredServer

        // For SSH we keep the existing connection alive until the user actually
        // submits credentials, so a cancelled credential sheet does not leave
        // them disconnected.
        if case .ssh = configuration.connectionMode {
            activeServerSheet = .sshReconnect(server)
            return
        }

        Task {
            do {
                await SshSessionStore.shared.close(serverId: server.id, ssh: appModel.ssh)
                try await appModel.serverBridge.disconnectServer(serverId: server.id)
                switch configuration.connectionMode {
                case .local:
                    try await appModel.restartLocalServer()
                case .directCodex:
                    guard let port = server.resolvedDirectCodexPort else {
                        throw SettingsServerConnectionError.missingCodexPort
                    }
                    _ = try await appModel.serverBridge.connectRemoteServer(
                        serverId: server.id,
                        displayName: server.name,
                        host: server.hostname,
                        port: port
                    )
                    await appModel.refreshSnapshot()
                case .websocket:
                    guard let websocketURL = server.websocketURL else {
                        throw SettingsServerConnectionError.invalidWebsocketURL
                    }
                    if isSettingsSlingshotURL(websocketURL) {
                        let tokens = try await ChatGPTOAuth.loadStoredOrRefreshedTokens()
                        do {
                            _ = try await appModel.serverBridge.connectRemoteSlingshotUrlServer(
                                serverId: server.id,
                                displayName: server.name,
                                connectionUrl: websocketURL,
                                accessToken: tokens.accessToken,
                                accountId: tokens.accountID,
                                stepUpToken: ""
                            )
                        } catch {
                            guard ChatGPTOAuth.isRemoteControlAuthorizationRequired(error) else {
                                throw error
                            }
                            let stepUpToken = try await ChatGPTOAuth.remoteControlEnrollmentStepUpToken()
                            _ = try await appModel.serverBridge.connectRemoteSlingshotUrlServer(
                                serverId: server.id,
                                displayName: server.name,
                                connectionUrl: websocketURL,
                                accessToken: tokens.accessToken,
                                accountId: tokens.accountID,
                                stepUpToken: stepUpToken
                            )
                        }
                    } else {
                        _ = try await appModel.serverBridge.connectRemoteUrlServer(
                            serverId: server.id,
                            displayName: server.name,
                            websocketUrl: websocketURL
                        )
                    }
                    await appModel.refreshSnapshot()
                case .ssh:
                    break
                }
            } catch {
                serverEditError = error.localizedDescription
            }
        }
    }

    private func reconnectViaSSH(
        server: DiscoveredServer,
        host: String,
        credentials: SSHCredentials
    ) async {
        await SshSessionStore.shared.close(serverId: server.id, ssh: appModel.ssh)

        do {
            try await appModel.serverBridge.disconnectServer(serverId: server.id)
            _ = try await startRemoteOverSSH(
                serverId: server.id,
                displayName: server.name,
                host: host,
                port: server.resolvedSSHPort,
                credentials: credentials
            )
            await appModel.refreshSnapshot()
        } catch {
            serverEditError = error.localizedDescription
        }
    }

    private func startRemoteOverSSH(
        serverId: String,
        displayName: String,
        host: String,
        port: UInt16,
        credentials: SSHCredentials
    ) async throws -> String {
        switch credentials {
        case .password(let username, let password, let unlockMacosKeychain):
            return try await appModel.serverBridge.startRemoteOverSshConnect(
                serverId: serverId,
                displayName: displayName,
                host: host,
                port: port,
                username: username,
                password: password,
                privateKeyPem: nil,
                passphrase: nil,
                unlockMacosKeychain: unlockMacosKeychain,
                acceptUnknownHost: true,
                workingDir: nil
            )
        case .key(let username, let privateKey, let passphrase):
            return try await appModel.serverBridge.startRemoteOverSshConnect(
                serverId: serverId,
                displayName: displayName,
                host: host,
                port: port,
                username: username,
                password: nil,
                privateKeyPem: privateKey,
                passphrase: passphrase,
                unlockMacosKeychain: false,
                acceptUnknownHost: true,
                workingDir: nil
            )
        }
    }

}

private enum SettingsServerSheet: Identifiable {
    case add
    case edit(HomeDashboardServer)
    case sshReconnect(DiscoveredServer)

    var id: String {
        switch self {
        case .add:
            return "add"
        case .edit(let server):
            return "edit-\(server.id)"
        case .sshReconnect(let server):
            return "ssh-\(server.id)"
        }
    }
}

private enum SettingsServerConnectionMode: String, CaseIterable, Identifiable {
    case local
    case ssh
    case directCodex
    case websocket

    var id: String { rawValue }

    var label: String {
        switch self {
        case .local:
            return "本机"
        case .ssh:
            return "SSH"
        case .directCodex:
            return "NeCode"
        case .websocket:
            return "WebSocket"
        }
    }

    var formHeader: String {
        switch self {
        case .local:
            return "本机运行时"
        case .ssh:
            return "SSH 主机"
        case .directCodex:
            return "NeCode 服务"
        case .websocket:
            return "NeCode URL"
        }
    }
}

private enum SettingsServerConnectionError: LocalizedError {
    case emptyName
    case emptyHost
    case invalidCodexPort
    case missingCodexPort
    case invalidSSHPort
    case invalidWakeMAC
    case invalidWebsocketURL

    var errorDescription: String? {
        switch self {
        case .emptyName:
            return "主机名称不能为空。"
        case .emptyHost:
            return "主机地址不能为空。"
        case .invalidCodexPort, .missingCodexPort:
            return "NeCode 端口必须是有效数字。"
        case .invalidSSHPort:
            return "SSH 端口必须是有效数字。"
        case .invalidWakeMAC:
            return "唤醒 MAC 格式应类似 aa:bb:cc:dd:ee:ff。"
        case .invalidWebsocketURL:
            return "请输入有效的 ws:// 或 wss:// URL。"
        }
    }
}

private struct SettingsServerConnectionConfiguration {
    let savedServer: SavedServer
    let discoveredServer: DiscoveredServer
    let connectionMode: SettingsServerConnectionMode
}

private struct SettingsServerConnectionEditor: View {
    let server: HomeDashboardServer
    let onSave: (SettingsServerConnectionConfiguration) -> Void
    let onReconnect: (SettingsServerConnectionConfiguration) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var displayName: String
    @State private var connectionMode: SettingsServerConnectionMode
    @State private var host: String
    @State private var codexPort: String
    @State private var websocketURL: String
    @State private var sshPort: String
    @State private var wakeMAC: String
    @State private var validationError: String?

    private let originalSavedServer: SavedServer?

    @MainActor
    init(
        server: HomeDashboardServer,
        onSave: @escaping (SettingsServerConnectionConfiguration) -> Void,
        onReconnect: @escaping (SettingsServerConnectionConfiguration) -> Void
    ) {
        self.server = server
        self.onSave = onSave
        self.onReconnect = onReconnect

        let saved = SavedServerStore.load().first { $0.id == server.id }
        self.originalSavedServer = saved

        let resolvedMode: SettingsServerConnectionMode
        if server.isLocal {
            resolvedMode = .local
        } else if saved?.websocketURL != nil {
            resolvedMode = .websocket
        } else if saved?.preferredConnectionMode == .ssh || saved?.sshPort != nil && saved?.hasCodexServer == false {
            resolvedMode = .ssh
        } else {
            resolvedMode = .directCodex
        }

        let name = saved?.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedHost = saved?.hostname.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedCodexPort = saved?.preferredCodexPort ?? saved?.port ?? (server.port == 0 ? nil : server.port)
        let resolvedSSHPort = saved?.sshPort ?? (resolvedMode == .ssh ? server.port : nil) ?? 22

        _displayName = State(initialValue: name?.isEmpty == false ? name! : server.displayName)
        _connectionMode = State(initialValue: resolvedMode)
        _host = State(initialValue: resolvedHost?.isEmpty == false ? resolvedHost! : server.host)
        _codexPort = State(initialValue: resolvedCodexPort.map(String.init) ?? "8390")
        _websocketURL = State(initialValue: saved?.websocketURL ?? "")
        _sshPort = State(initialValue: String(resolvedSSHPort))
        _wakeMAC = State(initialValue: saved?.wakeMAC ?? "")
    }

    private var availableModes: [SettingsServerConnectionMode] {
        server.isLocal ? [.local] : [.ssh, .directCodex, .websocket]
    }

    private var isSpecialPairedServer: Bool {
        originalSavedServer?.alleycatNodeId != nil || originalSavedServer?.alleycatAgentWire == "ssh-bridge"
    }

    var body: some View {
        NavigationStack {
            ZStack {
                LitterTheme.backgroundGradient.ignoresSafeArea()
                Form {
                    nameSection
                    connectionSection
                    actionSection
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("编辑主机")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("取消") { dismiss() }
                        .foregroundColor(LitterTheme.accent)
                }
            }
            .alert("主机配置无效", isPresented: Binding(
                get: { validationError != nil },
                set: { if !$0 { validationError = nil } }
            )) {
                Button("确定") { validationError = nil }
            } message: {
                Text(validationError ?? "请检查主机配置。")
            }
        }
    }

    private var nameSection: some View {
        Section {
            TextField("主机名称", text: $displayName)
                .litterFont(.footnote)
                .foregroundColor(LitterTheme.textPrimary)
        } header: {
            Text("名称")
                .foregroundColor(LitterTheme.textSecondary)
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    private var connectionSection: some View {
        Section {
            if isSpecialPairedServer {
                Text("这台已配对主机使用保存的配对信息。这里只能修改显示名称；如需更换配对，请移除后重新添加。")
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.textSecondary)
            } else if connectionMode == .local {
                Text("本机运行时会自动管理。")
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.textSecondary)
            } else {
                Picker("连接类型", selection: $connectionMode) {
                    ForEach(availableModes) { mode in
                        Text(mode.label).tag(mode)
                    }
                }
                .pickerStyle(.segmented)

                switch connectionMode {
                case .local:
                    EmptyView()
                case .ssh:
                    hostField
                    TextField("SSH 端口", text: $sshPort)
                        .litterFont(.footnote)
                        .foregroundColor(LitterTheme.textPrimary)
                        .keyboardType(.numberPad)
                    TextField("唤醒 MAC（可选）", text: $wakeMAC)
                        .litterFont(.footnote)
                        .foregroundColor(LitterTheme.textPrimary)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                case .directCodex:
                    hostField
                    TextField("NeCode 端口", text: $codexPort)
                        .litterFont(.footnote)
                        .foregroundColor(LitterTheme.textPrimary)
                        .keyboardType(.numberPad)
                case .websocket:
                    TextField("ws://主机:端口 或 wss://...", text: $websocketURL)
                        .litterFont(.footnote)
                        .foregroundColor(LitterTheme.textPrimary)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .keyboardType(.URL)
                }
            }
        } header: {
            Text(connectionMode.formHeader)
                .foregroundColor(LitterTheme.textSecondary)
        } footer: {
            if !isSpecialPairedServer, connectionMode == .websocket {
                Text("建议优先使用 SSH。若手动运行 NeCode 服务，请只绑定本机回环地址并自行配置隧道；除非非常清楚风险，不要直接暴露到公网。")
                    .litterFont(.caption2)
                    .foregroundColor(LitterTheme.textMuted)
            }
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    private var hostField: some View {
        TextField("主机名或 IP", text: $host)
            .litterFont(.footnote)
            .foregroundColor(LitterTheme.textPrimary)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)
    }

    private var actionSection: some View {
        Section {
            Button("保存") {
                submit(reconnect: false)
            }
            .foregroundColor(LitterTheme.accent)
            .litterFont(.subheadline)

            if !isSpecialPairedServer {
                Button(connectionMode == .local ? "保存并重启" : "保存并重连") {
                    submit(reconnect: true)
                }
                .foregroundColor(LitterTheme.accent)
                .litterFont(.subheadline)
            }
        }
        .listRowBackground(LitterTheme.surface.opacity(0.6))
    }

    private func submit(reconnect: Bool) {
        do {
            let configuration = try buildConfiguration()
            if reconnect {
                onReconnect(configuration)
            } else {
                onSave(configuration)
            }
        } catch {
            validationError = error.localizedDescription
        }
    }

    private func buildConfiguration() throws -> SettingsServerConnectionConfiguration {
        let name = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { throw SettingsServerConnectionError.emptyName }

        if isSpecialPairedServer, let originalSavedServer {
            let updated = originalSavedServer.withName(name)
            return SettingsServerConnectionConfiguration(
                savedServer: updated,
                discoveredServer: updated.toDiscoveredServer(),
                connectionMode: connectionMode
            )
        }

        switch connectionMode {
        case .local:
            let saved = SavedServer(
                id: server.id,
                name: name,
                hostname: "127.0.0.1",
                port: 0,
                codexPorts: [],
                sshPort: nil,
                source: .local,
                hasCodexServer: true,
                wakeMAC: nil,
                preferredConnectionMode: nil,
                preferredCodexPort: nil,
                sshPortForwardingEnabled: nil,
                websocketURL: nil,
                rememberedByUser: true
            )
            return SettingsServerConnectionConfiguration(
                savedServer: saved,
                discoveredServer: saved.toDiscoveredServer(),
                connectionMode: .local
            )
        case .ssh:
            let resolvedHost = try validatedHost()
            let resolvedWakeMAC = try validatedWakeMAC()
            guard let resolvedSSHPort = UInt16(sshPort.trimmingCharacters(in: .whitespacesAndNewlines)) else {
                throw SettingsServerConnectionError.invalidSSHPort
            }
            let saved = SavedServer(
                id: server.id,
                name: name,
                hostname: resolvedHost,
                port: nil,
                codexPorts: [],
                sshPort: resolvedSSHPort,
                source: .manual,
                hasCodexServer: false,
                wakeMAC: resolvedWakeMAC,
                preferredConnectionMode: .ssh,
                preferredCodexPort: nil,
                sshPortForwardingEnabled: nil,
                websocketURL: nil,
                rememberedByUser: true
            )
            return SettingsServerConnectionConfiguration(
                savedServer: saved,
                discoveredServer: saved.toDiscoveredServer(),
                connectionMode: .ssh
            )
        case .directCodex:
            let resolvedHost = try validatedHost()
            guard let resolvedCodexPort = UInt16(codexPort.trimmingCharacters(in: .whitespacesAndNewlines)) else {
                throw SettingsServerConnectionError.invalidCodexPort
            }
            let saved = SavedServer(
                id: server.id,
                name: name,
                hostname: resolvedHost,
                port: resolvedCodexPort,
                codexPorts: [resolvedCodexPort],
                sshPort: nil,
                source: .manual,
                hasCodexServer: true,
                wakeMAC: nil,
                preferredConnectionMode: .directCodex,
                preferredCodexPort: resolvedCodexPort,
                sshPortForwardingEnabled: nil,
                websocketURL: nil,
                rememberedByUser: true
            )
            return SettingsServerConnectionConfiguration(
                savedServer: saved,
                discoveredServer: saved.toDiscoveredServer(),
                connectionMode: .directCodex
            )
        case .websocket:
            let rawURL = websocketURL.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let url = URL(string: rawURL),
                  let scheme = url.scheme?.lowercased(),
                  (scheme == "ws" || scheme == "wss"),
                  let resolvedHost = url.host,
                  !resolvedHost.isEmpty else {
                throw SettingsServerConnectionError.invalidWebsocketURL
            }
            let resolvedPort = url.port.flatMap { UInt16(exactly: $0) }
            let saved = SavedServer(
                id: server.id,
                name: name,
                hostname: resolvedHost,
                port: resolvedPort,
                codexPorts: resolvedPort.map { [$0] } ?? [],
                sshPort: nil,
                source: .manual,
                hasCodexServer: true,
                wakeMAC: nil,
                preferredConnectionMode: .directCodex,
                preferredCodexPort: resolvedPort,
                sshPortForwardingEnabled: nil,
                websocketURL: rawURL,
                rememberedByUser: true
            )
            return SettingsServerConnectionConfiguration(
                savedServer: saved,
                discoveredServer: saved.toDiscoveredServer(),
                connectionMode: .websocket
            )
        }
    }

    private func validatedHost() throws -> String {
        let resolvedHost = host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !resolvedHost.isEmpty else { throw SettingsServerConnectionError.emptyHost }
        return resolvedHost
    }

    private func validatedWakeMAC() throws -> String? {
        let wakeInput = wakeMAC.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !wakeInput.isEmpty else { return nil }
        guard let normalized = DiscoveredServer.normalizeWakeMAC(wakeInput) else {
            throw SettingsServerConnectionError.invalidWakeMAC
        }
        return normalized
    }
}

private struct SettingsConnectionAccountSection: View {
    @Environment(AppModel.self) private var appModel
    let server: AppServerSnapshot
    @State private var apiKey = ""
    @State private var openAIBaseURL = ""
    @State private var isAuthWorking = false
    @State private var authError: String?
    @State private var hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
    @State private var hasStoredBaseURL = OpenAIApiKeyStore.shared.hasStoredBaseURL
    @State private var hasStoredChatGPTTokens = false

    var body: some View {
        Section {
            HStack(spacing: 12) {
                Circle()
                    .fill(authColor)
                    .frame(width: 10, height: 10)
                VStack(alignment: .leading, spacing: 2) {
                    Text(authTitle)
                        .litterFont(.subheadline)
                        .foregroundColor(LitterTheme.textPrimary)
                    if let sub = authSubtitle {
                        Text(sub)
                            .litterFont(.caption)
                            .foregroundColor(LitterTheme.textSecondary)
                    }
                }
                Spacer()
                if server.isLocal, server.account != nil {
                    Button("退出") {
                        Task { await logout() }
                    }
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.danger)
                }
            }
            .listRowBackground(LitterTheme.surface.opacity(0.6))

            if server.isLocal, hasStoredApiKey {
                Text("本机 OpenAI API Key 已保存。")
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.accent)
                    .listRowBackground(LitterTheme.surface.opacity(0.6))
            }

            if server.isLocal, hasStoredBaseURL {
                Text("OpenAI 兼容 Base URL 已保存。")
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.accent)
                    .listRowBackground(LitterTheme.surface.opacity(0.6))
            }

            if server.isLocal, !isChatGPTAccount {
                Button {
                    Task {
                        isAuthWorking = true
                        await loginWithChatGPT()
                        isAuthWorking = false
                    }
                } label: {
                    HStack {
                        if isAuthWorking {
                            ProgressView().tint(LitterTheme.textPrimary).scaleEffect(0.8)
                        }
                        Image(systemName: "person.crop.circle.badge.checkmark")
                        Text("使用 ChatGPT 登录")
                            .litterFont(.subheadline)
                    }
                    .foregroundColor(LitterTheme.accent)
                }
                .disabled(isAuthWorking)
                .listRowBackground(LitterTheme.surface.opacity(0.6))
            }

            if server.isLocal, allowsLocalEnvApiKey {
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 6) {
                        if hasStoredApiKey {
                            Text("OpenAI API Key 已保存到本机环境。")
                                .litterFont(.caption)
                                .foregroundColor(LitterTheme.textSecondary)
                        } else if isChatGPTAccount {
                            Text("将 API Key 保存到本机 NeCode 环境。")
                                .litterFont(.caption)
                                .foregroundColor(LitterTheme.textSecondary)
                        }
                        SecureField("sk-...", text: $apiKey)
                            .litterFont(.footnote)
                            .foregroundColor(LitterTheme.textPrimary)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                    Button {
                        let key = apiKey.trimmingCharacters(in: .whitespaces)
                        guard !key.isEmpty else { return }
                        Task {
                            isAuthWorking = true
                            await saveApiKey(key)
                            isAuthWorking = false
                        }
                    } label: {
                            Text(hasStoredApiKey ? "更新 API Key" : "保存 API Key")
                    }
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.accent)
                    .disabled(apiKey.trimmingCharacters(in: .whitespaces).isEmpty || isAuthWorking)
                }
                .listRowBackground(LitterTheme.surface.opacity(0.6))

                VStack(alignment: .leading, spacing: 8) {
                    if hasStoredBaseURL {
                        Text("本机 NeCode 服务已保存自定义 OpenAI 兼容地址。")
                            .litterFont(.caption)
                            .foregroundColor(LitterTheme.textSecondary)
                    } else {
                        Text("可选：为本地模型配置 OpenAI 兼容地址。")
                            .litterFont(.caption)
                            .foregroundColor(LitterTheme.textSecondary)
                    }
                    HStack(spacing: 8) {
                        TextField("http://主机:端口/v1", text: $openAIBaseURL)
                            .litterFont(.footnote)
                            .foregroundColor(LitterTheme.textPrimary)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                        Button {
                            let baseURL = openAIBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
                            Task {
                                isAuthWorking = true
                                await saveBaseURL(baseURL)
                                isAuthWorking = false
                            }
                        } label: {
                            Text(hasStoredBaseURL ? "更新 Base URL" : "保存 Base URL")
                        }
                        .litterFont(.caption)
                        .foregroundColor(LitterTheme.accent)
                        .disabled(openAIBaseURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isAuthWorking)
                    }
                    if hasStoredBaseURL {
                        Button("清除 Base URL") {
                            Task {
                                isAuthWorking = true
                                await clearBaseURL()
                                isAuthWorking = false
                            }
                        }
                        .litterFont(.caption)
                        .foregroundColor(LitterTheme.danger)
                        .disabled(isAuthWorking)
                    }
                }
                .listRowBackground(LitterTheme.surface.opacity(0.6))
            }

            if let authError {
                Text(authError)
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.danger)
                    .listRowBackground(LitterTheme.surface.opacity(0.6))
            }
        } header: {
            Text("账号")
                .foregroundColor(LitterTheme.textSecondary)
        }
        .task(id: server.serverId) {
            refreshStoredCredentialFlags()
            await refreshAuthStatusIfNeeded()
        }
    }

    private var allowsLocalEnvApiKey: Bool {
        server.isLocal
    }

    private var isChatGPTAccount: Bool {
        if case .chatgpt? = server.account {
            return true
        }
        return false
    }

    private var hasStoredLocalCredentials: Bool {
        hasStoredApiKey || hasStoredChatGPTTokens
    }

    private var authColor: Color {
        switch server.account {
        case .chatgpt?:
            return LitterTheme.accent
        case .apiKey?:
            return Color(hex: "#00AAFF")
        case nil where server.isLocal && hasStoredChatGPTTokens:
            return LitterTheme.accent.opacity(0.7)
        case nil where server.isLocal && hasStoredApiKey:
            return Color(hex: "#00AAFF").opacity(0.7)
        case nil:
            return LitterTheme.textMuted
        }
    }

    private var authTitle: String {
        switch server.account {
        case .chatgpt(let email, _)?:
            return email.isEmpty ? "ChatGPT" : email
        case .apiKey?:
            return "API Key"
        case nil where server.isLocal && hasStoredChatGPTTokens:
            return "ChatGPT"
        case nil where server.isLocal && hasStoredApiKey:
            return "API Key"
        case nil:
            return "未登录"
        }
    }

    private var authSubtitle: String? {
        switch server.account {
        case .chatgpt?:
            return "ChatGPT 账号"
        case .apiKey?:
            return "OpenAI API Key"
        case nil where server.isLocal && hasStoredChatGPTTokens:
            return "已保存到本机，正在恢复会话"
        case nil where server.isLocal && hasStoredApiKey:
            return "已保存到本机，正在刷新本机账号"
        case nil:
            return nil
        }
    }

    private func loginWithChatGPT() async {
        guard server.isLocal else {
            authError = "只能为本机服务在设置中登录。"
            return
        }
        do {
            authError = nil
            try await appModel.loginLocalChatGPTAccount(serverId: server.serverId)
        } catch ChatGPTOAuthError.cancelled {
            return
        } catch {
            authError = error.localizedDescription
        }
    }

    private func refreshStoredCredentialFlags() {
        hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
        hasStoredBaseURL = OpenAIApiKeyStore.shared.hasStoredBaseURL
        do {
            hasStoredChatGPTTokens = try ChatGPTOAuthTokenStore.shared.load() != nil
        } catch let error as ChatGPTOAuthError where error.isTransientKeychainAvailabilityFailure {
            hasStoredChatGPTTokens = false
        } catch {
            hasStoredChatGPTTokens = false
        }
    }

    private func refreshAuthStatusIfNeeded() async {
        guard server.isLocal, server.account == nil else { return }
        guard hasStoredLocalCredentials else { return }
        await appModel.restoreStoredLocalAuthState(serverId: server.serverId)
        await refreshAccount()
    }

    private func refreshAccount() async {
        do {
            _ = try await appModel.client.refreshAccount(
                serverId: server.serverId,
                params: AppRefreshAccountRequest(refreshToken: false)
            )
            await appModel.refreshSnapshot()
            refreshStoredCredentialFlags()
            authError = nil
        } catch {
            authError = error.localizedDescription
        }
    }

    private func saveApiKey(_ key: String) async {
        guard server.isLocal else {
            authError = "API Key 只能保存到本机服务。"
            return
        }
        do {
            authError = nil
            try OpenAIApiKeyStore.shared.save(key)
            if case .apiKey? = server.account {
                _ = try await appModel.client.logoutAccount(serverId: server.serverId)
            }
            try await appModel.restartLocalServer()
            refreshStoredCredentialFlags()
            guard hasStoredApiKey else {
                authError = "API Key 未能保存到本机。"
                return
            }
        } catch {
            authError = error.localizedDescription
        }
    }

    private func saveBaseURL(_ rawBaseURL: String) async {
        guard server.isLocal else {
            authError = "Base URL 只能保存到本机服务。"
            return
        }
        guard let baseURL = normalizedOpenAIBaseURL(rawBaseURL) else {
            authError = "请输入有效的 http 或 https Base URL。"
            return
        }
        do {
            authError = nil
            try OpenAIApiKeyStore.shared.saveBaseURL(baseURL)
            try await appModel.restartLocalServer()
            refreshStoredCredentialFlags()
            guard hasStoredBaseURL else {
                authError = "Base URL 未能保存到本机。"
                return
            }
            openAIBaseURL = ""
        } catch {
            authError = error.localizedDescription
        }
    }

    private func clearBaseURL() async {
        guard server.isLocal else {
            authError = "Base URL 只能从本机服务清除。"
            return
        }
        do {
            authError = nil
            try OpenAIApiKeyStore.shared.clearBaseURL()
            try await appModel.restartLocalServer()
            refreshStoredCredentialFlags()
            openAIBaseURL = ""
        } catch {
            authError = error.localizedDescription
        }
    }

    private func normalizedOpenAIBaseURL(_ rawValue: String) -> String? {
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              url.host != nil else {
            return nil
        }
        return trimmed.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private func logout() async {
        guard server.isLocal else {
            authError = "只能为本机服务在设置中退出登录。"
            return
        }
        do {
            try? ChatGPTOAuthTokenStore.shared.clear()
            try? OpenAIApiKeyStore.shared.clear()
            _ = try await appModel.client.logoutAccount(serverId: server.serverId)
            try await appModel.restartLocalServer()
            refreshStoredCredentialFlags()
            authError = nil
        } catch {
            authError = error.localizedDescription
        }
    }
}

private struct SettingsDisconnectedAccountSection: View {
    var body: some View {
        Section {
            Text("本机 NeCode 尚未运行。ChatGPT 登录和 API Key 配置需要先启动本机桥接服务。")
                .litterFont(.caption)
                .foregroundColor(LitterTheme.textMuted)
                .listRowBackground(LitterTheme.surface.opacity(0.6))
        } header: {
            Text("账号")
                .foregroundColor(LitterTheme.textSecondary)
        }
    }
}

private func isSettingsSlingshotURL(_ rawURL: String) -> Bool {
    URL(string: rawURL)?.scheme?.lowercased() == "slingshot"
}

#if DEBUG
#Preview("Settings") {
    LitterPreviewScene(includeBackground: false) {
        SettingsView()
    }
}
#endif

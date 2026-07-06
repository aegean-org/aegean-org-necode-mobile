import SwiftUI

struct SSHLoginSheet: View {
    let server: DiscoveredServer
    let onConnect: (ConnectionTarget) -> Void
    private let autoLoadSavedCredentials: Bool

    @Environment(\.dismiss) private var dismiss
    @State private var username: String
    @State private var password = ""
    @State private var isPasswordVisible = false
    @State private var useKey = false
    @State private var privateKey = ""
    @State private var passphrase = ""
    @State private var rememberCredentials = true
    @State private var unlockMacosKeychain = false
    @State private var hasSavedCredentials = false
    @State private var loadedSavedCredentials = false
    @State private var isConnecting = false
    @State private var errorMessage: String?

    init(
        server: DiscoveredServer,
        autoLoadSavedCredentials: Bool = true,
        initialUsername: String = "",
        onConnect: @escaping (ConnectionTarget) -> Void
    ) {
        self.server = server
        self.onConnect = onConnect
        self.autoLoadSavedCredentials = autoLoadSavedCredentials
        _username = State(initialValue: initialUsername)
    }

    private var sshPort: Int {
        Int(server.resolvedSSHPort)
    }

    private var hostDisplay: String {
        if sshPort == 22 {
            return server.hostname
        }
        return "\(server.hostname):\(sshPort)"
    }

    var body: some View {
        NavigationStack {
            ZStack {
                LitterTheme.backgroundGradient.ignoresSafeArea()
                Form {
                    Section {
                        HStack(spacing: 12) {
                            Image(systemName: "terminal")
                                .foregroundColor(LitterTheme.accent)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(server.name)
                                    .litterFont(.subheadline)
                                    .foregroundColor(LitterTheme.textPrimary)
                                Text(hostDisplay)
                                    .litterFont(.caption)
                                    .foregroundColor(LitterTheme.textSecondary)
                            }
                        }
                    }
                    .listRowBackground(LitterTheme.surface.opacity(0.6))

                    Section {
                        TextField("用户名", text: $username)
                            .litterFont(.footnote)
                            .foregroundColor(LitterTheme.textPrimary)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled(true)
                    } header: {
                        Text("用户名")
                            .foregroundColor(LitterTheme.textSecondary)
                    }
                    .listRowBackground(LitterTheme.surface.opacity(0.6))

                    Section {
                        Picker("认证方式", selection: $useKey) {
                            Text("密码").tag(false)
                            Text("SSH 密钥").tag(true)
                        }
                        .pickerStyle(.segmented)
                        .listRowBackground(LitterTheme.surface.opacity(0.6))

                        if useKey {
                            TextEditor(text: $privateKey)
                                .litterFont(.caption)
                                .foregroundColor(LitterTheme.textPrimary)
                                .scrollContentBackground(.hidden)
                                .frame(minHeight: 100)
                                .overlay(alignment: .topLeading) {
                                    if privateKey.isEmpty {
                                        Text("在这里粘贴私钥...")
                                            .litterFont(.caption)
                                            .foregroundColor(LitterTheme.textMuted)
                                            .padding(.top, 8)
                                            .padding(.leading, 4)
                                            .allowsHitTesting(false)
                                    }
                                }
                            SecureField("密钥口令（可选）", text: $passphrase)
                                .litterFont(.footnote)
                                .foregroundColor(LitterTheme.textPrimary)
                        } else {
                            passwordInput

                            VStack(alignment: .leading, spacing: 4) {
                                Toggle(isOn: $unlockMacosKeychain) {
                                    Text("解锁钥匙串（macOS）")
                                        .litterFont(.footnote)
                                        .foregroundColor(LitterTheme.textPrimary)
                                }
                                .tint(LitterTheme.accent)

                                Text("无界面启动时会使用 SSH/登录密码。gh CLI 授权等工具可能需要它。")
                                    .litterFont(.caption)
                                    .foregroundColor(LitterTheme.textSecondary)
                            }
                        }
                    } header: {
                        Text("认证")
                            .foregroundColor(LitterTheme.textSecondary)
                    }
                    .listRowBackground(LitterTheme.surface.opacity(0.6))

                    Section {
                        Toggle(isOn: $rememberCredentials) {
                            Text("在本设备记住凭据")
                                .litterFont(.footnote)
                                .foregroundColor(LitterTheme.textPrimary)
                        }
                        .tint(LitterTheme.accent)

                        if hasSavedCredentials {
                            Button(role: .destructive) {
                                forgetSavedCredentials()
                            } label: {
                                Text("忘记已保存凭据")
                                    .litterFont(.footnote)
                            }
                        }
                    } header: {
                        Text("已保存凭据")
                            .foregroundColor(LitterTheme.textSecondary)
                    }
                    .listRowBackground(LitterTheme.surface.opacity(0.6))

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
                        .disabled(isConnecting || username.isEmpty || (!useKey && password.isEmpty) || (useKey && privateKey.isEmpty))
                    }
                    .listRowBackground(LitterTheme.surface.opacity(0.6))

                    if let err = errorMessage {
                        Section {
                            Text(err)
                                .foregroundColor(.red)
                                .litterFont(.caption)
                        }
                        .listRowBackground(LitterTheme.surface.opacity(0.6))
                    }
                }
                .scrollContentBackground(.hidden)
            }
            .navigationTitle("SSH 登录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("取消") { dismiss() }
                        .foregroundColor(LitterTheme.accent)
                }
            }
        }
        .task {
            guard autoLoadSavedCredentials else { return }
            loadSavedCredentialsIfNeeded()
        }
        .onChange(of: useKey) { _, isUsingKey in
            if isUsingKey {
                isPasswordVisible = false
                unlockMacosKeychain = false
            }
        }
    }

    private var passwordInput: some View {
        HStack(spacing: 8) {
            Group {
                if isPasswordVisible {
                    TextField("密码", text: $password)
                        .textContentType(.password)
                } else {
                    SecureField("密码", text: $password)
                        .textContentType(.password)
                }
            }
            .litterFont(.footnote)
            .foregroundColor(LitterTheme.textPrimary)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled(true)

            Button {
                isPasswordVisible.toggle()
            } label: {
                Image(systemName: isPasswordVisible ? "eye.slash" : "eye")
                    .foregroundColor(LitterTheme.textSecondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPasswordVisible ? "隐藏密码" : "显示密码")
        }
    }

    private func connect() {
        let credentials: SSHCredentials
        if useKey {
            credentials = .key(
                username: username,
                privateKey: privateKey,
                passphrase: passphrase.isEmpty ? nil : passphrase
            )
        } else {
            credentials = .password(
                username: username,
                password: password,
                unlockMacosKeychain: unlockMacosKeychain
            )
        }
        isConnecting = true
        errorMessage = nil

        Task {
            do {
                do {
                    if rememberCredentials {
                        try SSHCredentialStore.shared.save(
                            savedCredential(from: credentials),
                            host: server.hostname,
                            port: sshPort
                        )
                        hasSavedCredentials = true
                    } else {
                        try SSHCredentialStore.shared.delete(host: server.hostname, port: sshPort)
                        hasSavedCredentials = false
                    }
                } catch {
                    NSLog("[SSH_CREDENTIALS] keychain update failed: %@", error.localizedDescription)
                }

                clearSensitiveInput()
                isConnecting = false
                onConnect(.sshThenRemote(host: server.hostname, credentials: credentials))
            } catch {
                isConnecting = false
                errorMessage = error.localizedDescription
            }
        }
    }

    private func loadSavedCredentialsIfNeeded() {
        guard !loadedSavedCredentials else { return }
        loadedSavedCredentials = true

        do {
            guard let saved = try SSHCredentialStore.shared.load(host: server.hostname, port: sshPort) else {
                hasSavedCredentials = false
                return
            }
            hasSavedCredentials = true
            rememberCredentials = true
            username = saved.username
            useKey = saved.method == .key
            if saved.method == .key {
                privateKey = saved.privateKey ?? ""
                passphrase = saved.passphrase ?? ""
                password = ""
                unlockMacosKeychain = false
            } else {
                password = saved.password ?? ""
                privateKey = ""
                passphrase = ""
                unlockMacosKeychain = saved.unlockMacosKeychain ?? false
            }
        } catch {
            NSLog("[SSH_CREDENTIALS] failed to load: %@", error.localizedDescription)
        }
    }

    private func forgetSavedCredentials() {
        do {
            try SSHCredentialStore.shared.delete(host: server.hostname, port: sshPort)
            hasSavedCredentials = false
            rememberCredentials = false
            clearSensitiveInput()
        } catch {
            NSLog("[SSH_CREDENTIALS] failed to delete: %@", error.localizedDescription)
        }
    }

    private func savedCredential(from credentials: SSHCredentials) -> SavedSSHCredential {
        switch credentials {
        case .password(let username, let password, let unlockMacosKeychain):
            return SavedSSHCredential(
                username: username,
                method: .password,
                password: password,
                privateKey: nil,
                passphrase: nil,
                unlockMacosKeychain: unlockMacosKeychain
            )
        case .key(let username, let privateKey, let passphrase):
            return SavedSSHCredential(
                username: username,
                method: .key,
                password: nil,
                privateKey: privateKey,
                passphrase: passphrase,
                unlockMacosKeychain: false
            )
        }
    }

    private func clearSensitiveInput() {
        password = ""
        isPasswordVisible = false
        privateKey = ""
        passphrase = ""
    }
}

#if DEBUG
#Preview("SSH Login") {
    SSHLoginSheet(
        server: LitterPreviewData.sampleSSHServer,
        autoLoadSavedCredentials: false,
        initialUsername: "builder"
    ) { _ in }
}
#endif

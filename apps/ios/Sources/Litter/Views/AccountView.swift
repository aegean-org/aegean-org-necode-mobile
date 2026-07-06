import SwiftUI

struct AccountView: View {
    @Environment(AppModel.self) private var appModel
    @Environment(\.dismiss) private var dismiss

    private var server: AppServerSnapshot? {
        // Account management (ChatGPT login / API key) is local-only, always.
        // If the local Codex bridge hasn't spun up there's no login target, and
        // the caller falls through to `AccountDisconnectedView`.
        appModel.snapshot?.servers.first(where: \.isLocal)
    }

    var body: some View {
        if let server {
            AccountConnectionView(server: server, dismiss: dismiss)
        } else {
            AccountDisconnectedView(dismiss: dismiss)
        }
    }
}

private struct AccountConnectionView: View {
    @Environment(AppModel.self) private var appModel
    let server: AppServerSnapshot
    let dismiss: DismissAction

    @State private var apiKey = ""
    @State private var isWorking = false
    @State private var authError: String?
    @State private var hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey

    var body: some View {
        NavigationStack {
            ZStack {
                LitterTheme.backgroundGradient.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        currentAccountSection
                        Divider().background(LitterTheme.surfaceLight)
                        loginSection
                        if let err = authError {
                            Text(err)
                                .font(.caption)
                                .foregroundColor(.red)
                                .padding(.horizontal, 20)
                        }
                    }
                    .padding(.top, 20)
                }
            }
            .navigationTitle("账号")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                        .foregroundColor(LitterTheme.accent)
                }
            }
            .task(id: server.serverId) {
                await refreshAccount()
                hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
            }
        }
    }

    private var currentAccountSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("当前账号")
                .litterFont(.caption)
                .foregroundColor(LitterTheme.textMuted)
                .padding(.horizontal, 20)

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
                    .litterFont(.footnote)
                    .foregroundColor(LitterTheme.danger)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.ultraThinMaterial)
            .cornerRadius(10)
            .padding(.horizontal, 16)

            if server.isLocal, hasStoredApiKey {
                Text("本机 OpenAI API Key 已保存。")
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.accent)
                    .padding(.horizontal, 20)
            }
        }
    }

    private var loginSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("登录")
                .litterFont(.caption)
                .foregroundColor(LitterTheme.textMuted)
                .padding(.horizontal, 20)

            if server.isLocal, !isChatGPTAccount {
                Button {
                    Task {
                        isWorking = true
                        await loginWithChatGPT()
                        isWorking = false
                    }
                } label: {
                    HStack {
                        if isWorking {
                            ProgressView().tint(LitterTheme.textOnAccent).scaleEffect(0.8)
                        }
                        Image(systemName: "person.crop.circle.badge.checkmark")
                        Text("使用 ChatGPT 登录")
                            .litterFont(.subheadline)
                    }
                    .foregroundColor(LitterTheme.textOnAccent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(LitterTheme.accent)
                    .cornerRadius(10)
                }
                .padding(.horizontal, 16)
                .disabled(isWorking)
            }

            if server.isLocal, allowsLocalEnvApiKey {
                Text("或为本机环境保存 API Key")
                    .litterFont(.caption)
                    .foregroundColor(LitterTheme.textMuted)
                    .frame(maxWidth: .infinity)

                VStack(alignment: .leading, spacing: 8) {
                    if hasStoredApiKey {
                        Text("OpenAI API Key 已保存到本机环境。")
                            .litterFont(.caption)
                            .foregroundColor(LitterTheme.textSecondary)
                            .padding(.horizontal, 16)
                    } else if isChatGPTAccount {
                        Text("将 OpenAI API Key 保存到本机 NeCode 环境。")
                            .litterFont(.caption)
                            .foregroundColor(LitterTheme.textSecondary)
                            .padding(.horizontal, 16)
                    }

                    SecureField("sk-...", text: $apiKey)
                        .litterFont(.subheadline)
                        .foregroundColor(LitterTheme.textPrimary)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding(12)
                        .background(LitterTheme.surface)
                        .cornerRadius(8)
                        .padding(.horizontal, 16)

                    Button {
                        let key = apiKey.trimmingCharacters(in: .whitespaces)
                        guard !key.isEmpty else { return }
                        Task {
                            isWorking = true
                            await saveApiKey(key)
                            isWorking = false
                        }
                    } label: {
                        Text(hasStoredApiKey ? "更新 API Key" : "保存 API Key")
                            .litterFont(.subheadline)
                            .foregroundColor(LitterTheme.textPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(12)
                            .background(LitterTheme.surface)
                            .cornerRadius(8)
                            .padding(.horizontal, 16)
                    }
                    .disabled(apiKey.trimmingCharacters(in: .whitespaces).isEmpty || isWorking)
                }
            }
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
    private var authColor: Color {
        switch server.account {
        case .chatgpt?:
            return LitterTheme.accent
        case .apiKey?:
            return Color(hex: "#00AAFF")
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
        case nil:
            return nil
        }
    }

    private func refreshAccount() async {
        do {
            _ = try await appModel.client.refreshAccount(
                serverId: server.serverId,
                params: AppRefreshAccountRequest(refreshToken: false)
            )
            await appModel.refreshSnapshot()
            authError = nil
        } catch {
            authError = error.localizedDescription
        }
    }

    private func loginWithChatGPT() async {
        guard server.isLocal else {
            authError = "只能为本机服务登录账号。"
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
            hasStoredApiKey = OpenAIApiKeyStore.shared.hasStoredKey
            guard hasStoredApiKey else {
                authError = "API Key 未能保存到本机。"
                return
            }
            dismiss()
        } catch {
            authError = error.localizedDescription
        }
    }

    private func logout() async {
        guard server.isLocal else {
            authError = "只能为本机服务退出登录。"
            return
        }
        do {
            try? ChatGPTOAuthTokenStore.shared.clear()
            try? OpenAIApiKeyStore.shared.clear()
            _ = try await appModel.client.logoutAccount(serverId: server.serverId)
            try await appModel.restartLocalServer()
            authError = nil
        } catch {
            authError = error.localizedDescription
        }
    }
}

private struct AccountDisconnectedView: View {
    let dismiss: DismissAction

    var body: some View {
        NavigationStack {
            ZStack {
                LitterTheme.backgroundGradient.ignoresSafeArea()
                VStack(spacing: 16) {
                    Text("本机 NeCode 尚未运行")
                        .litterFont(.subheadline)
                        .foregroundColor(LitterTheme.textPrimary)
                    Text("ChatGPT 登录和 API Key 配置需要先启动本机桥接服务。")
                        .litterFont(.caption)
                        .foregroundColor(LitterTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .navigationTitle("账号")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                        .foregroundColor(LitterTheme.accent)
                }
            }
        }
    }
}

#if DEBUG
#Preview("Account") {
    LitterPreviewScene(includeBackground: false) {
        AccountView()
    }
}
#endif

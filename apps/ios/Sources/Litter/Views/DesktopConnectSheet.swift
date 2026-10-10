import Foundation
import SwiftUI

struct DesktopConnectSheet: View {
    @State private var ucURL = UserDefaults.standard.string(forKey: "desktop.connect.ucURL") ?? "https://uc.inoteexpress.com/"
    @State private var connectURL = UserDefaults.standard.string(forKey: "desktop.connect.url") ?? "https://necode.inoteexpress.com/"
    @State private var username = ""
    @State private var password = ""
    @State private var passwordVisible = false
    @State private var token: String?
    @State private var accountId: String?
    @Binding var bridge: DesktopConnectBridge?
    let onConnected: (String) -> Void
    @State private var mobile: MobileDevice?
    @State private var hosts: [DesktopHost] = []
    @State private var hostsLoaded = false
    @State private var busy = false
    @State private var connectingHostId: String?
    @State private var restoring = true
    @State private var error: String?
    @State private var showAdvanced = false
    @FocusState private var focusedField: LoginField?
    @Environment(\.dismiss) private var dismiss

    private enum LoginField { case username, password }

    private var canLogin: Bool {
        !busy && !username.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(token == nil ? "连接桌面端" : "选择电脑")
                        .litterFont(size: 20, weight: .semibold)
                        .foregroundColor(LitterTheme.textPrimary)
                    if token == nil {
                        Text("登录 NE 账号，找到你的电脑")
                            .litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if token != nil {
                    Button { Task { await refreshHosts() } } label: {
                        Image(systemName: "arrow.clockwise").frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("刷新电脑")
                    .disabled(busy)
                }
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.system(size: 14, weight: .semibold))
                        .frame(width: 44, height: 44)
                        .background(LitterTheme.surface, in: Circle())
                }
                .accessibilityLabel("关闭")
                .disabled(busy || restoring)
            }
            if let error {
                HStack(spacing: 8) {
                    Text(error).litterFont(.footnote).foregroundColor(LitterTheme.danger)
                        .lineLimit(3).frame(maxWidth: .infinity, alignment: .leading)
                    if token != nil {
                        Button("重试") { Task { await refreshHosts() } }
                            .litterFont(.footnote, weight: .semibold)
                            .foregroundColor(LitterTheme.accentStrong)
                            .frame(minHeight: 44).disabled(busy)
                    }
                }
                .padding(.leading, 14).padding(.trailing, 8)
                .background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 12))
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if restoring {
                        connectLoading("正在恢复 NE 账号…")
                    } else if token == nil {
                        VStack(alignment: .leading, spacing: 8) {
                            if busy { connectLoading("正在登录 NE 账号…") }
                            Text("手机或邮箱").litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
                            TextField("输入 NE 账号", text: $username)
                                .textContentType(.username).keyboardType(.emailAddress)
                                .focused($focusedField, equals: .username).submitLabel(.next)
                                .onSubmit { focusedField = .password }
                                .textInputAutocapitalization(.never).autocorrectionDisabled()
                                .padding(14)
                                .background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(LitterTheme.border))
                                .disabled(busy)
                            Text("密码").litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
                            HStack(spacing: 0) {
                                Group {
                                    if passwordVisible {
                                        TextField("输入密码", text: $password)
                                    } else {
                                        SecureField("输入密码", text: $password)
                                    }
                                }
                                .textContentType(.password)
                                .focused($focusedField, equals: .password).submitLabel(.done)
                                .onSubmit { if canLogin { focusedField = nil; Task { await login() } } }
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .padding(.leading, 14)
                                Button { passwordVisible.toggle() } label: {
                                    Image(systemName: passwordVisible ? "eye.slash" : "eye")
                                        .foregroundColor(LitterTheme.textSecondary)
                                        .frame(width: 44, height: 48)
                                }
                                .accessibilityLabel(passwordVisible ? "隐藏密码" : "显示密码")
                                .disabled(busy)
                            }
                            .frame(minHeight: 48)
                            .background(LitterTheme.background, in: RoundedRectangle(cornerRadius: 12))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(LitterTheme.border))
                            .disabled(busy)
                        }
                    } else {
                        if busy && hosts.isEmpty {
                            connectLoading("正在查找在线电脑…")
                        }
                        if hosts.isEmpty && !busy {
                            VStack(spacing: 8) {
                                Image(systemName: "desktopcomputer").font(.system(size: 28))
                                Text(hostsLoaded ? "暂无可连接的电脑" : "暂时无法获取电脑列表")
                                    .foregroundColor(LitterTheme.textPrimary)
                                Text(hostsLoaded ? "确认桌面端已登录相同账号，再点击刷新" : "请稍后刷新；若仍失败，再检查网络或重新登录")
                                    .litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                            }
                            .frame(maxWidth: .infinity).padding(24)
                            .background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 16))
                        }
                        if !hosts.isEmpty && hosts.allSatisfy({ $0.status != "available" }) {
                            Text("电脑暂时不在线。请在电脑上打开 NeCode，并保持联网，然后刷新列表。")
                                .litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                        }
                        if !hosts.isEmpty {
                            if busy && connectingHostId == nil { connectLoading("正在刷新电脑列表…") }
                            let orderedHosts = hosts.sorted { left, right in
                                if (left.status == "available") != (right.status == "available") {
                                    return left.status == "available"
                                }
                                return left.name < right.name
                            }
                            VStack(spacing: 0) {
                                ForEach(orderedHosts, id: \.id) { host in
                                    if host.id != orderedHosts.first?.id {
                                        Divider().padding(.leading, 70)
                                    }
                                    let available = host.status == "available"
                                    DesktopHostTile(
                                        name: host.name,
                                        status: available ? "在线" : host.status == "busy" ? "占用中" : "离线",
                                        action: available ? "连接" : "不可连接",
                                        online: available,
                                        current: false,
                                        enabled: !busy && connectingHostId == nil && available,
                                        loading: connectingHostId == host.id
                                    ) {
                                        guard connectingHostId == nil else { return }
                                        connectingHostId = host.id
                                        Task { await connect(host) }
                                    }
                                }
                            }
                        }
                    }
                    if token == nil && !restoring {
                        Button { showAdvanced.toggle() } label: {
                            HStack(spacing: 4) {
                                Text(showAdvanced ? "收起服务地址" : "高级设置 · 服务地址")
                                Image(systemName: "chevron.down").font(.system(size: 10))
                            }
                            .litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                            .frame(maxWidth: .infinity, minHeight: 48)
                        }
                        .buttonStyle(.plain)
                        if showAdvanced {
                            TextField("NE 用户中心 HTTPS 地址", text: $ucURL)
                                .textInputAutocapitalization(.never).autocorrectionDisabled()
                                .disabled(bridge != nil)
                                .padding(14).background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 12))
                            TextField("Connect HTTPS 地址", text: $connectURL)
                                .textInputAutocapitalization(.never).autocorrectionDisabled()
                                .disabled(bridge != nil)
                                .padding(14).background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 12))
                        }
                    }
                }
                .padding(.bottom, 8)
            }
            .scrollDismissesKeyboard(.interactively)
            if token != nil && !restoring {
                Divider()
                HStack {
                    Text(accountId.map { "NE 账号 · \($0)" } ?? "已登录 NE 账号").lineLimit(1)
                        .foregroundColor(LitterTheme.textSecondary)
                    Spacer()
                    Button("退出登录") { Task { await logout() } }
                        .foregroundColor(LitterTheme.textSecondary)
                        .frame(minHeight: 44)
                        .disabled(busy)
                }
                .litterFont(.caption)
            }
            if token == nil && !restoring {
                Button { focusedField = nil; Task { await login() } } label: {
                    Text(busy ? "正在登录…" : "登录并查找电脑")
                        .litterFont(.subheadline, weight: .semibold)
                        .frame(maxWidth: .infinity, minHeight: 52)
                }
                .buttonStyle(.plain)
                .foregroundColor(canLogin ? LitterTheme.textOnAccent : LitterTheme.textSecondary)
                .background(canLogin ? LitterTheme.accentStrong : LitterTheme.surfaceLight,
                            in: RoundedRectangle(cornerRadius: 12))
                .disabled(!canLogin)
            }
        }
        .padding(.horizontal, 20).padding(.top, 4).padding(.bottom, 12)
        .background(LitterTheme.background)
        .presentationDetents(restoring ? [.height(240), .large] : token == nil ? [.medium, .large] :
            [.height(min(620, CGFloat(250 + max(1, hosts.count) * 94))), .large])
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(busy || restoring)
        .task { await restore() }
    }
    private func connectLoading(_ label: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "desktopcomputer")
                .font(.system(size: 24)).foregroundColor(LitterTheme.accentStrong)
                .frame(width: 56, height: 56)
                .background(LitterTheme.surfaceLight, in: RoundedRectangle(cornerRadius: 18))
            ProgressView().tint(LitterTheme.accentStrong)
            Text(label).litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 24)
    }

    private func configuredBridge() throws -> DesktopConnectBridge {
        if let bridge { return bridge }
        let created = try DesktopConnectBridge(ucUrl: ucURL, connectUrl: connectURL)
        UserDefaults.standard.set(ucURL, forKey: "desktop.connect.ucURL")
        UserDefaults.standard.set(connectURL, forKey: "desktop.connect.url")
        bridge = created
        return created
    }

    private func perform(_ action: () async throws -> Void) async {
        busy = true
        error = nil
        do { try await action() } catch {
            switch error {
            case let ClientError.Rpc(message), let ClientError.Transport(message),
                 let ClientError.InvalidParams(message), let ClientError.Serialization(message),
                 let ClientError.EventClosed(message), let ClientError.MinigameGenerationFailed(message):
                self.error = message
            default: self.error = error.localizedDescription
            }
        }
        busy = false
    }

    private func restore() async {
        await perform {
            guard !ucURL.isEmpty, !connectURL.isEmpty,
                  let saved = try DesktopConnectCredentialStore.load() else { return }
            let client = try configuredBridge()
            let account = try await client.verifyLogin(token: saved.token)
            guard account == saved.accountId else {
                throw NSError(domain: "DesktopConnect", code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "账号已变更，请重新登录"])
            }
            let device = try await client.registerMobile(token: saved.token,
                savedId: saved.mobileId, savedSecret: saved.mobileSecret)
            token = saved.token
            accountId = account
            mobile = device
            try saveIdentity()
            hosts = try await client.listDesktops(token: saved.token)
            hostsLoaded = true
            let active = try await client.recoverDesktop(token: saved.token, mobile: device,
                preferredDesktopId: DesktopConnectCredentialStore.preferredDesktopID(for: saved.accountId), forceReconnect: false)
            if let active, active.healthy {
                let name = hosts.first(where: { $0.id == active.desktopId })?.name ?? active.desktopId
                onConnected(name)
                return
            }
        }
        restoring = false
    }

    private func login() async {
        await perform {
            let client = try configuredBridge()
            let result = try await client.loginWithPassword(username: username, password: password)
            let previous = try DesktopConnectCredentialStore.load()
            let device = try await client.registerMobile(token: result.token,
                savedId: previous?.accountId == result.accountId ? previous?.mobileId : nil,
                savedSecret: previous?.accountId == result.accountId ? previous?.mobileSecret : nil)
            token = result.token
            accountId = result.accountId
            mobile = device
            try saveIdentity()
            password = ""
            hosts = try await client.listDesktops(token: result.token)
            hostsLoaded = true
            let active = try await client.recoverDesktop(token: result.token, mobile: device,
                preferredDesktopId: DesktopConnectCredentialStore.preferredDesktopID(), forceReconnect: false)
            if let active, active.healthy {
                let name = hosts.first(where: { $0.id == active.desktopId })?.name ?? active.desktopId
                onConnected(name)
                return
            }
        }
    }

    private func saveIdentity() throws {
        guard let accountId, let token, let mobile else { return }
        try DesktopConnectCredentialStore.save(.init(accountId: accountId, token: token,
            mobileId: mobile.id, mobileSecret: mobile.secret))
    }

    private func refreshHosts() async {
        await perform {
            if let token, let bridge {
                hosts = try await bridge.listDesktops(token: token)
                hostsLoaded = true
            }
        }
    }

    private func connect(_ host: DesktopHost) async {
        defer { connectingHostId = nil }
        await perform {
            guard let token, let mobile, let bridge else { return }
            _ = try await bridge.connectDesktop(token: token, mobile: mobile, desktopId: host.id)
            onConnected(host.name)
        }
    }

    private func logout() async {
        await perform {
            try await bridge?.disconnectDesktop()
            try DesktopConnectCredentialStore.clear()
            token = nil
            accountId = nil
            mobile = nil
            hosts = []
            hostsLoaded = false
        }
    }
}



struct DesktopHostTile: View {
    let name: String
    let status: String
    let action: String
    let online: Bool
    let current: Bool
    let enabled: Bool
    let loading: Bool
    let onSelect: () -> Void

    var body: some View {
        Button(action: onSelect) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 12) {
                    identity(singleLine: true)
                    Spacer(minLength: 0)
                    if action != "不可连接" { actionView }
                }
                VStack(alignment: .leading, spacing: 8) {
                    identity(singleLine: false)
                    if action != "不可连接" {
                        HStack { Spacer(); actionView }
                    }
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: current ? 76 : 68)
            .background(current ? LitterTheme.surfaceLight : LitterTheme.background)
            .clipShape(RoundedRectangle(cornerRadius: current ? 14 : 0))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled || loading)
        .accessibilityLabel("\(name)，\(status)" + (loading ? "，正在连接" : enabled ? "，\(action)" : ""))
    }

    private func identity(singleLine: Bool) -> some View {
        let statusColor = loading || status == "占用中" ? LitterTheme.warning :
            online ? LitterTheme.success :
            status == "连接中断" ? LitterTheme.danger : LitterTheme.textMuted
        return HStack(spacing: 12) {
            Image(systemName: "desktopcomputer")
                .font(.system(size: 19))
                .foregroundColor(online ? LitterTheme.accentStrong : LitterTheme.textMuted)
                .frame(width: 40, height: 40)
                .background(current ? LitterTheme.background : LitterTheme.surface,
                            in: RoundedRectangle(cornerRadius: 11))
            VStack(alignment: .leading, spacing: 4) {
                Text(name).litterFont(size: 15, weight: .semibold)
                    .foregroundColor(LitterTheme.textPrimary)
                    .lineLimit(singleLine ? 1 : 2)
                    .fixedSize(horizontal: singleLine, vertical: false)
                HStack(spacing: 6) {
                    Circle().fill(statusColor).frame(width: 6, height: 6)
                    Text(loading ? "正在连接" : current && online ? "当前连接" : status)
                        .litterFont(size: 12)
                        .foregroundColor(LitterTheme.textSecondary)
                        .lineLimit(1)
                }
            }
        }
    }
    @ViewBuilder private var actionView: some View {
        if loading {
            ProgressView().controlSize(.small).tint(LitterTheme.accentStrong)
                .frame(width: 44, height: 44)
        } else if current {
            Text(action).litterFont(.caption, weight: .semibold)
                .foregroundColor(enabled ? LitterTheme.background : LitterTheme.textMuted)
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
                .background(enabled ? LitterTheme.textPrimary : LitterTheme.surface,
                            in: RoundedRectangle(cornerRadius: 12))
        } else {
            HStack(spacing: 2) {
                Text(action).litterFont(.footnote, weight: .semibold)
                Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold))
            }
            .foregroundColor(enabled ? LitterTheme.accentStrong : LitterTheme.textMuted)
            .frame(minHeight: 44)
        }
    }
}

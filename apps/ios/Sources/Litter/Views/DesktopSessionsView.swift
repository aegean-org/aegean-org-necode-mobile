import Foundation
import SwiftUI
import PhotosUI
import UIKit
import UniformTypeIdentifiers
import AVFoundation
import WebKit
import QuickLook
import CryptoKit

private struct DesktopProject: Decodable, Identifiable {
    let projectID: String
    let name: String?
    let worktree: String
    var id: String { "\(projectID)|\(worktree)" }
    private enum CodingKeys: String, CodingKey { case projectID = "id", name, worktree }

    var title: String {
        let value = name.flatMap { $0.isEmpty ? nil : $0 } ?? worktree
        return String(value.replacingOccurrences(of: "\\", with: "/").split(separator: "/").last ?? Substring(value))
    }
}

private struct DesktopSessionRecord: Decodable {
    let id: String
    let title: String?
    let time: DesktopSessionTime?
    let model: DesktopSessionModel?
    let directory: String?
    let projectID: String?
    let project: DesktopProject?
}

private struct DesktopSessionPage: Decodable {
    let data: [DesktopSessionRecord]
    let nextCursor: String?
}

private struct DesktopBrowserQuery: Equatable {
    let host: String
    let project: String?
    let projects: Bool
    let search: String
    let session: String?
}

private struct DesktopSessionTime: Decodable {
    let updated: Int64?
}

private struct DesktopSessionModel: Decodable {
    let id: String
    let providerID: String
}

private struct DesktopModel: Decodable, Identifiable {
    let providerID: String
    let modelID: String
    let name: String
    let image: Bool?
    var providerName: String? = nil
    var id: String { "\(providerID)/\(modelID)" }
}

private struct DesktopLiveRevision: Decodable { let revision: String }

private struct DesktopModelList: Decodable {
    let models: [DesktopModel]
}

private struct DesktopSession: Identifiable {
    let id: String
    let title: String
    let project: DesktopProject
    let updated: Int64
    let model: DesktopSessionModel?
    var displayTitle: String {
        guard title.range(of: #"^(?:New|Child) session - \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$"#, options: .regularExpression) != nil else { return title }
        return title.hasPrefix("Child session") ? "子会话" : "新会话"
    }

}

private struct DesktopAttachment: Identifiable {
    let id = UUID()
    let filename: String
    let mime: String
    let data: Data

    var part: [String: String] {
        ["type": "file", "mime": mime, "filename": filename,
         "url": "data:\(mime);base64,\(data.base64EncodedString())"]
    }
}

private struct DesktopProjectedAttachment: Equatable, Decodable, Sendable {
    let filename: String
    let mime: String
    let size: Int?
    let dataBase64: String?
    let unavailableReason: String?
    var path: String? = nil
    private enum CodingKeys: String, CodingKey {
        case filename, mime, size, path
        case dataBase64 = "data_base64", unavailableReason = "unavailable_reason"
    }
    var isHtml: Bool { mime == "text/html" || ["html", "htm"].contains(URL(fileURLWithPath: filename).pathExtension.lowercased()) }
    var isImage: Bool { mime.hasPrefix("image/") }
    var detail: String {
        let ext = URL(fileURLWithPath: filename).pathExtension.uppercased()
        let kind = ext.isEmpty ? (isImage ? "图片" : "文件") : String(ext.prefix(12))
        guard let size else { return kind }
        let formatted = size < 1_000 ? "\(size) B" : size < 1_000_000 ? String(format: "%.1f KB", Double(size) / 1_000) : String(format: "%.1f MB", Double(size) / 1_000_000)
        return "\(kind) · \(formatted)"
    }
}

private struct DesktopAttachmentPreviewItem: Identifiable, Sendable {
    let id = UUID()
    let attachment: DesktopProjectedAttachment
}

private enum DesktopMessageKind: String, Decodable { case text, tool, file }

private struct DesktopMessage: Equatable, Decodable, Identifiable {
    let id: String
    let details: [String]
    let role: String
    let text: String
    let kind: DesktopMessageKind
    let status: String?
    var attachment: DesktopProjectedAttachment? = nil
}

// Local delivery presentation only; admission/retry remains in the durable Rust journal.
private struct DesktopLocalPrompt {
    var messageID: String
    let text: String
    let filenames: [String]
    var status = "正在发送…"
}

private struct DesktopComposerDraft {
    var localPrompt: DesktopLocalPrompt?
    var text = ""
    var attachments: [DesktopAttachment] = []
    var model: DesktopModel?
    var pending = false
    var submitting = false
    var needsPromptConfirmation: Bool { pending && !submitting }
    var awaiting = false
    var promptId: String?
    var stopRequested = false
}

struct DesktopSessionsView: View {
    let bridge: DesktopConnectBridge
    let hostName: String
    let topInset: CGFloat
    let bottomInset: CGFloat
    let onDisconnected: () -> Void
    let onHostChanged: (String) -> Void

    @State private var projects: [DesktopProject] = []
    @State private var sessions: [DesktopSession] = []
    @State private var displayedSessionQuery: DesktopBrowserQuery?
    @State private var refreshingSessions = false
    @State private var nextSessionCursor: String?
    @State private var loadingMoreSessions = false
    @State private var moreSessionsError: String?
    @State private var browserGeneration = UUID()
    @State private var projectGeneration = UUID()
    @State private var projectsLoaded = false
    @State private var projectsLoading = false
    @State private var browserHost: String?
    @State private var showNewSessionMenu = false
    @State private var directoryPickerHost: String?
    private var browserQuery: DesktopBrowserQuery {
        DesktopBrowserQuery(host: activeHostID ?? "", project: selectedProjectID,
            projects: showProjects && selectedProjectID == nil,
            search: (selectedProjectID != nil ? projectSessionSearch : recentSearch).trimmingCharacters(in: .whitespacesAndNewlines),
            session: selectedSession?.id)
    }
    @State private var selectedSession: DesktopSession?
    @State private var selectedProjectID: String?
    @State private var showProjects = false
    @State private var recentSearch = ""
    @State private var projectSearch = ""
    @State private var projectSessionSearch = ""
    private var search: String { selectedProjectID != nil ? projectSessionSearch : showProjects ? projectSearch : recentSearch }
    private var searchBinding: Binding<String> { Binding(
        get: { search },
        set: { value in
            if selectedProjectID != nil { projectSessionSearch = value }
            else if showProjects { projectSearch = value }
            else { recentSearch = value }
        })
    }
    @State private var messages: [DesktopMessage] = []
    @State private var attachmentPreview: DesktopAttachmentPreviewItem?
    @State private var rawMessages = "[]"
    @State private var liveSnapshot: String?
    @State private var messageScope = ""
    @State private var nextMessageCursor: String?
    @State private var catchupPage: String?
    @State private var attention = DesktopSessionAttention.idle
    @State private var showAttention = false
    @State private var stoppingScope: String?
    @State private var submissions: [String: Task<Void, Never>] = [:]
    private var composerShowsStop: Bool {
        submissions[composerKey] != nil || stoppingScope == composerKey
            || ((awaitingResponse || attention.status == "busy" || attention.status == "retry" || attention.count > 0)
                && drafts[composerKey]?.stopRequested != true)
    }
    private var composerActionEnabled: Bool {
        if composerShowsStop { return healthy && stoppingScope == nil }
        return healthy && !hasPendingPrompt && !busy && !modelsLoading && !speech.isTranscribing
            && (!prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !attachments.isEmpty)
    }
    @State private var fetchingMessages = false
    @State private var loadingOlderMessages = false
    @State private var isNearMessageBottom = true
    @State private var followingMessageTail = true
    @State private var userScrollingMessages = false
    @State private var scrollAfterMessageUpdate = true
    private var prompt: String {
        get { drafts[composerKey]?.text ?? "" }
        nonmutating set {
            var draft = drafts[composerKey] ?? DesktopComposerDraft()
            draft.text = newValue
            drafts[composerKey] = draft
        }
    }
    private var attachments: [DesktopAttachment] {
        get { drafts[composerKey]?.attachments ?? [] }
        nonmutating set {
            var draft = drafts[composerKey] ?? DesktopComposerDraft()
            draft.attachments = newValue
            drafts[composerKey] = draft
        }
    }
    @State private var drafts: [String: DesktopComposerDraft] = [:]
    private var timelineMessages: [DesktopMessage] {
        guard let local = drafts[composerKey]?.localPrompt,
              !messages.contains(where: { $0.id.hasPrefix(local.messageID + ":") }) else { return messages }
        let text = (local.filenames.map { "附件：" + $0 } + [local.text].filter { !$0.isEmpty }).joined(separator: "\n")
        return messages + [DesktopMessage(id: "local:" + local.messageID, details: [], role: "user", text: text, kind: .text, status: local.status)]
    }
    private var waitingForReplyBody: Bool {
        let rows = timelineMessages
        guard (awaitingResponse || attention.status == "busy"), !rows.isEmpty,
              error == nil, attention.count == 0 else { return false }
        let start = rows.lastIndex(where: { $0.role == "user" }).map { $0 + 1 } ?? 0
        return !rows.dropFirst(start).contains {
            $0.role == "assistant" && $0.kind == .text && !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        }
    }
    @State private var attachmentOwner: String?
    @State private var reconcilingPrompts: Set<String> = []
    private var composerKey: String { "\(activeHostID ?? "")|\(selectedSession?.id ?? "")" }
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var showPhotoPicker = false
    @State private var showFilePicker = false
    @State private var speech = VoiceTranscriptionManager()
    @State private var recordingOwner: String?
    @State private var actionOwners: Set<String> = []
    @State private var creatingProjects: [String: String] = [:]
    private var actionKey: String { "\(activeHostID ?? "")|\(selectedSession?.id ?? "")|\(selectedProjectID ?? "")|\(showProjects)|\(showHostPicker)" }
    private var busy: Bool { actionOwners.contains(actionKey) }
    private var hasPendingPrompt: Bool {
        get { drafts[composerKey]?.pending ?? false }
        nonmutating set {
            var draft = drafts[composerKey] ?? DesktopComposerDraft()
            draft.pending = newValue
            drafts[composerKey] = draft
        }
    }
    private var awaitingResponse: Bool {
        get { drafts[composerKey]?.awaiting ?? false }
        nonmutating set {
            var draft = drafts[composerKey] ?? DesktopComposerDraft()
            draft.awaiting = newValue
            drafts[composerKey] = draft
        }
    }
    @State private var modelOptions: [DesktopModel] = []
    @State private var modelError: String?
    @State private var modelsLoadingScope: String?
    @State private var showModels = false
    @State private var modelSearch = ""
    private var modelsLoading: Bool { modelsLoadingScope == composerKey }
    private var selectedModel: DesktopModel? {
        get { drafts[composerKey]?.model ?? nil }
        nonmutating set {
            var draft = drafts[composerKey] ?? DesktopComposerDraft()
            draft.model = newValue
            drafts[composerKey] = draft
        }
    }
    private var awaitedPromptID: String? {
        get { drafts[composerKey]?.promptId ?? nil }
        nonmutating set {
            var draft = drafts[composerKey] ?? DesktopComposerDraft()
            draft.promptId = newValue
            drafts[composerKey] = draft
        }
    }
    @FocusState private var composerFocused: Bool
    @State private var initialLoadComplete = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var connectionFailed = false
    @State private var reconnecting = false
    @State private var healthy = true
    @State private var error: String?
    @State private var showHostPicker = false
    @State private var hosts: [DesktopHost] = []
    @State private var activeHostID: String?
    @State private var connectingHostId: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                if selectedSession != nil || selectedProjectID != nil || showProjects {
                    Button {
                        if selectedSession != nil { selectedSession = nil }
                        else if selectedProjectID != nil { selectedProjectID = nil }
                        else { showProjects = false }
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(LitterTheme.textPrimary)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(selectedSession != nil ? "返回会话" : selectedProjectID != nil ? "返回项目" : "返回最近会话")
                }
                Text(selectedSession?.displayTitle ?? projects.first(where: { $0.id == selectedProjectID })?.title ?? (showProjects ? "项目" : "会话"))
                    .litterFont(size: selectedSession == nil ? 22 : 18, weight: .semibold)
                    .foregroundColor(LitterTheme.textPrimary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let session = selectedSession {
                    Menu {
                        Button {
                            Task { await perform { try await refreshMessages(for: session) } }
                        } label: { Label("刷新消息", systemImage: "arrow.clockwise") }
                        .disabled(busy || !healthy)
                    } label: {
                        Image(systemName: "ellipsis")
                            .foregroundColor(LitterTheme.textSecondary)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("会话菜单")
                } else {
                    Button { Task { await loadProjectsAndSessions() } } label: {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(LitterTheme.accent)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("刷新")
                    .disabled(busy || !healthy)
                }
            }
            .padding(.leading, selectedSession != nil || selectedProjectID != nil || showProjects ? 4 : 16).padding(.trailing, 12)
            .padding(.top, selectedSession == nil ? 12 : 6).padding(.bottom, 4)

            Button { openHostPicker() } label: {
                HStack(spacing: 10) {
                    Image(systemName: "desktopcomputer")
                        .font(.system(size: 18))
                        .foregroundColor(LitterTheme.accentStrong)
                        .frame(width: 36, height: 36)
                        .background(LitterTheme.surfaceLight, in: RoundedRectangle(cornerRadius: 10))
                    Group {
                        if let session = selectedSession {
                            HStack(spacing: 4) {
                                Text(hostName).foregroundColor(LitterTheme.textPrimary)
                                    .lineLimit(1).truncationMode(.middle).frame(maxWidth: .infinity, alignment: .leading)
                                Text("·").foregroundColor(LitterTheme.textMuted)
                                Text(session.project.title).foregroundColor(LitterTheme.textSecondary)
                                    .lineLimit(1).truncationMode(.middle).frame(maxWidth: .infinity, alignment: .leading)
                            }.litterFont(size: 13, weight: .medium)
                        } else {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(hostName).litterFont(size: 14, weight: .medium)
                                    .foregroundColor(LitterTheme.textPrimary).lineLimit(1).truncationMode(.middle)
                                Text("当前电脑").litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Circle().fill(healthy ? LitterTheme.success : LitterTheme.danger)
                        .frame(width: 7, height: 7)
                    Text(healthy ? "已连接" : "连接中断")
                        .litterFont(.caption)
                        .foregroundColor(healthy ? LitterTheme.textSecondary : LitterTheme.danger)
                        .fixedSize()
                    Image(systemName: "chevron.down")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(LitterTheme.textSecondary)
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
                .frame(minHeight: selectedSession == nil ? 56 : 48)
                .background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 14))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("选择电脑，当前\(hostName)，\(healthy ? "已连接" : "连接中断")"
                + (selectedSession.map { "，项目" + $0.project.title + "，目录" + $0.project.worktree } ?? ""))
            .disabled(busy)
            .padding(.horizontal, 16).padding(.vertical, 8)
            if let error, selectedSession == nil {
                Text(error).litterFont(.caption)
                    .foregroundColor(LitterTheme.danger)
                    .padding(.horizontal, 16)
            }
            if !healthy {
                VStack(spacing: 10) {
                    Image(systemName: "desktopcomputer")
                        .font(.system(size: 27))
                        .foregroundColor(LitterTheme.textSecondary)
                        .frame(width: 64, height: 64)
                        .background(LitterTheme.surfaceLight, in: RoundedRectangle(cornerRadius: 20))
                    Text(reconnecting ? "正在重新连接电脑…" : "与电脑的连接已中断")
                        .litterFont(size: 18, weight: .semibold)
                        .foregroundColor(LitterTheme.textPrimary)
                    Text("选择一台在线电脑，继续查看项目与会话")
                        .litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
                        .multilineTextAlignment(.center)
                    Button { openHostPicker() } label: {
                        Text("选择电脑").litterFont(.subheadline, weight: .semibold)
                            .foregroundColor(LitterTheme.textOnAccent)
                            .padding(.horizontal, 24).frame(minHeight: 48)
                            .background(LitterTheme.accentStrong, in: RoundedRectangle(cornerRadius: 12))
                    }
                    .disabled(busy)
                    .padding(.top, 10)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(24)
            } else if let selectedSession { conversation(selectedSession) }
            else { sessionList }
        }
        .padding(.top, topInset)
        .padding(.bottom, bottomInset)
        .background(LitterTheme.background)
        .task(id: scenePhase) {
            guard scenePhase == .active else { return }
            var checkOnResume = true
            while !Task.isCancelled {
                if showHostPicker {
                    do { try await Task.sleep(for: .seconds(2)) } catch { return }
                    checkOnResume = true
                    continue
                }
                let active = await bridge.activeDesktop()
                healthy = active?.healthy == true && !connectionFailed
                activeHostID = active?.desktopId ?? activeHostID
                if healthy && checkOnResume {
                    checkOnResume = false
                    let host = activeHostID
                    do { _ = try await request("GET", "/session/status", expectedHost: host) }
                    catch {
                        if Task.isCancelled { return }
                        if activeHostID == host {
                            if connectionFailed { healthy = false }
                            self.error = error.localizedDescription
                        }
                    }
                }
                if !healthy {
                    reconnecting = true
                    do {
                        guard let credentials = try DesktopConnectCredentialStore.load() else {
                            throw NSError(domain: "DesktopConnect", code: 1,
                                userInfo: [NSLocalizedDescriptionKey: "请重新登录 NeCode Desktop"])
                        }
                        let mobile = try await bridge.registerMobile(token: credentials.token,
                            savedId: credentials.mobileId, savedSecret: credentials.mobileSecret)
                        try DesktopConnectCredentialStore.save(.init(accountId: credentials.accountId,
                            token: credentials.token, mobileId: mobile.id, mobileSecret: mobile.secret))
                        guard let restored = try await bridge.recoverDesktop(token: credentials.token, mobile: mobile,
                            preferredDesktopId: active?.desktopId ?? DesktopConnectCredentialStore.preferredDesktopID(for: credentials.accountId),
                            forceReconnect: connectionFailed) else { reconnecting = false; return }
                        try Task.checkCancellation()
                        activeHostID = restored.desktopId
                        DesktopConnectCredentialStore.setPreferredDesktopID(restored.desktopId, for: credentials.accountId)
                        healthy = restored.healthy
                        connectionFailed = false
                        self.error = nil
                        if let session = selectedSession { try await refreshMessages(for: session) }
                        else { await loadProjectsAndSessions() }
                    } catch {
                        if Task.isCancelled { reconnecting = false; return }
                        self.error = "自动重连失败：\(error.localizedDescription)"
                        LLog.warn("DesktopConnect", "automatic recovery failed", fields: ["error": error.localizedDescription])
                    }
                    reconnecting = false
                    do { try await Task.sleep(for: .seconds(10)) } catch { return }
                    continue
                }
                do { try await Task.sleep(for: .seconds(2)) } catch { return }
            }
        }
        .sheet(isPresented: Binding(get: { directoryPickerHost != nil }, set: { if !$0 { directoryPickerHost = nil } })) {
            if let owner = directoryPickerHost {
                DesktopDirectoryPickerView(bridge: bridge, hostID: owner, hostName: hostName) { folder in
                    guard activeHostID == owner && directoryPickerHost == owner else { return }
                    let project = DesktopProject(projectID: folder.id, name: folder.name, worktree: folder.directory)
                    projects.removeAll { $0.id == project.id }
                    projects.append(project)
                    selectedProjectID = project.id
                    error = nil
                    do { try openedProject(project) } catch { self.error = error.localizedDescription }
                    projectSessionSearch = ""
                    selectedSession = nil
                    showProjects = true
                    directoryPickerHost = nil
                }
            }
        }
        .onChange(of: activeHostID) { _, _ in directoryPickerHost = nil }
        .sheet(isPresented: $showHostPicker, onDismiss: { error = nil }) { hostPicker }
        .sheet(isPresented: $showModels) { modelPicker }
        .sheet(item: $attachmentPreview) { item in
            let host = activeHostID
            let project = selectedSession?.project
            DesktopMobileAttachmentPreview(item: item, loadArtifact: { attachment in
                guard let path = attachment.path, let project, let host else { throw desktopInputError("请从文件所属会话打开预览") }
                let safe = CharacterSet.urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&=+?#"))
                guard let encodedPath = path.addingPercentEncoding(withAllowedCharacters: safe) else { throw desktopInputError("文件路径无效") }
                let content = try await request("GET", "/file/content" + directory(project) + "&path=" + encodedPath, expectedHost: host)
                let metadata = try JSONSerialization.data(withJSONObject: ["filename": attachment.filename, "mime": attachment.mime,
                    "size": attachment.size.map { NSNumber(value: $0) as Any } ?? NSNull()])
                let normalized = try resolveDesktopArtifact(attachmentJson: String(decoding: metadata, as: UTF8.self),
                    contentJson: String(decoding: content, as: UTF8.self))
                return try JSONDecoder().decode(DesktopProjectedAttachment.self, from: Data(normalized.utf8))
            })
        }
        .onChange(of: composerKey) { _, _ in attachmentPreview = nil }
        .onChange(of: composerKey) { _, _ in showModels = false; modelSearch = "" }
        .sheet(isPresented: $showAttention) {
            if let session = selectedSession {
                let host = activeHostID
                DesktopAttentionSheet(state: attention, busy: busy) { path, payload in
                    Task { await perform {
                        let body = String(decoding: try JSONSerialization.data(withJSONObject: payload), as: UTF8.self)
                        _ = try await request("POST", path + directory(session.project), body, expectedHost: host)
                        guard activeHostID == host, selectedSession?.id == session.id else { return }
                        try await refreshAttention(for: session)
                        guard activeHostID == host, selectedSession?.id == session.id else { return }
                        showAttention = false
                        try await refreshMessages(for: session)
                    } }
                }.id("\(host ?? "")|\(session.id)")
            }
        }
        .task(id: browserQuery) {
            if browserHost != activeHostID {
                browserHost = activeHostID
                browserGeneration = UUID()
                projectGeneration = UUID()
                projectsLoaded = false
                projectsLoading = false
                projects = []
                sessions = []
                nextSessionCursor = nil
            }
            guard selectedSession == nil else { return }
            browserGeneration = UUID()
            nextSessionCursor = nil
            if browserQuery.projects { initialLoadComplete = projectsLoaded }
            else if displayedSessionQuery != browserQuery { sessions = []; initialLoadComplete = false }
            do {
                if !browserQuery.search.isEmpty { try await Task.sleep(for: .milliseconds(250)) }
                await loadProjectsAndSessions()
            } catch is CancellationError { return }
            catch { self.error = error.localizedDescription }
        }
        .confirmationDialog("选择项目 · 新建会话", isPresented: $showNewSessionMenu, titleVisibility: .visible) {
            ForEach(projects) { project in
                Button(project.title) { Task { await createSession(in: project) } }
            }
            Button("添加项目…") { directoryPickerHost = activeHostID }
            Button("取消", role: .cancel) {}
        }
        .photosPicker(isPresented: $showPhotoPicker, selection: $selectedPhoto, matching: .images)
        .onChange(of: selectedPhoto) { _, photo in
            guard let photo, let owner = attachmentOwner else { return }
            Task {
                defer { if selectedPhoto == photo { selectedPhoto = nil } }
                do {
                    guard let data = try await photo.loadTransferable(type: Data.self) else {
                        throw desktopInputError("无法读取图片")
                    }
                    guard let image = UIImage(data: data) else { throw desktopInputError("无法解码图片") }
                    let scale = min(1, 1600 / max(image.size.width, image.size.height))
                    let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                    let jpeg = UIGraphicsImageRenderer(size: size).image { _ in
                        image.draw(in: CGRect(origin: .zero, size: size))
                    }.jpegData(compressionQuality: 0.75)
                    guard let jpeg else { throw desktopInputError("无法处理图片") }
                    try addAttachment(DesktopAttachment(filename: "图片.jpg", mime: "image/jpeg", data: jpeg), owner: owner)
                } catch { if composerKey == owner { self.error = error.localizedDescription } }
            }
        }
        .fileImporter(isPresented: $showFilePicker, allowedContentTypes: [.item]) { result in
            guard let owner = attachmentOwner else { error = "附件所属会话已失效，请重新选择"; return }
            do {
                let url = try result.get()
                guard url.startAccessingSecurityScopedResource() else { throw desktopInputError("没有文件访问权限") }
                defer { url.stopAccessingSecurityScopedResource() }
                let handle = try FileHandle(forReadingFrom: url)
                defer { try? handle.close() }
                let data = try handle.read(upToCount: 2_000_001) ?? Data()
                let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
                try addAttachment(DesktopAttachment(filename: url.lastPathComponent, mime: mime, data: data), owner: owner)
            } catch { if composerKey == owner { self.error = error.localizedDescription } }
        }
        .onDisappear { speech.cancelRecording() }
        .task(id: composerKey + "|live") { await followLiveMessages(owner: composerKey) }
        .task(id: composerKey) {
            let owner = composerKey
            while let session = selectedSession, composerKey == owner {
                do { try await Task.sleep(for: .seconds(2)) } catch { return }
                if Task.isCancelled { return }
                if busy || !healthy || scenePhase != .active { continue }
                do { try await refreshMessages(for: session) }
                catch { if !Task.isCancelled, composerKey == owner { self.error = error.localizedDescription } }
            }
        }
    }

    private func followLiveMessages(owner: String) async {
        guard let session = selectedSession, let host = activeHostID else { return }
        let messageOwner = "\(host)|\(session.id)|\(session.project.worktree)"
        var cursor: String?
        while composerKey == owner, !Task.isCancelled {
            if !healthy || scenePhase != .active || messageScope != messageOwner {
                do { try await Task.sleep(for: .milliseconds(500)) } catch { return }
                continue
            }
            do {
                let query = cursor.map { "&cursor=" + ($0.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "") } ?? ""
                let data = try await request("GET", "/connect/session/\(session.id)/live" + directory(session.project) + query, expectedHost: host)
                guard !Task.isCancelled, composerKey == owner, messageScope == messageOwner else { return }
                if scenePhase != .active { continue }
                let snapshot = try JSONDecoder().decode(DesktopLiveRevision.self, from: data)
                if cursor == snapshot.revision { continue }
                cursor = snapshot.revision
                liveSnapshot = String(decoding: data, as: UTF8.self)
                try await refreshMessageProjection(for: session)
                // The next absolute snapshot includes accumulated deltas; pace UI redraws, not text delivery.
                try await Task.sleep(for: .milliseconds(100))
            } catch is CancellationError { return }
            catch {
                guard !Task.isCancelled, composerKey == owner else { return }
                cursor = nil
                liveSnapshot = nil
                self.error = "实时回复暂不可用：\(error.localizedDescription)"
                do { try await Task.sleep(for: .seconds(2)) } catch { return }
            }
        }
    }

    private func openHostPicker() {
        showAttention = false
        showHostPicker = true
        Task { await loadHosts() }
    }

    private var hostPicker: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("选择电脑").litterFont(size: 22, weight: .semibold)
                    .foregroundColor(LitterTheme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button { Task { await loadHosts() } } label: {
                    Image(systemName: "arrow.clockwise").frame(width: 44, height: 44)
                }
                .accessibilityLabel("刷新电脑")
                .disabled(busy)
                Button { showHostPicker = false } label: {
                    Image(systemName: "xmark").frame(width: 44, height: 44)
                        .background(LitterTheme.surface, in: Circle())
                }
                .accessibilityLabel("关闭").disabled(busy)
            }
            if let error {
                HStack(spacing: 8) {
                    Text(error).litterFont(.caption).foregroundColor(LitterTheme.danger)
                        .lineLimit(3).frame(maxWidth: .infinity, alignment: .leading)
                    Button("重试") { Task { await loadHosts() } }
                        .litterFont(.footnote, weight: .semibold)
                        .foregroundColor(LitterTheme.accentStrong)
                        .frame(minHeight: 44).disabled(busy)
                }
                .padding(.leading, 12).padding(.trailing, 8)
                .background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 10))
            }
            if hosts.isEmpty && busy {
                desktopLoading("正在查找在线电脑…")
                    .frame(maxWidth: .infinity, minHeight: 140)
            }
            if hosts.isEmpty && !busy {
                VStack(spacing: 8) {
                    Image(systemName: "desktopcomputer").font(.system(size: 26))
                    Text(error == nil ? "没有发现电脑" : "无法获取电脑列表").foregroundColor(LitterTheme.textPrimary)
                    Text(error == nil ? "确认桌面端在线后刷新" : "检查网络后重试").litterFont(.caption)
                }
                .foregroundColor(LitterTheme.textSecondary)
                .frame(maxWidth: .infinity).padding(24)
                .background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 16))
            }
            if !hosts.isEmpty {
                let orderedHosts = hosts.sorted { left, right in
                    if left.id == activeHostID { return true }
                    if right.id == activeHostID { return false }
                    if (left.status == "available") != (right.status == "available") {
                        return left.status == "available"
                    }
                    return left.name < right.name
                }
                let currentHost = orderedHosts.first { $0.id == activeHostID }
                let otherHosts = orderedHosts.filter { $0.id != activeHostID }
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        if let currentHost { hostRow(currentHost) }
                        if !otherHosts.isEmpty {
                            if currentHost != nil {
                                Text("其他电脑").litterFont(.caption, weight: .semibold)
                                    .foregroundColor(LitterTheme.textSecondary)
                                    .padding(.top, 8)
                            }
                            VStack(spacing: 0) {
                                ForEach(otherHosts, id: \.id) { host in
                                    if host.id != otherHosts.first?.id {
                                        Divider().padding(.leading, 70)
                                    }
                                    hostRow(host)
                                }
                            }
                        }
                    }
                }
            }

            Divider()
            Button("断开当前电脑") {
                Task { await perform {
                    if let accountId = try DesktopConnectCredentialStore.load()?.accountId {
                        DesktopConnectCredentialStore.setPreferredDesktopID(nil, for: accountId)
                    }
                    try await bridge.disconnectDesktop()
                    onDisconnected()
                } }
            }
            .litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
            .frame(maxWidth: .infinity, minHeight: 44).disabled(busy)
        }
        .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 28)
        .background(LitterTheme.background)
        .presentationDetents([.height(hosts.isEmpty ? 300 : min(640, CGFloat(230 + hosts.count * 94))), .large])
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(busy)
    }

    private func hostRow(_ host: DesktopHost) -> some View {
        let current = host.id == activeHostID
        let available = host.status == "available"
        return DesktopHostTile(
            name: host.name,
            status: current ? (healthy ? "在线" : "连接中断") :
                available ? "在线" : host.status == "busy" ? "占用中" : "离线",
            action: current ? (healthy ? "继续使用" : "重连") :
                available ? "连接" : "不可连接",
            online: current ? healthy : available,
            current: current,
            enabled: !busy && connectingHostId == nil && (current || available),
            loading: connectingHostId == host.id
        ) {
            if current && healthy { showHostPicker = false }
            else {
                guard connectingHostId == nil else { return }
                connectingHostId = host.id
                Task { await switchHost(to: host) }
            }
        }
    }

    private func loadHosts() async {
        await perform {
            guard let credentials = try DesktopConnectCredentialStore.load() else {
                throw NSError(domain: "DesktopConnect", code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "请重新登录 NeCode Desktop"])
            }
            hosts = try await bridge.listDesktops(token: credentials.token)
            activeHostID = await bridge.activeDesktop()?.desktopId
        }
    }

    private func switchHost(to host: DesktopHost) async {
        defer { connectingHostId = nil }
        await perform {
            guard let credentials = try DesktopConnectCredentialStore.load() else {
                throw NSError(domain: "DesktopConnect", code: 1,
                    userInfo: [NSLocalizedDescriptionKey: "请重新登录 NeCode Desktop"])
            }
            let mobile = try await bridge.registerMobile(token: credentials.token,
                savedId: credentials.mobileId, savedSecret: credentials.mobileSecret)
            try DesktopConnectCredentialStore.save(.init(accountId: credentials.accountId,
                token: credentials.token, mobileId: mobile.id, mobileSecret: mobile.secret))
            healthy = false
            _ = try await bridge.connectDesktop(token: credentials.token, mobile: mobile, desktopId: host.id)
            DesktopConnectCredentialStore.setPreferredDesktopID(host.id, for: credentials.accountId)
            activeHostID = host.id
            connectionFailed = false
            healthy = true
            selectedSession = nil
            selectedProjectID = nil
            showProjects = false
            recentSearch = ""
            projectSearch = ""
            projectSessionSearch = ""
            browserGeneration = UUID()
            projectGeneration = UUID()
            projectsLoaded = false
            projectsLoading = false
            nextSessionCursor = nil
            projects = []
            sessions = []
            messages = []
            awaitingResponse = false
            onHostChanged(host.name)
            showHostPicker = false
        }
    }

    private var sessionList: some View {
        let browsingProjects = showProjects && selectedProjectID == nil
        let visible = sessions.filter { selectedProjectID == nil || $0.project.id == selectedProjectID }
        let visibleProjects = projects.filter { project in
            search.isEmpty || project.title.localizedCaseInsensitiveContains(search) ||
                project.worktree.localizedCaseInsensitiveContains(search)
        }

        return VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").foregroundColor(LitterTheme.textSecondary)
                TextField(selectedProjectID != nil ? "搜索项目会话" : browsingProjects ? "搜索项目" : "搜索最近会话", text: searchBinding)
                    .litterFont(.subheadline)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                if !search.isEmpty {
                    Button { searchBinding.wrappedValue = "" } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(LitterTheme.textSecondary)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("清除搜索")
                }
            }
            .frame(minHeight: 48)
            .padding(.horizontal, 14)
            .background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 12))
            .padding(.horizontal, 16)

            HStack(spacing: 8) {
                Text(selectedProjectID != nil ? "项目会话" : browsingProjects ? "项目" : "最近会话")
                    .litterFont(size: 14, weight: .semibold)
                    .foregroundColor(LitterTheme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if refreshingSessions && initialLoadComplete && !browsingProjects {
                    ProgressView().tint(LitterTheme.accentStrong).controlSize(.small)
                }
                if !browsingProjects && selectedProjectID == nil {
                    Button("项目") { showProjects = true }
                        .litterFont(size: 14, weight: .medium)
                        .foregroundColor(LitterTheme.accentStrong)
                        .frame(minHeight: 48)
                }
                Button {
                    if browsingProjects { directoryPickerHost = activeHostID }
                    else if let project = projects.first(where: { $0.id == selectedProjectID }) {
                        Task { await createSession(in: project) }
                    } else {
                        Task {
                            await perform { try await loadProjects() }
                            if projectsLoaded { showNewSessionMenu = true }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                        Text(browsingProjects ? "添加项目" : "新会话").litterFont(size: 14, weight: .medium)
                    }
                    .foregroundColor(LitterTheme.accentStrong).frame(minHeight: 44)
                }
                .disabled(busy || projectsLoading)
            }
            .padding(.leading, 16).padding(.trailing, 12).padding(.top, 12).padding(.bottom, 8)

            if !initialLoadComplete {
                desktopLoading("正在读取电脑上的" + (browsingProjects ? "项目" : "会话") + "…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if error != nil && projects.isEmpty && sessions.isEmpty {
                VStack(spacing: 10) {
                    Text("无法读取电脑上的" + (browsingProjects ? "项目" : "会话"))
                        .litterFont(size: 16, weight: .semibold).foregroundColor(LitterTheme.textPrimary)
                    Text("请检查连接，或点击重试")
                        .litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
                    Button("重新加载") { Task { await loadProjectsAndSessions() } }
                        .litterFont(.subheadline, weight: .semibold)
                        .foregroundColor(LitterTheme.accentStrong)
                        .frame(minHeight: 44).disabled(busy)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity).padding(24)
            } else if (browsingProjects && visibleProjects.isEmpty) || (!browsingProjects && visible.isEmpty) {
                VStack(spacing: 10) {
                    Image(systemName: !search.isEmpty ? "magnifyingglass" : browsingProjects ? "folder" : "desktopcomputer")
                        .font(.system(size: 24))
                        .foregroundColor(LitterTheme.accentStrong)
                        .frame(width: 56, height: 56)
                        .background(LitterTheme.surfaceLight, in: RoundedRectangle(cornerRadius: 18))
                    Text(!search.isEmpty ? "没有找到匹配的" + (browsingProjects ? "项目" : "会话") :
                         browsingProjects && projects.isEmpty ? "这台电脑还没有项目" :
                         browsingProjects ? "暂无项目" :
                         selectedProjectID != nil ? "这个项目暂无会话" : "还没有会话")
                        .litterFont(size: 16, weight: .semibold).foregroundColor(LitterTheme.textPrimary)
                    Text(!search.isEmpty ? "换个关键词，或清除搜索再试" :
                         browsingProjects && projects.isEmpty ? "添加这台电脑上的文件夹，即可开始会话" :
                         browsingProjects ? "换个关键词，或添加另一个文件夹" : "点击“新会话”开始提问")
                        .litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
                }
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(20)
            } else if browsingProjects {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(visibleProjects) { project in
                            HStack(spacing: 0) {
                                Button {
                                    selectedProjectID = project.id
                                    do { try openedProject(project) } catch { self.error = error.localizedDescription }
                                } label: {
                                    HStack(spacing: 12) {
                                        Image(systemName: "folder")
                                            .font(.system(size: 18)).foregroundColor(LitterTheme.accentStrong)
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(project.title)
                                                .litterFont(size: 15, weight: .semibold)
                                                .foregroundColor(LitterTheme.textPrimary)
                                                .lineLimit(1).truncationMode(.middle)
                                            Text(project.worktree)
                                                .litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                                                .lineLimit(1).truncationMode(.middle)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .frame(maxWidth: .infinity, minHeight: 76, alignment: .leading)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(project.title)
                                Button { Task { await createSession(in: project) } } label: {
                                    Group {
                                        if creatingProjects[actionKey] == project.id {
                                            ProgressView().controlSize(.small)
                                                .tint(LitterTheme.textSecondary)
                                                .frame(width: 20, height: 20)
                                        } else {
                                            Image(systemName: "bubble")
                                                .font(.system(size: 18))
                                                .foregroundColor(LitterTheme.textSecondary)
                                        }
                                    }
                                    .frame(width: 48, height: 48)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel((creatingProjects[actionKey] == project.id ? "正在为" : "在") + project.title + "新建会话")
                                .disabled(busy)
                            }
                            .padding(.horizontal, 4)
                            if project.id != visibleProjects.last?.id {
                                Divider().background(LitterTheme.separator)
                            }
                        }
                    }
                    .padding(.horizontal, 16).padding(.bottom, 24)
                }
            } else {
                PaginatedScrollView(hasMore: nextSessionCursor != nil, loading: loadingMoreSessions,
                    enabled: !busy && !refreshingSessions, error: moreSessionsError,
                    loadingText: "正在加载更多会话…", onLoadMore: {
                        let query = browserQuery
                        let generation = browserGeneration
                        Task {
                            do { try await refreshSessions(append: true) }
                            catch {
                                if !Task.isCancelled, browserQuery == query, browserGeneration == generation {
                                    moreSessionsError = error.localizedDescription
                                }
                            }
                        }
                    }) {
                        ForEach(visible) { session in
                            Button {
                                selectedSession = session
                                liveSnapshot = nil
                                do { try openedProject(session.project) } catch { self.error = error.localizedDescription }
                                modelOptions = []
                                modelError = nil
                                messages = []
                                let host = activeHostID
                                Task { await perform {
                                    try await refreshMessages(for: session)
                                    guard activeHostID == host, selectedSession?.id == session.id else { return }
                                    try await loadModels(for: session.project)
                                } }
                            } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    FormattedText(text: session.displayTitle, lineLimit: 2)
                                        .litterFont(size: 15)
                                        .foregroundColor(LitterTheme.textPrimary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    HStack(spacing: 6) {
                                        Image(systemName: "folder")
                                        Text(session.project.title)
                                            .lineLimit(1).truncationMode(.middle)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                        if session.updated > 0 {
                                            Text(sessionTime(session.updated))
                                                .lineLimit(1)
                                        }
                                    }
                                    .litterFont(.caption)
                                    .foregroundColor(LitterTheme.textSecondary)
                                }
                                .padding(.horizontal, 4).padding(.vertical, 12)
                                .frame(minHeight: 76)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(session.displayTitle + "，" + session.project.title)
                        }
                }
                .id("\(activeHostID ?? "")|\(selectedProjectID ?? "")|\(search)")
            }
        }
    }

    private func sessionTime(_ updated: Int64) -> String {
        let elapsed = max(0, Int64(Date().timeIntervalSince1970 * 1000) - updated)
        switch elapsed {
        case ..<60_000: return "刚刚"
        case ..<3_600_000: return "\(elapsed / 60_000) 分钟前"
        case ..<86_400_000: return "\(elapsed / 3_600_000) 小时前"
        case ..<172_800_000: return "昨天"
        case ..<604_800_000: return "\(elapsed / 86_400_000) 天前"
        default:
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "zh_CN")
            formatter.dateFormat = "M月d日"
            return formatter.string(from: Date(timeIntervalSince1970: TimeInterval(updated) / 1000))
        }
    }

    private var modelPicker: some View {
        let filtered = modelOptions.filter {
            modelSearch.isEmpty || $0.name.localizedCaseInsensitiveContains(modelSearch)
                || ($0.providerName ?? $0.providerID).localizedCaseInsensitiveContains(modelSearch)
        }
        let providers = filtered.reduce(into: [String]()) { result, model in
            let name = model.providerName ?? model.providerID
            if !result.contains(name) { result.append(name) }
        }
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("选择模型").litterFont(.title3).fontWeight(.semibold)
                Spacer()
                Button { showModels = false } label: {
                    Image(systemName: "xmark").frame(width: 44, height: 44)
                }.accessibilityLabel("关闭模型选择").foregroundColor(LitterTheme.textSecondary)
            }
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundColor(LitterTheme.textSecondary)
                TextField("搜索模型", text: $modelSearch).litterFont(.body)
            }.padding(12).background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 14))
            if modelsLoading {
                HStack(spacing: 10) {
                    ProgressView().tint(LitterTheme.accentStrong)
                    Text("正在加载模型…").litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
                }.frame(maxWidth: .infinity).padding(.vertical, 12)
            }
            if let modelError {
                HStack {
                    Text(modelError).litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                    Spacer()
                    Button("重试") {
                        if let session = selectedSession { Task { await perform { try await loadModels(for: session.project) } } }
                    }.disabled(modelsLoading || busy).tint(LitterTheme.accentStrong)
                }
            }
            if filtered.isEmpty && !modelsLoading {
                Text(modelOptions.isEmpty ? "暂无可选模型，请在桌面端开启模型" : "没有匹配的模型")
                    .litterFont(.footnote).foregroundColor(LitterTheme.textSecondary).padding(.vertical, 16)
            }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 4) {
                    ForEach(providers, id: \.self) { provider in
                        Text(provider).litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                            .padding(.top, 12).padding(.bottom, 6)
                        ForEach(filtered.filter { ($0.providerName ?? $0.providerID) == provider }) { model in
                            let picked = selectedModel?.id == model.id
                            Button { selectedModel = model; showModels = false; modelSearch = "" } label: {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(model.name).litterFont(.subheadline).fontWeight(picked ? .semibold : .regular)
                                            .foregroundColor(LitterTheme.textPrimary)
                                        if model.image == true {
                                            Text("支持图片").litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                                        }
                                    }
                                    Spacer()
                                    if picked { Image(systemName: "checkmark").foregroundColor(LitterTheme.accentStrong) }
                                }.frame(minHeight: 32).padding(.horizontal, 12).padding(.vertical, 10)
                                    .background(picked ? LitterTheme.accentStrong.opacity(0.08) : Color.clear,
                                        in: RoundedRectangle(cornerRadius: 12))
                            }.buttonStyle(.plain).accessibilityAddTraits(picked ? .isSelected : [])
                        }
                    }
                }.padding(.bottom, 24)
            }.scrollDismissesKeyboard(.interactively)
        }.padding(.horizontal, 20).padding(.top, 12)
            .foregroundColor(LitterTheme.textPrimary)
            .presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
            .presentationBackground(LitterTheme.background)
    }

    private func conversation(_ session: DesktopSession) -> some View {
        VStack(spacing: 0) {
            if attention.count > 0 {
                HStack {
                    Text("需要你处理 · \(attention.count) 项").litterFont(.footnote)
                    Spacer()
                    Button("查看") { showAttention = true }
                }.foregroundColor(LitterTheme.accentStrong).padding(.horizontal, 16)
            }
            if attention.status == "retry" {
                HStack {
                    Text(attention.label).litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                    Spacer()
                }.padding(.horizontal, 16)
            }
            if attention.status == "idle", drafts[composerKey]?.stopRequested == true {
                Text("本次运行已停止，消息以桌面记录为准").litterFont(.caption).foregroundColor(LitterTheme.textSecondary).padding(.horizontal, 16)
            }
            if catchupPage != nil { Text("正在补齐断线期间的消息…").litterFont(.caption).foregroundColor(LitterTheme.textSecondary).padding(.horizontal, 16) }
            if drafts[composerKey]?.needsPromptConfirmation == true {
                HStack {
                    Text("有一条待确认消息").litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                    Spacer()
                    Button("核对并重试") { Task { await perform { try await reconcilePrompt(for: session, retryMissing: true); try await refreshMessages(for: session) } } }
                        .disabled(busy)
                }.padding(.horizontal, 16)
            }
            if let modelError {
                HStack {
                    Text(modelError).litterFont(.caption).foregroundColor(LitterTheme.textSecondary)
                    Spacer()
                    Button("重试模型") { Task { await perform { try await loadModels(for: session.project) } } }.disabled(busy || modelsLoading)
                }.padding(.horizontal, 16)
            }
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        Label(session.project.title, systemImage: "folder")
                            .litterFont(.caption)
                            .foregroundColor(LitterTheme.textSecondary)
                            .lineLimit(1)
                            .padding(.horizontal, 10).padding(.vertical, 7)
                            .background(LitterTheme.surfaceLight, in: RoundedRectangle(cornerRadius: 10))
                            .padding(.bottom, 6)
                        if nextMessageCursor != nil {
                            HStack {
                                Spacer()
                                if loadingOlderMessages { Text("正在加载更早消息…").litterFont(.caption) }
                                else { Button("加载更早消息") { Task { await perform { try await refreshMessages(for: session, older: true) } } }
                                    .disabled(busy || fetchingMessages) }
                                Spacer()
                            }.foregroundColor(LitterTheme.accentStrong).frame(minHeight: 44)
                        }
                        if timelineMessages.isEmpty {
                            VStack(spacing: 8) {
                                if awaitingResponse && error == nil {
                                    DesktopReplyWaiting()
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                } else if busy {
                                    desktopLoading("正在读取消息…")
                                } else {
                                    Text(error != nil ? "暂时无法读取消息" : "从这里继续对话")
                                        .litterFont(size: 16, weight: .semibold)
                                        .foregroundColor(LitterTheme.textPrimary)
                                }
                                if error != nil && !busy {
                                    Button("重试") {
                                        Task { await perform { try await refreshMessages(for: session); try await loadModels(for: session.project) } }
                                    }
                                    .litterFont(.subheadline, weight: .semibold)
                                    .foregroundColor(LitterTheme.accentStrong)
                                    .frame(minHeight: 44)
                                } else if !busy && !awaitingResponse {
                                    Text("发送消息，桌面端会同步显示")
                                        .litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
                                }
                            }
                            .frame(maxWidth: .infinity).padding(.top, 80)
                        }
                        ForEach(Array(timelineMessages.enumerated()), id: \.element.id) { index, message in
                            if message.kind == .file {
                                let attachment = message.attachment ?? DesktopProjectedAttachment(filename: message.text,
                                    mime: "application/octet-stream", size: nil, dataBase64: nil,
                                    unavailableReason: "附件内容未随消息同步，暂时无法在手机预览")
                                DesktopMobileAttachmentCard(attachment: attachment, onPreview: {
                                    attachmentPreview = DesktopAttachmentPreviewItem(attachment: attachment)
                                })
                                .frame(maxWidth: .infinity, alignment: message.role == "user" ? .trailing : .leading)
                            } else if message.kind != .text {
                                let status: String? = switch message.status {
                                case "pending": "准备中"
                                case "running": "执行中"
                                case "completed": "已完成"
                                case "error": "失败"
                                default: nil
                                }
                                let failed = message.status == "error"
                                HStack(spacing: 8) {
                                    Image(systemName: message.kind == .tool ? "hammer" : "paperclip")
                                        .font(.system(size: 13))
                                        .foregroundColor(failed ? LitterTheme.danger : LitterTheme.textSecondary)
                                    VStack(alignment: .leading, spacing: 4) {
                                        if message.kind == .tool && !message.details.isEmpty {
                                            DisclosureGroup(message.text) {
                                                ForEach(Array(message.details.enumerated()), id: \.offset) { _, detail in
                                                    Text(detail).litterFont(.caption).textSelection(.enabled)
                                                        .frame(maxWidth: .infinity, alignment: .leading)
                                                }
                                            }
                                        } else { Text(message.text) }
                                    }
                                        .litterFont(size: 12)
                                        .foregroundColor(failed ? LitterTheme.danger : LitterTheme.textSecondary)
                                        .lineLimit(1)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    if let status, status != "已完成" {
                                        Text(status).litterFont(.caption2)
                                            .foregroundColor(failed ? LitterTheme.danger : LitterTheme.textSecondary)
                                    }
                                }
                                .frame(minHeight: 32)
                                .padding(.horizontal, 4)
                            } else {
                                let isUser = message.role == "user"
                                HStack(alignment: .top, spacing: 0) {
                                    if isUser { Spacer(minLength: 44) }
                                    if isUser {
                                        VStack(alignment: .trailing, spacing: 4) {
                                            LitterMarkdownView(markdown: message.text, style: .content, selectionEnabled: true)
                                                .padding(.horizontal, 14).padding(.vertical, 11)
                                                .background(LitterTheme.surfaceLight, in: RoundedRectangle(cornerRadius: 16))
                                            if message.id.hasPrefix("local:"), let status = message.status {
                                                Text(status).litterFont(.caption2)
                                                    .foregroundColor(status == "未发送" ? LitterTheme.danger : LitterTheme.textSecondary)
                                            }
                                        }
                                        .frame(maxWidth: 320, alignment: .trailing)
                                    } else {
                                        VStack(alignment: .leading, spacing: 8) {
                                            DesktopPacedMarkdownView(
                                                markdown: message.text,
                                                streaming: (awaitingResponse || attention.status == "busy") &&
                                                    index > (timelineMessages.lastIndex(where: { $0.role == "user" }) ?? -1)
                                            )
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 8)
                            }
                        }
                        if waitingForReplyBody {
                            DesktopReplyWaiting()
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        Color.clear.frame(height: 1).id("message-bottom")
                    }
                    .padding(.horizontal, 16).padding(.vertical, 24)
                }
                .defaultScrollAnchor(.bottom, for: .initialOffset)
                .overlay(alignment: .top) {
                    LinearGradient(colors: [LitterTheme.background, LitterTheme.background.opacity(0)], startPoint: .top, endPoint: .bottom)
                        .frame(height: 24).allowsHitTesting(false).accessibilityHidden(true)
                }
                .overlay(alignment: .bottom) {
                    LinearGradient(colors: [LitterTheme.background.opacity(0), LitterTheme.background], startPoint: .top, endPoint: .bottom)
                        .frame(height: 24).allowsHitTesting(false).accessibilityHidden(true)
                }
                .onAppear {
                    followingMessageTail = true
                    userScrollingMessages = false
                    scrollAfterMessageUpdate = true
                }
                .onScrollPhaseChange { _, phase in
                    if phase == .tracking || phase == .interacting {
                        userScrollingMessages = true
                        followingMessageTail = false
                        scrollAfterMessageUpdate = false
                    }
                    if phase == .idle && userScrollingMessages {
                        userScrollingMessages = false
                        followingMessageTail = isNearMessageBottom
                    }
                }
                .onScrollGeometryChange(for: Bool.self) { geometry in
                    geometry.contentSize.height - geometry.visibleRect.maxY < 72
                } action: { _, nearBottom in
                    isNearMessageBottom = nearBottom
                }
                .onScrollGeometryChange(for: CGFloat.self) { $0.containerSize.height } action: { _, _ in
                    if followingMessageTail && !userScrollingMessages { scrollAfterMessageUpdate = true }
                }
                .onScrollGeometryChange(for: CGFloat.self) { $0.contentSize.height } action: { _, _ in
                    // Paced line wrapping occurs between incoming source snapshots.
                    if followingMessageTail && !userScrollingMessages { scrollAfterMessageUpdate = true }
                }
                .onChange(of: timelineMessages) { _, _ in
                    if followingMessageTail && !userScrollingMessages {
                        proxy.scrollTo("message-bottom", anchor: .bottom)
                        scrollAfterMessageUpdate = false
                    }
                }
                .onChange(of: scrollAfterMessageUpdate) { _, follow in
                    if follow && !userScrollingMessages {
                        proxy.scrollTo("message-bottom", anchor: .bottom)
                        scrollAfterMessageUpdate = false
                    }
                }
                .onChange(of: session.id) { _, _ in
                    followingMessageTail = true
                    userScrollingMessages = false
                    scrollAfterMessageUpdate = true
                }
                .overlay(alignment: .bottom) {
                    if !followingMessageTail && !timelineMessages.isEmpty {
                        Button {
                            followingMessageTail = true
                            proxy.scrollTo("message-bottom", anchor: .bottom)
                        } label: {
                            Image(systemName: "arrow.down")
                                .font(.system(size: 20, weight: .regular))
                                .foregroundColor(LitterTheme.textPrimary)
                                .frame(width: 40, height: 40)
                                .background(LitterTheme.background.opacity(0.8), in: Circle())
                                .overlay { Circle().strokeBorder(LitterTheme.textPrimary.opacity(0.08), lineWidth: 1) }
                                .frame(width: 48, height: 48)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain).accessibilityLabel("回到最新").padding(.bottom, 8)
                    }
                }
            }
            Button {
                showAttention = false
                modelSearch = ""
                showModels = true
                if modelOptions.isEmpty && !modelsLoading {
                    Task { await perform { try await loadModels(for: session.project) } }
                }
            } label: {
                HStack(spacing: 6) {
                    if modelsLoading { ProgressView().controlSize(.small).tint(LitterTheme.accentStrong) }
                    Text(selectedModel?.name ?? (modelsLoading ? "正在加载模型…" : "选择模型"))
                        .lineLimit(1)
                    Image(systemName: "chevron.down").imageScale(.small)
                }
                .litterFont(.footnote)
                .foregroundColor(LitterTheme.textSecondary)
                .padding(.top, 12)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .padding(.horizontal, 16)
            }.disabled(busy && !modelsLoading)
            if !attachments.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(attachments) { attachment in
                            let preview = DesktopProjectedAttachment(filename: attachment.filename, mime: attachment.mime,
                                size: attachment.data.count, dataBase64: attachment.data.base64EncodedString(), unavailableReason: nil)
                            DesktopMobileAttachmentCard(attachment: preview, onPreview: {
                                attachmentPreview = DesktopAttachmentPreviewItem(attachment: preview)
                            }, onRemove: { attachments.removeAll { $0.id == attachment.id } })
                        }
                    }.padding(.horizontal, 16)
                }
            }
            if let error {
                Text(error).litterFont(.caption).foregroundColor(LitterTheme.danger)
                    .padding(.horizontal, 20)
            }
            HStack(alignment: .bottom, spacing: 10) {
                Button { attachmentOwner = composerKey; showPhotoPicker = true } label: {
                    Image(systemName: "photo").frame(width: 36, height: 44)
                }
                    .accessibilityLabel("选择图片").disabled(busy)
                Button { attachmentOwner = composerKey; showFilePicker = true } label: {
                    Image(systemName: "paperclip").frame(width: 36, height: 44)
                }.accessibilityLabel("选择文件").disabled(busy)
                TextField("发送消息…", text: Binding(get: { prompt }, set: { prompt = $0 }), axis: .vertical)
                    .focused($composerFocused)
                    .lineLimit(1...5)
                    .litterFont(.subheadline)
                    .foregroundColor(LitterTheme.textPrimary)
                    .frame(minHeight: 44)
                Button {
                    Task {
                        if speech.isRecording {
                            let owner = recordingOwner ?? composerKey
                            do {
                                guard let credentials = try DesktopConnectCredentialStore.load() else { throw desktopInputError("请先登录") }
                                if let text = await speech.stopAndTranscribeDesktop(token: credentials.token) {
                                    var draft = drafts[owner] ?? DesktopComposerDraft()
                                    draft.text = [draft.text, text].filter { !$0.isEmpty }.joined(separator: " ")
                                    drafts[owner] = draft
                                }
                            } catch { if composerKey == owner { self.error = error.localizedDescription } }
                            recordingOwner = nil
                            return
                        }
                        let owner = composerKey
                        guard await speech.requestMicPermission() else { speech.error = "请在设置中允许麦克风权限"; return }
                        guard owner == composerKey else { return }
                        recordingOwner = owner
                        speech.startRecording()
                    }
                } label: {
                    Image(systemName: speech.isRecording ? "stop.circle.fill" : "mic")
                        .foregroundColor(speech.isRecording ? LitterTheme.danger : LitterTheme.accentStrong)
                        .frame(width: 36, height: 44)
                }.accessibilityLabel(speech.isRecording ? "结束语音输入" : "语音输入").disabled(busy || speech.isTranscribing)
                Button {
                    composerFocused = false
                    if composerShowsStop {
                        Task { await stopSession(session) }
                    } else {
                        let owner = composerKey
                        submissions[owner] = Task {
                            defer { submissions[owner] = nil }
                            guard composerKey == owner else { return }
                            await send(to: session)
                        }
                    }
                } label: {
                    Group {
                        if stoppingScope == composerKey { ProgressView().tint(LitterTheme.textOnAccent) }
                        else { Image(systemName: composerShowsStop ? "stop.fill" : "arrow.up") }
                    }
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(LitterTheme.textOnAccent)
                    .frame(width: 44, height: 44)
                    .background(LitterTheme.accentStrong, in: Circle())
                }
                .accessibilityLabel(stoppingScope == composerKey ? "正在停止" : composerShowsStop ? "停止回复" : "发送")
                .disabled(!composerActionEnabled)
                .opacity(composerActionEnabled ? 1 : 0.5)
            }
            .padding(.leading, 16).padding(.trailing, 8).padding(.vertical, 8)
            .background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 18))
            .padding(12)
            if let message = speech.error {
                Text(message).litterFont(.caption).foregroundColor(LitterTheme.danger)
                    .padding(.horizontal, 16)
            }
        }
    }

    private func addAttachment(_ attachment: DesktopAttachment, owner: String) throws {
        var draft = drafts[owner] ?? DesktopComposerDraft()
        guard attachment.data.count <= 2_000_000 else { throw desktopInputError("单个附件不能超过 2 MB") }
        guard draft.attachments.reduce(0, { $0 + $1.data.count }) + attachment.data.count <= 2_500_000 else {
            throw desktopInputError("附件总大小不能超过 2.5 MB")
        }
        draft.attachments.append(attachment)
        drafts[owner] = draft
        error = nil
    }

    private func perform(creatingProject: DesktopProject? = nil, _ action: () async throws -> Void) async {
        let owner = actionKey
        guard actionOwners.insert(owner).inserted else { return }
        if let creatingProject { creatingProjects[owner] = creatingProject.id }
        defer {
            actionOwners.remove(owner)
            creatingProjects.removeValue(forKey: owner)
        }
        error = nil
        do { try await action() } catch { if !Task.isCancelled, actionKey == owner { self.error = error.localizedDescription } }
    }

    private func request(_ method: String, _ path: String, _ body: String = "null", expectedHost: String? = nil) async throws -> Data {
        let response: DesktopResponse
        let host = expectedHost ?? activeHostID
        guard let host else { throw desktopInputError("尚未连接电脑") }
        do { response = try await bridge.requestDesktopFor(desktopId: host, method: method, path: path, bodyJson: body) }
        catch {
            if activeHostID == host, let failure = error as? ClientError, case .Transport = failure { connectionFailed = true }
            throw error
        }
        try Task.checkCancellation()
        guard (200...299).contains(Int(response.status)), let data = response.bodyJson.data(using: .utf8) else {
            throw NSError(domain: "DesktopConnect", code: Int(response.status),
                userInfo: [NSLocalizedDescriptionKey: "桌面返回 \(response.status): \(response.bodyJson)"])
        }
        return data
    }

    private func directory(_ project: DesktopProject) -> String {
        let safe = CharacterSet.urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&=+?#"))
        return "?directory=" + (project.worktree.addingPercentEncoding(withAllowedCharacters: safe) ?? "")
    }

    private func loadProjectsAndSessions() async {
        error = nil
        let query = browserQuery
        do {
            if query.projects {
                if !projectsLoaded { initialLoadComplete = false }
                try await loadProjects(force: true)
                if !Task.isCancelled && browserQuery == query { initialLoadComplete = true }
            } else { try await refreshSessions() }
        } catch {
            if !Task.isCancelled && browserQuery == query {
                self.error = error.localizedDescription; initialLoadComplete = true
            }
        }
    }

    private func loadProjects(force: Bool = false) async throws {
        guard !projectsLoading && (!projectsLoaded || force) else { return }
        projectsLoading = true
        let generation = UUID()
        let host = activeHostID
        projectGeneration = generation
        let started = DispatchTime.now().uptimeNanoseconds
        defer { if projectGeneration == generation { projectsLoading = false } }
        guard let host else { throw desktopInputError("未连接电脑") }
        let folders = try await bridge.desktopProjects(desktopId: host)
        let result = folders.filter { $0.directory != "/" && !$0.directory.isEmpty }.map {
            DesktopProject(projectID: $0.id, name: $0.name, worktree: $0.directory)
        }
        guard activeHostID == host && projectGeneration == generation && !Task.isCancelled else { return }
        projects = try sortRecent(result)
        projectsLoaded = true
        LLog.info("DesktopConnect", "project list loaded", fields: ["elapsedMs": Int((DispatchTime.now().uptimeNanoseconds - started) / 1_000_000), "count": result.count])
    }

    private func refreshSessions(append: Bool = false) async throws {
        if append && (loadingMoreSessions || refreshingSessions || nextSessionCursor == nil) { return }
        moreSessionsError = nil
        let query = browserQuery
        let host = activeHostID
        if append { loadingMoreSessions = true }
        else {
            browserGeneration = UUID()
            nextSessionCursor = nil
            if displayedSessionQuery != query { sessions = []; initialLoadComplete = false }
            refreshingSessions = true
            loadingMoreSessions = false
        }
        let generation = browserGeneration
        defer {
            if browserGeneration == generation && browserQuery == query {
                initialLoadComplete = true
                refreshingSessions = false
                loadingMoreSessions = false
            }
        }
        let cursor = append ? nextSessionCursor : nil
        let started = DispatchTime.now().uptimeNanoseconds
        var url = URLComponents()
        url.path = "/connect/sessions"
        var items: [URLQueryItem] = []
        if let key = query.project {
            guard let project = projects.first(where: { $0.id == key }) else { throw desktopInputError("项目已变更，请返回项目列表重新选择") }
            items.append(URLQueryItem(name: "project", value: project.projectID))
            items.append(URLQueryItem(name: "directory", value: project.worktree))
        }
        if !query.search.isEmpty { items.append(URLQueryItem(name: "search", value: query.search)) }
        if let cursor { items.append(URLQueryItem(name: "cursor", value: cursor)) }
        if !items.isEmpty { url.queryItems = items }
        let page = try JSONDecoder().decode(DesktopSessionPage.self,
            from: try await request("GET", url.string ?? "/connect/sessions", expectedHost: host))
        let result = try page.data.map { row -> DesktopSession in
            guard let projectID = row.projectID, let directory = row.directory else {
                throw desktopInputError("桌面端返回的会话缺少项目或工作目录")
            }
            // Use the session directory, which can differ from the project's main worktree.
            let project = DesktopProject(projectID: projectID, name: row.project?.name, worktree: directory)
            return DesktopSession(id: row.id, title: row.title.flatMap { $0.isEmpty ? nil : $0 } ?? "未命名会话",
                project: project, updated: row.time?.updated ?? 0, model: row.model)
        }
        guard activeHostID == host && browserGeneration == generation && browserQuery == query && !Task.isCancelled else { return }
        if append {
            let ids = Set(sessions.map(\.id))
            sessions += result.filter { !ids.contains($0.id) }
        } else { sessions = result }
        displayedSessionQuery = query
        nextSessionCursor = page.nextCursor
        LLog.info("DesktopConnect", "recent session page loaded", fields: ["elapsedMs": Int((DispatchTime.now().uptimeNanoseconds - started) / 1_000_000), "count": result.count, "append": append, "hasMore": page.nextCursor != nil])
    }

    // UI navigation preference, isolated per account and computer (not session runtime state).
    private func recentPrefix() throws -> String? {
        guard let account = try DesktopConnectCredentialStore.load()?.accountId, let host = activeHostID else { return nil }
        let data = try JSONSerialization.data(withJSONObject: [account, host])
        return "desktop.project.recent." + data.base64EncodedString() + "."
    }

    private func sortRecent(_ rows: [DesktopProject]) throws -> [DesktopProject] {
        guard let prefix = try recentPrefix() else { return rows }
        return rows.enumerated().sorted {
            let left = UserDefaults.standard.double(forKey: prefix + $0.element.id)
            let right = UserDefaults.standard.double(forKey: prefix + $1.element.id)
            return left == right ? $0.offset < $1.offset : left > right
        }.map(\.element)
    }

    private func openedProject(_ project: DesktopProject) throws {
        guard let prefix = try recentPrefix() else { return }
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: prefix + project.id)
        projects = try sortRecent(projects)
    }

    private func createSession(in project: DesktopProject) async {
        await perform(creatingProject: project) {
            let host = activeHostID
            let route = browserQuery
            let data: Data
            do { data = try await request("POST", "/session" + directory(project), "{}", expectedHost: host) }
            catch let error as NSError where error.domain == "DesktopConnect" { throw error }
            catch {
                throw NSError(domain: "DesktopConnect", code: 4,
                    userInfo: [NSLocalizedDescriptionKey: "新建请求结果不明，请刷新会话列表确认，勿重复新建：\(error.localizedDescription)"])
            }
            guard activeHostID == host, browserQuery == route, !Task.isCancelled else { return }
            let created = try JSONDecoder().decode(DesktopSessionRecord.self, from: data)
            let session = DesktopSession(id: created.id, title: created.title ?? "新会话",
                project: project, updated: created.time?.updated ?? 0, model: created.model)
            selectedSession = session
            liveSnapshot = nil
            try openedProject(project)
            sessions.insert(session, at: 0)
            messages = []
            awaitingResponse = false
            modelOptions = []
            selectedModel = nil
            do { try await loadModels(for: project) }
            catch is CancellationError { throw CancellationError() }
            catch { if activeHostID == host, selectedSession?.id == session.id { modelError = "会话已创建，模型尚未准备好：\(error.localizedDescription)" } }
        }
    }

    private func loadModels(for project: DesktopProject) async throws {
        guard let session = selectedSession else { return }
        let hostId = activeHostID
        let owner = composerKey
        guard modelsLoadingScope != owner else { return }
        modelsLoadingScope = owner
        modelError = nil
        defer { if modelsLoadingScope == owner { modelsLoadingScope = nil } }
        do {
            let data = try await request("GET", "/connect/models" + directory(project), expectedHost: hostId)
            guard activeHostID == hostId, selectedSession?.id == session.id else { throw desktopInputError("电脑或会话已切换") }
            let currentData = try await request("GET", "/session/\(session.id)" + directory(project), expectedHost: hostId)
            let current = (try JSONSerialization.jsonObject(with: currentData) as? [String: Any])?["model"] as? [String: Any]
            let currentJSON = try current.map { String(decoding: try JSONSerialization.data(withJSONObject: $0), as: UTF8.self) }
            guard !Task.isCancelled, selectedSession?.id == session.id, activeHostID == hostId else { return }
            let picked = try selectedModel.map {
                String(decoding: try JSONSerialization.data(withJSONObject: ["providerID": $0.providerID, "modelID": $0.modelID]), as: UTF8.self)
            }
            let choice = try selectDesktopModel(modelsJson: String(decoding: data, as: UTF8.self),
                sessionModelJson: currentJSON, pickedModelJson: picked)
            func option(_ model: DesktopModelOption) -> DesktopModel {
                DesktopModel(providerID: model.providerId, modelID: model.modelId, name: model.name,
                    image: model.image, providerName: model.providerName)
            }
            modelOptions = choice.options.map(option)
            selectedModel = choice.selected.map(option)
            modelError = choice.warning
        } catch {
            if !Task.isCancelled, activeHostID == hostId, selectedSession?.id == session.id {
                modelError = "模型加载失败：\(error.localizedDescription)"
            }
            throw error
        }
    }

    private func acceptPrompt(_ session: DesktopSession, _ attempt: DesktopPromptAttempt, accountId: String, hostId: String) throws {
        guard try DesktopConnectCredentialStore.clearPendingPrompt(accountId: accountId, desktopId: hostId, sessionId: session.id, expected: attempt.journalJson) else { return }
        let key = "\(hostId)|\(session.id)"
        var draft = drafts[key] ?? DesktopComposerDraft()
        draft.pending = false
        draft.promptId = attempt.messageId
        draft.localPrompt?.messageID = attempt.messageId
        draft.localPrompt?.status = "已发送，正在同步…"
        draft.awaiting = true
        draft.stopRequested = false
        if try desktopPromptMatchesDraft(bodyJson: attempt.bodyJson, text: draft.text, fileUrls: draft.attachments.map { $0.part["url"] ?? "" }) {
            draft.text = ""
            draft.attachments = []
        }
        drafts[key] = draft
        guard selectedSession?.id == session.id, activeHostID == hostId else { return }
        error = nil
    }

    private func reconcilePrompt(for session: DesktopSession, retryMissing: Bool = false) async throws {
        guard !busy || retryMissing else { return }
        guard let credentials = try DesktopConnectCredentialStore.load(), let hostId = activeHostID else { return }
        let owner = "\(hostId)|\(session.id)"
        guard reconcilingPrompts.insert(owner).inserted else { return }
        defer { reconcilingPrompts.remove(owner) }
        let journal = try DesktopConnectCredentialStore.pendingPrompt(accountId: credentials.accountId, desktopId: hostId, sessionId: session.id)
        guard !Task.isCancelled, selectedSession?.id == session.id, activeHostID == hostId else { return }
        hasPendingPrompt = journal != nil
        guard let journal else { return }
        let attempt = try restoreDesktopPrompt(accountId: credentials.accountId, desktopId: hostId, sessionId: session.id, pendingJson: journal)
        let receiptData = try await request("GET", "/connect/session/\(session.id)/prompt/\(attempt.messageId)" + directory(session.project), expectedHost: hostId)
        let receipt = try parseDesktopPromptReceipt(receiptJson: String(decoding: receiptData, as: UTF8.self), sessionId: session.id, messageId: attempt.messageId)
        try Task.checkCancellation()
        guard selectedSession?.id == session.id, activeHostID == hostId else { return }
        switch receipt.state {
        case .admitted, .promoted: try acceptPrompt(session, attempt, accountId: credentials.accountId, hostId: hostId)
        case .discarded:
            guard try DesktopConnectCredentialStore.clearPendingPrompt(accountId: credentials.accountId, desktopId: hostId, sessionId: session.id, expected: attempt.journalJson) else { return }
            hasPendingPrompt = false
            error = "桌面端已撤销该消息，未执行；草稿保留"
        case .missing:
            if retryMissing {
                guard receipt.idempotent else { throw desktopInputError("此桌面版本不支持精确重试，请先在电脑端核对，不能盲目重复发送") }
                _ = try await request("POST", "/session/\(session.id)/prompt_async" + directory(session.project), attempt.bodyJson, expectedHost: hostId)
                try acceptPrompt(session, attempt, accountId: credentials.accountId, hostId: hostId)
            } else { error = "上一条发送尚未确认，可核对并重试原消息；不会生成重复消息 ID" }
        }
    }

    private func refreshAttention(for session: DesktopSession) async throws {
        let host = activeHostID
        let data = try await request("GET", "/connect/session/\(session.id)/attention" + directory(session.project), expectedHost: host)
        let snapshot = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        guard let snapshot, let statuses = snapshot["statuses"], let permissions = snapshot["permissions"], let questions = snapshot["questions"] else {
            throw desktopInputError("桌面状态快照格式无效")
        }
        func encodeJSON(_ value: Any) throws -> String { String(decoding: try JSONSerialization.data(withJSONObject: value), as: UTF8.self) }
        let json = try desktopSessionAttention(statusesJson: encodeJSON(statuses), permissionsJson: encodeJSON(permissions), questionsJson: encodeJSON(questions), sessionId: session.id)
        let state = try JSONDecoder().decode(DesktopSessionAttention.self, from: Data(json.utf8))
        guard !Task.isCancelled, activeHostID == host, selectedSession?.id == session.id else { return }
        attention = state
        if state.status == "idle", drafts[composerKey]?.stopRequested == true {
            awaitingResponse = false
            awaitedPromptID = nil
        }
    }

    private func stopSession(_ session: DesktopSession) async {
        guard let host = activeHostID, stoppingScope == nil else { return }
        let owner = "\(host)|\(session.id)"
        stoppingScope = owner
        defer { if stoppingScope == owner { stoppingScope = nil } }
        do {
            // Wait for admission before aborting, so a quick stop cannot race ahead of send.
            if let submission = submissions[owner] { await submission.value }
            guard !Task.isCancelled, activeHostID == host, selectedSession?.id == session.id else { return }
            _ = try await request("POST", "/session/\(session.id)/abort" + directory(session.project), "{}", expectedHost: host)
            var draft = drafts[owner] ?? DesktopComposerDraft()
            draft.stopRequested = true
            draft.awaiting = false
            draft.promptId = nil
            drafts[owner] = draft
            if activeHostID == host, selectedSession?.id == session.id { try await refreshAttention(for: session) }
        } catch {
            if !Task.isCancelled, activeHostID == host, selectedSession?.id == session.id {
                self.error = "停止请求失败：\(error.localizedDescription)"
            }
        }
    }

    private func refreshMessageProjection(for session: DesktopSession, forceFollow: Bool = false, older: Bool = false) async throws {
        let host = activeHostID
        let owner = "\(host ?? "")|\(session.id)|\(session.project.worktree)"
        while selectedSession?.id == session.id, activeHostID == host, messageScope == owner {
            let history = rawMessages
            let live = liveSnapshot
            let projected = try await Task.detached(priority: .userInitiated) {
                let input: String
                if let live {
                    let object: [String: Any] = ["messages": try JSONSerialization.jsonObject(with: Data(history.utf8)),
                                                "live": try JSONSerialization.jsonObject(with: Data(live.utf8))]
                    input = String(decoding: try JSONSerialization.data(withJSONObject: object), as: UTF8.self)
                } else { input = history }
                return try projectDesktopMessages(messagesJson: input)
            }.value
            guard !Task.isCancelled, selectedSession?.id == session.id, activeHostID == host, messageScope == owner else { return }
            if history != rawMessages || live != liveSnapshot { continue }
            let updated = try JSONDecoder().decode([DesktopMessage].self, from: Data(projected.utf8))
            if !older {
                scrollAfterMessageUpdate = forceFollow || (followingMessageTail && (scrollAfterMessageUpdate || updated != messages))
            }
            messages = updated
            return
        }
    }

    private func refreshMessages(for session: DesktopSession, forceFollow: Bool = false, older: Bool = false) async throws {
        guard selectedSession?.id == session.id else { return }
        let host = activeHostID
        let owner = "\(activeHostID ?? "")|\(session.id)|\(session.project.worktree)"
        if messageScope != owner {
            messageScope = owner
            rawMessages = "[]"
            liveSnapshot = nil
            attention = .idle
            showAttention = false
            catchupPage = nil
            nextMessageCursor = nil
            fetchingMessages = false
            loadingOlderMessages = false
        }
        guard !fetchingMessages, !older || nextMessageCursor != nil else { return }
        fetchingMessages = true
        loadingOlderMessages = older
        defer { if messageScope == owner { fetchingMessages = false; loadingOlderMessages = false } }
        let safe = CharacterSet.urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&=+?#"))
        let before = older ? "&before=" + (nextMessageCursor?.addingPercentEncoding(withAllowedCharacters: safe) ?? "") : ""
        var pageJSON: String
        if !older, let staged = catchupPage { pageJSON = staged }
        else {
            let page = try await request("GET", "/connect/session/\(session.id)/message" + directory(session.project) + "&limit=30" + before, expectedHost: host)
            pageJSON = String(decoding: page, as: UTF8.self)
        }
        guard !Task.isCancelled, selectedSession?.id == session.id, activeHostID == host, messageScope == owner else { return }
        if !older {
            var gap = try desktopMessageGapCursor(previousJson: rawMessages, pageJson: pageJSON)
            var pages = 0
            while let cursor = gap, pages < 4 {
                pages += 1
                let chunk = try await request("GET", "/connect/session/\(session.id)/message" + directory(session.project) + "&limit=30&before=" + (cursor.addingPercentEncoding(withAllowedCharacters: safe) ?? ""), expectedHost: host)
                guard !Task.isCancelled, selectedSession?.id == session.id, activeHostID == host, messageScope == owner else { return }
                let staged = try mergeDesktopMessagePage(previousJson: mergeDesktopMessagePage(previousJson: "[]", pageJson: pageJSON, older: false).messagesJson, pageJson: String(decoding: chunk, as: UTF8.self), older: true)
                let rows = try JSONSerialization.jsonObject(with: Data(staged.messagesJson.utf8))
                let object: [String: Any] = ["data": rows, "nextCursor": staged.nextCursor as Any? ?? NSNull()]
                pageJSON = String(decoding: try JSONSerialization.data(withJSONObject: object), as: UTF8.self)
                let next = try desktopMessageGapCursor(previousJson: rawMessages, pageJson: pageJSON)
                guard next == nil || next != gap else { throw desktopInputError("消息历史游标未前进，请重试") }
                gap = next
            }
            if gap != nil {
                catchupPage = pageJSON
                try await refreshAttention(for: session)
                guard !Task.isCancelled, selectedSession?.id == session.id, activeHostID == host, messageScope == owner else { return }
                try await reconcilePrompt(for: session)
                return
            }
            catchupPage = nil
        }
        let merged = try mergeDesktopMessagePage(previousJson: rawMessages, pageJson: pageJSON, older: older)
        if rawMessages == "[]" || older { nextMessageCursor = merged.nextCursor }
        rawMessages = merged.messagesJson
        try await refreshMessageProjection(for: session, forceFollow: forceFollow, older: older)
        guard !Task.isCancelled, selectedSession?.id == session.id, activeHostID == host, messageScope == owner else { return }
        let draftKey = "\(host ?? "")|\(session.id)"
        if var draft = drafts[draftKey], let local = draft.localPrompt,
           messages.contains(where: { $0.id.hasPrefix(local.messageID + ":") }) {
            draft.localPrompt = nil
            drafts[draftKey] = draft
        }
        try await refreshAttention(for: session)
        guard !Task.isCancelled, selectedSession?.id == session.id, activeHostID == host, messageScope == owner else { return }
        try await reconcilePrompt(for: session)
        guard !Task.isCancelled, selectedSession?.id == session.id, activeHostID == host, messageScope == owner else { return }
        if awaitingResponse, let id = awaitedPromptID,
           try desktopPromptSettled(messagesJson: rawMessages, promptId: id) {
            awaitingResponse = false
            awaitedPromptID = nil
        }
    }

    private func desktopLoading(_ label: String) -> some View {
        VStack(spacing: 12) {
            ProgressView().tint(LitterTheme.accentStrong)
            Text(label).litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
        }
        .padding(24)
    }

    private func send(to session: DesktopSession) async {
        await perform {
            speech.cancelRecording()
            let sendingOwner = composerKey
            let sendingHost = activeHostID
            let text = prompt
            let files = attachments
            composerFocused = false
            var preview = drafts[sendingOwner] ?? DesktopComposerDraft()
            preview.submitting = true
            preview.localPrompt = DesktopLocalPrompt(messageID: UUID().uuidString, text: text, filenames: files.map(\.filename))
            drafts[sendingOwner] = preview
            defer {
                if var draft = drafts[sendingOwner] {
                    draft.submitting = false
                    drafts[sendingOwner] = draft
                }
            }
            followingMessageTail = true
            scrollAfterMessageUpdate = true
            do {
                var parts: [[String: String]] = []
                if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    parts.append(["type": "text", "text": text])
                }
                parts.append(contentsOf: files.map(\.part))
                var payload: [String: Any] = ["parts": parts]
                if modelOptions.isEmpty { try await loadModels(for: session.project) }
                guard composerKey == sendingOwner, activeHostID == sendingHost else { throw desktopInputError("电脑或会话已切换，消息未发送") }
                guard let model = selectedModel else {
                    throw desktopInputError("请先选择模型；若列表为空，请在桌面端开启模型")
                }
                if model.image == false && files.contains(where: { $0.mime.hasPrefix("image/") }) {
                    throw desktopInputError("所选模型不支持图片，请切换到支持图片的模型")
                }
                payload["model"] = ["providerID": model.providerID, "modelID": model.modelID]
                let body = try JSONSerialization.data(withJSONObject: payload)
                guard body.count < 4_000_000 else { throw desktopInputError("消息与附件合计超过 4 MB，请缩短文字或移除附件") }
                guard let json = String(data: body, encoding: .utf8) else {
                    throw NSError(domain: "DesktopConnect", code: 3,
                        userInfo: [NSLocalizedDescriptionKey: "无法编码消息"])
                }
                guard let credentials = try DesktopConnectCredentialStore.load(), let hostId = activeHostID else { throw desktopInputError("请先登录并连接电脑") }
                let attempt = try prepareDesktopPrompt(accountId: credentials.accountId, desktopId: hostId, sessionId: session.id,
                    bodyJson: json, pendingJson: DesktopConnectCredentialStore.pendingPrompt(accountId: credentials.accountId, desktopId: hostId, sessionId: session.id))
                try DesktopConnectCredentialStore.savePendingPrompt(accountId: credentials.accountId, desktopId: hostId, sessionId: session.id, journal: attempt.journalJson)
                var sendingDraft = drafts[sendingOwner] ?? DesktopComposerDraft()
                sendingDraft.pending = true
                sendingDraft.localPrompt?.messageID = attempt.messageId
                drafts[sendingOwner] = sendingDraft
                guard composerKey == sendingOwner, activeHostID == hostId else { throw desktopInputError("电脑或会话已切换，原消息待确认") }
                do { _ = try await request("POST", "/session/\(session.id)/prompt_async" + directory(session.project), attempt.bodyJson, expectedHost: hostId) }
                catch is CancellationError { throw CancellationError() }
                catch {
                    let failure = error as NSError
                    if failure.domain == "DesktopConnect", (400...499).contains(failure.code) {
                        let data = try await request("GET", "/connect/session/\(session.id)/prompt/\(attempt.messageId)" + directory(session.project), expectedHost: hostId)
                        if try desktopPromptRejectionConfirmed(status: UInt16(failure.code), receiptJson: String(decoding: data, as: UTF8.self), sessionId: session.id, messageId: attempt.messageId) {
                            if try DesktopConnectCredentialStore.clearPendingPrompt(accountId: credentials.accountId, desktopId: hostId, sessionId: session.id, expected: attempt.journalJson) {
                                var draft = drafts[sendingOwner] ?? DesktopComposerDraft()
                                draft.pending = false
                                drafts[sendingOwner] = draft
                            }
                            throw desktopInputError("桌面未接纳消息，草稿已保留，请修正后发送：\(failure.localizedDescription)")
                        }
                    }
                    throw desktopInputError("发送未确认，原消息已安全保留，请点“核对并重试”：\(error.localizedDescription)")
                }
                try acceptPrompt(session, attempt, accountId: credentials.accountId, hostId: hostId)
                guard composerKey == sendingOwner, activeHostID == hostId else { return }
                try await refreshMessages(for: session)
            } catch {
                var failed = drafts[sendingOwner] ?? DesktopComposerDraft()
                if failed.localPrompt?.status == "正在发送…" {
                    failed.localPrompt?.status = failed.pending ? "发送待确认" : "未发送"
                    drafts[sendingOwner] = failed
                }
                throw error
            }
        }
    }
}





private func desktopInputError(_ message: String) -> NSError {
    NSError(domain: "DesktopConnectInput", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
}

private struct DesktopReplyWaiting: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var moving = false
    var body: some View {
        Text("思考中…")
            .litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
            .overlay {
                if !reduceMotion {
                    GeometryReader { geometry in
                        LinearGradient(colors: [.clear, LitterTheme.accentStrong, .clear],
                            startPoint: .leading, endPoint: .trailing)
                            .frame(width: geometry.size.width)
                            .offset(x: moving ? geometry.size.width : -geometry.size.width)
                    }.mask(Text("思考中…").litterFont(.footnote)).allowsHitTesting(false)
                }
            }
            .padding(.vertical, 8)
            .onAppear {
                moving = false
                if !reduceMotion { withAnimation(.linear(duration: 1.2).repeatForever(autoreverses: false)) { moving = true } }
            }
            .onDisappear { moving = false }
    }
}


private struct DesktopDirectoryPickerView: View {
    let bridge: DesktopConnectBridge
    let hostID: String
    let hostName: String
    let onSelected: (DesktopProjectFolder) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var requestedPath: String?
    @State private var revision = UUID()
    @State private var listing: DesktopDirectoryListing?
    @State private var pathInput = ""
    @State private var loading = true
    @State private var working = false
    @State private var failure: String?
    @State private var showNewFolder = false
    @State private var folderName = ""
    @State private var folderFailure: String?
    @State private var operation: Task<Void, Never>?
    private var busy: Bool { loading || working }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("添加项目").litterFont(size: 20, weight: .semibold)
                    .foregroundColor(LitterTheme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button { dismiss() } label: {
                    Image(systemName: "xmark").foregroundColor(LitterTheme.textSecondary)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("关闭")
            }
            Text("选择“\(hostName)”上的文件夹").litterFont(.footnote)
                .foregroundColor(LitterTheme.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 8) {
                TextField("电脑上的目录路径", text: $pathInput)
                    .litterFont(.subheadline).textInputAutocapitalization(.never)
                    .autocorrectionDisabled().submitLabel(.go)
                    .padding(12).background(LitterTheme.surface, in: RoundedRectangle(cornerRadius: 12))
                    .disabled(busy)
                    .onSubmit { browse(pathInput.trimmingCharacters(in: .whitespacesAndNewlines)) }
                Button("前往") { browse(pathInput.trimmingCharacters(in: .whitespacesAndNewlines)) }
                    .frame(minWidth: 44, minHeight: 44).disabled(busy || pathInput.isEmpty)
            }
            .padding(.top, 12)
            HStack {
                Button { if let parent = listing?.parent { browse(parent) } } label: {
                    Label("上级目录", systemImage: "arrow.left").litterFont(.subheadline)
                        .frame(minHeight: 44)
                }
                .disabled(busy || listing?.parent == nil)
                Spacer()
                Button { folderName = ""; folderFailure = nil; showNewFolder = true } label: {
                    Label("新建文件夹", systemImage: "plus").litterFont(.subheadline).frame(minHeight: 44)
                }
                .disabled(busy || listing == nil)
            }
            if let roots = listing?.roots, roots.count > 1 {
                ScrollView(.horizontal) {
                    HStack(spacing: 8) {
                        ForEach(roots, id: \.self) { root in
                            Button(root) { browse(root) }.frame(minWidth: 44, minHeight: 44).disabled(busy)
                        }
                    }
                }
            }
            if let failure {
                Text(failure).litterFont(.footnote).foregroundColor(LitterTheme.danger)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 8)
                Button("重试") { loading = true; revision = UUID() }.frame(minHeight: 44).disabled(busy)
            }
            if loading {
                VStack(spacing: 12) {
                    ProgressView().tint(LitterTheme.accentStrong)
                    Text("正在读取文件夹…").litterFont(.footnote).foregroundColor(LitterTheme.textSecondary)
                }
                .frame(maxWidth: .infinity, minHeight: 160)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        if listing?.directories.isEmpty == true {
                            Text("此目录没有子文件夹，可直接使用当前目录").litterFont(.footnote)
                                .foregroundColor(LitterTheme.textSecondary).padding(.vertical, 24)
                        }
                        ForEach(listing?.directories ?? [], id: \.path) { folder in
                            Button { browse(folder.path) } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: "folder").foregroundColor(LitterTheme.accentStrong)
                                    Text(folder.name).litterFont(size: 15).foregroundColor(LitterTheme.textPrimary)
                                        .lineLimit(1).frame(maxWidth: .infinity, alignment: .leading)
                                }
                                .frame(minHeight: 52).contentShape(Rectangle())
                            }
                            .buttonStyle(.plain).disabled(busy)
                        }
                    }
                }
                .frame(minHeight: 120, maxHeight: .infinity)
            }
            Button { addProject() } label: {
                Text(working ? "正在添加…" : "使用此目录").litterFont(.subheadline, weight: .semibold)
                    .foregroundColor(LitterTheme.textOnAccent).frame(maxWidth: .infinity, minHeight: 48)
                    .background(busy ? LitterTheme.textSecondary : LitterTheme.accentStrong,
                                in: RoundedRectangle(cornerRadius: 12))
            }
            .disabled(busy || listing == nil || pathInput != listing?.path || failure != nil)
            .padding(.vertical, 12)
        }
        .padding(.horizontal, 16).padding(.top, 8)
        .background(LitterTheme.background).tint(LitterTheme.accentStrong)
        .presentationDetents([.large]).presentationDragIndicator(.hidden)
        .task(id: revision) {
            loading = true
            failure = nil
            do {
                let result = try await bridge.browseDesktopDirectory(desktopId: hostID, path: requestedPath)
                try Task.checkCancellation()
                listing = result
                pathInput = result.path
            } catch {
                if !Task.isCancelled { failure = error.localizedDescription }
            }
            if !Task.isCancelled { loading = false }
        }
        .onDisappear { operation?.cancel() }
        .sheet(isPresented: $showNewFolder) {
            VStack(alignment: .leading, spacing: 16) {
                Text("新建文件夹").litterFont(size: 20, weight: .semibold).foregroundColor(LitterTheme.textPrimary)
                TextField("文件夹名称", text: $folderName).textInputAutocapitalization(.never)
                    .autocorrectionDisabled().textFieldStyle(.roundedBorder).disabled(working)
                if let folderFailure { Text(folderFailure).litterFont(.footnote).foregroundColor(LitterTheme.danger) }
                HStack {
                    Button("取消") { showNewFolder = false }.disabled(working).frame(minHeight: 44)
                    Spacer()
                    Button(working ? "正在创建…" : "创建") { createFolder() }
                        .disabled(working || folderName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                        .frame(minHeight: 44)
                }
            }
            .padding(20).background(LitterTheme.background).tint(LitterTheme.accentStrong)
            .presentationDetents([.height(260)]).interactiveDismissDisabled(working)
        }
    }

    private func browse(_ path: String) {
        guard !busy else { return }
        requestedPath = path
        loading = true
        revision = UUID()
    }
    private func addProject() {
        guard !busy, let path = listing?.path else { return }
        working = true
        failure = nil
        operation = Task { @MainActor in
            do {
                let folder = try await bridge.addDesktopProject(desktopId: hostID, path: path)
                try Task.checkCancellation()
                onSelected(folder)
            } catch {
                if !Task.isCancelled { failure = error.localizedDescription }
            }
            if !Task.isCancelled { working = false }
        }
    }
    private func createFolder() {
        guard !busy, let parent = listing?.path else { return }
        working = true
        folderFailure = nil
        operation = Task { @MainActor in
            do {
                let path = try await bridge.createDesktopDirectory(desktopId: hostID, parent: parent,
                    name: folderName.trimmingCharacters(in: .whitespacesAndNewlines))
                try Task.checkCancellation()
                showNewFolder = false
                requestedPath = path
                loading = true
                revision = UUID()
            } catch {
                if !Task.isCancelled { folderFailure = error.localizedDescription }
            }
            if !Task.isCancelled { working = false }
        }
    }
}


private struct DesktopMobileAttachmentCard: View {
    let attachment: DesktopProjectedAttachment
    let onPreview: () -> Void
    var onRemove: (() -> Void)? = nil
    @State private var image: UIImage?
    @State private var imageFailed = false

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onPreview) {
                HStack(spacing: 10) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 8).fill(LitterTheme.accentStrong.opacity(0.16))
                        if let image {
                            Image(uiImage: image).resizable().scaledToFill()
                        } else {
                            Image(systemName: attachment.isImage ? "photo" : "doc.text")
                                .foregroundColor(LitterTheme.accentStrong)
                        }
                    }
                    .frame(width: 48, height: 48).clipShape(RoundedRectangle(cornerRadius: 8))
                    VStack(alignment: .leading, spacing: 4) {
                        Text(attachment.filename).litterFont(size: 13, weight: .medium)
                            .foregroundColor(LitterTheme.textPrimary).lineLimit(1)
                        Text(imageFailed ? "图片无法预览" : attachment.detail).litterFont(.caption2)
                            .foregroundColor(LitterTheme.textSecondary).lineLimit(1)
                    }
                    .frame(maxWidth: onRemove == nil ? 200 : 156, alignment: .leading)
                }.padding(.horizontal, 16).padding(.vertical, 12).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityLabel("预览 \(attachment.filename)")
            if let onRemove {
                Button(action: onRemove) {
                    Image(systemName: "xmark").font(.system(size: 14))
                        .foregroundColor(LitterTheme.textSecondary).frame(width: 44, height: 48)
                }.buttonStyle(.plain).accessibilityLabel("移除 \(attachment.filename)")
            }
        }
        .background(LitterTheme.surfaceLight, in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(LitterTheme.textSecondary.opacity(0.12), lineWidth: 1))
        .task(id: attachment.dataBase64) {
            image = nil; imageFailed = false
            guard attachment.isImage, let encoded = attachment.dataBase64 else { return }
            guard encoded.utf8.count <= 5_333_336 else { imageFailed = true; return }
            let data = await Task.detached(priority: .userInitiated) { Data(base64Encoded: encoded) }.value
            guard !Task.isCancelled else { return }
            image = data.flatMap { UIImage(data: $0) }
            imageFailed = image == nil
        }
    }
}

private struct DesktopMobileAttachmentPreview: View {
    let item: DesktopAttachmentPreviewItem
    var loadArtifact: (DesktopProjectedAttachment) async throws -> DesktopProjectedAttachment = { _ in
        throw desktopInputError("请从文件所属会话打开预览")
    }
    @Environment(\.dismiss) private var dismiss
    @State private var fileURL: URL?
    @State private var failure: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if let fileURL {
                    if item.attachment.isHtml {
                        Text("安全预览 · 不执行脚本，不加载外部或电脑本地资源")
                            .litterFont(.caption2).foregroundColor(LitterTheme.textSecondary)
                        DesktopArtifactHTMLPreview(url: fileURL)
                    } else if QLPreviewController.canPreview(fileURL as NSURL) {
                        DesktopAttachmentQuickLook(url: fileURL)
                    } else {
                        Spacer()
                        Image(systemName: "doc.text").font(.system(size: 48)).foregroundColor(LitterTheme.accentStrong)
                        Text("此格式不支持内置预览，请用其他应用打开")
                            .foregroundColor(LitterTheme.textSecondary)
                        Spacer()
                    }
                    ShareLink(item: fileURL) {
                        Label("用其他应用打开或分享", systemImage: "square.and.arrow.up")
                    }.padding(.bottom, 16)
                } else if let failure {
                    Spacer()
                    Text(failure).foregroundColor(LitterTheme.textSecondary).padding()
                    Spacer()
                } else {
                    Spacer(); ProgressView("正在读取附件…"); Spacer()
                }
            }
            .background(LitterTheme.surface)
            .navigationTitle(item.attachment.filename)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("关闭") { dismiss() } } }
        }
        .task(id: item.id) {
            do {
                if let reason = item.attachment.unavailableReason { throw desktopInputError(reason) }
                let attachment: DesktopProjectedAttachment
                if item.attachment.path != nil { attachment = try await loadArtifact(item.attachment) }
                else { attachment = item.attachment }
                try Task.checkCancellation()
                let url = try await Task.detached(priority: .userInitiated) {
                    if let reason = attachment.unavailableReason {
                        throw NSError(domain: "DesktopAttachmentPreview", code: 1, userInfo: [NSLocalizedDescriptionKey: reason])
                    }
                    guard let encoded = attachment.dataBase64, encoded.utf8.count <= 5_333_336,
                          let data = Data(base64Encoded: encoded), data.count <= 4_000_000 else {
                        throw NSError(domain: "DesktopAttachmentPreview", code: 2, userInfo: [NSLocalizedDescriptionKey: "附件内容无法读取，或超出手机预览大小限制"])
                    }
                    let directory = FileManager.default.temporaryDirectory.appendingPathComponent("desktop-attachment-preview", isDirectory: true)
                    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                    let hash = SHA256.hash(data: data).prefix(12).map { String(format: "%02x", $0) }.joined()
                    let rawName = attachment.filename.replacingOccurrences(of: "\\", with: "/").split(separator: "/").last.map(String.init) ?? "附件"
                    let name = String(String.UnicodeScalarView(rawName.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) || "._ -".unicodeScalars.contains($0) }.suffix(120)))
                    let url = directory.appendingPathComponent("\(hash)-\(name.isEmpty ? "附件" : name)")
                    try data.write(to: url, options: .atomic)
                    return url
                }.value
                guard !Task.isCancelled else { return }
                fileURL = url
            } catch is CancellationError { return }
            catch { failure = error.localizedDescription }
        }
    }
}

// Match the PC's static HTML sandbox; never grant access to PC/local file URLs or native bridges.
private struct DesktopArtifactHTMLPreview: UIViewRepresentable {
    let url: URL
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        let web = WKWebView(frame: .zero, configuration: configuration)
        web.navigationDelegate = context.coordinator
        do {
            let html = try String(contentsOf: url, encoding: .utf8)
            WKContentRuleListStore.default().compileContentRuleList(forIdentifier: "desktop-artifact-offline",
                encodedContentRuleList: #"[{"trigger":{"url-filter":"^(https?|file|ftp):"},"action":{"type":"block"}}]"#) { [weak web] rules, error in
                DispatchQueue.main.async {
                    guard let web else { return }
                    guard let rules, error == nil else {
                        web.loadHTMLString("<p>安全预览初始化失败，请用其他应用打开。</p>", baseURL: nil)
                        return
                    }
                    web.configuration.userContentController.add(rules)
                    web.loadHTMLString(html, baseURL: nil)
                }
            }
        } catch { web.loadHTMLString("<p>HTML 内容无法读取，请用其他应用打开。</p>", baseURL: nil) }
        return web
    }
    func updateUIView(_ view: WKWebView, context: Context) {}
    static func dismantleUIView(_ view: WKWebView, coordinator: Coordinator) { view.stopLoading(); view.navigationDelegate = nil }
    func makeCoordinator() -> Coordinator { Coordinator() }
    final class Coordinator: NSObject, WKNavigationDelegate {
        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            decisionHandler(navigationAction.request.url?.absoluteString == "about:blank" ? .allow : .cancel)
        }
    }
}

private struct DesktopAttachmentQuickLook: UIViewControllerRepresentable {
    let url: URL
    func makeCoordinator() -> Coordinator { Coordinator(url: url) }
    func makeUIViewController(context: Context) -> QLPreviewController {
        let controller = QLPreviewController()
        controller.dataSource = context.coordinator
        return controller
    }
    func updateUIViewController(_ controller: QLPreviewController, context: Context) {
        if context.coordinator.url != url { context.coordinator.url = url; controller.reloadData() }
    }
    final class Coordinator: NSObject, QLPreviewControllerDataSource {
        var url: URL
        init(url: URL) { self.url = url }
        func numberOfPreviewItems(in controller: QLPreviewController) -> Int { 1 }
        func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem { url as NSURL }
    }
}


private struct DesktopPacedMarkdownView: View {
    let markdown: String
    let streaming: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown: String
    @State private var target: String

    init(markdown: String, streaming: Bool) {
        self.markdown = markdown
        self.streaming = streaming
        _shown = State(initialValue: markdown)
        _target = State(initialValue: markdown)
    }

    private var active: Bool { streaming && !reduceMotion }

    var body: some View {
        LitterMarkdownView(
            markdown: active && markdown.hasPrefix(shown) ? shown : markdown,
            style: .content, selectionEnabled: true
        )
        .transaction { $0.animation = nil }
        .onChange(of: markdown) {
            target = markdown
            if !active || !markdown.hasPrefix(shown) { shown = markdown }
        }
        // One clock survives snapshots; cancellation discards only presentation buffering.
        .task(id: active) {
            target = markdown
            guard active else {
                shown = markdown
                return
            }
            while !Task.isCancelled {
                do { try await Task.sleep(for: .milliseconds(40)) }
                catch { return } // View/task cancellation is the normal streaming lifecycle.
                guard target != shown else { continue }
                guard target.hasPrefix(shown) else {
                    shown = target
                    continue
                }
                let remaining = target.count - shown.count
                let step: Int
                switch remaining {
                case ...2: step = remaining
                case ...12: step = 1
                case ...48: step = 2
                case ...96: step = 4
                default: step = min(32, (remaining + 15) / 16)
                }
                // Swift counts Characters, so emoji/graphemes remain intact.
                shown = String(target.prefix(shown.count + step))
            }
        }
    }
}

import SwiftUI
import PhotosUI
import UIKit

struct ConversationComposerModalCoordinator<Content: View>: View {
    @Environment(AppModel.self) private var appModel
    @Environment(AppState.self) private var appState

    let snapshot: ConversationComposerSnapshot
    let experimentalFeatures: [ExperimentalFeature]
    let experimentalFeaturesLoading: Bool
    let skills: [SkillMetadata]
    let skillsLoading: Bool
    @Binding var showAttachMenu: Bool
    @Binding var showPhotoPicker: Bool
    @Binding var showCamera: Bool
    @Binding var showFileImporter: Bool
    @Binding var selectedPhoto: PhotosPickerItem?
    @Binding var attachedImage: UIImage?
    @Binding var showModelSelector: Bool
    @Binding var showPermissionsSheet: Bool
    @Binding var showExperimentalSheet: Bool
    @Binding var showSkillsSheet: Bool
    @Binding var showRenamePrompt: Bool
    @Binding var renameCurrentThreadTitle: String
    @Binding var renameDraft: String
    @Binding var slashErrorMessage: String?
    @Binding var showMicPermissionAlert: Bool
    let onOpenSettings: () -> Void
    let onLoadSelectedPhoto: (PhotosPickerItem) async -> Void
    let onLoadSelectedFile: (URL) -> Void
    let onLoadExperimentalFeatures: () async -> Void
    let onIsExperimentalFeatureEnabled: (String, Bool) -> Bool
    let onSetExperimentalFeature: (String, Bool) async -> Void
    let onLoadSkills: (Bool, Bool) async -> Void
    let onRenameThread: (String) async -> Void
    @ViewBuilder let content: Content
    @State private var modelSelectorDetent: PresentationDetent = .large

    private var selectedModelBinding: Binding<String> {
        Binding(
            get: {
                let pending = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty {
                    return pending
                }
                return snapshot.threadModel.trimmingCharacters(in: .whitespacesAndNewlines)
            },
            set: { appState.selectedModel = $0 }
        )
    }

    private var selectedAgentRuntimeKindBinding: Binding<AgentRuntimeKind?> {
        Binding(
            get: {
                let pending = appState.selectedModel.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty {
                    return appState.selectedAgentRuntimeKind
                }
                return currentThread?.agentRuntimeKind
            },
            set: { appState.selectedAgentRuntimeKind = $0 }
        )
    }

    private var reasoningEffortBinding: Binding<String> {
        Binding(
            get: {
                let pending = appState.reasoningEffort.trimmingCharacters(in: .whitespacesAndNewlines)
                if !pending.isEmpty {
                    return pending
                }
                return snapshot.threadReasoningEffort?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            },
            set: { appState.reasoningEffort = $0 }
        )
    }

    private var selectedApprovalValue: String {
        appState.approvalPolicy(for: snapshot.threadKey)
    }

    private var selectedSandboxValue: String {
        appState.sandboxMode(for: snapshot.threadKey)
    }

    private var selectedApprovalLabel: String {
        ComposerApprovalOption.allCases.first { $0.wireValue == selectedApprovalValue }?.title ?? "自定义"
    }

    private var selectedApprovalDescription: String {
        ComposerApprovalOption.allCases.first { $0.wireValue == selectedApprovalValue }?.description ?? "这个审批策略由服务端管理。"
    }

    private var selectedSandboxLabel: String {
        ComposerSandboxOption.allCases.first { $0.wireValue == selectedSandboxValue }?.title ?? "自定义"
    }

    private var selectedSandboxDescription: String {
        ComposerSandboxOption.allCases.first { $0.wireValue == selectedSandboxValue }?.description ?? "这个沙盒设置由服务端管理。"
    }

    private var currentThread: AppThreadSnapshot? {
        appModel.snapshot?.threads.first(where: { $0.key == snapshot.threadKey })
    }

    private var currentRuntimeSupportsPermissionOverrides: Bool {
        currentThread?.agentRuntimeKind.supportsThreadPermissionOverrides ?? true
    }

    private var hasAuthoritativeThreadPermissions: Bool {
        guard let thread = currentThread,
              thread.agentRuntimeKind.reportsEffectiveThreadPermissions else { return false }
        return threadPermissionsAreAuthoritative(
            approvalPolicy: thread.effectiveApprovalPolicy,
            sandboxPolicy: thread.effectiveSandboxPolicy
        )
    }

    private var currentApprovalLabel: String {
        guard hasAuthoritativeThreadPermissions else { return "Syncing..." }
        return currentThread?.effectiveApprovalPolicy?.displayTitle ?? "同步中..."
    }

    private var currentSandboxLabel: String {
        guard hasAuthoritativeThreadPermissions else { return "Syncing..." }
        return currentThread?.effectiveSandboxPolicy?.displayTitle ?? "同步中..."
    }

    private var usesThreadDefaults: Bool {
        selectedApprovalValue == ComposerApprovalOption.default.wireValue
            && selectedSandboxValue == ComposerSandboxOption.default.wireValue
    }

    private var attachSheetDetentHeight: CGFloat {
        let showsCamera = !LitterPlatform.isCatalyst
        let count = 2 + (showsCamera ? 1 : 0)
        return count >= 3 ? 260 : 210
    }

    var body: some View {
        content
            .sheet(isPresented: $showAttachMenu) {
                ConversationComposerAttachSheet(
                    onPickPhotoLibrary: {
                        showAttachMenu = false
                        showPhotoPicker = true
                    },
                    onChooseFile: {
                        showAttachMenu = false
                        showFileImporter = true
                    },
                    onTakePhoto: LitterPlatform.isCatalyst ? nil : {
                        showAttachMenu = false
                        showCamera = true
                    }
                )
                .presentationDetents([.height(attachSheetDetentHeight)])
                .presentationDragIndicator(.visible)
            }
            .photosPicker(isPresented: $showPhotoPicker, selection: $selectedPhoto, matching: .images)
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: ConversationAttachmentSupport.supportedFileContentTypes,
                allowsMultipleSelection: false
            ) { result in
                guard case let .success(urls) = result,
                      let url = urls.first else { return }
                onLoadSelectedFile(url)
            }
            .onChange(of: selectedPhoto) { _, item in
                guard let item else { return }
                Task { await onLoadSelectedPhoto(item) }
            }
            .fullScreenCover(isPresented: $showCamera) {
                CameraView(image: $attachedImage)
                    .ignoresSafeArea()
            }
            .sheet(isPresented: $showModelSelector) {
                ModelSelectorSheet(
                    models: snapshot.availableModels,
                    selectedModel: selectedModelBinding,
                    selectedAgentRuntimeKind: selectedAgentRuntimeKindBinding,
                    reasoningEffort: reasoningEffortBinding,
                    isReasoningEffortLocked: currentThread?.ampReasoningEffortLocked == true
                )
                .presentationDetents([.medium, .large], selection: $modelSelectorDetent)
                .presentationDragIndicator(.visible)
                .presentationContentInteraction(.scrolls)
                .presentationBackground(LitterTheme.surface)
            }
            .onChange(of: showModelSelector) { _, isPresented in
                if isPresented {
                    modelSelectorDetent = .large
                }
            }
            .sheet(isPresented: $showPermissionsSheet) {
                permissionsSheetContent
                    .task(id: snapshot.threadKey.threadId) {
                        _ = await appModel.hydrateThreadPermissions(for: snapshot.threadKey, appState: appState)
                    }
            }
            .sheet(isPresented: $showExperimentalSheet) {
                experimentalSheetContent
            }
            .sheet(isPresented: $showSkillsSheet) {
                skillsSheetContent
            }
            .alert("重命名会话", isPresented: Binding(
                get: { showRenamePrompt },
                set: { isPresented in
                    showRenamePrompt = isPresented
                    if !isPresented {
                        renameCurrentThreadTitle = ""
                        renameDraft = ""
                    }
                }
            )) {
                TextField("新的会话标题", text: $renameDraft)
                Button("取消", role: .cancel) {
                    showRenamePrompt = false
                }
                Button("重命名") {
                    let nextName = renameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !nextName.isEmpty else { return }
                    Task { await onRenameThread(nextName) }
                }
            } message: {
                Text("当前会话标题：\n\(renameCurrentThreadTitle)")
            }
            .alert("斜杠命令错误", isPresented: Binding(
                get: { slashErrorMessage != nil },
                set: { if !$0 { slashErrorMessage = nil } }
            )) {
                Button("好", role: .cancel) { slashErrorMessage = nil }
            } message: {
                Text(slashErrorMessage ?? "未知错误")
            }
            .alert("麦克风权限", isPresented: $showMicPermissionAlert) {
                Button("打开设置", action: onOpenSettings)
                Button("取消", role: .cancel) {}
            } message: {
                Text("语音输入需要麦克风权限，请在系统设置中开启。")
            }
    }

    @ViewBuilder
    private var permissionsSheetContent: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack(alignment: .center) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("会话权限")
                                    .foregroundStyle(LitterTheme.textPrimary)
                                    .litterFont(.headline)
                                Text(currentRuntimeSupportsPermissionOverrides ? "修改会从下一轮对话开始生效。" : "当前运行时会自行管理权限。")
                                    .foregroundStyle(LitterTheme.textMuted)
                                    .litterFont(.caption)
                            }
                            Spacer(minLength: 12)
                            Text(currentRuntimeSupportsPermissionOverrides ? (usesThreadDefaults ? "使用默认值" : "自定义覆盖") : "运行时管理")
                                .foregroundStyle(usesThreadDefaults ? LitterTheme.textSecondary : LitterTheme.accentStrong)
                                .litterFont(size: 11, weight: .semibold)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule()
                                        .fill((usesThreadDefaults ? LitterTheme.surfaceLight : LitterTheme.accentStrong).opacity(0.16))
                                )
                        }

                        HStack(spacing: 10) {
                            permissionSummaryTile(
                                title: "下一轮",
                                approval: selectedApprovalLabel,
                                sandbox: selectedSandboxLabel,
                                accent: LitterTheme.accentStrong
                            )
                            permissionSummaryTile(
                                title: "当前会话",
                                approval: currentApprovalLabel,
                                sandbox: currentSandboxLabel,
                                accent: hasAuthoritativeThreadPermissions ? LitterTheme.textSecondary : LitterTheme.warning
                            )
                        }
                    }
                    .padding(14)
                    .background(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .fill(LitterTheme.surface.opacity(0.82))
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 20, style: .continuous)
                            .stroke(LitterTheme.border.opacity(0.55), lineWidth: 1)
                    )

                    if currentRuntimeSupportsPermissionOverrides {
                        permissionSection(
                            title: "审批策略",
                            subtitle: "选择 NeCode 何时请求确认"
                        ) {
                            permissionDropdown(
                                title: selectedApprovalLabel,
                                detail: selectedApprovalDescription
                            ) {
                                ForEach(ComposerApprovalOption.allCases) { option in
                                    permissionMenuItem(
                                        title: option.title,
                                        description: option.description,
                                        isSelected: selectedApprovalValue == option.wireValue
                                    ) {
                                        appState.setPermissions(
                                            approvalPolicy: option.wireValue,
                                            sandboxMode: selectedSandboxValue,
                                            for: snapshot.threadKey
                                        )
                                    }
                                }
                            }
                        }

                        permissionSection(
                            title: "沙盒设置",
                            subtitle: "选择 NeCode 运行命令时可执行的范围"
                        ) {
                            permissionDropdown(
                                title: selectedSandboxLabel,
                                detail: selectedSandboxDescription
                            ) {
                                ForEach(ComposerSandboxOption.allCases) { option in
                                    permissionMenuItem(
                                        title: option.title,
                                        description: option.description,
                                        isSelected: selectedSandboxValue == option.wireValue
                                    ) {
                                        appState.setPermissions(
                                            approvalPolicy: selectedApprovalValue,
                                            sandboxMode: option.wireValue,
                                            for: snapshot.threadKey
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        unsupportedPermissionRuntimeCard
                    }
                }
                .padding(16)
                .padding(.bottom, 28)
            }
            .background(LitterTheme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("权限")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { showPermissionsSheet = false }
                        .foregroundColor(LitterTheme.accent)
                }
            }
        }
    }

    private var unsupportedPermissionRuntimeCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "info.circle.fill")
                    .foregroundStyle(LitterTheme.accentStrong)
                Text("运行时管理权限")
                    .foregroundStyle(LitterTheme.textPrimary)
                    .litterFont(.subheadline, weight: .semibold)
            }
            Text("当前 Agent 不支持移动端覆盖会话权限，因此本会话不会下发审批和沙盒设置。")
                .foregroundStyle(LitterTheme.textMuted)
                .litterFont(.caption)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(LitterTheme.surface.opacity(0.82))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(LitterTheme.border.opacity(0.55), lineWidth: 1)
        )
    }

    private func permissionSummaryTile(
        title: String,
        approval: String,
        sandbox: String,
        accent: Color
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .foregroundStyle(LitterTheme.textSecondary)
                .litterFont(size: 11, weight: .semibold)
            VStack(alignment: .leading, spacing: 8) {
                permissionSummaryRow(label: "审批", value: approval, accent: accent)
                permissionSummaryRow(label: "沙盒", value: sandbox, accent: accent)
            }
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(LitterTheme.surfaceLight.opacity(0.78))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(LitterTheme.border.opacity(0.45), lineWidth: 1)
        )
    }

    private func permissionSummaryRow(label: String, value: String, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .foregroundStyle(LitterTheme.textMuted)
                .litterFont(size: 10, weight: .medium)
            Text(value)
                .foregroundStyle(accent)
                .litterFont(.subheadline, weight: .semibold)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func permissionSection<SectionContent: View>(
        title: String,
        subtitle: String,
        @ViewBuilder content: () -> SectionContent
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .foregroundStyle(LitterTheme.textPrimary)
                    .litterFont(.headline)
                Text(subtitle)
                    .foregroundStyle(LitterTheme.textSecondary)
                    .litterFont(.caption)
            }
            content()
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(LitterTheme.surface.opacity(0.74))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(LitterTheme.border.opacity(0.5), lineWidth: 1)
        )
    }

    private func permissionDropdown<MenuContent: View>(
        title: String,
        detail: String,
        @ViewBuilder content: () -> MenuContent
    ) -> some View {
        Menu {
            content()
        } label: {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .foregroundStyle(LitterTheme.textPrimary)
                        .litterFont(size: 14, weight: .semibold)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                    Text(detail)
                        .foregroundStyle(LitterTheme.textMuted)
                        .litterFont(size: 11)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.up.chevron.down")
                    .foregroundStyle(LitterTheme.textMuted)
                    .imageScale(.small)
            }
            .frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(LitterTheme.surfaceLight.opacity(0.9))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(LitterTheme.border.opacity(0.45), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func permissionMenuItem(
        title: String,
        description: String,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 8) {
                    Text(title)
                        .foregroundStyle(LitterTheme.textPrimary)
                        .litterFont(size: 14, weight: .semibold)
                        .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                    if isSelected {
                        Image(systemName: "checkmark")
                            .foregroundStyle(LitterTheme.accentStrong)
                            .imageScale(.small)
                    }
                }
                Text(description)
                    .foregroundStyle(LitterTheme.textMuted)
                    .litterFont(size: 11)
                    .multilineTextAlignment(.leading)
            }
        }
    }

    @ViewBuilder
    private var experimentalSheetContent: some View {
        NavigationStack {
            Group {
                if experimentalFeaturesLoading {
                    ProgressView().tint(LitterTheme.accent)
                } else if experimentalFeatures.isEmpty {
                    Text("没有可用的实验功能")
                        .litterFont(.footnote)
                        .foregroundColor(LitterTheme.textMuted)
                } else {
                    List {
                        ForEach(Array(experimentalFeatures.enumerated()), id: \.element.id) { _, feature in
                            HStack(alignment: .top, spacing: 10) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(feature.displayName ?? feature.name)
                                        .litterFont(.subheadline)
                                        .foregroundColor(LitterTheme.textPrimary)
                                    Text(feature.description ?? "")
                                        .litterFont(.caption)
                                        .foregroundColor(LitterTheme.textSecondary)
                                }
                                Spacer(minLength: 0)
                                Toggle(
                                    "",
                                    isOn: Binding(
                                        get: { onIsExperimentalFeatureEnabled(feature.id, feature.enabled) },
                                        set: { value in
                                            Task { await onSetExperimentalFeature(feature.name, value) }
                                        }
                                    )
                                )
                                .labelsHidden()
                                .tint(LitterTheme.accent)
                            }
                            .listRowBackground(LitterTheme.surface.opacity(0.6))
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(LitterTheme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("实验功能")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("重新加载") { Task { await onLoadExperimentalFeatures() } }
                        .foregroundColor(LitterTheme.accent)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { showExperimentalSheet = false }
                        .foregroundColor(LitterTheme.accent)
                }
            }
        }
    }

    @ViewBuilder
    private var skillsSheetContent: some View {
        NavigationStack {
            Group {
                if skillsLoading {
                    ProgressView().tint(LitterTheme.accent)
                } else if skills.isEmpty {
                    Text("当前工作区没有可用技能")
                        .litterFont(.footnote)
                        .foregroundColor(LitterTheme.textMuted)
                } else {
                    List {
                        ForEach(skills) { skill in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(skill.name)
                                        .litterFont(.subheadline)
                                        .foregroundColor(LitterTheme.textPrimary)
                                    Spacer()
                                    if skill.enabled {
                                        Text("已启用")
                                            .litterFont(.caption2)
                                            .foregroundColor(LitterTheme.accent)
                                    }
                                }
                                Text(skill.description)
                                    .litterFont(.caption)
                                    .foregroundColor(LitterTheme.textSecondary)
                                Text(skill.path.value)
                                    .litterFont(.caption2)
                                    .foregroundColor(LitterTheme.textMuted)
                            }
                            .listRowBackground(LitterTheme.surface.opacity(0.6))
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(LitterTheme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("技能")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("重新加载") { Task { await onLoadSkills(true, true) } }
                        .foregroundColor(LitterTheme.accent)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { showSkillsSheet = false }
                        .foregroundColor(LitterTheme.accent)
                }
            }
        }
    }
}

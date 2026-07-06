import AppIntents

struct SendPromptIntent: AppIntent {
    static let title: LocalizedStringResource = "发送任务给 NeCode"
    static let description = IntentDescription("把指令发送到你的 NeCode 会话。")

    @Parameter(title: "指令") var prompt: String

    func perform() async throws -> some IntentResult & ProvidesDialog {
        await MainActor.run {
            WatchSessionBridge.shared.sendPrompt(prompt, serverId: nil, threadId: nil)
        }
        return .result(dialog: "已发送。")
    }
}

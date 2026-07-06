import AppIntents

struct EndVoiceSessionIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "结束语音会话"
    static var openAppWhenRun = false

    func perform() async throws -> some IntentResult {
        VoiceSessionControl.requestEnd()
        return .result()
    }
}

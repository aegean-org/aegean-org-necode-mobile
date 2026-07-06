import AppIntents

/// AppShortcuts surface for the watch app. These show up in the Shortcuts
/// app and (on Apple Watch Ultra) are assignable to the Action Button via
/// Settings → Action Button → Shortcut.
struct LitterWatchShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: SendPromptIntent(),
            phrases: [
                "发送任务给 \(.applicationName)"
            ],
            shortTitle: "发送任务",
            systemImageName: "mic.circle.fill"
        )
        AppShortcut(
            intent: OpenServerOnWatchIntent(),
            phrases: [
                "在 \(.applicationName) 打开主机",
                "在 \(.applicationName) 打开 \(\.$server)"
            ],
            shortTitle: "打开主机",
            systemImageName: "server.rack"
        )
        AppShortcut(
            intent: StartVoiceOnWatchIntent(),
            phrases: [
                "在 \(.applicationName) 开始语音"
            ],
            shortTitle: "开始语音",
            systemImageName: "waveform.circle.fill"
        )
    }
}

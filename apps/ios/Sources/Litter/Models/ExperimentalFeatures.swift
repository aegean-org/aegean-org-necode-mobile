import Foundation
import Observation

enum LitterFeature: String, CaseIterable, Identifiable {
    case realtimeVoice = "realtime_voice"
    case appleWatch = "apple_watch"
    case thinkingMinigame = "thinking_minigame"
    case terminal = "terminal"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .realtimeVoice: return "实时语音"
        case .appleWatch: return "Apple Watch"
        case .thinkingMinigame: return "思考小游戏"
        case .terminal: return "终端"
        }
    }

    var description: String {
        switch self {
        case .realtimeVoice: return "在首页显示实时语音入口。"
        case .appleWatch: return "将主机、任务和审批状态同步到已配对的 Apple Watch。需要安装 NeCode Watch 应用。"
        case .thinkingMinigame: return "助手思考时点击闪烁区域，可玩一个临时生成的小游戏。"
        case .terminal: return "在首页显示本地和远程终端入口。"
        }
    }

    var defaultEnabled: Bool {
        switch self {
        case .realtimeVoice: return true
        case .thinkingMinigame: return false
        case .terminal: return false
        case .appleWatch:
            // Default on now that the watch app is embedded again. The bridge
            // still no-ops when WatchConnectivity is unavailable.
            return true
        }
    }
}

@Observable
final class ExperimentalFeatures {
    static let shared = ExperimentalFeatures()

    @ObservationIgnored private let key = "litter.experimentalFeatures"
    private var overrides: [String: Bool]

    private init() {
        overrides = UserDefaults.standard.dictionary(forKey: key) as? [String: Bool] ?? [:]
    }

    private func persistOverrides() {
        UserDefaults.standard.set(overrides, forKey: key)
    }

    func isEnabled(_ feature: LitterFeature) -> Bool {
        overrides[feature.rawValue] ?? feature.defaultEnabled
    }

    func setEnabled(_ feature: LitterFeature, _ value: Bool) {
        var map = overrides
        if value == feature.defaultEnabled {
            map.removeValue(forKey: feature.rawValue)
        } else {
            map[feature.rawValue] = value
        }
        overrides = map
        persistOverrides()
    }

}

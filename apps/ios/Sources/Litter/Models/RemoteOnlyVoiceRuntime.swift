#if !targetEnvironment(macCatalyst)
import Foundation
import Observation

private enum RemoteOnlyVoiceRuntimeError: LocalizedError {
    case realtimeVoiceRemoved

    var errorDescription: String? {
        "NeCode Mobile iOS remote-only 包不再内置实时语音，请使用语音转文字输入。"
    }
}

@MainActor
@Observable
final class VoiceRuntimeController: VoiceActions {
    static let shared = VoiceRuntimeController()
    static let localServerID = "local"
    static let persistedLocalVoiceThreadIDKey = "litter.voice.local.thread_id"

    private(set) var activeVoiceSession: VoiceSessionState?

    func bind(appModel: AppModel) {}

    @discardableResult
    func startPinnedLocalVoiceCall(
        cwd _: String,
        model _: String?,
        approvalPolicy _: AppAskForApproval?,
        sandboxMode _: AppSandboxMode?
    ) async throws -> ThreadKey {
        throw RemoteOnlyVoiceRuntimeError.realtimeVoiceRemoved
    }

    @discardableResult
    func startVoiceOnThread(_: ThreadKey) async throws -> ThreadKey {
        throw RemoteOnlyVoiceRuntimeError.realtimeVoiceRemoved
    }

    func stopActiveVoiceSession() async {}

    func toggleActiveVoiceSessionSpeaker() async throws {
        throw RemoteOnlyVoiceRuntimeError.realtimeVoiceRemoved
    }
}
#endif

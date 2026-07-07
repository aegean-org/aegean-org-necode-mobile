import Foundation

private let asrModelKeywords: Set<String> = [
    "asr",
    "transcribe",
    "transcription"
]

func isAsrModelOption(_ model: ModelInfo) -> Bool {
    let searchable = [
        model.id,
        model.model,
        model.displayName,
        model.description
    ]
        .joined(separator: " ")
        .lowercased()

    if searchable.contains("speech-to-text") || searchable.contains("speech to text") {
        return true
    }

    return searchable
        .components(separatedBy: CharacterSet.alphanumerics.inverted)
        .contains { asrModelKeywords.contains($0) }
}

@MainActor
func voiceTranscriptionOptions(
    appModel: AppModel,
    serverId: String
) -> VoiceTranscriptionOptions {
    let asrModel = appModel.snapshot?
        .serverSnapshot(for: serverId)?
        .availableModels?
        .preferredAsrModel()?
        .asrRequestModelName

    return VoiceTranscriptionOptions(
        serverId: serverId,
        agentRuntimeKind: "necode",
        asrModel: asrModel,
        language: currentTranscriptionLanguage()
    )
}

private func currentTranscriptionLanguage() -> String? {
    let code = Locale.current.language.languageCode?.identifier.lowercased()
    switch code {
    case "zh":
        return "zh"
    case "en":
        return "en"
    default:
        return nil
    }
}

private extension [ModelInfo] {
    func preferredAsrModel() -> ModelInfo? {
        first { isAsrModelOption($0) && !$0.hidden }
            ?? first { isAsrModelOption($0) }
    }
}

private extension ModelInfo {
    var asrRequestModelName: String {
        let trimmedModel = model.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmedModel.isEmpty ? id : trimmedModel
    }
}

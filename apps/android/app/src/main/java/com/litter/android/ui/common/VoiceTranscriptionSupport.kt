package com.litter.android.ui.common

import com.litter.android.state.AppModel
import com.litter.android.state.VoiceTranscriptionOptions

internal suspend fun voiceTranscriptionOptions(
    appModel: AppModel,
    serverId: String,
): VoiceTranscriptionOptions {
    return VoiceTranscriptionOptions(
        serverId = serverId,
        agentRuntimeKind = "necode",
        asrModel = appModel.snapshot.value
            ?.servers
            ?.firstOrNull { it.serverId == serverId }
            ?.availableModels
            ?.preferredAsrModel()
            ?.asrRequestModelName(),
    )
}

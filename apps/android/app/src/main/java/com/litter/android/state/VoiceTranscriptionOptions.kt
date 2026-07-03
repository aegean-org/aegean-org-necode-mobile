package com.litter.android.state

/**
 * Configuration for one speech-to-text request.
 */
data class VoiceTranscriptionOptions(
    val serverId: String,
    val agentRuntimeKind: String? = "necode",
    val asrModel: String?,
    val language: String? = null,
)

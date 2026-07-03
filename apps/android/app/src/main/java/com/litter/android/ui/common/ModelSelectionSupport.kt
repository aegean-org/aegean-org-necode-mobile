package com.litter.android.ui.common

import java.util.Locale
import uniffi.codex_mobile_client.ModelInfo

private val AmpVisibleModes = setOf("smart", "rush", "deep")
private val AsrKeywords = setOf("asr", "transcribe", "transcription")

internal fun normalizedAmpModeName(value: String): String =
    value.trim()
        .lowercase(Locale.ROOT)
        .removePrefix("amp/")
        .removePrefix("amp:")

internal fun ModelInfo.ampModeName(): String =
    normalizedAmpModeName(id)
        .ifEmpty {
            normalizedAmpModeName(model)
        }

internal fun ModelInfo.isAsrModelOption(): Boolean {
    val searchable = listOf(id, model, displayName, description)
        .joinToString(separator = " ")
        .lowercase(Locale.ROOT)
    if ("speech-to-text" in searchable || "speech to text" in searchable) return true
    return searchable
        .split(Regex("[^a-z0-9]+"))
        .any { it in AsrKeywords }
}

internal fun ModelInfo.isVisibleModelOption(): Boolean {
    if (isAsrModelOption()) return false
    return agentRuntimeKind != "amp" || ampModeName() in AmpVisibleModes
}

internal fun List<ModelInfo>.preferredAsrModel(): ModelInfo? =
    firstOrNull { it.isAsrModelOption() && !it.hidden }
        ?: firstOrNull { it.isAsrModelOption() }

internal fun ModelInfo.asrRequestModelName(): String =
    model.ifBlank { id }

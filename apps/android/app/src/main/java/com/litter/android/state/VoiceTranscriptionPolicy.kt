package com.litter.android.state

import uniffi.codex_mobile_client.AppServerTransportState

/**
 * Voice transcription opens an Alleycat-side JSONL RPC. Treat only a fully
 * connected transport as ready for immediate transcription.
 */
internal fun shouldReconnectBeforeVoiceTranscription(state: AppServerTransportState): Boolean =
    state != AppServerTransportState.CONNECTED

/**
 * Retry voice transcription only for transport-level connectivity failures.
 * ASR credential, model, and server errors must surface unchanged.
 */
internal fun shouldRetryVoiceTranscriptionAfterReconnect(error: Throwable): Boolean {
    val message = error.message?.lowercase().orEmpty()
    if ("transport error" !in message) return false
    return "disconnected" in message ||
        "not connected" in message ||
        "connection failed" in message ||
        "timed out" in message
}

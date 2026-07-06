import Foundation

/// Voice transcription opens a paired-host RPC. Only a connected transport is
/// ready for immediate transcription; other states should reconnect first.
func shouldReconnectBeforeVoiceTranscription(_ state: AppServerTransportState) -> Bool {
    state != .connected
}

/// Retry only transport-level failures after reconnecting. Auth, model, and
/// ASR service errors are surfaced unchanged so they can be fixed directly.
func shouldRetryVoiceTranscriptionAfterReconnect(_ error: Error) -> Bool {
    let message = error.localizedDescription.lowercased()
    guard message.contains("transport error") else { return false }
    return message.contains("disconnected")
        || message.contains("not connected")
        || message.contains("connection failed")
        || message.contains("timed out")
}

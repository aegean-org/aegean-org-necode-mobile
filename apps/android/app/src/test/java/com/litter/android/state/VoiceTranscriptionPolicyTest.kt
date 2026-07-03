package com.litter.android.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.codex_mobile_client.AppServerTransportState

class VoiceTranscriptionPolicyTest {
    @Test
    fun voiceTranscriptionReconnectsBeforeRequestWhenServerIsNotConnected() {
        assertFalse(shouldReconnectBeforeVoiceTranscription(AppServerTransportState.CONNECTED))
        assertTrue(shouldReconnectBeforeVoiceTranscription(AppServerTransportState.CONNECTING))
        assertTrue(shouldReconnectBeforeVoiceTranscription(AppServerTransportState.UNRESPONSIVE))
        assertTrue(shouldReconnectBeforeVoiceTranscription(AppServerTransportState.DISCONNECTED))
        assertTrue(shouldReconnectBeforeVoiceTranscription(AppServerTransportState.UNKNOWN))
    }

    @Test
    fun voiceTranscriptionRetriesOnlyTransportConnectionFailures() {
        assertTrue(
            shouldRetryVoiceTranscriptionAfterReconnect(
                IllegalStateException("transport error: connection failed: connecting iroh endpoint: timed out"),
            ),
        )
        assertTrue(
            shouldRetryVoiceTranscriptionAfterReconnect(
                IllegalStateException("transport error: disconnected"),
            ),
        )
        assertFalse(
            shouldRetryVoiceTranscriptionAfterReconnect(
                IllegalStateException("server error -32603: missing ASR credentials"),
            ),
        )
        assertFalse(
            shouldRetryVoiceTranscriptionAfterReconnect(
                IllegalStateException("ASR request failed: HTTP 401"),
            ),
        )
    }
}

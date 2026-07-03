package com.litter.android.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.codex_mobile_client.AppServerTransportState

class TurnReconnectPolicyTest {
    @Test
    fun startTurnReconnectsBeforeSendWhenServerIsNotConnected() {
        assertFalse(shouldReconnectBeforeStartTurn(AppServerTransportState.CONNECTED))
        assertTrue(shouldReconnectBeforeStartTurn(AppServerTransportState.CONNECTING))
        assertTrue(shouldReconnectBeforeStartTurn(AppServerTransportState.UNRESPONSIVE))
        assertTrue(shouldReconnectBeforeStartTurn(AppServerTransportState.DISCONNECTED))
        assertTrue(shouldReconnectBeforeStartTurn(AppServerTransportState.UNKNOWN))
    }

    @Test
    fun startTurnRetriesOnlyDisconnectedTransportFailures() {
        assertTrue(
            shouldRetryStartTurnAfterReconnect(
                IllegalStateException("v1=transport error: disconnected"),
            ),
        )
        assertTrue(
            shouldRetryStartTurnAfterReconnect(
                IllegalStateException("transport error: not connected"),
            ),
        )
        assertFalse(
            shouldRetryStartTurnAfterReconnect(
                IllegalStateException("ACP session not found"),
            ),
        )
        assertFalse(
            shouldRetryStartTurnAfterReconnect(
                IllegalStateException("No model selected"),
            ),
        )
    }
}

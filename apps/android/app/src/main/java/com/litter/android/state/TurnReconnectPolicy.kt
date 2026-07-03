package com.litter.android.state

import uniffi.codex_mobile_client.AppServerTransportState

/**
 * Returns whether a turn send should reconnect before touching the current
 * transport. Only a fully connected server is trusted for immediate send.
 */
internal fun shouldReconnectBeforeStartTurn(state: AppServerTransportState): Boolean =
    state != AppServerTransportState.CONNECTED

/**
 * Returns whether a failed turn send is safe to retry after reconnecting the
 * same server. Other failures stay explicit so auth/model/session bugs surface.
 */
internal fun shouldRetryStartTurnAfterReconnect(error: Throwable): Boolean {
    val message = error.message?.lowercase().orEmpty()
    if ("transport error" !in message) return false
    return "disconnected" in message || "not connected" in message
}

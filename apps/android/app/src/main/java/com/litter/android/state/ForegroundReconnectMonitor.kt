package com.litter.android.state

import android.content.Context
import com.litter.android.util.LLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ReconnectResult

internal class ForegroundReconnectMonitor(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start(
        context: Context,
        appModel: AppModel,
        onReconnectResults: suspend (List<ReconnectResult>) -> Unit,
    ) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        job = scope.launch {
            while (true) {
                delay(FOREGROUND_RECOVERY_INTERVAL_MS)
                runTick(appContext, appModel, onReconnectResults)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runTick(
        context: Context,
        appModel: AppModel,
        onReconnectResults: suspend (List<ReconnectResult>) -> Unit,
    ) {
        val savedServers = SavedServerStore.remembered(context)
            .filterNot { it.source == "local" }
        if (savedServers.isEmpty()) return

        appModel.reconnectController.probeActiveRemoteServers()
        appModel.refreshSnapshot()

        val servers = appModel.snapshot.value?.servers.orEmpty()
        val needsReconnect = savedServers.any { saved ->
            servers.none { server -> server.serverId == saved.id && server.isConnected }
        }
        if (!needsReconnect) return

        appModel.reconnectController.syncSavedServers(savedServers.map { it.toRecord(context) })
        val results = appModel.reconnectController.reconnectSavedServers()
        onReconnectResults(results)
        results.filterNot { it.success }.forEach { result ->
            LLog.w(
                "ForegroundReconnect",
                "foreground reconnect failed serverId=${result.serverId} error=${result.errorMessage}",
            )
        }
        appModel.refreshSnapshot()
        appModel.persistAlleycatSecretKeyIfNeeded()
    }

    private companion object {
        const val FOREGROUND_RECOVERY_INTERVAL_MS = 10_000L
    }
}

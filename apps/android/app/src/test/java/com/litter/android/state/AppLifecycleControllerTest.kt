package com.litter.android.state

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLifecycleControllerTest {
    @Test
    fun reconnectCandidatesSkipLocalAndActiveServers() {
        val savedServers = listOf(
            SavedServer(
                id = "local",
                name = "This Device",
                hostname = "127.0.0.1",
                port = 0,
                source = "local",
            ),
            SavedServer(
                id = "already-active",
                name = "Remote Active",
                hostname = "active.example.com",
                port = 443,
                websocketURL = "wss://active.example.com/ws",
            ),
            SavedServer(
                id = "reconnect-me",
                name = "Remote Idle",
                hostname = "idle.example.com",
                port = 443,
                websocketURL = "wss://idle.example.com/ws",
            ),
        )

        val candidates = AppLifecycleController.reconnectCandidates(
            savedServers = savedServers,
            activeServerIds = setOf("already-active"),
        )

        assertEquals(listOf("reconnect-me"), candidates.map { it.id })
    }
}

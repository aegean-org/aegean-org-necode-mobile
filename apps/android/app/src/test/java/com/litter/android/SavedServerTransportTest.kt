package com.litter.android

import com.litter.android.state.SavedServer
import com.litter.android.state.ServerRemovalOperations
import com.litter.android.state.executeServerRemoval
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedServerTransportTest {
    @Test
    fun serverRemovalRunsDisconnectBeforeLocalCleanup() = runBlocking {
        val events = mutableListOf<String>()

        executeServerRemoval(
            ServerRemovalOperations(
                disconnect = { events += "disconnect" },
                removeSavedServer = { events += "remove-saved" },
                deleteAlleycatToken = { events += "delete-token" },
                closeSshSession = { events += "close-ssh" },
                refreshProjection = { events += "refresh" },
            ),
        )

        assertEquals(
            listOf("disconnect", "remove-saved", "delete-token", "close-ssh", "refresh"),
            events,
        )
    }

    @Test
    fun serverRemovalPreservesLocalStateWhenDisconnectFails() = runBlocking {
        val events = mutableListOf<String>()

        val error = runCatching {
            executeServerRemoval(
                ServerRemovalOperations(
                    disconnect = {
                        events += "disconnect"
                        error("shutdown failed")
                    },
                    removeSavedServer = { events += "remove-saved" },
                    deleteAlleycatToken = { events += "delete-token" },
                    closeSshSession = { events += "close-ssh" },
                    refreshProjection = { events += "refresh" },
                ),
            )
        }.exceptionOrNull()

        assertEquals("shutdown failed", error?.message)
        assertEquals(listOf("disconnect"), events)
    }

    @Test
    fun codexAndSshDiscoveryRequiresChoiceUntilPreferenceIsSet() {
        val server =
            SavedServer(
                id = "server-1",
                name = "Studio",
                hostname = "192.168.1.203",
                port = 8390,
                codexPorts = listOf(8390),
                sshPort = 22,
                hasCodexServer = true,
            )

        assertFalse(server.prefersSshConnection)
        assertTrue(server.requiresConnectionChoice)
        assertNull(server.directCodexPort)
    }

    @Test
    fun sshPreferenceForcesSshTransport() {
        val server =
            SavedServer(
                id = "server-2",
                name = "SSH Tunnel",
                hostname = "10.0.0.5",
                port = 8390,
                codexPorts = listOf(8390),
                sshPort = 22,
                hasCodexServer = true,
                preferredConnectionMode = "ssh",
            )

        assertTrue(server.prefersSshConnection)
        assertNull(server.directCodexPort)
        assertEquals(22, server.resolvedSshPort)
    }

    @Test
    fun legacyForwardingFlagMigratesToSshPreference() {
        val server =
            SavedServer(
                id = "server-3",
                name = "Old Saved Host",
                hostname = "192.168.1.203",
                port = 8390,
                codexPorts = listOf(8390),
                sshPort = 22,
                hasCodexServer = true,
                sshPortForwardingEnabled = true,
            )

        assertTrue(server.prefersSshConnection)
        assertNull(server.directCodexPort)
        assertEquals(22, server.resolvedSshPort)
    }

    @Test
    fun codexOnlyHostUsesDirectTransport() {
        val server =
            SavedServer(
                id = "server-4",
                name = "Codex",
                hostname = "10.0.0.4",
                port = 9234,
                codexPorts = listOf(9234),
                hasCodexServer = true,
            )

        assertFalse(server.prefersSshConnection)
        assertEquals(9234, server.directCodexPort)
    }
}

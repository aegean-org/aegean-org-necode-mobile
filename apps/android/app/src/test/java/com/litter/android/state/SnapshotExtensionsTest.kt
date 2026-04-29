package com.litter.android.state

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.AgentRuntimeKind

class SnapshotExtensionsTest {

    @Test
    fun `displayModelLabel uses concrete thread model first`() {
        assertEquals(
            "gpt-5.4",
            displayModelLabel(
                model = "gpt-5.4",
                infoModel = null,
                modelProvider = "anthropic",
                agentRuntimeKind = AgentRuntimeKind.CLAUDE,
            ),
        )
    }

    @Test
    fun `displayModelLabel falls back to provider and runtime labels`() {
        assertEquals(
            "Claude",
            displayModelLabel(
                model = null,
                infoModel = null,
                modelProvider = "anthropic",
                agentRuntimeKind = AgentRuntimeKind.CLAUDE,
            ),
        )
        assertEquals(
            "opencode",
            displayModelLabel(
                model = null,
                infoModel = null,
                modelProvider = null,
                agentRuntimeKind = AgentRuntimeKind.OPENCODE,
            ),
        )
    }
}

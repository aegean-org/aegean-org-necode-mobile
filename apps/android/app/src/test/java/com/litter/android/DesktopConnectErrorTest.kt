package com.litter.android

import com.litter.android.ui.desktop.desktopConnectErrorMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.ClientException

class DesktopConnectErrorTest {
    @Test fun rendersBridgePayloadWithoutGeneratedWrapper() {
        assertEquals("这台电脑暂时离线", desktopConnectErrorMessage(ClientException.Rpc("这台电脑暂时离线")))
        assertEquals("connection lost", desktopConnectErrorMessage(ClientException.Transport("connection lost")))
    }
    @Test fun preservesUnexpectedErrors() {
        assertEquals("unknown failure", desktopConnectErrorMessage(IllegalStateException("unknown failure")))
        assertEquals("Connect returned 409 Conflict: unknown", desktopConnectErrorMessage(ClientException.Rpc("Connect returned 409 Conflict: unknown")))
    }
}

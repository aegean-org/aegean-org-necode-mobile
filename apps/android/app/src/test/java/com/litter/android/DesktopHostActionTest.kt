package com.litter.android

import com.litter.android.ui.desktop.DesktopHostAction
import com.litter.android.ui.desktop.desktopHostAction
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopHostActionTest {
    @Test fun currentHostRemainsCurrentEvenWhenDirectoryMarksItBusy() {
        assertEquals(DesktopHostAction.CURRENT, desktopHostAction("host-a", "host-a", "busy"))
    }

    @Test fun onlyAvailableOtherHostsCanBeSelected() {
        assertEquals(DesktopHostAction.CONNECT, desktopHostAction("host-a", "host-b", "available"))
        assertEquals(DesktopHostAction.UNAVAILABLE, desktopHostAction("host-a", "host-b", "busy"))
        assertEquals(DesktopHostAction.UNAVAILABLE, desktopHostAction("host-a", "host-b", "offline"))
    }
    @Test fun interruptedCurrentHostRequestsReconnectInsteadOfClosingPicker() {
        assertEquals(DesktopHostAction.RECONNECT, desktopHostAction("host-a", "host-a", "busy", false))
        assertEquals(DesktopHostAction.RECONNECT, desktopHostAction("host-a", "host-a", "available", false))
    }
}

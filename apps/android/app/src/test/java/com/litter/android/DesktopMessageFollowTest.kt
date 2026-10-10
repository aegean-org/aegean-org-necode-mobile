package com.litter.android

import com.litter.android.ui.desktop.DesktopLocalPrompt
import com.litter.android.ui.desktop.desktopTimelineMessages
import com.litter.android.ui.desktop.desktopNearMessageBottom
import com.litter.android.ui.desktop.DesktopMessage
import com.litter.android.ui.desktop.shouldFollowDesktopMessageUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMessageFollowTest {
    private val previous = listOf(DesktopMessage("assistant", "开始回答"))
    private val continued = listOf(DesktopMessage("assistant", "开始回答，继续输出"))

    @Test fun streamsSameMessageWhileReadingAtBottom() {
        assertTrue(shouldFollowDesktopMessageUpdate(previous, continued, nearBottom = true, force = false))
    }

    @Test fun doesNotInterruptReadingOlderMessages() {
        assertFalse(shouldFollowDesktopMessageUpdate(previous, continued, nearBottom = false, force = false))
        assertFalse(shouldFollowDesktopMessageUpdate(previous, previous, nearBottom = true, force = false))
    }

    @Test fun followsFirstLoadAndExplicitSend() {
        assertTrue(shouldFollowDesktopMessageUpdate(emptyList(), previous, nearBottom = false, force = false))
        assertTrue(shouldFollowDesktopMessageUpdate(previous, continued, nearBottom = false, force = true))
        assertTrue(shouldFollowDesktopMessageUpdate(previous, previous, nearBottom = false, force = true))
        assertTrue(shouldFollowDesktopMessageUpdate(previous, continued, nearBottom = true,
            force = false, pendingFollow = true))
    }

    @Test fun staleFollowRequestDoesNotOverrideManualReading() {
        assertFalse(shouldFollowDesktopMessageUpdate(previous, continued, nearBottom = false,
            force = false, pendingFollow = true))
    }

    @Test fun longLastMessageIsNotAtBottomUntilItsTailIsVisible() {
        assertFalse(desktopNearMessageBottom(lastIndex = 5, itemCount = 6, lastBottom = 1300, viewportEnd = 700, threshold = 72))
        assertTrue(desktopNearMessageBottom(lastIndex = 5, itemCount = 6, lastBottom = 730, viewportEnd = 700, threshold = 72))
        assertFalse(desktopNearMessageBottom(lastIndex = 4, itemCount = 6, lastBottom = 700, viewportEnd = 700, threshold = 72))
    }

    @Test fun outgoingPreviewAppearsImmediatelyAndOnlyExactServerIdReplacesIt() {
        val local = DesktopLocalPrompt("msg-123", "问题", listOf("photo.jpg"))
        val preview = desktopTimelineMessages(emptyList(), local).single()
        assertEquals("local:msg-123", preview.id)
        assertEquals("附件：photo.jpg\n问题", preview.text)
        assertEquals("正在发送…", preview.status)
        val other = listOf(DesktopMessage("user", "问题", id = "msg-1234:part"))
        assertEquals(2, desktopTimelineMessages(other, local).size)
        val confirmed = listOf(DesktopMessage("user", "问题", id = "msg-123:part"))
        assertEquals(confirmed, desktopTimelineMessages(confirmed, local))
    }
}

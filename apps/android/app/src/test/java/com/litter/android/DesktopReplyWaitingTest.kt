package com.litter.android

import com.litter.android.ui.desktop.DesktopMessage
import com.litter.android.ui.desktop.DesktopMessageKind
import com.litter.android.ui.desktop.desktopComposerShowsStop
import com.litter.android.ui.desktop.shouldShowDesktopReplyWaiting
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopReplyWaitingTest {
    private val question = DesktopMessage("user", "问题")
    private val reply = DesktopMessage("assistant", "已经开始回答")

    @Test fun hidesWaitingWhenCurrentReplyStartsWithoutLosingStop() {
        assertFalse(waiting(listOf(question, reply)))
        assertTrue(desktopComposerShowsStop(submitting = false, awaiting = true, processing = true, stopping = false))
    }

    @Test fun aNewQuestionWaitsEvenWhenOlderRepliesExist() {
        assertTrue(waiting(listOf(question, reply, question.copy(text = "继续", id = "local:new"))))
        assertFalse(waiting(listOf(question, reply, question.copy(text = "继续"), reply)))
    }

    @Test fun toolsAttachmentsAndEmptyTextAreNotReplyBody() {
        assertTrue(waiting(listOf(question, reply.copy(text = "  \n  "))))
        assertTrue(waiting(listOf(question, reply.copy(kind = DesktopMessageKind.TOOL))))
        assertTrue(waiting(listOf(question.copy(kind = DesktopMessageKind.FILE), reply.copy(kind = DesktopMessageKind.FILE))))
    }

    @Test fun completedErrorsAndAttentionDoNotShowWaiting() {
        assertFalse(shouldShowDesktopReplyWaiting(listOf(question), false, false, 0))
        assertFalse(shouldShowDesktopReplyWaiting(listOf(question), true, true, 0))
        assertFalse(shouldShowDesktopReplyWaiting(listOf(question), true, false, 1))
        assertFalse(waiting(emptyList()))
    }

    private fun waiting(messages: List<DesktopMessage>) = shouldShowDesktopReplyWaiting(messages, true, false, 0)
}

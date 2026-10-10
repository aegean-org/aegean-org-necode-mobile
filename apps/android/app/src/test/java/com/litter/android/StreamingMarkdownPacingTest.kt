package com.litter.android

import com.litter.android.ui.conversation.streamingMarkdownNextEnd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMarkdownPacingTest {
    @Test fun phoneSizedChunksAreSplitInsteadOfUsingTheDesktop512CharacterShortcut() {
        val text = "已经显示" + "新增文字".repeat(20)
        val end = streamingMarkdownNextEnd(text, 4)
        assertTrue(end > 4 && end < text.length)
        assertEquals(8, end)
    }

    @Test fun smallTailFinishesAndLargeBacklogCatchesUp() {
        assertEquals(4, streamingMarkdownNextEnd("12345678", 3))
        assertEquals(8, streamingMarkdownNextEnd("12345678", 6))
        val text = "正文".repeat(1000)
        assertEquals(32, streamingMarkdownNextEnd(text, 0))
        var end = 0
        var frames = 0
        while (end < text.length) {
            val next = streamingMarkdownNextEnd(text, end)
            assertTrue(next > end && next <= text.length)
            end = next
            frames++
        }
        assertEquals(text, text.take(end))
        assertTrue("No endless typing backlog", frames < 230)
    }

    @Test fun incrementsNeverSplitEmojiSurrogatePairs() {
        val text = "中".repeat(7) + "😀" + "文".repeat(55)
        assertEquals(9, streamingMarkdownNextEnd(text, 4))
        assertEquals(text.length, streamingMarkdownNextEnd(text, text.length))
    }
}

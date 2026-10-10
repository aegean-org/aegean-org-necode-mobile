package com.litter.android

import com.litter.android.ui.common.ListLoadMoreGesture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListLoadMoreGestureTest {
    @Test fun loadsOnlyOnUpwardGestureAtTail() {
        val gesture = ListLoadMoreGesture()
        assertFalse(gesture.onScroll(upward = false, atEnd = true, canLoad = true))
        assertFalse(gesture.onScroll(upward = true, atEnd = false, canLoad = true))
        assertTrue(gesture.onScroll(upward = true, atEnd = true, canLoad = true))
    }

    @Test fun doesNotChainPagesDuringOneGestureOrWhileUnavailable() {
        val gesture = ListLoadMoreGesture()
        assertFalse(gesture.onScroll(upward = true, atEnd = true, canLoad = false))
        assertTrue(gesture.onScroll(upward = true, atEnd = true, canLoad = true))
        assertFalse(gesture.onScroll(upward = true, atEnd = true, canLoad = true))
        gesture.endGesture()
        assertTrue(gesture.onScroll(upward = true, atEnd = true, canLoad = true))
    }
}

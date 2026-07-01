package com.litter.android.ui.conversation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerBarInterruptTest {
    @Test
    fun canShowInterruptActionRejectsMissingTurnId() {
        assertFalse(canShowInterruptAction(isThinking = true, activeTurnId = null))
        assertFalse(canShowInterruptAction(isThinking = true, activeTurnId = ""))
        assertFalse(canShowInterruptAction(isThinking = true, activeTurnId = "   "))
    }

    @Test
    fun canShowInterruptActionRequiresThinkingAndTurnId() {
        assertFalse(canShowInterruptAction(isThinking = false, activeTurnId = "turn-1"))
        assertTrue(canShowInterruptAction(isThinking = true, activeTurnId = "turn-1"))
    }
}

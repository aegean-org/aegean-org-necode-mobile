package com.litter.android.ui.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseSubmissionErrorsTest {
    @Test
    fun turnSubmissionErrorExplainsNeLoginRequired() {
        val message = turnSubmissionErrorMessage(
            IllegalStateException("NE login required. Use /login to continue."),
        )

        assertTrue(message.contains("NeCode 登录已失效"))
        assertTrue(message.contains("/login"))
    }

    @Test
    fun turnSubmissionErrorExplainsMissingModel() {
        val message = turnSubmissionErrorMessage(
            IllegalStateException("No model selected. Use /login, then use /model to select a model."),
        )

        assertTrue(message.contains("没有可用模型"))
        assertTrue(message.contains("/model"))
    }

    @Test
    fun responseSubmissionErrorKeepsRawActionableDetails() {
        val message = responseSubmissionErrorMessage(IllegalStateException("permission denied"))

        assertEquals("permission denied", message)
    }
}

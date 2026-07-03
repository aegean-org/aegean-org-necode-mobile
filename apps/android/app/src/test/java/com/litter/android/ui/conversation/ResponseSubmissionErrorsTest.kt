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
    fun turnSubmissionErrorExplainsExpiredAcpSession() {
        val message = turnSubmissionErrorMessage(
            IllegalStateException("server error -32603: ACP session not found: 019f1804"),
        )

        assertTrue(message.contains("会话已失效"))
        assertTrue(message.contains("新建会话"))
    }

    @Test
    fun turnSubmissionErrorExplainsGenericAcpPromptInternalError() {
        val message = turnSubmissionErrorMessage(
            IllegalStateException(
                "v1=deserialization failed: server error -32603: " +
                    "Failed to send session/prompt to ACP agent: Internal error",
            ),
        )

        assertTrue(message.contains("可能已失效"))
        assertTrue(message.contains("重新选择项目"))
    }

    @Test
    fun responseSubmissionErrorKeepsRawActionableDetails() {
        val message = responseSubmissionErrorMessage(IllegalStateException("permission denied"))

        assertEquals("permission denied", message)
    }
}

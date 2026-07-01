package com.litter.android.ui.conversation

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.AppMessagePhase
import uniffi.codex_mobile_client.HydratedAssistantMessageData
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.HydratedUserMessageData

class TurnGroupingTest {
    @Test
    fun lateUserBoundaryStartsItsSourceTurnBeforeAssistantItems() {
        val assistant = assistantItem(id = "assistant-2", turnId = "turn-2")
        val user = userItem(id = "user-2", turnId = "turn-2")

        val turns = buildTranscriptTurns(
            items = listOf(assistant, user),
            isStreaming = false,
            expandedRecentTurnCount = Int.MAX_VALUE,
        )

        assertEquals(1, turns.size)
        assertEquals("turn-turn-2", turns.single().id)
        assertEquals(listOf("user-2", "assistant-2"), turns.single().items.map { it.id })
    }

    @Test
    fun sourceTurnIdKeepsTurnIdentityStableWhenBoundaryArrivesLate() {
        val assistant = assistantItem(id = "assistant-2", turnId = "turn-2")
        val initialTurns = buildTranscriptTurns(
            items = listOf(assistant),
            isStreaming = true,
            expandedRecentTurnCount = Int.MAX_VALUE,
        )
        val lateBoundaryTurns = buildTranscriptTurns(
            items = listOf(assistant, userItem(id = "user-2", turnId = "turn-2")),
            isStreaming = true,
            expandedRecentTurnCount = Int.MAX_VALUE,
        )

        assertEquals("turn-turn-2", initialTurns.single().id)
        assertEquals(initialTurns.single().id, lateBoundaryTurns.single().id)
    }

    @Test
    fun pendingTailLocalUserOverlayNeedsWaitingIndicator() {
        val pendingUser = userItem(id = "local-user-message:1", turnId = "")

        assertEquals(true, hasPendingTailLocalUserMessage(listOf(pendingUser)))
    }

    @Test
    fun serverBackedTailUserBoundaryDoesNotNeedLocalWaitingIndicator() {
        val user = userItem(id = "user-2", turnId = "turn-2")

        assertEquals(false, hasPendingTailLocalUserMessage(listOf(user)))
    }

    @Test
    fun reasoningDisplayTextKeepsContentForManualExpansion() {
        assertEquals(
            "summary\n\ncontent",
            reasoningDisplayText(listOf("summary"), listOf("", "content")),
        )
    }

    @Test
    fun reasoningCollapsedLabelDistinguishesLiveTurn() {
        assertEquals("正在思考...", reasoningCollapsedLabel(isLiveTurn = true))
        assertEquals("思考过程已隐藏", reasoningCollapsedLabel(isLiveTurn = false))
    }

    private fun userItem(id: String, turnId: String): HydratedConversationItem =
        HydratedConversationItem(
            id = id,
            content = HydratedConversationItemContent.User(
                HydratedUserMessageData(
                    text = "你能做什么",
                    imageDataUris = emptyList(),
                ),
            ),
            sourceTurnId = turnId,
            sourceTurnIndex = null,
            timestamp = null,
            isFromUserTurnBoundary = true,
        )

    private fun assistantItem(id: String, turnId: String): HydratedConversationItem =
        HydratedConversationItem(
            id = id,
            content = HydratedConversationItemContent.Assistant(
                HydratedAssistantMessageData(
                    text = "我可以帮你分析项目。",
                    agentNickname = null,
                    agentRole = null,
                    phase = AppMessagePhase.FINAL_ANSWER,
                ),
            ),
            sourceTurnId = turnId,
            sourceTurnIndex = null,
            timestamp = null,
            isFromUserTurnBoundary = false,
        )
}

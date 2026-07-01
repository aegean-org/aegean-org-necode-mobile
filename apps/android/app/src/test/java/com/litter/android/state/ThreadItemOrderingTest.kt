package com.litter.android.state

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.AppMessagePhase
import uniffi.codex_mobile_client.HydratedAssistantMessageData
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.HydratedUserMessageData

class ThreadItemOrderingTest {
    @Test
    fun insertsLateUserBoundaryBeforeSameSourceTurnIdItems() {
        val assistant = assistantItem(
            id = "assistant-2",
            turnId = "turn-2",
            turnIndex = null,
        )
        val user = userItem(
            id = "user-2",
            turnId = "turn-2",
            turnIndex = null,
        )

        val index = insertionIndexForConversationItem(listOf(assistant), user)

        assertEquals(0, index)
    }

    @Test
    fun insertsLateUserBoundaryBeforeSameSourceTurnIndexItems() {
        val assistant = assistantItem(
            id = "assistant-2",
            turnId = "turn-2",
            turnIndex = 2u,
        )
        val user = userItem(
            id = "user-2",
            turnId = "turn-2",
            turnIndex = 2u,
        )

        val index = insertionIndexForConversationItem(listOf(assistant), user)

        assertEquals(0, index)
    }

    @Test
    fun insertsAssistantAfterSameSourceTurnIdUserBoundary() {
        val user = userItem(
            id = "user-2",
            turnId = "turn-2",
            turnIndex = null,
        )
        val assistant = assistantItem(
            id = "assistant-2",
            turnId = "turn-2",
            turnIndex = null,
        )

        val index = insertionIndexForConversationItem(listOf(user), assistant)

        assertEquals(1, index)
    }

    private fun userItem(
        id: String,
        turnId: String,
        turnIndex: UInt?,
    ): HydratedConversationItem =
        HydratedConversationItem(
            id = id,
            content = HydratedConversationItemContent.User(
                HydratedUserMessageData(
                    text = "hello",
                    imageDataUris = emptyList(),
                ),
            ),
            sourceTurnId = turnId,
            sourceTurnIndex = turnIndex,
            timestamp = null,
            isFromUserTurnBoundary = true,
        )

    private fun assistantItem(
        id: String,
        turnId: String,
        turnIndex: UInt?,
    ): HydratedConversationItem =
        HydratedConversationItem(
            id = id,
            content = HydratedConversationItemContent.Assistant(
                HydratedAssistantMessageData(
                    text = "response",
                    agentNickname = null,
                    agentRole = null,
                    phase = AppMessagePhase.FINAL_ANSWER,
                ),
            ),
            sourceTurnId = turnId,
            sourceTurnIndex = turnIndex,
            timestamp = null,
            isFromUserTurnBoundary = false,
        )
}

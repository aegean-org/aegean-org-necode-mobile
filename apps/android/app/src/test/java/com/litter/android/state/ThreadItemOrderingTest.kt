package com.litter.android.state

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.AppMessagePhase
import uniffi.codex_mobile_client.AppModeKind
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.HydratedAssistantMessageData
import uniffi.codex_mobile_client.HydratedConversationItem
import uniffi.codex_mobile_client.HydratedConversationItemContent
import uniffi.codex_mobile_client.HydratedReasoningData
import uniffi.codex_mobile_client.HydratedUserMessageData
import uniffi.codex_mobile_client.ThreadInfo
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.ThreadSummaryStatus

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

    @Test
    fun repositionsExistingItemWhenSourceTurnIdChanges() {
        val firstUser = userItem(
            id = "user-1",
            turnId = "turn-1",
            turnIndex = null,
        )
        val misplacedAssistant = assistantItem(
            id = "assistant-live",
            turnId = "turn-1",
            turnIndex = null,
        )
        val thirdUser = userItem(
            id = "user-3",
            turnId = "turn-3",
            turnIndex = null,
        )
        val correctedAssistant = assistantItem(
            id = "assistant-live",
            turnId = "turn-3",
            turnIndex = null,
        )

        val updated = upsertConversationItem(
            items = listOf(firstUser, misplacedAssistant, thirdUser),
            item = correctedAssistant,
        )

        assertEquals(
            listOf("user-1", "user-3", "assistant-live"),
            updated.map { it.id },
        )
    }

    @Test
    fun keepsExistingItemPositionWhenSourceTurnIdIsUnchanged() {
        val user = userItem(
            id = "user-1",
            turnId = "turn-1",
            turnIndex = null,
        )
        val firstAssistant = assistantItem(
            id = "assistant-live",
            turnId = "turn-1",
            turnIndex = null,
        )
        val secondAssistant = assistantItem(
            id = "assistant-other",
            turnId = "turn-1",
            turnIndex = null,
        )
        val updatedAssistant = assistantItem(
            id = "assistant-live",
            turnId = "turn-1",
            turnIndex = null,
        )

        val updated = upsertConversationItem(
            items = listOf(user, firstAssistant, secondAssistant),
            item = updatedAssistant,
        )

        assertEquals(
            listOf("user-1", "assistant-live", "assistant-other"),
            updated.map { it.id },
        )
    }

    @Test
    fun preservesLoadedHistoryWhenIncomingThreadSnapshotOnlyContainsNewTurn() {
        val firstUser = userItem(id = "user-1", turnId = "turn-1", turnIndex = 1u)
        val firstAssistant = assistantItem(id = "assistant-1", turnId = "turn-1", turnIndex = 1u)
        val secondUser = userItem(id = "user-2", turnId = "turn-2", turnIndex = 2u)
        val loaded = threadSnapshot(
            items = listOf(firstUser, firstAssistant),
        )
        val incoming = threadSnapshot(
            items = listOf(secondUser),
        )

        val merged = mergeThreadSnapshotPreservingLoadedItems(loaded, incoming)

        assertEquals(
            listOf("user-1", "assistant-1", "user-2"),
            merged.hydratedConversationItems.map { it.id },
        )
    }

    @Test
    fun replacesLoadedLogicalLiveItemsWhenIncomingSnapshotHasPersistedIds() {
        val firstUser = userItem(id = "user-1", turnId = "turn-1", turnIndex = 1u)
        val firstAssistant = assistantItem(id = "assistant-1", turnId = "turn-1", turnIndex = 1u)
        val loaded = threadSnapshot(
            items = listOf(
                firstUser,
                firstAssistant,
                userItem(id = "local-user-message:2", turnId = "turn-2", turnIndex = 2u, text = "我研究下"),
                reasoningItem(id = "live-reasoning", turnId = "turn-2", turnIndex = 2u),
                assistantItem(id = "live-assistant", turnId = "turn-2", turnIndex = 2u, text = "好的"),
            ),
        )
        val incoming = threadSnapshot(
            items = listOf(
                userItem(id = "persisted-user", turnId = "turn-2", turnIndex = 2u, text = "我研究下"),
                reasoningItem(id = "persisted-reasoning", turnId = "turn-2", turnIndex = 2u),
                assistantItem(id = "persisted-assistant", turnId = "turn-2", turnIndex = 2u, text = "好的"),
            ),
        )

        val merged = mergeThreadSnapshotPreservingLoadedItems(loaded, incoming)

        assertEquals(
            listOf("user-1", "assistant-1", "persisted-user", "persisted-reasoning", "persisted-assistant"),
            merged.hydratedConversationItems.map { it.id },
        )
    }

    @Test
    fun replacesSameTurnItemsWhenTurnIdRepairsSourceTurnIndex() {
        val loaded = threadSnapshot(
            items = listOf(
                userItem(id = "local-user-message:2", turnId = null, turnIndex = 2u, text = "你会说话吗"),
            ),
        )
        val incoming = threadSnapshot(
            items = listOf(
                userItem(id = "persisted-user", turnId = "turn-2", turnIndex = 2u, text = "你会说话吗"),
            ),
        )

        val merged = mergeThreadSnapshotPreservingLoadedItems(loaded, incoming)

        assertEquals(
            listOf("persisted-user"),
            merged.hydratedConversationItems.map { it.id },
        )
    }

    @Test
    fun keepsSourcelessAssistantFromOlderTurnWhenNewTurnHasSameText() {
        val loaded = threadSnapshot(
            items = listOf(
                userItem(id = "user-1", turnId = "turn-1", turnIndex = 1u, text = "你会说话吗"),
                assistantItem(id = "live-assistant-1", turnId = null, turnIndex = null, text = "好的"),
            ),
        )
        val incoming = threadSnapshot(
            items = listOf(
                userItem(id = "user-2", turnId = "turn-2", turnIndex = 2u, text = "你还挺棒"),
                assistantItem(id = "persisted-assistant-2", turnId = "turn-2", turnIndex = 2u, text = "好的"),
            ),
        )

        val merged = mergeThreadSnapshotPreservingLoadedItems(loaded, incoming)

        assertEquals(
            listOf("user-1", "live-assistant-1", "user-2", "persisted-assistant-2"),
            merged.hydratedConversationItems.map { it.id },
        )
    }

    private fun userItem(
        id: String,
        turnId: String?,
        turnIndex: UInt?,
        text: String = "hello",
    ): HydratedConversationItem =
        HydratedConversationItem(
            id = id,
            content = HydratedConversationItemContent.User(
                HydratedUserMessageData(
                    text = text,
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
        turnId: String?,
        turnIndex: UInt?,
        text: String = "response",
    ): HydratedConversationItem =
        HydratedConversationItem(
            id = id,
            content = HydratedConversationItemContent.Assistant(
                HydratedAssistantMessageData(
                    text = text,
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

    private fun reasoningItem(
        id: String,
        turnId: String?,
        turnIndex: UInt?,
    ): HydratedConversationItem =
        HydratedConversationItem(
            id = id,
            content = HydratedConversationItemContent.Reasoning(
                HydratedReasoningData(
                    summary = listOf("思考过程已隐藏"),
                    content = listOf("hidden reasoning"),
                ),
            ),
            sourceTurnId = turnId,
            sourceTurnIndex = turnIndex,
            timestamp = null,
            isFromUserTurnBoundary = false,
        )

    private fun threadSnapshot(
        items: List<HydratedConversationItem>,
    ): AppThreadSnapshot =
        AppThreadSnapshot(
            key = ThreadKey(serverId = "server-1", threadId = "thread-1"),
            info = ThreadInfo(
                id = "thread-1",
                title = null,
                model = null,
                status = ThreadSummaryStatus.IDLE,
                preview = null,
                cwd = null,
                path = null,
                modelProvider = null,
                agentNickname = null,
                agentRole = null,
                parentThreadId = null,
                forkedFromId = null,
                agentStatus = null,
                createdAt = null,
                updatedAt = null,
            ),
            agentRuntimeKind = "necode",
            collaborationMode = AppModeKind.DEFAULT,
            model = null,
            reasoningEffort = null,
            effectiveApprovalPolicy = null,
            effectiveSandboxPolicy = null,
            hydratedConversationItems = items,
            queuedFollowUps = emptyList(),
            activeTurnId = null,
            activePlanProgress = null,
            pendingPlanImplementationPrompt = null,
            contextTokensUsed = null,
            modelContextWindow = null,
            rateLimits = null,
            realtimeSessionId = null,
            goal = null,
            stats = null,
            tokenUsage = null,
            olderTurnsCursor = null,
            initialTurnsLoaded = true,
        )
}

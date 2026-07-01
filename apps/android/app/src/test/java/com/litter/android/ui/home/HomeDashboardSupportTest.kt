package com.litter.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ThreadKey

class HomeDashboardSupportTest {
    @Test
    fun sessionTitleUsesFirstUserQuestionForUntitledSession() {
        val session = sessionSummary(
            title = "Untitled session",
            preview = "",
            lastUserMessage = "  帮我分析一下这个项目的入口和主要模块  ",
            cwd = "D:\\project\\alleycat",
        )

        assertEquals("帮我分析一下这个项目的入口和主要模块", HomeDashboardSupport.sessionTitle(session))
    }

    @Test
    fun sessionTitleFallsBackToWorkspaceWhenNoUserQuestionExists() {
        val session = sessionSummary(
            title = "Untitled session",
            preview = "",
            lastUserMessage = null,
            cwd = "D:\\project\\alleycat",
        )

        assertEquals("alleycat", HomeDashboardSupport.sessionTitle(session))
    }

    @Test
    fun mergeHomeSessionsFallsBackToRecentSessionsWhenPinsAreStale() {
        val liveSession = sessionSummary(
            title = "真实会话",
            preview = "",
            lastUserMessage = "在吗",
            cwd = "D:\\project\\alleycat",
            threadId = "live-thread",
        )

        val merged = HomeDashboardSupport.mergeHomeSessions(
            pinned = listOf(PinnedThreadKey(serverId = "server", threadId = "missing-thread")),
            hidden = emptyList(),
            allSessions = listOf(liveSession),
        )

        assertEquals(listOf(liveSession), merged)
    }

    private fun sessionSummary(
        title: String,
        preview: String,
        lastUserMessage: String?,
        cwd: String,
        threadId: String = "thread",
    ) = AppSessionSummary(
        key = ThreadKey(serverId = "server", threadId = threadId),
        agentRuntimeKind = "necode",
        serverDisplayName = "server",
        serverHost = "localhost",
        title = title,
        preview = preview,
        cwd = cwd,
        model = "",
        modelProvider = "",
        parentThreadId = null,
        forkedFromId = null,
        agentNickname = null,
        agentRole = null,
        agentDisplayLabel = null,
        agentStatus = AppSubagentStatus.UNKNOWN,
        updatedAt = null,
        hasActiveTurn = false,
        isResumed = true,
        isSubagent = false,
        isFork = false,
        lastResponsePreview = null,
        lastResponseTurnId = null,
        lastUserMessage = lastUserMessage,
        lastToolLabel = null,
        recentToolLog = emptyList(),
        lastTurnStartMs = null,
        lastTurnEndMs = null,
        stats = null,
        tokenUsage = null,
        goal = null,
    )
}

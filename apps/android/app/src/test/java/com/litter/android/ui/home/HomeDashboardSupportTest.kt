package com.litter.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.codex_mobile_client.AppServerCapabilities
import uniffi.codex_mobile_client.AppServerHealth
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppServerTransportState
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.AppSnapshotRecord
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.AppVoiceSessionSnapshot
import uniffi.codex_mobile_client.AgentRuntimeInfo
import uniffi.codex_mobile_client.PinnedThreadKey
import uniffi.codex_mobile_client.ThreadKey

class HomeDashboardSupportTest {
    @Test
    fun sortedConnectedServersExcludesLocalDevice() {
        val snapshot = snapshotRecord(
            servers = listOf(
                serverSnapshot(serverId = "local", displayName = "This Device", isLocal = true),
                serverSnapshot(serverId = "necode", displayName = "NeCode", isLocal = false),
            ),
        )

        val servers = HomeDashboardSupport.sortedConnectedServers(snapshot)

        assertEquals(listOf("necode"), servers.map { it.serverId })
    }

    @Test
    fun recentSessionsExcludesLocalDeviceSessions() {
        val remoteSession = sessionSummary(
            title = "Remote",
            preview = "",
            lastUserMessage = "hello",
            cwd = "D:\\project\\remote",
            serverId = "necode",
            threadId = "remote-thread",
            updatedAt = 2L,
        )
        val localSession = sessionSummary(
            title = "Local",
            preview = "",
            lastUserMessage = "hello",
            cwd = "D:\\project\\local",
            serverId = "local",
            threadId = "local-thread",
            updatedAt = 3L,
        )
        val snapshot = snapshotRecord(
            servers = listOf(
                serverSnapshot(serverId = "local", displayName = "This Device", isLocal = true),
                serverSnapshot(serverId = "necode", displayName = "NeCode", isLocal = false),
            ),
            sessions = listOf(localSession, remoteSession),
        )

        val sessions = HomeDashboardSupport.recentSessions(snapshot)

        assertEquals(listOf(remoteSession), sessions)
    }

    @Test
    fun voiceServerPrefersConnectedNecodeRemoteAndNeverFallsBackToLocal() {
        val local = serverSnapshot(serverId = "local", displayName = "This Device", isLocal = true)
        val codexRemote = serverSnapshot(
            serverId = "codex-remote",
            displayName = "Codex",
            isLocal = false,
            runtimes = listOf(runtime(kind = "codex", available = true)),
        )
        val necodeRemote = serverSnapshot(
            serverId = "necode-remote",
            displayName = "NeCode",
            isLocal = false,
            runtimes = listOf(runtime(kind = "necode", available = true)),
        )

        assertEquals(
            "necode-remote",
            HomeDashboardSupport.voiceTranscriptionServerId(
                selectedProjectServerId = null,
                selectedServerId = null,
                servers = listOf(local, codexRemote, necodeRemote),
            ),
        )
        assertEquals(
            null,
            HomeDashboardSupport.voiceTranscriptionServerId(
                selectedProjectServerId = null,
                selectedServerId = null,
                servers = listOf(local, codexRemote),
            ),
        )
    }

    @Test
    fun voiceServerRecognizesNeCodeDisplayName() {
        val necodeDisplayOnly = serverSnapshot(
            serverId = "necode-display",
            displayName = "NeCode",
            isLocal = false,
            runtimes = listOf(runtime(kind = "agent", displayName = "NeCode", available = true)),
        )

        assertEquals(
            "necode-display",
            HomeDashboardSupport.voiceTranscriptionServerId(
                selectedProjectServerId = null,
                selectedServerId = null,
                servers = listOf(necodeDisplayOnly),
            ),
        )
    }

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

    @Test
    fun mergeHomeSessionsKeepsPinnedFirstAndIncludesNewRecentSessions() {
        val pinnedSession = sessionSummary(
            title = "Pinned",
            preview = "",
            lastUserMessage = "pinned",
            cwd = "D:\\project\\alleycat",
            threadId = "pinned-thread",
            updatedAt = 10L,
        )
        val cliSession = sessionSummary(
            title = "CLI session",
            preview = "",
            lastUserMessage = "new from CLI",
            cwd = "D:\\project\\alleycat",
            threadId = "cli-thread",
            updatedAt = 20L,
        )

        val merged = HomeDashboardSupport.mergeHomeSessions(
            pinned = listOf(PinnedThreadKey(serverId = "server", threadId = "pinned-thread")),
            hidden = emptyList(),
            allSessions = listOf(cliSession, pinnedSession),
        )

        assertEquals(listOf(pinnedSession, cliSession), merged)
    }

    private fun snapshotRecord(
        servers: List<AppServerSnapshot>,
        sessions: List<AppSessionSummary> = emptyList(),
    ) = AppSnapshotRecord(
        servers = servers,
        threads = emptyList(),
        sessionSummaries = sessions,
        agentDirectoryVersion = 0u,
        activeThread = null,
        pendingApprovals = emptyList(),
        pendingUserInputs = emptyList(),
        voiceSession = AppVoiceSessionSnapshot(
            activeThread = null,
            sessionId = null,
            phase = null,
            lastError = null,
            transcriptEntries = emptyList(),
            handoffThreadKey = null,
        ),
        terminalSessions = emptyList(),
        activeTerminalId = null,
    )

    private fun serverSnapshot(
        serverId: String,
        displayName: String,
        isLocal: Boolean,
        runtimes: List<AgentRuntimeInfo> = emptyList(),
    ) = AppServerSnapshot(
        serverId = serverId,
        displayName = displayName,
        host = "127.0.0.1",
        port = 0u.toUShort(),
        wakeMac = null,
        isLocal = isLocal,
        health = AppServerHealth.CONNECTED,
        transportState = AppServerTransportState.CONNECTED,
        capabilities = AppServerCapabilities(
            canUseTransportActions = true,
            canBrowseDirectories = true,
            canStartThreads = true,
            canResumeThreads = true,
            supportsTurnPagination = true,
        ),
        account = null,
        requiresOpenaiAuth = false,
        rateLimits = null,
        rateLimitsByRuntime = emptyList(),
        availableModels = null,
        agentRuntimes = runtimes,
        connectionProgress = null,
        usageStats = null,
        codexVersion = null,
    )

    private fun runtime(
        kind: String,
        displayName: String = kind,
        available: Boolean,
    ) = AgentRuntimeInfo(
        kind = kind,
        name = kind,
        displayName = displayName,
        available = available,
    )

    private fun sessionSummary(
        title: String,
        preview: String,
        lastUserMessage: String?,
        cwd: String,
        serverId: String = "server",
        threadId: String = "thread",
        updatedAt: Long? = null,
    ) = AppSessionSummary(
        key = ThreadKey(serverId = serverId, threadId = threadId),
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
        updatedAt = updatedAt,
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

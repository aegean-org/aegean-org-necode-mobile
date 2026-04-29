package com.litter.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litter.android.state.displayTitle
import com.litter.android.ui.LitterTextStyle
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.common.runtimeDrawable
import com.litter.android.ui.common.runtimeLabel
import com.litter.android.ui.common.runtimeSortIndex
import com.litter.android.ui.scaled
import uniffi.codex_mobile_client.AgentRuntimeKind
import uniffi.codex_mobile_client.AppSessionSummary
import uniffi.codex_mobile_client.PinnedThreadKey

/**
 * List of every thread across connected servers, sorted by recency and
 * filtered by the current query. Tapping a row toggles its pinned state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadSearchResults(
    sessions: List<AppSessionSummary>,
    pinnedKeys: Set<PinnedThreadKey>,
    query: String,
    runtimeKinds: List<AgentRuntimeKind>,
    selectedRuntimeKind: AgentRuntimeKind?,
    isRefreshing: Boolean,
    onRuntimeSelected: (AgentRuntimeKind?) -> Unit,
    onRefresh: () -> Unit,
    onPin: (AppSessionSummary) -> Unit,
    onUnpin: (AppSessionSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = run {
        val needle = query.trim().lowercase()
        sessions.filter { session ->
            (selectedRuntimeKind == null || session.agentRuntimeKind == selectedRuntimeKind) &&
                (needle.isEmpty() ||
                    session.displayTitle.lowercase().contains(needle)
                || (session.cwd ?: "").lowercase().contains(needle)
                || session.serverDisplayName.lowercase().contains(needle)
                || session.preview.lowercase().contains(needle))
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(LitterTheme.surface.copy(alpha = 0.92f), RoundedCornerShape(14.dp))
            .border(1.dp, LitterTheme.border.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
        ) {
            if (runtimeKinds.size > 1) {
                item(key = "runtime-filters") {
                    RuntimeFilterRow(
                        runtimeKinds = runtimeKinds.sortedBy { it.runtimeSortIndex },
                        selectedRuntimeKind = selectedRuntimeKind,
                        onRuntimeSelected = onRuntimeSelected,
                    )
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (sessions.isEmpty()) "No threads yet" else "No matches",
                            color = LitterTheme.textMuted,
                            fontSize = LitterTextStyle.caption.scaled,
                        )
                    }
                }
            } else {
                items(
                    filtered,
                    key = { "${it.key.serverId}/${it.key.threadId}" },
                ) { session ->
                    val key = PinnedThreadKey(
                        serverId = session.key.serverId,
                        threadId = session.key.threadId,
                    )
                    val isPinned = pinnedKeys.contains(key)
                    ThreadSearchRow(
                        session = session,
                        isPinned = isPinned,
                        onToggle = {
                            if (isPinned) onUnpin(session) else onPin(session)
                        },
                    )
                    Divider(color = LitterTheme.border.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
private fun RuntimeFilterRow(
    runtimeKinds: List<AgentRuntimeKind>,
    selectedRuntimeKind: AgentRuntimeKind?,
    onRuntimeSelected: (AgentRuntimeKind?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RuntimeFilterPill(
            label = "All",
            kind = null,
            isActive = selectedRuntimeKind == null,
            onClick = { onRuntimeSelected(null) },
        )
        runtimeKinds.forEach { kind ->
            RuntimeFilterPill(
                label = kind.runtimeLabel,
                kind = kind,
                isActive = selectedRuntimeKind == kind,
                onClick = { onRuntimeSelected(kind) },
            )
        }
    }
}

@Composable
private fun RuntimeFilterPill(
    label: String,
    kind: AgentRuntimeKind?,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (isActive) LitterTheme.accent else LitterTheme.surface.copy(alpha = 0.65f))
            .border(
                1.dp,
                if (isActive) LitterTheme.accent else LitterTheme.border.copy(alpha = 0.7f),
                RoundedCornerShape(percent = 50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (kind != null) {
            Image(
                painter = painterResource(id = kind.runtimeDrawable),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = label,
            color = if (isActive) LitterTheme.onAccentStrong else LitterTheme.textSecondary,
            fontSize = LitterTextStyle.caption.scaled,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun ThreadSearchRow(
    session: AppSessionSummary,
    isPinned: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThreadSearchRuntimeIcon(kind = session.agentRuntimeKind)
        Spacer(Modifier.size(8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = session.displayTitle,
                color = LitterTheme.textPrimary,
                fontSize = LitterTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = session.serverDisplayName,
                    color = LitterTheme.accent.copy(alpha = 0.7f),
                    fontSize = 10f.scaled,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "\u00b7",
                    color = LitterTheme.textMuted.copy(alpha = 0.5f),
                    fontSize = 10f.scaled,
                )
                Text(
                    text = HomeDashboardSupport.workspaceLabel(session.cwd),
                    color = LitterTheme.textSecondary.copy(alpha = 0.8f),
                    fontSize = 10f.scaled,
                    fontFamily = FontFamily.Monospace,
                )
                val relative = HomeDashboardSupport.relativeTime(session.updatedAt)
                if (relative.isNotEmpty()) {
                    Text(
                        text = "\u00b7",
                        color = LitterTheme.textMuted.copy(alpha = 0.5f),
                        fontSize = 10f.scaled,
                    )
                    Text(
                        text = relative,
                        color = LitterTheme.textMuted.copy(alpha = 0.8f),
                        fontSize = 10f.scaled,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Icon(
            imageVector = if (isPinned) Icons.Default.CheckCircle else Icons.Default.Add,
            contentDescription = null,
            tint = if (isPinned) LitterTheme.accent else LitterTheme.textPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ThreadSearchRuntimeIcon(kind: AgentRuntimeKind) {
    Image(
        painter = painterResource(id = kind.runtimeDrawable),
        contentDescription = HomeDashboardSupport.runtimeLabel(kind),
        modifier = Modifier.size(16.dp),
    )
}

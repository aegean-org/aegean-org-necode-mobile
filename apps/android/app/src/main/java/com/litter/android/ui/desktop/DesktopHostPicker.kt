package com.litter.android.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.common.LitterDropdownMenu
import com.litter.android.ui.common.LitterDropdownMenuItem
import uniffi.codex_mobile_client.DesktopHost

internal fun desktopConnectErrorMessage(error: Exception): String = when (error) {
    is uniffi.codex_mobile_client.ClientException.Rpc -> error.v1
    is uniffi.codex_mobile_client.ClientException.Transport -> error.v1
    is uniffi.codex_mobile_client.ClientException.InvalidParams -> error.v1
    is uniffi.codex_mobile_client.ClientException.Serialization -> error.v1
    is uniffi.codex_mobile_client.ClientException.EventClosed -> error.v1
    is uniffi.codex_mobile_client.ClientException.MinigameGenerationFailed -> error.v1
    else -> error.localizedMessage ?: error.toString()
}

/** The same picker surface is used after login and while switching a connected desktop. */
@Composable
fun DesktopHostPicker(
    hosts: List<DesktopHost>,
    activeHostId: String?,
    healthy: Boolean,
    busy: Boolean,
    connectingHostId: String?,
    error: String?,
    showLogout: Boolean,
    accountId: String? = null,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (DesktopHost) -> Unit,
    onRemove: (DesktopHost) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    hostsLoaded: Boolean = hosts.isNotEmpty(),
) {
    var removalHost by remember { mutableStateOf<DesktopHost?>(null) }
    removalHost?.let { host ->
        AlertDialog(
            onDismissRequest = { removalHost = null },
            title = { Text("移除电脑“${host.name}”？") },
            text = { Text("仅移除连接登记，不会删除电脑上的项目、文件或会话。再次使用这台电脑需要重新登记。") },
            confirmButton = {
                TextButton(enabled = !busy && hosts.any { it.id == host.id && it.status == "offline" },
                    onClick = { removalHost = null; onRemove(host) }) {
                    Text("移除电脑", color = LitterTheme.danger)
                }
            },
            dismissButton = { TextButton(onClick = { removalHost = null }) { Text("取消") } },
            containerColor = LitterTheme.background,
        )
    }
    val ordered = hosts.sortedWith(compareBy<DesktopHost> { it.id != activeHostId }
        .thenBy { it.status != "available" }.thenBy { it.name })
    val currentHost = ordered.firstOrNull { it.id == activeHostId }
    Column(modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text("选择电脑", color = LitterTheme.textPrimary, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(top = 6.dp))
            IconButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新电脑", tint = LitterTheme.textSecondary)
            }
            IconButton(onClick = onDismiss, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).background(LitterTheme.surface, CircleShape)) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = LitterTheme.textSecondary)
            }
        }
        error?.let {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(LitterTheme.surface).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(it, color = LitterTheme.danger, fontSize = 13.sp, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                TextButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("重试", color = LitterTheme.accentStrong, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ordered.isEmpty() && busy) {
                DesktopCenterLoading("正在查找在线电脑…", Modifier.fillMaxWidth())
            } else if (ordered.isEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(LitterTheme.surface).padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.DesktopWindows, contentDescription = null,
                        tint = LitterTheme.accentStrong)
                    Text(if (busy) "正在查找电脑…" else if (!hostsLoaded) "暂时无法获取电脑列表" else "暂无可连接的电脑",
                        color = LitterTheme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (busy) "稍候即可选择电脑" else if (!hostsLoaded) "请稍后刷新；若仍失败，再检查网络或重新登录" else "确认桌面端已登录同一账号，再点击刷新",
                        color = LitterTheme.textSecondary, fontSize = 13.sp)
                }
            }
            if (ordered.isNotEmpty() && ordered.all { it.status != "available" } && (activeHostId == null || !healthy)) {
                Text("电脑暂时不在线。请在电脑上打开 NeCode，并保持联网，然后刷新列表。",
                    color = LitterTheme.textSecondary, fontSize = 13.sp)
            }
            if (ordered.isNotEmpty()) {
                if (busy && connectingHostId == null) {
                    DesktopCenterLoading("正在更新电脑列表…", Modifier.fillMaxWidth())
                }
                currentHost?.let { host ->
                    val action = desktopHostAction(activeHostId, host.id, host.status, healthy)
                    DesktopHostTile(
                        name = host.name, status = if (healthy) "在线" else "连接中断",
                        action = if (action == DesktopHostAction.CURRENT) "继续使用" else "重连",
                        online = healthy, current = true,
                        enabled = !busy && connectingHostId == null,
                        loading = connectingHostId == host.id,
                    ) { onSelect(host) }
                }
                val otherHosts = ordered.filter { it.id != activeHostId }
                if (currentHost != null && otherHosts.isNotEmpty()) {
                    Text("其他电脑", color = LitterTheme.textSecondary, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
                }
                Column {
                    otherHosts.forEachIndexed { index, host ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 70.dp), color = LitterTheme.divider)
                        val action = desktopHostAction(activeHostId, host.id, host.status, healthy)
                        val available = host.status == "available"
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                DesktopHostTile(
                                    name = host.name,
                                    status = if (available) "在线" else if (host.status == "busy") "占用中" else "离线",
                                    action = if (action == DesktopHostAction.CONNECT) "连接" else "不可连接",
                                    online = available, current = false,
                                    enabled = !busy && connectingHostId == null && action == DesktopHostAction.CONNECT,
                                    loading = connectingHostId == host.id,
                                ) { onSelect(host) }
                            }
                            if (host.status == "offline") {
                                var expanded by remember(host.id) { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { expanded = true }, enabled = !busy && connectingHostId == null) {
                                        Icon(Icons.Default.MoreHoriz, contentDescription = "更多操作：${host.name}", tint = LitterTheme.textSecondary)
                                    }
                                    LitterDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        LitterDropdownMenuItem(text = { Text("移除电脑", color = LitterTheme.danger) },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null,
                                                tint = LitterTheme.danger, modifier = Modifier.size(20.dp)) },
                                            enabled = !busy && connectingHostId == null,
                                            onClick = { expanded = false; removalHost = host })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showLogout) {
            HorizontalDivider(color = LitterTheme.divider)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(accountId?.takeIf { it.isNotBlank() }?.let { "NE 账号 · $it" } ?: "已登录 NE 账号",
                    color = LitterTheme.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onLogout, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("退出登录", color = LitterTheme.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

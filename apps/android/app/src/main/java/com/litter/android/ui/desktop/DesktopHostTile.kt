package com.litter.android.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litter.android.ui.LitterTheme

@Composable
fun DesktopHostTile(
    name: String,
    status: String,
    action: String,
    online: Boolean,
    current: Boolean,
    enabled: Boolean,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val statusColor = when {
        loading || status == "占用中" -> LitterTheme.warning
        online -> LitterTheme.success
        status == "连接中断" -> LitterTheme.danger
        else -> LitterTheme.textMuted
    }
    val visibleStatus = if (loading) "正在连接" else status
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(if (current) 14.dp else 0.dp))
            .background(if (current) LitterTheme.surfaceLight else LitterTheme.background)
            .clickable(enabled = enabled && !loading, onClickLabel = "$action $name", role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (current && online && !loading) "当前连接" else visibleStatus },
    ) {
        val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.2f
        Column(Modifier.fillMaxWidth().heightIn(min = if (current) 76.dp else 68.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (stacked) 8.dp else 0.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (current) LitterTheme.background else LitterTheme.surface),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DesktopWindows, contentDescription = null,
                        tint = if (online) LitterTheme.accentStrong else LitterTheme.textMuted,
                        modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, color = LitterTheme.textPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                        Text(if (current && online && !loading) "当前连接" else visibleStatus,
                            color = LitterTheme.textSecondary, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!stacked && action != "不可连接") DesktopHostAction(action, current, enabled, loading)
            }
            if (stacked && action != "不可连接") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    DesktopHostAction(action, current, enabled, loading)
                }
            }
        }
    }
}

@Composable
private fun DesktopHostAction(action: String, current: Boolean, enabled: Boolean, loading: Boolean) {
    if (loading) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(16.dp), color = LitterTheme.accentStrong, strokeWidth = 2.dp)
        }
        return
    }
    if (current) {
        Box(Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (enabled) LitterTheme.textPrimary else LitterTheme.surface).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center) {
            Text(action, color = if (enabled) LitterTheme.background else LitterTheme.textMuted, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        return
    }
    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(action, color = if (enabled) LitterTheme.accentStrong else LitterTheme.textMuted, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = if (enabled) LitterTheme.accentStrong else LitterTheme.textMuted, modifier = Modifier.size(20.dp))
    }
}

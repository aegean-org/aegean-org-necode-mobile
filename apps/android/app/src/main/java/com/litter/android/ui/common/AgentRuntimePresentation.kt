package com.litter.android.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litter.android.ui.LitterTheme
import com.sigkitten.litter.android.R
import uniffi.codex_mobile_client.AgentRuntimeKind

val AgentRuntimeKind.runtimeLabel: String
    get() = when (this) {
        AgentRuntimeKind.CODEX -> "Codex"
        AgentRuntimeKind.PI -> "Pi"
        AgentRuntimeKind.OPENCODE -> "opencode"
        AgentRuntimeKind.CLAUDE -> "Claude"
        AgentRuntimeKind.DROID -> "Droid"
    }

@get:DrawableRes
val AgentRuntimeKind.runtimeDrawable: Int
    get() = when (this) {
        AgentRuntimeKind.CODEX -> R.drawable.agent_codex
        AgentRuntimeKind.PI -> R.drawable.agent_pi
        AgentRuntimeKind.OPENCODE -> R.drawable.agent_opencode
        AgentRuntimeKind.CLAUDE -> R.drawable.agent_claude
        AgentRuntimeKind.DROID -> R.drawable.agent_droid
    }

val AgentRuntimeKind.runtimeSortIndex: Int
    get() = when (this) {
        AgentRuntimeKind.CODEX -> 0
        AgentRuntimeKind.PI -> 1
        AgentRuntimeKind.OPENCODE -> 2
        AgentRuntimeKind.CLAUDE -> 3
        AgentRuntimeKind.DROID -> 4
    }

val AgentRuntimeKind.isBeta: Boolean
    get() = when (this) {
        AgentRuntimeKind.CLAUDE,
        AgentRuntimeKind.PI,
        AgentRuntimeKind.OPENCODE,
        AgentRuntimeKind.DROID -> true
        AgentRuntimeKind.CODEX -> false
    }

private val betaAgentNameAliases = setOf(
    "claude", "claude-code", "claude_code",
    "pi", "pi.dev", "pidev",
    "opencode", "open-code", "open_code", "open code",
    "droid", "factory", "factory-droid", "factory_droid", "factory droid",
)

fun isBetaAgentName(name: String, displayName: String): Boolean =
    name.lowercase() in betaAgentNameAliases || displayName.lowercase() in betaAgentNameAliases

@Composable
fun BetaBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(
                width = 0.5.dp,
                color = LitterTheme.accent.copy(alpha = 0.6f),
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = "BETA",
            color = LitterTheme.accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

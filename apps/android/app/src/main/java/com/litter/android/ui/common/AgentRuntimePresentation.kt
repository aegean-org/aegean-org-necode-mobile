package com.litter.android.ui.common

import androidx.annotation.DrawableRes
import com.sigkitten.litter.android.R
import uniffi.codex_mobile_client.AgentRuntimeKind

val AgentRuntimeKind.runtimeLabel: String
    get() = when (this) {
        AgentRuntimeKind.CODEX -> "Codex"
        AgentRuntimeKind.PI -> "Pi"
        AgentRuntimeKind.OPENCODE -> "opencode"
        AgentRuntimeKind.CLAUDE -> "Claude"
    }

@get:DrawableRes
val AgentRuntimeKind.runtimeDrawable: Int
    get() = when (this) {
        AgentRuntimeKind.CODEX -> R.drawable.agent_codex
        AgentRuntimeKind.PI -> R.drawable.agent_pi
        AgentRuntimeKind.OPENCODE -> R.drawable.agent_opencode
        AgentRuntimeKind.CLAUDE -> R.drawable.agent_claude
    }

val AgentRuntimeKind.runtimeSortIndex: Int
    get() = when (this) {
        AgentRuntimeKind.CODEX -> 0
        AgentRuntimeKind.PI -> 1
        AgentRuntimeKind.OPENCODE -> 2
        AgentRuntimeKind.CLAUDE -> 3
    }

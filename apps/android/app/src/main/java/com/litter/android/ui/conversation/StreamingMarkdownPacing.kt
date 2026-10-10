package com.litter.android.ui.conversation

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

internal fun streamingMarkdownNextEnd(text: String, start: Int): Int {
    val remaining = text.length - start
    val step = when {
        remaining <= 2 -> remaining
        remaining <= 12 -> 1
        remaining <= 48 -> 2
        remaining <= 96 -> 4
        else -> minOf(32, (remaining + 15) / 16)
    }
    val end = (start + step).coerceAtMost(text.length)
    // Kotlin indexes UTF-16; never reveal half of an emoji.
    return if (end in 1 until text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end + 1 else end
}

@Composable
internal fun rememberStreamingMarkdown(text: String, streaming: Boolean): String {
    val target by rememberUpdatedState(text)
    var shown by remember { mutableStateOf(text) }
    val active = streaming && ValueAnimator.areAnimatorsEnabled()
    // Keep one clock across incoming snapshots, rather than restarting it for every chunk.
    LaunchedEffect(active) {
        if (!active) {
            shown = target
            return@LaunchedEffect
        }
        while (isActive) {
            if (target == shown) snapshotFlow { target }.first { it != shown }
            delay(40)
            val current = target
            shown = if (!current.startsWith(shown) || !ValueAnimator.areAnimatorsEnabled()) current
                else current.take(streamingMarkdownNextEnd(current, shown.length))
        }
    }
    // History, corrections, completion and stop are exact snapshots, with no queued tail.
    return if (active && text.startsWith(shown)) shown else text
}

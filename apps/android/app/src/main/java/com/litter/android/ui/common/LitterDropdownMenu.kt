package com.litter.android.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.litter.android.ui.LitterTheme

/** Shared native popup chrome; actions and dismissal stay owned by the caller. */
@Composable
fun LitterDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 200.dp, max = 300.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = LitterTheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(0.5.dp, LitterTheme.border),
        content = content,
    )
}

@Composable
fun LitterDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        colors = MenuDefaults.itemColors(
            textColor = LitterTheme.textPrimary,
            leadingIconColor = LitterTheme.textSecondary,
            trailingIconColor = LitterTheme.accentStrong,
            disabledTextColor = LitterTheme.textMuted,
            disabledLeadingIconColor = LitterTheme.textMuted,
            disabledTrailingIconColor = LitterTheme.textMuted,
        ),
    )
}

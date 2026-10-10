package com.litter.android.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litter.android.ui.LitterTheme

internal class ListLoadMoreGesture {
    private var consumed = false

    fun onScroll(upward: Boolean, atEnd: Boolean, canLoad: Boolean): Boolean {
        if (!upward || !atEnd || !canLoad || consumed) return false
        consumed = true
        return true
    }

    fun endGesture() { consumed = false }
}

/** UI-only pagination. Cursors, request cancellation and errors stay with the caller. */
@Composable
fun PaginatedLazyColumn(
    hasMore: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    loadingText: String = "正在加载更多…",
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
    content: LazyListScope.() -> Unit,
) {
    val canLoad by rememberUpdatedState(enabled && hasMore && !loading && error == null)
    val loadMore by rememberUpdatedState(onLoadMore)
    val footerKey = "pagination-footer"
    val connection = remember(state) {
        val gesture = ListLoadMoreGesture()
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && gesture.onScroll(
                    upward = available.y < 0,
                    atEnd = state.layoutInfo.visibleItemsInfo.any { it.key == footerKey },
                    canLoad = canLoad,
                )) loadMore()
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                gesture.endGesture()
                return Velocity.Zero
            }
        }
    }
    LazyColumn(modifier.nestedScroll(connection), state = state, contentPadding = contentPadding) {
        content()
        item(key = footerKey) {
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = LitterTheme.accentStrong, strokeWidth = 2.dp)
                    Text(loadingText, color = LitterTheme.textSecondary, fontSize = 13.sp)
                } else if (error != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = LitterTheme.textSecondary, fontSize = 12.sp, maxLines = 2)
                    TextButton(onClick = onLoadMore, enabled = enabled) { Text("重试加载", color = LitterTheme.accentStrong) }
                } else Text(if (hasMore) "上拉加载更多" else "已全部加载", color = LitterTheme.textMuted, fontSize = 13.sp)
            }
        }
    }
}

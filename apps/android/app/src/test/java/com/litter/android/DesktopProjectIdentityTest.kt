package com.litter.android

import com.litter.android.ui.desktop.DesktopProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import com.litter.android.ui.desktop.desktopProjectsByRecentOpen

class DesktopProjectIdentityTest {
    @Test fun recentOpenOrdersNewestFirstAndPreservesUnopenedOrder() {
        val rows = listOf("unopened-a", "old", "new", "unopened-b").map { DesktopProject("global", it, "D:/" + it) }
        val times = mapOf(rows[1].key to 10L, rows[2].key to 20L)
        assertEquals(listOf("new", "old", "unopened-a", "unopened-b"),
            desktopProjectsByRecentOpen(rows) { times[it.key] ?: 0L }.map { it.name })
    }

    @Test fun nonGitFoldersUseDifferentNavigationIdentities() {
        val first = DesktopProject("global", "first", "D:\\first")
        val second = DesktopProject("global", "second", "D:\\second")
        assertEquals(first.id, second.id)
        assertNotEquals(first.key, second.key)
        assertEquals(first.key, first.copy(name = "改名").key)
    }
}

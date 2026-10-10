package com.litter.android

import com.litter.android.ui.desktop.DesktopProject
import com.litter.android.ui.desktop.DesktopSession
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopConversationPresentationTest {
    private val project = DesktopProject("global", "D:\\project\\kkch\\", "D:\\project\\kkch")

    @Test fun projectLabelHandlesWindowsAndUnixPaths() {
        assertEquals("kkch", project.displayName)
        assertEquals("app", DesktopProject("git", "/home/team/app/", "/home/team/app").displayName)
        assertEquals("项目别名", project.copy(name = "项目别名").displayName)
    }

    @Test fun emptyProjectNameUsesDirectoryWithoutChangingIdentity() {
        assertEquals("kkch", project.copy(name = "").displayName)
        assertEquals(project.key, project.copy(name = "").key)
        assertEquals("/", DesktopProject("global", "", "/").displayName)
    }

    @Test fun onlyExactServerGeneratedTitlesAreLocalized() {
        assertEquals("新会话", session("New session - 2026-10-08T08:00:00.000Z").displayTitle)
        assertEquals("子会话", session("Child session - 2026-10-08T08:00:00.000Z").displayTitle)
    }

    @Test fun customTitlesAndSourceDataStayUnchanged() {
        listOf("修复登录", "New session - migration plan", "New session - 2026-10-08", "New session - 2026-10-08T08:00:00.000Z notes").forEach {
            assertEquals(it, session(it).displayTitle)
            assertEquals(it, session(it).title)
        }
    }

    private fun session(title: String) = DesktopSession("session", title, project, 0L)
}

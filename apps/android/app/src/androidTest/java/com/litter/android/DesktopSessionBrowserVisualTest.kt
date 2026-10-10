package com.litter.android

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.litter.android.ui.LitterAppTheme
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.desktop.DesktopProject
import com.litter.android.ui.desktop.DesktopSession
import com.litter.android.ui.desktop.DesktopSessionBrowser
import com.litter.android.ui.desktop.desktopSessionTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class DesktopSessionBrowserVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sessionTimeIsCompactAndChinese() {
        org.junit.Assert.assertEquals("刚刚", desktopSessionTime(1_000_000L - 20_000L, 1_000_000L))
        org.junit.Assert.assertEquals("2 分钟前", desktopSessionTime(1_000_000L - 120_000L, 1_000_000L))
        org.junit.Assert.assertEquals("昨天", desktopSessionTime(1_000_000L - 86_400_000L, 1_000_000L))
    }
    @Test fun recentSessionsAndProjectsUseTheProductionBrowser() {
        val alpha = DesktopProject("alpha", "necode-desktop", "D:/project/necode-desktop")
        val beta = DesktopProject("beta", "手机端长项目名称与工作区", "D:/project/litter-mobile-desktop-connect")
        val empty = DesktopProject("empty", "空项目", "D:/project/empty")
        val now = System.currentTimeMillis()
        val created = mutableListOf<DesktopProject>()
        var busy by mutableStateOf(false)
        var creatingProjectKey by mutableStateOf<String?>(null)
        var project by mutableStateOf<DesktopProject?>(null)
        compose.setContent {
            var projectsTab by remember { mutableStateOf(false) }
            var search by remember { mutableStateOf("") }
            LitterAppTheme {
                Column(Modifier.fillMaxSize().background(LitterTheme.background)) {
                    DesktopSessionBrowser(
                        projects = listOf(alpha, beta, empty),
                        sessions = listOf(
                            DesktopSession("one", "修复移动端连接态会话列表中的长标题与项目切换体验", alpha, now - 120_000),
                            DesktopSession("two", "继续昨天的桌面端项目", beta, now - 3_600_000),
                            DesktopSession("three", "新会话", alpha, now - 86_400_000),
                        ),
                        selectedProject = project, onSelectedProjectChange = { project = it },
                        showProjects = projectsTab, onShowProjectsChange = { projectsTab = it },
                        search = search, onSearchChange = { search = it },
                        initialLoadComplete = true, busy = busy, error = null,
                        onRetry = {}, onOpenSession = {}, onCreateSession = { created.add(it) }, onAddProject = {},
                        creatingProjectKey = creatingProjectKey,
                    )
                }
            }
        }
        compose.onNodeWithText("最近会话").assertExists()
        compose.onNodeWithText("修复移动端连接态会话列表中的长标题与项目切换体验").assertExists()
        capture("desktop-browser-recent.png")
        compose.onNodeWithText("项目").performClick()
        compose.onNodeWithText("空项目").assertExists()
        compose.onNodeWithText("新会话").assertDoesNotExist()
        compose.onNodeWithText("添加项目").assertExists()
        capture("desktop-browser-projects.png")
        val create = compose.onNodeWithContentDescription("在${beta.displayName}新建会话")
        create.assertExists().assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(listOf(beta), created)
            org.junit.Assert.assertNull(project)
        }
        compose.onNodeWithText("空项目").assertExists()
        val other = compose.onNodeWithContentDescription("在${alpha.displayName}新建会话")
        val idleIcon = other.captureToImage().asAndroidBitmap()
        compose.runOnIdle {
            busy = true
            creatingProjectKey = beta.key
        }
        assertTrue("Other project icons must not turn gray during creation", idleIcon.sameAs(other.captureToImage().asAndroidBitmap()))
        val loading = compose.onNodeWithContentDescription("正在为${beta.displayName}新建会话")
        loading.assertExists()
        loading.onParent().assertIsNotEnabled().performClick()
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(1)
        other.assertIsNotEnabled().performClick()
        capture("desktop-browser-projects-creating.png")
        compose.runOnIdle {
            org.junit.Assert.assertEquals(listOf(beta), created)
            busy = false
            creatingProjectKey = null
        }
        create.assertExists().assertIsEnabled()
        compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        compose.onNodeWithText("空项目").performClick()
        compose.onNodeWithText("这个项目暂无会话").assertExists()
        compose.onNodeWithText("最近会话").assertDoesNotExist()
        compose.onNodeWithText("项目会话").assertExists()
        compose.runOnIdle { org.junit.Assert.assertEquals(empty, project) }
        capture("desktop-browser-empty.png")
    }

    private fun capture(name: String) {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), name)
        FileOutputStream(file).use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.exists() && file.length() > 0)
    }
}

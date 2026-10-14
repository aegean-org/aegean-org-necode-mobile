package com.litter.android

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.litter.android.ui.LitterAppTheme
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.desktop.DesktopHostTile
import com.litter.android.ui.desktop.DesktopHostPicker
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.codex_mobile_client.DesktopHost

@RunWith(AndroidJUnit4::class)
class DesktopHostTileVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingHostNamesTheConnectionInTheRow() {
        compose.setContent {
            LitterAppTheme {
                DesktopHostTile("开发工作站", "在线", "连接", true, false, false, loading = true) {}
            }
        }
        compose.onNodeWithText("正在连接").assertExists()
    }

    @Test fun emptyHostFailureProvidesExplicitRetry() {
        var refreshed = false
        compose.setContent {
            LitterAppTheme {
                DesktopHostPicker(
                    hosts = emptyList(), activeHostId = null, healthy = false, busy = false,
                    connectingHostId = null, error = "网络暂时不可用", showLogout = true,
                    onRefresh = { refreshed = true }, onDismiss = {}, onSelect = {}, onRemove = {}, onLogout = {},
                )
            }
        }
        compose.onNodeWithText("暂时无法获取电脑列表").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        assertTrue(refreshed)
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun completeHostPickerHasRealHeaderAndMultipleStates() {
        compose.setContent {
            LitterAppTheme {
                ModalBottomSheet(onDismissRequest = {},
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = LitterTheme.background) {
                    DesktopHostPicker(
                        hosts = listOf(
                            DesktopHost("current", "AegeanQAM · Windows", "endpoint-1", null, "available"),
                            DesktopHost("mac", "开发工作站 · MacBook Pro", "endpoint-2", null, "available"),
                            DesktopHost("busy", "会议室电脑", "endpoint-3", null, "busy"),
                            DesktopHost("offline", "离线的长名称电脑 MacBook Pro 16-inch", "endpoint-4", null, "offline"),
                        ),
                        activeHostId = "current", healthy = true, busy = false,
                        connectingHostId = null, error = null, showLogout = true, accountId = "1050805",
                        onRefresh = {}, onDismiss = {}, onSelect = {}, onRemove = {}, onLogout = {},
                    )
                }
            }
        }
        compose.onNodeWithText("选择电脑").assertExists()
        compose.onNodeWithText("切换电脑，继续那台电脑上的会话").assertDoesNotExist()
        compose.onNodeWithText("其他电脑").assertExists()
        compose.onNodeWithText("当前连接").assertExists()
        compose.onAllNodesWithText("在线", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("继续使用").assertExists()
        compose.onNodeWithText("占用中", substring = true).assertExists()
        compose.onNodeWithText("退出登录").assertIsDisplayed()
        val image = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "desktop-host-picker.png")
        FileOutputStream(image).use {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(image.length() > 0)
        compose.onNodeWithText("离线的长名称电脑 MacBook Pro 16-inch").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("NE 账号 · 1050805").assertIsDisplayed()
        compose.onNodeWithText("退出登录").assertIsDisplayed()
    }

    @Test fun singleHostShowsDeviceWithoutRedundantSection() {
        compose.setContent {
            LitterAppTheme {
                DesktopHostPicker(
                    hosts = listOf(DesktopHost("one", "AegeanQAM · Windows", "endpoint", null, "available")),
                    activeHostId = null, healthy = true, busy = false, connectingHostId = null,
                    error = null, showLogout = false, onRefresh = {}, onDismiss = {},
                    onSelect = {}, onRemove = {}, onLogout = {},
                )
            }
        }
        compose.onNodeWithText("连接后继续电脑上的项目与会话").assertDoesNotExist()
        compose.onNodeWithText("AegeanQAM · Windows").assertIsDisplayed()
        compose.onNodeWithText("你的电脑").assertDoesNotExist()
        compose.onNodeWithText("1 / 1 台在线").assertDoesNotExist()
    }
    @Test fun onlyOfflineHostsOfferRemovalAndConfirmationIsRequired() {
        var removed: String? = null
        compose.setContent {
            LitterAppTheme {
                DesktopHostPicker(
                    hosts = listOf(
                        DesktopHost("online", "在线电脑", "endpoint-1", null, "available"),
                        DesktopHost("busy", "占用电脑", "endpoint-2", null, "busy"),
                        DesktopHost("old", "旧电脑", "endpoint-3", null, "offline"),
                    ),
                    activeHostId = null, healthy = true, busy = false, connectingHostId = null,
                    error = null, showLogout = false, onRefresh = {}, onDismiss = {}, onSelect = {},
                    onRemove = { removed = it.id }, onLogout = {},
                )
            }
        }
        compose.onNodeWithContentDescription("更多操作：在线电脑").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多操作：占用电脑").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多操作：旧电脑").performClick()
        compose.onNodeWithText("移除电脑").performClick()
        assertTrue(removed == null)
        compose.onNodeWithText("取消").performClick()
        assertTrue(removed == null)
        compose.onNodeWithContentDescription("更多操作：旧电脑").performClick()
        compose.onNodeWithText("移除电脑").performClick()
        compose.onNodeWithText("移除电脑").performClick()
        assertTrue(removed == "old")
    }

    @Test fun hostStatesAtDeviceWidth() {
        compose.setContent {
            LitterAppTheme {
                Column(Modifier.fillMaxSize().background(LitterTheme.background)
                    .padding(horizontal = 20.dp).padding(top = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("选择电脑", color = LitterTheme.textPrimary)
                    val shape = RoundedCornerShape(16.dp)
                    Column(Modifier.fillMaxWidth().clip(shape).background(LitterTheme.surface)
                        .border(1.dp, LitterTheme.border, shape)) {
                        DesktopHostTile("AegeanQAM · Windows", "当前连接", "继续使用", true, true, true) {}
                        HorizontalDivider(Modifier.padding(start = 70.dp), color = LitterTheme.divider)
                        DesktopHostTile("开发工作站 · MacBook Pro", "在线", "连接", true, false, true) {}
                        HorizontalDivider(Modifier.padding(start = 70.dp), color = LitterTheme.divider)
                        DesktopHostTile("会议室电脑", "占用中", "不可连接", false, false, false) {}
                        HorizontalDivider(Modifier.padding(start = 70.dp), color = LitterTheme.divider)
                        DesktopHostTile("离线的长名称电脑 MacBook Pro 16-inch", "离线", "不可连接", false, false, false) {}
                    }
                }
            }
        }
        compose.onNodeWithText("继续使用").assertExists()
        compose.onNodeWithText("占用中", substring = true).assertExists()
        compose.onNodeWithText("离线的长名称电脑 MacBook Pro 16-inch").assertExists()
    }

    @Test fun interruptedCurrentHostDoesNotPretendToBeConnected() {
        compose.setContent {
            LitterAppTheme {
                DesktopHostTile("工作电脑", "连接中断", "重连", false, true, true) {}
            }
        }
        compose.onNodeWithText("连接中断").assertExists()
    }
    @Test fun hostStateIsInlineWithIdentity() {
        compose.setContent {
            LitterAppTheme {
                DesktopHostTile("开发工作站", "在线", "连接", true, false, true) {}
            }
        }
        compose.onNodeWithText("桌面端").assertDoesNotExist()
        compose.onAllNodesWithText("在线", useUnmergedTree = true).assertCountEquals(1)
    }
}

package com.litter.android

import android.view.View
import android.net.Uri
import kotlinx.coroutines.runBlocking
import uniffi.codex_mobile_client.DesktopConnectBridge
import uniffi.codex_mobile_client.ClientException
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.litter.android.ui.desktop.DesktopAttachmentPreview
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.litter.android.ui.LitterAppTheme
import com.litter.android.ui.desktop.DesktopMessageKind
import com.litter.android.ui.desktop.parseDesktopMessages
import com.litter.android.ui.desktop.parseDesktopAttachment
import org.json.JSONObject
import uniffi.codex_mobile_client.projectDesktopMessages
import uniffi.codex_mobile_client.resolveDesktopArtifact
import com.litter.android.ui.desktop.DesktopAttachmentPreviewDialog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesktopArtifactPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun artifactRequestPassesPackagedNativeRouteGuardBeforeConnectionCheck() = runBlocking {
        DesktopConnectBridge("https://uc.invalid/", "https://connect.invalid/", object : uniffi.codex_mobile_client.NeSessionStore {
            override fun readSession(): String? = null
            override fun replaceSession(expected: String?, value: String?): Boolean = error("Preview test must not write login state")
        }).use { bridge ->
            val path = "/file/content?directory=" + Uri.encode("D:/repo") + "&path=" + Uri.encode("D:/repo/格调小区-百花山-路线图.html")
            val result = runCatching { bridge.requestDesktopFor("host", "GET", path, "null") }.exceptionOrNull()
            assertTrue("合法产物请求应通过路由检查，进入连接检查：$result", result is ClientException.Rpc)
            assertTrue(result?.message?.contains("尚未连接电脑") == true)
            for ((method, route) in listOf("POST" to path, "GET" to "/file/content?credential=secret")) {
                val rejected = runCatching { bridge.requestDesktopFor("host", method, route, "null") }.exceptionOrNull()
                assertTrue("非法产物请求应被打包的原生库拒绝：$rejected", rejected is ClientException.InvalidParams)
            }
        }
    }

    @Test fun registeredHtmlLoadsOnDemandAndRendersWithoutScriptOrFilePrivileges() {
        val html = "<html><head><title>路线预览</title></head><body><h1>百花山路线</h1>" +
            "<script>document.title='脚本不应执行'</script></body></html>"
        val projected = projectDesktopMessages("""[{"info":{"id":"a1","role":"assistant"},"parts":[
            {"id":"art","type":"tool","tool":"artifact","state":{"status":"completed","metadata":{"artifacts":[
                {"path":"D:/test/路线.html","name":"路线.html","mime":"text/html","size":99}
            ]}}}
        ]}]""")
        val attachment = checkNotNull(parseDesktopMessages(projected).single { it.kind == DesktopMessageKind.FILE }.attachment)
        var loads = 0
        compose.setContent {
            LitterAppTheme {
                DesktopAttachmentPreviewDialog(attachment, onDismiss = {}, loadArtifact = {
                    loads++
                    parseDesktopAttachment(JSONObject(resolveDesktopArtifact(
                        JSONObject().put("filename", it.filename).put("mime", it.mime).put("size", it.size).toString(),
                        JSONObject().put("type", "text").put("content", html).toString(),
                    )))
                })
            }
        }
        var rendered = false
        compose.waitUntil(10_000) {
            compose.runOnIdle {
                rendered = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findWebView)?.title == "路线预览"
            }
            rendered
        }
        compose.runOnIdle {
            assertTrue(loads == 1)
            val web = checkNotNull(WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::findWebView))
            assertFalse(web.settings.javaScriptEnabled)
            assertFalse(web.settings.allowFileAccess)
            assertFalse(web.settings.allowContentAccess)
            assertTrue(web.settings.blockNetworkLoads)
        }
    }

    @Test fun openButtonStaysAboveNavigationBarWithContentSpacing() {
        compose.setContent {
            LitterAppTheme {
                DesktopAttachmentPreviewDialog(
                    DesktopAttachmentPreview("路线.txt", "text/plain", 3, "YWJj"),
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        val button = compose.onNodeWithText("用其他应用打开").fetchSemanticsNode().boundsInWindow
        compose.runOnIdle {
            val windows = WindowInspector.getGlobalWindowViews()
            val decor = windows.last()
            // Dialog's decor may expose consumed insets; the activity retains the actual system bar bounds.
            val insets = checkNotNull(ViewCompat.getRootWindowInsets(windows.first()))
                .getInsets(WindowInsetsCompat.Type.navigationBars())
            val spacing = 24 * decor.resources.displayMetrics.density
            assertTrue("模拟器应提供真实导航栏 inset", insets.bottom > 0)
            assertTrue(
                "按钮 bottom=${button.bottom}, window=${decor.height}, navigation=${insets.bottom}, spacing=$spacing",
                button.bottom <= decor.height - insets.bottom - spacing + 1,
            )
        }
    }
    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        return (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
    }
}

package com.litter.android.ui.discovery

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import org.json.JSONObject
import uniffi.codex_mobile_client.NeCaptchaConfig
import uniffi.codex_mobile_client.neCaptchaHtml
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NeCaptchaDialog(config: NeCaptchaConfig, baseUrl: String, onResult: (String) -> Unit,
                    onCancel: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val closed = remember { AtomicBoolean(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    fun finish(action: () -> Unit) {
        handler.post { if (closed.compareAndSet(false, true)) action() }
    }
    val web = remember(config, baseUrl) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.domStorageEnabled = false
            addJavascriptInterface(object {
                @JavascriptInterface fun postMessage(raw: String) {
                    if (raw.length > 131072) { finish { onError("验证结果无效，请重试") }; return }
                    try {
                        val value = JSONObject(raw)
                        when (value.optString("type")) {
                            "success" -> {
                                val proof = value.optString("value")
                                if (proof.isBlank() || proof.length >= 65536) finish { onError("验证结果无效，请重试") }
                                else finish { onResult(proof) }
                            }
                            "cancel" -> finish(onCancel)
                            else -> finish { onError("人机验证未完成，请检查网络后重试") }
                        }
                    } catch (e: org.json.JSONException) { finish { onError("验证结果无效，请重试") } }
                }
            }, "NeCaptcha")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame || (request.url.scheme != "https" && request.url.toString() != "about:blank")
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) finish { onError("无法加载人机验证，请检查网络后重试") }
                }
            }
            loadDataWithBaseURL(baseUrl, neCaptchaHtml(config), "text/html", "UTF-8", null)
        }
    }
    DisposableEffect(web) {
        onDispose { closed.set(true); web.removeJavascriptInterface("NeCaptcha"); web.stopLoading(); web.destroy() }
    }
    Dialog(onDismissRequest = { finish(onCancel) }) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
            Column {
                AndroidView(factory = { web }, modifier = Modifier.fillMaxWidth().height(460.dp))
                TextButton(onClick = { finish(onCancel) }, modifier = Modifier.fillMaxWidth()) { Text("取消验证") }
            }
        }
    }
}

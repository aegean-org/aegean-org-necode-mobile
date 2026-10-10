package com.litter.android.ui.desktop

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.litter.android.ui.LitterTheme
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Thin presentation of Rust's attachment projection; no upstream file URL is resolved here.
internal data class DesktopAttachmentPreview(
    val filename: String,
    val mime: String,
    val size: Long?,
    val dataBase64: String?,
    val unavailableReason: String? = null,
    val path: String? = null,
) {
    val isHtml: Boolean get() = mime == "text/html" || filename.substringAfterLast('.', "").lowercase(Locale.ROOT) in listOf("html", "htm")
    val isImage: Boolean get() = mime.startsWith("image/")
    val isText: Boolean get() = mime.startsWith("text/") || mime in listOf("application/json", "application/xml") ||
        filename.substringAfterLast('.', "").lowercase() in listOf("txt", "md", "csv", "json", "xml", "yaml", "yml", "log", "kt", "swift", "rs", "ts", "tsx", "js", "py", "css", "html")
    val format: String get() = filename.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)?.take(12)
        ?: if (isImage) "图片" else if (isText) "文本" else "文件"
    val description: String get() = listOfNotNull(format, size?.let {
        when {
            it < 1_000 -> "$it B"
            it < 1_000_000 -> String.format(Locale.CHINA, "%.1f KB", it / 1_000.0)
            else -> String.format(Locale.CHINA, "%.1f MB", it / 1_000_000.0)
        }
    }).joinToString(" · ")
}

internal data class DesktopAttachmentTextPreview(val text: String, val truncated: Boolean)

internal fun desktopAttachmentPreviewText(bytes: ByteArray): DesktopAttachmentTextPreview {
    val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
    check(!text.contains('\u0000')) { "文件不是可预览的 UTF-8 文本，请用其他应用打开" }
    val preview = text.take(64_000)
    return DesktopAttachmentTextPreview(if (preview.lastOrNull()?.isHighSurrogate() == true) preview.dropLast(1) else preview,
        text.length > 64_000)
}

private fun decodeDesktopPreview(attachment: DesktopAttachmentPreview): ByteArray {
    check(attachment.unavailableReason == null) { attachment.unavailableReason.orEmpty() }
    val encoded = checkNotNull(attachment.dataBase64) { "附件内容未随消息同步，暂时无法在手机预览" }
    check(encoded.length <= 5_333_336) { "附件过大，暂不支持在手机预览" }
    return Base64.decode(encoded, Base64.NO_WRAP).also {
        check(it.size <= 4_000_000) { "附件过大，暂不支持在手机预览" }
    }
}

@Composable
internal fun DesktopAttachmentCard(
    attachment: DesktopAttachmentPreview,
    onPreview: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val image by produceState<Result<ByteArray>?>(null, attachment.dataBase64, attachment.mime) {
        value = if (attachment.isImage && attachment.dataBase64 != null) withContext(Dispatchers.IO) {
            runCatching { decodeDesktopPreview(attachment) }
        } else null
    }
    var imageFailed by remember(attachment.dataBase64) { mutableStateOf(false) }
    Row(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(12.dp))
        .background(LitterTheme.surfaceLight).border(1.dp, LitterTheme.textSecondary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f, fill = false).clickable(onClickLabel = "预览附件", onClick = onPreview).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(LitterTheme.accentStrong.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                val bytes = image?.getOrNull()
                if (bytes != null && !imageFailed) AsyncImage(
                    model = ImageRequest.Builder(context).data(bytes).crossfade(false).build(),
                    contentDescription = attachment.filename, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                ) else Icon(if (attachment.isImage) Icons.Default.Image else Icons.Default.Description,
                    contentDescription = null, tint = LitterTheme.accentStrong, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.widthIn(max = if (onRemove == null) 200.dp else 152.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(attachment.filename, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, color = LitterTheme.textPrimary)
                Text(if (imageFailed || image?.isFailure == true) "图片无法预览" else attachment.description,
                    maxLines = 1, fontSize = 11.sp, color = LitterTheme.textSecondary)
            }
        }
        if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, contentDescription = "移除 ${attachment.filename}", tint = LitterTheme.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun DesktopAttachmentPreviewDialog(
    attachment: DesktopAttachmentPreview, onDismiss: () -> Unit,
    loadArtifact: suspend (DesktopAttachmentPreview) -> DesktopAttachmentPreview = { error("请从文件所属会话打开预览") },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val content by produceState<Result<ByteArray>?>(null, attachment) {
        value = try {
            check(attachment.unavailableReason == null) { attachment.unavailableReason.orEmpty() }
            val loaded = if (attachment.path != null) loadArtifact(attachment) else attachment
            Result.success(withContext(Dispatchers.IO) { decodeDesktopPreview(loaded) })
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }
    val text by produceState<Result<DesktopAttachmentTextPreview>?>(null, content, attachment.isText) {
        value = if (attachment.isText && content?.isSuccess == true) withContext(Dispatchers.IO) {
            runCatching { desktopAttachmentPreviewText(content!!.getOrThrow()) }
        } else null
    }
    var error by remember(attachment) { mutableStateOf<String?>(null) }
    var opening by remember(attachment) { mutableStateOf(false) }
    // Read the host insets before entering the Dialog, whose decor can expose consumed insets.
    val safeArea = WindowInsets.safeDrawing.asPaddingValues()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(LitterTheme.surface).padding(safeArea).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(attachment.filename, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = LitterTheme.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(attachment.description, fontSize = 12.sp, color = LitterTheme.textSecondary)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭预览", tint = LitterTheme.textSecondary) }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                val bytes = content?.getOrNull()
                when {
                    content == null -> CircularProgressIndicator(color = LitterTheme.accentStrong)
                    content?.isFailure == true -> Text(content?.exceptionOrNull()?.let { if (it is Exception) desktopConnectErrorMessage(it) else "文件预览失败" } ?: "附件内容无法读取，请重试",
                        color = LitterTheme.textSecondary)
                    bytes != null && attachment.isHtml -> Column(Modifier.fillMaxSize()) {
                        Text("安全预览 · 不执行脚本，不加载外部或电脑本地资源", color = LitterTheme.textSecondary,
                            fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                        val html = remember(bytes) { bytes.toString(Charsets.UTF_8) }
                        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.blockNetworkLoads = true
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                                }
                                loadDataWithBaseURL("https://preview.invalid/", html, "text/html", "UTF-8", null)
                            }
                        }, onRelease = { it.stopLoading(); it.destroy() })
                    }
                    bytes != null && attachment.isImage -> {
                        var scale by remember(attachment) { mutableStateOf(1f) }
                        var offset by remember(attachment) { mutableStateOf(Offset.Zero) }
                        var viewport by remember { mutableStateOf(IntSize.Zero) }
                        val transform = rememberTransformableState { zoom, pan, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val next = offset + pan
                            offset = Offset(next.x.coerceIn(-viewport.width * (scale - 1) / 2, viewport.width * (scale - 1) / 2),
                                next.y.coerceIn(-viewport.height * (scale - 1) / 2, viewport.height * (scale - 1) / 2))
                        }
                        AsyncImage(model = ImageRequest.Builder(context).data(bytes).crossfade(false).build(),
                            contentDescription = attachment.filename, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it }
                                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                                .transformable(transform).pointerInput(attachment) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) },
                            onError = { error = "图片无法解码，请用其他应用打开" })
                    }
                    attachment.isText && text == null -> CircularProgressIndicator(color = LitterTheme.accentStrong)
                    attachment.isText && text?.isSuccess == true -> SelectionContainer {
                        Text(text!!.getOrThrow().text.ifEmpty { "（空文件）" }, color = LitterTheme.textPrimary, fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState()).padding(8.dp))
                    }
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Description, null, tint = LitterTheme.accentStrong, modifier = Modifier.size(48.dp))
                        Text(if (text?.isFailure == true) "无法按 UTF-8 文本预览，请用其他应用打开" else "此格式请用其他应用打开预览",
                            color = LitterTheme.textSecondary, fontSize = 14.sp)
                    }
                }
            }
            if (attachment.isImage) Text("双指缩放 · 双击复位", fontSize = 12.sp, color = LitterTheme.textSecondary, modifier = Modifier.padding(top = 12.dp))
            if (text?.getOrNull()?.truncated == true)
                Text("仅预览前 64,000 个字符，完整内容请用其他应用打开", fontSize = 12.sp, color = LitterTheme.textSecondary)
            if (error != null) Text(error!!, color = LitterTheme.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            TextButton(enabled = content?.isSuccess == true && !opening, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
                opening = true; error = null
                scope.launch {
                    try {
                        val uri = withContext(Dispatchers.IO) { desktopPreviewFile(context, attachment.filename, content!!.getOrThrow()) }
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mime)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    } catch (e: CancellationException) { throw e }
                    catch (_: ActivityNotFoundException) { error = "手机没有可打开此文件的应用，请安装对应应用后重试" }
                    catch (e: Exception) { error = "无法打开附件：${e.localizedMessage ?: "请稍后重试"}" }
                    finally { opening = false }
                }
            }) { Text(if (opening) "正在打开…" else "用其他应用打开", color = LitterTheme.accentStrong) }
        }
    }
}

private fun desktopPreviewFile(context: Context, filename: String, bytes: ByteArray): android.net.Uri {
    val directory = File(context.cacheDir, "desktop-attachment-preview")
    check(directory.isDirectory || directory.mkdirs()) { "无法创建附件预览缓存" }
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).take(12).joinToString("") { "%02x".format(it) }
    val name = filename.substringAfterLast('/').substringAfterLast('\\').replace(Regex("""[^\p{L}\p{N}._ -]"""), "_").takeLast(120).ifBlank { "附件" }
    val file = File(directory, "$hash-$name")
    check(file.canonicalFile.parentFile == directory.canonicalFile) { "附件缓存路径无效" }
    file.writeBytes(bytes)
    return FileProvider.getUriForFile(context, "${context.packageName}.attachment-files", file)
}

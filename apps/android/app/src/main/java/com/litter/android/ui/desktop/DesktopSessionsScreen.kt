package com.litter.android.ui.desktop

import com.litter.android.ui.common.PaginatedLazyColumn
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.litter.android.ui.common.LitterDropdownMenu
import com.litter.android.ui.common.LitterDropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.litter.android.ui.LitterTheme
import com.litter.android.state.DesktopConnectCredentialStore
import com.litter.android.state.VoiceTranscriptionManager
import com.litter.android.util.LLog
import com.litter.android.ui.common.FormattedText
import com.litter.android.ui.conversation.SelectableMarkdownText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import uniffi.codex_mobile_client.DesktopConnectBridge
import uniffi.codex_mobile_client.DesktopHost
import uniffi.codex_mobile_client.selectDesktopModel
import uniffi.codex_mobile_client.prepareDesktopPrompt
import uniffi.codex_mobile_client.restoreDesktopPrompt
import uniffi.codex_mobile_client.parseDesktopPromptReceipt
import uniffi.codex_mobile_client.desktopPromptMatchesDraft
import uniffi.codex_mobile_client.DesktopPromptState
import uniffi.codex_mobile_client.DesktopPromptAttempt
import uniffi.codex_mobile_client.mergeDesktopMessagePage
import uniffi.codex_mobile_client.desktopPromptSettled
import uniffi.codex_mobile_client.resolveDesktopArtifact
import uniffi.codex_mobile_client.projectDesktopMessages
import uniffi.codex_mobile_client.desktopMessageGapCursor
import uniffi.codex_mobile_client.desktopPromptRejectionConfirmed
import uniffi.codex_mobile_client.desktopSessionAttention

@Composable
internal fun DesktopCenterLoading(label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = LitterTheme.accentStrong,
            strokeWidth = 2.dp)
        Text(label, color = LitterTheme.textSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun DesktopReplyWaiting() {
    val transition = rememberInfiniteTransition(label = "reply waiting")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "reply progress",
    )
    Text("思考中…", modifier = Modifier.padding(vertical = 8.dp),
        style = TextStyle(fontSize = 13.sp, brush = Brush.linearGradient(
            colors = listOf(LitterTheme.textSecondary, LitterTheme.accentStrong, LitterTheme.textSecondary),
            start = Offset(progress * 360f - 180f, 0f), end = Offset(progress * 360f, 0f))))
}

internal data class DesktopProject(val id: String, val name: String, val directory: String) {
    val key: String get() = "$id|$directory"
    val displayName: String get() = name.ifBlank { directory }.trimEnd('/', '\\')
        .substringAfterLast('/').substringAfterLast('\\').ifBlank { directory }
}
internal fun desktopProjectsByRecentOpen(projects: List<DesktopProject>, opened: (DesktopProject) -> Long) =
    projects.sortedByDescending(opened)

private val desktopDefaultSessionTitle = Regex("""(?:New|Child) session - \d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")
internal data class DesktopSession(val id: String, val title: String, val project: DesktopProject, val updated: Long,
    val model: DesktopModel? = null) {
    val displayTitle: String get() = if (desktopDefaultSessionTitle.matches(title)) {
        if (title.startsWith("Child session")) "子会话" else "新会话"
    } else title
}
private class DesktopHttpException(val status: Int, body: String) : IllegalStateException("桌面返回 $status: $body")

internal data class DesktopModel(val providerID: String, val modelID: String, val name: String,
    val image: Boolean = false, val providerName: String = providerID)

internal fun desktopSessionTime(updated: Long, now: Long): String {
    val elapsed = (now - updated).coerceAtLeast(0L)
    return when {
        elapsed < 60_000 -> "刚刚"
        elapsed < 3_600_000 -> "${elapsed / 60_000} 分钟前"
        elapsed < 86_400_000 -> "${elapsed / 3_600_000} 小时前"
        elapsed < 172_800_000 -> "昨天"
        elapsed < 604_800_000 -> "${elapsed / 86_400_000} 天前"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(updated))
    }
}
internal enum class DesktopMessageKind { TEXT, TOOL, FILE }
internal data class DesktopMessage(
    val role: String, val text: String,
    val kind: DesktopMessageKind = DesktopMessageKind.TEXT, val status: String? = null, val details: List<String> = emptyList(),
    val id: String = "",
    val attachment: DesktopAttachmentPreview? = null,
)

internal fun parseDesktopMessages(raw: String): List<DesktopMessage> {
    val rows = JSONArray(raw)
    return (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        val details = row.getJSONArray("details")
        DesktopMessage(row.getString("role"), row.getString("text"),
            when (row.getString("kind")) { "text" -> DesktopMessageKind.TEXT; "tool" -> DesktopMessageKind.TOOL; "file" -> DesktopMessageKind.FILE; else -> error("无效消息类型") },
            row.optString("status").takeUnless { it == "null" || it.isBlank() },
            (0 until details.length()).map(details::getString), row.getString("id"),
            row.optJSONObject("attachment")?.let { attachment ->
                parseDesktopAttachment(attachment)
            })
    }
}

internal fun parseDesktopAttachment(attachment: JSONObject) = DesktopAttachmentPreview(
    attachment.getString("filename"), attachment.getString("mime"),
    attachment.optLong("size").takeUnless { attachment.isNull("size") },
    attachment.optString("data_base64").takeUnless { attachment.isNull("data_base64") },
    attachment.optString("unavailable_reason").takeUnless { attachment.isNull("unavailable_reason") },
    attachment.optString("path").takeUnless { attachment.isNull("path") || it.isBlank() },
)

internal fun shouldFollowDesktopMessageUpdate(
    previous: List<DesktopMessage>, updated: List<DesktopMessage>, nearBottom: Boolean, force: Boolean,
    pendingFollow: Boolean = false,
) = force || nearBottom && (pendingFollow || previous != updated) || (previous.isEmpty() && previous != updated)

internal fun desktopComposerShowsStop(submitting: Boolean, awaiting: Boolean, processing: Boolean, stopping: Boolean) =
    submitting || awaiting || processing || stopping

internal data class DesktopLocalPrompt(
    val messageId: String, val text: String, val filenames: List<String> = emptyList(),
    val status: String = "正在发送…",
)

internal fun desktopTimelineMessages(messages: List<DesktopMessage>, local: DesktopLocalPrompt?): List<DesktopMessage> {
    if (local == null || messages.any { it.id.startsWith(local.messageId + ":") }) return messages
    return messages + DesktopMessage("user", (local.filenames.map { "附件：$it" } +
        listOf(local.text).filter { it.isNotBlank() }).joinToString("\n"), status = local.status, id = "local:" + local.messageId)
}

internal fun shouldShowDesktopReplyWaiting(messages: List<DesktopMessage>, running: Boolean, hasError: Boolean, pendingCount: Int): Boolean {
    if (!running || messages.isEmpty() || hasError || pendingCount != 0) return false
    val latestUser = messages.indexOfLast { it.role == "user" }
    return messages.drop(latestUser + 1).none {
        it.role == "assistant" && it.kind == DesktopMessageKind.TEXT && it.text.isNotBlank()
    }
}

internal fun desktopNearMessageBottom(lastIndex: Int, itemCount: Int, lastBottom: Int, viewportEnd: Int, threshold: Int) =
    itemCount == 0 || lastIndex == itemCount - 1 && lastBottom <= viewportEnd + threshold

internal enum class DesktopHostAction { CURRENT, RECONNECT, CONNECT, UNAVAILABLE }

internal fun desktopHostAction(activeId: String?, hostId: String, status: String, healthy: Boolean = true) = when {
    activeId == hostId && !healthy -> DesktopHostAction.RECONNECT
    activeId == hostId -> DesktopHostAction.CURRENT
    status == "available" -> DesktopHostAction.CONNECT
    else -> DesktopHostAction.UNAVAILABLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopSessionsScreen(
    bridge: DesktopConnectBridge,
    hostName: String,
    onDisconnected: () -> Unit,
    onHostChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val store = remember(context) { DesktopConnectCredentialStore(context.applicationContext) }
    val recency = remember(context) { context.applicationContext.getSharedPreferences("desktop_project_recency", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<DesktopProject>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<DesktopSession>>(emptyList()) }
    var displayedSessionScope by remember { mutableStateOf<Triple<String, String?, String>?>(null) }
    var refreshingSessions by remember { mutableStateOf(false) }
    var nextSessionCursor by remember { mutableStateOf<String?>(null) }
    var loadingMoreSessions by remember { mutableStateOf(false) }
    var moreSessionsError by remember { mutableStateOf<String?>(null) }
    var browserGeneration by remember { mutableStateOf(0L) }
    var projectsLoaded by remember { mutableStateOf(false) }
    var projectsLoading by remember { mutableStateOf(false) }
    var projectGeneration by remember { mutableStateOf(0L) }
    var browserHost by remember { mutableStateOf<String?>(null) }
    var selectedProject by remember { mutableStateOf<DesktopProject?>(null) }
    var showProjects by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<DesktopSession?>(null) }
    var activeHostId by remember { mutableStateOf(store.preferredDesktopId()) }
    fun recentKey(project: DesktopProject): String? {
        val account = store.load()?.accountId ?: return null
        val host = activeHostId ?: return null
        return JSONArray(listOf(account, host, project.key)).toString()
    }
    fun sortRecent(rows: List<DesktopProject>) = desktopProjectsByRecentOpen(rows) {
        recentKey(it)?.let { key -> recency.getLong(key, 0L) } ?: 0L
    }
    fun openedProject(project: DesktopProject) {
        val key = recentKey(project) ?: return
        recency.edit().putLong(key, System.currentTimeMillis()).apply()
        projects = sortRecent(projects)
    }
    var showSessionActions by remember(activeHostId, selectedSession?.id) { mutableStateOf(false) }
    val composerDrafts = remember { mutableMapOf<Pair<String?, String?>, DesktopComposerDraft>() }
    val composer = composerDrafts.getOrPut(activeHostId to selectedSession?.id) { DesktopComposerDraft() }
    var recentSearch by remember { mutableStateOf("") }
    var projectSearch by remember { mutableStateOf("") }
    var projectSessionSearch by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<DesktopMessage>>(emptyList()) }
    var rawMessages by remember { mutableStateOf("[]") }
    var liveSnapshot by remember(activeHostId, selectedSession?.id) { mutableStateOf<String?>(null) }
    var messageScope by remember { mutableStateOf("") }
    var nextMessageCursor by remember { mutableStateOf<String?>(null) }
    var catchupPage by remember { mutableStateOf<String?>(null) }
    var fetchingMessages by remember { mutableStateOf(false) }
    var loadingOlderMessages by remember { mutableStateOf(false) }
    var attention by remember { mutableStateOf(JSONObject("{\"status\":\"idle\",\"label\":\"\",\"permissions\":[],\"questions\":[]}")) }
    var showAttention by remember { mutableStateOf(false) }
    var stoppingScope by remember { mutableStateOf<String?>(null) }
    var modelOptions by composer.modelOptions
    val modelsLoading by composer.modelsLoading
    var selectedModel by composer.model
    var showModels by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }
    var modelError by composer.modelError
    val messageListState = rememberLazyListState()
    var scrollAfterMessageUpdate by remember { mutableStateOf(true) }
    var followMessageTail by remember(activeHostId, selectedSession?.id) { mutableStateOf(true) }
    val messageDragging by messageListState.interactionSource.collectIsDraggedAsState()
    var messageDragStarted by remember(activeHostId, selectedSession?.id) { mutableStateOf(false) }
    val manualMessageScroll = remember(activeHostId, selectedSession?.id) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    followMessageTail = false
                    scrollAfterMessageUpdate = false
                }
                return Offset.Zero
            }
        }
    }
    val timelineMessages = desktopTimelineMessages(messages, composer.localPrompt.value)
    var error by remember { mutableStateOf<String?>(null) }
    var prompt by composer.text
    var attachments by composer.attachments
    var attachmentPreview by remember(activeHostId, selectedSession?.id) { mutableStateOf<DesktopAttachmentPreview?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var attachmentOwner by remember { mutableStateOf<DesktopComposerDraft?>(null) }
    var recordingOwner by remember { mutableStateOf<DesktopComposerDraft?>(null) }
    fun addAttachment(uri: Uri) {
        val target = attachmentOwner ?: return
        scope.launch {
            try {
                val picked = withContext(Dispatchers.IO) { readDesktopAttachment(context, uri) }
                check(target.attachments.value.sumOf { it.size } + picked.size <= 2_500_000) {
                    "附件总大小不能超过 2.5 MB"
                }
                target.attachments.value = target.attachments.value + picked
                error = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.localizedMessage ?: "无法读取附件" }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::addAttachment)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::addAttachment)
    }
    val transcriptionManager = remember { VoiceTranscriptionManager() }
    val recording by transcriptionManager.isRecording.collectAsState()
    val transcribing by transcriptionManager.isTranscribing.collectAsState()
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recordingOwner != null && recordingOwner === composer) transcriptionManager.startRecording(context)
        else error = "需要麦克风权限"
    }
    DisposableEffect(transcriptionManager) {
        onDispose { if (transcriptionManager.isRecording.value) transcriptionManager.cancelRecording() }
    }

    var showDirectoryPicker by remember(activeHostId) { mutableStateOf(false) }
    var showHostPicker by remember { mutableStateOf(false) }
    val actionOwners = remember { mutableStateMapOf<String, Boolean>() }
    val creatingProjects = remember { mutableStateMapOf<String, String>() }
    fun actionKey() = "$activeHostId|${selectedSession?.id}|${selectedProject?.key}|$showProjects|$showHostPicker"
    val busy = actionOwners.containsKey(actionKey())
    var hasPendingPrompt by composer.pending
    var awaitingResponse by composer.awaiting
    var awaitedPromptId by composer.promptId
    var healthy by remember { mutableStateOf(true) }
    var reconnecting by remember { mutableStateOf(false) }
    var connectionFailed by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) foreground = true
            if (event == Lifecycle.Event.ON_PAUSE) foreground = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var initialLoadComplete by remember { mutableStateOf(false) }

    var connectingHostId by remember { mutableStateOf<String?>(null) }
    var hosts by remember { mutableStateOf<List<DesktopHost>>(emptyList()) }
    var hostsLoaded by remember { mutableStateOf(false) }

    suspend fun request(method: String, path: String, body: String = "null", expectedHost: String? = activeHostId): String {
        val response = try { withContext(Dispatchers.IO) { bridge.requestDesktopFor(checkNotNull(expectedHost) { "尚未连接电脑" }, method, path, body) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (expectedHost == activeHostId && e is uniffi.codex_mobile_client.ClientException.Transport) connectionFailed = true
                throw e
            }
        if (response.status.toInt() !in 200..299) throw DesktopHttpException(response.status.toInt(), response.bodyJson)
        return response.bodyJson
    }

    fun directory(project: DesktopProject): String = "?directory=${Uri.encode(project.directory)}"
    attachmentPreview?.let { preview ->
        val host = activeHostId
        val project = selectedSession?.project
        DesktopAttachmentPreviewDialog(preview, onDismiss = { attachmentPreview = null }, loadArtifact = { file ->
            val path = checkNotNull(file.path) { "文件路径无效" }
            val owner = checkNotNull(project) { "请先打开文件所属的会话" }
            val content = request("GET", "/file/content" + directory(owner) + "&path=" + Uri.encode(path), expectedHost = host)
            withContext(Dispatchers.IO) {
                parseDesktopAttachment(JSONObject(resolveDesktopArtifact(
                    JSONObject().put("filename", file.filename).put("mime", file.mime).put("size", file.size).toString(), content)))
            }
        })
    }


    suspend fun refreshSessions(append: Boolean = false) {
        if (append && (loadingMoreSessions || refreshingSessions || nextSessionCursor == null)) return
        moreSessionsError = null
        val project = selectedProject
        val projectId = project?.id
        val projectKey = project?.key
        val search = (if (projectId != null) projectSessionSearch else recentSearch).trim()
        val host = activeHostId.orEmpty()
        val expectedHost = activeHostId
        val scope = Triple(host, projectKey, search)
        if (!append) {
            browserGeneration++
            nextSessionCursor = null
            if (displayedSessionScope != scope) { sessions = emptyList(); initialLoadComplete = false }
            refreshingSessions = true
            loadingMoreSessions = false
        } else loadingMoreSessions = true
        val generation = browserGeneration
        val cursor = if (append) nextSessionCursor else null
        val started = SystemClock.elapsedRealtime()
        val query = buildList {
            projectId?.let { add("project=" + Uri.encode(it)) }
            project?.let { add("directory=" + Uri.encode(it.directory)) }
            if (search.isNotBlank()) add("search=" + Uri.encode(search))
            cursor?.let { add("cursor=" + Uri.encode(it)) }
        }.joinToString("&")
        try {
            val page = JSONObject(request("GET", "/connect/sessions" + if (query.isEmpty()) "" else "?$query", expectedHost = expectedHost))
            val rows = page.getJSONArray("data")
            val result = (0 until rows.length()).map { index ->
                val item = rows.getJSONObject(index)
                val metadata = item.optJSONObject("project")
                val project = DesktopProject(item.getString("projectID"),
                    metadata?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: item.getString("directory"), item.getString("directory"))
                DesktopSession(item.getString("id"), item.optString("title").ifBlank { "未命名会话" },
                    project, item.getJSONObject("time").getLong("updated"),
                    item.optJSONObject("model")?.let { model -> DesktopModel(
                        model.getString("providerID"), model.getString("id"), model.getString("id")) })
            }
            if (expectedHost != activeHostId || generation != browserGeneration || expectedHost != browserHost || showProjects && selectedProject == null ||
                projectKey != selectedProject?.key || search != (if (selectedProject != null) projectSessionSearch else recentSearch).trim()) return
            sessions = if (append) (sessions + result).distinctBy { it.id } else result
            displayedSessionScope = scope
            nextSessionCursor = page.optString("nextCursor").takeUnless { it.isBlank() || it == "null" }
            LLog.i("DesktopConnect", "recent session page loaded", mapOf("elapsedMs" to
                SystemClock.elapsedRealtime() - started, "count" to result.size, "append" to append, "hasMore" to (nextSessionCursor != null)))
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (generation == browserGeneration) throw e }
        finally {
            if (generation == browserGeneration) {
                initialLoadComplete = true
                refreshingSessions = false
                loadingMoreSessions = false
            }
        }
    }

    suspend fun loadProjects(force: Boolean = false) {
        if (projectsLoading || projectsLoaded && !force) return
        projectsLoading = true
        val generation = ++projectGeneration
        val host = activeHostId
        val started = SystemClock.elapsedRealtime()
        try {
            val rows = withContext(Dispatchers.IO) { bridge.desktopProjects(host ?: error("未连接电脑")) }
            if (generation != projectGeneration || activeHostId != host) return
            projects = sortRecent(rows.filter { it.directory != "/" && it.directory.isNotBlank() }.map {
                DesktopProject(it.id, it.name, it.directory)
            })
            projectsLoaded = true
            LLog.i("DesktopConnect", "project list loaded", mapOf("elapsedMs" to
                SystemClock.elapsedRealtime() - started, "count" to projects.size))
        } finally { if (generation == projectGeneration) projectsLoading = false }
    }

    suspend fun loadModels(project: DesktopProject) {
        val session = selectedSession ?: return
        val owner = activeHostId
        val target = composerDrafts.getOrPut(owner to session.id) { DesktopComposerDraft() }
        if (target.modelsLoading.value) return
        target.modelsLoading.value = true
        target.modelError.value = null
        try {
            val catalog = request("GET", "/connect/models${directory(project)}", expectedHost = owner)
            check(selectedSession?.id == session.id && activeHostId == owner) { "电脑或会话已切换" }
            val current = JSONObject(request("GET", "/session/${session.id}${directory(project)}", expectedHost = owner)).optJSONObject("model")?.toString()
            // Read the manual selection after IO so refreshing cannot undo a choice made while loading.
            val picked = target.model.value?.let { JSONObject().put("providerID", it.providerID).put("modelID", it.modelID).toString() }
            val choice = withContext(Dispatchers.IO) { selectDesktopModel(catalog, current, picked) }
            if (selectedSession?.id != session.id || activeHostId != owner) return
            fun option(model: uniffi.codex_mobile_client.DesktopModelOption) = DesktopModel(
                model.providerId, model.modelId, model.name, model.image, model.providerName)
            target.modelOptions.value = choice.options.map(::option)
            target.model.value = choice.selected?.let(::option)
            target.modelError.value = choice.warning
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            target.modelError.value = "模型加载失败：${e.localizedMessage}"
            throw e
        } finally { target.modelsLoading.value = false }
    }

    suspend fun loadProjectsAndSessions() {
        if (showProjects && selectedProject == null) {
            if (!projectsLoaded) initialLoadComplete = false
            val generation = browserGeneration
            try { loadProjects(force = true) } finally {
                if (generation == browserGeneration && showProjects && selectedProject == null) initialLoadComplete = true
            }
            return
        }
        refreshSessions()
    }

    suspend fun acceptPrompt(session: DesktopSession, attempt: DesktopPromptAttempt, accountId: String, hostId: String) {
        val cleared = withContext(Dispatchers.IO) { store.clearPendingPrompt(accountId, hostId, session.id, attempt.journalJson) }
        if (!cleared) return
        val target = composerDrafts.getOrPut(hostId to session.id) { DesktopComposerDraft() }
        target.pending.value = false
        target.promptId.value = attempt.messageId
        target.awaiting.value = true
        target.stopRequested.value = false
        if (desktopPromptMatchesDraft(attempt.bodyJson, target.text.value, target.attachments.value.map { it.dataUrl })) {
            target.text.value = ""
            target.attachments.value = emptyList()
        }
        if (selectedSession?.id != session.id || activeHostId != hostId) return
        target.localPrompt.value = target.localPrompt.value?.copy(messageId = attempt.messageId, status = "已发送，正在同步…")
        error = null
    }

    val reconcilingPrompts = remember { mutableSetOf<Pair<String, String>>() }
    suspend fun reconcilePrompt(session: DesktopSession, retryMissing: Boolean = false) {
        if (actionOwners.containsKey(actionKey()) && !retryMissing) return
        val credentials = store.load() ?: return
        val hostId = activeHostId ?: return
        val owner = hostId to session.id
        if (!reconcilingPrompts.add(owner)) return
        try {
        val journal = withContext(Dispatchers.IO) { store.pendingPrompt(credentials.accountId, hostId, session.id) }
        if (selectedSession?.id != session.id || activeHostId != hostId) return
        composerDrafts.getOrPut(owner) { DesktopComposerDraft() }.pending.value = journal != null
        if (journal == null) return
        val attempt = restoreDesktopPrompt(credentials.accountId, hostId, session.id, journal)
        val receiptJSON = request("GET", "/connect/session/${session.id}/prompt/${attempt.messageId}${directory(session.project)}", expectedHost = hostId)
        val receipt = parseDesktopPromptReceipt(receiptJSON, session.id, attempt.messageId)
        if (selectedSession?.id != session.id || activeHostId != hostId) return
        when (receipt.state) {
            DesktopPromptState.ADMITTED, DesktopPromptState.PROMOTED -> acceptPrompt(session, attempt, credentials.accountId, hostId)
            DesktopPromptState.DISCARDED -> {
                val cleared = withContext(Dispatchers.IO) { store.clearPendingPrompt(credentials.accountId, hostId, session.id, attempt.journalJson) }
                if (cleared && selectedSession?.id == session.id && activeHostId == hostId) {
                    composerDrafts[owner]?.pending?.value = false
                    error = "桌面端已撤销该消息，未执行；草稿保留"
                }
            }
            DesktopPromptState.MISSING -> {
                if (retryMissing) {
                    check(receipt.idempotent) { "此桌面版本不支持精确重试，请先在电脑端核对，不能盲目重复发送" }
                    request("POST", "/session/${session.id}/prompt_async${directory(session.project)}", attempt.bodyJson, expectedHost = hostId)
                    acceptPrompt(session, attempt, credentials.accountId, hostId)
                } else error = "上一条发送尚未确认，可核对并重试原消息；不会生成重复消息 ID"
            }
        }
        } finally { reconcilingPrompts.remove(owner) }
    }

    suspend fun refreshAttention(session: DesktopSession) {
        val host = activeHostId
        val snapshot = JSONObject(request("GET", "/connect/session/${session.id}/attention" + directory(session.project), expectedHost = host))
        val state = JSONObject(desktopSessionAttention(snapshot.getJSONObject("statuses").toString(),
            snapshot.getJSONArray("permissions").toString(), snapshot.getJSONArray("questions").toString(), session.id))
        if (selectedSession?.id == session.id && activeHostId == host) {
            attention = state
            val target = composerDrafts[host to session.id]
            if (state.getString("status") == "idle" && target?.stopRequested?.value == true) {
                target.awaiting.value = false
                target.promptId.value = null
            }
        }
    }

    suspend fun refreshMessageProjection(session: DesktopSession, forceFollow: Boolean = false, older: Boolean = false) {
        val host = activeHostId
        val owner = "|$host|${session.id}|${session.project.directory}"
        while (selectedSession?.id == session.id && activeHostId == host && messageScope == owner) {
            val history = rawMessages
            val live = liveSnapshot
            val updated = withContext(Dispatchers.IO) {
                val input = if (live == null) history else JSONObject().put("messages", JSONArray(history))
                    .put("live", JSONObject(live)).toString()
                parseDesktopMessages(projectDesktopMessages(input))
            }
            if (selectedSession?.id != session.id || activeHostId != host || messageScope != owner) return
            if (history != rawMessages || live != liveSnapshot) continue
            if (!older) scrollAfterMessageUpdate = shouldFollowDesktopMessageUpdate(
                messages, updated, followMessageTail, forceFollow, scrollAfterMessageUpdate)
            messages = updated
            return
        }
    }

    suspend fun refreshMessages(session: DesktopSession, forceFollow: Boolean = false, older: Boolean = false) {
        if (selectedSession?.id != session.id) return
        val host = activeHostId
        val owner = "|" + activeHostId + "|" + session.id + "|" + session.project.directory
        if (messageScope != owner) {
            messageScope = owner
            rawMessages = "[]"
            attention = JSONObject("{\"status\":\"idle\",\"label\":\"\",\"permissions\":[],\"questions\":[]}")
            showAttention = false
            catchupPage = null
            nextMessageCursor = null
            fetchingMessages = false
            loadingOlderMessages = false
        }
        if (fetchingMessages || older && nextMessageCursor == null) return
        fetchingMessages = true
        loadingOlderMessages = older
        val before = if (older) "&before=" + Uri.encode(nextMessageCursor) else ""
        try {
            var page = if (!older) catchupPage else null
            if (page == null) page = request("GET", "/connect/session/${session.id}/message${directory(session.project)}&limit=30$before", expectedHost = host)
            if (!older) {
                var gap = desktopMessageGapCursor(rawMessages, checkNotNull(page))
                var pages = 0
                while (gap != null && pages++ < 4) {
                    val chunk = request("GET", "/connect/session/${session.id}/message${directory(session.project)}&limit=30&before=${Uri.encode(gap)}", expectedHost = host)
                    if (selectedSession?.id != session.id || activeHostId != host || messageScope != owner) return
                    val staged = mergeDesktopMessagePage(mergeDesktopMessagePage("[]", checkNotNull(page), false).messagesJson, chunk, true)
                    page = JSONObject().put("data", JSONArray(staged.messagesJson)).put("nextCursor", staged.nextCursor ?: JSONObject.NULL).toString()
                    val next = desktopMessageGapCursor(rawMessages, page)
                    check(next == null || next != gap) { "消息历史游标未前进，请重试" }
                    gap = next
                }
                if (selectedSession?.id != session.id || activeHostId != host || messageScope != owner) return
                if (gap != null) {
                    catchupPage = page
                    refreshAttention(session)
                    if (selectedSession?.id != session.id || activeHostId != host || messageScope != owner) return
                    reconcilePrompt(session)
                    return
                }
                catchupPage = null
            }
            if (selectedSession?.id != session.id || activeHostId != host || messageScope != owner) return
            val merged = withContext(Dispatchers.IO) { mergeDesktopMessagePage(rawMessages, checkNotNull(page), older) }
            if (selectedSession?.id != session.id || activeHostId != host || messageScope != owner) return
            if (rawMessages == "[]" || older) nextMessageCursor = merged.nextCursor
            rawMessages = merged.messagesJson
            refreshMessageProjection(session, forceFollow, older)
            if (selectedSession?.id != session.id || activeHostId != host || messageScope != owner) return
            val outgoing = composerDrafts[host to session.id]
            val local = outgoing?.localPrompt?.value
            if (outgoing != null && local != null && messages.any { it.id.startsWith(local.messageId + ":") }) outgoing.localPrompt.value = null
            refreshAttention(session)
            if (selectedSession?.id != session.id || activeHostId != host || messageScope != owner) return
            reconcilePrompt(session)
            if (selectedSession?.id != session.id || activeHostId != host || messageScope != owner) return
            val target = composerDrafts[host to session.id]
            if (target?.awaiting?.value == true && target.promptId.value?.let { desktopPromptSettled(rawMessages, it) } == true) {
                target.awaiting.value = false
                target.promptId.value = null
            }
        } finally {
            if (messageScope == owner) { fetchingMessages = false; loadingOlderMessages = false }
        }
    }

    fun act(creatingProject: DesktopProject? = null, block: suspend () -> Unit) {
        val owner = actionKey()
        if (actionOwners.containsKey(owner)) return
        actionOwners[owner] = true
        if (creatingProject != null) creatingProjects[owner] = creatingProject.key
        error = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (actionKey() == owner) error = desktopConnectErrorMessage(e) }
            finally {
                actionOwners.remove(owner)
                creatingProjects.remove(owner)
            }
        }
    }

    fun createSession(project: DesktopProject) = act(creatingProject = project) {
        val host = activeHostId
        val route = Triple(selectedSession?.id, selectedProject?.key, showProjects)
        val response = try { request("POST", "/session${directory(project)}", "{}", expectedHost = host) }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalStateException) { throw e }
            catch (e: Exception) {
                throw IllegalStateException("新建请求结果不明，请刷新会话列表确认，勿重复新建：${e.localizedMessage}", e)
            }
        if (activeHostId != host || route != Triple(selectedSession?.id, selectedProject?.key, showProjects)) return@act
        val created = JSONObject(response)
        val session = DesktopSession(created.getString("id"),
            created.optString("title").ifBlank { "新会话" }, project,
            created.optJSONObject("time")?.optLong("updated") ?: 0L)
        selectedSession = session
        openedProject(project)
        sessions = listOf(session) + sessions.filterNot { it.id == session.id }
        messages = emptyList()
        try { loadModels(project) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            composerDrafts[host to session.id]?.modelError?.value = "会话已创建，模型尚未准备好：${e.localizedMessage}"
        }
    }

    fun stopSession(session: DesktopSession) {
        val host = activeHostId ?: return
        val owner = "$host|${session.id}"
        if (stoppingScope != null) return
        stoppingScope = owner
        val submission = composerDrafts[host to session.id]?.submission
        scope.launch {
            try {
                // Do not let abort overtake an in-flight prompt admission.
                submission?.await()
                request("POST", "/session/${session.id}/abort${directory(session.project)}", "{}", expectedHost = host)
                composerDrafts[host to session.id]?.stopRequested?.value = true
                if (activeHostId == host && selectedSession?.id == session.id) refreshAttention(session)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (activeHostId == host && selectedSession?.id == session.id) error = "停止请求失败：${e.localizedMessage}" }
            finally { if (stoppingScope == owner) stoppingScope = null }
        }
    }

    fun disconnect() = act {
        store.setPreferredDesktopId(null)
        try { bridge.disconnectDesktop() } finally { onDisconnected() }
    }

    fun openHostPicker() {
        showModels = false
        showAttention = false
        showAttachMenu = false
        showHostPicker = true
        act {
            val credentials = checkNotNull(store.load()) { "请重新登录 NeCode Desktop" }
            hosts = bridge.listDesktops(credentials.sessionId)
            hostsLoaded = true
            activeHostId = bridge.activeDesktop()?.desktopId ?: store.preferredDesktopId()
        }
    }

    BackHandler(enabled = selectedSession != null || selectedProject != null || showProjects) {
        when {
            selectedSession != null -> selectedSession = null
            selectedProject != null -> selectedProject = null
            else -> showProjects = false
        }
    }

    LaunchedEffect(bridge, activeHostId, selectedProject?.key, showProjects, selectedSession?.id, recentSearch, projectSessionSearch) {
        if (browserHost != activeHostId) {
            browserHost = activeHostId
            browserGeneration++
            projectGeneration++
            projects = emptyList()
            projectsLoaded = false
            projectsLoading = false
            sessions = emptyList()
            nextSessionCursor = null
        }
        browserGeneration++
        if (selectedSession != null) return@LaunchedEffect
        nextSessionCursor = null
        if (showProjects && selectedProject == null) initialLoadComplete = projectsLoaded
        else if (displayedSessionScope != Triple(activeHostId.orEmpty(), selectedProject?.key,
            (if (selectedProject != null) projectSessionSearch else recentSearch).trim())) {
            sessions = emptyList()
            initialLoadComplete = false
        }
        error = null
        val generation = browserGeneration
        try {
            if ((if (selectedProject != null) projectSessionSearch else recentSearch).isNotBlank()) delay(250)
            loadProjectsAndSessions()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (generation == browserGeneration) { error = desktopConnectErrorMessage(e); initialLoadComplete = true } }
    }

    LaunchedEffect(bridge) {
        var checkOnResume = true
        while (true) {
            if (!foreground || showHostPicker) {
                checkOnResume = true
                delay(2_000)
                continue
            }
            val activeConnection = bridge.activeDesktop()
            healthy = activeConnection?.healthy == true && !connectionFailed &&
                activeConnection.expiresAt > System.currentTimeMillis()
            activeHostId = activeConnection?.desktopId ?: store.preferredDesktopId()
            if (healthy && activeConnection != null && store.preferredDesktopId() != activeConnection.desktopId)
                store.setPreferredDesktopId(activeConnection.desktopId)
            if (healthy && checkOnResume && !actionOwners.containsKey(actionKey())) {
                checkOnResume = false
                val host = activeHostId
                try { request("GET", "/session/status", expectedHost = host) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (activeHostId == host) {
                        if (connectionFailed) healthy = false
                        error = "电脑状态读取失败：${e.localizedMessage ?: e}"
                    }
                }
            }
            if (!healthy && !actionOwners.containsKey(actionKey()) && !reconnecting) {
                reconnecting = true
                var stage = "release"
                try {
                    LLog.i("DesktopConnect", "automatic reconnect started")
                    val credentials = checkNotNull(store.load()) { "请重新登录 NeCode Desktop" }
                    stage = "register"
                    val mobile = bridge.registerMobile(credentials.sessionId, credentials.mobileId, credentials.mobileSecret)
                    store.save(credentials.copy(mobileId = mobile.id, mobileSecret = mobile.secret))
                    stage = "recover"
                    val restored = checkNotNull(bridge.recoverDesktop(credentials.sessionId, mobile,
                        activeConnection?.desktopId ?: store.preferredDesktopId(), connectionFailed)) { "请重新选择电脑" }
                    healthy = restored.healthy
                    connectionFailed = false
                    activeHostId = restored.desktopId
                    store.setPreferredDesktopId(restored.desktopId)
                    error = null
                    stage = "load projects and sessions"
                    LLog.i("DesktopConnect", "automatic reconnect: load projects and sessions")
                    if (selectedSession == null) loadProjectsAndSessions()
                    else selectedSession?.let { refreshMessages(it) }
                    LLog.i("DesktopConnect", "automatic reconnect completed")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    LLog.w("DesktopConnect", "automatic reconnect failed", mapOf("stage" to stage, "error" to e.message))
                    error = "自动重连失败：${e.localizedMessage ?: e}"
                }
                finally { reconnecting = false }
                delay(10_000)
            } else delay(2_000)
        }
    }

    LaunchedEffect(activeHostId, selectedSession?.id, bridge) {
        while (selectedSession != null) {
            delay(2_000)
            val session = selectedSession ?: break
            if (actionOwners.containsKey(actionKey()) || !healthy || reconnecting || !foreground) continue
            val host = activeHostId
            try { refreshMessages(session) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (activeHostId == host && selectedSession?.id == session.id) error = desktopConnectErrorMessage(e) }
        }
    }

    LaunchedEffect(activeHostId, selectedSession?.id, bridge) {
        val host = activeHostId
        val session = selectedSession ?: return@LaunchedEffect
        val owner = "|$host|${session.id}|${session.project.directory}"
        var cursor: String? = null
        while (activeHostId == host && selectedSession?.id == session.id) {
            if (!healthy || reconnecting || !foreground || messageScope != owner) { delay(500); continue }
            try {
                val query = cursor?.let { "&cursor=" + Uri.encode(it) } ?: ""
                val snapshot = request("GET", "/connect/session/${session.id}/live${directory(session.project)}$query", expectedHost = host)
                if (activeHostId != host || selectedSession?.id != session.id || messageScope != owner) return@LaunchedEffect
                if (!foreground) continue
                val revision = JSONObject(snapshot).getString("revision")
                if (cursor == revision) continue
                cursor = revision
                liveSnapshot = snapshot
                refreshMessageProjection(session)
                // Absolute snapshots accumulate deltas while the UI yields: cap full Markdown redraws,
                // rather than debouncing indefinitely or dropping any reply text.
                delay(100)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (activeHostId == host && selectedSession?.id == session.id) {
                    cursor = null
                    liveSnapshot = null
                    error = "实时回复暂不可用：${desktopConnectErrorMessage(e)}"
                }
                delay(2_000)
            }
        }
    }

    LaunchedEffect(activeHostId, selectedSession?.id, messageDragging) {
        if (messageDragging) {
            messageDragStarted = true
            followMessageTail = false
            scrollAfterMessageUpdate = false
        } else if (messageDragStarted) {
            snapshotFlow { messageListState.isScrollInProgress }.first { !it }
            // Only the actual tail resumes following; a short upward drag must stay put.
            followMessageTail = !messageListState.canScrollForward
            messageDragStarted = false
            if (followMessageTail) scrollAfterMessageUpdate = true
        }
    }
    LaunchedEffect(selectedSession?.id, messageListState) {
        snapshotFlow { messageListState.layoutInfo.viewportEndOffset }.collect {
            if (selectedSession != null && followMessageTail && !messageDragging) scrollAfterMessageUpdate = true
        }
    }
    LaunchedEffect(timelineMessages, selectedSession?.id, scrollAfterMessageUpdate, followMessageTail, messageDragging) {
        if (selectedSession != null && followMessageTail && scrollAfterMessageUpdate && !messageDragging) {
            // Wait for the new rows/keyboard viewport to be laid out, then target the actual tail.
            withFrameNanos { }
            withFrameNanos { }
            if (!followMessageTail || messageDragging) return@LaunchedEffect
            val last = messageListState.layoutInfo.totalItemsCount - 1
            if (last >= 0) messageListState.scrollToItem(last)
            scrollAfterMessageUpdate = false
        }
    }

    Column(Modifier.fillMaxSize().background(LitterTheme.background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = if (selectedSession != null || selectedProject != null || showProjects) 4.dp else 16.dp, end = 12.dp, top = if (selectedSession != null) 6.dp else 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (selectedSession != null || showProjects || selectedProject != null) IconButton(onClick = {
                when {
                    selectedSession != null -> selectedSession = null
                    selectedProject != null -> selectedProject = null
                    else -> showProjects = false
                }
            }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (selectedSession != null) "返回会话" else if (selectedProject != null) "返回项目" else "返回最近会话", tint = LitterTheme.textPrimary)
            }
            if (selectedSession != null || showProjects || selectedProject != null) Spacer(Modifier.width(4.dp))
            Text(selectedSession?.displayTitle ?: selectedProject?.displayName ?: if (showProjects) "项目" else "会话", color = LitterTheme.textPrimary,
                fontSize = if (selectedSession == null) 22.sp else 18.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (selectedSession != null) Box {
                IconButton(onClick = { showSessionActions = true }, enabled = !busy && healthy) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "会话菜单", tint = LitterTheme.textSecondary)
                }
                LitterDropdownMenu(expanded = showSessionActions, onDismissRequest = { showSessionActions = false }) {
                    LitterDropdownMenuItem(text = { Text("刷新消息") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp)) }, onClick = {
                        showSessionActions = false
                        selectedSession?.let { session -> act { refreshMessages(session) } }
                    }, enabled = !busy && healthy)
                }
            } else IconButton(onClick = { act { loadProjectsAndSessions() } }, enabled = !busy && healthy) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = LitterTheme.textSecondary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (selectedSession != null) 4.dp else 8.dp)
            .clip(RoundedCornerShape(if (selectedSession != null) 12.dp else 14.dp)).background(LitterTheme.surface)
            .clickable(enabled = !busy) { openHostPicker() }
            .semantics {
                contentDescription = "选择电脑，当前$hostName，${if (healthy) "已连接" else "连接中断"}" +
                    (selectedSession?.let { "，项目${it.project.displayName}，目录${it.project.directory}" } ?: "")
            }
            .heightIn(min = if (selectedSession != null) 48.dp else 56.dp)
            .padding(horizontal = 12.dp, vertical = if (selectedSession != null) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (selectedSession != null) 8.dp else 10.dp)) {
            Box(Modifier.size(if (selectedSession != null) 28.dp else 36.dp)
                .background(LitterTheme.surfaceLight, RoundedCornerShape(if (selectedSession != null) 8.dp else 10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DesktopWindows, contentDescription = null,
                    tint = LitterTheme.accentStrong, modifier = Modifier.size(if (selectedSession != null) 18.dp else 20.dp))
            }
            val contextSession = selectedSession
            if (contextSession != null) Row(Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(hostName, color = LitterTheme.textPrimary, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Text("·", color = LitterTheme.textMuted, fontSize = 13.sp)
                Text(contextSession.project.displayName, color = LitterTheme.textSecondary, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hostName, color = LitterTheme.textPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("当前电脑", color = LitterTheme.textSecondary, fontSize = 12.sp)
            }
            Box(Modifier.size(7.dp).background(if (healthy) LitterTheme.success else LitterTheme.danger, CircleShape))
            Text(if (healthy) "已连接" else "连接中断",
                color = if (healthy) LitterTheme.textSecondary else LitterTheme.danger,
                fontSize = 12.sp, maxLines = 1)
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null,
                tint = LitterTheme.textSecondary, modifier = Modifier.size(18.dp))
        }
        error?.takeUnless { showHostPicker || selectedSession != null }?.let { Text(it, color = LitterTheme.danger, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        val active = selectedSession
        if (!healthy) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(64.dp).background(LitterTheme.surfaceLight, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DesktopWindows, contentDescription = null,
                        tint = LitterTheme.textSecondary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(if (reconnecting) "正在重新连接电脑…" else "与电脑的连接已中断", color = LitterTheme.textPrimary, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(if (reconnecting) "正在恢复项目与会话" else "选择一台在线电脑，继续查看项目与会话", color = LitterTheme.textSecondary,
                    fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = ::openHostPicker, enabled = !busy,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(LitterTheme.accentStrong).heightIn(min = 48.dp)
                        .padding(horizontal = 18.dp)) {
                    Text("选择电脑", color = LitterTheme.onAccentStrong, fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (active == null) {
            DesktopSessionBrowser(
                projects = projects, sessions = sessions, selectedProject = selectedProject,
                onSelectedProjectChange = { selectedProject = it; if (it != null) openedProject(it) }, showProjects = showProjects,
                onShowProjectsChange = { showProjects = it },
                search = if (selectedProject != null) projectSessionSearch else if (showProjects) projectSearch else recentSearch,
                onSearchChange = { if (selectedProject != null) projectSessionSearch = it else if (showProjects) projectSearch = it else recentSearch = it },
                initialLoadComplete = initialLoadComplete && (!(showProjects && selectedProject == null) || !projectsLoading), busy = busy, error = error,
                hasMore = nextSessionCursor != null, loadingMore = loadingMoreSessions, refreshing = refreshingSessions, loadMoreError = moreSessionsError,
                onLoadMore = {
                    val owner = actionKey()
                    val generation = browserGeneration
                    scope.launch {
                        try { refreshSessions(append = true) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { if (actionKey() == owner && browserGeneration == generation) moreSessionsError = e.localizedMessage ?: e.toString() }
                    }
                },
                onLoadProjects = { act { loadProjects() } }, projectsLoading = projectsLoading,
                onRetry = { act { loadProjectsAndSessions() } },
                onOpenSession = { session ->
                    val host = activeHostId
                    selectedSession = session
                    openedProject(session.project)
                    messages = emptyList()
                    act {
                        refreshMessages(session)
                        if (activeHostId == host && selectedSession?.id == session.id) loadModels(session.project)
                    }
                },
                onCreateSession = ::createSession, creatingProjectKey = creatingProjects[actionKey()],
                onAddProject = { showDirectoryPicker = true },
            )
        } else {
            val pendingCount = attention.getJSONArray("permissions").length() + attention.getJSONArray("questions").length()
            if (pendingCount > 0) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("需要你处理 · $pendingCount 项", color = LitterTheme.accentStrong, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAttachMenu = false; showModels = false; showAttention = true }) { Text("查看") }
            }
            if (composer.stopRequested.value && attention.optString("status") == "idle") Text("本次运行已停止，消息以桌面记录为准", color = LitterTheme.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            if (catchupPage != null) Text("正在补齐断线期间的消息…", color = LitterTheme.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            if (attention.optString("status") == "retry") Text(attention.optString("label"), color = LitterTheme.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            if (composer.needsPromptConfirmation) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("有一条待确认消息", fontSize = 12.sp, color = LitterTheme.textSecondary, modifier = Modifier.weight(1f))
                TextButton(onClick = { act { reconcilePrompt(active, retryMissing = true); refreshMessages(active) } }, enabled = !busy) {
                    Text("核对并重试")
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize().nestedScroll(manualMessageScroll).padding(horizontal = 16.dp),
                    state = messageListState, contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (nextMessageCursor != null) item(key = "older-messages") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (loadingOlderMessages) Text("正在加载更早消息…", color = LitterTheme.textSecondary, fontSize = 12.sp)
                            else TextButton(onClick = { act { refreshMessages(active, older = true) } }, enabled = !busy && !fetchingMessages) {
                                Text("加载更早消息", color = LitterTheme.accentStrong)
                            }
                        }
                    }
                    if (timelineMessages.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(top = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (awaitingResponse && error == null) DesktopReplyWaiting()
                            else if (busy) DesktopCenterLoading("正在读取消息…", Modifier.fillMaxWidth())
                            else Text(if (error != null) "暂时无法读取消息" else "从这里继续对话",
                                color = LitterTheme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            if (error != null && !busy) TextButton(onClick = { act { refreshMessages(active) } }) {
                                Text("重试", color = LitterTheme.accentStrong)
                            } else if (!busy && !awaitingResponse) Text("发送消息，桌面端会同步显示",
                                color = LitterTheme.textSecondary, fontSize = 13.sp)
                        }
                    }
                    itemsIndexed(timelineMessages, key = { _, message -> message.id }) { index, message ->
                        if (message.kind == DesktopMessageKind.FILE) {
                            val attachment = message.attachment ?: DesktopAttachmentPreview(message.text, "application/octet-stream", null, null,
                                "附件内容未随消息同步，暂时无法在手机预览")
                            Box(Modifier.fillMaxWidth(), contentAlignment = if (message.role == "user") Alignment.CenterEnd else Alignment.CenterStart) {
                                DesktopAttachmentCard(attachment, onPreview = { attachmentPreview = attachment })
                            }
                        } else if (message.kind != DesktopMessageKind.TEXT) {
                            val failed = message.status == "error"
                            val status = when (message.status) {
                                "pending" -> "准备中"
                                "running" -> "执行中"
                                "completed" -> "已完成"
                                "error" -> "失败"
                                else -> null
                            }
                            var expanded by remember(message.id) { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 4.dp, vertical = 3.dp)
                                .clickable(enabled = message.details.isNotEmpty()) { expanded = !expanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (message.kind == DesktopMessageKind.TOOL) Icons.Default.Build else Icons.Default.AttachFile,
                                    contentDescription = null, tint = if (failed) LitterTheme.danger else LitterTheme.textSecondary,
                                    modifier = Modifier.size(14.dp))
                                Text(message.text, color = if (failed) LitterTheme.danger else LitterTheme.textSecondary,
                                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f))
                                if (message.details.isNotEmpty()) Icon(Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (expanded) "收起操作" else "展开操作",
                                    tint = LitterTheme.textSecondary, modifier = Modifier.size(14.dp))
                                if (status != null && status != "已完成") Text(status,
                                    color = if (failed) LitterTheme.danger else LitterTheme.textSecondary,
                                    fontSize = 11.sp, maxLines = 1)
                            }
                            if (expanded) Text(message.details.joinToString(" · "),
                                color = LitterTheme.textSecondary, fontSize = 11.sp,
                                modifier = Modifier.padding(start = 26.dp, bottom = 6.dp))
                        } else {
                            val isUser = message.role == "user"
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                                if (isUser) {
                                    Box(Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp))
                                        .background(LitterTheme.surfaceLight)
                                        .padding(horizontal = 14.dp, vertical = 11.dp)) {
                                        SelectableMarkdownText(message.text, enableTerminalSelectionAction = false, readingLayout = true)
                                    }
                                    if (message.id.startsWith("local:")) Text(message.status.orEmpty(),
                                        color = if (message.status == "未发送") LitterTheme.danger else LitterTheme.textSecondary,
                                        fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, end = 4.dp))
                                } else {
                                    SelectableMarkdownText(message.text, modifier = Modifier.fillMaxWidth().onSizeChanged {
                                        // Paced text can wrap after the source snapshot's scroll request.
                                        if (followMessageTail && !messageDragging) scrollAfterMessageUpdate = true
                                    }, enableTerminalSelectionAction = false, readingLayout = true,
                                        streaming = (awaitingResponse || attention.optString("status") == "busy") &&
                                            index > timelineMessages.indexOfLast { it.role == "user" })
                                }
                            }
                        }
                    }
                    if (shouldShowDesktopReplyWaiting(timelineMessages, awaitingResponse || attention.optString("status") == "busy", error != null, pendingCount)) item(key = "reply-waiting") {
                        DesktopReplyWaiting()
                    }
                    item(key = "message-bottom") { Spacer(Modifier.height(1.dp)) }
                }
                // Match the desktop dock: content fades into the page, without a dark separator.
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(24.dp)
                    .background(Brush.verticalGradient(listOf(LitterTheme.background, LitterTheme.background.copy(alpha = 0f)))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(24.dp)
                    .background(Brush.verticalGradient(listOf(LitterTheme.background.copy(alpha = 0f), LitterTheme.background))))
                if (!followMessageTail && messageListState.canScrollForward && timelineMessages.isNotEmpty()) {
                    IconButton(onClick = { followMessageTail = true; scrollAfterMessageUpdate = true },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(48.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(LitterTheme.background.copy(alpha = 0.8f))
                            .border(1.dp, LitterTheme.textPrimary.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "回到最新",
                                tint = LitterTheme.textPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = {
                    showAttention = false; showAttachMenu = false; modelSearch = ""; showModels = true
                    if (modelOptions.isEmpty() && !modelsLoading) act { loadModels(active.project) }
                }, enabled = !busy || modelsLoading,
                    modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 0.dp)) {
                    if (modelsLoading) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(14.dp),
                        strokeWidth = 1.5.dp, color = LitterTheme.accentStrong)
                    Text(selectedModel?.name ?: if (modelsLoading) "正在加载模型…" else "选择模型", color = LitterTheme.textSecondary, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "选择模型",
                        tint = LitterTheme.textSecondary, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                }
            }
            if (modelError != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(modelError!!, color = LitterTheme.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { act { loadModels(active.project) } }, enabled = !busy && !modelsLoading) { Text("重试模型") }
            }
            if (attachments.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    attachments.forEachIndexed { index, attachment ->
                        val preview = remember(attachment) {
                            DesktopAttachmentPreview(attachment.filename, attachment.mime, attachment.size.toLong(),
                                attachment.dataUrl.substringAfter(',', ""))
                        }
                        DesktopAttachmentCard(preview, onPreview = { attachmentPreview = preview },
                            onRemove = { attachments = attachments.filterIndexed { i, _ -> i != index } })
                    }
                }
            }
            if (transcribing) Text("正在识别语音…", color = LitterTheme.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            if (error != null) Text(error!!, color = LitterTheme.danger, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            val isStopping = stoppingScope == "$activeHostId|${active.id}"
            val showStop = desktopComposerShowsStop(composer.submitting.value, awaitingResponse,
                attention.optString("status") in listOf("busy", "retry") || pendingCount > 0, isStopping)
            val canSend = healthy && !showStop && !hasPendingPrompt && !busy && !modelsLoading && !transcribing && (prompt.isNotBlank() || attachments.isNotEmpty())
            val composerActionEnabled = if (showStop) healthy && stoppingScope == null else canSend
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp))
                .background(LitterTheme.surface).padding(start = 0.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(onClick = { showAttachMenu = true }, enabled = !busy) {
                        Icon(Icons.Default.AttachFile, contentDescription = "添加图片或文件", tint = LitterTheme.textSecondary)
                    }
                    LitterDropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                        LitterDropdownMenuItem(text = { Text("选择图片") },
                            leadingIcon = { Icon(Icons.Default.Image, null, modifier = Modifier.size(20.dp)) }, onClick = {
                            showAttachMenu = false
                            attachmentOwner = composer; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        LitterDropdownMenuItem(text = { Text("选择文件") },
                            leadingIcon = { Icon(Icons.Default.Description, null, modifier = Modifier.size(20.dp)) }, onClick = {
                            showAttachMenu = false
                            attachmentOwner = composer; filePicker.launch(arrayOf("*/*"))
                        })
                    }
                }
                BasicTextField(prompt, { prompt = it }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .padding(end = 8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = LitterTheme.textPrimary, fontSize = 16.sp),
                    minLines = 1, maxLines = 5, decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                            if (prompt.isEmpty()) Text("发送消息…", color = LitterTheme.textMuted, fontSize = 16.sp)
                            inner()
                        }
                    })
                IconButton(onClick = {
                    if (recording) scope.launch {
                        val token = store.load()?.sessionId
                        if (token == null) {
                            transcriptionManager.cancelRecording()
                            error = "请重新登录 NeCode Desktop"
                            return@launch
                        }
                        val target = recordingOwner ?: composer
                        val transcript = try {
                            transcriptionManager.stopAndTranscribeDesktop(bridge, token)
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) {
                            transcriptionManager.cancelRecording()
                            error = desktopConnectErrorMessage(e)
                            return@launch
                        }
                        if (transcript != null) {
                            target.text.value = listOf(target.text.value.trim(), transcript).filter { it.isNotEmpty() }.joinToString(" ")
                            error = null
                        } else error = transcriptionManager.error.value ?: "没有识别到语音"
                    } else if (transcriptionManager.hasMicPermission(context)) {
                        recordingOwner = composer
                        transcriptionManager.startRecording(context)
                    } else { recordingOwner = composer; micPermission.launch(android.Manifest.permission.RECORD_AUDIO) }
                }, enabled = !busy && !transcribing) {
                    Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (recording) "停止录音并转文字" else "语音输入",
                        tint = if (recording) LitterTheme.accentStrong else LitterTheme.textSecondary)
                }
                IconButton(onClick = {
                    if (showStop) {
                        stopSession(active)
                        return@IconButton
                    }
                    if (actionOwners.containsKey(actionKey())) return@IconButton
                    keyboard?.hide()
                    focusManager.clearFocus()
                    val sendingHost = activeHostId
                    val sendingComposer = composer
                    val text = prompt
                    val files = attachments
                    val submission = CompletableDeferred<Unit>()
                    sendingComposer.submission = submission
                    sendingComposer.submitting.value = true
                    sendingComposer.stopRequested.value = false
                    sendingComposer.localPrompt.value = DesktopLocalPrompt(java.util.UUID.randomUUID().toString(), text, files.map { it.filename })
                    followMessageTail = true
                    scrollAfterMessageUpdate = true
                    act {
                      try {
                        if (modelOptions.isEmpty()) loadModels(active.project)
                        check(activeHostId == sendingHost && selectedSession?.id == active.id) { "电脑或会话已切换，消息未发送" }
                        val model = checkNotNull(selectedModel) { "请先选择模型；若列表为空，请在桌面端开启模型" }
                        check(model.image || files.none { it.mime.startsWith("image/") }) {
                            "所选模型不支持图片，请切换到支持图片的模型"
                        }
                        val credentials = checkNotNull(store.load()) { "请重新登录" }
                        val hostId = checkNotNull(activeHostId) { "尚未连接电脑" }
                        val body = withContext(Dispatchers.IO) {
                            val parts = JSONArray()
                            if (text.isNotBlank()) parts.put(JSONObject().put("type", "text").put("text", text))
                            files.forEach { parts.put(JSONObject().put("type", "file")
                                .put("mime", it.mime).put("filename", it.filename).put("url", it.dataUrl)) }
                            JSONObject().put("parts", parts)
                                .put("model", JSONObject().put("providerID", model.providerID).put("modelID", model.modelID))
                                .toString().also { check(it.toByteArray(Charsets.UTF_8).size < 4_000_000) {
                                    "消息与附件合计超过 4 MB，请缩短文字或移除附件"
                                } }
                        }
                        val attempt = withContext(Dispatchers.IO) {
                            prepareDesktopPrompt(credentials.accountId, hostId, active.id, body,
                                store.pendingPrompt(credentials.accountId, hostId, active.id)).also {
                                store.savePendingPrompt(credentials.accountId, hostId, active.id, it.journalJson)
                            }
                        }
                        sendingComposer.pending.value = true
                        sendingComposer.localPrompt.value = sendingComposer.localPrompt.value?.copy(messageId = attempt.messageId)
                        check(activeHostId == hostId && selectedSession?.id == active.id) { "电脑或会话已切换，原消息待确认" }
                        try { request("POST", "/session/${active.id}/prompt_async${directory(active.project)}", attempt.bodyJson, expectedHost = hostId) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            if (e is DesktopHttpException) {
                                val receipt = request("GET", "/connect/session/${active.id}/prompt/${attempt.messageId}${directory(active.project)}", expectedHost = hostId)
                                if (desktopPromptRejectionConfirmed(e.status.toUShort(), receipt, active.id, attempt.messageId)) {
                                    val cleared = withContext(Dispatchers.IO) { store.clearPendingPrompt(credentials.accountId, hostId, active.id, attempt.journalJson) }
                                    if (cleared) sendingComposer.pending.value = false
                                    throw IllegalStateException("桌面未接纳消息，草稿已保留，请修正后发送：${e.localizedMessage}", e)
                                }
                            }
                            throw IllegalStateException("发送未确认，原消息已安全保留，请点“核对并重试”：${e.localizedMessage}", e)
                        }
                        acceptPrompt(active, attempt, credentials.accountId, hostId)
                        sendingComposer.submitting.value = false
                        submission.complete(Unit)
                        if (activeHostId == hostId && selectedSession?.id == active.id) refreshMessages(active)
                      } catch (e: Exception) {
                        if (sendingComposer.localPrompt.value?.status == "正在发送…") {
                            sendingComposer.localPrompt.value = sendingComposer.localPrompt.value?.copy(
                                status = if (sendingComposer.pending.value) "发送待确认" else "未发送")
                        }
                        throw e
                      } finally {
                        sendingComposer.submitting.value = false
                        submission.complete(Unit)
                        if (sendingComposer.submission === submission) sendingComposer.submission = null
                      }
                    }
                }, enabled = composerActionEnabled, modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(if (composerActionEnabled || isStopping) LitterTheme.accentStrong else LitterTheme.surfaceLight)) {
                    if (isStopping) CircularProgressIndicator(modifier = Modifier.size(20.dp)
                        .semantics { contentDescription = "正在停止" }, color = LitterTheme.onAccentStrong, strokeWidth = 2.dp)
                    else Icon(if (showStop) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (showStop) "停止" else "发送",
                        tint = if (composerActionEnabled) LitterTheme.onAccentStrong else LitterTheme.textMuted,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (showAttention && selectedSession != null) {
        val session = checkNotNull(selectedSession)
        val host = activeHostId
        DesktopAttentionSheet(attention, activeHostId + "|" + session.id, busy,
            onDismiss = { showAttention = false },
            onReply = { path, body -> act {
                request("POST", path + directory(session.project), body, expectedHost = host)
                if (selectedSession?.id == session.id && activeHostId == host) {
                    refreshAttention(session)
                    if (selectedSession?.id == session.id && activeHostId == host) {
                        showAttention = false
                        refreshMessages(session)
                    }
                }
            } })
    }
    if (showModels) {
        ModalBottomSheet(onDismissRequest = { showModels = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = LitterTheme.background) {
            Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.78f).dp)
                .padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("选择模型", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                        color = LitterTheme.textPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showModels = false }) {
                        Icon(Icons.Default.Close, "关闭模型选择", tint = LitterTheme.textSecondary)
                    }
                }
                OutlinedTextField(modelSearch, { modelSearch = it }, placeholder = { Text("搜索模型") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(14.dp),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (modelsLoading) Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LitterTheme.accentStrong)
                    Text("正在加载模型…", color = LitterTheme.textSecondary, modifier = Modifier.padding(start = 10.dp))
                }
                if (modelError != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(modelError.orEmpty(), color = LitterTheme.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedSession?.let { act { loadModels(it.project) } } }, enabled = !modelsLoading && !busy) { Text("重试") }
                }
                val filtered = modelOptions.filter { modelSearch.isBlank() ||
                    it.name.contains(modelSearch, ignoreCase = true) ||
                    it.providerName.contains(modelSearch, ignoreCase = true) }
                if (filtered.isEmpty() && !modelsLoading) Text(
                    if (modelOptions.isEmpty()) "暂无可选模型，请在桌面端开启模型" else "没有匹配的模型",
                    color = LitterTheme.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    filtered.groupBy { it.providerName }.forEach { (provider, models) ->
                        item { Text(provider, color = LitterTheme.textSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)) }
                        items(models, key = { "${it.providerID}/${it.modelID}" }) { model ->
                            val picked = model.providerID == selectedModel?.providerID && model.modelID == selectedModel?.modelID
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (picked) LitterTheme.accentStrong.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable { selectedModel = model; showModels = false; modelSearch = "" }
                                .heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(model.name, color = LitterTheme.textPrimary, fontSize = 15.sp,
                                        fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Normal)
                                    if (model.image) Text("支持图片", color = LitterTheme.textSecondary, fontSize = 11.sp)
                                }
                                if (picked) Icon(Icons.Default.Check, "当前模型", tint = LitterTheme.accentStrong, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (showHostPicker) {
        ModalBottomSheet(onDismissRequest = { if (!busy) { showHostPicker = false; error = null } },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = LitterTheme.background) {
            Column(Modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.78f).dp)) {
                DesktopHostPicker(
                    hosts = hosts, activeHostId = activeHostId, healthy = healthy,
                    busy = busy || reconnecting || connectingHostId != null, connectingHostId = connectingHostId, error = error,
                    showLogout = false, hostsLoaded = hostsLoaded,
                    onRefresh = {
                        act {
                            val credentials = checkNotNull(store.load()) { "请重新登录 NeCode Desktop" }
                            hosts = bridge.listDesktops(credentials.sessionId)
                            hostsLoaded = true
                            activeHostId = bridge.activeDesktop()?.desktopId ?: store.preferredDesktopId()
                        }
                    },
                    onDismiss = { showHostPicker = false; error = null },
                    onSelect = { host ->
                        val action = desktopHostAction(activeHostId, host.id, host.status, healthy)
                        if (action == DesktopHostAction.CURRENT) showHostPicker = false
                        else {
                            connectingHostId = host.id
                            act {
                                var stage = "register"
                                try {
                                    LLog.i("DesktopConnect", "manual connect: register")
                                    val credentials = checkNotNull(store.load()) { "请重新登录 NeCode Desktop" }
                                    val mobile = bridge.registerMobile(credentials.sessionId, credentials.mobileId, credentials.mobileSecret)
                                    store.save(credentials.copy(mobileId = mobile.id, mobileSecret = mobile.secret))
                                    stage = "connect"
                                    healthy = false
                                    bridge.connectDesktop(credentials.sessionId, mobile, host.id)
                                    store.setPreferredDesktopId(host.id)
                                    activeHostId = host.id
                                    healthy = true
                                    connectionFailed = false
                                    selectedSession = null
                                    selectedProject = null
                                    showProjects = false
                                    browserGeneration++
                                    projectGeneration++
                                    projectsLoaded = false
                                    projectsLoading = false
                                    nextSessionCursor = null
                                    projects = emptyList()
                                    sessions = emptyList()
                                    messages = emptyList()
                                    onHostChanged(host.name)
                                    showHostPicker = false
                                    LLog.i("DesktopConnect", "manual connect completed")
                                } catch (e: Exception) {
                                    LLog.w("DesktopConnect", "manual connect failed", mapOf("stage" to stage, "error" to e.message))
                                    throw e
                                } finally { connectingHostId = null }
                            }
                        }
                    },
                    onRemove = { host ->
                        act {
                            val credentials = checkNotNull(store.load()) { "请重新登录 NeCode Desktop" }
                            bridge.removeDesktop(credentials.sessionId, host.id)
                            hosts = hosts.filterNot { it.id == host.id }
                            if (store.preferredDesktopId() == host.id) store.setPreferredDesktopId(null)
                            hosts = bridge.listDesktops(credentials.sessionId)
                            hostsLoaded = true
                        }
                    },
                    onLogout = {},
                    modifier = Modifier.weight(1f, fill = false),
                )
                HorizontalDivider(Modifier.fillMaxWidth(), color = LitterTheme.divider)
                TextButton(onClick = ::disconnect, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("断开当前电脑", color = LitterTheme.textSecondary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (showDirectoryPicker && activeHostId != null) {
        val owner = activeHostId!!
        DesktopDirectoryPickerSheet(bridge, owner, hostName,
            onDismiss = { showDirectoryPicker = false },
            onSelected = { folder ->
                if (activeHostId == owner && showDirectoryPicker) {
                    val project = DesktopProject(folder.id, folder.name, folder.directory)
                    projects = projects.filterNot { it.key == project.key } + project
                    selectedProject = project
                    openedProject(project)
                    projectSessionSearch = ""
                    selectedSession = null
                    showProjects = true
                    showDirectoryPicker = false
                    error = null
                }
            })
    }

}




@Composable
internal fun ColumnScope.DesktopSessionBrowser(
    projects: List<DesktopProject>, sessions: List<DesktopSession>,
    selectedProject: DesktopProject?, onSelectedProjectChange: (DesktopProject?) -> Unit,
    showProjects: Boolean, onShowProjectsChange: (Boolean) -> Unit,
    search: String, onSearchChange: (String) -> Unit,
    initialLoadComplete: Boolean, busy: Boolean, error: String?,
    onRetry: () -> Unit, onOpenSession: (DesktopSession) -> Unit,
    onCreateSession: (DesktopProject) -> Unit, onAddProject: () -> Unit,
    hasMore: Boolean = false, loadingMore: Boolean = false, refreshing: Boolean = false, onLoadMore: () -> Unit = {},
    loadMoreError: String? = null,
    onLoadProjects: () -> Unit = {}, projectsLoading: Boolean = false,
    creatingProjectKey: String? = null,
) {
    var showNewSessionMenu by remember { mutableStateOf(false) }
    val browsingProjects = showProjects && selectedProject == null
    val visibleSessions = sessions.filter { selectedProject == null || it.project.key == selectedProject.key }
    val visibleProjects = projects.filter { project ->
        search.isBlank() || project.name.contains(search, true) || project.directory.contains(search, true)
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(12.dp)).background(LitterTheme.surface)
        .heightIn(min = 48.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Search, contentDescription = null, tint = LitterTheme.textMuted, modifier = Modifier.size(20.dp))
        BasicTextField(value = search, onValueChange = onSearchChange, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = LitterTheme.textPrimary, fontSize = 14.sp),
            decorationBox = { inner ->
                Box {
                    if (search.isEmpty()) Text(
                        if (selectedProject != null) "搜索项目会话" else if (browsingProjects) "搜索项目" else "搜索最近会话",
                        color = LitterTheme.textMuted, fontSize = 14.sp)
                    inner()
                }
            }, modifier = Modifier.weight(1f))
        if (search.isNotEmpty()) IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, contentDescription = "清除搜索", tint = LitterTheme.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (selectedProject != null) "项目会话" else if (browsingProjects) "项目" else "最近会话",
            color = LitterTheme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (refreshing && initialLoadComplete && !browsingProjects) CircularProgressIndicator(
            Modifier.size(16.dp), color = LitterTheme.accentStrong, strokeWidth = 2.dp)
        if (!browsingProjects && selectedProject == null) TextButton(
            onClick = { onShowProjectsChange(true) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("项目", color = LitterTheme.accentStrong, fontSize = 14.sp)
        }
        Box {
            TextButton(onClick = {
                if (browsingProjects) onAddProject()
                else if (selectedProject != null) onCreateSession(selectedProject)
                else { showNewSessionMenu = true; onLoadProjects() }
            }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = LitterTheme.accentStrong, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (browsingProjects) "添加项目" else "新会话", color = LitterTheme.accentStrong, fontSize = 14.sp)
            }
            LitterDropdownMenu(expanded = showNewSessionMenu, onDismissRequest = { showNewSessionMenu = false }) {
                if (projectsLoading) Text("正在读取项目…", color = LitterTheme.textSecondary, modifier = Modifier.padding(16.dp))
                projects.forEach { project ->
                    LitterDropdownMenuItem(text = { Text(project.name) }, onClick = {
                        showNewSessionMenu = false
                        onCreateSession(project)
                    })
                }
                LitterDropdownMenuItem(text = { Text("添加项目…", color = LitterTheme.accentStrong) }, onClick = {
                    showNewSessionMenu = false
                    onAddProject()
                })
            }
        }
    }

    if (!initialLoadComplete && (!browsingProjects || projects.isEmpty())) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            DesktopCenterLoading("正在读取电脑上的" + (if (browsingProjects) "项目" else "会话") + "…")
        }
    } else if (error != null && projects.isEmpty() && sessions.isEmpty()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("无法读取电脑上的" + (if (browsingProjects) "项目" else "会话"), color = LitterTheme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("请检查连接，或点击重试", color = LitterTheme.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry, enabled = !busy) { Text("重新加载", color = LitterTheme.accentStrong) }
        }
    } else if ((browsingProjects && visibleProjects.isEmpty()) || (!browsingProjects && visibleSessions.isEmpty())) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(56.dp).background(LitterTheme.surfaceLight, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Icon(if (search.isNotBlank()) Icons.Default.Search else if (browsingProjects) Icons.Default.Folder else Icons.Outlined.DesktopWindows,
                        contentDescription = null, tint = LitterTheme.accentStrong, modifier = Modifier.size(24.dp))
                }
                Text(when {
                    search.isNotBlank() -> "没有找到匹配的" + (if (browsingProjects) "项目" else "会话")
                    browsingProjects && projects.isEmpty() -> "这台电脑还没有项目"
                    browsingProjects -> "暂无项目"
                    selectedProject != null -> "这个项目暂无会话"
                    else -> "还没有会话"
                }, color = LitterTheme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(when {
                    search.isNotBlank() -> "换个关键词，或清除搜索再试"
                    browsingProjects && projects.isEmpty() -> "添加这台电脑上的文件夹，即可开始会话"
                    browsingProjects -> "换个关键词，或添加另一个文件夹"
                    else -> "点击“新会话”开始提问"
                }, color = LitterTheme.textSecondary, fontSize = 13.sp)
            }
        }
    } else if (browsingProjects) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            itemsIndexed(visibleProjects, key = { _, project -> project.key }) { index, project ->
                Row(Modifier.fillMaxWidth().clickable { onSelectedProjectChange(project) }
                    .heightIn(min = 76.dp).padding(start = 4.dp, end = 0.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = LitterTheme.accentStrong, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(project.name.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\'), color = LitterTheme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(project.directory, color = LitterTheme.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(
                        onClick = { onCreateSession(project) }, enabled = !busy,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = LitterTheme.textSecondary, disabledContentColor = LitterTheme.textSecondary,
                        ),
                    ) {
                        if (creatingProjectKey == project.key) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).semantics { contentDescription = "正在为${project.displayName}新建会话" },
                            color = LitterTheme.textSecondary, strokeWidth = 2.dp,
                        )
                        else Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "在${project.displayName}新建会话", modifier = Modifier.size(18.dp))
                    }
                }
                if (index < visibleProjects.lastIndex) HorizontalDivider(color = LitterTheme.divider)
            }
        }
    } else {
        key(selectedProject?.key, search) {
            PaginatedLazyColumn(
                hasMore = hasMore, loading = loadingMore, onLoadMore = onLoadMore,
                enabled = !busy && !refreshing, error = loadMoreError,
                loadingText = "正在加载更多会话…", modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                itemsIndexed(visibleSessions, key = { _, session -> session.project.id + "-" + session.id }) { index, session ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenSession(session) }
                        .heightIn(min = 76.dp).padding(horizontal = 4.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FormattedText(session.displayTitle, color = LitterTheme.textPrimary, fontSize = 15.sp, maxLines = 2, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = LitterTheme.textSecondary, modifier = Modifier.size(13.dp))
                                Text(session.project.displayName, color = LitterTheme.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (session.updated > 0) Text(desktopSessionTime(session.updated, System.currentTimeMillis()), color = LitterTheme.textSecondary, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                    if (index < visibleSessions.lastIndex) HorizontalDivider(color = LitterTheme.divider)
                }
            }
        }
    }
}

internal class DesktopComposerDraft {
    var submission: CompletableDeferred<Unit>? = null
    val submitting = mutableStateOf(false)
    val localPrompt = mutableStateOf<DesktopLocalPrompt?>(null)
    val stopRequested = mutableStateOf(false)
    val text = mutableStateOf("")
    val attachments = mutableStateOf<List<DesktopAttachment>>(emptyList())
    val model = mutableStateOf<DesktopModel?>(null)
    // Async loads must update this session's draft, not a remember state replaced on navigation.
    val modelOptions = mutableStateOf<List<DesktopModel>>(emptyList())
    val modelsLoading = mutableStateOf(false)
    val modelError = mutableStateOf<String?>(null)
    val pending = mutableStateOf(false)
    // The durable journal is pending during a normal in-flight send, not just after a failure.
    val needsPromptConfirmation: Boolean get() = pending.value && !submitting.value
    val awaiting = mutableStateOf(false)
    val promptId = mutableStateOf<String?>(null)
}

internal data class DesktopAttachment(val filename: String, val mime: String, val dataUrl: String, val size: Int)

private fun readDesktopAttachment(context: android.content.Context, uri: Uri): DesktopAttachment {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.substringAfterLast('/')?.substringAfterLast('\\')?.take(255)?.ifBlank { null } ?: "附件"
    val mime = context.contentResolver.getType(uri)?.takeIf { it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) }
        ?: "application/octet-stream"
    val image = mime.startsWith("image/")
    val limit = if (image) 12_000_000 else 2_000_000
    val data = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (output.size() <= limit) {
            val read = input.read(chunk, 0, minOf(chunk.size, limit + 1 - output.size()))
            if (read < 0) break
            check(read != 0) { "附件读取没有进展" }
            output.write(chunk, 0, read)
        }
        output.toByteArray()
    } ?: throw IllegalStateException("无法打开附件")
    check(data.size <= limit) { if (image) "图片不能超过 12 MB" else "单个附件不能超过 2 MB" }
    if (!image) return DesktopAttachment(name, mime,
        "data:$mime;base64,${Base64.encodeToString(data, Base64.NO_WRAP)}", data.size)

    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法解码图片" }
    val sample = generateSequence(1) { it * 2 }.first {
        maxOf(bounds.outWidth, bounds.outHeight) / it <= 1600
    }
    val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size,
        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw IllegalStateException("无法处理图片")
    val encoded = java.io.ByteArrayOutputStream()
    try {
        check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, encoded)) { "无法压缩图片" }
    } finally { bitmap.recycle() }
    val jpeg = encoded.toByteArray()
    check(jpeg.size <= 2_000_000) { "图片压缩后仍超过 2 MB，请选择更小的图片" }
    return DesktopAttachment(name.substringBeforeLast('.') + ".jpg", "image/jpeg",
        "data:image/jpeg;base64,${Base64.encodeToString(jpeg, Base64.NO_WRAP)}", jpeg.size)
}

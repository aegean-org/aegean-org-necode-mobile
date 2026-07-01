package com.litter.android.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litter.android.auth.ChatGPTOAuthActivity
import com.litter.android.state.ChatGPTOAuth
import com.litter.android.state.ChatGPTOAuthTokenStore
import com.litter.android.state.DebugSettings
import com.litter.android.state.MessageRecorder
import com.litter.android.state.OpenAIApiKeyStore
import com.litter.android.state.PetOverlayController
import com.litter.android.state.SavedServer
import com.litter.android.state.SavedServerStore
import com.litter.android.state.SshAuthMethod
import com.litter.android.state.SshCredentialStore
import com.litter.android.state.connectionModeLabel
import com.litter.android.state.isConnected
import com.litter.android.state.statusColor
import com.litter.android.state.statusLabel
import com.litter.android.state.toRecord
import com.litter.android.ui.LocalAppModel
import com.litter.android.ui.LitterAppearanceMode
import com.litter.android.ui.LitterColorThemeType
import com.litter.android.ui.BerkeleyMono
import com.litter.android.ui.ConversationPrefs
import com.litter.android.ui.ExperimentalFeatures
import com.litter.android.ui.LitterFeature
import com.litter.android.ui.WallpaperBackdrop
import com.litter.android.ui.WallpaperManager
import com.litter.android.ui.WallpaperScope
import com.litter.android.ui.WallpaperType
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.LitterThemeIndexEntry
import com.litter.android.ui.LitterThemeManager
import com.litter.android.ui.discovery.SSHLoginDialog
import com.litter.android.util.LLog
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.Account
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppLoginAccountRequest
import uniffi.codex_mobile_client.AppPetSummary

/**
 * Settings — hierarchical navigation matching iOS:
 * Top level: Appearance → | Font | Conversation | Experimental → | Account | Servers
 * Appearance pushes to sub-screen with theme pickers.
 * Experimental pushes to sub-screen with feature toggles.
 */

// ═══════════════════════════════════════════════════════════════════════════════
// Top-level Settings
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    initialSubScreen: SettingsStartDestination = SettingsStartDestination.TopLevel,
) {
    // Sub-screen navigation
    var subScreen by remember(initialSubScreen) {
        mutableStateOf(
            when (initialSubScreen) {
                SettingsStartDestination.TopLevel -> null
                SettingsStartDestination.Pets -> SettingsSubScreen.Pets
            },
        )
    }

    when (subScreen) {
        SettingsSubScreen.Appearance -> AppearanceScreen(onBack = { subScreen = null })
        SettingsSubScreen.Experimental -> ExperimentalScreen(onBack = { subScreen = null })
        SettingsSubScreen.Pets -> PetsScreen(onBack = { subScreen = null })
        SettingsSubScreen.TipJar -> TipJarScreen(onBack = { subScreen = null })
        SettingsSubScreen.Debug -> DebugScreen(onBack = { subScreen = null })
        null -> SettingsTopLevel(
            onDismiss = onDismiss,
            onOpenAppearance = { subScreen = SettingsSubScreen.Appearance },
            onOpenExperimental = { subScreen = SettingsSubScreen.Experimental },
            onOpenDebug = { subScreen = SettingsSubScreen.Debug },
        )
    }
}

enum class SettingsStartDestination { TopLevel, Pets }

private enum class SettingsSubScreen { Appearance, Experimental, Pets, TipJar, Debug }

@Composable
private fun SettingsTopLevel(
    onDismiss: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenExperimental: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val collapseTurns = ConversationPrefs.areTurnsCollapsed
    var renameTarget by remember { mutableStateOf<AppServerSnapshot?>(null) }
    var renameText by remember { mutableStateOf("") }

    var editTarget by remember { mutableStateOf<AppServerSnapshot?>(null) }
    var sshReconnectTarget by remember { mutableStateOf<SavedServer?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Title
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("设置", color = LitterTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text("完成", color = LitterTheme.accent)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Theme ──
        item { SectionHeader("主题") }
        item {
            NavRow(icon = Icons.Default.Palette, label = "外观", onClick = onOpenAppearance)
        }

        // ── Font ──
        item { SectionHeader("字体") }
        item {
            Column(
                Modifier.fillMaxWidth().background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            ) {
                FontRow("Berkeley Mono", BerkeleyMono, LitterThemeManager.monoFontEnabled) { LitterThemeManager.applyFont(true) }
                HorizontalDivider(color = LitterTheme.divider)
                FontRow("系统默认", FontFamily.Default, !LitterThemeManager.monoFontEnabled) { LitterThemeManager.applyFont(false) }
            }
        }

        // ── Conversation ──
        item { SectionHeader("会话") }
        item {
            SettingsRow(
                icon = { Text("⊟", color = LitterTheme.accent, fontSize = 16.sp) },
                label = "折叠历史轮次",
                subtitle = "把之前的对话轮次折叠成卡片",
                trailing = {
                    Switch(
                        checked = collapseTurns,
                        onCheckedChange = { ConversationPrefs.setCollapseTurns(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = LitterTheme.accent),
                    )
                },
            )
        }

        // ── Experimental ──
        item { SectionHeader("实验功能") }
        item {
            NavRow(icon = Icons.Default.Science, label = "实验功能", onClick = onOpenExperimental)
        }

        // ── Debug ──
        if (DebugSettings.enabled) {
            item { SectionHeader("调试") }
            item {
                NavRow(icon = Icons.Default.Science, label = "调试设置", onClick = onOpenDebug)
            }
        }

        // ── Servers ──
        item { SectionHeader("设备") }
        val servers = snapshot?.servers ?: emptyList()
        if (servers.isEmpty()) {
            item { SettingsRow(label = "暂无已连接设备") }
        } else {
            items(servers, key = { it.serverId }) { server ->
                ServerSettingsRow(
                    server = server,
                    onRename = {
                        renameText = server.displayName
                        renameTarget = server
                    },
                    onEdit = {
                        editTarget = server
                    },
                    onRemove = {
                        scope.launch {
                            SavedServerStore.remove(context, server.serverId)
                            appModel.sshSessionStore.close(server.serverId)
                            appModel.serverBridge.disconnectServer(server.serverId)
                            appModel.refreshSnapshot()
                        }
                    },
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }

    renameTarget?.let { server ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名设备") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isEmpty()) return@TextButton
                    scope.launch {
                        SavedServerStore.rename(context, server.serverId, trimmed)
                        appModel.store.renameServer(server.serverId, trimmed)
                        appModel.refreshSnapshot()
                    }
                    renameTarget = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    editTarget?.let { server ->
        ServerEditSheet(
            server = server,
            onDismiss = { editTarget = null },
            onSave = { editTarget = null },
            onTriggerSshReconnect = { saved ->
                editTarget = null
                sshReconnectTarget = saved
            },
        )
    }

    sshReconnectTarget?.let { saved ->
        val sshCredentialStore = remember(context) { SshCredentialStore(context.applicationContext) }
        val sshPort = saved.resolvedSshPort
        SSHLoginDialog(
            server = saved,
            initialCredential = sshCredentialStore.load(saved.hostname, sshPort),
            onDismiss = { sshReconnectTarget = null },
            onConnect = { credential, rememberCredentials ->
                try {
                    if (rememberCredentials) {
                        sshCredentialStore.save(saved.hostname, sshPort, credential)
                    } else {
                        sshCredentialStore.delete(saved.hostname, sshPort)
                    }

                    appModel.serverBridge.disconnectServer(saved.id)

                    when (credential.method) {
                        SshAuthMethod.PASSWORD -> appModel.serverBridge.startRemoteOverSshConnect(
                            serverId = saved.id,
                            displayName = saved.name,
                            host = saved.hostname,
                            port = sshPort.toUShort(),
                            username = credential.username,
                            password = credential.password,
                            privateKeyPem = null,
                            passphrase = null,
                            unlockMacosKeychain = credential.unlockMacosKeychain,
                            acceptUnknownHost = true,
                            workingDir = null,
                        )
                        SshAuthMethod.KEY -> appModel.serverBridge.startRemoteOverSshConnect(
                            serverId = saved.id,
                            displayName = saved.name,
                            host = saved.hostname,
                            port = sshPort.toUShort(),
                            username = credential.username,
                            password = null,
                            privateKeyPem = credential.privateKey,
                            passphrase = credential.passphrase,
                            unlockMacosKeychain = false,
                            acceptUnknownHost = true,
                            workingDir = null,
                        )
                    }
                    appModel.refreshSnapshot()
                    sshReconnectTarget = null
                    null
                } catch (e: Exception) {
                    LLog.e("SettingsSheet", "SSH reconnect failed: ${e.message}", e)
                    e.message ?: "SSH reconnect failed"
                }
            },
        )
    }

}

@Composable
private fun ServerSettingsRow(
    server: AppServerSnapshot,
    onRename: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(if (server.isLocal) "📱" else "🖥", fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(server.displayName, color = LitterTheme.textPrimary, fontSize = 13.sp)
                Text(
                    "${server.statusLabel} · ${server.connectionModeLabel}",
                    color = server.statusColor,
                    fontSize = 11.sp,
                )
            }
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "设备操作",
                    tint = LitterTheme.textSecondary,
                )
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    onClick = {
                        showMenu = false
                        onEdit()
                    },
                )
            }
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        showMenu = false
                        onRename()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("移除") },
                onClick = {
                    showMenu = false
                    onRemove()
                },
            )
        }
    }
}

private enum class ServerConnectionMode(val label: String, val formHeader: String) {
    LOCAL("本机", "本机运行时"),
    SSH("SSH", "SSH 主机"),
    DIRECT_CODEX("App-server", "App-server"),
    WEBSOCKET("WebSocket", "App-server 地址"),
    SLINGSHOT("Slingshot", "Slingshot"),
}

private fun isSettingsSlingshotUrl(rawUrl: String): Boolean =
    runCatching { Uri.parse(rawUrl).scheme?.equals("slingshot", ignoreCase = true) == true }
        .getOrDefault(false)

private suspend fun loadSettingsSlingshotTokens(context: Context) =
    ChatGPTOAuth.requireStoredOrRefreshedTokens(
        context,
        "使用 Slingshot 连接前请先登录 ChatGPT。",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerEditSheet(
    server: AppServerSnapshot,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onTriggerSshReconnect: (SavedServer) -> Unit,
) {
    val context = LocalContext.current
    val appModel = LocalAppModel.current
    val scope = rememberCoroutineScope()

    val savedServers = remember { SavedServerStore.load(context) }
    val originalSaved = remember(savedServers, server.serverId) {
        savedServers.firstOrNull { it.id == server.serverId }
    }

    val resolvedMode = remember(originalSaved, server.isLocal) {
        when {
            server.isLocal -> ServerConnectionMode.LOCAL
            originalSaved?.websocketURL?.let(::isSettingsSlingshotUrl) == true -> ServerConnectionMode.SLINGSHOT
            originalSaved?.websocketURL != null -> ServerConnectionMode.WEBSOCKET
            originalSaved?.preferredConnectionMode == "ssh" || (originalSaved?.sshPort != null && originalSaved?.hasCodexServer == false) -> ServerConnectionMode.SSH
            else -> ServerConnectionMode.DIRECT_CODEX
        }
    }
    var displayName by remember { mutableStateOf(originalSaved?.name?.trim()?.takeIf { it.isNotEmpty() } ?: server.displayName) }
    var connectionMode by remember { mutableStateOf(resolvedMode) }
    var host by remember { mutableStateOf(originalSaved?.hostname?.trim()?.takeIf { it.isNotEmpty() } ?: server.host) }
    var codexPort by remember { mutableStateOf(originalSaved?.preferredCodexPort?.toString() ?: originalSaved?.port?.takeIf { it > 0 }?.toString() ?: "8390") }
    var websocketURL by remember { mutableStateOf(originalSaved?.websocketURL ?: "") }
    var sshPort by remember { mutableStateOf(originalSaved?.sshPort?.toString() ?: "22") }
    var wakeMAC by remember { mutableStateOf(originalSaved?.wakeMAC ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isReconnecting by remember { mutableStateOf(false) }
    var pendingSlingshotReconnect by remember { mutableStateOf<SavedServer?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun validateAndBuild(): SavedServer? {
        val name = displayName.trim()
        if (name.isEmpty()) {
            validationError = "设备名称不能为空。"
            return null
        }

        if (originalSaved?.alleycatNodeId != null || originalSaved?.alleycatAgentWire == "ssh-bridge") {
            // Paired server — only name is editable
            return originalSaved.copy(name = name)
        }

        return when (connectionMode) {
            ServerConnectionMode.LOCAL -> {
                SavedServer(
                    id = server.serverId,
                    name = name,
                    hostname = "127.0.0.1",
                    port = 0,
                    codexPorts = emptyList(),
                    sshPort = null,
                    source = "local",
                    hasCodexServer = true,
                    wakeMAC = null,
                    preferredConnectionMode = null,
                    preferredCodexPort = null,
                    sshPortForwardingEnabled = null,
                    websocketURL = null,
                    rememberedByUser = true,
                )
            }
            ServerConnectionMode.SSH -> {
                val resolvedHost = host.trim()
                if (resolvedHost.isEmpty()) {
                    validationError = "主机地址不能为空。"
                    return null
                }
                val resolvedSSHPort = sshPort.trim().toIntOrNull()
                if (resolvedSSHPort == null || resolvedSSHPort !in 1..65535) {
                    validationError = "SSH 端口必须是有效数字。"
                    return null
                }
                val wakeInput = wakeMAC.trim()
                val resolvedWakeMAC = SavedServer.normalizeWakeMac(wakeInput)
                if (wakeInput.isNotEmpty() && resolvedWakeMAC == null) {
                    validationError = "唤醒 MAC 格式应类似 aa:bb:cc:dd:ee:ff。"
                    return null
                }
                SavedServer(
                    id = server.serverId,
                    name = name,
                    hostname = resolvedHost,
                    port = 0,
                    codexPorts = emptyList(),
                    sshPort = resolvedSSHPort,
                    source = "manual",
                    hasCodexServer = false,
                    wakeMAC = resolvedWakeMAC,
                    preferredConnectionMode = "ssh",
                    preferredCodexPort = null,
                    sshPortForwardingEnabled = null,
                    websocketURL = null,
                    rememberedByUser = true,
                )
            }
            ServerConnectionMode.DIRECT_CODEX -> {
                val resolvedHost = host.trim()
                if (resolvedHost.isEmpty()) {
                    validationError = "主机地址不能为空。"
                    return null
                }
                val resolvedCodexPort = codexPort.trim().toIntOrNull()
                if (resolvedCodexPort == null || resolvedCodexPort !in 1..65535) {
                    validationError = "App-server 端口必须是有效数字。"
                    return null
                }
                SavedServer(
                    id = server.serverId,
                    name = name,
                    hostname = resolvedHost,
                    port = resolvedCodexPort,
                    codexPorts = listOf(resolvedCodexPort),
                    sshPort = null,
                    source = "manual",
                    hasCodexServer = true,
                    wakeMAC = null,
                    preferredConnectionMode = "directCodex",
                    preferredCodexPort = resolvedCodexPort,
                    sshPortForwardingEnabled = null,
                    websocketURL = null,
                    rememberedByUser = true,
                )
            }
            ServerConnectionMode.WEBSOCKET -> {
                val rawURL = websocketURL.trim()
                if (!rawURL.startsWith("ws://", ignoreCase = true) && !rawURL.startsWith("wss://", ignoreCase = true)) {
                    validationError = "请输入有效的 ws:// 或 wss:// 地址。"
                    return null
                }
                val uri = runCatching { java.net.URI(rawURL) }.getOrNull()
                if (uri == null || uri.host.isNullOrEmpty()) {
                    validationError = "请输入有效的 ws:// 或 wss:// 地址。"
                    return null
                }
                val resolvedPort = if (uri.port != -1) uri.port else null
                SavedServer(
                    id = server.serverId,
                    name = name,
                    hostname = uri.host,
                    port = resolvedPort ?: 0,
                    codexPorts = if (resolvedPort != null) listOf(resolvedPort) else emptyList(),
                    sshPort = null,
                    source = "manual",
                    hasCodexServer = true,
                    wakeMAC = null,
                    preferredConnectionMode = "directCodex",
                    preferredCodexPort = resolvedPort,
                    sshPortForwardingEnabled = null,
                    websocketURL = rawURL,
                    rememberedByUser = true,
                )
            }
            ServerConnectionMode.SLINGSHOT -> {
                val saved = originalSaved ?: run {
                    validationError = "请移除后重新添加这台电脑。"
                    return null
                }
                saved.copy(
                    name = name,
                    rememberedByUser = true,
                )
            }
        }
    }

    fun persist(saved: SavedServer) {
        val existing = SavedServerStore.load(context).toMutableList()
        existing.removeAll { it.id == saved.id }
        existing.add(saved)
        SavedServerStore.save(context, existing)
        appModel.reconnectController.setMultiClankerAndQuicEnabled(true)
        appModel.reconnectController.syncSavedServers(
            existing.filter { it.rememberedByUser }.map { it.toRecord(context) }
        )
        appModel.store.renameServer(saved.id, saved.name)
    }

    suspend fun reconnect(serverId: String) {
        val servers = SavedServerStore.load(context).map { it.toRecord(context) }
        appModel.reconnectController.setMultiClankerAndQuicEnabled(true)
        appModel.reconnectController.syncSavedServers(servers)
        val result = appModel.reconnectController.reconnectServer(serverId)
        if (result.needsLocalAuthRestore) {
            appModel.restoreStoredLocalAuthState(result.serverId)
            runCatching { appModel.refreshSessions(listOf(result.serverId)) }
        }
        appModel.refreshSnapshot()
    }

    suspend fun connectSlingshotSaved(saved: SavedServer, stepUpToken: String) {
        val websocketURL = saved.websocketURL?.takeIf(::isSettingsSlingshotUrl)
            ?: throw IllegalStateException("Saved server is not a Slingshot connection.")

        val tokens = loadSettingsSlingshotTokens(context)
        appModel.serverBridge.connectRemoteSlingshotUrlServer(
            saved.id,
            saved.name,
            websocketURL,
            tokens.accessToken,
            tokens.accountId,
            stepUpToken,
        )
        appModel.refreshSnapshot()
    }

    suspend fun reconnectSaved(saved: SavedServer) {
        if (saved.websocketURL?.let(::isSettingsSlingshotUrl) != true) {
            reconnect(saved.id)
            return
        }

        connectSlingshotSaved(saved, "")
    }

    val slingshotStepUpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val saved = pendingSlingshotReconnect
        pendingSlingshotReconnect = null
        if (saved == null) {
            return@rememberLauncherForActivityResult
        }
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            validationError = result.data?.getStringExtra(ChatGPTOAuthActivity.EXTRA_ERROR)
                ?: "Remote-control authorization was cancelled."
            isReconnecting = false
            return@rememberLauncherForActivityResult
        }
        val stepUpToken = ChatGPTOAuthActivity.parseRemoteControlStepUpToken(result.data)
        if (stepUpToken == null) {
            validationError = "Remote-control authorization returned incomplete credentials."
            isReconnecting = false
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            isReconnecting = true
            try {
                connectSlingshotSaved(saved, stepUpToken)
                onSave()
            } catch (e: Exception) {
                validationError = e.message
            } finally {
                isReconnecting = false
            }
        }
    }

    fun launchSlingshotStepUp(saved: SavedServer) {
        try {
            pendingSlingshotReconnect = saved
            slingshotStepUpLauncher.launch(
                ChatGPTOAuthActivity.createIntent(
                    context,
                    ChatGPTOAuth.createRemoteControlEnrollmentAttempt(),
                ),
            )
        } catch (e: Exception) {
            pendingSlingshotReconnect = null
            validationError = e.localizedMessage ?: e.message ?: "Unable to authorize remote control."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LitterTheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("编辑设备", color = LitterTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("完成", color = LitterTheme.accent) }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SectionHeader("名称")
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("设备名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 14.sp),
                    )
                }

                item {
                    SectionHeader(connectionMode.formHeader)

                    if (originalSaved?.alleycatNodeId != null || originalSaved?.alleycatAgentWire == "ssh-bridge") {
                        Text(
                            "这台已配对设备使用保存的配对信息。这里只能改显示名称，如需更换配对请移除后重新添加。",
                            color = LitterTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    } else if (server.isLocal) {
                        Text(
                            "本机运行时由 NeCode 自动管理。",
                            color = LitterTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    } else if (connectionMode == ServerConnectionMode.SLINGSHOT) {
                        Text(
                            "这台电脑来自已登录账号的远程连接。这里只能改显示名称，如需更换电脑请移除后重新添加。",
                            color = LitterTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    } else {
                        // Mode selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val modes = listOf(
                                ServerConnectionMode.SSH,
                                ServerConnectionMode.DIRECT_CODEX,
                                ServerConnectionMode.WEBSOCKET,
                            )
                            modes.forEach { mode ->
                                val selected = mode == connectionMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) LitterTheme.accent else Color.Transparent)
                                        .clickable { connectionMode = mode }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        mode.label,
                                        color = if (selected) LitterTheme.onAccentStrong else LitterTheme.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        when (connectionMode) {
                            ServerConnectionMode.SSH -> {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("主机名或 IP") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 14.sp),
                                )
                                OutlinedTextField(
                                    value = sshPort,
                                    onValueChange = { sshPort = it },
                                    label = { Text("SSH 端口") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 14.sp),
                                )
                                OutlinedTextField(
                                    value = wakeMAC,
                                    onValueChange = { wakeMAC = it },
                                    label = { Text("唤醒 MAC（可选）") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 14.sp),
                                )
                            }
                            ServerConnectionMode.DIRECT_CODEX -> {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("主机名或 IP") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 14.sp),
                                )
                                OutlinedTextField(
                                    value = codexPort,
                                    onValueChange = { codexPort = it },
                                    label = { Text("App-server 端口") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 14.sp),
                                )
                            }
                            ServerConnectionMode.WEBSOCKET -> {
                                OutlinedTextField(
                                    value = websocketURL,
                                    onValueChange = { websocketURL = it },
                                    label = { Text("ws://host:port or wss://...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 14.sp),
                                )
                            }
                            else -> Unit
                        }

                        if (connectionMode == ServerConnectionMode.WEBSOCKET) {
                            Text(
                                "能用 SSH 时优先用 SSH。手动运行服务时请自己做好隧道和访问控制，不要直接暴露到公网。",
                                color = LitterTheme.textMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                item {
                    if (isReconnecting) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = LitterTheme.accent, strokeWidth = 2.dp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    validationError = null
                                    val saved = validateAndBuild()
                                    if (saved != null) {
                                        persist(saved)
                                        onSave()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LitterTheme.accent),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("保存", color = LitterTheme.onAccentStrong)
                            }
                            if (server.isLocal || (originalSaved?.alleycatNodeId == null && originalSaved?.alleycatAgentWire != "ssh-bridge")) {
                                Button(
                                    onClick = {
                                        validationError = null
                                        val saved = validateAndBuild()
                                        if (saved != null) {
                                            persist(saved)
                                            // SSH mode requires interactive credentials, mirroring iOS:
                                            // hand off to the parent which will open SSHLoginDialog.
                                            if (connectionMode == ServerConnectionMode.SSH && !server.isLocal) {
                                                onTriggerSshReconnect(saved)
                                                return@Button
                                            }
                                            scope.launch {
                                                isReconnecting = true
                                                try {
                                                    reconnectSaved(saved)
                                                    onSave()
                                                } catch (e: Exception) {
                                                    isReconnecting = false
                                                    if (
                                                        connectionMode == ServerConnectionMode.SLINGSHOT &&
                                                        ChatGPTOAuth.isRemoteControlAuthorizationRequired(e)
                                                    ) {
                                                        launchSlingshotStepUp(saved)
                                                    } else {
                                                        validationError = e.message
                                                    }
                                                } finally {
                                                    if (pendingSlingshotReconnect == null) {
                                                        isReconnecting = false
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LitterTheme.accentStrong),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (server.isLocal) "保存并重启" else "保存并重连",
                                        color = LitterTheme.background,
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    validationError?.let { error ->
        AlertDialog(
            onDismissRequest = { validationError = null },
            title = { Text("设备配置无效") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { validationError = null }) {
                    Text("OK")
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Appearance Sub-Screen (matches iOS AppearanceSettingsView)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceScreen(onBack: () -> Unit) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var textSizeStep by remember { mutableFloatStateOf(com.litter.android.ui.TextSizePrefs.currentStep.toFloat()) }
    var showThemePicker by remember { mutableStateOf<LitterColorThemeType?>(null) }
    var wallpaperError by remember { mutableStateOf<String?>(null) }
    val appearanceMode = LitterThemeManager.appearanceMode
    @Suppress("UNUSED_VARIABLE")
    val wallpaperVersion = WallpaperManager.version
    val wallpaperServer = remember(snapshot) {
        val activeServerId = snapshot?.activeThread?.serverId
        snapshot?.servers?.firstOrNull { it.serverId == activeServerId && it.isConnected }
            ?: snapshot?.servers?.firstOrNull { it.isConnected }
    }
    val wallpaperServerId = wallpaperServer?.serverId
    val serverWallpaperConfig = wallpaperServerId?.let(WallpaperManager::resolvedConfigForServer)
    val wallpaperPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val targetServerId = wallpaperServerId
                if (targetServerId == null) {
                    wallpaperError = "请先连接一个设备，再设置聊天壁纸。"
                    return@launch
                }
                wallpaperError = if (WallpaperManager.setCustomImageFromUri(uri, WallpaperScope.Server(targetServerId))) {
                    null
                } else {
                    "无法保存所选图片作为壁纸。"
                }
            }
        }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
    ) {
        // Nav bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = LitterTheme.accent)
            }
            Spacer(Modifier.weight(1f))
            Text("外观", color = LitterTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Appearance mode
            item { SectionHeader("模式") }
            item {
                AppearanceModePicker(
                    selectedMode = appearanceMode,
                    onSelect = LitterThemeManager::applyAppearanceMode,
                )
            }
            item {
                Text(
                    "跟随系统设置，也可以固定为浅色或深色模式。",
                    color = LitterTheme.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // Font size slider
            item { SectionHeader("字号") }
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("字号", color = LitterTheme.textPrimary, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        val label = com.litter.android.ui.ConversationTextSize.fromStep(textSizeStep.toInt()).label
                        Text(label, color = LitterTheme.textSecondary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("A", color = LitterTheme.textMuted, fontSize = 11.sp)
                        Slider(
                            value = textSizeStep,
                            onValueChange = {
                                textSizeStep = it
                                com.litter.android.ui.TextSizePrefs.setStep(context, it.toInt())
                            },
                            valueRange = 0f..6f, steps = 5,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = LitterTheme.accent, activeTrackColor = LitterTheme.accent),
                        )
                        Text("A", color = LitterTheme.textMuted, fontSize = 18.sp)
                    }
                }
            }
            item {
                Text("可在会话内双指缩放，也可以用这里的滑块调整。", color = LitterTheme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }

            // Wallpaper picker
            item { SectionHeader("聊天壁纸") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, LitterTheme.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    ) {
                        WallpaperBackdrop(serverId = wallpaperServerId, modifier = Modifier.fillMaxSize())
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            wallpaperServer?.let { "作用于当前设备：${it.displayName}" } ?: "请先连接设备",
                            color = LitterTheme.textMuted,
                            fontSize = 11.sp,
                        )
                        TextButton(
                            onClick = { wallpaperPicker.launch("image/*") },
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Text("从相册选择", color = LitterTheme.accent)
                        }
                        val removableServerId = wallpaperServerId
                            ?.takeIf { serverWallpaperConfig?.type?.let { it != WallpaperType.NONE } == true }
                        if (removableServerId != null) {
                            TextButton(
                                onClick = {
                                    WallpaperManager.clearWallpaper(WallpaperScope.Server(removableServerId))
                                    wallpaperError = null
                                },
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) {
                                Text("移除壁纸", color = LitterTheme.danger)
                            }
                        }
                        if (!wallpaperError.isNullOrBlank()) {
                            Text(
                                wallpaperError!!,
                                color = LitterTheme.danger,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // Conversation preview
            item { SectionHeader("预览") }
            item {
                val scale = com.litter.android.ui.ConversationTextSize.fromStep(textSizeStep.toInt()).scale
                val previewFontSize = (14f * scale).sp
                val previewCodeFontSize = (13f * scale).sp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    WallpaperBackdrop(serverId = wallpaperServerId, modifier = Modifier.fillMaxSize())
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // User bubble
                        Text(
                            "帮我看看这个项目为什么启动失败",
                            color = LitterTheme.textPrimary,
                            fontSize = previewFontSize,
                            lineHeight = (previewFontSize.value * 1.3f).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LitterTheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        )
                        // Tool call card
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(LitterTheme.surface, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✓", color = LitterTheme.success, fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("rg 'TODO: fix later' --count", color = LitterTheme.toolCallCommand, fontFamily = BerkeleyMono, fontSize = (previewFontSize.value - 2).sp)
                            Spacer(Modifier.weight(1f))
                            Text("0.3s", color = LitterTheme.textMuted, fontSize = 10.sp)
                        }
                        // Assistant bubble
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "我看到了问题，关键在这里：",
                                color = LitterTheme.textBody,
                                fontSize = previewFontSize,
                                lineHeight = (previewFontSize.value * 1.3f).sp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "PYTHON",
                                    color = LitterTheme.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(LitterTheme.codeBackground, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        "if is_friday():\n    yolo_deploy(skip_tests=True)",
                                        color = LitterTheme.textBody,
                                        fontFamily = LitterTheme.monoFont,
                                        fontSize = previewCodeFontSize,
                                        lineHeight = (previewCodeFontSize.value * 1.35f).sp,
                                    )
                                }
                            }
                            Text(
                                "先改这一处，再重新跑一遍。",
                                color = LitterTheme.textBody,
                                fontSize = previewFontSize,
                                lineHeight = (previewFontSize.value * 1.3f).sp,
                            )
                        }
                        // User reply
                        Text(
                            "那就直接修吧",
                            color = LitterTheme.textPrimary,
                            fontSize = previewFontSize,
                            lineHeight = (previewFontSize.value * 1.3f).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(LitterTheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        )
                    }
                }
            }

            // Light theme picker
            item { SectionHeader("浅色主题") }
            item {
                val selectedLight = LitterThemeManager.lightThemes.firstOrNull {
                    it.slug == LitterThemeManager.lightTheme.slug
                } ?: LitterThemeManager.lightThemes.firstOrNull()
                ThemePickerButton(entry = selectedLight, onClick = { showThemePicker = LitterColorThemeType.LIGHT })
            }

            // Dark theme picker
            item { SectionHeader("深色主题") }
            item {
                val selectedDark = LitterThemeManager.darkThemes.firstOrNull {
                    it.slug == LitterThemeManager.darkTheme.slug
                } ?: LitterThemeManager.darkThemes.firstOrNull()
                ThemePickerButton(entry = selectedDark, onClick = { showThemePicker = LitterColorThemeType.DARK })
            }
        }
    }

    // Theme picker sheet
    showThemePicker?.let { type ->
        ModalBottomSheet(
            onDismissRequest = { showThemePicker = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = LitterTheme.background,
        ) {
            val themes = if (type == LitterColorThemeType.DARK) LitterThemeManager.darkThemes else LitterThemeManager.lightThemes
            val selectedSlug = if (type == LitterColorThemeType.DARK) LitterThemeManager.darkTheme.slug else LitterThemeManager.lightTheme.slug
            ThemePickerContent(
                title = if (type == LitterColorThemeType.DARK) "深色主题" else "浅色主题",
                themes = themes,
                selectedSlug = selectedSlug,
                onSelect = { slug ->
                    if (type == LitterColorThemeType.DARK) {
                        LitterThemeManager.selectDarkTheme(slug)
                    } else {
                        LitterThemeManager.selectLightTheme(slug)
                    }
                    showThemePicker = null
                },
                onDismiss = { showThemePicker = null },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Theme Picker Sheet (matches iOS ThemePickerSheet)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThemePickerContent(
    title: String,
    themes: List<LitterThemeIndexEntry>,
    selectedSlug: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(themes, searchQuery) {
        if (searchQuery.isBlank()) themes
        else themes.filter { it.name.contains(searchQuery, ignoreCase = true) || it.slug.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(16.dp),
    ) {
        // Title + Done
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(title, color = LitterTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("完成", color = LitterTheme.accent) }
        }

        Spacer(Modifier.height(8.dp))

        // Search
        Row(
            Modifier.fillMaxWidth()
                .background(LitterTheme.surface.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .border(1.dp, LitterTheme.border.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = LitterTheme.textMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(LitterTheme.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) Text("搜索主题", color = LitterTheme.textMuted, fontSize = 14.sp)
                    inner()
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Theme list
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Search, null, tint = LitterTheme.textMuted, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(8.dp))
                Text("没有匹配的主题", color = LitterTheme.textPrimary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.slug }) { entry ->
                    val isSelected = entry.slug == selectedSlug
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .background(LitterTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isSelected) LitterTheme.accent.copy(alpha = 0.6f) else LitterTheme.border.copy(alpha = 0.85f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(entry.slug) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        ThemePreviewBadge(entry)
                        Spacer(Modifier.width(10.dp))
                        Text(entry.name, color = LitterTheme.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = LitterTheme.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/** "Aa" badge with background/foreground/accent dot — matches iOS ThemePreviewBadge */
@Composable
private fun ThemePreviewBadge(entry: LitterThemeIndexEntry) {
    val bg = try { Color(android.graphics.Color.parseColor(entry.backgroundHex)) } catch (_: Exception) { LitterTheme.surface }
    val fg = try { Color(android.graphics.Color.parseColor(entry.foregroundHex)) } catch (_: Exception) { LitterTheme.textPrimary }
    val accent = try { Color(android.graphics.Color.parseColor(entry.accentHex)) } catch (_: Exception) { LitterTheme.accent }

    Box {
        Box(
            Modifier.size(width = 28.dp, height = 22.dp)
                .background(bg, RoundedCornerShape(5.dp))
                .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = BerkeleyMono)
        }
        Spacer(
            Modifier.size(6.dp).clip(CircleShape).background(accent)
                .align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun ThemePickerButton(entry: LitterThemeIndexEntry?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        if (entry != null) {
            ThemePreviewBadge(entry)
            Spacer(Modifier.width(10.dp))
            Text(entry.name, color = LitterTheme.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        } else {
            Text("暂无主题", color = LitterTheme.textMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
        Text("⇅", color = LitterTheme.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AppearanceModePicker(
    selectedMode: LitterAppearanceMode,
    onSelect: (LitterAppearanceMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LitterAppearanceMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) LitterTheme.accent else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.displayName,
                    color = if (isSelected) LitterTheme.onAccentStrong else LitterTheme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Pets Sub-Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val connectedServers = remember(snapshot) {
        snapshot?.servers.orEmpty().filter { it.isConnected }
    }
    var selectedServerId by remember(connectedServers) {
        mutableStateOf(
            PetOverlayController.selectedPet?.serverId?.takeIf { id ->
                connectedServers.any { it.serverId == id }
            }
                ?: snapshot?.activeThread?.serverId?.takeIf { id ->
                    connectedServers.any { it.serverId == id }
                }
                ?: connectedServers.firstOrNull()?.serverId
                ?: "",
        )
    }
    var pets by remember(selectedServerId) { mutableStateOf<List<AppPetSummary>>(emptyList()) }
    var loading by remember(selectedServerId) { mutableStateOf(false) }
    var error by remember(selectedServerId) { mutableStateOf<String?>(null) }
    val overlayPermissionGranted = PetOverlayController.canDrawOverlays(context)

    fun refresh() {
        if (selectedServerId.isBlank()) return
        scope.launch {
            loading = true
            error = null
            runCatching { appModel.client.listPets(selectedServerId) }
                .onSuccess { pets = it }
                .onFailure {
                    pets = emptyList()
                    error = it.message ?: "无法加载宠物。"
                }
            loading = false
        }
    }

    LaunchedEffect(selectedServerId) {
        refresh()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = LitterTheme.accent)
                }
                Spacer(Modifier.weight(1f))
                Text("宠物", color = LitterTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { refresh() }, enabled = selectedServerId.isNotBlank() && !loading) {
                    Icon(Icons.Default.Refresh, "刷新", tint = LitterTheme.accent)
                }
            }
        }

        item { SectionHeader("唤醒") }
        item {
            SettingsRow(
                label = "显示宠物",
                subtitle = PetOverlayController.selectedPet?.displayName ?: "未选择宠物",
                icon = { Icon(Icons.Default.Pets, null, tint = LitterTheme.accent, modifier = Modifier.size(18.dp)) },
                trailing = {
                    Switch(
                        checked = PetOverlayController.visible,
                        onCheckedChange = { PetOverlayController.setVisible(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = LitterTheme.accent),
                    )
                },
            )
        }
        item {
            SettingsRow(
                label = "显示在其他应用上方",
                subtitle = if (overlayPermissionGranted) {
                    "已获得悬浮窗权限"
                } else {
                    "需要开启悬浮窗权限"
                },
                icon = { Icon(Icons.Default.Widgets, null, tint = LitterTheme.accent, modifier = Modifier.size(18.dp)) },
                trailing = {
                    Switch(
                        checked = PetOverlayController.overlayEnabled,
                        onCheckedChange = { enabled ->
                            PetOverlayController.setOverlayEnabled(context, enabled)
                            if (enabled && !overlayPermissionGranted) {
                                PetOverlayController.requestOverlayPermission(context)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = LitterTheme.accent),
                    )
                },
                onClick = if (!overlayPermissionGranted) {
                    { PetOverlayController.requestOverlayPermission(context) }
                } else {
                    null
                },
            )
        }

        item { SectionHeader("设备") }
        if (connectedServers.isEmpty()) {
            item { SettingsRow(label = "请先连接设备") }
        } else {
            items(connectedServers, key = { it.serverId }) { server ->
                SettingsRow(
                    label = server.displayName,
                    subtitle = server.connectionModeLabel,
                    trailing = {
                        if (server.serverId == selectedServerId) {
                            Icon(Icons.Default.Check, null, tint = LitterTheme.accentStrong, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = { selectedServerId = server.serverId },
                )
            }
        }

        item { SectionHeader("宠物列表") }
        when {
            selectedServerId.isBlank() -> {
                item { SettingsRow(label = "未选择设备") }
            }
            loading -> {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = LitterTheme.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在加载宠物", color = LitterTheme.textSecondary, fontSize = 13.sp)
                    }
                }
            }
            error != null -> {
                item { SettingsRow(label = "无法加载宠物", subtitle = error) }
            }
            pets.isEmpty() -> {
                item { SettingsRow(label = "没有找到宠物", subtitle = "~/.codex/pets 下没有 hatch-pet 包") }
            }
            else -> {
                items(pets, key = { it.id }) { pet ->
                    val selected = PetOverlayController.selectedPet?.serverId == selectedServerId &&
                        PetOverlayController.selectedPet?.id == pet.id
                    SettingsRow(
                        label = pet.displayName,
                        subtitle = pet.validationError ?: pet.description ?: pet.sourcePath,
                        trailing = {
                            if (PetOverlayController.isLoading && selected) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = LitterTheme.accent, strokeWidth = 2.dp)
                            } else if (selected) {
                                Icon(Icons.Default.Check, null, tint = LitterTheme.accentStrong, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = if (pet.hasValidSpritesheet) {
                            {
                                scope.launch {
                                    PetOverlayController.selectPet(context, appModel, selectedServerId, pet)
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        PetOverlayController.errorMessage?.let { message ->
            item { SettingsRow(label = "宠物加载失败", subtitle = message) }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Experimental Sub-Screen (matches iOS ExperimentalFeaturesView)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExperimentalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val features = remember { LitterFeature.entries }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
    ) {
        // Nav bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = LitterTheme.accent)
            }
            Spacer(Modifier.weight(1f))
            Text("实验功能", color = LitterTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader("功能")
        Column(
            Modifier.fillMaxWidth().background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
        ) {
            features.forEachIndexed { idx, feature ->
                val enabled = ExperimentalFeatures.isEnabled(feature)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(feature.displayName, color = LitterTheme.textPrimary, fontSize = 14.sp)
                        Text(feature.description, color = LitterTheme.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { ExperimentalFeatures.setEnabled(context, feature, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = LitterTheme.accentStrong),
                    )
                }
                if (idx < features.lastIndex) HorizontalDivider(color = LitterTheme.divider)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("实验功能可能不稳定，也可能随版本调整。", color = LitterTheme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Debug Sub-Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
    ) {
        // Nav bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = LitterTheme.accent)
            }
            Spacer(Modifier.weight(1f))
            Text("调试", color = LitterTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader("渲染")
        Column(
            Modifier.fillMaxWidth().background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("禁用 Markdown", color = LitterTheme.textPrimary, fontSize = 14.sp)
                    Text("显示原始等宽文本，不渲染 Markdown", color = LitterTheme.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = DebugSettings.disableMarkdown,
                    onCheckedChange = { DebugSettings.setDisableMarkdown(context, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = LitterTheme.accentStrong),
                )
            }
            HorizontalDivider(color = LitterTheme.divider)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("显示轮次指标", color = LitterTheme.textPrimary, fontSize = 14.sp)
                    Text("在每轮对话上显示耗时和 Token 数", color = LitterTheme.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = DebugSettings.showTurnMetrics,
                    onCheckedChange = { DebugSettings.setShowTurnMetrics(context, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = LitterTheme.accentStrong),
                )
            }
        }

        // ── Recording ──
        Spacer(Modifier.height(12.dp))
        SectionHeader("录制")

        val appModel = LocalAppModel.current
        val scope = rememberCoroutineScope()
        var isRecording by remember { mutableStateOf(MessageRecorder.isRecording(appModel.store)) }
        var recordings by remember { mutableStateOf(MessageRecorder.listRecordings(context)) }

        Column(
            Modifier.fillMaxWidth().background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isRecording) "录制中..." else "消息录制",
                        color = if (isRecording) LitterTheme.danger else LitterTheme.textPrimary,
                        fontSize = 14.sp,
                    )
                    Text("录制服务消息，方便回放调试", color = LitterTheme.textSecondary, fontSize = 11.sp)
                }
                TextButton(onClick = {
                    if (isRecording) {
                        MessageRecorder.stopRecording(context, appModel.store)
                        isRecording = false
                        recordings = MessageRecorder.listRecordings(context)
                    } else {
                        MessageRecorder.startRecording(appModel.store)
                        isRecording = true
                    }
                }) {
                    Text(
                        if (isRecording) "停止" else "开始",
                        color = if (isRecording) LitterTheme.danger else LitterTheme.accent,
                    )
                }
            }

            if (recordings.isNotEmpty()) {
                HorizontalDivider(color = LitterTheme.divider)
                Text("已保存录制", color = LitterTheme.textSecondary, fontSize = 11.sp)
                recordings.forEach { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            file.name,
                            color = LitterTheme.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        val sizeKb = file.length() / 1024
                        Text("${sizeKb}KB", color = LitterTheme.textMuted, fontSize = 10.sp)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            MessageRecorder.deleteRecording(file)
                            recordings = MessageRecorder.listRecordings(context)
                        }) {
                            Text("删除", color = LitterTheme.danger, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("调试功能仅用于开发和测试。", color = LitterTheme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Account Section (inline in top-level, matches iOS SettingsConnectionAccountSection)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AccountSection(server: uniffi.codex_mobile_client.AppServerSnapshot) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiKeyStore = remember(context) { OpenAIApiKeyStore(context.applicationContext) }
    var apiKey by remember { mutableStateOf("") }
    var openAIBaseUrl by remember { mutableStateOf("") }
    var isAuthWorking by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var hasStoredApiKey by remember { mutableStateOf(apiKeyStore.hasStoredKey()) }
    var hasStoredBaseUrl by remember { mutableStateOf(apiKeyStore.hasStoredBaseUrl()) }
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        isAuthWorking = false
        LLog.d("ChatGPTOAuth", "settings auth result", fields = mapOf("resultCode" to result.resultCode))
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val tokens = ChatGPTOAuthActivity.parseResult(result.data)
            if (tokens == null) {
                authError = "ChatGPT 登录返回的凭据不完整。"
                LLog.w("ChatGPTOAuth", "settings auth result missing tokens")
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                isAuthWorking = true
                try {
                    LLog.d("ChatGPTOAuth", "settings loginAccount starting")
                    appModel.client.loginAccount(
                        server.serverId,
                        AppLoginAccountRequest.ChatgptAuthTokens(
                            accessToken = tokens.accessToken,
                            chatgptAccountId = tokens.accountId,
                            chatgptPlanType = tokens.planType,
                        ),
                    )
                    appModel.refreshSnapshot()
                    authError = null
                    LLog.i("ChatGPTOAuth", "settings loginAccount succeeded")
                } catch (e: Exception) {
                    authError = e.localizedMessage ?: e.message
                    LLog.e("ChatGPTOAuth", "settings loginAccount failed", e)
                }
                isAuthWorking = false
            }
        } else {
            authError = result.data?.getStringExtra(ChatGPTOAuthActivity.EXTRA_ERROR)
            authError?.let { LLog.w("ChatGPTOAuth", "settings auth canceled", fields = mapOf("error" to it)) }
        }
    }

    val authColor = when (server.account) {
        is Account.Chatgpt -> LitterTheme.accent
        is Account.ApiKey -> Color(0xFF00AAFF)
        else -> LitterTheme.textMuted
    }
    val authTitle = when (val acct = server.account) {
        is Account.Chatgpt -> acct.email.ifEmpty { "ChatGPT" }
        is Account.ApiKey -> "API Key"
        else -> "未登录"
    }
    val authSubtitle = when (server.account) {
        is Account.Chatgpt -> "ChatGPT 账号"
        is Account.ApiKey -> "OpenAI API Key"
        else -> null
    }
    val allowsLocalEnvApiKey = server.isLocal
    val isChatGPTAccount = server.account is Account.Chatgpt

    androidx.compose.runtime.LaunchedEffect(server.serverId, server.account) {
        hasStoredApiKey = apiKeyStore.hasStoredKey()
        hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
    }

    Column(
        Modifier.fillMaxWidth().background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(10.dp).clip(CircleShape).background(authColor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(authTitle, color = LitterTheme.textPrimary, fontSize = 14.sp)
                authSubtitle?.let { Text(it, color = LitterTheme.textSecondary, fontSize = 11.sp) }
            }
            if (server.isLocal && server.account != null) {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            ChatGPTOAuthTokenStore(context).clear()
                            apiKeyStore.clear()
                            appModel.client.logoutAccount(server.serverId)
                            appModel.restartLocalServer()
                        } catch (_: Exception) {}
                    }
                }) { Text("退出登录", color = LitterTheme.danger, fontSize = 12.sp) }
            }
        }

        if (server.isLocal && hasStoredApiKey) {
            Text(
                "本机 OpenAI API Key 已保存。",
                color = LitterTheme.accent,
                fontSize = 11.sp,
            )
        }

        if (server.isLocal && hasStoredBaseUrl) {
            Text(
                "OpenAI 兼容 Base URL 已保存。",
                color = LitterTheme.accent,
                fontSize = 11.sp,
            )
        }

        // Login button
        if (server.isLocal && !isChatGPTAccount) {
            Button(
                onClick = {
                    try {
                        authError = null
                        isAuthWorking = true
                        authLauncher.launch(
                            ChatGPTOAuthActivity.createIntent(
                                context,
                                ChatGPTOAuth.createLoginAttempt(),
                            ),
                        )
                    } catch (e: Exception) {
                        isAuthWorking = false
                        authError = e.localizedMessage ?: e.message
                    }
                },
                enabled = !isAuthWorking,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            ) {
                if (isAuthWorking) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = LitterTheme.textPrimary); Spacer(Modifier.width(6.dp)) }
                Text("使用 ChatGPT 登录", color = LitterTheme.accent, fontSize = 14.sp)
            }
        }

        if (allowsLocalEnvApiKey) {
            if (hasStoredApiKey) {
                Text(
                    "OpenAI API Key 已保存到本机环境。",
                    color = LitterTheme.textSecondary,
                    fontSize = 11.sp,
                )
            } else if (isChatGPTAccount) {
                Text(
                    "也可以把 API Key 保存到本机 NeCode 环境。",
                    color = LitterTheme.textSecondary,
                    fontSize = 11.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(LitterTheme.accent),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f).background(LitterTheme.codeBackground, RoundedCornerShape(6.dp)).padding(8.dp),
                    decorationBox = { inner -> if (apiKey.isEmpty()) Text("sk-...", color = LitterTheme.textMuted, fontSize = 13.sp); inner() },
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val key = apiKey.trim(); if (key.isEmpty()) return@TextButton
                        scope.launch {
                            isAuthWorking = true
                            try {
                                apiKeyStore.save(key)
                                if (server.account is Account.ApiKey) {
                                    appModel.client.logoutAccount(server.serverId)
                                }
                                appModel.restartLocalServer()
                                hasStoredApiKey = apiKeyStore.hasStoredKey()
                                if (hasStoredApiKey) {
                                    apiKey = ""
                                } else {
                                    authError = "API Key 没有成功保存到本机。"
                                    return@launch
                                }
                                authError = null
                            } catch (e: Exception) {
                                authError = e.message
                            }
                            isAuthWorking = false
                        }
                    },
                    enabled = apiKey.trim().isNotEmpty() && !isAuthWorking,
                ) {
                    Text(
                        if (hasStoredApiKey) "更新 API Key" else "保存 API Key",
                        color = LitterTheme.accent,
                        fontSize = 12.sp,
                    )
                }
            }

            Text(
                if (hasStoredBaseUrl) {
                    "本机 NeCode 服务已保存自定义 OpenAI 兼容端点。"
                } else {
                    "可选：为本地模型配置 OpenAI 兼容端点。"
                },
                color = LitterTheme.textSecondary,
                fontSize = 11.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = openAIBaseUrl,
                    onValueChange = { openAIBaseUrl = it },
                    textStyle = TextStyle(color = LitterTheme.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(LitterTheme.accent),
                    modifier = Modifier.weight(1f).background(LitterTheme.codeBackground, RoundedCornerShape(6.dp)).padding(8.dp),
                    decorationBox = { inner -> if (openAIBaseUrl.isEmpty()) Text("http://host:port/v1", color = LitterTheme.textMuted, fontSize = 13.sp); inner() },
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val normalized = normalizeOpenAIBaseUrl(openAIBaseUrl)
                        if (normalized == null) {
                            authError = "请输入有效的 http 或 https Base URL。"
                        } else {
                            scope.launch {
                                isAuthWorking = true
                                try {
                                    apiKeyStore.saveBaseUrl(normalized)
                                    appModel.restartLocalServer()
                                    hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
                                    if (hasStoredBaseUrl) {
                                        openAIBaseUrl = ""
                                        authError = null
                                    } else {
                                        authError = "Base URL 没有成功保存到本机。"
                                    }
                                } catch (e: Exception) {
                                    authError = e.message
                                } finally {
                                    isAuthWorking = false
                                }
                            }
                        }
                    },
                    enabled = openAIBaseUrl.trim().isNotEmpty() && !isAuthWorking,
                ) {
                    Text(
                        if (hasStoredBaseUrl) "更新" else "保存",
                        color = LitterTheme.accent,
                        fontSize = 12.sp,
                    )
                }
            }
            if (hasStoredBaseUrl) {
                TextButton(
                    onClick = {
                        scope.launch {
                            isAuthWorking = true
                            try {
                                apiKeyStore.clearBaseUrl()
                                appModel.restartLocalServer()
                                hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
                                openAIBaseUrl = ""
                                authError = null
                            } catch (e: Exception) {
                                authError = e.message
                            } finally {
                                isAuthWorking = false
                            }
                        }
                    },
                    enabled = !isAuthWorking,
                ) {
                    Text("清除 Base URL", color = LitterTheme.danger, fontSize = 12.sp)
                }
            }
        } else {
            Text(
                "远程设备需要登录时会自行请求授权。这里的登录和 API Key 只作用于本机。",
                color = LitterTheme.textSecondary,
                fontSize = 11.sp,
            )
        }

        authError?.let { Text(it, color = LitterTheme.danger, fontSize = 11.sp) }
    }
}

private fun normalizeOpenAIBaseUrl(rawValue: String): String? {
    val trimmed = rawValue.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    return trimmed
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shared Components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text.uppercase(), color = LitterTheme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
}

@Composable
private fun SettingsRow(
    label: String, subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(LitterTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = LitterTheme.textPrimary, fontSize = 14.sp)
            subtitle?.let { Text(it, color = LitterTheme.textSecondary, fontSize = 11.sp) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    SettingsRow(
        icon = { Icon(icon, null, tint = LitterTheme.accent, modifier = Modifier.size(20.dp)) },
        label = label,
        trailing = { Icon(Icons.Default.ChevronRight, null, tint = LitterTheme.textMuted, modifier = Modifier.size(16.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun FontRow(name: String, fontFamily: FontFamily, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = LitterTheme.textPrimary, fontSize = 14.sp)
            Text("字体预览文字", color = LitterTheme.textSecondary, fontSize = 13.sp, fontFamily = fontFamily)
        }
        if (isSelected) Icon(Icons.Default.Check, null, tint = LitterTheme.accent, modifier = Modifier.size(18.dp))
    }
}

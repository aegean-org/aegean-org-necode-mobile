package com.litter.android.ui.discovery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.litter.android.core.bridge.UniffiInit
import com.litter.android.state.AlleycatCredentialStore
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.LocalAppModel
import com.litter.android.ui.common.AgentIconView
import com.litter.android.ui.common.BetaBadge
import com.litter.android.ui.common.isBetaAgentName
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.codex_mobile_client.AppAlleycatAgentInfo
import uniffi.codex_mobile_client.AppAlleycatAgentWire
import uniffi.codex_mobile_client.AppAlleycatPairPayload
import uniffi.codex_mobile_client.AlleycatBridge

data class AlleycatConnectedTarget(
    val serverId: String,
    val nodeId: String,
    val displayName: String,
    val params: AppAlleycatPairPayload,
    val agentName: String,
    val agentWire: AppAlleycatAgentWire,
)

private const val LOG_TAG = "AlleycatSheet"

@Composable
fun AlleycatAddServerSheet(
    onDismiss: () -> Unit,
    onConnected: (AlleycatConnectedTarget) -> Unit,
    startScanningOnAppear: Boolean = false,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val credentialStore = remember(context) {
        AlleycatCredentialStore(context.applicationContext)
    }
    val alleycatBridge = remember { AlleycatBridge() }

    var displayName by remember { mutableStateOf("") }
    var parsedParams by remember { mutableStateOf<AppAlleycatPairPayload?>(null) }
    var agents by remember { mutableStateOf<List<AppAlleycatAgentInfo>>(emptyList()) }
    var selectedAgentNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoadingAgents by remember { mutableStateOf(false) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var agentError by remember { mutableStateOf<String?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var pasteJson by remember { mutableStateOf("") }
    var cameraDenied by remember { mutableStateOf(false) }

    fun loadAgents(params: AppAlleycatPairPayload) {
        isLoadingAgents = true
        agentError = null
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    UniffiInit.ensure(context.applicationContext)
                    appModel.serverBridge.listAlleycatAgents(params)
                }
                if (parsedParams?.nodeId == params.nodeId) {
                    val sorted = loaded.sortedWith(
                        compareBy<AppAlleycatAgentInfo>(
                            { alleycatAgentSortRank(it) },
                            { it.displayName.lowercase() },
                        ),
                    )
                    agents = sorted
                    selectedAgentNames = sorted
                        .filter { it.available && !isBetaAgentName(it.name, it.displayName) }
                        .map { it.name }
                        .toSet()
                    isLoadingAgents = false
                }
            } catch (e: Exception) {
                Log.w(LOG_TAG, "listAlleycatAgents failed", e)
                if (parsedParams?.nodeId == params.nodeId) {
                    agents = emptyList()
                    selectedAgentNames = emptySet()
                    isLoadingAgents = false
                    agentError = e.message ?: "无法加载 Agent"
                }
            }
        }
    }

    fun handleScannedPayload(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        try {
            val params = alleycatBridge.parsePairPayload(trimmed)
            parsedParams = params
            displayName = suggestedDisplayName(params)
            agents = emptyList()
            selectedAgentNames = emptySet()
            parseError = null
            agentError = null
            connectError = null
            loadAgents(params)
        } catch (e: Exception) {
            parsedParams = null
            agents = emptyList()
            selectedAgentNames = emptySet()
            parseError = e.message ?: "配对信息无效"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraDenied = false
            showScanner = true
        } else {
            cameraDenied = true
        }
    }

    fun requestCameraAndScan() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> {
                cameraDenied = false
                showScanner = true
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var autoStartTriggered by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(startScanningOnAppear) {
        if (startScanningOnAppear && !autoStartTriggered) {
            autoStartTriggered = true
            requestCameraAndScan()
        }
    }

    fun connect() {
        val params = parsedParams ?: return
        val selectedAgents = agents.filter { it.available && it.name in selectedAgentNames }
        val fallbackAgent = selectedAgents.firstOrNull() ?: return
        val trimmedDisplay = displayName.trim()
        val resolvedName = trimmedDisplay.ifEmpty { suggestedDisplayName(params) }
        val serverId = "alleycat:${params.nodeId}"

        isConnecting = true
        connectError = null

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    UniffiInit.ensure(context.applicationContext)
                    appModel.serverBridge.connectRemoteOverAlleycat(
                        serverId = serverId,
                        displayName = resolvedName,
                        params = params,
                        agentName = fallbackAgent.name,
                        selectedAgentNames = selectedAgents.map { it.name },
                        wire = fallbackAgent.wire,
                    )
                }
                runCatching {
                    credentialStore.saveToken(params.nodeId, params.token)
                }.onFailure {
                    Log.w(LOG_TAG, "Alleycat token save failed", it)
                }
                isConnecting = false
                onConnected(
                    AlleycatConnectedTarget(
                        serverId = result.serverId,
                        nodeId = result.nodeId,
                        displayName = resolvedName,
                        params = params,
                        agentName = result.agentName,
                        agentWire = fallbackAgent.wire,
                    )
                )
            } catch (e: Exception) {
                Log.w(LOG_TAG, "connectRemoteOverAlleycat failed", e)
                isConnecting = false
                connectError = e.message ?: "连接失败"
            }
        }
    }

    val availableAgents = agents.filter { it.available }
    val selectedAgents = agents.filter { it.available && it.name in selectedAgentNames }
    val canConnect = !isConnecting && !isLoadingAgents && parsedParams != null && selectedAgents.isNotEmpty()

    if (showScanner) {
        QrScannerScreen(
            onScanned = { payload ->
                showScanner = false
                handleScannedPayload(payload)
            },
            onCancel = { showScanner = false },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LitterTheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "添加设备",
                color = LitterTheme.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss, enabled = !isConnecting) {
                Text("取消", color = LitterTheme.accent)
            }
        }

        SectionHeader(label = "配对")
        OutlinedButton(
            onClick = ::requestCameraAndScan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = LitterTheme.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (parsedParams == null) "扫描配对二维码" else "重新扫描",
                color = LitterTheme.accent,
            )
        }
        if (cameraDenied) {
            Text(
                text = "需要相机权限才能扫描配对二维码。你也可以在下方粘贴 JSON。",
                color = LitterTheme.warning,
                fontSize = 11.sp,
            )
        }

        DisclosureRow(
            expanded = showPaste,
            label = "粘贴配对 JSON",
            onToggle = { showPaste = !showPaste },
        )
        if (showPaste) {
            OutlinedTextField(
                value = pasteJson,
                onValueChange = { pasteJson = it },
                placeholder = {
                    Text(
                        text = "{\"v\":1,\"node_id\":\"...\",\"token\":\"...\",\"relay\":\"https://...\"}",
                        color = LitterTheme.textMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        clipboardManager.getText()?.text?.let { pasteJson = it }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = LitterTheme.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("从剪贴板粘贴", color = LitterTheme.accent)
                }
                TextButton(
                    onClick = { handleScannedPayload(pasteJson) },
                    enabled = pasteJson.trim().isNotEmpty(),
                ) {
                    Text(
                        text = if (parsedParams == null) "解析 JSON" else "重新解析",
                        color = LitterTheme.accent,
                    )
                }
            }
        }

        parseError?.let { message ->
            Text(message, color = LitterTheme.warning, fontSize = 12.sp)
        }

        val params = parsedParams
        if (params != null) {
            SectionHeader(label = "已扫描设备")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LitterTheme.surface, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PreviewRow("节点", shortNodeId(params.nodeId))
                PreviewRow("协议", "v${params.v.toInt()}")
                params.relay?.takeIf { it.isNotBlank() }?.let {
                    PreviewRow("中继", it)
                }
                params.hostName?.takeIf { it.isNotBlank() }?.let {
                    PreviewRow("主机", it)
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("显示名称（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(label = "Agent", modifier = Modifier.weight(1f))
                if (availableAgents.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selectedAgentNames = if (selectedAgents.size == availableAgents.size) {
                                emptySet()
                            } else {
                                availableAgents.map { it.name }.toSet()
                            }
                        },
                    ) {
                        Text(
                            text = if (selectedAgents.size == availableAgents.size) "全不选" else "全选",
                            color = LitterTheme.accent,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LitterTheme.surface, RoundedCornerShape(8.dp))
                    .padding(vertical = 4.dp),
            ) {
                when {
                    isLoadingAgents -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = LitterTheme.accent,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在加载 Agent", color = LitterTheme.textSecondary, fontSize = 12.sp)
                    }
                    agents.isEmpty() -> Text(
                        text = "这台设备上暂无可用 Agent。",
                        color = LitterTheme.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp),
                    )
                    else -> agents.forEach { agent ->
                        AgentRow(
                            agent = agent,
                            selected = agent.name in selectedAgentNames,
                            onCheckedChange = { checked ->
                                if (agent.available) {
                                    selectedAgentNames = if (checked) {
                                        selectedAgentNames + agent.name
                                    } else {
                                        selectedAgentNames - agent.name
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        agentError?.let { message ->
            Text(message, color = LitterTheme.warning, fontSize = 12.sp)
        }

        Button(
            onClick = ::connect,
            enabled = canConnect,
            colors = ButtonDefaults.buttonColors(
                containerColor = LitterTheme.accent.copy(alpha = 0.18f),
                contentColor = LitterTheme.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LitterTheme.accent,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("连接")
        }

        connectError?.let { message ->
            Text(message, color = LitterTheme.danger, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AgentRow(
    agent: AppAlleycatAgentInfo,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Plain clickable Row instead of TextButton — TextButton injects
    // Material's minimum touch target (~48dp) plus internal content
    // padding, which made each agent row much taller than the actual
    // text content needed and forced the agent list to take far more
    // vertical space than necessary on small screens.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (agent.available) {
                    Modifier.clickable { onCheckedChange(!selected) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        AgentIconView(
            kind = agent.name,
            sizeDp = 22,
            modifier = Modifier.alpha(if (agent.available) 1f else 0.45f),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = agent.displayName,
                    color = if (agent.available) LitterTheme.textPrimary else LitterTheme.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (isBetaAgentName(agent.name, agent.displayName)) {
                    Spacer(Modifier.width(6.dp))
                    BetaBadge()
                }
            }
            Text(
                text = wireLabel(agent.wire),
                color = LitterTheme.textSecondary,
                fontSize = 11.sp,
            )
        }
        if (!agent.available) {
            Text("不可用", color = LitterTheme.textMuted, fontSize = 11.sp)
        } else {
            Checkbox(
                checked = selected,
                onCheckedChange = onCheckedChange,
                enabled = true,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}


@Composable
private fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        color = LitterTheme.textSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 4.dp),
    )
}

@Composable
private fun PreviewRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = LitterTheme.textSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            color = LitterTheme.textPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DisclosureRow(
    expanded: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = (if (expanded) "▾ " else "▸ ") + label,
            color = LitterTheme.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun shortNodeId(raw: String): String =
    if (raw.length <= 16) raw else raw.take(8) + "..." + raw.takeLast(8)

private fun suggestedDisplayName(params: AppAlleycatPairPayload): String =
    params.hostName?.trim()?.takeIf { it.isNotEmpty() }
        ?: "NeCode ${shortNodeId(params.nodeId)}"

private fun wireLabel(wire: AppAlleycatAgentWire): String = when (wire) {
    AppAlleycatAgentWire.WEBSOCKET -> "websocket"
    AppAlleycatAgentWire.JSONL -> "jsonl"
}

private fun alleycatAgentSortRank(agent: AppAlleycatAgentInfo): Int {
    val name = agent.name.trim().lowercase()
    val displayName = agent.displayName.trim().lowercase()
    if (name == "necode" || displayName == "necode") return 0
    if (agent.available && !isBetaAgentName(agent.name, agent.displayName)) return 1
    if (agent.available) return 2
    if (!isBetaAgentName(agent.name, agent.displayName)) return 3
    return 4
}

fun alleycatWireStorageValue(wire: AppAlleycatAgentWire): String = when (wire) {
    AppAlleycatAgentWire.WEBSOCKET -> "websocket"
    AppAlleycatAgentWire.JSONL -> "jsonl"
}

@Composable
private fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    var scanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            barcodeScanner.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                bindCameraUseCases(
                    context = ctx,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    barcodeScanner = barcodeScanner,
                    executor = executor,
                    onResult = { payload ->
                        if (!scanned) {
                            scanned = true
                            onScanned(payload)
                        }
                    },
                )
                previewView
            },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0f),
                        ),
                    ),
                )
                .align(Alignment.TopCenter),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onCancel,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                    modifier = Modifier
                        .background(
                            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                            RoundedCornerShape(50),
                        ),
                ) {
                    Text(
                        text = "取消",
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            InstructionsCard()

            Spacer(modifier = Modifier.weight(1f))

            FramingHint()
        }
    }
}

@Composable
private fun InstructionsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                RoundedCornerShape(14.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "扫码连接 NeCode",
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        StepRow(number = "1", title = "在电脑端运行 NeCode mobile，并生成配对二维码。")
        StepRow(number = "2", title = "用手机摄像头对准这个二维码。")
    }
}

@Composable
private fun StepRow(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(LitterTheme.accent, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                color = androidx.compose.ui.graphics.Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = title,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FramingHint() {
    Text(
        text = "保持稳定，二维码会自动识别。",
        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    executor: java.util.concurrent.ExecutorService,
    onResult: (String) -> Unit,
) {
    val providerFuture = ProcessCameraProvider.getInstance(context)
    providerFuture.addListener({
        val provider = providerFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { proxy ->
            val media = proxy.image
            if (media == null) {
                proxy.close()
                return@setAnalyzer
            }
            val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes
                        .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                        ?.rawValue
                        ?.let(onResult)
                }
                .addOnFailureListener { err ->
                    Log.w(LOG_TAG, "barcode analyze failed", err)
                }
                .addOnCompleteListener { proxy.close() }
        }
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }.onFailure {
            Log.w(LOG_TAG, "bindToLifecycle failed", it)
        }
    }, ContextCompat.getMainExecutor(context))
}

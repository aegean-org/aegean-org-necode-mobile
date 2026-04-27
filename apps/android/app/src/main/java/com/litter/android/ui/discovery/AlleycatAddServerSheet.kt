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
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.litter.android.state.AlleycatCredentialStore
import com.litter.android.state.SavedAlleycatParams
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.LocalAppModel
import com.sigkitten.litter.android.BuildConfig
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.codex_mobile_client.AlleycatBridge
import uniffi.codex_mobile_client.AppAlleycatParams

/**
 * Result handed back to the discovery flow once `connectRemoteOverAlleycat`
 * has brought up both the QUIC tunnel AND the loopback Codex WebSocket — the
 * server is fully connected and just needs to be persisted + navigated to.
 */
data class AlleycatConnectedTarget(
    val serverId: String,
    val connectedHost: String,
    val displayName: String,
    val params: AppAlleycatParams,
)

private const val LOG_TAG = "AlleycatSheet"

@Composable
fun AlleycatAddServerSheet(
    onDismiss: () -> Unit,
    onConnected: (AlleycatConnectedTarget) -> Unit,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialStore = remember(context) {
        AlleycatCredentialStore(context.applicationContext)
    }
    val alleycatBridge = remember { AlleycatBridge() }

    var hostOverride by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var parsedParams by remember { mutableStateOf<AppAlleycatParams?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showHostOverride by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var pasteJson by remember { mutableStateOf("") }
    var cameraDenied by remember { mutableStateOf(false) }

    fun handleScannedPayload(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        try {
            val params = alleycatBridge.parsePairPayload(trimmed)
            parsedParams = params
            parseError = null
            connectError = null
            // Auto-expand override row only when the QR didn't carry candidates.
            showHostOverride = params.hostCandidates.isEmpty()
        } catch (e: Exception) {
            parsedParams = null
            parseError = e.message ?: "Invalid pairing payload"
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

    fun connect() {
        val params = parsedParams ?: return
        val override = hostOverride.trim()
        val hosts = buildList {
            if (override.isNotEmpty()) add(override)
            for (candidate in params.hostCandidates) {
                if (candidate !in this) add(candidate)
            }
        }
        if (hosts.isEmpty()) return

        val trimmedDisplay = displayName.trim()
        val provisionalName = trimmedDisplay.ifEmpty { "alleycat" }
        val serverId = "alleycat:${hosts[0].lowercase()}:${params.udpPort.toInt()}"

        isConnecting = true
        connectError = null

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    appModel.serverBridge.connectRemoteOverAlleycat(
                        serverId = serverId,
                        displayName = provisionalName,
                        hosts = hosts,
                        params = params,
                    )
                }
                val resolvedName = trimmedDisplay.ifEmpty {
                    "${result.connectedHost} (alleycat)"
                }
                runCatching {
                    credentialStore.save(
                        result.connectedHost,
                        SavedAlleycatParams.fromParams(params),
                    )
                }.onFailure {
                    Log.w(LOG_TAG, "alleycat credential save failed", it)
                }
                isConnecting = false
                onConnected(
                    AlleycatConnectedTarget(
                        serverId = result.serverId,
                        connectedHost = result.connectedHost,
                        displayName = resolvedName,
                        params = params,
                    )
                )
            } catch (e: Exception) {
                Log.w(LOG_TAG, "connectRemoteOverAlleycat failed", e)
                isConnecting = false
                connectError = e.message ?: "Unable to connect"
            }
        }
    }

    val canConnect = !isConnecting && parsedParams != null && (
        hostOverride.trim().isNotEmpty() || (parsedParams?.hostCandidates?.isNotEmpty() == true)
    )

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
                text = "Add via Alleycat",
                color = LitterTheme.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss, enabled = !isConnecting) {
                Text("Cancel", color = LitterTheme.accent)
            }
        }

        // --- Pairing section ----------------------------------------------------
        SectionHeader(label = "Pairing")
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
                text = if (parsedParams == null) "Scan Pairing QR" else "Rescan QR",
                color = LitterTheme.accent,
            )
        }
        if (cameraDenied) {
            Text(
                text = "Camera permission is required to scan a pairing QR. " +
                    "Grant access in system Settings, or paste the JSON below in debug builds.",
                color = LitterTheme.warning,
                fontSize = 11.sp,
            )
        }

        if (BuildConfig.DEBUG) {
            DisclosureRow(
                expanded = showPaste,
                label = "Paste JSON (debug)",
                onToggle = { showPaste = !showPaste },
            )
            if (showPaste) {
                OutlinedTextField(
                    value = pasteJson,
                    onValueChange = { pasteJson = it },
                    placeholder = {
                        Text(
                            text = "{\"protocolVersion\":1,\"udpPort\":...,\"certFingerprint\":\"...\",\"token\":\"...\"}",
                            color = LitterTheme.textMuted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { handleScannedPayload(pasteJson) },
                    enabled = pasteJson.trim().isNotEmpty(),
                ) {
                    Text("Parse JSON", color = LitterTheme.accent)
                }
            }
        }

        parseError?.let { message ->
            Text(message, color = LitterTheme.warning, fontSize = 12.sp)
        }

        val params = parsedParams
        if (params != null) {
            // --- Preview card ---------------------------------------------------
            SectionHeader(label = "Scanned Params")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LitterTheme.surface, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PreviewRow("udp port", params.udpPort.toInt().toString())
                PreviewRow("protocol", "v${params.protocolVersion.toInt()}")
                PreviewRow("fingerprint", shortFingerprint(params.certFingerprint))
                if (params.hostCandidates.isNotEmpty()) {
                    PreviewRow("hosts", params.hostCandidates.joinToString(", "))
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("display name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // --- Override host --------------------------------------------------
            val hasCandidates = params.hostCandidates.isNotEmpty()
            SectionHeader(label = if (hasCandidates) "Connect" else "Relay Host")
            if (hasCandidates) {
                DisclosureRow(
                    expanded = showHostOverride,
                    label = "Override host (optional)",
                    onToggle = { showHostOverride = !showHostOverride },
                )
            }
            if (!hasCandidates || showHostOverride) {
                OutlinedTextField(
                    value = hostOverride,
                    onValueChange = { hostOverride = it },
                    label = { Text(if (hasCandidates) "hostname or IP this device can reach" else "relay.example.com or 100.64.0.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = if (hasCandidates) {
                    "The phone races the candidates above and uses the first that connects. " +
                        "Override only if none of them are reachable from here."
                } else {
                    "This QR doesn't carry host candidates — enter a hostname or IP " +
                        "that this device can reach."
                },
                color = LitterTheme.textMuted,
                fontSize = 11.sp,
            )

            // --- Connect button ------------------------------------------------
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
                Text("Connect")
            }
        }

        connectError?.let { message ->
            Text(message, color = LitterTheme.danger, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        color = LitterTheme.textSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
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

private fun shortFingerprint(raw: String): String {
    val stripped = raw.replace(":", "")
    return if (stripped.length <= 12) stripped else stripped.substring(0, 12) + "..."
}

// MARK: -- QR scanner ---------------------------------------------------------

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
            .background(LitterTheme.background),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .align(Alignment.Center),
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Point the camera at the alleycat QR code",
                color = LitterTheme.textPrimary,
                fontSize = 12.sp,
            )
            TextButton(onClick = onCancel) {
                Text("Cancel", color = LitterTheme.accent)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = LitterTheme.textPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
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

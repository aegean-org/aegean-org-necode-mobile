package com.litter.android.ui.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litter.android.ui.LitterTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.codex_mobile_client.DesktopConnectBridge
import uniffi.codex_mobile_client.DesktopDirectoryListing
import uniffi.codex_mobile_client.DesktopProjectFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopDirectoryPickerSheet(
    bridge: DesktopConnectBridge, hostId: String, hostName: String,
    onDismiss: () -> Unit, onSelected: (DesktopProjectFolder) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var requestedPath by remember(hostId) { mutableStateOf<String?>(null) }
    var revision by remember(hostId) { mutableStateOf(0) }
    var listing by remember(hostId) { mutableStateOf<DesktopDirectoryListing?>(null) }
    var pathInput by remember(hostId) { mutableStateOf("") }
    var loading by remember(hostId) { mutableStateOf(true) }
    var working by remember(hostId) { mutableStateOf(false) }
    var failure by remember(hostId) { mutableStateOf<String?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var folderFailure by remember { mutableStateOf<String?>(null) }
    val busy = loading || working

    LaunchedEffect(hostId, requestedPath, revision) {
        loading = true
        failure = null
        try {
            val result = withContext(Dispatchers.IO) { bridge.browseDesktopDirectory(hostId, requestedPath) }
            listing = result
            pathInput = result.path
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure = e.localizedMessage ?: "无法读取目录" }
        finally { loading = false }
    }
    fun browse(path: String) {
        if (busy) return
        loading = true
        requestedPath = path
        revision++
    }

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LitterTheme.background, dragHandle = null) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("添加项目", color = LitterTheme.textPrimary, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, "关闭", tint = LitterTheme.textSecondary)
                }
            }
            Text("选择“$hostName”上的文件夹", color = LitterTheme.textSecondary, fontSize = 13.sp)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = pathInput, onValueChange = { pathInput = it },
                    label = { Text("电脑上的目录路径") }, singleLine = true, enabled = !busy,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { browse(pathInput.trim()) }, enabled = !busy && pathInput.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text("前往", color = LitterTheme.accentStrong) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { listing?.parent?.let(::browse) }, enabled = !busy && listing?.parent != null,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("上级目录")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { folderName = ""; folderFailure = null; showNewFolder = true },
                    enabled = !busy && listing != null, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Text("新建文件夹")
                }
            }
            if ((listing?.roots?.size ?: 0) > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(listing?.roots ?: emptyList(), key = { it }) { root ->
                        TextButton(onClick = { browse(root) }, enabled = !busy) { Text(root) }
                    }
                }
            }
            if (failure != null) {
                Text(failure!!, color = LitterTheme.danger, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { loading = true; revision++ }, enabled = !busy) { Text("重试") }
            }
            if (loading) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                DesktopCenterLoading("正在读取文件夹…")
            }
            else LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (listing?.directories?.isEmpty() == true) item {
                    Text("此目录没有子文件夹，可直接使用当前目录", color = LitterTheme.textSecondary,
                        fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp))
                }
                items(listing?.directories ?: emptyList(), key = { it.path }) { folder ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { browse(folder.path) }
                        .heightIn(min = 52.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = LitterTheme.accentStrong, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(folder.name, color = LitterTheme.textPrimary, fontSize = 15.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Button(onClick = {
                val path = listing?.path ?: return@Button
                working = true
                failure = null
                scope.launch {
                    try { onSelected(withContext(Dispatchers.IO) { bridge.addDesktopProject(hostId, path) }) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failure = e.localizedMessage ?: "无法添加项目" }
                    finally { working = false }
                }
            }, enabled = !busy && listing != null && pathInput == listing?.path && failure == null,
                colors = ButtonDefaults.buttonColors(containerColor = LitterTheme.accentStrong, contentColor = LitterTheme.onAccentStrong),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp, bottom = 12.dp)) {
                Text(if (working) "正在添加…" else "使用此目录")
            }
        }
    }
    if (showNewFolder) AlertDialog(onDismissRequest = { if (!working) showNewFolder = false },
        containerColor = LitterTheme.background, title = { Text("新建文件夹") },
        text = {
            Column {
                OutlinedTextField(folderName, { folderName = it }, singleLine = true, enabled = !working,
                    label = { Text("文件夹名称") })
                folderFailure?.let { Text(it, color = LitterTheme.danger, fontSize = 13.sp) }
            }
        }, confirmButton = {
            TextButton(onClick = {
                val parent = listing?.path ?: return@TextButton
                working = true
                folderFailure = null
                scope.launch {
                    try {
                        val path = withContext(Dispatchers.IO) { bridge.createDesktopDirectory(hostId, parent, folderName.trim()) }
                        showNewFolder = false
                        loading = true
                        requestedPath = path
                        revision++
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { folderFailure = e.localizedMessage ?: "无法创建文件夹" }
                    finally { working = false }
                }
            }, enabled = !working && folderName.isNotBlank()) { Text(if (working) "正在创建…" else "创建") }
        }, dismissButton = { TextButton(onClick = { showNewFolder = false }, enabled = !working) { Text("取消") } })
}

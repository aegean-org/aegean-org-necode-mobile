package com.litter.android.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.litter.android.ui.LitterTheme
import com.litter.android.ui.desktop.DesktopCenterLoading
import com.litter.android.ui.desktop.DesktopHostPicker
import com.litter.android.ui.desktop.desktopConnectErrorMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.litter.android.state.DesktopConnectCredentialStore
import com.litter.android.state.NeSessionSecureStore
import com.litter.android.state.DesktopConnectCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.DesktopConnectBridge
import uniffi.codex_mobile_client.DesktopHost
import uniffi.codex_mobile_client.MobileDevice

@Composable
fun DesktopConnectSheet(
    bridgeState: MutableState<DesktopConnectBridge?>,
    onDismiss: () -> Unit,
    onConnected: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val passwordFocus = remember { FocusRequester() }
    val store = remember(context) { DesktopConnectCredentialStore(context.applicationContext) }
    val settings = remember(context) { context.getSharedPreferences("desktop_connect_settings", 0) }
    var ucUrl by remember { mutableStateOf(settings.getString("uc_url", "https://uc.inoteexpress.com/") ?: "https://uc.inoteexpress.com/") }
    var connectUrl by remember { mutableStateOf(settings.getString("connect_url", "https://necode.inoteexpress.com/") ?: "https://necode.inoteexpress.com/") }
    var bridge by bridgeState
    var accountId by remember { mutableStateOf<String?>(null) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var mobile by remember { mutableStateOf<MobileDevice?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var hosts by remember { mutableStateOf<List<DesktopHost>>(emptyList()) }
    var hostsLoaded by remember { mutableStateOf(false) }
    var connectingHostId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    var captcha by remember { mutableStateOf<uniffi.codex_mobile_client.NeCaptchaConfig?>(null) }

    fun configuredBridge(): DesktopConnectBridge {
        bridge?.let { return it }
        val created = DesktopConnectBridge(ucUrl = ucUrl, connectUrl = connectUrl, store = NeSessionSecureStore(context.applicationContext))
        check(settings.edit().putString("uc_url", ucUrl).putString("connect_url", connectUrl).commit()) {
            "无法保存服务地址"
        }
        bridge = created
        return created
    }

    fun saveIdentity() {
        val account = checkNotNull(accountId)
        val loginSession = checkNotNull(sessionId)
        val device = checkNotNull(mobile)
        store.save(DesktopConnectCredentials(account, loginSession, device.id, device.secret))
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = desktopConnectErrorMessage(e) }
            finally { busy = false }
        }
    }

    fun login(proof: String? = null) {
        act {
            val client = configuredBridge()
            if (proof == null) {
                val config = client.loginConfig()
                if (config.enabled == "1") { captcha = config; return@act }
            }
            val result = client.loginWithPassword(username, password, proof)
            val previous = store.load()
            if (previous != null && previous.accountId != result.accountId) store.setPreferredDesktopId(null)
            val device = client.registerMobile(result.sessionId,
                previous?.mobileId?.takeIf { previous.accountId == result.accountId },
                previous?.mobileSecret?.takeIf { previous.accountId == result.accountId })
            sessionId = result.sessionId
            accountId = result.accountId
            mobile = device
            saveIdentity()
            password = ""
            hosts = client.listDesktops(result.sessionId)
            hostsLoaded = true
            val active = client.recoverDesktop(result.sessionId, device, store.preferredDesktopId(), false)
            if (active != null && active.healthy) {
                store.setPreferredDesktopId(active.desktopId)
                val name = hosts.firstOrNull { it.id == active.desktopId }?.name ?: active.desktopId
                onConnected(name)
                return@act
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            if (ucUrl.isBlank() || connectUrl.isBlank()) return@LaunchedEffect
            val saved = store.load()
            busy = true
            val client = configuredBridge()
            val login = client.currentLogin()
            if (login == null) {
                if (saved != null) error = "登录已升级，请重新登录 NE 账号"
                return@LaunchedEffect
            }
            val account = client.verifyLogin(login.sessionId)
            val device = client.registerMobile(login.sessionId,
                saved?.mobileId?.takeIf { saved.accountId == account },
                saved?.mobileSecret?.takeIf { saved.accountId == account })
            accountId = account
            sessionId = login.sessionId
            mobile = device
            saveIdentity()
            hosts = client.listDesktops(login.sessionId)
            hostsLoaded = true
            val active = client.recoverDesktop(login.sessionId, device, store.preferredDesktopId(), false)
            if (active != null && active.healthy) {
                store.setPreferredDesktopId(active.desktopId)
                val name = hosts.firstOrNull { it.id == active.desktopId }?.name ?: active.desktopId
                onConnected(name)
                return@LaunchedEffect
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = desktopConnectErrorMessage(e) }
        finally { busy = false; restoring = false }
    }

    captcha?.let { config ->
        NeCaptchaDialog(config, ucUrl, onResult = { proof -> captcha = null; login(proof) },
            onCancel = { captcha = null; error = "验证已取消，请重试" },
            onError = { message -> captcha = null; error = message })
    }

    if (sessionId != null && !restoring) {
        DesktopHostPicker(
            hosts = hosts, activeHostId = null, healthy = true,
            busy = busy, connectingHostId = connectingHostId, error = error,
            showLogout = true, accountId = accountId, hostsLoaded = hostsLoaded,
            onRefresh = { act { hosts = configuredBridge().listDesktops(checkNotNull(sessionId)); hostsLoaded = true } },
            onDismiss = onDismiss,
            onSelect = { host ->
                connectingHostId = host.id
                act {
                    try {
                        configuredBridge().connectDesktop(checkNotNull(sessionId), checkNotNull(mobile), host.id)
                        store.setPreferredDesktopId(host.id)
                        onConnected(host.name)
                    } finally { connectingHostId = null }
                }
            },
            onRemove = { host ->
                act {
                    val client = configuredBridge()
                    client.removeDesktop(checkNotNull(sessionId), host.id)
                    hosts = hosts.filterNot { it.id == host.id }
                    if (store.preferredDesktopId() == host.id) store.setPreferredDesktopId(null)
                    hosts = client.listDesktops(checkNotNull(sessionId))
                    hostsLoaded = true
                }
            },
            onLogout = {
                act {
                    configuredBridge().logout(checkNotNull(sessionId))
                    store.clear()
                    sessionId = null
                    accountId = null
                    mobile = null
                    hosts = emptyList()
                    hostsLoaded = false
                }
            },
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.82f).dp),
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.82f).dp)
            .imePadding()
            .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (sessionId == null) "连接桌面端" else "选择电脑",
                    color = LitterTheme.textPrimary, fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold)
                if (sessionId == null) Text("登录 NE 账号，找到你的电脑",
                    color = LitterTheme.textSecondary, fontSize = 13.sp)
            }
            if (sessionId != null) IconButton(onClick = { act { hosts = configuredBridge().listDesktops(checkNotNull(sessionId)) } },
                enabled = !busy, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新电脑", tint = LitterTheme.textSecondary)
            }
            IconButton(onClick = onDismiss, enabled = !busy && !restoring,
                modifier = Modifier.size(48.dp).background(LitterTheme.surface, CircleShape)) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = LitterTheme.textSecondary)
            }
        }
        error?.let {
            Text(it, color = LitterTheme.danger, fontSize = 13.sp, maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(LitterTheme.surface).padding(12.dp))
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (restoring) {
                DesktopCenterLoading("正在恢复 NE 账号…", Modifier.fillMaxWidth())
            } else if (sessionId == null) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (busy) DesktopCenterLoading("正在登录 NE 账号…", Modifier.fillMaxWidth())
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LitterTheme.accentStrong, unfocusedBorderColor = LitterTheme.border,
                        focusedContainerColor = LitterTheme.surface, unfocusedContainerColor = LitterTheme.surface,
                        focusedTextColor = LitterTheme.textPrimary, unfocusedTextColor = LitterTheme.textPrimary)
                    Text("手机或邮箱", color = LitterTheme.textSecondary, fontSize = 13.sp)
                    OutlinedTextField(username, { username = it }, placeholder = { Text("输入 NE 账号") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                        singleLine = true, enabled = !busy, shape = RoundedCornerShape(12.dp),
                        colors = fieldColors, modifier = Modifier.fillMaxWidth())
                    Text("密码", color = LitterTheme.textSecondary, fontSize = 13.sp)
                    OutlinedTextField(password, { password = it }, placeholder = { Text("输入密码") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (!busy && username.isNotBlank() && password.isNotEmpty()) { keyboard?.hide(); login() }
                        }),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = !busy) {
                                Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                    tint = LitterTheme.textSecondary)
                            }
                        },
                        singleLine = true, enabled = !busy, shape = RoundedCornerShape(12.dp), colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus))
                }
            }
            if (sessionId == null && !restoring) {
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (showAdvanced) "收起服务地址" else "高级设置 · 服务地址", color = LitterTheme.textSecondary,
                        fontSize = 12.sp)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null,
                        tint = LitterTheme.textSecondary, modifier = Modifier.size(16.dp))
                }
                if (showAdvanced) {
                    OutlinedTextField(ucUrl, { ucUrl = it }, label = { Text("NE 用户中心 HTTPS 地址") },
                        enabled = bridge == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(connectUrl, { connectUrl = it }, label = { Text("Connect HTTPS 地址") },
                        enabled = bridge == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (sessionId == null && !restoring) {
            Button(onClick = { keyboard?.hide(); login() }, enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = LitterTheme.accentStrong,
                    contentColor = LitterTheme.onAccentStrong, disabledContainerColor = LitterTheme.surfaceLight,
                    disabledContentColor = LitterTheme.textSecondary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (busy) "正在登录…" else "登录并查找电脑", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

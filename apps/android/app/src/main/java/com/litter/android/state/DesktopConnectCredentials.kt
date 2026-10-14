package com.litter.android.state

import android.content.Context
import org.json.JSONObject

data class DesktopConnectCredentials(
    val accountId: String,
    val sessionId: String,
    val mobileId: String?,
    val mobileSecret: String?,
)

class DesktopConnectCredentialStore(context: Context) {
    private val prefs = openEncryptedPrefsOrReset(context, "desktop_connect_credentials")

    fun load(): DesktopConnectCredentials? {
        val raw = prefs.getString("identity", null) ?: return null
        val value = JSONObject(raw)
        return DesktopConnectCredentials(
            accountId = value.getString("accountId"),
            sessionId = value.optString("sessionId"),
            mobileId = value.optString("mobileId").takeIf { it.isNotEmpty() },
            mobileSecret = value.optString("mobileSecret").takeIf { it.isNotEmpty() },
        )
    }

    fun save(value: DesktopConnectCredentials) {
        val data = JSONObject()
            .put("accountId", value.accountId)
            .put("sessionId", value.sessionId)
            .put("mobileId", value.mobileId)
            .put("mobileSecret", value.mobileSecret)
        check(prefs.edit().putString("identity", data.toString()).commit()) { "无法保存桌面连接凭据" }
    }

    fun preferredDesktopId(): String? = prefs.getString("preferredDesktopId", null)

    fun setPreferredDesktopId(id: String?) {
        check(prefs.edit().apply {
            if (id == null) remove("preferredDesktopId") else putString("preferredDesktopId", id)
        }.commit()) { "无法保存最近连接的电脑" }
    }

    fun pendingPrompt(accountId: String, desktopId: String, sessionId: String): String? =
        prefs.getString("prompt.$accountId.$desktopId.$sessionId", null)

    @Synchronized
    fun savePendingPrompt(accountId: String, desktopId: String, sessionId: String, journal: String?) {
        val key = "prompt.$accountId.$desktopId.$sessionId"
        check(prefs.edit().apply {
            if (journal == null) remove(key) else putString(key, journal)
        }.commit()) { "无法保存发送核对记录，消息未发送" }
    }

    @Synchronized
    fun clearPendingPrompt(accountId: String, desktopId: String, sessionId: String, expected: String): Boolean {
        if (pendingPrompt(accountId, desktopId, sessionId) != expected) return false
        savePendingPrompt(accountId, desktopId, sessionId, null)
        return true
    }

    fun clear() {
        check(prefs.edit().remove("identity").remove("preferredDesktopId").commit()) { "无法清除桌面连接凭据" }
    }
}

package com.litter.android.state

import android.content.Context
import uniffi.codex_mobile_client.ClientException
import uniffi.codex_mobile_client.NeSessionStore

/** No automatic reset: unreadable refresh state must not be mistaken for a logged-out account. */
class NeSessionSecureStore(context: Context) : NeSessionStore {
    private val prefs = buildEncryptedPrefs(context, "ne_auth_v2")

    override fun readSession(): String? = synchronized(lock) {
        if (storageFailed) throw ClientException.Rpc("安全凭据存储失败，请重启应用后重新登录")
        try { prefs.getString("session", null) }
        catch (e: Exception) { throw ClientException.Rpc("无法读取安全登录凭据，请检查系统密钥存储") }
    }

    override fun replaceSession(expected: String?, value: String?): Boolean = synchronized(lock) {
        if (storageFailed) throw ClientException.Rpc("安全凭据存储失败，请重启应用后重新登录")
        try {
            if (prefs.getString("session", null) != expected) return@synchronized false
            val editor = prefs.edit()
            if (value == null) editor.remove("session") else editor.putString("session", value)
            if (!editor.commit()) {
                // SharedPreferences changes memory even when disk commit fails. Restore the durable
                // value before Rust retries saving its delivered pair; never treat memory as durable.
                val rollback = prefs.edit()
                if (expected == null) rollback.remove("session") else rollback.putString("session", expected)
                if (!rollback.commit()) storageFailed = true
                throw ClientException.Rpc("无法保存登录凭据，请检查存储空间后重试")
            }
            true
        } catch (e: ClientException) { throw e }
        catch (e: Exception) { throw ClientException.Rpc("无法更新安全登录凭据，请检查系统密钥存储") }
    }

    private companion object { val lock = Any(); var storageFailed = false }
}

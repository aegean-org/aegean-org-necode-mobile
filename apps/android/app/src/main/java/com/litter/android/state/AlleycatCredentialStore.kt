package com.litter.android.state

import android.content.Context

class AlleycatCredentialStore(context: Context) {
    private val prefs = openEncryptedPrefsOrReset(context, PREFS_NAME)

    fun loadToken(nodeId: String): String? =
        prefs.getString(key(nodeId), null)?.takeIf { it.isNotBlank() }

    fun saveToken(nodeId: String, token: String) {
        prefs.edit().putString(key(nodeId), token).apply()
    }

    fun deleteToken(nodeId: String) {
        prefs.edit().remove(key(nodeId)).apply()
    }

    private fun key(nodeId: String): String =
        nodeId.trim().lowercase()

    companion object {
        private const val PREFS_NAME = "alleycat_credentials"
    }
}

package com.litter.android.state

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import uniffi.codex_mobile_client.AlleycatCredentialRecord
import uniffi.codex_mobile_client.AppAlleycatParams

/**
 * Encoded form of `AppAlleycatParams` for EncryptedSharedPreferences storage.
 *
 * Tokens and certificate fingerprints are per-launch by design — this cache
 * exists so a saved alleycat server remembers `(host, udpPort)` and can
 * re-prompt the user for a fresh QR after a relay restart, not so it can
 * reconnect silently.
 */
data class SavedAlleycatParams(
    val protocolVersion: UInt,
    val udpPort: UShort,
    val certFingerprint: String,
    val token: String,
    val hostCandidates: List<String>,
) {
    fun toCredentialRecord(): AlleycatCredentialRecord = AlleycatCredentialRecord(
        protocolVersion = protocolVersion,
        udpPort = udpPort,
        certFingerprint = certFingerprint,
        token = token,
        hostCandidates = hostCandidates,
    )

    fun toJson(): String = JSONObject().apply {
        put("protocolVersion", protocolVersion.toLong())
        put("udpPort", udpPort.toInt())
        put("certFingerprint", certFingerprint)
        put("token", token)
        put("hostCandidates", JSONArray(hostCandidates))
    }.toString()

    companion object {
        fun fromParams(params: AppAlleycatParams): SavedAlleycatParams = SavedAlleycatParams(
            protocolVersion = params.protocolVersion,
            udpPort = params.udpPort,
            certFingerprint = params.certFingerprint,
            token = params.token,
            hostCandidates = params.hostCandidates,
        )

        fun fromJson(raw: String): SavedAlleycatParams {
            val obj = JSONObject(raw)
            val candidates = mutableListOf<String>()
            obj.optJSONArray("hostCandidates")?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(candidates::add)
                }
            }
            return SavedAlleycatParams(
                protocolVersion = obj.getLong("protocolVersion").toUInt(),
                udpPort = obj.getInt("udpPort").toUShort(),
                certFingerprint = obj.getString("certFingerprint"),
                token = obj.getString("token"),
                hostCandidates = candidates,
            )
        }
    }
}

class AlleycatCredentialStore(context: Context) {
    private val prefs = openEncryptedPrefsOrReset(context, PREFS_NAME)

    fun load(host: String, udpPort: UShort): SavedAlleycatParams? {
        val raw = prefs.getString(key(host, udpPort), null) ?: return null
        return try {
            SavedAlleycatParams.fromJson(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun save(host: String, params: SavedAlleycatParams) {
        prefs.edit().putString(key(host, params.udpPort), params.toJson()).apply()
    }

    fun delete(host: String, udpPort: UShort) {
        prefs.edit().remove(key(host, udpPort)).apply()
    }

    private fun key(host: String, udpPort: UShort): String =
        "${normalizedHost(host)}:${udpPort.toInt()}"

    private fun normalizedHost(host: String): String {
        val trimmed = host.trim().trimStart('[').trimEnd(']').replace("%25", "%")
        val withoutScope = if (!trimmed.contains(":")) {
            trimmed.substringBefore('%')
        } else {
            trimmed
        }
        return withoutScope.lowercase()
    }

    companion object {
        private const val PREFS_NAME = "litter_alleycat_credentials"
    }
}

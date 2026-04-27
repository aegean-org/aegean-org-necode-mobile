package com.litter.android.state

import android.util.Log
import com.litter.android.ui.ExperimentalFeatures
import com.litter.android.ui.LitterFeature
import uniffi.codex_mobile_client.AlleycatCredentialProvider
import uniffi.codex_mobile_client.AlleycatCredentialRecord

/// Returns null when the `ALLEYCAT` experimental feature is off — the Rust
/// planner treats "no cached creds" as "skip alleycat plan," so this acts
/// as a kill-switch for auto-reconnect of saved alleycat servers without
/// needing a Rust-side flag.
class KotlinAlleycatCredentialProvider(
    private val store: AlleycatCredentialStore,
) : AlleycatCredentialProvider {
    override fun loadCredential(host: String, udpPort: UShort): AlleycatCredentialRecord? {
        if (!ExperimentalFeatures.isEnabled(LitterFeature.ALLEYCAT)) return null
        return try {
            store.load(host, udpPort)?.toCredentialRecord()
        } catch (e: Exception) {
            Log.w(
                "ALLEYCAT",
                "credential lookup failed host=$host udpPort=${udpPort.toInt()}",
                e,
            )
            null
        }
    }
}

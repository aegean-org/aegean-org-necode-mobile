package com.litter.android.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.codex_mobile_client.InputModality
import uniffi.codex_mobile_client.ModelInfo
import uniffi.codex_mobile_client.ReasoningEffort

class ModelSelectionSupportTest {
    @Test
    fun asrModelsAreHiddenFromChatModelPicker() {
        val asr = model(id = "qwen-asr", displayName = "Qwen ASR")

        assertTrue(asr.isAsrModelOption())
        assertFalse(asr.isVisibleModelOption())
    }

    @Test
    fun qwenAsrModelIsHiddenFromChatModelPicker() {
        val asr = model(id = "qwen3-asr-1.7b", displayName = "Qwen3 ASR")

        assertTrue(asr.isAsrModelOption())
        assertFalse(asr.isVisibleModelOption())
    }

    @Test
    fun preferredAsrModelFindsSpeechToTextModel() {
        val chat = model(id = "qwen3.5", displayName = "Qwen 3.5")
        val asr = model(
            id = "speech",
            model = "ne-asr-large",
            displayName = "Speech to Text",
            description = "Transcription only",
        )

        assertSame(asr, listOf(chat, asr).preferredAsrModel())
        assertEquals("ne-asr-large", asr.asrRequestModelName())
    }

    @Test
    fun ampPickerOnlyShowsSupportedModes() {
        assertTrue(model(id = "amp/smart", runtime = "amp").isVisibleModelOption())
        assertFalse(model(id = "amp/experimental", runtime = "amp").isVisibleModelOption())
    }

    private fun model(
        id: String,
        model: String = id,
        displayName: String = id,
        description: String = "",
        runtime: String = "necode",
    ) = ModelInfo(
        id = id,
        model = model,
        upgrade = null,
        upgradeModel = null,
        upgradeCopy = null,
        modelLink = null,
        migrationMarkdown = null,
        availabilityNuxMessage = null,
        displayName = displayName,
        description = description,
        hidden = false,
        supportedReasoningEfforts = emptyList(),
        defaultReasoningEffort = ReasoningEffort.NONE,
        inputModalities = listOf(InputModality.TEXT),
        supportsPersonality = false,
        isDefault = false,
        agentRuntimeKind = runtime,
    )
}

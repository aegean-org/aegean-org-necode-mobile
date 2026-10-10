package com.litter.android

import com.litter.android.ui.desktop.DesktopComposerDraft
import com.litter.android.ui.desktop.DesktopModel
import com.litter.android.ui.desktop.desktopComposerShowsStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopComposerControlTest {
    @Test fun inFlightJournalDoesNotShowConfirmationWarning() {
        val draft = DesktopComposerDraft()
        draft.submitting.value = true
        draft.pending.value = true
        assertFalse(draft.needsPromptConfirmation)
    }

    @Test fun unresolvedPromptRemainsActionableAfterSubmission() {
        val draft = DesktopComposerDraft()
        draft.pending.value = true
        assertTrue(draft.needsPromptConfirmation)
        draft.pending.value = false
        assertFalse(draft.needsPromptConfirmation)
    }

    @Test fun submissionAndAdmissionGapBothExposeStop() {
        assertTrue(desktopComposerShowsStop(submitting = true, awaiting = false, processing = false, stopping = false))
        assertTrue(desktopComposerShowsStop(submitting = false, awaiting = true, processing = false, stopping = false))
    }

    @Test fun existingRunsAndInFlightStopsKeepStopVisible() {
        assertTrue(desktopComposerShowsStop(submitting = false, awaiting = false, processing = true, stopping = false))
        assertTrue(desktopComposerShowsStop(submitting = false, awaiting = false, processing = false, stopping = true))
        assertFalse(desktopComposerShowsStop(submitting = false, awaiting = false, processing = false, stopping = false))
    }

    @Test fun newSessionModelResultSurvivesComposerRebindingBeforeFirstPrompt() {
        val drafts = mutableMapOf<String, DesktopComposerDraft>()
        val loadingTarget = drafts.getOrPut("host|new-session") { DesktopComposerDraft() }
        loadingTarget.modelsLoading.value = true
        val rebound = drafts.getOrPut("host|new-session") { DesktopComposerDraft() }
        val defaultModel = DesktopModel("ne", "deepseek-v4.1-flash", "DeepSeek V4.1 Flash")
        loadingTarget.modelOptions.value = listOf(defaultModel)
        loadingTarget.model.value = defaultModel
        loadingTarget.modelsLoading.value = false
        assertSame(loadingTarget, rebound)
        assertEquals(listOf(defaultModel), rebound.modelOptions.value)
        assertEquals(defaultModel, rebound.model.value)
        assertFalse(rebound.modelsLoading.value)
        assertEquals("", rebound.text.value)
    }

    @Test fun modelSelectionAndLoadFailureBelongToTheirSessionDraft() {
        val first = DesktopComposerDraft()
        val next = DesktopComposerDraft()
        val chosen = DesktopModel("ne", "chosen", "手动选择的模型")
        first.modelOptions.value = listOf(chosen)
        first.model.value = chosen
        next.modelError.value = "模型加载失败"
        assertEquals(chosen, first.model.value)
        assertNull(first.modelError.value)
        assertTrue(next.modelOptions.value.isEmpty())
        assertNull(next.model.value)
        assertEquals("模型加载失败", next.modelError.value)
    }

}

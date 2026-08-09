package com.magicitengineer.batterynotifierandroidwearapp.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.magicitengineer.batterynotifierandroidwearapp.application.settings.ThresholdDraftCommandSink

internal data class ThresholdDraftEditorSnapshot(
    val isEditing: Boolean = false,
    val draftPercent: Int? = null,
    val pendingSavePercent: Int? = null,
)

/** Owns the newest accepted editor value and exposes a saved-state snapshot. */
internal class ThresholdDraftEditorState(
    initialSnapshot: ThresholdDraftEditorSnapshot = ThresholdDraftEditorSnapshot(),
    private val commandSink: ThresholdDraftCommandSink,
) {
    var isEditing by mutableStateOf(
        initialSnapshot.isEditing && initialSnapshot.draftPercent != null
    )
        private set

    var draftPercent: Int? by mutableStateOf(
        initialSnapshot.draftPercent?.coerceIn(THRESHOLD_PERCENT_RANGE)
    )
        private set

    var pendingSavePercent: Int? by mutableStateOf(
        initialSnapshot.pendingSavePercent?.coerceIn(THRESHOLD_PERCENT_RANGE)
    )
        private set

    fun beginEditing(initialThresholdPercent: Int) {
        draftPercent = initialThresholdPercent.coerceIn(THRESHOLD_PERCENT_RANGE)
        isEditing = true
    }

    fun stepBy(direction: Int) {
        val currentDraft = draftPercent ?: return
        val step = direction.compareTo(0)
        val updatedDraft = (currentDraft + step).coerceIn(THRESHOLD_PERCENT_RANGE)
        if (updatedDraft != currentDraft) {
            draftPercent = updatedDraft
            commandSink.persistDraft(updatedDraft)
        }
    }

    fun save() {
        draftPercent?.let { finalDraft ->
            pendingSavePercent = finalDraft
            commandSink.save(finalDraft)
        }
        finishEditing()
    }

    fun replayPendingSave() {
        pendingSavePercent?.let(commandSink::save)
    }

    /** Re-persists passive restored input without converting it into a Save request. */
    fun reconcileRestoredDraft() {
        if (isEditing && pendingSavePercent == null) {
            draftPercent?.let(commandSink::persistDraft)
        }
    }

    fun acknowledgePendingSave() {
        pendingSavePercent = null
    }

    fun cancel() {
        commandSink.cancel()
        finishEditing()
    }

    fun snapshot(): ThresholdDraftEditorSnapshot = ThresholdDraftEditorSnapshot(
        isEditing = isEditing,
        draftPercent = draftPercent,
        pendingSavePercent = pendingSavePercent,
    )

    private fun finishEditing() {
        isEditing = false
        draftPercent = null
    }
}

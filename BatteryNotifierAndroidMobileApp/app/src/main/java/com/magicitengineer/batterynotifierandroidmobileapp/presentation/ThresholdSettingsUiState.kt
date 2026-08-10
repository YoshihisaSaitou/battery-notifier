package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSaveResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinationResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncItemOutcome

enum class ThresholdSaveUiState {
    IDLE,
    UNSAVED,
    SAVING,
    SAVED,
    SAVED_SYNC_PENDING,
    SAVED_SYNC_FAILED,
    INVALID_THRESHOLD,
}

fun thresholdDraftSaveUiState(
    savedThreshold: Int,
    draftThreshold: Int,
): ThresholdSaveUiState = if (draftThreshold == savedThreshold) {
    ThresholdSaveUiState.IDLE
} else {
    ThresholdSaveUiState.UNSAVED
}

data class ThresholdSaveUiResult(
    val state: ThresholdSaveUiState,
    val currentAtOrBelowThreshold: Boolean = false,
)

fun ThresholdSaveResult.toUiResult(): ThresholdSaveUiResult = when (this) {
    is ThresholdSaveResult.Rejected -> ThresholdSaveUiResult(
        state = ThresholdSaveUiState.INVALID_THRESHOLD,
    )

    is ThresholdSaveResult.Saved -> ThresholdSaveUiResult(
        state = when (val coordination = syncResult) {
            is MobileSyncCoordinationResult.Skipped -> ThresholdSaveUiState.SAVED_SYNC_PENDING
            is MobileSyncCoordinationResult.Sent -> when (coordination.batchResult.stateOutcome) {
                is SyncItemOutcome.Accepted -> ThresholdSaveUiState.SAVED
                SyncItemOutcome.NotPending -> ThresholdSaveUiState.SAVED_SYNC_PENDING
                is SyncItemOutcome.Rejected -> ThresholdSaveUiState.SAVED_SYNC_FAILED
            }
        },
        currentAtOrBelowThreshold = currentAtOrBelowThreshold,
    )
}

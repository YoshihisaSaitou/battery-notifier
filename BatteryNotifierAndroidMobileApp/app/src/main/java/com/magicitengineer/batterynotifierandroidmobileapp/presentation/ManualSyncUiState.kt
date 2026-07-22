package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinationResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncItemOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncSkipReason

enum class ManualSyncUiState {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILED,
    BATTERY_UNAVAILABLE,
    INVALID_BATTERY_INPUT,
}

fun MobileSyncCoordinationResult.toManualSyncUiState(): ManualSyncUiState = when (this) {
    is MobileSyncCoordinationResult.Sent -> when (batchResult.stateOutcome) {
        is SyncItemOutcome.Accepted -> ManualSyncUiState.SUCCESS
        SyncItemOutcome.NotPending,
        is SyncItemOutcome.Rejected -> ManualSyncUiState.FAILED
    }

    is MobileSyncCoordinationResult.Skipped -> when (reason) {
        SyncSkipReason.BATTERY_UNAVAILABLE -> ManualSyncUiState.BATTERY_UNAVAILABLE
        SyncSkipReason.INVALID_BATTERY_INPUT -> ManualSyncUiState.INVALID_BATTERY_INPUT
        SyncSkipReason.UNCHANGED_BATTERY_INPUT -> ManualSyncUiState.SUCCESS
    }
}

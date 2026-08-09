package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import com.magicitengineer.batterynotifierandroidmobileapp.R
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinationResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncItemOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncSkipReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

enum class ManualSyncUiState {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILED,
    BATTERY_UNAVAILABLE,
    INVALID_BATTERY_INPUT,
}

data class ManualSyncUiPresentation(
    val actionEnabled: Boolean,
    val showProgress: Boolean,
    val actionLabelResource: Int,
    val statusMessageResource: Int,
)

fun ManualSyncUiState.toPresentation(): ManualSyncUiPresentation =
    ManualSyncUiPresentation(
        actionEnabled = this != ManualSyncUiState.SYNCING,
        showProgress = this == ManualSyncUiState.SYNCING,
        actionLabelResource = if (this == ManualSyncUiState.SYNCING) {
            R.string.mobile_sync_in_progress
        } else {
            R.string.mobile_sync_action
        },
        statusMessageResource = when (this) {
            ManualSyncUiState.IDLE -> R.string.mobile_sync_idle
            ManualSyncUiState.SYNCING -> R.string.mobile_sync_in_progress_status
            ManualSyncUiState.SUCCESS -> R.string.mobile_sync_success
            ManualSyncUiState.FAILED -> R.string.mobile_sync_failed
            ManualSyncUiState.BATTERY_UNAVAILABLE -> R.string.mobile_sync_battery_unavailable
            ManualSyncUiState.INVALID_BATTERY_INPUT -> R.string.mobile_sync_invalid_battery
        },
    )

class ManualSyncProgressRetainer(
    private val minimumVisibleMillis: Long = MINIMUM_MANUAL_SYNC_PROGRESS_MILLIS,
    private val monotonicMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val waitMillis: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun retainUntilResult(
        operation: suspend () -> ManualSyncUiState,
    ): ManualSyncUiState {
        val startedAtMillis = monotonicMillis()
        val result = try {
            operation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            ManualSyncUiState.FAILED
        }
        val remainingMillis = minimumVisibleMillis - (monotonicMillis() - startedAtMillis)
        if (remainingMillis > 0L) {
            waitMillis(remainingMillis)
        }
        return result
    }
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

const val MINIMUM_MANUAL_SYNC_PROGRESS_MILLIS = 500L
private const val NANOS_PER_MILLISECOND = 1_000_000L

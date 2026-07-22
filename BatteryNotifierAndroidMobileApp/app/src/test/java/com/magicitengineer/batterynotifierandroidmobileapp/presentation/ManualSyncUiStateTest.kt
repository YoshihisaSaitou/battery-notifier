package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncBatchResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinationResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncTrigger
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncItemOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncSkipReason
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncFailureClassification
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualSyncUiStateTest {
    @Test
    fun acceptedCurrentStateMapsToSuccess() {
        assertEquals(
            ManualSyncUiState.SUCCESS,
            sent(SyncItemOutcome.Accepted(sequence = 8L)).toManualSyncUiState(),
        )
    }

    @Test
    fun rejectedOrMissingCurrentStateMapsToFailure() {
        val outcomes = listOf(
            SyncItemOutcome.Rejected(
                sequence = 8L,
                classification = SyncFailureClassification.TASK_FAILURE,
            ),
            SyncItemOutcome.NotPending,
        )

        outcomes.forEach { outcome ->
            assertEquals(
                ManualSyncUiState.FAILED,
                sent(outcome).toManualSyncUiState(),
            )
        }
    }

    @Test
    fun skippedBatteryReasonsRemainDistinct() {
        val cases = mapOf(
            SyncSkipReason.BATTERY_UNAVAILABLE to ManualSyncUiState.BATTERY_UNAVAILABLE,
            SyncSkipReason.INVALID_BATTERY_INPUT to ManualSyncUiState.INVALID_BATTERY_INPUT,
        )

        cases.forEach { (reason, expected) ->
            val result = MobileSyncCoordinationResult.Skipped(
                trigger = MobileSyncTrigger.MANUAL_SYNC,
                reason = reason,
            )
            assertEquals(expected, result.toManualSyncUiState())
        }
    }

    private fun sent(stateOutcome: SyncItemOutcome): MobileSyncCoordinationResult.Sent =
        MobileSyncCoordinationResult.Sent(
            trigger = MobileSyncTrigger.MANUAL_SYNC,
            refreshResult = null,
            batchResult = MobileSyncBatchResult(
                stateOutcome = stateOutcome,
                eventOutcome = SyncItemOutcome.NotPending,
                persistedState = MobilePersistentState(),
            ),
        )
}

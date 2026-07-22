package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSaveRejectionReason
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSaveResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSettingsState
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncBatchResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinationResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncTrigger
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncItemOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncFailureClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdSettingsUiStateTest {
    @Test
    fun acceptedSyncMapsToSavedAndPreservesBelowFlag() {
        val result = saved(SyncItemOutcome.Accepted(sequence = 2L), below = true).toUiResult()

        assertEquals(ThresholdSaveUiState.SAVED, result.state)
        assertTrue(result.currentAtOrBelowThreshold)
    }

    @Test
    fun missingAndRejectedSyncOutcomesRemainDistinct() {
        val pending = saved(SyncItemOutcome.NotPending).toUiResult()
        val failed = saved(
            SyncItemOutcome.Rejected(
                sequence = 2L,
                classification = SyncFailureClassification.TASK_FAILURE,
            )
        ).toUiResult()

        assertEquals(ThresholdSaveUiState.SAVED_SYNC_PENDING, pending.state)
        assertEquals(ThresholdSaveUiState.SAVED_SYNC_FAILED, failed.state)
        assertFalse(failed.currentAtOrBelowThreshold)
    }

    @Test
    fun invalidThresholdMapsToInvalidUiState() {
        val result = ThresholdSaveResult.Rejected(
            ThresholdSaveRejectionReason.OUT_OF_RANGE
        ).toUiResult()

        assertEquals(ThresholdSaveUiState.INVALID_THRESHOLD, result.state)
    }

    private fun saved(
        stateOutcome: SyncItemOutcome,
        below: Boolean = false,
    ): ThresholdSaveResult.Saved = ThresholdSaveResult.Saved(
        state = ThresholdSettingsState(thresholdPercent = 20, currentLevelPercent = 18),
        currentAtOrBelowThreshold = below,
        syncResult = MobileSyncCoordinationResult.Sent(
            trigger = MobileSyncTrigger.SETTINGS_CHANGED,
            refreshResult = null,
            batchResult = MobileSyncBatchResult(
                stateOutcome = stateOutcome,
                eventOutcome = SyncItemOutcome.NotPending,
                persistedState = MobilePersistentState(),
            ),
        ),
    )
}

package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import com.magicitengineer.batterynotifierandroidmobileapp.R
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncBatchResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinationResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncTrigger
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncItemOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.SyncSkipReason
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncFailureClassification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualSyncUiStateTest {
    @Test
    fun syncingPresentationIsDisabledAndUnambiguous() {
        val presentation = ManualSyncUiState.SYNCING.toPresentation()

        assertFalse(presentation.actionEnabled)
        assertTrue(presentation.showProgress)
        assertEquals(R.string.mobile_sync_in_progress, presentation.actionLabelResource)
        assertEquals(
            R.string.mobile_sync_in_progress_status,
            presentation.statusMessageResource,
        )
    }

    @Test
    fun nonSyncingPresentationsRestoreTheManualActionWithoutProgress() {
        ManualSyncUiState.entries
            .filterNot { it == ManualSyncUiState.SYNCING }
            .forEach { state ->
                val presentation = state.toPresentation()

                assertTrue("$state should enable the action", presentation.actionEnabled)
                assertFalse("$state should not show progress", presentation.showProgress)
                assertEquals(R.string.mobile_sync_action, presentation.actionLabelResource)
            }
    }

    @Test
    fun fastResultRetainsProgressForTheRemainingMinimumInterval() = runBlocking {
        var nowMillis = 1_000L
        val waits = mutableListOf<Long>()
        val retainer = ManualSyncProgressRetainer(
            monotonicMillis = { nowMillis },
            waitMillis = { waits += it },
        )

        val result = retainer.retainUntilResult {
            nowMillis += 120L
            ManualSyncUiState.SUCCESS
        }

        assertEquals(ManualSyncUiState.SUCCESS, result)
        assertEquals(listOf(380L), waits)
    }

    @Test
    fun slowResultIsNotDelayedAfterTheMinimumInterval() = runBlocking {
        var nowMillis = 1_000L
        val waits = mutableListOf<Long>()
        val retainer = ManualSyncProgressRetainer(
            monotonicMillis = { nowMillis },
            waitMillis = { waits += it },
        )

        val result = retainer.retainUntilResult {
            nowMillis += MINIMUM_MANUAL_SYNC_PROGRESS_MILLIS + 1L
            ManualSyncUiState.FAILED
        }

        assertEquals(ManualSyncUiState.FAILED, result)
        assertTrue(waits.isEmpty())
    }

    @Test
    fun operationalExceptionBecomesFailureAfterTheRemainingMinimumInterval() = runBlocking {
        var nowMillis = 1_000L
        val waits = mutableListOf<Long>()
        val retainer = ManualSyncProgressRetainer(
            monotonicMillis = { nowMillis },
            waitMillis = { waits += it },
        )

        val result = retainer.retainUntilResult {
            nowMillis += 75L
            throw IllegalStateException("test failure")
        }

        assertEquals(ManualSyncUiState.FAILED, result)
        assertEquals(listOf(425L), waits)
    }

    @Test
    fun cancellationIsPropagatedWithoutWaiting() = runBlocking {
        val waits = mutableListOf<Long>()
        val retainer = ManualSyncProgressRetainer(
            monotonicMillis = { 1_000L },
            waitMillis = { waits += it },
        )

        try {
            retainer.retainUntilResult {
                throw CancellationException("screen left")
            }
            throw AssertionError("CancellationException should be propagated")
        } catch (_: CancellationException) {
            assertTrue(waits.isEmpty())
        }
    }

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

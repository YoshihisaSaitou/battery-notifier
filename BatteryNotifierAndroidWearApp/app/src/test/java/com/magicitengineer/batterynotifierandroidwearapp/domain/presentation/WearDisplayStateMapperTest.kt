package com.magicitengineer.batterynotifierandroidwearapp.domain.presentation

import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeResult
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeResultCode
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeStatus
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedPhoneState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearDisplayStateMapperTest {
    @Test
    fun freshnessBoundariesMatchTheSpecification() {
        val state = state(receivedAt = 1_000_000L)

        assertEquals(Freshness.FRESH, map(state, 1_120_000L).freshness)
        assertEquals(Freshness.DELAYED, map(state, 1_120_001L).freshness)
        assertEquals(Freshness.DELAYED, map(state, 1_300_000L).freshness)
        assertEquals(Freshness.STALE, map(state, 1_300_001L).freshness)
    }

    @Test
    fun noDataDoesNotInventAPhoneValue() {
        val display = WearDisplayStateMapper.map(WearPersistentState(), 1_000L)

        assertEquals(Freshness.NO_DATA, display.freshness)
        assertEquals(null, display.levelPercent)
        assertEquals(null, display.receivedAtEpochMillis)
    }

    @Test
    fun wallClockRollbackDoesNotKeepDataFreshIndefinitely() {
        val display = map(state(receivedAt = 1_000_000L), 999_999L)

        assertEquals(Freshness.STALE, display.freshness)
        assertEquals(null, display.ageMinutes)
        assertTrue(display.clockWarning)
    }

    @Test
    fun timelineCarriesFreshDelayedAndStaleValidityWithoutAnotherDataItem() {
        val timeline = WearDisplayTimelineMapper.map(state(receivedAt = 1_000_000L))

        assertEquals(
            listOf(Freshness.STALE, Freshness.FRESH, Freshness.DELAYED, Freshness.STALE),
            timeline.entries.map { it.displayState.freshness },
        )
        assertEquals(1_120_001L, timeline.entries[1].endEpochMillisExclusive)
        assertEquals(1_300_001L, timeline.entries[2].endEpochMillisExclusive)
        assertEquals(Freshness.STALE, timeline.defaultState.freshness)
        assertTrue(timeline.entries.all { it.displayState.ageMinutes == null })
        assertEquals(null, timeline.defaultState.ageMinutes)
    }

    @Test
    fun relativeAgeRemainsTruthfulAtSixTenAndSixtyMinutes() {
        val state = state(receivedAt = 1_000_000L)

        assertEquals(6L, map(state, 1_360_000L).ageMinutes)
        assertEquals(10L, map(state, 1_600_000L).ageMinutes)
        assertEquals(60L, map(state, 4_600_000L).ageMinutes)
    }

    @Test
    fun unsupportedSchemaKeepsTheLastValidValueAndShowsIncompatibleState() {
        val display = WearDisplayStateMapper.map(
            state(receivedAt = 1_000_000L).copy(lastReceiveError = "unsupported_schema"),
            1_010_000L,
        )

        assertEquals(68, display.levelPercent)
        assertTrue(display.incompatibleSchema)
    }

    @Test
    fun notificationOutcomesAreExposedAsUserVisibleDisplayState() {
        val permissionDenied = WearDisplayStateMapper.map(
            WearPersistentState(
                notificationDisposition = NotificationDisposition.PERMISSION_DENIED
            ),
            1_000L,
        )
        val failed = WearDisplayStateMapper.map(
            WearPersistentState(
                notificationDisposition = NotificationDisposition.RESERVED_FAILED
            ),
            1_000L,
        )
        val exhausted = WearDisplayStateMapper.map(
            WearPersistentState(
                notificationDisposition = NotificationDisposition.FAILED_EXHAUSTED
            ),
            1_000L,
        )

        assertTrue(permissionDenied.notificationPermissionMissing)
        assertTrue(failed.notificationDeliveryFailed)
        assertTrue(failed.notificationRetryAvailable)
        assertTrue(exhausted.notificationDeliveryFailed)
        assertTrue(exhausted.notificationRetryExhausted)
    }

    @Test
    fun conflictDisplaysThePhoneConfirmedEffectiveThresholdBeforeStateCatchesUp() {
        val conflicted = state(receivedAt = 1_000_000L).copy(
            thresholdChangeStatus = ThresholdChangeStatus.CONFLICT,
            thresholdChangeResult = ThresholdChangeResult(
                requestId = REQUEST_ID,
                resultCode = ThresholdChangeResultCode.CONFLICT,
                effectiveThresholdPercent = 25,
                phoneStateSequence = 2L,
            ),
        )

        assertEquals(
            25,
            WearThresholdDisplayPolicy.effectiveThresholdPercent(conflicted),
        )
        assertEquals(20, conflicted.lastPhoneState?.thresholdPercent)
    }

    private fun state(receivedAt: Long) = WearPersistentState(
        lastPhoneState = ReceivedPhoneState(
            schemaVersion = 1,
            sequence = 1L,
            levelPercent = 68,
            isCharging = false,
            capturedAtEpochMillis = 900_000L,
            thresholdPercent = 20,
            monitoringEnabled = true,
            sentAtEpochMillis = 950_000L,
        ),
        phoneStateReceivedAtEpochMillis = receivedAt,
    )

    private fun map(state: WearPersistentState, now: Long) =
        WearDisplayStateMapper.map(state, now)

    private companion object {
        const val REQUEST_ID = "550e8400-e29b-41d4-a716-446655440022"
    }
}

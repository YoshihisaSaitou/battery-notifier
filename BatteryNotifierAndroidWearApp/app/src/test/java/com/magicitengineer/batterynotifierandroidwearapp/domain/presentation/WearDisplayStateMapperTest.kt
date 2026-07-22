package com.magicitengineer.batterynotifierandroidwearapp.domain.presentation

import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
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

        assertTrue(permissionDenied.notificationPermissionMissing)
        assertTrue(failed.notificationDeliveryFailed)
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
}

package com.magicitengineer.batterynotifierandroidmobileapp.application.battery

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.BatteryProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadingSource
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncDeliveryUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentBatteryStateRefresherTest {
    @Test
    fun availableReadingIsPersistedWithCandidateEventId() = runBlocking {
        val reading = BatteryReading(67, false, 1_000L)
        val repository = RecordingRepository()
        val refresher = CurrentBatteryStateRefresher(
            source = BatteryReadingSource { BatteryReadResult.Available(reading) },
            repository = repository,
            eventIdFactory = EventIdFactory { EVENT_ID },
        )

        val result = refresher.refresh()

        assertTrue(result is BatteryRefreshResult.Refreshed)
        assertEquals(reading, repository.processedReading)
        assertEquals(EVENT_ID, repository.candidateEventId)
        assertEquals(1L, (result as BatteryRefreshResult.Refreshed).processingResult.snapshot.sequence)
    }

    @Test
    fun invalidReadingIncrementsDiagnosticsAndDoesNotProcessState() = runBlocking {
        val repository = RecordingRepository()
        val refresher = CurrentBatteryStateRefresher(
            source = BatteryReadingSource { BatteryReadResult.Invalid },
            repository = repository,
            eventIdFactory = EventIdFactory { error("must not create an event ID") },
        )

        val result = refresher.refresh()

        assertSame(BatteryRefreshResult.InvalidInput, result)
        assertEquals(1, repository.invalidInputCalls)
        assertEquals(null, repository.processedReading)
    }

    @Test
    fun unavailableReadingDoesNotMutateDiagnosticsOrState() = runBlocking {
        val repository = RecordingRepository()
        val refresher = CurrentBatteryStateRefresher(
            source = BatteryReadingSource { BatteryReadResult.Unavailable },
            repository = repository,
            eventIdFactory = EventIdFactory { error("must not create an event ID") },
        )

        val result = refresher.refresh()

        assertSame(BatteryRefreshResult.Unavailable, result)
        assertEquals(0, repository.invalidInputCalls)
        assertEquals(null, repository.processedReading)
    }

    private class RecordingRepository : MobileStateRepository {
        private val mutableState = MutableStateFlow(MobilePersistentState())
        override val state: Flow<MobilePersistentState> = mutableState
        var processedReading: BatteryReading? = null
        var candidateEventId: String? = null
        var invalidInputCalls = 0

        override suspend fun processBatteryReading(
            reading: BatteryReading,
            candidateEventId: String,
        ): BatteryProcessingResult {
            processedReading = reading
            this.candidateEventId = candidateEventId
            val snapshot = BatterySnapshot(
                levelPercent = reading.levelPercent,
                isCharging = reading.isCharging,
                capturedAtEpochMillis = reading.capturedAtEpochMillis,
                sequence = 1L,
            )
            val next = mutableState.value.copy(
                lastSnapshot = snapshot,
                sequence = 1L,
                pendingStateSequence = 1L,
            )
            mutableState.value = next
            return BatteryProcessingResult(next, snapshot, null)
        }

        override suspend fun recordInvalidInput(): MobilePersistentState {
            invalidInputCalls += 1
            return mutableState.value
        }

        override suspend fun updateAlertRule(rule: AlertRule): MobilePersistentState =
            error("not used")

        override suspend fun markMobileNotified(eventId: String): MobilePersistentState =
            error("not used")

        override suspend fun applySyncDelivery(update: SyncDeliveryUpdate): MobilePersistentState =
            error("not used")
    }

    private companion object {
        const val EVENT_ID = "550e8400-e29b-41d4-a716-446655440010"
    }
}

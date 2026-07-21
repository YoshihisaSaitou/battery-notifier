package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateSanitizer
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.ProtoMobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.DataLayerPutResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.EpochMillisClock
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.MobileSyncGateway
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.PhoneStateSync
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncFailureClassification
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileDataLayerSenderTest {
    @Test
    fun acceptedStateAndEventClearBothOutboxesAfterMappingCurrentValues() = runBlocking {
        val repository = createRepositoryWithCrossing()
        val gateway = RecordingGateway()
        val sender = MobileDataLayerSender(
            repository = repository,
            gateway = gateway,
            clock = QueueClock(3_000L, 4_000L),
        )

        val result = sender.syncPending()

        assertTrue(result.stateOutcome is SyncItemOutcome.Accepted)
        assertTrue(result.eventOutcome is SyncItemOutcome.Accepted)
        assertEquals(20, gateway.phoneStates.single().snapshot.levelPercent)
        assertEquals(2L, gateway.phoneStates.single().snapshot.sequence)
        assertEquals(20, gateway.phoneStates.single().thresholdPercent)
        assertTrue(gateway.phoneStates.single().monitoringEnabled)
        assertEquals(3_000L, gateway.phoneStates.single().sentAtEpochMillis)
        assertEquals(SECOND_EVENT_ID, gateway.events.single().eventId)
        assertEquals(0L, result.persistedState.pendingStateSequence)
        assertNull(result.persistedState.pendingEvent)
        assertEquals(4_000L, result.persistedState.lastSyncSuccessAtEpochMillis)
        assertNull(result.persistedState.lastSyncErrorClassification)
    }

    @Test
    fun partialFailureClearsOnlyAcceptedEventAndPersistsFailureClassification() = runBlocking {
        val repository = createRepositoryWithCrossing()
        val gateway = RecordingGateway(
            stateResult = DataLayerPutResult.Rejected(
                SyncFailureClassification.API_UNAVAILABLE
            ),
        )
        val sender = MobileDataLayerSender(
            repository = repository,
            gateway = gateway,
            clock = QueueClock(3_000L, 4_000L),
        )

        val result = sender.syncPending()

        assertTrue(result.stateOutcome is SyncItemOutcome.Rejected)
        assertTrue(result.eventOutcome is SyncItemOutcome.Accepted)
        assertEquals(2L, result.persistedState.pendingStateSequence)
        assertNull(result.persistedState.pendingEvent)
        assertEquals(
            SyncFailureClassification.API_UNAVAILABLE.persistedValue,
            result.persistedState.lastSyncErrorClassification,
        )
    }

    @Test
    fun reverseGatewayCompletionStillConfirmsTheMaximumSequenceOnce() = runBlocking {
        val repository = createRepositoryWithCrossing()
        val gateway = ControlledGateway()
        val sender = MobileDataLayerSender(
            repository = repository,
            gateway = gateway,
            clock = QueueClock(3_000L, 4_000L),
        )

        val send = async { sender.syncPending() }
        gateway.stateStarted.await()
        gateway.eventStarted.await()
        gateway.eventResult.complete(DataLayerPutResult.Accepted)
        gateway.stateResult.complete(DataLayerPutResult.Accepted)
        val result = send.await()

        assertEquals(0L, result.persistedState.pendingStateSequence)
        assertNull(result.persistedState.pendingEvent)
        assertEquals(2L, (result.stateOutcome as SyncItemOutcome.Accepted).sequence)
        assertEquals(2L, (result.eventOutcome as SyncItemOutcome.Accepted).sequence)
    }

    private suspend fun createRepositoryWithCrossing(): ProtoMobileStateRepository {
        val repository = ProtoMobileStateRepository(
            InMemoryDataStore(MobileStateSanitizer.defaultValue())
        )
        repository.updateAlertRule(AlertRule(monitoringEnabled = true))
        repository.processBatteryReading(
            BatteryReading(21, isCharging = false, capturedAtEpochMillis = 1_000L),
            FIRST_EVENT_ID,
        )
        repository.processBatteryReading(
            BatteryReading(20, isCharging = false, capturedAtEpochMillis = 2_000L),
            SECOND_EVENT_ID,
        )
        return repository
    }

    private class RecordingGateway(
        private val stateResult: DataLayerPutResult = DataLayerPutResult.Accepted,
        private val eventResult: DataLayerPutResult = DataLayerPutResult.Accepted,
    ) : MobileSyncGateway {
        val phoneStates = mutableListOf<PhoneStateSync>()
        val events = mutableListOf<ThresholdReachedEvent>()

        override suspend fun putPhoneState(state: PhoneStateSync): DataLayerPutResult {
            phoneStates += state
            return stateResult
        }

        override suspend fun putThresholdEvent(
            event: ThresholdReachedEvent,
        ): DataLayerPutResult {
            events += event
            return eventResult
        }
    }

    private class ControlledGateway : MobileSyncGateway {
        val stateStarted = CompletableDeferred<Unit>()
        val eventStarted = CompletableDeferred<Unit>()
        val stateResult = CompletableDeferred<DataLayerPutResult>()
        val eventResult = CompletableDeferred<DataLayerPutResult>()

        override suspend fun putPhoneState(state: PhoneStateSync): DataLayerPutResult {
            stateStarted.complete(Unit)
            return stateResult.await()
        }

        override suspend fun putThresholdEvent(
            event: ThresholdReachedEvent,
        ): DataLayerPutResult {
            eventStarted.complete(Unit)
            return eventResult.await()
        }
    }

    private class QueueClock(vararg values: Long) : EpochMillisClock {
        private val values = ArrayDeque(values.toList())

        override fun now(): Long = values.removeFirst()
    }

    private class InMemoryDataStore<T>(initial: T) : DataStore<T> {
        private val values = MutableStateFlow(initial)
        private val mutex = Mutex()

        override val data: Flow<T> = values

        override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
            val updated = transform(values.value)
            values.value = updated
            updated
        }
    }

    private companion object {
        const val FIRST_EVENT_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val SECOND_EVENT_ID = "550e8400-e29b-41d4-a716-446655440001"
    }
}

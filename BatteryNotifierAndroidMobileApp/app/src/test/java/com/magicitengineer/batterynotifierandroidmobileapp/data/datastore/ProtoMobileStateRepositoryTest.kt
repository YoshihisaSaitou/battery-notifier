package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
import com.magicitengineer.batterynotifierandroidmobileapp.domain.notification.MobileNotificationDisposition
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncDeliveryUpdate
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncFailureClassification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtoMobileStateRepositoryTest {
    @Test
    fun notificationPermissionRequestHistoryIsPersistedWithoutChangingMonitoring() = runBlocking {
        val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
        val repository = ProtoMobileStateRepository(store)
        repository.updateMonitoringState(monitoringEnabled = true, resumeRequired = false)

        val updated = repository.markNotificationPermissionRequested()

        assertTrue(updated.notificationPermissionRequested)
        assertTrue(updated.alertRule.monitoringEnabled)
        assertEquals(updated, MobileStateProtoMapper.toDomain(store.current()))
    }

    @Test
    fun batteryProcessingPersistsSequenceAlertAndOutboxAtomically() = runBlocking {
        val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
        val repository = ProtoMobileStateRepository(store)
        repository.updateAlertRule(AlertRule(monitoringEnabled = true))

        val first = repository.processBatteryReading(
            BatteryReading(21, isCharging = false, capturedAtEpochMillis = 1_000L),
            FIRST_EVENT_ID,
        )
        val crossing = repository.processBatteryReading(
            BatteryReading(20, isCharging = false, capturedAtEpochMillis = 2_000L),
            SECOND_EVENT_ID,
        )

        assertEquals(1L, first.snapshot.sequence)
        assertNull(first.event)
        assertEquals(2L, crossing.snapshot.sequence)
        assertEquals(SECOND_EVENT_ID, crossing.event?.eventId)
        assertEquals(2L, crossing.state.pendingStateSequence)
        assertEquals(SECOND_EVENT_ID, crossing.state.pendingEvent?.eventId)
        assertEquals(SECOND_EVENT_ID, crossing.state.pendingMobileNotification?.eventId)
        assertFalse(crossing.state.alertState.armed)

        val stored = MobileStateProtoMapper.toDomain(store.current())
        assertEquals(crossing.state, stored)
    }

    @Test
    fun olderSyncCompletionCannotClearNewerPendingState() = runBlocking {
        val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
        val repository = ProtoMobileStateRepository(store)
        repository.processBatteryReading(
            BatteryReading(50, isCharging = false, capturedAtEpochMillis = 1_000L),
            FIRST_EVENT_ID,
        )
        repository.processBatteryReading(
            BatteryReading(49, isCharging = false, capturedAtEpochMillis = 2_000L),
            SECOND_EVENT_ID,
        )

        val afterOldCompletion = repository.applySyncDelivery(
            SyncDeliveryUpdate(
                confirmedStateSequence = 1L,
                completedAtEpochMillis = 3_000L,
            )
        )
        val afterCurrentCompletion = repository.applySyncDelivery(
            SyncDeliveryUpdate(
                confirmedStateSequence = 2L,
                completedAtEpochMillis = 4_000L,
            )
        )

        assertEquals(2L, afterOldCompletion.pendingStateSequence)
        assertEquals(0L, afterCurrentCompletion.pendingStateSequence)
        assertEquals(4_000L, afterCurrentCompletion.lastSyncSuccessAtEpochMillis)
    }

    @Test
    fun syncDeliveryClearsOnlyConfirmedOutboxItemsAndPreservesFailure() = runBlocking {
        val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
        val repository = ProtoMobileStateRepository(store)
        repository.updateAlertRule(AlertRule(monitoringEnabled = true))
        repository.processBatteryReading(
            BatteryReading(21, isCharging = false, capturedAtEpochMillis = 1_000L),
            FIRST_EVENT_ID,
        )
        val crossing = repository.processBatteryReading(
            BatteryReading(20, isCharging = false, capturedAtEpochMillis = 2_000L),
            SECOND_EVENT_ID,
        )
        val event = requireNotNull(crossing.event)

        val afterEvent = repository.applySyncDelivery(
            SyncDeliveryUpdate(
                confirmedEventId = event.eventId,
                confirmedEventSequence = event.sequence,
                completedAtEpochMillis = 3_000L,
                failureClassification = SyncFailureClassification.TASK_FAILURE,
            )
        )
        val afterState = repository.applySyncDelivery(
            SyncDeliveryUpdate(
                confirmedStateSequence = crossing.snapshot.sequence,
                completedAtEpochMillis = 4_000L,
            )
        )

        assertEquals(crossing.snapshot.sequence, afterEvent.pendingStateSequence)
        assertNull(afterEvent.pendingEvent)
        assertEquals(event.eventId, afterEvent.pendingMobileNotification?.eventId)
        assertEquals(
            SyncFailureClassification.TASK_FAILURE.persistedValue,
            afterEvent.lastSyncErrorClassification,
        )
        assertEquals(0L, afterState.pendingStateSequence)
        assertNull(afterState.pendingEvent)
        assertEquals(event.eventId, afterState.pendingMobileNotification?.eventId)
        assertNull(afterState.lastSyncErrorClassification)
        assertEquals(4_000L, afterState.lastSyncSuccessAtEpochMillis)
    }

    @Test
    fun thresholdChangeWithSnapshotAdvancesSequenceWithoutCreatingEvent() = runBlocking {
        val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
        val repository = ProtoMobileStateRepository(store)
        repository.processBatteryReading(
            BatteryReading(18, isCharging = false, capturedAtEpochMillis = 1_000L),
            FIRST_EVENT_ID,
        )

        val updated = repository.updateAlertRule(
            AlertRule(thresholdPercent = 15, monitoringEnabled = true)
        )

        assertEquals(2L, updated.sequence)
        assertEquals(2L, updated.lastSnapshot?.sequence)
        assertEquals(2L, updated.pendingStateSequence)
        assertEquals(15, updated.alertRule.thresholdPercent)
        assertNull(updated.pendingEvent)
    }

    @Test
    fun raisingThresholdAboveCurrentLevelDisarmsWithoutCreatingEvent() = runBlocking {
        val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
        val repository = ProtoMobileStateRepository(store)
        repository.updateAlertRule(
            AlertRule(thresholdPercent = 15, monitoringEnabled = true)
        )
        repository.processBatteryReading(
            BatteryReading(18, isCharging = false, capturedAtEpochMillis = 1_000L),
            FIRST_EVENT_ID,
        )

        val updated = repository.updateAlertRule(
            AlertRule(thresholdPercent = 20, monitoringEnabled = true)
        )

        assertFalse(updated.alertState.armed)
        assertEquals(18, updated.alertState.previousLevelPercent)
        assertNull(updated.pendingEvent)
    }

    @Test
    fun monitoringStateChangePersistsResumeStateAndAdvancesSnapshotSequence() = runBlocking {
        val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
        val repository = ProtoMobileStateRepository(store)
        repository.processBatteryReading(
            BatteryReading(67, isCharging = false, capturedAtEpochMillis = 1_000L),
            FIRST_EVENT_ID,
        )

        val active = repository.updateMonitoringState(true, false)
        val recoveryRequired = repository.updateMonitoringState(false, true)

        assertTrue(active.alertRule.monitoringEnabled)
        assertFalse(active.resumeRequired)
        assertEquals(2L, active.sequence)
        assertEquals(2L, active.lastSnapshot?.sequence)
        assertFalse(recoveryRequired.alertRule.monitoringEnabled)
        assertTrue(recoveryRequired.resumeRequired)
        assertEquals(3L, recoveryRequired.sequence)
        assertEquals(recoveryRequired, MobileStateProtoMapper.toDomain(store.current()))
    }

    @Test
    fun userRestartBaselinePreventsStoppedDischargeFromBecomingACrossing() = runBlocking {
        val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
        val repository = ProtoMobileStateRepository(store)
        repository.updateAlertRule(
            AlertRule(thresholdPercent = 20, monitoringEnabled = true)
        )
        repository.processBatteryReading(
            BatteryReading(21, isCharging = false, capturedAtEpochMillis = 1_000L),
            FIRST_EVENT_ID,
        )
        repository.updateMonitoringState(false, false)

        val baseline = repository.processBatteryReading(
            BatteryReading(20, isCharging = false, capturedAtEpochMillis = 2_000L),
            SECOND_EVENT_ID,
        )
        repository.updateMonitoringState(true, false)
        val stickyCallback = repository.processBatteryReading(
            BatteryReading(20, isCharging = false, capturedAtEpochMillis = 2_001L),
            "550e8400-e29b-41d4-a716-446655440002",
        )

        assertNull(baseline.event)
        assertNull(stickyCallback.event)
        assertFalse(stickyCallback.state.alertState.armed)
        assertNull(stickyCallback.state.pendingEvent)
    }

    @Test
    fun mobileNotificationCompletionIsIndependentFromSyncAndRejectsStaleReservation() = runBlocking {
        val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
        val repository = ProtoMobileStateRepository(store)
        repository.updateAlertRule(AlertRule(monitoringEnabled = true))
        repository.processBatteryReading(
            BatteryReading(21, isCharging = false, capturedAtEpochMillis = 1_000L),
            FIRST_EVENT_ID,
        )
        val event = requireNotNull(
            repository.processBatteryReading(
                BatteryReading(20, isCharging = false, capturedAtEpochMillis = 2_000L),
                SECOND_EVENT_ID,
            ).event
        )

        repository.applySyncDelivery(
            SyncDeliveryUpdate(
                confirmedEventId = event.eventId,
                confirmedEventSequence = event.sequence,
                completedAtEpochMillis = 3_000L,
            )
        )
        val completed = repository.completeMobileNotification(
            event.eventId,
            MobileNotificationDisposition.PERMISSION_DENIED,
        )
        val stale = repository.completeMobileNotification(
            event.eventId,
            MobileNotificationDisposition.POSTED,
        )

        assertEquals(MobileNotificationCompletionOutcome.APPLIED, completed.outcome)
        assertNull(completed.state.pendingEvent)
        assertNull(completed.state.pendingMobileNotification)
        assertEquals(event.eventId, completed.state.lastMobileNotificationEventId)
        assertNull(completed.state.lastMobileNotifiedEventId)
        assertEquals(
            MobileNotificationDisposition.PERMISSION_DENIED,
            completed.state.mobileNotificationDisposition,
        )
        assertEquals(MobileNotificationCompletionOutcome.STALE_RESERVATION, stale.outcome)
        assertEquals(completed.state, stale.state)
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

        fun current(): T = values.value
    }

    private companion object {
        const val FIRST_EVENT_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val SECOND_EVENT_ID = "550e8400-e29b-41d4-a716-446655440001"
    }
}

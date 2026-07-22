package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceiveErrorClassification
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedPhoneState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ProtoWearStateRepositoryTest {
    @Test
    fun notificationPermissionRequestHistoryIsPersistedWithoutChangingReceivedState() =
        runBlocking {
            val repository = repository()
            repository.applyPhoneState(phoneState(10), 1_000L)

            val updated = repository.markNotificationPermissionRequested()

            assertTrue(updated.notificationPermissionRequested)
            assertEquals(10L, updated.lastPhoneState?.sequence)
        }

    @Test
    fun stateOrderingAcceptsOnlyTheHighestSequenceAndCountsRejections() = runBlocking {
        val repository = repository()

        val applied = repository.applyPhoneState(phoneState(10), 1_000L)
        val duplicate = repository.applyPhoneState(phoneState(10), 1_100L)
        val older = repository.applyPhoneState(phoneState(9), 1_200L)

        assertEquals(WearStateApplyOutcome.APPLIED, applied.outcome)
        assertEquals(WearStateApplyOutcome.DUPLICATE, duplicate.outcome)
        assertEquals(WearStateApplyOutcome.OUT_OF_ORDER, older.outcome)
        assertEquals(10L, older.state.lastPhoneState?.sequence)
        assertEquals(1L, older.state.duplicateCount)
        assertEquals(1L, older.state.outOfOrderCount)
    }

    @Test
    fun eventWithSameSequenceAsStateUsesItsIndependentCursor() = runBlocking {
        val repository = repository()
        repository.applyPhoneState(phoneState(10), 1_000L)

        val result = repository.applyThresholdEvent(event(10), 1_100_000L)

        assertEquals(WearStateApplyOutcome.APPLIED, result.outcome)
        assertEquals(EVENT_ID, result.state.lastProcessedEventId)
        assertEquals(NotificationDisposition.PENDING, result.state.notificationDisposition)
        assertEquals(1, result.state.notificationPostAttemptCount)
    }

    @Test
    fun duplicateEventIsReservedOnlyOnce() = runBlocking {
        val repository = repository()
        repository.applyThresholdEvent(event(10), 1_100_000L)

        val duplicate = repository.applyThresholdEvent(event(10), 1_200_000L)

        assertEquals(WearStateApplyOutcome.DUPLICATE, duplicate.outcome)
        assertEquals(1L, duplicate.state.duplicateCount)
        assertEquals(1_100_000L, duplicate.state.eventProcessedAtEpochMillis)
    }

    @Test
    fun expiredAndClockSkewedEventsArePersistedWithoutPendingNotification() = runBlocking {
        val expiredRepository = repository()
        val expired = expiredRepository.applyThresholdEvent(event(10), 1_300_001L)
        val skewedRepository = repository()
        val skewedEvent = event(10).copy(
            occurredAtEpochMillis = 2_000_001L,
            expiresAtEpochMillis = 2_300_001L,
        )
        val skewed = skewedRepository.applyThresholdEvent(skewedEvent, 1_700_000L)

        assertEquals(NotificationDisposition.EXPIRED, expired.state.notificationDisposition)
        assertEquals(NotificationDisposition.CLOCK_SKEW, skewed.state.notificationDisposition)
    }

    @Test
    fun notificationCompletionCannotOverwriteANewerEventReservation() = runBlocking {
        val repository = repository()
        repository.applyThresholdEvent(event(10), 1_100_000L)
        val newerEvent = event(11).copy(
            eventId = "550e8400-e29b-41d4-a716-446655440021"
        )
        repository.applyThresholdEvent(newerEvent, 1_100_001L)

        val staleCompletion = repository.completeNotification(
            eventId = EVENT_ID,
            disposition = NotificationDisposition.POSTED,
        )

        assertEquals(
            WearNotificationCompletionOutcome.STALE_RESERVATION,
            staleCompletion.outcome,
        )
        assertEquals(newerEvent.eventId, staleCompletion.state.lastProcessedEventId)
        assertEquals(NotificationDisposition.PENDING, staleCompletion.state.notificationDisposition)
    }

    @Test
    fun pendingNotificationCanBeCompletedOnlyOnce() = runBlocking {
        val repository = repository()
        repository.applyThresholdEvent(event(10), 1_100_000L)

        val first = repository.completeNotification(
            eventId = EVENT_ID,
            disposition = NotificationDisposition.POSTED,
        )
        val second = repository.completeNotification(
            eventId = EVENT_ID,
            disposition = NotificationDisposition.RESERVED_FAILED,
        )

        assertEquals(WearNotificationCompletionOutcome.APPLIED, first.outcome)
        assertEquals(WearNotificationCompletionOutcome.STALE_RESERVATION, second.outcome)
        assertEquals(NotificationDisposition.POSTED, second.state.notificationDisposition)
    }

    @Test
    fun failedNotificationCanBeAtomicallyReservedForTheNextAttempt() = runBlocking {
        val repository = repository()
        repository.applyThresholdEvent(event(10), 1_100_000L)
        repository.completeNotification(EVENT_ID, NotificationDisposition.RESERVED_FAILED)

        val reservation = repository.reserveNotificationRetry(EVENT_ID, 1_200_000L)

        assertEquals(WearNotificationRetryReservationOutcome.RESERVED, reservation.outcome)
        assertEquals(NotificationDisposition.PENDING, reservation.state.notificationDisposition)
        assertEquals(2, reservation.state.notificationPostAttemptCount)
    }

    @Test
    fun retryAfterExpiryIsTerminalWithoutCreatingAReservation() = runBlocking {
        val repository = repository()
        repository.applyThresholdEvent(event(10), 1_100_000L)
        repository.completeNotification(EVENT_ID, NotificationDisposition.RESERVED_FAILED)

        val reservation = repository.reserveNotificationRetry(EVENT_ID, 1_300_001L)

        assertEquals(WearNotificationRetryReservationOutcome.EXPIRED, reservation.outcome)
        assertEquals(NotificationDisposition.EXPIRED, reservation.state.notificationDisposition)
        assertEquals(0, reservation.state.notificationPostAttemptCount)
    }

    @Test
    fun retryAtClockSkewBoundaryIsAllowedButOneMillisecondBeyondIsTerminal() = runBlocking {
        val allowedRepository = repository()
        allowedRepository.applyThresholdEvent(event(10), 1_100_000L)
        allowedRepository.completeNotification(EVENT_ID, NotificationDisposition.RESERVED_FAILED)
        val allowed = allowedRepository.reserveNotificationRetry(EVENT_ID, 700_000L)

        val skewedRepository = repository()
        skewedRepository.applyThresholdEvent(event(10), 1_100_000L)
        skewedRepository.completeNotification(EVENT_ID, NotificationDisposition.RESERVED_FAILED)
        val skewed = skewedRepository.reserveNotificationRetry(EVENT_ID, 699_999L)

        assertEquals(WearNotificationRetryReservationOutcome.RESERVED, allowed.outcome)
        assertEquals(WearNotificationRetryReservationOutcome.CLOCK_SKEW, skewed.outcome)
        assertEquals(NotificationDisposition.CLOCK_SKEW, skewed.state.notificationDisposition)
        assertEquals(0, skewed.state.notificationPostAttemptCount)
    }

    @Test
    fun interruptedPendingRecoveryHonorsExpirySkewAndAttemptCap() = runBlocking {
        val retryableRepository = repository()
        retryableRepository.applyThresholdEvent(event(10), 1_100_000L)
        val retryable = retryableRepository.recoverInterruptedNotification(1_200_000L)

        val expiredRepository = repository()
        expiredRepository.applyThresholdEvent(event(10), 1_100_000L)
        val expired = expiredRepository.recoverInterruptedNotification(1_300_001L)

        val skewedRepository = repository()
        skewedRepository.applyThresholdEvent(event(10), 1_100_000L)
        val skewed = skewedRepository.recoverInterruptedNotification(699_999L)

        assertEquals(WearNotificationRecoveryOutcome.RECOVERED_FOR_RETRY, retryable.outcome)
        assertEquals(NotificationDisposition.RESERVED_FAILED, retryable.state.notificationDisposition)
        assertEquals(WearNotificationRecoveryOutcome.EXPIRED, expired.outcome)
        assertEquals(NotificationDisposition.EXPIRED, expired.state.notificationDisposition)
        assertEquals(WearNotificationRecoveryOutcome.CLOCK_SKEW, skewed.outcome)
        assertEquals(NotificationDisposition.CLOCK_SKEW, skewed.state.notificationDisposition)
    }

    @Test
    fun concurrentRetryTriggersCreateOnlyOneReservation() = runBlocking {
        val repository = repository()
        repository.applyThresholdEvent(event(10), 1_100_000L)
        repository.completeNotification(EVENT_ID, NotificationDisposition.RESERVED_FAILED)

        val outcomes = List(10) {
            async { repository.reserveNotificationRetry(EVENT_ID, 1_200_000L).outcome }
        }.awaitAll()

        assertEquals(1, outcomes.count { it == WearNotificationRetryReservationOutcome.RESERVED })
        assertEquals(
            9,
            outcomes.count { it == WearNotificationRetryReservationOutcome.NOT_ELIGIBLE },
        )
        assertEquals(2, repository.state.first().notificationPostAttemptCount)
    }

    @Test
    fun invalidPayloadDiagnosticsPreserveLastValidState() = runBlocking {
        val repository = repository()
        repository.applyPhoneState(phoneState(10), 1_000L)

        val state = repository.recordInvalidPayload(ReceiveErrorClassification.OUT_OF_RANGE)

        assertEquals(10L, state.lastPhoneState?.sequence)
        assertEquals(1L, state.invalidPayloadCount)
        assertEquals(ReceiveErrorClassification.OUT_OF_RANGE.persistedValue, state.lastReceiveError)
        assertNull(state.lastProcessedEventId)
    }

    private fun repository() = ProtoWearStateRepository(
        InMemoryDataStore(WearStateSanitizer.defaultValue())
    )

    private fun phoneState(sequence: Long) = ReceivedPhoneState(
        schemaVersion = 1,
        sequence = sequence,
        levelPercent = 68,
        isCharging = false,
        capturedAtEpochMillis = 900_000L,
        thresholdPercent = 20,
        monitoringEnabled = true,
        sentAtEpochMillis = 950_000L,
    )

    private fun event(sequence: Long) = ReceivedThresholdEvent(
        schemaVersion = 1,
        eventId = EVENT_ID,
        sequence = sequence,
        levelPercent = 20,
        thresholdPercent = 20,
        occurredAtEpochMillis = 1_000_000L,
        expiresAtEpochMillis = 1_300_000L,
    )

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
        const val EVENT_ID = "550e8400-e29b-41d4-a716-446655440020"
    }
}

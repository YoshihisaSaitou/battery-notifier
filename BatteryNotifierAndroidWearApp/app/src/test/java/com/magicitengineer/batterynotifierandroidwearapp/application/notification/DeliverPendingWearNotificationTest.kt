package com.magicitengineer.batterynotifierandroidwearapp.application.notification

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.ProtoWearStateRepository
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateRepository
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateSanitizer
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedPhoneState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.WearDataLayerContract
import com.magicitengineer.batterynotifierandroidwearapp.data.wearable.ownsInitialWearNotificationDelivery
import com.magicitengineer.batterynotifierandroidwearapp.data.wearable.protectInitialWearNotificationDelivery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliverPendingWearNotificationTest {
    @Test
    fun `phone state interleaving cannot acquire initial event delivery ownership`() = runBlocking {
        val repository = repository()
        val reserved = repository.applyThresholdEvent(event(), RECEIVED_AT).state
        repository.applyPhoneState(
            ReceivedPhoneState(
                schemaVersion = 1,
                sequence = 11,
                levelPercent = 19,
                isCharging = false,
                capturedAtEpochMillis = RECEIVED_AT,
                thresholdPercent = 20,
                monitoringEnabled = true,
                sentAtEpochMillis = RECEIVED_AT,
            ),
            RECEIVED_AT + 1,
        )
        var calls = 0
        val delivery = DeliverPendingWearNotification(repository) {
            calls += 1
            WearNotificationPostResult.POSTED
        }

        if (ownsInitialWearNotificationDelivery(WearDataLayerContract.PHONE_STATE_PATH)) {
            delivery.deliver(repository.state.first())
        }
        if (ownsInitialWearNotificationDelivery(WearDataLayerContract.THRESHOLD_EVENT_PATH)) {
            delivery.deliver(reserved)
        }

        assertEquals(1, calls)
        assertEquals(1, repository.state.first().notificationPostAttemptCount)
        assertEquals(NotificationDisposition.POSTED, repository.state.first().notificationDisposition)
    }

    @Test
    fun `all post outcomes are persisted against the reserved event`() = runBlocking {
        val cases = listOf(
            WearNotificationPostResult.POSTED to NotificationDisposition.POSTED,
            WearNotificationPostResult.PERMISSION_DENIED to
                NotificationDisposition.PERMISSION_DENIED,
            WearNotificationPostResult.FAILED to NotificationDisposition.RESERVED_FAILED,
        )

        cases.forEach { (postResult, expectedDisposition) ->
            val repository = repository()
            val reserved = repository.applyThresholdEvent(event(), RECEIVED_AT).state
            var postedEventId: String? = null
            val delivery = DeliverPendingWearNotification(repository) { receivedEvent ->
                postedEventId = receivedEvent.eventId
                postResult
            }

            val result = delivery.deliver(reserved)
            val persisted = repository.state.first()

            assertEquals(EVENT_ID, postedEventId)
            assertEquals(expectedDisposition, persisted.notificationDisposition)
            assertEquals(
                expectedDisposition,
                (result as WearNotificationDeliveryResult.Completed).disposition,
            )
        }
    }

    @Test
    fun `expired event never reaches the notification gateway`() = runBlocking {
        val repository = repository()
        val expired = repository.applyThresholdEvent(event(), EXPIRED_RECEIVED_AT).state
        var calls = 0
        val delivery = DeliverPendingWearNotification(repository) {
            calls += 1
            WearNotificationPostResult.POSTED
        }

        val result = delivery.deliver(expired)

        assertEquals(WearNotificationDeliveryResult.NotPending, result)
        assertEquals(0, calls)
        assertEquals(NotificationDisposition.EXPIRED, repository.state.first().notificationDisposition)
    }

    @Test
    fun `stable notification id is repeatable and event specific`() {
        val first = StableWearNotificationId.fromEventId(EVENT_ID)

        assertEquals(first, StableWearNotificationId.fromEventId(EVENT_ID))
        assertNotEquals(first, StableWearNotificationId.fromEventId(OTHER_EVENT_ID))
        assertTrue(first >= 0)
    }

    @Test
    fun `retry succeeds within expiry and preserves one event attempt sequence`() = runBlocking {
        val repository = repository()
        val initial = repository.applyThresholdEvent(event(), RECEIVED_AT).state
        var calls = 0
        val delivery = DeliverPendingWearNotification(repository) {
            calls += 1
            if (calls == 1) WearNotificationPostResult.FAILED else WearNotificationPostResult.POSTED
        }

        delivery.deliver(initial)
        val result = delivery.retry(repository.state.first(), RETRY_AT)
        val persisted = repository.state.first()

        assertEquals(2, calls)
        assertEquals(NotificationDisposition.POSTED, persisted.notificationDisposition)
        assertEquals(2, persisted.notificationPostAttemptCount)
        assertEquals(
            NotificationDisposition.POSTED,
            (result as WearNotificationDeliveryResult.Completed).disposition,
        )
    }

    @Test
    fun `three failed attempts exhaust the event and prevent a fourth post`() = runBlocking {
        val repository = repository()
        val initial = repository.applyThresholdEvent(event(), RECEIVED_AT).state
        var calls = 0
        val delivery = DeliverPendingWearNotification(repository) {
            calls += 1
            WearNotificationPostResult.FAILED
        }

        delivery.deliver(initial)
        delivery.retry(repository.state.first(), RETRY_AT)
        val third = delivery.retry(repository.state.first(), RETRY_AT + 1)
        val fourth = delivery.retry(repository.state.first(), RETRY_AT + 2)
        val persisted = repository.state.first()

        assertEquals(3, calls)
        assertEquals(NotificationDisposition.FAILED_EXHAUSTED, persisted.notificationDisposition)
        assertEquals(3, persisted.notificationPostAttemptCount)
        assertEquals(
            NotificationDisposition.FAILED_EXHAUSTED,
            (third as WearNotificationDeliveryResult.Completed).disposition,
        )
        assertEquals(WearNotificationDeliveryResult.RetryNotEligible, fourth)
    }

    @Test
    fun `retry after expiry is terminal and does not call the gateway again`() = runBlocking {
        val repository = repository()
        val initial = repository.applyThresholdEvent(event(), RECEIVED_AT).state
        var calls = 0
        val delivery = DeliverPendingWearNotification(repository) {
            calls += 1
            WearNotificationPostResult.FAILED
        }

        delivery.deliver(initial)
        val result = delivery.retry(repository.state.first(), EXPIRED_RECEIVED_AT)

        assertEquals(1, calls)
        assertEquals(WearNotificationDeliveryResult.RetryExpired, result)
        assertEquals(NotificationDisposition.EXPIRED, repository.state.first().notificationDisposition)
    }

    @Test
    fun `cancellation releases pending reservation for a bounded retry`() = runBlocking {
        val repository = repository()
        val initial = repository.applyThresholdEvent(event(), RECEIVED_AT).state
        val delivery = DeliverPendingWearNotification(repository) {
            throw CancellationException("service stopped")
        }

        try {
            delivery.deliver(initial)
        } catch (_: CancellationException) {
            // Expected: cancellation still releases the persisted reservation.
        }

        assertEquals(
            NotificationDisposition.RESERVED_FAILED,
            repository.state.first().notificationDisposition,
        )
        assertEquals(1, repository.state.first().notificationPostAttemptCount)
    }

    @Test
    fun `service cancellation after event persistence still reaches initial delivery`() = runBlocking {
        val persistenceCompleted = CompletableDeferred<Unit>()
        val allowDelivery = CompletableDeferred<Unit>()
        var deliveryReached = false
        val job = launch {
            protectInitialWearNotificationDelivery(WearDataLayerContract.THRESHOLD_EVENT_PATH) {
                persistenceCompleted.complete(Unit)
                allowDelivery.await()
                deliveryReached = true
            }
        }

        persistenceCompleted.await()
        job.cancel()
        allowDelivery.complete(Unit)
        job.join()

        assertTrue(deliveryReached)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `service cancellation after post cannot interrupt completion persistence`() = runBlocking {
        val delegate = repository()
        val initial = delegate.applyThresholdEvent(event(), RECEIVED_AT).state
        val completionStarted = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        val blockingRepository = object : WearStateRepository by delegate {
            override suspend fun completeNotification(
                eventId: String,
                disposition: NotificationDisposition,
            ) = run {
                completionStarted.complete(Unit)
                allowCompletion.await()
                delegate.completeNotification(eventId, disposition)
            }
        }
        val delivery = DeliverPendingWearNotification(blockingRepository) {
            WearNotificationPostResult.POSTED
        }
        val job = launch { delivery.deliver(initial) }

        completionStarted.await()
        job.cancel()
        allowCompletion.complete(Unit)
        job.join()

        assertEquals(NotificationDisposition.POSTED, delegate.state.first().notificationDisposition)
        assertEquals(1, delegate.state.first().notificationPostAttemptCount)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `process recovery releases pending attempt and retry accounts exactly once`() = runBlocking {
        val repository = repository()
        repository.applyThresholdEvent(event(), RECEIVED_AT)

        val recovery = repository.recoverInterruptedNotification(RETRY_AT)
        var calls = 0
        val delivery = DeliverPendingWearNotification(repository) {
            calls += 1
            WearNotificationPostResult.POSTED
        }
        delivery.retry(recovery.state, RETRY_AT)

        assertEquals(1, calls)
        assertEquals(2, repository.state.first().notificationPostAttemptCount)
        assertEquals(NotificationDisposition.POSTED, repository.state.first().notificationDisposition)
    }

    @Test
    fun `clock rollback retry is terminal and never reaches gateway`() = runBlocking {
        val repository = repository()
        val initial = repository.applyThresholdEvent(event(), RECEIVED_AT).state
        var calls = 0
        val delivery = DeliverPendingWearNotification(repository) {
            calls += 1
            WearNotificationPostResult.POSTED
        }
        repository.completeNotification(EVENT_ID, NotificationDisposition.RESERVED_FAILED)

        val result = delivery.retry(initial, 699_999L)

        assertEquals(WearNotificationDeliveryResult.RetryClockSkew, result)
        assertEquals(0, calls)
        assertEquals(NotificationDisposition.CLOCK_SKEW, repository.state.first().notificationDisposition)
    }

    private fun repository() = ProtoWearStateRepository(
        InMemoryDataStore(WearStateSanitizer.defaultValue())
    )

    private fun event() = ReceivedThresholdEvent(
        schemaVersion = 1,
        eventId = EVENT_ID,
        sequence = 10L,
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
        const val OTHER_EVENT_ID = "550e8400-e29b-41d4-a716-446655440021"
        const val RECEIVED_AT = 1_100_000L
        const val RETRY_AT = 1_200_000L
        const val EXPIRED_RECEIVED_AT = 1_300_001L
    }
}

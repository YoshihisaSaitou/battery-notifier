package com.magicitengineer.batterynotifierandroidwearapp.application.notification

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.ProtoWearStateRepository
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateSanitizer
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliverPendingWearNotificationTest {
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
        const val EXPIRED_RECEIVED_AT = 1_300_001L
    }
}

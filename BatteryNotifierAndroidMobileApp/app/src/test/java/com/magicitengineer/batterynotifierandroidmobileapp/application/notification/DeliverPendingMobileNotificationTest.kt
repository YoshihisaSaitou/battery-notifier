package com.magicitengineer.batterynotifierandroidmobileapp.application.notification

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateSanitizer
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.ProtoMobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
import com.magicitengineer.batterynotifierandroidmobileapp.domain.notification.MobileNotificationDisposition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeliverPendingMobileNotificationTest {
    @Test
    fun allPostOutcomesArePersistedAndClearOnlyTheNotificationReservation() = runBlocking {
        val cases = listOf(
            MobileNotificationPostResult.POSTED to MobileNotificationDisposition.POSTED,
            MobileNotificationPostResult.PERMISSION_DENIED to
                MobileNotificationDisposition.PERMISSION_DENIED,
            MobileNotificationPostResult.FAILED to MobileNotificationDisposition.FAILED,
        )

        cases.forEach { (postResult, expectedDisposition) ->
            val repository = repositoryWithCrossing()
            val eventId = requireNotNull(repository.stateValue().pendingEvent).eventId
            val deliverer = DeliverPendingMobileNotification(repository) { postResult }

            val result = deliverer.deliverPending()
            val persisted = repository.stateValue()

            assertEquals(
                MobileNotificationDeliveryResult.Completed(postResult, expectedDisposition),
                result,
            )
            assertNull(persisted.pendingMobileNotification)
            assertEquals(eventId, persisted.pendingEvent?.eventId)
            assertEquals(eventId, persisted.lastMobileNotificationEventId)
            assertEquals(expectedDisposition, persisted.mobileNotificationDisposition)
            assertEquals(
                eventId.takeIf { postResult == MobileNotificationPostResult.POSTED },
                persisted.lastMobileNotifiedEventId,
            )
        }
    }

    @Test
    fun securityAndRuntimeFailuresAreClassifiedWithoutEscaping() = runBlocking {
        val permissionRepository = repositoryWithCrossing()
        val failedRepository = repositoryWithCrossing()

        val denied = DeliverPendingMobileNotification(permissionRepository) {
            throw SecurityException("permission")
        }.deliverPending()
        val failed = DeliverPendingMobileNotification(failedRepository) {
            error("notification manager")
        }.deliverPending()

        assertEquals(
            MobileNotificationPostResult.PERMISSION_DENIED,
            (denied as MobileNotificationDeliveryResult.Completed).postResult,
        )
        assertEquals(
            MobileNotificationPostResult.FAILED,
            (failed as MobileNotificationDeliveryResult.Completed).postResult,
        )
    }

    @Test
    fun noReservationDoesNotCallTheGateway() = runBlocking {
        val repository = ProtoMobileStateRepository(
            InMemoryDataStore(MobileStateSanitizer.defaultValue())
        )
        var calls = 0
        val result = DeliverPendingMobileNotification(repository) {
            calls += 1
            MobileNotificationPostResult.POSTED
        }.deliverPending()

        assertEquals(MobileNotificationDeliveryResult.NotPending, result)
        assertEquals(0, calls)
    }

    @Test
    fun stableNotificationIdIsRepeatableAndEventSpecific() {
        assertEquals(
            StableMobileNotificationId.fromEventId(FIRST_EVENT_ID),
            StableMobileNotificationId.fromEventId(FIRST_EVENT_ID),
        )
        assertNotEquals(
            StableMobileNotificationId.fromEventId(FIRST_EVENT_ID),
            StableMobileNotificationId.fromEventId(SECOND_EVENT_ID),
        )
    }

    private suspend fun repositoryWithCrossing(): ProtoMobileStateRepository {
        val repository = ProtoMobileStateRepository(
            InMemoryDataStore(MobileStateSanitizer.defaultValue())
        )
        repository.updateAlertRule(AlertRule(monitoringEnabled = true))
        repository.processBatteryReading(
            BatteryReading(21, false, 1_000L),
            FIRST_EVENT_ID,
        )
        repository.processBatteryReading(
            BatteryReading(20, false, 2_000L),
            SECOND_EVENT_ID,
        )
        return repository
    }

    private suspend fun ProtoMobileStateRepository.stateValue() =
        state.first()

    private class InMemoryDataStore<T>(initial: T) : DataStore<T> {
        private val values = MutableStateFlow(initial)
        private val mutex = Mutex()

        override val data: Flow<T> = values

        override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
            transform(values.value).also { values.value = it }
        }
    }

    private companion object {
        const val FIRST_EVENT_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val SECOND_EVENT_ID = "550e8400-e29b-41d4-a716-446655440001"
    }
}

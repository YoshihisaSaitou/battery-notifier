package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealWearDataStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStoreScopes = mutableListOf<CoroutineScope>()
    private val stateFiles = mutableListOf<File>()

    @After
    fun cleanUpDataStores() {
        dataStoreScopes.forEach { it.cancel() }
        dataStoreScopes.clear()
        stateFiles.forEach { stateFile ->
            stateFile.delete()
            File("${stateFile.absolutePath}.tmp").delete()
        }
        stateFiles.clear()
    }

    @Test
    fun retryReservationIsPersistedToARealProtoFile() = runBlocking {
        val stateFile = newStateFile("retry")
        val repository = ProtoWearStateRepository(createDataStore(stateFile))
        repository.applyThresholdEvent(event(), RECEIVED_AT)
        repository.completeNotification(EVENT_ID, NotificationDisposition.RESERVED_FAILED)

        val reservation = repository.reserveNotificationRetry(EVENT_ID, RETRY_AT)

        assertEquals(WearNotificationRetryReservationOutcome.RESERVED, reservation.outcome)
        assertTrue(stateFile.isFile)
        assertTrue(stateFile.length() > 0L)
        val persistedProto = stateFile.inputStream().use(WearStateProto::parseFrom)
        val persisted = WearStateProtoMapper.toDomain(persistedProto)
        assertEquals(NotificationDisposition.PENDING, persisted.notificationDisposition)
        assertEquals(2, persisted.notificationPostAttemptCount)
    }

    @Test
    fun concurrentRealFileRetryTriggersCreateOnlyOneReservation() = runBlocking {
        val repository = ProtoWearStateRepository(createDataStore(newStateFile("concurrent")))
        repository.applyThresholdEvent(event(), RECEIVED_AT)
        repository.completeNotification(EVENT_ID, NotificationDisposition.RESERVED_FAILED)

        val outcomes = List(CONCURRENT_TRIGGER_COUNT) {
            async(Dispatchers.Default) {
                repository.reserveNotificationRetry(EVENT_ID, RETRY_AT).outcome
            }
        }.awaitAll()

        assertEquals(
            1,
            outcomes.count { it == WearNotificationRetryReservationOutcome.RESERVED },
        )
        assertEquals(
            CONCURRENT_TRIGGER_COUNT - 1,
            outcomes.count { it == WearNotificationRetryReservationOutcome.NOT_ELIGIBLE },
        )
    }

    @Test
    fun malformedWearProtoFileIsReplacedWithAValidSafeDefault() = runBlocking {
        val stateFile = newStateFile("corrupt")
        stateFile.writeBytes(byteArrayOf(0x0A, 0x05, 0x01))
        val dataStore = createDataStore(stateFile)

        val recovered = dataStore.data.first()

        assertEquals(WearStateSanitizer.defaultValue(), recovered)
        val persistedReplacement = stateFile.inputStream().use(WearStateProto::parseFrom)
        assertEquals(recovered, persistedReplacement)
    }

    private fun newStateFile(label: String): File =
        File(context.cacheDir, "$label-${UUID.randomUUID()}.pb").also { stateFile ->
            check(!stateFile.exists() || stateFile.delete())
            stateFiles += stateFile
        }

    private fun createDataStore(file: File): DataStore<WearStateProto> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreScopes += scope
        return DataStoreFactory.create(
            serializer = WearStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler {
                WearStateSanitizer.defaultValue()
            },
            scope = scope,
            produceFile = { file },
        )
    }

    private fun event() = ReceivedThresholdEvent(
        schemaVersion = 1,
        eventId = EVENT_ID,
        sequence = 10L,
        levelPercent = 20,
        thresholdPercent = 20,
        occurredAtEpochMillis = 1_000_000L,
        expiresAtEpochMillis = 1_300_000L,
    )

    private companion object {
        const val EVENT_ID = "550e8400-e29b-41d4-a716-446655440020"
        const val RECEIVED_AT = 1_100_000L
        const val RETRY_AT = 1_200_000L
        const val CONCURRENT_TRIGGER_COUNT = 10
    }
}

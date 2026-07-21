package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealMobileDataStoreInstrumentedTest {
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
    fun repositoryTransactionIsPersistedToARealProtoFile() = runBlocking {
        val stateFile = newStateFile("atomic")
        val dataStore = createDataStore(stateFile)
        val repository = ProtoMobileStateRepository(dataStore)
        repository.updateAlertRule(AlertRule(monitoringEnabled = true))

        repository.processBatteryReading(
            reading = BatteryReading(21, isCharging = false, capturedAtEpochMillis = 1_000L),
            candidateEventId = FIRST_EVENT_ID,
        )
        val crossing = repository.processBatteryReading(
            reading = BatteryReading(20, isCharging = false, capturedAtEpochMillis = 2_000L),
            candidateEventId = SECOND_EVENT_ID,
        )

        assertTrue(stateFile.isFile)
        assertTrue(stateFile.length() > 0L)
        val persistedProto = stateFile.inputStream().use(MobileStateProto::parseFrom)
        val persisted = MobileStateProtoMapper.toDomain(persistedProto)
        assertEquals(2L, persisted.sequence)
        assertEquals(2L, persisted.pendingStateSequence)
        assertEquals(SECOND_EVENT_ID, persisted.pendingEvent?.eventId)
        assertFalse(persisted.alertState.armed)
        assertEquals(crossing.state, persisted)
    }

    @Test
    fun concurrentRealFileUpdatesAllocateEveryLongSequenceExactlyOnce() = runBlocking {
        val repository = ProtoMobileStateRepository(createDataStore(newStateFile("concurrent")))

        val results = (1..UPDATE_COUNT).map { index ->
            async(Dispatchers.Default) {
                repository.processBatteryReading(
                    reading = BatteryReading(
                        levelPercent = index % 101,
                        isCharging = false,
                        capturedAtEpochMillis = BASE_CAPTURED_AT_EPOCH_MILLIS + index.toLong(),
                    ),
                    candidateEventId = UUID.nameUUIDFromBytes("reading-$index".toByteArray()).toString(),
                )
            }
        }.awaitAll()

        val allocatedSequences = results.map { it.snapshot.sequence }.toSet()
        val expectedSequences = (1L..UPDATE_COUNT.toLong()).toSet()
        val persisted = repository.state.first()

        assertEquals(expectedSequences, allocatedSequences)
        assertEquals(UPDATE_COUNT.toLong(), persisted.sequence)
        assertEquals(UPDATE_COUNT.toLong(), persisted.pendingStateSequence)
    }

    @Test
    fun malformedProtoFileIsReplacedWithAValidSafeDefault() = runBlocking {
        val stateFile = newStateFile("corrupt")
        stateFile.writeBytes(byteArrayOf(0x0A, 0x05, 0x01))
        val dataStore = createDataStore(stateFile)

        val recovered = dataStore.data.first()

        assertEquals(MobileStateSanitizer.defaultValue(), recovered)
        val persistedReplacement = stateFile.inputStream().use(MobileStateProto::parseFrom)
        assertEquals(recovered, persistedReplacement)
    }

    private fun newStateFile(label: String): File =
        File(context.cacheDir, "$label-${UUID.randomUUID()}.pb").also { stateFile ->
            check(!stateFile.exists() || stateFile.delete())
            stateFiles += stateFile
        }

    private fun createDataStore(file: File): DataStore<MobileStateProto> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreScopes += scope
        return DataStoreFactory.create(
            serializer = MobileStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler {
                MobileStateSanitizer.defaultValue()
            },
            scope = scope,
            produceFile = { file },
        )
    }

    private companion object {
        const val UPDATE_COUNT = 30
        const val BASE_CAPTURED_AT_EPOCH_MILLIS = 1_784_516_400_000L
        const val FIRST_EVENT_ID = "550e8400-e29b-41d4-a716-446655440000"
        const val SECOND_EVENT_ID = "550e8400-e29b-41d4-a716-446655440001"
    }
}

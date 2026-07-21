package com.magicitengineer.batterynotifierandroidwearapp.application.sync

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.ProtoWearStateRepository
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateSanitizer
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.WearDataLayerContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessWearDataItemTest {
    @Test
    fun invalidAndUnsupportedPayloadsUpdateOnlyDiagnostics() = runBlocking {
        val repository = ProtoWearStateRepository(
            InMemoryDataStore(WearStateSanitizer.defaultValue())
        )
        val processor = ProcessWearDataItem(repository)

        val invalid = processor.process(
            WearDataLayerContract.PHONE_STATE_PATH,
            mapOf(WearDataLayerContract.KEY_SCHEMA_VERSION to 1),
            1_000L,
        )
        val unsupported = processor.process(
            WearDataLayerContract.PHONE_STATE_PATH,
            mapOf(WearDataLayerContract.KEY_SCHEMA_VERSION to 2),
            1_000L,
        )
        val state = repository.state.first()

        assertTrue(invalid is WearDataItemProcessingResult.Rejected)
        assertTrue(unsupported is WearDataItemProcessingResult.UnsupportedSchema)
        assertEquals(1L, state.invalidPayloadCount)
        assertEquals(1L, state.unsupportedSchemaCount)
        assertEquals(2, state.lastUnsupportedSchemaVersion)
        assertEquals(null, state.lastPhoneState)
    }

    @Test
    fun unknownPathIsIgnoredWithoutDiagnosticMutation() = runBlocking {
        val repository = ProtoWearStateRepository(
            InMemoryDataStore(WearStateSanitizer.defaultValue())
        )
        val processor = ProcessWearDataItem(repository)

        val result = processor.process("/unrelated", emptyMap(), 1_000L)
        val state = repository.state.first()

        assertEquals(WearDataItemProcessingResult.IgnoredUnknownPath, result)
        assertEquals(0L, state.invalidPayloadCount)
        assertEquals(0L, state.unsupportedSchemaCount)
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
}

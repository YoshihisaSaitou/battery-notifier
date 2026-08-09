package com.magicitengineer.batterynotifierandroidwearapp.application.settings

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.ProtoWearStateRepository
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateSanitizer
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeRequest
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeResult
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeResultCode
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeStatus
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedPhoneState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

class WearThresholdSettingsControllerTest {
    @Test
    fun repeatedDraftAdjustmentsSendOnlyTheFinalValueAfterExplicitSave() = runBlocking {
        val store = InMemoryDataStore(WearStateSanitizer.defaultValue())
        val repository = ProtoWearStateRepository(store)
        repository.applyPhoneState(phoneState(sequence = 10), receivedAtEpochMillis = 1_000L)
        val gateway = RecordingGateway(ThresholdChangeRequestSendResult.SENT)
        val controller = WearThresholdSettingsController(
            repository = repository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory { REQUEST_ID },
        )

        listOf(19, 18, 17, 16, 15).forEach { controller.updateDraft(it) }

        assertEquals(15, repository.state.first().thresholdDraftPercent)
        assertEquals(0, gateway.requests.size)

        assertEquals(ThresholdChangeCommandResult.WAITING_FOR_RESULT, controller.save())
        assertEquals(1, gateway.requests.size)
        assertEquals(15, gateway.requests.single().thresholdPercent)
    }

    @Test
    fun saveWithAuthoritativeEditorValueAtomicallyOverridesOlderPersistedDraft() = runBlocking {
        val store = InMemoryDataStore(WearStateSanitizer.defaultValue())
        val repository = ProtoWearStateRepository(store)
        repository.applyPhoneState(phoneState(sequence = 10), receivedAtEpochMillis = 1_000L)
        repository.updateThresholdDraft(21)
        val gateway = RecordingGateway(ThresholdChangeRequestSendResult.SENT)
        val controller = WearThresholdSettingsController(
            repository = repository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory { REQUEST_ID },
        )

        assertEquals(
            ThresholdChangeCommandResult.WAITING_FOR_RESULT,
            controller.save(thresholdPercent = 22),
        )

        val persisted = repository.state.first()
        assertEquals(22, persisted.thresholdDraftPercent)
        assertEquals(22, persisted.pendingThresholdChangeRequest?.thresholdPercent)
        assertEquals(22, gateway.requests.single().thresholdPercent)
    }

    @Test
    fun capabilityAvailabilityIsCheckedWithoutSendingARequest() = runBlocking {
        val repository = ProtoWearStateRepository(
            InMemoryDataStore(WearStateSanitizer.defaultValue())
        )
        val gateway = RecordingGateway().apply { available = false }
        val controller = WearThresholdSettingsController(
            repository = repository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory { REQUEST_ID },
        )

        assertEquals(false, controller.isAvailable())
        assertEquals(0, gateway.requests.size)
    }

    @Test
    fun failedSendPersistsRequestAndReconnectDoesNotRetryUntilUserRequestsIt() = runBlocking {
        val store = InMemoryDataStore(WearStateSanitizer.defaultValue())
        val repository = ProtoWearStateRepository(store)
        repository.applyPhoneState(phoneState(sequence = 10), receivedAtEpochMillis = 1_000L)
        repository.updateThresholdDraft(30)
        val gateway = RecordingGateway(
            ThresholdChangeRequestSendResult.NO_REACHABLE_NODE,
            ThresholdChangeRequestSendResult.SENT,
        )
        val controller = WearThresholdSettingsController(
            repository = repository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory { REQUEST_ID },
        )

        val first = controller.save()
        val restoredRepository = ProtoWearStateRepository(store)
        restoredRepository.applyPhoneState(
            phoneState(sequence = 11),
            receivedAtEpochMillis = 1_100L,
        )
        val afterReconnect = restoredRepository.state.first()

        assertEquals(ThresholdChangeCommandResult.NO_REACHABLE_NODE, first)
        assertEquals(1, gateway.requests.size)
        assertEquals(ThresholdChangeStatus.SEND_FAILED, afterReconnect.thresholdChangeStatus)
        assertEquals(REQUEST_ID, afterReconnect.pendingThresholdChangeRequest?.requestId)

        val restoredController = WearThresholdSettingsController(
            repository = restoredRepository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory { "unused" },
        )
        val retried = restoredController.retry()
        val afterRetry = restoredRepository.state.first()

        assertEquals(ThresholdChangeCommandResult.WAITING_FOR_RESULT, retried)
        assertEquals(2, gateway.requests.size)
        assertEquals(REQUEST_ID, gateway.requests[0].requestId)
        assertEquals(REQUEST_ID, gateway.requests[1].requestId)
        assertEquals(ThresholdChangeStatus.WAITING_RESULT, afterRetry.thresholdChangeStatus)
    }

    @Test
    fun restoredDraftReconciliationPreservesActiveRequestWithoutSending() = runBlocking {
        val store = InMemoryDataStore(WearStateSanitizer.defaultValue())
        val repository = ProtoWearStateRepository(store)
        repository.applyPhoneState(phoneState(sequence = 10), receivedAtEpochMillis = 1_000L)
        repository.updateThresholdDraft(30)
        val gateway = RecordingGateway(ThresholdChangeRequestSendResult.NO_REACHABLE_NODE)
        val controller = WearThresholdSettingsController(
            repository = repository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory { REQUEST_ID },
        )
        controller.save()
        val beforeReconciliation = repository.state.first()

        controller.updateDraft(22)

        val afterReconciliation = repository.state.first()
        assertEquals(30, afterReconciliation.thresholdDraftPercent)
        assertEquals(
            beforeReconciliation.pendingThresholdChangeRequest,
            afterReconciliation.pendingThresholdChangeRequest,
        )
        assertEquals(
            beforeReconciliation.thresholdChangeStatus,
            afterReconciliation.thresholdChangeStatus,
        )
        assertEquals(ThresholdChangeStatus.SEND_FAILED, afterReconciliation.thresholdChangeStatus)
        assertEquals(1, gateway.requests.size)
    }

    @Test
    fun interruptedSendingRecoversForExplicitRetryWithTheSameRequestId() = runBlocking {
        val store = InMemoryDataStore(WearStateSanitizer.defaultValue())
        val firstRepository = ProtoWearStateRepository(store)
        firstRepository.applyPhoneState(phoneState(sequence = 10), receivedAtEpochMillis = 1_000L)
        firstRepository.updateThresholdDraft(30)
        firstRepository.prepareThresholdChange(REQUEST_ID)

        val restoredRepository = ProtoWearStateRepository(store)
        val recovered = restoredRepository.recoverInterruptedThresholdChange()
        val gateway = RecordingGateway(ThresholdChangeRequestSendResult.SENT)
        val restoredController = WearThresholdSettingsController(
            repository = restoredRepository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory { "unused" },
        )
        val retried = restoredController.retry()

        assertEquals(ThresholdChangeStatus.SEND_FAILED, recovered.thresholdChangeStatus)
        assertEquals(ThresholdChangeCommandResult.WAITING_FOR_RESULT, retried)
        assertEquals(1, gateway.requests.size)
        assertEquals(REQUEST_ID, gateway.requests.single().requestId)
    }

    @Test
    fun concurrentSavesPrepareAndSendOnlyOneRequest() = runBlocking {
        val repository = ProtoWearStateRepository(
            InMemoryDataStore(WearStateSanitizer.defaultValue())
        )
        repository.applyPhoneState(phoneState(sequence = 10), receivedAtEpochMillis = 1_000L)
        repository.updateThresholdDraft(30)
        val gateway = BlockingGateway()
        var idSequence = 0
        val controller = WearThresholdSettingsController(
            repository = repository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory {
                if (idSequence++ == 0) REQUEST_ID else SECOND_REQUEST_ID
            },
        )

        val first = async { controller.save() }
        gateway.started.await()
        val second = async { controller.save() }

        assertEquals(ThresholdChangeCommandResult.ALREADY_PENDING, second.await())
        gateway.release.complete(Unit)
        assertEquals(ThresholdChangeCommandResult.WAITING_FOR_RESULT, first.await())
        assertEquals(1, gateway.requests.size)
        assertEquals(REQUEST_ID, gateway.requests.single().requestId)
    }

    @Test
    fun concurrentRetriesAtomicallyReserveAndSendOnlyOneRequest() = runBlocking {
        val repository = ProtoWearStateRepository(
            InMemoryDataStore(WearStateSanitizer.defaultValue())
        )
        repository.applyPhoneState(phoneState(sequence = 10), receivedAtEpochMillis = 1_000L)
        repository.updateThresholdDraft(30)
        val failedController = WearThresholdSettingsController(
            repository = repository,
            gateway = RecordingGateway(ThresholdChangeRequestSendResult.NO_REACHABLE_NODE),
            requestIdFactory = ThresholdChangeRequestIdFactory { REQUEST_ID },
        )
        assertEquals(
            ThresholdChangeCommandResult.NO_REACHABLE_NODE,
            failedController.save(),
        )

        val gateway = BlockingGateway()
        val retryController = WearThresholdSettingsController(
            repository = repository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory { "unused" },
        )
        val first = async { retryController.retry() }
        gateway.started.await()
        val second = async { retryController.retry() }

        assertEquals(ThresholdChangeCommandResult.ALREADY_PENDING, second.await())
        assertEquals(ThresholdChangeStatus.SENDING, repository.state.first().thresholdChangeStatus)
        gateway.release.complete(Unit)
        assertEquals(ThresholdChangeCommandResult.WAITING_FOR_RESULT, first.await())
        assertEquals(1, gateway.requests.size)
        assertEquals(REQUEST_ID, gateway.requests.single().requestId)
        assertEquals(
            ThresholdChangeStatus.WAITING_RESULT,
            repository.state.first().thresholdChangeStatus,
        )
    }

    @Test
    fun appliedRetryConvergesWhenPhoneStateArrivesBeforeGatewayCompletion() = runBlocking {
        val repository = ProtoWearStateRepository(
            InMemoryDataStore(WearStateSanitizer.defaultValue())
        )
        repository.applyPhoneState(phoneState(sequence = 10), receivedAtEpochMillis = 1_000L)
        repository.updateThresholdDraft(30)
        repository.prepareThresholdChange(REQUEST_ID)
        repository.markThresholdChangeSendResult(REQUEST_ID, sent = true)
        repository.applyThresholdChangeResult(
            ThresholdChangeResult(
                requestId = REQUEST_ID,
                resultCode = ThresholdChangeResultCode.APPLIED,
                effectiveThresholdPercent = 30,
                phoneStateSequence = 11,
            )
        )
        val gateway = BlockingGateway()
        val controller = WearThresholdSettingsController(
            repository = repository,
            gateway = gateway,
            requestIdFactory = ThresholdChangeRequestIdFactory { "unused" },
        )

        val retry = async { controller.retry() }
        gateway.started.await()
        val confirmed = repository.applyPhoneState(
            phoneState(sequence = 11).copy(thresholdPercent = 30),
            receivedAtEpochMillis = 1_100L,
        )
        gateway.release.complete(Unit)
        val retryResult = retry.await()
        val finalState = repository.state.first()

        assertEquals(ThresholdChangeStatus.APPLIED, confirmed.state.thresholdChangeStatus)
        assertEquals(ThresholdChangeCommandResult.WAITING_FOR_RESULT, retryResult)
        assertEquals(1, gateway.requests.size)
        assertEquals(REQUEST_ID, gateway.requests.single().requestId)
        assertEquals(ThresholdChangeStatus.APPLIED, finalState.thresholdChangeStatus)
        assertEquals(null, finalState.pendingThresholdChangeRequest)
        assertEquals(null, finalState.thresholdChangeResult)
    }

    private fun phoneState(sequence: Long) = ReceivedPhoneState(
        schemaVersion = 1,
        sequence = sequence,
        levelPercent = 68,
        isCharging = false,
        capturedAtEpochMillis = 900L,
        thresholdPercent = 20,
        monitoringEnabled = true,
        sentAtEpochMillis = 950L,
    )

    private class RecordingGateway(
        vararg results: ThresholdChangeRequestSendResult,
    ) : ThresholdChangeRequestGateway {
        private val results = ArrayDeque(results.toList())
        val requests = mutableListOf<ThresholdChangeRequest>()
        var available = true

        override suspend fun isAvailable(): Boolean = available

        override suspend fun send(
            request: ThresholdChangeRequest,
        ): ThresholdChangeRequestSendResult {
            requests += request
            return results.removeFirst()
        }
    }

    private class BlockingGateway : ThresholdChangeRequestGateway {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val requests = mutableListOf<ThresholdChangeRequest>()

        override suspend fun send(
            request: ThresholdChangeRequest,
        ): ThresholdChangeRequestSendResult {
            requests += request
            started.complete(Unit)
            release.await()
            return ThresholdChangeRequestSendResult.SENT
        }
    }

    private class InMemoryDataStore<T>(initial: T) : DataStore<T> {
        private val values = MutableStateFlow(initial)
        private val mutex = Mutex()

        override val data: Flow<T> = values

        override suspend fun updateData(transform: suspend (t: T) -> T): T =
            mutex.withLock {
                transform(values.value).also { values.value = it }
            }
    }

    private companion object {
        const val REQUEST_ID = "550e8400-e29b-41d4-a716-446655440022"
        const val SECOND_REQUEST_ID = "550e8400-e29b-41d4-a716-446655440023"
    }
}

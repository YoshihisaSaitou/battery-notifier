package com.magicitengineer.batterynotifierandroidwearapp.application.settings

import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateRepository
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.ThresholdChangePreparationOutcome
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.ThresholdChangeRetryReservationOutcome
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeRequest

enum class ThresholdChangeRequestSendResult {
    SENT,
    NO_REACHABLE_NODE,
    API_UNAVAILABLE,
    FAILED,
}

fun interface ThresholdChangeRequestGateway {
    suspend fun send(request: ThresholdChangeRequest): ThresholdChangeRequestSendResult

    suspend fun isAvailable(): Boolean = true
}

fun interface ThresholdChangeRequestIdFactory {
    fun create(): String
}

enum class ThresholdChangeCommandResult {
    WAITING_FOR_RESULT,
    NO_PHONE_STATE,
    ALREADY_PENDING,
    NO_REACHABLE_NODE,
    API_UNAVAILABLE,
    FAILED,
}

class WearThresholdSettingsController(
    private val repository: WearStateRepository,
    private val gateway: ThresholdChangeRequestGateway,
    private val requestIdFactory: ThresholdChangeRequestIdFactory,
) {
    suspend fun updateDraft(thresholdPercent: Int) {
        repository.updateThresholdDraft(thresholdPercent)
    }

    suspend fun isAvailable(): Boolean = gateway.isAvailable()

    suspend fun save(): ThresholdChangeCommandResult {
        val prepared = repository.prepareThresholdChange(requestIdFactory.create())
        val request = when (prepared.outcome) {
            ThresholdChangePreparationOutcome.PREPARED ->
                checkNotNull(prepared.request)
            ThresholdChangePreparationOutcome.NO_PHONE_STATE ->
                return ThresholdChangeCommandResult.NO_PHONE_STATE
            ThresholdChangePreparationOutcome.ALREADY_PENDING ->
                return ThresholdChangeCommandResult.ALREADY_PENDING
        }
        return sendAndPersist(request)
    }

    suspend fun retry(): ThresholdChangeCommandResult {
        val reservation = repository.reserveThresholdChangeRetry()
        val request = when (reservation.outcome) {
            ThresholdChangeRetryReservationOutcome.RESERVED ->
                checkNotNull(reservation.request)
            ThresholdChangeRetryReservationOutcome.NO_PENDING_REQUEST ->
                return ThresholdChangeCommandResult.NO_PHONE_STATE
            ThresholdChangeRetryReservationOutcome.NOT_RETRYABLE ->
                return ThresholdChangeCommandResult.ALREADY_PENDING
        }
        return sendAndPersist(request)
    }

    suspend fun cancel() {
        repository.clearThresholdChange()
    }

    private suspend fun sendAndPersist(
        request: ThresholdChangeRequest,
    ): ThresholdChangeCommandResult {
        val sendResult = gateway.send(request)
        repository.markThresholdChangeSendResult(
            requestId = request.requestId,
            sent = sendResult == ThresholdChangeRequestSendResult.SENT,
        )
        return when (sendResult) {
            ThresholdChangeRequestSendResult.SENT ->
                ThresholdChangeCommandResult.WAITING_FOR_RESULT
            ThresholdChangeRequestSendResult.NO_REACHABLE_NODE ->
                ThresholdChangeCommandResult.NO_REACHABLE_NODE
            ThresholdChangeRequestSendResult.API_UNAVAILABLE ->
                ThresholdChangeCommandResult.API_UNAVAILABLE
            ThresholdChangeRequestSendResult.FAILED ->
                ThresholdChangeCommandResult.FAILED
        }
    }
}

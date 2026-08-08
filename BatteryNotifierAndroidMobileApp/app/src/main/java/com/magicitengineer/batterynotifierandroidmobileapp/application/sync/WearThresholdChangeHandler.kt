package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeRequest
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResult

data class WearThresholdChangeCoordinationResult(
    val processingResult: ThresholdChangeProcessingResult,
    val syncResult: MobileSyncCoordinationResult?,
)

fun interface WearThresholdChangeRunner {
    suspend fun applyWearThresholdChange(
        request: ThresholdChangeRequest,
    ): WearThresholdChangeCoordinationResult
}

enum class ThresholdChangeResultSendOutcome {
    SENT,
    FAILED,
    NOT_SENT_PHONE_STATE_UNAVAILABLE,
}

fun interface ThresholdChangeResultGateway {
    suspend fun send(
        nodeId: String,
        result: ThresholdChangeResult,
    ): ThresholdChangeResultSendOutcome
}

data class WearThresholdChangeHandleResult(
    val coordinationResult: WearThresholdChangeCoordinationResult,
    val sendOutcome: ThresholdChangeResultSendOutcome,
)

class WearThresholdChangeHandler(
    private val runner: WearThresholdChangeRunner,
    private val resultGateway: ThresholdChangeResultGateway,
) {
    suspend fun handle(
        sourceNodeId: String,
        request: ThresholdChangeRequest,
    ): WearThresholdChangeHandleResult {
        require(sourceNodeId.isNotBlank())
        val coordination = runner.applyWearThresholdChange(request)
        val result = coordination.processingResult.result
            ?: return WearThresholdChangeHandleResult(
                coordinationResult = coordination,
                sendOutcome = ThresholdChangeResultSendOutcome.NOT_SENT_PHONE_STATE_UNAVAILABLE,
            )
        return WearThresholdChangeHandleResult(
            coordinationResult = coordination,
            sendOutcome = resultGateway.send(
                sourceNodeId,
                result,
            ),
        )
    }
}

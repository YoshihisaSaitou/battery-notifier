package com.magicitengineer.batterynotifierandroidwearapp.application.sync

import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateApplyResult
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateRepository
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.PayloadValidationResult
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceiveErrorClassification
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.WearPayloadValidator

sealed interface WearDataItemProcessingResult {
    data class Applied(val result: WearStateApplyResult) : WearDataItemProcessingResult

    data object IgnoredUnknownPath : WearDataItemProcessingResult

    data class Rejected(
        val classification: ReceiveErrorClassification,
    ) : WearDataItemProcessingResult

    data class UnsupportedSchema(val receivedVersion: Int) : WearDataItemProcessingResult
}

class ProcessWearDataItem(
    private val repository: WearStateRepository,
) {
    suspend fun process(
        path: String,
        values: Map<String, Any?>,
        receivedAtEpochMillis: Long,
    ): WearDataItemProcessingResult = when (
        val validation = WearPayloadValidator.validate(path, values, receivedAtEpochMillis)
    ) {
        is PayloadValidationResult.ValidState -> WearDataItemProcessingResult.Applied(
            repository.applyPhoneState(validation.state, receivedAtEpochMillis)
        )

        is PayloadValidationResult.ValidEvent -> WearDataItemProcessingResult.Applied(
            repository.applyThresholdEvent(validation.event, receivedAtEpochMillis)
        )

        PayloadValidationResult.UnknownPath -> WearDataItemProcessingResult.IgnoredUnknownPath

        is PayloadValidationResult.UnsupportedSchema -> {
            repository.recordUnsupportedSchema(validation.receivedVersion)
            WearDataItemProcessingResult.UnsupportedSchema(validation.receivedVersion)
        }

        is PayloadValidationResult.Invalid -> {
            repository.recordInvalidPayload(validation.classification)
            WearDataItemProcessingResult.Rejected(validation.classification)
        }
    }
}

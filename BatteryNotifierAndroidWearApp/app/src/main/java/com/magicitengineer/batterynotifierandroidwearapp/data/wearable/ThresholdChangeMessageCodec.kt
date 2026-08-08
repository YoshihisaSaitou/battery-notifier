package com.magicitengineer.batterynotifierandroidwearapp.data.wearable

import com.google.android.gms.wearable.DataMap
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.MAX_THRESHOLD_CHANGE_REQUEST_ID_LENGTH
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.THRESHOLD_CHANGE_SCHEMA_VERSION
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeRequest
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeResult
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeResultCode
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.WearDataLayerContract
import java.util.UUID

sealed interface ThresholdChangeResultDecodeResult {
    data class Valid(val result: ThresholdChangeResult) : ThresholdChangeResultDecodeResult
    data class UnsupportedSchema(val schemaVersion: Int) : ThresholdChangeResultDecodeResult
    data object Invalid : ThresholdChangeResultDecodeResult
}

object ThresholdChangeResultValidator {
    fun validate(values: Map<String, Any?>): ThresholdChangeResultDecodeResult {
        val schemaVersion = values[WearDataLayerContract.KEY_SCHEMA_VERSION] as? Int
            ?: return ThresholdChangeResultDecodeResult.Invalid
        if (schemaVersion <= 0) {
            return ThresholdChangeResultDecodeResult.Invalid
        }
        if (schemaVersion != THRESHOLD_CHANGE_SCHEMA_VERSION) {
            return ThresholdChangeResultDecodeResult.UnsupportedSchema(schemaVersion)
        }
        val requestId = values[WearDataLayerContract.KEY_REQUEST_ID] as? String
            ?: return ThresholdChangeResultDecodeResult.Invalid
        val resultCodeValue = values[WearDataLayerContract.KEY_RESULT_CODE] as? String
            ?: return ThresholdChangeResultDecodeResult.Invalid
        val resultCode = ThresholdChangeResultCode.entries.firstOrNull {
            it.persistedValue == resultCodeValue
        } ?: return ThresholdChangeResultDecodeResult.Invalid
        val effectiveThreshold =
            values[WearDataLayerContract.KEY_EFFECTIVE_THRESHOLD_PERCENT] as? Int
                ?: return ThresholdChangeResultDecodeResult.Invalid
        val phoneStateSequence =
            values[WearDataLayerContract.KEY_PHONE_STATE_SEQUENCE] as? Long
                ?: return ThresholdChangeResultDecodeResult.Invalid
        if (
            requestId.length > MAX_THRESHOLD_CHANGE_REQUEST_ID_LENGTH ||
            runCatching { UUID.fromString(requestId) }.isFailure ||
            effectiveThreshold !in 5..100 ||
            phoneStateSequence < 1
        ) {
            return ThresholdChangeResultDecodeResult.Invalid
        }
        return ThresholdChangeResultDecodeResult.Valid(
            ThresholdChangeResult(
                requestId = requestId,
                resultCode = resultCode,
                effectiveThresholdPercent = effectiveThreshold,
                phoneStateSequence = phoneStateSequence,
            )
        )
    }
}

object WearThresholdChangeMessageCodec {
    fun encodeRequest(request: ThresholdChangeRequest): ByteArray = DataMap().apply {
        putInt(WearDataLayerContract.KEY_SCHEMA_VERSION, request.schemaVersion)
        putString(WearDataLayerContract.KEY_REQUEST_ID, request.requestId)
        putInt(WearDataLayerContract.KEY_THRESHOLD_PERCENT, request.thresholdPercent)
        putInt(
            WearDataLayerContract.KEY_EXPECTED_THRESHOLD_PERCENT,
            request.expectedThresholdPercent,
        )
    }.toByteArray()

    fun decodeResult(bytes: ByteArray): ThresholdChangeResultDecodeResult =
        runCatching {
            val dataMap = DataMap.fromByteArray(bytes)
            ThresholdChangeResultValidator.validate(
                dataMap.keySet().associateWith { key -> dataMap[key] }
            )
        }.getOrDefault(ThresholdChangeResultDecodeResult.Invalid)
}

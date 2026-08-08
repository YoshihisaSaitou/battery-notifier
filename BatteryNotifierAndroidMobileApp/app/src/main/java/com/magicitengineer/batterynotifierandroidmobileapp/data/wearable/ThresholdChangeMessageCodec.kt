package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import com.google.android.gms.wearable.DataMap
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.MAX_THRESHOLD_CHANGE_REQUEST_ID_LENGTH
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.THRESHOLD_CHANGE_SCHEMA_VERSION
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeRequest
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResult
import java.util.UUID

sealed interface ThresholdChangeRequestDecodeResult {
    data class Valid(val request: ThresholdChangeRequest) : ThresholdChangeRequestDecodeResult
    data class UnsupportedSchema(val schemaVersion: Int) : ThresholdChangeRequestDecodeResult
    data object Invalid : ThresholdChangeRequestDecodeResult
}

object ThresholdChangeRequestValidator {
    fun validate(values: Map<String, Any?>): ThresholdChangeRequestDecodeResult {
        val schemaVersion =
            values[BatteryDataLayerContractV1.Keys.SCHEMA_VERSION] as? Int
                ?: return ThresholdChangeRequestDecodeResult.Invalid
        if (schemaVersion <= 0) {
            return ThresholdChangeRequestDecodeResult.Invalid
        }
        if (schemaVersion != THRESHOLD_CHANGE_SCHEMA_VERSION) {
            return ThresholdChangeRequestDecodeResult.UnsupportedSchema(schemaVersion)
        }
        val requestId = values[BatteryDataLayerContractV1.Keys.REQUEST_ID] as? String
            ?: return ThresholdChangeRequestDecodeResult.Invalid
        val thresholdPercent =
            values[BatteryDataLayerContractV1.Keys.THRESHOLD_PERCENT] as? Int
                ?: return ThresholdChangeRequestDecodeResult.Invalid
        val expectedThresholdPercent =
            values[BatteryDataLayerContractV1.Keys.EXPECTED_THRESHOLD_PERCENT] as? Int
                ?: return ThresholdChangeRequestDecodeResult.Invalid
        if (
            requestId.length > MAX_THRESHOLD_CHANGE_REQUEST_ID_LENGTH ||
            runCatching { UUID.fromString(requestId) }.isFailure ||
            thresholdPercent !in 5..100 ||
            expectedThresholdPercent !in 5..100
        ) {
            return ThresholdChangeRequestDecodeResult.Invalid
        }
        return ThresholdChangeRequestDecodeResult.Valid(
            ThresholdChangeRequest(
                schemaVersion = schemaVersion,
                requestId = requestId,
                thresholdPercent = thresholdPercent,
                expectedThresholdPercent = expectedThresholdPercent,
            )
        )
    }
}

object MobileThresholdChangeMessageCodec {
    fun decodeRequest(bytes: ByteArray): ThresholdChangeRequestDecodeResult =
        runCatching {
            val dataMap = DataMap.fromByteArray(bytes)
            ThresholdChangeRequestValidator.validate(
                dataMap.keySet().associateWith { key -> dataMap[key] }
            )
        }.getOrDefault(ThresholdChangeRequestDecodeResult.Invalid)

    fun encodeResult(result: ThresholdChangeResult): ByteArray = DataMap().apply {
        putInt(
            BatteryDataLayerContractV1.Keys.SCHEMA_VERSION,
            THRESHOLD_CHANGE_SCHEMA_VERSION,
        )
        putString(BatteryDataLayerContractV1.Keys.REQUEST_ID, result.requestId)
        putString(
            BatteryDataLayerContractV1.Keys.RESULT_CODE,
            result.resultCode.persistedValue,
        )
        putInt(
            BatteryDataLayerContractV1.Keys.EFFECTIVE_THRESHOLD_PERCENT,
            result.effectiveThresholdPercent,
        )
        putLong(
            BatteryDataLayerContractV1.Keys.PHONE_STATE_SEQUENCE,
            result.phoneStateSequence,
        )
    }.toByteArray()
}

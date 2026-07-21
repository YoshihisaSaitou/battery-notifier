package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.PhoneStateSync
import java.util.UUID

object BatteryDataLayerContractV1 {
    const val SCHEMA_VERSION = 1
    const val PHONE_STATE_PATH = "/battery-notifier/v1/phone-state"
    const val THRESHOLD_EVENT_PATH = "/battery-notifier/v1/threshold-event"
    const val REQUEST_STATE_PATH = "/battery-notifier/v1/request-state"

    object Keys {
        const val SCHEMA_VERSION = "schemaVersion"
        const val SEQUENCE = "sequence"
        const val LEVEL_PERCENT = "levelPercent"
        const val IS_CHARGING = "isCharging"
        const val CAPTURED_AT_EPOCH_MILLIS = "capturedAtEpochMillis"
        const val THRESHOLD_PERCENT = "thresholdPercent"
        const val MONITORING_ENABLED = "monitoringEnabled"
        const val SENT_AT_EPOCH_MILLIS = "sentAtEpochMillis"
        const val EVENT_ID = "eventId"
        const val OCCURRED_AT_EPOCH_MILLIS = "occurredAtEpochMillis"
        const val EXPIRES_AT_EPOCH_MILLIS = "expiresAtEpochMillis"
    }
}

sealed interface DataLayerValue {
    data class IntValue(val value: Int) : DataLayerValue

    data class LongValue(val value: Long) : DataLayerValue

    data class BooleanValue(val value: Boolean) : DataLayerValue

    data class StringValue(val value: String) : DataLayerValue
}

data class DataLayerPayload(
    val path: String,
    val values: Map<String, DataLayerValue>,
    val urgent: Boolean,
) {
    init {
        require(path.startsWith('/'))
        require(values.isNotEmpty())
    }
}

object MobileDataLayerPayloadMapper {
    fun phoneState(state: PhoneStateSync): DataLayerPayload = DataLayerPayload(
        path = BatteryDataLayerContractV1.PHONE_STATE_PATH,
        values = mapOf(
            BatteryDataLayerContractV1.Keys.SCHEMA_VERSION to
                DataLayerValue.IntValue(BatteryDataLayerContractV1.SCHEMA_VERSION),
            BatteryDataLayerContractV1.Keys.SEQUENCE to
                DataLayerValue.LongValue(state.snapshot.sequence),
            BatteryDataLayerContractV1.Keys.LEVEL_PERCENT to
                DataLayerValue.IntValue(state.snapshot.levelPercent),
            BatteryDataLayerContractV1.Keys.IS_CHARGING to
                DataLayerValue.BooleanValue(state.snapshot.isCharging),
            BatteryDataLayerContractV1.Keys.CAPTURED_AT_EPOCH_MILLIS to
                DataLayerValue.LongValue(state.snapshot.capturedAtEpochMillis),
            BatteryDataLayerContractV1.Keys.THRESHOLD_PERCENT to
                DataLayerValue.IntValue(state.thresholdPercent),
            BatteryDataLayerContractV1.Keys.MONITORING_ENABLED to
                DataLayerValue.BooleanValue(state.monitoringEnabled),
            BatteryDataLayerContractV1.Keys.SENT_AT_EPOCH_MILLIS to
                DataLayerValue.LongValue(state.sentAtEpochMillis),
        ),
        urgent = true,
    )

    fun thresholdEvent(event: ThresholdReachedEvent): DataLayerPayload {
        require(runCatching { UUID.fromString(event.eventId) }.isSuccess) {
            "eventId must be a UUID"
        }
        return DataLayerPayload(
            path = BatteryDataLayerContractV1.THRESHOLD_EVENT_PATH,
            values = mapOf(
                BatteryDataLayerContractV1.Keys.SCHEMA_VERSION to
                    DataLayerValue.IntValue(BatteryDataLayerContractV1.SCHEMA_VERSION),
                BatteryDataLayerContractV1.Keys.EVENT_ID to
                    DataLayerValue.StringValue(event.eventId),
                BatteryDataLayerContractV1.Keys.SEQUENCE to
                    DataLayerValue.LongValue(event.sequence),
                BatteryDataLayerContractV1.Keys.LEVEL_PERCENT to
                    DataLayerValue.IntValue(event.levelPercent),
                BatteryDataLayerContractV1.Keys.THRESHOLD_PERCENT to
                    DataLayerValue.IntValue(event.thresholdPercent),
                BatteryDataLayerContractV1.Keys.OCCURRED_AT_EPOCH_MILLIS to
                    DataLayerValue.LongValue(event.occurredAtEpochMillis),
                BatteryDataLayerContractV1.Keys.EXPIRES_AT_EPOCH_MILLIS to
                    DataLayerValue.LongValue(event.expiresAtEpochMillis),
            ),
            urgent = true,
        )
    }
}

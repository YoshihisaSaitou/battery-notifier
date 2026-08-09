package com.magicitengineer.batterynotifierandroidwearapp.domain.sync

import java.util.UUID

object WearDataLayerContract {
    const val PHONE_STATE_PATH = "/battery-notifier/v1/phone-state"
    const val THRESHOLD_EVENT_PATH = "/battery-notifier/v1/threshold-event"
    const val FULL_CHARGE_EVENT_PATH = "/battery-notifier/v1/full-charge-event"
    const val REQUEST_STATE_PATH = "/battery-notifier/v1/request-state"
    const val CHANGE_THRESHOLD_PATH = "/battery-notifier/v1/change-threshold"
    const val CHANGE_THRESHOLD_RESULT_PATH =
        "/battery-notifier/v1/change-threshold-result"
    const val CHANGE_FULL_CHARGE_SETTING_PATH =
        "/battery-notifier/v1/change-full-charge-setting"
    const val MOBILE_THRESHOLD_WRITER_CAPABILITY = "battery_notifier_threshold_writer"
    const val MOBILE_FULL_CHARGE_SETTING_WRITER_CAPABILITY =
        "battery_notifier_full_charge_setting_writer_v1"
    const val PATH_PREFIX = "/battery-notifier/v1/"

    const val KEY_SCHEMA_VERSION = "schemaVersion"
    const val KEY_SEQUENCE = "sequence"
    const val KEY_LEVEL_PERCENT = "levelPercent"
    const val KEY_IS_CHARGING = "isCharging"
    const val KEY_CAPTURED_AT = "capturedAtEpochMillis"
    const val KEY_THRESHOLD_PERCENT = "thresholdPercent"
    const val KEY_MONITORING_ENABLED = "monitoringEnabled"
    const val KEY_SENT_AT = "sentAtEpochMillis"
    const val KEY_EVENT_ID = "eventId"
    const val KEY_OCCURRED_AT = "occurredAtEpochMillis"
    const val KEY_EXPIRES_AT = "expiresAtEpochMillis"
    const val KEY_REQUEST_ID = "requestId"
    const val KEY_EXPECTED_THRESHOLD_PERCENT = "expectedThresholdPercent"
    const val KEY_RESULT_CODE = "resultCode"
    const val KEY_EFFECTIVE_THRESHOLD_PERCENT = "effectiveThresholdPercent"
    const val KEY_PHONE_STATE_SEQUENCE = "phoneStateSequence"
    const val KEY_FULL_CHARGE_NOTIFICATION_ENABLED = "fullChargeNotificationEnabled"
    const val KEY_EXPECTED_FULL_CHARGE_NOTIFICATION_ENABLED =
        "expectedFullChargeNotificationEnabled"
}

sealed interface PayloadValidationResult {
    data class ValidState(val state: ReceivedPhoneState) : PayloadValidationResult

    data class ValidEvent(val event: ReceivedThresholdEvent) : PayloadValidationResult

    data object UnknownPath : PayloadValidationResult

    data class UnsupportedSchema(val receivedVersion: Int) : PayloadValidationResult

    data class Invalid(
        val classification: ReceiveErrorClassification,
    ) : PayloadValidationResult
}

object WearPayloadValidator {
    fun validate(
        path: String,
        values: Map<String, Any?>,
        receivedAtEpochMillis: Long,
    ): PayloadValidationResult {
        require(receivedAtEpochMillis > 0)
        if (
            path != WearDataLayerContract.PHONE_STATE_PATH &&
            path != WearDataLayerContract.THRESHOLD_EVENT_PATH &&
            path != WearDataLayerContract.FULL_CHARGE_EVENT_PATH
        ) {
            return PayloadValidationResult.UnknownPath
        }

        val schemaVersion = values[WearDataLayerContract.KEY_SCHEMA_VERSION] as? Int
            ?: return invalidType()
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return PayloadValidationResult.UnsupportedSchema(schemaVersion)
        }
        return when (path) {
            WearDataLayerContract.PHONE_STATE_PATH -> validateState(
                values = values,
                schemaVersion = schemaVersion,
                receivedAtEpochMillis = receivedAtEpochMillis,
            )

            WearDataLayerContract.THRESHOLD_EVENT_PATH,
            WearDataLayerContract.FULL_CHARGE_EVENT_PATH -> validateEvent(
                values = values,
                schemaVersion = schemaVersion,
                kind = if (path == WearDataLayerContract.FULL_CHARGE_EVENT_PATH) {
                    AlertEventKind.FULL_CHARGE
                } else {
                    AlertEventKind.LOW_BATTERY
                },
            )

            else -> PayloadValidationResult.UnknownPath
        }
    }

    private fun validateState(
        values: Map<String, Any?>,
        schemaVersion: Int,
        receivedAtEpochMillis: Long,
    ): PayloadValidationResult {
        val sequence = values[WearDataLayerContract.KEY_SEQUENCE] as? Long ?: return invalidType()
        val levelPercent = values[WearDataLayerContract.KEY_LEVEL_PERCENT] as? Int
            ?: return invalidType()
        val isCharging = values[WearDataLayerContract.KEY_IS_CHARGING] as? Boolean
            ?: return invalidType()
        val capturedAt = values[WearDataLayerContract.KEY_CAPTURED_AT] as? Long
            ?: return invalidType()
        val thresholdPercent = values[WearDataLayerContract.KEY_THRESHOLD_PERCENT] as? Int
            ?: return invalidType()
        val monitoringEnabled = values[WearDataLayerContract.KEY_MONITORING_ENABLED] as? Boolean
            ?: return invalidType()
        val sentAt = values[WearDataLayerContract.KEY_SENT_AT] as? Long ?: return invalidType()
        val fullChargeValue = values[
            WearDataLayerContract.KEY_FULL_CHARGE_NOTIFICATION_ENABLED
        ]
        val fullChargeNotificationEnabled = when (fullChargeValue) {
            null -> false
            is Boolean -> fullChargeValue
            else -> return invalidType()
        }

        if (sequence < 1 || levelPercent !in 0..100 || thresholdPercent !in 5..100) {
            return PayloadValidationResult.Invalid(ReceiveErrorClassification.OUT_OF_RANGE)
        }
        if (
            capturedAt <= 0 ||
            sentAt <= 0 ||
            (capturedAt > receivedAtEpochMillis &&
                capturedAt - receivedAtEpochMillis > MAX_FUTURE_SKEW_MILLIS)
        ) {
            return PayloadValidationResult.Invalid(ReceiveErrorClassification.INVALID_TIME)
        }
        return PayloadValidationResult.ValidState(
            ReceivedPhoneState(
                schemaVersion = schemaVersion,
                sequence = sequence,
                levelPercent = levelPercent,
                isCharging = isCharging,
                capturedAtEpochMillis = capturedAt,
                thresholdPercent = thresholdPercent,
                monitoringEnabled = monitoringEnabled,
                sentAtEpochMillis = sentAt,
                fullChargeNotificationEnabled = fullChargeNotificationEnabled,
            )
        )
    }

    private fun validateEvent(
        values: Map<String, Any?>,
        schemaVersion: Int,
        kind: AlertEventKind,
    ): PayloadValidationResult {
        val eventId = values[WearDataLayerContract.KEY_EVENT_ID] as? String ?: return invalidType()
        val sequence = values[WearDataLayerContract.KEY_SEQUENCE] as? Long ?: return invalidType()
        val levelPercent = values[WearDataLayerContract.KEY_LEVEL_PERCENT] as? Int
            ?: return invalidType()
        val thresholdPercent = values[WearDataLayerContract.KEY_THRESHOLD_PERCENT] as? Int
            ?: return invalidType()
        val occurredAt = values[WearDataLayerContract.KEY_OCCURRED_AT] as? Long
            ?: return invalidType()
        val expiresAt = values[WearDataLayerContract.KEY_EXPIRES_AT] as? Long
            ?: return invalidType()

        if (eventId.length > MAX_EVENT_ID_LENGTH || runCatching { UUID.fromString(eventId) }.isFailure) {
            return PayloadValidationResult.Invalid(ReceiveErrorClassification.INVALID_EVENT_ID)
        }
        if (sequence < 1 || levelPercent !in 0..100 || thresholdPercent !in 5..100) {
            return PayloadValidationResult.Invalid(ReceiveErrorClassification.OUT_OF_RANGE)
        }
        if (
            occurredAt <= 0 ||
            expiresAt <= occurredAt ||
            expiresAt - occurredAt > MAX_EVENT_EXPIRY_MILLIS
        ) {
            return PayloadValidationResult.Invalid(ReceiveErrorClassification.INVALID_TIME)
        }
        return PayloadValidationResult.ValidEvent(
            ReceivedThresholdEvent(
                schemaVersion = schemaVersion,
                eventId = eventId,
                sequence = sequence,
                levelPercent = levelPercent,
                thresholdPercent = thresholdPercent,
                occurredAtEpochMillis = occurredAt,
                expiresAtEpochMillis = expiresAt,
                kind = kind,
            )
        )
    }

    private fun invalidType() = PayloadValidationResult.Invalid(
        ReceiveErrorClassification.MISSING_OR_WRONG_TYPE
    )
}

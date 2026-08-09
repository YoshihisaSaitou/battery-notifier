package com.magicitengineer.batterynotifierandroidwearapp.domain.sync

data class ReceivedPhoneState(
    val schemaVersion: Int,
    val sequence: Long,
    val levelPercent: Int,
    val isCharging: Boolean,
    val capturedAtEpochMillis: Long,
    val thresholdPercent: Int,
    val monitoringEnabled: Boolean,
    val sentAtEpochMillis: Long,
    val fullChargeNotificationEnabled: Boolean = false,
) {
    init {
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION)
        require(sequence >= 1)
        require(levelPercent in 0..100)
        require(capturedAtEpochMillis > 0)
        require(thresholdPercent in 5..100)
        require(sentAtEpochMillis > 0)
    }
}

enum class AlertEventKind(val persistedValue: String) {
    LOW_BATTERY("low_battery"),
    FULL_CHARGE("full_charge"),
}

data class ReceivedThresholdEvent(
    val schemaVersion: Int,
    val eventId: String,
    val sequence: Long,
    val levelPercent: Int,
    val thresholdPercent: Int,
    val occurredAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val kind: AlertEventKind = AlertEventKind.LOW_BATTERY,
) {
    init {
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION)
        require(eventId.isNotBlank() && eventId.length <= MAX_EVENT_ID_LENGTH)
        require(sequence >= 1)
        require(levelPercent in 0..100)
        require(thresholdPercent in 5..100)
        require(occurredAtEpochMillis > 0)
        require(expiresAtEpochMillis > occurredAtEpochMillis)
        require(expiresAtEpochMillis - occurredAtEpochMillis <= MAX_EVENT_EXPIRY_MILLIS)
    }
}

enum class NotificationDisposition(val persistedValue: String) {
    NONE("none"),
    PENDING("pending"),
    POSTED("posted"),
    PERMISSION_DENIED("permission_denied"),
    RESERVED_FAILED("reserved_failed"),
    FAILED_EXHAUSTED("failed_exhausted"),
    EXPIRED("expired"),
    CLOCK_SKEW("clock_skew"),
}

enum class ReceiveErrorClassification(val persistedValue: String) {
    MISSING_OR_WRONG_TYPE("missing_or_wrong_type"),
    OUT_OF_RANGE("out_of_range"),
    INVALID_TIME("invalid_time"),
    INVALID_EVENT_ID("invalid_event_id"),
    DATA_MAP_ERROR("data_map_error"),
}

const val SUPPORTED_SCHEMA_VERSION = 1
const val MAX_EVENT_ID_LENGTH = 64
const val MAX_FUTURE_SKEW_MILLIS = 5 * 60 * 1_000L
const val MAX_EVENT_EXPIRY_MILLIS = 15 * 60 * 1_000L
const val MAX_WEAR_NOTIFICATION_POST_ATTEMPTS = 3

fun interface EpochMillisClock {
    fun now(): Long
}

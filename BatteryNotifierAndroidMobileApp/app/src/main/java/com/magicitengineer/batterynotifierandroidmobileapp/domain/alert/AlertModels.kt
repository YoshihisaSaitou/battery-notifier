package com.magicitengineer.batterynotifierandroidmobileapp.domain.alert

data class AlertRule(
    val thresholdPercent: Int = DEFAULT_THRESHOLD_PERCENT,
    val monitoringEnabled: Boolean = false,
    val notifyIfAlreadyBelowOnStart: Boolean = false,
    val rearmHysteresisPercent: Int = DEFAULT_HYSTERESIS_PERCENT,
    val fullChargeNotificationEnabled: Boolean = false,
) {
    init {
        require(thresholdPercent in MIN_THRESHOLD_PERCENT..MAX_THRESHOLD_PERCENT) {
            "thresholdPercent must be in $MIN_THRESHOLD_PERCENT..$MAX_THRESHOLD_PERCENT"
        }
        require(rearmHysteresisPercent in MIN_HYSTERESIS_PERCENT..MAX_HYSTERESIS_PERCENT) {
            "rearmHysteresisPercent must be in $MIN_HYSTERESIS_PERCENT..$MAX_HYSTERESIS_PERCENT"
        }
    }

    val rearmLevelPercent: Int
        get() = (thresholdPercent + rearmHysteresisPercent).coerceAtMost(100)

    companion object {
        const val DEFAULT_THRESHOLD_PERCENT = 20
        const val DEFAULT_HYSTERESIS_PERCENT = 2
        const val MIN_THRESHOLD_PERCENT = 5
        const val MAX_THRESHOLD_PERCENT = 100
        const val MIN_HYSTERESIS_PERCENT = 1
        const val MAX_HYSTERESIS_PERCENT = 10
    }
}

enum class AlertEventKind {
    LOW_BATTERY,
    FULL_CHARGE,
}

data class AlertState(
    val armed: Boolean = true,
    val previousLevelPercent: Int? = null,
    val lastEventId: String? = null,
    val lastTriggeredAtEpochMillis: Long? = null,
) {
    init {
        require(previousLevelPercent == null || previousLevelPercent in 0..100) {
            "previousLevelPercent must be null or in 0..100"
        }
        require(lastTriggeredAtEpochMillis == null || lastTriggeredAtEpochMillis > 0) {
            "lastTriggeredAtEpochMillis must be null or positive"
        }
    }
}

data class ThresholdReachedEvent(
    val eventId: String,
    val levelPercent: Int,
    val thresholdPercent: Int,
    val occurredAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val sequence: Long,
    val kind: AlertEventKind = AlertEventKind.LOW_BATTERY,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(eventId.length <= 64) { "eventId must be at most 64 characters" }
        require(levelPercent in 0..100) { "levelPercent must be in 0..100" }
        require(thresholdPercent in AlertRule.MIN_THRESHOLD_PERCENT..AlertRule.MAX_THRESHOLD_PERCENT) {
            "thresholdPercent is outside the supported range"
        }
        require(occurredAtEpochMillis > 0) { "occurredAtEpochMillis must be positive" }
        require(expiresAtEpochMillis > occurredAtEpochMillis) {
            "expiresAtEpochMillis must be later than occurredAtEpochMillis"
        }
        require(expiresAtEpochMillis - occurredAtEpochMillis <= MAX_EXPIRY_MILLIS) {
            "event expiry must not exceed 15 minutes"
        }
        require(sequence >= 1) { "sequence must be at least 1" }
    }

    companion object {
        const val DEFAULT_EXPIRY_MILLIS = 5 * 60 * 1000L
        const val MAX_EXPIRY_MILLIS = 15 * 60 * 1000L
    }
}

data class AlertEvaluation(
    val state: AlertState,
    val event: ThresholdReachedEvent?,
)

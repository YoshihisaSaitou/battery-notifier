package com.magicitengineer.batterynotifierandroidmobileapp.domain.settings

import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import java.util.UUID

const val THRESHOLD_CHANGE_SCHEMA_VERSION = 1
const val MAX_THRESHOLD_CHANGE_REQUEST_ID_LENGTH = 64

data class ThresholdChangeRequest(
    val schemaVersion: Int,
    val requestId: String,
    val thresholdPercent: Int,
    val expectedThresholdPercent: Int,
) {
    init {
        require(schemaVersion == THRESHOLD_CHANGE_SCHEMA_VERSION)
        require(requestId.length <= MAX_THRESHOLD_CHANGE_REQUEST_ID_LENGTH)
        require(runCatching { UUID.fromString(requestId) }.isSuccess)
        require(thresholdPercent in 5..100)
        require(expectedThresholdPercent in 5..100)
    }
}

enum class ThresholdChangeResultCode(val persistedValue: String) {
    APPLIED("APPLIED"),
    CONFLICT("CONFLICT"),
    REJECTED("REJECTED"),
}

data class ThresholdChangeResult(
    val requestId: String,
    val resultCode: ThresholdChangeResultCode,
    val effectiveThresholdPercent: Int,
    val phoneStateSequence: Long,
) {
    init {
        require(requestId.length <= MAX_THRESHOLD_CHANGE_REQUEST_ID_LENGTH)
        require(runCatching { UUID.fromString(requestId) }.isSuccess)
        require(effectiveThresholdPercent in 5..100)
        require(phoneStateSequence >= 1)
    }
}

enum class ThresholdChangeProcessingOutcome {
    PROCESSED,
    PHONE_STATE_UNAVAILABLE,
}

data class ThresholdChangeProcessingResult(
    val state: MobilePersistentState,
    val result: ThresholdChangeResult?,
    val replayed: Boolean,
    val settingChanged: Boolean,
    val outcome: ThresholdChangeProcessingOutcome = ThresholdChangeProcessingOutcome.PROCESSED,
) {
    init {
        require((result != null) == (outcome == ThresholdChangeProcessingOutcome.PROCESSED))
        require(!replayed || outcome == ThresholdChangeProcessingOutcome.PROCESSED)
        require(!settingChanged || outcome == ThresholdChangeProcessingOutcome.PROCESSED)
    }
}

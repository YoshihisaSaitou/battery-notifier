package com.magicitengineer.batterynotifierandroidwearapp.domain.settings

import java.util.UUID

const val THRESHOLD_CHANGE_SCHEMA_VERSION = 1
const val MAX_THRESHOLD_CHANGE_REQUEST_ID_LENGTH = 64

data class ThresholdChangeRequest(
    val schemaVersion: Int = THRESHOLD_CHANGE_SCHEMA_VERSION,
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

enum class ThresholdChangeStatus(val persistedValue: String) {
    IDLE("idle"),
    SENDING("sending"),
    WAITING_RESULT("waiting_result"),
    SEND_FAILED("send_failed"),
    APPLIED_WAITING_STATE("applied_waiting_state"),
    APPLIED("applied"),
    CONFLICT("conflict"),
    REJECTED("rejected"),
}

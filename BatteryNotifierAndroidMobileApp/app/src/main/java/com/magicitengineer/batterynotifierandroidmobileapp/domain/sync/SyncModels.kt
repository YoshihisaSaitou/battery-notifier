package com.magicitengineer.batterynotifierandroidmobileapp.domain.sync

import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent

data class PhoneStateSync(
    val snapshot: BatterySnapshot,
    val thresholdPercent: Int,
    val monitoringEnabled: Boolean,
    val sentAtEpochMillis: Long,
    val fullChargeNotificationEnabled: Boolean = false,
) {
    init {
        require(thresholdPercent in 5..100)
        require(sentAtEpochMillis > 0)
    }
}

enum class SyncFailureClassification(val persistedValue: String) {
    API_UNAVAILABLE("api_unavailable"),
    TASK_FAILURE("task_failure"),
    SECURITY_ERROR("security_error"),
    INVALID_OUTBOX("invalid_outbox"),
    UNEXPECTED_ERROR("unexpected_error"),
}

sealed interface DataLayerPutResult {
    data object Accepted : DataLayerPutResult

    data class Rejected(
        val classification: SyncFailureClassification,
    ) : DataLayerPutResult
}

interface MobileSyncGateway {
    suspend fun putPhoneState(state: PhoneStateSync): DataLayerPutResult

    suspend fun putThresholdEvent(event: ThresholdReachedEvent): DataLayerPutResult
}

fun interface EpochMillisClock {
    fun now(): Long
}

data class SyncDeliveryUpdate(
    val confirmedStateSequence: Long? = null,
    val confirmedEventId: String? = null,
    val confirmedEventSequence: Long? = null,
    val completedAtEpochMillis: Long,
    val failureClassification: SyncFailureClassification? = null,
) {
    init {
        require(confirmedStateSequence == null || confirmedStateSequence >= 1)
        require((confirmedEventId == null) == (confirmedEventSequence == null))
        require(confirmedEventId == null || confirmedEventId.isNotBlank())
        require(confirmedEventSequence == null || confirmedEventSequence >= 1)
        require(completedAtEpochMillis > 0)
        require(
            confirmedStateSequence != null ||
                confirmedEventId != null ||
                failureClassification != null
        ) { "A delivery update must contain a confirmation or failure" }
    }
}

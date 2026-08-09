package com.magicitengineer.batterynotifierandroidmobileapp.domain.state

import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.notification.MobileNotificationDisposition
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResult

data class MobilePersistentState(
    val storageSchemaVersion: Int = CURRENT_STORAGE_SCHEMA_VERSION,
    val alertRule: AlertRule = AlertRule(),
    val onboardingCompleted: Boolean = false,
    val resumeRequired: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    val lastSnapshot: BatterySnapshot? = null,
    val sequence: Long = 0,
    val alertState: AlertState = AlertState(),
    val lastMobileNotifiedEventId: String? = null,
    val pendingMobileNotification: ThresholdReachedEvent? = null,
    val lastMobileNotificationEventId: String? = null,
    val mobileNotificationDisposition: MobileNotificationDisposition =
        MobileNotificationDisposition.NONE,
    val pendingStateSequence: Long = 0,
    val pendingEvent: ThresholdReachedEvent? = null,
    val lastSyncSuccessAtEpochMillis: Long? = null,
    val lastSyncErrorClassification: String? = null,
    val invalidInputCount: Long = 0,
    val unsupportedSchemaCount: Long = 0,
    val lastThresholdChangeResult: ThresholdChangeResult? = null,
    val fullChargeArmed: Boolean = false,
) {
    init {
        require(storageSchemaVersion == CURRENT_STORAGE_SCHEMA_VERSION)
        require(!alertRule.monitoringEnabled || !resumeRequired) {
            "monitoring cannot be active while resume is required"
        }
        require(sequence >= 0)
        require(pendingStateSequence in 0..sequence)
        require(lastSyncSuccessAtEpochMillis == null || lastSyncSuccessAtEpochMillis > 0)
        require(invalidInputCount >= 0)
        require(unsupportedSchemaCount >= 0)
    }

    companion object {
        const val CURRENT_STORAGE_SCHEMA_VERSION = 1
    }
}

package com.magicitengineer.batterynotifierandroidwearapp.domain.state

import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedPhoneState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent

data class WearPersistentState(
    val storageSchemaVersion: Int = CURRENT_STORAGE_SCHEMA_VERSION,
    val lastPhoneState: ReceivedPhoneState? = null,
    val phoneStateReceivedAtEpochMillis: Long? = null,
    val lastEvent: ReceivedThresholdEvent? = null,
    val lastEventSequence: Long = 0,
    val lastProcessedEventId: String? = null,
    val eventProcessedAtEpochMillis: Long? = null,
    val notificationDisposition: NotificationDisposition = NotificationDisposition.NONE,
    val invalidPayloadCount: Long = 0,
    val unsupportedSchemaCount: Long = 0,
    val duplicateCount: Long = 0,
    val outOfOrderCount: Long = 0,
    val lastReceiveError: String? = null,
    val lastUnsupportedSchemaVersion: Int? = null,
    val notificationPermissionRequested: Boolean = false,
) {
    init {
        require(storageSchemaVersion == CURRENT_STORAGE_SCHEMA_VERSION)
        require((lastPhoneState == null) == (phoneStateReceivedAtEpochMillis == null))
        require(phoneStateReceivedAtEpochMillis == null || phoneStateReceivedAtEpochMillis > 0)
        require(lastEventSequence >= 0)
        require(lastEvent == null || lastEvent.sequence == lastEventSequence)
        require((lastProcessedEventId == null) == (eventProcessedAtEpochMillis == null))
        require(lastProcessedEventId == null || lastProcessedEventId.isNotBlank())
        require(eventProcessedAtEpochMillis == null || eventProcessedAtEpochMillis > 0)
        require(invalidPayloadCount >= 0)
        require(unsupportedSchemaCount >= 0)
        require(duplicateCount >= 0)
        require(outOfOrderCount >= 0)
    }

    companion object {
        const val CURRENT_STORAGE_SCHEMA_VERSION = 1
    }
}

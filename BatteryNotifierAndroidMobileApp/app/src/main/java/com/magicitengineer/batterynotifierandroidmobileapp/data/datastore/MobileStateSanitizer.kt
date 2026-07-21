package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.AlertStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import java.util.UUID

object MobileStateSanitizer {
    fun defaultValue(): MobileStateProto = MobileStateProto.newBuilder()
        .setStorageSchemaVersion(MobilePersistentState.CURRENT_STORAGE_SCHEMA_VERSION)
        .setThresholdPercent(AlertRule.DEFAULT_THRESHOLD_PERCENT)
        .setRearmHysteresisPercent(AlertRule.DEFAULT_HYSTERESIS_PERCENT)
        .setAlertState(AlertStateProto.newBuilder().setArmed(true).build())
        .build()

    fun sanitize(input: MobileStateProto): MobileStateProto {
        val builder = input.toBuilder()
            .setStorageSchemaVersion(MobilePersistentState.CURRENT_STORAGE_SCHEMA_VERSION)
            .setThresholdPercent(
                input.thresholdPercent.takeIf {
                    it in AlertRule.MIN_THRESHOLD_PERCENT..AlertRule.MAX_THRESHOLD_PERCENT
                } ?: AlertRule.DEFAULT_THRESHOLD_PERCENT
            )
            .setRearmHysteresisPercent(
                input.rearmHysteresisPercent.takeIf {
                    it in AlertRule.MIN_HYSTERESIS_PERCENT..AlertRule.MAX_HYSTERESIS_PERCENT
                } ?: AlertRule.DEFAULT_HYSTERESIS_PERCENT
            )
            .setSequence(input.sequence.coerceAtLeast(0))
            .setPendingStateSequence(input.pendingStateSequence.coerceIn(0, input.sequence.coerceAtLeast(0)))
            .setLastSyncSuccessAtEpochMillis(input.lastSyncSuccessAtEpochMillis.coerceAtLeast(0))
            .setInvalidInputCount(input.invalidInputCount.coerceAtLeast(0))
            .setUnsupportedSchemaCount(input.unsupportedSchemaCount.coerceAtLeast(0))

        if (!input.hasAlertState()) {
            builder.setAlertState(AlertStateProto.newBuilder().setArmed(true).build())
        } else if (
            input.alertState.hasPreviousLevel &&
            input.alertState.previousLevelPercent !in 0..100
        ) {
            builder.setAlertState(
                input.alertState.toBuilder()
                    .setHasPreviousLevel(false)
                    .setPreviousLevelPercent(0)
                    .build()
            )
        }

        if (input.hasLastSnapshot() && !input.lastSnapshot.isValid(input.sequence)) {
            builder.clearLastSnapshot()
        }
        if (input.hasPendingEvent() && !input.pendingEvent.isValid()) {
            builder.clearPendingEvent()
        }
        return builder.build()
    }

    private fun com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.BatterySnapshotProto.isValid(
        storedSequence: Long,
    ): Boolean =
        levelPercent in 0..100 &&
            capturedAtEpochMillis > 0 &&
            sequence >= 1 &&
            sequence <= storedSequence

    private fun com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.ThresholdReachedEventProto.isValid(): Boolean =
        eventId.length <= 64 &&
            runCatching { UUID.fromString(eventId) }.isSuccess &&
            levelPercent in 0..100 &&
            thresholdPercent in AlertRule.MIN_THRESHOLD_PERCENT..AlertRule.MAX_THRESHOLD_PERCENT &&
            occurredAtEpochMillis > 0 &&
            expiresAtEpochMillis > occurredAtEpochMillis &&
            expiresAtEpochMillis - occurredAtEpochMillis <= ThresholdReachedEvent.MAX_EXPIRY_MILLIS &&
            sequence >= 1
}

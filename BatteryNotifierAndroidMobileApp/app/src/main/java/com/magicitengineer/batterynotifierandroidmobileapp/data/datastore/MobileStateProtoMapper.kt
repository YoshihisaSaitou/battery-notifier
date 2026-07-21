package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.AlertStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.BatterySnapshotProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.ThresholdReachedEventProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState

object MobileStateProtoMapper {
    fun toDomain(proto: MobileStateProto): MobilePersistentState {
        val safe = MobileStateSanitizer.sanitize(proto)
        return MobilePersistentState(
            storageSchemaVersion = safe.storageSchemaVersion,
            alertRule = AlertRule(
                thresholdPercent = safe.thresholdPercent,
                monitoringEnabled = safe.monitoringEnabled,
                notifyIfAlreadyBelowOnStart = safe.notifyIfAlreadyBelowOnStart,
                rearmHysteresisPercent = safe.rearmHysteresisPercent,
            ),
            onboardingCompleted = safe.onboardingCompleted,
            lastSnapshot = if (safe.hasLastSnapshot()) safe.lastSnapshot.toDomain() else null,
            sequence = safe.sequence,
            alertState = safe.alertState.toDomain(),
            lastMobileNotifiedEventId = safe.lastMobileNotifiedEventId.nullIfBlank(),
            pendingStateSequence = safe.pendingStateSequence,
            pendingEvent = if (safe.hasPendingEvent()) safe.pendingEvent.toDomain() else null,
            lastSyncSuccessAtEpochMillis = safe.lastSyncSuccessAtEpochMillis.takeIf { it > 0 },
            lastSyncErrorClassification = safe.lastSyncErrorClassification.nullIfBlank(),
            invalidInputCount = safe.invalidInputCount,
            unsupportedSchemaCount = safe.unsupportedSchemaCount,
        )
    }

    fun toProto(state: MobilePersistentState): MobileStateProto {
        val builder = MobileStateProto.newBuilder()
            .setStorageSchemaVersion(state.storageSchemaVersion)
            .setThresholdPercent(state.alertRule.thresholdPercent)
            .setMonitoringEnabled(state.alertRule.monitoringEnabled)
            .setOnboardingCompleted(state.onboardingCompleted)
            .setNotifyIfAlreadyBelowOnStart(state.alertRule.notifyIfAlreadyBelowOnStart)
            .setRearmHysteresisPercent(state.alertRule.rearmHysteresisPercent)
            .setSequence(state.sequence)
            .setAlertState(state.alertState.toProto())
            .setLastMobileNotifiedEventId(state.lastMobileNotifiedEventId.orEmpty())
            .setPendingStateSequence(state.pendingStateSequence)
            .setLastSyncSuccessAtEpochMillis(state.lastSyncSuccessAtEpochMillis ?: 0)
            .setLastSyncErrorClassification(state.lastSyncErrorClassification.orEmpty())
            .setInvalidInputCount(state.invalidInputCount)
            .setUnsupportedSchemaCount(state.unsupportedSchemaCount)

        state.lastSnapshot?.let { builder.setLastSnapshot(it.toProto()) }
        state.pendingEvent?.let { builder.setPendingEvent(it.toProto()) }
        return builder.build()
    }

    private fun BatterySnapshotProto.toDomain() = BatterySnapshot(
        levelPercent = levelPercent,
        isCharging = isCharging,
        capturedAtEpochMillis = capturedAtEpochMillis,
        sequence = sequence,
    )

    private fun BatterySnapshot.toProto() = BatterySnapshotProto.newBuilder()
        .setLevelPercent(levelPercent)
        .setIsCharging(isCharging)
        .setCapturedAtEpochMillis(capturedAtEpochMillis)
        .setSequence(sequence)
        .build()

    private fun AlertStateProto.toDomain() = AlertState(
        armed = armed,
        previousLevelPercent = previousLevelPercent.takeIf { hasPreviousLevel },
        lastEventId = lastEventId.nullIfBlank(),
        lastTriggeredAtEpochMillis = lastTriggeredAtEpochMillis.takeIf { it > 0 },
    )

    private fun AlertState.toProto() = AlertStateProto.newBuilder()
        .setArmed(armed)
        .setHasPreviousLevel(previousLevelPercent != null)
        .setPreviousLevelPercent(previousLevelPercent ?: 0)
        .setLastEventId(lastEventId.orEmpty())
        .setLastTriggeredAtEpochMillis(lastTriggeredAtEpochMillis ?: 0)
        .build()

    private fun ThresholdReachedEventProto.toDomain() = ThresholdReachedEvent(
        eventId = eventId,
        levelPercent = levelPercent,
        thresholdPercent = thresholdPercent,
        occurredAtEpochMillis = occurredAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
        sequence = sequence,
    )

    private fun ThresholdReachedEvent.toProto() = ThresholdReachedEventProto.newBuilder()
        .setEventId(eventId)
        .setLevelPercent(levelPercent)
        .setThresholdPercent(thresholdPercent)
        .setOccurredAtEpochMillis(occurredAtEpochMillis)
        .setExpiresAtEpochMillis(expiresAtEpochMillis)
        .setSequence(sequence)
        .build()

    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }
}

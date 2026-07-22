package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.PhoneStateProto
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.ThresholdEventProto
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedPhoneState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent

object WearStateProtoMapper {
    fun toDomain(proto: WearStateProto): WearPersistentState {
        val safe = WearStateSanitizer.sanitize(proto)
        return WearPersistentState(
            storageSchemaVersion = safe.storageSchemaVersion,
            lastPhoneState = if (safe.hasLastPhoneState()) safe.lastPhoneState.toDomain() else null,
            phoneStateReceivedAtEpochMillis = safe.phoneStateReceivedAtEpochMillis.takeIf { it > 0 },
            lastEvent = if (safe.hasLastEvent()) safe.lastEvent.toDomain() else null,
            lastEventSequence = safe.lastEventSequence,
            lastProcessedEventId = safe.lastProcessedEventId.nullIfBlank(),
            eventProcessedAtEpochMillis = safe.eventProcessedAtEpochMillis.takeIf { it > 0 },
            notificationDisposition = NotificationDisposition.entries.first {
                it.persistedValue == safe.notificationDisposition
            },
            notificationPostAttemptCount = safe.notificationPostAttemptCount,
            invalidPayloadCount = safe.invalidPayloadCount,
            unsupportedSchemaCount = safe.unsupportedSchemaCount,
            duplicateCount = safe.duplicateCount,
            outOfOrderCount = safe.outOfOrderCount,
            lastReceiveError = safe.lastReceiveError.nullIfBlank(),
            lastUnsupportedSchemaVersion = safe.lastUnsupportedSchemaVersion.takeIf {
                safe.hasLastUnsupportedSchemaVersion
            },
            notificationPermissionRequested = safe.notificationPermissionRequested,
        )
    }

    fun toProto(state: WearPersistentState): WearStateProto {
        val builder = WearStateProto.newBuilder()
            .setStorageSchemaVersion(state.storageSchemaVersion)
            .setPhoneStateReceivedAtEpochMillis(state.phoneStateReceivedAtEpochMillis ?: 0)
            .setLastEventSequence(state.lastEventSequence)
            .setLastProcessedEventId(state.lastProcessedEventId.orEmpty())
            .setEventProcessedAtEpochMillis(state.eventProcessedAtEpochMillis ?: 0)
            .setNotificationDisposition(state.notificationDisposition.persistedValue)
            .setNotificationPostAttemptCount(state.notificationPostAttemptCount)
            .setInvalidPayloadCount(state.invalidPayloadCount)
            .setUnsupportedSchemaCount(state.unsupportedSchemaCount)
            .setDuplicateCount(state.duplicateCount)
            .setOutOfOrderCount(state.outOfOrderCount)
            .setLastReceiveError(state.lastReceiveError.orEmpty())
            .setHasLastUnsupportedSchemaVersion(state.lastUnsupportedSchemaVersion != null)
            .setLastUnsupportedSchemaVersion(state.lastUnsupportedSchemaVersion ?: 0)
            .setNotificationPermissionRequested(state.notificationPermissionRequested)
        state.lastPhoneState?.let { builder.setLastPhoneState(it.toProto()) }
        state.lastEvent?.let { builder.setLastEvent(it.toProto()) }
        return builder.build()
    }

    private fun PhoneStateProto.toDomain() = ReceivedPhoneState(
        schemaVersion = schemaVersion,
        sequence = sequence,
        levelPercent = levelPercent,
        isCharging = isCharging,
        capturedAtEpochMillis = capturedAtEpochMillis,
        thresholdPercent = thresholdPercent,
        monitoringEnabled = monitoringEnabled,
        sentAtEpochMillis = sentAtEpochMillis,
    )

    private fun ReceivedPhoneState.toProto() = PhoneStateProto.newBuilder()
        .setSchemaVersion(schemaVersion)
        .setSequence(sequence)
        .setLevelPercent(levelPercent)
        .setIsCharging(isCharging)
        .setCapturedAtEpochMillis(capturedAtEpochMillis)
        .setThresholdPercent(thresholdPercent)
        .setMonitoringEnabled(monitoringEnabled)
        .setSentAtEpochMillis(sentAtEpochMillis)
        .build()

    private fun ThresholdEventProto.toDomain() = ReceivedThresholdEvent(
        schemaVersion = schemaVersion,
        eventId = eventId,
        sequence = sequence,
        levelPercent = levelPercent,
        thresholdPercent = thresholdPercent,
        occurredAtEpochMillis = occurredAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )

    private fun ReceivedThresholdEvent.toProto() = ThresholdEventProto.newBuilder()
        .setSchemaVersion(schemaVersion)
        .setEventId(eventId)
        .setSequence(sequence)
        .setLevelPercent(levelPercent)
        .setThresholdPercent(thresholdPercent)
        .setOccurredAtEpochMillis(occurredAtEpochMillis)
        .setExpiresAtEpochMillis(expiresAtEpochMillis)
        .build()

    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }
}

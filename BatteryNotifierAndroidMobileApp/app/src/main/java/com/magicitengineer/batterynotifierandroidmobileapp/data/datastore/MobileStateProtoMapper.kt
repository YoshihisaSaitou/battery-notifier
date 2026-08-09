package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.AlertStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.BatterySnapshotProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileNotificationDispositionProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.ThresholdReachedEventProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.ThresholdChangeResultProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.AlertEventKindProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertEventKind
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.notification.MobileNotificationDisposition
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResultCode

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
                fullChargeNotificationEnabled = safe.fullChargeNotificationEnabled,
            ),
            onboardingCompleted = safe.onboardingCompleted,
            resumeRequired = safe.resumeRequired,
            notificationPermissionRequested = safe.notificationPermissionRequested,
            lastSnapshot = if (safe.hasLastSnapshot()) safe.lastSnapshot.toDomain() else null,
            sequence = safe.sequence,
            alertState = safe.alertState.toDomain(),
            lastMobileNotifiedEventId = safe.lastMobileNotifiedEventId.nullIfBlank(),
            pendingMobileNotification = if (safe.hasPendingMobileNotification()) {
                safe.pendingMobileNotification.toDomain()
            } else {
                null
            },
            lastMobileNotificationEventId = safe.lastMobileNotificationEventId.nullIfBlank(),
            mobileNotificationDisposition = safe.mobileNotificationDisposition.toDomain(),
            pendingStateSequence = safe.pendingStateSequence,
            pendingEvent = if (safe.hasPendingEvent()) safe.pendingEvent.toDomain() else null,
            lastSyncSuccessAtEpochMillis = safe.lastSyncSuccessAtEpochMillis.takeIf { it > 0 },
            lastSyncErrorClassification = safe.lastSyncErrorClassification.nullIfBlank(),
            invalidInputCount = safe.invalidInputCount,
            unsupportedSchemaCount = safe.unsupportedSchemaCount,
            lastThresholdChangeResult = if (safe.hasLastThresholdChangeResult()) {
                safe.lastThresholdChangeResult.toDomain()
            } else {
                null
            },
            fullChargeArmed = safe.fullChargeArmed,
        )
    }

    fun toProto(state: MobilePersistentState): MobileStateProto {
        val builder = MobileStateProto.newBuilder()
            .setStorageSchemaVersion(state.storageSchemaVersion)
            .setThresholdPercent(state.alertRule.thresholdPercent)
            .setMonitoringEnabled(state.alertRule.monitoringEnabled)
            .setOnboardingCompleted(state.onboardingCompleted)
            .setResumeRequired(state.resumeRequired)
            .setNotificationPermissionRequested(state.notificationPermissionRequested)
            .setNotifyIfAlreadyBelowOnStart(state.alertRule.notifyIfAlreadyBelowOnStart)
            .setRearmHysteresisPercent(state.alertRule.rearmHysteresisPercent)
            .setFullChargeNotificationEnabled(state.alertRule.fullChargeNotificationEnabled)
            .setFullChargeArmed(state.fullChargeArmed)
            .setSequence(state.sequence)
            .setAlertState(state.alertState.toProto())
            .setLastMobileNotifiedEventId(state.lastMobileNotifiedEventId.orEmpty())
            .setLastMobileNotificationEventId(state.lastMobileNotificationEventId.orEmpty())
            .setMobileNotificationDisposition(state.mobileNotificationDisposition.toProto())
            .setPendingStateSequence(state.pendingStateSequence)
            .setLastSyncSuccessAtEpochMillis(state.lastSyncSuccessAtEpochMillis ?: 0)
            .setLastSyncErrorClassification(state.lastSyncErrorClassification.orEmpty())
            .setInvalidInputCount(state.invalidInputCount)
            .setUnsupportedSchemaCount(state.unsupportedSchemaCount)

        state.lastSnapshot?.let { builder.setLastSnapshot(it.toProto()) }
        state.pendingEvent?.let { builder.setPendingEvent(it.toProto()) }
        state.pendingMobileNotification?.let { builder.setPendingMobileNotification(it.toProto()) }
        state.lastThresholdChangeResult?.let {
            builder.setLastThresholdChangeResult(it.toProto())
        }
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
        kind = when (kind) {
            AlertEventKindProto.ALERT_EVENT_KIND_FULL_CHARGE -> AlertEventKind.FULL_CHARGE
            else -> AlertEventKind.LOW_BATTERY
        },
    )

    private fun ThresholdReachedEvent.toProto() = ThresholdReachedEventProto.newBuilder()
        .setEventId(eventId)
        .setLevelPercent(levelPercent)
        .setThresholdPercent(thresholdPercent)
        .setOccurredAtEpochMillis(occurredAtEpochMillis)
        .setExpiresAtEpochMillis(expiresAtEpochMillis)
        .setSequence(sequence)
        .setKind(
            when (kind) {
                AlertEventKind.LOW_BATTERY -> AlertEventKindProto.ALERT_EVENT_KIND_LOW_BATTERY
                AlertEventKind.FULL_CHARGE -> AlertEventKindProto.ALERT_EVENT_KIND_FULL_CHARGE
            }
        )
        .build()

    private fun ThresholdChangeResultProto.toDomain() = ThresholdChangeResult(
        requestId = requestId,
        resultCode = ThresholdChangeResultCode.entries.first {
            it.persistedValue == resultCode
        },
        effectiveThresholdPercent = effectiveThresholdPercent,
        phoneStateSequence = phoneStateSequence,
    )

    private fun ThresholdChangeResult.toProto() = ThresholdChangeResultProto.newBuilder()
        .setRequestId(requestId)
        .setResultCode(resultCode.persistedValue)
        .setEffectiveThresholdPercent(effectiveThresholdPercent)
        .setPhoneStateSequence(phoneStateSequence)
        .build()

    private fun MobileNotificationDispositionProto.toDomain(): MobileNotificationDisposition =
        when (this) {
            MobileNotificationDispositionProto.MOBILE_NOTIFICATION_DISPOSITION_POSTED ->
                MobileNotificationDisposition.POSTED
            MobileNotificationDispositionProto.MOBILE_NOTIFICATION_DISPOSITION_PERMISSION_DENIED ->
                MobileNotificationDisposition.PERMISSION_DENIED
            MobileNotificationDispositionProto.MOBILE_NOTIFICATION_DISPOSITION_FAILED ->
                MobileNotificationDisposition.FAILED
            MobileNotificationDispositionProto.MOBILE_NOTIFICATION_DISPOSITION_NONE,
            MobileNotificationDispositionProto.UNRECOGNIZED -> MobileNotificationDisposition.NONE
        }

    private fun MobileNotificationDisposition.toProto(): MobileNotificationDispositionProto =
        when (this) {
            MobileNotificationDisposition.NONE ->
                MobileNotificationDispositionProto.MOBILE_NOTIFICATION_DISPOSITION_NONE
            MobileNotificationDisposition.POSTED ->
                MobileNotificationDispositionProto.MOBILE_NOTIFICATION_DISPOSITION_POSTED
            MobileNotificationDisposition.PERMISSION_DENIED ->
                MobileNotificationDispositionProto.MOBILE_NOTIFICATION_DISPOSITION_PERMISSION_DENIED
            MobileNotificationDisposition.FAILED ->
                MobileNotificationDispositionProto.MOBILE_NOTIFICATION_DISPOSITION_FAILED
        }

    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }
}

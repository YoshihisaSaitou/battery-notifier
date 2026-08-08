package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.ThresholdEventProto
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.MAX_EVENT_EXPIRY_MILLIS
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.MAX_EVENT_ID_LENGTH
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.MAX_WEAR_NOTIFICATION_POST_ATTEMPTS
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.SUPPORTED_SCHEMA_VERSION
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeResultCode
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeStatus
import java.util.UUID

object WearStateSanitizer {
    fun defaultValue(): WearStateProto = WearStateProto.newBuilder()
        .setStorageSchemaVersion(WearPersistentState.CURRENT_STORAGE_SCHEMA_VERSION)
        .setNotificationDisposition(NotificationDisposition.NONE.persistedValue)
        .build()

    fun sanitize(input: WearStateProto): WearStateProto {
        val builder = input.toBuilder()
            .setStorageSchemaVersion(WearPersistentState.CURRENT_STORAGE_SCHEMA_VERSION)
            .setPhoneStateReceivedAtEpochMillis(input.phoneStateReceivedAtEpochMillis.coerceAtLeast(0))
            .setLastEventSequence(input.lastEventSequence.coerceAtLeast(0))
            .setEventProcessedAtEpochMillis(input.eventProcessedAtEpochMillis.coerceAtLeast(0))
            .setInvalidPayloadCount(input.invalidPayloadCount.coerceAtLeast(0))
            .setUnsupportedSchemaCount(input.unsupportedSchemaCount.coerceAtLeast(0))
            .setDuplicateCount(input.duplicateCount.coerceAtLeast(0))
            .setOutOfOrderCount(input.outOfOrderCount.coerceAtLeast(0))
            .setNotificationPostAttemptCount(
                input.notificationPostAttemptCount.coerceIn(
                    0,
                    MAX_WEAR_NOTIFICATION_POST_ATTEMPTS,
                )
            )
            .setNotificationDisposition(
                NotificationDisposition.entries.firstOrNull {
                    it.persistedValue == input.notificationDisposition
                }?.persistedValue ?: NotificationDisposition.NONE.persistedValue
            )
            .setThresholdChangeStatus(
                ThresholdChangeStatus.entries.firstOrNull {
                    it.persistedValue == input.thresholdChangeStatus
                }?.persistedValue ?: ThresholdChangeStatus.IDLE.persistedValue
            )

        if (!input.hasLastUnsupportedSchemaVersion) {
            builder.setLastUnsupportedSchemaVersion(0)
        }

        if (
            !input.hasLastPhoneState() ||
            input.phoneStateReceivedAtEpochMillis <= 0 ||
            !input.lastPhoneState.isValid()
        ) {
            builder.clearLastPhoneState().setPhoneStateReceivedAtEpochMillis(0)
        }
        if (!input.hasLastEvent() || !input.lastEvent.isValid(input.lastEventSequence)) {
            builder.clearLastEvent()
                .setLastEventSequence(0)
                .clearLastProcessedEventId()
                .setEventProcessedAtEpochMillis(0)
                .setNotificationDisposition(NotificationDisposition.NONE.persistedValue)
        } else if (
            input.lastProcessedEventId != input.lastEvent.eventId ||
            input.eventProcessedAtEpochMillis <= 0
        ) {
            builder.clearLastProcessedEventId()
                .setEventProcessedAtEpochMillis(0)
                .setNotificationDisposition(NotificationDisposition.NONE.persistedValue)
        }
        val disposition = NotificationDisposition.entries.first {
            it.persistedValue == builder.notificationDisposition
        }
        val attemptCount = when (disposition) {
            NotificationDisposition.PENDING,
            NotificationDisposition.POSTED,
            NotificationDisposition.PERMISSION_DENIED,
            NotificationDisposition.RESERVED_FAILED,
            NotificationDisposition.FAILED_EXHAUSTED ->
                builder.notificationPostAttemptCount.coerceIn(
                    1,
                    MAX_WEAR_NOTIFICATION_POST_ATTEMPTS,
                )

            NotificationDisposition.NONE,
            NotificationDisposition.EXPIRED,
            NotificationDisposition.CLOCK_SKEW -> 0
        }
        builder.setNotificationPostAttemptCount(attemptCount)
        if (
            !input.hasThresholdDraftPercent ||
            input.thresholdDraftPercent !in 5..100
        ) {
            builder
                .setHasThresholdDraftPercent(false)
                .setThresholdDraftPercent(0)
        }
        if (
            input.hasPendingThresholdChangeRequest() &&
            !input.pendingThresholdChangeRequest.isValid()
        ) {
            builder.clearPendingThresholdChangeRequest()
        }
        if (
            input.hasThresholdChangeResult() &&
            !input.thresholdChangeResult.isValid()
        ) {
            builder.clearThresholdChangeResult()
        }
        val statusWithValidatedFields = ThresholdChangeStatus.entries.first {
            it.persistedValue == builder.thresholdChangeStatus
        }
        if (
            statusWithValidatedFields in setOf(
                ThresholdChangeStatus.SENDING,
                ThresholdChangeStatus.WAITING_RESULT,
                ThresholdChangeStatus.SEND_FAILED,
                ThresholdChangeStatus.APPLIED_WAITING_STATE,
            ) &&
            !builder.hasPendingThresholdChangeRequest()
        ) {
            builder
                .setThresholdChangeStatus(ThresholdChangeStatus.IDLE.persistedValue)
                .clearThresholdChangeResult()
        }
        val statusAfterPendingRepair = ThresholdChangeStatus.entries.first {
            it.persistedValue == builder.thresholdChangeStatus
        }
        if (
            statusAfterPendingRepair == ThresholdChangeStatus.APPLIED_WAITING_STATE &&
            !builder.hasThresholdChangeResult()
        ) {
            builder.setThresholdChangeStatus(ThresholdChangeStatus.WAITING_RESULT.persistedValue)
        }
        return builder.build()
    }

    private fun com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.PhoneStateProto.isValid(): Boolean =
        schemaVersion == SUPPORTED_SCHEMA_VERSION &&
            sequence >= 1 &&
            levelPercent in 0..100 &&
            capturedAtEpochMillis > 0 &&
            thresholdPercent in 5..100 &&
            sentAtEpochMillis > 0

    private fun ThresholdEventProto.isValid(storedSequence: Long): Boolean =
        schemaVersion == SUPPORTED_SCHEMA_VERSION &&
            eventId.length <= MAX_EVENT_ID_LENGTH &&
            runCatching { UUID.fromString(eventId) }.isSuccess &&
            sequence >= 1 &&
            sequence == storedSequence &&
            levelPercent in 0..100 &&
            thresholdPercent in 5..100 &&
            occurredAtEpochMillis > 0 &&
            expiresAtEpochMillis > occurredAtEpochMillis &&
            expiresAtEpochMillis - occurredAtEpochMillis <= MAX_EVENT_EXPIRY_MILLIS

    private fun com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.ThresholdChangeRequestProto.isValid(): Boolean =
        schemaVersion == SUPPORTED_SCHEMA_VERSION &&
            requestId.length <= 64 &&
            runCatching { UUID.fromString(requestId) }.isSuccess &&
            thresholdPercent in 5..100 &&
            expectedThresholdPercent in 5..100

    private fun com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.ThresholdChangeResultProto.isValid(): Boolean =
        requestId.length <= 64 &&
            runCatching { UUID.fromString(requestId) }.isSuccess &&
            ThresholdChangeResultCode.entries.any { it.persistedValue == resultCode } &&
            effectiveThresholdPercent in 5..100 &&
            phoneStateSequence >= 1
}

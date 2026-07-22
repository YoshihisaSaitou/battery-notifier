package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.MAX_FUTURE_SKEW_MILLIS
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.MAX_WEAR_NOTIFICATION_POST_ATTEMPTS
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceiveErrorClassification
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedPhoneState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class WearStateApplyOutcome {
    APPLIED,
    DUPLICATE,
    OUT_OF_ORDER,
}

data class WearStateApplyResult(
    val outcome: WearStateApplyOutcome,
    val state: WearPersistentState,
)

enum class WearNotificationCompletionOutcome {
    APPLIED,
    STALE_RESERVATION,
}

data class WearNotificationCompletionResult(
    val outcome: WearNotificationCompletionOutcome,
    val state: WearPersistentState,
)

enum class WearNotificationRetryReservationOutcome {
    RESERVED,
    NOT_ELIGIBLE,
    EXPIRED,
    EXHAUSTED,
    CLOCK_SKEW,
}

data class WearNotificationRetryReservationResult(
    val outcome: WearNotificationRetryReservationOutcome,
    val state: WearPersistentState,
)

enum class WearNotificationRecoveryOutcome {
    RECOVERED_FOR_RETRY,
    NOT_REQUIRED,
    EXPIRED,
    EXHAUSTED,
    CLOCK_SKEW,
}

data class WearNotificationRecoveryResult(
    val outcome: WearNotificationRecoveryOutcome,
    val state: WearPersistentState,
)

interface WearStateRepository {
    val state: Flow<WearPersistentState>

    suspend fun applyPhoneState(
        phoneState: ReceivedPhoneState,
        receivedAtEpochMillis: Long,
    ): WearStateApplyResult

    suspend fun applyThresholdEvent(
        event: ReceivedThresholdEvent,
        receivedAtEpochMillis: Long,
    ): WearStateApplyResult

    suspend fun recordInvalidPayload(
        classification: ReceiveErrorClassification,
    ): WearPersistentState

    suspend fun recordUnsupportedSchema(receivedVersion: Int): WearPersistentState

    suspend fun completeNotification(
        eventId: String,
        disposition: NotificationDisposition,
    ): WearNotificationCompletionResult

    suspend fun reserveNotificationRetry(
        eventId: String,
        nowEpochMillis: Long,
    ): WearNotificationRetryReservationResult

    suspend fun recoverInterruptedNotification(
        nowEpochMillis: Long,
    ): WearNotificationRecoveryResult

    suspend fun markNotificationPermissionRequested(): WearPersistentState
}

class ProtoWearStateRepository(
    private val dataStore: DataStore<WearStateProto>,
) : WearStateRepository {
    override val state: Flow<WearPersistentState> =
        dataStore.data.map(WearStateProtoMapper::toDomain)

    override suspend fun applyPhoneState(
        phoneState: ReceivedPhoneState,
        receivedAtEpochMillis: Long,
    ): WearStateApplyResult {
        require(receivedAtEpochMillis > 0)
        var outcome = WearStateApplyOutcome.APPLIED
        val updated = update { current ->
            val storedSequence = current.lastPhoneState?.sequence ?: 0
            when {
                phoneState.sequence == storedSequence -> {
                    outcome = WearStateApplyOutcome.DUPLICATE
                    current.copy(duplicateCount = current.duplicateCount.saturatingIncrement())
                }

                phoneState.sequence < storedSequence -> {
                    outcome = WearStateApplyOutcome.OUT_OF_ORDER
                    current.copy(outOfOrderCount = current.outOfOrderCount.saturatingIncrement())
                }

                else -> current.copy(
                    lastPhoneState = phoneState,
                    phoneStateReceivedAtEpochMillis = receivedAtEpochMillis,
                    lastReceiveError = null,
                )
            }
        }
        return WearStateApplyResult(outcome, updated)
    }

    override suspend fun applyThresholdEvent(
        event: ReceivedThresholdEvent,
        receivedAtEpochMillis: Long,
    ): WearStateApplyResult {
        require(receivedAtEpochMillis > 0)
        var outcome = WearStateApplyOutcome.APPLIED
        val updated = update { current ->
            when {
                event.eventId == current.lastProcessedEventId -> {
                    outcome = WearStateApplyOutcome.DUPLICATE
                    current.copy(duplicateCount = current.duplicateCount.saturatingIncrement())
                }

                event.sequence <= current.lastEventSequence -> {
                    outcome = WearStateApplyOutcome.OUT_OF_ORDER
                    current.copy(outOfOrderCount = current.outOfOrderCount.saturatingIncrement())
                }

                else -> current.copy(
                    lastEvent = event,
                    lastEventSequence = event.sequence,
                    lastProcessedEventId = event.eventId,
                    eventProcessedAtEpochMillis = receivedAtEpochMillis,
                    notificationDisposition = event.notificationDisposition(receivedAtEpochMillis),
                    notificationPostAttemptCount =
                        if (
                            event.notificationDisposition(receivedAtEpochMillis) ==
                            NotificationDisposition.PENDING
                        ) {
                            1
                        } else {
                            0
                        },
                    lastReceiveError = null,
                )
            }
        }
        return WearStateApplyResult(outcome, updated)
    }

    override suspend fun recordInvalidPayload(
        classification: ReceiveErrorClassification,
    ): WearPersistentState = update { current ->
        current.copy(
            invalidPayloadCount = current.invalidPayloadCount.saturatingIncrement(),
            lastReceiveError = classification.persistedValue,
        )
    }

    override suspend fun recordUnsupportedSchema(
        receivedVersion: Int,
    ): WearPersistentState = update { current ->
        current.copy(
            unsupportedSchemaCount = current.unsupportedSchemaCount.saturatingIncrement(),
            lastReceiveError = "unsupported_schema",
            lastUnsupportedSchemaVersion = receivedVersion,
        )
    }

    override suspend fun completeNotification(
        eventId: String,
        disposition: NotificationDisposition,
    ): WearNotificationCompletionResult {
        require(eventId.isNotBlank())
        require(
            disposition == NotificationDisposition.POSTED ||
                disposition == NotificationDisposition.PERMISSION_DENIED ||
                disposition == NotificationDisposition.RESERVED_FAILED
        )
        var outcome = WearNotificationCompletionOutcome.APPLIED
        val updated = update { current ->
            if (
                current.lastProcessedEventId != eventId ||
                current.notificationDisposition != NotificationDisposition.PENDING
            ) {
                outcome = WearNotificationCompletionOutcome.STALE_RESERVATION
                current
            } else {
                current.copy(
                    notificationDisposition = if (
                        disposition == NotificationDisposition.RESERVED_FAILED &&
                        current.notificationPostAttemptCount >=
                        MAX_WEAR_NOTIFICATION_POST_ATTEMPTS
                    ) {
                        NotificationDisposition.FAILED_EXHAUSTED
                    } else {
                        disposition
                    }
                )
            }
        }
        return WearNotificationCompletionResult(outcome, updated)
    }

    override suspend fun reserveNotificationRetry(
        eventId: String,
        nowEpochMillis: Long,
    ): WearNotificationRetryReservationResult {
        require(eventId.isNotBlank())
        require(nowEpochMillis > 0)
        var outcome = WearNotificationRetryReservationOutcome.NOT_ELIGIBLE
        val updated = update { current ->
            val event = current.lastEvent
            when {
                current.lastProcessedEventId != eventId ||
                    event == null ||
                    current.notificationDisposition != NotificationDisposition.RESERVED_FAILED ->
                    current

                nowEpochMillis > event.expiresAtEpochMillis -> {
                    outcome = WearNotificationRetryReservationOutcome.EXPIRED
                    current.copy(
                        notificationDisposition = NotificationDisposition.EXPIRED,
                        notificationPostAttemptCount = 0,
                    )
                }

                event.occurredAtEpochMillis > nowEpochMillis &&
                    event.occurredAtEpochMillis - nowEpochMillis >
                    MAX_FUTURE_SKEW_MILLIS -> {
                    outcome = WearNotificationRetryReservationOutcome.CLOCK_SKEW
                    current.copy(
                        notificationDisposition = NotificationDisposition.CLOCK_SKEW,
                        notificationPostAttemptCount = 0,
                    )
                }

                current.notificationPostAttemptCount >=
                    MAX_WEAR_NOTIFICATION_POST_ATTEMPTS -> {
                    outcome = WearNotificationRetryReservationOutcome.EXHAUSTED
                    current.copy(
                        notificationDisposition = NotificationDisposition.FAILED_EXHAUSTED
                    )
                }

                else -> {
                    outcome = WearNotificationRetryReservationOutcome.RESERVED
                    current.copy(
                        notificationDisposition = NotificationDisposition.PENDING,
                        notificationPostAttemptCount =
                            current.notificationPostAttemptCount + 1,
                    )
                }
            }
        }
        return WearNotificationRetryReservationResult(outcome, updated)
    }

    override suspend fun recoverInterruptedNotification(
        nowEpochMillis: Long,
    ): WearNotificationRecoveryResult {
        require(nowEpochMillis > 0)
        var outcome = WearNotificationRecoveryOutcome.NOT_REQUIRED
        val updated = update { current ->
            val event = current.lastEvent
            when {
                current.notificationDisposition != NotificationDisposition.PENDING ||
                    event == null -> current

                nowEpochMillis > event.expiresAtEpochMillis -> {
                    outcome = WearNotificationRecoveryOutcome.EXPIRED
                    current.copy(
                        notificationDisposition = NotificationDisposition.EXPIRED,
                        notificationPostAttemptCount = 0,
                    )
                }

                event.occurredAtEpochMillis > nowEpochMillis &&
                    event.occurredAtEpochMillis - nowEpochMillis >
                    MAX_FUTURE_SKEW_MILLIS -> {
                    outcome = WearNotificationRecoveryOutcome.CLOCK_SKEW
                    current.copy(
                        notificationDisposition = NotificationDisposition.CLOCK_SKEW,
                        notificationPostAttemptCount = 0,
                    )
                }

                current.notificationPostAttemptCount >=
                    MAX_WEAR_NOTIFICATION_POST_ATTEMPTS -> {
                    outcome = WearNotificationRecoveryOutcome.EXHAUSTED
                    current.copy(
                        notificationDisposition = NotificationDisposition.FAILED_EXHAUSTED
                    )
                }

                else -> {
                    outcome = WearNotificationRecoveryOutcome.RECOVERED_FOR_RETRY
                    current.copy(
                        notificationDisposition = NotificationDisposition.RESERVED_FAILED
                    )
                }
            }
        }
        return WearNotificationRecoveryResult(outcome, updated)
    }

    override suspend fun markNotificationPermissionRequested(): WearPersistentState =
        update { current ->
            current.copy(notificationPermissionRequested = true)
        }

    private suspend fun update(
        transform: (WearPersistentState) -> WearPersistentState,
    ): WearPersistentState {
        val updated = dataStore.updateData { raw ->
            WearStateProtoMapper.toProto(transform(WearStateProtoMapper.toDomain(raw)))
        }
        return WearStateProtoMapper.toDomain(updated)
    }

    private fun ReceivedThresholdEvent.notificationDisposition(
        receivedAtEpochMillis: Long,
    ): NotificationDisposition = when {
        receivedAtEpochMillis > expiresAtEpochMillis -> NotificationDisposition.EXPIRED
        occurredAtEpochMillis > receivedAtEpochMillis &&
            occurredAtEpochMillis - receivedAtEpochMillis > MAX_FUTURE_SKEW_MILLIS ->
            NotificationDisposition.CLOCK_SKEW

        else -> NotificationDisposition.PENDING
    }

    private fun Long.saturatingIncrement(): Long =
        if (this == Long.MAX_VALUE) Long.MAX_VALUE else this + 1
}

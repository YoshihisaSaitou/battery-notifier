package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRuleChangeEvaluator
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdEvaluator
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.notification.MobileNotificationDisposition
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncDeliveryUpdate
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeProcessingOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeRequest
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResultCode
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class BatteryProcessingResult(
    val state: MobilePersistentState,
    val snapshot: BatterySnapshot,
    val event: ThresholdReachedEvent?,
)

enum class MobileNotificationCompletionOutcome {
    APPLIED,
    STALE_RESERVATION,
}

data class MobileNotificationCompletion(
    val outcome: MobileNotificationCompletionOutcome,
    val state: MobilePersistentState,
)

interface MobileStateRepository {
    val state: Flow<MobilePersistentState>

    suspend fun processBatteryReading(
        reading: BatteryReading,
        candidateEventId: String,
    ): BatteryProcessingResult

    suspend fun updateAlertRule(rule: AlertRule): MobilePersistentState

    suspend fun updateMonitoringState(
        monitoringEnabled: Boolean,
        resumeRequired: Boolean,
    ): MobilePersistentState = error("Monitoring state updates are not configured")

    suspend fun resetMonitoringStartBaseline(): MobilePersistentState =
        error("Monitoring start baseline reset is not configured")

    suspend fun markMobileNotified(eventId: String): MobilePersistentState

    suspend fun completeMobileNotification(
        eventId: String,
        disposition: MobileNotificationDisposition,
    ): MobileNotificationCompletion = error("Mobile notification completion is not configured")

    suspend fun applySyncDelivery(update: SyncDeliveryUpdate): MobilePersistentState

    suspend fun recordInvalidInput(): MobilePersistentState

    suspend fun recordUnsupportedSchema(receivedVersion: Int): MobilePersistentState =
        error("Unsupported schema tracking is not configured")

    suspend fun markNotificationPermissionRequested(): MobilePersistentState =
        error("Notification permission request tracking is not configured")

    suspend fun applyThresholdChangeRequest(
        request: ThresholdChangeRequest,
    ): ThresholdChangeProcessingResult =
        error("Wear threshold changes are not configured")
}

class ProtoMobileStateRepository(
    private val dataStore: DataStore<MobileStateProto>,
) : MobileStateRepository {
    override val state: Flow<MobilePersistentState> =
        dataStore.data.map(MobileStateProtoMapper::toDomain)

    override suspend fun processBatteryReading(
        reading: BatteryReading,
        candidateEventId: String,
    ): BatteryProcessingResult {
        require(runCatching { UUID.fromString(candidateEventId) }.isSuccess) {
            "candidateEventId must be a UUID"
        }

        val updatedProto = dataStore.updateData { rawState ->
            val current = MobileStateProtoMapper.toDomain(rawState)
            val nextSequence = current.sequence.nextSequence()
            val snapshot = BatterySnapshot(
                levelPercent = reading.levelPercent,
                isCharging = reading.isCharging,
                capturedAtEpochMillis = reading.capturedAtEpochMillis,
                sequence = nextSequence,
            )
            val evaluation = ThresholdEvaluator.evaluate(
                rule = current.alertRule,
                state = current.alertState,
                snapshot = snapshot,
                candidateEventId = candidateEventId,
            )
            MobileStateProtoMapper.toProto(
                current.copy(
                    lastSnapshot = snapshot,
                    sequence = nextSequence,
                    alertState = evaluation.state,
                    pendingStateSequence = nextSequence,
                    pendingEvent = evaluation.event ?: current.pendingEvent,
                    pendingMobileNotification = evaluation.event ?:
                        current.pendingMobileNotification,
                    lastMobileNotificationEventId = if (evaluation.event != null) {
                        null
                    } else {
                        current.lastMobileNotificationEventId
                    },
                    mobileNotificationDisposition = if (evaluation.event != null) {
                        MobileNotificationDisposition.NONE
                    } else {
                        current.mobileNotificationDisposition
                    },
                )
            )
        }
        val updated = MobileStateProtoMapper.toDomain(updatedProto)
        val snapshot = requireNotNull(updated.lastSnapshot)
        val event = updated.pendingEvent?.takeIf {
            it.eventId == candidateEventId && it.sequence == snapshot.sequence
        }
        return BatteryProcessingResult(updated, snapshot, event)
    }

    override suspend fun updateAlertRule(rule: AlertRule): MobilePersistentState =
        update { current ->
            val nextSequence = if (current.lastSnapshot == null) {
                current.sequence
            } else {
                current.sequence.nextSequence()
            }
            current.copy(
                alertRule = rule,
                lastSnapshot = current.lastSnapshot?.copy(sequence = nextSequence),
                sequence = nextSequence,
                alertState = AlertRuleChangeEvaluator.reevaluateWithoutEvent(
                    rule = rule,
                    state = current.alertState,
                    snapshot = current.lastSnapshot,
                ),
                pendingStateSequence = nextSequence,
            )
        }

    override suspend fun updateMonitoringState(
        monitoringEnabled: Boolean,
        resumeRequired: Boolean,
    ): MobilePersistentState {
        require(!monitoringEnabled || !resumeRequired) {
            "monitoring cannot be active while resume is required"
        }
        return update { current ->
            val nextSequence = if (current.lastSnapshot == null) {
                current.sequence
            } else {
                current.sequence.nextSequence()
            }
            val nextRule = current.alertRule.copy(monitoringEnabled = monitoringEnabled)
            current.copy(
                alertRule = nextRule,
                resumeRequired = resumeRequired,
                lastSnapshot = current.lastSnapshot?.copy(sequence = nextSequence),
                sequence = nextSequence,
                alertState = AlertRuleChangeEvaluator.reevaluateWithoutEvent(
                    rule = nextRule,
                    state = current.alertState,
                    snapshot = current.lastSnapshot,
                ),
                pendingStateSequence = nextSequence,
            )
        }
    }

    override suspend fun resetMonitoringStartBaseline(): MobilePersistentState =
        update { current ->
            current.copy(
                alertState = current.alertState.copy(previousLevelPercent = null),
            )
        }

    override suspend fun markMobileNotified(eventId: String): MobilePersistentState =
        completeMobileNotification(eventId, MobileNotificationDisposition.POSTED).let {
            check(it.outcome == MobileNotificationCompletionOutcome.APPLIED) {
                "Only the current pending Mobile notification can be marked notified"
            }
            it.state
        }

    override suspend fun completeMobileNotification(
        eventId: String,
        disposition: MobileNotificationDisposition,
    ): MobileNotificationCompletion {
        require(disposition != MobileNotificationDisposition.NONE) {
            "A terminal notification disposition is required"
        }
        var applied = false
        val updated = update { current ->
            if (current.pendingMobileNotification?.eventId != eventId) {
                current
            } else {
                applied = true
                current.copy(
                    lastMobileNotifiedEventId = if (
                        disposition == MobileNotificationDisposition.POSTED
                    ) {
                        eventId
                    } else {
                        current.lastMobileNotifiedEventId
                    },
                    pendingMobileNotification = null,
                    lastMobileNotificationEventId = eventId,
                    mobileNotificationDisposition = disposition,
                )
            }
        }
        return MobileNotificationCompletion(
            outcome = if (applied) {
                MobileNotificationCompletionOutcome.APPLIED
            } else {
                MobileNotificationCompletionOutcome.STALE_RESERVATION
            },
            state = updated,
        )
    }

    override suspend fun applySyncDelivery(
        delivery: SyncDeliveryUpdate,
    ): MobilePersistentState {
        return update { current ->
            val confirmedState = delivery.confirmedStateSequence
            val clearPendingState = confirmedState != null &&
                current.pendingStateSequence > 0 &&
                confirmedState >= current.pendingStateSequence
            val pendingEvent = current.pendingEvent
            val clearPendingEvent = pendingEvent != null &&
                delivery.confirmedEventId == pendingEvent.eventId &&
                delivery.confirmedEventSequence == pendingEvent.sequence
            val hasConfirmation = confirmedState != null || delivery.confirmedEventId != null
            val nextPendingStateSequence = if (clearPendingState) 0 else current.pendingStateSequence
            val nextPendingEvent = if (clearPendingEvent) null else pendingEvent
            val hasRemainingOutbox = nextPendingStateSequence > 0 || nextPendingEvent != null

            current.copy(
                pendingStateSequence = nextPendingStateSequence,
                pendingEvent = nextPendingEvent,
                lastSyncSuccessAtEpochMillis = if (hasConfirmation) {
                    maxOf(
                        current.lastSyncSuccessAtEpochMillis ?: 0,
                        delivery.completedAtEpochMillis,
                    )
                } else {
                    current.lastSyncSuccessAtEpochMillis
                },
                lastSyncErrorClassification = when {
                    delivery.failureClassification != null ->
                        delivery.failureClassification.persistedValue
                    !hasRemainingOutbox -> null
                    else -> current.lastSyncErrorClassification
                },
            )
        }
    }

    override suspend fun recordInvalidInput(): MobilePersistentState =
        update { current ->
            current.copy(invalidInputCount = current.invalidInputCount.saturatingIncrement())
        }

    override suspend fun recordUnsupportedSchema(
        receivedVersion: Int,
    ): MobilePersistentState =
        update { current ->
            current.copy(
                unsupportedSchemaCount =
                    current.unsupportedSchemaCount.saturatingIncrement(),
            )
        }

    override suspend fun markNotificationPermissionRequested(): MobilePersistentState =
        update { current ->
            current.copy(notificationPermissionRequested = true)
        }

    override suspend fun applyThresholdChangeRequest(
        request: ThresholdChangeRequest,
    ): ThresholdChangeProcessingResult {
        var replayed = false
        var settingChanged = false
        var result: ThresholdChangeResult? = null
        var outcome = ThresholdChangeProcessingOutcome.PHONE_STATE_UNAVAILABLE
        val updated = update { current ->
            if (current.lastSnapshot == null) {
                return@update current
            }
            outcome = ThresholdChangeProcessingOutcome.PROCESSED
            val previous = current.lastThresholdChangeResult
            if (previous?.requestId == request.requestId) {
                replayed = true
                result = previous
                current
            } else {
                val currentThreshold = current.alertRule.thresholdPercent
                val code = when {
                    request.thresholdPercent == currentThreshold ->
                        ThresholdChangeResultCode.APPLIED
                    request.expectedThresholdPercent != currentThreshold ->
                        ThresholdChangeResultCode.CONFLICT
                    else -> ThresholdChangeResultCode.APPLIED
                }
                val shouldChange = code == ThresholdChangeResultCode.APPLIED &&
                    request.thresholdPercent != currentThreshold
                val nextSequence = if (shouldChange) {
                    current.sequence.nextSequence()
                } else {
                    current.sequence
                }
                val nextRule = if (shouldChange) {
                    current.alertRule.copy(thresholdPercent = request.thresholdPercent)
                } else {
                    current.alertRule
                }
                result = ThresholdChangeResult(
                    requestId = request.requestId,
                    resultCode = code,
                    effectiveThresholdPercent = nextRule.thresholdPercent,
                    phoneStateSequence = nextSequence,
                )
                settingChanged = shouldChange
                current.copy(
                    alertRule = nextRule,
                    lastSnapshot = if (shouldChange) {
                        current.lastSnapshot?.copy(sequence = nextSequence)
                    } else {
                        current.lastSnapshot
                    },
                    sequence = nextSequence,
                    alertState = if (shouldChange) {
                        AlertRuleChangeEvaluator.reevaluateWithoutEvent(
                            rule = nextRule,
                            state = current.alertState,
                            snapshot = current.lastSnapshot,
                        )
                    } else {
                        current.alertState
                    },
                    pendingStateSequence = if (shouldChange) {
                        nextSequence
                    } else {
                        current.pendingStateSequence
                    },
                    lastThresholdChangeResult = result,
                )
            }
        }
        return ThresholdChangeProcessingResult(
            state = updated,
            result = result,
            replayed = replayed,
            settingChanged = settingChanged,
            outcome = outcome,
        )
    }

    private suspend fun update(
        transform: (MobilePersistentState) -> MobilePersistentState,
    ): MobilePersistentState {
        val updated = dataStore.updateData { raw ->
            MobileStateProtoMapper.toProto(transform(MobileStateProtoMapper.toDomain(raw)))
        }
        return MobileStateProtoMapper.toDomain(updated)
    }

    private fun Long.nextSequence(): Long {
        check(this < Long.MAX_VALUE) { "sequence exhausted" }
        return this + 1
    }

    private fun Long.saturatingIncrement(): Long =
        if (this == Long.MAX_VALUE) Long.MAX_VALUE else this + 1
}

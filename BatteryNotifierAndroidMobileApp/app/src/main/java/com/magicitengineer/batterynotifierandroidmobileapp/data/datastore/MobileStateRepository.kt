package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdEvaluator
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncDeliveryUpdate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class BatteryProcessingResult(
    val state: MobilePersistentState,
    val snapshot: BatterySnapshot,
    val event: ThresholdReachedEvent?,
)

interface MobileStateRepository {
    val state: Flow<MobilePersistentState>

    suspend fun processBatteryReading(
        reading: BatteryReading,
        candidateEventId: String,
    ): BatteryProcessingResult

    suspend fun updateAlertRule(rule: AlertRule): MobilePersistentState

    suspend fun markMobileNotified(eventId: String): MobilePersistentState

    suspend fun applySyncDelivery(update: SyncDeliveryUpdate): MobilePersistentState

    suspend fun recordInvalidInput(): MobilePersistentState
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
                pendingStateSequence = nextSequence,
            )
        }

    override suspend fun markMobileNotified(eventId: String): MobilePersistentState =
        update { current ->
            require(current.pendingEvent?.eventId == eventId) {
                "Only the current pending event can be marked notified"
            }
            current.copy(lastMobileNotifiedEventId = eventId)
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

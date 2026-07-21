package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.MAX_FUTURE_SKEW_MILLIS
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

package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.DataLayerPutResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.EpochMillisClock
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.MobileSyncGateway
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.PhoneStateSync
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncDeliveryUpdate
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncFailureClassification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

sealed interface SyncItemOutcome {
    data object NotPending : SyncItemOutcome

    data class Accepted(
        val sequence: Long,
        val eventId: String? = null,
    ) : SyncItemOutcome

    data class Rejected(
        val sequence: Long?,
        val eventId: String? = null,
        val classification: SyncFailureClassification,
    ) : SyncItemOutcome
}

data class MobileSyncBatchResult(
    val stateOutcome: SyncItemOutcome,
    val eventOutcome: SyncItemOutcome,
    val persistedState: MobilePersistentState,
)

fun interface PendingSyncSender {
    suspend fun syncPending(): MobileSyncBatchResult
}

class MobileDataLayerSender(
    private val repository: MobileStateRepository,
    private val gateway: MobileSyncGateway,
    private val clock: EpochMillisClock,
) : PendingSyncSender {
    override suspend fun syncPending(): MobileSyncBatchResult = coroutineScope {
        val state = repository.state.first()
        val sentAtEpochMillis = clock.now().also { require(it > 0) }
        val stateDeferred = async { putPendingState(state, sentAtEpochMillis) }
        val eventDeferred = async { putPendingEvent(state) }
        val stateOutcome = stateDeferred.await()
        val eventOutcome = eventDeferred.await()

        val attempted = stateOutcome !is SyncItemOutcome.NotPending ||
            eventOutcome !is SyncItemOutcome.NotPending
        val persistedState = if (attempted) {
            val confirmedState = stateOutcome as? SyncItemOutcome.Accepted
            val confirmedEvent = eventOutcome as? SyncItemOutcome.Accepted
            repository.applySyncDelivery(
                SyncDeliveryUpdate(
                    confirmedStateSequence = confirmedState?.sequence,
                    confirmedEventId = confirmedEvent?.eventId,
                    confirmedEventSequence = confirmedEvent?.sequence
                        ?.takeIf { confirmedEvent.eventId != null },
                    completedAtEpochMillis = clock.now().also { require(it > 0) },
                    failureClassification = listOf(stateOutcome, eventOutcome)
                        .filterIsInstance<SyncItemOutcome.Rejected>()
                        .firstOrNull()
                        ?.classification,
                )
            )
        } else {
            state
        }

        MobileSyncBatchResult(
            stateOutcome = stateOutcome,
            eventOutcome = eventOutcome,
            persistedState = persistedState,
        )
    }

    private suspend fun putPendingState(
        state: MobilePersistentState,
        sentAtEpochMillis: Long,
    ): SyncItemOutcome {
        val pendingSequence = state.pendingStateSequence
        if (pendingSequence == 0L) return SyncItemOutcome.NotPending
        val snapshot = state.lastSnapshot
        if (snapshot == null || snapshot.sequence != pendingSequence) {
            return SyncItemOutcome.Rejected(
                sequence = pendingSequence,
                classification = SyncFailureClassification.INVALID_OUTBOX,
            )
        }
        val result = safePut {
            gateway.putPhoneState(
                PhoneStateSync(
                    snapshot = snapshot,
                    thresholdPercent = state.alertRule.thresholdPercent,
                    monitoringEnabled = state.alertRule.monitoringEnabled,
                    sentAtEpochMillis = sentAtEpochMillis,
                    fullChargeNotificationEnabled =
                        state.alertRule.fullChargeNotificationEnabled,
                )
            )
        }
        return result.toOutcome(sequence = pendingSequence)
    }

    private suspend fun putPendingEvent(state: MobilePersistentState): SyncItemOutcome {
        val event = state.pendingEvent ?: return SyncItemOutcome.NotPending
        val result = safePut { gateway.putThresholdEvent(event) }
        return result.toOutcome(sequence = event.sequence, eventId = event.eventId)
    }

    private suspend fun safePut(
        block: suspend () -> DataLayerPutResult,
    ): DataLayerPutResult = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        DataLayerPutResult.Rejected(SyncFailureClassification.SECURITY_ERROR)
    } catch (_: RuntimeException) {
        DataLayerPutResult.Rejected(SyncFailureClassification.UNEXPECTED_ERROR)
    }

    private fun DataLayerPutResult.toOutcome(
        sequence: Long,
        eventId: String? = null,
    ): SyncItemOutcome = when (this) {
        DataLayerPutResult.Accepted -> SyncItemOutcome.Accepted(sequence, eventId)
        is DataLayerPutResult.Rejected -> SyncItemOutcome.Rejected(
            sequence = sequence,
            eventId = eventId,
            classification = classification,
        )
    }
}

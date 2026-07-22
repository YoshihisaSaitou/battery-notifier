package com.magicitengineer.batterynotifierandroidwearapp.application.notification

import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearNotificationCompletionOutcome
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearNotificationRetryReservationOutcome
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateRepository
import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class WearNotificationPostResult {
    POSTED,
    PERMISSION_DENIED,
    FAILED,
}

fun interface WearNotificationGateway {
    suspend fun post(event: ReceivedThresholdEvent): WearNotificationPostResult
}

sealed interface WearNotificationDeliveryResult {
    data object NotPending : WearNotificationDeliveryResult

    data object StaleReservation : WearNotificationDeliveryResult

    data object RetryNotEligible : WearNotificationDeliveryResult

    data object RetryExpired : WearNotificationDeliveryResult

    data object RetryExhausted : WearNotificationDeliveryResult

    data object RetryClockSkew : WearNotificationDeliveryResult

    data class Completed(
        val postResult: WearNotificationPostResult,
        val disposition: NotificationDisposition,
    ) : WearNotificationDeliveryResult
}

class DeliverPendingWearNotification(
    private val repository: WearStateRepository,
    private val gateway: WearNotificationGateway,
) {
    suspend fun deliver(state: WearPersistentState): WearNotificationDeliveryResult {
        if (state.notificationDisposition != NotificationDisposition.PENDING) {
            return WearNotificationDeliveryResult.NotPending
        }
        val event = state.lastEvent ?: return WearNotificationDeliveryResult.NotPending
        val postResult = try {
            gateway.post(event)
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                repository.completeNotification(
                    event.eventId,
                    NotificationDisposition.RESERVED_FAILED,
                )
            }
            throw cancellation
        } catch (_: SecurityException) {
            WearNotificationPostResult.PERMISSION_DENIED
        } catch (_: RuntimeException) {
            WearNotificationPostResult.FAILED
        }
        val requestedDisposition = when (postResult) {
            WearNotificationPostResult.POSTED -> NotificationDisposition.POSTED
            WearNotificationPostResult.PERMISSION_DENIED ->
                NotificationDisposition.PERMISSION_DENIED

            WearNotificationPostResult.FAILED -> NotificationDisposition.RESERVED_FAILED
        }
        val completion = repository.completeNotification(event.eventId, requestedDisposition)
        return if (completion.outcome == WearNotificationCompletionOutcome.APPLIED) {
            WearNotificationDeliveryResult.Completed(
                postResult,
                completion.state.notificationDisposition,
            )
        } else {
            WearNotificationDeliveryResult.StaleReservation
        }
    }

    suspend fun retry(
        state: WearPersistentState,
        nowEpochMillis: Long,
    ): WearNotificationDeliveryResult {
        require(nowEpochMillis > 0)
        val event = state.lastEvent ?: return WearNotificationDeliveryResult.RetryNotEligible
        val reservation = repository.reserveNotificationRetry(
            eventId = event.eventId,
            nowEpochMillis = nowEpochMillis,
        )
        return when (reservation.outcome) {
            WearNotificationRetryReservationOutcome.RESERVED -> deliver(reservation.state)
            WearNotificationRetryReservationOutcome.NOT_ELIGIBLE ->
                WearNotificationDeliveryResult.RetryNotEligible

            WearNotificationRetryReservationOutcome.EXPIRED ->
                WearNotificationDeliveryResult.RetryExpired

            WearNotificationRetryReservationOutcome.EXHAUSTED ->
                WearNotificationDeliveryResult.RetryExhausted

            WearNotificationRetryReservationOutcome.CLOCK_SKEW ->
                WearNotificationDeliveryResult.RetryClockSkew
        }
    }
}

object StableWearNotificationId {
    fun fromEventId(eventId: String): Int {
        require(eventId.isNotBlank())
        return eventId.hashCode() and Int.MAX_VALUE
    }
}

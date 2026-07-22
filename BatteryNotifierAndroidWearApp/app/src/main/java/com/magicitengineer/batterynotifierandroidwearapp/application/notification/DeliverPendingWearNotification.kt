package com.magicitengineer.batterynotifierandroidwearapp.application.notification

import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearNotificationCompletionOutcome
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateRepository
import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent
import kotlinx.coroutines.CancellationException

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
            throw cancellation
        } catch (_: SecurityException) {
            WearNotificationPostResult.PERMISSION_DENIED
        } catch (_: RuntimeException) {
            WearNotificationPostResult.FAILED
        }
        val disposition = when (postResult) {
            WearNotificationPostResult.POSTED -> NotificationDisposition.POSTED
            WearNotificationPostResult.PERMISSION_DENIED ->
                NotificationDisposition.PERMISSION_DENIED

            WearNotificationPostResult.FAILED -> NotificationDisposition.RESERVED_FAILED
        }
        val completion = repository.completeNotification(event.eventId, disposition)
        return if (completion.outcome == WearNotificationCompletionOutcome.APPLIED) {
            WearNotificationDeliveryResult.Completed(postResult, disposition)
        } else {
            WearNotificationDeliveryResult.StaleReservation
        }
    }
}

object StableWearNotificationId {
    fun fromEventId(eventId: String): Int {
        require(eventId.isNotBlank())
        return eventId.hashCode() and Int.MAX_VALUE
    }
}

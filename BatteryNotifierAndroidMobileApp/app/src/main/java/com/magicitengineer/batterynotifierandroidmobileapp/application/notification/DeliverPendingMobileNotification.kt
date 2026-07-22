package com.magicitengineer.batterynotifierandroidmobileapp.application.notification

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileNotificationCompletionOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.notification.MobileNotificationDisposition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

enum class MobileNotificationPostResult {
    POSTED,
    PERMISSION_DENIED,
    FAILED,
}

fun interface MobileNotificationGateway {
    suspend fun post(event: ThresholdReachedEvent): MobileNotificationPostResult
}

sealed interface MobileNotificationDeliveryResult {
    data object NotPending : MobileNotificationDeliveryResult

    data object StaleReservation : MobileNotificationDeliveryResult

    data class Completed(
        val postResult: MobileNotificationPostResult,
        val disposition: MobileNotificationDisposition,
    ) : MobileNotificationDeliveryResult
}

fun interface PendingMobileNotificationDeliverer {
    suspend fun deliverPending(): MobileNotificationDeliveryResult
}

class DeliverPendingMobileNotification(
    private val repository: MobileStateRepository,
    private val gateway: MobileNotificationGateway,
) : PendingMobileNotificationDeliverer {
    override suspend fun deliverPending(): MobileNotificationDeliveryResult {
        val event = repository.state.first().pendingMobileNotification
            ?: return MobileNotificationDeliveryResult.NotPending
        val postResult = try {
            gateway.post(event)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            MobileNotificationPostResult.PERMISSION_DENIED
        } catch (_: RuntimeException) {
            MobileNotificationPostResult.FAILED
        }
        val disposition = when (postResult) {
            MobileNotificationPostResult.POSTED -> MobileNotificationDisposition.POSTED
            MobileNotificationPostResult.PERMISSION_DENIED ->
                MobileNotificationDisposition.PERMISSION_DENIED
            MobileNotificationPostResult.FAILED -> MobileNotificationDisposition.FAILED
        }
        val completion = repository.completeMobileNotification(event.eventId, disposition)
        return if (completion.outcome == MobileNotificationCompletionOutcome.APPLIED) {
            MobileNotificationDeliveryResult.Completed(postResult, disposition)
        } else {
            MobileNotificationDeliveryResult.StaleReservation
        }
    }
}

object StableMobileNotificationId {
    fun fromEventId(eventId: String): Int {
        require(eventId.isNotBlank())
        return eventId.hashCode() and Int.MAX_VALUE
    }
}

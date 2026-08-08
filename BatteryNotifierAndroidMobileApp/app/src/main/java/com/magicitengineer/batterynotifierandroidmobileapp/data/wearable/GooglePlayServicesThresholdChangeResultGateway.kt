package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.Wearable
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.ThresholdChangeResultGateway
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.ThresholdChangeResultSendOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResult
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

class GooglePlayServicesThresholdChangeResultGateway(
    context: Context,
) : ThresholdChangeResultGateway {
    private val applicationContext = context.applicationContext

    override suspend fun send(
        nodeId: String,
        result: ThresholdChangeResult,
    ): ThresholdChangeResultSendOutcome = try {
        Wearable.getMessageClient(applicationContext).sendMessage(
            nodeId,
            BatteryDataLayerContractV1.CHANGE_THRESHOLD_RESULT_PATH,
            MobileThresholdChangeMessageCodec.encodeResult(result),
        ).awaitSend()
        ThresholdChangeResultSendOutcome.SENT
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: SecurityException) {
        ThresholdChangeResultSendOutcome.FAILED
    } catch (_: RuntimeException) {
        ThresholdChangeResultSendOutcome.FAILED
    }
}

private suspend fun Task<Int>.awaitSend(): Int =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener {
            if (continuation.isActive) {
                continuation.resumeWith(Result.failure(it))
            }
        }
        addOnCanceledListener { continuation.cancel() }
    }

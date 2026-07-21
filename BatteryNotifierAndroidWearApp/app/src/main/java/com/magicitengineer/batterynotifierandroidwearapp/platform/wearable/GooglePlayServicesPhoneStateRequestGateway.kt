package com.magicitengineer.batterynotifierandroidwearapp.platform.wearable

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.Wearable
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.PhoneStateRequestGateway
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.PhoneStateRequestResult
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.WearDataLayerContract
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine

class GooglePlayServicesPhoneStateRequestGateway(
    context: Context,
) : PhoneStateRequestGateway {
    private val applicationContext = context.applicationContext

    override suspend fun requestCurrentState(): PhoneStateRequestResult {
        val availability = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(applicationContext)
        if (availability != ConnectionResult.SUCCESS) {
            return PhoneStateRequestResult.API_UNAVAILABLE
        }

        return try {
            val nodes = Wearable.getNodeClient(applicationContext).connectedNodes.awaitResult()
            if (nodes.isEmpty()) {
                PhoneStateRequestResult.NO_REACHABLE_NODE
            } else {
                val messageClient = Wearable.getMessageClient(applicationContext)
                val sent = coroutineScope {
                    nodes.map { node ->
                        async {
                            try {
                                messageClient.sendMessage(
                                    node.id,
                                    WearDataLayerContract.REQUEST_STATE_PATH,
                                    byteArrayOf(),
                                ).awaitResult()
                                true
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: SecurityException) {
                                false
                            } catch (_: RuntimeException) {
                                false
                            }
                        }
                    }.awaitAll()
                }
                if (sent.any { it }) {
                    PhoneStateRequestResult.SENT
                } else {
                    PhoneStateRequestResult.FAILED
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            PhoneStateRequestResult.FAILED
        } catch (_: RuntimeException) {
            PhoneStateRequestResult.FAILED
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener { continuation.cancel() }
}

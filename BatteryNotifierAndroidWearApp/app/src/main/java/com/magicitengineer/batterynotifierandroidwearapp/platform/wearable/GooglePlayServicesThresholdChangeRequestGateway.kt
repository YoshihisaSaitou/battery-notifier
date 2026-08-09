package com.magicitengineer.batterynotifierandroidwearapp.platform.wearable

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.DataMap
import com.magicitengineer.batterynotifierandroidwearapp.application.settings.ThresholdChangeRequestGateway
import com.magicitengineer.batterynotifierandroidwearapp.application.settings.ThresholdChangeRequestSendResult
import com.magicitengineer.batterynotifierandroidwearapp.data.wearable.WearThresholdChangeMessageCodec
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeRequest
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.WearDataLayerContract
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.SUPPORTED_SCHEMA_VERSION
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

class GooglePlayServicesThresholdChangeRequestGateway(
    context: Context,
) : ThresholdChangeRequestGateway {
    private val applicationContext = context.applicationContext

    override suspend fun isAvailable(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(applicationContext)
        if (availability != ConnectionResult.SUCCESS) return false
        return try {
            Wearable.getCapabilityClient(applicationContext)
                .getCapability(
                    WearDataLayerContract.MOBILE_THRESHOLD_WRITER_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE,
                )
                .awaitThresholdResult()
                .nodes
                .isNotEmpty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    override suspend fun send(
        request: ThresholdChangeRequest,
    ): ThresholdChangeRequestSendResult {
        val availability = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(applicationContext)
        if (availability != ConnectionResult.SUCCESS) {
            return ThresholdChangeRequestSendResult.API_UNAVAILABLE
        }
        return try {
            val capability = Wearable.getCapabilityClient(applicationContext)
                .getCapability(
                    WearDataLayerContract.MOBILE_THRESHOLD_WRITER_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE,
                )
                .awaitThresholdResult()
            val node = capability.nodes.preferredNode()
                ?: return ThresholdChangeRequestSendResult.NO_REACHABLE_NODE
            Wearable.getMessageClient(applicationContext).sendMessage(
                node.id,
                WearDataLayerContract.CHANGE_THRESHOLD_PATH,
                WearThresholdChangeMessageCodec.encodeRequest(request),
            ).awaitThresholdResult()
            ThresholdChangeRequestSendResult.SENT
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SecurityException) {
            ThresholdChangeRequestSendResult.FAILED
        } catch (_: RuntimeException) {
            ThresholdChangeRequestSendResult.FAILED
        }
    }
}

class GooglePlayServicesFullChargeSettingGateway(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    suspend fun isAvailable(): Boolean = resolveReachableNode() != null

    suspend fun send(enabled: Boolean, expectedEnabled: Boolean): Boolean {
        val node = resolveReachableNode() ?: return false
        return try {
            val payload = DataMap().apply {
                putInt(WearDataLayerContract.KEY_SCHEMA_VERSION, SUPPORTED_SCHEMA_VERSION)
                putString(WearDataLayerContract.KEY_REQUEST_ID, java.util.UUID.randomUUID().toString())
                putBoolean(WearDataLayerContract.KEY_FULL_CHARGE_NOTIFICATION_ENABLED, enabled)
                putBoolean(
                    WearDataLayerContract.KEY_EXPECTED_FULL_CHARGE_NOTIFICATION_ENABLED,
                    expectedEnabled,
                )
            }.toByteArray()
            Wearable.getMessageClient(applicationContext).sendMessage(
                node.id,
                WearDataLayerContract.CHANGE_FULL_CHARGE_SETTING_PATH,
                payload,
            ).awaitThresholdResult()
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            false
        }
    }

    private suspend fun resolveReachableNode(): Node? {
        if (
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(applicationContext) !=
            ConnectionResult.SUCCESS
        ) return null
        return try {
            Wearable.getCapabilityClient(applicationContext)
                .getCapability(
                    WearDataLayerContract.MOBILE_FULL_CHARGE_SETTING_WRITER_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE,
                )
                .awaitThresholdResult()
                .nodes
                .preferredNode()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            null
        }
    }
}

private fun Set<Node>.preferredNode(): Node? =
    sortedWith(compareByDescending<Node> { it.isNearby }.thenBy { it.id }).firstOrNull()

private suspend fun <T> Task<T>.awaitThresholdResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener { continuation.cancel() }
    }

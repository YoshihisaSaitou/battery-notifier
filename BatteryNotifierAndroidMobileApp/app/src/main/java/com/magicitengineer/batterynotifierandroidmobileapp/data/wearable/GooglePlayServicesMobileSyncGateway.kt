package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.DataLayerPutResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.MobileSyncGateway
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.PhoneStateSync
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncFailureClassification
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class GooglePlayServicesMobileSyncGateway(
    context: Context,
) : MobileSyncGateway {
    private val applicationContext = context.applicationContext

    override suspend fun putPhoneState(state: PhoneStateSync): DataLayerPutResult =
        put(MobileDataLayerPayloadMapper.phoneState(state))

    override suspend fun putThresholdEvent(
        event: ThresholdReachedEvent,
    ): DataLayerPutResult = put(MobileDataLayerPayloadMapper.thresholdEvent(event))

    private suspend fun put(payload: DataLayerPayload): DataLayerPutResult {
        val availability = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(applicationContext)
        if (availability != ConnectionResult.SUCCESS) {
            return DataLayerPutResult.Rejected(SyncFailureClassification.API_UNAVAILABLE)
        }

        return try {
            val mapRequest = PutDataMapRequest.create(payload.path)
            payload.values.forEach { (key, value) ->
                when (value) {
                    is DataLayerValue.BooleanValue -> mapRequest.dataMap.putBoolean(key, value.value)
                    is DataLayerValue.IntValue -> mapRequest.dataMap.putInt(key, value.value)
                    is DataLayerValue.LongValue -> mapRequest.dataMap.putLong(key, value.value)
                    is DataLayerValue.StringValue -> mapRequest.dataMap.putString(key, value.value)
                }
            }
            val putRequest = mapRequest.asPutDataRequest().apply {
                if (payload.urgent) setUrgent()
            }
            Wearable.getDataClient(applicationContext)
                .putDataItem(putRequest)
                .awaitPutResult()
        } catch (_: SecurityException) {
            DataLayerPutResult.Rejected(SyncFailureClassification.SECURITY_ERROR)
        } catch (_: RuntimeException) {
            DataLayerPutResult.Rejected(SyncFailureClassification.UNEXPECTED_ERROR)
        }
    }
}

private suspend fun Task<DataItem>.awaitPutResult(): DataLayerPutResult =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener {
            if (continuation.isActive) {
                continuation.resume(DataLayerPutResult.Accepted)
            }
        }
        addOnFailureListener { error ->
            if (continuation.isActive) {
                val classification = if (error is SecurityException) {
                    SyncFailureClassification.SECURITY_ERROR
                } else {
                    SyncFailureClassification.TASK_FAILURE
                }
                continuation.resume(DataLayerPutResult.Rejected(classification))
            }
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }

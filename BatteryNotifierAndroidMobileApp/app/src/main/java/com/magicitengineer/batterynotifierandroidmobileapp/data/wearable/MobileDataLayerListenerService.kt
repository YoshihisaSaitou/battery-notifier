package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileDataLayerMessageHandler
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileAppContainer
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal fun shouldSyncForReachableWearCapability(
    capabilityName: String,
    reachableNodeCount: Int,
): Boolean =
    capabilityName == BatteryDataLayerContractV1.WEAR_STATE_RECEIVER_CAPABILITY &&
        reachableNodeCount > 0

internal suspend fun dispatchThresholdChangeRequestMessage(
    sourceNodeId: String,
    payload: ByteArray,
    decode: (ByteArray) -> ThresholdChangeRequestDecodeResult =
        MobileThresholdChangeMessageCodec::decodeRequest,
    onValid: suspend (String, ThresholdChangeRequest) -> Unit,
    onUnsupportedSchema: suspend (Int) -> Unit,
    onInvalid: suspend () -> Unit,
) {
    when (val decoded = decode(payload)) {
        is ThresholdChangeRequestDecodeResult.Valid ->
            onValid(sourceNodeId, decoded.request)

        is ThresholdChangeRequestDecodeResult.UnsupportedSchema ->
            onUnsupportedSchema(decoded.schemaVersion)

        ThresholdChangeRequestDecodeResult.Invalid ->
            onInvalid()
    }
}

class MobileDataLayerListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageHandler by lazy {
        MobileDataLayerMessageHandler(
            requestStatePath = BatteryDataLayerContractV1.REQUEST_STATE_PATH,
            syncRunner = MobileAppContainer.syncCoordinator(this),
        )
    }
    private val runtimeTriggerHandler by lazy {
        MobileAppContainer.runtimeTriggerHandler(this)
    }
    private val thresholdChangeHandler by lazy {
        MobileAppContainer.wearThresholdChangeHandler(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        serviceScope.launch {
            if (messageEvent.path == BatteryDataLayerContractV1.CHANGE_THRESHOLD_PATH) {
                dispatchThresholdChangeRequestMessage(
                    sourceNodeId = messageEvent.sourceNodeId,
                    payload = messageEvent.data,
                    onValid = { sourceNodeId, request ->
                        thresholdChangeHandler.handle(
                            sourceNodeId = sourceNodeId,
                            request = request,
                        )
                    },
                    onUnsupportedSchema = { schemaVersion ->
                        MobileAppContainer.repository(this@MobileDataLayerListenerService)
                            .recordUnsupportedSchema(schemaVersion)
                    },
                    onInvalid = {
                        MobileAppContainer.repository(this@MobileDataLayerListenerService)
                            .recordInvalidInput()
                    },
                )
            } else {
                messageHandler.handle(messageEvent.path)
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        if (
            shouldSyncForReachableWearCapability(
                capabilityInfo.name,
                capabilityInfo.nodes.size,
            )
        ) {
            serviceScope.launch {
                runtimeTriggerHandler.onConnectionRecovered()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

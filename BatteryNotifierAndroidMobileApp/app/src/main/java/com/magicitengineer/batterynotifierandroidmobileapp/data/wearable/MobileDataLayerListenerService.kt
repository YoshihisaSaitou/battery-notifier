package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.DataMap
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileDataLayerMessageHandler
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileAppContainer
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.UUID

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
            if (messageEvent.path == BatteryDataLayerContractV1.CHANGE_FULL_CHARGE_SETTING_PATH) {
                handleFullChargeSetting(messageEvent)
            } else if (messageEvent.path == BatteryDataLayerContractV1.CHANGE_THRESHOLD_PATH) {
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

    private suspend fun handleFullChargeSetting(messageEvent: MessageEvent) {
        val repository = MobileAppContainer.repository(this)
        val map = runCatching { DataMap.fromByteArray(messageEvent.data) }.getOrNull()
        val requestId = map?.getString(BatteryDataLayerContractV1.Keys.REQUEST_ID)
        if (
            map == null ||
            map.getInt(BatteryDataLayerContractV1.Keys.SCHEMA_VERSION, -1) !=
            BatteryDataLayerContractV1.SCHEMA_VERSION ||
            requestId.isNullOrBlank() ||
            runCatching { UUID.fromString(requestId) }.isFailure ||
            !map.containsKey(BatteryDataLayerContractV1.Keys.FULL_CHARGE_NOTIFICATION_ENABLED) ||
            !map.containsKey(
                BatteryDataLayerContractV1.Keys.EXPECTED_FULL_CHARGE_NOTIFICATION_ENABLED
            )
        ) {
            repository.recordInvalidInput()
            return
        }
        val booleans = runCatching {
            map.getBoolean(
                BatteryDataLayerContractV1.Keys.FULL_CHARGE_NOTIFICATION_ENABLED
            ) to map.getBoolean(
                BatteryDataLayerContractV1.Keys.EXPECTED_FULL_CHARGE_NOTIFICATION_ENABLED
            )
        }.getOrElse {
            repository.recordInvalidInput()
            return
        }
        val (requested, expected) = booleans
        val current = repository.state.first().alertRule.fullChargeNotificationEnabled
        if (requested != current && expected == current) {
            MobileAppContainer.syncCoordinator(this)
                .saveFullChargeNotificationEnabled(requested)
        } else {
            MobileAppContainer.syncCoordinator(this)
                .sync(com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncTrigger.SETTINGS_CHANGED)
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

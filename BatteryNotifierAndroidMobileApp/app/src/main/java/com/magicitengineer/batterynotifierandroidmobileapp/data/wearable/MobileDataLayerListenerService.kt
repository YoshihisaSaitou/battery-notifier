package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileDataLayerMessageHandler
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MobileDataLayerListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageHandler by lazy {
        MobileDataLayerMessageHandler(
            requestStatePath = BatteryDataLayerContractV1.REQUEST_STATE_PATH,
            syncRunner = MobileAppContainer.syncCoordinator(this),
        )
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        serviceScope.launch {
            messageHandler.handle(messageEvent.path)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

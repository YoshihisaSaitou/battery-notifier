package com.magicitengineer.batterynotifierandroidwearapp.data.wearable

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearAppContainer
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateApplyOutcome
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.WearDataItemProcessingResult
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceiveErrorClassification
import com.magicitengineer.batterynotifierandroidwearapp.platform.presentation.AndroidWearSurfaceUpdater
import com.magicitengineer.batterynotifierandroidwearapp.platform.time.SystemEpochMillisClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WearDataLayerListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val repository = WearAppContainer.repository(this)
        val processor = WearAppContainer.dataItemProcessor(this)
        val notificationDelivery = WearAppContainer.notificationDelivery(this)
        val surfaceUpdater = AndroidWearSurfaceUpdater(this)
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val path = event.dataItem.uri.path ?: return@forEach
            val values = runCatching { DataMapValueReader.read(event.dataItem) }
                .getOrElse {
                    serviceScope.launch {
                        repository.recordInvalidPayload(ReceiveErrorClassification.DATA_MAP_ERROR)
                    }
                    return@forEach
                }
            val receivedAtEpochMillis = SystemEpochMillisClock.now()
            serviceScope.launch {
                val result = processor.process(path, values, receivedAtEpochMillis)
                if (
                    result is WearDataItemProcessingResult.Applied &&
                    result.result.outcome == WearStateApplyOutcome.APPLIED
                ) {
                    surfaceUpdater.requestRefresh()
                    notificationDelivery.deliver(result.result.state)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

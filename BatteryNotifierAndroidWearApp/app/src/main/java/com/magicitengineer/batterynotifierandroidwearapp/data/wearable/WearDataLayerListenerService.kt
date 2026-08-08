package com.magicitengineer.batterynotifierandroidwearapp.data.wearable

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.gms.wearable.MessageEvent
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearAppContainer
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearStateApplyOutcome
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.WearDataItemProcessingResult
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceiveErrorClassification
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.WearDataLayerContract
import com.magicitengineer.batterynotifierandroidwearapp.platform.presentation.AndroidWearSurfaceUpdater
import com.magicitengineer.batterynotifierandroidwearapp.platform.time.SystemEpochMillisClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun ownsInitialWearNotificationDelivery(path: String): Boolean =
    path == WearDataLayerContract.THRESHOLD_EVENT_PATH

internal suspend fun <T> protectInitialWearNotificationDelivery(
    path: String,
    block: suspend () -> T,
): T = if (ownsInitialWearNotificationDelivery(path)) {
    withContext(NonCancellable) { block() }
} else {
    block()
}

class WearDataLayerListenerService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationRecovery: Deferred<Unit>

    override fun onCreate() {
        super.onCreate()
        notificationRecovery = serviceScope.async {
            WearAppContainer.recoverInterruptedNotificationOnce(
                context = this@WearDataLayerListenerService,
                nowEpochMillis = SystemEpochMillisClock.now(),
            )
            Unit
        }
    }

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
                notificationRecovery.await()
                protectInitialWearNotificationDelivery(path) {
                    val result = processor.process(path, values, receivedAtEpochMillis)
                    if (
                        result is WearDataItemProcessingResult.Applied &&
                        result.result.outcome == WearStateApplyOutcome.APPLIED
                    ) {
                        surfaceUpdater.requestRefresh()
                        if (ownsInitialWearNotificationDelivery(path)) {
                            notificationDelivery.deliver(result.result.state)
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearDataLayerContract.CHANGE_THRESHOLD_RESULT_PATH) {
            return
        }
        val repository = WearAppContainer.repository(this)
        serviceScope.launch {
            when (
                val decoded =
                    WearThresholdChangeMessageCodec.decodeResult(messageEvent.data)
            ) {
                is ThresholdChangeResultDecodeResult.Valid -> {
                    repository.applyThresholdChangeResult(decoded.result)
                }

                is ThresholdChangeResultDecodeResult.UnsupportedSchema -> {
                    repository.recordUnsupportedSchema(decoded.schemaVersion)
                }

                ThresholdChangeResultDecodeResult.Invalid -> {
                    repository.recordInvalidPayload(
                        ReceiveErrorClassification.DATA_MAP_ERROR
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

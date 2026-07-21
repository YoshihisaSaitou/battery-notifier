package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import android.content.Context
import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.CurrentBatteryStateRefresher
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileDataLayerSender
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinator
import com.magicitengineer.batterynotifierandroidmobileapp.data.wearable.GooglePlayServicesMobileSyncGateway
import com.magicitengineer.batterynotifierandroidmobileapp.platform.battery.AndroidCurrentBatteryReadingSource
import com.magicitengineer.batterynotifierandroidmobileapp.platform.identity.RandomUuidEventIdFactory
import com.magicitengineer.batterynotifierandroidmobileapp.platform.time.SystemEpochMillisClock

object MobileAppContainer {
    @Volatile
    private var coordinatorInstance: MobileSyncCoordinator? = null

    fun syncCoordinator(context: Context): MobileSyncCoordinator =
        coordinatorInstance ?: synchronized(this) {
            coordinatorInstance ?: createCoordinator(context.applicationContext).also {
                coordinatorInstance = it
            }
        }

    private fun createCoordinator(context: Context): MobileSyncCoordinator {
        val repository = ProtoMobileStateRepository(context.mobileStateDataStore)
        val clock = SystemEpochMillisClock
        return MobileSyncCoordinator(
            refresher = CurrentBatteryStateRefresher(
                source = AndroidCurrentBatteryReadingSource(context, clock),
                repository = repository,
                eventIdFactory = RandomUuidEventIdFactory,
            ),
            sender = MobileDataLayerSender(
                repository = repository,
                gateway = GooglePlayServicesMobileSyncGateway(context),
                clock = clock,
            ),
        )
    }
}

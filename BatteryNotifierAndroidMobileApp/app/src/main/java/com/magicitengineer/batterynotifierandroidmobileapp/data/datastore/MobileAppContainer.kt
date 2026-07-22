package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import android.content.Context
import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.CurrentBatteryStateRefresher
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringController
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.RepositoryMonitoringStateUpdater
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.RepositoryMonitoringStartBaselineResetter
import com.magicitengineer.batterynotifierandroidmobileapp.application.notification.DeliverPendingMobileNotification
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.RepositoryThresholdSettingUpdater
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSettingsController
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileDataLayerSender
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileRuntimeTriggerHandler
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinator
import com.magicitengineer.batterynotifierandroidmobileapp.data.wearable.GooglePlayServicesMobileSyncGateway
import com.magicitengineer.batterynotifierandroidmobileapp.platform.battery.AndroidBatteryChangedCallback
import com.magicitengineer.batterynotifierandroidmobileapp.platform.battery.AndroidBatteryChangedIntentMapper
import com.magicitengineer.batterynotifierandroidmobileapp.platform.battery.AndroidCurrentBatteryReadingSource
import com.magicitengineer.batterynotifierandroidmobileapp.platform.identity.RandomUuidEventIdFactory
import com.magicitengineer.batterynotifierandroidmobileapp.platform.notification.AndroidMobileAlertNotificationGateway
import com.magicitengineer.batterynotifierandroidmobileapp.platform.service.AndroidMonitoringServiceGateway
import com.magicitengineer.batterynotifierandroidmobileapp.platform.time.SystemEpochMillisClock

object MobileAppContainer {
    @Volatile
    private var componentsInstance: MobileComponents? = null

    fun syncCoordinator(context: Context): MobileSyncCoordinator =
        components(context).coordinator

    fun runtimeTriggerHandler(context: Context): MobileRuntimeTriggerHandler =
        MobileRuntimeTriggerHandler(syncCoordinator(context))

    fun thresholdSettingsController(context: Context): ThresholdSettingsController =
        components(context).thresholdSettingsController

    fun batteryChangedCallback(context: Context): AndroidBatteryChangedCallback =
        components(context).batteryChangedCallback

    fun monitoringController(context: Context): MonitoringController =
        components(context).monitoringController

    private fun components(context: Context): MobileComponents =
        componentsInstance ?: synchronized(this) {
            componentsInstance ?: createComponents(context.applicationContext).also {
                componentsInstance = it
            }
        }

    private fun createComponents(context: Context): MobileComponents {
        val repository = ProtoMobileStateRepository(context.mobileStateDataStore)
        val clock = SystemEpochMillisClock
        val batteryRefresher = CurrentBatteryStateRefresher(
            source = AndroidCurrentBatteryReadingSource(context, clock),
            repository = repository,
            eventIdFactory = RandomUuidEventIdFactory,
        )
        val coordinator = MobileSyncCoordinator(
            refresher = batteryRefresher,
            sender = MobileDataLayerSender(
                repository = repository,
                gateway = GooglePlayServicesMobileSyncGateway(context),
                clock = clock,
            ),
            thresholdSettingUpdater = RepositoryThresholdSettingUpdater(repository),
            batteryReadResultProcessor = batteryRefresher,
            monitoringStateUpdater = RepositoryMonitoringStateUpdater(repository),
            monitoringStartBaselineResetter =
                RepositoryMonitoringStartBaselineResetter(repository),
            monitoringServiceGateway = AndroidMonitoringServiceGateway(context),
            mobileNotificationDeliverer = DeliverPendingMobileNotification(
                repository = repository,
                gateway = AndroidMobileAlertNotificationGateway(context),
            ),
        )
        return MobileComponents(
            coordinator = coordinator,
            thresholdSettingsController = ThresholdSettingsController(
                repository = repository,
                runner = coordinator,
            ),
            batteryChangedCallback = AndroidBatteryChangedCallback(
                mapper = AndroidBatteryChangedIntentMapper(clock),
                runner = coordinator,
            ),
            monitoringController = MonitoringController(
                repository = repository,
                runner = coordinator,
            ),
        )
    }

    private data class MobileComponents(
        val coordinator: MobileSyncCoordinator,
        val thresholdSettingsController: ThresholdSettingsController,
        val batteryChangedCallback: AndroidBatteryChangedCallback,
        val monitoringController: MonitoringController,
    )
}

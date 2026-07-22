package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import android.content.Context
import com.magicitengineer.batterynotifierandroidwearapp.application.notification.DeliverPendingWearNotification
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.ProcessWearDataItem
import com.magicitengineer.batterynotifierandroidwearapp.platform.notification.AndroidWearNotificationGateway

object WearAppContainer {
    @Volatile
    private var repositoryInstance: WearStateRepository? = null

    fun repository(context: Context): WearStateRepository =
        repositoryInstance ?: synchronized(this) {
            repositoryInstance ?: ProtoWearStateRepository(
                context.applicationContext.wearStateDataStore
            ).also { repositoryInstance = it }
        }

    fun dataItemProcessor(context: Context): ProcessWearDataItem =
        ProcessWearDataItem(repository(context))

    fun notificationDelivery(context: Context): DeliverPendingWearNotification =
        DeliverPendingWearNotification(
            repository = repository(context),
            gateway = AndroidWearNotificationGateway(context),
        )
}

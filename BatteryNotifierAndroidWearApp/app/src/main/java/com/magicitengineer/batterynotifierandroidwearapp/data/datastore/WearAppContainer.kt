package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import android.content.Context
import com.magicitengineer.batterynotifierandroidwearapp.application.notification.DeliverPendingWearNotification
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.ProcessWearDataItem
import com.magicitengineer.batterynotifierandroidwearapp.platform.notification.AndroidWearNotificationGateway
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first

object WearAppContainer {
    @Volatile
    private var repositoryInstance: WearStateRepository? = null
    private val notificationRecoveryMutex = Mutex()
    private var notificationRecoveryCompleted = false

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

    suspend fun recoverInterruptedNotificationOnce(
        context: Context,
        nowEpochMillis: Long,
    ): WearNotificationRecoveryResult = notificationRecoveryMutex.withLock {
        if (notificationRecoveryCompleted) {
            return@withLock WearNotificationRecoveryResult(
                outcome = WearNotificationRecoveryOutcome.NOT_REQUIRED,
                state = repository(context).state.first(),
            )
        }
        repository(context).recoverInterruptedNotification(nowEpochMillis).also {
            notificationRecoveryCompleted = true
        }
    }
}

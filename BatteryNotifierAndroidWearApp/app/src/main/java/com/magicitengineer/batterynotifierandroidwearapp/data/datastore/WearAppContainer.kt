package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import android.content.Context
import com.magicitengineer.batterynotifierandroidwearapp.application.sync.ProcessWearDataItem

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
}

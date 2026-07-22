package com.magicitengineer.batterynotifierandroidmobileapp

import android.app.Application
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BatteryNotifierApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launchApplicationTask(block: suspend CoroutineScope.() -> Unit) {
        applicationScope.launch(block = block)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            MobileAppContainer.runtimeTriggerHandler(this@BatteryNotifierApplication)
                .onProcessRestored()
        }
    }
}

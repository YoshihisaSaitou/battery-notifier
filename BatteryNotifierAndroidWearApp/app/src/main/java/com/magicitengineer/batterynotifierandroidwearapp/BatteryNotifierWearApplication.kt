package com.magicitengineer.batterynotifierandroidwearapp

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BatteryNotifierWearApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
    }

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            applicationScope.launch {
                val now = System.currentTimeMillis().coerceAtLeast(1L)
                WearAppContainer.recoverInterruptedNotificationOnce(
                    context = this@BatteryNotifierWearApplication,
                    nowEpochMillis = now,
                )
                val repository = WearAppContainer.repository(this@BatteryNotifierWearApplication)
                WearAppContainer.notificationDelivery(this@BatteryNotifierWearApplication).retry(
                    state = repository.state.first(),
                    nowEpochMillis = now,
                )
            }
        }
    }
}

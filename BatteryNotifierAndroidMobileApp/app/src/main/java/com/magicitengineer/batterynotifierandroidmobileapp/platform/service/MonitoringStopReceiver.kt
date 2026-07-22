package com.magicitengineer.batterynotifierandroidmobileapp.platform.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MonitoringStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_MONITORING) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                MobileAppContainer.monitoringController(context).stopMonitoring()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP_MONITORING =
            "com.magicitengineer.batterynotifierandroidmobileapp.action.STOP_MONITORING"
    }
}

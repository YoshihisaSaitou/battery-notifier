package com.magicitengineer.batterynotifierandroidmobileapp.platform.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MonitoringRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                MobileAppContainer.monitoringController(context).recoverIfNeeded()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

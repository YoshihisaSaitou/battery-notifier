package com.magicitengineer.batterynotifierandroidmobileapp.platform.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.magicitengineer.batterynotifierandroidmobileapp.BatteryNotifierApplication
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileAppContainer
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.platform.notification.AndroidMonitoringNotificationFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BatteryMonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pendingResult = goAsync()
            serviceScope.launch {
                try {
                    MobileAppContainer.batteryChangedCallback(context).onReceive(intent)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notificationFactory = AndroidMonitoringNotificationFactory(this)
        try {
            notificationFactory.ensureChannel()
            startAsForeground(notificationFactory, AlertRule.DEFAULT_THRESHOLD_PERCENT)
            ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        } catch (_: RuntimeException) {
            markRecoveryRequiredAndStop()
            return
        }

        serviceScope.launch {
            MobileAppContainer.thresholdSettingsController(this@BatteryMonitoringService)
                .state
                .collectLatest { settings ->
                    if (!settings.monitoringEnabled) {
                        stopSelf()
                    } else {
                        startAsForeground(notificationFactory, settings.thresholdPercent)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground(
        factory: AndroidMonitoringNotificationFactory,
        thresholdPercent: Int,
    ) {
        ServiceCompat.startForeground(
            this,
            AndroidMonitoringNotificationFactory.NOTIFICATION_ID,
            factory.create(thresholdPercent),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun markRecoveryRequiredAndStop() {
        (application as BatteryNotifierApplication).launchApplicationTask {
            MobileAppContainer.monitoringController(this@BatteryMonitoringService)
                .markRecoveryRequired()
        }
        stopSelf()
    }
}

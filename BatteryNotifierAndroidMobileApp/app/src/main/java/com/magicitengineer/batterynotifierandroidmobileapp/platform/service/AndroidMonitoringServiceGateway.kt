package com.magicitengineer.batterynotifierandroidmobileapp.platform.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringServiceGateway

class AndroidMonitoringServiceGateway(
    context: Context,
) : MonitoringServiceGateway {
    private val applicationContext = context.applicationContext

    override fun start() {
        ContextCompat.startForegroundService(
            applicationContext,
            Intent(applicationContext, BatteryMonitoringService::class.java),
        )
    }

    override fun stop() {
        applicationContext.stopService(
            Intent(applicationContext, BatteryMonitoringService::class.java),
        )
    }
}

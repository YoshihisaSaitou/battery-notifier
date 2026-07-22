package com.magicitengineer.batterynotifierandroidmobileapp.platform.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.magicitengineer.batterynotifierandroidmobileapp.MainActivity
import com.magicitengineer.batterynotifierandroidmobileapp.R
import com.magicitengineer.batterynotifierandroidmobileapp.platform.service.MonitoringStopReceiver

class AndroidMonitoringNotificationFactory(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.monitoring_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = applicationContext.getString(R.string.monitoring_channel_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    fun create(thresholdPercent: Int): Notification = NotificationCompat.Builder(
        applicationContext,
        CHANNEL_ID,
    )
        .setSmallIcon(R.drawable.ic_battery_monitoring)
        .setContentTitle(applicationContext.getString(R.string.monitoring_notification_title))
        .setContentText(
            applicationContext.getString(
                R.string.monitoring_notification_text,
                thresholdPercent,
            ),
        )
        .setContentIntent(openAppPendingIntent())
        .addAction(
            0,
            applicationContext.getString(R.string.monitoring_stop_action),
            stopMonitoringPendingIntent(),
        )
        .setCategory(Notification.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setLocalOnly(true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        OPEN_APP_REQUEST_CODE,
        Intent(applicationContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopMonitoringPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        STOP_REQUEST_CODE,
        Intent(applicationContext, MonitoringStopReceiver::class.java).setAction(
            MonitoringStopReceiver.ACTION_STOP_MONITORING,
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "monitoring_status"
        const val NOTIFICATION_ID = 10_001
        private const val OPEN_APP_REQUEST_CODE = 10_001
        private const val STOP_REQUEST_CODE = 10_002
    }
}

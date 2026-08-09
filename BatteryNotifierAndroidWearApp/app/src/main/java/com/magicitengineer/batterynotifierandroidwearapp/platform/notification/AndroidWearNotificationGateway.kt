package com.magicitengineer.batterynotifierandroidwearapp.platform.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.magicitengineer.batterynotifierandroidwearapp.R
import com.magicitengineer.batterynotifierandroidwearapp.application.notification.StableWearNotificationId
import com.magicitengineer.batterynotifierandroidwearapp.application.notification.WearNotificationGateway
import com.magicitengineer.batterynotifierandroidwearapp.application.notification.WearNotificationPostResult
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.ReceivedThresholdEvent
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.AlertEventKind
import com.magicitengineer.batterynotifierandroidwearapp.presentation.MainActivity

const val BATTERY_ALERT_CHANNEL_ID = "battery_alerts"

class AndroidWearNotificationGateway(
    context: Context,
) : WearNotificationGateway {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

    override suspend fun post(event: ReceivedThresholdEvent): WearNotificationPostResult {
        createChannel()
        if (!notificationsAreAllowed()) {
            return WearNotificationPostResult.PERMISSION_DENIED
        }

        return postWhenAllowed(event)
    }

    @SuppressLint("NotificationPermission")
    private fun postWhenAllowed(
        event: ReceivedThresholdEvent,
    ): WearNotificationPostResult = try {
        val notificationId = StableWearNotificationId.fromEventId(event.eventId)
        val openApp = PendingIntent.getActivity(
            applicationContext,
                notificationId,
                Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(applicationContext, BATTERY_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.splash_icon)
            .setContentTitle(
                if (event.kind == AlertEventKind.FULL_CHARGE) {
                    applicationContext.getString(R.string.full_charge_reached_title)
                } else {
                    applicationContext.getString(
                        R.string.phone_battery_alert_title,
                        event.levelPercent,
                    )
                }
            )
            .setContentText(
                if (event.kind == AlertEventKind.FULL_CHARGE) {
                    applicationContext.getString(R.string.full_charge_reached_body)
                } else {
                    applicationContext.getString(
                        R.string.phone_battery_alert_body,
                        event.thresholdPercent,
                    )
                }
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        notificationManager.notify(notificationId, notification)
        WearNotificationPostResult.POSTED
    } catch (_: SecurityException) {
        WearNotificationPostResult.PERMISSION_DENIED
    } catch (_: RuntimeException) {
        WearNotificationPostResult.FAILED
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            BATTERY_ALERT_CHANNEL_ID,
            applicationContext.getString(R.string.battery_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = applicationContext.getString(R.string.battery_alert_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun notificationsAreAllowed(): Boolean {
        if (!notificationManager.areNotificationsEnabled()) return false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return notificationManager
            .getNotificationChannel(BATTERY_ALERT_CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
    }
}

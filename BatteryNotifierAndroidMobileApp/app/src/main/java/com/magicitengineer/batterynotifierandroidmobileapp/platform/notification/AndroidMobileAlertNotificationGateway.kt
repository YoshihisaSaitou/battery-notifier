package com.magicitengineer.batterynotifierandroidmobileapp.platform.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.magicitengineer.batterynotifierandroidmobileapp.MainActivity
import com.magicitengineer.batterynotifierandroidmobileapp.R
import com.magicitengineer.batterynotifierandroidmobileapp.application.notification.MobileNotificationGateway
import com.magicitengineer.batterynotifierandroidmobileapp.application.notification.MobileNotificationPostResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.notification.StableMobileNotificationId
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent

class AndroidMobileAlertNotificationFactory(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.battery_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = applicationContext.getString(
                    R.string.battery_alert_channel_description
                )
            }
        )
    }

    fun create(event: ThresholdReachedEvent): Notification {
        val notificationId = StableMobileNotificationId.fromEventId(event.eventId)
        val openApp = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_monitoring)
            .setContentTitle(
                applicationContext.getString(
                    R.string.phone_battery_alert_title,
                    event.levelPercent,
                )
            )
            .setContentText(
                applicationContext.getString(
                    R.string.phone_battery_alert_body,
                    event.thresholdPercent,
                )
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun notificationsAreAllowed(): Boolean =
        notificationManager.areNotificationsEnabled() &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED &&
            notificationManager.getNotificationChannel(CHANNEL_ID)?.importance !=
            NotificationManager.IMPORTANCE_NONE

    companion object {
        const val CHANNEL_ID = "battery_alerts"
    }
}

class AndroidMobileAlertNotificationGateway(
    context: Context,
) : MobileNotificationGateway {
    private val notificationFactory = AndroidMobileAlertNotificationFactory(context)
    private val notificationManager = context.applicationContext
        .getSystemService(NotificationManager::class.java)

    override suspend fun post(
        event: ThresholdReachedEvent,
    ): MobileNotificationPostResult {
        notificationFactory.ensureChannel()
        if (!notificationFactory.notificationsAreAllowed()) {
            return MobileNotificationPostResult.PERMISSION_DENIED
        }
        return postWhenAllowed(event)
    }

    @SuppressLint("NotificationPermission")
    private fun postWhenAllowed(
        event: ThresholdReachedEvent,
    ): MobileNotificationPostResult = try {
        notificationManager.notify(
            StableMobileNotificationId.fromEventId(event.eventId),
            notificationFactory.create(event),
        )
        MobileNotificationPostResult.POSTED
    } catch (_: SecurityException) {
        MobileNotificationPostResult.PERMISSION_DENIED
    } catch (_: RuntimeException) {
        MobileNotificationPostResult.FAILED
    }
}

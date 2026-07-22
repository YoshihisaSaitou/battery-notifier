package com.magicitengineer.batterynotifierandroidmobileapp.platform.notification

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.magicitengineer.batterynotifierandroidmobileapp.R
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMobileAlertNotificationFactoryInstrumentedTest {
    @Test
    fun alertIsLocalizedLocalOnlyAndOpensTheExplicitMobileActivity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val factory = AndroidMobileAlertNotificationFactory(context)
        val event = ThresholdReachedEvent(
            eventId = "550e8400-e29b-41d4-a716-446655440001",
            levelPercent = 20,
            thresholdPercent = 20,
            occurredAtEpochMillis = 1_000L,
            expiresAtEpochMillis = 301_000L,
            sequence = 2L,
        )

        factory.ensureChannel()
        val notification = factory.create(event)

        assertEquals(AndroidMobileAlertNotificationFactory.CHANNEL_ID, notification.channelId)
        assertTrue(notification.extras.getBoolean("android.localOnly"))
        assertEquals(
            context.getString(R.string.phone_battery_alert_title, 20),
            notification.extras.getString("android.title"),
        )
        assertEquals(
            context.getString(R.string.phone_battery_alert_body, 20),
            notification.extras.getString("android.text"),
        )
        assertNotNull(notification.contentIntent)
        assertNotNull(
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(AndroidMobileAlertNotificationFactory.CHANNEL_ID),
        )
    }
}

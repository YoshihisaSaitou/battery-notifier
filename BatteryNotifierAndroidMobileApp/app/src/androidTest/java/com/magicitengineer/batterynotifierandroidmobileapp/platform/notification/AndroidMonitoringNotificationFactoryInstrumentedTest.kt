package com.magicitengineer.batterynotifierandroidmobileapp.platform.notification

import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.magicitengineer.batterynotifierandroidmobileapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMonitoringNotificationFactoryInstrumentedTest {
    @Test
    fun notificationIsOngoingLocalOnlyAndProvidesExplicitStopAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val factory = AndroidMonitoringNotificationFactory(context)

        factory.ensureChannel()
        val notification = factory.create(thresholdPercent = 35)

        assertEquals(AndroidMonitoringNotificationFactory.CHANNEL_ID, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.extras.getBoolean("android.localOnly"))
        assertNotNull(notification.contentIntent)
        assertEquals(1, notification.actions.size)
        assertEquals(context.getString(R.string.monitoring_stop_action), notification.actions[0].title)
        assertNotNull(
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(AndroidMonitoringNotificationFactory.CHANNEL_ID),
        )
    }
}

package com.magicitengineer.batterynotifierandroidmobileapp.platform.battery

import android.content.Intent
import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBatteryChangedIntentMapperInstrumentedTest {
    private val mapper = AndroidBatteryChangedIntentMapper { 2_000L }

    @Test
    fun unrelatedActionIsIgnored() {
        assertNull(mapper.map(Intent(Intent.ACTION_POWER_CONNECTED)))
    }

    @Test
    fun missingScaleIsInvalid() {
        val intent = batteryChangedIntent(level = 1, scale = null)

        assertSame(BatteryReadResult.Invalid, mapper.map(intent))
    }

    @Test
    fun levelUsesIntegerFloorAndCallbackTimestamp() {
        val result = mapper.map(batteryChangedIntent(level = 1, scale = 3))
            as BatteryReadResult.Available

        assertEquals(33, result.reading.levelPercent)
        assertEquals(false, result.reading.isCharging)
        assertEquals(2_000L, result.reading.capturedAtEpochMillis)
    }

    @Test
    fun chargingStatusOrPluggedSourceMarksReadingCharging() {
        val chargingByStatus = mapper.map(
            batteryChangedIntent(
                level = 50,
                scale = 100,
                status = BatteryManager.BATTERY_STATUS_CHARGING,
            ),
        ) as BatteryReadResult.Available
        val chargingByUsb = mapper.map(
            batteryChangedIntent(
                level = 50,
                scale = 100,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = BatteryManager.BATTERY_PLUGGED_USB,
            ),
        ) as BatteryReadResult.Available

        assertEquals(true, chargingByStatus.reading.isCharging)
        assertEquals(true, chargingByUsb.reading.isCharging)
    }

    private fun batteryChangedIntent(
        level: Int,
        scale: Int?,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING,
        plugged: Int = 0,
    ): Intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
        putExtra(BatteryManager.EXTRA_LEVEL, level)
        scale?.let { putExtra(BatteryManager.EXTRA_SCALE, it) }
        putExtra(BatteryManager.EXTRA_STATUS, status)
        putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
    }
}

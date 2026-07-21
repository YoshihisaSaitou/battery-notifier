package com.magicitengineer.batterynotifierandroidmobileapp.platform.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadingNormalizer
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadingSource
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.EpochMillisClock

class AndroidCurrentBatteryReadingSource(
    context: Context,
    private val clock: EpochMillisClock,
) : BatteryReadingSource {
    private val applicationContext = context.applicationContext

    override fun readCurrent(): BatteryReadResult {
        val intent = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return BatteryReadResult.Unavailable
        val status = intent.optionalIntExtra(BatteryManager.EXTRA_STATUS)
        val plugged = intent.optionalIntExtra(BatteryManager.EXTRA_PLUGGED)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            (plugged != null && plugged != 0)

        return BatteryReadingNormalizer.normalize(
            level = intent.optionalIntExtra(BatteryManager.EXTRA_LEVEL),
            scale = intent.optionalIntExtra(BatteryManager.EXTRA_SCALE),
            isCharging = isCharging,
            capturedAtEpochMillis = clock.now(),
        )
    }

    private fun Intent.optionalIntExtra(name: String): Int? =
        if (hasExtra(name)) getIntExtra(name, 0) else null
}

package com.magicitengineer.batterynotifierandroidmobileapp.platform.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadingSource
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.EpochMillisClock

class AndroidCurrentBatteryReadingSource(
    context: Context,
    clock: EpochMillisClock,
) : BatteryReadingSource {
    private val applicationContext = context.applicationContext
    private val mapper = AndroidBatteryChangedIntentMapper(clock)

    override fun readCurrent(): BatteryReadResult {
        val intent = applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return BatteryReadResult.Unavailable
        return mapper.map(intent) ?: BatteryReadResult.Unavailable
    }
}

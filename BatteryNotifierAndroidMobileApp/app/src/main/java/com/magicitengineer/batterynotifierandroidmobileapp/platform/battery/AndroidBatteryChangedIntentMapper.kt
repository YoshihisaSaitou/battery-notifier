package com.magicitengineer.batterynotifierandroidmobileapp.platform.battery

import android.content.Intent
import android.os.BatteryManager
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileBatteryChangeRunner
import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinationResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadingNormalizer
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.EpochMillisClock

class AndroidBatteryChangedIntentMapper(
    private val clock: EpochMillisClock,
) {
    fun map(intent: Intent): BatteryReadResult? {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return null

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

class AndroidBatteryChangedCallback(
    private val mapper: AndroidBatteryChangedIntentMapper,
    private val runner: MobileBatteryChangeRunner,
) {
    suspend fun onReceive(intent: Intent): MobileSyncCoordinationResult? =
        mapper.map(intent)?.let { runner.processBatteryChange(it) }
}

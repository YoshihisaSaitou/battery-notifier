package com.magicitengineer.batterynotifierandroidmobileapp.domain.battery

sealed interface BatteryReadResult {
    data class Available(val reading: BatteryReading) : BatteryReadResult

    data object Invalid : BatteryReadResult

    data object Unavailable : BatteryReadResult
}

fun interface BatteryReadingSource {
    fun readCurrent(): BatteryReadResult
}

object BatteryReadingNormalizer {
    fun normalize(
        level: Int?,
        scale: Int?,
        isCharging: Boolean,
        capturedAtEpochMillis: Long,
    ): BatteryReadResult {
        if (
            level == null ||
            scale == null ||
            level < 0 ||
            scale <= 0 ||
            capturedAtEpochMillis <= 0
        ) {
            return BatteryReadResult.Invalid
        }

        val levelPercent = (
            level.toLong() * 100L / scale.toLong()
        ).coerceIn(0L, 100L).toInt()
        return BatteryReadResult.Available(
            BatteryReading(
                levelPercent = levelPercent,
                isCharging = isCharging,
                capturedAtEpochMillis = capturedAtEpochMillis,
            )
        )
    }
}

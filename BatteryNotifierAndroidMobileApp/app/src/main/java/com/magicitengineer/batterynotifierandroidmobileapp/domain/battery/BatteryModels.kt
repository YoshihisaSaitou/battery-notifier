package com.magicitengineer.batterynotifierandroidmobileapp.domain.battery

data class BatteryReading(
    val levelPercent: Int,
    val isCharging: Boolean,
    val capturedAtEpochMillis: Long,
) {
    init {
        require(levelPercent in 0..100) { "levelPercent must be in 0..100" }
        require(capturedAtEpochMillis > 0) { "capturedAtEpochMillis must be positive" }
    }
}

data class BatterySnapshot(
    val levelPercent: Int,
    val isCharging: Boolean,
    val capturedAtEpochMillis: Long,
    val sequence: Long,
) {
    init {
        require(levelPercent in 0..100) { "levelPercent must be in 0..100" }
        require(capturedAtEpochMillis > 0) { "capturedAtEpochMillis must be positive" }
        require(sequence >= 1) { "sequence must be at least 1" }
    }
}

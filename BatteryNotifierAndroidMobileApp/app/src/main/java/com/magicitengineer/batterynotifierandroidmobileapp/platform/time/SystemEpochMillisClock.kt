package com.magicitengineer.batterynotifierandroidmobileapp.platform.time

import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.EpochMillisClock

object SystemEpochMillisClock : EpochMillisClock {
    override fun now(): Long = System.currentTimeMillis()
}

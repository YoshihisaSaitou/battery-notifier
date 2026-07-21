package com.magicitengineer.batterynotifierandroidwearapp.platform.time

import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.EpochMillisClock

object SystemEpochMillisClock : EpochMillisClock {
    override fun now(): Long = System.currentTimeMillis()
}

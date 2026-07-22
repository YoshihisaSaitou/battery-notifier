package com.magicitengineer.batterynotifierandroidwearapp.platform.presentation

import java.util.concurrent.atomic.AtomicBoolean

class WearSurfaceRefreshCoalescer {
    private val scheduled = AtomicBoolean(false)

    fun trySchedule(): Boolean = scheduled.compareAndSet(false, true)

    fun markCompleted() {
        scheduled.set(false)
    }
}

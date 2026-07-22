package com.magicitengineer.batterynotifierandroidwearapp.platform.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearSurfaceRefreshCoalescerTest {
    @Test
    fun `burst schedules one trailing refresh and allows the next window`() {
        val coalescer = WearSurfaceRefreshCoalescer()

        val scheduled = List(30) { coalescer.trySchedule() }

        assertEquals(1, scheduled.count { it })
        coalescer.markCompleted()
        assertTrue(coalescer.trySchedule())
    }
}

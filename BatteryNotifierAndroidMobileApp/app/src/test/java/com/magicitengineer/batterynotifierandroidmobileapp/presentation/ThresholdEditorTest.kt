package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ThresholdEditorTest {
    @Test
    fun adjustmentUsesOnePercentStepsAndStopsAtBothBoundaries() {
        assertEquals(5, adjustThreshold(current = 5, delta = -1))
        assertEquals(6, adjustThreshold(current = 5, delta = 1))
        assertEquals(19, adjustThreshold(current = 20, delta = -1))
        assertEquals(21, adjustThreshold(current = 20, delta = 1))
        assertEquals(99, adjustThreshold(current = 100, delta = -1))
        assertEquals(100, adjustThreshold(current = 100, delta = 1))
    }
}

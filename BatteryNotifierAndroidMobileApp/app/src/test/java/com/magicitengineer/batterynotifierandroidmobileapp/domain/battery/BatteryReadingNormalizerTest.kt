package com.magicitengineer.batterynotifierandroidmobileapp.domain.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryReadingNormalizerTest {
    @Test
    fun oneOfThreeUsesFloorPercentage() {
        val result = BatteryReadingNormalizer.normalize(
            level = 1,
            scale = 3,
            isCharging = false,
            capturedAtEpochMillis = 1_000L,
        )

        val reading = (result as BatteryReadResult.Available).reading
        assertEquals(33, reading.levelPercent)
        assertEquals(1_000L, reading.capturedAtEpochMillis)
        assertTrue(!reading.isCharging)
    }

    @Test
    fun percentageIsClampedToSupportedRange() {
        val result = BatteryReadingNormalizer.normalize(
            level = 150,
            scale = 100,
            isCharging = true,
            capturedAtEpochMillis = 1_000L,
        )

        val reading = (result as BatteryReadResult.Available).reading
        assertEquals(100, reading.levelPercent)
        assertTrue(reading.isCharging)
    }

    @Test
    fun missingOrInvalidRequiredValuesAreRejected() {
        val results = listOf(
            BatteryReadingNormalizer.normalize(null, 100, false, 1_000L),
            BatteryReadingNormalizer.normalize(20, null, false, 1_000L),
            BatteryReadingNormalizer.normalize(-1, 100, false, 1_000L),
            BatteryReadingNormalizer.normalize(20, 0, false, 1_000L),
            BatteryReadingNormalizer.normalize(20, 100, false, 0L),
        )

        results.forEach { assertSame(BatteryReadResult.Invalid, it) }
    }
}

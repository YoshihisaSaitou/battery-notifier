package com.magicitengineer.batterynotifierandroidmobileapp.domain.alert

import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullChargeEvaluatorTest {
    private val rule = AlertRule(
        monitoringEnabled = true,
        fullChargeNotificationEnabled = true,
    )

    @Test
    fun `99 to 100 while charging emits one full-charge event`() {
        val result = FullChargeEvaluator.evaluate(
            rule = rule,
            armed = true,
            previousSnapshot = snapshot(99, charging = true, sequence = 1),
            snapshot = snapshot(100, charging = true, sequence = 2),
            candidateEventId = UUID.randomUUID().toString(),
        )

        assertFalse(result.armed)
        assertEquals(AlertEventKind.FULL_CHARGE, result.event?.kind)
        assertEquals(100, result.event?.levelPercent)
    }

    @Test
    fun `same charging session does not rearm after 100 to 99 to 100`() {
        val at99 = FullChargeEvaluator.evaluate(
            rule,
            armed = false,
            previousSnapshot = snapshot(100, true, 2),
            snapshot = snapshot(99, true, 3),
            candidateEventId = UUID.randomUUID().toString(),
        )
        val at100 = FullChargeEvaluator.evaluate(
            rule,
            armed = at99.armed,
            previousSnapshot = snapshot(99, true, 3),
            snapshot = snapshot(100, true, 4),
            candidateEventId = UUID.randomUUID().toString(),
        )

        assertFalse(at100.armed)
        assertNull(at100.event)
    }

    @Test
    fun `unplug then new charging session below 100 rearms`() {
        val unplugged = FullChargeEvaluator.evaluate(
            rule,
            armed = false,
            previousSnapshot = snapshot(100, true, 2),
            snapshot = snapshot(98, false, 3),
            candidateEventId = UUID.randomUUID().toString(),
        )
        val replugged = FullChargeEvaluator.evaluate(
            rule,
            armed = unplugged.armed,
            previousSnapshot = snapshot(98, false, 3),
            snapshot = snapshot(98, true, 4),
            candidateEventId = UUID.randomUUID().toString(),
        )

        assertTrue(replugged.armed)
        assertNull(replugged.event)
    }

    @Test
    fun `first observation at 100 and disabled setting do not notify`() {
        val initial = FullChargeEvaluator.evaluate(
            rule,
            armed = false,
            previousSnapshot = null,
            snapshot = snapshot(100, true, 1),
            candidateEventId = UUID.randomUUID().toString(),
        )
        val disabled = FullChargeEvaluator.evaluate(
            rule.copy(fullChargeNotificationEnabled = false),
            armed = true,
            previousSnapshot = snapshot(99, true, 1),
            snapshot = snapshot(100, true, 2),
            candidateEventId = UUID.randomUUID().toString(),
        )

        assertFalse(initial.armed)
        assertNull(initial.event)
        assertFalse(disabled.armed)
        assertNull(disabled.event)
    }

    private fun snapshot(level: Int, charging: Boolean, sequence: Long) = BatterySnapshot(
        levelPercent = level,
        isCharging = charging,
        capturedAtEpochMillis = sequence * 1_000,
        sequence = sequence,
    )
}

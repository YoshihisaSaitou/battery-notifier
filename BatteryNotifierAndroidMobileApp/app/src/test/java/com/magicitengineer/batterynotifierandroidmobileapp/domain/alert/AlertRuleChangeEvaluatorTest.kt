package com.magicitengineer.batterynotifierandroidmobileapp.domain.alert

import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRuleChangeEvaluatorTest {
    @Test
    fun noSnapshotPreservesAlertState() {
        val state = AlertState(armed = false, previousLevelPercent = 42)

        assertSame(
            state,
            AlertRuleChangeEvaluator.reevaluateWithoutEvent(AlertRule(), state, null),
        )
    }

    @Test
    fun currentAtOrBelowNewThresholdDisarmsWithoutEvent() {
        val result = AlertRuleChangeEvaluator.reevaluateWithoutEvent(
            rule = AlertRule(thresholdPercent = 20, monitoringEnabled = true),
            state = AlertState(armed = true, previousLevelPercent = 18),
            snapshot = snapshot(18),
        )

        assertFalse(result.armed)
        assertEquals(18, result.previousLevelPercent)
    }

    @Test
    fun currentAtRearmLevelArmsAfterRuleChange() {
        val result = AlertRuleChangeEvaluator.reevaluateWithoutEvent(
            rule = AlertRule(thresholdPercent = 20, monitoringEnabled = true),
            state = AlertState(armed = false, previousLevelPercent = 20),
            snapshot = snapshot(22),
        )

        assertTrue(result.armed)
        assertEquals(22, result.previousLevelPercent)
    }

    @Test
    fun hysteresisBandPreservesExistingArmState() {
        val state = AlertState(armed = false, previousLevelPercent = 20)

        val result = AlertRuleChangeEvaluator.reevaluateWithoutEvent(
            rule = AlertRule(thresholdPercent = 20, monitoringEnabled = true),
            state = state,
            snapshot = snapshot(21),
        )

        assertFalse(result.armed)
        assertEquals(21, result.previousLevelPercent)
    }

    @Test
    fun threshold100AtFullArmsWithoutCreatingAnEvent() {
        val result = AlertRuleChangeEvaluator.reevaluateWithoutEvent(
            rule = AlertRule(thresholdPercent = 100, monitoringEnabled = true),
            state = AlertState(armed = false, previousLevelPercent = 99),
            snapshot = snapshot(100),
        )

        assertTrue(result.armed)
        assertEquals(100, result.previousLevelPercent)
    }

    private fun snapshot(level: Int) = BatterySnapshot(
        levelPercent = level,
        isCharging = false,
        capturedAtEpochMillis = 1_000L,
        sequence = 1L,
    )
}

package com.magicitengineer.batterynotifierandroidmobileapp.domain.alert

import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdEvaluatorTest {
    private val enabledRule = AlertRule(monitoringEnabled = true)

    @Test
    fun downwardCrossingCreatesOneEventAndDisarms() {
        val result = evaluate(
            state = AlertState(armed = true, previousLevelPercent = 21),
            level = 20,
            sequence = 2L,
        )

        val event = requireNotNull(result.event)
        assertEquals(20, event.levelPercent)
        assertEquals(20, event.thresholdPercent)
        assertEquals(2L, event.sequence)
        assertEquals(300_000L, event.expiresAtEpochMillis - event.occurredAtEpochMillis)
        assertFalse(result.state.armed)
        assertEquals(EVENT_ID, result.state.lastEventId)
    }

    @Test
    fun firstObservationBelowThresholdDoesNotNotifyAndStartsDisarmed() {
        val result = evaluate(
            state = AlertState(),
            level = 18,
            sequence = 1L,
        )

        assertNull(result.event)
        assertFalse(result.state.armed)
        assertEquals(18, result.state.previousLevelPercent)
    }

    @Test
    fun reachingHysteresisRearmsThenNextCrossingCreatesNewEvent() {
        val disarmed = AlertState(armed = false, previousLevelPercent = 20)
        val rearmed = evaluate(disarmed, level = 22, sequence = 2L)
        val aboveThreshold = evaluate(rearmed.state, level = 21, sequence = 3L)
        val crossed = evaluate(aboveThreshold.state, level = 20, sequence = 4L)

        assertTrue(rearmed.state.armed)
        assertNull(rearmed.event)
        assertNull(aboveThreshold.event)
        assertNotNull(crossed.event)
        assertFalse(crossed.state.armed)
    }

    @Test
    fun chargingCrossingDoesNotCreateEvent() {
        val result = evaluate(
            state = AlertState(armed = true, previousLevelPercent = 21),
            level = 20,
            sequence = 2L,
            charging = true,
        )

        assertNull(result.event)
        assertTrue(result.state.armed)
        assertEquals(20, result.state.previousLevelPercent)
    }

    @Test
    fun monitoringOffNeverCreatesEvent() {
        val result = ThresholdEvaluator.evaluate(
            rule = enabledRule.copy(monitoringEnabled = false),
            state = AlertState(armed = true, previousLevelPercent = 21),
            snapshot = snapshot(level = 20, sequence = 2L),
            candidateEventId = EVENT_ID,
        )

        assertNull(result.event)
        assertEquals(20, result.state.previousLevelPercent)
    }

    private fun evaluate(
        state: AlertState,
        level: Int,
        sequence: Long,
        charging: Boolean = false,
    ) = ThresholdEvaluator.evaluate(
        rule = enabledRule,
        state = state,
        snapshot = snapshot(level, sequence, charging),
        candidateEventId = EVENT_ID,
    )

    private fun snapshot(level: Int, sequence: Long, charging: Boolean = false) = BatterySnapshot(
        levelPercent = level,
        isCharging = charging,
        capturedAtEpochMillis = 1_784_516_400_000L + sequence,
        sequence = sequence,
    )

    private companion object {
        const val EVENT_ID = "550e8400-e29b-41d4-a716-446655440000"
    }
}

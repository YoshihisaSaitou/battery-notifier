package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertEventKind
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.PhoneStateSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileDataLayerPayloadMapperTest {
    @Test
    fun phoneStateUsesTheExactV1PathKeysTypesAndValues() {
        val payload = MobileDataLayerPayloadMapper.phoneState(
            PhoneStateSync(
                snapshot = BatterySnapshot(
                    levelPercent = 68,
                    isCharging = true,
                    capturedAtEpochMillis = 1_784_516_400_000L,
                    sequence = 42L,
                ),
                thresholdPercent = 20,
                monitoringEnabled = true,
                sentAtEpochMillis = 1_784_516_400_500L,
            )
        )

        assertEquals(BatteryDataLayerContractV1.PHONE_STATE_PATH, payload.path)
        assertTrue(payload.urgent)
        assertEquals(
            mapOf(
                "schemaVersion" to DataLayerValue.IntValue(1),
                "sequence" to DataLayerValue.LongValue(42L),
                "levelPercent" to DataLayerValue.IntValue(68),
                "isCharging" to DataLayerValue.BooleanValue(true),
                "capturedAtEpochMillis" to
                    DataLayerValue.LongValue(1_784_516_400_000L),
                "thresholdPercent" to DataLayerValue.IntValue(20),
                "monitoringEnabled" to DataLayerValue.BooleanValue(true),
                "sentAtEpochMillis" to DataLayerValue.LongValue(1_784_516_400_500L),
                "fullChargeNotificationEnabled" to DataLayerValue.BooleanValue(false),
            ),
            payload.values,
        )
    }

    @Test
    fun fullChargeEventUsesTheDedicatedFixedPath() {
        val event = ThresholdReachedEvent(
            eventId = EVENT_ID,
            levelPercent = 100,
            thresholdPercent = 100,
            occurredAtEpochMillis = 1_784_516_400_000L,
            expiresAtEpochMillis = 1_784_516_700_000L,
            sequence = 44L,
            kind = AlertEventKind.FULL_CHARGE,
        )

        val payload = MobileDataLayerPayloadMapper.thresholdEvent(event)

        assertEquals(BatteryDataLayerContractV1.FULL_CHARGE_EVENT_PATH, payload.path)
    }

    @Test
    fun thresholdEventUsesTheExactV1PathKeysTypesAndValues() {
        val event = ThresholdReachedEvent(
            eventId = EVENT_ID,
            levelPercent = 20,
            thresholdPercent = 20,
            occurredAtEpochMillis = 1_784_516_400_000L,
            expiresAtEpochMillis = 1_784_516_700_000L,
            sequence = 43L,
        )

        val payload = MobileDataLayerPayloadMapper.thresholdEvent(event)

        assertEquals(BatteryDataLayerContractV1.THRESHOLD_EVENT_PATH, payload.path)
        assertTrue(payload.urgent)
        assertEquals(
            mapOf(
                "schemaVersion" to DataLayerValue.IntValue(1),
                "eventId" to DataLayerValue.StringValue(EVENT_ID),
                "sequence" to DataLayerValue.LongValue(43L),
                "levelPercent" to DataLayerValue.IntValue(20),
                "thresholdPercent" to DataLayerValue.IntValue(20),
                "occurredAtEpochMillis" to
                    DataLayerValue.LongValue(1_784_516_400_000L),
                "expiresAtEpochMillis" to
                    DataLayerValue.LongValue(1_784_516_700_000L),
            ),
            payload.values,
        )
    }

    @Test
    fun thresholdEventRejectsANonUuidEventIdBeforeTransport() {
        val invalid = ThresholdReachedEvent(
            eventId = "not-a-uuid",
            levelPercent = 20,
            thresholdPercent = 20,
            occurredAtEpochMillis = 1_000L,
            expiresAtEpochMillis = 301_000L,
            sequence = 1L,
        )

        assertThrows(IllegalArgumentException::class.java) {
            MobileDataLayerPayloadMapper.thresholdEvent(invalid)
        }
    }

    private companion object {
        const val EVENT_ID = "550e8400-e29b-41d4-a716-446655440001"
    }
}

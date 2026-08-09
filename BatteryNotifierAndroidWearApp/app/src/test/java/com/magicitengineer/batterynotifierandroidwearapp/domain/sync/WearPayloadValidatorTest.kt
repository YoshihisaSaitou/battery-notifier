package com.magicitengineer.batterynotifierandroidwearapp.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPayloadValidatorTest {
    @Test
    fun fullChargeSettingCapabilityIsVersionedAndDistinctFromThresholdWriter() {
        assertEquals(
            "battery_notifier_full_charge_setting_writer_v1",
            WearDataLayerContract.MOBILE_FULL_CHARGE_SETTING_WRITER_CAPABILITY,
        )
        assertTrue(
            WearDataLayerContract.MOBILE_FULL_CHARGE_SETTING_WRITER_CAPABILITY !=
                WearDataLayerContract.MOBILE_THRESHOLD_WRITER_CAPABILITY
        )
    }

    @Test
    fun validPhoneStateMapsEveryRequiredField() {
        val result = WearPayloadValidator.validate(
            path = WearDataLayerContract.PHONE_STATE_PATH,
            values = validStateValues(),
            receivedAtEpochMillis = RECEIVED_AT,
        )

        val state = (result as PayloadValidationResult.ValidState).state
        assertEquals(42L, state.sequence)
        assertEquals(68, state.levelPercent)
        assertTrue(state.isCharging)
        assertEquals(20, state.thresholdPercent)
        assertTrue(state.monitoringEnabled)
        assertTrue(state.fullChargeNotificationEnabled)
    }

    @Test
    fun missingOrWrongTypeRejectsTheWholeState() {
        val missing = validStateValues().toMutableMap().apply {
            remove(WearDataLayerContract.KEY_LEVEL_PERCENT)
        }
        val wrongType = validStateValues().toMutableMap().apply {
            this[WearDataLayerContract.KEY_SEQUENCE] = 42
        }
        val wrongOptionalType = validStateValues().toMutableMap().apply {
            this[WearDataLayerContract.KEY_FULL_CHARGE_NOTIFICATION_ENABLED] = "true"
        }

        listOf(missing, wrongType, wrongOptionalType).forEach { values ->
            val result = WearPayloadValidator.validate(
                WearDataLayerContract.PHONE_STATE_PATH,
                values,
                RECEIVED_AT,
            )
            assertEquals(
                ReceiveErrorClassification.MISSING_OR_WRONG_TYPE,
                (result as PayloadValidationResult.Invalid).classification,
            )
        }
    }

    @Test
    fun outOfRangeValueRejectsTheWholeState() {
        val values = validStateValues().toMutableMap().apply {
            this[WearDataLayerContract.KEY_LEVEL_PERCENT] = 101
        }

        val result = WearPayloadValidator.validate(
            WearDataLayerContract.PHONE_STATE_PATH,
            values,
            RECEIVED_AT,
        )

        assertEquals(
            ReceiveErrorClassification.OUT_OF_RANGE,
            (result as PayloadValidationResult.Invalid).classification,
        )
    }

    @Test
    fun capturedTimeAtFutureBoundaryIsAcceptedButOneMillisecondBeyondIsRejected() {
        val atBoundary = validStateValues().toMutableMap().apply {
            this[WearDataLayerContract.KEY_CAPTURED_AT] = RECEIVED_AT + MAX_FUTURE_SKEW_MILLIS
        }
        val beyondBoundary = atBoundary.toMutableMap().apply {
            this[WearDataLayerContract.KEY_CAPTURED_AT] =
                RECEIVED_AT + MAX_FUTURE_SKEW_MILLIS + 1
        }

        assertTrue(
            WearPayloadValidator.validate(
                WearDataLayerContract.PHONE_STATE_PATH,
                atBoundary,
                RECEIVED_AT,
            ) is PayloadValidationResult.ValidState
        )
        assertEquals(
            ReceiveErrorClassification.INVALID_TIME,
            (WearPayloadValidator.validate(
                WearDataLayerContract.PHONE_STATE_PATH,
                beyondBoundary,
                RECEIVED_AT,
            ) as PayloadValidationResult.Invalid).classification,
        )
    }

    @Test
    fun futureSchemaIsClassifiedWithoutReadingRemainingFields() {
        val result = WearPayloadValidator.validate(
            WearDataLayerContract.PHONE_STATE_PATH,
            mapOf(WearDataLayerContract.KEY_SCHEMA_VERSION to 2),
            RECEIVED_AT,
        )

        assertEquals(2, (result as PayloadValidationResult.UnsupportedSchema).receivedVersion)
    }

    @Test
    fun validEventAndInvalidEventBoundariesAreClassified() {
        val valid = WearPayloadValidator.validate(
            WearDataLayerContract.THRESHOLD_EVENT_PATH,
            validEventValues(),
            RECEIVED_AT,
        )
        val invalidId = validEventValues().toMutableMap().apply {
            this[WearDataLayerContract.KEY_EVENT_ID] = "not-a-uuid"
        }
        val invalidExpiry = validEventValues().toMutableMap().apply {
            this[WearDataLayerContract.KEY_EXPIRES_AT] =
                EVENT_OCCURRED_AT + MAX_EVENT_EXPIRY_MILLIS + 1
        }

        assertEquals(EVENT_ID, (valid as PayloadValidationResult.ValidEvent).event.eventId)
        assertEquals(
            ReceiveErrorClassification.INVALID_EVENT_ID,
            (WearPayloadValidator.validate(
                WearDataLayerContract.THRESHOLD_EVENT_PATH,
                invalidId,
                RECEIVED_AT,
            ) as PayloadValidationResult.Invalid).classification,
        )
        assertEquals(
            ReceiveErrorClassification.INVALID_TIME,
            (WearPayloadValidator.validate(
                WearDataLayerContract.THRESHOLD_EVENT_PATH,
                invalidExpiry,
                RECEIVED_AT,
            ) as PayloadValidationResult.Invalid).classification,
        )
    }

    @Test
    fun fullChargeEventUsesDedicatedPathAndKind() {
        val values = validEventValues().toMutableMap().apply {
            this[WearDataLayerContract.KEY_LEVEL_PERCENT] = 100
            this[WearDataLayerContract.KEY_THRESHOLD_PERCENT] = 100
        }

        val result = WearPayloadValidator.validate(
            WearDataLayerContract.FULL_CHARGE_EVENT_PATH,
            values,
            RECEIVED_AT,
        ) as PayloadValidationResult.ValidEvent

        assertEquals(AlertEventKind.FULL_CHARGE, result.event.kind)
    }

    private fun validStateValues(): Map<String, Any?> = mapOf(
        WearDataLayerContract.KEY_SCHEMA_VERSION to 1,
        WearDataLayerContract.KEY_SEQUENCE to 42L,
        WearDataLayerContract.KEY_LEVEL_PERCENT to 68,
        WearDataLayerContract.KEY_IS_CHARGING to true,
        WearDataLayerContract.KEY_CAPTURED_AT to 900_000L,
        WearDataLayerContract.KEY_THRESHOLD_PERCENT to 20,
        WearDataLayerContract.KEY_MONITORING_ENABLED to true,
        WearDataLayerContract.KEY_SENT_AT to 950_000L,
        WearDataLayerContract.KEY_FULL_CHARGE_NOTIFICATION_ENABLED to true,
    )

    private fun validEventValues(): Map<String, Any?> = mapOf(
        WearDataLayerContract.KEY_SCHEMA_VERSION to 1,
        WearDataLayerContract.KEY_EVENT_ID to EVENT_ID,
        WearDataLayerContract.KEY_SEQUENCE to 42L,
        WearDataLayerContract.KEY_LEVEL_PERCENT to 20,
        WearDataLayerContract.KEY_THRESHOLD_PERCENT to 20,
        WearDataLayerContract.KEY_OCCURRED_AT to EVENT_OCCURRED_AT,
        WearDataLayerContract.KEY_EXPIRES_AT to EVENT_OCCURRED_AT + 300_000L,
    )

    private companion object {
        const val RECEIVED_AT = 1_000_000L
        const val EVENT_OCCURRED_AT = 900_000L
        const val EVENT_ID = "550e8400-e29b-41d4-a716-446655440020"
    }
}

package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.THRESHOLD_CHANGE_SCHEMA_VERSION
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdChangeRequestValidatorTest {
    @Test
    fun completeValidPayloadIsAccepted() {
        val result = ThresholdChangeRequestValidator.validate(validPayload())

        assertTrue(result is ThresholdChangeRequestDecodeResult.Valid)
        val request = (result as ThresholdChangeRequestDecodeResult.Valid).request
        assertEquals(30, request.thresholdPercent)
        assertEquals(20, request.expectedThresholdPercent)
    }

    @Test
    fun missingWrongTypedOrOutOfRangeFieldsRejectTheCompletePayload() {
        val missingExpected = validPayload().minus(
            BatteryDataLayerContractV1.Keys.EXPECTED_THRESHOLD_PERCENT
        )
        val wrongType = validPayload().toMutableMap().apply {
            this[BatteryDataLayerContractV1.Keys.THRESHOLD_PERCENT] = 30L
        }
        val outOfRange = validPayload().toMutableMap().apply {
            this[BatteryDataLayerContractV1.Keys.THRESHOLD_PERCENT] = 4
        }
        val invalidId = validPayload().toMutableMap().apply {
            this[BatteryDataLayerContractV1.Keys.REQUEST_ID] = "not-a-uuid"
        }

        listOf(missingExpected, wrongType, outOfRange, invalidId).forEach {
            assertEquals(
                ThresholdChangeRequestDecodeResult.Invalid,
                ThresholdChangeRequestValidator.validate(it),
            )
        }
    }

    @Test
    fun unsupportedSchemaIsDistinguishedFromMalformedPayload() {
        val payload = validPayload().toMutableMap().apply {
            this[BatteryDataLayerContractV1.Keys.SCHEMA_VERSION] = 2
        }

        assertEquals(
            ThresholdChangeRequestDecodeResult.UnsupportedSchema(2),
            ThresholdChangeRequestValidator.validate(payload),
        )
    }

    @Test
    fun zeroAndNegativeSchemaVersionsAreMalformedRatherThanUnsupported() {
        listOf(0, -1).forEach { schemaVersion ->
            val payload = validPayload().toMutableMap().apply {
                this[BatteryDataLayerContractV1.Keys.SCHEMA_VERSION] = schemaVersion
            }

            assertEquals(
                ThresholdChangeRequestDecodeResult.Invalid,
                ThresholdChangeRequestValidator.validate(payload),
            )
        }
    }

    @Test
    fun listenerBoundaryCountsEachNonPositiveSchemaExactlyOnceWithoutThrowing() =
        runBlocking {
            listOf(0, -1).forEach { schemaVersion ->
                var validCount = 0
                var unsupportedCount = 0
                var invalidCount = 0

                dispatchThresholdChangeRequestMessage(
                    sourceNodeId = "phone-node",
                    payload = byteArrayOf(),
                    decode = {
                        ThresholdChangeRequestValidator.validate(
                            validPayload().toMutableMap().apply {
                                this[BatteryDataLayerContractV1.Keys.SCHEMA_VERSION] =
                                    schemaVersion
                            }
                        )
                    },
                    onValid = { _, _ -> validCount += 1 },
                    onUnsupportedSchema = { unsupportedCount += 1 },
                    onInvalid = { invalidCount += 1 },
                )

                assertEquals(0, validCount)
                assertEquals(0, unsupportedCount)
                assertEquals(1, invalidCount)
            }
        }

    private fun validPayload(): Map<String, Any?> = mapOf(
        BatteryDataLayerContractV1.Keys.SCHEMA_VERSION to
            THRESHOLD_CHANGE_SCHEMA_VERSION,
        BatteryDataLayerContractV1.Keys.REQUEST_ID to REQUEST_ID,
        BatteryDataLayerContractV1.Keys.THRESHOLD_PERCENT to 30,
        BatteryDataLayerContractV1.Keys.EXPECTED_THRESHOLD_PERCENT to 20,
    )

    private companion object {
        const val REQUEST_ID = "550e8400-e29b-41d4-a716-446655440002"
    }
}

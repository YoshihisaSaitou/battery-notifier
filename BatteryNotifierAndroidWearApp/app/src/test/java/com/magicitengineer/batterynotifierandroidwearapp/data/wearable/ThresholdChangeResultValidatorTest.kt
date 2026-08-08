package com.magicitengineer.batterynotifierandroidwearapp.data.wearable

import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.THRESHOLD_CHANGE_SCHEMA_VERSION
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeResult
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeResultCode
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.WearDataLayerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdChangeResultValidatorTest {
    @Test
    fun completeValidPayloadIsAccepted() {
        val result = ThresholdChangeResultValidator.validate(validPayload())

        assertTrue(result is ThresholdChangeResultDecodeResult.Valid)
        val value = (result as ThresholdChangeResultDecodeResult.Valid).result
        assertEquals(ThresholdChangeResultCode.APPLIED, value.resultCode)
        assertEquals(30, value.effectiveThresholdPercent)
        assertEquals(12L, value.phoneStateSequence)
    }

    @Test
    fun invalidResultCodeWrongSequenceTypeAndOutOfRangeThresholdAreRejected() {
        val invalidCode = validPayload().toMutableMap().apply {
            this[WearDataLayerContract.KEY_RESULT_CODE] = "UNKNOWN"
        }
        val wrongSequenceType = validPayload().toMutableMap().apply {
            this[WearDataLayerContract.KEY_PHONE_STATE_SEQUENCE] = 12
        }
        val outOfRange = validPayload().toMutableMap().apply {
            this[WearDataLayerContract.KEY_EFFECTIVE_THRESHOLD_PERCENT] = 101
        }

        listOf(invalidCode, wrongSequenceType, outOfRange).forEach {
            assertEquals(
                ThresholdChangeResultDecodeResult.Invalid,
                ThresholdChangeResultValidator.validate(it),
            )
        }
    }

    @Test
    fun unsupportedSchemaIsDistinguishedFromMalformedPayload() {
        val payload = validPayload().toMutableMap().apply {
            this[WearDataLayerContract.KEY_SCHEMA_VERSION] = 2
        }

        assertEquals(
            ThresholdChangeResultDecodeResult.UnsupportedSchema(2),
            ThresholdChangeResultValidator.validate(payload),
        )
    }

    @Test
    fun zeroAndNegativeSchemaVersionsAreMalformedRatherThanUnsupported() {
        listOf(0, -1).forEach { schemaVersion ->
            val payload = validPayload().toMutableMap().apply {
                this[WearDataLayerContract.KEY_SCHEMA_VERSION] = schemaVersion
            }

            assertEquals(
                ThresholdChangeResultDecodeResult.Invalid,
                ThresholdChangeResultValidator.validate(payload),
            )
        }
    }

    @Test
    fun zeroAndNegativePhoneStateSequencesAreRejected() {
        listOf(0L, -1L).forEach { sequence ->
            val payload = validPayload().toMutableMap().apply {
                this[WearDataLayerContract.KEY_PHONE_STATE_SEQUENCE] = sequence
            }

            assertEquals(
                ThresholdChangeResultDecodeResult.Invalid,
                ThresholdChangeResultValidator.validate(payload),
            )
            assertTrue(
                runCatching {
                    ThresholdChangeResult(
                        requestId = REQUEST_ID,
                        resultCode = ThresholdChangeResultCode.APPLIED,
                        effectiveThresholdPercent = 30,
                        phoneStateSequence = sequence,
                    )
                }.isFailure
            )
        }
    }

    private fun validPayload(): Map<String, Any?> = mapOf(
        WearDataLayerContract.KEY_SCHEMA_VERSION to THRESHOLD_CHANGE_SCHEMA_VERSION,
        WearDataLayerContract.KEY_REQUEST_ID to REQUEST_ID,
        WearDataLayerContract.KEY_RESULT_CODE to
            ThresholdChangeResultCode.APPLIED.persistedValue,
        WearDataLayerContract.KEY_EFFECTIVE_THRESHOLD_PERCENT to 30,
        WearDataLayerContract.KEY_PHONE_STATE_SEQUENCE to 12L,
    )

    private companion object {
        const val REQUEST_ID = "550e8400-e29b-41d4-a716-446655440022"
    }
}

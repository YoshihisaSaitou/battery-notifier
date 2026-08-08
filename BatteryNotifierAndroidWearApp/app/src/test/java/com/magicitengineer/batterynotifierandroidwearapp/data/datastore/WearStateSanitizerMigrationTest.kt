package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.ThresholdEventProto
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.ThresholdChangeRequestProto
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.ThresholdChangeResultProto
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.settings.ThresholdChangeStatus
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WearStateSanitizerMigrationTest {
    @Test
    fun `pre attempt-counter notification dispositions migrate to one attempt`() {
        val legacyDispositions = listOf(
            NotificationDisposition.PENDING,
            NotificationDisposition.POSTED,
            NotificationDisposition.PERMISSION_DENIED,
            NotificationDisposition.RESERVED_FAILED,
        )

        legacyDispositions.forEach { disposition ->
            val sanitized = WearStateSanitizer.sanitize(legacyState(disposition))

            assertEquals(disposition.persistedValue, sanitized.notificationDisposition)
            assertEquals(1, sanitized.notificationPostAttemptCount)
        }
    }

    @Test
    fun `non-positive threshold result sequence is discarded`() {
        val invalid = WearStateSanitizer.defaultValue().toBuilder()
            .setThresholdChangeResult(
                ThresholdChangeResultProto.newBuilder()
                    .setRequestId("550e8400-e29b-41d4-a716-446655440022")
                    .setResultCode("APPLIED")
                    .setEffectiveThresholdPercent(30)
                    .setPhoneStateSequence(0L)
                    .build()
            )
            .build()

        val sanitized = WearStateSanitizer.sanitize(invalid)

        assertFalse(sanitized.hasThresholdChangeResult())
    }

    @Test
    fun `threshold change repair matrix is idempotent and domain compatible`() {
        val activeStatuses = listOf(
            ThresholdChangeStatus.SENDING,
            ThresholdChangeStatus.WAITING_RESULT,
            ThresholdChangeStatus.SEND_FAILED,
            ThresholdChangeStatus.APPLIED_WAITING_STATE,
        )
        val validPending = validPendingRequest()
        val invalidPending = validPending.toBuilder().setSchemaVersion(0).build()
        val validResult = validThresholdResult()
        val invalidResult = validResult.toBuilder().setPhoneStateSequence(0).build()
        val cases = buildList {
            add(
                ThresholdRepairCase(
                    name = "APPLIED_WAITING_STATE without pending and result",
                    input = thresholdState(ThresholdChangeStatus.APPLIED_WAITING_STATE),
                    expectedStatus = ThresholdChangeStatus.IDLE,
                    expectedPending = false,
                    expectedResult = false,
                )
            )
            activeStatuses.forEach { status ->
                add(
                    ThresholdRepairCase(
                        name = "$status without pending",
                        input = thresholdState(status, result = validResult),
                        expectedStatus = ThresholdChangeStatus.IDLE,
                        expectedPending = false,
                        expectedResult = false,
                    )
                )
                add(
                    ThresholdRepairCase(
                        name = "$status with invalid pending",
                        input = thresholdState(
                            status = status,
                            pending = invalidPending,
                            result = validResult,
                        ),
                        expectedStatus = ThresholdChangeStatus.IDLE,
                        expectedPending = false,
                        expectedResult = false,
                    )
                )
                add(
                    ThresholdRepairCase(
                        name = "$status with valid pending and result",
                        input = thresholdState(
                            status = status,
                            pending = validPending,
                            result = validResult,
                        ),
                        expectedStatus = status,
                        expectedPending = true,
                        expectedResult = true,
                    )
                )
            }
            add(
                ThresholdRepairCase(
                    name = "APPLIED_WAITING_STATE without result",
                    input = thresholdState(
                        status = ThresholdChangeStatus.APPLIED_WAITING_STATE,
                        pending = validPending,
                    ),
                    expectedStatus = ThresholdChangeStatus.WAITING_RESULT,
                    expectedPending = true,
                    expectedResult = false,
                )
            )
            add(
                ThresholdRepairCase(
                    name = "APPLIED_WAITING_STATE with invalid result",
                    input = thresholdState(
                        status = ThresholdChangeStatus.APPLIED_WAITING_STATE,
                        pending = validPending,
                        result = invalidResult,
                    ),
                    expectedStatus = ThresholdChangeStatus.WAITING_RESULT,
                    expectedPending = true,
                    expectedResult = false,
                )
            )
        }

        cases.forEach { case ->
            val sanitized = WearStateSanitizer.sanitize(case.input)
            val sanitizedAgain = WearStateSanitizer.sanitize(sanitized)
            val domain = WearStateProtoMapper.toDomain(case.input)

            assertEquals(case.name, case.expectedStatus.persistedValue, sanitized.thresholdChangeStatus)
            assertEquals(case.name, case.expectedPending, sanitized.hasPendingThresholdChangeRequest())
            assertEquals(case.name, case.expectedResult, sanitized.hasThresholdChangeResult())
            assertEquals(case.name, sanitized, sanitizedAgain)
            assertEquals(case.name, case.expectedStatus, domain.thresholdChangeStatus)
            assertEquals(case.name, case.expectedPending, domain.pendingThresholdChangeRequest != null)
            assertEquals(case.name, case.expectedResult, domain.thresholdChangeResult != null)
        }
    }

    private fun thresholdState(
        status: ThresholdChangeStatus,
        pending: ThresholdChangeRequestProto? = null,
        result: ThresholdChangeResultProto? = null,
    ): WearStateProto {
        val builder = WearStateSanitizer.defaultValue().toBuilder()
            .setThresholdChangeStatus(status.persistedValue)
        pending?.let(builder::setPendingThresholdChangeRequest)
        result?.let(builder::setThresholdChangeResult)
        return builder.build()
    }

    private fun validPendingRequest(): ThresholdChangeRequestProto =
        ThresholdChangeRequestProto.newBuilder()
            .setSchemaVersion(1)
            .setRequestId(THRESHOLD_REQUEST_ID)
            .setThresholdPercent(30)
            .setExpectedThresholdPercent(20)
            .build()

    private fun validThresholdResult(): ThresholdChangeResultProto =
        ThresholdChangeResultProto.newBuilder()
            .setRequestId(THRESHOLD_REQUEST_ID)
            .setResultCode("APPLIED")
            .setEffectiveThresholdPercent(30)
            .setPhoneStateSequence(11)
            .build()

    private fun legacyState(disposition: NotificationDisposition): WearStateProto =
        WearStateProto.newBuilder()
            .setStorageSchemaVersion(1)
            .setLastEvent(
                ThresholdEventProto.newBuilder()
                    .setSchemaVersion(1)
                    .setEventId(EVENT_ID)
                    .setSequence(10L)
                    .setLevelPercent(20)
                    .setThresholdPercent(20)
                    .setOccurredAtEpochMillis(1_000_000L)
                    .setExpiresAtEpochMillis(1_300_000L)
                    .build()
            )
            .setLastEventSequence(10L)
            .setLastProcessedEventId(EVENT_ID)
            .setEventProcessedAtEpochMillis(1_100_000L)
            .setNotificationDisposition(disposition.persistedValue)
            // Field 38 did not exist in the legacy schema and decodes as zero.
            .build()

    private companion object {
        const val EVENT_ID = "550e8400-e29b-41d4-a716-446655440020"
        const val THRESHOLD_REQUEST_ID = "550e8400-e29b-41d4-a716-446655440022"
    }

    private data class ThresholdRepairCase(
        val name: String,
        val input: WearStateProto,
        val expectedStatus: ThresholdChangeStatus,
        val expectedPending: Boolean,
        val expectedResult: Boolean,
    )
}

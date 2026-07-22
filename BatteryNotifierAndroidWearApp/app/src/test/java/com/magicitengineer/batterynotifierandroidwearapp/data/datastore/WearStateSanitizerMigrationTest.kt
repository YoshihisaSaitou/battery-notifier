package com.magicitengineer.batterynotifierandroidwearapp.data.datastore

import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.ThresholdEventProto
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.proto.WearStateProto
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition
import org.junit.Assert.assertEquals
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
    }
}

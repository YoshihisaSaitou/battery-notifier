package com.magicitengineer.batterynotifierandroidmobileapp.data.datastore

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.BatterySnapshotProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.ThresholdReachedEventProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileStateSanitizerTest {
    @Test
    fun invalidScalarValuesUseSafeDefaults() {
        val invalid = MobileStateProto.newBuilder()
            .setStorageSchemaVersion(99)
            .setThresholdPercent(4)
            .setRearmHysteresisPercent(99)
            .setSequence(-1L)
            .setPendingStateSequence(50L)
            .setInvalidInputCount(-2L)
            .build()

        val result = MobileStateSanitizer.sanitize(invalid)

        assertEquals(1, result.storageSchemaVersion)
        assertEquals(20, result.thresholdPercent)
        assertEquals(2, result.rearmHysteresisPercent)
        assertEquals(0L, result.sequence)
        assertEquals(0L, result.pendingStateSequence)
        assertEquals(0L, result.invalidInputCount)
    }

    @Test
    fun invalidNestedRecordsAreRejectedAsWholeRecords() {
        val invalid = MobileStateSanitizer.defaultValue().toBuilder()
            .setSequence(3L)
            .setLastSnapshot(
                BatterySnapshotProto.newBuilder()
                    .setLevelPercent(101)
                    .setCapturedAtEpochMillis(1L)
                    .setSequence(3L)
                    .build()
            )
            .setPendingEvent(
                ThresholdReachedEventProto.newBuilder()
                    .setEventId("not-a-uuid")
                    .setLevelPercent(20)
                    .setThresholdPercent(20)
                    .setOccurredAtEpochMillis(100L)
                    .setExpiresAtEpochMillis(200L)
                    .setSequence(3L)
                    .build()
            )
            .build()

        val result = MobileStateSanitizer.sanitize(invalid)

        assertFalse(result.hasLastSnapshot())
        assertFalse(result.hasPendingEvent())
        assertTrue(result.hasAlertState())
    }

    @Test
    fun impossibleActiveAndResumeRequiredCombinationKeepsOnlyRecoveryRequirement() {
        val invalid = MobileStateSanitizer.defaultValue().toBuilder()
            .setMonitoringEnabled(true)
            .setResumeRequired(true)
            .build()

        val result = MobileStateSanitizer.sanitize(invalid)

        assertFalse(result.monitoringEnabled)
        assertTrue(result.resumeRequired)
    }
}

package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileRuntimeTriggerHandlerTest {
    @Test
    fun runtimeEntryPointsUseTheirExactSyncTriggers() = runBlocking {
        val received = mutableListOf<MobileSyncTrigger>()
        val handler = MobileRuntimeTriggerHandler { trigger ->
            received += trigger
            MobileSyncCoordinationResult.Skipped(
                trigger = trigger,
                reason = SyncSkipReason.BATTERY_UNAVAILABLE,
            )
        }

        handler.onConnectionRecovered()
        handler.onProcessRestored()
        handler.onManualSync()

        assertEquals(
            listOf(
                MobileSyncTrigger.CONNECTION_RECOVERED,
                MobileSyncTrigger.PROCESS_RESTORED,
                MobileSyncTrigger.MANUAL_SYNC,
            ),
            received,
        )
    }
}

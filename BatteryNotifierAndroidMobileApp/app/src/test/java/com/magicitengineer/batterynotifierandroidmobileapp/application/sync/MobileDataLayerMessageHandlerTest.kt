package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

import com.magicitengineer.batterynotifierandroidmobileapp.data.wearable.BatteryDataLayerContractV1
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MobileDataLayerMessageHandlerTest {
    @Test
    fun `exact request path triggers current-state sync`() = runBlocking {
        val expected = MobileSyncCoordinationResult.Skipped(
            trigger = MobileSyncTrigger.REQUEST_STATE,
            reason = SyncSkipReason.BATTERY_UNAVAILABLE,
        )
        var receivedTrigger: MobileSyncTrigger? = null
        val handler = MobileDataLayerMessageHandler(
            requestStatePath = BatteryDataLayerContractV1.REQUEST_STATE_PATH,
            syncRunner = MobileSyncTriggerRunner { trigger ->
                receivedTrigger = trigger
                expected
            },
        )

        val actual = handler.handle(BatteryDataLayerContractV1.REQUEST_STATE_PATH)

        assertEquals(MobileSyncTrigger.REQUEST_STATE, receivedTrigger)
        assertSame(
            expected,
            (actual as MobileDataLayerMessageResult.RequestStateHandled).coordinationResult,
        )
    }

    @Test
    fun `prefix and unknown paths are ignored without syncing`() = runBlocking {
        var calls = 0
        val handler = MobileDataLayerMessageHandler(
            requestStatePath = BatteryDataLayerContractV1.REQUEST_STATE_PATH,
            syncRunner = MobileSyncTriggerRunner {
                calls += 1
                error("sync must not run for an unmatched path")
            },
        )

        val prefixResult = handler.handle("/battery-notifier/v1/request-state/extra")
        val unknownResult = handler.handle("/battery-notifier/v1/unknown")

        assertSame(MobileDataLayerMessageResult.Ignored, prefixResult)
        assertSame(MobileDataLayerMessageResult.Ignored, unknownResult)
        assertEquals(0, calls)
    }
}

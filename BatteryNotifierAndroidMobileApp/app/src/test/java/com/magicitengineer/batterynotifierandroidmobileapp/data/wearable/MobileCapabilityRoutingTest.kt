package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileCapabilityRoutingTest {
    @Test
    fun `current receiver capability with reachable node triggers convergence`() {
        assertTrue(
            shouldSyncForReachableWearCapability(
                BatteryDataLayerContractV1.WEAR_STATE_RECEIVER_CAPABILITY,
                reachableNodeCount = 1,
            )
        )
    }

    @Test
    fun `unreachable or unrelated capability does not trigger convergence`() {
        assertFalse(
            shouldSyncForReachableWearCapability(
                BatteryDataLayerContractV1.WEAR_STATE_RECEIVER_CAPABILITY,
                reachableNodeCount = 0,
            )
        )
        assertFalse(shouldSyncForReachableWearCapability("other", reachableNodeCount = 1))
    }
}

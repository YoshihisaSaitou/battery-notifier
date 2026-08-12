package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileAdsGateTest {
    @Test
    fun `consent starts initialization once and readiness controls the banner`() {
        val gate = MobileAdsGate()

        assertFalse(gate.updateConsent(canRequestAds = false))
        assertEquals(MobileAdsUiState(), gate.state.value)

        assertTrue(gate.updateConsent(canRequestAds = true))
        assertEquals(
            MobileAdsUiState(
                canRequestAds = true,
                initialization = MobileAdsInitialization.INITIALIZING,
            ),
            gate.state.value,
        )
        assertFalse(gate.state.value.showBanner)

        assertFalse(gate.updateConsent(canRequestAds = true))
        gate.markInitialized()

        assertEquals(MobileAdsInitialization.READY, gate.state.value.initialization)
        assertTrue(gate.state.value.showBanner)
    }

    @Test
    fun `revocation hides a ready banner without reinitializing on regrant`() {
        val gate = MobileAdsGate()
        assertTrue(gate.updateConsent(canRequestAds = true))
        gate.markInitialized()

        assertFalse(gate.updateConsent(canRequestAds = false))
        assertEquals(MobileAdsInitialization.READY, gate.state.value.initialization)
        assertFalse(gate.state.value.showBanner)

        assertFalse(gate.updateConsent(canRequestAds = true))
        assertTrue(gate.state.value.showBanner)
    }

    @Test
    fun `initialization failure is terminal for the process and keeps banner hidden`() {
        val gate = MobileAdsGate()
        assertTrue(gate.updateConsent(canRequestAds = true))

        gate.markInitializationFailed()

        assertEquals(MobileAdsInitialization.FAILED, gate.state.value.initialization)
        assertFalse(gate.state.value.showBanner)
        assertFalse(gate.updateConsent(canRequestAds = true))
    }

    @Test
    fun `late initialization callback cannot override a revoked consent decision`() {
        val gate = MobileAdsGate()
        assertTrue(gate.updateConsent(canRequestAds = true))
        gate.updateConsent(canRequestAds = false)

        gate.markInitialized()

        assertEquals(MobileAdsInitialization.READY, gate.state.value.initialization)
        assertFalse(gate.state.value.canRequestAds)
        assertFalse(gate.state.value.showBanner)
    }
}

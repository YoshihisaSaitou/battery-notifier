package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MobileAdsInitialization {
    NOT_STARTED,
    INITIALIZING,
    READY,
    FAILED,
}

data class MobileAdsUiState(
    val canRequestAds: Boolean = false,
    val initialization: MobileAdsInitialization = MobileAdsInitialization.NOT_STARTED,
) {
    val showBanner: Boolean
        get() = canRequestAds && initialization == MobileAdsInitialization.READY
}

class MobileAdsGate {
    private val mutableState = MutableStateFlow(MobileAdsUiState())
    val state: StateFlow<MobileAdsUiState> = mutableState.asStateFlow()

    @Synchronized
    fun updateConsent(canRequestAds: Boolean): Boolean {
        val current = mutableState.value
        val shouldInitialize = canRequestAds &&
            current.initialization == MobileAdsInitialization.NOT_STARTED
        mutableState.value = current.copy(
            canRequestAds = canRequestAds,
            initialization = if (shouldInitialize) {
                MobileAdsInitialization.INITIALIZING
            } else {
                current.initialization
            },
        )
        return shouldInitialize
    }

    @Synchronized
    fun markInitialized() {
        val current = mutableState.value
        if (current.initialization == MobileAdsInitialization.INITIALIZING) {
            mutableState.value = current.copy(
                initialization = MobileAdsInitialization.READY,
            )
        }
    }

    @Synchronized
    fun markInitializationFailed() {
        val current = mutableState.value
        if (current.initialization == MobileAdsInitialization.INITIALIZING) {
            mutableState.value = current.copy(
                initialization = MobileAdsInitialization.FAILED,
            )
        }
    }
}

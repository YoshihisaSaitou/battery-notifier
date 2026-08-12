package com.magicitengineer.batterynotifierandroidmobileapp

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileAppContainer
import com.magicitengineer.batterynotifierandroidmobileapp.presentation.MobileAdsGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BatteryNotifierApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val mobileAdsGate = MobileAdsGate()

    fun launchApplicationTask(block: suspend CoroutineScope.() -> Unit) {
        applicationScope.launch(block = block)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            MobileAppContainer.runtimeTriggerHandler(this@BatteryNotifierApplication)
                .onProcessRestored()
        }
    }

    fun updateMobileAdsConsent(canRequestAds: Boolean) {
        if (!mobileAdsGate.updateConsent(canRequestAds)) return

        applicationScope.launch {
            try {
                MobileAds.initialize(this@BatteryNotifierApplication) {
                    mobileAdsGate.markInitialized()
                }
            } catch (_: RuntimeException) {
                mobileAdsGate.markInitializationFailed()
            }
        }
    }
}

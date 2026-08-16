package com.magicitengineer.batterynotifierandroidmobileapp.platform.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.magicitengineer.batterynotifierandroidmobileapp.BuildConfig

data class MobileAdsConsentState(
    val canRequestAds: Boolean,
    val privacyOptionsRequired: Boolean,
)

class GoogleMobileAdsConsentManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val consentInformation =
        UserMessagingPlatform.getConsentInformation(applicationContext)

    fun gatherConsent(
        activity: Activity,
        onStateChanged: (MobileAdsConsentState) -> Unit,
    ) {
        val parameters = buildConsentRequestParameters()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                publishState(onStateChanged)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    publishState(onStateChanged)
                }
            },
            {
                publishState(onStateChanged)
            },
        )
    }

    fun showPrivacyOptions(
        activity: Activity,
        onStateChanged: (MobileAdsConsentState) -> Unit,
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            publishState(onStateChanged)
        }
    }

    private fun buildConsentRequestParameters(): ConsentRequestParameters =
        ConsentRequestParameters.Builder()
            .apply {
                if (BuildConfig.UMP_FORCE_EEA_FOR_TESTING) {
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(applicationContext)
                            .setDebugGeography(
                                ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
                            )
                            .build(),
                    )
                }
            }
            .build()

    private fun publishState(onStateChanged: (MobileAdsConsentState) -> Unit) {
        onStateChanged(
            MobileAdsConsentState(
                canRequestAds = consentInformation.canRequestAds(),
                privacyOptionsRequired =
                    consentInformation.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
            )
        )
    }
}

package com.magicitengineer.batterynotifierandroidmobileapp.platform.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

data class MobileAdsConsentState(
    val canRequestAds: Boolean,
    val privacyOptionsRequired: Boolean,
)

class GoogleMobileAdsConsentManager(context: Context) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

    fun gatherConsent(
        activity: Activity,
        onStateChanged: (MobileAdsConsentState) -> Unit,
    ) {
        val parameters = ConsentRequestParameters.Builder().build()
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

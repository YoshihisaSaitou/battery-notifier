package com.magicitengineer.batterynotifierandroidmobileapp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AdMobConfigurationContractTest {
    @Test
    fun `UMP EEA geography override is enabled only for debug builds`() {
        val buildFile = File("build.gradle.kts").readText()
        val debug = buildTypeBlock(buildFile, "debug")
        val release = buildTypeBlock(buildFile, "release")
        val consentManager = File(
            "src/main/java/com/magicitengineer/batterynotifierandroidmobileapp/" +
                "platform/ads/GoogleMobileAdsConsentManager.kt",
        ).readText()

        assertTrue(
            debug.contains(
                "buildConfigField(\"boolean\", \"UMP_FORCE_EEA_FOR_TESTING\", \"true\")",
            ),
        )
        assertTrue(
            release.contains(
                "buildConfigField(\"boolean\", \"UMP_FORCE_EEA_FOR_TESTING\", \"false\")",
            ),
        )
        assertTrue(consentManager.contains("BuildConfig.UMP_FORCE_EEA_FOR_TESTING"))
        assertTrue(consentManager.contains("setConsentDebugSettings"))
        assertTrue(
            consentManager.contains(
                "ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA",
            ),
        )
        assertFalse(consentManager.contains("addTestDeviceHashedId"))
    }

    @Test
    fun `debug and release keep demo and production identifiers isolated`() {
        val buildFile = File("build.gradle.kts").readText()
        val debug = buildTypeBlock(buildFile, "debug")
        val release = buildTypeBlock(buildFile, "release")

        assertTrue(debug.contains(DEMO_APP_ID))
        assertTrue(debug.contains(DEMO_BANNER_ID))
        assertFalse(debug.contains(PRODUCTION_APP_ID))
        assertFalse(debug.contains(PRODUCTION_BANNER_ID))

        assertTrue(release.contains(PRODUCTION_APP_ID))
        assertTrue(release.contains(PRODUCTION_BANNER_ID))
        assertFalse(release.contains(DEMO_APP_ID))
        assertFalse(release.contains(DEMO_BANNER_ID))

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("com.google.android.gms.ads.APPLICATION_ID"))
        assertTrue(manifest.contains("\${admobApplicationId}"))
    }

    @Test
    fun `ads dependencies and identifiers remain Mobile only`() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        assertTrue(catalog.contains("play-services-ads"))
        assertTrue(catalog.contains("user-messaging-platform"))

        val wearBuild = File("../../BatteryNotifierAndroidWearApp/app/build.gradle.kts").readText()
        val wearCatalog = File("../../BatteryNotifierAndroidWearApp/gradle/libs.versions.toml")
            .readText()
        val wearManifest = File("../../BatteryNotifierAndroidWearApp/app/src/main/AndroidManifest.xml")
            .readText()
        val wearConfiguration = wearBuild + wearCatalog + wearManifest

        assertFalse(wearConfiguration.contains("play-services-ads"))
        assertFalse(wearConfiguration.contains("user-messaging-platform"))
        assertFalse(wearConfiguration.contains(PRODUCTION_APP_ID))
        assertFalse(wearConfiguration.contains(PRODUCTION_BANNER_ID))
    }

    @Test
    fun `privacy options are localized and wired without app tracking`() {
        assertLocalizedPrivacyOptions(
            File("src/main/res/values/strings.xml"),
            expectedAction = "Privacy options",
        )
        assertLocalizedPrivacyOptions(
            File("src/main/res/values-ja/strings.xml"),
            expectedAction = "プライバシー設定",
        )

        val activitySource = File(
            "src/main/java/com/magicitengineer/batterynotifierandroidmobileapp/MainActivity.kt",
        ).readText()
        assertTrue(activitySource.contains("R.string.ad_privacy_options"))
        assertTrue(activitySource.contains("onPrivacyOptions"))

        val adsSourceDirectory = File(
            "src/main/java/com/magicitengineer/batterynotifierandroidmobileapp/platform/ads",
        )
        val adsSource = adsSourceDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString(separator = "\n") { it.readText() }

        assertFalse(adsSource.contains("DataStore"))
        assertFalse(adsSource.contains("DataClient"))
        assertFalse(adsSource.contains("MessageClient"))
        assertFalse(adsSource.contains("Log."))
        assertFalse(adsSource.contains("onAdClicked"))
    }

    @Test
    fun `loaded bottom banner keeps clear of system navigation without preload space`() {
        val bannerSource = File(
            "src/main/java/com/magicitengineer/batterynotifierandroidmobileapp/" +
                "presentation/AdMobBanner.kt",
        ).readText()
        val loadedBranch = bannerSource.indexOf("if (isLoaded)")
        val androidView = bannerSource.indexOf("AndroidView(", startIndex = loadedBranch)
        val navigationInset = bannerSource.indexOf(
            ".navigationBarsPadding()",
            startIndex = androidView,
        )

        assertTrue(loadedBranch >= 0)
        assertTrue(androidView > loadedBranch)
        assertTrue(navigationInset > androidView)
    }

    private fun buildTypeBlock(source: String, name: String): String {
        val start = source.indexOf("        $name {")
        check(start >= 0) { "Missing $name build type" }
        val end = source.indexOf("        }", start)
        check(end >= 0) { "Unterminated $name build type" }
        return source.substring(start, end)
    }

    private fun assertLocalizedPrivacyOptions(resourceFile: File, expectedAction: String) {
        val resources = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resourceFile)
        val strings = resources.getElementsByTagName("string")
        val values = (0 until strings.length)
            .map { strings.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }

        assertEquals(expectedAction, values["ad_privacy_options"])
        assertTrue(values.getValue("ad_privacy_options_description").isNotBlank())
    }

    private companion object {
        const val DEMO_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        const val DEMO_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"
        const val PRODUCTION_APP_ID = "ca-app-pub-9265284608955761~9984708322"
        const val PRODUCTION_BANNER_ID = "ca-app-pub-9265284608955761/2408053327"
    }
}

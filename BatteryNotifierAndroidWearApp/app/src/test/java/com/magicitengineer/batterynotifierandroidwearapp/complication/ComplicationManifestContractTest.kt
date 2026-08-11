package com.magicitengineer.batterynotifierandroidwearapp.complication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ComplicationManifestContractTest {
    @Test
    fun `provider declares picker icon label permission and supported types`() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("Manifest not found at ${manifestFile.absolutePath}", manifestFile.isFile)

        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifestFile)
        val service = document.getElementsByTagName("service")
            .let { services ->
                (0 until services.length)
                    .map { services.item(it) as Element }
                    .single {
                        it.getAttributeNS(ANDROID_NAMESPACE, "name") ==
                            ".complication.MainComplicationService"
                    }
            }

        assertEquals(
            "@drawable/ic_complication_provider_app_24",
            service.getAttributeNS(ANDROID_NAMESPACE, "icon"),
        )
        assertEquals(
            "@string/complication_label",
            service.getAttributeNS(ANDROID_NAMESPACE, "label"),
        )
        assertEquals(
            "com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER",
            service.getAttributeNS(ANDROID_NAMESPACE, "permission"),
        )

        val supportedTypes = service.getElementsByTagName("meta-data")
            .let { metadata ->
                (0 until metadata.length)
                    .map { metadata.item(it) as Element }
                    .single {
                        it.getAttributeNS(ANDROID_NAMESPACE, "name") ==
                            "android.support.wearable.complications.SUPPORTED_TYPES"
                    }
            }
        assertEquals(
            "SHORT_TEXT,RANGED_VALUE,LONG_TEXT",
            supportedTypes.getAttributeNS(ANDROID_NAMESPACE, "value"),
        )
    }

    @Test
    fun `picker vector is white 24dp and matches launcher monochrome path`() {
        val pickerVector = parseXml(
            File("src/main/res/drawable/ic_complication_provider_app_24.xml"),
        ).documentElement
        val launcherVector = parseXml(
            File("src/main/res/drawable/ic_battery_notifier_launcher_monochrome.xml"),
        ).documentElement

        assertEquals("24dp", pickerVector.getAttributeNS(ANDROID_NAMESPACE, "width"))
        assertEquals("24dp", pickerVector.getAttributeNS(ANDROID_NAMESPACE, "height"))
        assertEquals("108", pickerVector.getAttributeNS(ANDROID_NAMESPACE, "viewportWidth"))
        assertEquals("108", pickerVector.getAttributeNS(ANDROID_NAMESPACE, "viewportHeight"))

        val pickerPaths = pickerVector.getElementsByTagName("path")
        val launcherPaths = launcherVector.getElementsByTagName("path")
        assertEquals(1, pickerPaths.length)
        assertEquals(1, launcherPaths.length)
        val pickerPath = pickerPaths.item(0) as Element
        val launcherPath = launcherPaths.item(0) as Element
        assertEquals("#FFFFFFFF", pickerPath.getAttributeNS(ANDROID_NAMESPACE, "fillColor"))
        assertEquals(
            launcherPath.getAttributeNS(ANDROID_NAMESPACE, "pathData"),
            pickerPath.getAttributeNS(ANDROID_NAMESPACE, "pathData"),
        )
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(file)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}

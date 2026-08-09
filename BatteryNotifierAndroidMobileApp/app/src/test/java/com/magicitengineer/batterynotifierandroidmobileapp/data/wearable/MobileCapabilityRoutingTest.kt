package com.magicitengineer.batterynotifierandroidmobileapp.data.wearable

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

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

    @Test
    fun `mobile manifest routes every supported incoming message path`() {
        val manifest = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val services = manifest.getElementsByTagName("service")
        val listener = (0 until services.length)
            .map { services.item(it) as Element }
            .single {
                it.getAttributeNS(ANDROID_NAMESPACE, "name") ==
                    ".data.wearable.MobileDataLayerListenerService"
            }
        val messagePaths = listener.getElementsByTagName("intent-filter").let { filters ->
            (0 until filters.length).mapNotNull { index ->
                val filter = filters.item(index) as Element
                val actions = filter.getElementsByTagName("action")
                val receivesMessages = (0 until actions.length)
                    .map { actions.item(it) as Element }
                    .any {
                        it.getAttributeNS(ANDROID_NAMESPACE, "name") == MESSAGE_RECEIVED_ACTION
                    }
                if (!receivesMessages) return@mapNotNull null
                val data = filter.getElementsByTagName("data")
                (0 until data.length)
                    .map { data.item(it) as Element }
                    .map { it.getAttributeNS(ANDROID_NAMESPACE, "path") }
                    .single()
            }.toSet()
        }

        assertEquals(
            setOf(
                BatteryDataLayerContractV1.REQUEST_STATE_PATH,
                BatteryDataLayerContractV1.CHANGE_THRESHOLD_PATH,
                BatteryDataLayerContractV1.CHANGE_FULL_CHARGE_SETTING_PATH,
            ),
            messagePaths,
        )
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val MESSAGE_RECEIVED_ACTION = "com.google.android.gms.wearable.MESSAGE_RECEIVED"
    }
}

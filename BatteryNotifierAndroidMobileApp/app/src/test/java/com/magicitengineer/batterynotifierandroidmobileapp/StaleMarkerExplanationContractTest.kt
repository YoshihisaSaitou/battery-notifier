package com.magicitengineer.batterynotifierandroidmobileapp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class StaleMarkerExplanationContractTest {
    @Test
    fun `mobile explanation is localized and placed after sync content`() {
        assertExplanation(
            resourceFile = File("src/main/res/values/strings.xml"),
            expectedTitle = "About the “!” on the watch face",
            expectedAge = "five minutes",
            expectedAction = "Sync now",
        )
        assertExplanation(
            resourceFile = File("src/main/res/values-ja/strings.xml"),
            expectedTitle = "ウォッチフェイスの「!」について",
            expectedAge = "5分",
            expectedAction = "今すぐ同期",
        )

        val source = File(
            "src/main/java/com/magicitengineer/batterynotifierandroidmobileapp/MainActivity.kt",
        ).readText()
        val syncEnd = source.indexOf(
            "Text(text = stringResource(syncPresentation.statusMessageResource))",
        )
        val title = source.indexOf("R.string.complication_stale_explanation_title")
        val body = source.indexOf("R.string.complication_stale_explanation_body")

        assertTrue(syncEnd >= 0)
        assertTrue(title > syncEnd)
        assertTrue(body > title)
    }

    private fun assertExplanation(
        resourceFile: File,
        expectedTitle: String,
        expectedAge: String,
        expectedAction: String,
    ) {
        val resources = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(resourceFile)
        val strings = resources.getElementsByTagName("string")
        val values = (0 until strings.length)
            .map { strings.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
        val body = values.getValue("complication_stale_explanation_body")

        assertEquals(expectedTitle, values["complication_stale_explanation_title"])
        assertTrue(body.contains(expectedAge))
        assertTrue(body.contains("!"))
        assertTrue(body.contains(expectedAction))
    }
}

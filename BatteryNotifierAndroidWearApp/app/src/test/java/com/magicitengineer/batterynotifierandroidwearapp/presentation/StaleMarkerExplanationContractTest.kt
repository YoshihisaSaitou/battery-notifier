package com.magicitengineer.batterynotifierandroidwearapp.presentation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class StaleMarkerExplanationContractTest {
    @Test
    fun `wear explanation is localized and is the final list content`() {
        assertExplanation(
            resourceFile = File("src/main/res/values/strings.xml"),
            expectedTitle = "About the “!” on the watch face",
            expectedAge = "five minutes",
            expectedAction = "Retry sync",
        )
        assertExplanation(
            resourceFile = File("src/main/res/values-ja/strings.xml"),
            expectedTitle = "ウォッチフェイスの「!」について",
            expectedAge = "5分",
            expectedAction = "同期を再試行",
        )

        val source = File(
            "src/main/java/com/magicitengineer/batterynotifierandroidwearapp/presentation/MainActivity.kt",
        ).readText()
        val retryBlock = source.indexOf("displayState.levelPercent == null ||")
        val title = source.indexOf("R.string.complication_stale_explanation_title")
        val body = source.indexOf("R.string.complication_stale_explanation_body")
        val listEnd = source.indexOf("\n    }\n}\n\n@Composable", body)

        assertTrue(retryBlock >= 0)
        assertTrue(title > retryBlock)
        assertTrue(body > title)
        assertTrue(listEnd > body)
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

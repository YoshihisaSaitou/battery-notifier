package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.magicitengineer.batterynotifierandroidmobileapp.R
import com.magicitengineer.batterynotifierandroidmobileapp.ui.theme.BatteryNotifierAndroidMobileAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThresholdEditorInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sideButtonsFlankTheSliderAndScaleLabelsAreBelowIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val decreaseDescription = context.getString(R.string.threshold_decrease_description)
        val increaseDescription = context.getString(R.string.threshold_increase_description)

        composeRule.setContent {
            BatteryNotifierAndroidMobileAppTheme {
                ThresholdEditor(
                    draftThreshold = 20,
                    onThresholdChanged = {},
                )
            }
        }

        val decreaseBounds = composeRule
            .onNodeWithContentDescription(decreaseDescription)
            .fetchSemanticsNode().boundsInRoot
        val increaseBounds = composeRule
            .onNodeWithContentDescription(increaseDescription)
            .fetchSemanticsNode().boundsInRoot
        val sliderBounds = composeRule
            .onNodeWithTag(THRESHOLD_SLIDER_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(decreaseBounds.center.x < sliderBounds.center.x)
        assertTrue(sliderBounds.center.x < increaseBounds.center.x)

        listOf("5", "50", "100").forEach { label ->
            val labelBounds = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(labelBounds.top >= sliderBounds.bottom)
        }
    }

    @Test
    fun sideButtonsAdjustByOneAndStopAtTheRangeBoundaries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val decreaseDescription = context.getString(R.string.threshold_decrease_description)
        val increaseDescription = context.getString(R.string.threshold_increase_description)
        var latestValue = 5

        composeRule.setContent {
            BatteryNotifierAndroidMobileAppTheme {
                val value = remember { mutableIntStateOf(5) }
                latestValue = value.intValue
                ThresholdEditor(
                    draftThreshold = value.intValue,
                    onThresholdChanged = {
                        value.intValue = it
                        latestValue = it
                    },
                )
            }
        }

        composeRule.onNodeWithContentDescription(decreaseDescription).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(increaseDescription)
            .assertIsEnabled()
            .performClick()
        assertEquals(6, latestValue)
    }

    @Test
    fun captureReferenceStateForVisualComparison() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            BatteryNotifierAndroidMobileAppTheme {
                Box(
                    modifier = Modifier
                        .wrapContentHeight()
                        .testTag(SCREENSHOT_CAPTURE_TEST_TAG),
                ) {
                    ThresholdEditor(
                        draftThreshold = 50,
                        onThresholdChanged = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val output = File(
            requireNotNull(context.getExternalFilesDir(null)),
            SCREENSHOT_FILE_NAME,
        )
        val saved = output.outputStream().use { stream ->
            composeRule.onNodeWithTag(SCREENSHOT_CAPTURE_TEST_TAG)
                .captureToImage()
                .asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(saved)
    }

    private companion object {
        const val SCREENSHOT_FILE_NAME = "bn008-threshold-editor-pixel9a.png"
        const val SCREENSHOT_CAPTURE_TEST_TAG = "threshold-editor-capture"
    }
}

package com.magicitengineer.batterynotifierandroidwearapp.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.magicitengineer.batterynotifierandroidwearapp.R
import com.magicitengineer.batterynotifierandroidwearapp.presentation.theme.BatteryNotifierAndroidWearAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThresholdDraftSliderInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun increaseAndDecreaseControlsChangeTheExposedValueByOnePercent() {
        setSlider(initialThreshold = 20)

        composeRule.onNodeWithContentDescription(increaseDescription()).performClick()
        composeRule.onNodeWithContentDescription(sliderDescription(21)).assertExists()

        composeRule.onNodeWithContentDescription(decreaseDescription()).performClick()
        composeRule.onNodeWithContentDescription(sliderDescription(20)).assertExists()
    }

    @Test
    fun decreaseControlIsDisabledAtFivePercent() {
        setSlider(initialThreshold = MIN_THRESHOLD_PERCENT)

        composeRule.onNodeWithContentDescription(decreaseDescription()).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(sliderDescription(5)).assertExists()
    }

    @Test
    fun increaseControlIsDisabledAtOneHundredPercent() {
        setSlider(initialThreshold = MAX_THRESHOLD_PERCENT)

        composeRule.onNodeWithContentDescription(increaseDescription()).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(sliderDescription(100)).assertExists()
    }

    private fun setSlider(initialThreshold: Int) {
        composeRule.setContent {
            var threshold by remember { mutableIntStateOf(initialThreshold) }
            BatteryNotifierAndroidWearAppTheme {
                ThresholdDraftSlider(
                    thresholdDraftPercent = threshold,
                    onThresholdStep = { direction ->
                        threshold = (threshold + direction).coerceIn(THRESHOLD_PERCENT_RANGE)
                    },
                )
            }
        }
    }

    private fun sliderDescription(value: Int): String =
        context().getString(R.string.threshold_slider_description, value)

    private fun decreaseDescription(): String =
        context().getString(R.string.decrease_threshold)

    private fun increaseDescription(): String =
        context().getString(R.string.increase_threshold)

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()
}

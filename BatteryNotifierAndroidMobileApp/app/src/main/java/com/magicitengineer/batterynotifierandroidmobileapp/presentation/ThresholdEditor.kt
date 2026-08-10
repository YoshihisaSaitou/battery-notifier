package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.magicitengineer.batterynotifierandroidmobileapp.R
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThresholdEditor(
    draftThreshold: Int,
    onThresholdChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val decreaseDescription = stringResource(R.string.threshold_decrease_description)
    val increaseDescription = stringResource(R.string.threshold_increase_description)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            modifier = Modifier
                .size(ADJUSTMENT_BUTTON_SIZE)
                .semantics { contentDescription = decreaseDescription },
            enabled = draftThreshold > AlertRule.MIN_THRESHOLD_PERCENT,
            onClick = {
                onThresholdChanged(adjustThreshold(draftThreshold, delta = -1))
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_remove_24),
                contentDescription = null,
            )
        }

        Slider(
            modifier = Modifier
                .weight(1f)
                .testTag(THRESHOLD_SLIDER_TEST_TAG),
            value = draftThreshold.toFloat(),
            onValueChange = { onThresholdChanged(it.roundToInt()) },
            valueRange = AlertRule.MIN_THRESHOLD_PERCENT.toFloat()..
                AlertRule.MAX_THRESHOLD_PERCENT.toFloat(),
            steps = THRESHOLD_SLIDER_STEPS,
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    drawTick = { _, _ -> },
                )
            },
        )

        FilledTonalIconButton(
            modifier = Modifier
                .size(ADJUSTMENT_BUTTON_SIZE)
                .semantics { contentDescription = increaseDescription },
            enabled = draftThreshold < AlertRule.MAX_THRESHOLD_PERCENT,
            onClick = {
                onThresholdChanged(adjustThreshold(draftThreshold, delta = 1))
            },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_24),
                contentDescription = null,
            )
        }
    }
}

internal fun adjustThreshold(current: Int, delta: Int): Int =
    (current + delta).coerceIn(
        AlertRule.MIN_THRESHOLD_PERCENT,
        AlertRule.MAX_THRESHOLD_PERCENT,
    )

private val ADJUSTMENT_BUTTON_SIZE = 48.dp
private val CONTROL_SPACING = 8.dp
internal const val THRESHOLD_SLIDER_TEST_TAG = "threshold-slider"
internal const val THRESHOLD_SLIDER_STEPS =
    AlertRule.MAX_THRESHOLD_PERCENT - AlertRule.MIN_THRESHOLD_PERCENT - 1

package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.magicitengineer.batterynotifierandroidmobileapp.R
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import kotlin.math.roundToInt

@Composable
internal fun ThresholdEditor(
    draftThreshold: Int,
    onThresholdChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val decreaseDescription = stringResource(R.string.threshold_decrease_description)
    val increaseDescription = stringResource(R.string.threshold_increase_description)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ADJUSTMENT_BUTTON_SIZE + CONTROL_SPACING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScaleLabel(
                value = AlertRule.MIN_THRESHOLD_PERCENT,
                textAlign = TextAlign.Start,
            )
            ScaleLabel(
                value = THRESHOLD_MIDPOINT_LABEL,
                textAlign = TextAlign.Center,
            )
            ScaleLabel(
                value = AlertRule.MAX_THRESHOLD_PERCENT,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun RowScope.ScaleLabel(
    value: Int,
    textAlign: TextAlign,
) {
    Text(
        text = value.toString(),
        modifier = Modifier.weight(1f),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = textAlign,
    )
}

internal fun adjustThreshold(current: Int, delta: Int): Int =
    (current + delta).coerceIn(
        AlertRule.MIN_THRESHOLD_PERCENT,
        AlertRule.MAX_THRESHOLD_PERCENT,
    )

private val ADJUSTMENT_BUTTON_SIZE = 48.dp
private val CONTROL_SPACING = 8.dp
private const val THRESHOLD_MIDPOINT_LABEL = 50
internal const val THRESHOLD_SLIDER_TEST_TAG = "threshold-slider"

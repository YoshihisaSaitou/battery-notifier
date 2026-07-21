package com.magicitengineer.batterynotifierandroidwearapp.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.magicitengineer.batterynotifierandroidwearapp.R
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearAppContainer
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.Freshness
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayState
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayStateMapper
import com.magicitengineer.batterynotifierandroidwearapp.presentation.MainActivity
import kotlinx.coroutines.flow.first

class MainComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        createComplicationData(
            type = type,
            displayState = WearDisplayState(
                freshness = Freshness.FRESH,
                levelPercent = 68,
                monitoringEnabled = true,
            ),
        )

    override suspend fun onComplicationRequest(
        request: ComplicationRequest,
    ): ComplicationData {
        val persistentState = WearAppContainer.repository(this).state.first()
        return createComplicationData(
            type = request.complicationType,
            displayState = WearDisplayStateMapper.map(
                persistentState,
                System.currentTimeMillis().coerceAtLeast(1L),
            ),
        ) ?: NoDataComplicationData()
    }

    private fun createComplicationData(
        type: ComplicationType,
        displayState: WearDisplayState,
    ): ComplicationData? {
        val level = displayState.levelPercent ?: return NoDataComplicationData()
        val shortText = PlainComplicationText.Builder(
            getString(
                if (displayState.freshness == Freshness.STALE) {
                    R.string.stale_short
                } else {
                    R.string.battery_short
                },
                level,
            )
        ).build()
        val description = PlainComplicationText.Builder(
            buildDescription(displayState, level)
        ).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = shortText,
                contentDescription = description,
            ).setTapAction(mainActivityAction()).build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = level.toFloat(),
                min = 0f,
                max = 100f,
                contentDescription = description,
            ).setText(shortText).setTapAction(mainActivityAction()).build()

            else -> null
        }
    }

    private fun buildDescription(displayState: WearDisplayState, level: Int): String = buildString {
        append(getString(R.string.phone_battery_percent, level))
        append(", ")
        append(
            when {
                displayState.freshness == Freshness.STALE -> getString(R.string.stale_data)
                displayState.isCharging -> getString(R.string.charging)
                !displayState.monitoringEnabled -> getString(R.string.monitoring_off)
                else -> getString(R.string.discharging)
            }
        )
    }

    private fun mainActivityAction(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

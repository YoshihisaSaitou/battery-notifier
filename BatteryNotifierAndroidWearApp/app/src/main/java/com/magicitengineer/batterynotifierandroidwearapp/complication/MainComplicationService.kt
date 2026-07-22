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
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.magicitengineer.batterynotifierandroidwearapp.R
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearAppContainer
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.Freshness
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayState
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayTimelineMapper
import com.magicitengineer.batterynotifierandroidwearapp.presentation.MainActivity
import kotlinx.coroutines.flow.first
import java.time.Instant

internal fun buildBatteryComplicationData(
    type: ComplicationType,
    level: Int,
    shortTextValue: String,
    descriptionValue: String,
    visibleStatusValue: String,
    tapAction: PendingIntent? = null,
): ComplicationData? {
    val shortText = PlainComplicationText.Builder(shortTextValue).build()
    val description = PlainComplicationText.Builder(descriptionValue).build()
    val visibleStatus = PlainComplicationText.Builder(visibleStatusValue).build()
    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = shortText,
            contentDescription = description,
        ).setTitle(visibleStatus).apply {
            if (tapAction != null) setTapAction(tapAction)
        }.build()

        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
            value = level.toFloat(),
            min = 0f,
            max = 100f,
            contentDescription = description,
        ).setText(shortText).setTitle(visibleStatus).apply {
            if (tapAction != null) setTapAction(tapAction)
        }.build()

        else -> null
    }
}

class MainComplicationService : SuspendingTimelineComplicationDataSourceService() {
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
    ): ComplicationDataTimeline {
        val persistentState = WearAppContainer.repository(this).state.first()
        val displayTimeline = WearDisplayTimelineMapper.map(persistentState)
        val defaultData = createComplicationData(
            request.complicationType,
            displayTimeline.defaultState,
        ) ?: NoDataComplicationData()
        val timelineEntries = displayTimeline.entries
            .filter { it.endEpochMillisExclusive != Long.MAX_VALUE }
            .mapNotNull { entry ->
                createComplicationData(request.complicationType, entry.displayState)?.let { data ->
                    TimelineEntry(
                        validity = TimeInterval(
                            start = Instant.ofEpochMilli(entry.startEpochMillisInclusive),
                            end = Instant.ofEpochMilli(entry.endEpochMillisExclusive),
                        ),
                        complicationData = data,
                    )
                }
            }
        return ComplicationDataTimeline(defaultData, timelineEntries)
    }

    private fun createComplicationData(
        type: ComplicationType,
        displayState: WearDisplayState,
    ): ComplicationData? {
        val level = displayState.levelPercent ?: return NoDataComplicationData()
        val shortTextValue = getString(
                if (displayState.freshness == Freshness.STALE) {
                    R.string.stale_short
                } else {
                    R.string.battery_short
                },
                level,
            )
        val descriptionValue = buildDescription(displayState, level)
        val visibleStatusValue = when {
                displayState.clockWarning -> getString(R.string.clock_warning)
                displayState.freshness == Freshness.STALE -> getString(R.string.stale_data)
                displayState.freshness == Freshness.DELAYED -> getString(
                    R.string.delayed_updated,
                    displayState.ageMinutes ?: 0,
                )
                displayState.isCharging -> getString(R.string.charging)
                !displayState.monitoringEnabled -> getString(R.string.monitoring_off)
                else -> getString(R.string.discharging)
            }
        return buildBatteryComplicationData(
            type = type,
            level = level,
            shortTextValue = shortTextValue,
            descriptionValue = descriptionValue,
            visibleStatusValue = visibleStatusValue,
            tapAction = mainActivityAction(),
        )
    }

    private fun buildDescription(displayState: WearDisplayState, level: Int): String = buildString {
        append(getString(R.string.phone_battery_percent, level))
        append(", ")
        append(
            when {
                displayState.clockWarning -> getString(R.string.clock_warning)
                displayState.freshness == Freshness.STALE -> getString(R.string.stale_data)
                displayState.freshness == Freshness.DELAYED -> getString(
                    R.string.delayed_updated,
                    displayState.ageMinutes ?: 0,
                )
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

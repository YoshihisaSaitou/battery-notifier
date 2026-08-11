package com.magicitengineer.batterynotifierandroidwearapp.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
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
import java.util.concurrent.TimeUnit

internal const val COMPLICATION_LOW_BATTERY_PERCENT = 20

internal fun batteryComplicationIconRes(
    level: Int,
    isCharging: Boolean,
): Int = when {
    isCharging -> R.drawable.ic_complication_battery_charging_full_24
    level <= COMPLICATION_LOW_BATTERY_PERCENT -> R.drawable.ic_complication_battery_alert_24
    else -> R.drawable.ic_complication_battery_full_24
}

internal fun buildBatteryComplicationData(
    type: ComplicationType,
    level: Int,
    shortTextValue: String,
    descriptionValue: String,
    visibleStatusValue: String?,
    monochromaticImage: MonochromaticImage? = null,
    tapAction: PendingIntent? = null,
): ComplicationData? = buildBatteryComplicationData(
    type = type,
    level = level,
    shortTextValue = shortTextValue,
    description = PlainComplicationText.Builder(descriptionValue).build(),
    visibleStatus = visibleStatusValue?.let { PlainComplicationText.Builder(it).build() },
    monochromaticImage = monochromaticImage,
    tapAction = tapAction,
)

internal fun buildBatteryComplicationData(
    type: ComplicationType,
    level: Int,
    shortTextValue: String,
    description: ComplicationText,
    visibleStatus: ComplicationText?,
    monochromaticImage: MonochromaticImage? = null,
    tapAction: PendingIntent? = null,
): ComplicationData? {
    val shortText = PlainComplicationText.Builder(shortTextValue).build()
    return when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = shortText,
            contentDescription = description,
        ).apply {
            if (visibleStatus != null) setTitle(visibleStatus)
            if (monochromaticImage != null) setMonochromaticImage(monochromaticImage)
            if (tapAction != null) setTapAction(tapAction)
        }.build()

        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
            value = level.toFloat(),
            min = 0f,
            max = 100f,
            contentDescription = description,
        ).setText(shortText)
            .apply {
                if (visibleStatus != null) setTitle(visibleStatus)
                if (monochromaticImage != null) setMonochromaticImage(monochromaticImage)
                if (tapAction != null) setTapAction(tapAction)
            }.build()

        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
            text = shortText,
            contentDescription = description,
        ).apply {
            if (visibleStatus != null) setTitle(visibleStatus)
            if (monochromaticImage != null) setMonochromaticImage(monochromaticImage)
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
        val description = buildDescription(displayState, level)
        val monochromaticImage = createMonochromaticImage(
            batteryComplicationIconRes(
                level = level,
                isCharging = displayState.isCharging,
            ),
        )
        val visibleStatus = when {
                displayState.clockWarning -> getString(R.string.clock_warning)
                displayState.freshness == Freshness.STALE -> getString(R.string.stale_data)
                displayState.freshness == Freshness.DELAYED -> null
                !displayState.monitoringEnabled -> getString(R.string.monitoring_off)
                displayState.isCharging -> null
                else -> getString(R.string.discharging)
            }
        val visibleStatusText = if (
            displayState.freshness == Freshness.DELAYED &&
            !displayState.clockWarning &&
            displayState.receivedAtEpochMillis != null
        ) {
            relativeAgeComplicationText(
                receivedAtEpochMillis = displayState.receivedAtEpochMillis,
                surroundingText = getString(R.string.complication_updated_age),
            )
        } else {
            visibleStatus?.let { PlainComplicationText.Builder(it).build() }
        }
        return buildBatteryComplicationData(
            type = type,
            level = level,
            shortTextValue = shortTextValue,
            description = description,
            visibleStatus = visibleStatusText,
            monochromaticImage = monochromaticImage,
            tapAction = mainActivityAction(),
        )
    }

    private fun createMonochromaticImage(drawableRes: Int): MonochromaticImage {
        val icon = Icon.createWithResource(this, drawableRes)
        return MonochromaticImage.Builder(icon)
            .setAmbientImage(icon)
            .build()
    }

    private fun buildDescription(
        displayState: WearDisplayState,
        level: Int,
    ): ComplicationText = if (
        !displayState.clockWarning &&
        (displayState.freshness == Freshness.DELAYED ||
            displayState.freshness == Freshness.STALE) &&
        displayState.receivedAtEpochMillis != null
    ) {
        relativeAgeComplicationText(
            receivedAtEpochMillis = displayState.receivedAtEpochMillis,
            surroundingText = getString(
                if (displayState.freshness == Freshness.STALE) {
                    R.string.phone_battery_stale_updated_age
                } else {
                    R.string.phone_battery_updated_age
                },
                level,
            ),
        )
    } else {
        val status = when {
            displayState.clockWarning -> getString(R.string.clock_warning)
            displayState.isCharging -> getString(R.string.charging)
            !displayState.monitoringEnabled -> getString(R.string.monitoring_off)
            else -> getString(R.string.discharging)
        }
        PlainComplicationText.Builder(
            "${getString(R.string.phone_battery_percent, level)}, $status",
        ).build()
    }

    private fun mainActivityAction(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal fun relativeAgeComplicationText(
    receivedAtEpochMillis: Long,
    surroundingText: String,
): TimeDifferenceComplicationText {
    require(receivedAtEpochMillis > 0L)
    return TimeDifferenceComplicationText.Builder(
        style = TimeDifferenceStyle.SHORT_SINGLE_UNIT,
        countUpTimeReference = CountUpTimeReference(
            Instant.ofEpochMilli(receivedAtEpochMillis),
        ),
    ).setMinimumTimeUnit(TimeUnit.MINUTES)
        .setText(surroundingText)
        .build()
}

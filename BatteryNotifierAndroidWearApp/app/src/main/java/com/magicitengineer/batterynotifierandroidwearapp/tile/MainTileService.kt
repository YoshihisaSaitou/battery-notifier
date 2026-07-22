package com.magicitengineer.batterynotifierandroidwearapp.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.magicitengineer.batterynotifierandroidwearapp.R
import com.magicitengineer.batterynotifierandroidwearapp.data.datastore.WearAppContainer
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.Freshness
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayState
import com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayTimelineMapper
import com.magicitengineer.batterynotifierandroidwearapp.presentation.MainActivity
import kotlinx.coroutines.flow.first

private const val RESOURCES_VERSION = "1"

@OptIn(ExperimentalHorologistApi::class)
class MainTileService : SuspendingTileService() {
    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ) = resources()

    override suspend fun tileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): TileBuilders.Tile {
        val persistentState = WearAppContainer.repository(this).state.first()
        return tile(requestParams, this, WearDisplayTimelineMapper.map(persistentState))
    }
}

private fun resources(): ResourceBuilders.Resources = ResourceBuilders.Resources.Builder()
    .setVersion(RESOURCES_VERSION)
    .build()

private fun tile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    displayTimeline: com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayTimeline,
): TileBuilders.Tile {
    val timelineBuilder = TimelineBuilders.Timeline.Builder()
    displayTimeline.entries.forEach { entry ->
        timelineBuilder.addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setValidity(
                    TimelineBuilders.TimeInterval.Builder()
                        .setStartMillis(entry.startEpochMillisInclusive)
                        .setEndMillis(entry.endEpochMillisExclusive)
                        .build()
                )
                .setLayout(
                    LayoutElementBuilders.Layout.Builder()
                        .setRoot(tileLayout(requestParams, context, entry.displayState))
                        .build()
                )
                .build()
        )
    }
    if (displayTimeline.entries.isEmpty()) {
        timelineBuilder.addTimelineEntry(
            TimelineBuilders.TimelineEntry.Builder()
                .setLayout(
                    LayoutElementBuilders.Layout.Builder()
                        .setRoot(tileLayout(requestParams, context, displayTimeline.defaultState))
                        .build()
                )
                .build()
        )
    }
    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(timelineBuilder.build())
        .build()
}

private fun tileLayout(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    displayState: WearDisplayState,
): LayoutElementBuilders.LayoutElement {
    val valueText = when {
        displayState.levelPercent == null -> context.getString(R.string.no_data_short)
        displayState.freshness == Freshness.STALE -> context.getString(
            R.string.stale_short,
            displayState.levelPercent,
        )

        else -> context.getString(R.string.battery_short, displayState.levelPercent)
    }
    val labelText = when {
        displayState.levelPercent == null -> context.getString(R.string.no_phone_data)
        displayState.clockWarning -> context.getString(R.string.clock_warning)
        displayState.freshness == Freshness.STALE && displayState.ageMinutes != null ->
            context.getString(
            R.string.delayed_updated,
            displayState.ageMinutes,
        )
        displayState.freshness == Freshness.STALE -> context.getString(R.string.stale_data)
        displayState.isCharging -> context.getString(R.string.charging)
        !displayState.monitoringEnabled -> context.getString(R.string.monitoring_off)
        else -> context.getString(R.string.phone_label)
    }
    val content = LayoutElementBuilders.Column.Builder()
        .addContent(
            Text.Builder(context, valueText)
                .setColor(argb(Colors.DEFAULT.onSurface))
                .setTypography(Typography.TYPOGRAPHY_TITLE1)
                .build()
        )
        .addContent(
            Text.Builder(context, labelText)
                .setColor(argb(Colors.DEFAULT.onSurface))
                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                .build()
        )
        .build()
    val primaryLayout = PrimaryLayout.Builder(requestParams.deviceConfiguration)
        .setResponsiveContentInsetEnabled(true)
        .setContent(content)
        .build()
    val openApp = ModifiersBuilders.Clickable.Builder()
        .setId("open-wear-app")
        .setOnClick(
            ActionBuilders.launchAction(
                android.content.ComponentName(context, MainActivity::class.java),
            )
        )
        .build()
    return LayoutElementBuilders.Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setClickable(openApp)
                .build()
        )
        .addContent(primaryLayout)
        .build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData({ resources() }) {
    tile(
        it,
        context,
        com.magicitengineer.batterynotifierandroidwearapp.domain.presentation.WearDisplayTimeline(
            defaultState = WearDisplayState(
                freshness = Freshness.FRESH,
                levelPercent = 68,
                isCharging = true,
                monitoringEnabled = true,
            ),
            entries = emptyList(),
        ),
    )
}

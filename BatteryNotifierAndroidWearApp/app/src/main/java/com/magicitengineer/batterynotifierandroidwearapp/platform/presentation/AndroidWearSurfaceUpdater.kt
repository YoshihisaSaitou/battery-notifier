package com.magicitengineer.batterynotifierandroidwearapp.platform.presentation

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.magicitengineer.batterynotifierandroidwearapp.application.presentation.WearSurfaceUpdater
import com.magicitengineer.batterynotifierandroidwearapp.complication.MainComplicationService
import com.magicitengineer.batterynotifierandroidwearapp.tile.MainTileService

class AndroidWearSurfaceUpdater(
    context: Context,
) : WearSurfaceUpdater {
    private val applicationContext = context.applicationContext
    private val complicationRequester = ComplicationDataSourceUpdateRequester.create(
        applicationContext,
        ComponentName(applicationContext, MainComplicationService::class.java),
    )

    override fun requestRefresh() {
        TileService.getUpdater(applicationContext).requestUpdate(MainTileService::class.java)
        complicationRequester.requestUpdateAll()
    }
}

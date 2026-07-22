package com.magicitengineer.batterynotifierandroidwearapp.platform.presentation

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.magicitengineer.batterynotifierandroidwearapp.application.presentation.WearSurfaceUpdater
import com.magicitengineer.batterynotifierandroidwearapp.complication.MainComplicationService
import com.magicitengineer.batterynotifierandroidwearapp.tile.MainTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AndroidWearSurfaceUpdater(
    context: Context,
) : WearSurfaceUpdater {
    private val applicationContext = context.applicationContext
    private val complicationRequester = ComplicationDataSourceUpdateRequester.create(
        applicationContext,
        ComponentName(applicationContext, MainComplicationService::class.java),
    )

    override fun requestRefresh() {
        if (!refreshCoalescer.trySchedule()) return
        refreshScope.launch {
            try {
                delay(REFRESH_COALESCE_MILLIS)
                TileService.getUpdater(applicationContext).requestUpdate(MainTileService::class.java)
                complicationRequester.requestUpdateAll()
            } finally {
                refreshCoalescer.markCompleted()
            }
        }
    }

    private companion object {
        const val REFRESH_COALESCE_MILLIS = 250L
        val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val refreshCoalescer = WearSurfaceRefreshCoalescer()
    }
}

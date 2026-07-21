package com.magicitengineer.batterynotifierandroidmobileapp.application.battery

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.BatteryProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadingSource

fun interface EventIdFactory {
    fun create(): String
}

sealed interface BatteryRefreshResult {
    data class Refreshed(
        val processingResult: BatteryProcessingResult,
    ) : BatteryRefreshResult

    data object InvalidInput : BatteryRefreshResult

    data object Unavailable : BatteryRefreshResult
}

fun interface BatteryStateRefresher {
    suspend fun refresh(): BatteryRefreshResult
}

class CurrentBatteryStateRefresher(
    private val source: BatteryReadingSource,
    private val repository: MobileStateRepository,
    private val eventIdFactory: EventIdFactory,
) : BatteryStateRefresher {
    override suspend fun refresh(): BatteryRefreshResult = when (val result = source.readCurrent()) {
        is BatteryReadResult.Available -> BatteryRefreshResult.Refreshed(
            repository.processBatteryReading(
                reading = result.reading,
                candidateEventId = eventIdFactory.create(),
            )
        )

        BatteryReadResult.Invalid -> {
            repository.recordInvalidInput()
            BatteryRefreshResult.InvalidInput
        }

        BatteryReadResult.Unavailable -> BatteryRefreshResult.Unavailable
    }
}

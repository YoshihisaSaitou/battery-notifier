package com.magicitengineer.batterynotifierandroidmobileapp.application.battery

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.BatteryProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadingSource
import kotlinx.coroutines.flow.first

fun interface EventIdFactory {
    fun create(): String
}

sealed interface BatteryRefreshResult {
    data class Refreshed(
        val processingResult: BatteryProcessingResult,
    ) : BatteryRefreshResult

    data object InvalidInput : BatteryRefreshResult

    data object Unavailable : BatteryRefreshResult

    data object Unchanged : BatteryRefreshResult
}

fun interface BatteryStateRefresher {
    suspend fun refresh(): BatteryRefreshResult
}

fun interface BatteryReadResultProcessor {
    suspend fun process(result: BatteryReadResult): BatteryRefreshResult
}

class CurrentBatteryStateRefresher(
    private val source: BatteryReadingSource,
    private val repository: MobileStateRepository,
    private val eventIdFactory: EventIdFactory,
) : BatteryStateRefresher, BatteryReadResultProcessor {
    override suspend fun refresh(): BatteryRefreshResult = when (val result = source.readCurrent()) {
        is BatteryReadResult.Available -> persist(result)
        BatteryReadResult.Invalid -> recordInvalid()
        BatteryReadResult.Unavailable -> BatteryRefreshResult.Unavailable
    }

    override suspend fun process(result: BatteryReadResult): BatteryRefreshResult = when (result) {
        is BatteryReadResult.Available -> {
            val previous = repository.state.first().lastSnapshot
            if (
                previous?.levelPercent == result.reading.levelPercent &&
                previous.isCharging == result.reading.isCharging
            ) {
                BatteryRefreshResult.Unchanged
            } else {
                persist(result)
            }
        }

        BatteryReadResult.Invalid -> recordInvalid()

        BatteryReadResult.Unavailable -> BatteryRefreshResult.Unavailable
    }

    private suspend fun persist(result: BatteryReadResult.Available): BatteryRefreshResult =
        BatteryRefreshResult.Refreshed(
            repository.processBatteryReading(
                reading = result.reading,
                candidateEventId = eventIdFactory.create(),
            ),
        )

    private suspend fun recordInvalid(): BatteryRefreshResult {
        repository.recordInvalidInput()
        return BatteryRefreshResult.InvalidInput
    }
}

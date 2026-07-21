package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryRefreshResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryStateRefresher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class MobileSyncTrigger(
    val requiresCurrentBatteryReading: Boolean,
) {
    BATTERY_CHANGED(false),
    SETTINGS_CHANGED(false),
    CONNECTION_RECOVERED(true),
    REQUEST_STATE(true),
    MANUAL_SYNC(true),
    PROCESS_RESTORED(true),
}

enum class SyncSkipReason {
    INVALID_BATTERY_INPUT,
    BATTERY_UNAVAILABLE,
}

sealed interface MobileSyncCoordinationResult {
    data class Sent(
        val trigger: MobileSyncTrigger,
        val refreshResult: BatteryRefreshResult.Refreshed?,
        val batchResult: MobileSyncBatchResult,
    ) : MobileSyncCoordinationResult

    data class Skipped(
        val trigger: MobileSyncTrigger,
        val reason: SyncSkipReason,
    ) : MobileSyncCoordinationResult
}

fun interface MobileSyncTriggerRunner {
    suspend fun sync(trigger: MobileSyncTrigger): MobileSyncCoordinationResult
}

class MobileSyncCoordinator(
    private val refresher: BatteryStateRefresher,
    private val sender: PendingSyncSender,
) : MobileSyncTriggerRunner {
    private val mutex = Mutex()

    override suspend fun sync(
        trigger: MobileSyncTrigger,
    ): MobileSyncCoordinationResult = mutex.withLock {
        val refreshResult = if (trigger.requiresCurrentBatteryReading) {
            refresher.refresh()
        } else {
            null
        }

        when (refreshResult) {
            BatteryRefreshResult.InvalidInput -> MobileSyncCoordinationResult.Skipped(
                trigger = trigger,
                reason = SyncSkipReason.INVALID_BATTERY_INPUT,
            )

            BatteryRefreshResult.Unavailable -> MobileSyncCoordinationResult.Skipped(
                trigger = trigger,
                reason = SyncSkipReason.BATTERY_UNAVAILABLE,
            )

            is BatteryRefreshResult.Refreshed,
            null -> MobileSyncCoordinationResult.Sent(
                trigger = trigger,
                refreshResult = refreshResult,
                batchResult = sender.syncPending(),
            )
        }
    }
}

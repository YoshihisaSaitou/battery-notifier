package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

class MobileRuntimeTriggerHandler(
    private val syncRunner: MobileSyncTriggerRunner,
) {
    suspend fun onConnectionRecovered(): MobileSyncCoordinationResult =
        syncRunner.sync(MobileSyncTrigger.CONNECTION_RECOVERED)

    suspend fun onProcessRestored(): MobileSyncCoordinationResult =
        syncRunner.sync(MobileSyncTrigger.PROCESS_RESTORED)

    suspend fun onManualSync(): MobileSyncCoordinationResult =
        syncRunner.sync(MobileSyncTrigger.MANUAL_SYNC)
}

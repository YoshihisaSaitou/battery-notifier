package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

sealed interface MobileDataLayerMessageResult {
    data object Ignored : MobileDataLayerMessageResult

    data class RequestStateHandled(
        val coordinationResult: MobileSyncCoordinationResult,
    ) : MobileDataLayerMessageResult
}

class MobileDataLayerMessageHandler(
    private val requestStatePath: String,
    private val syncRunner: MobileSyncTriggerRunner,
) {
    suspend fun handle(path: String): MobileDataLayerMessageResult {
        if (path != requestStatePath) return MobileDataLayerMessageResult.Ignored

        return MobileDataLayerMessageResult.RequestStateHandled(
            syncRunner.sync(MobileSyncTrigger.REQUEST_STATE)
        )
    }
}

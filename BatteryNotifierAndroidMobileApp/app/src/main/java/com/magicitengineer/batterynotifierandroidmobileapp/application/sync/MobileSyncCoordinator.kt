package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryRefreshResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryReadResultProcessor
import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryStateRefresher
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringCommandOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringCommandResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringRunner
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringServiceGateway
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringStateUpdater
import com.magicitengineer.batterynotifierandroidmobileapp.application.notification.MobileNotificationDeliveryResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.notification.PendingMobileNotificationDeliverer
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSaveRejectionReason
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSaveResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSettingUpdater
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSettingsRunner
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.toSettingsState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadResult
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
    UNCHANGED_BATTERY_INPUT,
}

sealed interface MobileSyncCoordinationResult {
    data class Sent(
        val trigger: MobileSyncTrigger,
        val refreshResult: BatteryRefreshResult.Refreshed?,
        val batchResult: MobileSyncBatchResult,
        val mobileNotificationResult: MobileNotificationDeliveryResult =
            MobileNotificationDeliveryResult.NotPending,
    ) : MobileSyncCoordinationResult

    data class Skipped(
        val trigger: MobileSyncTrigger,
        val reason: SyncSkipReason,
    ) : MobileSyncCoordinationResult
}

fun interface MobileSyncTriggerRunner {
    suspend fun sync(trigger: MobileSyncTrigger): MobileSyncCoordinationResult
}

fun interface MobileBatteryChangeRunner {
    suspend fun processBatteryChange(result: BatteryReadResult): MobileSyncCoordinationResult
}

class MobileSyncCoordinator(
    private val refresher: BatteryStateRefresher,
    private val sender: PendingSyncSender,
    private val thresholdSettingUpdater: ThresholdSettingUpdater = ThresholdSettingUpdater {
        error("Threshold setting updates are not configured")
    },
    private val batteryReadResultProcessor: BatteryReadResultProcessor = BatteryReadResultProcessor {
        error("Battery callback processing is not configured")
    },
    private val monitoringStateUpdater: MonitoringStateUpdater = MonitoringStateUpdater { _, _ ->
        error("Monitoring state updates are not configured")
    },
    private val monitoringServiceGateway: MonitoringServiceGateway = object : MonitoringServiceGateway {
        override fun start() = error("Monitoring service start is not configured")
        override fun stop() = error("Monitoring service stop is not configured")
    },
    private val mobileNotificationDeliverer: PendingMobileNotificationDeliverer =
        PendingMobileNotificationDeliverer {
            MobileNotificationDeliveryResult.NotPending
        },
) : MobileSyncTriggerRunner, ThresholdSettingsRunner, MobileBatteryChangeRunner, MonitoringRunner {
    private val mutex = Mutex()

    override suspend fun sync(
        trigger: MobileSyncTrigger,
    ): MobileSyncCoordinationResult = mutex.withLock { syncLocked(trigger) }

    override suspend fun saveThreshold(thresholdPercent: Int): ThresholdSaveResult {
        if (thresholdPercent !in
            AlertRule.MIN_THRESHOLD_PERCENT..AlertRule.MAX_THRESHOLD_PERCENT
        ) {
            return ThresholdSaveResult.Rejected(ThresholdSaveRejectionReason.OUT_OF_RANGE)
        }

        return mutex.withLock {
            val persisted = thresholdSettingUpdater.updateThreshold(thresholdPercent)
            ThresholdSaveResult.Saved(
                state = persisted.toSettingsState(),
                currentAtOrBelowThreshold = persisted.lastSnapshot?.levelPercent
                    ?.let { it <= thresholdPercent }
                    ?: false,
                syncResult = syncLocked(MobileSyncTrigger.SETTINGS_CHANGED),
            )
        }
    }

    override suspend fun processBatteryChange(
        result: BatteryReadResult,
    ): MobileSyncCoordinationResult = mutex.withLock {
        syncAfterRefreshLocked(
            trigger = MobileSyncTrigger.BATTERY_CHANGED,
            refreshResult = batteryReadResultProcessor.process(result),
        )
    }

    override suspend fun startMonitoring(): MonitoringCommandResult = mutex.withLock {
        monitoringStateUpdater.update(monitoringEnabled = true, resumeRequired = false)
        try {
            monitoringServiceGateway.start()
            MonitoringCommandResult(
                outcome = MonitoringCommandOutcome.STARTED,
                syncResult = syncLocked(MobileSyncTrigger.SETTINGS_CHANGED),
            )
        } catch (_: RuntimeException) {
            monitoringStateUpdater.update(monitoringEnabled = false, resumeRequired = true)
            MonitoringCommandResult(
                outcome = MonitoringCommandOutcome.START_FAILED,
                syncResult = syncLocked(MobileSyncTrigger.SETTINGS_CHANGED),
            )
        }
    }

    override suspend fun stopMonitoring(): MonitoringCommandResult = mutex.withLock {
        monitoringStateUpdater.update(monitoringEnabled = false, resumeRequired = false)
        monitoringServiceGateway.stop()
        MonitoringCommandResult(
            outcome = MonitoringCommandOutcome.STOPPED,
            syncResult = syncLocked(MobileSyncTrigger.SETTINGS_CHANGED),
        )
    }

    override suspend fun resumeMonitoring(): MonitoringCommandResult = mutex.withLock {
        try {
            monitoringServiceGateway.start()
            MonitoringCommandResult(MonitoringCommandOutcome.RECOVERY_REQUESTED)
        } catch (_: RuntimeException) {
            markRecoveryRequiredLocked()
        }
    }

    override suspend fun markRecoveryRequired(): MonitoringCommandResult = mutex.withLock {
        markRecoveryRequiredLocked()
    }

    private suspend fun syncLocked(
        trigger: MobileSyncTrigger,
    ): MobileSyncCoordinationResult {
        val refreshResult = if (trigger.requiresCurrentBatteryReading) {
            refresher.refresh()
        } else {
            null
        }

        return syncAfterRefreshLocked(trigger, refreshResult)
    }

    private suspend fun markRecoveryRequiredLocked(): MonitoringCommandResult {
        monitoringStateUpdater.update(monitoringEnabled = false, resumeRequired = true)
        return MonitoringCommandResult(
            outcome = MonitoringCommandOutcome.START_FAILED,
            syncResult = syncLocked(MobileSyncTrigger.SETTINGS_CHANGED),
        )
    }

    private suspend fun syncAfterRefreshLocked(
        trigger: MobileSyncTrigger,
        refreshResult: BatteryRefreshResult?,
    ): MobileSyncCoordinationResult = when (refreshResult) {
        BatteryRefreshResult.InvalidInput -> MobileSyncCoordinationResult.Skipped(
            trigger = trigger,
            reason = SyncSkipReason.INVALID_BATTERY_INPUT,
        )

        BatteryRefreshResult.Unavailable -> MobileSyncCoordinationResult.Skipped(
            trigger = trigger,
            reason = SyncSkipReason.BATTERY_UNAVAILABLE,
        )

        BatteryRefreshResult.Unchanged -> MobileSyncCoordinationResult.Skipped(
            trigger = trigger,
            reason = SyncSkipReason.UNCHANGED_BATTERY_INPUT,
        )

        is BatteryRefreshResult.Refreshed,
        null -> {
            val notificationResult = mobileNotificationDeliverer.deliverPending()
            MobileSyncCoordinationResult.Sent(
                trigger = trigger,
                refreshResult = refreshResult,
                batchResult = sender.syncPending(),
                mobileNotificationResult = notificationResult,
            )
        }
    }
}

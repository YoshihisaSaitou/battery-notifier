package com.magicitengineer.batterynotifierandroidmobileapp.application.settings

import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinationResult
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.notification.MobileNotificationDisposition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ThresholdSettingsState(
    val thresholdPercent: Int = AlertRule.DEFAULT_THRESHOLD_PERCENT,
    val currentLevelPercent: Int? = null,
    val monitoringEnabled: Boolean = false,
    val resumeRequired: Boolean = false,
    val mobileNotificationDisposition: MobileNotificationDisposition =
        MobileNotificationDisposition.NONE,
    val notificationPermissionRequested: Boolean = false,
)

enum class ThresholdSaveRejectionReason {
    OUT_OF_RANGE,
}

sealed interface ThresholdSaveResult {
    data class Saved(
        val state: ThresholdSettingsState,
        val currentAtOrBelowThreshold: Boolean,
        val syncResult: MobileSyncCoordinationResult,
    ) : ThresholdSaveResult

    data class Rejected(
        val reason: ThresholdSaveRejectionReason,
    ) : ThresholdSaveResult
}

fun interface ThresholdSettingUpdater {
    suspend fun updateThreshold(thresholdPercent: Int): MobilePersistentState
}

fun interface ThresholdSettingsRunner {
    suspend fun saveThreshold(thresholdPercent: Int): ThresholdSaveResult
}

class RepositoryThresholdSettingUpdater(
    private val repository: MobileStateRepository,
) : ThresholdSettingUpdater {
    override suspend fun updateThreshold(thresholdPercent: Int): MobilePersistentState {
        val current = repository.state.first()
        return repository.updateAlertRule(
            current.alertRule.copy(thresholdPercent = thresholdPercent)
        )
    }
}

class ThresholdSettingsController(
    private val repository: MobileStateRepository,
    private val runner: ThresholdSettingsRunner,
) {
    val state: Flow<ThresholdSettingsState> = repository.state.map { it.toSettingsState() }

    suspend fun saveThreshold(thresholdPercent: Int): ThresholdSaveResult =
        runner.saveThreshold(thresholdPercent)

    suspend fun markNotificationPermissionRequested() {
        repository.markNotificationPermissionRequested()
    }
}

internal fun MobilePersistentState.toSettingsState(): ThresholdSettingsState =
    ThresholdSettingsState(
        thresholdPercent = alertRule.thresholdPercent,
        currentLevelPercent = lastSnapshot?.levelPercent,
        monitoringEnabled = alertRule.monitoringEnabled,
        resumeRequired = resumeRequired,
        mobileNotificationDisposition = mobileNotificationDisposition,
        notificationPermissionRequested = notificationPermissionRequested,
    )

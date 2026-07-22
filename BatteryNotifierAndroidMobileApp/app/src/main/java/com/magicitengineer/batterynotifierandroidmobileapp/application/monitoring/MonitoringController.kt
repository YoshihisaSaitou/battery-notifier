package com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring

import com.magicitengineer.batterynotifierandroidmobileapp.application.sync.MobileSyncCoordinationResult
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import kotlinx.coroutines.flow.first

enum class MonitoringCommandOutcome {
    STARTED,
    STOPPED,
    RECOVERY_REQUESTED,
    RECOVERY_NOT_NEEDED,
    START_FAILED,
}

data class MonitoringCommandResult(
    val outcome: MonitoringCommandOutcome,
    val syncResult: MobileSyncCoordinationResult? = null,
)

interface MonitoringRunner {
    suspend fun startMonitoring(): MonitoringCommandResult

    suspend fun stopMonitoring(): MonitoringCommandResult

    suspend fun resumeMonitoring(): MonitoringCommandResult

    suspend fun markRecoveryRequired(): MonitoringCommandResult
}

fun interface MonitoringStateUpdater {
    suspend fun update(
        monitoringEnabled: Boolean,
        resumeRequired: Boolean,
    ): MobilePersistentState
}

interface MonitoringServiceGateway {
    fun start()

    fun stop()
}

class RepositoryMonitoringStateUpdater(
    private val repository: MobileStateRepository,
) : MonitoringStateUpdater {
    override suspend fun update(
        monitoringEnabled: Boolean,
        resumeRequired: Boolean,
    ): MobilePersistentState = repository.updateMonitoringState(
        monitoringEnabled = monitoringEnabled,
        resumeRequired = resumeRequired,
    )
}

class MonitoringController(
    private val repository: MobileStateRepository,
    private val runner: MonitoringRunner,
) {
    suspend fun startMonitoring(): MonitoringCommandResult = runner.startMonitoring()

    suspend fun stopMonitoring(): MonitoringCommandResult = runner.stopMonitoring()

    suspend fun recoverIfNeeded(): MonitoringCommandResult =
        if (repository.state.first().alertRule.monitoringEnabled) {
            runner.resumeMonitoring()
        } else {
            MonitoringCommandResult(MonitoringCommandOutcome.RECOVERY_NOT_NEEDED)
        }

    suspend fun markRecoveryRequired(): MonitoringCommandResult =
        runner.markRecoveryRequired()
}

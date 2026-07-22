package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringCommandOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringCommandResult

enum class MonitoringCommandUiState {
    IDLE,
    STARTING,
    ACTIVE,
    STOPPING,
    STOPPED,
    START_FAILED,
}

fun MonitoringCommandResult.toMonitoringCommandUiState(): MonitoringCommandUiState =
    when (outcome) {
        MonitoringCommandOutcome.STARTED,
        MonitoringCommandOutcome.RECOVERY_REQUESTED -> MonitoringCommandUiState.ACTIVE

        MonitoringCommandOutcome.STOPPED -> MonitoringCommandUiState.STOPPED
        MonitoringCommandOutcome.START_FAILED -> MonitoringCommandUiState.START_FAILED
        MonitoringCommandOutcome.RECOVERY_NOT_NEEDED -> MonitoringCommandUiState.IDLE
    }

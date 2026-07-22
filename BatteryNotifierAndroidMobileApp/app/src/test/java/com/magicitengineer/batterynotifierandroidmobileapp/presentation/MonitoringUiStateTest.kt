package com.magicitengineer.batterynotifierandroidmobileapp.presentation

import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringCommandOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringCommandResult
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringUiStateTest {
    @Test
    fun monitoringOutcomesMapToStableUiStates() {
        val cases = mapOf(
            MonitoringCommandOutcome.STARTED to MonitoringCommandUiState.ACTIVE,
            MonitoringCommandOutcome.RECOVERY_REQUESTED to MonitoringCommandUiState.ACTIVE,
            MonitoringCommandOutcome.STOPPED to MonitoringCommandUiState.STOPPED,
            MonitoringCommandOutcome.START_FAILED to MonitoringCommandUiState.START_FAILED,
            MonitoringCommandOutcome.RECOVERY_NOT_NEEDED to MonitoringCommandUiState.IDLE,
        )

        cases.forEach { (outcome, expected) ->
            assertEquals(
                expected,
                MonitoringCommandResult(outcome).toMonitoringCommandUiState(),
            )
        }
    }
}

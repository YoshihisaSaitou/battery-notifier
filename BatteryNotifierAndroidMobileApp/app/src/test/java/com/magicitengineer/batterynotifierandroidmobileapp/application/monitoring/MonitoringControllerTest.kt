package com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.BatteryProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncDeliveryUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringControllerTest {
    @Test
    fun recoveryIsNotRequestedWhenMonitoringWasStopped() = runBlocking {
        val runner = RecordingRunner()
        val controller = MonitoringController(StateOnlyRepository(), runner)

        val result = controller.recoverIfNeeded()

        assertEquals(MonitoringCommandOutcome.RECOVERY_NOT_NEEDED, result.outcome)
        assertEquals(0, runner.resumeCalls)
    }

    @Test
    fun recoveryIsRequestedOnlyForPersistedActiveMonitoring() = runBlocking {
        val runner = RecordingRunner()
        val controller = MonitoringController(
            StateOnlyRepository(
                MobilePersistentState(alertRule = AlertRule(monitoringEnabled = true)),
            ),
            runner,
        )

        val result = controller.recoverIfNeeded()

        assertEquals(MonitoringCommandOutcome.RECOVERY_REQUESTED, result.outcome)
        assertEquals(1, runner.resumeCalls)
    }

    private class RecordingRunner : MonitoringRunner {
        var resumeCalls = 0

        override suspend fun startMonitoring() =
            MonitoringCommandResult(MonitoringCommandOutcome.STARTED)

        override suspend fun stopMonitoring() =
            MonitoringCommandResult(MonitoringCommandOutcome.STOPPED)

        override suspend fun resumeMonitoring(): MonitoringCommandResult {
            resumeCalls += 1
            return MonitoringCommandResult(MonitoringCommandOutcome.RECOVERY_REQUESTED)
        }

        override suspend fun markRecoveryRequired() =
            MonitoringCommandResult(MonitoringCommandOutcome.START_FAILED)
    }

    private class StateOnlyRepository(
        initial: MobilePersistentState = MobilePersistentState(),
    ) : MobileStateRepository {
        override val state: Flow<MobilePersistentState> = MutableStateFlow(initial)

        override suspend fun processBatteryReading(
            reading: BatteryReading,
            candidateEventId: String,
        ): BatteryProcessingResult = error("not used")

        override suspend fun updateAlertRule(rule: AlertRule): MobilePersistentState =
            error("not used")

        override suspend fun markMobileNotified(eventId: String): MobilePersistentState =
            error("not used")

        override suspend fun applySyncDelivery(update: SyncDeliveryUpdate): MobilePersistentState =
            error("not used")

        override suspend fun recordInvalidInput(): MobilePersistentState = error("not used")
    }
}

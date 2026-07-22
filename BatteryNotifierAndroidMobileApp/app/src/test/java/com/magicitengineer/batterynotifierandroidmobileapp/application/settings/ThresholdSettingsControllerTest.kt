package com.magicitengineer.batterynotifierandroidmobileapp.application.settings

import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.BatteryProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.notification.MobileNotificationDisposition
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncDeliveryUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ThresholdSettingsControllerTest {
    @Test
    fun stateFlowExposesOnlySettingsPresentationValues() = runBlocking {
        val repository = StateOnlyRepository(
            MobilePersistentState(
                alertRule = AlertRule(thresholdPercent = 35, monitoringEnabled = true),
                lastSnapshot = BatterySnapshot(67, false, 1_000L, 1L),
                sequence = 1L,
                mobileNotificationDisposition =
                    MobileNotificationDisposition.PERMISSION_DENIED,
            )
        )
        val controller = ThresholdSettingsController(repository) {
            error("save is not expected")
        }

        assertEquals(
            ThresholdSettingsState(
                thresholdPercent = 35,
                currentLevelPercent = 67,
                monitoringEnabled = true,
                resumeRequired = false,
                mobileNotificationDisposition =
                    MobileNotificationDisposition.PERMISSION_DENIED,
            ),
            controller.state.first(),
        )
    }

    @Test
    fun saveDelegatesExactThresholdToSerializedRunner() = runBlocking {
        var received: Int? = null
        val expected = ThresholdSaveResult.Rejected(ThresholdSaveRejectionReason.OUT_OF_RANGE)
        val controller = ThresholdSettingsController(StateOnlyRepository()) { threshold ->
            received = threshold
            expected
        }

        val actual = controller.saveThreshold(42)

        assertEquals(42, received)
        assertSame(expected, actual)
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

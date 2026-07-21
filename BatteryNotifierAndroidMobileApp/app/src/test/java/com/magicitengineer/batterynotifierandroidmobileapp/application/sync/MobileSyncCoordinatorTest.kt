package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryRefreshResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryStateRefresher
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.BatteryProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileSyncCoordinatorTest {
    @Test
    fun recoveryRequestManualAndRestoreRefreshBeforeSending() = runBlocking {
        val triggers = listOf(
            MobileSyncTrigger.CONNECTION_RECOVERED,
            MobileSyncTrigger.REQUEST_STATE,
            MobileSyncTrigger.MANUAL_SYNC,
            MobileSyncTrigger.PROCESS_RESTORED,
        )

        triggers.forEach { trigger ->
            val order = mutableListOf<String>()
            val coordinator = MobileSyncCoordinator(
                refresher = BatteryStateRefresher {
                    order += "refresh"
                    refreshedResult()
                },
                sender = PendingSyncSender {
                    order += "send"
                    emptyBatch()
                },
            )

            val result = coordinator.sync(trigger)

            assertTrue(result is MobileSyncCoordinationResult.Sent)
            assertEquals(listOf("refresh", "send"), order)
        }
    }

    @Test
    fun alreadyPersistedTriggersSendWithoutRecapturingBattery() = runBlocking {
        val triggers = listOf(
            MobileSyncTrigger.BATTERY_CHANGED,
            MobileSyncTrigger.SETTINGS_CHANGED,
        )

        triggers.forEach { trigger ->
            var refreshCalls = 0
            var sendCalls = 0
            val coordinator = MobileSyncCoordinator(
                refresher = BatteryStateRefresher {
                    refreshCalls += 1
                    refreshedResult()
                },
                sender = PendingSyncSender {
                    sendCalls += 1
                    emptyBatch()
                },
            )

            coordinator.sync(trigger)

            assertEquals(0, refreshCalls)
            assertEquals(1, sendCalls)
        }
    }

    @Test
    fun invalidOrUnavailableCurrentReadingDoesNotResendCachedState() = runBlocking {
        val cases = listOf(
            BatteryRefreshResult.InvalidInput to SyncSkipReason.INVALID_BATTERY_INPUT,
            BatteryRefreshResult.Unavailable to SyncSkipReason.BATTERY_UNAVAILABLE,
        )

        cases.forEach { (refreshResult, expectedReason) ->
            var sendCalls = 0
            val coordinator = MobileSyncCoordinator(
                refresher = BatteryStateRefresher { refreshResult },
                sender = PendingSyncSender {
                    sendCalls += 1
                    emptyBatch()
                },
            )

            val result = coordinator.sync(MobileSyncTrigger.CONNECTION_RECOVERED)

            assertEquals(0, sendCalls)
            assertEquals(
                expectedReason,
                (result as MobileSyncCoordinationResult.Skipped).reason,
            )
        }
    }

    @Test
    fun concurrentTriggersAreSerialized() = runBlocking {
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { refreshedResult() },
            sender = PendingSyncSender {
                val current = active.incrementAndGet()
                maximumActive.updateAndGet { maxOf(it, current) }
                delay(20)
                active.decrementAndGet()
                emptyBatch()
            },
        )

        val first = async { coordinator.sync(MobileSyncTrigger.MANUAL_SYNC) }
        val second = async { coordinator.sync(MobileSyncTrigger.REQUEST_STATE) }
        first.await()
        second.await()

        assertEquals(1, maximumActive.get())
    }

    private fun refreshedResult(): BatteryRefreshResult.Refreshed {
        val snapshot = BatterySnapshot(67, false, 1_000L, 1L)
        val state = MobilePersistentState(
            lastSnapshot = snapshot,
            sequence = 1L,
            pendingStateSequence = 1L,
        )
        return BatteryRefreshResult.Refreshed(
            BatteryProcessingResult(state, snapshot, null)
        )
    }

    private fun emptyBatch(): MobileSyncBatchResult = MobileSyncBatchResult(
        stateOutcome = SyncItemOutcome.NotPending,
        eventOutcome = SyncItemOutcome.NotPending,
        persistedState = MobilePersistentState(),
    )
}

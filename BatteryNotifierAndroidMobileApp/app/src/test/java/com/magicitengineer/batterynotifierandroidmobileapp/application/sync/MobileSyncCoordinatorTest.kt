package com.magicitengineer.batterynotifierandroidmobileapp.application.sync

import androidx.datastore.core.DataStore
import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryRefreshResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryReadResultProcessor
import com.magicitengineer.batterynotifierandroidmobileapp.application.battery.BatteryStateRefresher
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringCommandOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringServiceGateway
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringStateUpdater
import com.magicitengineer.batterynotifierandroidmobileapp.application.monitoring.MonitoringStartBaselineResetter
import com.magicitengineer.batterynotifierandroidmobileapp.application.notification.MobileNotificationDeliveryResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.notification.PendingMobileNotificationDeliverer
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSaveRejectionReason
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSaveResult
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.ThresholdSettingUpdater
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.RepositoryWearThresholdChangeProcessor
import com.magicitengineer.batterynotifierandroidmobileapp.application.settings.WearThresholdChangeProcessor
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.BatteryProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.MobileStateSanitizer
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.ProtoMobileStateRepository
import com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.proto.MobileStateProto
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.AlertRule
import com.magicitengineer.batterynotifierandroidmobileapp.domain.alert.ThresholdReachedEvent
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReadResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatteryReading
import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeProcessingResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeProcessingOutcome
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeRequest
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.settings.ThresholdChangeResultCode
import com.magicitengineer.batterynotifierandroidmobileapp.domain.state.MobilePersistentState
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.DataLayerPutResult
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.EpochMillisClock
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.MobileSyncGateway
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.PhoneStateSync
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncDeliveryUpdate
import com.magicitengineer.batterynotifierandroidmobileapp.domain.sync.SyncFailureClassification
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun mobileNotificationDeliveryPrecedesIndependentDataLayerOutboxDelivery() = runBlocking {
        val order = mutableListOf<String>()
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { refreshedResult() },
            sender = PendingSyncSender {
                order += "data-layer"
                emptyBatch()
            },
            mobileNotificationDeliverer = PendingMobileNotificationDeliverer {
                order += "mobile-notification"
                MobileNotificationDeliveryResult.NotPending
            },
        )

        coordinator.sync(MobileSyncTrigger.BATTERY_CHANGED)

        assertEquals(listOf("mobile-notification", "data-layer"), order)
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

    @Test
    fun batteryCallbackPersistsProvidedReadingBeforeSendingWithoutStickyRefresh() = runBlocking {
        val order = mutableListOf<String>()
        val reading = BatteryReading(42, false, 2_000L)
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { error("must not re-read sticky battery state") },
            sender = PendingSyncSender {
                order += "send"
                emptyBatch()
            },
            batteryReadResultProcessor = BatteryReadResultProcessor { result ->
                assertEquals(BatteryReadResult.Available(reading), result)
                order += "persist"
                refreshedResult()
            },
        )

        val result = coordinator.processBatteryChange(BatteryReadResult.Available(reading))

        assertEquals(listOf("persist", "send"), order)
        assertEquals(
            MobileSyncTrigger.BATTERY_CHANGED,
            (result as MobileSyncCoordinationResult.Sent).trigger,
        )
    }

    @Test
    fun invalidBatteryCallbackDoesNotSendCachedState() = runBlocking {
        var sendCalls = 0
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { error("must not refresh") },
            sender = PendingSyncSender {
                sendCalls += 1
                emptyBatch()
            },
            batteryReadResultProcessor = BatteryReadResultProcessor {
                BatteryRefreshResult.InvalidInput
            },
        )

        val result = coordinator.processBatteryChange(BatteryReadResult.Invalid)

        assertEquals(0, sendCalls)
        assertEquals(
            SyncSkipReason.INVALID_BATTERY_INPUT,
            (result as MobileSyncCoordinationResult.Skipped).reason,
        )
    }

    @Test
    fun thirtyConcurrentBatteryCallbacksShareOneProcessingAndSendBoundary() = runBlocking {
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val processedCount = AtomicInteger()
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { error("must not refresh") },
            sender = PendingSyncSender {
                processedCount.incrementAndGet()
                emptyBatch()
            },
            batteryReadResultProcessor = BatteryReadResultProcessor {
                val current = active.incrementAndGet()
                maximumActive.updateAndGet { maximum -> maxOf(maximum, current) }
                delay(2)
                active.decrementAndGet()
                refreshedResult()
            },
        )

        (1..30)
            .map { level ->
                async {
                    coordinator.processBatteryChange(
                        BatteryReadResult.Available(BatteryReading(level, false, level.toLong())),
                    )
                }
            }
            .forEach { it.await() }

        assertEquals(1, maximumActive.get())
        assertEquals(30, processedCount.get())
    }

    @Test
    fun unchangedBatteryCallbackDoesNotSendCachedState() = runBlocking {
        var sendCalls = 0
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { error("must not refresh") },
            sender = PendingSyncSender {
                sendCalls += 1
                emptyBatch()
            },
            batteryReadResultProcessor = BatteryReadResultProcessor {
                BatteryRefreshResult.Unchanged
            },
        )

        val result = coordinator.processBatteryChange(
            BatteryReadResult.Available(BatteryReading(42, false, 2_000L)),
        )

        assertEquals(0, sendCalls)
        assertEquals(
            SyncSkipReason.UNCHANGED_BATTERY_INPUT,
            (result as MobileSyncCoordinationResult.Skipped).reason,
        )
    }

    @Test
    fun monitoringStartPersistsThenStartsServiceThenSyncs() = runBlocking {
        val order = mutableListOf<String>()
        val coordinator = monitoringCoordinator(order)

        val result = coordinator.startMonitoring()

        assertEquals(listOf("refresh", "persist:true:false", "start", "send"), order)
        assertEquals(MonitoringCommandOutcome.STARTED, result.outcome)
    }

    @Test
    fun rejectedMonitoringStartPersistsResumeRequiredAndSyncsStoppedState() = runBlocking {
        val order = mutableListOf<String>()
        val coordinator = monitoringCoordinator(order, failStart = true)

        val result = coordinator.startMonitoring()

        assertEquals(
            listOf("refresh", "persist:true:false", "start", "persist:false:true", "send"),
            order,
        )
        assertEquals(MonitoringCommandOutcome.START_FAILED, result.outcome)
    }

    @Test
    fun unavailableReadingClearsStaleStartBaselineBeforeEnablingMonitoring() = runBlocking {
        val order = mutableListOf<String>()
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher {
                order += "refresh-unavailable"
                BatteryRefreshResult.Unavailable
            },
            sender = PendingSyncSender {
                order += "send"
                emptyBatch()
            },
            monitoringStartBaselineResetter = MonitoringStartBaselineResetter {
                order += "reset-baseline"
                MobilePersistentState()
            },
            monitoringStateUpdater = MonitoringStateUpdater { enabled, resumeRequired ->
                order += "persist:$enabled:$resumeRequired"
                MobilePersistentState(
                    alertRule = AlertRule(monitoringEnabled = enabled),
                    resumeRequired = resumeRequired,
                )
            },
            monitoringServiceGateway = object : MonitoringServiceGateway {
                override fun start() { order += "start" }
                override fun stop() = Unit
            },
        )

        coordinator.startMonitoring()

        assertEquals(
            listOf(
                "refresh-unavailable",
                "reset-baseline",
                "persist:true:false",
                "start",
                "send",
            ),
            order,
        )
    }

    @Test
    fun monitoringStopPersistsBeforeStoppingServiceAndSyncing() = runBlocking {
        val order = mutableListOf<String>()
        val coordinator = monitoringCoordinator(order)

        val result = coordinator.stopMonitoring()

        assertEquals(listOf("persist:false:false", "stop", "send"), order)
        assertEquals(MonitoringCommandOutcome.STOPPED, result.outcome)
    }

    @Test
    fun thresholdSavePersistsBeforeOneSettingsSync() = runBlocking {
        val order = mutableListOf<String>()
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { refreshedResult() },
            sender = PendingSyncSender {
                order += "send"
                emptyBatch()
            },
            thresholdSettingUpdater = ThresholdSettingUpdater { threshold ->
                order += "persist:$threshold"
                stateWithThreshold(threshold, level = 18)
            },
        )

        val result = coordinator.saveThreshold(20) as ThresholdSaveResult.Saved

        assertEquals(listOf("persist:20", "send"), order)
        assertEquals(20, result.state.thresholdPercent)
        assertTrue(result.currentAtOrBelowThreshold)
        assertEquals(
            MobileSyncTrigger.SETTINGS_CHANGED,
            (result.syncResult as MobileSyncCoordinationResult.Sent).trigger,
        )
    }

    @Test
    fun outOfRangeThresholdDoesNotPersistOrSend() = runBlocking {
        var persistCalls = 0
        var sendCalls = 0
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { refreshedResult() },
            sender = PendingSyncSender {
                sendCalls += 1
                emptyBatch()
            },
            thresholdSettingUpdater = ThresholdSettingUpdater {
                persistCalls += 1
                stateWithThreshold(20, level = 18)
            },
        )

        val result = coordinator.saveThreshold(4)

        assertEquals(
            ThresholdSaveRejectionReason.OUT_OF_RANGE,
            (result as ThresholdSaveResult.Rejected).reason,
        )
        assertEquals(0, persistCalls)
        assertEquals(0, sendCalls)
    }

    @Test
    fun thresholdSaveAndRuntimeSyncShareOneSerializationBoundary() = runBlocking {
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        suspend fun observeWork() {
            val current = active.incrementAndGet()
            maximumActive.updateAndGet { maxOf(it, current) }
            delay(20)
            active.decrementAndGet()
        }
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { refreshedResult() },
            sender = PendingSyncSender {
                observeWork()
                emptyBatch()
            },
            thresholdSettingUpdater = ThresholdSettingUpdater { threshold ->
                observeWork()
                stateWithThreshold(threshold, level = 67)
            },
        )

        val save = async { coordinator.saveThreshold(25) }
        val sync = async { coordinator.sync(MobileSyncTrigger.MANUAL_SYNC) }
        save.await()
        sync.await()

        assertEquals(1, maximumActive.get())
    }

    @Test
    fun replayedAppliedResultRetriesAPendingStateOutboxWithoutChangingSequence() =
        runBlocking {
            var sendCalls = 0
            val persisted = MobilePersistentState(
                alertRule = AlertRule(thresholdPercent = 15),
                lastSnapshot = BatterySnapshot(67, false, 1_000L, 12L),
                sequence = 12L,
                pendingStateSequence = 12L,
            )
            val result = ThresholdChangeResult(
                requestId = THRESHOLD_REQUEST_ID,
                resultCode = ThresholdChangeResultCode.APPLIED,
                effectiveThresholdPercent = 15,
                phoneStateSequence = 12L,
            )
            val coordinator = MobileSyncCoordinator(
                refresher = BatteryStateRefresher { error("must not refresh") },
                sender = PendingSyncSender {
                    sendCalls += 1
                    emptyBatch()
                },
                wearThresholdChangeProcessor = WearThresholdChangeProcessor {
                    ThresholdChangeProcessingResult(
                        state = persisted,
                        result = result,
                        replayed = true,
                        settingChanged = false,
                    )
                },
            )
            val request = ThresholdChangeRequest(
                schemaVersion = 1,
                requestId = THRESHOLD_REQUEST_ID,
                thresholdPercent = 15,
                expectedThresholdPercent = 20,
            )

            val coordination = coordinator.applyWearThresholdChange(request)

            assertEquals(1, sendCalls)
            assertEquals(12L, coordination.processingResult.state.sequence)
            assertEquals(12L, coordination.processingResult.state.pendingStateSequence)
            assertTrue(coordination.syncResult is MobileSyncCoordinationResult.Sent)
        }

    @Test
    fun resultSuccessAfterStateFailureRetriesTheSameRequestWithoutNewSequenceOrEvent() =
        runBlocking {
            val store = InMemoryDataStore(MobileStateSanitizer.defaultValue())
            val repository = ProtoMobileStateRepository(store)
            repository.processBatteryReading(
                BatteryReading(55, isCharging = false, capturedAtEpochMillis = 1_000L),
                THRESHOLD_REQUEST_ID,
            )
            repository.applySyncDelivery(
                SyncDeliveryUpdate(
                    confirmedStateSequence = 1L,
                    completedAtEpochMillis = 1_100L,
                )
            )
            val syncGateway = SequencedSyncGateway(
                DataLayerPutResult.Rejected(SyncFailureClassification.TASK_FAILURE),
                DataLayerPutResult.Accepted,
            )
            var now = 2_000L
            val coordinator = MobileSyncCoordinator(
                refresher = BatteryStateRefresher { error("must not refresh") },
                sender = MobileDataLayerSender(
                    repository = repository,
                    gateway = syncGateway,
                    clock = EpochMillisClock { now++ },
                ),
                wearThresholdChangeProcessor =
                    RepositoryWearThresholdChangeProcessor(repository),
            )
            val sentResults = mutableListOf<ThresholdChangeResult>()
            val handler = WearThresholdChangeHandler(
                runner = coordinator,
                resultGateway = ThresholdChangeResultGateway { _, result ->
                    sentResults += result
                    ThresholdChangeResultSendOutcome.SENT
                },
            )
            val request = ThresholdChangeRequest(
                schemaVersion = 1,
                requestId = THRESHOLD_REQUEST_ID,
                thresholdPercent = 15,
                expectedThresholdPercent = 20,
            )

            val first = handler.handle("wear-node", request)
            val afterFailure = repository.state.first()
            val second = handler.handle("wear-node", request)
            val afterRetry = repository.state.first()

            assertTrue(first.coordinationResult.processingResult.settingChanged)
            assertTrue(
                (first.coordinationResult.syncResult as MobileSyncCoordinationResult.Sent)
                    .batchResult.stateOutcome is SyncItemOutcome.Rejected
            )
            assertEquals(2L, afterFailure.sequence)
            assertEquals(2L, afterFailure.pendingStateSequence)
            assertNull(afterFailure.pendingEvent)
            assertTrue(second.coordinationResult.processingResult.replayed)
            assertFalse(second.coordinationResult.processingResult.settingChanged)
            assertTrue(
                (second.coordinationResult.syncResult as MobileSyncCoordinationResult.Sent)
                    .batchResult.stateOutcome is SyncItemOutcome.Accepted
            )
            assertEquals(2L, afterRetry.sequence)
            assertEquals(0L, afterRetry.pendingStateSequence)
            assertNull(afterRetry.pendingEvent)
            assertEquals(2, sentResults.size)
            assertEquals(sentResults[0], sentResults[1])
            assertEquals(2, syncGateway.phoneStates.size)
            assertEquals(0, syncGateway.eventCalls)
        }

    @Test
    fun noSnapshotRequestRefreshesBeforeApplyingAndReturnsAPositiveSequence() = runBlocking {
        val repository = ProtoMobileStateRepository(
            InMemoryDataStore(MobileStateSanitizer.defaultValue())
        )
        var refreshCalls = 0
        var syncCalls = 0
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher {
                refreshCalls += 1
                BatteryRefreshResult.Refreshed(
                    repository.processBatteryReading(
                        BatteryReading(55, isCharging = false, capturedAtEpochMillis = 1_000L),
                        THRESHOLD_REQUEST_ID,
                    )
                )
            },
            sender = PendingSyncSender {
                syncCalls += 1
                emptyBatch()
            },
            wearThresholdChangeProcessor = RepositoryWearThresholdChangeProcessor(repository),
        )

        val coordination = coordinator.applyWearThresholdChange(
            ThresholdChangeRequest(
                schemaVersion = 1,
                requestId = THRESHOLD_REQUEST_ID,
                thresholdPercent = 15,
                expectedThresholdPercent = 20,
            )
        )

        assertEquals(1, refreshCalls)
        assertEquals(1, syncCalls)
        assertEquals(ThresholdChangeProcessingOutcome.PROCESSED, coordination.processingResult.outcome)
        assertTrue(coordination.processingResult.settingChanged)
        assertEquals(2L, coordination.processingResult.result?.phoneStateSequence)
        assertEquals(2L, coordination.processingResult.state.sequence)
        assertEquals(2L, coordination.processingResult.state.pendingStateSequence)
        assertEquals(15, coordination.processingResult.state.alertRule.thresholdPercent)
        assertNull(coordination.processingResult.state.pendingEvent)
    }

    @Test
    fun unavailableInitialBatteryDefersRequestWithoutApplyingOrSendingAResult() = runBlocking {
        val repository = ProtoMobileStateRepository(
            InMemoryDataStore(MobileStateSanitizer.defaultValue())
        )
        var resultSendCalls = 0
        val coordinator = MobileSyncCoordinator(
            refresher = BatteryStateRefresher { BatteryRefreshResult.Unavailable },
            sender = PendingSyncSender { error("must not sync without a phone state") },
            wearThresholdChangeProcessor = RepositoryWearThresholdChangeProcessor(repository),
        )
        val handler = WearThresholdChangeHandler(
            runner = coordinator,
            resultGateway = ThresholdChangeResultGateway { _, _ ->
                resultSendCalls += 1
                ThresholdChangeResultSendOutcome.SENT
            },
        )

        val handled = handler.handle(
            sourceNodeId = "wear-node",
            request = ThresholdChangeRequest(
                schemaVersion = 1,
                requestId = THRESHOLD_REQUEST_ID,
                thresholdPercent = 15,
                expectedThresholdPercent = 20,
            ),
        )

        assertEquals(
            ThresholdChangeProcessingOutcome.PHONE_STATE_UNAVAILABLE,
            handled.coordinationResult.processingResult.outcome,
        )
        assertEquals(
            ThresholdChangeResultSendOutcome.NOT_SENT_PHONE_STATE_UNAVAILABLE,
            handled.sendOutcome,
        )
        assertEquals(0, resultSendCalls)
        assertNull(handled.coordinationResult.processingResult.result)
        assertFalse(handled.coordinationResult.processingResult.settingChanged)
        assertEquals(20, handled.coordinationResult.processingResult.state.alertRule.thresholdPercent)
        assertEquals(0L, handled.coordinationResult.processingResult.state.sequence)
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

    private fun stateWithThreshold(threshold: Int, level: Int): MobilePersistentState {
        val snapshot = BatterySnapshot(level, false, 1_000L, 1L)
        return MobilePersistentState(
            alertRule = AlertRule(thresholdPercent = threshold),
            lastSnapshot = snapshot,
            sequence = 1L,
            pendingStateSequence = 1L,
        )
    }

    private fun monitoringCoordinator(
        order: MutableList<String>,
        failStart: Boolean = false,
    ): MobileSyncCoordinator = MobileSyncCoordinator(
        refresher = BatteryStateRefresher {
            order += "refresh"
            refreshedResult()
        },
        sender = PendingSyncSender {
            order += "send"
            emptyBatch()
        },
        monitoringStateUpdater = MonitoringStateUpdater { enabled, resumeRequired ->
            order += "persist:$enabled:$resumeRequired"
            MobilePersistentState(
                alertRule = AlertRule(monitoringEnabled = enabled),
                resumeRequired = resumeRequired,
            )
        },
        monitoringStartBaselineResetter = MonitoringStartBaselineResetter {
            order += "reset-baseline"
            MobilePersistentState()
        },
        monitoringServiceGateway = object : MonitoringServiceGateway {
            override fun start() {
                order += "start"
                if (failStart) error("FGS start rejected")
            }

            override fun stop() {
                order += "stop"
            }
        },
    )

    private class SequencedSyncGateway(
        vararg phoneStateResults: DataLayerPutResult,
    ) : MobileSyncGateway {
        private val results = ArrayDeque(phoneStateResults.toList())
        val phoneStates = mutableListOf<PhoneStateSync>()
        var eventCalls = 0

        override suspend fun putPhoneState(state: PhoneStateSync): DataLayerPutResult {
            phoneStates += state
            return results.removeFirst()
        }

        override suspend fun putThresholdEvent(
            event: ThresholdReachedEvent,
        ): DataLayerPutResult {
            eventCalls += 1
            return DataLayerPutResult.Accepted
        }
    }

    private class InMemoryDataStore<T>(initial: T) : DataStore<T> {
        private val values = MutableStateFlow(initial)
        private val mutex = Mutex()

        override val data: Flow<T> = values

        override suspend fun updateData(transform: suspend (t: T) -> T): T =
            mutex.withLock {
                transform(values.value).also { values.value = it }
            }
    }

    private companion object {
        const val THRESHOLD_REQUEST_ID = "550e8400-e29b-41d4-a716-446655440002"
    }
}

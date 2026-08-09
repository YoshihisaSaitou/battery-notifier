package com.magicitengineer.batterynotifierandroidwearapp.presentation

import com.magicitengineer.batterynotifierandroidwearapp.application.settings.ThresholdDraftCommandQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ThresholdDraftEditSessionTest {
    @Test
    fun rapidPositiveEventsUseLatestAcceptedDraftAndSaveTwentyTwoOnce() = runBlocking {
        val persistedDrafts = mutableListOf<Int>()
        val sentThresholds = mutableListOf<Int>()
        val queue = ThresholdDraftCommandQueue(
            scope = this,
            persistDraftAction = { persistedDrafts += it },
            sendSaveRequest = { value ->
                persistedDrafts += value
                sentThresholds += value
            },
            cancelDraft = {},
        )
        try {
            val editor = editingState(initialThresholdPercent = 20, queue = queue)

            editor.stepBy(1)
            editor.stepBy(1)

            assertEquals(22, editor.draftPercent)
            assertEquals(emptyList<Int>(), sentThresholds)

            editor.save()
            queue.awaitIdle()

            assertEquals(listOf(21, 22, 22), persistedDrafts)
            assertEquals(listOf(22), sentThresholds)
        } finally {
            queue.closeAndJoin()
        }
    }

    @Test
    fun editorStopsAtSupportedBoundsWithoutEnqueuingDuplicateValues() = runBlocking {
        val persistedDrafts = mutableListOf<Int>()
        val queue = ThresholdDraftCommandQueue(
            scope = this,
            persistDraftAction = { persistedDrafts += it },
            sendSaveRequest = {},
            cancelDraft = {},
        )
        try {
            val editor = editingState(
                initialThresholdPercent = MIN_THRESHOLD_PERCENT,
                queue = queue,
            )

            editor.stepBy(-1)
            editor.stepBy(1)
            queue.awaitIdle()

            assertEquals(6, editor.draftPercent)
            assertEquals(listOf(6), persistedDrafts)
        } finally {
            queue.closeAndJoin()
        }
    }

    @Test
    fun editorAtUpperBoundIgnoresIncreaseAndPersistsSingleDecreaseWithoutSending() = runBlocking {
        val persistedDrafts = mutableListOf<Int>()
        val sentThresholds = mutableListOf<Int>()
        val queue = ThresholdDraftCommandQueue(
            scope = this,
            persistDraftAction = { persistedDrafts += it },
            sendSaveRequest = { sentThresholds += it },
            cancelDraft = {},
        )
        try {
            val editor = editingState(
                initialThresholdPercent = MAX_THRESHOLD_PERCENT,
                queue = queue,
            )

            editor.stepBy(1)
            queue.awaitIdle()

            assertEquals(MAX_THRESHOLD_PERCENT, editor.draftPercent)
            assertEquals(emptyList<Int>(), persistedDrafts)
            assertEquals(emptyList<Int>(), sentThresholds)

            editor.stepBy(-1)
            queue.awaitIdle()

            assertEquals(99, editor.draftPercent)
            assertEquals(listOf(99), persistedDrafts)
            assertEquals(emptyList<Int>(), sentThresholds)
        } finally {
            queue.closeAndJoin()
        }
    }

    @Test
    fun recreationRestoresSuspendedFinalDraftWithoutAutoSending() = runBlocking {
        val workerJob = SupervisorJob()
        val applicationScope = CoroutineScope(coroutineContext + workerJob)
        val firstPersistStarted = CompletableDeferred<Unit>()
        val releaseFirstPersist = CompletableDeferred<Unit>()
        val persistedDrafts = mutableListOf<Int>()
        val sentThresholds = mutableListOf<Int>()
        var isFirstPersist = true
        val queue = ThresholdDraftCommandQueue(
            scope = applicationScope,
            persistDraftAction = { value ->
                if (isFirstPersist) {
                    isFirstPersist = false
                    firstPersistStarted.complete(Unit)
                    releaseFirstPersist.await()
                }
                persistedDrafts += value
            },
            sendSaveRequest = { sentThresholds += it },
            cancelDraft = {},
        )
        try {
            val originalEditor = editingState(initialThresholdPercent = 20, queue = queue)
            originalEditor.stepBy(1)
            originalEditor.stepBy(1)
            firstPersistStarted.await()

            val recreatedEditor = ThresholdDraftEditorState(
                initialSnapshot = originalEditor.snapshot(),
                commandSink = queue,
            )
            recreatedEditor.reconcileRestoredDraft()

            assertEquals(true, recreatedEditor.isEditing)
            assertEquals(22, recreatedEditor.draftPercent)
            assertEquals(emptyList<Int>(), sentThresholds)

            releaseFirstPersist.complete(Unit)
            queue.awaitIdle()

            assertEquals(listOf(21, 22, 22), persistedDrafts)
            assertEquals(emptyList<Int>(), sentThresholds)
        } finally {
            releaseFirstPersist.complete(Unit)
            queue.closeAndJoin()
            applicationScope.cancel()
        }
    }

    @Test
    fun passiveProcessRecreationReconcilesRestoredDraftWithoutSendingOrChangingRequestState() =
        runBlocking {
            val originalWorkerJob = SupervisorJob()
            val originalApplicationScope = CoroutineScope(coroutineContext + originalWorkerJob)
            val firstPersistStarted = CompletableDeferred<Unit>()
            val releaseFirstPersist = CompletableDeferred<Unit>()
            var durableDraftPercent = 20
            var durablePendingRequestId: String? = null
            var durableStatus = "IDLE"
            val originalQueue = ThresholdDraftCommandQueue(
                scope = originalApplicationScope,
                persistDraftAction = {
                    firstPersistStarted.complete(Unit)
                    releaseFirstPersist.await()
                    durableDraftPercent = it
                },
                sendSaveRequest = { error("Passive recreation must not send") },
                cancelDraft = {},
            )
            val originalEditor = editingState(initialThresholdPercent = 20, queue = originalQueue)
            originalEditor.stepBy(1)
            originalEditor.stepBy(1)
            firstPersistStarted.await()
            val savedSnapshot = originalEditor.snapshot()

            originalApplicationScope.cancel()
            releaseFirstPersist.complete(Unit)
            originalQueue.closeAndJoin()

            val sentThresholds = mutableListOf<Int>()
            val restoredQueue = ThresholdDraftCommandQueue(
                scope = this,
                persistDraftAction = { durableDraftPercent = it },
                sendSaveRequest = { sentThresholds += it },
                cancelDraft = {},
            )
            try {
                val restoredEditor = ThresholdDraftEditorState(
                    initialSnapshot = savedSnapshot,
                    commandSink = restoredQueue,
                )

                restoredEditor.reconcileRestoredDraft()
                restoredQueue.awaitIdle()

                assertEquals(true, restoredEditor.isEditing)
                assertEquals(22, restoredEditor.draftPercent)
                assertEquals(22, durableDraftPercent)
                assertEquals(emptyList<Int>(), sentThresholds)
                assertEquals(null, durablePendingRequestId)
                assertEquals("IDLE", durableStatus)
            } finally {
                restoredQueue.closeAndJoin()
            }
        }

    @Test
    fun saveTappedBeforeRecreationSurvivesSuspendedPersistAndSendsOnce() = runBlocking {
        val workerJob = SupervisorJob()
        val applicationScope = CoroutineScope(coroutineContext + workerJob)
        val firstPersistStarted = CompletableDeferred<Unit>()
        val releaseFirstPersist = CompletableDeferred<Unit>()
        val sentThresholds = mutableListOf<Int>()
        var requestPrepared = false
        var isFirstPersist = true
        val queue = ThresholdDraftCommandQueue(
            scope = applicationScope,
            persistDraftAction = {
                if (isFirstPersist) {
                    isFirstPersist = false
                    firstPersistStarted.complete(Unit)
                    releaseFirstPersist.await()
                }
            },
            sendSaveRequest = { value ->
                if (!requestPrepared) {
                    requestPrepared = true
                    sentThresholds += value
                }
            },
            cancelDraft = {},
        )
        try {
            val originalEditor = editingState(initialThresholdPercent = 20, queue = queue)
            originalEditor.stepBy(1)
            originalEditor.stepBy(1)
            originalEditor.save()
            firstPersistStarted.await()

            val recreatedEditor = ThresholdDraftEditorState(
                initialSnapshot = originalEditor.snapshot(),
                commandSink = queue,
            )

            assertEquals(false, recreatedEditor.isEditing)
            assertEquals(null, recreatedEditor.draftPercent)
            assertEquals(22, recreatedEditor.pendingSavePercent)
            recreatedEditor.replayPendingSave()

            releaseFirstPersist.complete(Unit)
            queue.awaitIdle()

            assertEquals(listOf(22), sentThresholds)
            recreatedEditor.acknowledgePendingSave()
            assertEquals(null, recreatedEditor.snapshot().pendingSavePercent)
        } finally {
            releaseFirstPersist.complete(Unit)
            queue.closeAndJoin()
            applicationScope.cancel()
        }
    }

    @Test
    fun savedExplicitSaveIntentReplaysOnceAfterProcessScopeCancellation() = runBlocking {
        val originalWorkerJob = SupervisorJob()
        val originalApplicationScope = CoroutineScope(coroutineContext + originalWorkerJob)
        val firstPersistStarted = CompletableDeferred<Unit>()
        val releaseFirstPersist = CompletableDeferred<Unit>()
        val originalQueue = ThresholdDraftCommandQueue(
            scope = originalApplicationScope,
            persistDraftAction = {
                firstPersistStarted.complete(Unit)
                releaseFirstPersist.await()
            },
            sendSaveRequest = { error("Save must not run after process scope cancellation") },
            cancelDraft = {},
        )
        val originalEditor = editingState(initialThresholdPercent = 20, queue = originalQueue)
        originalEditor.stepBy(1)
        originalEditor.stepBy(1)
        originalEditor.save()
        firstPersistStarted.await()
        val savedSnapshot = originalEditor.snapshot()

        originalApplicationScope.cancel()
        releaseFirstPersist.complete(Unit)
        originalQueue.closeAndJoin()

        val persistedDrafts = mutableListOf<Int>()
        val sentThresholds = mutableListOf<Int>()
        val restoredQueue = ThresholdDraftCommandQueue(
            scope = this,
            persistDraftAction = { persistedDrafts += it },
            sendSaveRequest = { sentThresholds += it },
            cancelDraft = {},
        )
        try {
            val restoredEditor = ThresholdDraftEditorState(
                initialSnapshot = savedSnapshot,
                commandSink = restoredQueue,
            )

            restoredEditor.reconcileRestoredDraft()
            restoredEditor.replayPendingSave()
            restoredQueue.awaitIdle()

            assertEquals(emptyList<Int>(), persistedDrafts)
            assertEquals(listOf(22), sentThresholds)
            restoredEditor.acknowledgePendingSave()
            assertEquals(null, restoredEditor.snapshot().pendingSavePercent)
        } finally {
            restoredQueue.closeAndJoin()
        }
    }

    private fun editingState(
        initialThresholdPercent: Int,
        queue: ThresholdDraftCommandQueue,
    ) = ThresholdDraftEditorState(
        initialSnapshot = ThresholdDraftEditorSnapshot(
            isEditing = true,
            draftPercent = initialThresholdPercent,
        ),
        commandSink = queue,
    )
}

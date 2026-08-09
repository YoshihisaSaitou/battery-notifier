package com.magicitengineer.batterynotifierandroidwearapp.application.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal interface ThresholdDraftCommandSink {
    fun persistDraft(thresholdPercent: Int)

    fun save(thresholdPercent: Int)

    fun cancel()
}

/** Processes accepted editor commands in order from an application-owned scope. */
internal class ThresholdDraftCommandQueue(
    scope: CoroutineScope,
    private val persistDraftAction: suspend (Int) -> Unit,
    private val sendSaveRequest: suspend (Int) -> Unit,
    private val cancelDraft: suspend () -> Unit,
) : ThresholdDraftCommandSink {
    private sealed interface Command {
        data class PersistDraft(val thresholdPercent: Int) : Command

        data class Save(val thresholdPercent: Int) : Command

        data object Cancel : Command

        data class Barrier(val completed: CompletableDeferred<Unit>) : Command
    }

    private val commands = Channel<Command>(capacity = Channel.UNLIMITED)
    private val worker: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        for (command in commands) {
            when (command) {
                is Command.PersistDraft -> persistDraftAction(command.thresholdPercent)
                is Command.Save -> sendSaveRequest(command.thresholdPercent)
                Command.Cancel -> cancelDraft()
                is Command.Barrier -> command.completed.complete(Unit)
            }
        }
    }

    override fun persistDraft(thresholdPercent: Int) {
        enqueue(Command.PersistDraft(thresholdPercent))
    }

    override fun save(thresholdPercent: Int) {
        enqueue(Command.Save(thresholdPercent))
    }

    override fun cancel() {
        enqueue(Command.Cancel)
    }

    internal suspend fun awaitIdle() {
        val completed = CompletableDeferred<Unit>()
        enqueue(Command.Barrier(completed))
        completed.await()
    }

    internal suspend fun closeAndJoin() {
        commands.close()
        worker.join()
    }

    private fun enqueue(command: Command) {
        check(commands.trySend(command).isSuccess) {
            "Threshold draft command queue is closed"
        }
    }
}

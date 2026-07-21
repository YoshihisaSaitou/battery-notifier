package com.magicitengineer.batterynotifierandroidmobileapp.domain.alert

import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot

object ThresholdEvaluator {
    fun evaluate(
        rule: AlertRule,
        state: AlertState,
        snapshot: BatterySnapshot,
        candidateEventId: String,
    ): AlertEvaluation {
        if (!rule.monitoringEnabled) {
            return noEvent(state.copy(previousLevelPercent = snapshot.levelPercent))
        }

        val previousLevel = state.previousLevelPercent
        if (previousLevel == null) {
            if (snapshot.isCharging) {
                return noEvent(
                    state.copy(
                        armed = snapshot.levelPercent >= rule.rearmLevelPercent,
                        previousLevelPercent = snapshot.levelPercent,
                    )
                )
            }
            if (snapshot.levelPercent <= rule.thresholdPercent) {
                return if (rule.notifyIfAlreadyBelowOnStart && state.armed) {
                    trigger(rule, state, snapshot, candidateEventId)
                } else {
                    noEvent(state.copy(armed = false, previousLevelPercent = snapshot.levelPercent))
                }
            }
            return noEvent(state.copy(armed = true, previousLevelPercent = snapshot.levelPercent))
        }

        if (snapshot.isCharging) {
            return noEvent(
                state.copy(
                    armed = state.armed || snapshot.levelPercent >= rule.rearmLevelPercent,
                    previousLevelPercent = snapshot.levelPercent,
                )
            )
        }

        if (!state.armed) {
            return noEvent(
                state.copy(
                    armed = snapshot.levelPercent >= rule.rearmLevelPercent,
                    previousLevelPercent = snapshot.levelPercent,
                )
            )
        }

        return if (previousLevel > rule.thresholdPercent && snapshot.levelPercent <= rule.thresholdPercent) {
            trigger(rule, state, snapshot, candidateEventId)
        } else {
            noEvent(state.copy(previousLevelPercent = snapshot.levelPercent))
        }
    }

    private fun trigger(
        rule: AlertRule,
        state: AlertState,
        snapshot: BatterySnapshot,
        eventId: String,
    ): AlertEvaluation {
        val event = ThresholdReachedEvent(
            eventId = eventId,
            levelPercent = snapshot.levelPercent,
            thresholdPercent = rule.thresholdPercent,
            occurredAtEpochMillis = snapshot.capturedAtEpochMillis,
            expiresAtEpochMillis = snapshot.capturedAtEpochMillis + ThresholdReachedEvent.DEFAULT_EXPIRY_MILLIS,
            sequence = snapshot.sequence,
        )
        return AlertEvaluation(
            state = state.copy(
                armed = false,
                previousLevelPercent = snapshot.levelPercent,
                lastEventId = event.eventId,
                lastTriggeredAtEpochMillis = event.occurredAtEpochMillis,
            ),
            event = event,
        )
    }

    private fun noEvent(state: AlertState) = AlertEvaluation(state = state, event = null)
}

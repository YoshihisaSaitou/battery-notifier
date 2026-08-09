package com.magicitengineer.batterynotifierandroidmobileapp.domain.alert

import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot

data class FullChargeEvaluation(
    val armed: Boolean,
    val event: ThresholdReachedEvent?,
)

object FullChargeEvaluator {
    fun evaluate(
        rule: AlertRule,
        armed: Boolean,
        previousSnapshot: BatterySnapshot?,
        snapshot: BatterySnapshot,
        candidateEventId: String,
    ): FullChargeEvaluation {
        if (!rule.monitoringEnabled || !rule.fullChargeNotificationEnabled) {
            return FullChargeEvaluation(armed = false, event = null)
        }
        if (!snapshot.isCharging) {
            return FullChargeEvaluation(armed = false, event = null)
        }

        val startedNewChargingSession = previousSnapshot?.isCharging != true
        if (startedNewChargingSession) {
            return FullChargeEvaluation(
                armed = snapshot.levelPercent < 100,
                event = null,
            )
        }
        if (!armed || snapshot.levelPercent != 100 || previousSnapshot.levelPercent >= 100) {
            return FullChargeEvaluation(armed = armed, event = null)
        }

        return FullChargeEvaluation(
            armed = false,
            event = ThresholdReachedEvent(
                eventId = candidateEventId,
                levelPercent = 100,
                thresholdPercent = 100,
                occurredAtEpochMillis = snapshot.capturedAtEpochMillis,
                expiresAtEpochMillis = snapshot.capturedAtEpochMillis +
                    ThresholdReachedEvent.DEFAULT_EXPIRY_MILLIS,
                sequence = snapshot.sequence,
                kind = AlertEventKind.FULL_CHARGE,
            ),
        )
    }

    fun reevaluateWithoutEvent(
        rule: AlertRule,
        snapshot: BatterySnapshot?,
    ): Boolean = rule.monitoringEnabled &&
        rule.fullChargeNotificationEnabled &&
        snapshot?.isCharging == true &&
        snapshot.levelPercent < 100
}

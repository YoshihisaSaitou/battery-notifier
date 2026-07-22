package com.magicitengineer.batterynotifierandroidmobileapp.domain.alert

import com.magicitengineer.batterynotifierandroidmobileapp.domain.battery.BatterySnapshot

object AlertRuleChangeEvaluator {
    fun reevaluateWithoutEvent(
        rule: AlertRule,
        state: AlertState,
        snapshot: BatterySnapshot?,
    ): AlertState {
        if (snapshot == null) return state

        val armed = when {
            snapshot.levelPercent <= rule.thresholdPercent -> false
            snapshot.levelPercent >= rule.rearmLevelPercent -> true
            else -> state.armed
        }
        return state.copy(
            armed = armed,
            previousLevelPercent = snapshot.levelPercent,
        )
    }
}

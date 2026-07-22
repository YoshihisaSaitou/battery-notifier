package com.magicitengineer.batterynotifierandroidwearapp.domain.presentation

import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState
import com.magicitengineer.batterynotifierandroidwearapp.domain.sync.NotificationDisposition

enum class Freshness {
    NO_DATA,
    FRESH,
    DELAYED,
    STALE,
}

data class WearDisplayState(
    val freshness: Freshness,
    val levelPercent: Int? = null,
    val isCharging: Boolean = false,
    val thresholdPercent: Int? = null,
    val monitoringEnabled: Boolean = false,
    val receivedAtEpochMillis: Long? = null,
    val ageMinutes: Long? = null,
    val incompatibleSchema: Boolean = false,
    val clockWarning: Boolean = false,
    val notificationPermissionMissing: Boolean = false,
    val notificationDeliveryFailed: Boolean = false,
)

object WearDisplayStateMapper {
    const val FRESH_MAX_AGE_MILLIS = 2 * 60 * 1_000L
    const val DELAYED_MAX_AGE_MILLIS = 5 * 60 * 1_000L

    fun map(
        state: WearPersistentState,
        nowEpochMillis: Long,
    ): WearDisplayState {
        require(nowEpochMillis > 0)
        val phoneState = state.lastPhoneState ?: return WearDisplayState(
            freshness = Freshness.NO_DATA,
            incompatibleSchema = state.lastReceiveError == "unsupported_schema",
            clockWarning = state.notificationDisposition == NotificationDisposition.CLOCK_SKEW,
            notificationPermissionMissing =
                state.notificationDisposition == NotificationDisposition.PERMISSION_DENIED,
            notificationDeliveryFailed =
                state.notificationDisposition == NotificationDisposition.RESERVED_FAILED,
        )
        val receivedAt = requireNotNull(state.phoneStateReceivedAtEpochMillis)
        val ageMillis = nowEpochMillis - receivedAt
        val freshness = when {
            ageMillis < 0 -> Freshness.STALE
            ageMillis <= FRESH_MAX_AGE_MILLIS -> Freshness.FRESH
            ageMillis <= DELAYED_MAX_AGE_MILLIS -> Freshness.DELAYED
            else -> Freshness.STALE
        }
        return WearDisplayState(
            freshness = freshness,
            levelPercent = phoneState.levelPercent,
            isCharging = phoneState.isCharging,
            thresholdPercent = phoneState.thresholdPercent,
            monitoringEnabled = phoneState.monitoringEnabled,
            receivedAtEpochMillis = receivedAt,
            ageMinutes = if (ageMillis < 0) null else ageMillis / 60_000L,
            incompatibleSchema = state.lastReceiveError == "unsupported_schema",
            clockWarning = state.notificationDisposition == NotificationDisposition.CLOCK_SKEW,
            notificationPermissionMissing =
                state.notificationDisposition == NotificationDisposition.PERMISSION_DENIED,
            notificationDeliveryFailed =
                state.notificationDisposition == NotificationDisposition.RESERVED_FAILED,
        )
    }
}

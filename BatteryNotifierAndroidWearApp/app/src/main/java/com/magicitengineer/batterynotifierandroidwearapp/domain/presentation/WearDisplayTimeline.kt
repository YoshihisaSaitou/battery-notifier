package com.magicitengineer.batterynotifierandroidwearapp.domain.presentation

import com.magicitengineer.batterynotifierandroidwearapp.domain.state.WearPersistentState

data class WearDisplayTimelineEntry(
    val startEpochMillisInclusive: Long,
    val endEpochMillisExclusive: Long,
    val displayState: WearDisplayState,
)

data class WearDisplayTimeline(
    val defaultState: WearDisplayState,
    val entries: List<WearDisplayTimelineEntry>,
)

/**
 * Produces cacheable validity windows so system surfaces age without another DataItem.
 */
object WearDisplayTimelineMapper {
    fun map(state: WearPersistentState): WearDisplayTimeline {
        val receivedAt = state.phoneStateReceivedAtEpochMillis
            ?: return WearDisplayTimeline(
                defaultState = WearDisplayStateMapper.map(state, 1L),
                entries = emptyList(),
            )
        val freshEnd = receivedAt.saturatingAdd(
            WearDisplayStateMapper.FRESH_MAX_AGE_MILLIS + 1L
        )
        val delayedEnd = receivedAt.saturatingAdd(
            WearDisplayStateMapper.DELAYED_MAX_AGE_MILLIS + 1L
        )
        val rollbackState = WearDisplayStateMapper.map(
            state,
            (receivedAt - 1L).coerceAtLeast(1L),
        ).withoutCachedRelativeAge()
        val freshState = WearDisplayStateMapper.map(state, receivedAt).withoutCachedRelativeAge()
        val delayedState = WearDisplayStateMapper.map(state, freshEnd).withoutCachedRelativeAge()
        val staleState = WearDisplayStateMapper.map(state, delayedEnd).withoutCachedRelativeAge()
        val entries = buildList {
            if (receivedAt > 1L) {
                add(WearDisplayTimelineEntry(1L, receivedAt, rollbackState))
            }
            add(WearDisplayTimelineEntry(receivedAt, freshEnd, freshState))
            add(WearDisplayTimelineEntry(freshEnd, delayedEnd, delayedState))
            add(WearDisplayTimelineEntry(delayedEnd, Long.MAX_VALUE, staleState))
        }
        return WearDisplayTimeline(defaultState = staleState, entries = entries)
    }

    private fun Long.saturatingAdd(increment: Long): Long =
        if (this > Long.MAX_VALUE - increment) Long.MAX_VALUE else this + increment

    private fun WearDisplayState.withoutCachedRelativeAge(): WearDisplayState =
        copy(ageMinutes = null)
}

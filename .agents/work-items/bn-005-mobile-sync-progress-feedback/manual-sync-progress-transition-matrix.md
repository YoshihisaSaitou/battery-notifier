# BN-005 manual-sync progress transition matrix

## Invariants

- `ManualSyncUiState` in `MainActivity` is the sole writer for this screen-local presentation state.
- `SYNCING` disables the manual-sync action, shows a visual progress indicator, uses the syncing action label, and uses the dedicated syncing status message.
- A user-initiated sync remains `SYNCING` until both the coordination result is available and 500 ms have elapsed since the click.
- The existing `MobileSyncCoordinator` remains the sole serializer and persistence boundary for battery refresh and Data Layer outbox delivery. BN-005 does not change persisted fields or transport behavior.
- Background, connection-recovery, battery-change, and settings-change sync triggers do not claim the user-initiated manual action is running.

## Transitions

| Current state | Input/action | Coordination/callback order | Next state | Required presentation |
|---|---|---|---|---|
| IDLE/result | User taps manual sync | UI writes `SYNCING` before dispatching work | SYNCING | Disabled button, progress indicator, syncing action label, dedicated syncing message |
| SYNCING | Result arrives before 500 ms | Result is retained while minimum interval completes | SYNCING | Unchanged; idle and result text remain hidden |
| SYNCING | 500 ms elapses before result | Wait for coordination result | SYNCING | Unchanged; button remains disabled |
| SYNCING | Accepted or unchanged result after both conditions | Result mapped after interval/result join | SUCCESS | Progress removed, button re-enabled, success message |
| SYNCING | Rejected/not-pending result after both conditions | Result mapped after interval/result join | FAILED | Progress removed, button re-enabled, retry action available |
| SYNCING | Battery unavailable/invalid result after both conditions | Result mapped after interval/result join | BATTERY_UNAVAILABLE or INVALID_BATTERY_INPUT | Progress removed, button re-enabled, classified error message |
| SYNCING | Unexpected non-cancellation exception | Exception maps to `FAILED`; minimum interval still applies | FAILED | Progress removed after minimum interval, button re-enabled, retry message |
| SYNCING | Screen-scope cancellation | Cancellation propagates without conversion or delay | Activity leaves composition | No leaked work and no stale restored `SYNCING` state |
| SYNCING | Second tap | Button is disabled, so callback is not invoked | SYNCING | No duplicate manual request |

## Lifecycle, retry, and sibling paths

- Activity recreation cancels the screen scope and resets the screen-local manual-sync presentation to `IDLE`; it does not restore a stale `SYNCING` state or start a new request. BN-005 does not add reconnect-time, restart-time, or delayed auto-send behavior.
- Leaving/destroying the Activity cancels its Compose scope. The durable sync/outbox behavior remains owned by `MobileSyncCoordinator` and DataStore; returning to the screen permits an explicit retry.
- Connected, disconnected, and Data Layer failure results use the existing coordinator mapping; the progress presentation is identical until the classified result is available.
- The same-root sibling paths for threshold saving, monitoring start/stop, background battery callbacks, process restoration, and Wear-originated settings are inspected but excluded: each owns separate UI or background state and must not mutate the manual-sync presentation.
- There are no new persisted fields, migration/sanitizer combinations, transport paths, permissions, external services, or application/signing changes.

# Test Role

## Mission

Execute the specified normal, recovery, concurrency, invalid-input, UI, and compatibility scenarios and record reproducible evidence without modifying production behavior.

## Required inputs

1. Root `AGENTS.md`.
2. Active work-item `state.yaml` and review handoff.
3. `docs/test-plan-and-cases.md`, `docs/compatibility-matrix.md`, and feature specifications.
4. A build identified by commit/worktree state, application ID, signing type, and version.

## Mandatory scenarios

### 1. Normal sync

- Connected Mobile and Wear receive current battery, charging, threshold, and monitoring state.
- App, Tile, and complication display the same validated value.
- A threshold crossing creates at most one notification on each permitted device.

### 2. Updates while disconnected

- Disconnect all usable Data Layer routes.
- Change the phone battery state multiple times.
- Confirm Mobile monitoring/notification continues and Wear retains the last valid value before becoming stale.

### 3. Reconnection

- Restore connectivity after multiple disconnected updates.
- Confirm Wear converges to the current highest-sequence value rather than an intermediate value.
- Test reconnect before and after event expiry; expired events must not notify.

### 4. Smartphone restart

- Test monitoring enabled and disabled separately.
- Confirm boot/foreground-service behavior on each target API and report restrictions accurately.
- Confirm settings, sequence, armed state, and duplicate-notification prevention survive as specified.

### 5. Watch restart

- Restart the watch with Fresh and Stale stored data.
- Confirm last valid data loads immediately, freshness is recalculated, and a current value arrives after reconnect.
- Confirm Tile and complication recover without opening the Activity first.

### 6. Stale data display

- Test exact Fresh, Delayed, and Stale boundaries plus No Data.
- Verify app, Tile, complication, relative time, accessibility description, and retry guidance.
- Change device clocks and verify suspicious timestamps do not appear permanently fresh.

### 7. Consecutive updates

- Send at least 30 ordered updates rapidly and introduce reverse completion/delivery where the harness allows.
- Confirm highest sequence wins, no ANR/crash occurs, no notification duplicates, and update requests are bounded.

### 8. Invalid data reception

- Test unknown path, missing key, wrong DataMap type, negative/over-100 values, invalid threshold, invalid event ID, impossible expiry, duplicate, out-of-order, and future schema.
- Confirm invalid payloads are rejected as a whole, the last valid value remains, and no invalid notification occurs.

## Additional required coverage

- Notification permission combinations on Mobile and Wear.
- Japanese/English and mixed-locale behavior.
- Pixel 10 Pro Fold outer/inner/split-screen transitions.
- Pixel Watch 4 41mm/45mm layout, Tile, complication, and notification.
- TalkBack, maximum font/display scaling, monitoring-stop action, app update, process recreation, and Google Play services unavailable behavior.

## Evidence rules

For every case record:

- test case ID and requirement/acceptance IDs;
- date/time and tester;
- Mobile/Watch model, OS build, app build, connection mode, and permission state;
- exact steps or automated command;
- expected and actual result;
- Pass, Fail, Blocked, or Not Run;
- concise log/screenshot/artifact path when useful;
- issue/finding ID for failures.

Never include personal device identifiers, tokens, or large raw logs in `state.yaml`.

## Failure handling

- A product or code failure returns the item to implementation after review triage.
- An ambiguous expected result returns it to specification.
- An environment failure remains Blocked/Not Run and does not count as Pass.
- Add a regression test for every resolved defect, then rerun the affected scenario and relevant surrounding suite.

## Exit gate

Hand off to human verification only when required automated/emulator cases pass, all failures have disposition, and real-device cases are clearly separated from emulator evidence.

Set `status: awaiting_human`, `current_phase: human_verification`, and `current_role: human`.

## Prohibited

- Do not patch production code while acting as test role.
- Do not replace mandatory real-device results with emulator results.
- Do not mark a skipped or unavailable case as passed.
- Do not infer notification latency or battery impact without measurements.


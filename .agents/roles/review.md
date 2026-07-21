# Review Role

## Mission

Independently verify that the implementation matches the specification and is safe under time changes, concurrency, reconnection, Data Layer, and Android lifecycle behavior.

## Required inputs

1. Root `AGENTS.md`.
2. Active work-item `state.yaml` and implementation handoff.
3. All `spec_refs`, relevant ADRs, production changes, and tests.
4. Exact implementation check results.

## Mandatory review checklist

### Timestamp handling

- Separate `capturedAt`, `sentAt`, `receivedAt`, and event expiry semantics.
- Confirm sequence is the primary ordering key and wall clock is not used to resolve update order.
- Check Fresh/Delayed/Stale boundary equality, future timestamp tolerance, clock rollback/advance, timezone independence, and reboot behavior.
- Ensure elapsed time and epoch time are used only where their persistence properties are valid.

### Consecutive-update races

- Confirm battery callbacks and DataStore updates are serialized.
- Check atomic sequence increment, alert transition, outbox write, and dedup reservation.
- Consider send Task completion in reverse order, duplicate callbacks, parallel listener delivery, process recreation, and same-value resend.
- Ensure UI, Tile, complication, and notification observe one durable source of truth.

### Reconnection

- Verify Mobile monitoring continues while Wear is unreachable.
- Confirm reconnect sends a newly captured current state, not only an old cache.
- Confirm latest sequence converges and expired events do not create delayed notifications.
- Review retry limits, API-unavailable behavior, node/capability semantics, and no-node behavior.

### DataItem path design

- Compare path constants exactly with `docs/data-design.md`.
- Confirm leading slash, version, fixed state/event paths, listener filters, and no per-event path leak.
- Confirm MessageClient is not incorrectly relied on for durable state.
- Verify Mobile/Wear application ID and signature prerequisites are addressed.

### Android lifecycle

- Verify `ACTION_BATTERY_CHANGED` is registered/unregistered by the intended service lifecycle.
- Confirm background receive does not depend only on an Activity or ViewModel.
- Check foreground-service start restrictions, notification channel/permission handling, boot recovery, process death, app force-stop, and service recreation.
- Check coroutines are structured, cancelled with their owner, and do not leak Context or listeners.
- Check Fold/window changes do not duplicate monitoring or lose editable UI state.

## Additional review

- Data validation, permission minimization, exported components, immutable PendingIntents, localization, accessibility, logging, and privacy.
- Test quality: boundary cases, negative cases, deterministic clocks, regression coverage, and assertions against outcomes rather than implementation details.
- Documentation and traceability: requirement IDs, acceptance criteria, ADR status, and compatibility claims.

## Findings format

Record every actionable finding in state or an artifact referenced from state with:

- ID such as `RV-001`;
- severity: Critical, High, Medium, or Low;
- exact file/location;
- violated requirement or risk;
- reproducible scenario;
- required outcome, not a broad rewrite request;
- status and verification evidence.

Critical/High findings block test handoff. Medium findings require documented disposition. Low findings may be deferred with rationale.

## Progress reporting

- At start, report the active work item, reviewed build/worktree scope, checklist areas, and independence from the implementation actor.
- At each review milestone, report the areas reviewed, finding IDs and severity totals, fixes awaiting verification, and remaining review scope.
- When returning work, report the owning earlier role, violated requirement or risk, required outcome, phase-return count, and first corrective action.
- Do not report approval while any Critical/High finding is open or required review evidence is missing.
- Before handoff, report the review gate recommendation, reviewed evidence, accepted dispositions, unresolved risks, and the test role's first action.
- Keep the user-facing report and `progress_reporting.current` in `state.yaml` consistent with recorded findings and gate status.

## Exit gate

- If fixes are required, set `current_phase: implementation`, `current_role: implementation`, list the findings, and name the first fix.
- If review passes, record the reviewed commit/worktree state and evidence, then set `status: in_test`, `current_phase: test`, and `current_role: test`.

## Prohibited

- Do not approve based only on passing tests.
- Do not silently patch findings while remaining the reviewer.
- Do not accept comments or documentation as proof when the runtime path contradicts them.
- Do not downgrade a finding merely because the required real device is unavailable.

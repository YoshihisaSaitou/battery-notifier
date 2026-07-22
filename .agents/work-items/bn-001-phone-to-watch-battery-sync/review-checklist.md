# BN-001 Independent Review Checklist

This checklist prepares an independent review; it does not change the active role from implementation. The reviewer must read `AGENTS.md`, the active `state.yaml`, `.agents/roles/review.md`, every `spec_ref`, the complete production diff, and all affected tests.

## Independence gate

- The reviewer actor must be different from the implementation actor recorded in `state.yaml`.
- The reviewer records findings and evidence but does not patch production code while acting as reviewer.
- The implementation phase must explicitly hand off before the reviewer changes the work item to `in_review`.
- Review approval based only on passing tests is prohibited.

## Candidate identity

Record before review:

- `git rev-parse HEAD`;
- `git -c safe.directory=<repository-root> status --short`;
- all changed and untracked production/test/document paths;
- the exact Mobile and Wear application IDs and signing variants;
- the commands and results copied from `state.yaml`.

If the worktree changes after review starts, invalidate the affected findings and re-review those files.

## Environment and checks

1. Run `powershell -ExecutionPolicy Bypass -File .\scripts\preflight.ps1`.
2. Run `git diff --check`.
3. Run the affected project debug and release JVM unit tests and lint using the commands recorded in `state.yaml`.
4. Compile `RealMobileDataStoreInstrumentedTest`, then execute its three cases on an attached Android emulator or device.
5. Keep instrumented and real-device results as `not_run` until actually executed; a Windows host-JVM DataStore rename result is not Android storage evidence.

## IMP-002 focus

- FR-004/ADR-003: one Proto DataStore instance, repository-only access, safe defaults, corruption behavior, and future migration readiness.
- FR-012/FR-014/FR-015: downward crossing, no duplicate event in one discharge cycle, hysteresis, charging, and first-low observation.
- FR-018/FR-060/FR-061: monotonic `Long` sequence and atomic snapshot/alert/outbox persistence.
- Verify nullable `Long` assertions cannot select JUnit's boxed object overload.
- Verify concurrent real-file updates do not lose or duplicate sequence values.
- Verify a malformed protobuf file is replaced with a valid safe default without being reported as an emulator or device pass.

## Full BN-001 mandatory focus

- REV-001: timestamps, expiry, future-time tolerance, clock rollback/advance, and freshness boundaries.
- REV-002: callback serialization, atomic ordering, duplicate delivery, and reverse Task completion.
- REV-003: disconnected monitoring, reconnect capture, latest-state convergence, and expired events.
- REV-004: exact fixed DataItem paths, schema validation, application ID, signing identity, and listener filters.
- REV-005: Service/Receiver/coroutine lifecycle, boot recovery, permissions, notification behavior, Fold recreation, and context leaks.

## IMP-005 bounded Wear notification retry focus

- Verify Proto migration/sanitization maps a pre-counter `PENDING`, `POSTED`, `PERMISSION_DENIED`, or `RESERVED_FAILED` event to one prior attempt without exceeding the maximum of three.
- Verify the initial reservation is attempt 1 and every retry changes `RESERVED_FAILED` to `PENDING` while incrementing the count in the same DataStore transaction.
- Verify parallel foreground/manual triggers create at most one reservation and cannot post a fourth time after `FAILED_EXHAUSTED`.
- Verify `now <= expiresAt` remains eligible, `now > expiresAt` becomes terminal `EXPIRED`, and the notification gateway is not called after expiry.
- Verify `POSTED`, `PERMISSION_DENIED`, `EXPIRED`, and `FAILED_EXHAUSTED` never resurrect an old event after app resume, permission changes, duplicate DataItem delivery, or process restoration.
- Verify `MainActivity.onStart` consumes at most one automatic retry per foreground transition and the explicit retry button is shown only for `RESERVED_FAILED`.
- Compile and, when a Wear target is attached, execute `RealWearDataStoreInstrumentedTest` to confirm real-file persistence, concurrent reservation serialization, and corruption recovery; compilation alone is not runtime evidence.

## Finding template

```yaml
- id: "RV-001"
  severity: "Critical|High|Medium|Low"
  status: "open"
  file: "repository-relative/path"
  location: "symbol or line"
  requirement_refs: ["FR-000"]
  scenario: "Minimal reproducible input and sequence"
  observed: "Current outcome"
  required_outcome: "Behavior required for closure"
  verification: null
```

Critical and High findings block the transition to test. Medium findings need an explicit disposition. Low findings may be deferred only with rationale.

## Review exit

- If a fix is required, return the state to implementation, preserve the finding IDs, and name the first required correction.
- If review passes, record the reviewed candidate identity and exact evidence, then transition to the test role as required by `.agents/roles/review.md`.

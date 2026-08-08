# Implementation Role

## Mission

Implement the approved specification with minimal scope, deterministic domain behavior, durable local state, and automated unit evidence.

## Required inputs

1. Root `AGENTS.md`.
2. Active work-item `state.yaml`.
3. Every file in `spec_refs`.
4. Specification handoff and unresolved decisions.
5. Existing code, Gradle configuration, manifests, and tests in both affected apps.

## Responsibilities

### Mobile Data Layer sender

- Implement versioned fixed-path DataItems and DataMap mapping exactly as specified.
- Persist state/event outbox before sending and mark only confirmed results.
- Use `sequence`, `eventId`, expiry, and urgency according to the contract.
- Handle API unavailable, no reachable node, Task failure, retry triggers, and reconnection without stopping local monitoring.
- Avoid unbounded DataItems and high-frequency send storms.

### Wear receiver

- Receive background DataItem changes through the specified Wear lifecycle component, not only an Activity listener.
- Match the path exactly, validate the whole payload, reject unknown schema/range/type errors, and preserve the last valid value.
- Ignore duplicate or out-of-order state and expired notification events.
- Persist before updating UI, Tile, complication, or notification side effects.

### DataStore persistence

- Implement one Proto DataStore instance per app and access it through repositories.
- Make schema types immutable and provide safe defaults, migration, and corruption behavior.
- Atomically store sequence, threshold/alert state, outbox, received state, and notification deduplication markers where specified.
- Never access DataStore directly from composables.

### State-machine and review-fix completeness

These requirements apply whenever a feature or fix changes persisted state,
sanitization/migration, asynchronous delivery, callback ordering, retry,
reconnection, process recreation, or lifecycle recovery.

- Before changing production code, record a transition matrix in the work item
  or a repository artifact. It must identify the states and invariants, each
  input/action, relevant callback arrival orders, retry/cancel behavior,
  disconnect/reconnect behavior, process recreation, and the presence or absence
  of related persisted fields.
- Identify the authoritative writer and atomic persistence boundary for every
  transition. Do not infer correctness from UI serialization or the expected
  callback order.
- Treat a review finding as evidence of a violated invariant or incomplete
  transition family, not only as one failing example. Before marking the fix
  complete, inspect every call site and sibling path with the same cause,
  including reverse callback order and restart/retry paths, and record the
  inspected scope in `state.yaml`.
- When code mutates a builder or intermediate state and then makes another
  dependent decision, recompute derived status from the current post-mutation
  state. Do not reuse a snapshot captured before the mutation unless immutability
  makes that dependency explicit and a test proves it.
- A sanitizer or migration must be idempotent and domain-compatible: sanitizing
  an already sanitized value must not change it, and every sanitized output must
  map to a valid domain object without an unintended exception.
- If the transition matrix exposes an unimplemented, untested, or intentionally
  unsupported combination, record its disposition before implementation
  handoff. Do not silently treat an omitted combination as covered.

### Unit tests

- Test threshold crossing, first observation below threshold, charging, hysteresis, re-arming, sequence ordering, duplication, expiry, freshness boundaries, malformed payloads, and future schema.
- Test sender/receiver mappers against shared contract fixtures.
- Use fake clock, battery source, Data Layer gateway, notifier, and repository boundaries instead of sleeps or real network dependency.
- Add a regression test for every bug fixed during review or testing.
- For stateful asynchronous behavior, use parameterized or table-driven tests
  for applicable callback-order permutations, competing actions, and process
  recreation. One reproduction path is not sufficient evidence for a transition
  family.
- For persisted-state repair, test the valid and defined malformed combinations
  from the transition matrix. Assert sanitizer idempotence, successful domain
  mapping, and preservation of the last valid state where required.
- For every review fix, test both the reported scenario and the same-root sibling
  scenarios identified during impact analysis. Record the covered matrix rows or
  test IDs in `state.yaml`.

## Working rules

- Keep domain code independent from Android and Google Play services classes.
- Serialize battery-event processing and make side effects idempotent.
- Make application ID/signing changes explicit and record their impact before applying them.
- Keep Japanese and English resources synchronized.
- Update specifications in the same work item if implementation exposes an ambiguity; return to specification for material behavior changes.
- Preserve existing user changes and do not reformat unrelated files.

## Evidence to record

- Files changed and requirement/acceptance IDs implemented.
- Exact Gradle commands run for Mobile and Wear.
- Unit, lint, contract, and targeted integration results, including `not_run` reasons.
- Known limitations, emulator-only areas, and remaining human-device checks.

## Progress reporting

- At start, report the active work item, current `IMP-*` task, affected components, intended behavior, and first code or test action.
- At each material milestone, report the `IMP-*` task, behavior and files completed, checks run with exact outcomes, remaining work, and known limitations.
- For failures, report the failure signature, same-cause attempt count, corrective change, result, and next safe action before continuing or stopping the loop.
- Do not report an implementation task as complete while runtime wiring, required tests, documentation alignment, or another stated part remains pending.
- Do not report a state-machine or review-fix task as complete until the
  transition-matrix impact analysis and invariant-based regression evidence are
  recorded. Passing existing tests alone is not completion evidence.
- Before handoff, report the implementation gate result, exact review scope/evidence, unresolved risks, and the review role's first action.
- Keep the user-facing report and `progress_reporting.current` in `state.yaml` consistent with implementation evidence and next actions.

## Exit gate

Hand off to review only when:

- all implementation tasks in state are complete or explicitly excluded;
- relevant unit and contract tests pass;
- applicable transition matrices, invariant checks, and same-root sibling-path
  regressions are complete and referenced from the work item;
- affected project lint/check tasks pass or failures are documented as blockers;
- the code and documents agree;
- the handoff names review focus areas and exact evidence.

Set `status: in_review`, `current_phase: review`, and `current_role: review`.

## Prohibited

- Do not silently modify acceptance criteria after code is written.
- Do not use Data Layer as the only storage.
- Do not order updates by callback completion time or wall-clock time alone.
- Do not suppress an exception without a user-visible/retry state or diagnostic classification.
- Do not mark device or emulator tests as passed when only unit tests ran.

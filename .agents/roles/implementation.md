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

### Unit tests

- Test threshold crossing, first observation below threshold, charging, hysteresis, re-arming, sequence ordering, duplication, expiry, freshness boundaries, malformed payloads, and future schema.
- Test sender/receiver mappers against shared contract fixtures.
- Use fake clock, battery source, Data Layer gateway, notifier, and repository boundaries instead of sleeps or real network dependency.
- Add a regression test for every bug fixed during review or testing.

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
- Before handoff, report the implementation gate result, exact review scope/evidence, unresolved risks, and the review role's first action.
- Keep the user-facing report and `progress_reporting.current` in `state.yaml` consistent with implementation evidence and next actions.

## Exit gate

Hand off to review only when:

- all implementation tasks in state are complete or explicitly excluded;
- relevant unit and contract tests pass;
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

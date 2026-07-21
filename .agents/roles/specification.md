# Specification Role

## Mission

Turn product intent into an implementable, testable contract before code changes begin. This role owns normative behavior, not production implementation.

## Required inputs

1. Root `AGENTS.md`.
2. Active work-item `state.yaml`.
3. `docs/product-requirements.md` and `docs/functional-requirements.md`.
4. All feature-specific documents in `spec_refs`.
5. Open review, test, or human feedback recorded in state.

## Responsibilities

### Sync data format

- Define versioned DataItem paths and every DataMap key, type, required/optional status, range, and default.
- Define the ordering key, event identity, expiry, deduplication, validation, and schema compatibility rules.
- Keep `docs/data-design.md`, `docs/wear-os-integration-specification.md`, and contract fixtures consistent.
- Confirm that Mobile and Wear use the same application ID/signing constraint without embedding secrets.

### Connected, disconnected, and reconnected behavior

- Specify send triggers, urgency, local persistence, and observable UI state while connected.
- Specify what continues on Mobile while disconnected, what Wear retains, and what is not guaranteed.
- Specify reconnection detection, current-state refresh, latest-value convergence, event expiry, and retry limits.
- Distinguish node reachability, pairing, API availability, and data freshness. Do not collapse them into one “connected” flag.

### Stale data behavior

- Define Fresh, Delayed, Stale, and No Data boundaries using exact inclusive/exclusive limits.
- Define how the last valid value, warning, final update time, Tile, and complication behave in each state.
- Define behavior for device clock changes and suspicious future timestamps.

### Acceptance criteria

- Write Given/When/Then or equivalently unambiguous acceptance criteria.
- Cover normal sync, threshold crossing, disconnect, reconnection, restart, stale data, consecutive updates, malformed data, permissions, and localization.
- Map each criterion to requirement and test IDs.
- Identify mandatory real-device criteria separately from emulator criteria.

## Deliverables

- Updated normative Markdown documents under `docs/`.
- Any contract example/fixture required by implementation and test.
- Updated `spec_refs`, decisions, risks, phase tasks, handoff, and next actions in `state.yaml`.

## Progress reporting

- At start, report the active work item, specification task, affected contract/specification IDs, and first decision or definition to complete.
- At each material update, report the behavior defined, acceptance criteria added or changed, decisions resolved, and remaining ambiguities.
- Report open product or architecture decisions as blockers or `decisions_needed`; never present an assumption as an approved decision.
- Before handoff, report the specification gate result, implementation-ready artifacts, unresolved exclusions, and the implementation role's first action.
- Keep the user-facing report and `progress_reporting.current` in `state.yaml` consistent with the current specification task.

## Exit gate

Hand off to implementation only when:

- the sync payload and paths are complete and internally consistent;
- connected/disconnected/reconnected and stale behavior are explicit;
- acceptance criteria are testable;
- material unresolved decisions are either human-approved or clearly marked as blockers;
- affected requirement and test IDs are listed in state.

Set `current_phase: implementation`, `current_role: implementation`, and identify the first implementation task. Do not mark the implementation itself complete.

## Prohibited

- Do not hide an unresolved product decision inside sample code.
- Do not change application scope solely to make implementation easier.
- Do not claim Android behavior from memory when an official source can be checked cheaply.
- Do not implement production code while acting only as specification role; transition the state first.

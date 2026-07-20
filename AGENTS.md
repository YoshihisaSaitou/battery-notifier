# Battery Notifier Agent Rules

This file is the project-wide operating contract for Codex, Claude Code, and human contributors. It applies to the entire repository unless a more specific nested `AGENTS.md` adds stricter rules.

## 1. Required reading order

Before changing files, read these sources in order:

1. This `AGENTS.md` in full.
2. The active `.agents/work-items/*/state.yaml` file.
3. The file for `current_role` under `.agents/roles/`.
4. Every document listed in the work item's `spec_refs`.
5. The source files and tests directly affected by the work.

Do not rely on chat history as the only source of project state. If chat history and repository files disagree, stop and record the discrepancy in the work item before proceeding.

## 2. Source-of-truth order

Use the following precedence when instructions conflict:

1. The human's current explicit instruction.
2. This file and any applicable nested `AGENTS.md`.
3. Accepted ADRs and approved specifications under `docs/`.
4. The active work item's `state.yaml`.
5. Existing implementation and tests.

Draft documents describe the intended direction but may contain open decisions. Never silently resolve a product or architecture decision that would materially change scope; record it under `decisions_needed` and request human direction.

## 3. Repository map

| Path | Purpose |
|---|---|
| `docs/` | Product, functional, architecture, data, UI, privacy, and test specifications |
| `BatteryNotifierAndroidMobileApp/` | Android smartphone application and its independent Gradle build |
| `BatteryNotifierAndroidWearApp/` | Wear OS application and its independent Gradle build |
| `specs/` | Machine-readable or narrowly scoped feature contracts when introduced |
| `.agents/roles/` | Stable role-specific operating rules |
| `.agents/work-items/<id>/state.yaml` | Dynamic state, handoff, evidence, and next actions for one work item |
| `CLAUDE.md` | Claude Code bootstrap that points back to this file |

The Mobile and Wear projects are separate Gradle projects. Run and report checks for each affected project independently.

## 4. Product invariants

- v1.0 monitors the smartphone battery, not the watch's own battery.
- The threshold range is 5–100%, the default is 20%, and a notification is created on a downward threshold crossing.
- The phone remains the single writer for settings in v1.0; Wear is read-only for threshold and monitoring settings.
- Wear shows the last valid phone value and marks data stale after the limits defined in `docs/functional-requirements.md`.
- Data Layer is a transport, not the durable source of truth. Each device persists validated state in its own DataStore.
- Mobile and Wear must use the same final application ID and signing identity for Data Layer communication. Kotlin namespaces may differ.
- Use the fixed, versioned DataItem paths defined in `docs/data-design.md`. Do not create an unbounded path per event.
- Phone notifications are local-only; Wear creates a local notification from a validated, unexpired event to prevent mirrored duplicates.
- A complication is supported only on watch faces and slots that accept the documented complication types. Never claim that the app can force content onto every watch face.
- User-facing text must support English and Japanese and must not be hard-coded in Kotlin.
- Pixel 10 Pro Fold and Pixel Watch 4 are mandatory real-device release gates.

## 5. Architecture and implementation rules

- Use Kotlin and the existing Jetpack Compose/Gradle Kotlin DSL projects.
- Follow the simplified DDD layers in `docs/system-architecture-design.md`: presentation, application, domain, and data/platform.
- Keep domain rules free of Android framework and Google Play services types.
- Make threshold crossing, re-arming, freshness, validation, ordering, and deduplication deterministic and unit-testable.
- Use Proto DataStore through repositories. Do not read or write DataStore directly from a composable.
- Serialize battery processing so consecutive callbacks cannot race. Increment and persist `sequence`, alert state, and outbox state atomically.
- Validate the complete incoming DataMap before persisting any field. Preserve the last valid state when a payload is invalid or unsupported.
- Treat wall-clock timestamps as display/expiry data and `sequence` as the primary ordering key. Account for clock changes and future timestamps.
- Respect Android lifecycle and background restrictions. Do not move continuous polling into an Activity, ViewModel, or unmanaged global scope.
- Minimize permissions and payload data. Do not add analytics, advertising identifiers, location, account data, or custom Bluetooth/socket transport without an approved specification and ADR.

## 6. Specification-driven loop

Every feature follows this loop:

```text
specification -> implementation -> review -> test -> human verification -> done
        ^              |            |        |              |
        +--------------+------------+--------+--------------+
                       feedback returns to the owning phase
```

### Phase gates

| From | Gate required to advance |
|---|---|
| Specification -> Implementation | Data contract, connected/disconnected/reconnected behavior, stale behavior, and acceptance criteria are recorded |
| Implementation -> Review | Code, unit tests, relevant automated checks, and implementation handoff are complete |
| Review -> Test | No open Critical/High review finding; required fixes are re-reviewed |
| Test -> Human verification | Required automated/emulator cases pass and failures are documented |
| Human verification -> Done | Required devices, battery impact, notification latency, and final approval are recorded |

If a phase finds a problem owned by an earlier phase, update `state.yaml` and hand the work back. Do not bypass a failed gate by changing only the status string.

## 7. Role discipline

- Only one `current_role` is active for a work item at a time.
- The active agent must follow the matching `.agents/roles/*.md` file.
- Specification owns normative documents and acceptance criteria.
- Implementation owns production code and unit tests.
- Review reports findings and verifies fixes; it does not silently fix its own findings unless the state explicitly reassigns the agent to implementation.
- Test executes the test plan and records evidence; it does not silently patch production code.
- Human owns real-device judgment and final approval.
- A person or agent may perform multiple roles only through explicit state transitions. Record each transition and keep the evidence from the previous role.

## 8. Work-item state rules

Create one directory per independently reviewable feature:

```text
.agents/work-items/<lower-kebab-work-item-id>/state.yaml
```

The state file must always contain:

- schema version, work item ID, title, and scope;
- status, current phase, current role, and current actor;
- `spec_refs` and affected components;
- phase-by-phase task status;
- decisions needed, blockers, risks, evidence, and test results;
- handoff summary and concrete next actions;
- RFC 3339 timestamp with timezone and the actor that updated it.

Allowed top-level statuses are:

```text
draft
ready
in_progress
blocked
in_review
in_test
awaiting_human
done
cancelled
```

Update `state.yaml`:

- before beginning a phase;
- after a material decision or specification change;
- after each check that produces reusable evidence;
- when a blocker appears or clears;
- before an agent session ends or rate limits are likely to interrupt work;
- during every role handoff;
- when human verification approves or rejects the release candidate.

Never write `pass`, `complete`, or `done` without evidence. Use repository-relative artifact paths and exact commands/results where practical. Do not store secrets, tokens, personal data, or large raw logs in state files.

## 9. Handoff requirements

Before handing off, the current role must record:

1. What changed and why.
2. Files and requirement IDs affected.
3. Checks run and their exact result.
4. Known risks, unresolved findings, and decisions needed.
5. The next role and the first concrete next action.

The receiving role must verify the handoff against the repository before changing status to `in_progress`.

## 10. Documentation rules

- Update the relevant specification before or with behavior-changing code.
- Use existing IDs (`PR-*`, `FR-*`, `NFR-*`, `AC-*`, `TC-*`, `ADR-*`) for traceability.
- Record architecture changes as a new or superseding ADR; do not erase the old decision history.
- Keep Markdown filenames in English lower-kebab-case.
- Keep DataMap keys, paths, default values, freshness boundaries, and notification expiry consistent across data, integration, functional, and test documents.
- Use official Android/Google documentation for platform behavior that can change. Record the verification date when it affects compatibility or permissions.

## 11. Verification rules

Run the smallest relevant checks while iterating, then the full affected-project gate before handoff.

Typical Mobile checks from `BatteryNotifierAndroidMobileApp/`:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
```

Typical Wear checks from `BatteryNotifierAndroidWearApp/`:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
```

Also run targeted instrumented, Compose UI, contract, and emulator tests required by `docs/test-plan-and-cases.md`. A command that was not run must be recorded as `not_run`, never implied to have passed.

Real-device checks cannot be replaced by emulator evidence for Pixel 10 Pro Fold or Pixel Watch 4 release gates.

## 12. Change safety

- Preserve unrelated user changes and do not rewrite or delete them.
- Do not use destructive Git or filesystem commands to clean the workspace.
- Keep commits and work items narrowly scoped when commits are requested.
- Do not commit generated build output, local SDK paths, signing material, credentials, or device identifiers.
- Stop and request human direction when a change requires a new permission, external service, distribution policy, final application ID, signing change, or material product-scope expansion not already approved.

## 13. Definition of done

A feature is done only when:

- normative specifications and acceptance criteria match the implementation;
- implementation and migrations are complete in every affected app;
- required automated checks pass with recorded evidence;
- review has no unresolved Critical/High finding;
- required emulator and negative-path tests pass;
- Pixel 10 Pro Fold and Pixel Watch 4 checks are recorded when applicable;
- battery impact and notification latency are human-reviewed when applicable;
- `state.yaml` contains the final handoff, evidence, approval actor, and timestamp.


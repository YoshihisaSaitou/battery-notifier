# Human Role

## Mission

Make the judgments that agents and emulators cannot make reliably: required-device usability, perceptible battery cost and latency, distribution-policy acceptability, and final release approval.

## Required inputs

1. Root `AGENTS.md`.
2. Active work-item `state.yaml` and test handoff.
3. Open decisions, risks, review findings, failures, and evidence.
4. The release-candidate builds for both apps.

## Responsibilities

### Real-device verification

- Install matching, correctly signed builds on Google Pixel 10 Pro Fold and Google Pixel Watch 4.
- Verify Fold outer/inner/split-screen behavior, Wear 41mm/45mm behavior as available, notification sound/vibration, Tile, complication selection, and reconnect/restart flows.
- Confirm wording, readability, tap targets, and stale-data warning are understandable in Japanese and English.
- Record which required device sizes were physically tested. Missing mandatory hardware remains a release blocker unless the product owner explicitly changes the gate.

### Battery consumption and notification latency

- Run the documented monitoring-off versus monitoring-on battery comparison under comparable conditions.
- Review foreground-service visibility, CPU/wakeup/network behavior, and whether the ongoing notification is acceptable.
- Measure Mobile and Wear notification delay over the required connection/idle conditions and record samples, percentile/summary, outliers, and judgment.
- Reject results that are based only on subjective memory or incomparable runs.

### Final approval

- Review unresolved Medium/Low issues, privacy/permission behavior, foreground-service policy risk, and release notes.
- Approve, reject, or request changes explicitly with date, approver, build identity, and rationale.
- Approval applies only to the identified build/worktree state. Material code or specification changes invalidate prior approval and return the item to the appropriate phase.

## Decision outcomes

- **Approve**: set `status: done`; record approval, devices, measurements, and final evidence.
- **Request implementation changes**: set `current_phase: implementation`, `current_role: implementation`, and list exact required outcomes.
- **Request specification changes**: set `current_phase: specification`, `current_role: specification`, and record the product decision needed.
- **Blocked**: set `status: blocked`, name the unavailable device/authority/dependency, and define the unblock condition.

## Prohibited

- Do not approve without identifying the tested build.
- Do not treat emulator evidence as Pixel 10 Pro Fold or Pixel Watch 4 physical-device approval.
- Do not omit negative observations from the state file.
- Do not expose signing secrets, account data, or device identifiers in evidence.


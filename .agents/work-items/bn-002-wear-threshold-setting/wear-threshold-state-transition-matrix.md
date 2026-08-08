# BN-002 Wear threshold state-transition matrix

Status: implementation evidence for IMP-017

Finding: RV-010

Requirements: FR-005, FR-009, AC-015, AC-016, AC-017, TC-U030

## 1. Scope and invariants

This matrix covers Wear threshold-change persistence, sanitization, mapping,
retry, callback ordering, and process recreation. Mobile remains the only
authoritative settings writer.

| ID | Invariant |
|---|---|
| INV-01 | `SENDING`, `WAITING_RESULT`, `SEND_FAILED`, and `APPLIED_WAITING_STATE` always have a valid pending request after sanitization. |
| INV-02 | `APPLIED_WAITING_STATE` always has a valid result after sanitization. |
| INV-03 | Every `WearStateSanitizer.sanitize` output can be passed to `WearStateProtoMapper.toDomain` without an unintended exception. |
| INV-04 | Sanitization is idempotent: applying it twice produces the same Proto state as applying it once. |
| INV-05 | A missing or invalid pending request never leaves an active threshold-change status. |
| INV-06 | A missing or invalid result never leaves `APPLIED_WAITING_STATE`; a valid pending request remains explicitly retryable. |
| INV-07 | Reconnect or process recreation does not automatically transmit a threshold request. |
| INV-08 | Callback completion order cannot overwrite a confirmed APPLIED reconciliation or create a second semantic request. |

## 2. Authority and atomic boundaries

| Concern | Authority | Atomic boundary |
|---|---|---|
| Effective threshold | Mobile | Mobile Proto DataStore repository transaction |
| Wear draft and pending request | Wear | `ProtoWearStateRepository` `DataStore.updateData` call |
| Persisted-state repair | Wear | `WearStateSanitizer.sanitize` before serializer output or domain mapping |
| Domain validity | Wear | `WearStateProtoMapper.toDomain` plus `WearPersistentState` constructor invariants |
| Transport | Neither durable authority | MessageClient request/result delivery only |

UI serialization is not an admission, ordering, or recovery boundary.

## 3. Persisted-state repair matrix

`valid pending` means schema/version, UUID, desired threshold, and expected
threshold pass validation. `valid result` means UUID, result code, effective
threshold, and positive phone-state sequence pass validation.

| Input status | Pending | Result | Sanitized status | Sanitized fields | Disposition |
|---|---|---|---|---|---|
| `SENDING` | absent or invalid | any | `IDLE` | clear pending and result | Safe default; no automatic retry |
| `WAITING_RESULT` | absent or invalid | any | `IDLE` | clear pending and result | Safe default; no stranded waiting state |
| `SEND_FAILED` | absent or invalid | any | `IDLE` | clear pending and result | Safe default; no unusable retry action |
| `APPLIED_WAITING_STATE` | absent or invalid | any | `IDLE` | clear pending and result | RV-010 reported and sibling paths |
| `APPLIED_WAITING_STATE` | valid | absent or invalid | `WAITING_RESULT` | retain pending; clear result | Explicit retry remains available |
| `APPLIED_WAITING_STATE` | valid | valid | `APPLIED_WAITING_STATE` | retain pending and result | Await confirming phone-state |
| `SENDING` | valid | valid or absent | `SENDING` | retain valid fields | Result-first callback is allowed; repository recovery decides retry state |
| `WAITING_RESULT` | valid | valid or absent | `WAITING_RESULT` | retain valid fields | Await result or explicit retry |
| `SEND_FAILED` | valid | valid or absent | `SEND_FAILED` | retain valid fields | Explicit retry/cancel only |
| Terminal status | any | any valid fields | same terminal status | preserve valid fields | Existing domain-compatible behavior; not expanded by RV-010 |
| Unknown status | any | any | `IDLE` | apply normal field validation | Safe enum default |

Dependent repairs must evaluate the builder's current status after earlier
repairs. A status captured before clearing an invalid/missing pending request
must not be reused.

## 4. Runtime transition and ordering matrix

| Current state | Input/action | Next state | Persistence/transport rule |
|---|---|---|---|
| `IDLE` or terminal | Save | `SENDING` | Atomically persist one pending request before MessageClient send |
| `SENDING` | Gateway success | `WAITING_RESULT` | Update only if the same request is still `SENDING` and no APPLIED result already arrived |
| `SENDING` | Gateway failure | `SEND_FAILED` | Retain the same pending request and draft |
| `SENDING` | APPLIED result first | `APPLIED_WAITING_STATE` | Retain result and pending request; later gateway completion cannot overwrite it |
| `WAITING_RESULT` or `SEND_FAILED` | Explicit retry | `SENDING` | Atomically reserve the same request; competing retry is refused |
| `APPLIED_WAITING_STATE` | Explicit retry | `SENDING` with APPLIED result retained | Replay same request; result state remains reconcilable |
| Any pending state | Matching APPLIED result and confirming phone-state, either order | `APPLIED` | Clear draft, pending request, and transient result after sequence/threshold confirmation |
| Any pending state | CONFLICT or REJECTED | corresponding terminal state | Clear pending/draft and show Mobile effective threshold until state catches up |
| `SENDING` without APPLIED result | Process recreation | `SEND_FAILED` | No automatic send; explicit retry required |
| `SENDING` with APPLIED result | Process recreation | `APPLIED_WAITING_STATE` | Preserve confirmation data; no automatic send |
| Retryable state | Reconnect only | unchanged | Never auto-send |
| Any non-`SENDING` state | Cancel | `IDLE` | Clear draft, pending request, and result |

## 5. Required regression evidence

| Matrix coverage | Required assertion |
|---|---|
| All four active statuses without pending | Sanitize to `IDLE`, clear result, map to domain, and remain unchanged on a second sanitize |
| `APPLIED_WAITING_STATE` with invalid pending | Same safe result as missing pending |
| `APPLIED_WAITING_STATE` with valid pending but missing/invalid result | Sanitize to `WAITING_RESULT`, retain pending, map to domain, and remain idempotent |
| `APPLIED_WAITING_STATE` with valid pending and valid result | Preserve state and map to domain |
| APPLIED retry with phone-state before gateway completion | Converge to `APPLIED` with one gateway request |
| Interrupted `SENDING` with and without APPLIED result | Recover to the documented retry state without auto-send |

The first four rows are IMP-017 additions. The runtime ordering rows retain the
RV-008 regression evidence and must remain in the focused Wear gate.

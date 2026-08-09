# BN-004 full-charge transition matrix

Authoritative writer: Mobile Proto DataStore. Battery callbacks, Mobile UI changes,
and Wear requests are serialized by `MobileSyncCoordinator`; the repository
persists rule, arm state, event, sequence, notification outbox, sync outbox, and
request result in one `updateData` transaction.

| Prior persisted state | Input/action | Next state | Event/request result |
|---|---|---|---|
| setting OFF | any battery reading | disarmed | no event |
| setting ON, no snapshot, charging 100 | first reading | disarmed, session active | no event |
| setting ON, no snapshot, charging below 100 | first reading | armed | no event |
| armed, same session, charging below 100 -> 100 | battery reading | disarmed | one full-charge event |
| disarmed, same session, charging 100 -> 99 -> 100 | battery readings | disarmed | no additional event |
| disarmed, non-charging observed | battery reading | disarmed, session ended | no event |
| session ended, charging starts below 100 | battery reading | armed | no event |
| setting changed OFF -> ON while charging below 100 | Mobile/Wear setting action | armed | no event |
| setting changed OFF -> ON while charging 100 | Mobile/Wear setting action | disarmed | no event |
| setting changed ON -> OFF | Mobile/Wear setting action | disarmed | no event |
| Wear expected value matches Mobile | request | atomically apply and sync | phone-state confirms |
| Wear desired value already equals Mobile | request | no setting/sequence change | current phone-state re-sent |
| Wear expected value differs from Mobile | request | preserve Mobile state | current phone-state re-sent |
| Mobile handler exists but manifest path is absent | Wear setting action | Mobile state unchanged | message is not delivered; UI appears unresponsive |
| Mobile handler and exact manifest path are present | Wear setting action | apply or preserve by expected-value rule | current phone-state confirms |
| disconnect/reconnect or process recreation | lifecycle | preserve confirmed display | no automatic send |

Callback-order invariants:

- A phone-state arriving before or after the setting result cannot make Wear
  confirm a value until the state sequence is at least the result sequence.
- An older result cannot overwrite a newer phone-state.
- Missing pending/result fields sanitize to IDLE without changing the last valid
  Mobile-confirmed value; sanitization is idempotent and domain-mappable.
- Low-battery and full-charge events use distinct DataItem paths and event kind,
  while the latest pending outbox event remains sequence ordered.
- Every supported incoming MessageClient path is registered exactly once on the
  Mobile listener service; the manifest contract test covers request-state,
  threshold change, and full-charge setting change together.

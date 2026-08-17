# Release build optimization specification

Document ID: RBO-001  
Version: 1.0  
Status: Approved  
Last updated: 2026-08-16

## 1. Scope

R8 code shrinking, optimization, obfuscation, and Android resource shrinking are
enabled for the Mobile and Wear `release` variants. The two Android projects remain
independent builds and must produce independent evidence.

The `debug` variants remain unoptimized. This work does not change application IDs,
signing, permissions, Data Layer contracts, stored data, notification rules, AdMob
IDs, native-symbol packaging, or user-visible behavior.

## 2. Build contract

Both application modules must satisfy all of the following:

- `release.isMinifyEnabled = true`.
- `release.isShrinkResources = true`.
- The optimized Android defaults come from `proguard-android-optimize.txt`.
- Project rules remain in `app/proguard-rules.pro`.
- Because both projects use AGP 8.13, each project enables the optimized resource
  shrinking pipeline with `android.r8.optimizedResourceShrinking=true`.
- Neither project sets `android.enableR8.fullMode=false`.
- Debug behavior and build-specific AdMob ID isolation remain unchanged.

## 3. Keep-rule policy

Do not add package-wide or application-wide keep rules preemptively. Dependencies'
consumer rules and the optimized Android defaults are used first. Add the narrowest
possible project rule only after a reproducible R8 build warning, missing runtime
entry point, serialization failure, or reflection failure identifies the affected
type or member.

Every corrective keep rule must record its failure signature, owning dependency or
entry point, affected check, and fix verification in the work-item state.

## 4. Runtime invariants

Optimization must not alter any existing functional contract:

- Mobile monitoring, threshold/full-charge event generation, persistence, and local
  notifications behave identically.
- Connected delivery, disconnected persistence, explicit/manual synchronization,
  and reconnection convergence remain unchanged.
- Wear validation, ordering, deduplication, Fresh/Delayed/Stale/No Data behavior,
  UI, Tile, complication, and local notifications remain unchanged.
- UMP consent gating, debug/release AdMob ID isolation, privacy options, and AdView
  lifecycle remain unchanged. Automated checks must not request production ads.
- Japanese/English resources and manifest-declared Android entry points remain
  reachable in optimized releases.

## 5. Release artifacts and retention

Each successful optimized release build must generate a non-empty ReTrace-compatible
`mapping.txt` under `app/build/outputs/mapping/release/`. Each published mapping file
belongs to exactly the application version and build that produced it and must not be
substituted with a mapping from another build.

For an Android App Bundle, the build must package the mapping metadata so Google Play
can associate it with the uploaded bundle. Generated mappings and bundles are build
evidence and are not committed to source control.

## 6. Acceptance criteria

- `AC-039`: Mobile and Wear release configurations enable code and resource
  optimization using the optimized default rules and AGP 8.13 optimized resource
  shrinking pipeline, while debug configuration and existing build IDs remain
  unchanged.
- `AC-040`: Both projects pass their JVM tests, lint, `assembleRelease`, and
  `bundleRelease`; each produces a non-empty release `mapping.txt`, and each AAB
  contains the corresponding R8 mapping metadata.
- `AC-041`: A Human smoke-tests the optimized Mobile and Wear release candidates on
  the mandatory real-device combination and observes no regression in startup,
  persistence, monitoring, Mobile/Wear synchronization, notification, Wear display,
  or Mobile consent/advertising gates. Production ads are not loaded or clicked
  during development verification.

## 7. Verification source

Android's official R8 guidance was verified on 2026-08-16:

- <https://developer.android.com/topic/performance/app-optimization/enable-app-optimization?hl=ja>


# Development Environment and Verification Workflow

Document ID: DEV-001
Version: 0.1
Status: Draft
Last updated: 2026-07-21

## Purpose

Prevent environment failures from being confused with implementation failures and keep Mobile and Wear verification reproducible across Codex, Claude Code, Android Studio, and human contributors.

## Preflight

Run the repository preflight before the first Gradle command in a new shell or agent session:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preflight.ps1
```

The script is read-only. It checks:

- Android Studio JBR or `JAVA_HOME`, with Java 17 as the minimum;
- Android SDK location, required `compileSdk` platforms, and `adb`;
- Mobile and Wear Gradle wrapper files;
- Git readability without changing global `safe.directory` configuration;
- dirty-worktree visibility;
- Mobile/Wear application ID compatibility for Data Layer testing;
- Codex sandbox conditions that require cached dependencies or in-process Kotlin compilation.

`FAIL` makes the script exit with code 1. `WARN` records a condition that does not block local JVM work but must be considered before the affected check. JSON output is available with `-Json`.

## Gradle invocation

Use the JDK reported as `RESOLVED_JAVA_HOME` as a process-local value. Do not commit a machine-specific JDK or SDK path.

When Kotlin daemon IPC is unavailable, use the Gradle project property, not a JVM `-D` property:

```powershell
$preflight = powershell -ExecutionPolicy Bypass -File .\scripts\preflight.ps1 -Json | ConvertFrom-Json
$env:JAVA_HOME = $preflight.resolvedJavaHome
.\gradlew.bat '-Pkotlin.compiler.execution.strategy=in-process' test --no-daemon --max-workers=1 --console=plain
.\gradlew.bat '-Pkotlin.compiler.execution.strategy=in-process' lint --no-daemon --max-workers=1 --console=plain
```

Use `--offline` only after the required dependency and lint artifacts are cached. A missing offline artifact is an environment condition, not an implementation failure.

If a Windows run spends several minutes in the JVM C2 compiler, confirm it with `jstack` before retrying. For that diagnosed condition, limit JIT work for the affected Gradle command:

```powershell
.\gradlew.bat '-Dorg.gradle.jvmargs=-Xmx2048m -XX:TieredStopAtLevel=1' '-Pkotlin.compiler.execution.strategy=in-process' <task> --no-daemon --offline --max-workers=1 --console=plain
```

This is an environment workaround, not a default product build setting; do not commit it to Gradle configuration without separate evidence.

## Numeric test types

The persisted ordering and timestamp fields are `Long` values. Tests must:

- suffix sequence and epoch-millisecond literals with `L`;
- unwrap nullable results before numeric assertions so JUnit selects the primitive `long` overload;
- keep percentages and schema versions as `Int`;
- avoid assertions that compare boxed `Integer` and `Long` values.

## DataStore verification levels

Use both levels:

1. An in-memory `DataStore` fake for fast host-JVM repository outcome tests.
2. Android instrumented tests using `DataStoreFactory`, the production serializer, and a real `.pb` file in the app cache directory.

`RealMobileDataStoreInstrumentedTest` covers persisted transactions, concurrent serialized updates, and corruption replacement. Compile the suite without a device using:

```powershell
.\gradlew.bat '-Pkotlin.compiler.execution.strategy=in-process' assembleDebugAndroidTest --no-daemon --offline --max-workers=1 --console=plain
```

Run it on an emulator or connected test device using:

```powershell
.\gradlew.bat '-Pkotlin.compiler.execution.strategy=in-process' connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.magicitengineer.batterynotifierandroidmobileapp.data.datastore.RealMobileDataStoreInstrumentedTest --no-daemon --offline --max-workers=1 --console=plain
```

Do not move this suite to Windows host-JVM file storage. AndroidX issue [203087070](https://issuetracker.google.com/issues/203087070) documents the Windows/Robolectric existing-file rename failure; the Android instrumented suite verifies the production storage environment instead. Verified 2026-07-21.

Passing this suite does not replace restart, process-death, migration, emulator, or real-device tests required by `docs/test-plan-and-cases.md`.

## Independent review

The implementation actor prepares evidence but does not approve its own implementation. The independent reviewer follows the work-item-specific checklist under `.agents/work-items/<id>/review-checklist.md`, records findings without patching production code, and performs the role transition required by `AGENTS.md` only after the implementation exit gate is met.

## Role-aware progress reporting

Every work session reports the active role and work while it proceeds. `AGENTS.md` is the normative rule; this section describes the daily workflow.

Use the same facts in both places:

1. Send a concise user-facing update at work start, after a material milestone or check, on a failure/blocker, before a role handoff, and at session end.
2. Update `.agents/work-items/<id>/state.yaml` under `progress_reporting.current` so Codex, Claude Code, or a human can resume without chat history.
3. Append material milestones, failures, blockers, resumptions, and handoffs to `progress_reporting.history`. Do not append routine heartbeat messages.
4. Keep the report aligned with top-level `current_role`, `current_phase`, `current_actor`, task status, evidence, loop counters, and next actions.

Use this format in the user's language:

```text
[Role: <role> | Phase: <phase> | Work item: <id> | Actor: <actor>]
Completed: <completed result or none>
In progress: <current task>
Next: <next concrete action>
Checks/Blockers: <results, blockers, or none>
```

Specification reports contract and acceptance-criteria changes. Implementation reports `IMP-*` tasks, changed behavior/files, and check outcomes. Review reports scope and findings by severity. Test reports identified environments/builds and `TC-*` result totals. Human verification reports required devices, measurements, observations, and the explicit approval decision.

Do not claim pending, blocked, not-run, or unassigned work as complete or active. Keep reports concise and never include raw secrets, personal device identifiers, or large logs.

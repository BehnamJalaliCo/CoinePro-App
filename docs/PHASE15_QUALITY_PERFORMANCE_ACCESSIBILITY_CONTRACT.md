# Phase 15 — Quality, Performance & Accessibility Contract

## Purpose

Phase 15 hardens the Android client’s quality, accessibility and performance measurement boundaries without changing server, provider, broker, AI or execution truth.

This phase adds deterministic regression gates, accessibility behavior for critical Home states, reduced-motion enforcement, Baseline Profile support and a release-like Macrobenchmark path.

## Scope boundaries

Phase 15 does not change:

- signal lifecycle or server authority
- execution submission/close semantics
- broker/provider state or outcome
- AI job/result authority
- authentication or entitlement authority
- production credentials or external provider configuration

No Phase 15 quality/performance mechanism may invent realtime state, execution success, AI progress, ROI, equity or account return.

## Regression quality gate

The cumulative Android CI remains authoritative and continues to run prior domain/controller/business-rule tests together with:

- debug and release lint
- app unit tests
- debug AndroidTest assembly
- debug and release app assembly
- benchmark variant assembly
- reduced-motion policy enforcement

Phase 15 therefore extends the existing regression gate rather than replacing earlier truth tests.

## Critical Compose UI accessibility gate

`HomeAccessibilityTest` validates four deterministic critical states on an Android emulator:

1. Cached market data is announced as stale and keeps its financial value readable in RTL.
2. A 2× font scale under RTL keeps the critical financial quote reachable and readable.
3. The offline empty state exposes a real Retry action and the action is clickable.
4. A network quote is announced as live only when the domain state is explicitly `LIVE`.

These tests exercise actual Compose semantics and visibility instead of screenshot-only presence.

## TalkBack / semantic contract

Quote cards expose a concise semantic description containing:

- instrument display name
- symbol
- explicit stale/live state
- formatted price
- source
- market type

TalkBack semantics do not upgrade stale data to live and do not infer provider state from visual styling.

## RTL and financial LTR contract

Financial values remain directionally isolated inside RTL layouts. The Home quote price keeps Latin digits and LTR ordering while the surrounding screen may be RTL.

The 2× font-scale RTL test is a required regression gate. The Home content is vertically scrollable so larger text cannot make critical quote content unreachable merely because it falls below the initial viewport.

## Reduced-motion contract

`scripts/quality/check-motion-policy.sh` is an Android CI gate.

The gate rejects continuous/infinite Compose animation primitives in app/core/feature source unless a future change is explicitly audited and the policy itself is intentionally revised.

The emulator UI job also runs with Android system animations disabled. Critical actions and state communication must remain usable without motion.

Phase 15 does not add fake urgency, fake pulsing, fake count-up or decorative animation that implies changing provider state.

## Signature-state golden policy

Hosted Android emulators are not treated as a deterministic pixel renderer for hard screenshot/golden acceptance because font rasterization, rendering backend and emulator image changes can produce non-product visual diffs.

For Phase 15, deterministic semantic/state assertions are the hard signature-state golden gate: stale/cache truth, live truth, offline actionability, RTL financial direction and large-font reachability are locked by Compose instrumentation tests.

A future pinned screenshot renderer may add pixel goldens, but pixel snapshots must not replace semantic truth assertions.

## Baseline Profile contract

The app includes `androidx.profileinstaller` and checks in `app/src/main/baseline-prof.txt` as an explicit startup optimization seed.

The `benchmark` module includes `BaselineProfileGenerator` so the profile can be regenerated/extended from real app startup behavior rather than hand-waving optimization claims.

The existence of a profile is not itself evidence that a startup target has been met.

## Macrobenchmark / startup measurement contract

The app defines a `benchmark` build type derived from release behavior:

- release-like
- minified/resource-shrunk through release inheritance
- target app remains `isDebuggable = false`
- debug signing is used only so CI/local benchmark artifacts can be installed

The manifest exposes `profileable` shell access for profiling without making the target app debuggable.

The separate `benchmark` test APK is debug-signed only for instrumentation installation and may be debuggable; it is not a production artifact.

`StartupBenchmark` measures cold startup using Macrobenchmark when run as a real benchmark.

## Hosted CI performance rule

GitHub-hosted emulator timing is not reference-device performance evidence.

CI runs Macrobenchmark in dry-run/smoke mode to prove that:

- the release-like target app can be built and installed
- the benchmark test APK can be built and installed
- instrumentation wiring is valid
- Macrobenchmark rules can execute without configuration breakage

No hosted-emulator latency is promoted to a product performance claim.

## Reference-device performance budget

The following values are engineering acceptance targets for a controlled physical Pixel 6-class-or-better reference device using the release-like benchmark build. They are targets, not measurements from hosted CI:

- cold-start median target: at or below 2.0 seconds
- cold-start p95 target: at or below 3.0 seconds
- critical Home/Signals/Activity interaction jank target: no more than 5% janky frames in the controlled trace

A release candidate must record physical-device evidence before these targets may be described as achieved. Phase 15 establishes the measurement contract and budget; it does not fabricate reference-device results.

## Security interaction

Phase 15 dependencies and build changes remain subject to Security CI.

The benchmark/profile additions must continue to pass:

- tracked-secret scanning
- resolved dependency OSV audit
- debug/release BuildConfig isolation checks

No production signing key, vendor secret or broker credential is introduced by benchmark tooling.

## Validation checkpoint

Code checkpoint:

- branch: `feat/phase15-quality-performance-accessibility`
- SHA: `97d3ebc0165be27e86ad97dceef16494f7a7b428`
- Android CI Run #208: **success**
- Security CI Run #40: **success**

Run #208 passed the cumulative lint/test/build gate, reduced-motion policy, four Compose accessibility tests and the benchmark wiring dry-run.

## Explicit non-claims

Phase 15 does not claim:

- that hosted-emulator startup timing represents a physical production device
- that Macrobenchmark dry-run proves a latency/jank target was met
- that a checked-in Baseline Profile guarantees startup performance
- that pixel screenshot rendering is deterministic across hosted emulator images
- that accessibility tests cover every authenticated/external-provider end-to-end path
- that any execution, broker, provider or AI state changed because of quality/performance work

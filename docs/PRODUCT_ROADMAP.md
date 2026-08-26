# CoinePro Android Product Roadmap

Status: Phases 0 through 16 are Closed / Complete at validated final Heads. Phase 17 repository/client implementation and Phase 1–17 reconciliation are implemented on `feat/phase17-launch-readiness`; final closure still requires the exact final documentation Head to pass Android CI and Security CI plus any explicitly required external production evidence recorded in `PHASE17_EVIDENCE_LEDGER.md`.

The canonical phase-to-branch/SHA/CI mapping lives in `PHASE_INDEX.md`. Detailed truth/API/security rules live in each phase contract document.

## Repository and delivery model

- Source-of-truth repository: `BehnamJalaliCo/CoinePro-App`
- `main` is the stable base.
- `bootstrap/android-foundation` is the cumulative integration branch.
- Every phase branch, PR, CI check and phase ledger entry for this project stays in this repository.
- PRs remain Draft and unmerged unless merge is explicitly requested.
- Android never stores production vendor/broker secrets in the repository.
- External legal/provider/production evidence is recorded separately from deterministic repository/client validation and is never fabricated from code or mocks.

## Completed foundation — Phases 0 through 6

### Phase 0 — Foundation
Status: Complete

Native Kotlin/Jetpack Compose app, Gradle/version catalog, GitHub Actions lint/test/debug build, public-repository secret safeguards and application shell.

### Phase 1 — Design System & Architecture Skeleton
Status: Complete

Core module boundaries, five-destination shell (Home / Signals / AI / Tools / Activity), RTL conventions and LTR financial formatting.

### Phase 2 — Authentication, Session & Entitlements
Status: Complete

Keystore-backed session protection, authenticated profile revalidation, Telegram auth bridge, token redaction, unauthorized clearing, server-truth entitlements and environment-specific debug/staging/production API configuration.

### Phase 3 — Realtime Market Data Foundation
Status: Complete

Normalized HTTP/WebSocket quote contract, reconnect/fallback, stale/fresh state, source timestamps, product-scope validation and no fake `LIVE` state. Freshness is LBank 15 s, Finnhub 90 s and unknown-source 30 s with future-skew rejection.

### Phase 4 — Signals Core
Status: Complete

Typed Signal list/detail flow, Active/Recent/Closed states, Entry/SL/TP/R:R/confidence, missing-field safety, positive persisted signal IDs and product scope locked to Forex V1 `XAUUSD/XAGUSD` plus Crypto `*USDT`.

### Phase 5 — Alerts & Push
Status: Complete

Notification Center, push preferences, price alerts, FCM lifecycle, native notification channels, permission flow and validated deep links/payloads. Notification/deep-link Signal navigation accepts only positive persisted signal IDs.

### Phase 6 — Connections & Signal Execution Bridge
Status: Complete

MT5/LBank connection state, signal-scoped execution confirmation, quantity validation, idempotency request IDs, positive Signal identity validation, server/provider-truth execution states and duplicate-close protection.

## Phase 7 — AI Generated Market Signal

Status: Complete

Delivered:
- typed AI Signal job contract and server-truth lifecycle
- product/timeframe/risk controls and entitlement/quota state
- strict structured result validation
- no fake progress or client-invented completion
- raw model text is never executable
- only persisted positive server `signal_id` can continue to Signal Detail
- no direct execution from the AI screen

Contract: `docs/PHASE7_AI_SIGNAL_CONTRACT.md`

## Phase 8 — AI Vision Flagship

Status: Complete

Delivered:
- CameraX + gallery/document input
- orientation normalization, resize/compression and outbound EXIF stripping
- structured server-truth AI Vision lifecycle
- actionable/low-confidence/unknown/unsupported states
- strict trade geometry/product validation
- only validated persisted positive server Signal can become actionable
- AI Vision never executes directly

Contract: `docs/PHASE8_AI_VISION_CONTRACT.md`

## Phase 9 — AI Assistant

Status: Complete

Delivered:
- typed contextual chat with structured context scopes
- source/as-of/freshness provenance
- stable conversation identity and explicit server history policy
- no invented positions, signals or execution state
- only verified active-signal context with a positive persisted Signal ID can open Signal Detail
- transcript clears on logout/session loss

Contract: `docs/PHASE9_AI_ASSISTANT_CONTRACT.md`

## Phase 10 — News & Economic Calendar

Status: Complete

Delivered:
- typed normalized news/calendar domain
- strict timestamp parsing and stale truth
- explicit impact/sentiment unknown states
- Gold/Silver/Crypto relevance filters
- HTTPS-only article URL data
- high-impact active-signal risk context without prediction or execution instruction
- News/Calendar routes from Tools

Contract: `docs/PHASE10_MARKET_INTELLIGENCE_CONTRACT.md`

## Phase 11 — Trader Tools

Status: Complete

Delivered:
- premium Trader Toolkit
- risk, position-size, R:R, profit, pip, crypto PnL, compound and drawdown calculators
- deterministic finite-number validation
- visible formulas/units/assumptions/precision
- explicit LTR financial output inside RTL
- calculators remain fully isolated from execution

Contract: `docs/PHASE11_TRADER_TOOLS_CONTRACT.md`

## Phase 12 — Activity, History & Performance

Status: Complete

Final closure milestone: `feat/phase12-activity-history-performance` → `23f7113d83acdcfda74798380f04da1c7447be9f` → Run #132 success.

Delivered:
- premium server-evidence Activity / Performance dashboard
- paginated CLOSED signal history across Forex and Crypto
- explicit loaded/expected counts and incomplete-coverage state
- market/instrument/result filters over loaded history
- Win/Loss/Breakeven/Missing classifications from explicit evidence only
- TP/SL rates with explicit denominators and nullable target-hit evidence
- average planned R:R from finite positive server evidence
- full server execution ledger kept separate from performance
- no inferred ROI, equity, broker P&L or execution outcome
- LTR financial values inside RTL layouts

Contract: `docs/PHASE12_ACTIVITY_HISTORY_PERFORMANCE_CONTRACT.md`

## Phase 13 — Offline, Reliability & Background Work

Status: Complete

Final closure milestone: `feat/phase13-offline-reliability-background-work` → `a6b664f035e047afd51515b3481452d57ecd1ee9` → Run #159 success.

Delivered:
- `core:database` Room 2.8.4 safe read-cache boundary
- cached market snapshots that always restore as explicit stale/cache evidence
- independent cache product-scope and finite-number validation for market rows and Signal history
- CLOSED signal-history cache with live quote/live P&L authority intentionally removed
- nullable target-hit evidence preserved through cache round-trips
- cache-aware signal-history fallback with explicit provenance, storage time and refresh errors
- successful server refresh replaces cached history
- membership loss/logout clears account-scoped history cache
- authenticated app-resume synchronization across server-backed read surfaces
- WorkManager 2.11.2 durable **read-only** synchronization
- periodic network + battery-not-low constraints and immediate network constraint
- unique work scheduling plus update/replace semantics for scheduler idempotency
- exponential retry for transient read failures
- missing session is a no-op, not a retry loop
- process-death worker hydration from existing secure token storage into temporary memory only
- HTTP 401 expires session state instead of retrying stale credentials
- HiltWorkerFactory on-demand WorkManager initialization without lint suppression
- no execution, close, broker write, AI job creation or fake provider state from background work
- Phase 13 reliability tests included in cumulative Android CI

Contract: `docs/PHASE13_OFFLINE_RELIABILITY_BACKGROUND_CONTRACT.md`

## Phase 14 — Security Hardening

Status: Complete

Final closure milestone: `feat/phase14-security-hardening` → `8abdb6909beb2468ec10c911ebd22ad8411a1b5f` → Android Run #184 success + Security Run #16 success.

Delivered:
- tracked-secret scanning for private keys, common token signatures and forbidden local secret/config files
- resolved runtime dependency export and OSV vulnerability audit independent of GitHub Dependency Graph availability
- explicit vulnerability exception ledger with no silent suppressions
- Retrofit HTTPS-only boundary plus manifest/network-security cleartext denial
- release HTTP logging disabled; debug is opt-in BASIC only with sensitive-header redaction
- explicit system-CA certificate policy and a deliberate no-fake-pinning decision until production domains/backup pins/rotation ownership exist
- generated BuildConfig cross-variant isolation verified in Security CI
- release explicitly non-debuggable, minified and resource-shrunk
- explicit execution 429 state and one-call/no-auto-retry trading-write policy
- execution and AI Vision upload threat-model review
- AI Vision EXIF stripping and app-owned camera-temp deletion boundaries preserved
- root/debug/tamper policy based on server authority rather than bypassable client-only trust claims
- session/cache/assistant/image privacy and retention rules documented without inventing server retention

Contract: `docs/PHASE14_SECURITY_HARDENING_CONTRACT.md`

## Phase 15 — Quality, Performance & Accessibility

Status: Complete

Code milestone: `feat/phase15-quality-performance-accessibility` → `97d3ebc0165be27e86ad97dceef16494f7a7b428` → Android Run #208 success + Security Run #40 success.
Final closure Head: `a8d26b7df6332f569f963756a2e041ad31b3cdab` → Android Run #212 success + Security Run #44 success.

Delivered:
- cumulative prior domain/controller/business-rule tests preserved as regression gates
- four Compose UI accessibility tests for cached stale truth, offline Retry, explicit network LIVE semantics and 2× font-scale RTL quote reachability
- Home large-text accessibility fix via vertical scrolling rather than weakening the test
- TalkBack quote semantics with instrument, symbol, stale/live state, price, source and market
- explicit LTR financial values inside RTL at large font scale
- CI-enforced reduced-motion policy
- deterministic semantic/state signature-state gate without false hosted-emulator pixel-golden claims
- ProfileInstaller plus checked-in Baseline Profile seed targeting the current `CoineProThemeKt` class
- dedicated `benchmark` module with Baseline Profile generation and cold-start Macrobenchmark
- release-like non-debuggable benchmark target app; signing only for installability
- hosted CI Macrobenchmark dry-run verifies wiring without promoting emulator latency to a performance claim
- explicit physical reference-device cold-start and jank target budget without a false measured claim

Contract: `docs/PHASE15_QUALITY_PERFORMANCE_ACCESSIBILITY_CONTRACT.md`

## Phase 16 — Release Engineering

Status: Closed / Complete.

Code milestone: `feat/phase16-release-engineering` → `0681a763cf504275b60e50495d3c64d13f73ac79` → Android Run #226 success + Security Run #58 success.
Final documentation Head: `5a1a02daf72acc60581665b3aee27dec713b400c` → Android Run #230 success + Security Run #62 success.

Delivered:
- semantic `versionName` validation plus positive/range-safe Android `versionCode` validation
- Play remains authoritative for cross-release `versionCode` monotonicity against existing release history
- dedicated debug, staging, production and benchmark configuration namespaces
- distinct staging application identity and non-production endpoint boundary
- protected signing configuration requiring the complete signing tuple
- ephemeral CI JKS signing smoke proving a signed release AAB without production key material
- manual Play Console internal-track workflow targeting protected `play-internal`
- runner-temp upload keystore with cleanup
- Android Publisher edit/upload/internal-track/commit flow with incomplete-edit cleanup
- changelog/release-note policy
- Android Vitals / Play Console baseline crash/ANR decision without a new telemetry SDK before privacy review
- cumulative Android CI for debug/staging/release/benchmark plus protected signing
- Security CI for secret/dependency/BuildConfig-isolation gates

Contract: `docs/PHASE16_RELEASE_ENGINEERING_CONTRACT.md`

## Phase 17 — Launch Readiness

Status: Repository/client implementation complete; final exact-Head CI and external production evidence are tracked independently in `docs/PHASE17_EVIDENCE_LEDGER.md`.

Delivered in the repository:
- launch/safety education surface
- notification permission education before request and denial recovery path
- Camera permission remains user-action scoped with gallery/file fallback
- connection setup education that separates configured credentials from provider-confirmed state
- trading/AI/provider-truth risk disclosures without regulatory approval claims
- support/feedback share-sheet path with safe app metadata only
- analytics explicitly disabled rather than introducing an unreviewed telemetry SDK
- incident/rollback runbook
- production read-only smoke workflow and sanitizer
- market smoke freshness checks matched to Phase 3 source thresholds
- positive persisted Signal ID invariant reconciled across Signals, notifications, deep links, AI and execution
- cache product-scope boundary reconciled with Phase 3/4 product scope
- Baseline Profile class reconciled with current design-system source
- deterministic Phase 1–17 repository consistency gate
- full cross-phase audit document

External runtime/legal evidence is not manufactured from repository state. It remains visible in the evidence ledger until supplied by the appropriate protected production/provider/legal source.

Contract: `docs/PHASE17_LAUNCH_READINESS_CONTRACT.md`
Audit: `docs/PHASE1_17_CROSS_PHASE_AUDIT.md`
Evidence: `docs/PHASE17_EVIDENCE_LEDGER.md`
Runbook: `docs/PHASE17_INCIDENT_RUNBOOK.md`

## Product module map

```text
app
benchmark
core:common
core:model
core:network
core:datastore
core:navigation
core:designsystem
core:auth
core:security
core:marketdata
core:chart
core:help
core:symbols
core:signals
core:notifications
core:execution
core:copytrade
core:portfolio
core:academy
core:aisignal
core:aivision
core:aiassistant
core:marketintel
core:account
core:guest
core:diagnostics
core:database
feature:admin
feature:auth
feature:home
feature:search
feature:chart
feature:portfolio
feature:academy
feature:terminal
feature:signals
feature:signal-detail
feature:connections
feature:execution
feature:kyc
feature:account
feature:guest
feature:copytrade
feature:ai
feature:ai-vision
feature:ai-assistant
feature:news
feature:calendar
feature:tools
feature:activity
```

This block must match `settings.gradle.kts` exactly; `scripts/quality/check-cross-phase-consistency.py` enforces that invariant in CI. Empty or phantom architecture modules are not listed.

## Cross-cutting contracts

1. Authentication/session — bearer session + authenticated profile validation; no client-invented refresh flow
2. Entitlements — authenticated server profile is source of truth
3. Signal schema/lifecycle — normalized in Phase 4; persisted Signal IDs are positive across downstream features
4. Realtime quote schema — normalized in Phase 3; production read-only smoke uses the same freshness thresholds
5. FCM device + alert schema — client implemented in Phase 5; Signal links accept only positive IDs
6. Signal-scoped execution contract — client safety boundary implemented in Phase 6; provider status remains server-owned
7. AI Signal job schema — Phase 7 server-truth lifecycle and persisted-Signal trust boundary
8. AI Vision upload/job/result schema — Phase 8 privacy preprocessing and persisted-Signal trust boundary
9. AI Assistant contextual chat schema — Phase 9 context provenance, stable conversation identity and explicit history policy
10. News/calendar timestamps and impact schema — Phase 10 timestamp/stale/unknown truth boundaries and active-signal risk context
11. Trader Tools formulas — Phase 11 local deterministic contract with no execution side effect
12. Activity/performance evidence — Phase 12 explicit denominators, history coverage truth and no ROI/equity inference
13. Offline/reliability boundary — Phase 13 safe read caches, independent product-scope validation, explicit stale/cache provenance and read-only durable sync
14. Security hardening boundary — Phase 14 secret/dependency gates, release transport/logging policy, build-config isolation, explicit rate limits and threat models
15. Quality/performance/accessibility boundary — Phase 15 semantic critical-state tests, RTL/large-font reachability, reduced-motion policy and current Baseline Profile/Macrobenchmark wiring
16. Release-engineering boundary — Phase 16 signing/version/build-environment isolation, internal-track publishing, changelog and crash/ANR monitoring decision
17. Launch-readiness/reconciliation boundary — Phase 17 education/runbook/evidence tooling plus a deterministic Phase 1–17 consistency gate; external evidence remains evidence-based, never inferred

## Definition of Done

A client feature is not Done until:
- its API contract is explicit
- loading/empty/error/offline behavior exists where applicable
- RTL and financial LTR formatting are handled
- security/privacy/logging implications are reviewed
- tests cover critical business rules
- Android CI and any phase-specific required CI are green
- no fake realtime, execution or AI progress state exists
- cross-phase invariants remain compatible with prior contracts

External legal/provider/production verification is additionally evidence-gated by Phase 17 and cannot be substituted by a client-side pass.

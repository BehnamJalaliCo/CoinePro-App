# CoinePro Android Product Roadmap

Status: Phases 0 through 14 delivered at validated code checkpoints — Phase 15 starts only after the final Phase 14 documentation/security-verifier Head is green.

The canonical phase-to-branch/SHA/CI mapping lives in `PHASE_INDEX.md`. Detailed truth/API/security rules live in each phase contract document.

## Repository and delivery model

- Source-of-truth repository: `BehnamJalaliCo/CoinePro-App`
- `main` is the stable base.
- `bootstrap/android-foundation` is the cumulative integration branch.
- Every phase branch, PR, CI check and phase ledger entry for this project stays in this repository.
- PRs remain Draft and unmerged unless merge is explicitly requested.
- Production vendor credentials, broker connectivity, IP whitelisting and real external smoke testing remain Phase 17 unless explicitly moved earlier.
- Android never stores production vendor/broker secrets in the repository.

## Completed foundation — Phases 0 through 6

### Phase 0 — Foundation
Status: Complete

Native Kotlin/Jetpack Compose app, Gradle/version catalog, GitHub Actions lint/test/debug build, public-repository secret safeguards and application shell.

### Phase 1 — Design System & Architecture Skeleton
Status: Complete

Core module boundaries, five-destination shell (Home / Signals / AI / Tools / Activity), RTL conventions and LTR financial formatting.

### Phase 2 — Authentication, Session & Entitlements
Status: Complete

Keystore-backed session protection, authenticated profile revalidation, Telegram auth bridge, token redaction, unauthorized clearing and server-truth entitlements.

### Phase 3 — Realtime Market Data Foundation
Status: Complete

Normalized HTTP/WebSocket quote contract, reconnect/fallback, stale/fresh state, source timestamps, product-scope validation and no fake `LIVE` state.

### Phase 4 — Signals Core
Status: Complete

Typed Signal list/detail flow, Active/Recent/Closed states, Entry/SL/TP/R:R/confidence, missing-field safety and product scope locked to Forex V1 `XAUUSD/XAGUSD` plus Crypto `*USDT`.

### Phase 5 — Alerts & Push
Status: Complete

Notification Center, push preferences, price alerts, FCM lifecycle, native notification channels, permission flow and validated deep links/payloads.

### Phase 6 — Connections & Signal Execution Bridge
Status: Complete

MT5/LBank connection state, signal-scoped execution confirmation, quantity validation, idempotency request IDs, server/provider-truth execution states and duplicate-close protection.

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
- only validated persisted server Signal can become actionable
- AI Vision never executes directly

Contract: `docs/PHASE8_AI_VISION_CONTRACT.md`

## Phase 9 — AI Assistant

Status: Complete

Delivered:
- typed contextual chat with structured context scopes
- source/as-of/freshness provenance
- stable conversation identity and explicit server history policy
- no invented positions, signals or execution state
- only verified active-signal context can open a persisted Signal
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
- cache product-scope and finite-number validation
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

Status: Complete at validated code checkpoint; final documentation/security-verifier Head CI pending.

Code milestone: `feat/phase14-security-hardening` → `ed568e8672ef1c112f874f85411a11e0c6e4b7fb` → Android Run #178 success + Security Run #10 success.

Delivered:
- tracked-secret scanning for private keys, common token signatures and forbidden local secret/config files
- resolved debug/release runtime dependency export and OSV vulnerability audit independent of GitHub Dependency Graph availability
- explicit vulnerability exception ledger with no silent suppressions
- Retrofit HTTPS-only boundary plus manifest/network-security cleartext denial
- release HTTP logging disabled; debug is opt-in BASIC only with sensitive-header redaction
- explicit system-CA certificate policy and a deliberate no-fake-pinning decision until production domains/backup pins/rotation ownership exist
- debug and release service configuration separated into different Gradle property namespaces
- generated BuildConfig cross-variant isolation verified in Security CI using distinct markers
- release explicitly non-debuggable, minified and resource-shrunk
- Android CI now gates both debug and release lint/assembly
- explicit execution 429 state and one-call/no-auto-retry trading-write policy
- execution and AI Vision upload threat-model review
- AI Vision EXIF stripping and app-owned camera-temp deletion boundaries preserved
- root/debug/tamper policy based on server authority rather than bypassable client-only trust claims
- session/cache/assistant/image privacy and retention rules documented without inventing server retention

Contract: `docs/PHASE14_SECURITY_HARDENING_CONTRACT.md`

Exit state:
- Android Run #178 passed cumulative unit tests, debug/release lint, app tests, debug/release assembly and debug APK upload
- Security Run #10 passed tracked-secret scan and resolved dependency OSV audit
- final Phase 14 documentation/security-verifier Head must pass both Android CI and Security CI before Phase 15 begins

## Phase 15 — Quality, Performance & Accessibility

Status: Next after final Phase 14 closure CI

Deliverables:
- domain/ViewModel tests
- Compose UI tests for critical flows
- screenshot/golden tests for signature states
- baseline profile/startup measurement
- RTL stress testing
- font scaling
- reduced motion
- TalkBack labels

Exit criteria:
- critical flow matrix green
- financial values do not clip at supported font scales
- startup/jank budget documented

## Phase 16 — Release Engineering

Status: Planned

Deliverables:
- protected release signing
- versioning strategy
- staging vs production build configuration
- Play Console internal testing pipeline
- release notes/changelog
- crash/ANR monitoring decision

Exit criteria:
- reproducible signed release
- signing key never enters repository
- staging cannot accidentally execute against production accounts

## Phase 17 — Launch Readiness

Status: Planned — final external/runtime phase

Deliverables:
- onboarding and permission education
- connection setup education
- legal/risk disclosures
- support/feedback path
- privacy-reviewed analytics events
- incident/runbook preparation
- production vendor domain and credential configuration
- production IP whitelist verification where required
- real external market-data connectivity smoke tests
- real broker/exchange execution lifecycle verification

Exit criteria:
- final production market sources verified
- end-to-end login → signal → execution confirmation → tracked result smoke
- AI Vision image → validated analysis → eligible action smoke
- provider close lifecycle verified before enabling any deferred Close CTA
- rollback/disable switches defined

## Product module map

```text
app
core:common
core:model
core:designsystem
core:network
core:datastore
core:auth
core:security
core:marketdata
core:signals
core:notifications
core:execution
core:aisignal
core:aivision
core:aiassistant
core:marketintel
core:database
core:testing
feature:auth
feature:home
feature:signals
feature:signal-detail
feature:execution
feature:connections
feature:ai
feature:ai-vision
feature:ai-assistant
feature:news
feature:calendar
feature:tools
feature:activity
feature:profile
```

Create modules when boundaries become useful; do not create empty architecture for its own sake.

## Cross-cutting contracts

1. Authentication/session — bearer session + authenticated profile validation; no client-invented refresh flow
2. Entitlements — authenticated server profile is source of truth
3. Signal schema/lifecycle — normalized in Phase 4; execution extends it in Phase 6
4. Realtime quote schema — normalized in Phase 3; production external activation in Phase 17
5. FCM device + alert schema — client implemented in Phase 5
6. Signal-scoped execution contract — client safety boundary implemented in Phase 6; live provider validation Phase 17
7. AI Signal job schema — Phase 7 server-truth lifecycle and persisted-Signal trust boundary
8. AI Vision upload/job/result schema — Phase 8 privacy preprocessing and persisted-Signal trust boundary
9. AI Assistant contextual chat schema — Phase 9 context provenance, stable conversation identity and explicit history policy
10. News/calendar timestamps and impact schema — Phase 10 timestamp/stale/unknown truth boundaries and active-signal risk context
11. Trader Tools formulas — Phase 11 local deterministic contract with no execution side effect
12. Activity/performance evidence — Phase 12 explicit denominators, history coverage truth and no ROI/equity inference
13. Offline/reliability boundary — Phase 13 safe read caches, explicit stale/cache provenance, read-only durable sync, retry/idempotency rules and no background execution side effects
14. Security hardening boundary — Phase 14 secret/dependency gates, release transport/logging policy, build-config isolation, explicit rate limits, threat models and privacy/retention non-claims

## Definition of Done

A client feature is not Done until:
- its API contract is explicit
- loading/empty/error/offline behavior exists where applicable
- RTL and financial LTR formatting are handled
- security/privacy/logging implications are reviewed
- tests cover critical business rules
- Android CI and any phase-specific required CI are green
- no fake realtime, execution or AI progress state exists

External production connectivity is additionally gated by Phase 17.

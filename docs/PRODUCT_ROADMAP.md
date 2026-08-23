# CoinePro Android Product Roadmap

Status: Phases 0 through 8 complete — Phase 9 next

The canonical phase-to-branch/SHA/CI mapping lives in `PHASE_INDEX.md`.

## Repository and delivery model

- Source-of-truth repository: `BehnamJalaliCo/CoinePro-App`
- `main` is the stable base.
- `bootstrap/android-foundation` is the cumulative integration branch.
- Every phase branch, PR, CI check and phase ledger entry for this project stays in this repository.
- Completed phase checkpoints are recorded in `PHASE_INDEX.md`.
- Production vendor credentials, broker connectivity, IP whitelisting and real external smoke testing remain Phase 17 unless explicitly moved earlier.
- Android never stores production vendor/broker secrets in the repository.

## Completed foundation — Phases 0 through 6

### Phase 0 — Foundation
Status: Complete

Native Kotlin/Jetpack Compose app, Gradle/version catalog, GitHub Actions lint/test/debug build, public-repository secret safeguards and application shell.

### Phase 1 — Design System & Architecture Skeleton
Status: Complete

Design direction, core design/model/common/network/datastore/navigation boundaries, five-destination shell (Home / Signals / AI / Tools / Activity), RTL layout conventions and LTR financial formatting.

### Phase 2 — Authentication, Session & Entitlements
Status: Complete

Milestone: `feat/android-mobile-auth` → `12cc837ac02e378f3ca4452a95bfed224ad3222b` → Run #11 success.

Keystore-backed session protection, authenticated profile revalidation, Telegram auth bridge, token redaction, unauthorized clearing, entitlement truth from server profile and no invented refresh-token flow.

### Phase 3 — Realtime Market Data Foundation
Status: Complete

Milestone: `feat/phase3-realtime-market-data` → `7158a78ef6ee378ec531576bf7d9364816d25b56` → Run #14 success.

Normalized CoinePro HTTP/WebSocket quote contract, resilient reconnect/fallback, stale/fresh state, source timestamps, product-scope validation and no fake `LIVE` state.

### Phase 4 — Signals Core
Status: Complete

Milestone: `feat/phase4-signals-core` → `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` → Run #17 success.

Typed Signal list/detail flow, Active/Recent/Closed filters, Entry/SL/TP/R:R/confidence, safe missing fields, current/last quote state and product scope locked to Forex V1 `XAUUSD/XAGUSD` plus Crypto `*USDT`.

### Phase 5 — Alerts & Push
Status: Complete

Milestone: `feat/phase5-alerts-push` → `60dfd64259ec92775b38288f2a4dc8e4c50169e9` → Run #41 success.

Notification Center, preferences, price alerts, FCM token lifecycle, native messaging service/channel, Android 13+ permission flow, deterministic deep links and validated alert payloads.

### Phase 6 — Connections & Signal Execution Bridge
Status: Complete

Audited milestone: `feat/phase6-signal-execution` → `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26` → Run #86 success.

MT5/LBank connection state, signal-scoped execution confirmation, quantity validation, idempotency request IDs, explicit provider-truth execution states, active executed signals, duplicate-close prevention, safe LBank close gating and no Android persistence of trading credentials.

## Phase 7 — AI Generated Market Signal

Status: Complete

Milestone: `feat/phase7-ai-generated-market-signal` → `f718d9ad310ab37d4b109297c4fadcb33e287775` → Run #91 success.

Delivered:
- `core:aisignal` domain, Retrofit gateway and server-truth controller
- explicit authenticated contract in `docs/PHASE7_AI_SIGNAL_CONTRACT.md`
- product-scoped symbol controls
- timeframe controls: M15 / H1 / H4 / D1
- risk controls: low / medium / high
- server-derived quota and entitlement states
- exact queued / running / done / failed / expired lifecycle
- polling based only on server status; no fake completion percentage
- failed/expired recovery
- strict structured-result validation
- result must be `validated=true` and match request symbol/timeframe
- invalid direction/prices/targets/confidence/product scope are blocked
- raw model text is never executable
- valid result can only open its persisted positive `signal_id`
- no direct execution from AI screen; action remains Signal Detail → Execution

## Phase 8 — AI Vision Flagship

Status: Complete

Milestone: `feat/phase8-ai-vision` → `10844e48e65b90e9bcd8d60bb5c7ecfea982c18b` → Run #97 success.

Delivered:
- `core:aivision` typed multimodal upload/job/result contract
- `feature:ai-vision` native CameraX and picker UI
- back-camera capture with camera hardware optional
- Android document/gallery picker without broad storage permission
- screenshot/image upload
- EXIF orientation normalization and outbound metadata stripping through JPEG re-encoding
- maximum 2048 px image edge with adaptive compression under 6 MB
- exact queued / running / done / failed / expired server-truth lifecycle
- explicit actionable / low-confidence / unknown / unsupported assessments
- structured fields: symbol/timeframe/confidence, trend/bias, market structure/setup, direction, entry zone, stop loss, TP1/TP2/TP3, risk and concise reasoning
- strict result validation, including trade geometry and product scope
- low-confidence/unknown/unsupported outputs cannot carry an executable signal
- actionable output requires `validated=true` and a positive persisted server `signal_id`
- AI Vision never executes directly; eligible action opens the persisted Signal flow
- privacy/trust/API contract in `docs/PHASE8_AI_VISION_CONTRACT.md`
- Phase 8 mapper/controller tests in cumulative Android CI

Exit state:
- EXIF/privacy rules defined
- unclear/unsupported images have explicit state
- no fake multimodal progress
- raw or unvalidated model output cannot execute
- Run #97 passed Phase 8 tests, all prior core tests, app lint, app tests, debug assembly and APK upload

## Phase 9 — AI Assistant

Status: Next

Deliverables:
- contextual authenticated chat
- active signal context
- current market context
- news/calendar context
- risk/tool context
- freshness/source labels where available

Exit criteria:
- no invented active positions/signals
- relevant context freshness is visible
- conversation history policy defined

## Phase 10 — News & Economic Calendar

Status: Planned

Deliverables:
- market news feed
- sentiment/impact labels from structured service output
- economic calendar
- Low/Medium/High impact
- actual/forecast/previous when available
- Gold/Silver/Crypto relevance
- high-impact warning attached to active signals

Exit criteria:
- event/publication times normalized correctly
- stale/unknown impact not presented as certainty

## Phase 11 — Trader Tools

Status: Planned

Deliverables:
- Risk Calculator
- Position Size / Lot Calculator
- Risk/Reward
- Profit Calculator
- Pip Calculator
- Crypto PnL
- Compound Calculator
- Drawdown Simulator

Exit criteria:
- deterministic formula tests
- precision/contract assumptions documented
- calculations remain separate from execution

## Phase 12 — Activity, History & Performance

Status: Planned

Deliverables:
- executed-signal history
- signal history
- market/instrument/result filters
- total signals, win rate, TP hit rates, SL rate and average R:R
- explicit denominator and no-record state

Exit criteria:
- never infer ROI/equity without source data
- losses receive equal visual prominence
- zero and no-record are distinct

## Phase 13 — Offline, Reliability & Background Work

Status: Planned

Deliverables:
- Room cache for safe read models
- WorkManager durable sync
- offline/stale states
- app-resume synchronization
- retry policy and idempotent background operations

Exit criteria:
- offline mode never pretends execution succeeded
- stale market data remains explicit
- background work respects battery/network constraints

## Phase 14 — Security Hardening

Status: Planned

Deliverables:
- secret scan in CI
- dependency vulnerability review
- release network security config
- certificate strategy decision
- complete log redaction review
- root/debug/tamper policy based on threat model
- API abuse/rate-limit handling
- privacy/data-retention documentation

Exit criteria:
- no secrets in artifacts/logs
- execution and image-upload threat model reviewed
- production credentials isolated from debug builds

## Phase 15 — Quality, Performance & Accessibility

Status: Planned

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
7. AI Signal job schema — client implemented in Phase 7 with server-truth lifecycle and persisted-Signal trust boundary
8. AI Vision upload/job/result schema — client implemented in Phase 8 with image privacy preprocessing, structured assessments and persisted-Signal trust boundary
9. News/calendar timestamps and impact schema — Phase 10

## Definition of Done

A client feature is not Done until:
- its API contract is explicit
- loading/empty/error/offline behavior exists where applicable
- RTL and financial LTR formatting are handled
- security/privacy/logging implications are reviewed
- tests cover critical business rules
- Android CI is green
- no fake realtime, execution or AI progress state exists

External production connectivity is additionally gated by Phase 17.

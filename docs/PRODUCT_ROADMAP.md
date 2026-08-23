# CoinePro Android Product Roadmap

Status: Phases 0 through 11 complete — Phase 12 next

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

Final audited milestone: `feat/phase8-ai-vision` → `85ed5a681b9f3a548fdc1d30faeea8dacb3d88b1` → Run #101 success.

Delivered:
- `core:aivision` typed multimodal upload/job/result contract
- `feature:ai-vision` native CameraX and picker UI
- back-camera capture with camera hardware optional
- Android document/gallery picker without broad storage permission
- screenshot/image upload
- EXIF orientation normalization and outbound metadata stripping through JPEG re-encoding
- temporary CameraX cache capture deleted after preparation, including error paths
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
- EXIF/privacy rules defined and camera temp files do not linger after preprocessing
- unclear/unsupported images have explicit state
- no fake multimodal progress
- raw or unvalidated model output cannot execute
- Run #101 passed Phase 8 tests, all prior core tests, app lint, app tests, debug assembly and APK upload

## Phase 9 — AI Assistant

Status: Complete

Final audited milestone: `feat/phase9-ai-assistant` → `3d158c9d0fc72724e9bbf402ae81540300950cc3` → Run #114 success.

Delivered:
- `core:aiassistant` typed contextual-chat domain, authenticated Retrofit gateway and in-memory controller
- `feature:ai-assistant` native Compose chat surface linked from the AI hub
- authenticated `POST /user/ai/assistant/messages` client contract
- structured context requests for active signals, market, news, calendar, risk and tools
- structured context cards with explicit source, as-of and freshness where supplied
- unknown/future freshness values degrade to `UNKNOWN`
- reported `FRESH` context requires non-empty source and as-of provenance or is downgraded to `UNKNOWN`
- active-signal context requires a positive persisted server `signal_id`
- non-signal context cannot carry a signal ID
- Assistant prose never creates active positions, active signals, execution state or trade truth
- only verified active-signal context can navigate to the persisted Signal flow; there is no direct Assistant execution route
- established conversation identity cannot silently switch mid-chat
- transcript is memory-only on Android and clears on logout/session loss or New chat
- server history policy (`ephemeral`, `account`, unknown) and positive retention days are displayed explicitly
- entitlement-required, server-validation, rate-limit and generic failure states are explicit
- failed turns never insert a fake assistant reply
- trust/history/API contract documented in `docs/PHASE9_AI_ASSISTANT_CONTRACT.md`
- mapper/controller tests cover context trust and conversation lifecycle

Exit state:
- no invented active positions or signals
- relevant context freshness/provenance is explicit and never upgraded locally
- conversation-history policy is explicit
- Run #114 passed Phase 9 tests, all prior core tests, app lint, app tests, debug assembly and APK upload

## Phase 10 — News & Economic Calendar

Status: Complete

Final milestone: `feat/phase10-news-economic-calendar` → `cfef5ba5c20be8ccf189de137ca9e6a9a199def4` → Run #121 success.

Delivered:
- `core:marketintel` typed normalized news/calendar domain, Retrofit gateway and shared controller
- authenticated `GET /user/market-intelligence` client contract
- `feature:news` advanced Compose market-news feed
- source publication timestamp, structured sentiment, structured impact and stale truth
- `feature:calendar` advanced economic-event timeline
- Low / Medium / High / Unknown impact remains explicit
- actual / forecast / previous values only when supplied
- Gold / Silver / Crypto structured relevance and filters
- required event/publication timestamps normalize from ISO-8601 into `Instant`; invalid timestamps are rejected rather than guessed
- missing stale truth defaults to stale
- unknown impact/sentiment never becomes certainty locally
- article URL data is accepted only for HTTPS hosts
- exact-HIGH, fresh, relevant economic events can attach a risk-context warning to active Signal Detail inside the defined time window
- warning never creates a prediction, direction or execution state
- News and Calendar routes are available from Tools
- market-intelligence state clears on sign-out/session loss
- native Compose state/list/card motion follows platform animator-duration scale and is driven only by real data-state changes
- no fake live pulse, count-up price or urgency animation
- API/truth/motion contract documented in `docs/PHASE10_MARKET_INTELLIGENCE_CONTRACT.md`
- `core:marketintel` tests are part of cumulative Android CI

Exit state:
- event/publication time truth is normalized and unit-tested
- stale/unknown impact is never presented as certainty
- Run #121 passed Phase 10 tests, all prior core tests, app lint, app tests, debug assembly and APK upload

## Phase 11 — Trader Tools

Status: Complete

Final code milestone: `feat/phase11-trader-tools` → `11d91b2cb90a484611a1b1c773187b7c2b2795e4` → Run #126 success.

Delivered:
- premium Trader Toolkit dashboard in existing `feature:tools`
- Risk Calculator with deterministic account-risk math
- Position Size / Lot Calculator with explicit pip-value assumptions
- Risk / Reward Calculator with strict long/short level geometry
- Profit Calculator with explicit lots and contract size
- Pip Calculator with explicit pip size and pip value per lot
- Crypto PnL Calculator for USDT-quoted pairs with entry and exit fees
- Compound Calculator with arithmetic-only growth assumptions
- Drawdown Simulator with compounded loss and recovery requirement
- formula, units, precision and assumptions visible in the UI
- designed missing-input, validation-error and result states plus per-tool reset
- zero, negative, invalid and non-finite input handling per formula contract
- successful results pass a final finite-number guard so `NaN` / Infinity cannot enter UI
- financial outputs use deterministic Latin precision, Unicode LTR isolates and Compose LTR text direction for RTL safety
- calculator engine remains local and fully separate from execution; no calculator can send an order
- connected News, Calendar and Connections remain separate source-backed surfaces
- no fake realtime, AI progress, broker state, execution state, urgency animation or price count-up
- `docs/PHASE11_TRADER_TOOLS_CONTRACT.md` documents formulas, assumptions, precision and truth boundaries
- `:feature:tools:testDebugUnitTest` added to cumulative Android CI

Exit state:
- all eight formula families and important invalid-input paths are unit-tested
- assumptions and display precision are documented
- RTL financial LTR isolation is unit-tested
- Run #126 passed Phase 11 tests, all prior cumulative core tests, app lint, app tests, debug assembly and APK upload
- final phase closure still requires the latest documentation Head to pass the same Android CI gate

## Phase 12 — Activity, History & Performance

Status: Next

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
7. AI Signal job schema — client implemented in Phase 7 with server-truth lifecycle and persisted-Signal trust boundary
8. AI Vision upload/job/result schema — client implemented in Phase 8 with image privacy preprocessing, structured assessments and persisted-Signal trust boundary
9. AI Assistant contextual chat schema — client implemented in Phase 9 with structured context provenance, stable conversation identity and explicit history policy
10. News/calendar timestamps and impact schema — client implemented in Phase 10 with strict timestamp/stale/unknown truth boundaries and active-signal high-impact risk context
11. Trader Tools formulas — local deterministic Phase 11 contract; no execution side effect and explicit numeric/precision/RTL boundaries

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

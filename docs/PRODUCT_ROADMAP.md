# CoinePro Android Product Roadmap

Status: Phases 0 through 6 complete — Phase 7 next

This roadmap is ordered by dependency and risk. The canonical phase-to-branch/SHA/CI mapping lives in `PHASE_INDEX.md`.

## Repository and delivery model

- Source-of-truth repository: `BehnamJalaliCo/CoinePro-App`
- `main` is the stable base.
- `bootstrap/android-foundation` is the cumulative integration branch.
- Each completed `feat/...` branch points to the exact green Android milestone for that phase.
- Production vendor credentials, broker connectivity, IP whitelisting and real external smoke testing are deferred to Phase 17.
- Android never stores production vendor/broker secrets in the repository.

## Phase 0 — Foundation

Status: Complete

Delivered:
- Native Android app with Kotlin + Jetpack Compose
- Gradle/version catalog
- GitHub Actions lint/test/debug build
- public-repository secret safeguards
- application shell

Exit state:
- CI green
- debug APK artifact available
- no committed production credentials

## Phase 1 — Design System & Architecture Skeleton

Status: Complete

Delivered:
- locked Design Direction
- `core:designsystem`
- `core:model`
- `core:common`
- `core:network`
- `core:datastore`
- `core:navigation`
- five-destination shell: Home / Signals / AI / Tools / Activity
- RTL/LTR financial formatting conventions

## Phase 2 — Authentication, Session & Entitlements

Status: Complete

Milestone:
- branch: `feat/android-mobile-auth`
- end SHA: `12cc837ac02e378f3ca4452a95bfed224ad3222b`
- Android CI Run #11: success

Delivered:
- `core:auth` session/auth domain and gateway
- `core:security` Android Keystore-backed token protection
- authenticated `/user/me` validation contract
- auth-only Telegram login bridge
- bearer token redaction
- mandatory cold-start revalidation before protected navigation
- encrypted session clearing on logout/unauthorized response
- entitlement state sourced from the authenticated server profile
- no invented refresh-token flow

Security boundary:
- no bearer token in URLs
- no plaintext session token persistence
- no UI-only entitlement bypass

## Phase 3 — Realtime Market Data Foundation

Status: Complete

Milestone:
- branch: `feat/phase3-realtime-market-data`
- end SHA: `7158a78ef6ee378ec531576bf7d9364816d25b56`
- Android CI Run #14: success

Source contract:
- XAUUSD / XAGUSD: Finnhub-originated normalized feed
- Crypto: LBank-originated normalized feed
- Android consumes the CoinePro HTTP/WebSocket contract rather than vendor credentials

Delivered:
- `core:marketdata`
- resilient OkHttp WebSocket transport
- HTTP snapshot fallback
- reconnect/backoff and superseded-socket protection
- source timestamps and stale/fresh state
- fresh quote required before `LIVE`
- Home Market Pulse using real quote state only

Launch deferral:
- live production vendor connectivity and whitelist smoke testing remain Phase 17

## Phase 4 — Signals Core

Status: Complete

Milestone:
- branch: `feat/phase4-signals-core`
- end SHA: `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb`
- Android CI Run #17: success

Delivered:
- `core:signals`
- typed signal network/domain models
- Forex/Crypto signal list
- Active / Recent / Closed filters
- Signal Detail route and screen
- Entry/zone, SL, TP1/TP2/TP3, R:R and confidence
- optional rationale/evidence only when supplied
- current/last quote with stale state
- invalid/non-actionable direction rejection
- safe missing-field rendering

Product scope:
- Forex V1: XAUUSD and XAGUSD only
- Crypto: LBank-style USDT pairs
- no generic trading terminal

## Phase 5 — Alerts & Push

Status: Complete

Milestone:
- branch: `feat/phase5-alerts-push`
- end SHA: `60dfd64259ec92775b38288f2a4dc8e4c50169e9`
- Android CI Run #41: success

Delivered:
- `core:notifications`
- Notification Center in Activity
- notification preferences
- price above/below/cross alert contract
- FCM token registration
- authenticated device unregister before explicit logout
- Firebase Messaging service
- Android notification channel
- Android 13+ notification permission handling
- deep links to Signal Detail / Activity
- build-time Firebase runtime configuration without committed `google-services.json`

Exit state:
- notification payload mapping is unit-tested
- app navigation is deterministic
- production FCM delivery smoke remains Phase 17

## Phase 6 — Connections & Signal Execution Bridge

Status: Complete

Milestone:
- branch: `feat/phase6-signal-execution`
- end SHA: `710ede98b19c74244e61048174fdd3939b0cb98a`
- Android CI Run #65: success

Delivered:
- `core:execution`
- `feature:connections`
- `feature:execution`
- MT5 and LBank connection-state surfaces
- signal-scoped execution confirmation
- no arbitrary-symbol New Trade screen
- venue/lot/amount validation
- idempotency request ID on every execution attempt
- explicit execution states: queued, submitted, open, close-requested, closed, failed, cancelled
- active executed-signal loading/tracking
- UI never declares an order open without provider truth
- LBank close remains hidden after submit/open until that external provider lifecycle is verified
- queued execution can be cancelled before provider acknowledgement
- Android does not persist trading credentials or render them back into logs/UI

Launch deferral:
- real broker/exchange credentials
- production execution worker/provider activation
- external close lifecycle verification
- end-to-end live trade smoke

These are Phase 17 deployment gates, not reasons to split Android phase history across repositories.

## Phase 7 — AI Generated Market Signal

Status: Next

Deliverables:
- AI Signal request form
- symbol/timeframe/risk controls inside product scope
- real pending/done/error job states
- generated result using the standard Signal Card language
- quota/entitlement handling
- server-validated structured result before display or execution

Exit criteria:
- no fake progress
- failed/expired jobs recover cleanly
- unvalidated model text cannot be executed

## Phase 8 — AI Vision Flagship

Status: Planned

Deliverables:
- CameraX capture
- gallery/document picker
- screenshot/image upload
- image compression/orientation/privacy handling
- multimodal job contract
- structured analysis result
- unknown/low-confidence states
- optional eligible Execute CTA

Required structured result:
- symbol/timeframe plus confidence
- trend/bias
- market structure/setup
- direction
- entry zone
- stop loss
- TP1/TP2/TP3
- confidence
- risk
- concise reasoning

Exit criteria:
- EXIF/privacy rules defined
- unclear/unsupported images have explicit state
- execution never proceeds from raw unvalidated model text

## Phase 9 — AI Assistant

Status: Planned

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

Current/target structure evolves toward:

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
6. Signal-scoped MT5 execution contract — client implemented in Phase 6; live provider validation Phase 17
7. LBank execution/close contract — client safety boundary implemented; live close lifecycle Phase 17
8. AI Signal job schema — Phase 7
9. AI Vision upload/job/result schema — Phase 8
10. News/calendar timestamps and impact schema — Phase 10

## Definition of Done

A client feature is not Done until:
- its API contract is explicit
- loading/empty/error/offline behavior exists where applicable
- RTL and financial LTR formatting are handled
- security/logging implications are reviewed
- tests cover critical business rules
- Android CI is green
- no fake realtime, execution or AI progress state exists

External production connectivity is additionally gated by Phase 17.
# CoinePro Android Product Roadmap

Status: Baseline roadmap v1

This roadmap is ordered by dependency and risk, not by visual excitement. A phase is complete only when its exit criteria pass.

## Phase 0 — Foundation

Status: In progress / bootstrap PR

Deliverables:
- Native Android, Kotlin, Jetpack Compose
- Gradle/version catalog
- CI: lint, unit tests, debug build artifact
- public-repo secret safeguards
- application shell

Exit criteria:
- clean CI on PR
- debug APK artifact produced
- no credentials in repository

## Phase 1 — Design System & Architecture Skeleton

Deliverables:
- locked Design Direction
- `core:designsystem` tokens/theme
- `core:model`
- `core:common`
- `core:network`
- `core:datastore`
- navigation shell with five destinations
- app-level error/loading conventions
- RTL/LTR financial formatting helpers

Exit criteria:
- theme preview/snapshot tests where practical
- app builds with module boundaries enforced
- no feature directly owns transport/storage implementation

## Phase 2 — Authentication, Session & Entitlements

Deliverables:
- login/register/refresh flow matching backend contract
- Keystore-backed session/token storage
- DataStore preferences
- logout/revocation path
- subscription/VIP entitlement source of truth from backend
- global unauthorized/expired-session handling

Security gates:
- no bearer tokens in URLs
- no MT5/LBank secrets persisted in plaintext
- no UI-only entitlement protection

Exit criteria:
- cold-start session restore tested
- token expiration/refresh tested
- unauthorized state cannot access protected flows

## Phase 3 — Realtime Market Data Foundation

Deliverables:
- OkHttp/Retrofit HTTP stack
- resilient OkHttp WebSocket layer
- Gold/Silver live prices
- Crypto price stream / fallback polling
- reconnect/backoff/network-state handling
- normalized market model and timestamps

Exit criteria:
- stale data visibly identified
- reconnect works after network loss/app resume
- no fake live badge when data is stale

## Phase 4 — Signals Core

Deliverables:
- Forex/Crypto signal list
- XAUUSD/XAGUSD scope enforced for Forex V1
- Signal Detail
- entry/SL/TP1/TP2/TP3/R:R/confidence/reasoning
- server-truth signal lifecycle
- active/closed/history filters

Exit criteria:
- signal state survives restart
- status agrees with backend tracking
- missing fields render safely
- signal detail deep link ready

## Phase 5 — Alerts & Push

Deliverables:
- FCM/device registration backend contract
- new signal notifications
- Entry Hit / TP / SL notifications
- price above/below/cross alerts
- deep-link routing from notification to signal/activity
- notification preference controls

Exit criteria:
- duplicate notification suppression
- revoked session/device handled
- notification opens correct entity

## Phase 6 — Connections & Signal Execution Bridge

Deliverables:
- Connections screen
- MetaTrader 5 connection state
- LBank connection state
- per-signal execution confirmation
- risk/lot/amount validation
- idempotency key on every execution request
- execution audit record bound to Signal ID
- active executed signals
- close action only where backend contract is verified

Critical backend work:
- user-scoped MT5 signal execution API separate from owner/master/copy paths
- verify/complete LBank close lifecycle before exposing Close in Android

Exit criteria:
- duplicate taps cannot duplicate orders
- execution cannot mutate arbitrary symbols outside eligible signal
- server audit trail exists
- connection secrets never exposed in logs/UI

## Phase 7 — AI Generated Market Signal

Deliverables:
- AI Signal request form
- symbol/timeframe/risk controls within product scope
- real pending/done/error job states
- generated signal result using standard Signal Card language
- quota/entitlement handling

Exit criteria:
- no fake progress
- failed/expired jobs recover cleanly
- model output is validated server-side before display/execution

## Phase 8 — AI Vision Flagship

Deliverables:
- CameraX capture
- gallery/document picker
- screenshot/image upload
- image compression/orientation/privacy handling
- multimodal backend endpoint/job contract
- structured analysis result
- unknown/low-confidence states
- optional eligible Execute CTA

Required result schema:
- symbol/timeframe (nullable + confidence)
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
- unsupported/unclear image has explicit response
- execution never proceeds from unvalidated raw model text

## Phase 9 — AI Assistant

Deliverables:
- contextual chat using authenticated user context
- active signal context
- current market price context
- news/calendar context
- risk/tool context
- explicit citations/source labels where backend can provide them

Exit criteria:
- assistant cannot silently invent active positions/signals
- context freshness timestamp visible when relevant
- conversation history policy defined

## Phase 10 — News & Economic Calendar

Deliverables:
- market news feed
- AI sentiment/impact labels from backend
- economic calendar
- Low/Medium/High impact
- actual/forecast/previous when available
- Gold/Silver/Crypto relevance
- high-impact warning attached to active signals

Exit criteria:
- publication/event times normalized correctly
- stale/unknown impact not presented as certainty
- high-impact event warning has source time

## Phase 11 — Trader Tools

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
- deterministic unit tests for formulas
- instrument precision/contract assumptions documented
- calculations clearly separated from trade execution

## Phase 12 — Activity, History & Performance

Deliverables:
- executed-signal history
- signal history
- filters by market/instrument/result
- total signals, win rate, TP hit rates, SL rate, average R:R
- clear denominator and no-record state

Exit criteria:
- never infer ROI/equity without backend data
- losses have equal visual prominence
- zero and no-record are distinct

## Phase 13 — Offline, Reliability & Background Work

Deliverables:
- Room cache for safe read models
- WorkManager for durable sync tasks
- offline/stale states
- app resume synchronization
- retry policy and idempotent background operations

Exit criteria:
- offline mode never pretends execution succeeded
- stale market data visibly marked
- background work respects battery/network constraints

## Phase 14 — Security Hardening

Deliverables:
- secret scan in CI
- dependency vulnerability review
- release network security configuration
- certificate strategy decision
- log redaction
- root/debug/tamper policy decision based on threat model
- API rate-limit/error abuse handling
- privacy/data retention documentation

Backend prerequisite:
- rotate and purge any historical secrets found in source repositories before production launch.

Exit criteria:
- no secrets in build artifacts/logs
- threat model reviewed for execution and AI image upload
- production endpoints and credentials isolated from debug

## Phase 15 — Quality, Performance & Accessibility

Deliverables:
- unit tests for domain/data logic
- ViewModel tests
- Compose UI tests for critical flows
- screenshot/golden tests for Signal Card and AI Vision states
- baseline profile/startup measurement
- RTL stress testing
- font scaling
- reduced motion
- TalkBack labels

Exit criteria:
- critical flow test matrix green
- no clipped financial values at supported font scales
- startup/jank budget documented and met

## Phase 16 — Release Engineering

Deliverables:
- release signing via protected CI secrets
- versioning strategy
- staging vs production build configuration
- Play Console internal testing pipeline
- release notes/changelog
- crash/ANR monitoring decision

Exit criteria:
- reproducible signed release
- production signing key never enters repository
- staging cannot accidentally execute against production accounts

## Phase 17 — Launch Readiness

Deliverables:
- onboarding
- permissions education
- connection setup education
- legal/risk disclosures
- support/feedback path
- analytics events with privacy review
- operational runbooks for backend/notifications/execution incidents

Exit criteria:
- end-to-end smoke: login → signal → execution confirmation → tracked result
- AI Vision smoke: image → validated analysis → eligible action
- incident rollback/disable switches defined server-side

## Product module map

Target structure evolves toward:

```text
app
core:common
core:model
core:designsystem
core:network
core:database
core:datastore
core:notifications
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

## Cross-cutting contracts that must be settled early

1. Authentication/refresh token contract
2. User entitlement/subscription contract
3. Normalized Signal schema and lifecycle
4. Realtime price/WebSocket schema
5. FCM device + alert schema
6. Per-user MT5 execution contract
7. LBank open/close/order-status contract
8. AI Signal job schema
9. AI Vision upload/job/result schema
10. News/calendar timestamps and impact schema

## Definition of Done for every feature

A feature is not Done until:
- backend contract is documented/validated
- loading/empty/error/offline states exist
- RTL + financial LTR formatting is verified
- analytics/privacy decision is explicit
- security/logging implications reviewed
- tests cover critical business rules
- CI is green
- no fake states or fake progress exist

# CoinePro Android Product Roadmap

Status: Baseline roadmap v1 — Phases 0, 1 and 2 complete

This roadmap is ordered by dependency and risk, not by visual excitement. A phase is complete only when its exit criteria pass.

## Phase 0 — Foundation

Status: Complete

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

Status: Complete

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

Status: Complete

Backend contract implemented as it actually exists today:
- Telegram signed-login flow via `GET /user/auth/config` and `POST /user/auth/telegram`
- authenticated session validation via `GET /user/me`
- no refresh-token endpoint exists today; Android does not invent one
- email OTP remains a secondary verification step after authentication
- server `/user/me` fields are the entitlement source of truth

Deliverables:
- `core:auth` session/auth domain and gateway
- `core:security` Keystore-backed token storage
- auth-only Telegram Login Widget bridge
- Hilt dependency injection for auth/session/network dependencies
- encrypted bearer-token persistence: AES/GCM key in Android Keystore, ciphertext in DataStore
- cold-start session restore followed by mandatory `/user/me` validation
- logout/local token clearing
- global authenticated `401` handling and session invalidation
- network-failure revalidation state that keeps protected flows locked
- subscription/VIP entitlement state from backend fields
- public-repo-safe API base URL injection via Gradle property

Security gates:
- no bearer tokens in URLs
- Authorization and Cookie headers redacted from logs
- no HTTP body logging in the shared production-capable client
- no MT5/LBank secrets persisted in plaintext
- no UI-only entitlement protection
- protected shell remains locked until server revalidation succeeds

Exit criteria:
- cold-start session restore tested
- expired/unauthorized session clearing tested
- free user cannot acquire paid entitlement client-side
- network failure cannot silently unlock protected flows
- auth/session unit tests green
- lint, debug assembly and APK artifact green in CI

Future backend improvement:
- add explicit server-side token revocation/logout and refresh/rotation only if the backend security model adopts them; Android must then update this contract before implementation.

## Phase 3 — Realtime Market Data Foundation

Status: Next

Deliverables:
- resilient OkHttp WebSocket layer
- Gold/Silver live prices
- Crypto price stream / fallback polling
- reconnect/backoff/network-state handling
- normalized market model and timestamps
- stale/freshness calculation based on server timestamps

Exit criteria:
- stale data visibly identified
- reconnect works after network loss/app resume
- fallback polling does not create duplicate streams
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
core:database
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

1. Authentication/session contract — settled for current backend: bearer token + `/user/me`, no refresh endpoint
2. User entitlement/subscription contract — backend profile fields are source of truth
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

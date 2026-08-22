# CoinePro Android Product Roadmap

Status: Baseline roadmap v1 — Phases 0 through 4 complete

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

Status: Complete

Source-of-truth contract:
- `XAUUSD` / `XAGUSD` realtime market data originates from Finnhub
- Crypto realtime market data originates from LBank
- Android connects only to the normalized CoinePro HTTP/WebSocket boundary

Deliverables:
- resilient OkHttp WebSocket layer in `core:marketdata`
- normalized Gold/Silver and Crypto quote model
- HTTP snapshot fallback without duplicate realtime streams
- reconnect/backoff/network-state handling
- normalized timestamps and quote-source metadata
- stale/freshness calculation based on source timestamps
- stale-aware Market Pulse UI
- fresh-quote requirement before declaring a stream `LIVE`

Exit criteria:
- stale data visibly identified
- reconnect works after stream loss/app session restart
- fallback polling does not create duplicate streams
- no fake live badge when data is stale
- market transport/unit tests and Android CI are green

Launch note:
- real production vendor connectivity, IP whitelist and end-to-end external smoke tests are intentionally deferred to Phase 17. They are deployment gates, not blockers for feature development.

## Phase 4 — Signals Core

Status: Complete

Backend contract:
- native authenticated list: `GET /user/signals`
- native authenticated detail: `GET /user/signals/{signalId}`
- actionable levels require an active paid membership server-side
- signal viewing is not coupled to execution/KYC approval; execution has stricter gates in Phase 6
- owner/manual chart orders are excluded from the native signal product surface

Deliverables:
- `core:signals` domain/gateway/controller
- Forex/Crypto signal list
- XAUUSD/XAGUSD scope enforced for Forex V1
- LBank-style USDT pair scope for Crypto
- Active / Recent / Closed filters
- Signal Detail route and UI
- entry/entry-zone, SL, TP1/TP2/TP3, R:R and confidence
- human-readable rationale/evidence only when actually supplied by backend
- current/last quote with stale state instead of fake realtime
- server-truth closed result when available
- safe missing-field rendering

Exit criteria:
- restart refetches signal truth from backend rather than trusting stale local lifecycle state
- status/result agree with backend tracking contract
- missing fields render safely
- invalid/non-actionable direction is rejected rather than guessed
- signal detail internal deep-link route is ready for Phase 5 notification routing
- `core:signals` tests, lint, debug assembly and APK CI pass

## Phase 5 — Alerts & Push

Status: Next

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
- production vendor domain/credential configuration review
- production IP whitelist verification where required
- real external market-data/API connectivity smoke tests

Exit criteria:
- external market sources are verified from the final production environment
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
core:marketdata
core:signals
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
3. Normalized Signal schema and lifecycle — settled for native list/detail in Phase 4; execution lifecycle extends it in Phase 6
4. Realtime price/WebSocket schema — settled for normalized Android consumption in Phase 3; production external activation is a Phase 17 gate
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

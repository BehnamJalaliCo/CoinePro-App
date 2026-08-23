# CoinePro Delivery Checklist

Use this as the execution checklist beside `PRODUCT_ROADMAP.md` and the canonical milestone map in `PHASE_INDEX.md`.

## Repository rule

- All CoinePro Android phase work is tracked in `BehnamJalaliCo/CoinePro-App` only.
- `bootstrap/android-foundation` is the cumulative integration branch.
- Completed phase branches point to the exact green Android milestone recorded in `PHASE_INDEX.md`.
- Production vendor/broker credentials, external connectivity, whitelist checks and live end-to-end smoke tests remain Phase 17 work.

## Global gates for every phase

- [x] API contract documented before UI integration
- [x] Loading / empty / error / offline states implemented where applicable
- [x] RTL layout and LTR financial values verified
- [x] Security and logging implications reviewed
- [x] Unit/UI tests added for critical paths
- [x] CI green
- [x] No fake realtime state, execution state or AI progress

## Phase progress

- [x] Phase 0 — Foundation bootstrap
- [x] Phase 1A — Design Direction locked
- [x] Phase 1B — Initial `core:designsystem` tokens/theme
- [x] Phase 1C — Architecture skeleton modules + navigation shell
- [x] Phase 2 — Authentication / Session / Entitlements
- [x] Phase 3 — Realtime Market Data Foundation
- [x] Phase 4 — Signals Core
- [x] Phase 5 — Alerts & Push
- [x] Phase 6 — Connections & Signal Execution Bridge
- [x] Phase 7 — AI Generated Market Signal
- [ ] Phase 8 — AI Vision Flagship
- [ ] Phase 9 — AI Assistant
- [ ] Phase 10 — News & Economic Calendar
- [ ] Phase 11 — Trader Tools
- [ ] Phase 12 — Activity / History / Performance
- [ ] Phase 13 — Offline / Reliability / Background Work
- [ ] Phase 14 — Security Hardening
- [ ] Phase 15 — Quality / Performance / Accessibility
- [ ] Phase 16 — Release Engineering
- [ ] Phase 17 — Launch Readiness

## Phase 1C delivered

- `core:common` — shared result/error model and market number formatting
- `core:model` — market/instrument/quote domain models
- `core:network` — Retrofit + OkHttp HTTPS boundary with credential redaction
- `core:datastore` — DataStore persistence boundary
- `core:navigation` — canonical five-destination navigation contract
- `feature:home`
- `feature:signals`
- `feature:ai`
- `feature:tools`
- `feature:activity`
- App shell wired to Home / Signals / AI / Tools / Activity

## Phase 2 delivered

- `core:auth` — Telegram auth client contract, `/user/me` session validation and entitlement model
- `core:security` — AES/GCM token encryption with Android Keystore; ciphertext only in DataStore
- `feature:auth` — auth-only Telegram Login Widget bridge; bearer token never enters the WebView or URL
- Hilt DI wired for session, storage, network and auth gateway
- Bearer interceptor with Authorization/Cookie log redaction
- global authenticated `401` invalidation and encrypted-session clearing
- cold-start token restore is locked until `/user/me` validates the session
- network failure during restore enters `RevalidationRequired`; protected navigation stays locked
- server profile fields are treated as entitlement truth
- no refresh token is invented by Android
- production/staging API base URL is injected by Gradle property and is not committed
- SessionController tests cover signed-out restore, valid restore, unauthorized clearing, network revalidation failure and free-user entitlement
- milestone: `feat/android-mobile-auth` → `12cc837ac02e378f3ca4452a95bfed224ad3222b` → CI Run #11 success; coverage remains in the cumulative CI gate

## Phase 3 delivered

- `core:marketdata` — normalized quote state, WebSocket transport, HTTP snapshot fallback and freshness rules
- Gold/Silver source contract is Finnhub; Crypto source contract is LBank
- Android consumes only the normalized CoinePro API contract, never vendor credentials
- reconnect/backoff and duplicate/superseded socket protection
- a socket connection alone never creates a fake `LIVE` state; at least one fresh quote is required
- stale quotes are explicitly represented and degrade live state
- Home Market Pulse renders real quote state only
- out-of-scope market symbols are rejected instead of guessed
- milestone: `feat/phase3-realtime-market-data` → `7158a78ef6ee378ec531576bf7d9364816d25b56` → CI Run #14 success
- final production vendor connectivity/whitelist smoke testing is intentionally deferred to Phase 17

## Phase 4 delivered

- `core:signals` — typed signal models, Retrofit gateway, controller and membership/error state
- authenticated list/detail client contract for CoinePro signals
- Forex V1 scope locked to `XAUUSD` / `XAGUSD`
- Crypto contract accepts only LBank-style `*USDT` symbols
- Signal list supports Forex/Crypto plus Active/Recent/Closed views
- Signal Detail renders entry/entry-zone, SL, TP1/TP2/TP3, R:R, confidence, rationale/evidence when present, current/last quote and closed result
- missing fields render as missing; no invented values or fake live state
- invalid/neutral directions are rejected rather than displayed as actionable trades
- financial values remain LTR inside RTL-capable UI
- product scope is enforced in the Android mapper
- milestone: `feat/phase4-signals-core` → `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` → CI Run #17 success

## Phase 5 delivered

- `core:notifications` notification, preference and price-alert models/gateway/controller
- Activity tab acts as the native Notification Center
- signal/entry/TP/SL and price-alert data contracts
- FCM token registration plus authenticated unregister on explicit logout
- native Firebase Messaging service and market-event notification channel
- Android 13+ notification permission handling
- notification deep-links route to Signal Detail or Activity
- Firebase runtime configuration is injected at build time; no `google-services.json` or production credential is committed
- notification mapper tests are part of Android CI
- outgoing and incoming alert symbols/values are validated against product scope and finite positive values
- milestone: `feat/phase5-alerts-push` → `60dfd64259ec92775b38288f2a4dc8e4c50169e9` → CI Run #41 success
- real production push delivery smoke testing remains Phase 17

## Phase 6 delivered

- `core:execution` execution models, Retrofit gateway and controller
- `feature:connections` for MT5/LBank connection state and secure credential submission
- `feature:execution` for per-signal execution confirmation
- execution is signal-scoped; no generic New Trade surface exists
- venue and quantity validation before request submission
- idempotency request ID on execution requests
- explicit `QUEUED / SUBMITTED / OPEN / CLOSE_REQUESTED / CLOSED / FAILED / CANCELLED / UNKNOWN` truth states
- UI never claims an execution is open before provider truth says `OPEN`
- active executed signals are rendered and tracked in Activity
- duplicate close requests after `CLOSE_REQUESTED` are blocked
- LBank close is deliberately not exposed after submission/open; a queued intent may be cancelled before provider acknowledgement
- trading credentials are not persisted by the Android client and are not rendered back into UI/logs
- audited milestone: `feat/phase6-signal-execution` → `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26` → CI Run #86 success
- production broker/exchange connectivity and end-to-end execution smoke remain Phase 17

## Phase 7 delivered

- `core:aisignal` AI Signal domain, Retrofit gateway and server-truth controller
- `docs/PHASE7_AI_SIGNAL_CONTRACT.md` defines the authenticated job/trust contract
- AI request UI supports product-scoped symbol, timeframe and risk controls
- server-derived quota and entitlement states
- exact `QUEUED / RUNNING / DONE / FAILED / EXPIRED` job lifecycle
- pending state uses server polling without invented percentage or fake success
- failed and expired jobs can be retried or replaced
- structured AI result must be `validated=true` and match the original symbol/timeframe request
- invalid direction, prices, targets, confidence, product scope or request/result mismatches block the result
- raw model text is not part of the Android execution contract
- AI result CTA opens only a persisted validated `signal_id`
- no direct execution call exists on the AI screen; execution remains Signal Detail → Execution
- AI state clears on sign-out
- `core:aisignal` mapper/controller tests cover trust boundary, quota, entitlement, pending→done and expiry
- audited milestone: `feat/phase7-ai-generated-market-signal` → `f718d9ad310ab37d4b109297c4fadcb33e287775` → CI Run #91 success

## Current next milestone

Phase 8 — AI Vision Flagship.

There are 10 phases remaining: Phase 8 through Phase 17.

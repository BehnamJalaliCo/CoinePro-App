# CoinePro Delivery Checklist

Use this as the execution checklist beside `PRODUCT_ROADMAP.md`.

## Global gates for every phase

- [ ] Backend/API contract documented before UI integration
- [ ] Loading / empty / error / offline states implemented
- [ ] RTL layout and LTR financial values verified
- [ ] Security and logging implications reviewed
- [ ] Unit/UI tests added for critical paths
- [ ] CI green
- [ ] No fake realtime state or fake AI progress

## Phase progress

- [x] Phase 0 — Foundation bootstrap
- [x] Phase 1A — Design Direction locked
- [x] Phase 1B — Initial `core:designsystem` tokens/theme
- [x] Phase 1C — Architecture skeleton modules + navigation shell
- [x] Phase 2 — Authentication / Session / Entitlements
- [x] Phase 3 — Realtime Market Data Foundation
- [x] Phase 4 — Signals Core
- [ ] Phase 5 — Alerts & Push
- [ ] Phase 6 — Connections & Signal Execution Bridge
- [ ] Phase 7 — AI Generated Market Signal
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

- `core:auth` — backend-matched Telegram auth, `/user/me` session validation and entitlement model
- `core:security` — AES/GCM token encryption with Android Keystore; ciphertext only in DataStore
- `feature:auth` — auth-only Telegram Login Widget bridge; bearer token never enters the WebView or URL
- Hilt DI wired for session, storage, network and auth gateway
- Bearer interceptor with Authorization/Cookie log redaction
- global authenticated `401` invalidation and encrypted-session clearing
- cold-start token restore is locked until `/user/me` validates the session
- network failure during restore enters `RevalidationRequired`; protected navigation stays locked
- backend `is_vip`, `is_paid`, `panel_allowed`, `panel_state`, `plan`, `plan_expires_at` are entitlement truth
- no refresh token was invented: current backend has no refresh endpoint
- production/staging API base URL is injected by Gradle property and is not committed
- SessionController tests cover signed-out restore, valid restore, unauthorized clearing and free-user entitlement

## Phase 3 delivered

- `core:marketdata` — normalized quote state, WebSocket transport, HTTP snapshot fallback and freshness rules
- Gold/Silver source contract is Finnhub; Crypto source contract is LBank
- Android consumes only the normalized CoinePro backend contract, never vendor credentials
- reconnect/backoff and duplicate/superseded socket protection
- a socket connection alone never creates a fake `LIVE` state; at least one fresh quote is required
- stale quotes are explicitly represented and degrade live state
- Home Market Pulse renders real quote state only
- final production vendor connectivity/whitelist smoke testing is intentionally deferred to Phase 17 Launch Readiness

## Phase 4 delivered

- `core:signals` — typed signal models, Retrofit gateway, controller and membership/error state
- backend mobile contract: authenticated `/user/signals` list + detail
- paid-membership gate is enforced server-side for actionable signal data
- Forex V1 scope is locked to `XAUUSD` / `XAGUSD`
- Crypto contract accepts only LBank-style `*USDT` symbols
- owner/manual chart orders are excluded from the mobile signal surface
- Signal list supports Forex/Crypto plus Active/Recent/Closed views
- Signal Detail renders entry/entry-zone, SL, TP1/TP2/TP3, R:R, confidence, rationale/evidence when actually present, current/last quote and closed result
- missing fields render as missing; no invented values or fake live state
- invalid/neutral server directions are rejected rather than displayed as actionable trades
- financial values remain LTR inside RTL-capable UI
- no execution control is introduced before Phase 6

## Current next milestone

Phase 5 — Alerts & Push.

Production vendor activation remains a Launch Readiness task and does not block Phases 5–16.

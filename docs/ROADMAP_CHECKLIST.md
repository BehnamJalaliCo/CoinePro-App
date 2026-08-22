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
- [ ] Phase 3 — Realtime Market Data Foundation
- [ ] Phase 4 — Signals Core
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
- `core:network` — HTTPS-only Retrofit/OkHttp factory with credential redaction
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
- Android CI Run #9 passed lint, unit tests, debug assembly and APK artifact upload

## Current next milestone

Phase 3 — Realtime Market Data Foundation.

Phase 3 will establish resilient HTTP/WebSocket market transport, Gold/Silver live prices, crypto prices, reconnect/backoff, fallback polling, stale-data detection and server-truth timestamps before the Signals UI consumes live market state.

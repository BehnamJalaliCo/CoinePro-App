# CoinePro Delivery Checklist

Use this beside `PRODUCT_ROADMAP.md`; canonical phase SHA/CI mapping lives in `PHASE_INDEX.md`.

## Repository rule

- All CoinePro phase work is tracked in `BehnamJalaliCo/CoinePro-App` only.
- `bootstrap/android-foundation` is the cumulative integration branch.
- Completed phase checkpoints are recorded in `PHASE_INDEX.md`.
- Production vendor/broker credentials, external connectivity, whitelist checks and live end-to-end smoke tests remain launch-readiness work unless explicitly moved earlier.

## Global gates for every phase

- [x] API contract explicit
- [x] Loading / empty / error states where applicable
- [x] RTL layout and LTR financial values handled
- [x] Security/privacy/logging implications reviewed
- [x] Critical business-rule tests added
- [x] Android CI green
- [x] No fake realtime, execution or AI progress

## Phase progress

- [x] Phase 0 — Foundation bootstrap
- [x] Phase 1 — Design System & Architecture Skeleton
- [x] Phase 2 — Authentication / Session / Entitlements
- [x] Phase 3 — Realtime Market Data Foundation
- [x] Phase 4 — Signals Core
- [x] Phase 5 — Alerts & Push
- [x] Phase 6 — Connections & Signal Execution Bridge
- [x] Phase 7 — AI Generated Market Signal
- [x] Phase 8 — AI Vision Flagship
- [x] Phase 9 — AI Assistant
- [x] Phase 10 — News & Economic Calendar
- [ ] Phase 11 — Trader Tools
- [ ] Phase 12 — Activity / History / Performance
- [ ] Phase 13 — Offline / Reliability / Background Work
- [ ] Phase 14 — Security Hardening
- [ ] Phase 15 — Quality / Performance / Accessibility
- [ ] Phase 16 — Release Engineering
- [ ] Phase 17 — Launch Readiness

## Validated milestones

- Phase 2: `feat/android-mobile-auth` → `12cc837ac02e378f3ca4452a95bfed224ad3222b` → Run #11 success
- Phase 3: `feat/phase3-realtime-market-data` → `7158a78ef6ee378ec531576bf7d9364816d25b56` → Run #14 success
- Phase 4: `feat/phase4-signals-core` → `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` → Run #17 success
- Phase 5: `feat/phase5-alerts-push` → `60dfd64259ec92775b38288f2a4dc8e4c50169e9` → Run #41 success
- Phase 6 audited: `feat/phase6-signal-execution` → `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26` → Run #86 success
- Phase 7: `feat/phase7-ai-generated-market-signal` → `f718d9ad310ab37d4b109297c4fadcb33e287775` → Run #91 success
- Phase 8 audited: `feat/phase8-ai-vision` → `85ed5a681b9f3a548fdc1d30faeea8dacb3d88b1` → Run #101 success
- Phase 9 audited: `feat/phase9-ai-assistant` → `3d158c9d0fc72724e9bbf402ae81540300950cc3` → Run #114 success
- Phase 10: `feat/phase10-news-economic-calendar` → `cfef5ba5c20be8ccf189de137ca9e6a9a199def4` → Run #121 success

## Phase 10 delivered

- `core:marketintel` typed normalized news/calendar models, Retrofit gateway and shared controller
- explicit authenticated `GET /user/market-intelligence` contract
- `feature:news` animated Market Intelligence feed with source, publication time, stale state, impact, sentiment and Gold/Silver/Crypto relevance
- `feature:calendar` animated economic-event timeline with Low / Medium / High / Unknown impact filters
- actual / forecast / previous values render only when supplied
- required publication/event timestamps normalize from ISO-8601 into `Instant`; invalid timestamps are rejected instead of guessed
- missing stale flag defaults to stale
- unknown impact/sentiment remains unknown
- HTTPS-only article URL normalization
- active Signal Detail high-impact warning requires exact HIGH + fresh + matching instrument relevance + defined time window
- warning is risk context only and never creates trade direction/execution state
- News and Calendar routes are available from Tools
- native Compose AnimatedContent/card/list motion is tied only to real state changes and follows system reduced-motion scaling
- no fake live pulse, price count-up or urgency animation
- state clears on logout/session loss
- `docs/PHASE10_MARKET_INTELLIGENCE_CONTRACT.md` documents API/truth/motion rules
- `core:marketintel` tests are part of cumulative Android CI
- Run #121 passed Phase 10 tests, all earlier core tests, lint, app tests, debug build and APK upload

## Current next milestone

Phase 11 — Trader Tools.

There are 7 phases remaining: Phase 11 through Phase 17.

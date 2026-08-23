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
- [x] Phase 11 — Trader Tools
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
- Phase 11: `feat/phase11-trader-tools` → `11d91b2cb90a484611a1b1c773187b7c2b2795e4` → Run #126 success

## Phase 11 delivered

- premium Trader Toolkit dashboard in the existing `feature:tools` surface
- Risk Calculator
- Position Size / Lot Calculator
- Risk / Reward Calculator with direction geometry validation
- Profit Calculator with explicit contract size
- Pip Calculator with explicit pip size and pip value assumptions
- Crypto PnL Calculator for USDT-quoted pairs with two-sided fees
- Compound Calculator with arithmetic-only growth assumptions
- Drawdown Simulator with compounded loss and recovery requirement
- deterministic local formulas isolated from signal/order execution
- zero, negative and non-finite input handling by formula contract
- final finite-output guard prevents `NaN` and Infinity from reaching UI
- formula, unit, precision, assumption, missing-input, validation-error and reset states are explicit
- financial outputs use Latin precision plus Unicode LTR isolate/PDI and Compose LTR text direction for RTL safety
- News / Calendar / Connections remain separate source-backed surfaces
- no fake market state, AI progress, broker state, execution state or urgency animation
- `docs/PHASE11_TRADER_TOOLS_CONTRACT.md` documents formulas, assumptions, precision and truth boundaries
- `:feature:tools:testDebugUnitTest` is part of cumulative Android CI
- Run #126 passed Phase 11 tests, all earlier core tests, lint, app tests, debug build and APK upload

## Current next milestone

Phase 12 — Activity / History / Performance, after the final Phase 11 closure Head is green.

There are 6 phases remaining: Phase 12 through Phase 17.

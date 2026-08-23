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
- [ ] Phase 9 — AI Assistant
- [ ] Phase 10 — News & Economic Calendar
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
- Phase 8: `feat/phase8-ai-vision` → `10844e48e65b90e9bcd8d60bb5c7ecfea982c18b` → Run #97 success

## Phase 8 delivered

- `core:aivision` typed domain, multipart Retrofit gateway and server-truth job controller
- `feature:ai-vision` native UI
- CameraX back-camera capture
- Android document/gallery picker without broad storage permission
- CAMERA permission requested only for capture; camera hardware marked optional
- EXIF orientation read locally then outbound image re-encoded so original metadata is stripped
- maximum 2048 px edge and adaptive JPEG compression under 6 MB
- exact `QUEUED / RUNNING / DONE / FAILED / EXPIRED` lifecycle
- explicit `ACTIONABLE / LOW_CONFIDENCE / UNKNOWN / UNSUPPORTED` result states
- structured fields: symbol, timeframe, confidence, trend/bias, market structure/setup, direction, entry zone, stop loss, TP1/TP2/TP3, risk and concise reasoning
- actionable result requires `validated=true`, internally valid trade geometry and a positive persisted `signal_id`
- low-confidence/unknown/unsupported results expose no execution action
- contradictory non-actionable trade payloads are rejected
- AI Vision has no direct execute path; eligible action opens the persisted Signal and reuses Signal Detail → Execution
- `docs/PHASE8_AI_VISION_CONTRACT.md` defines privacy/trust/API rules
- `core:aivision` mapper/controller tests are part of cumulative CI
- Run #97 passed Phase 8 tests, all earlier core tests, lint, app tests, debug build and APK upload

## Current next milestone

Phase 9 — AI Assistant.

There are 9 phases remaining: Phase 9 through Phase 17.

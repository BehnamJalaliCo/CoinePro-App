# CoinePro Phase Index

This file is the canonical map between delivery phases, Git branches, milestone commits and validation state.

## Source of truth

- Project repository: `BehnamJalaliCo/CoinePro-App`
- `main` is the stable base.
- `bootstrap/android-foundation` is the cumulative Android integration branch.
- All phase branches, PRs, CI checks and phase bookkeeping for this project belong in this repository only.
- Other repositories are outside the scope of this phase ledger.
- Production credentials, vendor whitelisting and real external smoke tests remain later launch-readiness work unless explicitly moved into an earlier phase.

## Completed milestones

| Phase | Scope | Milestone branch | Validated commit | Android CI |
| --- | --- | --- | --- | --- |
| 0 | Foundation bootstrap | `bootstrap/android-foundation` | foundation history | green |
| 1 | Design system + architecture skeleton | `bootstrap/android-foundation` | architecture history | green |
| 2 | Authentication / Session / Entitlements | `feat/android-mobile-auth` | `12cc837ac02e378f3ca4452a95bfed224ad3222b` | Run #11 — success |
| 3 | Realtime Market Data Foundation | `feat/phase3-realtime-market-data` | `7158a78ef6ee378ec531576bf7d9364816d25b56` | Run #14 — success |
| 4 | Signals Core | `feat/phase4-signals-core` | `adbefeb33b1e39eddd65f28ddd89ad40b70bafdb` | Run #17 — success |
| 5 | Alerts & Push | `feat/phase5-alerts-push` | `60dfd64259ec92775b38288f2a4dc8e4c50169e9` | Run #41 — success |
| 6 | Connections & Signal Execution Bridge | `feat/phase6-signal-execution` | `d8173f79df1aee18b169e8ccbbdcd7c776f7fa26` | Run #86 — success |
| 7 | AI Generated Market Signal | `feat/phase7-ai-generated-market-signal` | `f718d9ad310ab37d4b109297c4fadcb33e287775` | Run #91 — success |
| 8 | AI Vision Flagship | `feat/phase8-ai-vision` | `10844e48e65b90e9bcd8d60bb5c7ecfea982c18b` | Run #97 — success |

## Phase 1–6 audit closure

A final client-side audit before Phase 7 closed the remaining gaps in Phases 1–6:

- Phase 2 authentication tests are an explicit CI gate and network revalidation failure remains locked.
- Phase 3 rejects unsupported market symbols instead of guessing their market type.
- Phase 4 enforces Forex V1 (`XAUUSD` / `XAGUSD`) and Crypto (`*USDT`) scope in the Android mapper.
- Phase 5 validates outgoing and incoming alert symbols and finite positive prices.
- Phase 6 renders active executed signals in Activity, blocks duplicate close requests after `CLOSE_REQUESTED`, keeps LBank Close hidden after submit/open, and includes quantity/close-gating tests.

## Phase 7 status

**Closed / Complete.**

Validated behavior:

- `core:aisignal` domain, Retrofit gateway and server-truth job controller
- explicit authenticated AI Signal API contract in `PHASE7_AI_SIGNAL_CONTRACT.md`
- product-scoped symbol, timeframe and risk controls
- server-derived entitlement and quota state
- exact `QUEUED / RUNNING / DONE / FAILED / EXPIRED` lifecycle
- no local percentage, fake success or fake AI progress
- failed/expired jobs remain recoverable
- strict structured-result validation
- raw model text is not part of the execution contract
- a valid result may only open its persisted positive `signal_id`
- no direct execution from the AI screen
- AI state clears on sign-out

Code checkpoint:

- SHA: `f718d9ad310ab37d4b109297c4fadcb33e287775`
- Android CI Run #91: **success**

## Phase 8 status

**Closed / Complete.**

Validated behavior:

- `core:aivision` structured upload/job/result domain and Retrofit gateway
- `feature:ai-vision` native CameraX capture and Android document/gallery picker
- camera permission requested only when capture is selected; camera hardware remains optional so gallery-only devices are supported
- selected/captured images are orientation-normalized, resized to a maximum 2048 px edge when needed, re-encoded as JPEG and limited to 6 MB
- re-encoding removes original EXIF metadata from the outbound payload; image bytes, local paths and EXIF data are not part of logs/UI
- authenticated multipart upload contract documented in `PHASE8_AI_VISION_CONTRACT.md`
- exact `QUEUED / RUNNING / DONE / FAILED / EXPIRED` server-truth job lifecycle
- explicit `ACTIONABLE / LOW_CONFIDENCE / UNKNOWN / UNSUPPORTED` structured assessment states
- no fake percentage or locally invented AI completion state
- `validated=false` results are blocked
- non-actionable results cannot carry executable signal IDs or trade levels
- actionable results require product-scoped symbol, supported timeframe, BUY/SELL direction, valid entry zone/SL/targets, confidence, risk and concise structured reasoning
- actionable trade geometry is validated so SL and targets are on the correct side of the entry zone
- only a positive persisted server `signal_id` can expose `Open validated Signal`
- AI Vision never calls execution directly; eligible action continues through Signal Detail → Execution
- `core:aivision` mapper/controller tests are included in the Android CI gate

Code checkpoint:

- SHA: `10844e48e65b90e9bcd8d60bb5c7ecfea982c18b`
- Android CI Run #97: **success**
- Run #97 passed Phase 8 tests, all previous core tests, app lint, app unit tests, debug assembly and debug APK artifact upload.

## Next phase

**Phase 9 — AI Assistant**

Status: **Ready to start.**

## Branch rule from Phase 9 onward

For every new phase:

1. Create `feat/phaseN-<scope>` in `BehnamJalaliCo/CoinePro-App` only.
2. Build on top of the approved cumulative integration head.
3. Run phase-specific unit tests plus the cumulative app lint/test/assemble gate.
4. Record the exact validated end SHA and CI run here.
5. Keep PRs Draft and unmerged unless merge is explicitly requested.
6. Keep production credentials, whitelisting and live external provider smoke tests out of phase branches until their designated launch-readiness work.

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
| 8 | AI Vision Flagship | `feat/phase8-ai-vision` | `85ed5a681b9f3a548fdc1d30faeea8dacb3d88b1` | Run #101 — success |
| 9 | AI Assistant | `feat/phase9-ai-assistant` | `3d158c9d0fc72724e9bbf402ae81540300950cc3` | Run #114 — success |
| 10 | News & Economic Calendar | `feat/phase10-news-economic-calendar` | `cfef5ba5c20be8ccf189de137ca9e6a9a199def4` | Run #121 — success |
| 11 | Trader Tools | `feat/phase11-trader-tools` | `11d91b2cb90a484611a1b1c773187b7c2b2795e4` | Run #126 — success |
| 12 | Activity, History & Performance | `feat/phase12-activity-history-performance` | `d592401d6a775254f60850cfc6f2772d4483ee6f` | Run #131 — success |

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
- CameraX temporary cache captures are deleted after image preparation, including failure paths
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

Final Phase 8 checkpoint:

- SHA: `85ed5a681b9f3a548fdc1d30faeea8dacb3d88b1`
- Android CI Run #101: **success**

## Phase 9 status

**Closed / Complete.**

Validated behavior:

- `core:aiassistant` typed contextual-chat domain, authenticated Retrofit gateway and in-memory controller
- `feature:ai-assistant` native Compose chat surface linked from the AI hub
- explicit authenticated Assistant contract in `PHASE9_AI_ASSISTANT_CONTRACT.md`
- requested structured context scopes: active signals, market, news, calendar, risk and tools
- assistant prose never creates positions, signals, trade levels or execution state
- active-signal context requires a positive persisted server `signal_id`
- non-signal context cannot smuggle a signal ID
- only structured active-signal context can expose `Open verified Signal`; there is no direct Assistant execution route
- context provenance displays source / as-of / freshness when supplied
- invalid freshness values degrade to `UNKNOWN`
- `FRESH` context additionally requires non-empty source and as-of provenance; otherwise it degrades to `UNKNOWN`
- an existing conversation ID cannot silently switch mid-chat
- Android transcript is memory-only and clears on logout/session loss or `New chat`
- server-declared conversation history policy (`ephemeral`, `account`, unknown) and positive retention days are displayed explicitly
- entitlement, validation rejection, rate-limit and generic failure states are explicit; no fake assistant reply is inserted
- `core:aiassistant` trust-boundary and lifecycle tests are part of cumulative Android CI

Final Phase 9 checkpoint:

- SHA: `3d158c9d0fc72724e9bbf402ae81540300950cc3`
- Android CI Run #114: **success**

## Phase 10 status

**Closed / Complete.**

Validated behavior:

- `core:marketintel` typed normalized market-news/economic-calendar domain, Retrofit gateway and state controller
- authenticated `GET /user/market-intelligence` client contract documented in `PHASE10_MARKET_INTELLIGENCE_CONTRACT.md`
- `feature:news` flagship Compose news feed with animated real-state transitions, relevance filters, structured impact/sentiment and explicit stale state
- `feature:calendar` animated economic timeline with Low / Medium / High / Unknown impact and Gold / Silver / Crypto filters
- publication/event times require valid ISO-8601 timestamps and normalize to `Instant`; invalid required timestamps are rejected rather than guessed
- missing stale truth defaults to stale, never fresh
- unknown impact and unknown sentiment remain unknown; Android never upgrades them to certainty
- actual / forecast / previous render only when supplied
- article links are normalized as HTTPS-only data
- active Signal Detail displays a high-impact risk-context card only for exact HIGH, fresh, instrument-relevant events inside the defined time window
- calendar warning is explicitly risk context, not a prediction or execution instruction
- News/Calendar are linked from Tools and share one authenticated controller
- market-intelligence state clears on logout/session loss
- native Compose motion is driven only by real state transitions and respects system animator-duration scale; no fake live pulse/count-up urgency
- `core:marketintel` truth-boundary tests are part of cumulative Android CI

Final Phase 10 checkpoint:

- SHA: `cfef5ba5c20be8ccf189de137ca9e6a9a199def4`
- Android CI Run #121: **success**
- Run #121 passed Phase 10 tests, every prior core gate, app lint, app unit tests, debug assembly and debug APK upload.

## Phase 11 status

**Closed / Complete.**

Validated behavior:

- existing `feature:tools` upgraded into a premium Trader Toolkit dashboard instead of a flat Material form list
- Risk Calculator with explicit capital/risk percentage validation
- Position Size / Lot Calculator with user-supplied stop distance and pip value per lot; broker values are never guessed
- Risk / Reward Calculator with strict Long/Short SL/Entry/TP geometry validation
- Profit Calculator with explicit lot and contract-size assumptions
- Pip Calculator with explicit pip-size and pip-value assumptions
- Crypto PnL Calculator scoped to USDT-quoted pairs with entry and exit fees
- Compound Calculator as arithmetic only; entered return is never presented as AI or market forecast
- Drawdown Simulator with compounded losses and required recovery percentage
- all calculator math is local, deterministic and isolated from signal/order execution
- zero, negative, invalid and non-finite inputs are rejected as required by each formula
- every successful result is checked for finite output so `NaN` / Infinity cannot enter UI
- formulas, units, assumptions, precision, missing-input state, validation errors and reset are explicit in each calculator
- Latin-digit formatting uses deterministic US-locale precision plus Unicode LTR isolate/PDI and Compose LTR text direction for financial output inside RTL layouts
- connected News, Calendar and Connections remain separate source-backed surfaces
- no fake realtime, fake AI progress, broker state, execution state, urgency or price count-up is introduced
- formula/truth/precision contract documented in `PHASE11_TRADER_TOOLS_CONTRACT.md`
- `:feature:tools:testDebugUnitTest` is part of cumulative Android CI

Final Phase 11 code checkpoint:

- SHA: `11d91b2cb90a484611a1b1c773187b7c2b2795e4`
- Android CI Run #126: **success**
- Run #126 passed Phase 11 formula/RTL tests, every prior core gate, app lint, app unit tests, debug assembly and debug APK upload.

## Phase 12 status

**Closed / Complete at code checkpoint; final closure Head validation pending after this documentation update.**

Validated behavior:

- Activity upgraded into a premium server-evidence performance dashboard while preserving alerts and notifications
- paginated CLOSED signal history loads Forex and Crypto from the existing authenticated signals contract
- Forex V1 and Crypto USDT product-scope validation remains enforced by the signal mapper
- pagination advances by server page size so locally rejected invalid rows cannot repeat or shift server pages
- history exposes loaded count, expected server total, coverage completeness, entitlement and error state
- incomplete history is disclosed; partial records are never labeled as complete account history
- market, exact instrument and explicit result filters operate only on loaded closed-signal records
- Win / Loss / Breakeven classification uses only finite explicit `result.pnlUsd`; missing/non-finite P&L stays missing
- Win rate denominator uses only finite explicit P&L evidence
- target `hit` is nullable end-to-end; omitted provider/server hit state remains missing instead of becoming a fake miss
- TP hit denominator uses only signals with explicit target-hit evidence
- SL rate uses only explicit close-reason evidence and recognizes only normalized explicit stop-loss codes
- average R:R uses only finite positive server-provided planned TP1 R:R and is labeled as planned, not realized
- zero with evidence, missing evidence and no-record states are visually and semantically distinct
- losses receive equal dashboard prominence with wins
- full server-reported execution ledger is displayed separately; Android does not infer P&L from execution records
- no ROI, equity, account return, broker outcome or execution lifecycle is invented
- financial prices, P&L, quantities, percentages and R:R render in explicit LTR context inside RTL layouts
- signal-history and execution cards can navigate to persisted Signal Detail but performance calculations never execute orders
- contract and evidence rules documented in `PHASE12_ACTIVITY_HISTORY_PERFORMANCE_CONTRACT.md`
- Phase 12 truth/denominator/pagination tests run inside the existing `:core:signals:testDebugUnitTest` cumulative CI gate

Final Phase 12 code checkpoint:

- SHA: `d592401d6a775254f60850cfc6f2772d4483ee6f`
- Android CI Run #131: **success**
- Run #131 passed Phase 12 tests, every previous cumulative core gate, app lint, app unit tests, debug assembly and debug APK upload.

## Next phase

**Phase 13 — Offline, Reliability & Background Work**

Status: **Ready after the final Phase 12 documentation Head passes Android CI.**

## Branch rule from Phase 11 onward

For every new phase:

1. Create `feat/phaseN-<scope>` in `BehnamJalaliCo/CoinePro-App` only.
2. Build on top of the approved cumulative integration head.
3. Run phase-specific unit tests plus the cumulative app lint/test/assemble gate.
4. Record the exact validated end SHA and CI run here.
5. Keep PRs Draft and unmerged unless merge is explicitly requested.
6. Keep production credentials, whitelisting and live external provider smoke tests out of phase branches until their designated launch-readiness work.

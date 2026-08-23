# Phase 12 — Activity, History & Performance Contract

## Scope

Phase 12 turns the existing Activity destination into a server-evidence trading record. It does not create broker state, account equity, ROI, execution outcomes or signal results locally.

Repository scope is `BehnamJalaliCo/CoinePro-App` only.

## Data sources

### Closed signal history

Android reads the existing authenticated `GET /user/signals` contract with:

- `status=closed`
- `market=forex` for Forex V1
- `market=crypto` for Crypto
- explicit `limit` and `offset` pagination

Forex remains limited by the existing signal mapper to `XAUUSD` and `XAGUSD`. Crypto remains limited to `*USDT` pairs.

Android loads pages with a page size of 50 and caps retained history at 1,000 mapped records per market. The cap is a client safety bound, not a claim that more records do not exist.

The pagination offset follows the server row-set. It advances by the requested page size rather than by the number of rows that survive Android validation. This prevents a rejected invalid server row from causing a repeated or shifted page request.

### Execution history

The Activity execution ledger uses the existing authenticated execution-history source exposed by `ExecutionController.history`.

Signal history and execution history are intentionally separate:

- a closed signal is not proof that Android executed it;
- an execution record is not proof of signal P&L;
- Android does not join the two into invented broker performance.

## Coverage truth

`SignalHistoryState` carries:

- loaded mapped records;
- server-reported expected total;
- `coverageComplete`;
- entitlement and error state.

If loaded coverage is incomplete, the UI states the loaded count and expected server total. Performance cards then say that they use loaded evidence only. Partial history is never presented as a complete historical statistic.

A refresh failure with already loaded records does not mark those records as fresh. The UI keeps the last loaded records visible and displays the refresh error.

## Performance definitions

All performance values are computed deterministically from explicit normalized signal fields. There is no predictive model in these calculations.

### Total signals

Total displayed in a filtered view is the number of loaded closed signals that match the active filters.

When global coverage is incomplete, this is explicitly a loaded-record count, not a full-account total.

### Win rate

Evidence field: finite explicit `result.pnlUsd`.

Classification:

- Win: `pnlUsd > 0`
- Loss: `pnlUsd < 0`
- Breakeven: `pnlUsd == 0`
- Missing result: no finite `pnlUsd`

Formula:

`win rate = wins / records with finite explicit P&L × 100`

Signals with missing, NaN or infinite P&L are excluded from the denominator and remain visible as missing evidence.

### TP hit rate

Evidence field: explicit target hit status.

`SignalTarget.hit` is nullable. A missing `hit` field stays missing through DTO and domain mapping; Android does not coerce it to `false`.

A signal enters the TP-rate denominator only when at least one target contains explicit hit evidence (`true` or `false`). A TP hit is counted when at least one target has `hit == true`.

Formula:

`TP hit rate = signals with an explicitly hit target / signals with explicit target-hit evidence × 100`

### SL rate

Evidence field: nonblank server `closeReason`.

A stop-loss hit is recognized only for normalized explicit reasons:

- `SL`
- `STOP_LOSS`
- `STOPLOSS`

Other close reasons stay other close reasons; free text is not guessed as stop loss.

Formula:

`SL rate = explicit stop-loss closes / records with explicit close-reason evidence × 100`

### Average planned R:R

Evidence field: finite positive `riskRewardTp1` supplied with the signal.

Formula:

`average planned R:R = sum(valid positive TP1 R:R) / count(valid positive TP1 R:R)`

This is the average planned setup ratio. It is not realized R:R and is not reconstructed from missing trade data.

## Zero, missing and no-record behavior

These states are different:

- a calculated `0.0%` is a real zero with a non-zero evidence denominator;
- `—` means the metric has no valid evidence denominator;
- an empty signal history means the server returned no closed records;
- an empty filtered view means records exist but none match the active filters.

Android must never replace missing performance evidence with zero.

## Filters

The Activity dashboard supports local filters over loaded closed-signal history:

- Market: All / Forex / Crypto
- Instrument: exact normalized symbol
- Result: All / Win / Loss / Breakeven / Result missing

Filters do not trigger execution and do not mutate server signal state.

If global history coverage is incomplete, filtered metrics remain explicitly based on the loaded subset.

## UI contract

The Activity destination contains:

1. premium server-evidence header and coverage summary;
2. performance evidence cards;
3. market/instrument/result filters;
4. closed signal history;
5. server-reported execution ledger;
6. existing price alerts and push preferences;
7. existing notification history.

Loss count has equal visual prominence with positive performance evidence. The screen does not use casino/neon treatment, artificial urgency, count-up prices or fake live animation.

## RTL and financial rendering

Financial values are rendered inside explicit LTR layout context so prices, P&L, quantities, percentages and R:R remain readable inside RTL screens.

Missing values render as `—`. Formatting only receives finite values on Phase 12 performance paths.

## Execution boundary

Phase 12 is read-oriented for history/performance.

- Signal history may navigate to the persisted Signal Detail record.
- Execution ledger may navigate to the persisted signal.
- Performance cards never send orders.
- No performance calculation changes execution state.
- No raw AI text is involved in performance or execution.

## Security and privacy

Phase 12 introduces no credentials and no new credential persistence.

Android does not log bearer tokens, broker secrets, result payloads as credentials, or place secrets in URLs.

The dashboard does not infer or expose account equity, ROI, account balance or portfolio return because those values are not part of the source contract.

## Validation

Phase 12 tests cover:

- finite explicit P&L denominators;
- Win / Loss / Breakeven / Missing classification;
- zero-record missing rates;
- explicit TP-hit evidence denominator;
- missing target-hit status staying missing;
- explicit stop-loss close reasons;
- positive finite planned R:R denominator;
- market / symbol / result filtering;
- incomplete coverage truth;
- server-page pagination when locally rejected rows reduce mapped page size.

Code checkpoint:

- Branch: `feat/phase12-activity-history-performance`
- SHA: `d592401d6a775254f60850cfc6f2772d4483ee6f`
- Android CI Run #131: **success**
- Run #131 passed cumulative core unit tests, app lint, app tests, debug assembly and debug APK upload.

The final Phase 12 closure Head must pass the same Android CI gate after documentation bookkeeping changes.

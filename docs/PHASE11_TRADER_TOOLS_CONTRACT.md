# Phase 11 — Trader Tools Contract

## Scope

Repository: `BehnamJalaliCo/CoinePro-App` only.

Trader Tools are deterministic local calculations. They do not read broker state, infer market data, create signals, create execution state, or send orders. Connected market-intelligence and broker surfaces remain separate routes.

## Delivered calculators

1. Risk Calculator
2. Position Size / Lot Calculator
3. Risk / Reward Calculator
4. Profit Calculator
5. Pip Calculator
6. Crypto PnL Calculator
7. Compound Calculator
8. Drawdown Simulator

## Formula and assumption contract

### Risk

`risk amount = capital × riskPercent / 100`

- capital must be finite and greater than zero
- risk percentage must be greater than zero and at most 100%
- output precision: account-currency amount to 2 decimals

### Position Size / Lot

`lots = riskAmount / (stopLossPips × pipValuePerLot)`

- all three inputs must be finite and greater than zero
- pip value is explicitly supplied by the user
- Android does not guess instrument/broker pip value
- lot output precision: 4 decimals

### Risk / Reward

`riskDistance = |entry - stop|`

`rewardDistance = |takeProfit - entry|`

`ratio = rewardDistance / riskDistance`

- prices must be finite and greater than zero
- long geometry requires `SL < Entry < TP`
- short geometry requires `TP < Entry < SL`
- ratio precision: 2 decimals
- distance display precision: 5 decimals

### Profit

`pnl = signedPriceMove × lots × contractSize`

- signed price move depends on Long/Short direction
- entry, exit, lots and contract size must be finite and greater than zero
- contract size is explicit because broker metal specifications can differ
- spread, commission, swap and slippage are not guessed
- PnL precision: 2 decimals

### Pip

`pips = signedPriceMove / pipSize`

`pnl = pips × lots × pipValuePerLot`

- entry, exit, lots, pip size and pip value must be finite and greater than zero
- pip size and pip value are explicit user inputs
- Android does not infer broker contract specifications
- pips display precision: 1 decimal
- PnL precision: 2 decimals

### Crypto PnL

`grossPnl = signedPriceMove × quantity`

`fees = (entryNotional + exitNotional) × feePercentPerSide / 100`

`netPnl = grossPnl - fees`

`returnPercent = netPnl / entryNotional × 100`

- intended for USDT-quoted pairs
- entry, exit and quantity must be finite and greater than zero
- fee percentage can be zero but cannot be negative or reach 100%
- fee is applied to both entry and exit notional
- funding, liquidation, leverage-specific margin and slippage are not modeled
- USDT/PnL precision: 2 decimals

### Compound

`ending = principal × (1 + ratePercentPerPeriod / 100) ^ periods`

- principal must be finite and greater than zero
- rate must be finite and greater than -100%
- periods must be a positive integer
- the rate is a user assumption, not an AI or market forecast
- output precision: 2 decimals

### Drawdown

`ending = startingBalance × (1 - lossPercentPerTrade / 100) ^ consecutiveLosses`

`drawdownPercent = (startingBalance - ending) / startingBalance × 100`

`recoveryPercent = (startingBalance / ending - 1) × 100`

- starting balance must be finite and greater than zero
- loss percentage must be greater than zero and below 100%
- consecutive losses must be a positive integer
- each loss compounds on the remaining balance
- percentage precision: 2 decimals

## Numeric safety

- zero/negative inputs are rejected where the formula requires positive values
- `NaN` and positive/negative Infinity are rejected
- every successful result passes a finite-number check before it can reach UI
- numeric overflow is surfaced as an invalid result instead of rendering `NaN` or `Infinity`
- calculator input errors are real validation errors; no fake loading/progress state exists

## RTL / financial LTR

Financial outputs use:

- US-locale Latin digit formatting for deterministic decimal precision
- Unicode LTR isolate (`U+2066`) and PDI (`U+2069`)
- Compose `TextDirection.Ltr` for financial result text

Unit tests verify the isolate boundary and exact formatted precision. This keeps financial values stable inside RTL layouts.

## UI contract

The Trader Toolkit is a data-first dashboard rather than a Material form list:

- risk/sizing and PnL/growth categories
- quick-open actions
- one expanded calculator at a time
- explicit formula strip
- input units and assumptions
- immediate local result calculation
- designed missing-input state
- designed validation-error state
- per-tool reset
- connected News, Calendar and Connections remain visually separated from local math

No decorative fake urgency, fake realtime, price count-up, AI progress, or execution state is used.

## Validation gate

Android CI must run:

- `:feature:tools:testDebugUnitTest`
- every prior cumulative core unit-test gate
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- debug APK artifact upload

Phase 11 is not complete until the latest branch Head passes this gate and the exact green SHA/run are recorded in the phase index, roadmap and checklist.

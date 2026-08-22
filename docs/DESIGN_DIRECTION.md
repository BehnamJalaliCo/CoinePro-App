# CoinePro Design Direction

Status: Locked baseline

## Product identity

CoinePro is an AI-powered market intelligence and signal execution platform for Forex and Crypto. It is not a broker terminal, exchange clone, or TradingView replacement.

Design DNA:
- eToro 2026: AI-first product thinking
- Binance: realtime speed and execution clarity
- Revolut: premium polish and simplicity
- TradeYar TAPESH: motion discipline and data integrity
- CoinePro: original Signal Cards, AI Vision, Forex + Crypto, MT5 + LBank bridge

## Brand character

Keywords: intelligent, calm, premium, precise, trustworthy, realtime.

Avoid: neon casino visuals, purple AI gradients, glass everywhere, robot mascots, fake urgency, decorative motion, odometer/count-up prices.

## Color system

Primary dark mode is the launch baseline.

- Background / Stage: #090B10
- Surface: #10131A
- Surface Elevated: #171B24
- Border: #282E3A
- Text Primary: #F3F5F8
- Text Secondary: #9CA4B4
- Text Muted: #687184
- Lapis / Brand: #4164E8
- Buy / Positive: #2CCB8E
- Sell / Negative: #F15B69
- Warning: #F3B64A
- Gold semantic accent: #D6A84B (XAU only / rare)
- Silver semantic accent: #AEB8C7 (XAG only / rare)

Rules:
- Lapis is the only general brand accent.
- Green/red are reserved for market semantics.
- Gold/silver are instrument-specific accents, never general decoration.
- Never communicate financial state with color alone; pair with sign, icon, or label.

## Typography

- Persian/UI: Vazirmatn family where bundled/approved.
- Financial values: tabular numerals + LTR isolation.
- Prices must render correctly from first frame; no count-up animation.
- Use an em dash (—) for unavailable data; never confuse missing data with zero.

## Shape & spacing

Spacing uses an 8dp grid with 4dp half-step.

Radii:
- xs 4dp
- sm 8dp
- md 12dp
- lg 16dp
- xl 24dp
- pill 999dp

Minimum touch target: 44dp; prefer 48dp for primary actions.

## Motion

Single motion beat: 120ms.

Durations:
- 60ms micro feedback
- 120ms state response
- 180ms small transition
- 240ms standard transition
- 360ms emphasis
- 480ms large transition

Rules:
- Every animation must correspond to a real state change.
- Never animate a number from a fake old value.
- Live heartbeat is allowed only for genuinely live data.
- Honor system reduced-motion settings.

## Signature component: Signal Card

Signal Card is a primary CoinePro brand asset.

Required anatomy:
1. Symbol + market + live/closed state
2. Direction (BUY / SELL / NEUTRAL)
3. AI confidence
4. Entry or entry zone
5. Stop Loss
6. TP1 / TP2 / TP3
7. Risk:Reward
8. Real progression state (Entry Hit, TP1 Hit, TP2 Hit, Closed, SL)
9. Compact AI rationale
10. Signal-scoped execution CTA
11. Connection state when execution is available (MT5/LBank)

Signal states must come from backend truth; never fake TP progress or live state.

## AI entry point

Bottom navigation baseline:
Home · Signals · AI · Tools · Activity

AI is the central product action, not a support chatbot in a corner.

AI action sheet priority:
1. Analyze a Chart
2. Generate Signal
3. Ask CoinePro

## AI Vision signature flow

Camera / Gallery / Screenshot → upload → real processing state → structured result.

Result must prioritize:
- detected symbol/timeframe if reliable
- bullish/bearish/neutral bias
- confidence
- entry zone
- stop loss
- TP1/TP2/TP3
- market structure
- risk
- concise reasoning
- Execute on MT5/LBank only when eligible

No fake staged progress. Unknown symbol/timeframe must be shown as unknown, not guessed.

## Home direction

Home is signal-centric, not portfolio/balance-centric.

Priority order:
1. current high-value signal / market state
2. Gold & Silver pulse
3. key Crypto pulse
4. active signal progress
5. AI quick action
6. high-impact news/calendar warnings

Dense terminal layouts are explicitly rejected.

## Forex vs Crypto

One design system, two subtle tones:
- Forex: calm, institutional, restrained
- Crypto: slightly more dynamic, same hierarchy and typography

V1 Forex instruments: XAUUSD and XAGUSD only.

## Accessibility & RTL

- Persian layouts are RTL; prices, symbols, percentages and R:R values use isolated LTR rendering.
- Do not rely on color alone for direction or outcome.
- Preserve minimum touch targets and readable contrast.
- Dynamic type must not clip financial values or CTAs.

## No-go rules

Do not introduce:
- trading charts/candles as an app feature
- generic manual trading terminal
- social/copy trading UI without a new product decision
- neon/cyberpunk casino aesthetic
- decorative robot identity for AI
- gradient text
- fake urgency counters
- random motion/orbs
- fake AI progress

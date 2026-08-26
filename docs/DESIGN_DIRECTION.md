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

---

## The token layer (adopted 2026-08)

The palette is no longer chosen twice. `core:designsystem` now carries the neutral ladder from
`foundation-v2.css` — the token layer the owner's own web terminal already ships — so a reader
moving between the app and the terminal is not looking at two different machines.

What was adopted is the **structure**:

* A five-step surface ladder instead of three, plus separate hover and pressed steps. That is what
  lets this app keep separating cards by gap rather than by rules: a sheet over a card over the page
  is legible without a single border.
* Three border weights, so the hairline that closes a shape and the rule that divides a list are not
  the same colour.
* A `terminal` ground one step below the stage, because a chart is a dense field of thin strokes and
  reads better on a ground that recedes further than the page around it.
* Three durations — 100 / 160 / 240ms — on `cubic-bezier(.2, 0, 0, 1)`.
* The tint formula for every surface that means something: the base pulled **8%** toward the
  meaning's colour, with a border pulled **34%**. Not alpha. Alpha over an unknown ground gives one
  colour on a card and another on the page, and the same "selected" state then looks like two
  different states depending where it sits.

What was **not** adopted is the identity. `foundation-v2` uses Binance yellow `#F0B90B` for brand and
execution; this app's gold is `#D8A848`, sampled from the CoinePro mark. Taking their yellow would be
taking another company's brand.

### One button, three identities

`LocalPageAccent` is set once per navigation destination and read by every primary button, selected
chip and selected border. A domain colour is never decorative: blue on a chart screen does not mean
somebody liked blue there, it means *this screen reads the market*.

| Accent | Where | Colour |
| --- | --- | --- |
| Analysis | markets search, chart, news, calendar, the AI screens | `#2962FF` |
| Brand | trade, orders, account, subscription | `#D8A848` |
| Social | copy trading | `#00B15C` |

There are three, not the terminal's four. Its fourth is a premium gold, `#D4AF37`, distinct from its
brand yellow — and that distinction does not survive here, because this brand's gold *is* the same
metal. Shipping two golds a reader cannot tell apart, under a rule claiming they mean different
things, would be a rule with no teeth. Premium is marked by treatment instead: the tinted card and
its label, with the brand accent under it.

Gold splits into a **fill** and an **ink** value instead, and the split is load-bearing. The fill is
the brand mid-tone in both themes; the ink is darkened in the light theme, where the mid-tone
measures 2.1:1 on white. Filling a button with the ink value gives near-black text on dark brown,
which is precisely the failure the pair exists to prevent.

### The discipline rules, now enforced

`scripts/quality/check-motion-policy.sh` grew a second half. It fails the build on:

* **any blur** — `Modifier.blur`, `BlurEffect`, `RenderEffect.createBlur`. Elevation is a hairline
  plus at most one very soft shadow. On Android this is a performance rule as much as a visual one:
  a blurred panel behind a scrolling list is a render-effect pass per frame.
* **coloured shadows** — `ambientColor`/`spotColor`. A shadow is black at low alpha; colour belongs
  in the fill or the border.
* **gradients outside the allow-list.** Two kinds earn a place: the brand mark and the agent orb,
  which *are* the metal and would be a different logo as a flat fill; and a moving band reporting
  work in flight or a chart's own area fill, where the gradient is the shape carrying the meaning.
  A gradient on a card, a header or a button is what makes an interface look like a skin rather than
  a system, and that is the case being banned.

`55-design-kit-fa.png` and its light twin render the accents, the ladder and the tinted cards on one
page. That is deliberate: the failure these rules guard against — two domains that ended up the same
colour, two ladder steps that are not distinguishable — cannot be seen one screen at a time, because
on any single screen a wrong accent still looks like a decision.

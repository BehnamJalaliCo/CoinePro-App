# Design tokens — Pro Chart

Exported from the single source in `core/designsystem`. Change the Kotlin, then this file; a value
that appears here and nowhere in the code is a value the app does not use.

| Token family | Source |
| --- | --- |
| Colour roles | `CoineProPalette.kt` (`CoineProDarkPalette`, `CoineProLightPalette`), read through `CoineProColors` |
| Spacing | `CoineProSpacing.kt` |
| Shape | `CoineProShapes.kt` |
| Type | `CoineProType.kt` |
| Motion | `CoineProMotionSpecs.kt` |
| Press | `CoineProPress.kt` |
| Chart | `core/chart/TradingViewPalette.kt`, `ChartViewport.BODY_RATIO`, `CoineProChart.kt` constants |

## Colour

Two palettes, one set of roles. Nothing reads a hex; everything reads a role. Dynamic colour is
off by design — market colours must be the same on every phone.

| Role | Dark | Light | Used for |
| --- | --- | --- | --- |
| `stage` | `#0B0E11` | `#FFFFFF` | the page behind everything |
| `terminal` | `#070A0F` | `#F1F3F7` | the chart's ground when a template asks for one |
| `surface` | `#10141B` | `#F1F2F6` | cards, sheets |
| `surfaceElevated` | `#171C24` | `#EBEEF3` | a card on a card, the switch track |
| `surfaceRaised` | `#222831` | `#FFFFFF` | the topmost plate — light's raised **is** the stage, which is why the Ideas switch needed a tray |
| `surfaceOverlay` | `#1E2329` | `#E8EBEF` | scrims and overlays |
| `surfaceHover` | `#252A31` | `#E9EDF2` | pointer hover |
| `surfacePressed` | `#2B3139` | `#E1E6EC` | pressed state |
| `borderSubtle` | `#FFFFFF` 8 % | `#0D121C` 10 % | hairlines inside a card |
| `border` | `#FFFFFF` 12 % | `#0D121C` 14 % | card edges, dividers |
| `borderStrong` | `#FFFFFF` 18 % | `#0D121C` 20 % | focused fields |
| `textPrimary` | `#F0F1F2` | `#111318` | |
| `textSecondary` | `#B7BDC6` | `#4E5661` | |
| `textMuted` | `#848E9C` | `#5F6875` | captions, stamps |
| `textDisabled` | `#6B7482` | `#767F8D` | |
| `accent` | `#D8A848` | `#8A6318` | the gold, as ink |
| `accentFill` | `#D8A848` | `#D8A848` | the gold, as a fill (light keeps the fill and darkens the ink for contrast) |
| `onAccent` | `#0B0E11` | `#111318` | text on a gold fill |
| `analysis` | `#2962FF` | `#1B4ACC` | the analysis page accent |
| `social` | `#00B15C` | `#0E8A4C` | community |
| `premium` | `#D4AF37` | `#8A6318` | membership |
| `buy` | `#00B15C` | `#08703C` | an order, a long |
| `sell` | `#F6465D` | `#C9203A` | an order, a short, a refusal |
| `marketUp` | `#089981` | `#057A66` | a rising figure — TradingView's green, so a screenshot beside theirs agrees |
| `marketDown` | `#F23645` | `#D01427` | a falling figure |
| `warning` | `#F0B90B` | `#8A5606` | |

The reader's green-up / red-up choice is a `MarketColorScheme` applied over `marketUp` / `marketDown`
and the chart palette; the roles above are the defaults.

Shadows are black at low alpha, never coloured; blur is not used; gradients exist only in the brand
mark, the busy indicator and the chart's own fill (`check-motion-policy.sh` enforces all three).

## Spacing

Base 8 dp.

| Token | dp | |
| --- | --- | --- |
| `Half` | 4 | |
| `One` | 8 | |
| `OneHalf` | 12 | |
| `Two` | 16 | |
| `Three` | 24 | |
| `Four` | 32 | |
| `Six` | 48 | |
| `Gutter` | 16 | the page's side margin |
| `Stack` | 24 | between sections |
| `CardHorizontal` / `CardVertical` | 18 / 18 | inside a card |
| `Row` | 10 | vertical padding of a list row |

Touch targets are 48 dp minimum (`TouchTargetTest`); the chart toolbar's targets are `TOOLBAR_TARGET`.

## Shape

| Material slot | Radius |
| --- | --- |
| `extraSmall` | 6 dp |
| `small` | 10 dp |
| `medium` | 14 dp |
| `large` | 18 dp — the card |
| `extraLarge` | 20 dp — the sheet |
| `CoineProPillShape` | 50 % |

## Type

Face: **IRANYekanX (Eco)** — Regular 400 and Bold 700 are the weights shipped. Its Latin digits share
one advance (562/572 units, gated by `check_tabular_digits`), so figures column without `tnum`.
Medium and SemiBold are not in the tree; adding them is a licence question for the owner.

| Style | Size / line | Weight | Tracking |
| --- | --- | --- | --- |
| displayLarge | 52 / 62 | Bold | −0.5 |
| displayMedium | 42 / 52 | Bold | −0.25 |
| displaySmall | 34 / 42 | Bold | |
| headlineLarge | 30 / 38 | Bold | |
| headlineMedium | 25 / 32 | Bold | |
| headlineSmall | 22 / 29 | Bold | |
| titleLarge | 19 / 26 | Bold | |
| titleMedium | 17 / 23 | Bold | |
| titleSmall | 15 / 20 | Bold | |
| bodyLarge | 16 / 23 | Regular | |
| bodyMedium | 14 / 20 | Regular | |
| bodySmall | 13 / 18 | Regular | |
| labelLarge | 15 / 20 | Bold | |
| labelMedium | 13 / 17 | Bold | |
| labelSmall | 11 / 15 | Regular | +0.4 |

Job styles beyond the scale live in `CoineProTextStyles` (the account total, the price ladder…).

Digits: Persian in prose (`Int.toPersianDigits`, `Long.toPersianGroupedDigits`); Latin, `Locale.US`,
for every market figure through `NumberStyle` — chart axis, DOM, calculators, lists.

## Motion

Durations and curves match the web terminal's `foundation-v2.css`.

| Token | Value | For |
| --- | --- | --- |
| `FAST_MS` | 100 ms | a press, a colour under a finger |
| `STANDARD_MS` | 160 ms | a sheet, a chip, a card expanding |
| `SLOW_MS` | 240 ms | a full-screen change |
| `Standard` | cubic-bezier(.2, 0, 0, 1) | already on screen |
| `Enter` | cubic-bezier(0, 0, .2, 1) | arriving |
| `Exit` | cubic-bezier(.4, 0, 1, 1) | leaving |
| `fastSpatial` | spring 800 / 0.6 | a tap's response |
| `defaultSpatial` | spring 380 / 0.8 | a screen sliding, a sheet rising — the navigation slide |
| `slowSpatial` | spring 200 / 1.0 | a large surface crossing |

Springs move things; tweens change effects (opacity, colour, progress). Continuous motion is
allowed only where work is genuinely running, and every such site is marked
`continuousMotionAllowed` (the motion gate). The chart's fling is the platform's own curve
(`FlingSpline`), not any of these.

## Press

Scale on press, through `pressScale`:

| Token | Scale |
| --- | --- |
| `CTA` | 0.955 |
| `CONTROL` | 0.965 |
| `CHIP` | 0.98 |
| `CARD` | 0.99 |
| `ROW` | 0.997 |

## Chart

Measured against TradingView rather than chosen, so a screenshot of the same market agrees.

| Token | Value |
| --- | --- |
| up / down | `#089981` / `#F23645` |
| dark background / grid / text / crosshair / separator | `#0F0F0F` / `#282828` / `#B2B2B2` / `#787878` / `#2E2E2E` |
| light background / grid / text / crosshair / separator | `#FFFFFF` / `#D5D5D5` / `#0F0F0F` / `#8C8C8C` / `#E0E0E0` |
| trade ring | `#8D32A9` |
| grid alpha | 1.0 — the grid colour is the measured colour, not a white at low alpha |
| grid pitch | one row per 76 dp, clamped 3…12; 5 columns |
| grid stroke | 1 dp dot, 3 dp gap; hairline 0.8 dp |
| line width | 1.6 dp |
| axis padding | 10 dp (TradingView's tick 5 + inner 5) |
| candle body | 72 % of the bar slot; wicks and bodies snapped to device pixels |
| bar gap | up to 20 % of the slot at the zoomed-out limit; 3 dp bodies pinned between 2.5 and 4 dp spacing |

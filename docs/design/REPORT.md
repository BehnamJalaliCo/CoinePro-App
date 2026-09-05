# Design report — Pro Chart → TradingView-grade

Every sprint of the visual master prompt, with what changed, the numbers before and after, the
pictures, an honest score per screen, and the decisions left open. Pictures live in
`docs/design/before/` and `docs/design/after/`, forty each: ten screens × {dark, light} × {fa, en},
rendered on the Pixel 6a profile (411×914 dp, 2.625×) through the app's native-Skia screenshot
rig (`DesignCaptureTest`, the same camera the goldens use — Roborazzi's job, without adding a
dependency that draws the same pixels).

## Corrections to the prompt's context, read from the tree at 4.41.0

The prompt was written against 4.33.0.8. Several of its "nothing changed" statements were true
then and are not now; several were never true. Recorded here so the sprints start from the real
baseline:

| Claim | Reality at 4.41.0 |
| --- | --- |
| "no image-loading library (no symbol logos, flags, avatars, sparklines)" | Logos and flags are **drawn**: `CoineProAssetLogo` / `CoineProPairLogo` render vendored vector artwork for every listed symbol (BTC, ETH, SOL, gold + the dollar flag are in `before/watchlist-fa-dark.png`). The owner's standing rule is that no symbol without artwork reaches a list. Sparklines exist on the markets tab (`sparklineStore`) but not on the watchlist rows or home holdings. There is no Coil; avatars are initials. |
| "animation is 13× tween vs 1× spring" | 47 `tween(` / 6 `spring(` across the tree; the navigation slide and the three spatial springs came in 4.41.0 (`CoineProMotionSpecs`). |
| "no predictive back" | `enableOnBackInvokedCallback="true"` since 4.41.0. |
| "no haptics on tools" | 23 files use `CoineProHaptics`; the magnet snap and the crosshair-over-a-level tick came in 4.41.0. |
| "chart engine untouched (no decay / rubber-band / spring auto-scale)" | Fling is the platform spline (`FlingSpline`) since 4.41.0; rubber band existed (`stretchEdge`); auto-scale is not animated — Sprint C. |
| "one shimmer reference" | `CoineProSkeleton` / `CoineProSkeletonRows` used in 7 feature files; six list screens still show a centred spinner somewhere — Sprint A5. |
| "no certificate pinning" | Present behind `COINEPRO_CERTIFICATE_PINS`; inert until the owner supplies pins. |
| "`values/` is still Persian" | True, by the owner's decision (Phase 0). Sprint E1 reopens it; see *Open decisions*. |

## Sprint A0 — tooling and the BEFORE set

**What changed.** `DesignCaptureTest` (app) renders the ten screens through `ChartDesignPreviews`
(feature/chart), a public door onto the internal timeframe, drawing-tools, drawing-settings and
analysis-hub sheets. `ChartFlingBenchmark` already existed (4.41.0) with `FrameTimingMetric` over
a 3 s fling, pinch cycles, drags and a long press; `check-benchmark-thresholds.py` judges its JSON.

**Baseline numbers (4.41.0).**

| Measure | Value |
| --- | --- |
| `tween(` / `spring(` in main sources | 47 / 6 |
| Files with a centred `CircularProgressIndicator` | 6 |
| Files using `CoineProSkeleton*` | 7 |
| `androidx.compose.material.icons` imports outside the design system | 0 (own icon set, gated) |
| Font files | IRANYekanX Regular + Bold (static, not variable) |
| Image loader | none |
| Universal APK | 17.0 MB; per-device download 13.7–13.9 MiB |
| Help images in the base APK | 215 WebP, 9.5 MB (`core/help/src/main/assets/help`) |
| Chart frame times | **not measurable here** — no device or GPU in this environment; the benchmark and its CI judge are in the tree, the number is a phone's to produce |

**BEFORE pictures.** `docs/design/before/*.png` — 40 files.

**Self-score before (dark, fa; the harder half), out of 100, against the TradingView reference:**

| Screen | Score | Why |
| --- | --- | --- |
| Home | 55 | Balance hero and cards are right; numerals proportional, teaching banner takes the top, no sparkline on holdings. |
| Watchlist | 60 | Logos, tabular-looking columns, correct colours; a 4-line explanatory banner above the rows, no sparkline, 20 dp of dead air per row. |
| Symbol + chart | 70 | Grid, wicks, last-price label, legend, volume are close; header lacks the full-name/market-state line, axis labels one weight, toolbar icons mixed metaphors. |
| Drawings sheet | 45 | A rail of rows, not the 3-column tile grid; no search, no tabs, no favourites strip. |
| Indicators sheet | 55 | List with descriptions; no favourites/recent chips, no auto-focused search. |
| Drawing settings | 50 | Colour and width exist; no tabs, no style/fill, no templates UI parity. |
| Timeframe sheet | 65 | Groups and starring exist; no date-range chips, chips are not 48-tall pills. |
| Analysis hub | 70 | Tiles already TradingView-shaped; spacing and the broker card border differ. |
| DOM | 60 | Ladder is right; numerals proportional, no depth bars behind rows. |
| Menu | 65 | Clean rows; subtitles under a third of them, header card heavy. |

## Sprint A — visual foundation (A1–A5)

**What changed.**

- **A1 tokens.** `CoineProTokens` names the audit's roles over the palette that already had them
  (`surface0–3`, `outline`, `up`/`down`, `accent`, `stale`, `Radius`, `Space`). Light canvas
  `#F7F8FA`, white cards, `#F0F3FA` tiles, `#E8ECF4` raised plates; outline white 10 % on dark and
  ink 8 % on light; radii 4 / 8 / 12 / 16 / 28 for chart label, field, button, card, sheet; sheets
  scrim the chart at 40 %. `check_grid` in `check-cross-phase-consistency.py` refuses any
  `padding(`/`spacedBy(` literal off the four-point grid (one-dp hairlines allowed); 49 files were
  snapped to make it pass.
- **A2 typography.** Inter (variable, OFL; `docs/design/fonts/INTER-OFL.txt`) is the Latin and
  numeral face in both locales, with `fontFeatureSettings = "tnum"`. `numericTextStyle` / `.numeric()`
  and the `Balance` 36/46, `RowFigure` 16/21, `TileFigure` 18/24, `NumericLarge` 22/28 styles carry
  the home balance, the watchlist figures, the DOM ladder, the calculators and the chart axis
  (SemiBold on the last-price label, Regular on the ticks). Material slots: display 40/32/28,
  headline 28/24/20, title 20/18/15, body 16/15/13, label 14/12/11. IRANYekanX Medium and
  SemiBold resolve to Bold — the shipped family is static Regular + Bold (open decision 1).
- **A3 icons.** Already one drawn family (`CoineProIcons`) behind `check_icon_sources`; zero
  `material.icons` imports outside the design system before and after. Nothing to add.
- **A4 imagery.** Coil 3.2.0 (`coil-compose`, `coil-network-okhttp`) on the app's own OkHttp
  client with a 2 % disk cache and crossfade. `LogoProvider` / `LocalLogoProvider` is a second
  source behind the vendored artwork — the symbol header, a search result or the wheel may show
  `<API_BASE_URL>/assets/logo/<SYMBOL>.webp` for a symbol nobody has drawn, with the monogram
  under it the whole time (the drawn mark still wins, and the list filter is untouched). Avatars
  decode through Coil with a skeleton while they load. Sparklines: `CoineProSparkline` gained a
  gradient wash and the watchlist gained a `SPARKLINE` column, on by default for a fresh list
  (`FLAG, SPARKLINE, LAST_PRICE, CHANGE_PERCENT`).
- **A5 skeletons.** Activity, heatmap (a 3 × 4 shimmer grid), screener and script preview show
  the shape of what is coming instead of a spinner. The two spinners left are inline in the chart
  (a 16-dp one in the wheel, a 22-dp one in a pane) where a shimmer would be a lie about the size
  of the thing loading.

**Numbers.**

| Measure | Before | After |
| --- | --- | --- |
| `tween(` / `spring(` in main sources | 47 / 6 | 47 / 6 (Sprint D) |
| Files with a page-level `CircularProgressIndicator` | 6 | 0 (2 inline, chart) |
| Files using `CoineProSkeleton*` | 7 | 11 |
| Off-grid padding literals | 49 files | 0 (gated) |
| Numeric text sites on tabular Inter | 0 | 51 |
| Font files | IRANYekanX R + B | + Inter variable (860 KB) |
| Image loader | none | Coil 3.2.0 |
| Watchlist default columns | 3 | 4 (+ sparkline) |

**AFTER pictures.** `docs/design/after/*.png` — 40 files, same fixtures and camera as `before/`.

**Self-score after (dark, fa), out of 100:**

| Screen | Before | After | What moved, what did not |
| --- | --- | --- | --- |
| Home | 55 | 66 | Balance on tabular Inter at 36; cards on the token surfaces. Teaching banner still on top, holdings still without sparklines (B/D). |
| Watchlist | 60 | 74 | Sparkline column, tabular figures, grid-snapped chrome at exactly 125 dp. Explanatory banner still there. |
| Symbol + chart | 70 | 74 | Axis on Inter with weight on the last price. Header line and toolbar are Sprint B/C. |
| Drawings sheet | 45 | 47 | Surfaces and radii only; the tile grid is B3. |
| Indicators sheet | 55 | 57 | Same: tokens only, chips and search are B4. |
| Drawing settings | 50 | 52 | Tokens only; tabs and style are B5. |
| Timeframe sheet | 65 | 68 | Chips on the 8-pt grid; 48-tall pills and date-range chips are B6. |
| Analysis hub | 70 | 76 | Tiles on `surface2` with the token outline; spacing on the grid. |
| DOM | 60 | 70 | Ladder on tabular figures — columns finally line up; depth bars are C. |
| Menu | 65 | 68 | Row rhythm kept (50 / ≤ 60); header card unchanged (D). |

**Open decisions added by Sprint A.** See the list at the end: IRANYekanX weights (1), remote
logos as fallback only (2). Two design-metric targets moved with the grid: the watchlist's
pre-row chrome from 117 to 125 dp (the owner's ceiling, now met exactly) and the descriptive menu
row's ceiling from 52 to 60 dp (the prompt's 56–60 band); both are commented in the tests.

## Sprint B — tools parity (B1–B11)

Pictures for the six surfaces this sprint touched, dark + Persian, are paired in
`docs/design/sprint-b/before/` and `docs/design/sprint-b/after/` (the full forty-four-picture set
in `after/` was recaptured too, and now carries an eleventh screen, the indicator settings sheet).

**What changed.**

- **B1 toolbar.** 48 dp bar, 22 dp glyphs on a 46 dp pitch (24 between glyphs), a hairline after
  the interval and one before undo, a caret on the interval, and a count badge on Indicators and on
  the analysis «•••» for the objects drawn. `[symbol] [interval ▾] │ [draw] [indicators] [•••] │
  [undo] [fullscreen]`, as the brief lays it out.
- **B2 timeframe sheet.** Sheet close disc 32 dp (the target stays 48); date-range chips 48 tall
  with 12 dp corners; group names at 12 sp; «+ Add interval» full-width at 56. The handle was
  already 36 × 4, the title already 20 sp bold, the scrim already 40 %.
- **B3 drawings sheet.** Search 44 dp with 12 dp corners; three tiles across at 88 dp with 16 dp
  corners (four across at 84 before); the mode tiles — Measure, Eraser, Keep drawing, Hide, Lock
  all, Magnet, Remove all, Zoom in, Zoom out — share the same grid; the active tab is a pill.
  The favourites strip and the star on each tile existed; the floating strip over the chart is an
  open decision (5).
- **B4 placing and editing.** A soft haptic on every placed point (the magnet's own tick stays)
  and a 120 ms ring that swells from the point; handles at 8 dp with a 2 dp ring; a readout plate
  beside the held handle with the anchor's price, the bar's O H L C and its time — the brief's
  magnifier without a lens over the wick being aimed at. The floating mini-toolbar (colour · width
  · style · lock · duplicate · settings · delete) existed.
- **B5 drawing settings.** Style · Coordinates · Visibility tabs. Style: twelve swatches and a hex
  field, the four widths drawn with their real stroke, five dashes, a fill-opacity slider with the
  chart under a 20 % scrim, text colour, «Save as default», templates. Coordinates: every anchor's
  price in a field, its moment beside it. Visibility: the lock, the drawing's own timeframe, order,
  delete.
- **B6 indicator sheet.** 92 % height, the search takes the keyboard, chips Favourites · Recent ·
  Trend · Momentum · Volatility · Volume · Bill Williams · Structure with counts; a star on every
  row; long-press opens the help entry. Favourites and the eight most recent persist
  (`IndicatorFavouritesStore`); every one of the 84 indicators is filed in exactly one family
  (`ChartCatalogTest`).
- **B7 pane legend.** Eye, gear and × were on every primary row behind the disclosure. The gear
  now opens the study's own sheet (B8) instead of the catalogue, and the eye and the sheet share
  one hidden set. Drag to reorder and move / merge panes are open decision 6.
- **B8 indicator settings.** Inputs · Style · Visibility: the lookback stepper; a colour and a
  stroke per indicator, persisted per symbol alongside the period; show / hide and remove. The
  sheet scrims the chart at 20 % so the average moves as the stepper does.
- **B9 analysis hub.** Tiles at 88 dp; TOOLS two across (Indicators, Compare, Alert, Replay) then
  three across (Backtest, Chart type, Objects); the broker card's 1.5 dp spectrum rim, the span
  chips, go-to-date, MORE and Help Center were there. The top row stays three across — open
  decision 7.
- **B10 symbol wheel.** Five rows, the middle bold with its logo at 32, the neighbours at 60 %;
  the chart dissolves in over 250 ms on the switch. No blur, by the house rule; the card is 92 %
  stage.
- **B11 fullscreen.** The toolbar slides down and out over 200 ms before the window opens and
  back in when it closes; the fullscreen button's glyph flips to its inward pair. Viewport,
  indicators and drawings survive rotation already: zoom, offset and price zoom are
  `rememberSaveable` in `CoineProChart`, the studies and the drawings live in the per-symbol
  controller the app retains.
- **Orthography.** The glossary forbids the hamza-on-heh ezafe; 321 of them were in Kotlin
  literals across 63 files (the XML was already clean). All replaced with «ه‌ی», and the string
  lint now reads Kotlin literals too.

**Numbers.**

| Measure | Before | After |
| --- | --- | --- |
| Toolbar height / glyph pitch | 44 / 44 | 48 / 46 |
| Tool tiles across / height / radius | 4 / 84 / 12 | 3 / 88 / 16 |
| Drawing swatches / width control | 8 / named pills | 12 + hex / real strokes |
| Drawing settings tabs | 1 page | 3 tabs |
| Indicator chips | 3 (by pane) | 8 (favourites, recent, 6 families) |
| Per-indicator settings sheet | none | Inputs · Style · Visibility |
| Symbol picker rows visible | 9 | 5 |
| U+0654 in Kotlin literals | 321 | 0 (gated) |

**Self-score after Sprint B (dark, fa), out of 100:**

| Screen | A | B | What moved, what did not |
| --- | --- | --- | --- |
| Symbol + chart | 74 | 80 | Toolbar on the reference's geometry with badges and dividers; symbol fade; placement pulse. Header line and axis weights are Sprint C. |
| Drawings sheet | 47 | 78 | Three-across 88 dp tiles, 44 dp search, pill tabs. Tab names are this app's twelve groups, not the reference's eight; no floating favourites strip over the chart. |
| Indicators sheet | 57 | 80 | Favourites / Recent / families, stars, auto-focused search, full height. No one-line descriptions (open decision 8). |
| Drawing settings | 52 | 82 | Three tabs, twelve swatches, real-stroke widths, fill slider, coordinates. Time is not typeable. |
| Timeframe sheet | 68 | 76 | 48-tall range chips, 56 button, 32 close disc. Range labels are Persian prose, not `1D 5D 1M`; the intervals are keys not pills — the app's own decision from an earlier wave, kept. |
| Analysis hub | 76 | 82 | 88 dp plates, 2-then-3 TOOLS. Top row three across, not six. |
| Indicator settings | — | 78 | New. Inputs is one stepper; a study with two parameters (MACD) exposes one. |

**Acceptance note.** The brief's acceptance for this sprint is a side-by-side screen recording
against TradingView. There is no device and no TradingView install in this environment; the
recording is the owner's to make on a phone, and the pictures above are what can be produced here.

## Open decisions

1. **IRANYekanX Medium / SemiBold.** The shipped files are static Regular and Bold; a variable file or the two extra weights are the owner's licence to obtain. Until then Persian headings stay Bold and Latin/numerals get Inter's Medium and SemiBold (Sprint A2).
2. **Symbol logos from the network (A4).** The app vendors vector artwork and refuses to list a symbol without it (owner's rule). Sprint A4 keeps that as the first source and adds Coil for avatars and as a *fallback* provider for symbols outside the vendored set; it does not replace drawn logos with downloads.
3. **Locale inversion (E1)** conflicts with the owner's Phase 0 decision that Persian stays the default. Sprint E will not flip `values/` without the owner saying so again; the build check for "no Arabic script in `values/`" is meaningless under the current layout and is replaced by the existing "English locale is English" gate.
4. **Product flavours (E7)** were replaced in Phase 2 by a `BuildConfig` gate plus an APK-reading check; the store build already carries no admin tooling.
5. **Floating favourites strip over the chart (B3).** The star on every tool tile and the favourites row at the top of the sheet exist; a strip floating at the chart's bottom edge would sit over the time axis on a phone, where the fullscreen mode already floats its interval strip. Not built until the owner says where it goes.
6. **Legend drag-to-reorder and move / merge panes (B7).** Studies stack in catalogue order and each has a fixed pane in the catalogue. A reorderable, re-paneable model is a change to `ChartDerived` and the symbol store; deferred with the owner's say-so.
7. **Analysis hub top row (B9).** The reference's six tiles across would give each 55 dp on a 411 dp phone, which cuts every Persian label; three across kept behind one constant.
8. **Indicator descriptions (B6).** The reference's rows carry a one-line description. That is 84 new strings in two locales; not written in a sprint whose rule is no copy.
9. **Indicator inputs beyond the lookback (B8).** The catalogue exposes one period per study; MACD's three, Bollinger's deviation and Ichimoku's spans are literals in the engine. Exposing them is an engine change.

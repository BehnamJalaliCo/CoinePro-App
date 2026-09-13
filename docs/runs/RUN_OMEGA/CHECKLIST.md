# RUN Ω — the end state, line by line

`✅` means done and proven by the evidence beside it. `❌` means not yet — including anything that
was narrowed, per R3. Every line is re-read at the end of every phase, not once.

| # | The line | State | Evidence |
|---|---|---|---|
| E1 | First run: one question, then BTCUSDT H1 with the Signal Layer and Rasad's three sentences | ❌ | Ω3 |
| E2 | Every built-in and every script exposes state, markers, confidence % with N, and a sentence; the legend row opens Explain | ✅ | `SignalSpec`, `ConfidenceEngine`, `ChartSignalEngine`; `SignalLayerTest` (16), `ChartSignalEngineTest` (7), `sem_signal_verdict.nama`; `app/build/proof/omega-signal-layer-fa-dark.png`, `omega-explain-sheet-fa-dark.png` |
| E3 | A Setup score, 0–100, grey→gold, with N signals, that opens into what contributed | ✅ | `ConfidenceEngine.setupScore`; the chip in `omega-signal-layer-fa-dark.png`; `ExplainSheetBody(id = null)` |
| E4 | Replay Arena: daily challenge, discipline + P&L score, streak, league, share card | ❌ | Ω4 |
| E5 | Rasad coach: read this chart, suggest an alert, review my last paper trade | ❌ | Ω4 |
| E6 | Chart owns the phone: ≥ 60 % height, one accent, no prose, haptics, flash, springs | ❌ | Ω2, most of the way — see the table below for what is done and what is not |
| E7 | Simple/Full mode, switchable anywhere in one tap, nothing lost either way | ❌ | Ω3 |
| E8 | Share card 1080×1080 from any chart, signal or Arena result, in ≤ 3 taps | ❌ | Ω4 |
| E9 | Market Mood strip on Home | ❌ | Ω4 |
| E10 | Every one of the above on the tablet, Explain as a side panel | ❌ | Ω5 |

## E6, item by item

E6 is the only line that is a list rather than a feature, so it is broken out. It stays ❌ until
every row below is ✅, per R3.

| Ω2 item | State | Evidence |
|---|---|---|
| The top «→» row gone; readings and the studio off the page under the toolbar | ✅ | `ChartSheet.READINGS` behind the «…» hub; `RunGProofTest.theReadingsAreNotOnTheChartPage` asserts neither the drawer's handle nor its contents are on the phone page |
| Price pane ≥ 60 % of the screen on a phone | ✅ | `PLOT_SCREEN_FRACTION = 0.72f` in `ChartScreen`; visible in `chart-fa-411.png` |
| Crosshair = axis labels + an OHLCV reading, no floating pill | ✅ | `drawCrosshair` draws the price tag and the time tag and nothing else; `legendRows` gained `V` on its widest form (`ChartLegendOverlay`) |
| «+» on the price axis while scrubbing → alert **or** paper order | ✅ | `PriceAxisAlertAffordance` with `onRequestOrderAt`; `ChartScreen` builds `TradeFromChart.defaultOrder` and opens the setup sheet |
| Draggable alert lines | ✅ | `PriceAxisAlertLines` + `ChartAlertLine`; `LocalAlertStore.setValue` re-arms a moved one-shot |
| A/L mini buttons | ✅ | `PriceAxisScaleMinis` — `A` fits the visible bars, `L` flips the axis |
| A 3 s toast for an alert created and an alert fired | ✅ | `ALERT_TOAST_MILLIS = 3_000L` on all three created paths; the fired toast is `ToastTone.NEUTRAL`, 3 000 ms |
| One accent (gold) everywhere; blue only as an indicator colour | ✅ | `PageAccent.ANALYSIS`/`SOCIAL` resolve to the brand gold; `PageAccentTest` (5) |
| Light-theme candles saturated | ✅ | `TradingViewPalette.LIGHT_UP` / `LIGHT_DOWN`; the re-recorded `chart-*-light.png` goldens |
| Stale dimming resets, with a test | ✅ | `ChartStaleTest` — 7 cases, two added in Ω2 for the retry path and the refused refresh |
| A Midnight (true black) dark option | ✅ | `CoineProMidnightPalette`, `ThemeMode.MIDNIGHT`, the swatch in `AppearanceSheet`; `SurfaceLadderTest` runs over all three palettes and holds three Midnight-specific properties |
| Zero explanatory prose | ❌ | The tip-class notes now route through `CoineProNote`, so `NotePolicy` decides inline or ⓘ for each — but the *decision* per note has not been taken. The timeframe sheet, the library cards and the sync banner are untouched |
| Brand strings «پرو چارت» / "Pro Chart" | ✅ | `ProChartBrand.PRO_CHART_FA` delegates to `BrandConfig.DISPLAY_NAME_FA`; the last «کوینه‌پرو» is out of `community_subtitle`; the consistency gate holds every resource file |
| Haptics ≥ 12 sites across CONFIRM/REJECT/CLOCK_TICK/LONG_PRESS/CONTEXT_CLICK | ✅ | 83 sites: 67 `select`, 13 `commit`, 2 `reject`, 1 `longPress`, 1 `contextClick`. `CoineProHaptics.contextClick` is new in Ω2 |
| Price flash 200 ms | ✅ | `FLASH_MS = 200` in `CoineProMotionEffects` |
| Rolling digits | ✅ | `CoineProRollingNumber`, on the market preview's live price |
| Springs on sheets, chips and navigation | ✅ | `CoineProMotionSpecs`; the motion gate asserts no tween on a slide, an expand, a placement or a shared element |
| Shared elements on three flows | ✅ | `CoineProSharedElement` + `SharedElementNav`; markets → chart, signals → detail, explore → article |
| Snackbar + Undo on removing a watchlist row, a drawing and an alert | ✅ | `onToggleWatchAnnounced`, `deleteDrawingAnnounced`, and the alert centre's own undo |
| One confetti for the first script on a chart, the first alert, a seven-day streak | ✅ | `CoineProConfetti` + `CoineProCelebration`; three call sites, each keyed to a persisted once-per-install flag |
| Before/after screenshots for every screen touched | ❌ | The chart goldens are re-recorded, so the *after* exists for every chart surface. A paired before/after set is not assembled |

## Decisions taken for the owner, with the reason

1. **The Now strip is under the plot, not under the legend.** The legend is drawn inside the canvas
   by the chart engine; a row «under» it would be positioned against a plate whose height changes
   with the number of studies. Under the plot it is the first thing below the candles, and it costs
   the plot nothing on a chart with no studies on it.
2. **The Setup score is the first chip of that strip rather than a header.** The chart's header was
   deleted in 4.70.0 for the reason E6 restates — the instrument and its price are the legend's own
   first two lines — so re-adding one to carry the score would undo the line it sits beside.
3. **Confidence is a base rate, never a probability.** The sample size is printed beside every
   percentage and under eight samples no percentage is printed at all. `ConfidenceReport.THIN`.
4. **Direction is green or red; confidence is grey to gold.** A win rate in green on a chart where
   green means «up» would be read as a buy. The dot is the market's colour, the figure is the
   brand's, and they never swap.
5. **A study's sentence is a template, not a model.** Sixteen phrasings cover eighty-three
   indicators, they are computed on the phone from the same arithmetic that drew the line, and they
   are identical every time. `NoteShape`.
6. **`signal(condition)` gained a short form rather than a second function.** One argument is a
   verdict, three are a setup: to a reader they are the same sentence with more or less detail.
7. **`PageAccent.DESTRUCTIVE` keeps its red.** «One accent» is about domains. Destruction is not a
   domain — it means «this cannot be undone» — and it is the one press in the app that must not wear
   the colour that means «press me». §2's «green and red are the market's» is about *direction*; red
   here is not «down», it is «gone».
8. **The Persian brand name is «پرو چارت», two words.** The prompt writes it that way and so does
   `BrandConfig.DISPLAY_NAME_FA`, which the consistency gate holds every resource file to.
   `ProChartBrand.PRO_CHART_FA` held «پروچارت» against it — one word, from the logo — and the gate
   could not see it because it reads resources and that is Kotlin. It now delegates, so there is one
   spelling in one file.
9. **Midnight inserts a rung at the bottom rather than shifting the ladder down.** Shifting every
   rung put `surfaceRaised` at `#171C24` over a `#0B0E11` card, where *linear* luminance between two
   near-blacks is a few thousandths and a lifted plate stops reading as lifted —
   `SurfaceLadderTest` caught it. `surface` becomes the dark theme's stage instead, so Midnight has
   one more step of structure than the dark theme and every ink keeps the contrast it was measured
   at.
10. **The light theme's candles leave the reference's hex.** `#089981` was measured on `#0F0F0F` and
    reads 3.3:1 on white, where a five-pixel body is a tint rather than a candle. The light pane
    keeps the hue and takes the lightness to the values this app's own light palette already holds,
    so a rising candle, a sparkline and a green percentage are finally one colour. The dark theme is
    untouched and still carries the published values exactly.
11. **A dragged alert re-arms.** A fired one-shot is kept so a reader can see what it was.
    Dragging its line is not a note about history — it is «tell me at *this* price instead» — so
    `LocalAlertStore.setValue` clears the fire stamp and sets `active`.

## Narrowings, per R3

Each of these is ❌ above and is written down here rather than left to be noticed.

1. **Persian literals outside `feature:chart`.** Ω2's «one language» line is done for the chart:
   `feature:chart` went from 397 hard-coded Persian literals to **zero**, in 21 files, with 331 new
   string pairs. The same class of leak remains in other modules — `CandlePatterns.persianName` in
   `:chart-core` (sixty-odd pattern names), `WatchlistFlag.persianName` in `:core:datastore`,
   `DrawingTools`' own labels, and the `*_persianLabel` extensions in `feature/search` and
   `feature/profile`. None of it is on the chart page. It is a separate sweep and it is named here so
   it is not mistaken for done.
2. **«Zero explanatory prose» is a mechanism, not yet a decision.** Every tip-class note in
   `feature:chart` now goes through `CoineProNote`, which is what folds a tip into an ⓘ rather than
   drawing it — so the *lever* exists and `NotePolicy` is the one place it is pulled. Which of the
   thirty-four notes should be folded has not been decided, and the timeframe sheet, the library
   cards and the sync banner are untouched.
3. **Before/after pairs.** The *after* exists for every chart surface, because the goldens were
   re-recorded. Nobody assembled the pairs.

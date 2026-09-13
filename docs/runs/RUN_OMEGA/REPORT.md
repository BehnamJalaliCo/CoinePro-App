# RUN Ω — what changed, phase by phase

One section per phase, written as the phase lands rather than at the end. Numbers are measured, not
estimated; a figure with no command behind it does not appear.

---

## Ω1 — the Signal Layer (4.75.0, `5750f44`)

### The thesis, as code

Three new files in `:chart-core`, which is the module that has no Android on its classpath and is
therefore the one the web terminal will reuse:

* **`SignalSpec`** — reads any of the eighty-three built-ins and says what it is saying. Four rules
  cover all of them rather than eighty-three branches: an *oscillator* against its own bounds, a
  *reference line* against the close, a *zero cross*, a *breakout*. `MEASURES` — the ATR, the ADX,
  the choppiness index, eleven in all — are explicitly silent, because «the ATR is 41» is a number
  and not a reading. `defaultStop` is the swing over ten bars, ± a quarter of an ATR.
* **`ConfidenceEngine`** — walks up to five hundred bars, takes every signal the study fired, and
  measures what happened over the next five, ten or twenty. A stop hit before the horizon is −1 R; a
  signal whose horizon has not printed is skipped rather than counted as a loss. Out comes a win
  rate, an average R, a sample size and the last five outcomes.
* **`ChartSignalLayer`** — the whole chart's answer, computed off the main thread and published once,
  so the legend, the Now strip, the Explain sheet, the markers and the coach cannot disagree.

### What a reader sees

A **Now strip** under the plot: the Setup score first, then one pill per study — a state dot in the
market's colour, the study's name, and «61 % of 18» in a grey-to-gold that can never be mistaken for
a direction. Tapping any of it opens **Explain**: the sentence, the base rate with its sample size,
the horizon chips, the last five outcomes as dots, the study's default stop, and three actions.

### NamaScript

`signal(...)` gained a short form. `signal(ta.crossover(fast, slow), text = "…")` is a verdict; the
three-argument form is still a setup. A script that calls neither is read off its own first plot by
the same rule a built-in's line is read by, so the promise holds for the ninety per cent of scripts
that will never call `signal`.

### Numbers

| | |
|---|---|
| New tests | 23 (`SignalLayerTest` 16, `ChartSignalEngineTest` 7) |
| New conformance script | `sem_signal_verdict.nama` |
| Proof frames | 3 (`omega-signal-layer-fa-dark`, `-en-dark`, `omega-explain-sheet-fa-dark`) |
| Gates | all five |

---

## Ω2 — one accent, one language, and the chart's own buttons (4.76.0 → this commit)

### One accent

`PageAccent.ANALYSIS` and `SOCIAL` resolved to blue and green for twenty-five versions on the
argument that a domain colour is never decorative. That argument is sound about buttons and wrong
about charts: on a surface where green means «the price went up» and red means «it went down», a
green «follow» button and a blue «add indicator» chip are two more colours competing with the only
two that carry a fact, and a reader scanning for the one thing they can act on has four candidates
instead of one. Both now resolve to the brand gold. `CoineProColors.Analysis` and `Social` survive as
*hues* — an indicator's line, an avatar's ring, a badge — and no longer paint a control.
`PageAccentTest` holds it: five cases, including «no control wears a direction colour».

`DESTRUCTIVE` keeps its red, and the reason is in the CHECKLIST's decision 7.

### Midnight

A fourth `ThemeMode`. The dark palette on `#000000`, so on an OLED panel the page and the chart's
pane are switched off rather than lit. Every ink, hue and rung above the card is the dark theme's by
construction — it is a `copy`. Three values move: the stage, the chart's ground, and the card that
sits on them.

The first attempt shifted the whole ladder down and `SurfaceLadderTest` caught it: near black, the
*linear* luminance between `#171C24` and `#0B0E11` is a few thousandths, so a plate lifted off a
card stopped reading as lifted. A rung is inserted at the bottom instead — `surface` becomes the dark
theme's stage, the value every ink in the file was already measured against — so Midnight has one
more step of structure than the dark theme and nothing needed re-measuring. Three Midnight-specific
tests hold all of that.

### The light theme's candles

`#089981` and `#F23645` were measured off TradingView's chart on `#0F0F0F` and are exact there. On
white the green reads **3.3:1**, and a five-pixel body of it is a grey-green tint rather than a
candle. The light pane keeps the hue and takes the lightness to `#057A66` / `#D01427` — 4.62:1 and
5.02:1 — which are the values this app's own light palette already holds for `marketUp` /
`marketDown`. So in the light theme a rising candle, a rising sparkline and a green percentage are
finally one colour. The dark theme is untouched and still carries the published values exactly.

### The price gutter

Four things, all of them one tap from a price a reader has stopped on:

* the `+` chip now offers an **alert or a paper order** — the side is read off the market, because a
  price below the last trade is where somebody wants to get long;
* the reader's own **alerts are drawn**, as dashed gold lines, and **dragged** by their axis tags —
  the tag and not the line, because a one-pixel line on a chart whose every other gesture is a pan
  is not a drag target;
* **`A` and `L`** sit in the corner of the price scale. Both existed: auto-fit was a double tap
  nobody found and the log switch was three taps into a sheet;
* the crosshair's reading gained the **volume**, on its widest form only, so the first thing a narrow
  phone drops is the volume rather than the open.

### Feedback

The price flash went from 380 ms to **200**: at 380 the tint on a crypto pair never went out, which
is the same failure 620 ms had, arrived at more slowly. `CoineProRollingNumber` is new and is used on
exactly one figure — the live price a reader opened a sheet to watch — because forty rows of rolling
digits is a slot machine and the flash is what a list uses. Un-starring a market and removing a
drawing gained an undo. `CoineProHaptics` gained `contextClick`, which is the buzz a menu makes as it
lands and is a different answer to the finger from the long press that fired while it was still
holding.

And **confetti, three times per install**: the first script on a chart, the first alert, a seven-day
streak. Once, marked by a persisted key, with no way to show it twice. Confetti on the tenth alert is
a product congratulating somebody for typing a number.

### One language

`feature:chart` held **397 hard-coded Persian literals**. It now holds **zero**.

| | |
|---|---|
| Files touched | 21 |
| String pairs added (`values/` + `values-fa/`) | 331 |
| Pure-Kotlin producers converted to resource ids | 9 (`ChartRange`, `RepaintClaim`, `ChartExport`, `DRAWING_WIDTHS`, `LINE_STYLES`, `StudioSection`, `ReportTab`, `Side`, `DrawingSyncMode`) |
| Note keys registered in `tools/i18n/notes.tsv` | 34 |

Three shapes came up and each got the same answer: a composable reads `stringResource`; a top-level
table carries ids and the call site resolves them; a pure function that is unit-tested returns an
*identity* rather than a sentence. The third is the interesting one —
`chartExclusions` now returns `List<ChartExclusion>`, so `ChartProvenanceTest` asserts «the volume
reason is in the list» instead of «some string contains حجم», which is the thing it always meant and
could not say.

The extraction also *exposed* vocabulary the string lint had never been able to see, because it reads
resources and those were Kotlin: «نمودار» where the glossary says «چارت», «بازه‌ی زمانی» where it says
«تایم‌فریم», two informal imperatives, and `ProChartBrand.PRO_CHART_FA` holding «پروچارت» against
`BrandConfig`'s «پرو چارت». All fixed. That is the extraction earning its keep twice.

### Numbers

| | |
|---|---|
| New tests | 12 (`PageAccentTest` 5, `SurfaceLadderTest` +3, `ChartStaleTest` +2, `ChartProvenanceTest` rewritten to identities, `ChartRangeTest`/`RepaintClaimTest` to ids) |
| Goldens re-recorded | 40 (the light candles, the A/L minis, the legend's V) |
| Gates | all five |

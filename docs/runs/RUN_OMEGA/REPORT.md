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

---

## Ω3 — one question, one filter, and a chart in the list (4.77.0)

### The question

One screen between the splash and the app, three cards and a way out. It asks about *how much to
show*, not about experience, and the wording is the point: «تازه‌کارم» is something a person is
willing to say about themselves where «سطح دانش شما» is a test. Simple is first, deliberately — the
reader this screen exists for is the beginner, and putting them last would put the app's own
preference above the question's purpose.

Nothing it sets is a gate. `ReaderMode` is a statement about what to draw, it lives beside
`ThemeMode` rather than anywhere near an entitlement, and `ReaderModeTest` holds that: five cases,
including «the modes widen in one direction and never cross», which fails if a narrower mode ever
carries something a wider one does not.

### Simple mode is four handlers unset

The chart page did not grow a variant. Every advanced entry on it was *already* nullable —
`onOpenDepth`, `onOpenStudio`, `onOpenScript` and `ChartWorkbench.tools` — because a build without a
depth feed has to draw a page that makes sense, and «this reader asked for a simpler page» turned out
to be the same question with a different answer. So Simple mode is `ChartScreen` with those unset,
plus the band's pencil and the studio row under the plot on the same flag. Five reads of one
property, no second layout, and every control still built and still tested.

`RunOmegaProofTest` photographs both at 1280 dp and asserts the difference on the one thing with a
semantics tag of its own: `chart-tool-rail`, present in Trader and absent in Simple, same controller,
same candles, one parameter.

### The toggle, and the second key it needed

The «…» hub carries the flip, beside «خوانش بازار», because the moment somebody wants a simpler page
is the moment they are looking at one with too much on it — and a preference two screens away is one
they never find. The appearance sheet carries the full three-way choice, under its own heading,
between the palette and the language.

A toggle with one stored key can only return to a constant, which would have meant a Pro reader who
simplified the chart for one look coming back as a Trader with their workbench gone: the app quietly
demoting somebody for using a control. `reader_mode_full` holds the mode they came from, and
`setReaderMode` writes it on the way past too, so the settings page and the toggle agree about what
«full» means for this reader. `ReaderModeStoreTest` holds the round trip from both directions,
including the case where Simple was chosen on the settings page rather than by the toggle.

### The chart preview

A long press on a market used to answer «what is this one doing» with a price, a pill and one day of
closes. Any other question meant opening the chart, which is a route, a candle request and a terminal
layout — four seconds on this audience's connection, which is long enough that most people do not
ask.

The sheet now carries six spans, a line a finger can run along, and one line saying what the market
is doing. The spans are `PreviewRange`, and each is drawn at a bar length both backends serve
directly so a chip is one request rather than a fold: `PreviewRangeTest` holds that every chip covers
the period it is named after, within the rounding a round bar count allows, and that no span draws
more points than a phone can stroke in one pass.

The scrub ticks **per detent, not per bar**. A year is 365 bars across a phone and a buzz per bar is
a vibration rather than feedback; twenty detents is a scale a thumb reads as separate events, and it
is a property of the hand rather than of the data, which is why it is a constant.

The summary line is `ChartReading.of` over the span's own candles — the same arithmetic the chart's
own reading panel uses, so the sheet and the chart behind it cannot disagree.

And a tap on a market row opens it, for everybody except a Pro reader. That is the brief's «setting
to skip for pros», and it is the setting they already have rather than a fourth switch asking the
same question in different words.

### Sign-up, at a save and nowhere else

A guest has the live catalogue, the chart, the Signal Layer, Explain, the watchlist, alerts, layouts
and the paper account. All of it is stored on this phone, so there is nothing to gate and gating
anything would be charging admission for something already built.

What an account buys is that the thing they just saved outlives this phone, which is a true sentence
exactly at the moment of a save. So it is the action on the toast that already says the save
happened — «نگهش دار», once per install, marked in the same dismissal store the teaching strips use.
The two remaining `GuestGate`s are the two server-fed surfaces, which have no local answer to gate.

### The states nobody designs

`CoineProEmptyState` and `CoineProErrorState` were already the only two and already shared one
private `StateBlock`, so the API allows exactly one action by construction. What had drifted was the
mark: five empty states passed no icon, which draws a sentence alone in the middle of a page — which
is precisely what a screen that failed to load looks like. They have their screen's glyph now.

Two new checks in the consistency gate keep it: `check_state_surfaces` fails on an empty state with
no mark and on a state illustration over 8 KB, and `check_coach_marks` holds the brief's «one 2-line
tooltip per screen» — exactly one strip per surface, no surface with strings and no screen, and no
teaching line over 150 characters.

### Numbers

| | |
|---|---|
| New tests | 18 (`ReaderModeTest` 6, `ReaderModeStoreTest` 7, `PreviewRangeTest` 6 — and 3 new proof cases in `RunOmegaProofTest`) |
| New gate checks | 2 (`check_state_surfaces`, `check_coach_marks`) |
| Proof frames | 3 (`omega3-first-run-fa`, `omega3-full-chart-fa`, `omega3-simple-chart-fa`) |
| New string pairs | 14 |
| Gates | all five |

---

## Ω4 — a coach, a challenge, the board's mood, and something to post (4.78.0)

### رصد, the coach that cannot lie

The Signal Layer got most of the way to «چارتی که حرف می‌زند»: every study says what it is saying and
prints the base rate behind it. What it did not do is *compose*. Four studies on a chart is four
sentences, and deciding which of them is the story is exactly the skill the reader does not have yet.

Rasad says three, in one order, always: what the market is doing, where the prices that matter are,
and what the chart's own studies make of it. Nothing calls a model. Every sentence is a template
filled from the same arithmetic that drew the lines — `ChartReading` for the trend,
`Structure.supportResistance` for the levels, the Signal Layer's own reads and score — so the coach
and the chart cannot disagree, the same chart always produces the same words, and there is no
sentence it can emit that a reader cannot check against the picture. `RasadCoachTest` holds both
halves of that: «the same chart always produces the same words», and «it names no level that is not
on the chart», which parses every number out of the level sentence and requires each to be one
`Structure` actually found.

It lives in `:chart-core`, which has no Android on it, so it ships to the web terminal with the
engine rather than being rewritten against it.

On the page it is **one line** — the trend sentence, in a row the height of a chip, under the Now
strip. Three permanent lines of prose under the plot is the explanatory text run Ω2 spent a version
removing. The other two, the suggested alert and the review of the reader's last rehearsal trade are
behind it.

Two decisions in the review are worth naming. The risk comes first and the result last, because a
winning trade taken without a stop is a worse trade than a losing one taken with a stop and every
review that leads with the profit teaches the opposite. And a record that does not say *whether*
there was a stop is not read as one that says there was none: the app's own book keeps the stop's
price only where the stop is what closed the trade, so a take-profit arrives with none attached, and
a coach that read that as «you traded without a stop» would be scolding somebody for a gap in a
database.

### میدان, the daily challenge

The replay engine has existed since run E and almost nobody opens it, for the reason almost nobody
opens a backtester: it is a tool with no question attached. The Arena attaches one — the same
instrument and the same window for everybody on a given day, five minutes, a paper account, and a
score.

The challenge is **computed from the date**, because no backend serves one and `BLOCKED.md` entry 2
names the endpoint. `Arena.challengeFor` is a written-out multiplicative hash rather than a platform
`Random`, so a phone, a JVM test and the web terminal give the same answer; `ArenaTest` holds that,
and that the window always has both a past and a future.

**Sixty of the hundred points are for how the trades were taken.** A five-minute replay scored on
profit alone teaches the one thing this product argues against. The profit half is capped at three
units of risk, so a reader who caught a ten-R move cannot out-score a disciplined session six times
over. `ArenaTest`'s «a disciplined loss beats an undisciplined win» is that argument as one
assertion, and it would fail on any weighting that got it wrong.

The Arena is a **mode of the chart**, not a screen: one band above the command band and one sheet at
the end. A second screen would be a second copy of the replay bar, the paper ticket and the setup
card, and the reader would be rehearsing on a chart that is not the one they trade on.

The streak is counted back over the rows rather than stored beside them, and *not having played yet
today does not break it* — a count that resets at midnight is a mechanic for making people anxious
rather than a record of what they did. The league is «you, over time» until there is somebody to
compare against.

### The board's mood

One strip on Home, above the reader's own markets and below their own money. The brief asks for
fear/greed; there is a published index by that name, it is somebody else's number and no backend
here serves it, so this measures **breadth** — the share of the board that is up today — and is
named for what it is. Under it, the biggest moves and «شلوغ و در حرکت», which is what «unusual
volume» honestly reduces to with one day's table and no per-symbol history.

Every part is independently absent. `MarketMoodTest` holds the one that would be invisible: a market
with no figure is not counted into the denominator, because a half-loaded table of four hundred
markets would otherwise drag every reading to the middle and report «مختلط» on a day the board is
flying.

### Something worth posting

`ShareCard` draws a 1080 × 1080 square rather than screenshotting a screen. A phone screenshot is
1080 × 2400, carries the reader's status bar, and is unreadable at the size a feed shows it — and
every surface that shows a shared image crops to a square, so what survives the crop has to be
designed. The card carries the plot in its top, the instrument, the move in a direction colour,
رصد's own sentences, and the mark and the host on the floor. The link is printed as text and not
embedded: a card travels through screenshots and re-encodings that strip every byte that is not a
pixel.

Two decisions came out of the first frame. The paragraphs take the *card's* direction rather than
each string's, or «BTC/USDT» and «+4.20%» hang left while the Persian sentences hang right and one
card reads as two. And the figures are bidi-isolated at the call site, or a signed percentage inside
a right-to-left paragraph comes out as «4.20%+», with the sign at the wrong end.

### The markets board

Two more lenses — «دنبال‌شده‌ها» and «پرمعامله‌ترین» — composing with the category tabs and the sort
in that order. Swipe-to-star on the markets list, armed at 56 dp and deliberately not on the
watchlist panel, which has a reorder drag of its own. And milestone alerts: three signed chips on the
preview sheet arming `CHANGE_24H_OVER` / `CHANGE_24H_UNDER`, which is the *day's* move — the figure
the row the reader pressed was showing — rather than a percent from the moment they pressed it.

### Numbers

| | |
|---|---|
| New tests | 41 (`RasadCoachTest` 12, `ArenaTest` 12, `ArenaStoreTest` 10, `MarketMoodTest` 7) |
| New pure modules of engine | 3 (`RasadCoach`, `Arena` in `:chart-core`; `MarketMood` in `:core:marketdata`) |
| Proof frames | 1 (`omega4-share-card.png`, which is the artefact rather than a picture of one) |
| Goldens re-recorded | 54 (the chart page gained رصد's line) |
| New string pairs | 26 |
| Gates | all five |

---

## Ω5 — the tablet, and the map to the web (4.79.0)

### Three panels, no second layout

Explain, رصد and the Arena result dock beside the plot rather than covering it. That cost three
`ChartSidePanel` declarations and nothing else, because all three were written as sheet *bodies*
rather than sheets from the day each landed — `ExplainSheetBody`, `RasadSheetBody`,
`ArenaResultBody`. On a phone a bottom sheet is the right shape: there is one column and the sheet is
the second. On a tablet a sheet that covers the chart it is explaining is the wrong shape for exactly
the same content. Same composable, two homes, nothing to keep in step.

The Arena's is the clearest case. On a phone the result covering the plot is right — the five minutes
are over and there is nothing behind it to look at. On a tablet there is: the chart the reader was
just scored on, with their own trades still marked on it, and the score is worth more read against it.

### The preview steps aside on two panes

`previewOnTap` is false where the layout is list-detail. The preview exists because opening a chart
costs a route and four seconds on this audience's connection; beside a two-pane layout it costs
neither — the chart appears next to the list with the list still on screen, which is the preview's own
argument, better. A sheet there would cover the answer.

### A frame that caught a real defect

The mood strip's tablet render showed the header, the counts and both chip rows — and no bar between
them. The two halves of the split bar asked for the row's full *width* where they should have asked
for its full *height*, so each measured against the weight rather than inside it and came out zero
points tall. It was invisible on the phone too; nobody had looked at it against a ruler. This is what
the render tests are for, and it is why the parity matrix is a count of renders rather than a
checklist of intentions.

### The map to the web

`docs/web/PLAN.md` §3a. Five of the seven Ω1–Ω4 pieces are already in `:chart-core` with no Android
on them — `SignalSpec`, `ConfidenceEngine`, `RasadCoach`, `Arena`, and the types the layer is made
of. `ChartSignalLayer` is a bag of those same types sitting in the feature module only because that
is where it was written; moving it down is a file move. `MarketMood` goes wherever the web's own
ticker type does. `ShareCard` is the one surface that does not cross, and the document says so
plainly: it draws with `android.graphics` because it has to work from a background thread with a
context and no composition, and on the web the same card is a canvas with `ShareCardContent` as the
contract both sides fill.

The Arena's daily pick is a written-out multiplicative hash rather than a platform `Random`, and that
was a decision taken *for* this document: «the same challenge for everybody» has to hold across a
phone, a JVM test and a browser, and only arithmetic spelled out in common code does.
`ArenaTest`'s «the same day gives the same challenge» is the web-parity test written a year early.

### Numbers

| | |
|---|---|
| New renders | 16 (`panelExplain` 4, `panelRasad` 4, `panelArena` 4, `homeMood` 4, each Fa-dark and En-light at Pixel Tablet and Tab S9 Ultra) |
| Parity matrix | tablet columns 20 → **26** renders each; regenerated from the tests |
| Defects the frames caught | 1 (the mood strip's split bar, zero points tall on every window) |
| Gates | all five |

### E6's last two rows, closed

**«Zero explanatory prose» was a mechanism waiting for a decision.** The decision is `NotePolicy`,
and reading it back: of the fifty-five notes `feature:chart` registers, exactly **one** is drawn
inline — `setup_paper_trade_note`, which is about putting real money on a level — and the other
fifty-four fold into an ⓘ with their full text one tap away. The three surfaces the row named were
each checked: the timeframe sheet's seconds note goes through `CoineProNote`, so do the pane-sync
notes, and the studio's five `studio_*_blurb` strings are a card's own content rather than a tip
under a control, which is why they were renamed off the `_body` suffix in the first place. The lint
fails the build on a demoted key that a source resolves itself, so the policy cannot be bypassed at a
call site.

**The before/after pairs exist.** `scripts/quality/gen_before_after.py` takes the commit a run began
at and writes one sheet per changed golden — the frame as it was on the left, as it is on the right,
at half width. Fifty-four sheets for run Ω, 1.9 MB as WEBP against nine as PNG, with an index naming
the `git show` that gets each full-resolution *before* back. The pair for `chart-fa-411` is the run
in one image: the disclosure at the foot of the page on the left, رصد's line and no disclosure on the
right.

## Ω-FIX — what the device found, and what it cost to be wrong about it (4.80.0)

The owner installed 4.79.0 on a phone, recorded two sessions and sent 229 proof files. The verdict
on the thesis was that it holds: «تز «چارتی که حرف می‌زند» واقعاً پیاده شده و روی دستگاه کار
می‌کند», the experience 4.6 → 7.2 out of ten, performance untouched at 120 fps with 1.2 % of frames
dropped and none at 60 Hz. The verdict on this repository's own documentation was worse: **eight
places where the device disagreed with `CHECKLIST.md`**, three of them 🔴.

### The one that matters most is not a bug

`CHECKLIST.md` said «ردیف → بالای چارت حذف شد» with a ✅ and an evidence cell beside it. The row was
in every portrait frame of both recordings. Nothing in the repository was lying deliberately; run Ω2
removed the app bar's *contents* — the depth button, the title, the avatar — read the result, and
wrote down that the bar was gone. The band was still there, fifty-six points of stage colour holding
one arrow, above the one page in this app whose entire product is the height of the plot.

That is a process defect rather than a code defect, and it has two answers in this version:

* **The rule is a function now.** `showsTopBar(route, isSubScreen)`, called by the shell and called
  directly by `ChartTopBarTest`. The old claim could only have been checked by running the app;
  this one is checked by the suite.
* **Every row of the E6 table names a frame, or says it cannot have one.** Seven rows are motion,
  timing or feel — a haptic, a 200 ms flash, a spring, a three-second toast — and there is no honest
  still of a spring. Those seven now say so and name the gate that fails when the thing stops being
  true. A blank cell is what let the «→» row ship.

### The three 🔴, in order of how much they misled a reader

**The Setup score was direction consensus wearing a confidence label.** The device showed «۱۰۰
صعودی» above four contributors reading 43 %, 40 %, 39 % and 40 %. Every one of them agreed, and
agreement was the whole measurement, so four studies that are wrong three times in five produced the
same number as four that are right. A hundred beside forty per cent is the app contradicting its own
figures on one screen.

It is now `|Σ(direction × winRate × w)| / Σw` with `w = min(1, samples / 30)`. Three properties
follow and they are the three the old one lacked: a chart cannot score above the records behind it,
a thin record counts proportionally less, and a study nobody has measured neither lifts the score nor
drags it down — it simply is not one of the things the number is about. `SetupScore.winRate` carries
the mean of the contributing records and the panel prints it under the figure, which is what makes
the number checkable rather than believable. The owner's own four contributors now score **41**.

**The «→» row.** Above.

**The one-line symbol chip was never in the E6 table.** It is in the Ω2 brief; it was dropped
silently from the tracking, which is exactly the narrowing R3 exists to catch, and on the device the
chip was still three lines with two grey tickers under the instrument the chart was drawing. It is
back in the table, and the chip now shows one name until a thumb is held on it.

### The five 🟠 and 🟡

* **RSI 48.6 read «نزولی».** The state was a single cut at the midpoint. There is a dead band now —
  a quarter of the floor-to-ceiling span each side, which on RSI's 30..70 is exactly the 40–60 the
  fix names and on every other oscillator is the same sentence at its own scale. A moving average
  gets the equivalent in the instrument's own units: neutral within 0.15 of an average range.
* **«بازار خنثی است و روند قوی خوانده می‌شود.»** Two measurements that are allowed to disagree —
  ADX says how hard, the bias says which way — printed in one sentence as though they could not.
  `RasadCoach.trendLine` now takes a *nullable* direction, so the contradiction is not representable
  in its arguments, and `RasadContradiction` walks all 108 combinations of the templates in both
  languages.
* **The Rasad strip ellipsised** — «با ن…». It wraps to two lines and clips rather than ellipsising,
  and every template is budgeted against those two lines in `:chart-core`, where the templates are.
* **«.در 11% از 9 بار درست بوده».** Three Latin runs in a Persian paragraph and the sentence's own
  full stop resolved against the wrong one. `BidiText.isolateNumbers` wraps every number, its
  separators, a leading sign and a trailing per-cent sign in FSI…PDI and leaves the sentence's
  punctuation outside. Applied at six surfaces, including the share card, whose `StaticLayout` has
  the same problem in an image the reader posts.
* **«زیر 91,263.03 اشتباه است»** named no stop and read as a verdict on the reader. It is «حد ضرر
  پیشنهادی: زیر ⁨91,263.03⁩».
* **The scrub chip sat on the plot.** 84 to 108 points wide against a 64-point gutter, right-aligned
  to the canvas with two points of deliberate bleed. It is the gutter's own width now, laid out from
  the axis hairline outwards, stacking the price over its actions instead of stretching across them
  — and `AlertChipGutterTest` holds that no pixel of it is over the plot at nineteen gutter widths on
  all four scale sides, which is a stronger claim than the one frame a screenshot could make.

### What this cost

| | |
|---|---|
| Files changed | 20 source, 7 test, 3 documents |
| New tests | `ChartTopBarTest`, `RunOmegaFixProofTest`, `BidiIsolateNumbersTest`, `AlertChipGutterTest`, `RasadStripTest`, plus cases added to `SignalLayerTest`, `RasadCoachTest`, `SymbolWheelTest` |
| Tests whose expectation changed | 3 — two setup-score cases and `ChartSignalEngineTest`'s, all three because a hundred is no longer the right answer |
| New proof frames | 6 |
| Goldens re-recorded | the fold-closed English chart pair, where the coach's line now takes two rows |
| New features | none, deliberately |

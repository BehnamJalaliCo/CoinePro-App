# RUN Τ2 — checklist

Four phases. Every line carries a state, the evidence behind it, and a frame — or, where a still
cannot carry the claim, the test that can and why a picture would be a lie.

States: ✅ done · ❌ not done, or narrowed (the row says exactly how) · ⏳ owed to the owner's device.

---

## The correction this run opens with

Phase A is written on a premise that is not true of this tree, and saying so is the first thing the
run owes the owner rather than something to bury in a report.

> «Phase Τ of RUN ΤΦΥ was not executed: in 4.92 the classes `ChartFling`, `ChartFrameRate` and
> `ChartStrokePredictor` … are byte-identical to 4.89.»

They are not. `2465e19` — **4.90.0 «run Τ: the frame the fling stood still on»** — is on `main`
between 4.89 and 4.91, and `git diff d3c0481..HEAD -- chart/ benchmark/` is six files: `ChartFling`
(+47), `KineticScroll` in `ChartPixels` (+13), their two test files, `ChartFlingBenchmark` (+43) and
a new `ChartFlingRegressionTest` (+60). The friction went 3.8 → **1.25** and the cut-off 20 →
**240 px/s** in that commit, and `docs/runs/RUN_TFY/CHECKLIST.md` carries the same seven rows with
the same measurements. What 4.92's APK contains is that build.

So this phase is an **audit against A1–A7 with the numbers re-measured on the current tree**, plus
the two things A asks for that run Τ genuinely did not leave behind. Nothing below is a re-statement
of a row without a fresh run behind it.

---

## PHASE A — the chart scroll

| Item | State | Evidence | Frame |
|---|---|---|---|
| **A1** Raw `VelocityTracker`, 100 ms, px/s, no density anywhere | ✅ | `CoineProChart.kt:1785` feeds the tracker from `awaitPointerEvent(PointerEventPass.Final)` — every `change.historical` sample and then the frame's own, so a 240 Hz digitiser is not resampled at the display's rate — and `tracker.calculateVelocity().x` goes **straight** into `kinetic.start` with nothing between the two calls. Compose's tracker fits over its own 100 ms horizon. `ChartStrokePredictor` is reachable only from the freehand drawing handler and no predicted event reaches the fling. Re-measured on this tree today: `ChartFlingRegressionTest` injects 3 000 px/s and recovers **2 907 px/s — 3.1 % low**, against the ±10 % A1 allows; a divide by this phone's 2.625 would read back 1 143 | — **a speed, not a still.** The test prints the figure it recovered |
| **A2** `exponentialDecay(0.9f, 150f)` | ❌ **narrowed on the two constants; met on the behaviour they are there for** | The shipped curve is `exponentialDecay(frictionMultiplier = 1.25 / 4.2, absVelocityThreshold = 240f)`. **A2's own test refutes A2's own numbers**: 150 px/s is 1.25 px a frame at 120 Hz, so a fling that runs to 150 ends *below* the 2 px/frame the same line forbids — coasting from 240 down to 150 takes 0.38 s, **45 frames** of exactly the creep the owner filmed. 240 px/s **is** two pixels a frame, which is what makes the tail impossible rather than merely shorter. And `0.9` friction is 3.78/s, which puts a 4 300 px/s release at 1 138 px — one screen — against the 2 900 px the owner measured TradingView covering. The row stays ❌ because it is not the literal ask | — **a curve, not a still.** `ChartFlingTest.it stops rather than creeping a pixel a frame` asserts a tail ≤ 3 frames and measures **0** |
| **A3** 1:1 float pan, fling starts in the hand-off frame | ✅ | The clock is seeded `HANDOFF_NANOS` (one 120 Hz frame) *before* the frame that first ticks it, in `ChartFling` and in its `KineticScroll` twin, because the finger lifts *between* frames and reading the curve at `t = 0` there spends the fastest frame of the gesture standing still | — **the absence of a frame.** `ChartFlingTest.the first frame after the release moves`: hand-off step > 0, one frame's worth (< 60 px at 3 000 px/s, measured ≈ 25), and no speed-up across it |
| **A4** hard flick ≥ 1.5 screens · 800 px/s ≥ 0.4 screens · slow drag never flings · **gutter flick does not fling time** · stop ≤ 1.4 s | ❌ **four of five. The 1.4 s cannot hold beside the distances — arithmetic below** | Re-measured today on the owner's density: hard flick **2 134 px = 1.98 screens** (≥ 1 618 asked), settled in **~2.0 s**. 800 px/s: ≥ 0.4 screens, the bound written as four tenths *less the one bar this measurement cannot see*. Slow drag at 120 px/s: ≤ 1 bar after the lift, which is the settle spring and not momentum. **The gutter clause had no test in run Τ and now does** — `a flick in the price gutter stretches the scale and never throws the time axis` drives a 3 000 px/s swipe down the price ladder at `x = 0.97 · width` and requires the window to stay put | — **gestures, not stills.** Six tests in `ChartFlingRegressionTest`, five in `ChartFlingTest` |
| **A5** 120 Hz kept; rubber-band, focal pinch, auto-scale springs untouched | ✅ | Nothing in this run touches `ChartFrameRate`, `releaseEdge`, `PinchZone` or the settle springs. Phase A's diff is three files and two of them are tests | — **an absence of change.** The commit is the evidence |
| **A6** Pencil between Indicators and the timeframe chip · portrait + landscape · Simple, Trader, Pro · Simple removes nothing | ✅ | `ChartToolbarTest` composes all six and reads the **laid-out tree**: the band's four (indicators, more, undo, fullscreen) all present, the drawing tools reachable exactly once (band pencil, or the permanent column that replaces it on a wide window, never neither and never both), and the pencil's centre between the timeframe chip's and the indicators'. Stated as an order rather than a pixel, because the band lays out right-to-left in Persian and left-to-right in English and «between» is the same sentence in both | `tau-toolbar-{simple,trader,pro}-{portrait,landscape}-fa.png` — six compositions, six frames |
| **A7** `flickVelocity` slow/medium/hard, listed in DEVICE_PROOFS | ✅ **written**, ⏳ **owed a device for its numbers** | `ChartFlingBenchmark.flickVelocitySlow/Medium/Hard`: the same finger travel over 40, 10 and 3 samples — roughly 900, 3 500 and 12 000 px/s at release on a 1 080-wide panel. Three scenarios and not one loop, because a hard release coasts two seconds across thousands of bars and a slow one settles in a few frames; one average describes neither. `docs/qa/DEVICE_PROOFS.md §1b` carries the command | — **a benchmark needs a GPU.** This container has none |

### Why A2's and A4's numbers cannot all be true

A flick on an exponential decay covers `(v − c) / f` and lasts `ln(v / c) / f`. Both are the same
two knobs pulling opposite ways, so the brief's set is a system of inequalities, and on the owner's
own phone — 411 dp at 420 dpi, **1 079 px** wide — it has no solution.

From the 3 000 px/s clause alone, at the cut-off that kills the creep (`c = 240`):

* stop within 1.4 s → `f ≥ ln(12.5) / 1.4` = **1.804**
* travel ≥ 1.5 screens → `f ≤ 2 760 / 1 618` = **1.705**

1.804 > 1.705: **empty**. Raising the cut-off to 300 px/s opens a window at `f ≈ 1.65`, and closes a
different clause — an 800 px/s release then covers 303 px, 0.28 of a screen against the 0.4 asked
for, and a 4 300 px/s one covers 2 424 px against TradingView's measured 2 900. Making the friction
depend on the release speed does not rescue it either: **both** failing constraints are evaluated at
the same 3 000 px/s, so no `f(v)` can satisfy them.

What is shipped keeps the distances — which is what «کند» was — and pays for them in the one
second the brief wanted back. TradingView takes two seconds itself on the owner's own recording.
`BLOCKED.md §A` states the choice the owner owns.

---

## PHASE B — the gaps the 4.92 audit found

| Item | State | Evidence | Frame |
|---|---|---|---|
| **B1** Monogram fallback everywhere; a symbol is never hidden for a missing logo | ✅ **already shipped, 4.91.0** | Run Φ replaced `SymbolArtwork.covers` with the monogram: `CoineProAssetLogo` draws the disc under every logo — «the monogram is not a placeholder that gets swapped for a spinner: it *is* the logo until the picture arrives» — and `CoineProColors.monogramHue` is the stable hash of the ticker, with saturation and lightness fixed so every disc in a list carries the same weight. `SymbolUniverse`: «a market with no artwork is drawn as a monogram and **is still listed**». Tests: `SymbolListingTest`, `MonogramTintTest` | Run Φ's own set. Not re-taken: this run changed no code behind B1, and a fresh frame of unchanged pixels is evidence of nothing |
| **B2** `entitlements.all` as a real switch, **server-fed**, local default `true` | ✅ | The switch and both its states were already there (`Entitlements`, `EntitlementGateTest`); what was missing is the word **server-fed**, which the old KDoc admitted — «server-fed in intent and local in fact». Now: `EntitlementsGateway` GETs one field on the crypto deployment, `EntitlementStore` keeps it, `EntitlementStartUp` applies the stored value at process start and refreshes for the next launch. **Applied at start and never mid-session**, deliberately: a wall that appears while a reader is standing in the doorway reads as the app breaking. Every failure — no route, no signal, a body that will not parse — answers null, and null keeps the app open | — **a flag, so not a still.** `EntitlementGateTest` now has seven cases, two of them new: a silence leaves the app open, and a served refusal reaches every derived answer |
| **B3** `forexTrading = false` airtight | ❌ **narrowed on one key of the four named** | The gap this found and closed: **`copy-trade` survived the flag**. It is keyed to the forex *platform*, which is a different question from whether this build trades there at all, so a reader on CoinePro-FX still had the row. Both call sites now drop it with `connections`. `ForexSurfaceReachabilityTest` reads the shell's own hiding rule out of `CoineProApp.kt` rather than retyping the ids, drives both states, and asserts the hidden set stays inside the trading block. **What is not done:** `membership_open_ourbit` is deliberately kept — the membership journey is the **crypto** venue's sub-account check, and `FeatureFlags.forexTrading`'s own note keeps «the account verification the crypto venue requires» either way. Removing it would take crypto sign-up out of a crypto app. The row stays ❌ because the line names that key | — **an absence, so not a still.** Four cases in `ForexSurfaceReachabilityTest`, plus `TradePartnersFlagTest` for the chart's partner row |
| **B4** Five tabs; everything of 4.92 still at depth ≤ 2 | ✅ **already shipped, 4.92.0** | `AppDestination` is دیده‌بان · چارت · رَصد · انجمن · منو, and `NavigationDepthTest` holds every declared route to the menu, the bar, or a named exemption | `upsilon-markets-tabs-phone-fa.png` and the rest of run Υ's set, unchanged: this run did not touch the bar or the tabs, so re-taking them would be a picture of the same pixels |
| **B5** Pulse figures only; the explanation behind ⓘ | ✅ | The row printed figures already; what it did not have is the **affordance** — a tappable cell that looks like a printed one is a door nobody opens. A 12 dp ⓘ now sits beside each cell's name, and the *target* stays the whole cell, because a 12 dp mark is below every minimum a thumb is measured against. The four `_why` sentences are unchanged, word for word | `t2-b5-pulse-row-phone-fa-dark.png`, `t2-b5-pulse-sheet-phone-fa-dark.png`. The test also pins what the picture cannot: that none of the four explanations is drawn *on* the row |
| **B6** Offline is a first-class state | ❌ **not done in this session** | `CoineProOfflineBar` and the caches are there; the «ذخیره‌شده · %s پیش» banner, the queued-alert badge and the flush-on-reconnect are not. `RESUME.md` carries it | — |
| **B7** Two Glance widgets | ❌ **not done in this session** | One widget exists (`MarketsWidget`, RemoteViews, with its configuration activity and a WorkManager refresh). The single-symbol widget and the Glance rewrite are not started. `RESUME.md` carries it | — |
| **B8** Picture-in-Picture | ❌ **not done in this session** | Nothing exists. `RESUME.md` carries it | — |
| **B9** Alert sound and repeat | ❌ **partly there, not finished in this session** | `AlertRepeat` (once / daily / always), `AlertChannel` and a per-alert `soundLevel` exist. The bundled sound *choice*, «repeat every N minutes until acknowledged» and the Do-Not-Disturb rule are not. `RESUME.md` carries it | — |
| **B10** One sentence about what this app is not | ✅ | «این اپ معامله انجام نمی‌دهد و کارمزدی دریافت نمی‌کند.» / "This app does not trade and takes no commission." — drawn directly **under the identity block** on the menu, not in the footer: the foot of a nine-section directory is a place a reader arrives at by accident, and «prominent» and «at the bottom» are not the same instruction. Same sentence in `docs/product/STORE_LISTING.md`, and the test compares the two strings so they cannot drift | `t2-b10-menu-phone-fa-dark.png`, `t2-b10-menu-phone-en-light.png` |

## PHASE C — six features no competitor has

| Item | State | Evidence | Frame |
|---|---|---|---|
| **C5** Compare on the chart | ⏳ **mostly already shipped; audited, not extended** | `Comparison.kt` in `:chart-core` already normalises to PERCENT (rebased to the **left edge of the viewport**, so the answer re-computes as the reader pans), INDEXED_100, RATIO and ABSOLUTE; the hub's «مقایسه با نماد دیگر» tile adds from the watchlist, and the legend carries a removable row per series. What C5 asks beyond that — the per-series percentage across the visible range printed **on the legend**, and a one-tap swap rather than a four-chip row — is not done | Run E's comparison frames, unchanged — this row is an audit, not a change |
| **C1** «Why this move?» | ❌ **not done in this session** | Nothing exists. `RESUME.md` carries it | — |
| **C2** Alerts on drawn objects | ⏳ **the engine exists; the run did not audit the gaps** | `AlertTrigger.DrawingTouch`, `AlertDrawingOption` and `AlertDrawings.kt` are shipped, and the evaluator resolves a trend line's price again at every sample. What C2 adds — the delete-asks-about-its-alerts flow, the thumbnail in the alert list — is not audited or built | — |
| **C3** Duel with the past | ❌ **not done in this session** | — | — |
| **C4** My week | ❌ **not done in this session** | — | — |
| **C6** Rasad's morning brief as a notification | ❌ **not done in this session** | — | — |

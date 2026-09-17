# RUN ΤΦΥ — checklist

Three phases. Every line carries a state, the evidence behind it, and a frame — or, where a still
cannot carry the claim, the test that can and why a picture would be a lie.

States: ✅ done · ❌ not done, or narrowed (the row says exactly how) · ⏳ owed to the owner's device.

---

## PHASE Τ — the chart scroll

Shipped as **4.90.0** before phase Φ began, because the owner has to put a thumb on it before
anything else in this run is worth doing.

| Item | State | Evidence | Frame |
|---|---|---|---|
| **T1** Release velocity is raw, in px/s, no density anywhere | ✅ | The tracker is fed from `awaitPointerEvent(Final)` in `CoineProChart.kt:1785`, every historical sample included, and `tracker.calculateVelocity().x` goes **straight** into `kinetic.start` — one call, nothing between them, and `ChartFling` has no `Density` in scope to divide by. `ChartStrokePredictor` is fed only by the freehand drawing handler and reaches no fling. The audit is a grep and the grep is in the report; what carries the row is the **measurement**: `ChartFlingRegressionTest.the speed the finger left at is the speed that reaches the curve` injects a 3 000 px/s flick, reads the travel back through the closed-form curve, and recovers **2 907 px/s — 3.1 % low**, against the ±10 % the brief allows. A divide by this phone's 2.625 would read back 1 143 | — **a speed, so not a still.** The test prints the number it recovered |
| **T2** `exponentialDecay`, cut-off kills the creep | ❌ **narrowed on the constants, met on the behaviour** | The curve is `exponentialDecay(frictionMultiplier = 1.25 / 4.2, absVelocityThreshold = 240)`, **not** the `0.9f / 150f` the line names, and the line's own test is why. `150 px/s` is 1.25 px a frame at 120 Hz, so a fling ending there ends *below* the 2 px/frame the same item forbids: from 240 px/s down to 150 takes 0.38 s, which is **45 frames** of exactly the creep the owner filmed. 240 px/s **is** two pixels a frame, which is what makes the tail impossible. And `0.9` friction would undo run Τ's distance: it puts a 4 300 px/s release at 1 130 px, one screen, against TradingView's 2 900. Both numbers are the owner's earlier measurement, reasoned about in `KineticScroll.EXPONENTIAL_FRICTION`. The row stays ❌ because it is not what the line says | — **a curve, so not a still.** `ChartFlingTest.it stops rather than creeping a pixel a frame` asserts the tail at ≤ 3 frames and gets **0** |
| **T3** Pan is 1:1 in float, and the first fling frame moves | ✅ **a real defect, found by writing the test** | The finger lifts *between* two frames, so on the first frame the fling loop gets, the release is already about one frame old. Both curves read themselves at `t = 0` on that frame and returned **zero** — the picture tracked the finger at full speed, stood still for 8 ms, then started again, at the exact moment the chart is moving fastest, which is the one frame of stillness a thumb can feel. The clock is seeded `HANDOFF_NANOS` (one frame at 120 Hz) *before* the frame that first ticks it, in `ChartFling` and in its `KineticScroll` twin. A flag rather than a negative sentinel, because a first frame at or near zero seeds a negative start and a sentinel would re-seed every frame — a fling that never leaves its first step, which is what the first run of this change actually did | — **one frame, and it is the absence of a frame.** `ChartFlingTest.the first frame after the release moves` asserts the hand-off step is > 0, is one frame's worth (< 60 px at 3 000 px/s, measured ~25), and that the fling does not speed up across it |
| **T4** Injected pointers: hard flick ≥ 1.5 screens, 800 px/s ≥ 0.4 screens, slow drag never flings, stop within 1.4 s | ❌ **three of four; the 1.4 s is arithmetically impossible beside the other two** | `ChartFlingRegressionTest` drives real events through the real chart on the owner's density. Hard flick: **2 134 px = 1.98 screens** (≥ 1 618 asked). 800 px/s: **≥ 0.4 screens**, the bound written as the brief's four tenths less the one bar this measurement cannot see. Slow drag at 120 px/s: **≤ 1 bar** after the lift, which is the settle spring, not momentum. Stop: **~2.0 s**, not 1.4. That last one cannot be bought — see «What Τ does not claim» below for the arithmetic — and 2.0 s is what TradingView itself takes on the owner's own recording | — **gestures, so not stills.** Five tests in `ChartFlingRegressionTest`, four in `ChartFlingTest`, one in `ChartPixelsTest` |
| **T5** 120 Hz rendering, rubber-band, focal pinch and auto-scale springs untouched | ✅ | Nothing in this phase touched `setFrameRate`, `releaseEdge`, `PinchZone` or the settle springs. The diff for Τ is four files: the two fling clocks, one `testTag` on the timeframe chip, and tests | — **an absence of change.** `git show` for 4.90.0 is the evidence |
| **T6** The pencil is on the band, between Indicators and the timeframe chip, in both orientations and all three modes | ✅ | It already was, since run Τ's first pass — what this run added is the **order**, which nothing asserted. `ChartToolbarTest` now reads the laid-out tree and requires the pencil's centre to lie between the timeframe chip's and the indicators'. Stated as an order rather than a pixel because the band lays out right-to-left in Persian and left-to-right in English and «between» is the same sentence in both. Where a window is wide enough for the permanent tool column the band drops its pencil, and there the existing rule applies instead: exactly one of pencil or column, never neither | `tau-toolbar-simple-portrait-fa.png`, `tau-toolbar-trader-portrait-fa.png`, `tau-toolbar-pro-portrait-fa.png`, and the same three in landscape. Six compositions, six frames |
| **T7** `flickVelocity` slow / medium / hard in the benchmark, listed in DEVICE_PROOFS | ✅ **written**, ⏳ **owed to a device for its numbers** | `ChartFlingBenchmark.flickVelocitySlow/Medium/Hard`: the same finger travel over 40, 10 and 3 samples — roughly 900, 3 500 and 12 000 px/s at release on a 1 080-wide panel. Three scenarios rather than one loop, because a hard release coasts two seconds across thousands of bars and a slow one settles in a few frames; one average describes neither. `docs/qa/DEVICE_PROOFS.md §1b` carries the command and the threshold | — **a benchmark needs a GPU.** This container has none |

### What Τ does not claim

**The brief's four fling numbers cannot all be true at once, and this is the arithmetic.** On an
exponential decay a flick covers `(v − cut-off) / f` and lasts `ln(v / cut-off) / f`. Take the
owner's own phone, 1 079 px wide:

* «3 000 px/s covers ≥ 1.5 screens» wants `f ≤ (3000 − c) / 1618`.
* «800 px/s covers ≥ 0.4 screens» wants `f ≤ (800 − c) / 432` — and for any positive cut-off this
  is the *tighter* of the two, so it is the one that binds.
* «3 000 px/s stops within 1.4 s» wants `f ≥ ln(3000 / c) / 1.4`.

Put the binding pair together and you need `800 − c ≥ 308.6 × ln(3000 / c)`. At `c = 240` that is
560 ≥ 779. At 400 it is 400 ≥ 622. At 600 it is 200 ≥ 497. **There is no cut-off that satisfies it**,
and the gap widens as the cut-off falls, so `150` — the number T2 names — is further from a solution
than what shipped. A velocity-dependent drag term was tried on paper and moves the numbers the wrong
way: total travel then grows only logarithmically in the release speed, which starves the hard flick
to feed the soft one. So the distances were kept and the duration was not, on the grounds that
distance is what the owner filmed and 2.0 s is what the app being matched takes.

**Nothing in Τ was felt.** A container replays an event stream faithfully and drops no frames; what
it cannot say is whether the chart now moves like the one in the other app. That is the owner's
three flicks of increasing speed on BTCUSDT H1 — §3 and §1b of `docs/qa/DEVICE_PROOFS.md` — and it
is the only thing that closes this phase. RUN Τ2 item 3 stays ❌ for the same reason.

**T3 is the only behaviour change in the phase.** T1, T5 and T6 were already true and what this run
added to them is a measurement. Saying so is the point of the row: a checklist that marks a thing ✅
without saying whether the run *did* it is a checklist that cannot be read twice.

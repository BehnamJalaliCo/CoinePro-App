# RUN Ξ — checklist

States: ✅ done · ❌ not done, or narrowed (the row says exactly how) · ⏳ owed to the owner's device.

The brief says **ship after Phase Ξ2 and stop** until the owner's recording confirms the chart
follows the finger. Phases Ξ3, Ξ4 and Ξ5 are therefore not started, and that is the instruction
rather than an omission.

---

## PHASE Ξ1 — instrument first

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 1 | Debug overlay: events/sec, last 60 dx, state-machine branch, `requestDisallowInterceptTouchEvent`, `scrollPx` per frame — **and a screen recording of it** | ❌ **not built; the substitute is stronger for the fix and weaker for the confidence** | There is no internal flavour and no device here: the owner installs **signed release** APKs from CI, so a debug-only overlay is one they could never open. What stands in its place is `ChartDragTraceTest` — a stepwise drag driven through the real chart, which **failed on `main`**, names the frame-by-frame shape, and now passes. `BLOCKED.md §Ξ1` carries what the overlay would still be worth and what the owner must provide for it | — **a recording, and this container has no screen.** The trace's numbers are printed by the test |
| 2 | Every node above the plot that can consume a pointer, with file:line | ✅ | `trace.md` — fourteen handlers on the chart's own modifier chain with their pass and whether they consume, then the page and the shell. **Nothing above the plot consumes**: no pager, no nested scroll, no sheet, no swipe-to-dismiss, and `requestDisallowInterceptTouchEvent` appears nowhere in the repository | — **a table, and it is in `trace.md`** |
| 3 | State plainly which one is winning the gesture | ✅ | `CoineProChart.kt:2242` (time-axis scale) and `CoineProChart.kt:2069` (drawing edit). **Each alone is sufficient**, proved by bisection: both installed → `[0,0,0,0,0,0,0,0,0,0]`; only the time axis dropped → still zero; both dropped → `[0,0,0,0,1,2,3,4,5,6]`. The condition that selected the wrong branch is named: both call a Compose drag detector that consumes at the touch slop, and both put their «was this mine?» guard *inside the callback*, which runs after the consumption | — **a bisection table, in `trace.md`** |

---

## PHASE Ξ2 — fix the input path

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 4 | The plot owns horizontal gestures unconditionally; `awaitEachGesture` + `awaitFirstDown(requireUnconsumed = false)`; consume for the rest of the gesture | ✅ **inverted, and the row says how** | The defect was never that the pan failed to claim — it was that **four siblings claimed first and then declined**. So `awaitOwnedDrag` (`ChartOwnedDrag.kt`) is exactly the mechanism item 4 asks for, applied to the four zone handlers rather than to the pan: `awaitEachGesture` + `awaitFirstDown(requireUnconsumed = false)`, a **`claims(down.position)`** question asked once at the down, and `consume()` on every change from the slop onwards **only** once the answer was yes. A gesture a zone refuses is dropped without a single `consume()`, so the pan sees it exactly as if that handler were not in the tree — which is what «the plot owns it unconditionally» means in practice. Rewriting the pan itself would have meant replacing `detectTransformGestures`, losing working multi-touch, and touching the one thing item 9 forbids | — **an absence of consumption.** `ChartDragTraceTest`, four cases |
| 5 | Delta applied in the same frame; no accumulation, no threshold beyond slop, no commit-on-release; **test: 10 moves × 12 px changes the window before the up** | ✅ | `ChartDragTraceTest.the window moves while the finger is still down` and `…it keeps moving, move after move…`. Measured: `[0, 0, 0, 0, 1, 2, 3, 4, 5, 6]` — four moves spent crossing the platform slop, then **one bar per move, every move, to the lift**. Before the fix the same drag read `[0,0,0,0,0,0,0,0,0,0]`. The pan's own arithmetic was never changed: it already applied whole bars plus a sub-bar `panShift` in the same frame | — **frames of a drag, so not a still.** The test prints them |
| 6 | Slop is the platform value, applied once, and the distance beyond it is not discarded | ✅ | `awaitOwnedDrag` reports `overSlop` as the first delta rather than dropping it, and the slop helpers are Compose's own (`awaitTouchSlopOrCancellation` and its axis variants), so the value is the platform's. `ChartDragTraceTest.the travel is the finger's, less one slop`: **6 bars over 120 px** of finger, against ~21 px of slop and ~19 px a bar | — **arithmetic.** The test prints the figure |
| 7 | Crosshair never reachable by a plain drag | ✅ | `ChartDragTraceTest.a fast horizontal drag never lands on the crosshair`: **0** crosshair emissions across the whole stepwise drag. Tracking is set in two places only, `CoineProChart.kt:1996` and `:2408`, and both are behind a long press or the dedicated tool | — **an absence, so not a still.** The test counts it |
| 8 | Re-measure with the overlay: slow drag ≥ 100 non-zero frames over a second; flick ≥ 60 frames of decay | ⏳ **owed to the owner's device** | What can be measured here is measured: the drag is continuous move-for-move, and `ChartFlingRegressionTest` reports the flick delivered over **100 moving frames** of 500, biggest step 4 bars of 164. Frames-per-second of a real finger is a property of a real digitiser | — **recordings.** `BLOCKED.md §Ξ8` has what to record |
| 9 | Only then revisit the decay constants; friction and threshold untouched in Ξ1–Ξ8 | ✅ | `FLING_FRICTION_MULTIPLIER` and `KineticScroll.MIN_VELOCITY` are byte-identical to 4.96.0. `git diff` for this run touches no file under `chart/core/` | — **an absence of change.** The commit is the evidence |

---

## PHASE Ξ3 — the welcome slides · PHASE Ξ4 — visual craft on the chart · PHASE Ξ5 — finish what is half-done

| # | Item | State |
|---|---|---|
| 10–14 | The five illustrations, typography, buttons, transitions, the sixth brand frame | ❌ **not started — the brief holds them behind the owner's recording** |
| 15–18 | Chart gradient, price glow, pane hairline, crosshair shadow, marker scale-in, symbol draw-in, pressed states, the skeleton | ❌ **not started — same** |
| 19–22 | The broker surfaces, the symbol universe, the redemption failure states, the empty-list audit | ❌ **not started — same.** Item 19 is a widening of what RUN Τ2 B3 closed for `connections` and `copy-trade`; `copy_account_*` and `copy_balance` are not yet covered |

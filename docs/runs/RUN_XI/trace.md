# RUN Ξ — trace

Written before a single line of gesture-fixing code, per the brief.

---

## What the owner filmed, and what it turned out to be

Four 120 Hz recordings of 4.96.0, per-frame horizontal displacement of the candles: bursts of 15–19
frames shaped `[-90, 0, -90, 0, -74, -57, -22, … 2, 2, 2]`, then **seconds of exactly zero while the
finger is on the glass**. Rendering healthy at 120 fps, 0.2–0.9 % dropped.

Every part of that shape is now accounted for:

| What the recording shows | What it is |
|---|---|
| One or two large steps at the start | `detectTransformGestures` spending the pan it accumulated while crossing its own touch slop |
| Then nothing, for the rest of the gesture | The pan **cancelled** — a sibling handler consumed the change, and `detectTransformGestures` ends its gesture on the first consumed event and does not restart until the next `DOWN` |
| Seconds of zero with the finger still down | The same. One finger down is one gesture; once cancelled there is nothing left to restart it |
| The `2, 2, 2` tail | The fling, from the observer that reads on the **Final** pass and consumes nothing — so it still gets a velocity. `KineticScroll.MIN_VELOCITY` is 240 px/s, which is exactly 2 px per frame at 120 Hz |
| Smooth in a WebView | No sibling Compose handlers there. Nothing to consume the events |

**RUN Τ2's numbers were measuring the fling, not the drag.** Every gesture test in this repository
drove `swipeWithVelocity`, which injects down, moves and up **between two frames** — the finger is
never on the glass for a frame, so the drag cannot be observed. `ChartFlingRegressionTest` even
printed the defect and excused it: its interruption case reports `drag 1` and `drag 0`, and the
comment beside it calls that an artefact of the harness. It was not an artefact.

---

## The instrument

`app/src/test/kotlin/com/coinepro/app/ChartDragTraceTest.kt` — a **stepwise** gesture: `down`, then
ten separate `moveTo` calls of twelve pixels each, each its own `performTouchInput` invocation and
therefore its own pointer event, with the window read **before** the `up`.

That is the shape of a real drag, and it is the only shape that can fail the way the recordings fail.
It reproduces the defect deterministically in CI, on the owner's own density (411 dp at 420 dpi):

```
drag frames (bars from rest, one per move): [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
moves that changed the window: 0 of 9
travelled 0 bars over 120 px of finger
```

A hundred and twenty pixels of finger. Zero pixels of chart. Nothing about a decay curve is involved.

---

## Every node above and around the plot that can consume a pointer

The chart's own handlers are all on **one** modifier chain on the `Canvas` in
`chart/ui/…/CoineProChart.kt`. Modifiers apply outside-in, so the **last** block in the chain is the
innermost node — and on the **Main** pass Compose dispatches innermost first. The pan is the *first*
block written, which makes it the **last** to be offered every event.

| file:line | node | pass | consumes? |
|---|---|---|---|
| `CoineProChart.kt:1556` | `detectTransformGestures` — **the pan** | Main, last | yes, once it owns the gesture |
| `CoineProChart.kt:1632` | pinch observer | Final | no |
| `CoineProChart.kt:1765` | fling observer → `KineticScroll` | Final | no |
| `CoineProChart.kt:1843` | magnet latch | Final | no |
| `CoineProChart.kt:1893` | price-gutter alert affordance | Main | guarded at the **block** level |
| `CoineProChart.kt:1926` | `detectTapGestures` — taps, eraser, event glyphs | Main | the `DOWN` only; harmless |
| `CoineProChart.kt:2024` | freehand stroke | Main | guarded at the **block** level |
| **`CoineProChart.kt:2069`** | **`detectDragGestures` — drawing edit** | **Main** | **yes, unconditionally** |
| `CoineProChart.kt:2175` | pane divider | Main | guarded at the **block** level |
| `CoineProChart.kt:2209` | `detectVerticalDragGestures` — price gutter | Main | yes, unconditionally (vertical slop) |
| **`CoineProChart.kt:2242`** | **`detectHorizontalDragGestures` — time axis** | **Main** | **yes, unconditionally** |
| `CoineProChart.kt:2288` | `detectDragGesturesAfterLongPress` | Main | after a long press only |
| `CoineProChart.kt:2392` | press-moved observer | Final | no |
| `CoineProChart.kt:2415` | `detectTapGestures` — long press | Main | the `DOWN` only |

Above the chart, in the page and the shell:

| file:line | node | verdict |
|---|---|---|
| `ChartScreen.kt:1846` | `Modifier.verticalScroll` on the page column, on a phone only | **not the culprit.** `scrollable` handles on Main at the parent, so a child wins the tie; and the chart's own siblings consume long before the parent is offered anything |
| `ChartScreen.kt:3082` | `PredictiveBackHandler` | back gesture only, edge-confined by the system |
| `ChartScreen.kt:4538` | `draggable` — the replay scrubber | a different composable, not an ancestor of the plot |
| `ChartScreen.kt:1170` | the `CoineProChart` call site | `graphicsLayer` + `testTag` only; consumes nothing |
| `CoineProApp.kt`, `AppChrome.kt` | the shell | **no** `pointerInput`, `nestedScroll`, `scrollable`, pager or sheet wraps the chart route |

There is no `HorizontalPager`, `NestedScrollConnection`, `BottomSheetScaffold` or `SwipeToDismiss`
anywhere above the plot, and **nothing calls `requestDisallowInterceptTouchEvent`** anywhere in the
repository. No ancestor is stealing anything. The chart is eating its own events.

---

## The culprit, named

**`CoineProChart.kt:2242` — the time-axis scale drag — and `CoineProChart.kt:2069` — the
drawing-edit drag. Each one alone is sufficient to kill the pan.**

Both have the same defect, and it is one line in the wrong place. Each installs a Compose drag
detector **unconditionally**, then decides inside the callback whether the gesture was for it:

```kotlin
// CoineProChart.kt:2260
detectHorizontalDragGestures(
    onDragStart = { position -> onTimeAxis = axisTop > 0f && position.y >= axisTop },
) { change, dragAmount ->
    if (!onTimeAxis) return@detectHorizontalDragGestures   // ← line 2269
    change.consume()
    …
}
```

`detectHorizontalDragGestures` consumes **before that guard ever runs**: its slop lambda calls
`change.consume()` on the event that crosses the horizontal slop, and its drag loop calls
`it.consume()` on every event after. `detectDragGestures` at 2069 does the same in any direction.
So a drag that starts in the middle of the plot — nowhere near the dates — is consumed by the
time-axis handler, and `detectTransformGestures` at 1556, which cancels its whole gesture on the
first consumed event, is finished for as long as that finger is down.

The comment at `CoineProChart.kt:2255` states the intent and is **false in the one direction that
matters**: «the pan gesture above refuses any drag that started there, so the two cannot both claim
the same finger.» The pan does refuse a drag that started on the axis. This handler does not refuse
a drag that started on the plot. The guard is real; it is simply three lines too late.

The claim at `CoineProChart.kt:2078` is false in the same way: «Only runs with nothing armed and
something selected, so it cannot steal the drag that places a freehand stroke or the one that pans.»
Those conditions are checked in `onDragStart` and `onDrag`, after `detectDragGestures` has consumed.

### The bisection

One constant per suspect, `true` meaning «return from the block before the detector is installed»:

| `2242` time axis | `2069` drawing edit | `ChartDragTraceTest` |
|---|---|---|
| installed | installed | **fails** — `[0, 0, 0, 0, 0, 0, 0, 0, 0, 0]` |
| dropped | installed | **fails** |
| dropped | dropped | **passes** — `[0, 0, 0, 0, 1, 2, 3, 4, 5, 6]` |

The green row is what following a finger looks like: four moves spent crossing the platform's touch
slop, then one bar per twelve-pixel move, continuously, to the lift. The bisection constants were
reverted the moment this table was written; the tree carries no trace of them.

`CoineProChart.kt:2209` — the price-gutter vertical drag — has the identical defect and is left in
the table above rather than claimed as a third culprit: its slop is vertical, so it does not claim a
horizontal drag, and this trace has no evidence that it bites. It is fixed with the other two
because the shape is the same, not because it was measured.

---

## What this trace does not have

**The overlay and its recording.** Item 1 asks for a debug overlay drawing live event counts and the
state machine's branch, and a screen recording of one drag through it. This container has no device
and no emulator with a GPU, so the recording cannot be taken here; `BLOCKED.md §Ξ1` carries it with
the command. What stands in its place is the CI reproduction above, which is stronger in the one way
that matters for a fix — it fails on `main` today, it is deterministic, and it will not go quiet
again — and weaker in the one way that matters for confidence: it is Robolectric, and only the
owner's phone can confirm that the same fix moves the same pixels.

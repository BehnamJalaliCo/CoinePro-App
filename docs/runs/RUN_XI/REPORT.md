# RUN Ξ — report

## The one-sentence version

Four handlers on the chart's own canvas were installing Compose drag detectors unconditionally and
deciding afterwards whether they wanted the gesture — and a Compose drag detector consumes at the
touch slop, before any of those decisions run. Two of them alone were enough to cancel the pan for
as long as a finger was down.

## What was wrong with the previous run's evidence, exactly

RUN Τ2 measured 2 907 px/s arriving at the curve and 2 134 px travelled, and both numbers are
correct. They are also about the **fling** — the momentum after the lift — because every gesture
test in this repository drove `swipeWithVelocity`, which injects down, moves and up between two
frames. In that harness the finger is never on the glass for a frame, so no test could see whether
the chart followed it.

The defect was visible in that run's own output and was written off. `ChartFlingRegressionTest`'s
interruption case prints `drag 1` and `drag 0` — the drag moved one bar, or none — and the comment
beside it attributes that to «a harness which injects a whole swipe between two frames». It was not
the harness.

## The instrument

`ChartDragTraceTest` drives a **stepwise** gesture: `down`, then ten separate `moveTo` calls of
twelve pixels, each its own `performTouchInput` invocation and therefore its own pointer event, with
the window read **before** the `up`. On `main` at 4.96.0, on the owner's density:

```
drag frames (bars from rest, one per move): [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
```

A hundred and twenty pixels of finger, zero pixels of chart. Deterministic, in CI, with no device.

## The culprit

`trace.md` carries the full table and the bisection. In short: the chart's fourteen pointer handlers
sit on one modifier chain, and Compose dispatches the **Main** pass innermost-first — so the pan,
written first, is offered every event **last**. Four handlers ahead of it were shaped like this:

```kotlin
detectHorizontalDragGestures(
    onDragStart = { position -> onTimeAxis = … },
) { change, dragAmount ->
    if (!onTimeAxis) return@detectHorizontalDragGestures   // ← too late
    change.consume()
```

`detectHorizontalDragGestures` calls `change.consume()` in its slop lambda and `it.consume()` in its
drag loop. The guard on the next line runs after both. `detectTransformGestures` — the pan — ends
its gesture on the first consumed event and does not start another until the next `DOWN`, which is
precisely «seconds of exactly zero displacement while the finger is on the glass».

Bisection, one constant per suspect:

| `2242` time axis | `2069` drawing edit | `ChartDragTraceTest` |
|---|---|---|
| installed | installed | fails — `[0,0,0,0,0,0,0,0,0,0]` |
| dropped | installed | fails |
| dropped | dropped | passes — `[0,0,0,0,1,2,3,4,5,6]` |

Both, independently sufficient. The constants were reverted before anything was fixed.

## The fix

`ChartOwnedDrag.kt` — `awaitOwnedDrag(axis, claims, …)`. It is item 4's mechanism, applied to the
zone handlers rather than to the pan:

* `awaitEachGesture` + `awaitFirstDown(requireUnconsumed = false)`;
* **`claims(down.position)` asked once, at the down** — and a gesture it refuses is dropped without
  a single `consume()`, because `awaitEachGesture` drains the remaining pointers on the final pass;
* once claimed, `consume()` from the slop onwards, which gives the zone the same protection in the
  other direction.

Four handlers converted: the time-axis scale (`2242`), the price-gutter scale (`2209`), the pane
divider (`2175`) and the drawing edit (`2069`).

Two deliberate differences from what they did before:

**The zone is asked about the down, not the drag start.** Compose reports `onDragStart` at the
*post-slop* position, so a finger that landed a few pixels off the price gutter and slid into it used
to be claimed by the gutter. Where the finger landed is the answer a reader would give.

**A selection is not a claim on the whole canvas.** The drawing-edit handler now claims only when
the down lands on a handle or on the selected drawing's own body (`DrawingHitTest.at`). Before, any
drag anywhere on the plot with a drawing selected went to it — and was then declined inside the
callback, after the damage.

## After

```
drag frames (bars from rest, one per move): [0, 0, 0, 0, 1, 2, 3, 4, 5, 6]
moves that changed the window: 6 of 9
travelled 6 bars over 120 px of finger
crosshair emissions during a plain drag: 0
```

Four moves spent crossing the platform's touch slop, then one bar per twelve-pixel move, every move,
to the lift. The whole unit suite is green, goldens included.

## What was not touched

`FLING_FRICTION_MULTIPLIER` and `KineticScroll.MIN_VELOCITY` are byte-identical to 4.96.0, per item
9. `detectTransformGestures` — the pan and its multi-touch — is unchanged. No file under
`chart/core/` is in this run's diff. Nothing about a decay curve was ever the problem, and the open
question in `RUN_T2/BLOCKED.md §A` about distance-versus-time should not be answered until the owner
has felt a chart that follows the finger.

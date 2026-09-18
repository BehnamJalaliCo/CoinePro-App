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

---

# Phases Ξ3, Ξ4, Ξ5 — 4.98.0

The brief put one gate in front of these three phases: «ship a build after Phase Ξ2 and stop … nothing
below starts until that recording confirms the chart follows the finger». The owner installed 4.97.0,
recorded it, and replied **«حرکت چارت درست شد»**. Item 8 closes on that, and everything below is what
the gate was holding.

---

## Phase Ξ3 — the welcome slides

### What was wrong with the first version, in three sentences

Each slide was **one primitive** — a sine wave, a grid of squares, three circles — on a flat ground,
and a single stroked path on an empty surface reads as a placeholder however carefully it is drawn.
Each one **looped forever**, which is wallpaper: the eye tunes it out inside a second and it costs a
frame every sixteen milliseconds for the rest of the slide. And the type was a **card's heading**,
20/28 over 15/22, on the first screen anybody sees.

### What replaced it

Five **scenes**, each in three layers — a soft radial fall-off in the brand gold behind, a mid-layer
carrying the subject, a foreground element saying what the subject is *for*. The explain card that
arrives at the end of the candle series; the plus in the sixteenth tool cell, which is NamaScript;
the play head riding the replay sweep; the keyhole in the lock that has just opened.

Every animation **runs once and stops**. That is not a promise in a comment, it is where the state
lives: `AnimatedContent` constructs a new `SlideBody` per frame, `SlideBody` owns the `Animatable`,
and `LaunchedEffect(Unit)` inside it fires exactly once. The outgoing slide keeps the finished
picture it had while it slides away. Every `rememberInfiniteTransition` is gone from the file — the
reduced-motion gate's count of files using continuous motion fell from four to three.

Three decisions worth inheriting:

* **The screen is near-black in both themes.** `LaunchSplash` is black on white in both, for a
  reason that applies here twice over: the welcome runs *before* the reader is asked what theme they
  want — the starter questions come after it — so there is no answer of theirs to follow, and a
  brand sequence that changes colour with a setting nobody has set yet is a sequence that flashes.
  The whole screen is wrapped in the dark palette, so the buttons, the dots and the small print
  inherit the ground rather than arguing with it.
* **The candles are written down, not generated.** A sine wave is the one shape no market ever
  prints, and a reader who looks at charts all day recognises it instantly as a drawing of a chart
  rather than a chart. `CANDLE_OPENS` and its three siblings are a plausible hour: two up bars, a
  pull-back, a range, then the push the last candle is in the middle of.
* **The clock stops at the *down*, not at the drag start.** The first version paused auto-advance in
  `onDragStart`, so a thumb resting still on a slide did not stop it — which is the exact case where
  a reader is reading. The gesture is now an `awaitEachGesture` that sets `held` on the first down
  and consumes nothing until the touch slop, so the buttons under the slides still take their taps.
  After the lift there are three seconds of stillness before the dwell restarts: somebody who just
  touched the screen is the last person whose page should move under them.

### The one number that is not the brief's

Item 12 asks for a pressed scale of 0.98. `CoineProPress.CTA` is **0.955**, and it stays. It is the
design system's own depth for a full-width action, it applies at a hundred call sites, and changing
the whole product's press to suit one screen is the wrong direction of travel. What *was* missing is
the half the eye actually reads, and that is new and app-wide: the primary button's fill now animates
to eight per cent towards black under a thumb — the brief's number, applied where it belongs.

The 56 dp height is applied on the welcome screen **only**. `ButtonContent`'s own note explains why
the shared button is 46 dp — at 56 it was taller than a list row and the loudest object on every one
of the hundred-odd screens that has one — and that argument is right everywhere except the screen
where the button *is* the screen.

### The gradient allow-list

`check-motion-policy.sh` bans gradients outside a short list, because a gradient on a card or a
header is what makes an interface look like a skin rather than a system. `WelcomeSlides.kt` joins the
list, and the argument is written into the script rather than implied: the radial fall-off behind
each scene is a **layer of the drawing**, the same case as the brand mark's metal and the chart's own
area fill; and the 2 % vertical lift on the stage is not a wash behind a surface because this screen
is not a surface in the system's sense — it is the brand sequence, before any of the app's chrome
exists, with no card, header or button fill touched by it.

---

## Phase Ξ4 — the chart

**The gradient is added, not mixed.** `lerp(stage, White, 0.02)` on a `0xFF0B0E11` ground is half a
level — invisible. Two per cent of full scale is five levels, which is what «two per cent lighter at
the top» means to an eye looking at the top of the screen. It is drawn exactly where the flat fill
already was, under the same `decoration.colours != null` condition, so a chart embedded in a card or
a list row — which has never filled its ground — still does not.

**The volume band had no lid.** The inline volume shares the price plot's ground, so the only thing
saying where one ended and the other began was the height of the bars; on a quiet stretch, where
every bar is a stub, nothing said it at all. One registered hairline at the top of the band fixes it,
and it is the same mark the indicator panes are lidded with, so it needs no explaining.

**The crosshair's tags are the one place a shadow belongs.** They are drawn *over* the picture, they
move under a finger, and on a busy chart a flat chip on top of candles of a similar value reads as
part of the chart rather than as the reading the reader asked for. `Modifier.blur` is banned in this
repository and would be the wrong tool anyway — a render-effect pass over a whole layer, per frame,
to soften one small chip. `Paint.setShadowLayer` is the platform's own answer, black at 45 %, which
is exactly what the surface rules require of every shadow in the product. The paint is one instance
for the module rather than one per frame, because this runs on every pointer move while a crosshair
is up.

**The last price's glow is the one exception, and it is named.** The rules ban coloured glows because
a tinted shadow under a card is what makes an interface look sprayed on. This is a fall-off painted
inside the chart's own canvas, in the colour the bar is already drawn in, behind the single label that
says what the price is right now — the one mark on the screen a reader looks for first.
`ambientColor` and `spotColor` appear nowhere, and the gate that bans them is untouched.

**The markers scale what is new, not what is on screen.** A naive reading of «a 200 ms scale-in when
they enter the viewport» would set the whole chart pulsing on every pan. The arrangement is the one
the time axis already uses for its fade: the draw publishes the set of marks actually on the plot,
an effect chases it, and only the difference animates. A mark the last frame carried is drawn at full
size whatever the clock is doing, and nothing scales on the first frame — a share card renders once
and would otherwise photograph a chart's signals half-grown.

**The arrival is two motions because they are two events.** Changing the symbol replaces every bar
with another instrument's, and a picture that is wholly new should be seen to be drawn — left to
right, the direction time runs. Changing the timeframe keeps the instrument and re-cuts it: the same
market described again, where a wipe would claim a change of subject that has not happened. Both are
applied to the finished picture — a `graphicsLayer` alpha and a `clipRect` — rather than threaded
through eighty draw calls, which would have been eighty places for one of them to be forgotten. The
screen's existing interval dissolve went from 150 ms to 250 so the two speeds match.

**The controls audit found thirty-one silent controls of fifty.** The six the brief names were mostly
among the nineteen that were already right — the timeframe chip, the indicator button, fullscreen,
undo and the ••• menu all had their press and their haptic; the **drawing pencil did not**, and
neither did any replay transport, the selection toolbar, the legend's rows, the scale minis, the
pickers or the colour templates. Each was a perfectly ordinary `Modifier.clickable(onClick = …)`,
which is why nobody noticed: missing feedback is invisible in a diff and inaudible in a screenshot.
`Modifier.coineProControl` now carries the interaction source, the scale and the haptic in one place
— the same argument the haptic gate already makes for its five primitives, extended to everything
that is a control without being a button. Thirty-seven call sites across fifteen files; the gate's
count went from forty-odd to a hundred and seven.

**The spinner is gone.** A circle turning in the middle of a black rectangle says «wait» and says it
identically whether what is coming is a chart, a list or a photograph. What arrives here is always
the same shape — a grid, a ladder of prices down one side, a row of times along the bottom — so the
skeleton draws that shape and the bars **fill it in** rather than replacing a spinner with a layout.

---

## Phase Ξ5

### Item 19 — the route refuses itself

Run Τ2 took every menu row that leads to a broker account out of the directory, which is where a
reader would have found one. It did not stop a deep link, a restored back stack, or one future call
site that forgets the guard. Both addresses now ask `tradingOffered(activePlatform)` and pop back
rather than drawing — not a wall and not an empty screen: the address simply does not exist, and
there is nothing to explain because nothing the reader asked for was refused.

The test reads that check out of the shell rather than trusting somebody remembered it, and proves
the claim the guards rest on: every `connections_mt5_*`, `copy_account_*`, `copy_balance` and
`copy_broker` string is drawn on one of those two screens and nowhere else, so hiding the screens
takes the lot. It also asserts the patterns match something real — a walk over a path that does not
exist finds nothing and passes, which is the failure mode of every test written that way.

The brief says twenty-two `connections_mt5_*` keys. The tree carries **twenty**. The count is printed
rather than asserted, because a gate that fails when somebody writes a twenty-first string would be a
gate arguing with the product.

### Item 20 — attributing a short list

The question «does the app render every symbol the venue serves?» has two failure modes that look
identical from the screen: a client that drops markets, and a backend that serves few. They call for
opposite work. `MarketCatalog.served` — the count the venue returned, recorded before this app drops
a single name — is what tells them apart, and `SymbolUniverseBreadthTest` settles the client half:
twelve hundred synthetic pairs, none of them carrying artwork in this repository, twelve hundred
listed.

The printed forex figure is the interesting one. `MarketDataSymbols.forex` is **two symbols** — a
seed for a cold start rather than a universe. If the FX snapshot's configured set is also small, no
client work will fill that screen; `BLOCKED.md §Ξ20` has the endpoint and the one question to ask.

A stale comment in `MarketCatalogGateway` still claimed the rule run ΤΦΥ overturned — «a market the
app cannot draw is a market it does not list» — beside a filter that has answered true for everything
since F1. A comment that misreports the code beside it is worse than none, because the next reader
acts on it.

### Item 21 — the flow the item describes does not exist

There is no subscription code to redeem in this app. Membership is free and says so in two places.
Read as what it is actually about — the signals surface must never be an empty list with no
explanation — three of the four states are real and one is not, and the one that is not has nowhere
to come from: nothing in the signals route distinguishes «entitled but unlinked» from any other
refusal, and a sentence the client guessed would be worse than the server's own.

What the run did build is worth more than the item asked for. `toUiMessage` discarded the throwable
entirely, so **every** controller in the app collapsed «the phone has no network» into whatever
sentence the caller had chosen — «signals could not be loaded», «markets could not be loaded». A
reader on a train read that as the desk being down and tried again in an hour. It now reads the
throwable's *type*: an `IOException` means the request reached nobody, and that is one key, one
sentence, and every screen in the product at once.

### Item 22 — an action, or a reason

The rule is «no empty screen without one sentence and one action». The sentence half was already true
by construction. The action was not: eighteen empty states had none.

The audit's finding is that the rule cannot be applied flat, and several places in the app had
already worked that out and written it down. A locked community board answers the same refusal on
every press. A feed that publishes no order book will publish none on the second ask. A paper-trade
tab's action is the ticket tab in the row the reader is already looking at. A control whose every
press repeats the same answer teaches a reader that the app's controls do nothing, which is a worse
lesson than the empty screen the rule was written against.

So five gained a real action and the rest gained a written reason, and the gate asks for one or the
other. A reason that has to be written is a reason somebody has to have.

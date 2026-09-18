# RUN Ξ — resume

Written as the last action of every session, per R5.

---

## Where the run is

**All five phases are done.** Ξ1 and Ξ2 shipped as 4.97.0 and the owner's recording confirmed the
drag on device — «حرکت چارت درست شد» — which is the gate the brief put in front of everything below.
Ξ3, Ξ4 and Ξ5 shipped as 4.98.0.

**Twenty of the twenty-two items are ✅.** The two that are not say exactly what is missing and why:

* **Item 1** — the debug overlay was not built. There is no internal flavour and no device here, and
  the owner installs signed release APKs, so a `BuildConfig.DEBUG` overlay is one nobody could open.
  `ChartDragTraceTest` stood in and is the thing that found the defect. `BLOCKED.md §Ξ1` carries the
  decision the owner would have to take for a real one; nothing is waiting on it.
* **Item 21** — three of the four redemption failure states are built; the fourth cannot be, because
  this app has no subscription code to redeem and no signal from the server that distinguishes
  «entitled but unlinked» from any other refusal. `BLOCKED.md §Ξ21` has the table and the one thing
  the owner must supply to close it.

Four rows are ✅ with a stated deviation rather than silently: item 4 (the mechanism applied to the
four zone handlers rather than to the pan, which is what fixed it), item 8 (the fact, not the frame
count), item 12 (the press scale stays at the design system's 0.955, the 8 % darken is new), item 22
(«an action, **or** a written reason», and the reason is the finding).

**Shipped:** 4.97.0 (Ξ1 + Ξ2), 4.98.0 (Ξ3 + Ξ4 + Ξ5). Every gate green, the whole unit suite passing,
54 chart goldens re-recorded and no other family touched.

---

## What the next session does, in order

1. **Read the owner's verdict on 4.98.0 first.** The three things worth their eye, in this order:
   the welcome sequence from a cold install (the brand frame, then five scenes, each animating once);
   a symbol switch on the chart (the wipe) against a timeframe change (the dissolve); and the chart's
   first load on a slow connection (the skeleton rather than the spinner).
2. **`RUN_T2/BLOCKED.md §A` is now answerable.** Distance-versus-time on the fling was meaningless
   while the drag was broken — the owner had never felt the curve that argument is about. They have
   now. One word closes it.
3. **The RUN Τ2 backlog**, which is untouched and is the largest thing outstanding: B6 (offline as a
   first-class state), B7 (the single-symbol widget), B8 (Picture-in-Picture), C1's surface, C2's two
   gaps, C3, C4, C6. `RUN_T2/RESUME.md` names the files, the data each reads and the trap in each,
   and none of them needs a backend.
4. **`BLOCKED.md §Ξ20`'s question to the FX backend** — how many symbols a bare `ws/snapshot`
   returns. The client handles hundreds and is proved to; the bundled forex seed is two.

---

## Traps this session hit, so the next one does not

* **`-Dcoinepro.golden.record=true` on the whole `:app` suite runs out of memory.** Exit 137, SIGKILL,
  half the goldens recorded and a build that looks like a test failure. Record with
  `--tests "…ChartTypeGoldenTest" --tests "…GoldenScreenshotTest"` — one minute instead of six, and
  it finishes. Then run the suite normally to verify, and check `git status app/src/test/goldens/`
  groups by family before committing.
* **A Compose drag detector consumes before your callback runs.** `detectDragGestures` and its axis
  variants call `consume()` in the slop lambda and in the drag loop. A guard inside `onDragStart` or
  `onDrag` is always too late. Use `awaitOwnedDrag` on the chart — that is what it is for — and the
  same shape by hand anywhere else, as `WelcomeSlides` now does.
* **Modifier order decides who wins.** Modifiers apply outside-in, so the **last** `pointerInput` in
  a chain is the innermost node and is offered the Main pass **first**.
* **`swipeWithVelocity` cannot see a drag.** It injects the whole gesture between two frames. Use
  separate `performTouchInput` invocations and read the state before `up`; `ChartDragTraceTest` is
  the pattern.
* **The motion gate's spring rule greps a single line.** `slideInHorizontally(...) ... tween(` on one
  line fails; `fadeIn(tween(...)) + slideInHorizontally(spring)` passes, because the token order is
  what the regular expression sees. Put the fade first or the spring on its own line — and mean it
  either way: the fade is a tween because opacity has no momentum.
* **A test that walks a file tree passes when the tree is not there.** Every «no offender found»
  assertion needs a companion that says the search matched something real, or it is green because it
  looked at nothing. `ForexSurfaceReachabilityTest` carries one.
* **`testDebugUnitTest` and `:app:assembleRelease` are separate invocations**, or they run out of
  memory. A **wedged Gradle daemon** looks exactly like a slow suite: `./gradlew --stop` and re-run.

---

## Open questions for the owner

1. **`BLOCKED.md §Ξ21`** — can the signals route distinguish «entitled but the account is not
   linked»? A field on the 403 body would close item 21. If the case cannot happen, say so and it
   closes anyway.
2. **`BLOCKED.md §Ξ20`** — how many symbols does a bare `ws/snapshot` return on CoinePro-FX today?
3. **`BLOCKED.md §Ξ1`** — the drag overlay: a debuggable CI artifact, or behind the admin surface in
   the release build? Nothing waits on it now that the chart follows the finger.
4. **`RUN_T2/BLOCKED.md §A`** — the fling: distance or stop time. One word, and it is answerable now.
5. Everything else still open from RUN Τ2: the four alert tones, `membership_open_ourbit`, C5's
   separate scales, and the watchlist's «تحلیل».

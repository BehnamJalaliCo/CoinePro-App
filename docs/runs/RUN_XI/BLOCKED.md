# RUN Ξ — blocked

Each item carries the workaround that is **in the build** and the exact thing the owner must supply.

---

## §Ξ1 — the debug overlay, and why it could not be the instrument

**What cannot be done here.** Item 1 asks for a debug-only overlay on an internal flavour, and a
screen recording of one drag through it. This container has no device and no GPU emulator, so no
recording can be taken. More to the point, **the owner installs signed release APKs from CI** —
there is no internal flavour and no debug build in their hands, so an overlay behind `BuildConfig.DEBUG`
is one they could never open, and the recording item 1 asks for could not be produced by anybody.

**What is in the build instead.** `ChartDragTraceTest` — a stepwise drag through the real chart,
which failed on `main` at 4.96.0 with `[0,0,0,0,0,0,0,0,0,0]`, drove the bisection that named both
culprits, and now reads `[0,0,0,0,1,2,3,4,5,6]`. For finding and fixing the defect this is stronger
than an overlay: it is deterministic, it runs on every push, and it cannot go quiet again.

**What the owner must decide, if the overlay is still wanted.** One of:

* **a debuggable APK** — CI grows a second artifact from the `debug` build type, which is unsigned
  and installable side by side; the overlay then lives in `chart/ui/src/debug/`; or
* **a switch in the release build** — the overlay ships behind the existing diagnostics surface
  (`AdminController`), off by default and reachable the way the admin panel is.

The second is what would actually let the owner film it. It is not built yet because it puts a
development instrument into the store binary, and that is the owner's call rather than a build's.

---

## §Ξ8 — the re-measurement

Not a blocker on the code; a measurement only a real digitiser can take.

Three flicks of increasing speed and **one slow drag** on BTCUSDT H1, screen-recorded at 120 fps.
What to read out of them:

* the slow drag must show **non-zero displacement on essentially every frame** the finger is moving
  — not a burst and a stop;
* the flick must show a **continuous decay**, on the order of a hundred frames, ending cleanly
  rather than creeping at one pixel a frame.

Until that recording exists, item 8 is ⏳ and items 10–22 are not started — which is the brief's own
instruction, not a shortfall.

---

## §Ξ19 — the broker surfaces not yet covered

RUN Τ2 closed `connections` and `copy-trade` behind `FeatureFlags.forexTrading`, with
`ForexSurfaceReachabilityTest` reading the shell's hiding rule out of the source. Item 19 widens
that to `copy_account_*`, `copy_balance` and a walk of the whole navigation graph. Not started, and
not blocked on anything but the ordering the brief set.

# RUN Τ2 — blocked

Only real blockers. Each carries the workaround that is **in the build** and the exact thing the
owner must supply or decide.

---

## §A — A4's stop time against A4's distances

**What cannot be done.** A flick cannot both cover a screen and a half and be over in 1.4 seconds
at a cut-off low enough not to creep. The arithmetic is in `CHECKLIST.md` under «Why A2's and A4's
numbers cannot all be true»: on the owner's phone the two clauses evaluated at 3 000 px/s need
`f ≥ 1.804` and `f ≤ 1.705` of the same friction.

**What is in the build instead.** The distances, at `f = 1.25` and a 240 px/s cut-off: a 3 000 px/s
flick covers 2 134 px (1.98 screens, measured through the real event stream today) and settles in
about two seconds. TradingView, on the owner's own recording of the same finger, takes two seconds
as well.

**What the owner must decide.** Which of the two they want, in one word:

* **distance** — what is shipped. A flick that carries, and a two-second glide.
* **time** — raise the cut-off to 300 px/s and the friction to about 1.65. Hard flicks then stop in
  1.4 s and cover 1.52 screens; gentle ones lose a third of their travel (0.28 of a screen at
  800 px/s, against the 0.4 the brief asks for), and a hard release covers 2 424 px against
  TradingView's 2 900.

There is no third setting. Nothing else in the run is waiting on this answer.

---

## §B — what the device still owes

Not a blocker on the code; a measurement only a phone can take.

* **`ChartFlingBenchmark.flickVelocity{Slow,Medium,Hard}`** — written, and a benchmark needs a GPU.
  `docs/qa/DEVICE_PROOFS.md §1b` has the command.
* **The feel itself.** Three flicks of increasing speed on BTCUSDT H1, screen-recorded at 120 fps,
  is the only thing that answers whether the curve is right. Every figure in this run is a
  Robolectric measurement of the same event stream, which can prove the velocity arrives and the
  distance follows, and cannot prove a thumb is satisfied.

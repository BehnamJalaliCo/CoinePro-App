# RUN ΤΦΥ — report

What changed, with the numbers. `CHECKLIST.md` carries the line-by-line state; this file carries the
measurements and the reasoning behind the three or four decisions that were not obvious.

---

## Phase Τ — the chart scroll (4.90.0, shipped before Φ began)

### The one defect the phase found

Everything else in Τ was already true on `main`: the velocity path was clean, the curve was the
retuned one, the pencil was on the band. What was not true was the **hand-off frame**.

A finger lifts between two frames. The fling loop's first `withFrameNanos` therefore arrives when
the release is already about one frame in the past — and both clocks, `ChartFling` on the chart and
`KineticScroll` in the JVM twin, answered *«nothing yet»* on that frame and returned zero. The
sequence a thumb got was: the picture tracks the finger at full speed, stops dead for eight
milliseconds, then resumes. That is one frame, and it is the one frame of stillness that can be
felt, because it lands at the moment the chart is moving fastest.

The clock is now seeded one frame *before* the frame that first ticks it.

The first attempt at the fix did not work, and the way it failed is worth writing down. The clock
was stored with `-1` meaning «unset». Seeding a frame back from a frame time at or near zero — and
`withFrameNanos` hands out small numbers on a fresh process — produces a **negative** start, which
the sentinel read as «unset» and re-seeded on the next frame, and on the next. The fling returned
one step and then stood still for ever. It is a flag now, which is what the `KineticScroll` twin had
been doing for the same reason since run B.

### The velocity, read back rather than asserted about

A distance test cannot separate «the curve is wrong» from «the speed handed to the curve was divided
by the display density on the way in». Both produce a short flick. So the velocity is recovered from
the travel: the curve is closed-form, `distance = (v − cut-off) / f`, which makes the distance an
invertible measurement of the speed that entered it.

| Injected | Recovered | Error |
|---|---|---|
| 3 000 px/s | **2 907 px/s** | −3.1 % |

The brief allows ±10 %. A divide by the owner's 2.625 would have read back 1 143 px/s.

### The fling, measured on the owner's density

`ChartFlingRegressionTest`, 420 dpi, screen 1 079 px:

| Release | Travel | Screens | Settled |
|---|---|---|---|
| 3 000 px/s | 2 134 px | 1.98 | ~2.0 s |
| 800 px/s | ≥ 432 px | ≥ 0.40 | — |
| 120 px/s | ≤ 1 bar | — | it is the settle spring, not momentum |

Three of the brief's four acceptance numbers. The fourth — «stops within 1.4 s» — is not reachable
beside the other two on any exponential decay with any cut-off; the arithmetic is in `CHECKLIST.md`
and the short version is that the 800 px/s distance and the 3 000 px/s duration pull `f` in opposite
directions and never meet. TradingView, the thing being matched, takes two seconds itself on the
owner's own recording, so the distances were kept.

### The constants, and why they are not the brief's

T2 names `frictionMultiplier = 0.9f, absVelocityThreshold = 150f`. What ships is `1.25 / 4.2` and
`240f`, and the reason is the line's own acceptance test.

* **150 px/s is 1.25 pixels a frame at 120 Hz.** A fling that runs down to it spends its last
  0.38 s — 45 frames — moving under two pixels a frame, which is precisely the creep the same item
  forbids and precisely what the owner filmed («۲،۲،۲،۲،۱،۱،۱،۲،۱،۱»). 240 px/s *is* two pixels a
  frame, which is what makes the tail impossible rather than merely short. Measured: **0 frames**.
* **0.9 friction is 3.78 per second**, which puts a 4 300 px/s release at 1 130 px — one screen —
  against the 2 900 px TradingView covered on the same phone with the same finger. It is, within
  rounding, the 3.8 that run Τ was called in to remove.

### The pencil

Already on the band, in all three modes and both orientations, since run Τ's first pass. What this
run added is the **order**: `ChartToolbarTest` now reads the laid-out tree and requires the pencil's
centre to lie between the timeframe chip's and the indicators'. The chip carries a `testTag` so it
can be addressed; that is the only production change the assertion needed.

Six frames, one per mode × orientation, in `app/build/proof/`.

### What the phase did not touch

`setFrameRate`, the rubber band, `PinchZone`, the auto-scale springs. T5 asks for an absence and the
diff is the evidence: two fling clocks, one `testTag`, one benchmark file, one docs section, and
tests.

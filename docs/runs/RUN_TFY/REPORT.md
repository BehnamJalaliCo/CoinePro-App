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

---

## Phase Φ — the universe, the positioning, the free period (4.91.0)

### The measurement that decided F1

`SymbolArtwork.covers` was the filter at the catalogue and at the live feed alike, and the reasoning
behind it was good: a grey disc with a «D» on it beside forty real logos reads as a broken image.
What the rule was actually doing, as of the last coverage report:

| | |
|---|---|
| Tether pairs LBank lists | **1 333** |
| Of those, traded in the last day | 1 007 |
| Marks in this repository | **178** |
| Share of LBank's 24-hour turnover those 178 carry | 50.4 % |

So the filter was hiding **seven markets in eight** in a product whose subject is the list of
markets. What replaces it: three letters of the ticker on a disc whose hue is an FNV-1a of that
ticker, written out here rather than taken from `String.hashCode`, which is a platform's business
and not a contract. Two bands of the wheel are excluded — the greens around 120° and the reds around
0° — because those two colours already mean «up» and «down» on every other surface in this app.

`ARTWORK_GATES_LISTING` is the one constant behind the reversal, and both states are tested.

### What actually answers, and what the app carries instead

Probed from this container on 2026-09-17:

| Address | Answer |
|---|---|
| `coineprofx.com/v1/symbols` | `200` — the web app's own HTML |
| `coineprofx.com/api/v1/symbols` | `404` |
| `coineprofx.com/ws/snapshot` | `200`, **19 symbols** |
| `tradeyar.trade-future.ir/v1/symbols` | `307` → `/login` |

The client is written to the brief's shape anyway, and falls through to the snapshot and then to
`BundledUniverse`: **300 coins by market capitalisation plus the 49 non-crypto markets this app has
marks for**, generated by `scripts/design/build-symbol-universe.py`. 78 of the 300 have a Persian
name in this app already; the rest fall back to their English one, because a transliteration
invented in a script is a name no exchange shows and no reader types.

### The debounce, and why it moved

80 ms → 200 ms, which is the brief's number. Eighty was measured against a catalogue of a few
hundred rows. The search now covers the venue's list **plus** the bundled three hundred, which on
LBank is thousands, and a re-rank of that on every keystroke of a fast typist is work thrown away
before anybody sees it.

### The recent list that was documented and did not exist

`SearchScreen`'s own KDoc has described «the recent list when the field is empty» since it was
written. There was no such list — an empty field showed the browse ranking, which is the markets tab
one tap away. `RecentSearchStore` is eight markets, newest first, drawn as chips.

What is stored is the **market that was opened**, not the text that was typed. A query is somebody's
half-typed guess; a ticker is a row; and on a phone that may be shared, in a product about money, a
list of visited markets is a smaller thing to be keeping than a list of typed strings.

### What phase Φ found about «everything free»

**This app has almost no client-side paywall.** The walls that exist belong to a server: the academy
sends `locked` and a `lockReason` per lesson on the wire, the signal list answers a reader without
membership with a 403, and the crypto venue's identity check is the venue's law. `Entitlements.all`
opens what the client decides and cannot open any of those.

Where the app can usefully try, it now does. A **tier**-locked lesson is live and tappable, the
request goes out, and the wall appears only if the backend actually refuses — with the server's own
copy. A door painted shut on a field that may be a release out of date is worse than a wall the
server admits to. A **phone**-number lock is left alone: that one the reader can fix in one screen,
and a refusal they were not warned about is worse than a lock that explains itself.

### The flags, and why they are `var`

`FeatureFlags.forexTrading = false` and `FeatureFlags.allUnlocked = true` are the whole of phase Φ's
positioning. They are `var` rather than `const` deliberately: a `const val false` makes the other
half of every condition unreachable, and the half that is off in a shipping build is the half that
rots. `EntitlementGateTest` and `TradePartnersFlagTest` drive both states of both.

With forex trading off, and **absent rather than dimmed** in every case: the introducing-broker card
and the second exchange leave the partner sheet (LBank stays — it is the venue, not a listing);
`onOpenCopyTrading` is null on the signal page; the connections row is null on the menu and on the
portfolio; and `connections` joins the search's `absent` set so its section is not drawn either.

One narrowing, and the checklist row says so: F5 lists «forex KYC» and this app has no
forex-specific KYC. It has one account-verification screen, which the crypto venue requires, so it
stays.

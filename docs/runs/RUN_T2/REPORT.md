# RUN Τ2 — report

---

## Phase A — the chart scroll

### What the phase found before it changed anything

The phase is written on the premise that run Τ was skipped. It was not. `2465e19` — **4.90.0 «run
Τ: the frame the fling stood still on»** — sits on `main` between 4.89.0 and 4.91.0, and the fling
files are not byte-identical to 4.89:

| File | Change since 4.89.0 (`d3c0481`) |
|---|---|
| `chart/ui/.../ChartFling.kt` | +47 / −8 — friction 3.8 → **1.25**, cut-off 20 → **240 px/s**, hand-off seeding |
| `chart/core/.../ChartPixels.kt` (`KineticScroll`) | +13 — the same two numbers on the JVM twin |
| `chart/ui/src/test/.../ChartFlingTest.kt` | +32 |
| `chart/core/src/jvmTest/.../ChartPixelsTest.kt` | +15 |
| `benchmark/.../ChartFlingBenchmark.kt` | +43 — `flickVelocitySlow/Medium/Hard` |
| `app/src/test/.../ChartFlingRegressionTest.kt` | +60, new file |

`ChartFrameRate` and `ChartStrokePredictor` are unchanged since 4.89, correctly: neither carries a
velocity. The frame rate is what the owner's own measurement ruled *out* as the cause (120 fps
against TradingView's 40), and the predictor is fed only by the freehand drawing handler.

### What was re-measured, on this tree, today

Driving the real event stream through the real chart at the owner's density (411 dp, 420 dpi):

| Figure | Measured | Asked for |
|---|---|---|
| Velocity reaching the curve from a 3 000 px/s flick | **2 907 px/s** (−3.1 %) | ±10 % |
| Hard-flick travel | **2 134 px = 1.98 screens** | ≥ 1.5 screens |
| Hard-flick settle | **≈ 2.0 s** | ≤ 1.4 s — see `BLOCKED.md §A` |
| Frames the flick is delivered over | **100 moving frames**, biggest step 4 of 164 bars | ≥ 20 moving, no frame > ¼ |
| Creep tail under 2 px/frame | **0 frames** | ≤ 3 |
| 800 px/s travel | ≥ 0.4 screens | ≥ 0.4 |
| 120 px/s release | ≤ 1 bar (the settle spring) | never flings |

### What this phase actually added

1. **The gutter clause of A4 had no test.** A flick that starts on the price ladder must scale price
   and must not throw the time axis; the rule lived in one `onPlot` condition in the fling handler
   and nothing asserted it. `ChartFlingRegressionTest.a flick in the price gutter stretches the
   scale and never throws the time axis` drives a 3 000 px/s swipe down the ladder at
   `x = 0.97 · width` and requires the window to stay where it was, within the one bar the lift
   springs to.
2. **A stale claim in a KDoc.** `ChartFlingTest`'s header said friction went «3.8 to 1.4» and the
   cut-off «20 to 150». Neither number is the one in the file beside it. A comment that misreports
   the constant it documents is worse than no comment, because the next reader tunes against it.
3. **The measurements above**, printed by the tests rather than asserted loosely, so the next run
   can diff them.

### What Phase A did not change, and why

`exponentialDecay(0.9f, 150f)` is not shipped. A2's own acceptance test forbids A2's own cut-off —
150 px/s is 1.25 px a frame at 120 Hz, so a fling that runs down to it ends *inside* the sub-2
px/frame tail the same item says must not exist, and spends 45 frames getting there. The full
arithmetic, and the one decision this leaves the owner, are in `BLOCKED.md §A`.


---

## Phase B — the gaps the 4.92 audit found

Five of the ten landed in this run; two of the ten were already shipped by runs Φ and Υ and are
audited rather than rebuilt; three are not done and are named, with their files and their traps, in
`RESUME.md`.

### B2 — the entitlement is server-fed now, and it was not

The switch and both its states were already there. What the old KDoc admitted is exactly what B2
asks for: «server-fed **in intent** and local in fact». Three pieces close it:

| Piece | Where | What it does |
|---|---|---|
| `EntitlementsGateway` | `core/account/` | One GET of one field, per deployment prefix. **Every** failure — no route, no signal, an unparseable body, a non-boolean field — answers `null` |
| `EntitlementStore` | `core/datastore/` | Keeps the last served answer. `null` means «never answered», which is not `false` |
| `EntitlementStartUp` | `app/` | Applies the stored value at process start, then refreshes **for the next launch** |

The ordering is the design. An entitlement that arrived mid-session and took effect at once would
close a door somebody was standing in — the lesson they were three minutes into, the layout they
were about to save — and a wall that appears while the screen is up reads as the app breaking, not
as a subscription ending. So the reader is at most one launch behind the server and never surprised.
Two new cases in `EntitlementGateTest` hold the asymmetry: a silence leaves the app open, and a
served refusal reaches every derived answer through the one path rather than two.

### B3 — what the audit actually found

**`copy-trade` survived the flag.** It is keyed to the forex *platform*, which is a different
question from whether this build trades there at all, so a reader whose session was on CoinePro-FX
still had the row — leading to a screen about mirroring signals onto a MetaTrader account this
build will not open. Both call sites in the shell now drop it beside `connections`.

`ForexSurfaceReachabilityTest` reads the shell's own hiding rule **out of `CoineProApp.kt`** rather
than retyping the ids, because a second list is a list free to be shorter than the code — which is
the hole the file exists to close. Four cases: the flag off hides both; the flag on leaves both in
the catalogue so they can come back; and the hidden set never grows outside the trading block,
which would mean the flag had quietly gained a second meaning.

One key of the four the line names is deliberately **not** removed. `membership_open_ourbit` is the
crypto venue's sub-account check, and `FeatureFlags.forexTrading`'s own note keeps «the account
verification the crypto venue requires» either way. Taking it out would remove crypto sign-up from
a crypto app. The row is ❌ rather than ✅ for that reason, and `RESUME.md` asks the owner to
confirm the reading.

### B5 — the row was already right; the affordance was missing

The pulse row printed figures and kept the four `_why` sentences in a sheet from the day it shipped.
What it did not have is anything saying so: a tappable cell that looks exactly like a printed one is
a door nobody opens. A 12 dp ⓘ now sits beside each cell's name and the **target stays the whole
cell**, because 12 dp is below every minimum a thumb is measured against. Not one word of the four
explanations changed.

### B9 — the repeat that was actually missing

Three policies existed and none of them says the useful thing. `ONCE` tells the reader once, which
is nothing if the phone was face down. `ALWAYS` re-fires only while the condition keeps *becoming*
true again, so a price that crosses a line and stays above it is announced once and never again —
which is precisely the move somebody set the alert for. `DAILY` is a day late.

`AlertRepeat.UNTIL_ACKNOWLEDGED` keeps speaking every N minutes until the reader answers, and an
answer is the only thing that stops it. Not a count, not a timeout: a repeat that gives up on its
own is the missed alert it was built to prevent. There are two ways to answer, because the value of
the policy is that it keeps going when a tap did not happen — **opening** the notification, and a
**«دیدم»** button on it, so it can be silenced from the shade by somebody who is driving or in a
meeting and has already decided to do nothing.

The rest of the model was already right and stayed where it was: four delivery channels per alert,
a per-alert loudness, vibration as its own channel. One thing was wrong. The loud level plays on the
**alarm** output, and an alarm is the one sound Android lets through a «priority only» filter on
most phones — so the escalation a reader asked for on a Tuesday afternoon was what would wake them
at three in the morning, from an app about candles. It is now capped back to the ordinary channel
while the phone is in Do Not Disturb, and the system's own filter decides from there. Nothing in
this app calls `setBypassDnd`, and that is deliberate.

Two editors reach it. In the composer it is a chip in the row that was already there. In the editor
sheet it is a row of its own, **below** the bar frequency rather than inside it, because the two
answer different questions in different units — the chips above choose which *bars* may fire, this
chooses what happens on the *clock* once one has — and folding them together would make «once per
bar close» and «keep telling me» mutually exclusive for no reason a reader could discover.

What is not done is the bundled sound set: this repository holds no audio, and `BLOCKED.md §B9`
carries the four files, their format and the channel work that follows the moment they exist.

### B10 — one sentence, and where it goes

«این اپ معامله انجام نمی‌دهد و کارمزدی دریافت نمی‌کند.» / "This app does not trade and takes no
commission."

It started in the menu's footer and moved to sit **directly under the identity block**. The trigger
was a failing test — the footer of a `LazyColumn` is not composed until it is scrolled to — and the
test was right about the reader too: the foot of a nine-section directory is a place somebody
arrives at by accident, and «prominent» and «at the bottom of a list nobody scrolls to the end of»
are not the same instruction. `docs/product/STORE_LISTING.md` carries the same sentence, and
`T2ProofTest` compares the two strings so they cannot drift.

---

## Phase C — what the audit found before anything was built

Two of the six are largely already in the product, which is worth knowing before a session spends a
day rebuilding them:

* **C5, compare on the chart.** `chart/core/…/Comparison.kt` normalises to PERCENT — rebased to the
  **left edge of the viewport**, so the comparison re-answers itself as the reader pans, which is
  the whole point of the feature — and to INDEXED_100, RATIO and ABSOLUTE. The hub's «مقایسه با نماد
  دیگر» tile adds from the watchlist and the legend carries a removable row per series. What C5 adds
  on top is the per-series percentage across the visible range printed on the legend, and a one-tap
  swap in place of the four-chip row.
* **C2, alerts on drawn objects.** `AlertTrigger.DrawingTouch` and `AlertDrawings.kt` are shipped,
  and the evaluator resolves a trend line's price again at every sample rather than freezing it —
  which is the hard half. What is left is the delete-asks-about-its-alerts flow and the thumbnail.

The other four — C1, C3, C4, C6 — do not exist. `RESUME.md` names the data each one reads, and none
of them needs a backend: the news and the calendar are in `core/marketintel`, the discipline chart
is in `JournalController`, the alert history is in `AlertAuditStore`, and the share generator is
`ShareCard.kt`.

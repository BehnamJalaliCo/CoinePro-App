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
  دیگر» tile adds from the watchlist and the legend carries a removable row per series.

  **What this run added** is the figure C5 asks for: `changeAcrossVisible`, and the percentage
  beside each symbol on the legend. It is the move across the **bars on screen** and not the
  series', because the lines are rebased to the viewport's left edge and a legend reporting anything
  else would be describing a different picture from the one drawn. It anchors on the first bar that
  *traded* rather than the first index — a market closed for the first two bars on screen has not
  moved `NaN` per cent over a window the reader can see rising — and prints «—» rather than «۰٪»
  where there is nothing to measure, because a market that has not traded has not moved zero.

  Two clauses are not met and the checklist row says so. «Separate scales» is a feature this chart
  does not have: ABSOLUTE is not it, since raw values on one axis put a $2 300 instrument and a
  $0.42 one on the same ladder — the flat line the enum's own note exists to warn about. And the
  watchlist's «تحلیل» opens the side-by-side panes run Υ built, which is a *different* comparison,
  several symbols each with their own chart; pointing it at the overlay would take the multi-chart
  away from the reader who has it.
* **C2, alerts on drawn objects.** `AlertTrigger.DrawingTouch` and `AlertDrawings.kt` are shipped,
  and the evaluator resolves a trend line's price again at every sample rather than freezing it —
  which is the hard half. What is left is the delete-asks-about-its-alerts flow and the thumbnail.

### C1 — the rule, ahead of its surface

`NotableBars` decides which bars «چرا این حرکت؟» would offer itself on: range greater than twice the
average range of the **preceding** twenty bars. A ratio and not a number, because «a big candle» has
no absolute meaning across the markets this app carries — two hundred dollars is a quiet hour on
Bitcoin and an impossible one on EURUSD, and a threshold in price would mark every bar on one and
none on the other. What a reader notices is a bar that is big *for this chart, lately*.

Four decisions in it are worth keeping:

* the bar is **kept out of its own baseline**, because a bar in its own window raises the bar it has
  to clear and the biggest candles would be the likeliest to hide themselves;
* the **first twenty bars of a series are never marked**, since a baseline of three bars is not a
  baseline and a dot on the oldest bars of every chart reads as a fault;
* a **flat window answers nothing**, rather than «infinitely more than zero»;
* a window with **gaps averages over the bars that traded**, because dividing by twenty when four of
  them never printed understates the baseline and marks ordinary bars.

Nothing a reader can see has changed, and the checklist row is ❌ for that reason. The design for the
surface — and the argument that the dot belongs in the **event strip** rather than in the plot's
gestures, which already carry the drawings, the eraser and the trade ring — is in `RESUME.md`.

The other three — C3, C4, C6 — do not exist. `RESUME.md` names the data each one reads, and none
of them needs a backend: the news and the calendar are in `core/marketintel`, the discipline chart
is in `JournalController`, the alert history is in `AlertAuditStore`, and the share generator is
`ShareCard.kt`.

---

## C2 — alerts on drawn objects, and the two silences under it

The row read «the engine exists; the run did not audit the gaps», and the gaps were listed as two
surfaces: a question before deleting a drawing, and a thumbnail on the alert row. Both are built.
Neither was the point.

### The failure was written down and nobody had read it as a failure

`GuestAlertMarketSource` resolves each alerted drawing's level at the last two closed bars, and its
own KDoc says what happens when it cannot:

> A drawing the reader deleted, or one whose tool has no price at all, is left out entirely and its
> alert simply never fires.

As evaluation that is exactly right — an unresolvable level must not fire — and as a product it is
the failure this app has spent four runs removing from itself. The alert stayed in the centre under
«فعال», with its sentence, its timeframe and its repeat policy, reading as armed. It could not go
off. The way a reader would find out is **by not being told about a touch**, which is to say never.

So the dependence is made visible in the only two places it can be seen.

**Before the deletion**, because the chart is the only screen the deletion starts from. Three
answers, not two: *keep everything*, *take the line and its alerts*, and *take the line and keep the
alerts*. The third is not a hedge — it is what somebody about to redraw the line wants, and a
two-button dialog would force them to pick wrong and repair it afterwards. `CoineProChoiceDialog` is
new in the design system for it, and it inherits `CoineProConfirmDialog`'s rule whole: **ask only
where recovery is otherwise impossible.** A drawing nothing watches still goes on the tap with no
question and an undo behind it, which is the trade run Ω2 made and it is untouched.

**After it**, in the alert centre, in the refusal colour rather than «متوقف»'s muted grey — because
a paused alert is the reader's decision and this is not one.

The rule that makes the mark honest is in `AlertDrawingLinks.orphanIds` and is worth repeating:
**not knowing a symbol's drawings is not the same as knowing it has none.** Only symbols actually
read get a verdict. Without that, the first frame of the screen — before any drawing has loaded —
would mark every drawing alert in the list as broken, which is a worse lie than the silence.

### And the second defect, which the tests found rather than the audit

Writing the proof for «the undo still works» produced a failing test on the **old** behaviour.
Since run Ω2 the toast beside a deleted drawing has offered «واگرد»; it called the chart's undo,
which walks a stack `deleteDrawing` **never pushed to**. Every other change to the drawing layer
arrives through `onDrawing`, which records a step whenever the shape of the layer changes; the
delete wrote the new state straight in. So the button did nothing, or took back an unrelated
change — a chart type, an indicator, a bar length — while announcing that it had restored a
drawing.

It records now, once per deletion rather than once per drawing, which also makes true a comment the
selection toolbar had been carrying for two runs: «the chart's history records the whole deletion as
one step». It did not. It does.

### What the thumbnail is, precisely

A polyline through the drawing's **stored anchors**, normalised into a 34dp box. Not a render:
`feature:alerts` does not depend on `core:chart` and must not start — the argument is in
`AlertDrawings`' own KDoc and it is that a second geometry engine would one day tell a reader about
a touch that is not on the chart in front of them. What the sketch answers is «which of my three
trend lines is this», which the anchors answer completely. A one-point tool draws as a horizontal
line, because that is what a price level is, and not as a dot, which would read as a fault.

---

## C6 — the morning brief, and the four things it refuses to say

Once a day, at an hour the reader picks, one notification: how their watchlist stands, which market
moved most and by how much, what the chart says about it, and a line of that market's night.

The composing is `RasadBrief` in `:chart-core`, beside the coach whose sentence it borrows, and for
the same reasons — no model, templates over arithmetic the reader can check, and it ships to the web
terminal with the engine rather than being rewritten against it. What took the thinking was not the
sentences. It was the four cases where the right answer is to say nothing, and the one where it is
not.

**Off until switched on.** Every other one of the eighteen notification categories *reacts* to
something that happened, and arrives because the reader already asked for the thing it reports.
This one arrives on a clock whether or not anything happened. An app that starts waking somebody
every morning without being asked is the one they uninstall rather than the one they configure, so
`MORNING_BRIEF.defaultOn` is false and the hour is theirs.

**No brief about nothing.** An empty watchlist, or a pass where no market's change could be read at
all, produces null rather than a notification with a blank in it. And a symbol whose quote came back
*without* a daily change is left out of the count entirely rather than counted as flat — «two of
your five rose» and a sentence quietly describing three markets nobody measured are different
claims.

**No story out of noise.** Below a tenth of a percent — inside the spread on most of what this app
carries — there is no mover. Naming a 0.04 % night as the day's story would be the brief
manufacturing one, and a reader who acted on it would have learned exactly the wrong lesson.

**But a quiet night is still sent.** This is the decision that goes the other way, and the tempting
rule — «only send it when something moved» — is wrong. A scheduled brief that sometimes does not
arrive is indistinguishable from a broken one, and the reader's response is not «nothing happened»;
it is to stop relying on it and open the app anyway, which is the whole thing the feature was for.
So a flat night says so in one sentence and costs two seconds.

### The calendar is its own file, because every bug in it takes a day to see

«The brief arrived at four in the afternoon», «the brief arrived twice», «the brief stopped after
the clocks changed» are one arithmetic slip apart from each other, and none of them can be found by
running the app — you would have to wait until tomorrow to see the wrong answer. So
`MorningBriefSchedule` takes the clock as a parameter and each of them is a case in a list.

Two of its rules are load-bearing. **At exactly the chosen minute the delay is a whole day, never
zero** — zero is how a daily brief becomes a loop that fires, reschedules for now, and fires again.
And the once-a-day guard is keyed on **the local day** rather than on «at least twenty hours ago»,
because the second reading silently skips a day whenever a run lands early: 07:05 on Monday and
06:50 on Tuesday is 23h45m, which fails the test, and Tuesday then gets no brief at all.

**`PeriodicWorkRequest` is the wrong tool** and this is worth writing down. It takes an interval,
not a time of day; Android runs it anywhere inside the period, so «every 24 hours starting at 07:00»
drifts a little later every run and after a fortnight the morning brief arrives at lunchtime with
nothing in the code to say why. Worse, an interval never asks what time it is, so it cannot follow a
reader across a time zone or across the hour the clocks change. One-time work with a delay computed
from the device's own clock asks that question every single day. The cost is that the chain must
re-arm after each run — which `MorningBriefWorker` does **before** the delivery, so a brief that
throws still books tomorrow's — and that the app re-arms it at start-up, so a run the system dropped
does not end the schedule for good.

### The picture, and the trap under it

`BriefSparkline` draws the closes of the same bars the coach's sentence was written from: no axes,
no grid, no labels, nothing that would need a scale a reader cannot read at that size. What survives
is the shape, which is the part that cannot mislead. The colour is read **first against last** — a
night that fell all the way and bounced on the final bar is a night that fell, and colouring by the
last candle would contradict the sentence printed directly above it.

Trying to test that colour rule taught two things, and the second one changed the design.

**Robolectric's default graphics rasterise nothing.** Its `Canvas` records calls, so every pixel of
every bitmap comes back as the background — and the first version of the colour assertion passed
against an empty image. `@GraphicsMode(NATIVE)` fixes that and made the test real.

**And then the `:app` suite was killed with SIGKILL.** Exit 137, no failing test in any module, and
the reason is that native graphics loads a library *outside* the JVM heap: `maxHeapSize = "2g"` does
not bound it, and the suite already runs near this container's ceiling with four hundred Robolectric
renders behind it. Raising the heap would not have helped, because the heap was not what overflowed.

So the geometry came out into `BriefSparklineShape`: draw or refuse, which way the night went, where
each point lands when the span is zero, which closes survive. Ten cases, an ordinary unit test, no
Android at all — and what is left in the painter is three paint calls with nothing to decide. That
is the same move `priceAlertChannelId` made in `NotificationChannels` and for the same reason, and
it is stated there as plainly as it deserves: out here it is a few lines and a unit test.

### What the reader sees on the settings screen, including a contradiction

The brief's hour can fall inside the reader's own quiet hours. The delivery honours the brief —
they typed that time specifically, and swallowing it would be the app overruling the more specific
of two instructions and leaving them with a feature that does nothing. But the card says so, where
both numbers are on the screen together. The contradiction is shown rather than resolved.

---

## C1 — the dot, and the sentence it refuses to write

`NotableBars` shipped in 4.96.0 as a rule with no surface: it decided which bars a reader might ask
«چرا؟» about and nothing on screen changed. The row was ❌ for that reason and it is ✅ now.

### Where the mark goes was the whole design

**Not in the plot.** A tap there already means a drawing, an eraser stroke or a trade ring, and a
long press already opens the context menu. Taking one of them over for a feature nobody has asked
for yet would put the app's most delicate gesture path at risk, and the failure would not be «the
new feature does not work» — it would be that drawing a trend line started doing something else.

**In the event strip**, under the bars, where the glyphs already live. That strip is already «things
that happened at this time» and an unusually large bar is exactly that; the hit-test is already
written and already confined to the few points of height below the time axis.

Which raised the one question the plan had not: what happens on a bar that has both. A glyph *names*
something; the dot only says the bar was large. So the glyph wins, and it wins **twice** — the
renderer draws no dot under a bar a glyph holds, and `ChartEvents.notableAt` takes the same set as
`exclude`. Either half alone is a defect: skip only the drawing and a reader can tap an invisible
dot; skip only the hit-test and a tap that misses the glyph by four points opens the wrong sheet.

### The sheet is bound to the rule, by construction

`NotableBars.ratioAt` lives beside `of`, repeats its rules exactly — twenty preceding bars, the bar
never in its own baseline, an average over the bars that actually traded — and is the only place the
sheet's figure comes from. The first case in `NotableBarReadingTest` asserts that the rule marks the
bar *and* that the ratio agrees, on the same series.

That test exists because of the one outcome worse than no feature: a dot on a bar whose sheet says
there was nothing unusual about it. Two implementations of one threshold drift, and this drift would
be invisible until a reader tapped.

### And what it will not say

The feature's name is a question, and the honest answer to it is usually «nobody published one».
What the sheet carries is *what was on the calendar and on the wire inside that bar's own half-open
window* — presented as that, not as a reason. Where the window holds nothing, it says so in a
sentence and stops.

The tempting alternative is to widen the window until something falls in and print it under «چرا».
That is the same move `RasadCoach` was built to refuse on the chart itself, and it fails the same
way: not with a worse sentence but with a **confident wrong one**, which a reader has no way to
check and every reason to act on.

One smaller decision, in the same spirit: the events in the sheet are **not** filtered by the
reader's own glyph switches. Those switches decide what clutters the axis. This is a question asked
about one bar, and hiding a rate decision inside it because the calendar's glyphs were switched off
would be answering «why did this move» with a filtered truth.

---

## C4 — «هفته‌ی من», and the number it will not print

A card at the top of the journal: trades closed this week, how many won, alerts that fired against
alerts still armed, replay sessions. Nothing inferred, no score, no grade, no streak — each number
is something the reader did that the app already recorded, and a week is far too short a sample for
anything else to be honest at.

### The week is Saturday's, and that had to be one definition

`core:marketdata` already knew that Iran's week opens on Saturday — it is what keeps a weekly candle
from putting Thursday and Friday, the two quietest days of an Iranian week, in the middle of the bar
instead of at its end. The rule was private to that module, serving the candle alone.

The moment the reader's *own* week needed the same boundary there were two choices, and a second
copy of a boundary is the copy that gets corrected alone. So `WeekStart` moved to `core:common`,
where the calendar already lives, and `CandleContracts` delegates to it. A weekly bar and «هفته‌ی
من» now open on the same day by construction rather than by coincidence.

### The win rate is withheld, and that is the feature

**Two of four is fifty percent and means nothing.** One outcome moves that figure by twenty-five
points. Every app in this market prints it anyway, and the reader who went two-for-four reads «۵۰٪»
and believes something about themselves that four coin flips produce more often than not.

So under `MyWeek.MINIMUM_TRADES` the percentage is **not shown**. Not rounded, not greyed, not
wrapped in a caveat — absent, with the count in its place: «۲ از ۴», which is the claim that is
true, plus one line saying why the percentage is not there. Above the floor it is printed plainly,
because at that point it is worth something and dressing it in caveats would be the opposite
failure.

`WeekSummary.winPercent` is null below the floor and the card has **no fallback branch**, which is
what makes the rule hold: breaking it would take adding one. The share card keeps the same rule for
a stronger reason — a picture outlives the screen it came from, and a «۵۰٪» posted to a channel is
read by people who never see the four under it.

### And one row is wired to nothing, deliberately

«Moved most on your list» is in `WeekSummary` and tested, and the shell passes nothing to it.

The only change figure this app holds for a watchlist is the feed's own **24-hour** one. Putting
that under a heading that says «this week» would be a lie in a confident font — which is the exact
failure this phase has spent three items removing from the alert centre, the morning brief and the
notable-bar sheet. So the field stays null, the card omits the row, and `JournalWeekInputs` names
where to hand in a weekly figure when something actually measures one.

### A gate that crashed instead of reporting

Writing the Persian for this card tripped `tools/i18n/lint_strings.py`'s hamza-on-heh rule — «هفتهٔ»
where the house spelling is «هفته‌ی» — and the gate died with an `AttributeError` instead of naming
the file. Its `fail` was typed `Entry | None` while the two Kotlin checks had always passed a plain
string, so the **first** violation of those two rules would always have crashed it.

A gate that dies on the thing it is watching for reports nothing at all, and reads as a broken tool
rather than as a violation. It is fixed, it named all fifteen occurrences, and all fifteen are
corrected.

---

## C3 — «دوئل با گذشته», and the verdict that is neither

The last unbuilt item in phase C, and the one with the shortest description and the most rules.

The shape: one round a day. A moment out of the reader's own past — 120 bars of context behind it,
the next 20 hidden — and one question. Up or down. The moment they answer, those 20 bars appear.

### It is the reader's own markets, and that is not a detail

`Duel.roundFor` picks from the catalogue the reader is already watching, sorted so two phones agree
about what the list is. A duel on an instrument they have never opened is a quiz question; the
feeling the feature exists for is «I would have caught that» about a market they know.

The pick is written-out arithmetic rather than a platform `Random`, for `Arena.challengeFor`'s
reason — the same answer has to come out on a phone, on the JVM in a test and in the web terminal —
and it is deterministic in the date, so closing the app does not reroll the question. A reader who
could reroll until they liked the look of a chart would not be duelling, they would be playing a
slot machine.

**Different constants from the Arena's**, and there is a test for it. Two features asking about the
same instrument on the same day reads as the app having one idea rather than two.

### The rule the whole feature rests on

A move under `Duel.FLAT_PERCENT` is `TOO_CLOSE`: **neither right nor wrong.**

Without it every round resolves. Half of them resolve on noise, a reader converges on fifty percent,
and what they learn is that a coin flip is a read — which is the single habit this product exists to
argue against. With it the record counts only the rounds where the market actually did something,
and says how many it set aside.

Half a percent over twenty bars, which is five times the morning brief's floor, because this one has
to clear twenty bars of drift rather than describe a single day. A threshold a market crosses by
standing still is not a threshold.

### What it deliberately does not have

**No timer.** The Arena's five minutes exist because trading under time pressure is part of what it
rehearses. Reading a chart is not, and a reader who wants to stare at this one for ten minutes is
doing the thing the feature is for.

**No second call.** Once the outcome is set the buttons are gone, and `DuelSession.answer` ignores a
second one rather than relying on the band drawing none. A reader who could call again with the next
twenty bars in front of them would be recording a prediction they did not make.

**No screen.** `ArenaSession`'s argument, unchanged and stronger here: the replay engine, its bar and
the plot are all in `feature:chart` and all tested there, and a second screen would be a second copy
of them drifting from the first. More to the point, the whole of this feature is *look at this chart
and say which way* — a rehearsal on a different-looking chart rehearses the wrong thing.

### The future is never in the series, by construction

The chart in a duel is in replay, stopped at `round.atBar`. What it draws is `ReplayState.visible`,
which is the same mechanism the replay mode has used since run E — so there is no path by which the
answer is on screen before the call. `Duel.judge` is handed the whole series because it runs *after*
the call, and it is the only thing in the feature that ever looks past `atBar`.

The reveal is `replayGoTo(round.resolveBar)`, and it happens **after** the record write returns, not
before it.

### The request is a boolean, and that is a fix rather than a style

The Arena carries its challenge across the navigation and then checks the arriving chart is long
enough for it. That check can fail for ever: the window was measured against the **previous**
instrument's series, so a shorter one leaves a request that never starts — a tap that did nothing,
with nothing said.

The duel does not carry a round. It carries a boolean, and re-reads the round from whatever chart is
now loaded. It is the same round: `Duel.roundFor` picks the instrument from the date alone and only
the bar inside it depends on how much history came back. And `DuelTest` asserts, across every series
length where the window only just fits, that the resolving bar is inside the series the caller
loaded — so once there is a round at all, it fits the chart in front of it.

### One round a day, and the refusal is said out loud

`DuelStore.answer` reads the stored day and writes the counters inside a single `edit` — which is
what makes the guard real, because two taps arriving together would otherwise both read «not
answered» and both write. A record that could be padded by tapping «up» five times on one chart is
not a record.

It returns **whether it recorded**, and the band prints «امروز را قبلاً جواب داده‌اید» when it did
not. A silent refusal is indistinguishable from a counter that stopped working, which is the same
class of fault as an alert that reads as armed and cannot fire — the failure this run has now
removed four times.

One thing the store deliberately allows: a day *earlier* than the stored one. Refusing anything not
strictly later would leave a reader whose phone corrected its clock locked out until the calendar
caught up.

### And no rate under the floor

`DuelRecord.rightPercent` is null below `Duel.MINIMUM_ROUNDS` judged rounds — `MyWeek`'s rule, with
`MyWeek`'s shape and for `MyWeek`'s reason. The band has no branch that invents one; it prints the
count and says a rate needs more than this to mean anything.

The denominator is **judged** rounds, not played ones. Three right out of five judged is sixty
percent even when four more were set aside, because the four the market did not answer are not
losses.

### The boundary that cannot be tested

`DuelTest` asserts both sides of `FLAT_PERCENT` and writes down why it does not assert the middle: a
close of 100.5 against 100.0 comes back as `0.4999999999999858` in binary floating point, so a test
claiming to hit the floor exactly would be asserting about the arithmetic rather than about the
rule. The rule is `< FLAT_PERCENT`; the exact boundary is not a state a market can reach.

### What landed

`Duel.kt` and `DuelTest` (18) in `:chart-core`; `DuelStore.kt` and `DuelStoreTest` (8) in
`:core:datastore` — with `DuelTally` as that module's own type, because a preferences store that
dragged in the chart engine would be on every gateway's classpath; `DuelSession.kt`, `DuelChrome.kt`
and `DuelSessionTest` (6) in `:feature:chart`; the pending→navigate→`enterReplay`→`replayGoTo` dance
in `CoineProApp`, mirroring the Arena's and deliberately not sharing a helper with it — the two pick
by different arithmetic, one needs a paper-book mark and the other does not, and a helper taking
both would be a helper with a boolean in it.

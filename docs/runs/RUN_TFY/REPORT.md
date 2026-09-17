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

---

# PHASE Υ — the first five minutes, and the screen a reader lands on

**Version 4.92.0.** Seven items, no device work owed.

## The shape of the change

Phase Φ made the markets surface hold every symbol the venue serves. Phase Υ is about the two
moments either side of it: the four minutes before a reader has seen anything, and the screen they
land on afterwards.

Three of the seven items are new screens (U1, U2, and the pulse row of U3). Three are rearrangements
of surfaces that existed and were in the wrong shape (U5's tabs, U6's list picker, U7's bar). One —
U4's ticker — is a new row over a feed the app already polls.

## What replaced what, and why

### One strip of tabs where there were two (U5)

The markets screen carried a **category tray** — همه · کریپتو · فارکس · فلزات · دیده‌بان — and a
**lens row** under it — داغ · بیشترین رشد · بیشترین افت · ارزش معاملات. They composed, which was the
design, and a reader had to understand that before either was useful. Two filled trays over a list
is a lot of chrome to ask one question.

`MarketsPage` is seven underlined tabs: برتر · پرطرفدار · دیده‌بان · برنده/بازنده · حجم · فارکس ·
فلزات. Some carry a family and some carry an ordering, and the fact that those are different
mechanisms underneath is an implementation detail nobody has to learn. Underlined rather than
filled, because with seven across a phone the label that reads *where you are* fits and the pill
that reads *filter applied* does not.

The rule that survived intact is **absent, not empty**: `offeredPages` drops the three ordering tabs
on a platform with no ticker route and the family tabs on a catalogue that holds no such family.
That rule is the one this screen already had, and it is there because a tab that opens onto
«بازاری با این نام پیدا نشد» teaches the reader that the app is broken rather than that the data is
elsewhere — which is exactly what was reported when the tray was five fixed tabs.

The sortable column headings did not move, so «only the ones that are up» — the thing the two
one-sided lenses answered — is one tap on the change column from برنده/بازنده.

### A pulse row that is honest about three of its four figures (U3)

This is the item the rest of the phase is measured against, because it is the one where the brief
asks for numbers this app does not have.

What arrives per symbol is a last price, a 24-hour change and a turnover. From that, one of the four
cells can be computed: the day's turnover across the venue's book, which `MarketPulse.turnoverIsVenueOnly`
marks as the venue's rather than the world's. The other three cannot:

* **Capitalisation** is price × circulating supply, and no supply figure exists on either wire.
* **Dominance** is one capitalisation over the sum of all of them — the same missing field.
* **Fear and greed** is a published index belonging to somebody else, carried by neither backend.

So the row draws four cells, three of them «—», and every dash opens a sentence saying which input
is missing. `BLOCKED.md` §5 names the three routes that would fill them, in order of how little they
cost.

The alternative — hiding the cells until the data arrives — was rejected for a reason worth stating:
a row that grew a field at a time as backends caught up would be a row whose shape changed under a
reader. `upsilon-pulse-full-phone-fa.png` is what the same composition looks like with all four
figures in it, and nothing about its geometry differs.

Breadth — the share of the board that is up today — is the one mood figure this app *can* measure.
It is printed under its own name in the explanation and never in the gauge's cell, because it is a
different number from the index and `MarketMood`'s own note has said since it was written why this
app will not compute one and print that name over it.

### A list picker where there was a chip row (U6)

The watchlist's lists lived in a `CoineProChipRow`. That read correctly with two lists and not at
all with six: the chips took the whole line, the live one scrolled out of sight, and there was no
single place on the screen that said which list you were looking at.

It is a **name and a caret** now, with «+» beside it, «•••» after it and «تحلیل» between them. The
five actions the brief names are all real: rename and delete were already in the manage sheet;
`WatchlistStore.duplicate` and `WatchlistStore.moveList` are new.

`duplicate` copies the **symbols and their order** and nothing else. The flags and the chosen columns
stay with the original, because a duplicate is nearly always the start of a variation — the same
markets, about to be pruned — and carrying a colour scheme set for a different purpose into it makes
the new list look finished before it is started.

`moveList` refuses a move into or out of the first seat, and that is not a limitation of the
function: `readLists` puts the default list at the head of every read whether it is stored there or
not, because it is the one list that always exists and the one every unqualified alert points at. A
move written and silently undone on the next read would be worse than an arrow that is not drawn.

«تحلیل» opens `ChartPanesScreen` with the list's markets in the reader's own order, through a new
optional `compare` query on the panes route. A comparison **wins over the stored arrangement and
writes nothing down**: a reader who asked to compare four markets is asking about those four now,
and the panes they arranged by hand are still theirs the next time they split a chart.

### A bar of five, and nothing lost behind it (U7)

`AppDestination.EXPLORE` became `RASAD("home")` and `IDEAS` became `COMMUNITY("ideas")`. **The routes
did not change**, which is the whole reason this is safe: every saved back stack, every deep link
and every `menuRoute` still resolves to the same screen.

Explore lost its seat because it was the markets screen with more on it, and after U5 the markets
screen carries the same content under its own tabs. Its three rooms — news, the calendar, the heat
map — are three pills above those tabs, and `NavigationDepthTest` now asserts that all four routes
are still one layer from the chart rather than trusting the pills to be enough.

رَصد takes the seat because the briefing is the thing no other terminal has. It left the bar in run
Ω2 attached to a home screen that was a dashboard, and the briefing went down with it, which was the
mistake.

### A ticker that opens the story (U4)

`MarketNewsTicker` takes plain values — an id, a title, a flag — rather than `feature:news`'s model,
because the markets surface has no business depending on that module and a ticker that did would
drag a reading page, an image loader and a body parser into a row that wants four fields (R6).

The tap opens **that headline** rather than the news list. `NEWS_PATTERN` gained an optional `story`
argument and `NewsScreen` an `initialStoryId`, seeded into the same state a press on a card sets and
resolved against the feed exactly like any other id — so a story that has aged out of the two-hour
window lands on the page's own «این خبر دیگر در دسترس نیست», which is the truth, rather than on a
blank list.

## The two narrowings, said plainly

**U2 asks four questions, not five.** The brief's list includes notifications. This app asks for that
permission at the moment it first has something to notify about, which is the ask people grant;
asking on screen two, before a reader has a single alert, is the ask people decline. That is a
reading of the line rather than an implementation of it, so the checklist row is ❌ for it.

**U6 draws neither a rank nor a market cap on the watchlist row.** The market cap is `BLOCKED.md` §5
again — the same missing supply figure. The rank is refused on arithmetic, and the arithmetic is in
`WatchlistColumn.DEFAULT`'s own note: a 393 dp phone gives the row 361 usable points, the leading
block spends 151 and the four default columns spend 208, which lands at 359. A 24 dp rank column and
its 8 dp gap is 32 points that do not exist. What falls off the far end of a right-to-left row is the
**left** end of the last column — the sign and the integer of the move — and that was a shipped bug
once. It is not being reintroduced to print a number the row is already in.

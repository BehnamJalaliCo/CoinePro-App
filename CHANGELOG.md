# Changelog

Every version of the CoinePro Android app, what is in it, and which commits made it.

The format follows [Keep a Changelog](https://keepachangelog.com); the numbers follow
[semantic versioning](https://semver.org). `docs/VERSIONING.md` sets out how a version name becomes
the `versionCode` Android actually enforces.

**On the `0.x` entries.** They are named retroactively. The work happened as one continuous run and
only `1.0.0` has ever been built and handed to anybody — so nothing below `1.0.0` is a release that
somebody once installed. They are here because a version is only useful if it names something you
can point at, and each of these names a capability that was not there the commit before. Where a
version fixed something rather than adding it, it is a `PATCH` bump, which is the scheme doing what
it is for.

---

## [1.38.0] — 2026-08-28 — The markets, on the home screen

A home-screen widget: the prices a reader watches, without opening anything.

It is worth building rather than being a nice extra for one reason — it is the only surface of this
product somebody sees *without deciding to*. A widget that is right every time they unlock their
phone earns more attention than a screen they visit weekly. No Persian-market trading app ships one.

### It follows the watchlist. There is no configuration screen.

Deliberately. A reader who has already starred the markets they care about has answered this
question, and asking it again — in a different screen, with a second list that drifts out of step —
is how a product ends up with two watchlists nobody trusts. Star a market in the app and it is on
the home screen. Where nothing is starred it shows the crypto majors, because a widget that says
"star something first" on the day it is placed is a widget removed the same day.

### Every size answers honestly

`WidgetLayout` is a pure function from the size the launcher reports to what fits, and it is the
part most likely to be wrong invisibly: a row too many is a row clipped in half on somebody's home
screen, on one launcher, at one font size, and it will never appear in a screenshot taken here. So
it is seven unit tests, including a sweep asserting that every size a launcher can report produces
a layout that fits inside it.

The header — wordmark, freshness, refresh — has to *earn* three rows before it is drawn. On a
two-cell-high widget it would cost a third of the glass, and somebody who put a price widget on
their home screen did not ask for a title bar. That widget gets a compact strip carrying the
freshness alone instead, because "when" is the one thing a price widget cannot honestly omit.

### It always says how old it is

A widget with no time on it shows yesterday's price exactly as confidently as this second's. Four
coarse buckets — now, minutes, hours, older — each true for as long as it is shown; precision finer
than that is precision a glance from arm's length cannot use, and it would be stale between draws
anyway. A refresh that *failed* says «آفلاین» rather than letting the age keep climbing quietly:
"an hour old" may be fine, "an hour old and we tried and could not" is a reason to open the app.

Never fetched is never called "now", and a clock that appears to have moved backwards admits it
does not know rather than giving a confident wrong answer.

### Three processes, and why the work is split

A widget is drawn by the *launcher's* process from a `RemoteViews` tree. The provider runs in a
broadcast receiver with about five seconds and no scope worth the name — it cannot open a socket,
wait for a quote and build a view. So `WidgetRefreshWorker` fetches and writes a snapshot, the
provider renders whatever is there, and the launcher draws it.

The fetch uses the **guest** price route, so the widget works signed out — a widget only members
can see is one most people who install this app never see working — and it keeps working after a
sign-out, which is correct: the price of gold is not the reader's private data.

WorkManager rather than the manifest's own `updatePeriodMillis`, which Android clamps to thirty
minutes and wakes the device to honour. Fifteen minutes while a widget is placed, cancelled
entirely when the last one is removed, so a reader without one pays nothing for this feature. A
failed fetch keeps the stored prices and marks them stale rather than blanking somebody's home
screen because one request timed out.

### The details that decide whether a widget feels made or generated

* **Prices are formatted at write time**, by `MarketNumberFormatter` — the same code every other
  surface uses. Re-implementing Latin digits, magnitude-aware decimals and a real minus sign inside
  a `RemoteViews` builder is how a widget ends up spelling a number differently from the app.
* **The direction is stored as a number, not a colour**, and resolved at draw time — so a reader on
  the red-up convention sees their own colours without a refetch.
* **Each row carries its own `data` URI.** `PendingIntent` compares intents by everything except
  their extras, so rows differing only in an extra all collapse to one — the classic widget bug
  where every row opens the first market.
* **The plate matches the app's cards**: a hairline and a radius, no shadow, no gradient, the same
  surface discipline the quality gate enforces everywhere else.
* **Light and dark** through `values-night`, and the reader's *in-app* theme choice deliberately
  does not reach it: a widget belongs to the home screen's light or dark, not to this app's.

### The link the widget opens is treated as hostile

`coinepro://` is unverified — any installed app may register it — so a ticker arriving from a
widget row is an arbitrary string from an untrusted sender that is about to become a navigation
argument and then a request path. It is shape-checked and refused rather than sanitised: a ticker
that needed cleaning was not a ticker, and quietly opening a *different* market than the link named
would be worse than opening none.

---

## [1.37.0] — 2026-08-28 — Open it with a finger

An app lock: a fingerprint, a face, or the phone's own passcode, asked for when the app opens.

### It locks the app, not the account

The tokens stay where they were, in the hardware keystore, and unlocking signs nobody in or out.
This is a curtain over a phone somebody else is holding and it answers exactly one threat — an
unlocked phone in the wrong hands, briefly. It is not a defence against somebody with the device
and time, and the file says so rather than letting a reader believe their balance is safe on a
phone they lent out.

It is the other half of the eye that hides the balance behind six dots: that one hides a number
from a shoulder, this one hides the whole app from a hand.

### No new passcode

`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`, both. A reader with wet hands, a cut finger or gloves has
to be able to get into their own app, and biometric-only locks are the ones people uninstall over.
The fallback is the phone's own PIN — the same secret it already uses — so nothing here asks
anybody to remember a code this app invented.

### The grace period is the whole design

A lock that re-arms the instant the app leaves the foreground is a lock nobody keeps on. Copying a
code out of an authenticator, answering a message, screenshotting a chart — every one is a trip out
and back, and being challenged each time is what makes people switch the feature off. Thirty
seconds is free; longer than that and the phone was put down.

Measured on `elapsedRealtime`, not the wall clock: it is monotonic and counts while the phone
sleeps, and a grace period that could be extended by changing the time is not a lock.

### It never keeps anybody out

If the phone loses its ability to authenticate — the reader removed their fingerprints, a work
policy changed — the challenge is skipped rather than shown. An app that cannot be opened again is
a far worse failure than the one this guards against. And the lock screen carries a button, because
the prompt is dismissable and a locked screen with no way forward is the trap this kind of feature
usually ships with.

The policy is pure and is nine unit tests, including the ones that assert it does **not** challenge
— a lock that asks too often gets switched off, and a lock that is off protects nothing.

### The three phones that are not the happy one

A switch alone would be right on a phone with a fingerprint enrolled and misleading on every other
kind. Hardware but nothing enrolled gets a shortcut into the system's own enrolment; no sensor but
a PIN is told plainly that its passcode is what will be used; a phone with no screen lock at all
never sees the row, because the app cannot add a lock the device does not have.

`MainActivity` becomes a `FragmentActivity` — `BiometricPrompt` requires one, so its dialog can
survive a rotation mid-authentication. It is a subclass of what was there, so nothing else moved.

---

## [1.36.1] — 2026-08-28 — Drag the price gutter

The last gesture the chart was missing, and the one that answers two opposite complaints with one
control.

The auto-fit price range is right for reading a *price* and wrong for reading a *shape*. A market
that has moved half a percent all week fills the plot with a mountain range of noise; one that
gapped ten percent on Monday spends the rest of the week as a flat line in the bottom third. Every
terminal answers both by letting the reader drag the price gutter, and now so does this one — with
a double-tap on the gutter to put it back, sitting beside the double-tap on the plot that returns
to the live edge.

Three things had to be right and each is a test:

* **The middle does not move.** Widening about the wrong point slides the chart up the plot as the
  reader drags, which reads as the chart running away from them. On the log axis that means the
  *geometric* middle, which is a different number.
* **It is bounded.** A quarter to eight. Below a quarter the visible bars are four times the height
  of the plot; above eight the candles are a horizontal line. Both are reachable by accident.
* **The default is bit-for-bit what the chart drew before.** The range calculation was rewritten to
  take a factor, and a feature that silently changed every chart in the app by arriving would be a
  worse outcome than not shipping it. A test holds the untouched range to the old fit-plus-eight-
  percent exactly.

The gesture is confined to the gutter plus twelve density-independent pixels — the axis alone is
under the minimum tap target once its padding is off, and readers aim at the numbers rather than at
the edge — and whether a drag counts is decided once, at the down event. Testing the current
position every frame would let a finger that started on the plot wander into the gutter and begin
rescaling mid-drag.

The stretch is saved with the zoom and the pan position, so it survives a rotation like they do.

---

## [1.36.0] — 2026-08-28 — A log axis, a target you can drag, and a door to a person

### The price axis can be logarithmic

On a linear axis equal distances mean equal amounts of money. On a log axis they mean equal
*percentages*, which is what a trader actually reads: 100→110 and 1,000→1,100 are the same trade,
and the linear axis draws the second ten times taller.

It matters exactly where this chart was weakest. Bitcoin over two years spans more than an order of
magnitude; linear, the whole first year is a flat line pressed against the bottom of the plot with
every level and every trend line in it invisible. That is not a preference, it is a chart that
cannot answer the question it was opened for.

Two things had to be right beyond the arithmetic. The gridlines are a 1-2-5-per-decade ladder
rather than the linear one — feeding evenly spaced *values* to a log placement over 1,000–100,000
puts eight lines of ten inside the top fifth of the plot and leaves the rest empty — falling back
to the linear ladder under a decade, where the two axes are indistinguishable anyway. And the
headroom is multiplicative: a failing test caught that eight percent of a 100–10,000 span is 792,
which put the bottom of the axis at −692, a price with no logarithm, which sent the whole axis back
to the linear fallback and made the toggle do nothing at all.

### The target is a point now

The position tool drew entry, stop and a two-to-one target, with a comment beside the target saying
a reader who wants a different multiple drags the line afterwards. There was no third handle. The
comment described behaviour that did not exist and the tool drew exactly one reward, for ever.

The target is placed as a real third point on commit — still two taps, no extra work for the reader
— so it has a handle, `movePoint` already moves handles, and the label follows what was dragged
rather than continuing to claim 2R. Setups saved before this have two points and still draw, by the
same fallback the renderer and the controller both use.

### Support is one tap and reaches a person

Nothing in this app has ever put an AI between a reader and support, which in the corpus is the
worst-rated thing any app in this category does — AI-as-support averages 1.57★, the lowest of any
theme measured. But it was *inside* «ایمنی و نسخه», two taps down a row named after something else.
Reachable and honest are different properties and this product only had the second.

### On the fling

Momentum after the finger lifts is not here, and that is a decision rather than an omission.
`detectTransformGestures` offers no release callback and no velocity — getting one means
reimplementing multi-touch transform detection, which is a rewrite of a gesture that works, for
polish. The defect that actually made panning feel broken was the discarded sub-bar residue, and
that was fixed in 1.35.0. The reasoning is written into the file so the next person does not have
to rediscover it.

---

## [1.35.1] — 2026-08-28 — What a candle means, and where it came from

Four things the research corpus ranked and the last version did not reach.

### A rise is not always green

Green-for-up is the convention in Europe and the Americas and the opposite of the convention
across most of East Asia, where red marks a rise. A reader who learned the market on a Chinese,
Korean or Japanese terminal reads a green candle as a fall — instantly, without thinking about it.
Binance, OKX and Bybit all ship this switch. Getting it wrong is not cosmetic: it inverts every
candle, every percentage, every profit figure and every signal direction in the product at once.

Implemented by exchanging `buy` and `sell` on the palette rather than at any call site, which is
what makes it one line instead of a hundred: every direction colour in the app resolves through
those two fields, the chart's canvas included. A test holds the swap to being complete and
symmetric, because a *partial* one is the worst outcome available — a chart drawing rises in red
beside a percentage drawing them in green is two contradictory answers about whether the reader
made money.

The default stays green-up. Iran follows the Western convention; the switch is for the reader it
is not right for, not a coin toss.

### «کندل‌سازی»

The loudest accusation in Persian-language reviews of this category of app is that the broker
manufactures its candles. It is usually wrong and it was, until this version, unanswerable: a
chart that asserts a number with no provenance gives a suspicious reader nothing to check and an
honest operator no way to be believed.

Every candle gateway now names its venue — LBank for crypto, MetaTrader 5 by way of the master
account's own feed for forex — and the chart prints it under the canvas with the last bar's clock
time beside it. The claim becomes falsifiable: a reader can hold this chart against that venue's
own. The time is the *bar's*, not the request's, because a successful request that returns the
same bars is not new data and printing when the app last asked would be reassuring and false.

### Time to first candle

Load and render speed is the single largest sub-theme of chart complaints in the corpus — larger
than every missing feature put together. A budget nothing measures is a wish, so every chart load
is now timed and anything past 1,200ms is logged as a warning carrying the symbol, the timeframe,
the bar count and the elapsed milliseconds: enough to tell a slow network from a slow route from a
response that was simply too big. Measured to the state update rather than to the response,
because what matters is the interval a reader spends looking at an empty chart.

### Asking, and not asking

Disconnecting a venue asks first: it discards credentials the reader typed by hand, which this app
never keeps locally, and it silently stops every copy running from that account.

Deleting a saved layout or a price alert does **not** ask — it offers the thing straight back.
That is the same rule read the other way: a confirmation is a tax on everybody who meant it, and
it is only worth charging where recovery is otherwise impossible. Both of those are fully in hand
at the moment of deletion, so an undo restores them exactly and costs the reader who meant it
nothing.

### The price alert nobody heard

It shipped on a channel at Android's ordinary default importance, which means it arrives in the
shade with no sound. A price alert is the one notification in the whole product the reader asked
for **by name** — they opened a screen, chose a market, typed a number and said tell me — and it
was the quietest thing the app sent. In the corpus that is the single most common shape of "I
never got my alert": the alert fired correctly and nobody was told.

Correcting it is not a one-line change, because Android takes a channel's importance only on the
call that creates it and ignores it on every call after. That is deliberate and right — an app
that could raise its own volume would — but it makes a channel id a one-way door. So channel
versions are now per category rather than one shared suffix: the price alert moves to `_v3` and
everything else stays on `_v2`, because bumping a shared suffix would reset every category's
sounds and vibration to fix the default on one. The superseded id is deleted rather than left in
the reader's list doing nothing, and a test pins every id — a diff there means somebody's settings
are being reset, which is a decision rather than a refactor.

---

## [1.35.0] — 2026-08-28 — The corpus, the questions, and the length of an average

The research agent's report landed and its findings reorder what was left. It read 52,324 Apple RSS
reviews, 11,391 English Play reviews and 5,594 Persian-script ones for the apps this product is
judged against, and the ranking it produced is not the ranking that was queued.

Three of its findings changed what got built here:

* **An explicit dark/light control is the most-asked-for thing in the Persian corpus** — 11.9% of
  requests, ahead of chart features (10.7%) and speed (6.5%). The app followed the system and had
  no switch, on the reasonable argument that the phone already has one. That argument loses to the
  data: a trading app is the one app somebody wants pinned dark while their phone stays light.
* **Negative reviews split roughly 70/30 broken-versus-missing.** People are not starved of
  features; they are worn down by things that do not answer. Every silent action in this app is on
  that side of the split.
* **Chart state surviving backgrounding and rotation is the second-largest chart complaint**,
  after load speed and ahead of every feature request.

### Nothing said anything back

Before this version, **no successful action anywhere in the app produced any feedback at all.**
A saved layout, a created alert, a copied fingerprint — each did its work in silence, which is
indistinguishable from a tap that missed. `CoineProToast` is a host at the top of the tree that any
composable reaches through a `CompositionLocal`, so a two-second sentence does not need a
`Scaffold` and a `SnackbarHostState` threaded to seventeen screens. One message at a time and the
newest wins: a queue would show a reader three messages about things they have already left.

### Nothing asked anything either

There was not one `AlertDialog` in the whole product, and the profile screen made that a hazard
rather than a stylistic gap: «خروج از حساب» sat one row above «حذف حساب», both were a single tap,
and the first silently discarded every session on both backends and cleared the stored name and
face. `CoineProConfirmDialog` asks first, and the dismiss button is the wider of the two so a thumb
aimed between them lands on the harmless one.

The rule for when to use it is written into the file, and the half that gets forgotten is the
second: **do not** ask when the action is reversible. A product that confirms everything has taught
its readers to tap «تایید» without reading.

### The theme

Three values — follow the phone, always dark, always light — stored on the device rather than in
the profile, because a reader who pinned the app dark did not ask for it to go light again because
they signed out. The picker is a sheet with a swatch per option showing the stage colour that
choice produces, and the swatches read the palettes themselves rather than `CoineProColors.Stage`,
which would have drawn three identical discs in whatever theme is currently running.

### The chart

* **Zoom and pan both threw away what they could not spend.** Pan is quantised to whole bars and
  zoom to a whole bar count, and each frame's remainder was discarded. That is invisible at three
  pixels a bar and completely broken zoomed in: at thirty pixels a bar a fourteen-pixel drag
  rounded to zero, so a slow drag moved the chart *not at all* while a fast one jumped. Same at the
  zoom floor, where `14 / 1.02` rounds back to 14 and a pinch did nothing. Both now carry the
  residue across frames.
* **The viewport survives rotation and process death.** Two integers — how far zoomed, how far
  panned — in saved state, applied on the same composition pass rather than in an effect, so the
  chart does not draw once at the default and then jump.
* **A bar-close countdown** under the live price, ticking on the second and only when the reader is
  at the live edge of a live feed. The interval comes from the last two timestamps rather than from
  the timeframe label, so a feed that disagrees with its own label still counts down correctly, and
  a bar whose close is already past shows a dash rather than a negative number.
* **`loadMore()` had no caller.** History paging was written, tested and never invoked: a reader
  could pan back to the first bar of the first response and simply stop. The chart now asks when it
  gets within ten bars of the oldest one loaded.
* **Indicator periods were literals.** «EMA 20» was the only exponential average this app could
  ever draw. Twenty-one indicators now carry an editable lookback, with a stepper whose step scales
  — single bars to 20, fives to 50, tens to 100, twenties beyond — so 200 is eleven taps from 20
  and every value on the way is one somebody uses. A test holds every label against the number the
  maths actually used, because a fifty-bar average under a label saying twenty is a lie nobody
  looking at the screen can catch.
* **A note can hold a note.** `Drawing.text` had existed since the drawing engine was written with
  no way to set it: `text`, `callout` and `pricelabel` rendered the literal «یادداشت» forever, and
  `note` drew a bare circle. The sheet opens by itself the moment a text tool finishes placing.
* **Full screen.** The chart lived in a 280dp card under a header, a symbol wheel, a timeframe
  strip and a tool bar — about a third of the glass on a 411dp phone. It now takes the whole screen
  on request, with the timeframe strip floating at the bottom where a thumb is, and back leaves
  full screen before it leaves the chart. It does not force landscape: rotating is the reader's
  decision and the phone already has that control.

### Offline is a condition, not an error

Every failure in the app arrived as the same thing and every screen said the same sentence about
it. `NetworkStatus` reports whether the phone has a *validated* network — `INTERNET` alone is true
for a café captive portal — tracked as a set of networks rather than a boolean, so a wifi-to-
cellular handover does not flash an offline banner. It is drawn as one line at the top that takes
its own row and leaves by itself when the network returns.

### The text field

It had a value, a label and a keyboard type, which is four ordinary things short of a form somebody
can fill in on a phone: no reveal on a password, no error state, no supporting line, and no
autofill hint — so every reader with a saved password was typing it by hand. All four now exist,
and registration asks for a *new* password while sign-in asks for the saved one, which is the
distinction Android's autofill actually acts on.

### Notifications

The fifteen categories stay; what changes is that each section carries a one-tap "all off". The
corpus says the two loudest complaints are "too many" and "none at all", in that order, and almost
never "I want finer control" — so the common case is now one tap instead of fifteen. And the
promise the screen makes fifteen times is now tested exhaustively: every silenceable category,
turned off, is off, and turning one off silences only that one.

### The signals list

It asked the server for fifty and said nothing about the rest. The route takes a `limit` capped at
a hundred and no offset, so it genuinely cannot page — but it knows the total, and a reader who
scrolls to the bottom of a truncated list and finds no mark reasonably concludes that is all there
is. It now asks for the ceiling and names the shortfall.

---

## [1.34.0] — 2026-08-28 — Four specialists read the app line by line

The owner asked for it read end to end and the gaps extracted. Four ran in parallel — the chart
against TradingView, the visual scale, the information architecture, and functional gaps — and this
is the first pass through what they found. Their three loudest findings are the three things he had
already named, and in each case the cause was not what it looked like.

### The drawing tools

**They did not work for anybody, and there is no guest gate on the chart. There never was.** The
complaint was right about the symptom and wrong about the cause, and the cause is worse.

The chart screen holds seven bottom sheets — the tool rail, the indicator picker, the chart-type
picker, the drawing list, layouts, backtest, setup — all written, all compiling, all in the
screenshot suite. Exactly one line in the file ever assigned `sheet`, and it assigned `SETUP`. Six
of the seven were unreachable dead code. The tools were not broken; they had no door. There is a
tool bar under the timeframe strip now, carrying the counts, because *what have I got on this chart*
and *how do I take it off* are the two questions a reader has there and a menu answers neither until
it is open.

And the chart and the studio each built their own `ChartController` inside their own `composable`. A
`NavHost` composes one destination at a time, so they were never two views of one chart — they were
two charts. Arming a tool in the studio wrote to an object the chart could not see. Indicators,
chart type and a replay in progress went the same way, and the round trip disposed the chart's own
composition, so its viewport and drawings went too. One controller per symbol now, above the graph,
on the shell's scope rather than a screen's — a screen scope was cancelled the moment the studio
opened, so even a surviving controller came back empty.

Three rendering defects behind "it scores 10 out of 100":

- The **viewport was remembered against the series identity**, so zoom and pan were erased on every
  timeframe switch, every chart-type switch, and — because `ReplayState.visible` allocated a fresh
  `CandleSeries` on every read, and `CandleSeries` has identity equality — on every frame of a
  replay. A replay could not be zoomed at all.
- The **price axis** divided the padded range into five and printed whatever fell out: `2571.34`,
  `2578.85`, `2586.36`. Ticks come off the 1-2-5 ladder now, the grid draws on the same list, and
  the precision comes from the step rather than the magnitude — zoomed into a thirty-cent window on
  gold, every label used to round to the same string.
- The **time axis** printed `HH:mm` in UTC. A reader in Tehran saw London's clock under their own
  candles, and on a daily chart every label read `00:00`.
- Every **stroke was a raw pixel**. On a 3× phone a 1.4f wick is 0.47dp, thinner than the platform
  can draw honestly, while the same file's markers were density-scaled and came out right.

### The home screen, and where news lives

Home measured about 2,190dp for a real member — two and a half screens — with everything the app is
for below the first 736dp. The guest home was 4,390dp, of which the news was 1,760dp: forty per cent
of the page, at the very bottom, and there was nowhere to send them, because `market/news` is passed
null for a guest. The one reader being shown the most news was the only one who could not open a
news screen.

News is a place you go now: a slot in the home quick row, a guest screen over the public headline
route, and one teaser card where twelve cards used to be. The two market cards became one, capped at
six, starred symbols first, with a footer that says how many are not shown. The holdings card became
one line to the portfolio screen that already draws them properly. The subscription card appears
only in the week a plan is ending. **Home is 1,241dp** — and that is *with* the quick row and the
watchlist, which the reference render had been omitting.

Which is its own fix: the screenshot everybody reviews was not the product. It left three entry
points null and supplied an `openSignals` the shipping app has never passed.

### The scale

The source of «همه چیز گنده و بزرگه» is a comment in `CoineProType.kt` that says, in as many words,
"one step above Material's defaults across the board". The argument behind it is sound — Persian
does need more size than Latin, because its marks are small relative to the letter body. The mistake
was the size of the correction (Persian wants about one point; this carried two to three) and the
line heights, left at Material's *reading* ratios of 1.63–1.76 where dense UI runs at 1.30–1.45.
The two compounded: every two-line row paid the size once and the leading twice.

Reading text keeps Persian's point and loses the slack leading; the dense roles come back to where a
terminal puts them. With it: the button 56dp → 46 (105 call sites), the platform switch 56 → 40,
card padding and the gutter 20 → 16, the corner radius 22 → 16, the market row 80 → 63, the profile
avatar 112 → 80.

And the opposite correction, which matters more: this app was oversized in everything the eye reads
and **undersized in everything the thumb hits**. The watchlist star was a 26dp target, so was the
balance eye, and the refresh button at the head of every list was 34. Six controls keep their drawn
size and get a 48dp target — shrinking the type without this would have made the app look tighter
and feel worse.

The most terminal-grade change is the smallest: the price column is fixed-width and right-aligned,
so the decimal points line up. Free-width it aligned on the *first* digit, and `1.08`, `91,248.30`
and `3,147.62` ended up to 36dp apart.

### Fixed, from the functional read

- **The Markets tab rendered a network failure as «موردی یافت نشد»** — the empty-search copy — with
  no error, no retry and no pull target, on one of five bottom-bar destinations. The controller had
  been setting `error` since it was written and the screen never read it.
- **Nine authored English sentences** were being written into UI state by three AI controllers and
  rendered verbatim to a Persian audience — "AI Signal job expired on the server.", "Write a message
  before sending." Not exception text: sentences somebody wrote, in the wrong language, for the
  reader. And the market search error *was* exception text — `Unable to resolve host …` as product
  copy. All of it goes through `UiMessage` now, and the test that asserted on the exception's own
  message, pinning the bug in place, asserts the opposite.
- **The Markets tab had a watchlist tab and no way to fill it.** The star existed on search and on
  Home, and Home lists only what the platform quotes — two instruments on the forex side.
- **Every market row was mute to a screen reader.** `clearAndSetSemantics` wipes descendants, so
  TalkBack heard the ticker and never the price, the move, the range or the stale note, and the star
  was unreachable while working perfectly under a finger. The comment there reasoned correctly about
  the row's own click surviving and then made the identical mistake one node lower.
- **The broker and exchange connect forms lost everything on a rotation.** `remember`, not
  `rememberSaveable`, on the two screens with the most to retype. Passwords and API secrets stay
  unsaved, deliberately.
- **KYC read a failed status as "not started"**, so a reader with a submission already pending was
  invited to submit again.

---

## [1.33.0] — 2026-08-28 — The five that were still missing

The list I gave the owner at the end of 1.32.0, worked through — plus one correction to it.

### Corrected

- **RSI was never drawn over the candles.** I said it was; it is not. `ChartCatalog` has placed it,
  MACD and ATR in their own panes since the engine was built, and `73-chart-panes-fa` shows all
  three. The line I mistook for an oscillator on the other render is SuperTrend, which belongs on
  the price. Nothing to fix, and nothing was changed.

### Added

- **Indices are listable again, drawn as their country.** An index has no base to look up, so it
  fell through to the lettered token — which is why `SymbolArtwork` refused every one of them and
  US30, GER40, UK100 and JPN225 simply were not in the catalogue. They are drawn the way every
  terminal draws them now: US30 an American flag, DAX a German one, Nikkei a Japanese one. Nine
  indices, seven flags from the vendored set and two authored — TradingView publishes neither
  Germany's nor France's, and both are three plain bands, so they are drawn here in the *vendor's*
  palette rather than the flag's own, because one flag in true colours among twenty-seven softened
  ones reads as the odd one out. `HK50` stays out: the bauhinia is not something to approximate.

- **What each signal is worth right now.** `livePnlPercent` has been arriving on every signal since
  the feed was first read, and no screen has ever drawn it — so a reader asking whether a call was
  working had four prices and their own arithmetic. It is the same pill the market list uses. Where
  the server sends none it is computed, signed by *direction* rather than by price, because a sell
  that has fallen is winning and `(price − entry) / entry` paints it red. Tested, because that is
  exactly the line somebody writes the wrong way round.

- **An empty screen that reads as deliberate.** `CoineProEmptyState` — a mark, the fact, what would
  fill the screen, and at most one action. The signals list, the news list and the calendar all
  showed a single grey sentence on a black screen, which cannot be told apart from a screen that
  failed to load. The signals hint is per tab, because «فعال», «اخیر» and «بسته‌شده» are empty for
  three different reasons and one sentence would be wrong twice.

- **The account's own curve under its balance.** A hero number says where the account is and nothing
  about how it got there. The line comes from the portfolio's closed trades — the same history the
  portfolio screen charts — and waits for five points, because two make a straight segment that
  reads as a trend with none of a trend's evidence. Nothing is padded or interpolated: an invented
  line under a balance is a claim about somebody's money.

### Fixed

- **The index logo shipped as an infinite recursion.** Its fallback called the coin logo, which
  routed straight back to it, so an index with no flag was a `StackOverflowError` rather than a
  token — and every index *did* lack one, because the flag table is keyed by currency (`USD`,
  `GBP`) and an index has a country (`US`, `GB`). Both halves fixed: the seven country codes added,
  and the fallback now draws the token directly instead of returning to the function that called
  it. A test holds the two tables against each other.

- **Three fixture signals disagreed with their own prices.** XAUUSD reported 0.61% where its entry
  and quote make 0.18%, and the BTC *short* reported −0.42% where a fall from 92,100 to 91,248 is
  +0.92% — a losing figure on a winning trade. Nobody had caught it because no screen drew the
  number; the first render that did put it directly above a progress bar computed from the prices,
  so the screen contradicted itself twice over. Fixture figures no screen reads are figures nobody
  checks.

---

## [1.32.0] — 2026-08-28 — The polish pass: touch, gesture, privacy and the chart's legend

A line-by-line and pixel-by-pixel review of every screen against what a reader would compare this
app to. Nothing here is a new feature; all of it is the difference between an application and a
screen that shows the right numbers.

### Fixed

- **The chart's legend was written over its own candles.** Five rows of coloured text — OHLC, the
  moving average, the bands, the SuperTrend, a pivot — drawn straight onto the plot, with every
  stroke that crossed a glyph taking a bite out of it. On a busy chart it stopped being readable at
  all, and it was the centre of the app's most important screen. It sits on a plate now: the stage
  colour at 82%, sized to the block and drawn before it. The OHLC line is *fitted* rather than
  truncated — the separator tightens, then the labels go, and only then does it fall back to the
  close alone — because «O 2571.2  H 2575.7  L 2570.1  C 2…» cuts off the one number a reader came
  for.

- **Two of the five navigation tabs never changed shape when selected.** Home, Signals and AI swap
  to a filled weight; Markets and Chart borrowed glyphs that had no filled weight, so on those two
  the entire selection was a shade of grey. `build-nav-icons.py` now derives both — Markets from
  Phosphor's published pair, Chart by solidifying TradingView's candlestick the same way the other
  four are solidified.

- **And the tool that derives them was measuring nothing.** Its bounding boxes were "every number
  in the path data taken as alternating x and y", which is true only of absolute path data; the
  candlestick is written in relative commands, so every box was nonsense and the counter test found
  no counters. It did not fail — it returned the outline it had been asked to fill. There is a real
  path walker now, run over the whole path in one pass so a contour that opens with a relative
  move-to is measured where it actually sits, and each surviving contour is re-anchored to the
  absolute point the walk found it at. The five existing drawables regenerate byte-for-byte
  identical, which is the proof that the fix changed only what was broken.

- **A "go there" caret pointed back.** The markets screen's open-signals strip and the chart's studio
  row both used the left caret, which is auto-mirrored — so in Persian, the app's default, it turned
  round and pointed away from the screen it opens. Both now use the forward chevron the profile rows
  already used.

- **The replay bar's two transport buttons were not a pair** — a bare caret for one bar back beside a
  skip glyph for one bar forward, which reads as two kinds of control rather than one control's two
  directions. Both are the skip glyph now.

- **Every forex row in the market list ended in an ellipsis.** «دلار آمریکا / ف..», «پوند انگلیس …» —
  a column of cut words that says nothing and looks like a rendering fault. A pair's list line is the
  base currency alone now; the ticker above it already spells both legs out and the two flags say it
  again. Search still matches on the full description, so «فرانک» still finds USDCHF.

- **The market list breathed as prices landed.** A row with no quote yet was shorter than its
  neighbours, so the list resettled under the reader's thumb as the feed filled in. Both market rows
  hold a minimum height now.

- **The markets screen had grown its own percentage pill** — a flat alpha over the move's colour
  rather than the tint formula computed against the surface behind it — so the same figure was a
  slightly different green there than on Home, one tap away. It uses the shared pill.

- **The screenshot gate was showing a screen the app cannot produce.** Its market catalogue skipped
  the artwork filter the real gateway applies, so US30, GER40, UK100 and JPN225 appeared as lettered
  grey discs — the exact thing the owner's rule forbids, in the render that exists to catch it. The
  fixture filters like the gateway now.

### Added

- **The app answers the finger.** `CoineProHaptics`, wired into the segmented control, both buttons,
  the brand button, every market row, the star and the profile rows — so it arrives everywhere
  without a screen having to ask for it. Three weights and no more: a tick for a choice that
  changed, a heavier one for something done that would want undoing, and a doubled one for a
  refusal, because "it worked" and "it did not" must never feel the same. There is no in-app switch:
  Android has one and `HapticFeedback` honours it.

- **Pull to refresh**, on Home, Markets, Signals, Activity, News and the calendar. Every one of these
  already had a refresh button and every one of them ignored the gesture a person watching a price
  actually makes. The buttons stay — they say *when* the data is from, which a gesture cannot — and
  both call the same function.

- **A balance the reader can put away.** The eye beside «دارایی کل» hides the total, the day's
  change, and every holding's value *and quantity* — hiding the total while printing the three
  figures it is the sum of is a curtain with a hole in it. Masked, the change line loses its green
  as well, since a green row of dots still tells the person behind you how the day went. The mask is
  a fixed six dots whatever it covers, because a mask that tracked the real length would leak the
  magnitude. Remembered across launches and cleared on sign-out.

- **The trade, drawn.** Every signal row carries a track from its stop to its target with the entry
  notched and the live price marked. The four prices were already there and the reader was left to
  do the subtraction that turns them into the only thing they wanted to know. The axis is the trade
  rather than the price — zero is always the stop and one always the target — so a long and a short
  are the same picture, and it is forced left-to-right because a number line is not a sentence.

- **Glyphs on the buttons that were choices.** Both shared buttons take an optional leading icon, and
  Home's three pills — which differed only by one word — now differ by a picture as well. The signal
  pill takes the sparkle rather than the candlesticks, because it opens the AI studio and the pill
  beside it was already showing candles.

- **An icon on every profile row.** Eight lines of identically weighted text is a list a reader reads
  all of, every time. The destructive row's glyph takes the refusal colour with its label.

- **The price flash**, on every shared market row: the row tints green or red for a moment when its
  price moves. It was built for this app months ago and never called from anywhere.

- **Controls that were only punctuation.** The quiet-hours «+» and «−» were bare gold glyphs with a
  target barely wider than themselves, and «حذف» on an alert row was red prose beside a switch —
  which reads as a warning label about the switch rather than as a second control. Both are real
  controls now: a 36dp disc for the stepper in the neutral surface the rest of the app uses, and the
  bin glyph, still in the refusal colour, with a target around it. Gold stayed out of both, because
  gold means the screen's primary action and a settings page's is not "add an hour".

### Changed

- The markets screen's tab strip was a byte-for-byte copy of `CoineProSegmentTabs` — same tray, same
  raised block, same weights — and that is how it ended up as the one strip in the app with no tick
  under a finger. It uses the shared one.
- The profile hero has more air above it than below. There is no top bar on that route, so a 112dp
  avatar sixteen points from the status bar read as an element that had been cut off.

---

## [1.31.1] — 2026-08-28 — LBank's mark, traced rather than drawn

### Fixed
- **The LBank mark was still wrong, and the reason was the method.** The first version was their old
  blue circular logo out of an archive. The second was the right mark but *hand-fitted* — the two
  lobes solved as circles from three rows each and the waist between them guessed at with cubics.
  It was close and it looked wrong: the neck too thin, the notch on the right too shallow. A shape
  like this is not made of primitives anybody can name, so fitting named primitives to it will
  always be an approximation that reads as one.

  It is machine-traced now. Every pixel classified, the ink split into components so the frame and
  the mark inside it are separate, each boundary walked on the pixel grid, the staircase rounded
  with three rounds of Chaikin corner-cutting, and the result simplified at 0.9px — against a source
  of 294 pixels and a mark that ships at 66, which is four times finer than anything that can be
  seen. Both colours are sampled from the file.

### Added
- **`scripts/design/trace-logo.py`**, because this will happen again. A company that publishes its
  mark only as a raster is the ordinary case, and reproducing one by eye is the thing that just
  cost three attempts. The script and the reasoning behind each step are in its own header.

---

## [1.31.0] — 2026-08-28 — The step the card asked for, with nowhere to take it

### Fixed
- **A reader could register on an exchange, fund it, and then find no way to submit their UID.**
  Step three of the membership card has always said «شناسهٔ کاربری (UID) حساب صرافی را در اپ وارد
  کنید», and `feature:membership` has always had the screen for it — but nothing in the app ever
  routed to it. The whole arrangement turns on that step. It is now reachable from the profile,
  under «عضویت و شناسه‌ی صرافی».
- **The exchange picker would have been empty for exactly the people who need it.** The list of
  exchanges that accept a UID comes from the public membership route, which only the guest home
  polls — and a signed-in reader never renders that screen. The membership screen now asks for the
  terms itself.

### Notes
- **Both referral links come from TradeYar and always have.** Verified today against the live route:
  `api/v1/public/membership` serves `lbankReferralUrl`, `ourbitReferralUrl`, the deposit threshold
  and the Persian notice, and the app compiles none of them in — a link one release out of date
  does not fail visibly, it silently costs the reader their membership.
- Both links resolve. LBank's redirects to `lbank.com/signup?icode=…`; Ourbit's opens its register
  page with the invite code attached.
- **Which exchanges accept a UID is also the server's answer** — today `["lbank", "ourbit"]`, a
  deliberate superset of `copyTradeExchanges`, which is `["lbank"]` alone. Ourbit earns membership
  and every signal; it is never traded on. That distinction is now visible in three places: the two
  labelled buttons on the card, the exchange picker here, and the copy-trading note.

---

## [1.30.1] — 2026-08-28 — LBank's mark, the current one

### Fixed
- **The LBank logo was their old blue circular mark.** It came from a logo archive, and an archive
  is what a search finds rather than what a company is using. The owner supplied the current icon —
  a black squircle, a yellow field pinched at the right, and the black bone through the middle that
  makes the whole thing read as a B — and it is now measured from that: the silhouette scanned row
  by row, the two lobes solved as circles from three rows each, and both colours sampled from the
  file rather than guessed (`#FFD900` and `#161616`).
- The artwork is **trimmed to its own bounds**, where the source icon carries about a tenth of
  padding. Keeping that padding would have made LBank's button read as the quieter of the two on a
  card whose whole point is that the two choices are equal in weight and different in kind.

### Notes
- Worth recording as a habit rather than an incident: for a mark this app draws, the company's own
  current file beats any archive, and where none is published — Ourbit — measuring the raster beats
  tracing it by eye. Both marks in this release were built that way and both are documented with how.

---

## [1.30.0] — 2026-08-28 — Notifications worth keeping on, and two exchanges told apart

### Added
- **Fifteen notification categories instead of one switch.** New signal, target reached, stop hit,
  signal closed; copy opened, closed, failed; price alerts, starred-market moves, news, calendar,
  AI setups; account security, account status; and offers — the last separated from everything else
  and **off by default**, because that is the switch people go looking for and burying it is how an
  app loses the reader's trust in the whole screen. The shape is the one Binance, OKX, Bybit, Kraken
  and Coinbase all converge on; converging with them means somebody who has used an exchange app
  does not have to learn a new idea to find the switch they came for.
- **Each category is its own Android notification channel**, in four groups, with importance chosen
  per category rather than uniformly. That gives the reader the operating system's own per-kind
  control — long-press a notification to change *that kind's* sound, vibration or importance — which
  is better than the in-app sound picker OKX ships and is one nobody has to be taught. The app's
  switch says whether to send it at all; the channel says how loudly. The single `market_events`
  channel is deleted rather than left as a dead entry in the system list.
- **Quiet hours.** Not one of the seven apps measured against has this; they all point at the
  phone's Do Not Disturb instead. That is a fair answer where the market closes at night and a poor
  one here, where the reader follows a market that never does — telling somebody to silence their
  whole phone is telling them to silence their alarm. A copy that opened or failed, and anything
  about account security, still come through: money moved.
- **Price alerts that need no account.** Six conditions — above, below, up %, down %, 24-hour change
  over and under — with an explicit repeat rule of once, once a day or every time, evaluated on the
  device against the public feed. **Of Binance, Bybit, Coinbase, OKX, Kraken, eToro and TradingView,
  not one lets somebody set a price alert without signing in**, and neither do CoinGecko or
  CoinMarketCap. The screen is honest about the cost: they fire while the app is open and otherwise
  when Android next runs its periodic work, never more often than every quarter of an hour.
- **`CoineProBrandButton`** — a button that carries somebody else's mark, drawn with `Image` and
  never `Icon`, because a tint on a company's logo produces a mark they do not have.

### Changed
- **The two exchanges are told apart, with their own marks on their own buttons.** LBank in gold
  with «اگر می‌خواهید سرویس به‌جای شما معامله کند» over it, because it is the only one copy trading
  runs on; Ourbit beneath it with «اگر فقط سیگنال‌ها را می‌خواهید و خودتان سفارش می‌گذارید» — a real
  choice rather than a lesser one, for the reader who wants every signal and will place their own
  orders. Two identical gold buttons differing by one word is a choice made by reading; with the
  marks it is a choice made by recognising.
  - LBank's mark is their own published vector, circle only.
  - Ourbit publishes no vector, so theirs was **measured** off their 256px icon rather than traced:
    the silhouette scanned row by row, the vertices solved where the edges meet, the green sampled
    from the file. The ring is open at the top with the rhombus in the gap — drawing it as a closed
    ring with a dot above would be a different mark that looks similar at a glance.
- **Google's button carries Google's G**, the four published paths, uncoloured by us, on the
  reading edge.
- **The server's three flags are derived from the fifteen switches** rather than mirrored, and the
  derivation has one rule worth knowing: the server sends target, stop and closed under one flag, so
  turning it off to satisfy one would silence the other two *at the source*, where the app cannot
  get them back. One wanted update keeps the flag on and the unwanted ones are dropped on arrival.
  The screen says which kind of "off" each switch is.
- **The alert schedule exists only while there is an alert.** A worker waking every quarter of an
  hour to read an empty list is a battery cost with no possible benefit.

### Notes
- Everything above was designed against what Binance, Bybit, eToro, TradingView, Coinbase, OKX and
  Kraken actually ship, read from their own documentation. Two ideas are taken almost whole —
  Kraken's zero-configuration alert on starred markets, and the industry-standard category split —
  and two are deliberately not theirs: quiet hours, and alerts without an account.

---

## [1.29.4] — 2026-08-28 — The audience is live

### Notes
- **Google sign-in's server side is done, and it was confirmed rather than accepted.** TradeYar's
  `auth/methods` now serves
  `1033486124390-07nqc4h9j1agsrcrpvq7cgsa5k6evced.apps.googleusercontent.com`, and that id answers
  `redirect_uri_mismatch` at Google — read from this repository against the live route, not taken on
  report. With the Android client and the release fingerprint already verified, every piece up to
  the moment Credential Manager opens its sheet is now known good.
- **The one step left is not code: tap the button on a phone.** No release is needed; the audience
  has always been read from the server at runtime, which is the whole reason this was fixable
  without one.
- **Their answer improved on the ask**, and it is worth recording why: the two ends *cannot*
  diverge, because both read one variable — `auth/methods` serves its first element and
  `auth/google` uses the same list as the accepted `aud`. That makes the class of bug impossible
  instead of fixing one instance of it, which is the better of the two shapes.
- **A fair correction taken.** The client ids in the request document were abbreviated with
  ellipses, which made the probe unreproducible against two of the three rows. They are written out
  in full now, in both documents.

---

## [1.29.3] — 2026-08-28 — One field, on TradeYar's side

### Notes
- **All three Google-side pieces now exist.** The replacement Web client `…-07nqc4h9…` answers
  `redirect_uri_mismatch` to the probe, which is the healthy answer — Google resolved it and only
  objected to the throwaway redirect URI. The Android client and the release fingerprint were
  already verified. Nothing is missing in the Google project.
- **What is left is one field on TradeYar**, and it cannot be worked around from here: their
  `auth/methods` still returns the deleted `…-nnr0l8q2…`. It has to return the new id *and* verify
  ID tokens against the same value in `auth/google` — both, or a valid token is refused at the far
  end. `docs/REQUEST6_TRADEYAR_GOOGLE_CLIENT.md` is written to be forwarded as it stands, with the
  probe, the two places to change, and the note that no client secret is involved in verifying an
  ID token.
- **No client-side workaround was built, deliberately.** The app could compile in an audience of its
  own, and the token it minted would then be refused by a server verifying against a different one
  — the same failure one layer deeper and harder to read. The audience belongs to the server.

---

## [1.29.2] — 2026-08-27 — The audience had been deleted

### Notes
- **Google sign-in is one console step from working, and the step is not the one anybody was
  taking.** Established by asking Google's authorize endpoint about each client id rather than by
  reading files, because it answers differently for a client that exists, one that never existed and
  one that was removed:
  - the new Android client `…-aji26kov4…` answers `redirect_uri_mismatch` — **it exists and is
    valid**, and the `google-services.json` of 27 August confirms it carries this repository's
    release fingerprint exactly;
  - the web client `…-nnr0l8q2…`, which is the audience the app sends and TradeYar serves, answers
    **`deleted_client`**;
  - a made-up id answers `invalid_client`, which is what makes the other two trustworthy.

  So the Android half is now correct and Google still refuses, because the audience no longer
  exists. What is left is to create a **Web** OAuth client in the same project and give its id to
  the TradeYar backend — both as `google_client_id` in `auth/methods` and as the `aud` its
  `auth/google` route verifies against. `docs/PLAY_LISTING.md` carries the steps and the one-line
  probe, which is worth keeping: it settles in a second a question that files cannot answer.

### Changed
- `app/google-services.json` replaced with the 27 August file. It is the same project, app and API
  key, and it now carries the Android OAuth client — so push is unaffected and the Firebase side is
  finally complete. The web client's type-3 entry is absent from it, which is the same deletion seen
  above; nothing in the app reads that entry, because the audience comes from the server.

---

## [1.29.1] — 2026-08-27 — Two causes, one message

### Fixed
- **The Google sign-in failure now names both things it can be.** Credential Manager reports "the
  signing certificate is not registered" and "there is no Google account on this phone" with the
  same exception, and nothing inside the app can tell them apart. The message named only the first,
  which was right while the console was known to be missing an Android client and becomes wrong the
  moment it is not: somebody with no Google account would be told the app is broken and would never
  guess that adding an account fixes it. Naming both costs nothing and is the only honest wording.

### Notes
- A second OAuth client id in the project was reported: `…-aji26kov4…`. The app uses
  `…-nnr0l8q2…`, the **web** client, which is what TradeYar serves as the audience — and the two
  being different is what a *correct* setup looks like, because an Android client is never sent by
  an app. `docs/PLAY_LISTING.md` records how to confirm the new one is of type Android, including
  the one test that is now meaningful: a freshly downloaded `google-services.json` should at last
  contain a `client_type: 1` entry. Nothing in the app changes either way.

---

## [1.29.0] — 2026-08-27 — A copy that refuses to be it

Three things: why Google sign-in has not been fixed by six downloads of the same file, what is
actually in the APK, and Google's own mark on the button that carries their name.

### Added
- **The app refuses to run if somebody re-signed it.** The release build bakes in the SHA-256 of the
  certificate that signs it — read from the keystore at configure time, so a genuine build carries
  its own fingerprint and cannot fail its own test — and checks it before a single screen is drawn.
  A repackaged copy gets a refusal that names what it found and says the thing that matters to
  somebody holding a trading app: do not sign in here. There is no "continue anyway".
  `AppIntegrity` is candid about the limits: Android will not install a modified APK under the
  original signature, so a changed CoinePro must be re-signed and this stops the copy that spreads —
  it does not stop somebody who patches the check out, and nothing running on the attacker's own
  hardware could.
- **`COINEPRO_EXPECTED_SIGNERS`**, the escape hatch that has to be set before the first Play
  release. Play App Signing re-signs uploads with Google's key; without the fingerprint from Play
  Console → App integrity, every store install would refuse to run. Both keys are accepted at once.
- **The install's own signing fingerprint, in the app**, under «ایمنی و نسخه», SHA-1 and SHA-256,
  with a copy button. Not a secret — it is derived from the APK — and it settles in one look the
  question that has cost days: which key is this phone's copy actually signed with.
- **`docs/SECURITY_HARDENING.md`** — what is in the APK, verified by unpacking it and reading every
  string; what protects it; and the part these documents usually leave out, which is what cannot be
  done at all and what to do instead.
- **Google's own G on the Google button.** The four published paths, uncoloured by us, on the
  reading edge of the button. A button that only says "continue with Google" is a claim in text; the
  mark is what a reader recognises before they have read anything, and it is what Google's sign-in
  guidelines ask for.

### Changed
- **The build residue is no longer packaged**: `DebugProbesKt.bin`, a map of the coroutine internals
  that only a debugger reads, and the Google libraries' `.properties` version stamps and `.proto`
  analytics schemas. The version stamps are the first thing anybody hunting a known vulnerability
  reads, and nothing in the app reads any of it.
- **The Google sign-in instructions are rewritten around what actually fixes it.** Six copies of
  `google-services.json` have now been supplied; five are byte-identical to each other and the sixth
  had no OAuth client at all, so nothing has changed in the console between the first and the last.
  It cannot: that file is read by Firebase for push, and **not by Credential Manager at all**. What
  Credential Manager needs is an **Android OAuth client** in Google Cloud → Credentials, with the
  package name and the SHA-1, in the project that owns the audience the app sends. Verified today
  that TradeYar's `google_client_id` is in the same project as this app's Firebase, so it is one
  client in one console — and nothing to download, because the audience is read from the server at
  runtime. `docs/PLAY_LISTING.md` carries the four steps.

### Fixed
- **The style gate's `println` rule matched `out.println` on a `PrintWriter`** and so failed the
  crash recorder — the one file in the app whose job is to make a crash reportable. It now matches
  only the bare stdlib call. It had passed in 1.27.0 only because the file was still untracked when
  the gate ran, which is its own lesson: `git add` before the gates, not after.

### Notes
- There is **no secret in this APK** and never was one to remove. Verified by unpacking
  `app-release.apk`: two backend addresses, the Firebase Android API key and the Google web client
  id — all three public by design, the key restricted by package and certificate — the published
  legal-page links, and obfuscated class names. No private key, no token, no `mapping.txt`.

---

## [1.28.0] — 2026-08-27 — Sign in wherever your account already is

1.27.0 moved sign-in to TradeYar, which was right, and stranded every account made before it, which
was not. This is that, fixed — plus two other ways the same flow could leave somebody outside.

### Fixed
- **An account made before 1.27.0 can sign in again.** CoinePro runs two independent user tables.
  Until 1.27.0 the app registered against CoinePro-FX; from 1.27.0 it registers against TradeYar,
  where a CoinePro account belongs. Every older account is therefore real, with a correct password,
  on a server the app had stopped asking — so TradeYar answered `TYR-001 Auth Invalid Credentials`
  and the reader was told, accurately from the server's side and falsely from theirs, that their
  password was wrong. Sign-in now asks TradeYar first and, **only when TradeYar says the credentials
  are wrong**, asks CoinePro-FX. A network failure, a rate limit or a 500 stops at the first server:
  none of those is evidence the account lives elsewhere, and a fallback sends the reader's password
  to a second host.
- **The session goes to the backend that issued it, and the shell opens on that backend.** A
  CoinePro-FX token written into TradeYar's storage is an app that believes it is signed in and is
  answered 401 by everything — which is the "it throws me back to the guest screen" symptom again,
  from the other direction. `EmailAuthSession` now carries its platform, and the shell is gated on
  the session of the platform on screen rather than on TradeYar's alone.
- **Every configured platform restores on launch**, not just TradeYar. Restoring one of two would
  have left a returning reader signed out of the app with a perfectly good session in storage.
- **The address is lower-cased before it is sent.** A phone keyboard capitalises the first letter of
  a field often enough that the same person registers as `Reader@…` and signs in as `reader@…`; a
  server comparing the local part exactly then reports a wrong password. There is no case in which
  a reader means two different accounts by two spellings of one address.
- **Signing out signs out of both backends.** «خروج» means leaving, and logging out of one would
  otherwise let the effect that follows a session move the reader silently onto the other.
- **Google sign-in's failure speaks Persian.** It was an English sentence in an RTL message box,
  which does not read as a note to a developer — it reads as the app being broken, full stop in the
  wrong place included. It now says what a reader can do, and keeps one clause of cause because the
  person testing this build is the one who can fix it.

### Added
- **A half-finished registration survives the process being killed.** Registration is two steps with
  a wait in the middle, and the wait is somebody leaving the app to open their e-mail — which is
  exactly when Android reclaims the process. The registration token used to live in memory, so they
  came back to a sign-in screen for an account that had never been created, tried to sign in, and
  were told the credentials were wrong. It is written down now, and the code screen picks up where
  they left off. The password is not stored, here or anywhere.
- Tests for all of it: what does and does not federate, that only a credential refusal reaches the
  second server, that home's wording is the one reported when both refuse, that registration is
  never made twice, and that recovery is asked of every backend — because a route that answers
  identically for a registered and an unregistered address cannot tell the app where to send it.

### Notes
- **Google sign-in still needs one thing from the Google console and no app change.** This build has
  no Android OAuth client, so no SHA-1 is registered and Google will not mint a token for it.
  Verified today: the audience the app sends is TradeYar's `google_client_id`, and its Google Cloud
  project is the *same* one as this app's Firebase project — so it is one fingerprint added in one
  console, not two. `docs/PLAY_LISTING.md` carries the fingerprint and the three steps.

---

## [1.27.0] — 2026-08-27 — The app, for everybody

Four things the owner asked for, and one they did not have to: sign-in now creates the right kind of
account, the guest experience *is* the app rather than a page in front of it, and everybody who uses
this product — signed in or not — has a profile with a face they chose.

### Added
- **A profile, and it is a real one.** `feature:profile`: a hero with the reader's avatar, their own
  name and one line about themselves, the platform and plan they are on, and the account rows that
  used to hide in a dropdown off Home's corner. It is the gold voice, because it is a screen about
  one thing and the thing is a person — and it is the one page in the app where no number moves.
- **An avatar composer.** A reader picks a **photograph** from their phone, or an **instrument** —
  five hundred and sixty crypto marks, the major currency pairs drawn as two flags, the four metal
  discs, all of it the same artwork the market rows use — or one of **ten marks the app draws
  itself**: a rocket, a bull, a bear, a candle, a diamond, a flame, a bolt, a trend line, a shield
  and a globe. Each one moves, once every few seconds, and every one of them stops dead when the
  device has animations turned off.
- **A ring around it**, in the colours this app already means something by: brand gold, analysis
  blue, the buy green and the sell red. Not a colour wheel — a ring is a small flag somebody plants,
  and the set they can plant should be made of things that mean something here.
- **`ProfileStore`**, and one rule with it: nothing in the profile leaves the device. Neither backend
  has a route for an avatar and this app is not going to invent one by uploading a reader's
  photograph to a trading server. The screen says so, including the part where a reinstall loses it.
- **`GuestMarketCatalogGateway` and `GuestCandleGateway`** — TradeYar's public routes wearing the two
  interfaces the signed-in app already builds against. That is the whole trick behind the guest
  work: the markets list, the search, the chart and every sparkline are unchanged, with no `if
  (guest)` anywhere inside them.

### Changed
- **The guest experience is the app.** Signed out no longer means a single scrolling page in front
  of a sign-in form: it is the same shell, the same bottom bar, the same several hundred markets, the
  same chart on the same candles, the same toolkit — journal, paper trading, NamaScript — and the
  same profile. Two tabs need an account, say so once, and show the real closed-signal record while
  they say it. Nothing is blurred and nothing is withheld to make a point. **به زور کسی رو ما ثبت
  نام نمی‌کنیم.**
- **Sign-in creates a CoinePro account, not a CoinePro-FX one.** `emailAuthController` and the
  unqualified `sessionController` are bound to **TradeYar**, so the mail arrives signed *CoinePro*
  and the account is filed in TradeYar's user table — which is where an account made in this app
  belongs. It was the forex product's, and that was wrong twice over: the wrong sender's name on the
  mail, and the wrong table underneath it.
- **The shell follows the session rather than a remembered preference.** This is the second half of
  the same fix and it is the half that showed up as "I sign in and it throws me straight back to the
  guest screen". The two backends are separate accounts; the shell reads one of them, chosen by a
  stored preference that on an upgraded phone still said CoinePro-FX. The new session was TradeYar's,
  so the first request came back 401, the 401 handler ended the session, and the app landed back on
  the guest screen — indistinguishable, from the outside, from a crash. If the platform on screen has
  no session and exactly one platform does, the app now follows it.
- **The top corner is the reader, not two words.** «ایمنی» and «خروج» were a pair of text buttons on
  every screen and neither is something anybody reaches for often. The corner is now the avatar, and
  safety, verification, alerts, sign-out and deletion are rows on the page it opens.
- **«جامعهٔ کوین‌پرو» is gone from the guest home.** A member count and a list of Telegram channels is
  a crowd shown to somebody who has not yet been told what the crowd is for, and it was taking the
  place of the market on the one screen where the market is the argument. The controller no longer
  spends a request on it either.
- **The toolkit's three signed-in cards are hidden from a guest** rather than leading to a 401 worded
  as an outage. Everything else on that screen is local to the phone and opens for anybody.
- **Signing out clears the profile.** The next person to open this app on this phone is not
  necessarily the same person.

### Fixed
- **Marks that were blank for part of their loop.** The trend line and the shield's tick were drawn
  *up to* the animation phase, so at the start of every cycle the avatar was an empty disc — and the
  screenshot caught it, because a render holds the clock at zero. Both are now whole at every
  instant and the motion decorates them instead of constituting them.
- **`PathMeasure.getSegment` drew nothing under the renderer**, which is how the above shipped
  unnoticed in the first capture. Replaced with a hand-walked polyline: a dozen lines, the same
  arithmetic, and no platform behind it to disagree with.
- **A ring of `NONE` tinted the artwork transparent**, so seven of the ten marks rendered as
  invisible discs in the composer's own grid. The unringed avatar now falls back to the brand gold.
- **The bull read as a dog and the bear as a mouse.** Both redrawn from the silhouette out: the bull
  has horns that leave the head and a muzzle that tapers, the bear has small ears set low and a
  light snout. The contact sheet is in `87-avatar-gallery-fa.png` and is the reason either was
  caught.

### Notes
- The avatar marks are drawn in Compose rather than taken from an emoji font. A licensed emoji
  cannot be redrawn at forty points without looking like a sticker pasted onto a trading app, and an
  animated GIF would mean a decoder, a cache and a frame loop for something that is forty pixels
  across.
- `AvatarRing.PREMIUM` is in the enum and not on the shelf. It is `#D4AF37` against the brand's
  `#D8A848`: side by side in a picker they are the same swatch, and offering it would also let
  anybody wear the ring the design system reserves for a subscription.

---

## [1.26.0] — 2026-08-27 — The rest of the app, and the debt

The design rule the owner set — content screens speak the gold voice, lists speak the terminal one
— now reaches the screens the last release did not, and the engineering debt that had been carried
since the handoff is paid down or honestly named.

### Added
- **Both voices as design-system components.** `CoineProPageHeading`, `CoineProHeroFigure` and
  `CoineProReadingRow` for the gold voice; `CoineProListHeader`, `CoineProSegmentTabs`,
  `CoineProColumnHeadings`, `CoineProDenseRow` and `CoineProRowDivider` for the terminal one. A
  screen now declares which voice it speaks by which components it reaches for, instead of every
  screen finding its own slightly different answer to the same layout.
- **`scripts/quality/check-kotlin-style.sh`** — a fourth gate, in CI and in the working agreement.
  It catches wildcard imports, `println` in shipping code, `Thread.sleep`, stray `TODO`/`FIXME`,
  tabs, trailing whitespace and files with no closing newline.
- **A gate that keeps every screen looked at.** The consistency check now refuses a feature module
  with no case in `ScreenshotRenderTest`. Five modules are listed as unrenderable with the reason —
  a WebView has no off-device renderer, a streaming screen's capture is one arbitrary frame, and a
  screen whose whole content is one account's server answer would have to fabricate the account.
- Render cases for the journal, paper trading and alerts, which had none.
- Tests for `core:security` and `core:navigation`, the two untested core modules. The keystore
  itself is not tested and cannot be — Robolectric ships no `AndroidKeyStore` provider — but the
  envelope either side of it is, which is the part that has actually failed in products like this.

### Changed
- **Signals** is the terminal voice: a compact header, status tabs, named columns, and two lines per
  row — identity and call on the first, the three levels labelled on the second. Dropping the levels
  would have made the list scannable and useless.
- **Signal detail** and **portfolio** are the gold voice: a heading over a fading rule, the one
  figure the screen exists to show at forty points, and three readings beside it. Signal detail
  gains the risk-to-reward ratio it never showed, computed from the first target rather than the
  furthest — the first is the one a reader is deciding against.
- **News, the economic calendar and activity** get the list header, their one action as an icon.
  Activity's eyebrow, headline and note collapse into a title and a subtitle: three lines of chrome
  before the first entry is three lines nobody reads twice.
- **Lessons and membership** get the gold heading.
- **A screen that draws its own heading no longer has one in the app bar too.** The bar keeps the
  way back and nothing else, which returns the fifty-six points the page's own heading needs.
- **Every module compiles against Java 17.** The library modules were on 11 while `:app` was on 17.

### Notes
- ktlint and detekt were deliberately not added. Both are Gradle plugins resolved from the plugin
  portal, and a build that cannot reach it would fail on formatting rather than on anything real.
  The shell gate covers the damage that is invisible in review; the rest is what a formatter is for,
  and is worth wiring the day one is.
- `baseline-prof.txt` is still hand-written. It cannot be generated here — `BaselineProfileGenerator`
  needs a device — and the file says so at the top rather than pretending otherwise.

---

## [1.25.0] — 2026-08-27 — The picked direction, built

The owner chose two of the three directions off the design canvas — «ب · طلایی» for the chart and
«الف · ترمینال» for the markets list — and settled the rule for the rest of the app: content screens
speak the gold language, lists speak the terminal one. This release is that choice, built.

### Added
- **A markets tab.** The dense terminal row: logo, ticker and Persian name, a twenty-four-hour
  trend line, price and a filled change pill, with «همه · کریپتو · فارکس · فلزات · دیده‌بان» over it
  and the open signals at the foot. Search is a separate destination rather than a field on it —
  a field would take the first row of every visit for something people do on a minority of them.
- **A sparkline in every row**, and `SparklineStore` behind it. Three rules keep it honest: only
  rows on screen ask, a symbol is asked for once per run whether or not the answer came, and four
  requests are in flight at a time. A market list without a shape per row is a spreadsheet.
- **A chart tab.** It opens the reader's own first market, or the platform's first quoted one.
- **The chart studio** — indicators, drawing tools, replay, backtest, NamaScript and layouts on
  their own page, at the owner's call. Six sections, each closed but stating what it would say if
  opened: «۴ روشن» beside "اندیکاتورها" answers what somebody opened the studio to ask without
  opening anything. It replaces the sheet stack the tool strip used to raise.
- **Three readings above the setup** — trend strength, volatility and bias — computed in
  `ChartReading` from the app's own ADX, ATR and moving averages. Coarse on purpose: ADX is noisy
  and «۶۲ از ۱۰۰» is a false precision, so three named bands is as much as the number honestly
  carries. Below sixty bars there is no reading rather than a neutral one.
- `CoineProGoldRule` and `CoineProSparkline` in the design system.

### Changed
- **The chart page is the gold direction**: a thirty-four-point logo and the market's Persian name,
  a forty-point price with its move beside it, a fading gold rule under the heading, eight outlined
  timeframe pills, and the chart itself in a rounded card with a gold-tinted edge. The card is what
  makes the readings and the setup below it read as belonging to the chart rather than as unrelated
  rows that happen to follow.
- **The bottom bar is خانه · بازارها · چارت · سیگنال · هوش.** Markets and Chart earn tabs because
  they are the two surfaces somebody opens this app to look at; both were several taps deep behind
  a search field. The toolkit and the activity log move into Home as two labelled rows — their
  routes keep their old spelling, so every deep link that named them still lands.
- The chart's tool strip is gone. What replaced it is a card naming the studio and what is on the
  chart: «۴ اندیکاتور · ۲ ترسیم» tells a returning reader the state of their chart, which a row of
  icons never did.

### Notes
- The design canvas that settled this is `design/canvas/`; the two substitutions in its mockups —
  Vazirmatn for IRANYekanX, lettered discs for the vendored logos — do not appear in the app, which
  uses the shipping font and the real artwork.

---

## [1.24.0] — 2026-08-27 — The chart, rebuilt

The chart was the worst-looking screen in the app and the owner was right about every part of it.
This is the pass that fixes it, measured against what a terminal actually looks like rather than
against what the code happened to draw.

### Fixed
- **A hundred and thirty density-independent pixels of empty black under every chart.** The tool
  strip carried eight labelled buttons in a fixed row. They overflowed the width, so Compose
  measured the last few against zero and their Persian labels wrapped one character per line — a
  262dp-tall toolbar, of which only the top fifth had anything in it. The strip is icons now, it
  scrolls, the label survives as the accessibility name, and counts ride as a badge on the glyph.
- **The timeframe chips sat on the chart's top gridline.** They have their own vertical room and a
  hairline under them, and a compact size: all eight fit the width instead of the first scrolling
  off the edge.
- **A MACD pane's lower bound printed as `4.92-`.** Every label on the canvas is measured with the
  composition's layout direction, so on a Persian device an axis label was laid out as a
  right-to-left paragraph and the leading minus — a neutral character — moved to the end. Axis text
  is explicitly left-to-right now.
- **The calendar's figure cells split their own labels**: «پیش‌بینی» broke as «پیش‌بین / ی» and
  «Forecast» as «Foreca / st». Three cells share a row and the inset left the label thirty pixels.

### Changed
- **Volume moved into the foot of the price pane**, drawn faint and under the candles, the way every
  terminal draws it. It was a band of its own at a fifth of the canvas: on a chart with three
  oscillators the volume bars ended up taller than the candles above them. The price now keeps at
  least half the picture no matter how many strips are switched on.
- **A last-price line and tag.** A dashed rule at the live close and a filled tag on the axis in the
  bar's own direction — and the gridline label it would have covered steps aside. The header's price
  says what the number is; this says where on the scale it sits.
- **A corner legend.** The bar's open, high, low and close, then each overlay by name in its own
  colour with its value at that bar. With the crosshair down it follows the crosshair, so a finger
  on the chart scrubs history rather than reading a second copy of the header. Bounded to a quarter
  of the plot, so a chart in a card gets one line rather than a paragraph over its own candles.
- **The crosshair labels its own position** — the price tagged in the axis gutter, the time under
  the plot — instead of printing a readout in a corner the reader has to look away to find. Its
  vertical rule runs through the panes, so a turn in the price and the oscillator reading under it
  are measured against the same bar.
- **Vertical gridlines stand where the time labels stand**, from the same arithmetic, rather than at
  even fractions of the width. A grid whose columns miss the dates underneath them cannot be used to
  read a date off a bar, which is the only thing vertical gridlines are for.
- The chart header carries the move across the loaded window beside the price, in the direction's
  colour.
- Indicator pane titles sit on their own ground, so a pane's reference lines no longer run through
  the letters.

### Added
- `compact` on the shared chip row, for a strip that is chrome rather than content.

---

## [1.23.0] — 2026-08-27 — NamaScript

The reader writes their own indicator, saves it, and sees it on the chart they were already looking
at. Ten scripts ship with it and a twelve-part course teaches the language beside the editor.

The design decision this rests on: **evaluation is vectorised**. Every expression is evaluated once
over the whole series and scalars broadcast, rather than a bar loop with rolling state per call
site. That is why the language has no `for` and no user functions, and why running a script over a
thousand bars on a phone is a few milliseconds rather than a spinner. `iff` and `[n]` cover what a
loop would have been used for.

### Added
- `core:script` — the whole language. Lexer, parser, interpreter and about fifty built-ins across
  `ta.*`, `math.*`, control (`iff`, `nz`), inputs and output (`plot`, `hline`, `marker`, `signal`,
  `log`). Every `ta.*` delegates to `core:chart`'s own `Indicators`, so a script's EMA is bit for
  bit the EMA the chart draws — not a second implementation that agrees most of the time.
- Ten shipped scripts, each a study somebody actually trades and each written the way it would be
  written by hand: two-average cross, RSI zones, Bollinger squeeze, ATR stop, breakout setup, MACD,
  trend filter, volume spike, inside bar, session range.
- A twelve-lesson course in Persian, in the order the ideas depend on each other. The two hardest —
  that everything is a whole series at once, and that an absent value is not zero — come third and
  fourth, before anything quietly relies on them. Every example is runnable, and one tap puts it in
  the editor against the symbol on screen.
- A function reference derived from the interpreter's own dispatch table, and a colour list derived
  from its own palette map. A test calls every entry: a reference that names a function the language
  does not have teaches an error in the reader's own hand.
- `feature:script` — the studio. A live chart preview at the top, the code field under it, the input
  panel, the setup readout and the log. Four tabs: editor, library, lessons, reference.
- Saved scripts, in a new `saved_scripts` table with `MIGRATION_3_4` written out by hand. That is
  the third table in this database that nobody can give back if it is dropped, beside the journal
  and the paper trades.
- Reader-set input values are saved with the script and restored when it is reopened, clamped into
  the range the script declares now rather than the range it declared when the value was stored.
- Indicator panes on the chart — `ChartPane` and `ChartCatalog.paneFor`. This closes a real gap that
  had nothing to do with scripting: thirty separate-pane indicators were in the catalogue with their
  arithmetic already written, and switching one on drew nothing at all, because there was nowhere on
  the canvas to put a second scale. RSI, MACD, ATR and twenty-seven others now draw.
- Two ways in, both from where a reader already is: the chart's toolbar, and a card in the toolkit.

### Changed
- Whether a plotted line goes over the price or into its own pane is decided by **measuring its
  typical distance from the close**, not by overlapping ranges and not by reading its title. Ranges
  overlap by accident — an RSI reading 0–100 sits neatly inside a price that trades between 100 and
  160 — and an RSI drawn over the candles flattens the price axis to a line.
- Volume and the indicator panes are now capped *together* rather than each on its own. Separately
  clamped they added up, and three oscillators plus volume left the candles less height than the
  volume bars underneath them.
- Chart axis labels are laid out left-to-right explicitly. The measurer took its direction from the
  composition, so on a Persian device a MACD pane's lower bound printed as `4.92-`.

### Notes
- A script cannot reach the network, cannot read the account, and cannot place an order. It reads
  candles and it draws. Each run has a node budget, so a mistake in a reader's own script cannot
  lock the app.
- During bar replay a script sees exactly the bars the reader sees. The future is absent from what
  the interpreter is given, not hidden by the renderer.

---

## [1.22.0] — 2026-08-27 — Membership status

Both backends confirmed everything again on live servers. The one gap left in the app was the
membership check itself: the routes were contracted but nothing called them.

### Added
- `core:membership` and `feature:membership` — the status read, the UID form, and the seven states
  the server actually uses, mapped one to one. A state added server-side after this build ships maps
  to `UNKNOWN` and draws the server's own sentence, rather than to the nearest neighbour, which
  would be a guess about somebody's membership.
- The UID is folded to Latin digits before it is sent. A Persian keyboard produces ۰-۹ by default,
  and the exchange asked about `۱۲۳` answers that it has never heard of that account — a refusal
  that reads as a judgement about the person rather than about the keyboard.

### Changed
- `pending_deposit` is drawn in the warning colour, not the refusal colour. It means a genuine
  sub-account whose balance has not reached the threshold — somebody who did everything right
  except the last step — and painting that red tells a reader who succeeded that they were turned
  away. How many readers are in that state on any given day is the server's business, not a
  premise this screen rests on.
- `note` is carried and never rendered. TradeYar found their own web form printing it — a reader
  seeing `referral_status=false` — which is exactly the mistake the field's separation prevents.

---

## [1.21.1] — 2026-08-27 — What deletion actually takes

CoinePro-FX asked whether deleting an account should take the academy account created with the same
verified e-mail. It should, and both servers do. This makes the app say so.

### Changed
- The deletion screen and the published privacy policy both name the academy account and lesson
  progress. It is the one item a reader would not predict, and somebody who asked to be forgotten
  and could still recover an academy password with the same e-mail has not been forgotten.

### Fixed
- The academy token is cleared on sign-out. It is a second credential, derived from the mobile one
  and held in memory, and without this it outlived the session by up to twelve hours — after a
  *deletion*, as a live bearer for an account that no longer exists.

---

## [1.21.0] — 2026-08-27 — Both backends answered

Every blocker either team owned is now built and on production. This is the app catching up to it.

### Added
- The membership card carries the **real referral links**, from `GET /api/v1/public/membership`.
  This is the one thing that could never be compiled in: a link one release out of date does not
  fail visibly — the exchange simply never records the account as CoinePro's, so the reader funds
  it, submits their UID and is refused for a reason nothing on screen can explain. The minimum
  deposit and the notice above the links come from the same place, read from the same values the
  verifier reads.
- Public candles (`GET /api/v1/public/candles/{symbol}`) and CoinePro-FX's guest token
  (`POST user/auth/guest`) in the gateways, so a guest can be shown a chart on either platform.
  The guest token creates no account, cannot be refreshed, and opens three chart routes.
- Contact details in the privacy policy and the terms — support address and the developer's legal
  name and address, in both languages. The last two rows of the Play checklist nobody else could
  fill in.
- `docs/assetlinks/`, one file per host, with the real release fingerprint and the three conditions
  that make verification silently fail if any is missed.
- `scripts/quality/redact-backend-internals.py`, and a `--check` mode, so no future document
  reintroduces a server module name or a cache key.

### Changed
- The terminal's address comes from the server's capability answer, not from the build. The address
  that was compiled in pointed at a host that had been decommissioned — the domain no longer
  resolved — so the button would have opened a browser error, and no release could have known.
- **The academy token is injected into the WebView no more.** The credential rides in the URL
  fragment, which browsers never send to a server: not in the request line, not in `Referer`. That
  also removes the last thing `onPageStarted` could hand to a page the app did not choose.
- Every capability flag parses in either spelling. CoinePro-FX's config answers `bot_username` and
  `accountDeletion` in the same object; under a snake_case policy the camelCase one silently
  defaulted to `false`, which is a working feature the app quietly stops offering.
- The academy token's lifetime is read from `expiresIn` rather than the absolute stamp — the
  opposite of the advice that came with the route, and for the reason that advice was worried
  about: a phone whose clock is wrong compares an absolute time against a wrong now and gets a wrong
  answer, where a relative one cancels out of the subtraction.
- Password-recovery App Links are claimed for CoinePro-FX's host as well, at its own path.

### Fixed
- A `403 deletion_disabled` reads as "this server does not offer deletion", not as a session
  expiring. Everywhere else in this app a 403 means signed out — left to the general path it would
  have logged somebody out of an account that still exists, right after they asked to delete it.
- The site renderer handles a mailto autolink. The support address rendered as the literal text
  `<name@example.com>` — on the one page whose whole job is to say how to reach a human.

---

## [1.20.1] — 2026-08-27 — Hardening for a public repository

The repository went from private to public. Nothing in its history was secret — no keystore, no
`.env`, no `local.properties`, no Firebase file, and no credential in any commit message — but a
public source tree makes every weakness in it findable by reading rather than by guessing. These are
the ones worth fixing before somebody else finds them.

### Security
- **The terminal WebView compared origins by string prefix.** With the address normalised to
  `https://terminal.example`, `https://terminal.example.evil.tld`, `https://terminal.example@evil.tld`
  and `https://terminal.example-not-really.tld` all passed the check. That mattered more than a stray
  navigation, because `onPageStarted` plants the reader's academy token into whatever document is
  loading — a page reached that way would have been handed the token. The comparison is now on the
  parsed host, exactly, and the injection is gated on the same test so a server-side redirect cannot
  slip past a check that only runs on navigations. Six tests, one per bypass.
- **A server-supplied URL went straight to `ACTION_VIEW`.** The community channels added in 1.17.0
  carry their links from the server, and `ACTION_VIEW` on an arbitrary URI hands the string to
  whatever app claims that scheme — `intent://` starts a component in another app, `file://` hands
  over a local path. `Context.open` now requires https and a host.
- **Four workflow-dispatch inputs were interpolated into shell scripts.** Only someone with write
  access can dispatch those workflows, which bounds it, but the runners hold the release keystore and
  the Play service account. The inputs now reach bash through the environment.
- **`mapping.txt` is no longer uploaded as a build artifact.** It undoes R8's obfuscation of the
  bundle beside it, and on a public repository an artifact is downloadable by anybody. Play Console
  is where a crash report needs it, behind the same account as the release.

---

## [1.20.0] — 2026-08-27 — An icon with a ground under it

### Changed
- The launcher icon's background is a fifteen-level radial rather than one flat slab of #0E1118. A
  flat slab is what makes an icon look dead on a home screen full of icons that are not flat: there
  is nothing for the mark to sit *in*. It is not a glow — centred on the canvas rather than on the
  mark, and neutral rather than tinted with either brand colour.
- The mark is centred on its ink centroid rather than its bounding box. Its mass sits at 44% of the
  height, because it is an interlocking C and P with a tail hanging off the bottom, so a box-centred
  mark floats high.
- The size is unchanged, and that is a finding rather than an oversight: measuring the artwork says
  the old fit was within 3% of the largest it can honestly be. The icon was not lifeless because it
  was small.

### Added
- A real monochrome layer for themed icons. Android tints that layer flat, so pointing it at the
  colour artwork threw away everything separating the silver C from the gold P and left one blob.
  The generated one keeps the crossing by cutting a thin seam where the two materials meet — thin
  because the mark is drawn as six-dp ribbons, and a wide knock-out chews through a stroke.
- `design/play/icon-512.png`, composited from the same two layers the launcher draws, so the store
  icon and the installed one cannot drift apart. That row of the Play checklist is now done.

### Fixed
- The mark no longer overruns the adaptive icon's 72dp viewport. Sizing it to the guaranteed circle
  alone pushed the end of the P's tail off the bottom of the canvas — an error that only shows up on
  a phone.

---

## [1.19.0] — 2026-08-27 — "Coine" in the mark's own silver

### Changed
- "Coine" is the mark's silver instead of near-white. In the supplied artwork it sits at luminance
  232 in a flat top face beside a genuinely metallic "Pro", so under the mark it read as white text
  next to gold rather than as two halves of one name in the same material. It is histogram-matched
  onto the C's own silver — "make it the silver of the C" performed by measurement rather than
  picked by eye. The gold and every edge in the file are untouched.
- `CoineProColors.Silver` moved from #E4E4E4 to #DBDBDB, the median of the artwork's own silver. It
  was brighter than the metal it names, which is the same fault the wordmark had.

### Added
- `scripts/design/build-brand-lockup.py`, which produces every brand raster from the one master, and
  `--check`, which proves the committed files are what it produces. The brand README had promised
  this pipeline for a while; now it exists, and the mark it regenerates is byte-identical to the one
  that was already committed — which is what makes the crop boxes and the resampling trustworthy.

---

## [1.18.0] — 2026-08-27 — The reader's own calendar

### Added
- `JalaliDate` in `core:common` — the Solar Hijri conversion, by the Borkowski algorithm with its
  33-year break table rather than a modulus that is right most of the time. A simplified rule
  disagrees with the printed calendar about once a decade, and the day it disagrees is a day the app
  shows the wrong date for a release somebody is trading. Pinned against hand-checked fixtures and a
  four-thousand-day round trip.
- `PersianDateTime` — one place that decides how a date and a time are written, replacing the eight
  copies of `"MMM d · HH:mm"` scattered through the calendar, the news list, the activity log, the
  signal detail, the portfolio, copy trading and Home.
- A date on each journal entry. A trading diary whose entries cannot be put in order is missing most
  of what a diary is for.

### Changed
- The economic calendar, and every other date in the app, is Solar Hijri with Persian digits. It
  printed «Wed, Aug 26» into a Persian interface, for a reader whose phone says ۵ شهریور.
- The clock stays Latin and is deliberately not converted. `14:30` is read against MetaTrader or
  LBank — it is a market figure in everything but name, and converting it would be the same mistake
  as converting a price.

### Fixed
- The portfolio's monthly breakdown buckets by **Solar Hijri** months, not Gregorian ones. Mordad
  runs from the 23rd of July to the 22nd of August; a Gregorian bucket under a Persian month name
  would attribute three weeks of trades to a month they did not happen in.

---

## [1.17.0] — 2026-08-27 — The community, counted honestly

### Added
- The public channels and their member counts on the guest screen, from TradeYar's
  `GET /api/v1/public/community`. It answers the question a reader asks after they know what the
  product is — is anyone else here — which is why it sits under the membership card and not over it.
- `Long.toPersianGroupedDigits()` in `core:common`, for counts that run to five and six figures.
  The separator is U+066C and not a Latin comma, which reads as a decimal point to half the world.

### Fixed
- A count the server could not fetch renders as «داده در دسترس نیست» — never as a zero, and never as
  the number it returned last time. The route documents this rule; `MemberCount` is a sealed type
  rather than a `Long?` precisely so there is no `?: 0` for anyone to write.
- The total is the server's own, not summed from the channels. A sum here would silently omit every
  channel whose count failed and report a figure that is confidently wrong instead of honestly
  partial.

---

## [1.16.0] — 2026-08-27 — Sharing the chart

### Added
- One button captures the chart itself — not the screen — and hands it to the system share sheet.
  The phone already has a screenshot key; what a reader wants is the chart without the status bar,
  the navigation bar and the toolbar around it.

### Security
- A FileProvider scoped to one cache directory and nothing else. A provider over `files/` would
  offer the reader's cached quotes and a journal export to any app that could be persuaded to ask.
- The previous share is deleted before the next is written, so the app does not accumulate a hidden
  folder of every chart anybody ever sent.
- The URI is granted for one intent rather than the file being made readable to everything.

---

## [1.15.0] — 2026-08-27 — Saved chart layouts

### Added
- A layout keeps the chart type, the timeframe and the indicators that were on. Setting a chart up
  the way somebody likes it is eight or ten taps, and it is the same eight or ten every time they
  open a different symbol.
- Applying replaces the indicator set rather than merging it. A layout that added to whatever was
  already on would drift towards every indicator being on at once — the state a layout exists to
  escape.
- Drawings are deliberately not saved. A trend line is anchored to one instrument's prices and
  dates; a layout carrying drawings would paste last week's lines onto whatever chart it was applied
  to, at prices that mean nothing there.
- An unknown indicator id is skipped rather than failing the apply, so a layout saved by an older
  build loses one line instead of the whole layout.
- A name containing a record separator is refused rather than sanitised: silently renaming
  somebody's layout is worse than not saving it.

---

## [1.14.0] — 2026-08-27 — Keyboard shortcuts on the chart

### Added
- Digits pick a timeframe, space plays and pauses replay, the arrows step a bar, Escape cancels a
  drawing and Z undoes one. For tablets, DeX, Chromebooks and anything with a Bluetooth keyboard —
  on all of which a chart that ignores the arrow keys feels like a phone app being tolerated.
- Nothing is keyboard-only. Every shortcut has a control on screen; a shortcut that is the sole
  route to a function is a function most readers do not have.
- Key-down only. Android delivers both edges, and acting on both fires every shortcut twice — which
  on a timeframe key is invisible and on a step key is two bars.
- Left and right mean back and forward in *time* even in the right-to-left interface, because the
  chart's time axis runs left to right in every terminal and a trader comparing two screens must not
  have to reverse one of them in their head.

---

## [1.13.0] — 2026-08-27 — The order ticket that can honestly exist

### Added
- The setup drawn on the chart can be taken as a paper trade: same side, same entry, sized by the
  risk the reader entered. One tap from the numbers to the position.
- Deliberately not a real order. Neither backend serves a free-form one — TradeYar executes against
  a *published signal*, CoinePro-FX mirrors a copy account — so a button claiming to place a trade
  would be a button that cannot. The note under it says no order is sent anywhere, and
  `docs/REQUEST4_ACCOUNT_DELETION.md` asks both servers for the real route.

---

## [1.12.0] — 2026-08-27 — Switching symbol without leaving the chart

### Added
- A watchlist strip on the chart. It replaces four taps — back, search, type, open — repeated every
  time somebody compares two instruments, which is most of what looking at charts is.
- The reader's own list and nothing else. A strip of "popular" symbols would be a second market list
  on a screen that is not one; if the watchlist is empty the strip is absent rather than filled with
  suggestions.
- The current symbol scrolls itself into view, so opening the chart on the ninth of a list does not
  show a strip apparently starting somewhere else.
- Switching replaces the chart rather than stacking one on another: flipping through six symbols
  must not build a six-deep back stack that takes six presses to leave.

---

## [1.11.0] — 2026-08-27 — A backtest on the chart

### Added
- `core:backtest`: three named rules — moving-average cross, RSI reversion, channel breakout — run
  over the bars already loaded, on the device, with no request. The web terminal backtests an
  arbitrary language on a server; this answers the question a reader actually has, which is whether
  an idea survives the last thousand bars.
- Every choice is the pessimistic one, because a backtest that flatters is worse than none. A signal
  on bar *n* fills at the **open of bar n+1** — filling at the close that produced it is the most
  common way a backtest invents money. Anything still open closes at the final bar. Costs default to
  five basis points, not zero: a rule that flips every few bars is a fortune at zero and ruinous at
  five, and that difference is usually the whole finding.
- Maximum drawdown is measured peak to trough rather than start to end — the number that decides
  position size, and the one a flattering backtest leaves out.
- Long only, and the sheet says so: a short needs a borrow and a funding rate that a bar series does
  not know, and silently shorting answers a question about a position the reader could not hold.

---

## [1.10.0] — 2026-08-27 — Paper trading

### Added
- `core:papertrade` and `feature:papertrade`: open a position at the price on screen, close it at
  the price on screen, keep the record. For the reader who has installed the app and has not funded
  an exchange account — which, given that membership needs fifty tether, is most first-day readers.
- The entry price is the one the screen was showing at the moment of the tap, passed in rather than
  fetched inside. Filling at a number the reader never saw teaches them something untrue about
  market orders.
- A closed trade marks at its exit, never at today's price, so yesterday's win does not grow every
  time the screen is opened.
- The record counts only closed trades: an open position's profit changes while being read, and a
  win rate that moves when nothing happened is not a win rate.
- What is not modelled — fees, spread, swap, funding, slippage — is stated on the screen rather than
  left to be discovered against a real fill. A simulation that guesses a broker's fee schedule
  produces a number that looks like a real result and is not.

---

## [1.9.0] — 2026-08-27 — A trading journal

### Added
- `core:journal` and `feature:journal`. The signals list holds what the service published and the
  portfolio holds what the broker executed; neither holds the record that actually changes how
  somebody trades — what they thought at the time and what they would do differently.
- Everything optional but the symbol. A journal is written in the ninety seconds after a trade
  closes, and a form demanding four numbers first is a journal abandoned in the second week.
- Statistics that exclude ungraded rows rather than averaging them as zeros, and say how many were
  excluded. An entry with no P&L is not a break-even trade.
- Profit factor is null where there is no loss to divide by: three winners is not an infinite
  profit factor, it is one not yet produced.
- Tag filtering, with the statistics recomputed over the filter — a reader tapping "بریک‌اوت" is
  asking what their breakouts do.
- CSV export with a UTF-8 byte-order mark, shared rather than written to a file the app then owns.
  Without the mark Excel opens a Persian journal as mojibake, and nobody exports twice.

### Changed
- The database is at version 2 with a written migration rather than destructive recreation. Every
  other table in it is a cache; the journal is the one thing that cannot be refetched.

---

## [1.8.0] — 2026-08-27 — Somewhere to create a price alert

### Added
- `feature:alerts`: create, mute and delete. The gateway had existed since the notification work
  and had no screen, so the app could *receive* a price alert while offering nowhere to make one —
  every alert in the list had come from somewhere else.
- All five conditions, not just above and below. "Above" fires immediately on an instrument that has
  been above the level all week, which is usually the opposite of what was wanted; the crossing
  conditions fire on the transition. Telling them apart is the difference between an alert that
  arrives when something happens and one that arrives the moment it is set.
- Once by default, recurring behind a switch with the reason written next to it: on a price
  oscillating around the level, recurring is a phone that will not stop while its owner is asleep.

---

## [1.7.0] — 2026-08-27 — The guest market is the real one

### Changed
- The guest shelf is chosen from the feed's actual universe instead of eight tickers compiled into
  the app months ago. The first read asks for everything, takes the twenty busiest, and polls only
  those.
- The order is fixed at that first read and never recomputed. A list that re-sorts itself by volume
  every ten seconds rearranges under the reader's finger — the market decides what is on the shelf,
  not where each thing sits while somebody is looking at it.
- The header says how many markets there really are. Twenty rows with nothing saying otherwise is a
  much smaller product than the one the feed carries.

---

## [1.6.0] — 2026-08-27 — What the signals actually did, before the sign-up

### Added
- A track record on the guest screen: real published signals that have already closed, with the
  outcome the server recorded. Above the membership card, not below it — a card asking for a
  sign-up before showing what the signals did is asking for trust it has not earned.
- The win rate is the server's own count by its own ladder definition, not recomputed here. The
  route says in as many words that a client must not, and two different win rates in front of one
  reader is worse than none.
- `data_available` is read rather than inferred from an empty list. Empty-because-the-query-failed
  and empty-because-nothing-closed are different sentences to put in front of somebody deciding
  whether to trust the product, and the section is hidden rather than showing either.

---

## [1.5.0] — 2026-08-27 — A watchlist

### Added
- A star on every market row, on Home and in search, and the reader's own list above the market on
  Home. The most-used control in a trading app, and the app did not have one.
- Order is insertion order, oldest first — not alphabetical and not by price. The reader put them in
  a sequence and the sequence is information; a personal list that rearranges itself while being
  read is the one thing it must never do.
- Local, in the same preferences file as every other choice. TradeYar does serve a watchlist, but
  behind its own device-link flow — a second identity to establish before the first star. A round
  trip per star turns the most-tapped control into the slowest one; sync belongs on top of this,
  not instead of it.
- One list across both platforms. A reader watching gold and bitcoin has one list, and splitting it
  would mean the star they pressed vanishing when they switched tab.

---

## [1.4.0] — 2026-08-27 — What the setup on the chart is worth

### Added
- The `longshort` tool has always drawn three lines. This is the other half: entry, stop and target
  as prices, the two distances, the stop in pips, and the risk-to-reward ratio — from
  `TradeFromChart`, the web terminal's own trade maths, which had been ported, tested and wired to
  nothing.
- Position size from a risk amount. Asked for as "how much are you prepared to lose", never as a
  lot size: risk decides size, and the reverse question is the same arithmetic with the opposite
  habit.
- The side is read off the geometry — a stop below the entry is a buy — so the numbers cannot
  disagree with the lines on screen. A setup whose stop sits on its entry is refused rather than
  printed, because a ratio there would be read as a real one.

---

## [1.3.0] — 2026-08-27 — Bar replay, reachable

### Added
- Replay is on the chart. The engine had been ported and tested for weeks and wired to nothing —
  dead code with a green test suite, which is the worst kind, because the tests say it works and
  nobody can use it.
- A transport bar that says loudly that replay is on: a reader who forgets is reading a live chart
  that is hours stale. Leaving is one tap and never behind a menu.

### Fixed
- Indicators and structure levels now compute over the *visible* slice. Derived from the whole
  series they would place a moving average using prices the reader is not allowed to have seen —
  the future leaking back in through the one door nobody watches.

---

## [1.2.1] — 2026-08-27

### Fixed
- **The trader toolkit was half in English.** Fifty-six hard-coded English literals — every
  calculator's field labels, units, result rows and assumption notes — on the screen a Persian
  trader uses to size a position. It had only ever been rendered in the English locale, which is
  the reason nobody saw it.
- Two more of the same: an `executions` metric label on Activity, and `n=1` as a sample-size
  marker.
- Latin numerals in three prose counts, where the app's own rule says Persian.

### Added
- Persian renders of the six screens that had only ever been captured in English — tools,
  activity, connections, news, calendar and launch readiness. A screen the app ships in Persian and
  has only been looked at in English has not been looked at, and this is where its bugs live: a
  Latin figure inside a right-to-left paragraph reorders, a label and its value swap ends, a unit
  lands on the wrong side of its field.

---

## [1.2.0] — 2026-08-27 — One market row, at the density the job needs

### Added
- `CoineProMarketRow` — the row Home, search and the guest market now share. There were three,
  and they had already drifted: different logo sizes, different vertical rhythm, the percentage as
  plain text in two of them and absent from the third.
- `CoineProPercentPill`. Coloured text has to be found before it can be read; a filled block is
  found before it is read, which is the actual job — someone scanning a market list is looking for
  *which rows moved*, not for any particular figure. The fill is the system's 8% tint rather than a
  flat alpha, so it is the same colour on a card, on the stage, and in the light theme.
- `CoineProRangeBar` — where the price sits between the day's low and high. Two numbers the feed
  already carries and almost no app draws: 64,180 says nothing, 64,180 at the top of a
  62,800–64,900 day says the thing a reader opened the app for. It sits *beside* the pill rather
  than under it, so the range costs no extra row height.
- A hairline between search results, inset past the logo. Sixty-five markets on a bare stage had
  nothing for the eye to count by.

### Changed
- The guest market went from a card per quote — a stack of blocks to scroll — to one card of dense
  rows to scan. A market list is read by comparison, and comparison needs the rows close enough to
  hold in one glance.
- The guest lockup is smaller. A logo taking a fifth of the first screen is a brand announcing
  itself; the live market underneath is what does the convincing.

### Fixed
- Persian numerals in the membership steps. They were hand-written `1.` `2.` `3.` — Latin digits in
  a prose count, which is the app's one number rule and the way it actually gets broken: nobody
  writing a numbered list thinks of themselves as formatting a number.
- Three copies of the Latin-to-Persian digit helper became one, in `core:common` beside its
  opposite, with the market-figure exception written down where both live.

---

## [1.1.1] — 2026-08-26

### Changed
- The membership card and the landing page no longer explain where the money comes from. They say
  what a reader needs to decide — membership is free, the condition is a funded account at a
  partner exchange — and stop there. Explaining a commercial arrangement to somebody who asked to
  see a signal changes the subject, and reads as a disclosure rather than an offer.
- The terms keep a short, neutral clause about the partnership, because §6-2 makes registering
  through CoinePro's link mandatory and a condition with no stated reason is a worse document. It
  says the two things a reader actually needs: it costs them nothing extra, and CoinePro never
  holds their funds.
- "Sub-account" and "referral link" are gone from every reader-facing string, in both languages.
  The account is *linked to* CoinePro through a *dedicated link* — the same fact, without the
  vocabulary of somebody else's affiliate programme.

---

## [1.1.0] — 2026-08-26 — The app opens without an account

### Added
- **Guest mode.** The app used to open on a sign-in form, which asks for a password before giving
  any reason to have one. It now opens on the market: real prices from TradeYar's public feed, the
  published headlines, and the membership route explained underneath. The form appears when the
  reader asks for it — or straight away when a recovery link says they are already mid-flow.
- `core:guest` over the public routes the server has published all along for its own web site and
  the app never called. No token, so nothing here can log anybody out.
- A membership card that is not a paywall, because there is no wall: the four steps are stated in
  full, including the one that cannot be undone — an account opened without the referral link is
  not a sub-account in the exchange's own system and cannot be verified afterwards.
- **Deleting an account**, in the app and on the web. What goes, what stays anonymised and for how
  long, and that it does not close the exchange account. It asks for a typed word rather than a
  second tap, and where the server has no route it hands over the published page instead of a
  button that would fail.
- `PageAccent.DESTRUCTIVE` — a fourth accent that means "this cannot be undone", deliberately
  unavailable to a cancel or a sign-out.
- `MarketNumberFormatter.priceAuto`, which takes its decimals from the price's own magnitude. At a
  fixed two, XRP read 0.52 and a sub-cent coin read 0.00 — not a rounding but a claim that the
  asset is worthless.
- The privacy policy, terms and deletion page published as real pages, rendered from
  `docs/legal/*.md` so there is no second copy of the text to drift.

### Changed
- Terms §6 says what the service actually charges: nothing. Membership is free, and its condition
  is a funded account at a partner exchange with a 50 USDT floor — `VIP_MIN_DEPOSIT` in the
  server's own configuration — rather than a purchase. Copy trading is LBank only, because Ourbit
  does not offer it.

### Fixed
- `AccountApi`'s four paths carried CoinePro-FX's `user/mobile` prefix for both platforms. TradeYar
  serves them under `api/mobile/v1`, so briefing, portfolio and both KYC calls had been answering
  404 on the crypto platform.

---

## [1.0.0] — 2026-08-26

**The first release that installs, updates, and can be named.**

Everything below this line was work. This is the first build that leaves the machine it was made
on: signed with the permanent key, published by CI, and carrying a version that means something to
whoever is holding the phone.

### Added
- `version.properties` as the one place the app's version is written, with `versionCode` **derived**
  from it — `MAJOR×10,000,000 + MINOR×100,000 + PATCH×1,000 + BUILD` — so the integer Android
  enforces can never drift from the name a person reads. `docs/VERSIONING.md` has the widths and
  why they are those widths.
- `scripts/release/version.py` — the same arithmetic for CI and for the command line, with
  `--bump major|minor|patch`, `--json`, `--check`, and range checks that fail the build rather than
  wrapping. A wrap would produce a *lower* code, which is the one outcome that breaks updates on
  every device at once.
- `BUILD` counted as commits since the last version bump, so every push produces a strictly higher
  code with nobody having to remember anything, and the counter resets the moment a version is named.
- Semver build metadata on the device — `1.0.0+4` — so a bug report names the exact build rather
  than the nearest version. Git tags spell the same thing as `v1.0.0-b4`, in characters a refname
  and a URL both take unescaped.
- This changelog, covering the whole history rather than the last entry.

### Changed
- CI stopped inventing versions. It computed `run_number + 1000` and called the result
  `0.1.<code>` — monotonic, which was all it had to be, but `0.1.1004` was not a patch of
  `0.1.1003`; it was whatever landed that day. `1.0.0` lands at `10,000,000` and clears the whole
  of that old series with room to spare, so no build already in the field is stranded.
- `android-ci.yml` no longer pins `16.0.1-ci`, which read as a version 16 that has never existed —
  the 16 was a phase number.

---

## [0.27.0] — 2026-08-26 — A signed APK on every push

### Added
- `.github/workflows/android-apk.yml`: every push to `main` produces one installable, signed APK on
  a GitHub Release. One track, one signature, so an update **installs over the last one** instead of
  asking the reader to uninstall and lose their session.
- The workflow refuses to run without the four signing secrets rather than generating a throwaway
  key — a generated key installs fine exactly once, and then every later build is rejected by every
  device that took the first.
- `keytool -list` validates the alias and passwords before Gradle spends ten minutes finding out.
- `docs/CI_APK_SETUP.md`, mapping each secret to where its value comes from.

`99a87e3`

## [0.26.0] — 2026-08-26 — Sign-in that tells the truth

### Removed
- The Telegram Login Widget. It could never have authenticated anybody: the widget asks Telegram to
  sign a payload for the embedding page's **origin**, and Telegram checks that origin against the
  domain the bot's owner registered with BotFather. The origin was `telegram.org`, which nobody can
  register, so Telegram refused every time and drew its own error inside the frame — which is the
  "bot admin" error the owner was seeing. A mobile app has no page, so it cannot be fixed in place;
  the supported shape is a bot deep link plus a server route, asked for in
  `docs/REQUEST3_COINEPROFX.md`.

### Changed
- Google sign-in now says what is actually wrong. `NoCredentialException` and the "developer console
  is not set up correctly" family are not about the reader at all — they mean this build's signing
  certificate is not registered in the Google Cloud project that issued the server client id. A
  reader told "no account found" goes and adds a Google account, which cannot help.
- The auth screen renders the state it actually serves, rather than one that no longer exists.

`bfbeee5`, `2ef3406`

## [0.25.0] — 2026-08-26 — The release build stopped lying

### Fixed
- **R8 was renaming every field Gson serialises.** Nine request bodies are not named `*Dto` and none
  carried `@SerializedName`, so full mode renamed them to single letters and the shipped app posted
  `{"a":…,"b":…}` to every auth route. It is why sign-in failed in release and worked in debug.
- The check is now on the **dex**, not the keep rules. `mapping.txt` only lists *renamed* members,
  so a field's absence there is not evidence it survived — `scripts/quality/check-release-wire-fields.py`
  reads the built APK with `dexdump` and asserts ten wire field names are in it. The shipped build
  had one field named `email`; the fixed build has five.
- The `«؟»` help catalogue was entirely dead code, and R8 was right to remove it. It is now wired to
  the sheet that shows it, and what was genuinely unreachable stayed removed.
- `@HiltWorker` modules and assisted factories were being stripped, taking background refresh with
  them.

`88c716b`, `00da1d5`

## [0.24.0] — 2026-08-26 — Everything the store asks for

### Added
- `docs/PLAY_LISTING.md` — the listing pack: title, short and full description, category, content
  rating answers, data-safety declarations, and the release blockers that are the owner's to clear.
- `docs/legal/PRIVACY_POLICY.md` and `docs/legal/TERMS.md`, in Persian.

`dd36a51`

## [0.23.0] — 2026-08-26 — The untested modules, tested

### Added
- `core:security` covered, including `SessionCipher` extracted as a seam so the cipher can be tested
  without a device keystore.
- `core:model` covered.
- A real baseline profile, recorded against the current theme class, replacing the stub.

`baedc59`

## [0.22.0] — 2026-08-26 — One design language, enforced

### Added
- The `foundation-v2` token layer adopted whole: the five-step surface ladder, three border weights,
  the 8% fill / 34% border tint formula as `Color.lerp` rather than alpha, and 100/160/240 ms on
  `cubic-bezier(.2,0,0,1)`.
- `LocalPageAccent` — one primary-button component with a per-domain identity, so Markets, Trade,
  Copy and Subscribe each read one variable instead of each screen inventing a colour.
- Press feedback with the right proportions: a bigger surface compresses less.

### Changed
- Two golds are no longer interchangeable. The brand gold is an *ink*, not a *fill* — using it as a
  fill produced a near-black button on dark brown in the light theme, which the light design-kit
  render caught.
- The four accents became two. Two of the golds were indistinguishable on a phone; premium is now
  marked by treatment rather than by a colour nobody could tell apart.

### Security
- `scripts/quality/check-motion-policy.sh` now enforces the discipline rules — no blur, no coloured
  glows, gradients only in the five allow-listed places.

`f066647`

## [0.21.0] — 2026-08-26 — The professional terminal

### Added
- `feature:terminal`: the full Bazaarnama terminal — 52 drawing tools, `namascript` with its editor,
  bar replay, the strategy tester — behind one button and one build property. An ordinary reader
  never sees a WebView; a power user gets everything.

`13ca899`

## [0.20.0] — 2026-08-26 — The academy

### Added
- `feature:academy`: the curriculum roadmap, a lesson, and its quiz, over the `/academy/*` routes
  CoinePro-FX already serves.
- Badges, the leaderboard and the glossary, which had nowhere to be seen.
- Lesson HTML rendered to `AnnotatedString` with the bullets **written into the text** — `BulletSpan`
  paints at a fixed left offset, which is the wrong side in RTL.

`b4dadad`, `dc7fda9`

## [0.19.0] — 2026-08-26 — The portfolio

### Added
- `feature:portfolio`: closed trades, an equity curve, per-symbol attribution, a monthly breakdown,
  drawdown, win rate, profit factor and expectancy — computed in `core:portfolio` and tested.
- No borrowed statistics. Every figure is derived from the trades the server returned; nothing is
  carried over from the web app's own numbers.

### Fixed
- Drawdown rendered as `9.6%)( -$4,475.13`. Two adjacent LTR isolates reorder as runs inside an RTL
  paragraph; one isolate around the whole phrase fixes it.
- The monthly bars had no labels, because `DrawScope` has no access to resources. They are passed in
  from the screen.

`b77efd8`

## [0.18.0] — 2026-08-26 — The chart, reachable

### Added
- The chart mounted in a screen and reachable from the app.
- The setup drawn — entry, stop, targets — on both screens that show one.
- The nine new indicators documented in the `«؟»` catalogue.

### Fixed
- The chart preview bled to the screen edges in signal detail. It is in a card, without volume.

`4075b37`, `b551b67`, `d1297a2`

## [0.17.0] — 2026-08-26 — Real candles

### Added
- Candles from both backends against the contracts they published — forex from the `candles` table
  the EA pushes (broker-true prices), crypto live from LBank — on the compact `{t,o,h,l,c,v}` shape
  both already speak.

`756ba88`

## [0.16.1] — 2026-08-26

### Fixed
- No symbol without artwork reaches a list. `SymbolArtwork.covers` is the filter, at the catalogue
  and at the live feed — no blank squares, no lettered discs.

### Added
- TradingView's whole logo set, rather than the twenty-three that had been taken by hand.

`0f534bd`, `61b43a9`

## [0.16.0] — 2026-08-26 — Ninety-five indicators

### Added
- Twenty-nine more indicators, each checked against the app it came from.
- The seven structure studies — and one place where the original is wrong, documented rather than
  reproduced.

`9cffea0`, `2b74136`

## [0.15.0] — 2026-08-26 — The chart's own controls

### Added
- Every control drawn with its real icon, and the fifty-two drawing tools brought across.
- A finger can place a tool: points are stored in chart space `{t, p}`, so pan and zoom are free.

### Changed
- The three chart sheets share one chrome instead of repeating it three times.

`454f65a`, `b255c8b`, `5496267`

## [0.14.0] — 2026-08-26 — The «؟»

### Added
- The help catalogue brought across — 177 entries — and the pickers it hangs off, so every indicator
  and every tool can say what it is where it is used.

`f6a411e`

## [0.13.1] — 2026-08-26

### Added
- The Gradle wrapper, so the build does not depend on whatever Gradle happens to be installed.

`3ab4159`

## [0.13.0] — 2026-08-26 — The chart, drawn

### Added
- The Compose Canvas renderer: candles, Heikin-Ashi, line and area, with pan, zoom and a crosshair.

`cc6edc3`

## [0.12.0] — 2026-08-26 — The chart engine

### Added
- `core:chart`, ported from `proChart.js` — itself already a port from Flutter, so explicit pixel
  maths rather than DOM.
- Proved against the original: both run over the same candle fixture and the outputs are asserted
  equal to 1e-9.

`ee9c2b0`, `51148bd`

## [0.11.0] — 2026-08-26 — Knowing what a symbol is

### Added
- `core:symbols`: the classifier (metal / energy / index / forex / crypto), Persian display names,
  the `FOREX_ALIAS` table, liquidity ranking, and client-owned market status — the server's flag was
  unreliable per category, and weekends are the client's to know.
- The ranked fuzzy matcher: exact 1000, prefix 800, substring 600, subsequence 300, +25 for
  popularity, searching **the Persian description as well as the ticker**, tie-broken by liquidity
  rather than alphabetically.
- `feature:search` on top of it — 80 ms debounce, category chips, recent searches, live prices for
  the visible rows, match highlighting. It replaced a substring filter.

`63dabab`

## [0.10.0] — 2026-08-26 — An interface icon set

### Added
- The whole TradingView icon set rather than the twenty-three chart-toolbar glyphs.
- The app's own eleven — the icons that name its sections.
- A drawn navigation set, after the three exchanges' own sets could not be obtained.
- The bottom bar taken from the exchanges' own sets.
- `design/ui-icons/README.md` states plainly what each set is and where it came from.

`53dd8a9`, `d62664c`, `ac59b25`, `4118e73`, `33e9a66`

## [0.9.2] — 2026-08-26

### Fixed
- An oversized symbol falls through to its raster instead of to a letter. A lettered disc is what
  "failed to load" looks like.

`3e6d140`

## [0.9.1] — 2026-08-26

### Fixed
- The SVG converter learned no-op clips and real gradients, which is what Binance's set needs, so it
  could lead rather than be the fallback.

`748665f`

## [0.9.0] — 2026-08-26 — Every instrument has a logo

### Added
- Every symbol either backend can quote, not eight. The hand-written map is gone; the lookup is
  generated.
- The forex pairs drawn as a two-disc composition — base flag in front, quote flag behind with a
  notch ring — positioned physically, so a pair reads identically in RTL and LTR.
- The four metal discs.

### Fixed
- A third of the instrument logos rendered wrong. Gold in particular looked like the one that failed
  to load.

`84e99b2`, `b6a14ab`

---

## [0.8.0] — 2026-08-25

### Changed
- Every brand raster regenerated from the supplied transparent master.

`9c181ae`

## [0.7.0] — 2026-08-25 — Copy trading

### Added
- `core:copytrade` against CoinePro-FX's copy-status and copy-config.
- The copy-trading screen replaces the execution screen on the forex platform, which is what that
  backend actually offers.
- The plan name shown in Persian from `plan_fa`; the AI capability flags consumed rather than assumed.

`02d7c7d`

## [0.6.0] — 2026-08-25 — Diagnostics

### Added
- `core:diagnostics`: a request log and a catalogue of every route, with a prober that says which
  are live.
- An admin control hub with per-platform sections, behind five taps on the version number.

`49d4ef9`, `b7b438b`, `473dd00`, `2627307`

## [0.5.0] — 2026-08-25 — Two backends, two identities

### Added
- Email-first sign-in, registration and password reset, matched to each backend's real auth shape
  read from its own source.
- Google sign-in through Credential Manager, with the audience taken from `auth/methods` rather than
  compiled in — the two deployments have separate Google configuration and a token minted for one
  has an `aud` the other refuses.
- Refresh tokens stored alongside access tokens, renewed on schedule and on a 401 before signing out.
- Per-platform gateways, with every path pinned in tests.
- The HTTPS App Link, pointed at the host that already serves `assetlinks.json`.

### Fixed
- English protocol strings were being shown to Persian readers as if they had been written for them.
- The `coinepro://` intent filter was accepting more shapes than it should.
- A per-platform install id, so readers behind the same CGNAT address do not share a rate-limit
  bucket.

`7b0c802`, `f2bbff4`, `edbcc51`, `871956e`, `1d0c4a9`, `4057ccc`, `9a35136`, `f293892`, `481cb37`

## [0.4.0] — 2026-08-25 — The «آرام» direction

### Changed
- Home rebuilt in the settled visual direction, then the whole app behind it: chrome, type and
  spacing scale, signals, the AI section, connections, execution, chart analysis, the assistant,
  signal detail, market news, the calendar, sign-in, safety, activity and the trader toolkit.
- A light theme.
- Motion that carries information rather than decorating.
- A mixed market became impossible to produce — the platform is a property of the screen, not of a
  row.

`3112de7` … `6dab604`

## [0.3.0] — 2026-08-24 — The app's own identity

### Added
- Persian as the default language, and the RTL foundation under it.
- IRANYekanX typography.
- The CoinePro identity and a launcher icon, from the black-ground masters.
- The asset logo artwork and a real UI icon family.
- Screens rendered to PNG without an emulator, which is how every visual change since has been
  reviewed.

`042874e`, `e499cb4`, `3080919`, `a95cf59`, `bebcbbe`, `1c75da0`, `823e017`

## [0.2.0] — 2026-08-24 — Release engineering and launch safety

### Added
- A protected release-engineering pipeline, a staging build identity, and a reproducible signed App
  Bundle path with signing material supplied from outside the repository.
- Manual Play Console internal-track publishing for the staging package.
- Notification-permission education and a denial-recovery path, shown *before* the platform prompt.
- Connection setup that distinguishes credentials being configured from a provider having confirmed
  them.
- A feedback and share path carrying app version and environment only.
- `docs/PHASE17_INCIDENT_RUNBOOK.md`, and a read-only production smoke that never mutates anything.
- A permanent Phase 1–17 repository consistency gate.

### Changed
- Signal navigation requires a positive server signal id, consistently, everywhere it is used.
- Deep-link parsing restricted to the CoinePro scheme and the supported route shapes.
- The Room market cache rejects out-of-scope rows on write *and* on restore.
- Release documentation distinguishes this repository's syntax and range checks from the
  cross-release monotonicity Play enforces.

### Security
- Release keystores and service-account credentials are external secrets and are never committed.
- Benchmark builds use non-routable configuration instead of inheriting production endpoints.
- External legal, provider and production evidence is never synthesised from CI, mocks or cache.

## [0.1.0] — 2026-08-23

Initial native Android product milestone covering the Phase 0–15 application surface and quality
gates.

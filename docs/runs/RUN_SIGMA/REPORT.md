# RUN Σ — what changed, phase by phase

## Σ0 — the two the owner asked for first (4.81.0)

Shipped on its own, ahead of the rest of the run, because both items are things the owner has to put
a thumb on before anything else is worth building.

### S1 — the pinch, and why only one axis worked

The report was «pinch افقی روی چارت کار نمی‌کند، عمودی کار می‌کند». Both halves of that come out of
one line.

The time axis was driven by `1.0025.pow(spanX − lastX)` — the bar spacing growing by a quarter of a
per cent for every pixel the fingers opened **in that frame** — and the result was then put through a
one-per-cent dead zone. On a 120 Hz phone a finger separating at a brisk five hundred pixels a second
moves about two pixels of half-span per frame, which is a ratio of 1.005. Under the dead zone. Every
frame, for the whole gesture. To clear it you had to separate your fingers at roughly two thousand
pixels a second, which is not a gesture, it is a flinch.

The price axis, three lines below, used a plain `spanY / lastY`. The same two pixels on a
hundred-pixel span is 1.02 and sailed through. One axis worked and the other was arithmetically
unreachable, which is exactly what the device showed.

Underneath that was a second, worse thing: **the routing discriminated by the angle between the
fingers.** The horizontal branch only fired when the fingers were far enough apart *horizontally*;
the vertical branch fired anywhere on the canvas, gutter or not. So a reader pinching at forty-five
degrees drove both axes at once, and a reader pinching vertically on the candles got the price scale
when what they had asked for was more bars.

The angle between two fingers is not a statement about intent. Where they landed is. `pinchZoneOf`
now decides once, at the first touch — plot, price ladder, or date strip — and the plot's zoom is the
Euclidean distance ratio at any angle. A manual price scale follows the same ratio; an automatic one
rescales itself to the bars now visible, which is what the reference does.

Six injected-pointer tests, one of them through the whole page rather than at the chart, because the
thing that would look identical to this bug is something above the plot swallowing the second finger.

### S2 — the word beside the triangle

«برچسب خرید/فروش روی فلش‌ها.» A triangle is a direction, and an app whose whole thesis is that a
chart should say what it means cannot put an unlabelled arrow on a candle and call that saying it.

The word is the easy half. The half that needed designing is what happens at the other end of the
zoom: a label under every triangle on a year of daily bars is forty words overlapping each other and
the candles they are about, which hides more than it says. So the density rule is arithmetic, in
`:chart-core`, in **points per bar** rather than in bars — a phone and a tablet showing the same
hundred bars are showing them at very different sizes, and the question a label has to answer is «is
there room for four characters here».

| Points a bar | What is drawn |
|---|---|
| ≥ 12 | triangle + «خرید» / «فروش» |
| 6 – 12 | the triangle alone |
| < 6 | one triangle per ten-bar window, the strongest of it |

Three more rules came with it. The glyph is 6, 8 or 10 points by the signal's own strength, so a
cross that barely happened is not the same object on the glass as one that went a long way past the
line. The word goes on the far side of the glyph from the candle — under a buy, over a sell — so it
never lands between the mark and the bar it is about. And a label that would cross the plot's edge,
the legend plate, or a label already placed is dropped rather than drawn: the older signal keeps its
word, which is the only tiebreak that does not leave both illegible.

The setting is per **study** and not per chart, because the reason to silence marks is that *that
study* is noisy. Three chips at the foot of the Explain sheet: «با برچسب», «فقط مثلث», «خاموش». The
middle one is the setting people actually reach for, and offering only the other two would be a false
choice.

### What Σ0 cost

| | |
|---|---|
| Files changed | 9 source, 4 test, 1 benchmark, 3 documents |
| New tests | `PinchZoneTest` (6), `ChartPinchTest` (6), `SignalMarkersTest` (13), `SignalMarkerProofTest` (8) |
| New proof frames | 7 |
| Goldens re-recorded | none — the golden charts carry no Signal Layer, so no marker moved |
| New features | S1 is a bug; S2 is the one feature in this version |
| Owed to device | S1's acceptance, and the two new Macrobenchmark scenarios |

# Σ1 — bring your own script (4.82.0)

## What this phase is actually about

From this version the *main* way a script arrives is that somebody asked an assistant for one and
pasted the answer in. That is not a case to be tolerated at the edge of the studio; it is the front
door, and the whole of Σ1 is about what happens in the three seconds after the paste.

## The paste table, and the test that nearly wasn't written

Twenty textual repairs, each for a mistake an assistant actually makes: Pine's `indicator(...)`
header, `&&` for `and`, a bare `sma(close, 20)`, `close(1)` for `close[1]`, `ta.rma`, Pine's
`shape.triangleup` and `minval=`, typographic quotes, Persian numerals inside code, and a script
that computes something and draws nothing.

The admission test for a rule is that **applying it to a correct script must be a no-op**, and the
test that enforces it runs the whole conformance suite — three hundred–odd files — plus the twelve
templates, the eleven presets and the twelve library strategies through `repair()` and requires
every one to come back byte-identical.

That test earned its place immediately. Four of the first twenty rules failed it:

| Rule | What it "fixed" | Why it was wrong |
|---|---|---|
| `declare-with-equals` | `x := y` → `x = y` | `:=` is NamaScript's own reassignment — `sem_reassign_series.nama` |
| `drop-var` | `var x = …` → `x = …` | `var` and `varip` are this language's, and they mean something |
| `plotshape-to-marker` | `plotshape(` → `marker(` | `plotshape` is a documented alias with Pine's shape names |
| `hline-to-level` | `hline(` → `level(` | `level(...)` **does not exist**; `hline` is the real name |

The last one is worth dwelling on, because the same mistake was in three other places at once: the
prompt kit taught `level(...)` and `ownPane = true`, and two of the twelve templates called them.
All four files had been written from the same wrong memory of the language, and none of them had
been run. They are all run now.

The same test also found that every rule could see into string literals, which meant «Persian
numerals in code» would have rewritten the numerals in a reader's own `text = "۳ کندل"` label. Every
rule is now blind to strings and to comments, with the two header rules opting back in because a
header *is* a comment.

## Sixty-one strategies, generated in two languages from one table

The brief asks for sixty with `signal()` and a default stop, and the same sixty in English. Writing
a hundred and twenty scripts by hand would mean keeping a hundred and twenty in step; they would not
stay in step. So each entry carries only the parts that differ — the titles, the sentence each
signal says — and `source(english)` assembles the one shape they all share. The test that matters is
`the two languages compute exactly the same thing`: both renderings with every quoted string blanked
out must be identical character for character.

Three things the tests caught in the content:

* `volume-spike` never fired, because the fixture's volume was bounded and had no spike in it. A
  fixture with no spike cannot tell a working volume study from a silent one. The fixture now has
  them.
* `golden-cross` fired only one way, because a two-hundred-period average on four hundred bars
  crosses about once. The fixture is twelve hundred bars now — a fixture shorter than the slowest
  study in the library cannot tell a one-sided strategy from a one-sided sample.
* Every condition ends with `and confirmed`, which is the fault every script in the old library had:
  an arrow painted on the forming bar can be gone a minute later, and a reader scrolling back sees
  arrows only on the turns that worked.

## The prompt, and the invisible characters that must not travel with it

The prompt is generated from `ScriptReference` rather than written beside it, so the third place the
language gets described — the one that always goes stale — does not exist.

Rendering it was its own problem. It is Persian sentences with Latin code inside them, which is
exactly what bidi reordering mangles: forced left-to-right, every Persian full stop landed at the
wrong end; left to the paragraph's own direction, `//@version` rendered as `version@//` and the list
of series read backwards. The fix is per-line direction plus an LRI isolate around each ASCII run —
**applied on the way to the screen only**. The clipboard gets the raw prompt, because an isolate is
a control character and one pasted into an assistant would sit inside `ta.ema(close, 20)`.
`ScriptPromptDisplayTest` holds that stripping the isolates gives the prompt back exactly.

## What is not here

Two of the brief's seven parts, and they are in `CHECKLIST.md` and `BLOCKED.md` as ❌ rather than
folded into a ✅:

* **Save as mine (C)** is a model with no home. `ScriptDocument`, `ScriptFile` and `ScriptLink` are
  written and tested — twenty cases, including an exported `.nama` file that is itself a runnable
  script and a link reader that refuses nine hostile forms — but `saved_scripts` has no columns for
  a colour, tags, a description or a history, and adding them is a hand-written Room migration.
* **Share (D)** needs a community surface to share into, which is S7 in Σ4. What Σ1 leaves for it is
  the address format, and the reason it carries an id and never source: a link that carried code
  would be running a stranger's script on the strength of a tap.

## Counts

| | |
|---|---|
| Repairs in the table | 20 |
| Templates | 12 |
| Strategies in the library | 61, over six families, in two languages |
| Assistant answers in the paste suite | 30 |
| New tests | 100 in `:namascript`, 8 in `:core:script`, 7 in `:feature:script`, 4 proof frames |

## Σ1 addendum — «save as mine» wired (4.82.1)

4.82.0 shipped `ScriptDocument` as a tested model with nothing behind it. This finishes it.

**The migration.** `saved_scripts` gains six columns — description, colour, tags, pane, public id,
history — as six `ALTER TABLE … ADD COLUMN … NOT NULL DEFAULT`. No rebuild, so nothing is copied
and nothing can be lost in the copying; a script saved in 4.73.0 comes back with its name and its
source, gold, untagged and with no history, which is exactly what it was. The statements live in
`SAVED_SCRIPT_COLUMNS` so `SavedScriptMigrationTest` can run them against a seeded version-6 table
without Room, an emulator or a device — the same arrangement `MIGRATION_5_6` has.

**The history, and the assumption that was wrong.** Five revisions in one column, pushed on a save
that changed the source and on no other event. The first encoding separated records with U+001E and
U+001F, on the reasoning that NamaScript cannot contain them. `ScriptHistoryTest` was written to
confirm that and disproved it: the lexer refuses a control character in the code and **accepts one
inside a string literal**, which is exactly where a reader's own label lives. A single stray
character in a title would have split a record and taken every earlier version after it. The column
is length-prefixed now — `at:length:source`, the source never scanned — so nothing is forbidden and
nothing has to be escaped. The test stays, inverted, as the record of why.

**The panel, and where it sits.** «اسکریپت من» carries the description, a colour swatch row, tags,
the pane switch, the five versions and export / import / copy-link. It went in above the inputs and
came out below them: `StudioProofTest.studioInputs` failed because «ورودی‌ها» was no longer
composed — pushed off the bottom of a phone — which is the right answer to the wrong order. The
inputs are touched on every run and this panel about once per script.

**The link.** `pro-chart.com/s/<id>` is claimed in the manifest and read by `parseCoineProDeepLink`,
which checks the host and then hands the id to `ScriptLink.idOf` — the language's own rule, so the
app and the language cannot disagree about what an id is. What it opens is the reader's own script,
matched on the public id stored beside it. A link from anyone else names something this device does
not have and the app says so. It does not fetch, because there is nothing to fetch from yet, and it
will never carry source: the whole point of an id is that the code arrives from somewhere the reader
can be shown it before anything is added.

| | |
|---|---|
| New columns on `saved_scripts` | 6, by `ALTER TABLE`, no rebuild |
| New tests | 5 migration, 10 controller, 3 history, 4 deep-link, 1 proof frame |

# Σ2 — the doctrine's gates (4.82.2)

Four of the ten principles had no gate. `docs/DOCTRINE.md` said so in its own status column, which
is the only reason they were findable rather than assumed — and which is the doctrine working on
itself before anything else in this phase did.

## D10, and what it found in the first hour

`check-checklist-honesty.py` walks every `docs/runs/**/CHECKLIST.md` and fails a ✅ whose Evidence or
Frame cell points at nothing. `—` counts as nothing: a dash is the shape a blank takes once somebody
has been told not to leave one.

It failed twice on this run's own checklist. First on S9 — «this document, `REPORT.md`,
`BLOCKED.md`», ✅ with a bare dash for a frame, written by the same hand that wrote the rule. Then on
a Σ2 row whose Frame cell said «**A gate**», which is three words and not an explanation. Both are
fixed above, and neither would have been noticed by reading.

That is the whole argument for D10 being a script rather than a habit: the failure mode is not
dishonesty, it is a cell somebody filled in while thinking about something else.

## D4, and a bug in the test rather than the app

`NavigationDepthTest` reads every `composable(route = …)` **out of `CoineProApp.kt`** rather than
from a list beside it. A second list is one that can be shorter than the graph without anything
failing, which is exactly the hole D4 exists to close.

It reported five unreachable routes. Four were real and correctly deeper — a portfolio report, the
two legal documents, and diagnostics, which is not in the store build's menu because it is not for
readers — and each now carries a sentence saying so, with a test that the sentence is long enough to
be one. The fifth, `AI_PATTERN`, was the test's fault: `"ai"` and `"ai?symbol={symbol}"` are one
screen and the comparison said otherwise. A gate that reports a false positive on its first run is a
gate nobody will trust on its tenth, so the query is stripped before the comparison.

## D6, three claims instead of one

The brief said «the `CoineProHaptics` call-site count», which on its own is a number that only ever
goes up. `check-haptic-policy.sh` holds the two claims underneath it as well:

* **One vocabulary.** Nothing outside `CoineProHaptics` may call `performHapticFeedback` or reach
  for `LocalHapticFeedback`. There are five words; a sixth invented at a call site is a buzz a
  reader cannot learn, and it looks like every other line in review. There are currently none.
* **The primitives keep theirs.** Five components — the buttons, the confirm dialog, the market row,
  pull-to-refresh — carry the haptics that every screen using them inherits. One of those losing its
  call would silence hundreds of call sites in a single edit with nothing else failing.
* **The count does not collapse.** A floor of forty against the ninety-five that exist.

## D3, which is not a stopwatch

Sixty seconds depends on a device, a network and a person, and no unit test holds it. What
`SixtySecondsToMeaningTest` holds is everything that makes sixty seconds possible: the start
destination is not a sign-in wall, the chart route asks for no session, and the **default** studies
produce a sentence and a **non-neutral** state on a two-hundred-bar first fetch and on a forty-bar
half-loaded one, in both languages.

The non-neutral assertion is the one with teeth. A default set where every study shrugs is a chart
that has told a new reader nothing, and it would look perfectly reasonable in a diff.

## What Σ2 does not claim

D7 and D8 are still ❌, and they stay ❌ until Σ3 builds the features they are about. A gate over a
feature that does not exist is a green check measuring nothing — which is the exact failure D10 is
for.

| | |
|---|---|
| Gates added | 2 scripts, 2 test classes (12 tests) |
| Wired into | `android-ci.yml` as named steps, `android-apk.yml` in its gate block |
| Principles with a gate | 8 of 10 |
| Found by the new gates, in their own repository | 2 hollow checklist cells, 4 undocumented deep routes |


# Σ3 — a reason to come back (4.83.0)

## The part that is not a card

Two cards went on Home: «وقتی نبودید», which says what the reader's own watchlist did while they
were away, and today's challenge, which is one task and a streak. Drawing either is an afternoon.
The work is in the four rules that keep them off the screen.

A card that greets a reader every morning is not a feature with a small flaw — it is worse than no
card at all, because it teaches them that the top of Home is noise, and after about four days they
stop looking at it. Everything above the fold then costs more than it earns. So `ReturnLoop` is a
decision object rather than a layout, and most of `ReturnLoopTest` is about silence:

* **Under four hours away is not an absence.** Somebody who checked the price before lunch is not
  «back».
* **A move under two per cent is not news.** One per cent is about the width of the candle it
  happened in. A card announcing it would be a card announcing nothing.
* **A first launch has been away from nothing.** Zero means never, not «a long time».
* **Offline is the quiet case, not a broken one.** No quotes means no movers and no counts, and the
  card is absent — «۰ سیگنال» reads as a claim about the market when it is a claim about the radio.

The third proof frame is the one worth having. `sigma3-return-loop-quiet-phone-fa.png` is the same
Home, the same fixture and the same movers, four minutes after the last visit instead of fourteen
hours, with neither card on it. Without that frame the other two prove only that a card can be
drawn.

## Where the last visit is stamped

On the way *out* of the shell, in an `onDispose`, not on the way in. A visit recorded when Home was
composed would make «since your last visit» measure from the moment the card was drawn — a window of
zero, and a card that can never appear. `LastVisitStore` keeps two timestamps for a related reason:
the visit is the clock, and the acknowledgement is a different moment, because a reader who reads
the card and comes back an hour later must not be shown the same three movers as news.

## Twelve challenges, and why the walk has a stride

One task a day, the same for everybody, decided by the date and nothing else. Not random — a reader
who closes and reopens the app would get a different task, which makes the whole thing feel
arbitrary. Not personalised — the app is in no position to know what somebody needs to practise.

The table is walked at a stride of seven over twelve entries rather than by the day number, because
the two are coprime: consecutive days land seven places apart, the cycle is the full fortnight, and
the three scripting tasks do not arrive on the three consecutive mornings a reader happens to open
it. A wrong clock reaching a negative epoch day uses the mathematical modulo, not Kotlin's
remainder, which would otherwise throw.

`challengeRoute` maps each challenge's surface to this graph, exhaustively on the enum, so a
thirteenth challenge with nowhere to go fails to compile rather than opening the profile screen. The
arena maps to the chart deliberately: the replay game lives there, behind its own button.

## The twelve sentences the string lint cannot see

They were written informally — «یک سیگنال را باز کن», «دیده‌بانت را مرتب کن» — and the reason is
worth writing down, because it is structural rather than careless. These are the only pieces of
interface copy in the product that live in Kotlin rather than in `strings.xml`, and
`tools/i18n/lint_strings.py` — the thing that holds the entire app to «شما» — reads `strings.xml`.
Copy that steps outside the resources steps outside the rule with it.

All twelve are now the formal plural, and `ReturnLoopTest` carries the lint's own patterns so they
stay that way. A card reading «باز کن» directly beneath one reading «وقتی نبودید» is two different
applications stacked on top of each other.

## What Σ3 does not claim

**The signal and alert counts come from the shell's own state.** The fields are real and the card
renders them, but nothing counts a signal that fired while the app was closed. That is background
work, and the claim is not made.

**The streak is the arena's.** It counts days a reader played a replay round — something they did.
A number that went up for opening the app would be a number about the app.

**D8 is half a principle.** The archive round-trips, export and import are wired into the profile,
and nothing syncs to a server. S6's row and D8's row both say so.

| | |
|---|---|
| New | `ReturnLoop`, `LastVisitStore`, two Home cards, `challengeRoute` |
| Tests | `ReturnLoopTest` (21), `ReaderArchiveTest` (22), `ReturnLoopProofTest` (3 frames) |
| Principles with a gate | 10 of 10 — D8's is honest about covering only half of its rule |
| Frames | `sigma3-return-loop-phone-fa.png`, `-phone-en.png`, `-quiet-phone-fa.png` |

# Σ5 — the big glass, and what rendering it found (4.84.0)

## Two bugs, both of which had been there for months

S8's line is «all of the above on tablet, parity matrix 100 %», and the honest way to do it is not
to reason about layout — it is to render the surfaces at the panels' real dp and look. Nineteen
renders later, two things were broken, and neither was new:

**Every sheet whose body scrolls crashed above 840 dp.** `CoineProSheet` becomes a capped dialog on
an expanded window, and that branch wrapped the body in a `verticalScroll`. A scrolling container
measures its child with an unbounded height, so a body that scrolls itself throws — «Vertically
scrollable component was measured with an infinity maximum height». That is the paste panel, the
alert editor, the screener's filters, the webhook sheet and several more: not a cosmetic defect, a
crash on open, on every tablet, since the dialog branch was written. Nothing caught it because no
sheet body had ever been rendered above phone width.

The fix is one word: the dialog does not scroll. The phone's `ModalBottomSheet` does not scroll its
content either — a body either scrolls itself or is short enough to fit — so this is the same
contract in a narrower window rather than a new rule. `SheetShapeTest` now opens a scrolling body at
1280 dp, which is the test that would have caught it.

**The dashboard filled a twelve-inch panel.** Home is a column of cards, and every card was the full
width of the device: on a Galaxy Tab S9 Ultra, a watchlist row with its symbol at one edge and its
percentage at the other, nearly two thousand dp apart, and a «انجامش بدهید» button the width of the
glass. The «since your last visit» card — three symbols, three figures, one row each — is simply
what made it impossible to keep looking past.

Capped at `CONTENT_MAX_WIDTH` (720 dp) and centred. A phone is narrower than the cap, so nothing on
a phone moved, and `ContentWidthTest` asserts both halves: capped on the widest panel, untouched at
411 dp. The second assertion is the one that matters — a «fix» that narrowed the phone would be a
worse bug than the one being fixed, and it would look fine in a diff.

## Why the parity matrix is generated

`gen_parity_matrix.py` reads `@Config(qualifiers = …)` out of the test sources. A matrix typed by
hand says what somebody hoped; this one cannot claim a render that does not exist, and the
consistency gate fails when the committed copy is stale. Σ's surfaces now have their own row —
«script studio» — filled at all six windows, and Home's return-loop frames join the watchlist row.

## The web plan, and the rule that made it short

`docs/web/PLAN.md` §3b lists every surface Σ built. For eleven of them the answer to «what would a
browser need» is *nothing*: `ScriptPaste`, `PineTranslator`, `ScriptTemplates`, `ScriptPromptKit`,
`ScriptLibrary`, `ScriptDocument`, `ReturnLoop`, `ReaderArchive` and the rest are all `commonMain`.
`LastVisitStore` is nine lines of `localStorage`. The share link needs the service the phone is
already waiting for, and on the web that address is not a deep link at all — it is a page, and the
natural home for S7's community surface.

That is not web-mindedness for its own sake. `ReturnLoop` returns `null` rather than a card;
`ScriptPaste` returns a list of fixes rather than a sheet; the library returns source rather than a
chart. Writing the decisions away from the screens is what made all of them testable off a device —
which is the only reason run Σ has gates at all.

## What Σ5 does not claim

Robolectric renders at a panel's dp are not a tablet. They found both bugs, which is the argument
for them, and they are still not a device. The hinge cannot be rendered at all: the fold columns are
the Pixel Fold's two displays at their dp plus pure assertions about the posture, and
`PARITY_MATRIX.md` says so in its own words.

| | |
|---|---|
| Renders added | 19, at four windows, both languages |
| Bugs found by rendering | 2, both pre-existing, both with a gate now |
| Gates added | `SheetShapeTest.a sheet whose body scrolls itself opens on a tablet`, `ContentWidthTest` (2) |
| Parity matrix | regenerated; «script studio» filled at all six windows |

# Σ4 — a script on the board (4.85.0)

## The thing that was waiting on nothing

S7 read «community scripts» and the plan had it blocked on a service. So did item D of Σ1 —
«share → a community post with the code, a chart snapshot and a one-tap install» — which the Σ1
checklist named as the one part it could not build.

It was blocked on the wrong thing. **The board is real**: `core:community` talks to a live feed with
posts, categories, replies, reactions, a leaderboard and pictures, eight thousand characters a post.
A `.nama` document is text. Everything item D asks for was already there.

## Why the post carries the code rather than a link

The obvious shape for a one-tap install is an address — `pro-chart.com/s/<id>`, which `ScriptLink`
already spells. Two things rule it out, and only the first is temporary.

There is no service behind that address; a link that opens nothing is worse than no link. And the
board **refuses links**, server-side, along with phone numbers and messenger handles, each with its
own Persian sentence. A share that put an address in the body would be refused at the door — today,
and equally after the service is built.

So a shared script is a post whose body is a sentence and then the document. The document is the
same comment-headed `.nama` file `ScriptFile` already writes, which is *also* runnable source — the
reason that format was chosen in Σ1 turns out to be the reason this works now. The install is local:
the thread recognises a post that carries a document and offers to open it in the studio. Nothing is
fetched, nothing runs on the tap, and the code a reader installs is the code they can read in the
post above the button.

`ScriptShare.refusals` checks the three rules before the round trip, so a script whose own comment
carries a URL is reported in the studio instead of discovered from a rejection. It deliberately does
not claim to match the server — the server decides, and a body this finds nothing in can still be
refused for a reason this build has never heard of, which is why the refusal path keeps the reader's
text in the composer. One subtlety worth the test it has: `//@version=5` rides in on nearly every
script an assistant writes, and reporting it as a handle would put a warning on a post the board
would have accepted. A warning that cries wolf is worse than no warning.

## The bug the frame found

The first render of a shared post came back with every line reversed: `// nama 1` read «1 nama //»,
`plot(rsi)` read «(rsi)plot». A post sharing a script is Persian prose with a Latin file under it,
which is precisely the shape bidi reordering gets wrong — the neutral characters around a Latin run
are pulled to its far side in a right-to-left paragraph.

Run Ω-FIX solved this once, for the prompt kit, and the fix lived in `feature:script`. It moved to
`BidiText.isolateCode` in `core:common` and now serves both. Display only, as before: an isolate is
an invisible control character, and a reader who copies the post must get what the author wrote.

## What Σ4 does not claim

**A scripts tab of its own is not built.** The board filters by five categories the server owns and
a sixth is a server change. What exists instead is that a shared script is a first-class post
anywhere on the board.

**No ranking, no verification, no badge.** A script in a post is exactly as trustworthy as the
person who posted it, which the reader can see. A mark here would be the app vouching for code it
has not run.

**Nothing was posted to the real board from this container.** The composition and the reading are
tested and photographed; what the server does with a given body is the server's, and the app reports
its sentence rather than predicting it.

| | |
|---|---|
| New | `ScriptShare`, `BidiText.isolateCode`, the share button, the open button, the pre-filled composer |
| Tests | `ScriptShareTest` (11), `ScriptShareProofTest` (3) |
| Bug found by the first frame | code on the board read backwards; fixed for the board and the prompt kit at once |
| Service needed | none |

# Σ-FIX — the owner's review of 4.85.0 (4.86.0)

Twenty-four frames, seven items back. Two were real defects, two were things the app does and could
not prove, two were features worth having, and one was a zip file.

## The code on the board, and the third attempt at it

A shared script rendered as one right-to-left paragraph came out with every line's leading token at
the far end: `// nama 1` read «1 nama //». Σ4 fixed that by wrapping the code runs in bidi isolates,
which straightened the tokens — and left the file wrapped, right-aligned and carrying invisible
control characters. Better, and still not code.

A code block is four decisions that travel together, and the fourth is the one everybody forgets:

* left to right **as a layout direction**, not only a text one, or the block is pushed to the right
  edge of the card;
* monospace, at the editor's own size and line height;
* no wrapping, with a horizontal scroll, because a wrapped line of NamaScript reads as three
  statements;
* and the Persian inside it **left alone** — in an LTR paragraph the bidi algorithm already puts a
  Persian comment where it belongs, and isolating it by hand reproduces the original bug.

That is `CoineProCodeBlock`, and both the board and the prompt kit now use it. The post is split into
prose and file by `ScriptShare.split`, which is the screen's half of the same decision: they are read
in opposite directions and were being drawn as one paragraph.

The test asserts the *absence* of an isolate in the drawn text rather than the presence of the
words, because `// nama 1` matches either way and only one of them is a code block.

## A Persian ask and an English specification

The owner's item 2: «مدل‌ها با مشخصات انگلیسی کد دقیق‌تری می‌دهند». That is right, and the reason is
worth writing down — the reader and the model are two audiences and only one of them is Persian.
Every assistant a Persian reader reaches was trained overwhelmingly on English technical text, and a
specification in Persian comes back as Pine with Persian titles.

So the prompt is now three lines of Persian ask and one generated English specification, shared word
for word between both prompts (one language, one description of it) with the reader's chart named
inside the English half. It also says, explicitly, that titles and signal text may stay Persian —
without that line the English spec quietly asks for an English legend, which is run G's leak
arriving through a prompt.

## The two the review could not see

Marker labels at 4, 8 and 16 dp have had seven frames since Σ0, and the documents — `DOCTRINE.md`,
`CHECKLIST.md`, `REPORT.md`, `BLOCKED.md` — have been in the repository the whole time. Neither was
in the package that was handed over. That is D10 defeated by a zip file: a reviewer with frames and
no documents cannot check a claim against what the run says about itself, and «it is in the repo» is
not an answer when the deliverable is what somebody was given.

## The install, and the number that is not there

«به چارت من اضافه کنید» is now the primary button on a post that shares a script, with the studio
second — 4.85.0 had it the other way round, which made a reader who wanted the indicator walk
through an editor to get it. It is the same `putScript` call the studio's own button makes, so an
installed script is one of the reader's indicators in every sense that matters.

What is deliberately absent is the counter. How many people installed a script is a fact about
everybody; this device knows what this device did. Printing the local number under the word «نصب»
would be a private figure wearing a public label, which is the failure D2 exists to forbid arriving
through a feature instead of a percentage. `ScriptInstallStore` remembers this device's own installs
and the card says exactly that. BLOCKED item 8 names the endpoint.

## The tablet, and the correction to the correction

4.84.0 capped Home's column because a watchlist row on a 1973 dp panel had its symbol at one edge
and its figure at the other. That was right and it was half the answer: a 720 dp column in the
middle of a twelve-inch panel with both thirds empty is not restraint, it is a phone screen on a
tablet, which is what the review said.

Home now puts the pairs that belong together side by side — «وقتی نبودید» beside «امروز», the
balance beside the markets — and caps the *pair* at 1160 dp, which is two sheet-widths and a gutter.
`ContentWidthTest` holds both halves, and the second is the one that matters: on a phone they stay
stacked, because two 180 dp columns at 411 dp is two cards nobody can read.

## And the guest who signs in

S6 claimed export, import and migration. The first two had a round-trip test and no frame; the third
had neither. Both are closed: the profile's backup rows are in a frame with their own note about
what is *not* in the file, and `GuestMigrationTest` holds the migration — a guest's list survives
their first sync, an account's markets are added rather than substituted, and an empty account takes
nothing away.

The reason it holds is worth stating because it is structural rather than careful: **there is no
account-scoped store in this app.** Signing in changes who the server thinks you are and changes
nothing about what is on the phone. The migration is not a step that runs; it is the absence of a
step that would destroy something — which is exactly the kind of property that quietly stops being
true, and now has a test.

| | |
|---|---|
| Items | 7 of 7 closed; 2 with a named limit (public install count, server sync) |
| New | `CoineProCodeBlock`, `ScriptShare.split`, `ScriptPromptKit.spec`, `ScriptInstallStore`, two-column Home |
| Tests | `GuestMigrationTest` (3), `ContentWidthTest` (4), 4 new in `ScriptShareTest`, 4 in `ScriptPromptKitTest`, 2 in `ScriptShareProofTest` |
| Frames | the thread with its code block and its install button, the prompt kit, tablet Home in two columns, the guest's backup rows |

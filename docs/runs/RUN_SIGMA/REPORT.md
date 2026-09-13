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


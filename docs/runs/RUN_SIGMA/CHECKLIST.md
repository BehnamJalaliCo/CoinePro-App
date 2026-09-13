# RUN Σ — the second half, line by line

`✅` means done and proven by the evidence beside it. `❌` means not yet — including anything that
was narrowed, per R3. **`⏳ owed to device` is neither**: it means the code is in and the only thing
that can finish the claim is a person with a phone, and it stays that way until the owner's
recording lands.

The Frame column is not optional and is never blank. Where a claim is not the kind of thing a still
frame can carry — a gesture, a haptic, a spring — the cell says so and names the device-independent
proof instead. That rule came out of run Ω-FIX, where a ✅ with a sentence behind it turned out to be
false on the device in every frame of two recordings.

| # | The line | State | Evidence | Frame |
|---|---|---|---|---|
| S1 | Horizontal pinch on the plot zooms time; vertical does too with auto-scale on; the price gutter scales price only | ✅ code, ⏳ owed to device | `PinchZone` + `pinchZoneOf` route by **start location only**; the plot's zoom is the Euclidean distance ratio at any angle. The bug was a per-frame `1.0025.pow(Δspan)` behind a one-per-cent dead zone — arithmetically unreachable at any human finger speed. `ChartPinchTest` (6) injects two real pointers; `PinchZoneTest` (6) walks every point of the canvas | **A gesture, so not a still.** Six injected-pointer tests, one of them through the whole page to prove nothing above the plot steals the second finger. The owner's recording is what closes it |
| S2 | Buy/Sell markers carry labels per the density rules; goldens at 4/8/16 dp | ✅ | `ChartMarker.label` + `TradeSide.action`; `SignalMarkers.detailFor` — label at ≥ 12 dp a bar, triangle 6–12, one per ten-bar swing below 6; `sizeDpFor` gives 6/8/10 dp by strength; labels skip the legend plate, the plot's edges and each other. Per-study «با برچسب / فقط مثلث / خاموش» in the Explain sheet. `SignalMarkersTest` (13) | `sigma0-markers-{16,8,4}dp-phone-fa.png`, the same three on a tablet, `sigma0-markers-16dp-phone-en.png`, `sigma0-markers-triangles-phone-fa.png` |
| S3 | BYO Script end to end | ✅ | Σ1 below | `sigma1-paste-fixes-phone-fa.png`, `sigma1-paste-templates-phone-fa.png`, `sigma1-prompt-kit-phone-{fa,en}.png`, `sigma1-my-script-phone-fa.png` |
| S4 | `docs/DOCTRINE.md` with D1–D10, each with a CI gate | ✅ for nine; D8's row is ✅ for export and import and ❌ for server sync, and says so | Σ2 below | **Gates, not pictures** — the evidence is that they run and fail: `check-haptic-policy.sh`, `check-checklist-honesty.py`, `NavigationDepthTest`, `SixtySecondsToMeaningTest`, all four in CI |
| S5 | Retention loop | ✅ | Σ3 below. `ReturnLoop` decides; Home draws. The card is absent on a quiet morning, which is most of what `ReturnLoopTest` (21) is about | `sigma3-return-loop-phone-fa.png`, `sigma3-return-loop-phone-en.png`, and `sigma3-return-loop-quiet-phone-fa.png` — the same fixture four minutes later, with neither card |
| S6 | Account value: sync, export/import, guest migration | ✅ for the archive, for export/import and for guest→account migration; ❌ for layouts, the journal and server sync | `ReaderArchive` + `ReaderArchiveTest` (22) is D8's gate — write, export, wipe, import, compare, per store. The profile carries «پشتیبان‌گیری» and «بازگرداندن» for a guest, who needs them most because nothing of theirs is on a server. Import merges and never duplicates | **A round trip, so not a still.** The test is the evidence; the row's own note on the profile says what is in the file and what is not |
| S7 | Community scripts | ✅ for sharing, reading and one-tap installing on the board this app already has; ❌ for a scripts tab of its own and for a public install count | Σ4 below. A shared script is a post: the code in the body, the install a local read of it. No new service, because the board is real and `pro-chart.com/s/<id>` is not | `sigma4-share-mine-phone-fa.png`, `sigma4-share-thread-phone-fa.png` |
| S8 | All of the above on tablet, parity matrix 100 % | ✅ | Σ5 below. Sixteen renders of Σ's own surfaces at four windows; two real bugs found by rendering them, both fixed with a gate each; `PARITY_MATRIX.md` regenerated and now carries a «script studio» row filled at every window | `sigma-home-loop-{pixel-tablet,tab-s9-ultra,tablet-portrait,fold-open,fold-closed}-*.png`, `sigma-studio-*`, `sigma-paste-*`, `sigma-prompt-*`, `sigma-mine-*` |
| S9 | This document, `REPORT.md`, `BLOCKED.md` | ✅ for Σ0 and Σ1 | The Σ0 and Σ1 sections below, and `REPORT.md` | **The documents are the evidence, not a subject of it** — and from 4.82.2 they are checked by `scripts/quality/check-checklist-honesty.py`, which fails on a ✅ whose Frame or Evidence cell points at nothing. It found this row |

## Σ0, item by item

The hotfix the owner asked to be shipped immediately, so the next recording can settle S1.

| Σ0 item | State | Evidence |
|---|---|---|
| Gesture routing by **start location only**; finger orientation is never a discriminator | ✅ | `pinchZoneOf(frame, timeAxisTop, start)`. `PinchZoneTest.every point on the canvas resolves to exactly one zone` sweeps the whole canvas rather than three corners, because a routing rule with a gap in it is a gesture that does nothing — which is what was reported |
| Plot pinch: `zoom = currentDistance / previousDistance`, Euclidean, any angle | ✅ | `PointerEvent.pinchSpan()` — the mean distance of the pressed pointers from their centroid. The old `axisSpan(vertical)` threw the direction away twice over: once into a per-axis scalar and once into a power function |
| `barSpacing *= zoom`, clamped 0.5..50 px | ✅ | `ChartViewport.zoomedBy` then `clampedToBarSpacing`, which already held `MIN_BAR_SPACING_PX = 0.5f` and `MAX_BAR_SPACING_PX = 50f` |
| Anchored so the bar under the centroid stays under it | ✅ | The `focal` share passed to `zoomedBy`. `ChartPinchTest.the bar under the fingers stays under the fingers` bounds the drift, and `where the fingers are decides what stays on screen` proves the anchor by contrast — the same pinch at the left and at the right cannot produce the same window |
| Simultaneous pan by the centroid delta | ✅ | Unchanged: `detectTransformGestures` still owns the pan and this handler still consumes nothing |
| Vertical pinch on the plot zooms time with auto-scale on; scales both with it off | ✅ | The `PinchZone.PLOT` branch zooms time always and adds `priceZoomedBy(ratio)` only when `priceZoom != 1f` and `priceBarLock` is off — the lock already moves the price inside `zoomedBy` and doing it twice would double the rate. `ChartPinchTest.fingers moving apart vertically on the plot also show fewer bars` |
| Two pointers in the price gutter: price changes, bar count does not | ✅ | `ChartPinchTest.fingers in the price ladder scale the price and leave the bars alone` |
| Nothing above the plot may consume a second pointer | ✅ | `ChartPinchTest.nothing stacked above the plot steals the second finger` drives the **whole page** through the root — no test tag to aim at — and asks the controller what window it ended on |
| A «horizontal pinch» Macrobenchmark scenario | ✅ code, ⏳ owed to device | `ChartFlingBenchmark.horizontalPinch` and `verticalPinch`, with an explicit `performMultiPointerGesture` path kept clear of both gutters. `UiObject2.pinchOpen` could not stand in: it pinches against the object's bounds and spends part of its travel on the price ladder. The module's own dependencies are not in this container's offline cache, so it has never compiled here — see `BLOCKED.md` |
| Marker = triangle + «خرید» / «فروش», 11 sp Medium, 4 dp pill | ✅ | `drawMarkers` / `drawMarker` in `CoineProChart`; the word is the stage colour on a plate in the study's own, because four characters of one hue on a plate of the same hue is a contrast ratio of about two |
| Buy label under the bar low, sell label above the bar high | ✅ | The label goes on the far side of the glyph from the candle, so it never lands between the mark and the bar it is about |
| Label at ≥ 12 dp a bar; triangle only at 6–12; one per swing below 6 | ✅ | `SignalMarkers.detailFor` + `thin`. `SignalMarkersTest.the rule is monotonic` walks the whole range: a threshold entered the wrong way round passes every named case and fails that one |
| Labels never overlap the last-price label, the legend plate or each other | ✅ | The plot's own bounds (the price tag is in the gutter, which is off the plot), a legend guard rectangle, and a list of claimed rectangles that grows as labels are placed. The older signal keeps its word and the newer one loses it, which is the only tiebreak that does not leave both illegible |
| Marker size 6/8/10 dp by signal strength | ✅ | `SignalMarkers.sizeDpFor(strength)`, fed by `SignalEvent.strength` through `ChartMarker.strength` |
| Per-study setting: labels / triangles only / off, default labels | ✅ | `MarkerStyle` + `ChartUiState.markerStyles` + `ChartController.setMarkerStyle`; three chips at the foot of the Explain sheet. Session-scoped, beside `hiddenIndicators`, for the same reason: it is «what I want to see right now», not how the study is set up |
| Goldens at barSpacing 4, 8, 16 dp on phone and tablet | ✅ | `SignalMarkerProofTest` — seven frames |

## Σ1, item by item — bring your own script

The brief's seven parts (A–G). Five are in; two are named honestly as not.

| Σ1 item | State | Evidence | Frame |
|---|---|---|---|
| **A** Paste entry in the studio; the dialect is detected, never asked for | ✅ | `ScriptPaste.dialectOf` — Pine only on what is Pine's *alone*, prose by exclusion. The «چسباندن» button sits above the snippets in the editor, not behind a menu | `sigma1-paste-fixes-phone-fa.png` |
| **A** Pine → NamaScript with a diff of what could not be carried | ✅ | `PineTranslator` first, then the repair table; `Paste.unsupported` is rendered as monospace lines under the fixes rather than dropped | `sigma1-paste-fixes-phone-fa.png` shows the fix list; the unsupported block is on the same panel when a paste has any |
| **A** Twenty one-tap fixes for the commonest assistant mistakes, undoable | ✅ | `ScriptPaste.RULES` (20). Applied before the sheet opens and reversed in one tap by `ScriptController.undoPaste`, which is why `pastedRaw` is kept. `ScriptPasteTest` (30), `ScriptPasteControllerTest` (8) | `sigma1-paste-fixes-phone-fa.png` — three fixes, each with its line number and «برگرداندن اصلاح‌ها» beside «همین خوب است» |
| **A** Free text → a picker of twelve templates, pre-filled | ✅ | `ScriptTemplates.ALL` (12) matched on keywords in either language; `numbersIn` reads Persian and Latin digits and fills the holes in order. `ScriptTemplatesTest` (17) compiles and runs all twelve over a real series | `sigma1-paste-templates-phone-fa.png` |
| **B** A copyable prompt built from the language's own reference, versioned | ✅ | `ScriptPromptKit.prompt(symbol, timeframe, english)`, generated from `ScriptReference` so it cannot go stale; `VERSION` rides in its own text. `ScriptPromptKitTest` (11) holds that every name it teaches is one `Builtins.call` answers and that all three examples run | `sigma1-prompt-kit-phone-fa.png`, `sigma1-prompt-kit-phone-en.png` |
| **B** «Copy prompt», «Open ChatGPT», «Open Claude», «Paste result» | ✅ | The four buttons on the prompt sheet. The clipboard gets the **raw** prompt and the screen gets an isolated copy — `isolatedForDisplay`, with `ScriptPromptDisplayTest` (7) proving the transform is exactly reversible, because an isolate pasted into an assistant would be a control code inside `ta.ema(close, 20)` | `sigma1-prompt-kit-phone-fa.png` |
| **C** Save as mine: name, description, colour, tags, pane, default inputs; five revisions | ✅ | `saved_scripts` grew six columns in `MIGRATION_6_7` — six `ALTER TABLE`s with defaults and **no rebuild**, so nothing is copied and nothing can be lost in the copying. `SavedScriptMigrationTest` (5) runs them against a seeded version-6 table; `ScriptDocumentControllerTest` (10) holds that everything the reader chose survives a save and a reopen | `sigma1-my-script-phone-fa.png` |
| **C** Five revisions, kept on a save that changed the source | ✅ | Pushed in `ScriptController.save`, not on every keystroke, and not on a save that changed nothing. Stored **length-prefixed** rather than delimiter-separated: `ScriptHistoryTest` showed the lexer accepts a control character inside a string literal, so a separator would have split a record on a reader's own label and taken the rest of their versions with it | `sigma1-my-script-phone-fa.png` — the panel with its «نسخه‌های قبلی» section |
| **C** `.nama` export, import from file or clipboard, deep-link install | ✅ export and import; ⏳ install needs a source | Export writes the document to the clipboard, because a `.nama` file is text and a document provider is three taps and a permission away from where the reader is going with it. Import reads a whole document, or falls through to the paste path for a bare script. The link is claimed in the manifest, validated twice and routed — `DeepLinkValidationTest` (4 new) — and it opens the reader's **own** script by its public id; a link from somebody else names a script that is not on this device, and until S7 exists the app says so rather than pretending to fetch | `sigma1-my-script-phone-fa.png` — «خروجی گرفتن», «ورودی گرفتن», «کپی پیوند» |
| **D** Share → a community post with the code, a chart snapshot and a one-tap install | ✅ | `ScriptShare` — the post's body is a sentence and the `.nama` document, and «باز کردن در استودیو» appears on a post that carries one. The picture is the composer's own, which the board already had. `ScriptShareTest` (11), `ScriptShareProofTest` (3) | `sigma4-share-mine-phone-fa.png`, `sigma4-share-thread-phone-fa.png` |
| **E** Every script gets the same confidence and Explain sheet; a hint chip where `signal()` is missing | ✅ | The Signal Layer already treats a script's verdicts like any study's. The hint appears under the editor when a script ran, drew, and said nothing — which is the moment it means something | — **a conditional row in a list**; `script-signal-hint` is its semantics tag |
| **F** ≥ 60 Persian-named strategies with `signal()`, one line each, a default stop; the same in English | ✅ | `ScriptLibrary.ALL` — 61, across six families. Both languages render from one table, so the code either side is identical token for token (`ScriptLibraryContentTest.the two languages compute exactly the same thing`). Every one draws a stepped stop line, speaks in both directions over a 1 200-bar series, and never marks the forming bar. 15 tests | — **a data table**; the tests above are the proof, and the strategies appear in the studio's library tab |
| **G** Thirty real assistant answers either run or produce a one-tap-fixable diagnostic | ✅ | `src/jvmTest/resources/pastes/*.txt` — thirty files, each a header plus what a chat window actually returns. Every one is required to end up running **and drawing**, or to offer a template that does. `ScriptPasteSuiteTest` (4) | — **a file-driven suite** |
| **G** Import/export round trip; deep-link install | ✅ round trip, ❌ install | `ScriptDocumentTest.a script survives the round trip whole` and `the round trip is stable a second time`. The install path has nothing to install *into* until C is wired | — |

### What Σ1 does not claim

**A share link opens only what this device already has.** The address format, the manifest claim,
the two shape checks and the routing are all real and tested; what is behind the id is not, because
there is no service to ask. A reader who exports a script and sends themselves the link gets their
script back. A link from somebody else says so plainly. That stays true.

**Item D was finished in Σ4, and not the way this section expected.** Sharing turned out not to need
a service at all: the board is real, and a `.nama` document is text. The post carries the code —
which is also what the board's own rules require, since it refuses links. `ScriptLink` remains what
it was: an address that carries an id and never source, so a tapped link can never be a script that
ran.

## Σ2, item by item — the doctrine's gates

Four principles were intentions. `docs/DOCTRINE.md` said so in its own status column, which is the
only reason this was findable at all.

| Σ2 item | State | Evidence | Frame |
|---|---|---|---|
| **D3** Sixty seconds to meaning, as a step count rather than a stopwatch | ✅ | `SixtySecondsToMeaningTest` (7). Sixty seconds is not a thing a unit test holds; what it holds is everything that makes it possible — the start destination is not a sign-in wall, the chart route asks for no session, and the **default** studies produce a sentence and a **non-neutral** state on a two-hundred-bar first fetch. The neutral check is the one with teeth: a default set where every study shrugs is a chart that has told the reader nothing, and it would look fine in a diff | **A gate, so not a still.** The forty-bar case is the half-loaded chart a reader actually sees first |
| **D4** Nothing more than one layer from the chart | ✅ | `NavigationDepthTest` (5) reads every `composable(route = …)` **out of `CoineProApp.kt`** rather than from a list beside it — a second list can be shorter than the graph without anything failing, which is the hole D4 exists to close. It found five routes the menu cannot reach; three are correctly deeper (a portfolio report, the two legal documents) and one is deliberately absent from the store menu (diagnostics). The fifth was a bug in the test, not the app: `AI_ROUTE` and `AI_PATTERN` are one screen and differ by a query | **A gate.** Every exemption carries a sentence, and a test checks the sentence is long enough to be one |
| **D6** Every confirmation is a haptic | ✅ | `check-haptic-policy.sh`. Three claims: nothing outside `CoineProHaptics` calls the platform's haptics (a sixth weight invented at a call site is a buzz nobody can learn — there are currently none), the five primitives a screen relies on each still take them, and the call-site count has a floor of forty against the ninety-five that exist, so a refactor cannot silence the app in one edit | **A gate, not a picture** — it fails in CI when the vocabulary widens |
| **D10** No ✅ without a frame or a test | ✅ | `check-checklist-honesty.py` walks every `docs/runs/**/CHECKLIST.md`. A ✅ needs a non-empty Evidence cell and a Frame cell that either names an image or explains, in a sentence, why the claim is not the kind a still frame carries. `—` is treated as blank, because a dash is the shape a blank takes once somebody has been told not to leave one. **It failed on RUN Σ's own S9 row the first time it ran** | **A gate**, and the one that grades the others |
| All four wired into CI | ✅ | `android-ci.yml` runs the two scripts as named steps; `android-apk.yml` runs them in its gate block. D3 and D4 are tests, so `testDebugUnitTest` already carries them | — **CI configuration**; the workflow files are the evidence |

### What Σ2 does not claim

**D7 and D8 had no gate in Σ2 because they had no feature.** A Home test in three data states needs
a Home that has «since your last visit» on it, and a round-trip test per store needs the export to
exist. Σ3 built both, and the gates went in with them — `ReturnLoopTest` and `ReaderArchiveTest`.
Writing either a release earlier would have been a green check measuring nothing, which is the exact
failure D10 is about. D8 is still only half a principle: the archive round-trips, and nothing
syncs.

## Σ3, item by item — a reason to come back

The loop is three parts and a rule about when to keep quiet. The rule is the part with the work in
it: two of the three parts are easy to draw and all three are easy to draw *too often*, which is the
failure that costs more than the cards earn.

| Σ3 item | State | Evidence | Frame |
|---|---|---|---|
| «Since your last visit» above the fold, with what actually moved | ✅ | `ReturnLoop.sinceLastVisit` takes the reader's own watchlist and the feed's own change figures, keeps the three biggest moves either way, and names how many signals fired and how many of their own alerts went off. The sentence is composed at the screen, never in the model — a summary string built here would bake one language's word order into shared code | `sigma3-return-loop-phone-fa.png`, `sigma3-return-loop-phone-en.png` |
| Nothing new means **nothing drawn** | ✅ | Four rules, each with its own test: under four hours away is not an absence; a move smaller than two per cent is the width of its own candle and not news; a first launch has been away from nothing; and offline — no quotes, so no movers and no counts — is the quiet case rather than a broken one, because «۰ سیگنال» reads as a claim about the market rather than about the radio | `sigma3-return-loop-quiet-phone-fa.png` — the same screen, the same movers, four minutes instead of fourteen hours |
| The last visit is measured from the right moment | ✅ | Stamped on the way *out* of the shell rather than on arrival: a visit recorded when Home was drawn would make the window zero and the card would never appear at all. `LastVisitStore` keeps the visit and the acknowledgement as two timestamps for the same reason | — **a lifecycle effect**; `LastVisitStore`'s own note is the record, and the four-minute frame is what it buys |
| One challenge a day, the same for everybody | ✅ | `ReturnLoop.challengeFor(epochDay)` walks a twelve-entry table in a stride coprime with its length, so a fortnight passes before a repeat and three scripting tasks do not arrive together. Not random — a reader who reopens the app would get a different task, which makes the whole thing feel arbitrary — and not personalised, which the app is in no position to decide. Negative epoch days (a wrong clock) use the mathematical modulo, with a test | `sigma3-return-loop-phone-fa.png` |
| Every challenge is a minute's work, in both languages, and lands somewhere real | ✅ | Twelve tasks, each doable today with no account and no money; `challengeRoute` maps the surface to this graph's routes and is exhaustive on the enum, so a thirteenth with nowhere to go fails to compile. The arena is deliberately the chart: the replay game lives there, and a route that did not exist would be a promise the graph cannot keep | — **a table and a mapping**; the two tests are the proof |
| The challenges address the reader as «شما», like everything else | ✅ | These twelve sentences are the only interface copy in the product that lives in Kotlin rather than `strings.xml`, so `lint_strings.py` cannot see them — and they were written informally on the first pass for exactly that reason. `ReturnLoopTest.the challenges address the reader the way the rest of the app does` carries the lint's own patterns | `sigma3-return-loop-phone-fa.png` |
| The streak, and a day's grace | ✅ | The arena already keeps it; `ReturnLoop.streakAfter` is the rule about losing it. One day's grace, because a streak that breaks because somebody was asleep at midnight in the wrong time zone teaches them the number is not about them; two days and it is genuinely over, which is what keeps it worth anything. A clock corrected backwards does not inflate it | `sigma3-return-loop-phone-fa.png` — «۶ روز پشت سر هم» |
| Export and import of everything a reader made | ✅ | `ReaderArchiveFile` — a text format with length-prefixed records, so a journal entry containing the format's own punctuation survives. Import merges without duplicating, never shortens a streak, and refuses a file that is not an archive rather than half-reading it. This is D8's gate | — **a round trip, so not a still**; `ReaderArchiveTest` (22) is the evidence, and S6's row says which stores are in |

### What Σ3 does not claim

**The «since» card counts signals and alerts only when something counted them.** The fields are
real and the card renders them; what feeds them today is the shell's own state, which means a
signal that fired while the app was closed is not in the number. That is a background-work claim,
and it is not made.

**A streak is the arena's, not a login streak.** It counts days a reader played a replay round,
which is a thing they did. A number that went up for opening the app would be a number about the
app rather than about them.

## Σ4, item by item — a script on the board

The brief's S7 is «community scripts», and the plan had it waiting on a service that does not exist.
It was waiting on the wrong thing: **the board is real**, it takes eight thousand characters and a
picture, and a `.nama` document is text. What could not be done was the part nobody needed.

| Σ4 item | State | Evidence | Frame |
|---|---|---|---|
| A shared script is a post, and the post carries the **code** | ✅ | `ScriptShare.post` writes a sentence a reader can act on, then the document. The sentence is first because the board cuts a post at two hundred characters in every list it appears in, and a post opening with `// nama 1` would be a wall of header in the feed | `sigma4-share-thread-phone-fa.png` |
| **Why not a link** | ✅ | Two reasons and the second is the one that settles it: there is no service behind `pro-chart.com/s/<id>`, and **the board refuses links** — server-side, along with phone numbers and messenger handles. A share that put an address in the body would be refused at the door, today and after the service is built | — **a rule, not a picture**; `ScriptShareTest.what this run shares is not refusable` is the assertion |
| The refusal is named before the round trip | ✅ | `ScriptShare.refusals` checks the three rules the board enforces, so a script whose own comment carries a URL is reported in the studio rather than discovered from a rejection. Deliberately not a claim to match the server: it decides, and a body this finds nothing in can still be refused for a reason this build has never heard of — which is why the refusal path keeps the reader's text in the composer | — **a check before a request**; the frame would show a button, not a rule |
| `//@version` is not a messenger handle | ✅ | It rides in on nearly every script an assistant writes. Reporting it would put a warning on a post the board would have accepted, which teaches a reader to ignore warnings | — **a regular expression**; `ScriptShareTest.a version comment is not a messenger handle` |
| The install: «باز کردن در استودیو» on a post that carries one | ✅ | The button appears only when the document actually reads out of the body — not guessed from a word in it — and it opens the editor with the code the reader can see above the button. Nothing is fetched and nothing runs on the tap | `sigma4-share-thread-phone-fa.png` |
| A reader may write as much as they like above the script | ✅ | The header is looked for anywhere in the post, so the best kind of post on this board — somebody explaining what they were thinking, with the file underneath — is not refused for putting its prose first | — **a parser**; `ScriptShareTest` |
| **Bug found: code on the board read backwards** | ✅ fixed | A post sharing a script is Persian prose with a Latin file under it, which is the shape bidi reordering gets wrong: `// nama 1` rendered «1 nama //» and `plot(rsi)` rendered «(rsi)plot». The prompt kit's fix from Σ1 moved into `BidiText.isolateCode` and now serves both. Display only — the post's own text is untouched, so a reader who copies it gets what the author wrote | `sigma4-share-thread-phone-fa.png` is the after; the before is in `REPORT.md` |
| A scripts **tab** of its own on the board | ❌ | Not built. The board filters by five categories the server owns, and a sixth is a server change. What exists is that a shared script is a first-class post anywhere on the board | — |

### What Σ4 does not claim

**No ranking, no verification, no badge.** A script in a post is exactly as trustworthy as the
person who posted it, which the reader can see. A mark here would be the app vouching for code it
has not run.

**Nothing was posted to the real board.** The composition and the reading are tested and
photographed; what the server does with a given body is the server's, and the app reports its
sentence rather than predicting it.

## Σ5, item by item — the big glass, and the browser

| Σ5 item | State | Evidence | Frame |
|---|---|---|---|
| Every Σ surface rendered on a tablet, both foldings, both languages | ✅ | `SigmaTabletProofTest` — 19 renders at Pixel Tablet (1280×800), Galaxy Tab S9 Ultra (1973×1232), tablet portrait (800×1280) and the Pixel Fold open and closed. These were the first time any of Σ1's or Σ3's surfaces had been measured above 411 dp | `sigma-home-loop-*.png`, `sigma-studio-*.png`, `sigma-paste-*.png`, `sigma-prompt-*.png`, `sigma-mine-*.png` |
| **Bug found: every scrolling sheet crashed on a tablet** | ✅ fixed | `CoineProSheet`'s dialog branch wrapped the body in a `verticalScroll`, and a scrolling container measures its child with an unbounded height. Any sheet whose body scrolls itself — the paste panel, the alert editor, the screener's filters, the webhook sheet — threw «Vertically scrollable component was measured with an infinity maximum height» the moment it opened above 840 dp, and had done since the dialog branch was written. The phone's `ModalBottomSheet` does not scroll its content either, so the fix is the same contract in a narrower window | **A gate**: `SheetShapeTest.a sheet whose body scrolls itself opens on a tablet` |
| **Bug found: the dashboard column filled a twelve-inch panel** | ✅ fixed | Home is a list of cards, and every card was the full width of the device: a row with its symbol at one edge and its figure at the other, and a "do it" button 1973 dp wide. Capped at `CONTENT_MAX_WIDTH` (720 dp) and centred; a phone is narrower than the cap, so nothing on a phone moved | **A gate**: `ContentWidthTest`, two assertions that are opposites — capped on the widest panel, untouched on the phone — because a "fix" that narrowed the phone would be the worse bug. Frames: `sigma-home-loop-tab-s9-ultra-fa-dark.png` |
| The paste and prompt sheets are the capped dialog, not a strip | ✅ | `CoineProSheet` already swapped shape at `showsTwoPanes`; `SheetShapeTest` asserts the cap. The frames render the same body inside the same cap, because a `Dialog` draws into its own window and an off-device capture of the activity comes back empty | `sigma-paste-pixel-tablet-fa-dark.png`, `sigma-prompt-tab-s9-ultra-en-light.png` |
| Parity matrix regenerated, with a row for the studio | ✅ | `gen_parity_matrix.py` reads the qualifiers out of the test sources rather than a list beside them, so the matrix cannot claim a render that does not exist. «script studio» is filled at all six windows; the consistency gate fails when the committed copy is stale | — **a generated document**; `docs/qa/PARITY_MATRIX.md` is the evidence |
| The web plan updated for BYO Script and the return loop | ✅ | `docs/web/PLAN.md` §3b — every Σ surface, where it lives, and what a browser would have to add. The answer is «nothing» for eleven of them and «nine lines» for `LastVisitStore`, because the decisions were written in `commonMain` and the screens draw them | **A plan is not a picture** — what it claims is where each type lives, and the two `ArchitectureTest`s are what hold that: no Android type may enter `chart-core/src/commonMain` or `namascript/src/commonMain`, and the build fails when one does |

### What Σ5 does not claim

**Nothing here was run on a tablet.** These are Robolectric renders at the panels' real dp, which is
what found both bugs and is not the same as a device. The hinge in particular cannot be rendered at
all — `PARITY_MATRIX.md` says so in its own words, and the fold columns are the two displays at
their dp plus pure assertions about the posture.

**The studio's editor is the one surface the web will not get free.** A text field with selection,
an IME and a soft keyboard is real platform behaviour; §3b says so rather than leaving it to be
discovered.

## Σ-FIX, item by item — the owner's review of 4.85.0

Seven items, from a review of twenty-four frames. Two were real defects, two were things the app
does and could not prove, two were features the review was right to ask for, and one was a packaging
failure: the documents exist and were not in the zip.

| Σ-FIX item | State | Evidence | Frame |
|---|---|---|---|
| **1** Code in a community post reads as code | ✅ | The post is split — `ScriptShare.split` — and the file goes to `CoineProCodeBlock`: left-to-right in *layout* as well as in text, monospace at the editor's own size, no wrapping, scrolled sideways. The prose keeps its isolates, because a Persian sentence with `ta.ema` in it is what those were for. The first fix (isolating the code runs) straightened the tokens and left the block wrapped and right-aligned, which is better and still not code | `sigma4-share-thread-phone-fa.png`. The assertion is the absence of an isolate in the drawn text, not the presence of the words: `// nama 1` matches either way and only one of them is a code block |
| **2** The prompt asks in Persian and specifies in English | ✅ | Models write better NamaScript from an English specification, and the reader and the model are two audiences. `ScriptPromptKit.spec` is one generated block used by both prompts word for word, with the reader's chart named inside it; the Persian half is now three lines of ask. It also says titles and signal text may stay Persian, or the English spec quietly asks for an English legend. Prompt version 2 | `sigma1-prompt-kit-phone-fa.png` — Persian at the top, the specification as a code block under it |
| **3** Marker labels at 4/8/16 dp | ✅ (already shipped in Σ0) | `SignalMarkerProofTest` — seven frames, phone and tablet, both languages. They were not missing; they were not in the zip, which is item 4's fault | `sigma0-markers-{16,8,4}dp-phone-fa.png`, the same three on a tablet, `sigma0-markers-16dp-phone-en.png`, `sigma0-markers-triangles-phone-fa.png` |
| **4** Ship the documents with the frames | ✅ | `DOCTRINE.md`, `CHECKLIST.md`, `REPORT.md` and `BLOCKED.md` are in the repository and were in it for every version reviewed. What was wrong is that the package handed over carried frames only, so a reviewer reading it had no way to check a claim against what the run said about itself — which is D10 defeated by a zip file. The package now carries `docs/` | — **a packaging rule**; the documents are the evidence and they are in the repository at every commit named here |
| **5** S6: export, import, and a guest signing in | ✅ for export, import and migration; ❌ for server sync | `ReaderArchiveTest` (22) round-trips per store; the profile's guest rows are now in a frame rather than only in prose. Migration is `GuestMigrationTest` (3): a guest's list survives their first sync, the account's markets are *added* rather than substituted, and an empty account takes nothing away. It holds structurally — there is no account-scoped store, so signing in changes who the server thinks you are and nothing about the phone | `84-profile-guest-fa.png` — «پشتیبان‌گیری از چیزهایی که ساخته‌ام» with its own note about what is not in the file |
| **6** One-tap install from a post | ✅ for the install; ❌ for a public counter | «به چارت من اضافه کنید» is the primary and the studio the secondary — the reverse of 4.85.0, which offered only the editor. It is the same `putScript` the studio's own button calls, so an installed script is one of the reader's indicators in every sense: legend row, settings sheet, eye, `×`. `ScriptInstallStore` remembers what *this device* added and the card says so in those words. There is no «۱۲ نصب», because nothing knows that — BLOCKED item 8 | `sigma-fix-share-thread-install-phone-fa.png` |
| **7** Tablet Home is two columns | ✅ | «وقتی نبودید» beside «امروز», and the balance beside the markets, capped as a *pair* at `CONTENT_MAX_WIDTH_WIDE` rather than each alone. 4.84.0's single cap fixed a row whose symbol and figure were two thousand dp apart and introduced the fault the review names: a phone-width column with both thirds empty. `ContentWidthTest` (4) holds both halves — side by side on a tablet, stacked on a phone | `sigma-home-loop-pixel-tablet-fa-dark.png`, `sigma-home-loop-tab-s9-ultra-fa-dark.png` |

### What Σ-FIX does not claim

**Item 6 is an install, not a count.** How many people installed a script is a fact about everybody
and this app cannot know it. A private number under a public word would read as social proof and be
something else; the board's own like is server-side and stays the social number on the post.

**Nothing here was run on a device.** Still Robolectric at the panels' real dp, and S1 still reads
⏳ owed to device.

## The one item of the 4.86.0 review that was mine

The review of 4.86.0 closed all seven Σ-FIX items and left a table of what remains. Everything on it
is owed to a device or to a backend except one line: «ذخیره‌شدن سبک مارکر بین اجراها» — what a study
draws on the candles was session state, like the legend's eye, and the review asked whether it should
be.

| Item | State | Evidence | Frame |
|---|---|---|---|
| Marker style survives a cold start | ✅ | `markerStyles` is field 25 of the per-symbol record — appended, so a row written by an older build reads as an empty map, which is the same chart it was: every study labelled. The restore forgives a name this build no longer knows and drops ids that are neither in the catalogue nor owned by a script, exactly as the colours and widths beside it do. The default is still *not stored*, so turning the labels back on removes the row rather than writing `LABELS` — and that removal persists too, which is the half a session-only fix gets wrong in the other direction | `sigma-marker-style-restored-phone-fa.png` beside `sigma-marker-style-default-phone-fa.png` — **added in 4.87.2**, after the owner asked whether 4.86.1 had a picture. It did not, and the row said so: a fact about two runs is not something one still can carry. The pair carries it, because the map each frame is drawn from was **not written by the test** — it is set through `setMarkerStyle`, written to a real store, read back by a second controller that has never seen the first, and only then handed to the renderer. If the restore breaks, the first frame comes back with «خرید» and «فروش» on it. `MarkerStylePersistenceProofTest` (2), `ChartSymbolStateTest` (2) |

A reader who has learned which way a triangle points turns the labels off once. A setting that came
back every morning is one they turn off forever, which is the difference between a preference and a
nag. That is the whole argument, and it is why this was not left as the legend's eye: the eye is a
glance, and this is a decision.

## What Σ0 does **not** claim

The pinch is proved against an injected event stream, which is the strongest thing this container
can do and is not the same as a thumb. Until the owner's thirty seconds land, S1 reads
**⏳ owed to device** and not ✅.

## The owner's two, 4.87.0 — the broker link and a way to reach a person

Neither is a feature. Both are a wire that was never connected.

| Item | State | Evidence | Frame |
|---|---|---|---|
| The OneRoyal button opens the owner's own introducing-broker link | ✅ | `https://vc.cabinet.oneroyal.com/fa/links/go/16669`, taken whole. The two exchanges hand out a *code* and read it from a parameter on their own page; a broker's IB programme issues a whole address instead, which lands on the cabinet, records the introduction and forwards to the form. `TradePartner.referralLink` replaces `signUp` rather than decorating it, and a test asserts there is nothing appended — the failure this is written against is somebody adding the code field later and the builder helpfully sticking `?ib=` onto an address that already identifies the introducer | — **not a frame**: a link is not a picture. `TradePartnersTest` (4), including the two venues whose blank code must still open their own page |
| Support reaches a person, and wears the mark that says so | ✅ | `BrandConfig.SUPPORT_URL` had been in the source since the brand file was written and **nothing in the app opened it** — an address with no door. It is now `t.me/ProChart_Sup`, and it is a row in the account list above «ارسال بازخورد» rather than instead of it, because asking and reporting are two errands: the share sheet ends in the reader's own outbox with no sign that it arrived. The row carries Telegram's own mark in Telegram's own blue — `ProfileAction.brandMark`, the one exception to a list that tints every glyph to the app's ink, because a Telegram logo painted grey is a grey circle. A guest gets the row too, first among the rows that are not their own data | `support-profile-phone-fa.png`, `support-safety-phone-fa.png` — the safety card is scrolled to before the shutter, which is also the assertion that the button is reachable and not merely in the tree |

## 4.87.1 — the two corrections

| Item | State | Evidence | Frame |
|---|---|---|---|
| The support row asks for what the owner wants sent | ✅ | «نظرها، پیشنهادها و انتقادهایتان را برای ما بفرستید.» replaces «با یک آدم حرف می‌زنید، نه با ربات» on the row and in the safety card's body. The promise about a person is still true and is still made — by the row's own title and by the mark next to it — but the *note* is the line a reader acts on, and it now says what to do rather than what we are | `support-profile-phone-fa.png` |
| The launch animation stopped catching | ✅ | Two causes, one of them not in the animation at all. **The whole app was composing underneath while the sheet was moving**: the most expensive composition this app ever does, on the same main thread that owed the wipe a frame every eight milliseconds, against a wall clock that skips rather than slows. The sheet now draws its lockup alone, hands over with `onDrawn`, and the app composes during a **still hold** — bounded, so a slow phone gets a half-drawn chart rather than a white screen. **And the clock had a dead stretch**: the mark landed at 0.36, the name started at 0.34, and from 0.72 to 0.86 nothing happened at all. The phases now overlap, the clock is linear and each phase is eased on its own — an eased clock spends its speed at the front and leaves the name creeping, which is the same fault read from the other end. Each element also rises in alpha as it wipes, so a lost frame is a soft edge rather than a stopped clip | `launch-draw-320ms.png`, `launch-draw-620ms.png`, `launch-landed.png` — three frames off one clock. The smoothness itself is **owed to a device**: `LaunchSplashTest` holds the order and the bounded hold, which is what an edit would undo; a recording is what settles how it feels |

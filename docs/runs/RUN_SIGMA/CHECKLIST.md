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
| S3 | BYO Script end to end | ✅ for A, B, C, E, F, G; ❌ for D | Σ1 below | `sigma1-paste-fixes-phone-fa.png`, `sigma1-paste-templates-phone-fa.png`, `sigma1-prompt-kit-phone-{fa,en}.png`, `sigma1-my-script-phone-fa.png` |
| S4 | `docs/DOCTRINE.md` with D1–D10, each with a CI gate | ❌ | Σ2 | — |
| S5 | Retention loop | ❌ | Σ3 | — |
| S6 | Account value: sync, export/import, guest migration | ❌ | Σ4 | — |
| S7 | Community scripts | ❌ | Σ4 | — |
| S8 | All of the above on tablet, parity matrix 100 % | ❌ | Σ5 | — |
| S9 | This document, `REPORT.md`, `BLOCKED.md` | ✅ for Σ0 | The Σ0 rows above and `REPORT.md`'s Σ0 section | — |

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
| **D** Share → a community post with the code, a chart snapshot and a one-tap install | ❌ | Not started. `ScriptLink` is the half of it that exists. The other half is a community surface, which is S7 in Σ4 | — |
| **E** Every script gets the same confidence and Explain sheet; a hint chip where `signal()` is missing | ✅ | The Signal Layer already treats a script's verdicts like any study's. The hint appears under the editor when a script ran, drew, and said nothing — which is the moment it means something | — **a conditional row in a list**; `script-signal-hint` is its semantics tag |
| **F** ≥ 60 Persian-named strategies with `signal()`, one line each, a default stop; the same in English | ✅ | `ScriptLibrary.ALL` — 61, across six families. Both languages render from one table, so the code either side is identical token for token (`ScriptLibraryContentTest.the two languages compute exactly the same thing`). Every one draws a stepped stop line, speaks in both directions over a 1 200-bar series, and never marks the forming bar. 15 tests | — **a data table**; the tests above are the proof, and the strategies appear in the studio's library tab |
| **G** Thirty real assistant answers either run or produce a one-tap-fixable diagnostic | ✅ | `src/jvmTest/resources/pastes/*.txt` — thirty files, each a header plus what a chat window actually returns. Every one is required to end up running **and drawing**, or to offer a template that does. `ScriptPasteSuiteTest` (4) | — **a file-driven suite** |
| **G** Import/export round trip; deep-link install | ✅ round trip, ❌ install | `ScriptDocumentTest.a script survives the round trip whole` and `the round trip is stable a second time`. The install path has nothing to install *into* until C is wired | — |

### What Σ1 does not claim

**A share link opens only what this device already has.** The address format, the manifest claim,
the two shape checks and the routing are all real and tested; what is behind the id is not, because
there is no service to ask. A reader who exports a script and sends themselves the link gets their
script back. A link from somebody else says so plainly. That is the whole of what can be honestly
built before S7.

**D needs a place to post to.** Sharing a script is a community surface; the run's own plan puts
that in Σ4 (S7). What Σ1 leaves behind for it is `ScriptLink` — an address that carries an id and
never source, so that a tapped link can never be a script that ran.

## What Σ0 does **not** claim

The pinch is proved against an injected event stream, which is the strongest thing this container
can do and is not the same as a thumb. Until the owner's thirty seconds land, S1 reads
**⏳ owed to device** and not ✅.

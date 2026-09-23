# The web terminal against TradingView — what parity means, and when it arrives

The owner's ask is one sentence: **the web version should be exactly at parity with TradingView.**
This document answers it in the only way that is worth anything — by saying which parts of that
sentence are reachable, which are not and why, what is already built, and what each remaining piece
costs. `PLAN.md` says what the web terminal *is*; this says where it stands against the thing it is
measured by.

Nothing here is an aspiration. Every count is read out of this repository, and where a thing is not
built the row says «not built» rather than «planned».

---

## 0. The short answer

**Since 5.9.0 the web build is the phone app** — every screen, compiled from the phone's own
Kotlin sources for the browser, not ported (`docs/runs/RUN_WEB/REPORT.md` §1). Whatever the phone
has, the site has at the next build.

| | |
| --- | --- |
| the chart engine and the language compile for a browser | ✅ 5.0.0 |
| the chart itself in a browser, on live candles | ✅ 5.8.0 (W1b) |
| **every screen of the app in a browser** | ✅ **5.9.0** — `docs/runs/RUN_WEB/CHECKLIST.md`, a frame per screen |
| the server the browser talks to | ✅ the named relay routes are live; ⏳ the `/up/` passthrough for signed-in screens (`SERVER.md` §4.12) and `/api/img` (§4.13) are owed to the server agent |
| an account that is one account on both | ✅ the phone's own sign-in, on the same two backends, once §4.12 is live |

What a browser cannot be (widgets, picture-in-picture, Play Integrity, work while the tab is closed)
is listed in the checklist, with what the page does instead. The phases in §3 below are the plan this
replaced, kept because §3a's measurements are still true.

---

## 1. «Parity with TradingView» is three different promises

Saying yes to all three would be a lie, so here they are separated. The first two are the target;
the third is not, and no amount of building buys it.

### 1a. Chart parity — *reachable, and mostly already reached*

What the chart itself can do: series types, timeframes, indicators, drawing tools, scales, replay,
multi-chart layouts, the physics of panning and zooming. This is the part that has been measured
against TradingView for a year, and the numbers are in §2.

### 1b. Terminal parity — *reachable, and the actual work*

The surfaces around the chart: the watchlist, the screener, alerts, saved layouts, the script
editor, sharing. These exist on the phone. On the web they are a port, not an invention — which is
why §3's estimate is a schedule rather than a guess.

### 1c. Network parity — *not reachable, and largely not wanted*

The things TradingView has because it is TradingView and has been for fifteen years:

* **Data licences** — every equity market on earth, futures, options chains, depth of book. This
  product carries two venues: crypto and forex. That is a licensing and commercial question with a
  price on it, not an engineering one.
* **The community** — hundreds of thousands of published scripts, ideas, followers, comments.
* **Broker integrations** — trading from the chart, through dozens of brokers.
* **Their own infrastructure** — a global CDN, their alert fleet, their replay data going back
  decades.

**This is where «exactly» has to be put down.** A reader who opens this product and TradingView side
by side should find the chart does the same things and the terminal answers the same way. They will
not find NASDAQ in the symbol search, and building for a year would not put it there. Anything
written here that implied otherwise would be a promise made to the owner on somebody else's behalf.

---

## 2. The matrix — feature by feature

«Phone today» is what ships in the APK. «Web needs» is what the browser costs on top of it, given
that the engine already compiles.

| | TradingView | phone today | web needs |
| --- | --- | --- | --- |
| **chart types** | ~15 | **18** (`ChartType`: candles, hollow, Heikin-Ashi, bars, line, area, Renko, Range, Line Break, Kagi, Point & Figure, …) | nothing — `:chart-core` |
| **indicators** | ~100 built-in | **83** in the picker (`ChartCatalog`), checked series-by-series against a pandas/`ta` fixture (`IndicatorReferenceTest`) | nothing — `:chart-core` |
| **drawing tools** | ~110 | **92** entries in the rail (7 cursor modes, 85 tools: 11 Fibonacci, 9 channels, 9 measures, 6 patterns, 5 Elliott, 4 Gann, …) | nothing for the geometry; the rail is a Compose surface — §3 |
| **scripting** | Pine Script | **NamaScript** — its own language, 351 conformance scripts, a Pine translator for pasted code, 61 shipped strategies | nothing — `:namascript`; the editor's text field is the one piece with real platform behaviour |
| **multi-chart layouts** | up to 8 | **1–8**, same grid | the Compose port |
| **replay** | bar replay | **Replay Arena**, plus a daily challenge | nothing — `:chart-core` |
| **alerts** | server-side | **on-device**, plus venue alerts | **server-side evaluation** — a browser tab that is closed evaluates nothing. `PLAN.md` §4 |
| **watchlist** | synced | synced (`WatchlistSyncController`) | the account — §5 |
| **saved layouts and drawings** | synced | `ChartLayoutStore` local, `DrawingSyncStore` wired | one GET/PUT pair per document, the shape the watchlist already uses |
| **screener** | full | present | the Compose port |
| **symbol coverage** | every major market | **crypto + forex** | nothing new; §1c |
| **depth of book, options, futures** | yes | no | not planned — §1c |
| **published scripts / social** | the centre of their product | no | `pro-chart.com/s/<id>` is the natural home, and `ScriptLink` already produces the ids |
| **news, calendar** | integrated | integrated | the relay carries both |
| **the thing they do not have** | — | **the Signal Layer, Confidence, رصد, Setup score** — all in `:chart-core`, all pure arithmetic | nothing |

Read down the «web needs» column and the pattern is the whole argument of `MODULES.md`: **almost
everything that decides is already portable, and what is left is drawing.**

---

## 3. What it costs, in phases

Durations are working days at the pace this repository has actually moved — the tablet work
(`RUN 4.52+` item 2) and run Σ are the comparable pieces. They assume no new product decisions
arrive mid-phase; each phase ends with something a reader could open.

| phase | what it is | days | depends on |
| --- | --- | --- | --- |
| **W0** | the target exists, engine + language compile for a browser, CI keeps them compiling | **done** | — |
| **W1a** | **`:chart-ui` is a KMP module.** Done, 2026-09-21 — one target, Android, sources in `androidMain`. The whole suite and `:app:assembleRelease` green, no behaviour changed | **done** | W0 |
| **W1b** | the browser target on it: `wasmJs`, the two Android files behind a seam, the five resource calls | 5–9 | W1a, W1½ |
| **W1½** | **the drawables — and there is nothing to convert.** The XML is *generated*, and **1,117 of the 1,150 still have their original SVG in this repository**, including **all 226** of the ones with `aapt:attr` gradients. The browser reads those SVGs; Android keeps its XML. What is left is 33 hand-authored XML icons with no SVG behind them | **1** | W0, and it can run in parallel with W1b |
| **W2** | the terminal shell: the labelled rail, the tool column, the readings panel, the 1–8 grid, the object tree, the watchlist as list-detail. All of it is the tablet layout, which is why §4 of the plan was done before §5 | 8–12 | W1b, W1½ |
| **W3** | the script studio in the browser — the editor, the diagnostics, the console, the library | 4–6 | W1b, and a `<textarea>`'s selection model |
| **W4** | the account: one login across phone, tablet and browser; synced layouts, drawings, watchlists | 5–8 | server phase 4. **Unblocked 2026-09-21** — TradeYar owns the account |
| **W5** | server-side alerts, because a closed tab evaluates nothing | 4–6 | W4 |
| **W6** | the parity pass proper: the comparison rig pointed at TradingView's *web* terminal rather than their Android app, and every gap it finds closed | 5–10 | W2 |

**W1 + W1½ + W2 + W3 is a terminal a reader can use** — a chart, the tools, the scripts, the
watchlist, read-only, no account. Call it **21–33 working days** from a standing start, and it is
not blocked on anybody: everything it needs is either built or decided. The figure went up rather
than down after §3a's audit, and that is the audit working: W1½ is real work that was inside
nobody's estimate, while W1 itself turned out smaller than it looked.

## 3a. What is actually Android in the chart — counted, not guessed

`PLAN.md` §2 was written as an estimate and then measured, and the measuring changed it. The same
was owed to W1 before anybody spends a fortnight on it, so here is `:chart-ui` read import by
import.

**`:chart-ui` is 15,242 lines of Compose across 17 files, and 22 imports of it are platform-bound.**
That is the whole list:

| what | count | what it costs |
| --- | --- | --- |
| `LocalDensity`, `LocalLayoutDirection` | 7 | **nothing.** Both exist in Compose Multiplatform under the same names |
| `painterResource`, `stringResource` | 5 | the `org.jetbrains.compose.resources` equivalents — mechanical |
| `LocalConfiguration` | 1 | one site; the window size it reads is available without it |
| `android.graphics.BitmapFactory` | 1 | one site |
| `android.view.Surface`, `SurfaceControl`, `View`, `os.Build` | 5 | **one file** — `ChartFrameRate.kt`, 69 lines. A browser has no `setFrameRate`; this is an `expect`/`actual` whose web half does nothing |
| `android.view.MotionEvent`, `MotionEventPredictor` | 2 | **one file** — `ChartStrokePredictor.kt`, 59 lines. Same treatment; the browser starts without prediction |
| `:core:designsystem` | 34 | not a rewrite — the module beneath, below |

Everything else — `CoineProChart` at 7,429 lines, the drawing renderer at 2,576, the legend overlay
at 1,357, all eighteen series types — draws on `androidx.compose.ui.graphics.Canvas`, which is
Compose's own API and is the same one on the web. **Two files totalling 128 lines are the only
Android in the chart.**

**The design system is the real dependency**, and it is bigger but still bounded: 55 files, 12,308
lines, about sixty platform-bound imports, of which the ones that need thought rather than a rename
are `StaticLayout` and `android.graphics` in the share card (already known — §2 of this document's
sibling table calls it the one surface that does not cross) and Coil for remote images, which has a
multiplatform build.

**And the thing nobody had counted: 1,150 vector drawables** — every asset logo and every tool
glyph, of which **226 use `aapt:attr` with an inline `<gradient>`**, an Android resource-linker
feature rather than a vector one, which renders nowhere else.

**Then the first line of one of them was read, and W1½ went from five days to one.** Each file
opens with `<!-- Generated by scripts/design/svg-to-vector.py from design/asset-logos/… -->`. The
XML is an *output*. **The SVG sources are in this repository** — 5,021 of them — and
**1,117 of the 1,150 drawables still have theirs, including all 226 with gradients.** Compose
Multiplatform reads SVG on every target, so the browser reads the sources and Android keeps the
generated XML it already ships: no conversion, no gradient to flatten, nothing lost.

What is actually left is **33 hand-authored XML icons** with no SVG behind them — `tv_tool_eraser`,
`tv_zoom_in`, a handful of chart-type glyphs — and they are the whole of W1½ now.

This is the second time in this document that a measured number replaced an estimated one and the
answer got smaller. The first was `:chart-ui`'s 22 imports. The pattern is worth naming: **the
estimates were expensive because nobody had opened the files.**

**W4 onwards can start now.** It could not when this was written; §5 has what changed.

---

## 4. How parity gets proven, rather than claimed

The rig for this already exists and has a rule written into it: the phrases «Pixel Perfect»,
«100% TradingView Parity» and «Exact TradingView Match» do not appear as claims anywhere in this
repository, and will not until the comparison actually runs
(`docs/design/TRADINGVIEW_FINAL_PARITY_REPORT.md`). That report's verdict today is
`TRADINGVIEW VISUAL PARITY: NOT YET PROVEN`, blocker `REFERENCE_MISSING`, because comparing against
their Android app needs a device with their app installed on it and no build host has one.

**The web changes that, and in the product's favour.** TradingView's web terminal runs in a browser
that a build machine can also run — the same browser, the same window size, the same device pixel
ratio. So the reference capture that is impossible for Android is ordinary for the web: two pages,
one rig, the same screenshot pipeline that already produces the phone's goldens. W6 is short for
exactly that reason.

Three rules carry over unchanged, because they are what makes the number mean anything:

* The comparison runs in **English, left-to-right**, on both sides. Comparing this product's Persian
  against their English would mask most of both frames and measure almost nothing.
* This product's own regression stays **Persian, right-to-left**, because that is what ships.
* Nothing is substituted for the reference — no marketing screenshot, no resized PNG.

---

## 5. What was blocking — all of it, answered on 2026-09-21

This section listed three decisions and two data faults. **None of them is open**, and the two
halves came unstuck in opposite ways, which is worth keeping side by side.

**The decisions were answered by the owner:**

1. ~~Which backend owns the account on the web.~~ **TradeYar.** Open since
   `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md` was written; server phase 4 and **W4** are
   unblocked, and the reset route moved the same day — same body, a different host (`SERVER.md`
   §4.4.1).
2. ~~Free, member-only, or a read-only guest page.~~ **Open and read-only, no account.** This is the
   answer that costs nothing to implement and changes the schedule most: **W1 → W3 ships to readers
   on its own.** There is nothing to sign in to, so the chart, the tools, the scripts and the
   watchlist do not wait behind W4.
3. ~~One origin or two.~~ **One** — it is what the relay already is, and every phase since has been
   built that way.

**The data faults fixed themselves.** `RUN_ALEF/BLOCKED.md` §א20 and §א22: gold and silver are in
CoinePro-FX's snapshot *and* on its socket, and `source` reads `finnhub` on all 19 rows.
CoinePro-FX's team changed it with no message from here — the faults were measured, written down,
and left alone rather than worked around, because a symbol the feed does not list is a symbol the
app must not invent.

**What is left of §א22 is one sentence and it is not a blocker:** `bid == ask == price` on 19 of
19, so this feed carries no spread, and `CandleGateway.sourceName` still prints «MetaTrader 5»
beside a quote that now says Finnhub. A terminal at parity with TradingView on a feed that cannot
say where its prices come from would be parity of the wrong kind — but it can say now, and what
remains is a label that names one of two venues rather than a feed that names none.

---

## 6. The one-line answer to «when»

**Built, 5.9.0.** The whole app runs in a browser from the phone's sources. What stands between it
and readers is deployment and two server routes (`docs/runs/RUN_WEB/BLOCKED.md`), not more work in
this repository.

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

**There is no web build today.** What exists is everything underneath one:

| | |
| --- | --- |
| the chart engine compiles for a browser | ✅ 5.0.0 — `:chart-core:compileKotlinWasmJs` runs on every push |
| the language compiles for a browser | ✅ 5.0.0 — `:namascript:compileKotlinWasmJs`, same job |
| the server the browser would talk to | ✅ phases 1–3 live on `pro-chart.com`: legal, the update document, crypto and forex prices, candles, news, the forex socket |
| the layouts a wide window needs | ✅ the tablet work — a browser window *is* an Expanded window, and the app already decides from width rather than from «is this a phone» |
| the screens themselves, in a browser | ❌ **not built.** `:chart-ui` and every `feature:*` module are Android libraries |
| an account that is one account on both | ❌ **not built**, and blocked — see §5 |

So the honest shape of the answer is: **the hard half is done and proven, the visible half has not
started**, and the visible half is gated on two decisions that are the owner's, not the work's.

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
| **W1** | `:chart-ui` becomes KMP. `CoineProChart` draws on `androidx.compose.ui.graphics.Canvas`, which is Compose's own API and identical on both; the `ChartIcon → drawable` map gets a second `actual` returning a Compose resource. Ends with **a chart in a browser, on real candles from the relay** | 6–10 | W0 |
| **W2** | the terminal shell: the labelled rail, the tool column, the readings panel, the 1–8 grid, the object tree, the watchlist as list-detail. All of it is the tablet layout, which is why §4 of the plan was done before §5 | 8–12 | W1 |
| **W3** | the script studio in the browser — the editor, the diagnostics, the console, the library | 4–6 | W1, and a `<textarea>`'s selection model |
| **W4** | the account: one login across phone, tablet and browser; synced layouts, drawings, watchlists | 5–8 | **blocked** — §5, and server phase 4 |
| **W5** | server-side alerts, because a closed tab evaluates nothing | 4–6 | W4 |
| **W6** | the parity pass proper: the comparison rig pointed at TradingView's *web* terminal rather than their Android app, and every gap it finds closed | 5–10 | W2 |

**W1 + W2 + W3 is a terminal a reader can use** — a chart, the tools, the scripts, the watchlist,
read-only, no account. Call it **18–28 working days** from a standing start, and it is not blocked
on anybody: everything it needs is either built or decided.

**W4 onwards cannot start**, and the reason is in §5.

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

## 5. What is blocking, and it is not engineering

Three decisions, all in `PLAN.md` §6, all the owner's:

1. **Which backend owns the account** on the web. Two backends, one reader; the question has been
   open since `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md` was written. Until it is answered,
   server phase 4 cannot start, and W4 sits behind server phase 4.
2. **Is the web terminal free, member-only, or a read-only guest page?** This is not a late
   detail — it decides whether W1's output is publishable on its own or has to wait for W4.
3. **One origin or two.** The gateway is the clean answer and is what the relay already is; the
   alternative is CORS on two servers this app does not own.

And two facts about the data, which parity cannot paper over — both with CoinePro-FX, both one
message away (`RUN_ALEF/BLOCKED.md` §א20 and §א22):

* **Gold is missing** from the snapshot the catalogue is built from, while the forex signals are
  gold and nothing else.
* **The forex quotes say `source: "yfinance"`** with `bid == ask`, while the chart names
  «MetaTrader 5» beside them.

A terminal at parity with TradingView on a feed that cannot say where its prices come from would be
parity of the wrong kind.

---

## 6. The one-line answer to «when»

**A usable web terminal — chart, tools, scripts, watchlist, no account — is 18–28 working days of
work that nothing is blocking.** Everything past that is behind an account, and the account is
behind a question only the owner can answer.

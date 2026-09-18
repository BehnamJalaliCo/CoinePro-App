# RUN Ψ — report

Three instructions in one message: check the tablet, stand up the web, and take copy trading out of
forex. What follows is what each turned out to mean once it was looked at.

---

## 1. The tablet audit

The owner's question was whether the work that shipped for a phone also works on the larger glass.
Every surface the last three runs touched, at every window `docs/qa/PARITY_MATRIX.md` tracks:

| surface | shipped in | verdict |
|---|---|---|
| the welcome slides | Ξ3 | **wrong.** Every measurement was a fraction of the window |
| the chart's loading skeleton | Ξ4 item 18 | **wrong.** A fixed 5 × 6 grid |
| the plot's vertical gradient | Ξ4 item 15 | right — a brush over `size.height` |
| the last price's glow | Ξ4 item 15 | right — a radius off the gutter's own width |
| the volume band's lid | Ξ4 item 15 | right — a registered hairline across the plot |
| the crosshair's soft shadow | Ξ4 item 15 | right — 4 dp, and a dp is a dp |
| the marker scale-in | Ξ4 item 15 | right — a scale about the marker's own anchor |
| the symbol wipe and the timeframe dissolve | Ξ4 item 16 | right — a clip and an alpha over the canvas |
| `coineProControl`, 37 sites | Ξ4 item 17 | right — a press and a haptic are not layout |
| the empty-state actions | Ξ4 item 22 | right — and the gate reads source, not pixels |
| the broker-route guards | Ξ5 item 19 | right — navigation has no width |

**Two of eleven were wrong, and both were wrong the same way**: a number that should have been a
fraction of the space the composable was *given* was a fraction of the window, or was not a fraction
at all. That is the one failure mode `CoineProWindowClass`'s own KDoc warns about — «decide from the
space given, never from is-this-a-phone» — and it is worth saying plainly that the rule was already
written down and the two new surfaces did not follow it.

### The welcome

A 1 280 dp Pixel Tablet drew a **790 dp square illustration**, a 28 sp headline set on a **1 248 dp
measure** — six times the length a line of type is readable at — and two full-width pills at the
foot. Not a broken layout: a phone's composition stretched, which is the thing that makes an app
look like it was ported rather than designed.

The fix is one number. `CONTENT_MAX_WIDTH = 448.dp` caps the content column and centres it, and the
scene, the type, the dots and the two buttons all take their width from that column rather than from
the window. 448 is a large phone and a little more: wide enough that no phone is ever narrowed,
narrow enough that a headline sits on a measure somebody reads in one movement of the eye. It is the
same idea as a reading measure in print, and it is the same idea `ChartWorkbench` and
`CoineProListDetail` already use one level up.

**The welcome had no tablet render at all.** It sat in the parity matrix's `other` row with a dash
under every tablet column. That is exactly how this ships unnoticed, so it now has two — one at each
tablet width the matrix tracks, in both languages and both themes.

### The skeleton

Five rules by six columns is a phone's chart. At 1 280 dp the same five are 160 dp apart, and the
picture reads as a sparse table rather than as a chart about to arrive.

The count comes from the space now, at roughly the spacing `drawGrid` puts its own rules at, bounded
three to fifteen. The arithmetic is `ChartSkeletonGrid` — an object with a test rather than three
lines inside the composable, and the reason is that **a rendered still cannot say «these are 96 dp
apart»**. It can only show a picture somebody has to judge. The test drives all ten windows and
prints the spacing: 80–123 dp, against a band of 60–130.

The upper bound moved from eleven to fifteen while writing that test, and the test is why: a Galaxy
Tab S9 Ultra is 1 973 dp across, and eleven rules there are 164 dp apart — the same fault one size
up, which is exactly what a bound chosen without measuring the widest case does.

### A note on the proof frames, so nobody chases it

Every welcome frame this rig produced before today was a picture of the **brand frame** — the 600 ms
hold — because `waitForIdle` settles composition and does not move a wall clock, and the hold is a
`delay`. The captures now advance the clock past it, and the brand frame is captured separately and
named as such. Run Ξ's checklist cited those frames for the illustration and typography items; they
showed a mark on a dark ground.

Also: the proof PNGs carry a faint duplicate of the screen's last line across their top few pixels.
That is the capture rig, not the product — driven directly, the terms sentence resolves to exactly
one semantics node, at the bottom.

---

## 2. The web, and the server the owner offered to buy

The offer was «whenever you say, I'll get a server». The useful answer to that is not «yes please»,
it is a document somebody can provision against, so `docs/web/SERVER.md` is this run's deliverable:
the machine, the four containers, every route the relay carries with its upstream and its cache, the
socket fan-out, the three tables it owns, and a seven-step order to bring it up with a proof for each
step.

**The sentence worth arguing with is the owner's own**: «take the data we need from the TradeYar and
CoinePro-FX servers and put it on the Pro Chart server». That has two readings. A **relay** asks
upstream when a browser asks it and holds nothing for long. A **copy** pulls continuously and serves
its own store, and the backends become upstreams it syncs from. They are different servers to
operate and different things to be wrong about.

`SERVER.md` specifies the relay, and the reason is not caution. A copy has to answer «how stale may
this be?» for every field, and the honest answer is different per field: two seconds for a price,
for ever for a closed candle. The cache policy in §4.1 *is* that answer written out, which is what a
copy would need anyway — so the relay grows into a copy exactly where a copy earns something and
stays a relay everywhere else. The one place a real copy is worth building on day one is **candle
history**, because both backends serve a bounded window and a reader who pans a year back walks off
the end of it.

Two things are worth doing the day the machine exists, whatever happens to the rest: **the legal
pages and `assetlinks.json`**. Every legal link in the shipping app already points at
`pro-chart.com/legal/…`, and that host does not answer today. It is an afternoon, and it closes the
one place the product currently points at nothing.

---

## 3. Copy trading, and what «only the gold signal» means in code

### What was removed

Not hidden — deleted. `core:copytrade` and `feature:copytrade`, the screen, the controller, the
gateway, the route and its deep-link mapping, the menu row, the DI bindings, the injected map, the
MetaTrader card on Connections, twenty `connections_mt5_*` strings in two languages, eleven
registered note keys, and the «go to copy trading» button on the signal detail.

**This is the opposite of the usual rule in this repository**, and it is worth being explicit about
that. `FeatureFlags`' own KDoc argues for keeping gated code compiled so that turning a flag back on
is a word rather than a project, and run Ξ item 19 hardened exactly that gating for these very
surfaces. That argument holds for a feature the product might want back. It does not hold for one
the owner has said the product does not have: leaving it compiled would mean every future run
carrying, testing and reasoning about a screen nobody can reach. `git log` is where it lives now.

`FeatureFlags.forexTrading` survives and still governs the broker links. Its note now says plainly
that turning it back on would restore those and **would not** restore copy trading, because there is
nothing left to restore.

### What «only the gold signal» is

`ForexSignalScope`, in `:core:signals`. On the forex market the app shows gold and nothing else; on
crypto it returns the list it was given, as the same object. Three decisions in it:

* **The base, after the punctuation comes out.** `XAUUSD`, `XAU/USD`, `XAUUSD.m` are all gold; the
  quote and the broker's suffix are not stable and the base is.
* **Silver is not gold.** `XAGUSD` is in the bundled symbol list because the **chart** carries it,
  and a chart is not a call. That is the one place these two lists were quietly the same list, and
  this is where they stop being.
* **What is held back is counted.** `SignalsState.withheld`, and the server's own total is corrected
  by it — a reader must not be told there are eleven more in the history when every one of them is a
  market this app does not publish. Zero on every response the desk is expected to send; a non-zero
  number is a conversation to have with the desk rather than a bug to hunt in the client.

The filter is in the client because **the client is where the promise is made**. The list route
answers what the desk publishes under the word «forex», which is the desk's business and may be
wider than this product's; narrowing here is the app keeping its own copy honest.

### What was deliberately not touched

`docs/legal/TERMS.md` §6-3 and `membership_copytrade_note` describe automatic copy trading **on
LBank** — the crypto venue, on TradeYar. The owner's instruction was about forex, and the app's
copy-trading surface was forex-only. Rewriting the LBank sentences would be this run deciding
something about the crypto service it was not told, in the document a reader legally agrees to.
`BLOCKED.md §Ψ16` is the question, and it is one line.

### The five tests the removal broke

Each was fixed by rewriting what it claims, not by deleting it:

* `ConnectionsSurfaceTest` — the closed set is two surfaces now, and the file says why the second
  MetaTrader surface went for a different reason from the first.
* `MenuCatalogueTest` — `copy-trade` is not a row one platform has and another does not; it is
  nobody's.
* `MenuRowMetricsTest` — six descriptive rows, not seven.
* `NotePolicyTest` — the money example is `connections_lbank_body` now: the exchange key that places
  real orders, which is the same claim on a surface that still exists.
* `ForexSurfaceReachabilityTest` — rewritten around what is **gone** rather than what is hidden, and
  it now asserts the search matched something real before asserting it found nothing: «walked 598
  sources; broker strings drawn in 0».

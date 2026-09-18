# RUN Ψ — checklist

States: ✅ done · ❌ not done, or narrowed (the row says exactly how) · ⏳ owed to the owner.

The owner's brief, in their own words: check that everything done so far was done **on the tablet
too**; set up the **web version** on a server they will provide, taking the data from the TradeYar
and CoinePro-FX servers onto a Pro Chart server; **forex has no copy trading any more and is only
the gold signal**; and it must be **executed in full**.

---

## Ψ1 — everything so far, on the tablet as well

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 1 | Audit every surface the recent runs touched at every window the parity matrix tracks | ✅ | Eleven surfaces, listed in `REPORT.md §1` with the verdict for each. **Two were wrong and are fixed** (rows 2 and 3); the other nine were right because they measure the space they are given rather than asking «is this a phone», which is the rule `CoineProWindowClass` exists to enforce | — **an audit, and it is the table in `REPORT.md`** |
| 2 | The welcome slides on a tablet | ✅ **was broken, now fixed** | Every measurement was a fraction of the **window**: a 1 280 dp tablet drew a 790 dp square illustration, a headline on a 1 248 dp measure, and two full-width pills. `WelcomeSlides` now caps its content column at `CONTENT_MAX_WIDTH = 448.dp` and centres it; the scene, the type, the dots and the buttons all take their width from that one number. A phone never reaches the cap, so nothing there changed | `psi-welcome-pixel-tablet-fa`, `psi-welcome-tab-s9-ultra-en-light` |
| 3 | The chart's loading skeleton on a tablet | ✅ **was wrong, now fixed** | Five rules by six columns is a phone's chart; on a landscape tablet the same five are 160 dp apart — a sparse table where a chart is about to be. `ChartSkeletonGrid.lines()` derives the count from the space at ~96 dp a rule, bounded 3..15, and the ladder counts with it. Measured at every window the matrix tracks: **80–123 dp** apart on all ten, against a band of 60–130 | — **arithmetic, and a still cannot state a spacing.** `ChartSkeletonGridTest` prints all ten |
| 4 | The run Ξ chart craft (the plot gradient, the price glow, the volume lid, the crosshair shadow, the marker scale-in, the arrival) on a tablet | ✅ | Every one is a fraction of the canvas or a dp, so it scales by construction — and the claim is not taken on trust: 54 chart goldens were re-recorded across phone, tablet-portrait, pixel-tablet, Tab S9 Ultra, fold-open and fold-closed, and all 54 pass | `app/src/test/goldens/chart-*` — the tablet families are named in `docs/qa/PARITY_MATRIX.md` |
| 5 | The run Ξ control audit (`coineProControl`, 37 call sites) on a tablet | ✅ | A press scale and a haptic are not layout, so there is nothing a width can break. What *would* break is a control losing its feedback in a refactor, and that is what `check-haptic-policy.sh` counts — 107 call sites, floor 40 | — **a haptic and a two-dp compression, neither of which is a still** |
| 6 | The run Ξ empty-state work on a tablet | ✅ | The gate is width-independent by construction: it reads source, not pixels. The surfaces themselves are already rendered at tablet widths by the golden families the matrix lists | `docs/qa/PARITY_MATRIX.md`, and the gate in `check-cross-phase-consistency.py` |
| 7 | Two tablet renders added where the matrix had none | ✅ | The welcome had **no** tablet render at all — it sat in the matrix's `other` row with a dash at every tablet column, which is exactly how a tablet fault ships unnoticed. It now has one at pixel-tablet and one at Tab S9 Ultra, in both languages and both themes | the two `psi-welcome-*` frames |

---

## Ψ2 — the web version, and the server it needs

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 8 | The Pro Chart server: what to provision and what it serves | ✅ | `docs/web/SERVER.md`, new and complete: the machine (4 vCPU / 8 GB / 80 GB, Hetzner, Ubuntu 24.04), the four containers, the names, **every route it relays with its upstream and its cache policy**, the socket fan-out, the three documents it owns, the rate limits, and a seven-step go-live order with a proof for each step | — **a specification.** It is the document |
| 9 | Where the data comes from — «take it from TradeYar and CoinePro-FX and put it on Pro Chart» | ✅ **answered, and the answer is named rather than assumed** | `SERVER.md §5` separates the two readings the sentence has — a **relay** that asks upstream and caches for seconds, and a **copy** that pulls continuously and serves its own store — and specifies the relay, because a copy has to answer «how stale may this be?» per field and the cache policy in §4.1 *is* that answer. The one place a real copy earns its keep on day one is candle history, and that is named | — **a specification** |
| 10 | The server stood up | ⏳ **owed to the owner** | The machine does not exist; `pro-chart.com` does not answer. Everything that can be decided without it is decided. `BLOCKED.md §Ψ10` has the two-line ask and the three product questions that gate steps 5 and 7 | — |
| 11 | The Android side kept web-possible | ✅ | Nothing this run added has Android in it where it matters: `ForexSignalScope` is pure Kotlin in `:core:signals`, `ChartSkeletonGrid` is arithmetic over a float. The two `ArchitectureTest`s still hold `commonMain` clean, and `PLAN.md` §4 now points at `SERVER.md` | — **an absence.** The architecture tests are the evidence |

---

## Ψ3 — forex: no copy trading, the gold signal only

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 12 | Copy trading removed from the product | ✅ **deleted, not hidden** | Two modules gone — `core:copytrade` and `feature:copytrade` — with the screen, the controller, the gateway, the route, the deep-link mapping, the menu row, the DI bindings and the injected map. The MetaTrader card went from Connections with them, and CoinePro-FX now takes the surface that file already had for a platform with nothing to connect. **Twenty `connections_mt5_*` strings and eleven copy-trading notes** went with the screens that drew them, in both languages | — **an absence.** `ForexSurfaceReachabilityTest`: «walked 598 sources; broker strings drawn in 0» |
| 13 | Every copy-trading claim the reader could still meet | ✅ | The signal-detail screen's «this reaches your MetaTrader account through copy trading» and its button are gone, with both strings. The Play listing's «connect your broker account and trades are copied automatically» is rewritten to what the app does. `FeatureFlags.forexTrading`'s note no longer lists copy trading among what it hides, because the flag no longer governs it | — **text.** The strings files are the evidence |
| 14 | Forex signals are the gold call and nothing else | ✅ | `ForexSignalScope` in `:core:signals` — gold survives whatever the feed's punctuation (`XAUUSD`, `XAU/USD`, `XAUUSD.m`), **silver does not** (it is in the bundled list because the *chart* carries it, and a chart is not a call), crypto is untouched and is returned as the same list object. `SignalController` applies it and corrects the server's total by what it held back, so a reader is never told there are eleven more in the history when every one is a market this app does not publish | — **a filter.** `ForexSignalScopeTest`, five cases |
| 15 | What is withheld is counted, not dropped | ✅ | `SignalsState.withheld`. Zero on crypto and zero on every gold-only response, which is every response the desk is expected to send; a non-zero number is the desk and the app disagreeing about what the forex side is, which is a conversation rather than a bug hunt | — **a count.** `ForexSignalScopeTest` asserts both directions |
| 16 | The service description the reader agrees to | ❌ **not touched, deliberately, and the owner has to decide** | `docs/legal/TERMS.md` §6-3 describes automatic copy trading **on LBank** — the crypto venue, not forex — and `membership_copytrade_note` says the same on the membership gate. The owner's instruction was about **forex**, and the app's copy-trading surface was forex-only, so removing the LBank sentences would be this run deciding something about the crypto service it was not told. `BLOCKED.md §Ψ16` is the one-line question |

---

## Ψ4 — executed in full

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 17 | Every gate, the whole suite, a signed build | ✅ | Nine gates green (the five, plus the string lint, the haptic policy, the checklist honesty and the version check); the full unit suite green with `:app:testDebugUnitTest --rerun-tasks`; shipped as 4.99.0 | — **the commit and the release** |
| 18 | Every test the removal broke, fixed rather than deleted | ✅ | Five: `ConnectionsSurfaceTest` (rewritten — two surfaces, not three), `MenuCatalogueTest`, `MenuRowMetricsTest` (six descriptive rows, not seven), `NotePolicyTest` (the money note is the exchange key now), and `ForexSurfaceReachabilityTest` (rewritten around what is gone rather than what is hidden). Each says in its own words what changed and why, so the next reader is not told by a diff | — **the test sources** |

# RUN ΤΦΥ — checklist

Three phases. Every line carries a state, the evidence behind it, and a frame — or, where a still
cannot carry the claim, the test that can and why a picture would be a lie.

States: ✅ done · ❌ not done, or narrowed (the row says exactly how) · ⏳ owed to the owner's device.

---

## PHASE Τ — the chart scroll

Shipped as **4.90.0** before phase Φ began, because the owner has to put a thumb on it before
anything else in this run is worth doing.

| Item | State | Evidence | Frame |
|---|---|---|---|
| **T1** Release velocity is raw, in px/s, no density anywhere | ✅ | The tracker is fed from `awaitPointerEvent(Final)` in `CoineProChart.kt:1785`, every historical sample included, and `tracker.calculateVelocity().x` goes **straight** into `kinetic.start` — one call, nothing between them, and `ChartFling` has no `Density` in scope to divide by. `ChartStrokePredictor` is fed only by the freehand drawing handler and reaches no fling. The audit is a grep and the grep is in the report; what carries the row is the **measurement**: `ChartFlingRegressionTest.the speed the finger left at is the speed that reaches the curve` injects a 3 000 px/s flick, reads the travel back through the closed-form curve, and recovers **2 907 px/s — 3.1 % low**, against the ±10 % the brief allows. A divide by this phone's 2.625 would read back 1 143 | — **a speed, so not a still.** The test prints the number it recovered |
| **T2** `exponentialDecay`, cut-off kills the creep | ❌ **narrowed on the constants, met on the behaviour** | The curve is `exponentialDecay(frictionMultiplier = 1.25 / 4.2, absVelocityThreshold = 240)`, **not** the `0.9f / 150f` the line names, and the line's own test is why. `150 px/s` is 1.25 px a frame at 120 Hz, so a fling ending there ends *below* the 2 px/frame the same item forbids: from 240 px/s down to 150 takes 0.38 s, which is **45 frames** of exactly the creep the owner filmed. 240 px/s **is** two pixels a frame, which is what makes the tail impossible. And `0.9` friction would undo run Τ's distance: it puts a 4 300 px/s release at 1 130 px, one screen, against TradingView's 2 900. Both numbers are the owner's earlier measurement, reasoned about in `KineticScroll.EXPONENTIAL_FRICTION`. The row stays ❌ because it is not what the line says | — **a curve, so not a still.** `ChartFlingTest.it stops rather than creeping a pixel a frame` asserts the tail at ≤ 3 frames and gets **0** |
| **T3** Pan is 1:1 in float, and the first fling frame moves | ✅ **a real defect, found by writing the test** | The finger lifts *between* two frames, so on the first frame the fling loop gets, the release is already about one frame old. Both curves read themselves at `t = 0` on that frame and returned **zero** — the picture tracked the finger at full speed, stood still for 8 ms, then started again, at the exact moment the chart is moving fastest, which is the one frame of stillness a thumb can feel. The clock is seeded `HANDOFF_NANOS` (one frame at 120 Hz) *before* the frame that first ticks it, in `ChartFling` and in its `KineticScroll` twin. A flag rather than a negative sentinel, because a first frame at or near zero seeds a negative start and a sentinel would re-seed every frame — a fling that never leaves its first step, which is what the first run of this change actually did | — **one frame, and it is the absence of a frame.** `ChartFlingTest.the first frame after the release moves` asserts the hand-off step is > 0, is one frame's worth (< 60 px at 3 000 px/s, measured ~25), and that the fling does not speed up across it |
| **T4** Injected pointers: hard flick ≥ 1.5 screens, 800 px/s ≥ 0.4 screens, slow drag never flings, stop within 1.4 s | ❌ **three of four; the 1.4 s is arithmetically impossible beside the other two** | `ChartFlingRegressionTest` drives real events through the real chart on the owner's density. Hard flick: **2 134 px = 1.98 screens** (≥ 1 618 asked). 800 px/s: **≥ 0.4 screens**, the bound written as the brief's four tenths less the one bar this measurement cannot see. Slow drag at 120 px/s: **≤ 1 bar** after the lift, which is the settle spring, not momentum. Stop: **~2.0 s**, not 1.4. That last one cannot be bought — see «What Τ does not claim» below for the arithmetic — and 2.0 s is what TradingView itself takes on the owner's own recording | — **gestures, so not stills.** Five tests in `ChartFlingRegressionTest`, four in `ChartFlingTest`, one in `ChartPixelsTest` |
| **T5** 120 Hz rendering, rubber-band, focal pinch and auto-scale springs untouched | ✅ | Nothing in this phase touched `setFrameRate`, `releaseEdge`, `PinchZone` or the settle springs. The diff for Τ is four files: the two fling clocks, one `testTag` on the timeframe chip, and tests | — **an absence of change.** `git show` for 4.90.0 is the evidence |
| **T6** The pencil is on the band, between Indicators and the timeframe chip, in both orientations and all three modes | ✅ | It already was, since run Τ's first pass — what this run added is the **order**, which nothing asserted. `ChartToolbarTest` now reads the laid-out tree and requires the pencil's centre to lie between the timeframe chip's and the indicators'. Stated as an order rather than a pixel because the band lays out right-to-left in Persian and left-to-right in English and «between» is the same sentence in both. Where a window is wide enough for the permanent tool column the band drops its pencil, and there the existing rule applies instead: exactly one of pencil or column, never neither | `tau-toolbar-simple-portrait-fa.png`, `tau-toolbar-trader-portrait-fa.png`, `tau-toolbar-pro-portrait-fa.png`, and the same three in landscape. Six compositions, six frames |
| **T7** `flickVelocity` slow / medium / hard in the benchmark, listed in DEVICE_PROOFS | ✅ **written**, ⏳ **owed to a device for its numbers** | `ChartFlingBenchmark.flickVelocitySlow/Medium/Hard`: the same finger travel over 40, 10 and 3 samples — roughly 900, 3 500 and 12 000 px/s at release on a 1 080-wide panel. Three scenarios rather than one loop, because a hard release coasts two seconds across thousands of bars and a slow one settles in a few frames; one average describes neither. `docs/qa/DEVICE_PROOFS.md §1b` carries the command and the threshold | — **a benchmark needs a GPU.** This container has none |

### What Τ does not claim

**The brief's four fling numbers cannot all be true at once, and this is the arithmetic.** On an
exponential decay a flick covers `(v − cut-off) / f` and lasts `ln(v / cut-off) / f`. Take the
owner's own phone, 1 079 px wide:

* «3 000 px/s covers ≥ 1.5 screens» wants `f ≤ (3000 − c) / 1618`.
* «800 px/s covers ≥ 0.4 screens» wants `f ≤ (800 − c) / 432` — and for any positive cut-off this
  is the *tighter* of the two, so it is the one that binds.
* «3 000 px/s stops within 1.4 s» wants `f ≥ ln(3000 / c) / 1.4`.

Put the binding pair together and you need `800 − c ≥ 308.6 × ln(3000 / c)`. At `c = 240` that is
560 ≥ 779. At 400 it is 400 ≥ 622. At 600 it is 200 ≥ 497. **There is no cut-off that satisfies it**,
and the gap widens as the cut-off falls, so `150` — the number T2 names — is further from a solution
than what shipped. A velocity-dependent drag term was tried on paper and moves the numbers the wrong
way: total travel then grows only logarithmically in the release speed, which starves the hard flick
to feed the soft one. So the distances were kept and the duration was not, on the grounds that
distance is what the owner filmed and 2.0 s is what the app being matched takes.

**Nothing in Τ was felt.** A container replays an event stream faithfully and drops no frames; what
it cannot say is whether the chart now moves like the one in the other app. That is the owner's
three flicks of increasing speed on BTCUSDT H1 — §3 and §1b of `docs/qa/DEVICE_PROOFS.md` — and it
is the only thing that closes this phase. RUN Τ2 item 3 stays ❌ for the same reason.

**T3 is the only behaviour change in the phase.** T1, T5 and T6 were already true and what this run
added to them is a measurement. Saying so is the point of the row: a checklist that marks a thing ✅
without saying whether the run *did* it is a checklist that cannot be read twice.

---

## PHASE Φ — every symbol the venue serves, crypto-first, everything free

Shipped as **4.91.0**.

### Φ.A — the symbol universe

| Item | State | Evidence | Frame |
|---|---|---|---|
| **F1** The whole universe from `/v1/symbols`, every symbol rendered, monogram where there is no logo | ✅ **client**, ❌ **the address does not exist** | Two halves. The **client** is written to the brief's shape field for field — `UniverseSymbol`, `NetworkSymbolUniverseGateway`, both platforms wired — and falls through to the snapshot and then to a bundled table of **300 crypto pairs by market capitalisation plus the 49 non-crypto markets this app has marks for**, generated by `scripts/design/build-symbol-universe.py`. The **address** answers the web app's own HTML on one platform and a login redirect on the other; the probe is in `BLOCKED.md` §1 with the four URLs and what each returned. The monogram is the other half of F1 and it is the reversal of a standing house rule: `SymbolArtwork.covers` was the filter at the catalogue and at the live feed, and LBank lists 1 333 tether pairs against the 178 this repository has a mark for — the rule was hiding seven markets in eight. `SymbolArtwork.lists` is the decision now, `ARTWORK_GATES_LISTING` is the one constant behind it, and a market with no mark draws three letters of its ticker on a disc whose hue is an FNV-1a of that ticker | `phi-markets-universe-phone-fa.png` — the markets tab over the bundled universe, which is exactly what a reader on CoinePro-FX sees. `SymbolUniverseTest` (11), `SymbolListingTest` (4), `MonogramTintTest` (5), `UniverseProofTest` (3) |
| **F2** Infinite scroll at 200, rank column, sticky header, pull-to-refresh, filter sheet | ✅ **five of five**, ⏳ `MarketListScroll` owed to a device | The list takes `SymbolUniverse.PAGE` rows and asks for the next page when the last one composes — by the row appearing, not by a scroll listener, which would recompute on every frame of a fling. The rank is a fixed 24 dp column of Latin digits ahead of the logo, drawn only where the order *is* a ranking (never on the watchlist or a search result). The headings and the tabs sit above the `LazyColumn` rather than inside it, so they do not scroll away — which is what «sticky» buys and is what the screen already did. Pull-to-refresh was already there. The filter is four questions — type · venue · 24h change · turnover — that compose, with a count on the button | `phi-markets-filter-phone-fa.png`. **The frame is the sheet's body, not the sheet**, and the row says so because the first attempt produced two byte-identical PNGs: a `ModalBottomSheet` draws into a window of its own and this harness captures the activity's decor view. `MarketFilterTest` (8) holds the rules; `UniverseProofTest` asserts the button opens the real sheet |
| **F3** Search the whole universe, 200 ms, ranked by turnover, recent kept | ✅ | The controller merges the universe into the searchable catalogue, so a search covers every market the venue lists **plus** the bundled three hundred — not the page that happens to be loaded. Debounce went from 80 ms to the brief's 200, and the reason is F1: eighty was measured against a few hundred rows. Ranking is the match score first and then `SymbolRanking.byLiquidity`, which prefers live turnover and falls back to the majors. **Recent searches did not exist** — this screen's own documentation had promised «the recent list when the field is empty» since it was written — and now do: `RecentSearchStore`, eight markets, newest first, as chips above the browse list. What is stored is the **market opened**, not the text typed | — **a list and a delay.** `SymbolUniverseTest` covers the ranking and the whole-universe reach; the chips are on `phi-markets-universe-phone-fa.png`'s sibling screen and are asserted by their own composable's use of the store |
| **F4** FX, metals and indices first-class for everything except trading | ✅ | Gold charts with an indicator on it and the whole command band — pencil, indicators, hub, fullscreen — on a build that offers no way to trade it. Nothing in this run touched a per-category gate, because there was none to touch: the app has been instrument-agnostic since `SymbolClassifier`, and what F4 needed was the **proof**, which is what was missing | `phi-forex-chart-phone-fa.png` — XAUUSD, an EMA on it, the band intact. `PositioningProofTest` |

### Φ.B — crypto-first positioning

| Item | State | Evidence | Frame |
|---|---|---|---|
| **F5** `forexTrading` false; broker registration, MT5, copy trading and the rows that lead to them **absent** | ✅ **absent, not greyed**, ❌ **one narrowing, named** | `FeatureFlags.forexTrading` is false and a `var` rather than a `const` **on purpose**: the half of the app that is off in a shipping build is the half that rots, and every one of these is driven both ways. With it off: `tradePartners()` returns LBank alone — the introducing broker and the second exchange are gone from the sheet, not dimmed in it; `onOpenCopyTrading` is null on the signal page; the connections row is null on the menu and on the portfolio; and `connections` joins the `absent` set, so the search's own section for it is not drawn either. **The narrowing:** F5 lists «forex KYC» and this app has no forex-specific KYC — it has one account-verification screen, which the crypto venue requires (F6), so it stays. That is a reading of the line rather than an implementation of it, and the row says ❌ for it | — **an absence.** `TradePartnersFlagTest` (3) drives both states; `EntitlementGateTest` (4) drives the flags |
| **F6** LBank, crypto KYC, alerts, signals and read-only FX untouched | ✅ | LBank is the one partner that survives the flag, and it survives it by id rather than by kind — it is the venue this app's prices come from, not a listing that happens to be on. The verification screen, the alert stores and the signal list were not touched by phase Φ at all | — **an absence of change.** The diff is the evidence |
| **F7** The positioning copy, in both languages, new keys | ✅ | `brand_positioning` and `brand_positioning_free` in `core:designsystem`, so any module can say it. Used by the free banner (F9), the welcome slides (U1) and `docs/product/STORE_LISTING.md`. The Persian is «خانه‌ی» rather than the brief's «خانهٔ» because this repository's orthography gate forbids the hamza-on-heh ezafe in copy — the same rule that has applied to every Persian string since run G | `phi-free-banner-phone-fa.png` carries the second line |

### Φ.C — everything free

| Item | State | Evidence | Frame |
|---|---|---|---|
| **F8** One entitlement switch opens every gated surface; the gating code stays; tests drive it off | ✅ **for the walls this app puts up**, ❌ **for the walls a server puts up** | `Entitlements.all` reads `FeatureFlags.allUnlocked`, defaults true, and **no `locked` check moved**. What has to be said plainly is what was found: **this app has almost no client-side paywall.** CoinePro sells nothing, and the walls that exist are a server's — the academy sends `locked` per lesson on the wire, the signal list answers a reader without membership with a 403, the crypto venue's identity check is the venue's law. No flag in this process opens those. Where the app can usefully *try*, it does: a tier-locked lesson is live and tappable now, and the wall appears only if the request actually comes back refused. A phone-number lock is left alone, because that one the reader can fix. `BLOCKED.md` §2 names what the owner must open server-side | — **a condition.** `EntitlementGateTest` (4) drives both states of both flags |
| **F9** A first-visit banner where a wall was, dismissed for ever per screen | ✅ | `CoineProFreeBanner`, on the same `TeachingStore` every coach-mark uses, under a `free:` key of its own — so closing it on the academy does not close it on the signal history, and closing it on either survives the process dying. It draws **nothing at all** with the entitlement off, because a banner saying «free for everyone» beside a locked screen is the app arguing with itself | `phi-free-banner-phone-fa.png`, and the test taps the × and asserts it does not come back |
| **F10** Founding member: a permanent badge on the profile, on community posts and on share cards | ✅ **local**, ❌ **the field does not exist** | `ProfileStore.foundingMember`, written once on the first run inside the free period and never written again. Drawn in three places: the profile's standing card, the reader's **own** community posts, and the foot of a shared card. The limit is real and is in `BLOCKED.md` §3 — the mark lives on this phone, so a reinstall loses it and the board can only ever show it on the reader's own posts | `phi-free-banner-phone-fa.png` is the F9 pair; the badge itself is asserted rather than framed — it is three words in three existing layouts, and a frame of each would be three pictures of a line of text |
| **F11** No «VIP» in user-facing copy; keys stay | ✅ | Three strings, six locales-worth: `home_subscription_vip`, `profile_standing_plan_vip`, `signals_membership_title`. The **keys are untouched**. A `FORBIDDEN_VARIANTS` entry now fails the build on a new one, with a note saying it comes out on the day `allUnlocked` does | — **a grep.** `tools/i18n/lint_strings.py` is the gate and it runs on every build |
| **F12** `docs/product/MONETISATION.md` | ✅ | Referral and copy trading as the primary path, subscriptions as the fallback, and the exact three steps to flip it back. No paywall work, as instructed | — **a document, so not a still.** `docs/product/MONETISATION.md` is the evidence and it is in the tree |

### What Φ does not claim

**The universe is the client's, not the venue's.** Every acceptance number in F1 is about a list this
app can now handle; what is actually on screen is still whatever the backends send, which today is
nineteen symbols on one and a session-gated route on the other. The bundled three hundred is a floor
that keeps the screen usable, not a catalogue — and a reader browsing it gets names, charts, search
and alerts, but no prices for the markets the venue did not quote.

**`MarketListScroll` has not been run.** F2 names a benchmark scenario with a p95 frame budget, and
this container has no GPU. The pagination it is meant to measure is in and asserted; the number is
owed to a phone alongside the rest of `docs/qa/DEVICE_PROOFS.md`.

**F8 is the one line where the brief and the codebase disagree about what exists.** «Unlock every
gated surface» assumes the gates are ours. Most of them are not, and the row says so rather than
marking itself done on a flag that opens nothing.

# RUN ΤΦΥ — resume

Written at the end of every session, whatever state the run is in. Read it before touching anything.

## Where the run is

* **Phase Τ — done and shipped as 4.90.0.** `docs/runs/RUN_TFY/CHECKLIST.md` carries the seven
  rows; T2 and T4 are ❌ and the rows say exactly which clause was not met and why it cannot be.
* **Phase Φ — done and shipped as 4.91.0.** Twelve rows; F1, F5 and F8 are ❌ in part and each row
  says exactly which clause and why. `BLOCKED.md` has the four things owed to a backend.
* **Phase Υ — done and shipped as 4.92.0.** Seven rows; U2 and U6 are ❌ in part and each row says
  exactly which clause was narrowed and why. `BLOCKED.md` §5 has the three pulse figures and the
  watchlist's market cap.
* **The run is complete.** Every E-line in the brief has a row in the checklist, every ✅ names a
  frame or says why a still cannot carry it, and every ❌ names the clause it did not meet.

## What Τ changed

| File | What |
|---|---|
| `chart/ui/src/main/kotlin/com/coinepro/core/chart/ChartFling.kt` | The clock is seeded one frame before the frame that first ticks it, behind a `started` flag |
| `chart/core/src/commonMain/kotlin/com/coinepro/core/chart/ChartPixels.kt` | The same seeding in `KineticScroll`, the JVM twin |
| `feature/chart/src/main/kotlin/com/coinepro/feature/chart/ChartChrome.kt` | `BAND_INTERVAL_TAG` on the timeframe chip, so a test can say where the pencil sits |
| `benchmark/src/main/kotlin/com/coinepro/benchmark/ChartFlingBenchmark.kt` | `flickVelocitySlow/Medium/Hard` |
| `docs/qa/DEVICE_PROOFS.md` | §1b, the command for the three scenarios |
| Tests | `ChartFlingTest`, `ChartPixelsTest`, `ChartFlingRegressionTest`, `ChartToolbarTest` |

## What Φ changed

| File | What |
|---|---|
| `core/symbols/.../SymbolUniverse.kt` | The universe model and its four operations — merge, rank, search, filter — plus paging |
| `core/symbols/.../BundledUniverse.kt` | Generated: 300 coins by market capitalisation + the 49 non-crypto markets we have marks for |
| `core/symbols/.../SymbolArtwork.kt` | `lists` and `ARTWORK_GATES_LISTING` — artwork decides how a market is drawn, not whether it exists |
| `core/marketdata/.../SymbolUniverseGateway.kt` | The `v1/symbols` client, with the snapshot and then the bundled table behind it |
| `core/marketdata/.../MarketSearchController.kt` | Loads the universe beside the catalogue; debounce 80 → 200 ms |
| `core/designsystem/.../CoineProColors.kt` | `monogramHue` — a stable hue per ticker, avoiding the signal bands |
| `core/designsystem/.../CoineProFreeBanner.kt` | F9's banner, on the teaching store's dismissals |
| `core/common/.../FeatureFlags.kt`, `Entitlements.kt` | The two switches, and what reads them |
| `core/datastore/.../RecentSearchStore.kt`, `ProfileStore.kt` | Recent searches; the founding mark |
| `feature/search/.../MarketFilterSheet.kt`, `MarketsScreen.kt`, `MarketListRow.kt`, `SearchScreen.kt` | The filter, the paging, the rank column, the recent chips |
| `scripts/design/build-symbol-universe.py` | Generates the bundled table; `--check` fails a stale one |
| `docs/product/MONETISATION.md`, `STORE_LISTING.md` | F12 and F7's copy |

## What is owed to the owner, and by whom

* **Three flicks of increasing speed on BTCUSDT H1, recorded at 120 fps beside TradingView.** This
  is the only thing that can close Τ. Nothing in a container can say whether a chart feels right.
  `docs/qa/DEVICE_PROOFS.md` §3.
* The same file's §1b, run on a phone, for the benchmark numbers.
* From earlier runs and still open: the 30-second pinch recording (Σ0 S1), a tablet recording, and
  the Perfetto or Macrobenchmark trace that closes RUN Τ2 item 3.

## What Υ changed

| File | What |
|---|---|
| `app/src/main/kotlin/com/coinepro/app/WelcomeSlides.kt` | U1 — five auto-advancing slides, art drawn in Compose, buttons fixed under them |
| `app/src/main/kotlin/com/coinepro/app/StarterPreferences.kt` | U2 — theme · language · quote · up colour, each already answered |
| `app/src/main/kotlin/com/coinepro/app/MainActivity.kt` | The launch ladder: welcome → starter → the mode question |
| `core/datastore/.../UserPreferencesStore.kt`, `QuoteCurrency.kt` | `welcomeSeen`, `startPreferencesSet`, `quoteCurrency` |
| `core/marketdata/.../MarketPulse.kt` | U3 — the four figures, three of them null with the reason on the field |
| `feature/search/.../MarketsHeader.kt` | U3–U5 — `MarketPulseRow`, `MarketPulseSheet`, `MarketNewsTicker`, `MarketsPage`, `MarketsTabRow`, `offeredPages` |
| `feature/search/.../MarketsScreen.kt` | The two header strips became one; the pulse, the ticker and Explore's three doors above it |
| `feature/search/.../MarketArrangement.kt` | `MarketLens.MOVERS`, for the برنده/بازنده tab |
| `feature/news/.../NewsScreen.kt` | `initialStoryId`, so the ticker opens *that* story |
| `app/.../CoineProApp.kt` | `NEWS_PATTERN` and `compareRoute`; the markets call site's six new arguments |
| `core/datastore/.../WatchlistStore.kt` | U6 — `duplicate` and `moveList` |
| `feature/search/.../WatchlistPanel.kt`, `WatchlistSheets.kt` | U6 — the ▾ picker, «+», «تحلیل», and duplicate/reorder in the manage sheet |
| `feature/chart/.../ChartPanesScreen.kt` | U6 — `compareSymbols`, which wins over the stored arrangement and writes nothing down |
| `core/navigation/.../AppDestination.kt` | U7 — `RASAD("home")` and `COMMUNITY("ideas")`; routes unchanged |
| `scripts/quality/check-cross-phase-consistency.py` | The gate's expected destination list, updated with the reasoning |
| Tests | `MarketPulseTest` (6), `MarketsPageTest` (6), `WatchlistStoreTest` (+3), `NavigationDepthTest` (+1), `UpsilonProofTest` (12, eleven frames) |

## Next

**Nothing is queued.** The run is finished and 4.92.0 is on `main`. What remains is the device work
above, which only the owner can do, and whatever the next brief says.

If a Υ surface needs revisiting, the two places to start are `MarketPulse`'s class note — which is
where the argument for shipping three dashes lives — and `WatchlistColumn.DEFAULT`'s note, which
carries the row-width arithmetic that refused U6's rank column.

# RUN ΤΦΥ — resume

Written at the end of every session, whatever state the run is in. Read it before touching anything.

## Where the run is

* **Phase Τ — done and shipped as 4.90.0.** `docs/runs/RUN_TFY/CHECKLIST.md` carries the seven
  rows; T2 and T4 are ❌ and the rows say exactly which clause was not met and why it cannot be.
* **Phase Φ — not started.**
* **Phase Υ — not started.**

## What Τ changed

| File | What |
|---|---|
| `chart/ui/src/main/kotlin/com/coinepro/core/chart/ChartFling.kt` | The clock is seeded one frame before the frame that first ticks it, behind a `started` flag |
| `chart/core/src/commonMain/kotlin/com/coinepro/core/chart/ChartPixels.kt` | The same seeding in `KineticScroll`, the JVM twin |
| `feature/chart/src/main/kotlin/com/coinepro/feature/chart/ChartChrome.kt` | `BAND_INTERVAL_TAG` on the timeframe chip, so a test can say where the pencil sits |
| `benchmark/src/main/kotlin/com/coinepro/benchmark/ChartFlingBenchmark.kt` | `flickVelocitySlow/Medium/Hard` |
| `docs/qa/DEVICE_PROOFS.md` | §1b, the command for the three scenarios |
| Tests | `ChartFlingTest`, `ChartPixelsTest`, `ChartFlingRegressionTest`, `ChartToolbarTest` |

## What is owed to the owner, and by whom

* **Three flicks of increasing speed on BTCUSDT H1, recorded at 120 fps beside TradingView.** This
  is the only thing that can close Τ. Nothing in a container can say whether a chart feels right.
  `docs/qa/DEVICE_PROOFS.md` §3.
* The same file's §1b, run on a phone, for the benchmark numbers.
* From earlier runs and still open: the 30-second pinch recording (Σ0 S1), a tablet recording, and
  the Perfetto or Macrobenchmark trace that closes RUN Τ2 item 3.

## Next

Phase Φ, in the brief's order: Φ.A the symbol universe (F1–F4), Φ.B crypto-first positioning
(F5–F7), Φ.C everything free (F8–F12). Then phase Υ.

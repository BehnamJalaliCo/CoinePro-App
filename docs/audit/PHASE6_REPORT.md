# Phase 6 — tests, CI, release

## Done

| Item | Where | Note |
| --- | --- | --- |
| Calculator validations, every `tools_rule_*` | `feature/tools/src/test/…/TraderToolsRulesTest.kt` | Nine rules in the strings, nine reached on purpose with the input that trips each; a tenth added without a case fails the count. |
| i18n lint in the suite | `tools/i18n/lint_strings.py`, run by the consistency gate and by CI | Phase 3. |
| Help-content schema | `HelpCatalogTest` (count, collisions, aliases, both languages) | Phase 0. |
| Indicators and NamaScript | `IndicatorParityTest` (1e-6 against the web terminal, 60+ series), `NamaScriptTest`, the script error tests | Already present. |
| Screenshot matrix | `GoldenScreenshotTest` + `app/src/test/goldens/` | Was fa × {light, dark} × {393, 411} plus one EN. Now also **EN dark**, **tablet 840 dp** (watchlist and menu, past the two-pane threshold) and **font scale 1.3** (menu and watchlist at 393 dp, where the longest strings wrap first). 20 goldens; every one compared at 0.1 % tolerance on real Skia pixels. Not all twelve screens at all sixteen points: that is a diff nobody reads, so each axis is pinned on the screen where it bites. |
| Benchmark budget in CI | `scripts/quality/check-benchmark-thresholds.py` | P95 frame ≤ 8 ms on the chart gestures, no overrun, cold start ≤ 800 ms. Reads the macrobenchmark JSON; CI runs it with `--allow-missing` because the runner has no device, and says "skipped", never "passed". |
| Download-size check in CI | `scripts/release/check-bundle-size.sh`, workflow step *Check the download size a phone would pay for* | bundletool builds the split APK set from the signed AAB and `get-size total` reports the largest per-device download; the budget is 9 MiB. Fails the release build when exceeded. |
| Release docs | `docs/release/CHECKLIST.md` | Signing, R8 wire fields, release surface, pins, App Links, benchmark, PAD, store strings from the app's own vocabulary. |

## Not done, and why

| Item | Reason |
| --- | --- |
| Compose UI tests for chart gestures, watchlist reorder, alert fire, replay end-to-end | The gesture arithmetic is unit-tested (`ChartCanvasGesturesTest`, 15 cases), the replay session (`ChartReplayTest`, `ReplayAndTradeTest`), watchlist and alert stores each have their own tests. Driving the real `Canvas` with `performTouchInput` under Robolectric needs the pointer-input pipeline the screenshot rig does not exercise; it is a separate rig to build, not a test to add. |
| `testStoreReleaseUnitTest :app:bundleStoreRelease` | There is no `store` flavour — Phase 2 replaced flavours with a `BuildConfig` gate plus the APK-reading check, so the CI line stays `testDebugUnitTest` + `:app:assembleRelease` + `:app:bundleRelease`. |
| Benchmark numbers | Need a phone (Phase 4). The script that judges them is in CI; the JSON is a person's to produce. |

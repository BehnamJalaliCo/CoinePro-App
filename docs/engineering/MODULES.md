# Modules — the chart and the language, split by what they need from a platform

Since 4.48.0 the chart is two modules and the indicator language is two modules, cut along one
line: **does this code need Android?** Everything on the "no" side is a Kotlin Multiplatform
module that compiles for the JVM and for Android today and will compile for the web terminal
(`docs/web/PLAN.md`) without a rewrite. Everything on the "yes" side is an ordinary Android
library, as before.

| Module | Directory | Plugin | Targets | Lines (main) | Tests | What is in it |
| --- | --- | --- | --- | ---: | ---: | --- |
| `:chart-core` | `chart/core` | `org.jetbrains.kotlin.multiplatform` + `com.android.kotlin.multiplatform.library` | `jvm()`, Android | 15 962 | 36 files, `jvmTest` | scales, viewport, series transforms, indicators (four files), the indicator chain and templates, drawings (state, geometry, controller, catalogue), candle patterns, structure, setup zones, replay, the backtester, the object tree, comparison, the catalogue of chart types, the TradingView palette, event marks |
| `:chart-ui` | `chart/ui` | `com.android.library` (was `:core:chart`) | Android | 12 377 | 18 files | `CoineProChart` (the Canvas composable), the drawing renderer, the legend overlay, the tool rail, the pickers, the series-type painters, the alert affordance, the text cache, `ChartIcons` |
| `:namascript` | `namascript` | multiplatform, as above | `jvm()`, Android | 3 573 | 4 files, `jvmTest` | lexer, parser, AST, values, interpreter, built-ins, sources, results, the reference, the lessons, the presets, the strategies, the overlay model |
| `:core:script` | `core/script` | `com.android.library` | Android | 251 | 1 file | `ScriptController` — saving scripts (Room) and running them on a chart (coroutines) |

Package names did not change. Both halves of the chart are `com.coinepro.core.chart`, both halves
of the language are `com.coinepro.core.script`; a split package across two modules is legal on
every target and it kept the move to a move, with no import rewritten in the thirty modules that
use them. `:chart-ui` re-exports `:chart-core` (`api`), `:core:script` re-exports `:namascript`,
so a consumer that used `:core:chart` and now uses `:chart-ui` sees the same types. Four modules
only ever wanted the engine and now say so: `:core:backtest`, `:core:chartevents`, `:core:script`
and `:feature:screener` depend on `:chart-core` alone.

## The platform seam

`:chart-core` needs five things from the world, declared once as `expect` in
`chart/core/src/commonMain/.../ChartPlatform.kt` and implemented once for both JVM-based targets
in `src/jvmShared`:

| Declaration | Replaces | Used by |
| --- | --- | --- |
| `ChartZone` (`fun interface`, an offset per moment) + `systemChartZone()` | `java.time.ZoneId` in the engine's API | `TimeScale.ticks`, `TimeScale.boundaryOf` — `ZoneId.asChartZone()` adapts at the UI's call site |
| `currentTimeMillis()` | `System.currentTimeMillis()` | drawing fade-out |
| `formatFixed(value, decimals)` | `String.format(Locale.US, "%.nf")`, `NumberStyle.fixed` | axis labels, Fibonacci ratios, `formatPrice` |
| `formatLocalMoment(epochSeconds, pattern)` | `Instant…atZone…DateTimeFormatter` | the object tree's row labels |
| `CivilDate.ofEpochDay` (pure, not `expect`) | `java.time.LocalDate.ofEpochDay` | the time axis' year/month boundaries |

Icons were the other tie: a catalogue entry carried an `@DrawableRes Int`. It now carries a
`ChartIcon(name)` and `:chart-ui`'s `ChartIcons.drawable(icon)` is the one `when` that turns a
name into a resource. `ChartIconsTest` reads the engine's sources and fails when a name has no
branch or no drawable file; `ChartCatalogTest` and `DrawingToolsTest` still check the help
catalogue, from the module's new position.

The `actual`s are byte-for-byte what the code did before — `Locale.US`, the same patterns — so no
label or ratio changed. `ChartIconsTest`, `TimeScaleTest` and `ChartTimeZoneTest` are the tests
that would notice if one had.

`:namascript` needs nothing of its own: the two things it had taken from `core:common`
(`toPersianDigits` for a default plot title, `MarketNumberFormatter.priceAuto` for a number a
script turns into text) are restated in `Builtins.kt` in eight lines, and `ScriptFailure.text`
takes a `Boolean` rather than the app's `AppLanguage`; `core/script/ScriptFailureText.kt` keeps
the `AppLanguage` overload for the screen that calls it.

## What keeps it true

* **The compiler.** `commonMain` is compiled for the JVM target on every build; an `android.*` or
  `androidx.*` import does not resolve there. `java.*` does resolve on both current targets, which
  is why there is also:
* **`ArchitectureTest`** in each multiplatform module's `jvmTest`: reads `commonMain` as text and
  fails on any import of `android.`, `androidx.`, `java.`, `javax.`, `kotlinx.coroutines.android`
  or one of the app's Android-only modules, naming the file and line. `:chart-core`'s version also
  insists the only file with an `expect` in it is `ChartPlatform.kt`, so the next target's author
  has one file to implement.
* **The gates.** `check-motion-policy.sh` and `check-kotlin-style.sh` scan `chart/**` and
  `namascript/**` as they scan `core/**`; `check-cross-phase-consistency.py` reads the tool rail
  from its new path.
* **CI.** `android-ci.yml` runs `:chart-core:jvmTest`, `:namascript:jvmTest`,
  `:chart-ui:testDebugUnitTest` and `:core:script:testDebugUnitTest` by name; the repository's
  `./gradlew testDebugUnitTest` gate also reaches the JVM tests, because each multiplatform module
  registers a `testDebugUnitTest` task that depends on `jvmTest`.

## Tests, by module

| Module | Suite | What it covers |
| --- | --- | --- |
| `:chart-core` (`jvmTest`, JUnit 4) | 36 files: Backtest, CandlePatterns, ChartCatalog, ChartEvents, ChartHistory, ChartPixels, ChartTransforms, ChartTypes, ChartViewport, Comparison, DrawingController, DrawingGeometryA/B, DrawingLock, DrawingStyle, DrawingTools, DrawingWaveThree, IndicatorChain, IndicatorParity (the TradingView fixture, `src/jvmTest/resources/indicator-parity.txt`), IndicatorPeriod, IndicatorTemplate, IndicatorTrailing, IndicatorsExtB/C, LogScale, ObjectTree, PositionTool, PriceScale, ReplayAndTrade, Replay, SetupZone, Structure, TradingViewSourceConstants, VolumeProfileWindow, Architecture | the engine's arithmetic, with no Android on the classpath |
| `:chart-ui` (`testDebugUnitTest`) | 18 files: the fourteen that exercise `CoineProChart.kt`'s own functions (legend rows and summary, pane bands, gestures, countdown, focus, pan restore, previous close, time formatting, the two zone tests, series-type painters, wave-two drawings) and the three that need Compose types (frame, pointer/profile, image), plus `ChartIconsTest` | what the composable computes before it draws |
| `:namascript` (`jvmTest`) | NamaScript, ScriptLibrary, ScriptStrategies, Architecture | the language |
| `:core:script` | ScriptController | saving and running |

## What did not move, and why

* `ChartTextCache.kt` stays in `:chart-ui`: it is an access-ordered `LinkedHashMap` with
  `removeEldestEntry`, a JVM-only constructor, and it caches *measured text*, which is a rendering
  concern.
* `ChartFrame.kt` (the plot frame with its gutters) stays: it is built from Compose `Offset`s.
* `formatTime`, `formatTimeTick`, `previousSessionClose` and the rest of `CoineProChart.kt`'s
  helpers stay: they take a `ZoneId` and a Compose `TextMeasurer`, and moving them means moving
  the seam further than the plan asked. They are the natural next cut when the web terminal needs
  an axis.

## How to add to the engine

1. Put the file under `chart/core/src/commonMain/kotlin/com/coinepro/core/chart/`.
2. If it needs the clock, the zone or a formatter, take it from `ChartPlatform.kt`; if it needs
   something new from the platform, add the `expect` there and the `actual` in `src/jvmShared`.
3. If it names an icon, add the branch to `ChartIcons` in `:chart-ui`; `ChartIconsTest` tells you
   which.
4. `./gradlew :chart-core:jvmTest` — the architecture test runs with the rest.

# RUN WEB — report

The web terminal's first release: the phone's chart, drawn by the phone's code, in a browser, on the
relay's live candles. What was done, what was measured, and what was chosen against the brief.

---

## 1. The phone did not change

The whole port is a refactor from the phone's point of view (`TERMINAL_BUILD_PROMPT.md` rule 3),
so the first thing measured was the one thing that could silently move it: the dependency graph.

Compose Multiplatform's Android artifacts are redirects to androidx Compose. Pin the wrong version
and Gradle quietly upgrades the phone's Compose under a refactor that promised to change nothing.
So `:app:dependencies --configuration releaseRuntimeClasspath` was resolved twice — on `HEAD` in a
separate worktree and on this tree — and every `--- androidx.G:A:V -> W` line reduced to its final
version:

| | `androidx.*` modules | differing |
|---|---|---|
| Compose MP 1.9.0 + Material 3 **1.9.0** | 152 / 152 | **Compose 1.9.2 → 1.9.3 across foundation, ui, animation, material-ripple** — refused |
| Compose MP 1.9.0 + Material 3 **1.8.2** | 152 / 152 | **none** |

Material 3 1.9.0 requires Compose 1.9.1, whose Android side is androidx 1.9.3 — above the app's
BOM. 1.8.2 requires 1.8.2, below it, so the BOM wins and the phone is the build it was. The pin is
written into `libs.versions.toml` with that reason beside it.

Then the suite: `./gradlew testDebugUnitTest` green (every golden screenshot test included) and
`./gradlew :app:assembleRelease` green, as two invocations.

## 2. The seam

`chart/ui/src/commonMain/.../ChartUiPlatform.kt`. Every `actual` on Android is the code that was
at the call site before, moved and not changed: the Tehran `ZoneId` (the platform zone *is*
`java.time.ZoneId`, as a typealias, so no Android caller changed a type), `DateTimeFormatter` with
`Locale.US`, `JalaliDate` for the Solar Hijri axis, `LocalConfiguration`, the frame-rate vote and
stylus predictor behind one `ChartHost`, Android's magnifier, the native shadow paint, `BitmapFactory`,
`synchronized`, `coineProControl`, `CoineProAssetLogo`, two `painterResource`s and seven
`stringResource`s.

The browser's side, and what it does without:

* **Time.** A fixed +03:30 for Tehran (Iran has kept no daylight saving since 2022, so a fixed
  offset is exact), the browser's own zone per moment for «the device's zone», a pattern printer
  over Hinnant's civil-date arithmetic — no `Intl`, for `ChartPlatform.wasmJs.kt`'s reason.
* **Solar Hijri.** `JalaliDate`'s Borkowski break table, ported line for line, because
  `:core:common` is an Android module.
* **No frame-rate vote** (a browser already paints at the display's rate), **no stylus
  prediction** (the live stroke ends at the last real point), **no magnifier lens**, **no blur**
  under the price chip. None of these is faked.
* **Marks the typeface lacks.** IRANYekanX carries no `·`, `Δ`, `◉`, `⋮`, `✕`, `—` or `∅`
  (measured with its character map). On the phone the system's fallback fonts draw them; a browser
  page has no fallback and one typeface by rule, so the first frames showed empty boxes. `ChartMarks`
  gives each the nearest mark the typeface does carry — `•`, `±`, `°`, `¦`, `×`, `…` — in the
  browser only.

## 3. The bundle, and why there is no webpack

`:web:wasmJsBrowserDistribution` needs the Kotlin plugin's test tooling, whose Karma is fetched
from a GitHub archive at build time. That download has nothing to do with the page and failed here
(`403`). The compiler already emits browser-ready ES modules, so `:web:terminalBundle` ships them
as they are:

```
index.html                      1.3 KB   import map for @js-joda/core, <base href="/terminal/">
terminal.mjs                    0.2 KB
terminal.uninstantiated.mjs    32 KB
terminal.wasm                 2.4 MB   Binaryen-optimised
skiko.mjs                     613 KB   Skia's runtime
skiko.wasm                    8.0 MB
js-joda.esm.js                392 KB   imported by name through kotlinx-datetime
fonts/iranyekanx_{regular,medium,semibold,bold}.ttf   336 KB
```

Node and Yarn come from the machine rather than a download, because `settings.gradle.kts` refuses
project repositories; Binaryen still downloads, from a repository the settings declare for it alone.

## 4. What the page does

`/terminal/{SYMBOL}/{tf}` — eleven instruments (six crypto, five forex, each fetched live on
2026-09-23 before it was listed) and the four timeframes the forex route answers. Crypto: candles
once, the venue's snapshot every 2 s moving the forming bar, candles again every five minutes.
Forex: candles every 30 s, never spliced with the forex price, which comes from a different
upstream. Nothing is asked while the tab is hidden. One random `X-Client-Id` per browser. Persian by
default, English on one tap, remembered. A failed load retries once after three seconds, then says so
with a button.

## 5. What is not in it

The workbench, the rails, the layout grid, the object tree, the watchlist, the screener (W2) and
the script studio (W3). The page is a chart. See `CHECKLIST.md`.

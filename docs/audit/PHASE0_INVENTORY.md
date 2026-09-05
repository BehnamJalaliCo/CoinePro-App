# Phase 0 — Inventory (read from the source, 2026-09-05, at 4.32.2)

The static audit of the 4.32.1 APK (`CoinePro_Audit_and_ClaudeCode_Prompt.md`) was written from an
obfuscated binary. This is the same inventory written from the repository, with each of the audit's
premises marked **confirmed**, **wrong** or **partly right**. Where the audit was wrong, the
correction is the point of the line; nothing downstream should be built on a premise the source
contradicts.

## 1) What the app is made of

| | |
|---|---|
| Modules | 77 Gradle modules: `app`, `benchmark`, 37 `core:*`, 38 `feature:*` (`settings.gradle.kts`, pinned against `docs/PRODUCT_ROADMAP.md` by the consistency gate) |
| UI | Jetpack Compose + Material 3 throughout; no View XML |
| Navigation | **`androidx.navigation:navigation-compose`** (`app/build.gradle.kts:513`, `NavHost` in `CoineProApp.kt`). The audit's "no navigation library, hand-rolled" is **wrong** — R8 renamed it out of sight. |
| DI | Hilt (`AppModule.kt`, 1,700 lines, per-platform qualifiers `@ForexPlatform` / `@CryptoPlatform`) |
| Data | Room 2.8.4 (`core:database`), DataStore preferences + proto (`core:datastore`), WorkManager, OkHttp + Retrofit/Gson, OkHttp WebSocket (`MarketDataController`) |
| Chart engine | Own Canvas renderer in `core:chart` — 38 files, 27,359 lines. Entry points: `CoineProChart.kt` (composable, three `pointerInput` blocks for pan/zoom/draw, `invalidate(level)` with three invalidation levels), `ChartViewport.kt` / `TimeScale.kt` (scales), `DrawingController.kt` + `DrawingRenderer.kt` + `Drawings.kt` (90+ tools), `Indicators*.kt` + `IndicatorChain.kt` (83 indicators), `Replay.kt`, `ChartTextCache.kt` (axis-label measurement cache — the audit's "text measurement caching" already exists). |
| NamaScript | `core:script` — `Lexer.kt`, `Parser.kt`, `Ast.kt`, `Interpreter.kt`, `Builtins.kt`, `ScriptStrategies.kt`; `feature:script` is the editor. |
| Images | **Vendored vector artwork**, not a loader. `core:symbols/SymbolArtwork.kt` (1,002 lines) maps every listed symbol to a packaged logo, and `SymbolArtwork.covers` is the house filter — a symbol with no artwork is never listed. The audit's "no Coil/Glide, so logos are probably not shown" is **wrong**; the watchlist, markets, chart legend, symbol wheel and heat map all draw them. Avatars are generated (`CoineProAvatar`), not fetched. |
| Fonts | IRANYekanX Regular + Bold only. **Latin digits are already tabular** — every digit advances 562 units in Regular and 572 in Bold, read off the TTF (`check_tabular_digits` in the consistency gate). Persian digits are proportional (۱ = 238, ۳ = 655), which is why market figures are Latin by rule. The audit's "no tabular figures, prices wobble" is **wrong** for prices; a Latin companion font would change the look of every number for no gain. |
| Locale | `values/` Persian, `values-en/` English. **Kept by the owner's decision** (`CLAUDE.md`: Persian is the default). |
| Deep links | `coinepro://signal|activity|market` (manifest + `BrandConfig.SCHEME`), `https://coineprofx.com/reset-password`, `https://user.tradeyar.trade-future.ir/reset` (both App Links). |
| Security | Biometric app lock, Keystore-backed secrets, signature tamper screen (`EXPECTED_SIGNERS`), `allowBackup=false`, `usesCleartextTraffic=false`. **No certificate pinning, no Play Integrity** — confirmed; both need material only the owner holds (pins, a Play project). |
| Observability | Firebase Messaging only. No analytics SDK; crash report is local + a copy button on the safety screen. |
| CI | `android-ci.yml` (lint/test/build · Compose UI on an emulator · benchmark smoke on an emulator), `android-apk.yml` (signed release APK → GitHub release), `internal-release.yml` (**staging AAB** → Play internal), `security-ci.yml`, `pages.yml`, `production-readonly-smoke.yml`, `rendered-app-screenshot.yml`, `connected-test-apk.yml`. |

## 2) Test baseline

* **3,667 unit tests, 0 failures** (`testDebugUnitTest`, every module, last local run before this phase).
* Golden screenshot gate (`GoldenScreenshotTest`, Robolectric native graphics): 12 goldens, fa-411/393, dark and light, plus the Ideas switch in both themes.
* Instrumented: `VisualParityCaptureTest`, `ActualAppMenuScreenshotTest` (a real 1080×2400 PNG off the emulator, PNG-magic-checked).
* Benchmark: `benchmark/` module — `StartupBenchmark`, `BaselineProfileGenerator`. **No chart-fling or list-scroll macrobenchmark exists yet**; the audit's p95 ≤ 8 ms budget has nothing measuring it. That is Phase 4's first task and is not claimed here.

## 3) Size of the 4.32.1 store APK (read from the file)

| Part | Compressed | Raw |
|---|---:|---:|
| `assets/help/images` (215 WebP) | 8.31 MB | 8.31 MB |
| dex | 4.26 MB | 9.12 MB |
| resources | 3.54 MB | 5.17 MB |
| `assets/help/content.json` | 0.57 MB | 1.99 MB |
| `assets/help/content.json.orig` | — | **0.85 MB, packaged by accident** (confirmed; removed in this phase) |
| native, four ABIs | 0.40 MB | — (x86 + x86_64 = 0.22 MB; removed from release in this phase) |
| **Total** | **17.18 MB** | |

After this phase the release APK built locally (unsigned, same R8 configuration) is **16.99 MB**:
the `.orig`, the two emulator ABIs and the admin strings are gone, and `check-release-surface.py`
confirms all three off the file.

Help images are half the download. Moving them to on-demand delivery is Phase 2 work and needs a
CDN or Play Asset Delivery decision from the owner; nothing here pretends to have done it.

## 4) Brand

Owner's decision, 2026-09-05: the product is **Pro Chart / پرو چارت**; `CoinePro` stays as the
company, the repository, the package id and the website. `core/common/BrandConfig.kt` is the single
source for the display names, the URI scheme, the recovery host and the legal base URL; the
consistency gate fails the build on `Pro CHart`, `Pro-Chart`, `پروچارت` in any string resource or
legal document. Before this phase the typo `Pro CHart` appeared in 29 strings and the app's own
`app_name`.

Remaining decision for the owner: the website (`scripts/site/build-site.py`) and the GitHub release
title still say «CoinePro · کوین‌پرو». They are the company's pages; if the owner wants the product
name there too it is one string in the site builder and one in `android-apk.yml`.

## 5) Hosts the app talks to (source, not guesses)

| Host | Role | Direct from the device? |
|---|---|---|
| `coineprofx.com` | CoinePro-FX API (forex/metals), password-recovery App Link | yes — ours |
| `tradeyar.trade-future.ir` | TradeYar API (crypto), public routes, calendar relay | yes — ours |
| `terminal.coinepro.com` | Web terminal (WebView) | yes — ours |
| `lbkperp.lbank.com` | LBank public order book (`LBankPublicOrderBookGateway`) | yes — the venue's own public route |
| `www.investing.com`, `www.cointelegraph.com` | RSS, **fallback only** when our newsroom answers empty | yes — behind `BuildConfig.DIRECT_THIRD_PARTY_FEEDS` |
| `nfs.faireconomy.media` | ForexFactory weekly calendar, **fallback only** after both our hosts | yes — same switch |
| `behnamjalalico.github.io/CoinePro-App` | Legal pages and account deletion | link only |
| `t.me/CoinePro_Admin` | Support | link only |

The audit's "RSS hit from thousands of phones" is **partly right**: the order of sources is ours
first and the wires last, so the third-party hosts are reached only when the backend answered an
empty section. `docs/backend/FEEDS.md` is the contract that makes the switch flippable to off.

## 6) The audit's findings, checked

| # | Finding | Verdict | Done in this phase |
|---|---|---|---|
| 1 | Persian is the default locale | true by design; owner keeps it | — |
| 2 | Brand spelt five ways | confirmed | typo fixed everywhere, `BrandConfig`, gate |
| 3 | 29 keys with no English | **7** are the app's own (the other 22 are library/Firebase keys) | all 7 translated, plus `field_clear` |
| 4 | Terms Persian-only; privacy says "App version 1.0" | confirmed | `TERMS_EN.md` written and shipped; stamp removed |
| 5 | Iran-specific KYC | confirmed, not in this phase | Phase 1.5 |
| 6 | `.ir` host and TradeYar in the manifest | it is the owner's own backend | no change |
| 7 | Third-party feeds from the device | fallback only; confirmed | switch + contract doc |
| 8 | 8.4 MB of help images; `content.json.orig` packaged | confirmed | `.orig` removed; gate on `src/main/assets` and on the built APK |
| 9 | APK not AAB; x86 ABIs | staging already ships an AAB to Play; release drops x86 now | release ABI filter |
| 10 | Admin panel in the store build | confirmed | `BuildConfig.ADMIN_PANEL=false` in release; gate reads the APK for `admin_*` |
| 11 | No pinning | confirmed | needs the owner's pins — Phase 2 |
| 12 | No Play Integrity | confirmed | needs a Play project — Phase 2 |
| 13 | Engineering language in UI | confirmed (7 strings) | rewritten, and each retired word is now a gate failure |
| 14 | `terminal_error_disabled` copy-paste | confirmed | fixed |
| 15 | `chart_band_more` fa/en disagree; `STALE` | confirmed | «تحلیل» / Analysis; Stale / قدیمی |
| 16 | Three description sets for one feature | confirmed, not in this phase | Phase 3 |
| 17 | Duplicate help ids; 9 topics with no English | **partly right**: the pairs differed only in case and were *different* entries — the app's indicators pointed at the export's copy while a richer entry written for this app sat unused | indicators point at their own entries, four export copies removed, aliases keep old ids working, 27 English fields written, schema test |
| 18 | Two font weights; no tabular digits | two weights confirmed; tabular digits **already true** | gate that reads the TTF |
| 19 | Legal pages on GitHub Pages | confirmed; needs the owner's domain | — |
| 20–23 | Motion: tween-heavy, few springs, one shimmer, two haptics | counts from source: `tween(` 44, `spring(` 2, shimmer in 5 files, **74 haptic call sites** (the audit's "two" is wrong), `SharedTransition` in 2 files, `BackHandler` in 5 | Phase 5 |

## 7) Decisions recorded for the owner

1. **Default locale stays Persian.** (Decided.)
2. **Brand is Pro Chart / پرو چارت.** (Decided.) Website and release titles still say CoinePro — flip or keep.
3. **Third-party feed fallback stays on** until `docs/backend/FEEDS.md` is served; the switch is `COINEPRO_DIRECT_THIRD_PARTY_FEEDS=false`.
4. **No Latin companion font.** Digits are already tabular; the gate proves it on every build.
5. Pinning, Play Integrity, on-demand help images, brand domain for legal pages: each needs something only the owner holds and is listed for Phase 2, not started.

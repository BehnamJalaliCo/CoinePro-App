# The brief for the web terminal

Hand this to a Claude Code running **on the CoinePro-App repository**. It is written to be read
cold, by an agent that has none of the conversation that produced it: everything it needs is either
here or discoverable in the repository.

The server is **not** where this work happens. `pro-chart.com` is already built and serving
(`SERVER_BUILD_PROMPT.md` was its brief); it is waiting for a static bundle to put at `/terminal/`
and needs nothing else from you until you have one.

Read next to this file: `PARITY.md` for what is being built and what it is measured against,
`PLAN.md` for the shape of the terminal, `SERVER.md` §3.1 and §6.2 for how the bundle is served.
This document is the order of work, the acceptance check for each step, and the rules that do not
bend.

---

## What you are building

**The Pro Chart terminal, in a browser, from the same Kotlin that draws it on the phone.** Not a
rewrite in TypeScript — a Compose Multiplatform build of the modules that already exist, so that the
chart engine, the eighty-three indicators, the eighty-five drawing tools and the NamaScript
interpreter are *one* implementation with one set of tests, on every surface the product has.

The guarantee the whole plan rests on is «mobile, tablet and web are one». A second implementation
of an indicator is a second answer to «what is the RSI here», and the day the two disagree is the
day the product stops being trustworthy.

**It ships without an account.** The owner decided (2026-09-21) that the terminal is open and
read-only: no sign-in, no sync, no membership gate. That is the whole of what you are building
toward — chart, tools, scripts, watchlist. The account is a later phase and is not your problem.

---

## The rules that do not bend

1. **`main` only. Never create a branch.** This repository's owner works this way deliberately.
2. **Green after every commit.** Ten gates, then `./gradlew testDebugUnitTest`, then
   `./gradlew :app:assembleRelease` — as two separate invocations, because together they run out of
   memory. The gate list is in `CLAUDE.md`; run all of them, not the five that document names:
   ```
   python3 scripts/quality/check-cross-phase-consistency.py
   python3 scripts/quality/check-checklist-honesty.py
   bash   scripts/security/scan-secrets.sh
   python3 tools/i18n/lint_strings.py
   python3 scripts/release/sync-legal-documents.py --check
   python3 scripts/release/check-update-notes.py
   python3 scripts/release/version.py --check
   bash   scripts/quality/check-kotlin-style.sh
   bash   scripts/quality/check-motion-policy.sh
   bash   scripts/quality/check-haptic-policy.sh
   ```
   Then read the three workflows after the push rather than assuming them.
3. **The Android app must not change behaviour.** Every step of this port is a refactor from the
   phone's point of view. If a proof frame moves by a pixel, you have done something wrong, and the
   golden screenshot tests are there to tell you.
4. **One file at a time out of `androidMain` into `commonMain`, with the build green after each.**
   This is how `:chart-core` reached the browser without a single red commit and it is not
   negotiable. A big-bang move of fifteen thousand lines cannot be bisected when it fails.
5. **Never invent provenance.** If the browser cannot do something the phone does, the browser says
   so or does without; it does not fake it. The same rule the relay lives by.
6. **Persian is the default language and the house orthography is enforced** — «به‌روز» not «بروز»,
   «نسخه‌ی» not «نسخهٔ», Latin digits for market figures and Persian digits for prose counts.
   `tools/i18n/lint_strings.py` is the judge. `values/` is English, `values-fa/` is Persian.
7. **One typeface: IRANYekanX.** No Inter, no Vazirmatn, no font service, no `fonts.googleapis.com`.
   The consistency gate fails the build on any other font file.
8. **Do not add `Cross-Origin-Opener-Policy` or `Cross-Origin-Embedder-Policy`** to anything, and do
   not ask the server to. They exist for `SharedArrayBuffer`, `SharedArrayBuffer` exists for
   shared-memory threads, and Kotlin/Wasm has no threading model. `COEP: require-corp` breaks every
   cross-origin resource that has not opted in — guessing wrong that way is a blank page, while
   guessing wrong the other way is a bundle that refuses to start and says why in the console.
9. **Commit messages end with the two trailers** the repository uses; copy them from `git log`.
   Never put a model name in a commit message, a code comment or any other pushed artefact.

---

## What is already true, so you do not re-derive it

All of this is measured and in `PARITY.md` §3a. Trust it, and re-measure only if something
contradicts it.

| | |
| --- | --- |
| `:chart-core` and `:namascript` | compile to **WebAssembly** already; `:chart-core:compileKotlinWasmJs :namascript:compileKotlinWasmJs` runs in `android-ci.yml` on every push |
| the platform seam | five functions in `ChartPlatform.kt`, with a `wasmJsMain` `actual` that uses **no `Intl`** — `toFixed` and a pattern printer over `CivilDate` give the same bytes `Locale.US` gives on the phone. Copy that approach, do not replace it |
| `:chart-ui` | **already a KMP module** (2026-09-21), one target, sources in `androidMain`, tests in `androidHostTest`, with a `testDebugUnitTest` alias so the gate keeps its name |
| what is Android in `:chart-ui` | **22 imports in 15,242 lines.** Seven are `LocalDensity` / `LocalLayoutDirection`, which Compose Multiplatform has under the same names — they cost nothing. Five are `painterResource` / `stringResource`. Seven are **two files**: `ChartFrameRate.kt` (69 lines) and `ChartStrokePredictor.kt` (59). One is `LocalConfiguration`, one is `BitmapFactory` |
| what is *not* Android in it | `CoineProChart` (7,429 lines), `DrawingRenderer` (2,576), `ChartLegendOverlay` (1,357), all eighteen series types — they draw on `androidx.compose.ui.graphics.Canvas`, which is Compose's own API and identical in a browser |
| the design system | 55 files, 12,308 lines, ~60 platform imports. The share card (`android.graphics` + `StaticLayout`) is the one surface that genuinely needs a second implementation |
| the drawables | **1,150 vector XML files**; 226 use `aapt:attr` inline gradients, 218 use `fillType`, 11 use `<group>` |
| the relay | live. `/api/crypto/prices`, `/api/fx/prices`, candles, news, `/api/fx/showcase`, and `wss://pro-chart.com/api/stream` (forex only — crypto has no public socket and the welcome frame says so) |

---

## W1½ — the drawables *(start here; it is independent of everything else)*

**Why first:** it is the only item with an unknown in it, it blocks W1b, and it can be finished
while nothing else is in flight.

1. **Decide the target format and write the decision down before converting anything.** Compose
   Multiplatform reads Android `<vector>` XML, so 924 of the 1,150 move by copying. The 226 with
   `aapt:attr` do not: that is a resource-linker feature, not a vector one. Your options are to
   convert those 226 to SVG (which CMP also reads, on every target including Android), or to
   convert *all* 1,150 to SVG for one format everywhere. **Recommend one, with the reason, and say
   what it costs the phone** — an APK size delta measured, not guessed.
2. **Write the converter as a script in `scripts/`**, not by hand and not one-off. It must be
   idempotent and re-runnable, because the icon set will grow.
3. **Move to `composeResources/drawable/`** and switch `R.drawable.x` to the generated accessor.
   `ChartIcons.kt` is the mapping and is where this lands.
4. **Prove it visually.** The golden screenshot tests already render the tool rail, the indicator
   picker and the watchlist. If those frames are byte-identical before and after, the conversion is
   sound; if they are not, the diff tells you which icon broke.

**Acceptance:** every gate green, the golden frames unchanged, the APK's size delta reported, and a
`scripts/` entry that can be run again.

---

## W1b — the browser target on `:chart-ui`

1. **Add Compose Multiplatform and the `wasmJs` target.** The module is already KMP; this is a
   target and a source set, not a restructure. Expect the Compose *compiler* plugin the repo
   already applies to keep working and the Compose *runtime* artifacts to come from the JetBrains
   multiplatform coordinates in `commonMain`.
2. **Put the two Android files behind a seam.** `ChartFrameRate` and `ChartStrokePredictor` are
   `expect`/`actual`, and the browser `actual` **does nothing** — there is no `setFrameRate` in a
   browser and the first version draws ink without prediction. Both call sites take a `View`, so
   the seam has to hide that type too: give it an interface the composable holds, not a `View`.
   Follow `DeepNesting.kt` in `:namascript` for the shape, including being honest in the KDoc about
   what the web half cannot do.
3. **Then move files from `androidMain` to `commonMain`, one at a time, build green after each.**
   Start with the ones that have zero platform imports — `ChartSeriesTypes.kt`, `ChartStaticLayer.kt`,
   `ChartTextCache.kt`, `ChartFling.kt`, `ChartFrame.kt`, `ChartOwnedDrag.kt` — and leave
   `CoineProChart.kt` for last, because it is the one that will find everything you missed.
4. **The five resource calls** become the `org.jetbrains.compose.resources` equivalents.
5. **`:core:designsystem` will have to follow.** Do it the same way and in the same order: KMP
   module first with one target, then the browser target, then files move down. Its share card
   stays Android-only behind an `expect`.
6. **Add `:chart-ui:compileKotlinWasmJs` to `android-ci.yml`** beside the two that are already
   there, so the browser cannot rot.

**Acceptance:** `:chart-ui:compileKotlinWasmJs` green in CI; the Android suite and
`:app:assembleRelease` unchanged and green; the golden frames unchanged.

**Ends with:** a page that renders `CoineProChart` in a browser on real candles fetched from
`pro-chart.com/api/…`. Screenshot it. That frame is the milestone, not the compile.

---

## W2 — the terminal shell

The tablet layouts **are** the web layouts — that is why the tablet work was done before the web
was started, and a browser window is an Expanded window by the app's own rule (it decides from the
width it is given, never from «is this a phone»).

Port, in this order, each one shippable on its own:

1. `ChartWorkbench` — the chart with its tools column and readings panel.
2. The labelled rail and the tool rail (`ToolRail.kt`).
3. The 1–8 layout grid (`ChartLayoutPreset`).
4. The object tree and the legend overlay.
5. The watchlist as the list of a list-detail.
6. The screener.

**Mount at `/terminal/`** and route inside it. `SERVER.md` §3.1: that prefix is the *only* one with
an SPA fallback, so `/legal/*`, `/api/*`, `/s/<id>` and `/reset` are never swallowed. A refresh on
`/terminal/BTCUSDT/4h` must render the terminal.

**The terminal's half of the rate limit is not optional** (`SERVER.md` §6): poll
`/api/crypto/prices` every 2 s **and stop when `document.visibilityState` is `hidden`**. A
background tab has no reader and is spending a real one's budget. Send a stable random
`X-Client-Id` per browser — the buckets are keyed on `(address, client id)` and Iranian CGNAT puts a
great many readers behind one address.

**Crypto does not tick.** There is no public crypto socket; the relay's welcome frame says which
venue is live and why. Show that, rather than a chart that silently never moves — the difference
between «there is no crypto feed» and a dead chart is the whole point of that frame.

---

## W3 — the script studio

`:namascript` compiles to WebAssembly already, so the language, the 351 conformance scripts, the
Pine translator, the repair table and the 61 shipped strategies come free. What you are porting is
the screen: `ScriptScreen`, `ScriptPasteBody`, `ScriptPromptBody`, `MinePanel`.

**The one piece with real platform behaviour is the editor's text field** — selection, IME, the
soft keyboard. A browser's `<textarea>` behaves differently enough that you should expect to spend
your time there and nowhere else. Diagnostics carry a line and a column; make the caret land on
them.

---

## Publishing

The server is ready and was proved against a synthetic bundle:

* `*.wasm` → `Content-Type: application/wasm`, so the browser streams the compile.
* `*.wasm`, `*.js` → `Content-Encoding: br`, **pre-compressed on disk** at quality 11 by
  `bin/precompress.sh <dir>` on the server. Compressing ten megabytes per reader is a CPU bill, not
  a cache.
* A content hash in the filename (`[.-]<8+ hex>.<ext>`) → `max-age=31536000, immutable`; `*.html` →
  `no-cache`.

The rules key on **extension and filename, not path**, so where the bundler puts things does not
matter. Hand the built directory to the server agent; its whole job is
`bin/precompress.sh site/terminal`.

---

## What to report, and how

Follow the habit that produced every fact in this brief:

* **Measure, do not assume.** «The snapshot returns 19» beat «the backend team says it returns 19»
  every time in this project's history, and once a wrong parser said a route was empty — so
  **measure a second way before believing an alarming answer.**
* **When a document and the code disagree, the code wins and the document gets fixed** in the same
  commit, with both readings and their dates kept. `SERVER.md` §4.10 and §4.10.1 are the pattern.
* **Say what you did not do and why.** A gap named is a gap somebody can close; a gap hidden is a
  week somebody else loses.
* **Never claim a check you did not run.** `docs/runs/*/CHECKLIST.md` has a gate
  (`check-checklist-honesty.py`) that fails the build on a ✅ with no evidence behind it, and it is
  there because it caught real ones.

Open a run directory under `docs/runs/` and keep CHECKLIST, REPORT, BLOCKED and RESUME as the other
runs do. **Stop only when the whole list is done or something is genuinely blocked on the owner —
and if you find yourself about to ask «shall I continue», that is the thing this brief exists to
prevent.**

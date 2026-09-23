# RUN WEB — report

The web version, from the chart alone (W1b, 5.8.x) to the whole app (5.9.0). What was done, what
was measured, and what was chosen.

---

## 1. One app, compiled twice

The plan in `docs/web/PLAN.md` was a port: screen by screen, W2 then W3, each rewritten against
Compose Multiplatform. The owner's ask was stricter: the site should have **exactly** what the
Android app has. A port cannot promise that. Every phone release would start a second copy
drifting away.

So the browser build compiles **the phone's own sources**. `web/build.gradle.kts` names them:
`app`, all 37 `core/*` modules, every `feature/*` module and `chart/ui`. `:web:shareSources` copies
them into `web/build/generated/shared` and makes only mechanical changes
(`web/tools/share_sources.py`):

* **Wire classes.** Gson reads by reflection, and a browser has none. Every class the phone decodes
  (Retrofit return and body types, `fromJson` targets, Room entities, anything with
  `@SerializedName`) and everything they contain gets `@Serializable`. `@SerializedName` becomes
  `@SerialName` plus `@JsonNames` for the Kotlin name, so both spellings decode as they did.
  141 classes on this tree.
* **Retrofit.** Each interface gets a `<Name>Web` class that builds the same request from the same
  annotations and sends it with `fetch`. 31 services.
* **Hilt.** `WebGraph.kt` is the graph Hilt would build, generated from `AppModule`'s `@Provides`
  functions and the `@Inject` constructors, with the same qualifiers.
* **A handful of rewrites that have no browser meaning**: `runBlocking` (runs straight through when
  the block does not wait), `Class.simpleName`, `decorFitsSystemWindows`.

Under the copied sources, `web/src/shims/kotlin` gives each Android, AndroidX, OkHttp, Retrofit,
Room, DataStore, WorkManager and JVM API they call its browser meaning, under the same name.
Thirteen files are replaced outright (`CHECKLIST.md`, first table). Everything else on screen is the
phone's code.

**What this buys.** A screen added to the phone next month is on the site at the next build, with
no web work. That is the only way «exactly what Android has» stays true.

## 2. The phone did not change

No file under `app/`, `core/`, `feature/` or `chart/` changed. The build changes are additive:
`:web` alone applies the serialization plugin, and the Kotlin daemon gets `-Xmx6g`, because
compiling 606 files for Wasm in one module needs it. `./gradlew testDebugUnitTest` (every golden)
and `./gradlew :app:assembleRelease` are green on this tree.

## 3. Where the data comes from

The page's origin is `pro-chart.com`, and the phone calls two other hosts. `WebRoutes.map` sends
each phone URL:

1. to the relay's named route where one fronts exactly that call (prices, candles, news, track
   record, community, membership, FX showcase). These work today;
2. to `/up/tradeyar/…` or `/up/coineprofx/…` otherwise. That is the passthrough `SERVER.md` §4.12
   asks for, with the one thing it must add: the bearer token stays on the server, behind an
   `HttpOnly` cookie;
3. pictures from any other host go to `/api/img?url=` (`SERVER.md` §4.13), because Compose draws
   from bytes and a page may not read another site's bytes.

## 4. What was found in the browser

Every screen was driven in Chromium at phone and desktop sizes, in Persian and English, dark and
light (`CHECKLIST.md`). Three faults were the page's own, and all three are fixed:

* **Icons that never drew.** `Drawables.ensure` ran inside the `LaunchedEffect` of whichever
  composable asked first. The watchlist toolbar is composed once while the list settles and then
  again; the first composition's effect was cancelled mid-fetch, and the name stayed «in flight»
  for the rest of the visit. The fetch is the page's now.
* **Marks the typeface lacks.** The Kotlin sources write some marks as escapes (`"○"`), which
  the glyph map, written for literal characters, did not see. It maps both forms now, and the gate
  reads every string literal in the shared sources, not only the string tables.
* **News photos.** §3 item 3.

Two things that looked like faults are the phone's behaviour, so the web keeps them: the community
feed is empty because the server's feed is empty, and the screener lists symbols without a logo
because `SymbolArtwork.ARTWORK_GATES_LISTING` has been `false` since run ΤΦΥ.

## 5. What a browser cannot be

Home-screen widgets, picture-in-picture, Play Integrity, background work while the tab is closed,
and opening the system's notification settings. The page does each one's nearest honest thing and
says so (`CHECKLIST.md`, «What a browser cannot do»). Push while closed is possible and needs Web
Push keys (`BLOCKED.md` B5).

## 6. The bundle

`./gradlew :web:terminalBundle` → `web/build/terminal/`: `terminal.wasm` 15 MB and Skia's
`skiko.wasm` 8 MB, both before compression; the phone's 1,156 drawables as their own XML/WebP
files, fetched as they are first drawn; the fa/en string tables; the help and legal assets; the four
IRANYekanX weights. No webpack, for the reason in the W1b section of this report's history: the
compiler's ES modules are served as they are.

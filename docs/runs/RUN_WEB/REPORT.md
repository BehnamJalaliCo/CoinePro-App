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

## 7. An app to install, and the relay the server runs (5.10.0)

**Installable.** The old Pro-Chart web app (`BehnamJalaliCo/Pro-Chart`, `frontend/prochart`) had
learnt one thing the hard way: its first service worker cached the bundle and readers got stuck on an
old release, so its second one cached nothing. This one keeps both lessons. `sw.js` asks the network
for every bundle file, every time, and keeps the last good copy only for when the network does not
answer. `manifest.webmanifest` carries the phone's launcher icon, stacked from its two adaptive
layers by `scripts/design/build-web-icons.py`. Chromium's own checks on the local page:

```
installability errors: [{"errorId":"in-incognito","errorArguments":[]}]   ← the test browser's mode, nothing else
manifest url: http://127.0.0.1:8765/terminal/manifest.webmanifest errors: []
service worker: {"scope":"http://127.0.0.1:8765/terminal/","active":true}
offline title: Pro Chart                                                  ← reloaded with the network off
```

**The relay.** `SERVER.md` §4.12 and §4.13 were written as asks, and the relay behind
`pro-chart.com` lives on its server, not in any repository. So the two routes are now written and
tested here, in `web/relay/relay.py` (aiohttp, one file), for the server to run as it is:

* `/up/tradeyar/…`, `/up/coineprofx/…`: every method, and WebSocket upgrades. Only the headers the
  phone sends go through, and never the reader's cookies.
* **Token swap.** A sign-in or refresh answer's tokens become `pch_…` handles, and the real ones stay
  in the relay against an `HttpOnly; SameSite=Strict` cookie. A handle from another browser is
  dropped, not forwarded. A browser cannot put an `Authorization` header on a WebSocket, so an upgrade
  gets the session's bearer from the cookie. That is the one thing the page could not have done alone.
* `/api/img`: `https://` only, image types only, 5 MB, up to three redirects. Private, loopback and
  link-local addresses are refused both as literals and after resolution, and the address checked is
  the address connected to.

Thirteen tests (`web/relay/test_relay.py`) drive it against a fake of each backend and a fake
publisher, and CI runs them. Locally the page ran behind it: the local server forwarded `/up/*` and
`/api/img` to the relay as the Caddy lines in `web/relay/README.md` will, and the news cards drew
their photos through it: six of six. The first run gave two `415`s — `cryptoslate.com` serves its WebP
with no `Content-Type` at all — so a photo with no type, or a generic one, is now judged by its first
bytes, and a body that is not an image is still refused.

## 8. The server's review, and what changed because of it (5.10.1)

The server agent ran `web/relay` on `pro-chart.com` on 2026-09-24. Its checks: the tests green inside
the image, `/relay/health` ok, `/up/tradeyar/api/mobile/v1/auth/methods` 200, a public photo through
`/api/img` 200 and `127.0.0.1` refused with 400. Every existing route was unchanged, and the relay
used 26 MiB. It left the bundle undeployed because a CI artefact needs a GitHub login, and it asked
three questions.

* **Rate limits at the edge for `/up/*`?** No, and the relay's own limits were tightened instead.
  They keyed on address and `X-Client-Id`, and the client id is the reader's to choose. So
  sign-in routes now also count per address alone (30 a minute, `RELAY_AUTH_ADDRESS_REQUESTS_PER_MINUTE`),
  and a test rotates the id to prove it does not escape. A second bucket in Caddy would refuse what
  the relay allowed, where no one can say why.
* **The backends over the public internet, not the private network?** Now optional:
  `RELAY_TRADEYAR_CONNECT` / `RELAY_COINEPROFX_CONNECT` name the private address to connect to, while
  TLS is still verified against the public name (SNI) and `Host` still carries it. That is the
  `upstream.py` approach without its `verify=False`: nothing is turned off. Two tests.
* **Cache rules for a bundle with no hashes.** The concern was right. `terminal.wasm` has no hash in
  its name, and a catch-all `.wasm → immutable` rule would pin readers to the first release forever.
  `web/relay/README.md` now says so, with the Caddy lines: `/terminal/*` gets `no-cache` and is
  excluded from the immutable matchers.

**The bundle without a login.** CI now publishes it on every push to main as the pre-release
`web-latest` (`pro-chart-terminal.zip`, its `.sha256`, and a `BUILD.txt` inside naming the commit).
It is never marked «latest», so the site's link to the newest APK is unaffected.

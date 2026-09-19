# The Pro Chart server — what to provision, and what it serves

Companion to `PLAN.md`, which fixes the *shape* of the web terminal. This fixes the **server**: what
to buy, what runs on it, which upstream routes it relays, what it stores, and how to tell when it is
working. Written so the owner can hand it to whoever provisions the machine and get back something
this app and the browser terminal can both talk to on the first day.

Nothing here is built yet. What is written down is the contract, so that the work on the machine is
configuration rather than design. **`SERVER_BUILD_PROMPT.md`, beside this file, is the order of work
and the acceptance check for each step** — that is what goes to the agent doing the building; this
is what it reads for the detail.

**The three machines share a Hetzner private network** (the owner's, confirmed when the server was
provisioned). Upstream calls go over the private address rather than out to the public internet and
back, which is faster, cheaper, and — the part that matters most — means neither backend needs a
CORS header, a firewall change or any other modification to serve the web.

---

## 1. What the server is for, in one paragraph

Two backends already serve this product's data — **TradeYar** (crypto, over LBank) and
**CoinePro-FX** (gold and the dollar, over Finnhub). The phone talks to both directly, and that is
fine for a phone. A browser cannot: two origins mean two CORS negotiations on servers this project
does not own, two cookies, two sessions and two rate limits, and a thousand open tabs mean a
thousand upstream sockets where the phone opened one. So the Pro Chart server is **one origin in
front of two backends**: it relays what the terminal needs, holds one upstream socket per venue and
fans it out, keeps the little state that is genuinely the web's own, and serves the static bundle
and the legal pages.

It is a relay and a cache. It is **not** a second source of truth: it must never compute a price, a
candle or a signal of its own. Where an upstream is down, it says so and serves the last good
answer with its age — the same rule the app's own offline copy follows.

---

## 2. The machine

Sized for the first year, not the fifth. This is a relay with a cache, not an analytics cluster.

| | first deployment | when to grow |
| --- | --- | --- |
| vCPU | 4 | sustained 60 % across a week |
| RAM | 8 GB | the candle cache alone passing 4 GB |
| disk | 80 GB SSD | the Postgres volume passing 40 GB |
| bandwidth | 5 TB/month | the Wasm bundle is 5–15 MB; a CDN in front makes this a rounding error |
| location | Europe (Hetzner Nuremberg or Falkenstein) | — |
| OS | Ubuntu 24.04 LTS | — |

**One machine is right to start.** The two things that would force a second are the WebSocket
fan-out (memory per connection) and the static bundle (bandwidth), and both are answered more
cheaply by a CDN in front than by a second box behind.

**Hetzner, for the same reason CoinePro-FX is there**: the owner already runs one, the billing and
the access are known, and the three servers being neighbours on one private network makes every
upstream hop a local one. That is not only speed — it is the reason neither backend has to be
modified at all to serve the web.

### What runs on it

Everything in Docker Compose, as CoinePro-FX is:

| service | image | what it does |
| --- | --- | --- |
| `edge` | Caddy 2 | TLS, HTTP/3, `Content-Encoding: br`, static bundle, reverse proxy to `api` |
| `api` | the relay (Node or FastAPI — see §6) | `/api/*`, the WebSocket, the sync documents |
| `db` | Postgres 16 | accounts' workspace documents, saved layouts, watchlists, drawings |
| `cache` | Redis 7 | the quote and candle cache, the fan-out's subscriber registry, rate limits |

No Kubernetes, no message broker, no separate worker for the first deployment. Each of those is a
thing to operate, and this server's whole job is to stay up and be boring.

---

## 3. The names

| name | points at | why |
| --- | --- | --- |
| `pro-chart.com` | the edge | the terminal, the legal pages, `/.well-known/assetlinks.json` |
| `www.pro-chart.com` | 301 to the apex | one canonical origin |
| `api.pro-chart.com` | the edge, same machine | **optional and deliberately not used at first.** A separate API host reintroduces the CORS negotiation this server exists to remove. `pro-chart.com/api/` is the address |

**Certificate pinning does not apply to this host**, and that is not an oversight —
`docs/security/PINNING.md` has the rule: a host behind a CDN rotates leaf certificates on a schedule
nobody controls, and a pin set against one is a pin set against an outage. The two backends keep
their pins because the app reaches them directly.

---

## 4. What it relays, route by route

The rule for every row: **the browser asks `pro-chart.com`, the server asks the backend, and the
body comes back unchanged.** «The backend» means its **private address on the Hetzner network** —
the three machines share one, so the hop never leaves it and neither backend is modified to serve
the web. No reshaping. A relay that rewrites a payload is a second contract to
keep in step with the first, and the app has one contract per backend already
(`docs/backend/MARKET_DATA_CONTRACT.md`, `docs/AUTH_CONTRACT.md`).

### 4.0 Each backend has **two** surfaces, and Phase 2 uses the public one

This section is new, and the first three tables below were wrong without it. **Correcting a spec
that the server disagreed with is the spec's job, not the server's** — the routes were written from
what the Android app calls, and the Android app signs in.

Every route the app uses under `api/mobile/v1/…` (TradeYar) or `user/…` (CoinePro-FX) is the
**authenticated** surface. It answers `401 TYR-004 Auth Token Missing` to anybody without a bearer
token, which is every request a Phase-2 relay makes, because Phase 2 has no account by definition.
That is not a fault to work around; it is the two surfaces doing their jobs.

Beside it each backend has a **public** surface the app already knows:

* **TradeYar** — `api/v1/public/…`. No auth at all. `:core:guest`'s `GuestApi` is the app's own
  client for it, and `GuestMarketCatalogGateway` / `GuestCandleGateway` in `GuestMarketGateways.kt`
  adapt it to the **same two interfaces** the signed-in chart uses. **The web's Phase 2 is the guest
  tier**, and the guest tier is specified, shipped and proved on the phone. Build the relay against
  the same routes and the browser gets the same markets a guest gets, which is the intended answer
  rather than a compromise.
* **CoinePro-FX** — `api/public/…`. Two routes, and the app calls **neither** today (it signs in and
  uses `ws/snapshot` plus the academy chart). They are the only FX data a Phase-2 relay can reach.

So Phase 2 does **not** wait on `PLAN.md` §6.2. That question gates the **account** — §4.4 and the
sync documents — and nothing in §4.1 or the public part of §4.3 needs it answered.

### 4.1 Market data — the whole of the chart

**Public surface — Phase 2, no account.** Every row below was fetched on 2026-09-18; §4.7 records
what came back.

| `pro-chart.com` | upstream | upstream route | auth | cache |
| --- | --- | --- | --- | --- |
| `GET /api/crypto/prices` | TradeYar | `api/v1/public/prices?symbols=` | none | 2 s |
| `GET /api/crypto/candles?symbol=&tf=&limit=` | TradeYar | `api/v1/public/candles/{symbol}?tf=&limit=` | none | see below |
| `GET /api/fx/prices` | CoinePro-FX | `api/public/prices/live` | none | 5 s |
| `GET /api/fx/candles?symbol=&timeframe=&limit=` | CoinePro-FX | `api/public/prices/series?symbol=&timeframe=&limit=` | none | see below |

**Authenticated surface — not before Phase 4, and only once `PLAN.md` §6.2 is answered.** These are
the routes the Android app uses. They are listed so nobody has to rediscover them, and they are
**not to be relayed yet**.

| `pro-chart.com` | upstream | upstream route | auth |
| --- | --- | --- | --- |
| `GET /api/crypto/snapshot` | TradeYar | `api/mobile/v1/ws/snapshot` | bearer |
| `GET /api/crypto/candles` (full history) | TradeYar | `api/mobile/v1/market/candles` | bearer |
| `GET /api/fx/snapshot` | CoinePro-FX | `api/ws/snapshot` | none, **but see §4.7** |
| `GET /api/fx/candles` (M5, paging) | CoinePro-FX | `academy/chart/{symbol}` | academy token, minted from the mobile one |

**The bare snapshot is TradeYar's discovery mechanism, and only TradeYar's.** A call with no
`symbols` returns everything that venue quotes — 857 on the public route, measured — and the relay
must keep asking it bare; naming a list would cap the web's universe at whatever the relay's author
happened to know. **On CoinePro-FX the same call is not discovery**: it answers 17 symbols and omits
gold and silver. Use `api/public/prices/live`, which is the full 19. §4.7 has the measurement and
`RUN_ALEF/BLOCKED.md §א20` is the question to that backend's team.

**Candles cache by whether the bar is closed.** A closed bar never changes, so it is cached
indefinitely and keyed `venue:symbol:interval:openTime`; the newest bar is cached for one interval
tick at most (2 s at a minute, 30 s at an hour). This is the one place the relay earns its keep: a
hundred tabs on BTCUSDT H1 become one upstream call an hour plus one live bar.

**Two things about the FX candle route that the terminal has to know**, because they are not
choices the relay can hide:

* **Four timeframes, not eight.** `M15`, `H1`, `H4`, `D1` answer 200; `M5`, `M30` and `W1` answer
  `422`. Measured, all seven. The app's own `ACADEMY_NATIVE_TIMEFRAMES` lists five — the same four
  plus `M5` — so **the public route is the academy route minus the five-minute bar**. A weekly and a
  half-hourly bar are folded on the client out of `D1` and `M15`, exactly as `CandleGateway` folds
  them on the phone. Nothing finer than fifteen minutes can be drawn on forex from the web.
* **The shape is not the app's candle shape.** `t` is an ISO-8601 **string**, there is no volume, no
  `has_more` and no `before`, and `limit` is bounded `20..400` (`limit=3` is a `422`, with the bound
  in the body). Rule 2 still holds — **the relay passes it through unchanged** — so the adapting
  happens in the terminal, in the same place `GuestCandleGateway` does it on the phone. A relay that
  reshaped this would be a second candle contract to keep in step with the first.

### 4.2 The socket

One upstream connection per venue, held by the relay for as long as it is up; browsers subscribe by
symbol and the relay fans out. The contract the browser sees is the app's own — **snapshot, then
stream** — because that is what `MARKET_DATA_CONTRACT.md` specifies and what the chart engine
expects.

```
wss://pro-chart.com/api/stream
  → {"subscribe": ["BTCUSDT", "XAUUSD"]}
  ← {"venue": "crypto", "symbol": "BTCUSDT", "last": 64182.4, ...}
```

A subscriber count of zero on a symbol does **not** unsubscribe upstream: both venues send
everything on one socket anyway, and a relay that renegotiated its upstream on every tab close would
spend its life renegotiating.

### 4.3 Signals, news, the calendar

**Most of this section is Phase 4, and the table said otherwise.** Signals are the product's paid
surface: `public/signals/active` and `public/signals/recent` sit under a path called *public* on
CoinePro-FX and are still behind VIP — `EndpointCatalog` in `:core:diagnostics` says so in as many
words, and the live server answers 401. The same is true of the calendar and both
market-intelligence routes. A Phase-2 relay reaches none of them.

**What Phase 2 can carry**, because it needs no account:

| `pro-chart.com` | upstream | upstream route | cache |
| --- | --- | --- | --- |
| `GET /api/news` | TradeYar | `api/v1/news/list?type=news&limit=` | 60 s |
| `GET /api/track-record` | TradeYar | `api/demo/signals?limit=` | 300 s |
| `GET /api/community` | TradeYar | `api/v1/public/community` | 60 s |
| `GET /api/membership` | TradeYar | `api/v1/public/membership` | 300 s |
| `GET /api/fx/showcase` | CoinePro-FX | `public/signals/showcase` | 60 s |

`api/demo/signals` is named badly and the app's own `GuestApi` says why it is worth having: **every
row is a real published signal that has already closed**, with the outcome it actually banked. It is
a track record, not a demonstration, and it is the only signal content this product will show
somebody who has not signed in.

**Phase 4, once `PLAN.md` §6.2 is answered** — listed so they are not rediscovered, not to be
relayed yet:

| `pro-chart.com` | upstream | upstream route | auth |
| --- | --- | --- | --- |
| `GET /api/signals?market=crypto` | TradeYar | `api/mobile/v1/signals?status=` | bearer |
| `GET /api/signals?market=forex` | CoinePro-FX | `public/signals/active`, `public/signals/recent` | bearer (VIP) |
| `GET /api/signals/{id}` | either | `api/mobile/v1/signals/{id}`, `public/signals/detail/{id}` | bearer |
| `GET /api/calendar` | CoinePro-FX | `user/economic-calendar` | bearer |
| `GET /api/announcements` | TradeYar | `api/mobile/v1/announcements` | bearer |
| `GET /api/market-intelligence` | either | `api/mobile/v1/market-intelligence`, `user/mobile/market-intelligence` | bearer |

**The showcase route exists** — `public/signals/showcase`, read off `/api/openapi.json` as this
paragraph used to instruct rather than taken on anybody's word, and confirmed to answer without a
token while `active` and `recent` beside it do not. It is in the Phase-2 table above.

Today it answers `{"signal": null, "live": null}`, and that is **empty rather than broken**: the
neighbouring `public/signals/stats` reports `total_signals: 0`. A relay must not turn a null into an
error, and a terminal must not draw a card for it.

`public/signals/stats` is public too and is **not** relayed, because §4.3 did not name it and the
server was right not to invent a route. It is worth a line here anyway for something it says in
passing: **`symbols_covered: 19`**. The desk's own count of the forex universe is nineteen — the
same nineteen `public/prices/live` returns and two more than the bare snapshot gives the phone. See
§4.7 and `RUN_ALEF/BLOCKED.md §א20`.

**`market=forex` is the gold call** — `ForexSignalScope` in `:core:signals` is the rule and the
terminal applies the same one. The relay does not filter; a client that narrows and a relay that
narrows would be two places to change the day the desk publishes something else.

### 4.4 The account

| `pro-chart.com` | upstream | note |
| --- | --- | --- |
| `POST /api/auth/*` | whichever backend owns the account | **`PLAN.md` §6.2 is still open.** Until the owner answers, the relay proxies auth to CoinePro-FX, which is where `RESET_HOST` already points |
| `POST /api/auth/guest` | TradeYar | `user/auth/guest` — the read-only tier |
| `GET /api/membership` | TradeYar | `api/v1/public/membership`, `api/mobile/v1/membership/status` |
| `GET,POST /api/community/*` | TradeYar | `api/v1/public/app-community/*` |
| `GET /api/academy/*` | CoinePro-FX | the academy routes, behind `user/academy-token` |

**The browser never holds a bearer token in `localStorage`.** The relay exchanges the upstream token
for an `HttpOnly; Secure; SameSite=Lax` session cookie and keeps the bearer server-side. That is the
one behaviour where the web deliberately differs from the phone, and the reason is that a browser
has an XSS surface a phone does not.

**There is no broker account, and no route for one.** Copy trading was removed from the product
(run Ψ); `user/account/link`, `DELETE user/account` and `user/copy-status` are **not** relayed, and
adding them later is a product decision rather than a configuration one.

### 4.5 What the server owns itself

Three documents per account, and nothing else. Each is a JSON blob with a version number,
last-writer-wins, exactly as the watchlist sync already works on the phone
(`WatchlistSyncController`):

| `pro-chart.com` | what | already on the phone |
| --- | --- | --- |
| `GET,PUT /api/sync/watchlists` | the reader's lists | `WatchlistSyncController` — this route exists upstream today |
| `GET,PUT /api/sync/layouts` | saved chart layouts and workspaces | `ChartLayoutStore`, `ChartWorkspaceStore` — **needs the pair building** |
| `GET,PUT /api/sync/drawings` | drawn objects per symbol | `DrawingSyncStore` — **needs the pair building** |

These are the only tables. Everything else the server holds is a cache and may be thrown away at any
moment without the product noticing.

### 4.6 The update document — the piece of the app store this server has to be

**This is new, and it exists because the product has no app store.** Google Play does not serve
Iran; the Android app is downloaded as an APK and installed by hand (`docs/release/DISTRIBUTION.md`
is the whole picture). That works, and it is missing exactly one thing every other app gets free:
any way for a reader to learn that the build in their hand is six months old.

| `pro-chart.com` | what | who reads it |
| --- | --- | --- |
| `GET /api/app/latest` | the newest published Android build | the app's safety screen, once per visit |
| `GET /download/pro-chart-X.Y.Z.apk` | the file itself | a browser, when the reader taps the button |

The document is **static JSON written by the release process**. There is no code behind it, nothing
computes it, and it is the one route on this server that is not a relay — nothing upstream knows or
should know what the Android release is.

```json
{
  "version_code": 50000000,
  "version_name": "5.0.0",
  "url": "https://pro-chart.com/download/pro-chart-5.0.0.apk",
  "sha256": "…64 hex characters…",
  "notes_fa": "…",
  "notes_en": "…",
  "mandatory": false
}
```

Four things the app enforces, so the server has to get them right or its release is simply not
offered to anybody (`AppUpdate.publishable`):

* **`version_code` is the only field compared.** It is the integer Android itself orders installs
  by, and `scripts/release/version.py` derives it from the name. A name that reads newer over a
  code that is not changes nothing.
* **`url` must be HTTPS and on `pro-chart.com`, `www.pro-chart.com` or `github.com`.** An allow-list
  rather than any address, because this is the one response in the product that ends as an
  installable package rather than as text on a screen.
* **`sha256` is required**, and the app shows it to the reader before they download. An APK offered
  with no way to check it is one the app declines to offer.
* **`mandatory` changes a sentence and nothing else.** It must never be understood as a switch that
  can stop an installed app from working; the app does not implement one and will not.

Cache it for a few minutes at the edge and no longer. A release that has just gone out is exactly
when somebody is looking.

### 4.7 Measured, not assumed — 2026-09-18

Everything above that says «measured» was fetched from `coineprofx.com` and read back. Recorded
here so the next reader argues with a number rather than with a memory, and so a backend that
changes is caught by a re-run rather than by a broken chart.

| call | answer |
| --- | --- |
| `GET api/ws/snapshot` (bare) | 200, **17 symbols**: AUDJPY AUDUSD DE40 EURAUD EURGBP EURJPY EURUSD GBPJPY GBPUSD NAS100 NZDUSD US30 US500 USDCAD USDCHF USDJPY XTIUSD. **No XAUUSD, no XAGUSD** |
| `GET api/public/prices/live` | 200, **19 symbols** — the same 17 **plus XAUUSD and XAGUSD**, first in the list, with `symbol` (`XAU/USD`), `raw_symbol` (`XAUUSD`), `price`, `change_pct`, `positive`, and an `as_of` |
| `GET api/public/prices/series?symbol=XAUUSD&timeframe=H1&limit=20` | 200 — `symbol`, `display`, `timeframe`, `candles[{t,o,h,l,c}]`, `change_pct`. **`t` is an ISO-8601 string**, no volume, no paging |
| the same with `limit=3` | `422`, and the body names the bound: `limit >= 20` |
| the same across seven timeframes | `M15` `H1` `H4` `D1` → **200**; `M5` `M30` `W1` → **422** |

**The 17-against-19 line is the one that matters beyond this server.** The Android app builds its
forex market list from the bare snapshot and from nothing else — there is no bundled fallback list
— so the metals are missing from the phone's forex catalogue too, today. Run Ψ narrowed the forex
signal list to gold; a reader can now be shown a gold call and find no gold market to open.
`docs/runs/RUN_ALEF/BLOCKED.md §א20` is the ask to CoinePro-FX's team, and it is one line: put
XAUUSD and XAGUSD in `ws/snapshot`'s bare answer, where `prices/live` already has them.

### 4.8 Live — 2026-09-19, measured through Cloudflare

The host answers. Everything below was fetched from outside, as a reader's phone or browser would,
and re-checked here against the artefacts rather than taken from the server's own report.

| call | answer |
| --- | --- |
| `/legal/terms/`, `/legal/privacy/`, `/legal/delete-account/` | `200`, **zero redirects**, `text/html` |
| `/.well-known/assetlinks.json` | `200`, `application/json`, no redirect — and the fingerprint in it **is** the release keystore's, checked against `apksigner`'s own reading of the APK |
| `/api/app/latest` | `200`, `application/json`; the notes are **byte-identical** to `docs/release/UPDATE_NOTES.md` |
| the APK the document names | `200`, and `sha256sum` says it is **byte-identical to the GitHub release** |
| `/api/crypto/prices` | `857` rows |
| `/api/fx/prices` | `19` rows, XAUUSD and XAGUSD first |
| `/api/fx/candles`, seven timeframes | `M15 H1 H4 D1` → 200; `M5 M30 W1` → **422, unchanged from upstream** |
| `/api/health` | both upstreams reachable, cache hit rate reported |
| `www.pro-chart.com` | `301` to the apex |

**The relay does not translate a refusal.** That is the row worth dwelling on: asking for `M5`
returns the backend's own `422` with the backend's own message, rather than a timeframe the relay
picked as near enough. A relay that quietly substituted `M15` would draw a chart of the wrong bars
and nothing anywhere would say so.

### 4.9 Two decisions the relay's author made, and why both stand

**The candle cache expires on the bar's close, not per candle.** §4.1 names the key
`venue:symbol:interval:openTime`, which implies caching each bar and rebuilding the response from
the ones held. The server declined, and its reason is better than the rule: rebuilding a body means
the relay has to write `server_time_ms` itself — and **rule 1 says this server is never the author
of any number.** So the body is cached whole, unmodified, with a TTL that runs to the moment the
newest bar closes, capped at one interval tick. The effect §4.1 asked for is unchanged — a hundred
tabs on BTCUSDT H1 are one upstream call an hour plus one live bar — without the relay acquiring a
second contract. Measured: 657 ms cold, **3.5 ms** warm.

**The health probe is not `/healthz`.** TradeYar's answers `307` to `/login?from=/healthz`, and the
relay does not follow redirects, so the first reading was a false «degraded». The probe is
`api/v1/system/health`, which answers `200` directly. Worth recording because the failure mode is
the dangerous kind: a health check that reports a fault which is not there teaches everybody to
ignore it.

**One thing to change.** `/api/health` prints each upstream's **private address** (`10.10.1.2`,
`10.10.1.3`) to the public internet. Neither is a secret and neither is reachable from outside, but
it is the shape of a network drawn for anybody who asks, and it buys the reader nothing: the fields
that matter are `reachable`, `probe`, `latency_ms` and the cache rate. Drop `address`, or move the
whole route behind the private network and leave a bare `{"status":"ok"}` on the public one.

---

## 5. Where the data actually comes from

The owner's words for this run were «take the data we need from the TradeYar and CoinePro-FX servers
and put it on the Pro Chart server». Two readings, and the difference matters:

**A relay** — the server asks the backend when a browser asks it, caches the answer for a few
seconds, and holds nothing. **A copy** — the server pulls continuously and serves from its own
store, and the backends become upstreams it syncs from rather than servers it fronts.

**This document specifies the relay, and the copy is what §4.1's candle cache quietly becomes.** The
reason is not caution: a copy has to answer «how stale may this be?» for every field, and the honest
answer for a price is «two seconds» and for a closed candle is «for ever». The cache policy in §4.1
*is* that answer written per field, which is what a copy would need anyway, so the relay grows into
a copy exactly where a copy earns something and stays a relay everywhere else.

The one place a real copy is worth building on day one is **candle history**. Both backends serve a
bounded window; a reader who pans a year back on a daily chart walks off the end of it. A nightly
pull into Postgres — one row per `venue, symbol, interval, openTime` — turns that into a server the
terminal can keep panning against, and it is the same shape as `CandleArchive` on the phone.

---

## 6. The relay itself

**FastAPI on Python 3.12**, unless whoever builds it prefers Node. The reason to name one is that
CoinePro-FX is FastAPI already: the owner's team knows the shape, the deployment and the logging, and
a second language on a second server is a second thing to learn at three in the morning.

What it is, in whole: an HTTP client with a Redis cache in front, a WebSocket hub, three tables, and
a cookie. No ORM beyond SQLAlchemy for the three documents. No background workers except the nightly
candle pull and the two upstream sockets.

**Rate limits** at the edge rather than in the app: 60 requests a minute per IP on `/api/*`, 10 a
minute on `/api/auth/*`, and one WebSocket per IP with a 200-symbol subscription ceiling.

---

## 7. Going live — the order, and how to know each step worked

1. **The machine and the names.** `pro-chart.com` resolves; Caddy answers on 443 with a valid
   certificate. *Proof:* `curl -I https://pro-chart.com` returns 200.
2. **The legal pages and `assetlinks.json`.** These are the only things the **phone** needs from
   this server, and they unblock nothing else — but every legal link in the shipping app already
   points at `pro-chart.com/legal/…`, so this is the first thing that stops being a promise.
   *Proof:* `scripts/release/print-assetlinks.sh` output matches what the host serves, and the app's
   own legal links open a page rather than an error.
2½. **The update document** (§4.6). Static JSON and a file; no relay, no cache logic, no account.
   It is listed here rather than folded into step 2 because it is the only step whose absence gets
   *worse* over time — every release that ships without it is another cohort of readers with no way
   to hear about the next one. *Proof:* `curl -s https://pro-chart.com/api/app/latest` parses, and
   its `version_code` matches `python3 scripts/release/version.py --code` for the published tag.
3. **The read-only relay.** §4.1 and §4.3, no account, no socket. *Proof:* `/api/fx/snapshot`
   returns the same body as the backend's own route, and `/api/crypto/candles` returns a second
   request from cache in under 5 ms.
4. **The socket.** §4.2. *Proof:* two browser tabs on one symbol produce one upstream connection.
5. **The account.** §4.4, once `PLAN.md` §6.2 is answered. *Proof:* one sign-in works from the
   browser and the phone against the same credentials.
6. **The sync documents.** §4.5. *Proof:* a watchlist changed on the phone appears in the browser on
   the next load, and back.
7. **The terminal.** `PLAN.md` §2 and §3 — the Wasm build. Everything above is what it needs to
   exist; none of it needs the terminal to exist first, which is why it is last rather than first.

**Steps 1 and 2 are worth doing the day the server exists**, whatever happens to the rest: they cost
an afternoon and they close the one place the shipping app currently points at a host that does not
answer.

---

## 8. What is still the owner's to decide

0. ~~The provider and the machine.~~ **Done** — provisioned, on the same Hetzner private network as
   TradeYar and CoinePro-FX, with an agent on it. `SERVER_BUILD_PROMPT.md` is what it works from.

1. **Which backend owns the account** (`PLAN.md` §6.2, and
   `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md`). Step 5 cannot start without it.
2. **Whether the terminal is open, member-only, or a read-only guest page** (`PLAN.md` §6.3).
3. **Whether the candle archive is built on day one** (§5). It is the difference between a reader
   panning to the edge of the backend's window and panning as far as the product has history.
4. ~~**The App Signing certificate's SHA-256 fingerprint**, from Play Console.~~ **Settled, and the
   answer turned out to be simpler than the question.** Google Play does not serve Iran and this
   app is installed from a downloaded APK (`docs/release/DISTRIBUTION.md`), so there is no Play App
   Signing in the path and no re-signed key to ask a console about: the fingerprint
   `assetlinks.json` needs is the **release keystore's own** SHA-256. Three ways to read it, in
   `DISTRIBUTION.md` §5 — the shortest being the app's own «ایمنی و انتشار» screen, which prints
   the certificate of the running install with a copy button. The owner still has to *hand it over*;
   they no longer have to find it.
5. **Whether the APK is served from this host as well as from GitHub** (§4.6). Nothing breaks if it
   is not — the update document may point at the GitHub release — but a download that comes from
   the same host as the document is one fewer thing a reader has to trust.

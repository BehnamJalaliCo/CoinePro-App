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
**CoinePro-FX** (gold and the dollar — whose live quotes are **not** Finnhub; see §4.10). The phone talks to both directly, and that is
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

### 3.1 The site's shape — which path holds what

One origin serves four different kinds of thing, and they have different lifetimes, different cache
policies and different consequences when one swallows another. So the paths are fixed here rather
than discovered when a bundler is wired up.

| path | what | notes |
| --- | --- | --- |
| `/` | the landing page | **not the terminal.** See below |
| `/terminal/…` | the web terminal — a single-page app | the **only** prefix with an SPA fallback |
| `/legal/terms/`, `/legal/privacy/`, `/legal/delete-account/` | the three documents | server-rendered, `no-cache`. **The trailing slash is canonical** — the app already builds it that way, and the bare form answers `308` to it |
| `/download/…` | the APK | served beside the document that names its digest (§4.6) |
| `/api/…` | the relay | §4 |
| `/s/<id>` | a shared script | **not built yet**, and when it is: server-rendered, not an SPA route — see below. `ScriptLink.of` in `:namascript` already spells this address |
| `/reset` | password reset | **not built yet — build it, with one proxied route.** See below |
| `/.well-known/assetlinks.json` | App Links | `application/json`, no redirect |

**The terminal mounts at `/terminal/`, and the root is a page of its own.** The tempting answer is
`/`, because the domain is the product's name. It is the wrong one here, for two reasons that are
specific to this product rather than matters of taste:

* **The root page has a more urgent job than the chart.** Google Play does not serve this app's
  readers, so a first install is a hand-carried APK — and `docs/release/DISTRIBUTION.md` §4½ says
  the exposed case is precisely the reader who has no copy yet to compare a signature against.
  Their protection is that the **fingerprint is published where they will actually look**, and
  where they look is the address they were given. The root is the download page and the
  fingerprint page. A terminal at `/` buries the one thing a new reader came for.
* **An SPA fallback at `/` is a denylist, and denylists rot.** Rooted there, every path in the table
  above has to be *excluded* from the catch-all, and so does every path added later by somebody who
  does not know the rule. Rooted at `/terminal/` it is an allowlist: the fallback applies inside one
  prefix and nowhere else, and a mistake outside it is an honest `404` rather than a chart where a
  legal document should be.

So: **`/terminal/*` falls back to `/terminal/index.html`; nothing else falls back at all.** A
refresh on `/terminal/BTCUSDT/4h` renders the terminal; a typo at `/legall/terms` is a `404`.

**`/s/<id>` is reserved now and server-rendered when it is built.** Two things to know before
anybody builds it. It is **not** what the app's own sharing uses: the community board refuses URLs
at the door, so a shared script travels as the `.nama` document itself inside the post, and
`ScriptShare`'s KDoc explains why that is the better shape anyway — the reader can read the code
above the button. What the address is for is a link pasted into Telegram, and there the whole job
of the link is the Open Graph card the server puts in the HTML head. An SPA route has nothing in
its head to scrape, so the link would arrive as a bare URL, which is the one thing it must not do.
Server-rendered, then: the script's name and author in the head, and a link into `/terminal/` to
run it. Note also what `ScriptLink` already guarantees — the link carries an **id, never source**,
so nothing runs on a tap.

**`/reset` is claimed by the app and does not exist on the server yet.**
`/.well-known/assetlinks.json` verifies — the fingerprint is right and the file is served the way
Android's verifier needs it — so on a phone with the app installed, the link opens the app and the
reset happens in it (`DeepLinkValidation` returns `PasswordReset(token)`, and the token stays opaque
to the app). **The page is for the reader who does not have the app**: somebody resetting on a
laptop. Today they get a `404`.

**Build it, with one route proxied, rather than leaving the `404`.** The reasoning, because it looks
like it touches Phase 4 and does not:

* §4.4's default is already written: auth proxies to **CoinePro-FX** until §8.1 is answered. A
  reset mail carrying a `pro-chart.com/reset` link would be CoinePro-FX's mail carrying
  CoinePro-FX's token, so forwarding it there is not a guess about who owns the account — it is
  the row above, followed. `POST /api/auth/password/reset` alone, not the rest of `/api/auth/*`.
* **The page must invent nothing**, which is rule 1 again: it reads the token from the query,
  posts it, and shows the backend's own answer. It does not validate the token, does not decide
  what «expired» means, and does not write a message the backend did not send.
* The alternative that looks safer is not: a page that renders a form it cannot submit is **worse
  than a `404`**, because a `404` is unambiguous while a dead form teaches a reader to distrust the
  product at the one moment they are already locked out.
* And leaving the `404` makes the product depend on nobody ever changing a mail template — a
  decision taken in another repository, by people who have no reason to know this page is missing.
  A thing that is correct only while somebody else does not act is not correct.

The mail itself is a separate question and stays as it is until somebody decides it: the page
existing does not mean anything should point at it yet. `docs/release/APP_LINKS.md` carries the
same note from the app's side.

**No `Cross-Origin-Opener-Policy` / `Cross-Origin-Embedder-Policy`**, and this is a decision rather
than an omission. Those two headers are needed only for `SharedArrayBuffer`, `SharedArrayBuffer` is
needed only for shared-memory threads, and **Kotlin/Wasm has no threading model** — no `Thread`, no
shared-memory atomics, and coroutines on `wasmJs` dispatch on the one thread exactly as they do on
`js`. Adding the pair «to be safe» is not safe: `COEP: require-corp` breaks every cross-origin
resource that does not opt in, so the cost of guessing wrong in that direction is a blank page,
while the cost of guessing wrong in the other is a bundle that refuses to start and says so in the
console. **The bundle is the test** — if a future Compose or Skia build ever wants shared memory it
will fail loudly on first load, at which point the headers go in with a reason recorded here.

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

**This section said «one upstream connection per venue» and one of the two venues has no socket to
connect to.** Measured 2026-09-19, seven candidate paths on TradeYar: `/ws/prices` and `/ws` time out
at the handshake, three `…/public/…` shapes answer `404`, `/api/v1/ws/prices` closes `4001
Unauthorized`, and `/api/mobile/v1/ws/prices` — the one the Android app opens — closes `4401
unauthorized`. So the crypto socket is the same wall as §4.0: it is the authenticated surface, and a
relay with no account cannot open **zero** connections to it, not one.

So Phase 3 is **forex-complete and crypto-closed**, and the contract says so in its first frame
rather than leaving a chart to tick silently forever:

```
← {"type":"welcome","venues":{
     "forex":  {"live": true,  "reason": null},
     "crypto": {"live": false, "reason": "upstream requires an account (PLAN.md §6.2, Phase 4)"}}}
```

**The difference between a chart that says «there is no crypto feed» and a chart that simply never
ticks is this one frame.** A terminal must read it and say so; a silent chart is the failure this
product keeps designing out.

### 4.2.1 Crypto on the socket — the decision, and it is «wait»

Two ways were possible and the server was right to ask rather than pick:

1. **Leave it false until Phase 4.** The terminal reads crypto from `GET /api/crypto/prices`, which
   is already relayed and already cached two seconds.
2. **Bridge it.** The relay polls that same public route every two seconds and pushes the
   differences down the socket as ticks.

**It is (1), and the reason is rule 1 rather than effort.** A bridged tick does not compute a price
— the server would relay the values unchanged — but it invents the one thing a tick is *for*: **when
it happened.** The relay can only ever know «my poll two seconds apart differed», so the event would
carry either the poll's own clock (a number this server authored) or the upstream's timestamp on a
frame that arrives up to two seconds late with nothing saying so. Both are this server becoming the
author of something, and the phone's contract — snapshot, then the venue's stream — would quietly
mean two different things on the two platforms.

And the cost of refusing is nil. A tab polling `/api/crypto/prices` every two seconds is the **same
upstream traffic** as the bridge, because the relay's two-second cache collapses it either way: a
hundred tabs are one upstream call per two seconds under both designs. The difference is only
whether the browser is told the truth about what it is receiving.

Two notes for the terminal, since it is the one polling:
* **Back off when the tab is hidden.** `document.visibilityState` — a background tab has no reader.
* Two seconds is 30 requests a minute. **This section is why §6's figure changed**: against the 60
  a minute §6 used to give every route, two tabs from one address exhausted one route before
  anything else took a share. The price routes are now a bucket of their own at 240, because they
  are served from the two-second cache and cost the upstreams nothing — but the point above is not
  optional at any ceiling, because a hidden tab is spending a real reader's budget for nobody.

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
| `POST /api/auth/*` | whichever backend owns the account | **§8.1 is still open, and this row is not waiting on it.** The default is written down: until the owner answers, the relay proxies auth to **CoinePro-FX**, which is where `RESET_HOST` already points. Building against that default is not pre-empting the decision — it is following the spec, and one proxied route is reversible in an afternoon if the answer goes the other way |
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

### 4.10 What the forex feed actually is — measured, and it is not what this document said

§1 called CoinePro-FX «gold and the dollar, over Finnhub». That line is a year old and it is wrong.
Every row of `api/ws/snapshot`, read on 2026-09-19:

```json
{"symbol":"EURUSD","price":1.1490291357040405,
 "bid":1.1490291357040405,"ask":1.1490291357040405,
 "ts":1789857617149,"source":"yfinance"}
```

**`source: "yfinance"` on all seventeen, and `bid == ask == price` exactly** — so there is no spread
in this feed; one number is echoed into three fields. Crypto, for contrast, reports
`source: "lbank-ws"` and means it.

Three consequences, and the third is the one that matters:

1. **A third symbol universe.** `prices/live` serves 19, `ws/snapshot` 17, and the **socket 7** —
   AUDUSD, EURUSD, GBPUSD, NZDUSD, USDCAD, USDCHF, USDJPY. No metals, no indices, no crude, no
   crosses. So `XAUUSD` has a snapshot and **no live stream at all**: a subscriber gets its opening
   value and never another frame. The relay sends snapshot-then-stream, so that is the truth of the
   symbol rather than an invisible hole — but it is the product's headline instrument.
2. **The app files every forex quote under `QuoteSource.UNKNOWN`.** `MarketDataController` maps
   `finnhub` and `lbank` by name and everything else to `UNKNOWN`, which carries a 30-second
   staleness budget. Nothing is broken and nothing is labelled either.
3. **The chart names a different venue from the quote beside it.** `CandleGateway.sourceName` on the
   forex path is **«MetaTrader 5»**, and its KDoc says exactly why it is printed: the commonest
   accusation against this category of app is «کندل‌سازی», and the answer is provenance a reader can
   check. On one screen today the candles say MetaTrader 5 and the last price is Yahoo Finance, with
   nothing saying so. **That is the promise the label exists to keep, not kept.**

Nothing in the app or the relay should paper over any of this: a client that renamed the source
would be inventing provenance, which is worse than having none. `RUN_ALEF/BLOCKED.md §א22` is the
question to CoinePro-FX's team, and §א20 gains a second sentence — gold is missing from the
snapshot **and** from the stream.

### 4.11 Phase 3, measured — 2026-09-19

| check | answer |
| --- | --- |
| two clients on EURUSD for 150 s | **one** TCP connection from the relay to CoinePro-FX:443, counted from `/proc/net/tcp` inside the relay's own container |
| the tick both received | identical `timestamp` and `last`, one frame each |
| a second socket from one address | refused, `403` |
| 201 symbols | `{"type":"error","detail":"201 symbols; the ceiling is 200"}` |
| 200 symbols | accepted |
| `wss://pro-chart.com/api/stream` from outside, through Cloudflare | works |

**The ceiling refuses rather than truncates**, which is the behaviour `webSocketUrl`'s own comment in
the app warns about: a silently shortened subscription is a chart that never ticks for a symbol the
reader asked for and was never told about.

One measurement the server had to make to test at all, worth keeping: **the forex market is shut at
the weekend.** Upstream still sends a frame a second, with frozen values — 45 seconds, 45 frames,
zero change — and re-samples about every two minutes, which is when the timestamp moves. The relay
forwards changes rather than frames, so a closed market costs nothing downstream. A tick in the test
above is real; its price simply has not moved, because nothing is trading.

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

### Rate limits, and the number that was wrong

**60 a minute per IP on `/api/*` was written before §4.2.1 existed, and §4.2.1 broke it.** The
relay's author did the arithmetic and did not change the figure unilaterally, which was right — so
here it is, corrected.

Two seconds of polling is thirty requests a minute. **Two tabs from one address reach a sixty-a-minute
ceiling on that one route**, before `fx/prices`, a candle load or the news take any share at all. And
a per-IP limit is blunt in this product's own market: `NetworkFactory`'s KDoc already says why the
backends cannot rate-limit the phone by address — «carrier-grade NAT puts a very large number of
Iranian mobile subscribers behind one address» — which is exactly as true of an office, a household,
or a floor of a building behind one NAT.

**A limit should be proportional to what a request costs**, and these do not cost the same:

| bucket | routes | per IP |
| --- | --- | --- |
| **cached reads** | `/api/crypto/prices`, `/api/fx/prices`, `/api/app/latest`, the legal pages | **240 a minute** |
| **everything else under `/api/*`** | candles, news, signals, membership, health | **60 a minute** |
| **auth** | `/api/auth/*` (Phase 4) | **10 a minute** |
| **socket** | `wss://…/api/stream` | one per IP, 200 symbols |

The first row is generous because it is nearly free: those responses are served from a two-second
Redis cache and cost the upstreams **nothing at all** — a hundred tabs are one upstream call either
way. Refusing them is protecting a resource that is not scarce. The second row stays at sixty
because a candle miss really does reach a backend.

**Bucket by `(address, client id)` where a client id is present**, falling back to the address
alone. The terminal sends a random per-browser identifier the way the app sends `X-Install-Id`, and
for the same reason: so that honest readers behind one NAT do not collide. It is **not** a
replacement for the address limit — a browser can mint identifiers — so the per-address ceiling stays
as the backstop, an order of magnitude above the per-client one.

And the terminal's side of the bargain, which is not optional: **stop polling when the tab is
hidden** (`document.visibilityState`). A background tab has no reader. If a `429` is ever seen in
the terminal, this is the first place to look and the relay is the second.

### 6.1 As built, and measured from outside

The relay implements the table above as **two zones per bucket**: a per-client key of
`{client_ip}|{X-Install-Id}{X-Client-Id}` and a per-address key of `{client_ip}` at ten times the
figure (2400 / 600 / 100). Concatenating both headers rather than choosing between them is a Caddy
constraint — it has no fallback expression — and it lands on the right behaviour anyway: absent
headers expand to empty, so a client that sends neither collapses to the address alone, which is
this section's fallback with no conditional to get wrong. `X-Install-Id` is the name because it is
already this product's own (`NetworkFactory`); `X-Client-Id` is accepted as an alias so a browser
need not borrow the app's header.

Measured against the live host rather than read from a report:

| probe | result |
| --- | --- |
| `/api/app/latest` × 70, one address | 70 × 200 — above the 60 ceiling, so the cached-read bucket is in force |
| `/api/news` × 70, one address | 60 × 200, then 10 × 429 — the default bucket, exactly |
| `/api/news` × 40 + × 40 under two `X-Client-Id` values, then × 5 with none | 85 × 200, no 429 — the per-client key is real, and the 600 per-address backstop is what is left |

Two consequences worth writing down:

* **The buckets are shared, not per route.** Spending `/api/app/latest` also spends
  `/api/crypto/prices` and the legal pages, because they are one bucket; `/api/health` is in the
  other and is unaffected. That is the intent — the bucket is a budget for a class of cost, not a
  quota per URL — but it means a terminal that hammers one cached route starves the rest of its own
  cached reads.
* **The app sends no client id to this host.** `appUpdateGateway` builds its own unauthenticated
  client with no `installId` provider (`AppModule`), deliberately, so the phone's update check
  lands in the address bucket. It costs one request per launch against a ceiling of 240, which is
  the right trade: an install identifier does not belong on a request that carries no account and
  asks a public question.

The socket is deliberately in no per-minute bucket. This section gives it no figure, and a
self-invented one would punish a reconnect during a flap — the case where a reader is already
having a bad time. Its limits are the one-per-address and 200-symbol rules, enforced in the relay
where the subscription is read rather than at the edge where it cannot be seen.

### 6.2 Serving the bundle

A Wasm terminal is 5–15 MB, which is a different kind of object from everything else this host
serves and needs three things right. All three are in place and were **proved against a synthetic
bundle before the real one exists**, which is the right order: nothing to discover on the day it
lands.

| rule | why |
| --- | --- |
| `*.wasm` → `Content-Type: application/wasm` | without it the browser drops out of streaming compilation and reads the whole file before starting |
| `*.wasm`, `*.js` → `Content-Encoding: br`, **pre-compressed on disk at quality 11** | compressing ten megabytes per reader is a CPU bill, not a cache. Written once by `bin/precompress.sh` |
| a content hash in the name (`[.-]<8+ hex>.<ext>`) → `max-age=31536000, immutable`; `*.html` → `no-cache` | the usual pair, and for a file this size it is the difference between one download and every download |

The rules key on **extension and filename, not on path**, which is what makes §3.1's mount point a
decision the server does not have to care about.

### 6.3 Two things that look like faults and are not

Written down because each will be found again by somebody with `curl`, and a false alarm costs more
than the line it takes to pre-empt it.

* **`HEAD /api/app/latest` reports `content-length: 20`; `GET` returns 989 bytes.** Cloudflare
  answers a `HEAD` with the *compressed* length (its ETag carries the `-gzip` suffix), while the
  origin's own answer to the same request is 989. The app uses `GET`, which returns the whole
  document. `curl -I` against this route is measuring the edge's compression, not a truncated file.
* **The legal pages are `no-cache` rather than `max-age=300`.** A document whose entire value is
  being current should not be served stale for five minutes; the cost is one revalidation and a
  `304`, not a re-download. The app bundles its own copies anyway, so a reader is never stranded by
  a revalidation that fails (`docs/release/DOMAINS.md`).

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

**Cite these as «`SERVER.md` §8.<n>».** They used to be cited as «`PLAN.md` §6.<n>», which was fine
until this document grew a §6.2 of its own about serving the bundle — and «§6.2» now names two
different things in two documents, one of which is an owner decision and the other a cache header.
The numbering below is the canonical one.

0. ~~The provider and the machine.~~ **Done** — provisioned, on the same Hetzner private network as
   TradeYar and CoinePro-FX, with an agent on it. `SERVER_BUILD_PROMPT.md` is what it works from.

1. **Which backend owns the account** (`PLAN.md` §6.2, and
   `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md`). Step 5 cannot start without it. **What does not
   wait on it:** §4.4 names CoinePro-FX as the default until the answer arrives, so anything built
   against that default — `/reset` and its one proxied route (§3.1) — is following the spec rather
   than pre-empting the decision.
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

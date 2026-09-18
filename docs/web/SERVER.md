# The Pro Chart server — what to provision, and what it serves

Companion to `PLAN.md`, which fixes the *shape* of the web terminal. This fixes the **server**: what
to buy, what runs on it, which upstream routes it relays, what it stores, and how to tell when it is
working. Written so the owner can hand it to whoever provisions the machine and get back something
this app and the browser terminal can both talk to on the first day.

Nothing here is built. The machine does not exist yet, and `pro-chart.com` does not answer. What is
written down is the contract, so that when it does exist the work is configuration rather than
design.

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
the access are known, and the two servers being neighbours makes the upstream hop a local one.

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
body comes back unchanged.** No reshaping. A relay that rewrites a payload is a second contract to
keep in step with the first, and the app has one contract per backend already
(`docs/backend/MARKET_DATA_CONTRACT.md`, `docs/AUTH_CONTRACT.md`).

### 4.1 Market data — the whole of the chart

| `pro-chart.com` | upstream | upstream route | cache |
| --- | --- | --- | --- |
| `GET /api/crypto/snapshot` | TradeYar | `api/mobile/v1/ws/snapshot` | 2 s |
| `GET /api/fx/snapshot` | CoinePro-FX | `ws/snapshot` | 2 s |
| `GET /api/crypto/candles?symbol=&interval=` | TradeYar | `api/mobile/v1/market/candles` | see below |
| `GET /api/fx/candles/{symbol}` | CoinePro-FX | `api/v1/public/candles/{symbol}` | see below |
| `GET /api/crypto/prices` | TradeYar | `api/v1/public/prices` | 2 s |

**The snapshot is asked bare.** Both backends answer a call with no `symbols` parameter by returning
everything they quote, and that is the app's only discovery mechanism — `MarketCatalogGateway` says
so and `SymbolUniverseBreadthTest` proves the client handles the full list. The relay must keep
asking it bare. Naming a list here would cap the web's universe at whatever the relay's author
happened to know.

**Candles cache by whether the bar is closed.** A closed bar never changes, so it is cached
indefinitely and keyed `venue:symbol:interval:openTime`; the newest bar is cached for one interval
tick at most (2 s at a minute, 30 s at an hour). This is the one place the relay earns its keep: a
hundred tabs on BTCUSDT H1 become one upstream call an hour plus one live bar.

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

| `pro-chart.com` | upstream | upstream route | cache |
| --- | --- | --- | --- |
| `GET /api/signals?market=crypto` | TradeYar | the signals list route | 10 s |
| `GET /api/signals?market=forex` | CoinePro-FX | the signals list route | 10 s |
| `GET /api/news` | TradeYar | `api/v1/news/list` | 60 s |
| `GET /api/news/{id}` | CoinePro-FX | `user/mobile/news/{id}` | 300 s |
| `GET /api/calendar` | CoinePro-FX | `user/economic-calendar` | 300 s |
| `GET /api/announcements` | TradeYar | `api/mobile/v1/announcements` | 60 s |
| `GET /api/market-intelligence` | either | `api/mobile/v1/market-intelligence`, `user/mobile/market-intelligence` | 60 s |

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

1. **Which backend owns the account** (`PLAN.md` §6.2, and
   `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md`). Step 5 cannot start without it.
2. **Whether the terminal is open, member-only, or a read-only guest page** (`PLAN.md` §6.3).
3. **Whether the candle archive is built on day one** (§5). It is the difference between a reader
   panning to the edge of the backend's window and panning as far as the product has history.
4. **The provider.** Hetzner is assumed above because the owner runs one. Anything with 4 vCPU,
   8 GB and a fixed IP does the job.

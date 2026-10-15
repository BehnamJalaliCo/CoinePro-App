# The web app's relay

What `docs/web/SERVER.md` §4.12 and §4.13 ask `pro-chart.com` for, written and tested here so the
server only has to run it:

* `/up/tradeyar/…` and `/up/coineprofx/…`: the phone's calls to its two backends, from the page.
  Every method, and WebSocket upgrades. The bearer stays on the server behind an `HttpOnly` cookie.
* `/api/img?url=…`: a publisher's photo for the news cards. `https://` only, image types only,
  5 MB, never a private address.
* `/up/panel/…`: the web user panel (`/next/panel`). See [The web user panel](#the-web-user-panel).

Tests: `python3 -m unittest test_relay` (against fake backends, a fake publisher and a fake console).

## Run it next to the existing relay

```yaml
# docker-compose.yml, beside edge / api / db / cache
  web-relay:
    build: ./web-relay            # this directory
    restart: unless-stopped
    environment:
      RELAY_COOKIE_SECURE: "1"
      PANEL_INTERNAL_TOKEN: ${PANEL_INTERNAL_TOKEN}   # the same value the admin service has
      RELAY_PANEL_URL: http://admin:8791/internal/panel
    expose: ["8790"]
```

```caddy
pro-chart.com {
    # Before the existing /api/* block: Caddy picks the most specific path first.
    handle /up/* {
        reverse_proxy web-relay:8790
    }
    handle /api/img {
        reverse_proxy web-relay:8790
    }
    # … the existing /api/*, /terminal/* and site blocks unchanged …
}
```

One replica. Sessions are in memory, so a second replica would not know the first one's
browsers, and a restart signs every browser out, as clearing an app's data does on a phone.

## Knobs

| variable | default | |
| --- | --- | --- |
| `RELAY_TRADEYAR_ORIGIN` | `https://tradeyar.trade-future.ir/` | on the private network, the backend's inside address |
| `RELAY_COINEPROFX_ORIGIN` | `https://coineprofx.com/` | same |
| `RELAY_SESSION_DAYS` | `30` | how long a browser stays signed in without a visit |
| `RELAY_REQUESTS_PER_MINUTE` | `240` | per address + `X-Client-Id` |
| `RELAY_AUTH_REQUESTS_PER_MINUTE` | `12` | sign-in routes, same key |
| `RELAY_AUTH_ADDRESS_REQUESTS_PER_MINUTE` | `30` | sign-in routes, per address alone — `X-Client-Id` is the reader's to choose |
| `RELAY_TRADEYAR_CONNECT` | — | e.g. `https://10.0.0.3/`: connect here instead of the public name. TLS is still verified against the public name (SNI) and `Host` still names it |
| `RELAY_COINEPROFX_CONNECT` | — | same |
| `RELAY_COOKIE_SECURE` | `1` | `0` only for a local run over plain http |
| `PANEL_INTERNAL_TOKEN` | empty (panel off) | the web user panel's shared secret, the same value as the admin service's; at least 32 characters (`openssl rand -hex 32`) |
| `RELAY_PANEL_URL` | `http://admin:8791/internal/panel` | where the admin service's internal panel routes are, on the private network |
| `RELAY_COUNTRY_HEADER` | `CF-IPCountry` | the request header that carries the reader's two-letter country, if the edge adds one |
| `RELAY_HASH_SALT` | random per process | salts the hash kept of each session's address; set it to keep the hashes stable across restarts |

## Rate limits: the relay's, not a second set at the edge

`/up/*` and `/api/img` need no Caddy bucket. The relay counts every request by address and
`X-Client-Id` (the §6 key), and sign-in routes twice more: by that key at 12 a minute, and by
address alone at 30, so rotating client ids does not multiply a guesser's attempts. A second
ceiling at the edge would refuse what the relay had allowed, where no one can say why. Each backend
also enforces its own sign-in limits behind this. The edge's `/api/auth/*` and `/api/link/*` bucket
covers Pro Chart's own account routes, which are a different backend, so the two policies are about
two different things.

## Caching `/terminal/`

The bundle's file names carry **no hash**: `terminal.wasm`, `skiko.wasm`, `terminal.mjs`, `sw.js`,
`manifest.webmanifest`, the drawables. None of them may be `immutable`. Serve everything under
`/terminal/` with `Cache-Control: no-cache`, so the browser revalidates with the ETag and gets a `304`
when nothing changed. That costs one round trip per file, not a download. A catch-all `@wasm`
matcher that marks every `.wasm` as `immutable` must not match `/terminal/*`, or a reader keeps the
old app forever:

```caddy
@terminal path /terminal/*
header @terminal Cache-Control "no-cache"
# … and exclude /terminal/* from any @wasm / @hashed immutable matcher, e.g.:
# @wasm { path *.wasm; not path /terminal/* }
```

## Getting the bundle without a GitHub login

Every push to `main` puts the built bundle on the rolling release `web-latest`, which anyone can
download:

```bash
curl -fsSL -o /tmp/pro-chart-terminal.zip \
  https://github.com/BehnamJalaliCo/CoinePro-App/releases/download/web-latest/pro-chart-terminal.zip
curl -fsSL https://github.com/BehnamJalaliCo/CoinePro-App/releases/download/web-latest/pro-chart-terminal.zip.sha256
sha256sum /tmp/pro-chart-terminal.zip       # must match the line above
```

The zip holds the files of `web/build/terminal/` at its top level, plus `BUILD.txt` naming the
commit it was built from.

## Serving the new web app at `/next/`

`webapp/` is the browser's own app (Vite + React + TypeScript + lightweight-charts), built to run at
`/next/` beside `/terminal/` until it replaces it. It talks only to this relay (`/up/*`, `/api/*`,
`/api/img`) and to `/terminal/google-callback.html` for Google sign-in, all on the same origin.
Every push to `main` puts it on the same rolling release:

```bash
curl -fsSL -o /tmp/pro-chart-next.zip \
  https://github.com/BehnamJalaliCo/CoinePro-App/releases/download/web-latest/pro-chart-next.zip
curl -fsSL https://github.com/BehnamJalaliCo/CoinePro-App/releases/download/web-latest/pro-chart-next.zip.sha256
sha256sum /tmp/pro-chart-next.zip           # must match the line above
```

Unzip it into the directory Caddy serves as `/next/`. Every path that is not a file is the app's
own address (`/next/BTCUSDT/4h`), so it falls back to `index.html`. `assets/*` carry a content hash
and may be cached forever; `index.html` and `generated/*` (fonts, icons, logos, names) carry none:

```caddy
handle_path /next/* {
    root * /srv/pro-chart/next
    @hashed path /assets/*
    header @hashed Cache-Control "public, max-age=31536000, immutable"
    @plain not path /assets/*
    header @plain Cache-Control "no-cache"
    try_files {path} /index.html
    file_server
}
redir /next /next/ 308
```

When it replaces the terminal, build with `WEB_BASE=/terminal/` and serve it at `/terminal/` with
the same block; nothing else changes.

## The web user panel

`/next/panel` is the reader's own panel in the web app: identity and membership on both platforms,
trading and the audit of what the chart sent, live web sessions, the cloud workspace, the inbox,
support tickets, billing, connections, and data export and erasure. Its server half lives in the
admin service (`web/admin`, «کاربران وب»); the relay is the only way to it.

* **Who the browser is.** On every sign-in (password, Google, Telegram, register, account link —
  never a guest token) the relay asks that backend's `me` route with the real token
  (`api/mobile/v1/me`, `api/user/me`) and keeps the account's id, email, name and VIP / paid / plan
  flags with the session. Beside it: when the session began, when it was last seen, the browser,
  system and device family (never the raw User-Agent), the country from `RELAY_COUNTRY_HEADER`,
  and a salted hash of the address. The address itself is never kept or sent anywhere.
* **`/up/panel/<path>`** is passed to `RELAY_PANEL_URL/<path>` with `X-Panel-Token` (the secret)
  and `X-Panel-Identity` (that verified identity). The page must send one of its own handles as
  `Authorization: Bearer pch_…`, so a browser that signed out cannot use the panel with the
  cookie alone; the page never names its own account, and whatever it sends as `X-Panel-Identity`
  is replaced. Bodies up to 2 MB.
* **Answered by the relay itself**, since only it holds the sessions:
  `GET /up/panel/sessions` (the reader's live web sessions, this one first),
  `POST /up/panel/sessions/revoke` with `{sid}`, `{others: true}` or `{all: true}`, and
  `POST /up/panel/signout` (this browser's sign-out: the page calls it when it forgets its
  handles, and the relay forgets the session and clears the cookie).
* **Told to the console**, server to server, batched and without holding a reader's call up:
  sign-ins, refused sign-ins (the address typed, as an HMAC keyed with the secret, so only the
  console can match it to an account), sign-outs, refresh failures, revoked sessions, and every
  `POST`/`PUT`/`PATCH`/`DELETE` to `trade/orders[/id]`, `trade/positions/close|protection`,
  `trade/leverage`, `executions[/id/close]` and `venues/*` on either platform — with the method,
  path, status, the symbol, side, quantity, type, price, leverage, venue… from the body (a list of
  what may go: an API key or secret never does, whatever it is called) and the order id and state
  that came back. A console that is down loses those lines; the reader's call is never affected.
* **`/relay/internal/sessions[?account=tradeyar:7]`** and
  **`POST /relay/internal/sessions/revoke {account, sid?, operator}`** are the console's: «online
  now», devices and browsers, and «sign out everywhere». They answer 404 without the secret, and
  Caddy never routes `/relay/*`.

No Caddy change: the panel is under the existing `/up/*` block. Without `PANEL_INTERNAL_TOKEN`
(or with one shorter than 32 characters) `/up/panel/<path>` answers 503 and no event is sent; the
sessions list and sign-out still work.

## How to know it works

`docker compose exec web-relay python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8790/relay/health').read())"` prints `{"ok": true}`. Then, from the page: sign in
and open the portfolio. The network panel shows `/up/…` answering 200, `localStorage` holds only
`pch_…` handles, and the news cards show their photos.

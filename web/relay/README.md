# The web app's relay

What `docs/web/SERVER.md` §4.12 and §4.13 ask `pro-chart.com` for, written and tested here so the
server only has to run it:

* `/up/tradeyar/…` and `/up/coineprofx/…`: the phone's calls to its two backends, from the page.
  Every method, and WebSocket upgrades. The bearer stays on the server behind an `HttpOnly` cookie.
* `/api/img?url=…`: a publisher's photo for the news cards. `https://` only, image types only,
  5 MB, never a private address.

Tests: `python3 -m unittest test_relay` (sixteen cases, against fake backends and a fake publisher).

## Run it next to the existing relay

```yaml
# docker-compose.yml, beside edge / api / db / cache
  web-relay:
    build: ./web-relay            # this directory
    restart: unless-stopped
    environment:
      RELAY_COOKIE_SECURE: "1"
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

## How to know it works

`docker compose exec web-relay python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8790/relay/health').read())"` prints `{"ok": true}`. Then, from the page: sign in
and open the portfolio. The network panel shows `/up/…` answering 200, `localStorage` holds only
`pch_…` handles, and the news cards show their photos.

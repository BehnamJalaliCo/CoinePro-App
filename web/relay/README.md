# The web app's relay

What `docs/web/SERVER.md` §4.12 and §4.13 ask `pro-chart.com` for, written and tested here so the
server only has to run it:

* `/up/tradeyar/…` and `/up/coineprofx/…`: the phone's calls to its two backends, from the page.
  Every method, and WebSocket upgrades. The bearer stays on the server behind an `HttpOnly` cookie.
* `/api/img?url=…`: a publisher's photo for the news cards. `https://` only, image types only,
  5 MB, never a private address.

Tests: `python3 -m unittest test_relay` (thirteen cases, against fake backends and a fake publisher).

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
| `RELAY_COOKIE_SECURE` | `1` | `0` only for a local run over plain http |

## How to know it works

`docker compose exec web-relay python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8790/relay/health').read())"` prints `{"ok": true}`. Then, from the page: sign in
and open the portfolio. The network panel shows `/up/…` answering 200, `localStorage` holds only
`pch_…` handles, and the news cards show their photos.

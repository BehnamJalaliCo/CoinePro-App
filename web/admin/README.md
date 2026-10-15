# Pro Chart — the owner console

`pro-chart.com/admin/`: downloads, active installs, retention, campaigns (notifications), discount
codes, remote configuration, the release on offer, service health and an audit log, behind one
account with an optional TOTP second factor. Also the three small routes the Android app (5.30.0+)
talks to: `POST /api/app/hello`, `POST /api/app/event`, `GET /api/app/coupon`.

Tests: `python3 -m unittest test_admin` (no network, an in-memory database).

## Where the numbers come from

| figure | source |
| --- | --- |
| downloads, web visits, update checks | Caddy's **JSON** access log, read every minute from the last offset; one per address per day per file, a resumed download (`Range: bytes=N-`) is not a second one |
| installs, DAU/WAU/MAU, retention, versions, languages, stores, countries, hours | `POST /api/app/hello`, sent by the app at most every `hello_every_hours` with a random install id; only a salted hash of it is stored, never an address |
| delivered / opened per campaign | `POST /api/app/event` from the app |
| release and adoption | the static `/api/app/latest` file, read from disk |
| health | probes every five minutes (`ADMIN_PROBES` to change the list) |

## Run it

```yaml
# docker-compose.yml, beside edge / web-relay
  admin:
    build: ./admin                     # this directory, with fonts/ filled in (see Dockerfile)
    restart: unless-stopped
    environment:
      ADMIN_TZ: Asia/Tehran
      ADMIN_ACCESS_LOG: /logs/access.log
      ADMIN_LATEST_JSON: /site/api/app/latest
    volumes:
      - admin-data:/data                         # the SQLite file; back it up with the rest
      - /var/log/caddy:/logs:ro                  # wherever Caddy writes the JSON log below
      - /opt/prochart/site/api/app:/site/api/app:ro   # wherever /api/app/latest lives
    expose: ["8791"]
volumes:
  admin-data:
```

Before building: `mkdir -p fonts && cp ../../core/designsystem/src/main/res/font/iranyekanx_*.ttf fonts/`
(or from wherever the repository is checked out). The copies are ignored by git.

## «کاربران» — the readers' accounts (5.35.0)

The accounts readers create in the app (Android, Bazaar and direct builds, and the web terminal)
live on the two backends: TradeYar (crypto, Premium subscriptions) and CoinePro-FX (forex). Each
exposes the same admin bridge, and this console is its only caller, server to server:

| variable (this service) | default | must equal |
| --- | --- | --- |
| `ADMIN_TRADEYAR_BRIDGE_URL` | `https://tradeyar.trade-future.ir/api/mobile/v1/admin-bridge` | — |
| `ADMIN_TRADEYAR_BRIDGE_TOKEN` | empty (section off) | TradeYar's `PROCHART_ADMIN_BRIDGE_TOKEN` |
| `ADMIN_COINEPROFX_BRIDGE_URL` | `https://coineprofx.com/api/user/admin-bridge` | — |
| `ADMIN_COINEPROFX_BRIDGE_TOKEN` | empty (section off) | CoinePro-FX's `PROCHART_ADMIN_BRIDGE_TOKEN` |

Each token is at least 32 random characters (`openssl rand -hex 32`); a backend without one answers
404 on every bridge route. The token never reaches the browser. What the section does: totals,
daily sign-ups, origin (Android / web, Bazaar / direct), sign-up method and app version; a searchable,
filterable, paged list with CSV export; and per account the profile, sessions, notification devices,
Premium periods and the owner's note, with ban / unban (every session ends at once), sign out
everywhere, mark email verified, send a password-reset mail, send a notification, grant or end
Premium (TradeYar) and delete (the email typed to confirm). Every action is in the audit log.

## «کاربران وب» — the web app's readers and their panel

The web app's own user panel (`pro-chart.com/next/panel`) keeps its data here, and the owner
watches it here. The browser never calls this service for it: the relay (`web/relay`) verifies
the reader with their backend and calls `/internal/panel/…` with a shared secret and that
identity. **Caddy must never route `/internal/*`** — the four lines below do not, and the secret
is the second lock.

| variable (this service) | default | |
| --- | --- | --- |
| `PANEL_INTERNAL_TOKEN` | empty (section off) | the same value as the relay's `PANEL_INTERNAL_TOKEN`; at least 32 random characters (`openssl rand -hex 32`) |
| `ADMIN_RELAY_URL` | `http://web-relay:8790` | the relay on the private network, for «online now» and revoking sessions |

```yaml
# docker-compose.yml: the admin service gains two variables; the relay gets the same secret
  admin:
    environment:
      PANEL_INTERNAL_TOKEN: ${PANEL_INTERNAL_TOKEN}
      ADMIN_RELAY_URL: http://web-relay:8790
  web-relay:
    environment:
      PANEL_INTERNAL_TOKEN: ${PANEL_INTERNAL_TOKEN}
      RELAY_PANEL_URL: http://admin:8791/internal/panel
```

**What the reader's panel stores** (one SQLite file, per web account `tradeyar:<id>` or
`coineprofx:<id>`; a reader signed in to both platforms in one browser is one account with two
aliases): profile and preferences; the cloud workspace — layouts, chart settings, drawings,
indicator templates and scripts, watchlists — as opaque JSON with a version each, the last ten
versions kept for restore, 256 KB a part (1 MB for drawings), a stale write refused with 409; the
inbox (messages from the owner, to one account or all); support tickets with up to three small
images a message (PNG, JPEG, WebP or GIF, 300 KB each, judged by their bytes, served with a
sandboxing CSP); the activity and security log; the trade audit; client errors and feature use.
`GET export` hands the reader all of it as one JSON file; `DELETE data` (with `{confirm: true}`)
erases it, keeping only the trade audit with no account on it, and is written to the audit log.
Every write is size-limited and rate-limited per account (60 writes a minute, 30 event batches a
minute, 5 new tickets an hour, 20 replies in ten minutes, 6 exports an hour).

**Kept for:** page views and feature use 90 days, client errors 60 days, the trade audit two
years, everything else a year (pruned every six hours).

**What the owner sees** under «کاربران وب»: online now (sessions seen in the last five minutes,
from the relay), sign-ins, trades and errors per day, devices, browsers, systems and countries,
the most used features; a searchable, filterable list of web accounts (both backends) with last
seen, VIP / Premium, sign-ins, trades, open tickets and errors, with CSV; per account the
timeline, live web sessions with «close» and «sign out everywhere», the trade audit, the cloud
workspace (sizes, versions, download, restore), tickets, a message into their panel and a note;
VIP / Premium read through the existing admin bridges (matched by email: `membership`, the live
subscriptions and the ban), or the flags the backend's `me` gave at the last sign-in when a bridge
is not configured; a global trade audit with filters (account, symbol, side, status, action,
backend, venue, dates) and CSV; the tickets inbox with reply, reply-and-close and reopen; messages
to one or all web readers; the error feed, grouped. Every action, every CSV and every workspace
download is in the audit log.

## The account

No password is ever in the repository or the image. Create the owner's account on the server, once,
reading the password from standard input so it is not left in the shell history or in `ps`:

```sh
printf '%s' "$PASSWORD" | docker compose exec -T admin python admin.py set-password <username> --stdin
```

The same command resets a forgotten password (and signs every browser out). After the first sign-in,
turn on two-step verification under «حساب و امنیت».

## Removing test traffic

A check-in or a download sent by hand to test the service counts like any other. Take it out with:

```sh
docker compose exec -T admin python admin.py forget-install <the install_id that was sent>
docker compose exec -T admin python admin.py forget-ip <address> [YYYY-MM-DD]
```

Both are written to the audit log.

## Caddy

```caddy
pro-chart.com {
    log {
        output file /var/log/caddy/access.log {
            roll_size 50MiB
            roll_keep 10
        }
        format json
    }

    # Before the existing /api/* block: Caddy picks the most specific path first.
    handle /admin* {
        reverse_proxy admin:8791
    }
    handle /api/app/hello {
        reverse_proxy admin:8791
    }
    handle /api/app/event {
        reverse_proxy admin:8791
    }
    handle /api/app/coupon {
        reverse_proxy admin:8791
    }
    # … /api/app/latest stays the static file; everything else unchanged …
}
```

Keep the four routes in the `pro-chart.com` site block itself, not in a snippet shared with other
hosts. And set the site's own security headers with `?` (only when absent), so the console's stricter
ones survive: `header ?X-Frame-Options SAMEORIGIN`, `header ?Referrer-Policy strict-origin-when-cross-origin`.

`/admin*` must never be cached at the edge: the console already answers `Cache-Control: no-store`;
add a Cloudflare cache rule «bypass» for `/admin*` and `/api/app/hello` if the zone caches HTML.

Sessions are `HttpOnly; Secure; SameSite=Strict` on `/admin`, so the console needs HTTPS end to end
from the browser's view (Cloudflare in front is fine). `ADMIN_COOKIE_SECURE=0` is only for a local run.

## Knobs

| variable | default | |
| --- | --- | --- |
| `ADMIN_DB` | `/data/admin.sqlite3` | |
| `ADMIN_ACCESS_LOG` | `/logs/access.log` | comma-separated for more than one |
| `ADMIN_LATEST_JSON` | `/site/api/app/latest` | |
| `ADMIN_TZ` | `Asia/Tehran` | the day boundary of every daily figure |
| `ADMIN_PROBES` | six probes | JSON list of `{"name", "url"}` |
| `ADMIN_PROBE_EVERY` / `ADMIN_INGEST_EVERY` | `300` / `60` | seconds |
| `ADMIN_SESSION_IDLE_HOURS` / `ADMIN_SESSION_MAX_DAYS` | `12` / `7` | |
| `ADMIN_HELLO_PER_MINUTE` | `60` | per address; Iranian carriers put many phones behind one |
| `ADMIN_COOKIE_SECURE` | `1` | |
| `PANEL_INTERNAL_TOKEN` | empty | «کاربران وب», above |
| `ADMIN_RELAY_URL` | `http://web-relay:8790` | «کاربران وب», above |

One replica: state is one SQLite file.

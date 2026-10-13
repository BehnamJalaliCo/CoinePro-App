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

## The account

No password is ever in the repository or the image. Create the owner's account on the server, once,
reading the password from standard input so it is not left in the shell history or in `ps`:

```sh
printf '%s' "$PASSWORD" | docker compose exec -T admin python admin.py set-password <username> --stdin
```

The same command resets a forgotten password (and signs every browser out). After the first sign-in,
turn on two-step verification under «حساب و امنیت».

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

One replica: state is one SQLite file.

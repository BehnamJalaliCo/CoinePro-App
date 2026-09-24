# RUN WEB — blocked

Everything the browser can do is built and runs locally against the live servers (`CHECKLIST.md`).
What is left is owed to the server agent or the owner. Nothing here needs a change in this repository.

## B1 — the bundle on pro-chart.com (server agent)

`pro-chart.com/terminal/` answers 404 until the bundle is there. The relay is running (server
review, 2026-09-24). The bundle was not deployed then only because a CI artefact needs a GitHub
login to download. Since 5.10.1 every push to main also publishes it where no login is needed, as the
release `web-latest` (`web/relay/README.md`, «Getting the bundle»). Unzip into `site/terminal`, run
`bin/precompress.sh site/terminal`, and serve `/terminal/*` with `Cache-Control: no-cache`, never
`immutable`: none of its file names carry a hash (`web/relay/README.md`, «Caching»).

## B2 — run the relay (server agent) — done 2026-09-24

`web/relay/` is the service `SERVER.md` §4.12 and §4.13 describe, written and tested in this
repository: the `/up/tradeyar/…` and `/up/coineprofx/…` passthrough (every method, WebSocket too,
the bearer kept server-side behind an `HttpOnly` cookie) and `/api/img` for the news photos. What is
left is running it: one compose service and two Caddy `handle` lines, both in `web/relay/README.md`.
Until then, everything a guest sees works, signed-in screens say they cannot reach the server, and
news cards say «تصویر نیامد».

## B4 — Google sign-in from the page (owner)

In the Google Cloud console, on the web OAuth client the app already uses, add
`https://pro-chart.com` under **Authorised JavaScript origins** and
`https://pro-chart.com/terminal/google-callback.html` under **Authorised redirect URIs**. Email
sign-in does not need this.

## B5 — push while the tab is closed (owner + server)

A Web Push (VAPID) key pair and a sender on the server. While the tab is open, notifications already
work through the browser's Notification API.

## Not a blocker: the mail domain

Sign-in, reset and verification mail are sent by the two backends, as for the phone. The page sends
no mail of its own.

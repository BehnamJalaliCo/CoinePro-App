# RUN WEB — blocked

Everything the browser can do is built and runs locally against the live servers (`CHECKLIST.md`).
What is left is owed to the server agent or the owner. Nothing here needs a change in this repository.

## B1 — the bundle on pro-chart.com — done 2026-09-24

Deployed by the server agent from the `web-latest` release (5.10.1, `7ded34f`), checksum matching.
`/terminal/*` is served `no-cache` with brotli, `terminal.wasm` as `application/wasm` and never
`immutable`, and the SPA fallback answers `/terminal/BTCUSDT/4h`. Checked from here in a browser:
the app loads and its `/up/*` calls reach the relay.

## B2 — run the relay (server agent) — done 2026-09-24, on the private network since 5.10.1

`web/relay/` is the service `SERVER.md` §4.12 and §4.13 describe, written and tested in this
repository: the `/up/tradeyar/…` and `/up/coineprofx/…` passthrough (every method, WebSocket too,
the bearer kept server-side behind an `HttpOnly` cookie) and `/api/img` for the news photos. What is
left is running it: one compose service and two Caddy `handle` lines, both in `web/relay/README.md`.
Until then, everything a guest sees works, signed-in screens say they cannot reach the server, and
news cards say «تصویر نیامد».

## B4 — Google sign-in from the page (owner) — done 2026-09-24

Both web OAuth clients carry `https://pro-chart.com` and
`https://pro-chart.com/terminal/google-callback.html`. The CoinePro-FX client (`…nnr0l8…`) had been
deleted on 2026-08-28 while that backend still named it, so Google sign-in on CoinePro-FX was broken
on the phone as well. It was restored inside Google's 30-day window, which ended about 2026-09-27.

## B5 — push while the tab is closed (owner + server)

A Web Push (VAPID) key pair and a sender on the server. While the tab is open, notifications already
work through the browser's Notification API.

## Not a blocker: the mail domain

Sign-in, reset and verification mail are sent by the two backends, as for the phone. The page sends
no mail of its own.

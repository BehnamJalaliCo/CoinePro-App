# RUN WEB — blocked

Everything the browser can do is built and runs locally against the live servers (`CHECKLIST.md`).
What is left is owed to the server agent or the owner. Nothing here needs a change in this repository.

## B1 — the bundle on pro-chart.com (server agent)

`pro-chart.com/terminal/` serves the W1b chart until the new bundle replaces it. Put
`web/build/terminal/` (or CI's `pro-chart-terminal` artefact) at `site/terminal` and run
`bin/precompress.sh site/terminal`. `.wasm` is already `application/wasm` and the SPA fallback on
`/terminal/*` is already on.

## B2 — the passthrough for signed-in screens (server agent)

`SERVER.md` §4.12: `/up/tradeyar/…` and `/up/coineprofx/…`, every method and the WebSocket upgrade,
the header allowlist, and the bearer kept server-side behind an `HttpOnly` cookie. Until it exists,
everything a guest sees works (the named relay routes), and every signed-in screen says it cannot
reach the server, which is what the phone says with no network.

## B3 — publishers' photos (server agent)

`SERVER.md` §4.13: `GET /api/img?url=`, `https://` only, image types only, 5 MB, no private
addresses. Until it exists the news cards say «تصویر نیامد».

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

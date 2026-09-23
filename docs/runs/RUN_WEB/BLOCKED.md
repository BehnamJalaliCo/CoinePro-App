# RUN WEB — blocked

## B1 — the bundle on pro-chart.com

Owed to the server agent, not the owner. `pro-chart.com/terminal/` answers `404` until the directory
`web/build/terminal/` — or the `pro-chart-terminal` artefact the CI run keeps — is placed at
`site/terminal` and `bin/precompress.sh site/terminal` is run. Nothing about the server needs to
change: `.wasm` is already `application/wasm`, the SPA fallback is already on `/terminal/*`, and the
relay routes the page calls are live.

## Not a blocker: the mail domain

The terminal is open and read-only (owner, 2026-09-21). No sign-in, no reset, no verification — so no
mail is sent and no sending domain is needed for this release.

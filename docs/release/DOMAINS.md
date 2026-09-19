# Domains — which host does what, and what moves when

The product is **Pro Chart**. Its domain is **`pro-chart.com`**, owned by the owner. The two
backends it talks to have their own domains, and those do not change here: an API host is a
deployment address, not a brand.

| Host | Role | In the app | Status (measured 2026-09-19) |
| --- | --- | --- | --- |
| `pro-chart.com` | the brand: the legal pages, `assetlinks.json`, the update document, the read-only relay, later the web terminal | `BrandConfig.WEB_HOST`, `WEB_URL`, `LEGAL_BASE_URL`; App Link `/reset` in the manifest; `AppUpdateGateway` | **live** — Caddy behind Cloudflare, HTTP/2 and HTTP/3, brotli. Origin CA certificate to 2041, zone on Full (strict), **so never certificate-pinned** |
| `coineprofx.com` | CoinePro-FX: the forex API (`/api/`), static assets (`/assets/…`), its own recovery e-mail (`/reset-password`) | `COINEPRO_API_BASE_URL_FX` at build time; `BrandConfig.RESET_HOST`; App Link `/reset-password` | live, behind Cloudflare (so never certificate-pinned — `docs/security/PINNING.md`) |
| `tradeyar.trade-future.ir` | TradeYar: the crypto API | `COINEPRO_API_BASE_URL` at build time | live, Let's Encrypt, pinned |
| `user.tradeyar.trade-future.ir` | TradeYar's web app; its recovery e-mail links here | **nothing** since 4.47.0 — the App Link claim was removed | live; its recovery link opens the browser |

## What changed in 4.47.0

* `BrandConfig.LEGAL_BASE_URL` is `https://pro-chart.com/legal`. Every terms / privacy /
  delete-account link the app opens, and the four legal documents' own cross-references
  (`docs/legal/*.md` and the in-app copies), point there.
* The manifest claims `https://pro-chart.com/reset` as an App Link and no longer claims
  `user.tradeyar.trade-future.ir`. `coineprofx.com/reset-password` stays, because that server
  still mails it.
* `scripts/release/print-assetlinks.sh` prints the file for `pro-chart.com`.

## What changed on 2026-09-19 — the host answers

**This paragraph used to say «none of this is visible to a reader until the host serves».** It
serves. Measured from outside, through Cloudflare:

| | |
| --- | --- |
| `/legal/terms/`, `/legal/privacy/`, `/legal/delete-account/` | `200`, **zero redirects**, `text/html` |
| `/.well-known/assetlinks.json` | `200`, `application/json`, no redirect, and the fingerprint in it is the release keystore's — checked against `apksigner`'s reading of the APK |
| `/api/app/latest` | `200`, `application/json`, and the APK it names is **byte-identical** to the GitHub release |
| `www.pro-chart.com` | `301` to the apex |

So the three consequences listed here for a year are closed:

* **A legal link from inside the app now opens a page.** The bundled copies
  (`feature/legal/src/main/assets/legal/`) remain the reader's primary path and the in-app reader
  still shows them; the link is no longer an affordance that leads nowhere. This was the oldest
  loose end in the product.
* **The `pro-chart.com` App Link can verify.** `assetlinks.json` answers as `application/json` with
  no redirect and names the right key, which is the whole of what Android's verifier asks.
* **The Play listing URLs can move** from `coineprofx.com/legal/…` — though `docs/PLAY_LISTING.md`
  now opens by saying the product is not on Play at all, so this is preparation rather than a task.

One rule this makes permanent rather than provisional: **`pro-chart.com` must never be
certificate-pinned.** It is behind Cloudflare, which rotates the edge certificate on its own
schedule, and a pinned build cannot be told. Same reason `coineprofx.com` is not pinned.

## What has to be put on `pro-chart.com`, in order

1. **DNS + TLS.** A and AAAA records; a certificate (Let's Encrypt is fine; if it goes behind
   Cloudflare, it must not be pinned — same rule as `coineprofx.com`).
2. **`/legal/terms/`, `/legal/privacy/`, `/legal/delete-account/`** — rendered from
   `docs/legal/TERMS.md`, `TERMS_EN.md`, `PRIVACY_POLICY.md`. Static HTML is enough. Once these
   answer, switch the three Play listing URLs.
3. **`/.well-known/assetlinks.json`** — `Content-Type: application/json`, no redirect, no auth;
   `scripts/release/print-assetlinks.sh <keystore> <alias>` prints it. Verify with
   `adb shell pm get-app-links com.coinepro.app`. **The fingerprint is the release keystore's own**,
   because with no Play in the path nothing re-signs the upload — `docs/release/DISTRIBUTION.md`
   §5, which also gives the three ways to read it.
4. **`/api/app/latest` and `/download/pro-chart-X.Y.Z.apk`** — the update document and the file it
   names. Static, written by the release process. **This is the app's whole update channel**: there
   is no store to carry one, so until this answers, a reader on an old build has no way to learn
   there is a newer one. `docs/web/SERVER.md` §4.6 is the contract; `DISTRIBUTION.md` is why.
5. **`/reset`** — only when a backend starts mailing recovery links on this host. Until then the
   path can 404; the claim is harmless.
5. **The web terminal** (`docs/web/PLAN.md`) — an API gateway on this host in front of the two
   backends, then the Compose Multiplatform build. Not before 1–4.

## What does not move

* The API base URLs. They are per-build (`app/build.gradle.kts`, `COINEPRO_API_BASE_URL*`), and the
  app talks to whatever host the backend is deployed on. Putting the brand host in front of the
  APIs is step 5's gateway, and a backend change, not an app change.
* The certificate pins. They are per API host and expire (`COINEPRO_CERTIFICATE_PINS_UNTIL`); the
  brand host is not pinned.
* Support. `BrandConfig.SUPPORT_URL` is a Telegram channel and stays one.
* The three Play listing URLs, for now. They must keep pointing at `coineprofx.com/legal/…` until
  `pro-chart.com` answers — and, separately, **the product is not on Play at all**
  (`docs/release/DISTRIBUTION.md`), so `docs/PLAY_LISTING.md` is preparation rather than a
  description of how anybody gets the app.

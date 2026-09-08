# Domains — which host does what, and what moves when

The product is **Pro Chart**. Its domain is **`pro-chart.com`**, owned by the owner. The two
backends it talks to have their own domains, and those do not change here: an API host is a
deployment address, not a brand.

| Host | Role | In the app | Status (measured 2026-09-08) |
| --- | --- | --- | --- |
| `pro-chart.com` | the brand: website, legal pages, `assetlinks.json`, later the web terminal | `BrandConfig.WEB_HOST`, `WEB_URL`, `LEGAL_BASE_URL`; App Link `/reset` in the manifest | **not serving** — no answer on 80 or 443 (`curl` times out) |
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

None of this is visible to a reader until the host serves. Until then:

* a legal link from inside the app opens a browser on a host that does not answer. The documents
  themselves are **bundled** (`feature/legal/src/main/assets/legal/`) and the in-app reader shows
  them; the link is the "open on the web" affordance and nothing else;
* the `pro-chart.com` App Link fails verification and the recovery link, if anyone ever received
  one, opens the browser;
* **the Play listing must keep the `coineprofx.com/legal/…` URLs** — the store rejects a privacy
  URL that does not answer. `docs/PLAY_LISTING.md` says so beside each field.

## What has to be put on `pro-chart.com`, in order

1. **DNS + TLS.** A and AAAA records; a certificate (Let's Encrypt is fine; if it goes behind
   Cloudflare, it must not be pinned — same rule as `coineprofx.com`).
2. **`/legal/terms/`, `/legal/privacy/`, `/legal/delete-account/`** — rendered from
   `docs/legal/TERMS.md`, `TERMS_EN.md`, `PRIVACY_POLICY.md`. Static HTML is enough. Once these
   answer, switch the three Play listing URLs.
3. **`/.well-known/assetlinks.json`** — `Content-Type: application/json`, no redirect, no auth;
   `scripts/release/print-assetlinks.sh <keystore> <alias>` prints it. Verify with
   `adb shell pm get-app-links com.coinepro.app`.
4. **`/reset`** — only when a backend starts mailing recovery links on this host. Until then the
   path can 404; the claim is harmless.
5. **The web terminal** (`docs/web/PLAN.md`) — an API gateway on this host in front of the two
   backends, then the Compose Multiplatform build. Not before 1–3.

## What does not move

* The API base URLs. They are per-build (`app/build.gradle.kts`, `COINEPRO_API_BASE_URL*`), and the
  app talks to whatever host the backend is deployed on. Putting the brand host in front of the
  APIs is step 5's gateway, and a backend change, not an app change.
* The certificate pins. They are per API host and expire (`COINEPRO_CERTIFICATE_PINS_UNTIL`); the
  brand host is not pinned.
* Support. `BrandConfig.SUPPORT_URL` is a Telegram channel and stays one.

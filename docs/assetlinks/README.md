# `assetlinks.json` — one per host

Android verifies an App Link by fetching this file from the host the link points at. Until it is
served, the link opens in the browser instead of the app — which is harmless, and has to work
anyway for anyone reading the e-mail on a desktop.

| Host | Serve at | Claims |
| --- | --- | --- |
| `user.tradeyar.trade-future.ir` | `/.well-known/assetlinks.json` | `/reset…` |
| `coineprofx.com` | `/.well-known/assetlinks.json` | `/reset-password…` |

Requirements, all three of which are the usual reasons this silently fails:

* `Content-Type: application/json`
* **no redirect** — not even http→https, and not a trailing-slash redirect
* **no authentication**, and reachable from outside your network

The fingerprint in both files is the SHA-256 of the release signing certificate the app is signed
with today (`CN=CoinePro`), taken from `apksigner verify --print-certs` rather than typed.

> **If the app ever moves to Play App Signing**, Google re-signs with its own key and the
> fingerprint changes. Add the one from Play Console → App integrity → App signing key certificate
> to the same array — **both**, not instead: the upload key still signs what reviewers install.

Verify after deploying:

```
curl -sI https://<host>/.well-known/assetlinks.json      # 200, application/json, no redirect
```

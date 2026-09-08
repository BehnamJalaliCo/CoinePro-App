# App Links — what the app claims, and what each host has to serve

The manifest claims two verified `https` links, both for password recovery, and one custom scheme.
A verified link opens the app directly from the recovery e-mail; an unverified one opens the
browser, which also works and has to. Nothing here is a bug when verification is off — it is a
link that could be one tap shorter.

| Claim | Where it is declared | Who owns the host |
|---|---|---|
| `coinepro://signal/…`, `coinepro://activity`, `coinepro://market/…` | `AndroidManifest.xml`, `BrandConfig.SCHEME` | the app; nothing to serve |
| `https://pro-chart.com/reset` | `AndroidManifest.xml`, `BrandConfig.WEB_HOST` + `WEB_RESET_PATH`, `DeepLinkValidation.kt` | the brand (owner) — see `DOMAINS.md` |
| `https://coineprofx.com/reset-password` | `AndroidManifest.xml`, `BrandConfig.RESET_HOST`, `DeepLinkValidation.kt` | CoinePro-FX |

## The decision on the TradeYar link (revised 4.47.0)

Until 4.47.0 the manifest also claimed `https://user.tradeyar.trade-future.ir/reset`, on the
reasoning that the link is the one that backend's recovery e-mail already sends. The owner's
brand plan reversed that: the store manifest names the brand's host and the API hosts it talks
to, and nothing else. The claim is gone from the manifest, from `DeepLinkValidation.kt` and from
`print-assetlinks.sh`. That e-mail's link now opens the browser on every phone, which is where a
reader without the app was always going to read it, and which works. If TradeYar ever mails a
`pro-chart.com/reset` link instead, the claim above already covers it.

## What each host must serve

`https://<host>/.well-known/assetlinks.json`, `Content-Type: application/json`, no redirect, over
TLS, reachable without a session:

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.coinepro.app",
    "sha256_cert_fingerprints": ["<SHA-256 of the signing certificate>"]
  }
}]
```

`scripts/release/print-assetlinks.sh <keystore> <alias>` prints it with the fingerprint of the key
it is given. **Which key matters:** for an install from the GitHub release APK it is the release
keystore's key (`5DE87F4B…` as SHA-1; the script prints the SHA-256 form). For an install from Google
Play with Play App Signing on, it is the *app signing key* from Play Console → Setup → App signing,
not the upload key. During a rollout both may be listed at once.

## Verifying

```bash
adb shell pm get-app-links com.coinepro.app
```

`verified` beside a host means the link opens the app; `legacy_failure` or `1024` means the JSON
was not reachable or the fingerprint did not match. Android re-verifies on install and on update;
after fixing a file, reinstall rather than waiting.

## The brand domain

`pro-chart.com` is claimed already (`BrandConfig.WEB_HOST`), and `DeepLinkValidation.kt` accepts a
`/reset?token=…` from it. What is missing is the host itself: as of 2026-09-08 it does not answer
on 80 or 443, so the claim fails verification and the link opens the browser, harmlessly. The
order of work to make it real is in `docs/release/DOMAINS.md`. `coineprofx.com` stays claimed for
as long as CoinePro-FX's e-mails name it.

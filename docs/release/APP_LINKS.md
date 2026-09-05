# App Links — what the app claims, and what each host has to serve

The manifest claims two verified `https` links, both for password recovery, and one custom scheme.
A verified link opens the app directly from the recovery e-mail; an unverified one opens the
browser, which also works and has to. Nothing here is a bug when verification is off — it is a
link that could be one tap shorter.

| Claim | Where it is declared | Who owns the host |
|---|---|---|
| `coinepro://signal/…`, `coinepro://activity`, `coinepro://market/…` | `AndroidManifest.xml`, `BrandConfig.SCHEME` | the app; nothing to serve |
| `https://coineprofx.com/reset-password` | `AndroidManifest.xml`, `BrandConfig.RESET_HOST`, `DeepLinkValidation.kt` | CoinePro-FX |
| `https://user.tradeyar.trade-future.ir/reset` | `AndroidManifest.xml` | TradeYar |

## The decision on the TradeYar link

The audit asked for the `.ir` App Link to be removed or gated behind a build flag. It stays: the
host is the owner's own crypto backend, the link is the one its recovery e-mail already sends, and
removing the claim would make that e-mail open a browser for readers who have the app. A manifest
intent filter cannot be switched by a Gradle property without a second manifest source set, and a
second manifest for one `<data>` line is more surface than the line. If the host ever moves behind a
brand domain, this is the one place to change and `print-assetlinks.sh` regenerates the file below.

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

## When the brand domain exists

Add `<data android:scheme="https" android:host="<brand-domain>" android:pathPrefix="/reset" />` to the
same intent filter, update `BrandConfig.RESET_HOST` and `DeepLinkValidation.kt`, and serve the same
`assetlinks.json` there. The old hosts can stay claimed for as long as their e-mails are in inboxes.

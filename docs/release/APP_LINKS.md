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
`/reset?token=…` from it.

**The host answers now, and the verification side is done.** As of 2026-09-20,
`/.well-known/assetlinks.json` returns `200 application/json` with no redirect and carries the
release keystore's own SHA-256 — checked against `apksigner`'s reading of the APK rather than
against anybody's report of it. That is everything Android's verifier asks, so the claim verifies
and the link opens the app.

**The page behind the link exists now.** It was a `404` on 2026-09-20 and the asymmetry was worth
naming — the app would have opened on a link that, for anybody without the app, led nowhere. It
serves `200` with and without `?token=`, reads the token from the query, posts it and shows the
backend's own answer, and calls nothing on load (the reason for that last one is in `SERVER.md`
§4.4.1: a mail scanner that fetched the URL would otherwise burn the token before the reader
clicked). `SERVER.md` §3.1 keeps the path out of the terminal's SPA fallback.

**One thing to change in the same breath as any mail that starts carrying a link.** There are two
reset flows on CoinePro-FX and they carry different credentials: the **academy** one e-mails
`…/reset-password?token=<43 characters>`, which `RESET_TOKEN` accepts; the **mobile** one — the flow
this app uses — e-mails an **eight-character code**, `ABCD-EFGH`, and no link at all. So if anybody
ever puts that code into a `pro-chart.com/reset?token=…` link, `RESET_TOKEN`'s sixteen-character
minimum will drop it, `DeepLinkValidation` will return null, and the App Link will open the app to
nothing. The regex is deliberately not widened in advance — a validator loosened for a case that
does not exist is a validator nobody can reason about — but widening it belongs in the same change
as the mail.

**Since 2026-09-21 the account on the web is TradeYar's** (`SERVER.md` §8.1), and that settles what
this claim is for. TradeYar's reset mail builds its link from `MOBILE_RESET_DEEP_LINK_BASE`, which
is **unset by default on purpose** — its own comment says a link is «never given a guessed default,
because a link that 404s is worse than no link» — and the address it should carry is
`https://pro-chart.com/reset`, which now exists. Its token is a URL token, comfortably past
`RESET_TOKEN`'s sixteen-character floor, so the note above about the eight-character code is a
CoinePro-FX matter rather than a live risk.

`coineprofx.com` stays claimed for as long as CoinePro-FX's e-mails name it.

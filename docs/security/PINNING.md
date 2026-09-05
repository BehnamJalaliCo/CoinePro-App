# Certificate pinning — how it is wired, why it ships off, and how to turn it on

## What is in the app

`NetworkFactory.okHttpClient(pins = …)` installs an OkHttp `CertificatePinner` when it is given
pins and installs nothing when it is not. Both API clients — CoinePro-FX and TradeYar — read their
pins from `BuildConfig.CERTIFICATE_PINS`, which the build fills from the Gradle property
`COINEPRO_CERTIFICATE_PINS` (or `local.properties`). Format, one line:

```
coineprofx.com=sha256/<primary>;coineprofx.com=sha256/<backup>;tradeyar.trade-future.ir=sha256/<primary>;tradeyar.trade-future.ir=sha256/<backup>
```

A malformed entry fails the build's client construction loudly (`CertificatePinsTest`); an empty
property means no pinning. **Every build today ships with it empty.**

## Why it ships off

A pin is a promise about a certificate the app does not control. A wrong pin — a rotated
certificate, an intermediate the host changed, a typo — is an app that cannot reach its own server
and cannot tell the reader why, for every reader, until a new build reaches them. Turning it on
without the backup pin and the rotation procedure below is worse than leaving it off, so the switch
is the owner's, taken with the material only the owner holds: the live certificates and the next
ones.

## Producing the pins

For each host, the SPKI digest of the leaf **and** of the intermediate that will still be valid
after the next rotation (the intermediate is the usual backup, because a renewed leaf from the same
CA chains to it):

```bash
openssl s_client -servername coineprofx.com -connect coineprofx.com:443 </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64
```

Prefix the result with `sha256/`. Repeat with `-showcerts` to take the intermediate's key. List both
for each host. Never list only one.

## Rotation

1. A release ahead of any certificate change, add the **new** certificate's pin beside the current
   one (three pins for the host during the overlap is fine).
2. Ship it; wait until the release has reached the readers who matter (the release notes say what
   is in it, and `X-App-Version` on every request says who has updated).
3. Rotate the certificate.
4. In the following release, drop the old pin.

If step 1 is ever skipped, the recovery is a new build with the pin fixed — there is no remote
escape hatch, deliberately: an unpinned channel that can rewrite the pins is the hole pinning
exists to close, and a *pinned* channel for it needs the same pins that are wrong.

## What pinning does not cover

* The WebView terminal (`terminal.coinepro.com`) — the system WebView trusts the system store.
* The public feed client (`PublicFeedClient`) — third-party hosts whose certificates the owner
  does not control and must not pin.
* `network_security_config.xml` still trusts the system store only (`cleartextTrafficPermitted`
  is false). A `<pin-set>` there would duplicate the OkHttp pins in a resource the build cannot
  fill from a property; the one source is the property above.

## Play Integrity

Not in the app. The SDK (`com.google.android.play:integrity`) is not among the dependencies and
adding it needs a Google Cloud project linked to the Play listing, a server endpoint that decodes
the verdict, and a decision about what a failed verdict blocks. The natural seam is already there:
`AdminGate` and the signature tamper screen (`EXPECTED_SIGNERS`) are where an integrity verdict
would be consulted on sign-in, on saving an exchange key and on executing a signal. Until the
project exists, the tamper screen is the only layer and it says so in `PHASE2_REPORT.md`.

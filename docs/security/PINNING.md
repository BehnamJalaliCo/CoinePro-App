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

---

## What the two servers answered — 2026-09-05

Both were asked for a primary pin and a backup. The two answers were **different in kind**, and the
app's position follows the answers rather than the request.

### TradeYar — two pins, and the rotation fault they found on the way

| | SHA-256 SPKI |
|---|---|
| primary | `RO8XwxTQmKWLxQ7Ij7dkTd5vWTS4aC2pROWNg3Sh25c=` |
| backup | `Q1JB2C45jMeyX4xQi8ZE83kmB+EfduUc2utHJ+H6YHI=` |

Both P-256 ECDSA. Worth recording what they found while producing them: `certbot 5.6` **changes the
key on every renewal** unless told otherwise, and theirs was doing exactly that — two separate
`privkey` files in the archive, June and 8 August. The current certificate expires 6 November, so
the next renewal was due around **7 October**: the failure this document was written to prevent,
arriving from a cron job nobody was watching. `reuse_key = True` is now set and `--dry-run` passes.
They accept the 30-day notice.

### CoinePro-FX — pinned at the CA, by the owner's decision (4.57.0)

`coineprofx.com` is behind Cloudflare: the certificate a handset sees is Cloudflare's edge
certificate, renewed with a **new key** and no notice, and Cloudflare has changed CA before
(DigiCert → Let's Encrypt → Google Trust Services). A leaf pin there would be a dated lock-out.
The server team's advice was not to pin; the owner's instruction for the 4.52 run was to pin both
hosts with a primary and a backup. Both are honoured by pinning **the CA and not the leaf**:

| | SHA-256 SPKI | valid to |
|---|---|---|
| primary — GTS WE1, the intermediate issuing today's edge certificate | `kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=` | 2029-02-20 |
| backup — GTS Root R4 | `mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=` | 2028-01-28 |
| backup — GTS Root R1 | `hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=` | 2036 |
| backup — ISRG Root X1 | `C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=` | 2035 |
| backup — ISRG Root X2 | `diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=` | 2040 |

A renewal under any Google Trust Services or Let's Encrypt intermediate keeps matching; a move to
a third CA (SSL.com, say) would not, and the expiry below bounds that. The root digests were
computed from the CAs' own published certificates (`pki.goog/repo/certs/gtsr1.pem`, `gtsr4.pem`,
`letsencrypt.org/certs/isrgrootx1.pem`, `isrg-root-x2.pem`) and cross-checked against the chain
the host actually served.

## What ships, and since when

`app/build.gradle.kts` carries `DEFAULT_CERTIFICATE_PINS` and `DEFAULT_CERTIFICATE_PINS_UNTIL`
(**2027-03-01**) since 4.57.0; `COINEPRO_CERTIFICATE_PINS` / `_UNTIL` override them. TradeYar
ships the two pins the server team produced plus ISRG Root X1 and X2, so a renewal that lands on
the other Let's Encrypt intermediate (their June and August certificates were issued by YE1 and
YE2 respectively) still matches. `CertificatePinDefaultsTest` reads `BuildConfig` and fails the
build when either host has fewer than two pins or the expiry has passed.

### The verification that was pending, done

The earlier version of this document held the pins back because the build environment's egress
proxy was believed to terminate TLS. Measured on 2026-09-09: it does not — `openssl s_client
-proxy … -showcerts` returned the CA's chain (issuer `GTS WE1` for CoinePro-FX, `Let's Encrypt
YE2` for TradeYar), and TradeYar's leaf digest was exactly the primary the server team had sent:

```
subject=CN = tradeyar.trade-future.ir issuer=C = US, O = Let's Encrypt, CN = YE2 notAfter=Nov  6 14:06:23 2026 GMT
RO8XwxTQmKWLxQ7Ij7dkTd5vWTS4aC2pROWNg3Sh25c=
```

That match is what turned the recorded pins into shipped ones.

## Before 2027-03-01

Re-run the measurement above from any network, confirm the two leaves and the intermediates,
and move `DEFAULT_CERTIFICATE_PINS_UNTIL` forward in a release that reaches readers before the
date — or the app is simply unpinned from that day, which is the failure mode chosen on purpose.

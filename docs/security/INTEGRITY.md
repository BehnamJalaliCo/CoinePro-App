# Play Integrity — what the app sends, and what the backend has to verify

## In the app

`PlayIntegrityInterceptor` (app module) asks Play for an integrity token on the three requests that
move money or credentials, and sends it in two headers:

| Header | Value |
|---|---|
| `X-Play-Integrity` | the token Play issued |
| `X-Play-Integrity-Nonce` | the nonce the token is bound to |

The gated requests are writes (`POST`, `PUT`, `PATCH`) whose path ends in one of:

- `/login` — sign-in on either platform,
- `/execution/connections` — saving an exchange's API keys,
- `/executions` — executing a signal,
- `/venues/lbank` — the venue's own key route.

The nonce is `base64url(SHA-256("METHOD path minute"))` without padding, where `minute` is
`floor(epochMillis / 60000)`: the backend recomputes it for the current and the previous minute,
so a token cannot be replayed onto another route and a captured one goes stale in a minute.

**The app never refuses.** Where Play cannot issue a token — no Play Services, an emulator, a
network that cannot reach Google — the request goes without the headers and the backend's policy
decides what an unattested sign-in is worth. On this app's market a phone without Play is not a
corner case. The signature check (`EXPECTED_SIGNERS`, the tamper screen) stays as the second layer.

## Turning it on

Set `COINEPRO_PLAY_INTEGRITY_PROJECT` (a Gradle property or `local.properties`) to the Google
Cloud project number the app is linked to in Play Console → *App integrity*. Zero or absent sends
nothing. Debug builds always send nothing.

## In the backend

1. Decode the token with the Play Integrity API (`decodeIntegrityToken`) using the same project.
2. Check `requestDetails.nonce` equals the nonce header and equals a nonce recomputed for this
   request in the current or previous minute; check `requestDetails.requestPackageName` is the
   app's and `requestDetails.timestampMillis` is recent.
3. Read `appIntegrity.appRecognitionVerdict` (`PLAY_RECOGNIZED`) and
   `deviceIntegrity.deviceRecognitionVerdict` (`MEETS_DEVICE_INTEGRITY`).
4. Decide by route: a failed verdict on `/executions` is a refusal; on `/login` it may be a
   step-up (e-mail code) rather than a refusal, because the reader without Play is real.
5. A request with no headers is «unattested», not «failed»: apply the same step-up policy.

The verdict is a signal, never the only lock: the session token, the rate limiter and the
signature check all stay.

---

## Where both servers got to — 2026-09-05

Both implemented the contract above, both measured the same wall, and it is not on either side of
the wire this document describes.

```
POST playintegrity.googleapis.com/v1/com.coinepro.app:decodeIntegrityToken
-> 400 {"error":{"message":"App is not found.","status":"INVALID_ARGUMENT"}}
```

That answer is **not** 403 (the service account is authorised), **not** `SERVICE_DISABLED` (the API
is on), and not a timeout (Google is reachable from both hosts — worth confirming on these
networks). It means the package `com.coinepro.app` is not linked to the Cloud project in **Play
Console**, and until it is, every decode returns "unverified".

**The one action, and it is the owner's:** Play Console → CoinePro → App integrity → Link Cloud
project → `1033486124390`.

Until then both servers hold the feature in observe mode, which is the correct posture: a verdict
that can only come back unverified must never refuse a request, or enforcement becomes a control
that looks real and checks nothing. Both implemented the three-state rule this document asked for
— header absent and verdict unknown both **pass**, and only an explicit bad verdict on the two
non-login routes refuses — so switching enforcement on the day of the link is strictly additive:
every request that works today goes on working.

## A limit of the nonce, stated by TradeYar and worth keeping here

`SHA-256("METHOD path minute")` proves the token was minted **for this route** within the last two
minutes. It does not prove it was minted for *this request*, and it cannot: every device calling
`POST /api/mobile/v1/executions` in the same minute computes the identical nonce, because the input
contains nothing specific to the caller. A stolen token is therefore replayable by anyone on the
same route until that minute expires.

This is a property of the scheme, not a fault in either implementation, and the value it does have
is real: it stops a scripted client that never talks to Play at all, and it caps a stolen token's
useful life at two minutes.

Closing it needs a **server-issued nonce** — the backend mints a random value bound to the account
and the request, the app passes it to Play, and only that value is accepted back. That is a change
on both sides of the wire and a new round trip before every protected call. TradeYar proposed it;
this app has not assumed it. It is an open decision, not a scheduled change.


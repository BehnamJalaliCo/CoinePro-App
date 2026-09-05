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

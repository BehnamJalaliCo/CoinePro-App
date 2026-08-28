# TradeYar — the Google client id in `auth/methods` points at a deleted client

> **Resolved 28 August 2026.** `auth/methods` now serves
> `1033486124390-07nqc4h9j1agsrcrpvq7cgsa5k6evced.apps.googleusercontent.com`, and that id answers
> `redirect_uri_mismatch` at Google — confirmed from this repository against the live route, not
> taken on report.
>
> Their answer improved on the ask in a way worth recording: the two ends **cannot** diverge,
> because both read one variable — `auth/methods` serves its first element and `auth/google` uses
> the same list as the accepted `aud`. That makes the class of bug impossible rather than fixing an
> instance of it, which is the better shape of the two.
>
> They were also right that the abbreviated ids in the table below were not reproducible. They are
> written out in full now, so the probe can be run against any of them verbatim.

**One field to change, on your side only. The app needs no release.**

Google sign-in from the Android app has never worked. The cause is now established, and it is not
in the app or in the Firebase configuration: the OAuth client whose id you serve as
`google_client_id` **has been deleted from the Google Cloud project**.

## How that was established

Google's authorize endpoint distinguishes three states, so any client id can be checked from a
terminal without credentials:

```bash
curl -sL "https://accounts.google.com/o/oauth2/v2/auth?client_id=<ID>\
&redirect_uri=https%3A%2F%2Fexample.com&response_type=code&scope=openid" \
  | grep -oE "deleted_client|invalid_client|redirect_uri_mismatch"
```

| Client id | Answer | State |
| --- | --- | --- |
| `1033486124390-nnr0l8q2k8e5mqjhpf0o4spmigakovsp.apps.googleusercontent.com` — what `auth/methods` returned | `deleted_client` | **Deleted.** |
| `1033486124390-07nqc4h9j1agsrcrpvq7cgsa5k6evced.apps.googleusercontent.com` — the replacement Web client | `redirect_uri_mismatch` | Live. |
| `1033486124390-aji26kov4lnmiolpq8rb00csajv51ij4.apps.googleusercontent.com` — the app's Android client | `redirect_uri_mismatch` | Live. |
| a made-up id, as a control | `invalid_client` | Never existed. |

`redirect_uri_mismatch` is the *healthy* answer here: it means Google resolved the client and only
objected to the throwaway redirect URI the probe supplied.

## What to change

Replace the client id in **both** places. One without the other fails at the other end.

1. **`GET api/mobile/v1/auth/methods` → `google_client_id`**

   ```
   1033486124390-07nqc4h9j1agsrcrpvq7cgsa5k6evced.apps.googleusercontent.com
   ```

   This is the audience the app asks Google to mint a token for. The app reads it at runtime and
   compiles nothing in, which is why this is fixable without an app release.

2. **`POST api/mobile/v1/auth/google` → the `aud` you verify the ID token against**

   The same value. If the app mints a token for one audience and you verify against another,
   verification fails with a valid token and a correct password, which is the hardest kind of
   failure to read from either side.

## Two things you do not need

* **The client secret is not required for this.** Verifying a Google ID token needs the client id
  as the expected `aud` and Google's public keys — that is all. `google-auth`'s
  `id_token.verify_oauth2_token(token, request, CLIENT_ID)` takes no secret. A secret is only for
  the authorization-code flow, which this app does not use: Credential Manager on the phone returns
  an ID token directly.
* **Nothing from the app.** No new build, no new `google-services.json` on your side. The Android
  OAuth client and the app's signing fingerprint are already correct in the Google project —
  verified against the release key on 27 August.

## How to confirm it worked

```bash
curl -s https://tradeyar.trade-future.ir/api/mobile/v1/auth/methods | jq -r .google_client_id
```

should print the `…07nqc4h9…` id, and the probe at the top of this document should answer
`redirect_uri_mismatch` for it. Sign-in from the app then works with no further change.

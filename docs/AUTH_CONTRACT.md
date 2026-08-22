# Authentication / Session Contract

Validated against the current CoineProFx user backend on 2026-08-22.

## Existing backend truth

- `GET /user/auth/config` returns the Telegram bot username.
- `POST /user/auth/telegram` accepts Telegram's signed login payload and returns `{ token, profile }`.
- `GET /user/me` validates the bearer token and returns current user + entitlement state.
- There is no refresh-token endpoint today.
- `POST /user/auth/request-otp` and `POST /user/auth/verify-otp` are secondary email-verification steps and require an already authenticated user.
- `GET /user/subscription` is VIP-gated; Android therefore uses `/user/me` as the entitlement source of truth for all authenticated users.

## Android policy

- The bearer token is never placed in a URL.
- Telegram's signed login payload is handed from the Telegram widget directly to native code, then POSTed to `/user/auth/telegram`.
- The returned bearer token is AES/GCM encrypted with an Android Keystore key before ciphertext is stored in DataStore.
- The plaintext token exists only in process memory while the session is active.
- Every cold start reads/decrypts the token and revalidates it with `/user/me` before protected navigation is unlocked.
- Any authenticated HTTP `401` emits a global unauthorized event, clears the encrypted session, and returns the app to sign-in.
- Network failure during cold-start revalidation does not unlock protected flows; the app enters `RevalidationRequired` until retry or sign-out.
- Logout is currently local because the backend has no token-revocation endpoint. JWT expiry + server `401` remains the server-side invalidation path.

## Entitlement mapping

The app trusts backend fields only:

- `is_vip`
- `is_paid`
- `panel_approved`
- `panel_allowed`
- `panel_state`
- `plan`
- `plan_expires_at`

`hasPaidPanelAccess = is_paid && panel_allowed`.

UI gating is convenience only. Protected APIs must continue to enforce authorization server-side.

## Public repository configuration

Production/staging API URLs are not committed. Builds inject:

`-PCOINEPRO_API_BASE_URL=https://.../api/`

Without that property the debug build uses `https://example.invalid/` and cannot authenticate against production by accident.

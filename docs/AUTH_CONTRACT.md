# Authentication / Session Contract

This document records the Android-facing authentication/session contract for `BehnamJalaliCo/CoinePro-App`.

The backend endpoint shape below is the contract snapshot already consumed by this Android repository. Phase 17 cross-phase audit validates the Android implementation and its internal consistency only; it does not inspect or make claims about any out-of-scope repository.

## Existing backend contract

- `GET /user/auth/config` returns the Telegram bot username.
- `POST /user/auth/telegram` accepts Telegram's signed login payload and returns `{ token, profile }`.
- `GET /user/me` validates the bearer token and returns current user + entitlement state.
- There is no client-invented refresh-token flow.
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
- Logout is local unless/until a server token-revocation endpoint is part of the explicit contract. Server expiry/`401` remains the authoritative invalidation path.

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

Service configuration is separated by build environment and is injected outside the repository:

- debug API: `COINEPRO_DEBUG_API_BASE_URL` — default `https://debug.example.invalid/`
- staging API: `COINEPRO_STAGING_API_BASE_URL` — default `https://staging.example.invalid/`
- production API: `COINEPRO_PRODUCTION_API_BASE_URL` — default `https://production.example.invalid/`

Staging and production API base URLs are required to be different. The corresponding Firebase values also use separate `COINEPRO_DEBUG_*`, `COINEPRO_STAGING_*`, and `COINEPRO_PRODUCTION_*` namespaces.

Placeholder `.invalid` defaults deliberately cannot authenticate against a real service. Production/staging credentials and secrets are not committed to this repository.

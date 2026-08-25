# Server prompt — CoinePro-FX mobile API

Hand this to Claude Code running on the CoinePro-FX server. It is written to be pasted as-is.

---

You are working on the CoinePro-FX FastAPI backend (`src/api/`). A native Android client is being
built against this API. Everything below is additive: **do not rename, move or change the
behaviour of any existing endpoint** — five React front-ends and a Telegram bot depend on them.

Read `src/api/routes/user_panel.py` and `src/api/routes/ai_signal.py` first. Follow the patterns
already in this codebase: the same auth dependencies (`current_user`, `require_vip`), the same
`AsyncSession` handling, the same Persian user-facing error strings, the same structured logging.
Add Alembic migrations for new tables. Add tests alongside the existing suites.

Work in this order and stop after each part so it can be reviewed.

---

## Part 1 — Email-first registration and sign-in

Today `/user/auth/request-otp` and `/user/auth/verify-otp` require an already-authenticated user,
so email is only a secondary verification bolted onto a Telegram identity. The product now wants
email as the **primary** identity, with Telegram and Google beside it.

`src/api/routes/user_panel.py` (mounted at `/user`), reusing the existing `email_otp` service:

```
POST /user/auth/register/start       { email, password, full_name }
                                     -> { registration_token, otp_sent: true, cooldown_seconds }
POST /user/auth/register/verify      { registration_token, otp }
                                     -> { access_token, refresh_token, user }
POST /user/auth/login                { email, password }
                                     -> { access_token, refresh_token, user }
POST /user/auth/password/forgot      { email }            -> { sent: true }
POST /user/auth/password/reset       { reset_token, new_password } -> { reset: true }
POST /user/auth/google               { id_token }         -> { access_token, refresh_token, user }
POST /user/auth/refresh              { refresh_token }    -> { access_token, refresh_token }
POST /user/auth/logout               { refresh_token }    -> { ok: true }
```

Requirements:

- Hash passwords with **argon2id** (or bcrypt if argon2 is not already a dependency). Never store
  or log a plaintext password.
- Minimum password length 10. Reject the top few thousand common passwords if a list is available;
  do not invent complexity rules that push people toward `Passw0rd!`.
- Rate-limit per email **and** per IP: 5 registration starts per 10 minutes, 10 login attempts per
  10 minutes, then exponential backoff. Redis is already available; `is_rate_limited` exists in
  TradeYar and can be mirrored here.
- `POST /user/auth/password/forgot` and `/register/start` must return the **same** response shape
  and timing whether or not the address exists. A differing response is an account-existence
  oracle. Note `GET /user/email-exists` already leaks exactly this — either remove it or put it
  behind authentication.
- Verify the Google `id_token` against Google's public keys and check `aud` matches our client id.
  Do not trust any claim in an unverified token.
- Refresh tokens: opaque, stored hashed, single-use, rotated on every refresh, revocable. If a
  refresh token is presented twice, revoke the whole family — that is the standard signal of a
  stolen token.
- A user who registers by email and later links Telegram must end up as **one** user row, not two.
  Decide the merge rule explicitly and write it down.
- `POST /user/auth/register/verify` returns the same `user` shape `GET /user/me` returns, so the
  client has one profile model.

## Part 2 — Push notifications

Nothing on this server sends mobile push. Add FCM.

```
POST   /user/mobile/push/devices      { token, platform, app_version, locale } -> { registered: true }
DELETE /user/mobile/push/devices      { token }  -> { removed: true }
GET    /user/mobile/push/preferences  -> { preferences: { new_signals, signal_updates, price_alerts } }
PATCH  /user/mobile/push/preferences  { new_signals?, signal_updates?, price_alerts? }
                                      -> { preferences: {...} }
```

- One user may hold several devices. Registering a token that already exists for another user
  moves it — that is a device that changed hands, not a duplicate.
- Prune tokens FCM reports as unregistered rather than retrying them forever.
- Send **data-only** messages with the keys `_title`, `_body` and, when the notification concerns a
  signal, `signal_id` as a decimal string. The client already reads exactly these.
- Respect the preference flags at send time, not on the client.

## Part 3 — Price alerts

```
GET    /user/mobile/alerts              -> { items: [alert] }
POST   /user/mobile/alerts              { symbol, condition, value, trigger } -> { alert }
PATCH  /user/mobile/alerts/{alert_id}   { active } -> { alert }
DELETE /user/mobile/alerts/{alert_id}   -> { removed: true }
```

`condition` is one of `above`, `below`, `cross_up`, `cross_down`, `cross`. `trigger` is `once` or
`recurring`. An alert shape is:

```
{ id, market, symbol, condition, value, trigger, expires_at, active,
  created_at_ms, last_triggered_at_ms }
```

- Validate `symbol` against the same product scope the rest of the platform enforces, and reject
  a non-finite or non-positive `value`.
- Evaluate against the same price source the platform already trusts. Do not add a second feed.
- A `once` alert deactivates itself on firing; a `recurring` one needs a cooldown so a price
  oscillating around the level does not send a burst.
- Cap alerts per user.

## Part 4 — Notification centre

`GET /user/notifications` and `POST /user/notifications/read` already exist. The client needs the
list items to carry:

```
{ kind, title, body, data: { signal_id? }, ts, read }
```

where `ts` is **epoch seconds** (the client multiplies by 1000). Add `unread` to the list response.
If the current shape differs, extend it additively rather than changing existing keys.

## Part 5 — Market snapshot over HTTP

`WS /ws/prices` exists; the client needs a plain HTTP fallback for cold start and for when the
socket is down.

```
GET /ws/snapshot?symbols=XAUUSD,XAGUSD
-> { prices: { "XAUUSD": { symbol, price, bid, ask, ts, source } , ... }, server_time_ms }
```

`ts` is epoch **milliseconds**, and `source` must name the real upstream. The client marks a quote
stale from these two fields, so a fabricated or rounded timestamp makes a stale price look live.
Serve from the same Redis the socket serves from.

## Part 6 — Chart image analysis

New capability. The user sends a screenshot of a chart and gets back a structured setup.

```
POST /user/ai-vision/jobs        multipart: image (JPEG/PNG, ≤ 6 MB), symbol?, timeframe?
                                 -> { job_id, status: "queued", quota: { used, limit, reset_at } }
GET  /user/ai-vision/jobs/{id}   -> { status, result?, error_code?, error_message? }
```

Mirror `ai_signal.py` exactly: background task, Redis job with a TTL, daily quota, the same status
vocabulary (`queued`, `running`, `done`, `failed`, `expired`). `result` must use the **same shape**
as the AI-signal result — `direction`, `entry`, `sl`, `tp1`, `tp2`, `tp3`, `confidence`, `rr`,
`rationale`, `warnings` — so the client renders one model for both.

Requirements:

- Strip EXIF before the image goes anywhere. Chart screenshots carry GPS often enough to matter.
- Reject anything that is not a real decoded image. Do not trust the declared content type.
- The model must be able to answer "this is not a chart I can read" and have that come back as a
  `failed` job with a reason, not as an invented setup. **A confident answer from an unreadable
  image is the worst possible failure here** — it is indistinguishable from a real one on screen.
- Do not persist the image after the job completes.

## Part 7 — Streaming for the AI screen

The AI flow is currently submit-then-poll, so the client shows an indeterminate progress bar. If
real streaming is wanted, add:

```
GET /user/ai-signal/stream/{job_id}   -> text/event-stream
```

emitting `status` events as the job progresses and a final `result` event with the same payload
`result/{job_id}` returns. Keep `result/{job_id}` working — the client falls back to polling when
the stream drops, and reconnects must be idempotent.

If real token streaming is not practical, say so and skip this part. The client will keep its
honest indeterminate state rather than animating fake progress.

---

## Ground rules

- Everything above is **additive**. No existing endpoint changes shape.
- Every new endpoint returns the same error envelope the existing ones use, with Persian user-facing
  messages and machine-readable codes.
- Write an Alembic migration per new table. No implicit schema creation.
- Add tests: the auth flows, the rate limits, the account-existence timing, alert validation, and
  the vision job's refusal path.
- When you finish a part, report which endpoints exist, their exact paths, and the exact JSON keys
  they return, so the Android client can be wired against reality rather than against this document.

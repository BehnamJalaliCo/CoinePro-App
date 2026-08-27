# Server prompt — CoinePro-FX, mobile API for the CoinePro Android app

Hand this to Claude Code running on the **CoinePro-FX** server. Paste it as-is.

---

You are working on the CoinePro-FX FastAPI backend. Everything below exists to serve **one client: the
CoinePro Android app**, a native Kotlin/Compose app that is already written and already calls this
API. You are not designing an API from scratch — you are filling the gaps between what the app
already sends and what this server already answers.

CoinePro-FX is the app's **Forex platform**. Its markets are XAUUSD and XAGUSD, quoted by Finnhub,
executed through MetaTrader 5. Crypto belongs to a completely separate backend (TradeYar) with its
own users, its own credentials and its own prompt. **Never add a crypto symbol, a USDT pair or an
exchange integration here.** The app treats the two as different accounts on different servers, and
a crypto symbol appearing in a CoinePro-FX response is a bug the app is built to reject rather than
display.

## Before you write anything

1. **Create a branch**: `git checkout -b feature/android-mobile-api`
2. **Create a folder** for this work rather than scattering it through the existing tree:
   `src/api/routes/mobile/` — every new route module below goes in it, with its own `«ماژول مربوطه در سمت شما»`
   exporting one `APIRouter` that `main` mounts once. Shared helpers go in `src/api/mobile/`.
   The reason is not tidiness: five React front-ends and a Telegram bot depend on the existing
   modules, and a reviewer needs to see at a glance that nothing outside this folder changed.
3. **Read first**, then plan: `src/api/routes/user_panel.py` and `src/api/routes/ai_signal.py`.
   Follow the patterns already there — the same auth dependencies (`current_user`, `require_vip`),
   the same `AsyncSession` handling, the same Persian user-facing error strings, the same structured
   logging. Add an Alembic migration per new table. Add tests beside the existing suites.
4. **Everything is additive.** Do not rename, move or change the behaviour of any existing endpoint.

Work in order and **stop after each part** so it can be reviewed.

---

## What the app already does, so you know what you are matching

The Android client is already wired against this server's `/user/*` surface. It reads
`GET /user/me` and expects the exact field names `_profile_dict` already returns. It connects to
`WS /ws/prices`. It submits AI signal jobs and polls them. None of that changes.

Three rules the client enforces, which shape every response you write:

- **The client never invents state.** If a field is missing it renders an em dash, not a zero. If a
  quote has no timestamp it is drawn as stale. If an execution is queued it is never drawn as open.
  So a field you cannot fill honestly should be **absent or null**, never a filler value.
- **Server text is shown verbatim.** Error messages, refusal reasons and AI rationale are rendered
  as written and never paraphrased. Write them as a person should read them, in Persian.
- **Numbers stay Latin-digit and dot-decimal.** Do not localise digits in JSON.

---

## Part 1 — Email-first registration and sign-in

Today `/user/auth/request-otp` and `/user/auth/verify-otp` require an already-authenticated user, so
email is a secondary verification bolted onto a Telegram identity. The product now wants **email as
the primary identity**, with Telegram and Google beside it.

In `src/api/routes/mobile/auth.py`, reusing the existing `email_otp` service:

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

- Hash with **argon2id** (bcrypt if argon2 is not already a dependency). Never store or log a
  plaintext password.
- Minimum length 10. Reject the top few thousand common passwords if a list is available. Do not
  invent complexity rules that push people toward `Passw0rd!`.
- Rate-limit per email **and** per IP: 5 registration starts per 10 minutes, 10 login attempts per
  10 minutes, then exponential backoff. Redis is already available.
- `/register/start` and `/password/forgot` must return the **same shape and the same timing**
  whether or not the address exists. A differing response is an account-existence oracle. Note that
  `GET /user/email-exists` already leaks exactly this — remove it or put it behind auth.
- Verify the Google `id_token` against Google's public keys and check `aud` matches our client id.
  Do not trust any claim in an unverified token.
- Refresh tokens: opaque, stored hashed, single-use, rotated on every refresh, revocable. If one is
  presented twice, revoke the whole family — that is the standard signal of theft.
- A user who registers by email and later links Telegram must end up as **one** row. Decide the
  merge rule explicitly and write it down.
- `/register/verify` returns the same `user` shape `GET /user/me` returns, so the client keeps one
  profile model.

## Part 2 — Level-1 KYC

`POST /user/kyc` already exists and auto-approves. The app asks for level 1 only — enough to know a
real person is behind the account, not a document pipeline.

```
GET  /user/mobile/kyc          -> { level, status, required_fields: [...], submitted_at, reviewed_at }
POST /user/mobile/kyc/level1   { full_name, national_id, birth_date, phone } -> { level, status }
```

`status` is one of `not_started`, `pending`, `approved`, `rejected`. If it is `rejected`, include a
`reason` the app can show verbatim. Do not return `approved` from the request handler unless the
approval genuinely happened — the app gates features on this.

## Part 3 — Home briefing

The app's home screen opens with a short briefing from an assistant the product calls **رَصد**. It
is the first thing a reader sees, so it is also the easiest place to mislead them.

```
GET /user/mobile/briefing -> { body, generated_at, streaming: false }
     404 / 204 when there is nothing to say
```

- `body` is **one short paragraph of plain Persian text** with the figures inline. No markdown, no
  bullet list, no headings — the client renders it as a paragraph and marks up the numbers itself.
- `generated_at` is required and is epoch seconds. The client shows the age beside it, and an
  undated market claim is indistinguishable from a live one.
- Return nothing rather than filler. The client has a resting state that says so honestly; a
  briefing that says "the market is calm" because no analysis ran is worse than silence.
- Every number in `body` must come from data this server actually holds.

## Part 4 — Portfolio and holdings

The home screen shows a total balance and the positions behind it. Today the app has no endpoint for
either and renders an em dash.

```
GET /user/mobile/portfolio
-> {
     total: { amount, currency },
     change: { amount, percent, period: "today" },
     holdings: [
       { symbol, display_name, quantity, quantity_unit, value, change_percent }
     ],
     as_of
   }
```

- `as_of` is epoch seconds and is required.
- `quantity` and `value` are **numbers**, not preformatted strings; the client formats them.
- If the MT5 bridge cannot report an account, return `total: null` with the rest absent. Do not
  return zero — the client renders a dash for null and a real balance for zero, and the difference
  matters.

## Part 5 — Push notifications

Nothing here sends mobile push. Add FCM.

```
POST   /user/mobile/push/devices      { token, platform, app_version, locale } -> { registered: true }
DELETE /user/mobile/push/devices      { token }  -> { removed: true }
GET    /user/mobile/push/preferences  -> { preferences: { new_signals, signal_updates, price_alerts } }
PATCH  /user/mobile/push/preferences  { new_signals?, signal_updates?, price_alerts? }
                                      -> { preferences: {...} }
```

- One user may hold several devices. A token that already belongs to another user **moves** — that
  is a device that changed hands, not a duplicate.
- Prune tokens FCM reports as unregistered rather than retrying forever.
- Send **data-only** messages with keys `_title`, `_body`, and `signal_id` as a decimal string when
  the notification concerns a signal. The client already reads exactly these.
- Respect the preference flags at send time, not on the client.

## Part 6 — Price alerts

```
GET    /user/mobile/alerts              -> { items: [alert] }
POST   /user/mobile/alerts              { symbol, condition, value, trigger } -> { alert }
PATCH  /user/mobile/alerts/{alert_id}   { active } -> { alert }
DELETE /user/mobile/alerts/{alert_id}   -> { removed: true }
```

`condition` ∈ `above`, `below`, `cross_up`, `cross_down`, `cross`. `trigger` ∈ `once`, `recurring`.

```
{ id, market, symbol, condition, value, trigger, expires_at, active,
  created_at_ms, last_triggered_at_ms }
```

- Validate `symbol` against this platform's scope — XAUUSD and XAGUSD. Reject a non-finite or
  non-positive `value`.
- Evaluate against the price source the platform already trusts. Do not add a second feed.
- A `once` alert deactivates on firing; a `recurring` one needs a cooldown so a price oscillating
  around the level does not send a burst.
- Cap alerts per user.

## Part 7 — Notification centre

`GET /user/notifications` and `POST /user/notifications/read` already exist. The client needs each
item to carry:

```
{ kind, title, body, data: { signal_id? }, ts, read }
```

`ts` is **epoch seconds** (the client multiplies by 1000). Add `unread` to the list response. Extend
additively rather than changing existing keys.

## Part 8 — Market snapshot over HTTP

`WS /ws/prices` exists; the client needs a plain HTTP fallback for cold start and for when the socket
is down.

```
GET /ws/snapshot?symbols=XAUUSD,XAGUSD
-> { prices: { "XAUUSD": { symbol, price, bid, ask, ts, source } , ... }, server_time_ms }
```

`ts` is epoch **milliseconds** and `source` must name the real upstream. The client decides staleness
from those two fields, so a fabricated or rounded timestamp makes a stale price look live. Serve from
the same Redis the socket serves from.

## Part 9 — Chart image analysis

New capability. The user sends a screenshot of a chart and gets back a structured setup.

```
POST /user/ai-vision/jobs        multipart: image (JPEG/PNG, ≤ 6 MB), symbol?, timeframe?
                                 -> { job_id, status: "queued", quota: { used, limit, reset_at } }
GET  /user/ai-vision/jobs/{id}   -> { status, result?, error_code?, error_message? }
```

Mirror `«ماژول مربوطه در سمت شما»` exactly: background task, Redis job with a TTL, daily quota, the same status
vocabulary (`queued`, `running`, `done`, `failed`, `expired`). `result` uses the **same shape** as
the AI-signal result — `direction`, `entry`, `sl`, `tp1`, `tp2`, `tp3`, `confidence`, `rr`,
`rationale`, `warnings` — so the client renders one model for both.

- Strip EXIF before the image goes anywhere. Chart screenshots carry GPS often enough to matter.
- Reject anything that is not a real decoded image. Do not trust the declared content type.
- The model must be able to answer "this is not a chart I can read", and that must come back as a
  `failed` job with a reason, not as an invented setup. **A confident answer from an unreadable
  image is the worst failure here** — on screen it is indistinguishable from a real one.
- Do not persist the image after the job completes.

## Part 10 — Streaming for the AI screen

The AI flow is submit-then-poll, so the client shows an honest indeterminate bar. If real streaming
is wanted:

```
GET /user/ai-signal/stream/{job_id}   -> text/event-stream
```

emitting `status` events as the job progresses and a final `result` event with the same payload
`result/{job_id}` returns. Keep `result/{job_id}` working — the client falls back to polling when the
stream drops, and reconnects must be idempotent.

If real token streaming is not practical, **say so and skip this part**. The client keeps its honest
indeterminate state rather than animating fake progress.

---

## Ground rules

- Everything above is **additive**. No existing endpoint changes shape.
- Every new endpoint returns the same error envelope the existing ones use, with Persian user-facing
  messages and machine-readable codes.
- One Alembic migration per new table. No implicit schema creation.
- Tests for: the auth flows, the rate limits, the account-existence timing, alert validation, the
  vision job's refusal path, and that no crypto symbol can enter any response.
- When you finish a part, report **which endpoints exist, their exact paths, and the exact JSON keys
  they return**, so the Android client is wired against reality rather than against this document.

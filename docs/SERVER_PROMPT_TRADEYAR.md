# Server prompt — TradeYar, mobile API for the CoinePro Android app

Hand this to Claude Code running on the **TradeYar** server. Paste it as-is.

---

You are working on the TradeYar FastAPI backend. Everything below exists to serve **one client: the
CoinePro Android app**, a native Kotlin/Compose app that already exists and already knows the shape
it expects. You are not designing an API from scratch — you are adding the mobile surface that this
server does not yet have.

TradeYar is the app's **Crypto platform**. Its markets are USDT pairs quoted by LBank over its
realtime socket, executed through LBank. Forex and metals belong to a completely separate backend
(CoinePro-FX) with its own users, its own credentials and its own prompt. **Never add XAUUSD,
XAGUSD, a currency pair or a MetaTrader integration here.** The app treats the two platforms as
different accounts on different servers and filters every incoming quote by market type — a forex
symbol in a TradeYar response is dropped by the client rather than displayed.

The app currently ships this crypto list: `BTCUSDT ETHUSDT SOLUSDT BNBUSDT XRPUSDT ADAUSDT DOGEUSDT
TRXUSDT`. Tell me if TradeYar's tradable set differs, rather than silently answering for symbols the
app will never ask about.

## Before you write anything

1. **Create a branch**: `git checkout -b feature/android-mobile-api`
2. **Create a folder** for this work rather than threading it through the existing routers:
   `app/api/routers/mobile/` with its own `__init__.py` exporting one `APIRouter`, mounted once
   under the prefix `/api/mobile/v1`. Shared helpers go in `app/api/mobile/`. The reason is not
   tidiness: a Next.js front-end, a Telegram bot and an admin surface depend on the existing
   routers, and a reviewer needs to see at a glance that nothing outside this folder changed.
3. **Read first**, then plan. The pieces that matter:
   - `app/api/routers/user/` — mounted at `/api/user/v1`, holds `auth.py`, `register.py`,
     `account.py`, `signals.py`, `notifications.py`, `positions.py`, `settings.py`
   - `app/api/auth/router.py` — mounted at `/auth`, holds `/login`, `/logout`, `/me`, `/refresh`
   - `app/api/routers/public/` — `tickers.py`, `stream.py`, `news.py`, `watchlist.py`, `alerts.py`
   - `app/api/routers/signals.py` — `/signals`
   - `app/data/` — the LBank kline collector, candle cache and market fallback
   Follow the patterns already there: the same auth dependencies from `app/api/routers/user/deps.py`,
   the same rate limiter in `app/api/rate_limiter.py`, the same schema style in `app/api/schemas/`,
   the same Persian user-facing strings. Add an Alembic migration per new table under `alembic/`.
4. **Everything is additive.** Do not rename, move or change the behaviour of any existing endpoint.

Work in order and **stop after each part** so it can be reviewed.

---

## What the app expects, so you know what you are matching

Three rules the Android client enforces, which shape every response you write:

- **The client never invents state.** A missing field renders as an em dash, not a zero. A quote
  with no timestamp is drawn as stale. A queued order is never drawn as filled. So a field you
  cannot fill honestly should be **absent or null**, never a filler value.
- **Server text is shown verbatim.** Error messages, refusal reasons and AI rationale are rendered
  as written and never paraphrased. Write them as a person should read them, in Persian.
- **Numbers stay Latin-digit and dot-decimal** in JSON, and prices stay numbers rather than
  preformatted strings. The client formats and isolates them for right-to-left layout itself.

Wherever an endpoint below matches something `/api/user/v1` already does, **reuse the existing
service layer** and expose the mobile shape on top of it. Do not fork the business logic.

---

## Part 1 — Email-first registration and sign-in

Registration today runs through `app/api/routers/user/register.py` — `/start`,
`/send-otp-telegram`, `/verify-otp`, `/consent`, `/uid`, `/complete-session` — which is a
Telegram-first flow. The product now wants **email as the primary identity**, with Telegram and
Google beside it. Keep the Telegram path working for everyone already on it.

```
POST /api/mobile/v1/auth/register/start   { email, password, full_name }
                                          -> { registration_token, otp_sent: true, cooldown_seconds }
POST /api/mobile/v1/auth/register/verify  { registration_token, otp }
                                          -> { access_token, refresh_token, user }
POST /api/mobile/v1/auth/login            { email, password }
                                          -> { access_token, refresh_token, user }
POST /api/mobile/v1/auth/password/forgot  { email }            -> { sent: true }
POST /api/mobile/v1/auth/password/reset   { reset_token, new_password } -> { reset: true }
POST /api/mobile/v1/auth/google           { id_token }         -> { access_token, refresh_token, user }
POST /api/mobile/v1/auth/refresh          { refresh_token }    -> { access_token, refresh_token }
POST /api/mobile/v1/auth/logout           { refresh_token }    -> { ok: true }
```

Requirements:

- Email OTP delivery does not exist here yet — Telegram does. Add it, and keep the same
  `cooldown_seconds` contract the Telegram path already uses so the client has one timer model.
- Hash with **argon2id** (bcrypt if argon2 is not already a dependency). Never store or log a
  plaintext password.
- Minimum length 10. Reject the top few thousand common passwords if a list is available. Do not
  invent complexity rules that push people toward `Passw0rd!`.
- Rate-limit per email **and** per IP through the existing `app/api/rate_limiter.py`: 5 registration
  starts per 10 minutes, 10 login attempts per 10 minutes, then exponential backoff.
- `/register/start` and `/password/forgot` must return the **same shape and the same timing**
  whether or not the address exists. A differing response is an account-existence oracle. Audit the
  existing registration endpoints for the same leak while you are there.
- Verify the Google `id_token` against Google's public keys and check `aud` matches our client id.
  Do not trust any claim in an unverified token.
- Refresh tokens: opaque, stored hashed, single-use, rotated on every refresh, revocable. If one is
  presented twice, revoke the whole family — that is the standard signal of theft. Coordinate with
  `app/api/auth/token_blacklist.py` rather than adding a second mechanism.
- A user who registers by email and later links Telegram must end up as **one** row. `register.py`
  already has a UID/link concept; decide the merge rule explicitly against it and write it down.
- `user` in these responses is the same shape `/api/user/v1/auth/me` returns, so the client keeps one
  profile model. If that shape is not stable, tell me before changing it.

## Part 2 — Level-1 KYC

The app asks for level 1 only — enough to know a real person is behind the account, not a document
pipeline. If TradeYar already has a consent or verification concept in `register.py`, build on it.

```
GET  /api/mobile/v1/kyc          -> { level, status, required_fields: [...], submitted_at, reviewed_at }
POST /api/mobile/v1/kyc/level1   { full_name, national_id, birth_date, phone } -> { level, status }
```

`status` ∈ `not_started`, `pending`, `approved`, `rejected`. When `rejected`, include a `reason` the
app shows verbatim. Do not return `approved` unless the approval genuinely happened — the app gates
features on this.

## Part 3 — Market data for the app

The client needs a realtime socket and an HTTP fallback for cold start. `app/api/routers/public/`
already has `tickers.py` and `stream.py`; expose them in the shape the app parses.

```
WS  /api/mobile/v1/ws/prices?symbols=BTCUSDT,ETHUSDT
    -> frames of { symbol, price, bid, ask, ts, source, change_percent }

GET /api/mobile/v1/ws/snapshot?symbols=BTCUSDT,ETHUSDT
    -> { prices: { "BTCUSDT": { symbol, price, bid, ask, ts, source, change_percent }, ... },
         server_time_ms }
```

- `ts` is epoch **milliseconds** and `source` must name the real upstream (`lbank`). The client
  decides staleness from those two fields, so a fabricated or rounded timestamp makes a stale price
  look live.
- `change_percent` is the 24-hour move. If LBank has not supplied it, send **null** — the client
  renders "stale" or a dash rather than drawing a flat zero, and a fabricated 0.00 reads as "this
  market did not move".
- Serve the snapshot from the same cache the socket serves from, not a second fetch.
- Reject any symbol outside this platform's crypto scope rather than proxying it.

## Part 4 — Signals

`app/api/routers/user/signals.py` exposes `/recent` and `app/api/routers/signals.py` exposes
`/signals`. The app needs a list it can filter and a detail view.

```
GET /api/mobile/v1/signals?status=active|recent|closed&limit=&cursor=
-> { items: [signal], next_cursor, membership_required: false }

GET /api/mobile/v1/signals/{id} -> { signal }
```

A signal:

```
{ id, market: "crypto", symbol, direction: "buy"|"sell"|"neutral", status,
  timeframe, strategy, confidence,
  entry, entry_zone: { low, high }, stop_loss,
  targets: [ { level, price, hit } ],
  risk_reward_tp1, rationale,
  score_breakdown: { technical, pattern, ml },
  current_quote: { price, ts, is_stale },
  result: { pnl_usd, source },
  created_at, closed_at }
```

- `market` is always `"crypto"` here. The client drops anything else.
- Every price is a number or **null**. Null means "not set" and renders as a dash; zero means the
  price is zero.
- When a subscription is required, return `membership_required: true` with an empty `items` rather
  than a 403 — the app has a dedicated panel for that state.

## Part 5 — Home briefing

The app's home screen opens with a short briefing from an assistant the product calls **رَصد**. It is
the first thing a reader sees, so it is also the easiest place to mislead them.

```
GET /api/mobile/v1/briefing -> { body, generated_at, streaming: false }
     204 when there is nothing to say
```

- `body` is **one short paragraph of plain Persian text** with the figures inline. No markdown, no
  bullets, no headings — the client renders a paragraph and marks up the numbers itself.
- `generated_at` is required, epoch seconds. The client shows the age beside it, and an undated
  market claim is indistinguishable from a live one.
- Return nothing rather than filler. The client has a resting state that says so honestly; a
  briefing that says "the market is calm" because no analysis ran is worse than silence.
- Every number in `body` must come from data this server actually holds.

## Part 6 — Portfolio and holdings

The home screen shows a total balance and the positions behind it. `app/api/routers/user/account.py`
has `/balance` and `positions.py` has `/active` — compose them into one call so the screen is not
three round trips.

```
GET /api/mobile/v1/portfolio
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
- `display_name` is the Persian asset name (`بیت‌کوین`), `symbol` the wire symbol (`BTCUSDT`).
- `quantity` and `value` are **numbers**. The client formats them.
- If LBank cannot report an account, return `total: null` with the rest absent. Do not return zero —
  the client renders a dash for null and a real balance for zero, and the difference matters.

## Part 7 — Execution

The app can send one signal to LBank. It never builds a manual order: symbol, direction, entry, stop
and targets all come from the signal, and the only things the app contributes are quantity and an
explicit confirmation.

```
POST /api/mobile/v1/executions        { signal_id, quantity, client_request_id } -> { execution }
GET  /api/mobile/v1/executions        -> { items: [execution] }
POST /api/mobile/v1/executions/{id}/close -> { execution }
```

```
{ id, signal_id, venue: "lbank", side, product, quantity, status,
  provider_order_id, error_message, can_request_close, created_at, closed_at }
```

- `status` ∈ `queued`, `submitted`, `open`, `close_requested`, `closed`, `failed`, `cancelled`,
  `unknown`. **Only `open` and `closed` may mean the exchange confirmed something.** The client
  colours everything else as pending on purpose: a reader who believes they hold a position when
  they do not will size the next one wrongly.
- `client_request_id` makes the call idempotent. The same id must never open two positions.
- `quantity` echoes back **as the exchange accepted it**, as a string. The client shows it as text
  rather than re-rounding, so a value you normalise silently is the value the reader will believe.
- `failed` must carry `error_message` in the exchange's own terms. "It failed" with no cause sends a
  reader back to repeat the same rejected order.

## Part 8 — Exchange credentials

`ConnectionsScreen` in the app collects an LBank API key and secret with a **spot** or **futures**
permission, and never asks for withdrawal.

```
GET    /api/mobile/v1/venues/lbank        -> { configured, connected, key_hint, permission, status }
POST   /api/mobile/v1/venues/lbank        { api_key, api_secret, permission } -> { ...same }
DELETE /api/mobile/v1/venues/lbank        -> { removed: true }
```

- **`configured` and `connected` are different things and the app depends on the difference.**
  `configured` means credentials are stored. `connected` means LBank verified them. Never set
  `connected` from a successful save; set it only from a real verification call.
- `key_hint` is the last four characters only. Never return the key or the secret.
- Encrypt the secret at rest. It never appears in a log, an error or a response.

## Part 9 — Push notifications

```
POST   /api/mobile/v1/push/devices      { token, platform, app_version, locale } -> { registered: true }
DELETE /api/mobile/v1/push/devices      { token }  -> { removed: true }
GET    /api/mobile/v1/push/preferences  -> { preferences: { new_signals, signal_updates, price_alerts } }
PATCH  /api/mobile/v1/push/preferences  { new_signals?, signal_updates?, price_alerts? }
                                        -> { preferences: {...} }
```

- One user may hold several devices. A token that already belongs to another user **moves** — that
  is a device that changed hands, not a duplicate.
- Prune tokens FCM reports as unregistered rather than retrying forever.
- Send **data-only** messages with keys `_title`, `_body`, and `signal_id` as a decimal string when
  the notification concerns a signal. The client already reads exactly these.
- Respect the preference flags at send time, not on the client.

## Part 10 — Price alerts and the notification centre

`app/api/routers/public/alerts.py` exists; the app needs a per-user version.

```
GET    /api/mobile/v1/alerts              -> { items: [alert] }
POST   /api/mobile/v1/alerts              { symbol, condition, value, trigger } -> { alert }
PATCH  /api/mobile/v1/alerts/{alert_id}   { active } -> { alert }
DELETE /api/mobile/v1/alerts/{alert_id}   -> { removed: true }

GET  /api/mobile/v1/notifications         -> { items: [...], unread }
POST /api/mobile/v1/notifications/read    { ids: [...] } -> { ok: true }
```

`condition` ∈ `above`, `below`, `cross_up`, `cross_down`, `cross`. `trigger` ∈ `once`, `recurring`.

```
{ id, market: "crypto", symbol, condition, value, trigger, expires_at, active,
  created_at_ms, last_triggered_at_ms }

{ kind, title, body, data: { signal_id? }, ts, read }     # ts is epoch SECONDS
```

- Validate `symbol` against this platform's crypto scope. Reject a non-finite or non-positive value.
- Evaluate against the LBank feed the platform already trusts. Do not add a second feed.
- A `once` alert deactivates on firing; a `recurring` one needs a cooldown so a price oscillating
  around the level does not send a burst.
- Cap alerts per user.
- Reuse `app/api/routers/user/notifications.py` for the read-marking logic.

## Part 11 — AI signal generation

The app's AI screen asks for a setup and shows the evidence behind it. TradeYar has `/jobs`,
`/jobs/{job_id}/result` and `/run` under the user router — expose the mobile contract over them.

```
GET  /api/mobile/v1/ai/quota                  -> { used, limit, remaining, reset_at }
POST /api/mobile/v1/ai/generate               { symbol, timeframe, trade_style?, risk_appetite?,
                                                direction_bias?, min_rr?, risk_percent?, balance? }
                                              -> { job_id, status: "queued", quota: {...} }
GET  /api/mobile/v1/ai/result/{job_id}        -> { status, result?, error_code?, error_message? }
```

`status` ∈ `queued`, `running`, `done`, `failed`, `expired`. `result`:

```
{ signal_id, direction, entry, sl, tp1, tp2, tp3, confidence, rr, lot, strategy,
  rationale, warnings: [...],
  snapshot: { rsi_14, atr_14, macd, ema_20, ema_50, ema_200 },
  candles: [ { t, o, h, l, c } ] }
```

- `snapshot` and `candles` are what make the verdict checkable rather than trusted. Send the readings
  the model actually received; a value you could not compute is **null**, not zero. The app renders
  the candles with the levels drawn across them.
- The optional request fields are genuinely optional. An absent one means "you decide" and the app
  says so to the reader — do not treat absence as a default you silently substitute.
- `warnings` are shown verbatim and separately from `rationale`. Do not fold them together.

## Part 12 — Chart image analysis

```
POST /api/mobile/v1/ai/vision/jobs        multipart: image (JPEG/PNG, ≤ 6 MB), symbol?, timeframe?
                                          -> { job_id, status: "queued", quota: {...} }
GET  /api/mobile/v1/ai/vision/jobs/{id}   -> { status, result?, error_code?, error_message? }
```

`result` uses the **same shape** as the AI-signal result so the client renders one model for both,
plus an `assessment` of `actionable`, `low_confidence`, `unknown` or `unsupported`.

- Strip EXIF before the image goes anywhere. Chart screenshots carry GPS often enough to matter.
- Reject anything that is not a real decoded image. Do not trust the declared content type.
- The model must be able to answer "this is not a chart I can read", and that must come back as
  `unsupported` or a `failed` job with a reason — never as an invented setup. **A confident answer
  from an unreadable image is the worst failure here**; on screen it is indistinguishable from a
  real one.
- Do not persist the image after the job completes.

## Part 13 — Streaming for the AI screen

The AI flow is submit-then-poll, so the client shows an honest indeterminate bar. If real streaming
is wanted:

```
GET /api/mobile/v1/ai/stream/{job_id}   -> text/event-stream
```

emitting `status` events as the job progresses and a final `result` event with the same payload the
result endpoint returns. Keep the polling endpoint working — the client falls back to it when the
stream drops, and reconnects must be idempotent.

If real token streaming is not practical, **say so and skip this part**. The client keeps its honest
indeterminate state rather than animating fake progress.

---

## Ground rules

- Everything above is **additive**. No existing endpoint changes shape.
- Every new endpoint returns one consistent error envelope with Persian user-facing messages and
  machine-readable codes. If the existing routers already have one, use it.
- One Alembic migration per new table. No implicit schema creation.
- Tests for: the auth flows, the rate limits, the account-existence timing, execution idempotency
  under a repeated `client_request_id`, the configured-versus-connected distinction, alert
  validation, the vision job's refusal path, and that no forex symbol can enter any response.
- When you finish a part, report **which endpoints exist, their exact paths, and the exact JSON keys
  they return**, so the Android client is wired against reality rather than against this document.

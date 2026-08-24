# Backend integration analysis — CoineProFx and TradeYar

Written after reading both platforms at commit `92076ec` (CoineProFx) and `eb0a694` (TradeYar).
Answers the central question: **does the Android app need its own server?**

---

## Answer

**No new platform server. The Android app was already built against CoineProFx's `/user/*` API.**
What it needs is a small set of additions to that API, plus one product decision about execution.

The evidence is not circumstantial. `GET /user/auth/config` on CoineProFx returns
`{"bot_username": …}`, and the Android `AuthConfigDto` reads `botUsername` through Gson's
`LOWER_CASE_WITH_UNDERSCORES` policy — the exact same wire name. The profile payload matches
field for field:

| Android `UserProfile` | CoineProFx `_profile_dict` |
| --- | --- |
| `telegramId` `name` `username` `phone` `email` | `telegram_id` `name` `username` `phone` `email` |
| `emailVerified` `kycStatus` `isVip` `isPaid` | `email_verified` `kyc_status` `is_vip` `is_paid` |
| `panelApproved` `panelAllowed` `panelState` | `panel_approved` `panel_allowed` `panel_state` |
| `plan` `planExpiresAt` `disclaimerAccepted` | `plan` `plan_expires_at` `disclaimer_accepted` |

Fourteen of fourteen. The Android model is a transcription of that payload. `WSS /ws/prices` is
also an exact match, and the AI-signal job shape (`quota` → `generate` → `result/{job_id}`) is the
same three-step contract the Android `AiSignalGateway` implements.

## The two platforms are different products

| | **CoineProFx** | **TradeYar** |
| --- | --- | --- |
| Market | Forex only — the README states zero crypto on any surface | Crypto |
| Stack | FastAPI · TimescaleDB · Redis · Celery · React ×5 · MT5 EA bridge | FastAPI · TimescaleDB · Redis · Celery · Next.js |
| Resolved endpoints | ~501 | ~62 routers |
| Primary identity | Telegram (`/user/auth/telegram`, `/auth/webapp`) | Email + phone + name |
| Email | Secondary verification on an already-authenticated user | **Primary registration identity** |
| KYC | Level 1, auto-approved on submit | Consent step only |
| Execution model | **Copy trading** — link an MT5 account, engine mirrors | **None** — affiliate/UID; users trade on their own exchange, positions are read-only |
| Admin | Large: `panel.py` 70 ops, `seo_admin` 48, `admin_instagram` 46, CRM, employees, academy | Large: 26 admin routers |

They have separate databases, separate user tables and separate auth. There is no shared identity
between them today.

## What the Android app expects that nobody implements

The client calls 32 endpoints. Auth, profile and the price socket line up exactly. These do not:

**Per-signal execution — the whole `user/signals/execution/*` family.**
The Android app models: pick one signal → choose venue and quantity → execute that single trade →
track and close it. **CoineProFx does not work that way and neither does TradeYar.** CoineProFx's
model is copy trading: the user links a broker account once
(`POST /user/account/link`, gated on verified email plus accepted disclaimer), configures it
(`/user/copy-config`, `/user/copy-symbols`), starts the service (`/user/copy-svc`), and the engine
mirrors signals automatically. Closing is per-position or all-at-once
(`/user/copy/close-position`, `/user/copy/close-all`, `/user/copy/stop`).

Eight Android endpoints and four screens are built against a contract that exists nowhere. This is
the one finding that needs a product decision rather than code.

**Missing outright on both backends:**

- FCM push — device registration, preferences, notification centre (`user/signals/mobile/push/*`)
- Price alerts — create, toggle, delete (`user/signals/mobile/alerts`)
- AI Vision — chart-image jobs (`user/ai/vision/jobs`)
- News feed — CoineProFx has `GET /user/economic-calendar` but no news endpoint
- `GET ws/snapshot` — the HTTP fallback beside the socket; nearest existing is
  `GET /public/prices/live`

**Present but under a different path** (rename on one side, no new capability needed):

| Android calls | CoineProFx serves |
| --- | --- |
| `user/signals`, `user/signals/{id}` | `/signals`, `/signals/{signal_id}`, `/public/signals/active` |
| `user/signals/ai/quota` · `/jobs` · `/jobs/{id}` | `/user/ai-signal/quota` · `/generate` · `/result/{job_id}` |
| `user/ai/assistant/messages` | `/user/ai/chat` |
| `user/signals/mobile/notifications` | `/user/notifications`, `/user/notifications/read` |

## What already exists that the roadmap assumed had to be built

Three of the items on the Android backlog are done server-side:

- **Email registration.** TradeYar's `/register/start` takes email + phone + full name, sends an
  email OTP, then `verify-otp` → `consent` → `uid`, rate-limited per email. CoineProFx has the
  `email_otp` service and `/user/email-exists`. The pieces exist; what the product wants is to
  make email *primary* on CoineProFx, where it is currently a secondary verification bolted onto a
  Telegram identity.
- **KYC level 1.** `POST /user/kyc` takes name, country, date of birth and nationality and
  auto-approves — precisely the light verification the product asked for. No new work.
- **Admin panel.** CoineProFx already has a far larger admin surface than an in-app panel could
  reasonably reproduce. The in-app five-tap panel should be a focused mobile view onto a subset of
  it, not a reimplementation.

## Recommended architecture

**Point the Android app at CoineProFx and add the gaps there.** Reasons: the auth contract,
profile payload and price socket already match; entitlements (`panel_approved`, `is_vip`,
`is_paid`, `plan`) that the Android app currently computes and ignores are real gates on that
server; and copy trading — the product's actual execution model — lives there.

Concretely:

1. **Adopt copy trading in the Android app** and retire the per-signal execution screens, or keep
   them dark until someone builds that contract. Do not build per-signal execution on the server
   just to satisfy screens that were written speculatively.
2. **Add to CoineProFx**, under `/user/*`: FCM device registration and push preferences, price
   alerts, a news endpoint beside the economic calendar, and an `ws/snapshot`-equivalent HTTP
   fallback. AI Vision only if it is still wanted.
3. **Align the paths** — cheaper to rename on the Android side than to add aliases on a server
   with 501 live endpoints and five frontends depending on it.
4. **Make email primary** on CoineProFx's user auth, keeping Telegram as the secondary path the
   product asked for. TradeYar's `/register/*` flow is the working reference.
5. **Crypto is a later phase.** CoineProFx is Forex-only by design, so the Android app's `*USDT`
   support has no backend on that side. Either drop crypto from v1, or add a market dimension
   later that routes crypto to TradeYar — which would first need user execution built there, since
   TradeYar users do not execute through the platform at all today.

## Open questions for the owner

1. **Copy trading or per-signal execution?** This decides four Android screens and eight
   endpoints. The server says copy trading.
2. **Crypto in v1?** If yes, TradeYar needs a user-execution path it does not have, and the two
   user identities need reconciling.
3. **One account across both platforms, or two?** Today they are entirely separate systems.
4. **AI Vision** — still wanted? Nothing on either backend serves it.

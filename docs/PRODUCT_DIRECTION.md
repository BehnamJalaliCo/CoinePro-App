# Product direction — decisions and what they cost

Settled with the owner after reading both platforms. Supersedes any earlier assumption in
`SESSION_HANDOFF.md`. Read `BACKEND_INTEGRATION_ANALYSIS.md` first for the evidence behind the
backend facts referenced here.

---

## Decisions

1. **Copy trading and signalling are separate products.** Copy trading is not a replacement for
   per-signal execution; both exist and neither substitutes for the other.
2. **Crypto ships in v1.** CoineProFx is Forex-only by design, so crypto comes from TradeYar.
3. **Accounts are per-platform.** A CoineProFx user and a TradeYar user are different people with
   different credentials. No unified identity.
4. **AI gets its own screen** — animated, streaming, generating signals *and* analysing chart
   screenshots into an entry / stop / take-profit setup. Built as a self-contained, extensible
   section.

## The consequence nobody has costed yet

Decisions 2 and 3 together mean **the app must speak to two backends under two identities at the
same time**. The current code cannot: there is exactly one base URL and exactly one token.

- `app/build.gradle.kts` bakes a single `API_BASE_URL` per build type.
- `AppModule.retrofit()` builds one `Retrofit` from it, and every gateway takes that one instance.
- `SessionMemory` holds one `AtomicReference<String?>`, and `KeystoreSessionTokenStorage` persists
  one token under one key.
- `SessionController` models one session, so `SessionState.SignedIn` means "signed in" with no
  notion of *where*.

This is the largest single piece of work the decisions imply, and it sits underneath everything
else — auth, signals, execution, copy trading and the AI screen all ride on it. It has to land
before the redesign, or every screen gets rewritten twice.

**Shape to build:** a `MarketPlatform` dimension (`FOREX` → CoineProFx, `CRYPTO` → TradeYar)
carried through DI, with per-platform Retrofit instances, per-platform token storage, and a
session model that can be signed into one, both, or neither. The bottom navigation then filters by
whichever platforms the user holds an account on.

## What already exists server-side — do not rebuild it

Verified in the source, not inferred:

- **AI signal generation is already what the AI screen needs, and richer than the app models.**
  `POST /user/ai-signal/generate` takes symbol, timeframe, lot, risk percent, balance, trade style
  (scalp/intraday/swing), risk appetite, direction bias and minimum R:R. It returns a `job_id`,
  runs in the background, and `GET /user/ai-signal/result/{job_id}` returns entry, stop, `tp1`,
  `tp2`, `tp3`, direction, confidence, R:R, lot, rationale, strategy and warnings — plus a full
  technical snapshot (EMA 20/50/200, RSI, ATR, MACD, Bollinger bands, 20-bar swing high/low) and
  `recent_candles`. The Android request model sends three of those nine inputs and the response
  model drops the entire snapshot.
- **Daily quota** is enforced in Redis with a 26-hour expiry and returned alongside every result.
- **Email registration with OTP**, **auto-approving level-1 KYC**, and a **disclaimer gate** all
  exist.

## What must be built server-side

- **Chart-image analysis.** Nothing on either platform accepts an image. Confirmed: the only
  matches for "vision" in CoineProFx are the word *division*. This is a new multimodal endpoint.
- **Token streaming for AI.** The existing AI flow is submit-then-poll, and the only
  `StreamingResponse` in the codebase serves CSV exports. The animated streaming screen needs
  either a real SSE endpoint or an honest client-side progressive reveal over the existing poll —
  and if it is the latter, it must not be presented as live model output.
- **Push (FCM), price alerts, a news feed**, and an HTTP `snapshot` fallback beside `/ws/prices`.
- **Per-signal execution**, if signalling is to stay executable — it exists on neither platform.
- **Crypto user execution on TradeYar**, which today has none: its users trade on their own
  exchange accounts bound by UID, and the platform only reads positions back.

## Suggested order

1. **Multi-platform foundation** — the two-backend, two-identity refactor above.
2. **Auth** — email-primary sign-up with Telegram and Google beneath it, per platform.
3. **AI screen** — wire the real generate/poll contract first, with the full snapshot the server
   already returns; add image analysis and streaming as the server side lands.
4. **Signals and copy trading** as separate sections.
5. **Redesign** across the settled screen inventory.
6. **Admin panel** as a mobile view onto CoineProFx's existing admin API, not a reimplementation.
7. **Versioning and Play release.**

## Open question

Per-signal execution exists on neither backend, yet decision 1 keeps signalling and copy trading
as separate products. If signals are meant to be executable one at a time, that contract has to be
built on both platforms. If they are advisory and copy trading is the only execution path, the
existing execution screens should be retired. This still needs an answer.

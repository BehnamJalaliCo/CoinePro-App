# Phase 4 — the complete handover. Nothing else is needed to start.

**Assembled 2026-09-22, after a first handover failed because only the
brief was sent.** The brief opens with «Read `ACCOUNT.md` first», and `ACCOUNT.md` lives in the
Android app's repository, which the server does not have. That was the sender's mistake rather than
a gap in the plan. Everything the brief leans on is below, copied verbatim.

---

## Answers to what came back, before the documents

**1. Does `ACCOUNT.md` supersede §8.1? Yes — and the dates settle it, not an opinion.**
`ACCOUNT.md` was committed **2026-09-22**. The §8.1 answer naming TradeYar was **2026-09-21**. The
account document is the newer one, says so in its own first line, and §8.1 was rewritten in the same
commit: it now reads «Answered twice… **neither — the account is Pro Chart's own**». Both are below;
check them rather than taking this sentence for it.

So the brief is **not** the stale document, and the fallback reading — building the sync documents
on TradeYar's identity — is **not** what to do.

**2. `ACCOUNT.md` has five sections, not six.** The request asked for its §6, «the rate-limit
buckets». There is no §6; it ends at §5. Rate limits are **`SERVER.md` §6** — a different document,
included below in full. The same goes for «§4.5's three sync documents» and «§4.4.1»: both are
`SERVER.md`, not `ACCOUNT.md`. Worth knowing before reading, because a citation that points at the
wrong document is how a reader concludes a section is missing.

**3. The per-account backoff curve genuinely is not specified anywhere.** That reading is correct,
and it is the one real gap the review found. `ACCOUNT.md` §3 rule 2 gives the constraint and not the
curve: 10 a minute per address (`SERVER.md` §6), **plus** a per-account backoff so attempts cannot
be spread across addresses, and **lock nothing permanently** — a lockout a stranger can trigger is a
denial of service against the account's owner.

**Propose a curve in your report rather than waiting for one.** Doubling from a small base per
consecutive failure, capped at a few minutes, cleared by a success, sits inside that constraint and
is a decision the owner can correct once it is in front of them. What is *outside* the constraint is
any permanent lock, and any counter keyed on the address alone.

**4. «The `localStorage` rule predates this reversal» — where that is written.** `ACCOUNT.md` §2,
last paragraph: the rule «was already in `SERVER.md` §4.4 for the relayed design and survives the
reversal unchanged, for the same reason: a browser has an XSS surface a phone does not». No hidden
document — the author had both files open, which is exactly what was missing from the handover.

**5. The two things that hold either way — agreed, with one correction of emphasis.**
The reset proxy stays up, and the mail sender is unanswered: both correct. The correction is that
`MOBILE_RESET_DEEP_LINK_BASE` *should* point here is not the same as it *does*.
`docs/release/APP_LINKS.md` records it as **unset by default**, with its own comment that a link is
«never given a guessed default, because a link that 404s is worse than no link». So the likely
answer is that nothing depends on the proxy and the undo is free. It is still TradeYar's team to
confirm, and until they do, the proxy stays up — that part is not negotiable.

**6. One thing the review did not raise and should have.** `SERVER.md` §4.4's *older* subsection
about `/reset` still said «auth proxies to CoinePro-FX until §8.1 is answered» — two reversals out
of date, and the likeliest single cause of doubting which document was current. It now carries a
note saying so. If anything else in these documents reads as though it predates a decision, say so;
that is a finding, not a nuisance.

---

# Document 1 of 5 — the brief

# Pro Chart server — Phase 4: the account. A brief to be read cold.

You are working on the machine behind `pro-chart.com`. Everything before this phase is **built and
serving**; this document is the next phase and it is the largest one in the plan.

Read `docs/web/ACCOUNT.md` first, then `docs/web/SERVER.md` §4.4 and §4.4.1. This brief does not
repeat them — it says where things stand as of **2026-09-22**, what to do in what order, and the
two places to stop rather than decide.

---

## 1. What is already true, measured rather than assumed

Every line below was probed against the live host on 2026-09-22. Do not re-derive it; do check it
still holds before you start, because a thing that was true yesterday is not evidence.

| | |
| --- | --- |
| `/`, `/reset`, the three `/legal/*/` pages | `200 text/html` |
| `/api/app/latest` | `200 application/json` |
| `/api/crypto/prices` | `200`, `source: lbank-ws`, `stale: false` |
| `/api/fx/prices` | `200`, 19 rows, `XAU/USD` first |
| `/api/crypto/candles`, `/api/fx/candles` | `200` with real bars |
| `/api/news` | `200` |
| `/api/health` | `200`, both upstreams reachable |
| **every other `/api/auth/*`** | **`404`** |
| `/terminal/` | `404` — nothing is deployed there yet, and that is correct (§5) |

So Phases 0, 1, 1½, 2 and 3 are done and healthy. **Phase 4 is untouched.**

### The one thing that is live and must be handled first

`POST /api/auth/password/reset` answers — and it is **still the proxy to TradeYar**. Probed with a
deliberately invalid token, the body that comes back is TradeYar's own:

```json
{"type":"https://api.tradeyar.io/errors/TYR-017","title":"Validation Field Invalid",
 "status":422,"detail":"این فیلد الزامی است.","code":"TYR-017","field":"reset_token"}
```

`ACCOUNT.md` §5 says this route stops being proxied and becomes Pro Chart's own. **Do not do that
first.** See §2.

---

## 2. The gate before the undo — and it is not in the older brief

`SERVER_BUILD_PROMPT.md` Phase 4 opens with «Undo first. Remove the proxy.» That instruction is
right and its ordering is dangerous, because of something neither document checked:

**If TradeYar's recovery mail currently links to `pro-chart.com/reset`, removing the proxy breaks
password recovery for live readers** — their mail lands on this page, the page posts the token to a
route that now checks Pro Chart's own `reset` table, the token is not in it, and the reset fails
with no way forward. The page would be working perfectly and the reader would be locked out.

The variable is `MOBILE_RESET_DEEP_LINK_BASE` on TradeYar. `docs/release/APP_LINKS.md` records that
it is **unset by default on purpose** — its own comment says a link is «never given a guessed
default, because a link that 404s is worse than no link» — which would mean no live reader depends
on the proxy and the undo is free. **That was true when it was written and it is not a measurement
of today.**

**So: ask TradeYar's team, or read their environment, before removing the proxy.**

* **Unset, or pointing anywhere but here** → nothing depends on it. Remove the proxy whenever you
  reach step 3 below. Say in your report that you checked.
* **Set to `https://pro-chart.com/reset`** → **stop and tell the owner.** The proxy has to stay
  until TradeYar's mail stops pointing here, and that is a change on their side, not yours. Build
  everything else in the meantime; this is the one step that waits.

Either way, `/reset` the *page* does not change: always-visible field, `?token=` prefill, **nothing
called on load** (a mail scanner that fetched the URL would otherwise burn the token before the
reader clicked), and the backend's own message rendered rather than an invented one.

---

## 3. What blocks on the owner, and what does not — build in this order

`ACCOUNT.md` names sending mail as a real dependency the owner must supply: a domain with SPF, DKIM
and DMARC, and a provider. **Do not invent one and do not guess a relay** — a reset mail in a spam
folder is a reset that did not happen.

But mail blocks **two steps, not the phase.** Ordered so you get as far as possible without it:

### Before mail — build all of this now

1. **The four tables** of `ACCOUNT.md` §2 — `account`, `session`, `link`, `reset` — with the
   **Argon2id parameters written into the migration**, not left to a library default that changes
   under you.
2. **`register`, `login`, `logout`, `refresh`.** The refresh token is opaque and lives in an
   `HttpOnly; Secure; SameSite=Lax` cookie. **Never a bearer in `localStorage`** — a browser has an
   XSS surface a phone does not, and that rule predates this reversal.
   **Register works without mail:** create the row, leave `email_verified_at` **null**, and let
   them in. An unverified account is exactly what `ACCOUNT.md` §4 already calls a Pro Chart
   account — a chart account, not a subscription — so nothing is being promised that verification
   would have to deliver. Verification is step 5, when mail exists.
3. **`GET /api/me`** — the reader, their link state, and nothing about markets.
4. **`POST /api/link/{tradeyar|coineprofx}`** — the **link-on-demand** shape of `ACCOUNT.md` §4.
   Store `upstream_user_id` and **nothing secret**. Anything needing VIP is asked for with the
   reader's own upstream session, never with a token Pro Chart kept. Entitlement stays on the
   backends: Pro Chart holds identity, not permission.
   **Do not build the migration.** The link is reversible; moving the backends' readers into this
   table is not, and it needs both teams. `ACCOUNT.md` §4 says build the first and not the second.
5. **The three sync documents** — watchlists, layouts, drawings — versioned JSON, last-writer-wins,
   per Pro Chart account. Last, because they are worth nothing until there is an account to hang
   them on.

### Waiting on mail — do not start these

6. `password/forgot` and `password/reset` against Pro Chart's own `reset` table: hashed at rest,
   single use, thirty minutes, and **opening the link must not consume it**. (Plus §2's gate.)
7. `verify`, and the «your password changed» mail — best-effort, never allowed to fail the change,
   and **the page must never claim it was sent**.

---

## 4. The rules that do not bend

`ACCOUNT.md` §3 is the list and all seven are load-bearing. The three most often got wrong:

* **The same answer whether the address exists or not.** `password/forgot` always says «if an
  account exists for that address, a mail was sent». An enumerable login is how a leak becomes a
  list.
* **Rate limits that are not the read buckets.** `/api/auth/*` is 10 a minute per address (§6), and
  **on top of that a per-account backoff**, so an attacker cannot spread attempts across addresses.
  Lock nothing permanently: a lockout a stranger can trigger is a denial of service against the
  account's owner.
* **Deleting an account deletes it.** `/legal/delete-account/` is already a page; it now has to be
  a route that works. Rows go, not a flag.

And one that is about this machine rather than this phase: **from the first account, `pro-chart.com`
holds password hashes, e-mail addresses and session tokens.** Until now it held a cache, three
static documents and a public APK — nothing that could hurt a reader if it were lost. That sentence
is why §3 exists and why none of it is optional.

### What the error bodies have to make distinguishable

This is new, and it comes from a real store rejection. Cafe Bazaar refused the Android app because
its sign-in screen showed «پاسخی نرسید» — *no answer came* — for **four different causes**, three of
which were not that. The app now tells them apart and says which:

| what happened | what the reader is told |
| --- | --- |
| nothing arrived | «پاسخی نرسید» |
| the server answered `5xx` | «سرور پاسخ داد ولی خطای خودش را برگرداند» |
| the answer arrived and the client could not read it | «برنامه نتوانست پاسخ را بخواند» |
| the client refused the certificate | «ارتباط امن را خودِ برنامه رد کرد» |

**So: answer with a status a client can act on, and a body it can parse.** A `4xx` for a refusal
with the reason in it, a `5xx` only when the fault is genuinely yours, and never a `200` carrying an
error. A field you stop sending is a client that cannot read your answer — which now has its own
sentence on the glass, pointing at whoever changed the shape.

---

## 5. Two smaller things, both independent of the account

* **`/s/<id>` — a shared script.** `SERVER.md` §3.1 has it as «not built yet», and when it is:
  **server-rendered, not an SPA route.** `ScriptLink.of` in the app's `:namascript` module already
  spells the address. It carries an **id and never source** — a link that carried code would run a
  stranger's script on the strength of a tap. The page shows the script and lets the reader decide;
  it never installs anything.
* **`/terminal/` is not yours to build.** It is a Kotlin/Wasm single-page app compiled from the
  same engine the Android app draws with, and it is built in the app's repository
  (`docs/web/TERMINAL_BUILD_PROMPT.md`, phases W1–W3). Your side is what §3.1 already specifies:
  `/terminal/…` is the **only** prefix with an SPA fallback, and `/reset` stays outside it. Serve
  it when it arrives; do not write one.

---

## 6. Acceptance — paste the output, do not summarise it

After the pre-mail steps:

```bash
# the four tables exist and the migration carries the Argon2id parameters
psql -c '\d account' -c '\d session' -c '\d link' -c '\d reset'
grep -ri 'argon2\|memory_cost\|time_cost\|parallelism' migrations/ | head

# register → login → me → refresh → logout, with the cookie jar
curl -si -c jar -X POST https://pro-chart.com/api/auth/register \
  -H 'Content-Type: application/json' -d '{"email":"…","password":"…"}'
curl -si -c jar -b jar -X POST https://pro-chart.com/api/auth/login  -d '…'
curl -s      -b jar      https://pro-chart.com/api/me
curl -si -c jar -b jar -X POST https://pro-chart.com/api/auth/refresh
curl -si      -b jar -X POST https://pro-chart.com/api/auth/logout

# the cookie is HttpOnly, Secure, SameSite=Lax — read it off the Set-Cookie header, not off a claim
# and nothing in the body is a bearer token

# the enumeration rule: both of these must answer identically
curl -s -X POST https://pro-chart.com/api/auth/password/forgot -d '{"email":"definitely-not-a-user@example.com"}'
curl -s -X POST https://pro-chart.com/api/auth/password/forgot -d '{"email":"<a real one>"}'

# the read routes did not move
for u in /api/crypto/prices /api/fx/prices /api/news /api/health /api/app/latest; do
  printf '%-24s ' "$u"; curl -s -o /dev/null -w '%{http_code}\n' "https://pro-chart.com$u"; done
```

## 7. Stop and ask on

* **`MOBILE_RESET_DEEP_LINK_BASE` pointing at `pro-chart.com/reset`** (§2) — the proxy stays.
* **The mail sender** — domain and provider. Do not guess one.
* **Migrating the backends' readers** — build the link, not the migration.
* Anything that would change either live backend.
* Phase 5, the candle archive, at all: it is the first thing on this server that would *store*
  market data rather than cache it, and it needs the owner's yes before a line of it.

Report after the phase, not during: the acceptance output verbatim, what you changed, and anything
that surprised you. Keep it short.

---

# Document 2 of 5 — `docs/web/ACCOUNT.md`, verbatim

# The Pro Chart account — its own, depending on no backend

**Decided by the owner on 2026-09-22**, reversing the previous day's answer. `SERVER.md` §8.1 had
said the account on the web belonged to TradeYar; it belongs to **Pro Chart**, and to nobody else.

This document is what that means, what it costs, and the one question it leaves open.

---

## 1. Why the reversal is right, and what it makes this server

The year-old question in `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md` asked **which of the two
backends** owns the reader. Every answer to it was bad in the same way: it made the product's front
door somebody else's property. If TradeYar owns the reader, then a reader who wants the chart has
an account on a crypto venue's server; if CoinePro-FX does, the same with a forex desk. Neither is
what the brand is, and either makes «Pro Chart» a skin over a backend.

So: **Pro Chart holds identity. The backends hold data.** `SERVER.md` §1 already says the two are
sources of market data; from now on that sentence is the whole of their role.

**But be clear what it makes this machine.** Until today `pro-chart.com` held nothing that could
hurt a reader if it were lost — a cache, three static documents, an APK anybody can download. From
the first account it holds **password hashes, e-mail addresses and session tokens**, and it becomes
a thing worth attacking. Every rule in §3 exists because of that sentence and none of them is
optional.

---

## 2. What it stores

Four tables. Nothing else, and nothing about markets.

| table | columns that matter | notes |
| --- | --- | --- |
| `account` | `id`, `email` (citext, unique), `password_hash`, `email_verified_at`, `created_at`, `disabled_at` | `email` is the identifier. No usernames — one fewer thing to recover |
| `session` | `id`, `account_id`, `refresh_hash`, `issued_at`, `expires_at`, `revoked_at`, `user_agent`, `ip_first_seen` | one row per sign-in, so «sign out everywhere» is one `UPDATE` |
| `link` | `account_id`, `platform` (`tradeyar` \| `coineprofx`), `upstream_user_id`, `linked_at` | §4 |
| `reset` | `token_hash`, `account_id`, `expires_at`, `consumed_at` | 30 minutes, single use, **hash only** |

**Passwords: Argon2id**, not bcrypt and not PBKDF2 — memory-hard, and the parameters written down
in the migration rather than left to a library default that changes. Never log a password, never
log a token, never put either in an error body.

**Sessions: an opaque refresh token in an `HttpOnly; Secure; SameSite=Lax` cookie**, and a short
access token the server keeps. The browser never holds a bearer in `localStorage` — that rule was
already in `SERVER.md` §4.4 for the relayed design and survives the reversal unchanged, for the
same reason: a browser has an XSS surface a phone does not.

---

## 3. The rules that make it safe to hold this

1. **Argon2id, and the parameters in the migration.**
2. **Rate limits, and they are not the read buckets.** `/api/auth/*` is 10 a minute per address
   (`SERVER.md` §6), and on top of that a **per-account backoff** so an attacker cannot spread
   attempts across addresses. Lock nothing permanently; a lockout that a stranger can trigger is a
   denial of service against the owner of the account.
3. **The same answer whether the address exists or not.** `/password/forgot` always says «if an
   account exists for that address, a mail was sent». An enumerable login is how a leak becomes a
   list.
4. **A reset token is hashed at rest, single use, and 30 minutes.** And **opening its link must not
   consume it** — mail scanners fetch every URL before the human does, so the page renders a form
   and calls nothing on load. This is already true of `/reset` (`SERVER.md` §4.4.1) and must stay
   true when the route behind it becomes Pro Chart's own.
5. **Tell the owner after the fact.** A password change sends a «your password changed» mail —
   the message that exposes a takeover. It is best-effort: it must never fail the change, and the
   page must never claim it was sent (`SERVER.md` §4.4.1 says why).
6. **Deleting an account deletes it.** `/legal/delete-account/` already exists as a page; it now
   has to be a route that works. Rows go, not a flag.
7. **Nothing about markets in these tables.** If a column here names a symbol, the design took a
   wrong turn.

---

## 4. The open question: the reader who already has an app account

**This is the one thing this document does not decide, because it is the owner's.**

Today a reader's account lives on TradeYar (crypto, community, membership) or CoinePro-FX (the
academy, forex VIP). Their **entitlement** — whether they are a paying member — lives there too.
Pro Chart holding identity does not move that, and it should not: billing and VIP belong where the
service is sold.

So a Pro Chart account needs a way to say «and this reader is also that reader», and there are two
shapes:

* **Link on demand.** The reader signs in to Pro Chart, then once, from inside the terminal, signs
  in to TradeYar to join the two. Pro Chart stores `upstream_user_id` and nothing secret. Anything
  needing VIP is asked for **with the reader's own upstream session**, not with a token Pro Chart
  kept. Safer, and it degrades honestly: unlinked means the paid surfaces say «link your account»
  rather than lying about why they are empty.
* **Migrate.** The backends' accounts move into Pro Chart's table and the backends stop having
  readers at all. Cleaner in the end, a migration of real people in the middle, and it needs both
  backends' teams.

**Build the first. Do not build the second without the owner saying so.** The first is reversible;
the second is not.

**And say the consequence out loud where the reader can see it:** until they link, a Pro Chart
account is a chart account. It is not a subscription, it does not carry their membership, and the
screen must say that rather than leaving them to discover it.

---

## 5. What this changes that was already built

* **`POST /api/auth/password/reset` stops being proxied to TradeYar.** It was wired there on
  2026-09-21 and is now Pro Chart's own route against Pro Chart's own `reset` table. The `/reset`
  page keeps its shape — always-visible field, `?token=` prefill, nothing called on load, the
  backend's own message shown rather than an invented one — and only its destination changes.
  `SERVER.md` §4.4.1 records what that page learned from two different backends; the lesson stands
  even though neither is behind it now.
* **`MOBILE_RESET_DEEP_LINK_BASE` on TradeYar is no longer the point.** That variable was going to
  make TradeYar's mail open Pro Chart's page. Pro Chart now sends its own mail to its own page, so
  the variable matters only for readers resetting a *TradeYar* password, which is TradeYar's own
  business again.
* **Sending mail is new, and it is a real dependency.** This server has never sent an e-mail. It
  needs a sender the receiving world trusts: a domain with SPF, DKIM and DMARC, and a provider or
  a relay. **Do not invent one** — it is the owner's to choose, and a reset mail that lands in spam
  is a reset that did not happen.
* **The app is unchanged for now.** It keeps its own accounts on the two backends. Moving the phone
  onto the Pro Chart account is a later decision, and `AUTH_CONTRACT.md` is the document it would
  change.

---

# Document 3 of 5 — `SERVER.md` §4.4 and §4.4.1, verbatim

### 4.4 The account — **Pro Chart's own, from 2026-09-22**

**The owner changed this, and it is the largest decision in this document.** §8.1 was answered
«TradeYar» on 2026-09-21 and reversed the next day: **the account belongs to Pro Chart and depends
on no backend.** `docs/web/ACCOUNT.md` is the specification; this row is the summary.

What that means in one line: `pro-chart.com` stops relaying anybody else's identity and starts
holding its own — its own users, its own password hashes, its own sessions, its own reset mail.
TradeYar and CoinePro-FX go back to being what §1 calls them, **sources of market data**, and stop
being sources of *people*.

**The reversal costs one route and buys the product its own front door.** `POST
/api/auth/password/reset` was proxied to TradeYar on 2026-09-21 (§4.4.1); it becomes Pro Chart's own
and the proxy goes.

**Measured 2026-09-22: the proxy is still in place** — a deliberately invalid token comes back as
TradeYar's own `TYR-017` body, trace id and all. That is the correct state, because removing it has
a precondition neither this section nor the build prompt had written down: **if TradeYar's recovery
mail currently links here** (`MOBILE_RESET_DEEP_LINK_BASE`), removing the proxy locks live readers
out of their own password recovery, with every page involved looking like it works.
`ACCOUNT_BUILD_PROMPT.md` §2 is the gate. That is what «one route, reversible in an afternoon» was for.

| `pro-chart.com` | where it lives | note |
| --- | --- | --- |
| `POST /api/auth/register`, `/login`, `/logout`, `/refresh`, `/password/forgot`, `/password/reset`, `/verify` | **Pro Chart itself** | `ACCOUNT.md`. No upstream, no proxy |
| `GET /api/me` | Pro Chart itself | the reader, their link state, their entitlement |
| `POST /api/link/{tradeyar\|coineprofx}` | Pro Chart, calling the backend **once** | how a reader who already has an app account joins it to this one. **The open question is in `ACCOUNT.md` §4** |
| `GET /api/membership` | TradeYar | `api/v1/public/membership`, `api/mobile/v1/membership/status` — **entitlement still comes from the backends**; Pro Chart holds identity, not permission |
| `GET /api/membership` | TradeYar | `api/v1/public/membership`, `api/mobile/v1/membership/status` |
| `GET,POST /api/community/*` | TradeYar | `api/v1/public/app-community/*` |
| `GET /api/academy/*` | CoinePro-FX | the academy routes, behind `user/academy-token` |

**The browser never holds a bearer token in `localStorage`.** The relay exchanges the upstream token
for an `HttpOnly; Secure; SameSite=Lax` session cookie and keeps the bearer server-side. That is the
one behaviour where the web deliberately differs from the phone, and the reason is that a browser
has an XSS surface a phone does not.

**There is no broker account, and no route for one.** Copy trading was removed from the product
(run Ψ); `user/account/link`, `DELETE user/account` and `user/copy-status` are **not** relayed, and
adding them later is a product decision rather than a configuration one.

### 4.4.1 What the switch to TradeYar exposed — read from TradeYar's source, 2026-09-21

The move cost what §4.4 said it would: the body is `{reset_token, new_password}` on both backends,
so only the destination changed. Three things were **not** the same, and each is worth having in
writing before somebody assumes one backend's habits about the other.

**The credential is a link, not a typed code.** CoinePro-FX's mobile flow mails an eight-character
`ABCD-EFGH` and nothing else; TradeYar mails **both** — a button carrying `?token=<token>` *and* the
token printed below it as copyable text, deliberately, «for the very common case of reading the mail
on a desktop and finishing in the app on the phone». `reset_token` is `1..512` here against
`6..40` there. So the page keeps its always-visible field *and* its `?token=` prefill, and the
`ABCD-EFGH` hint goes, because it was one backend's rule wearing the other's clothes.

**Opening the link must not consume the token**, and that is TradeYar's own stated contract:
corporate mail scanners and link-preview bots fetch every URL in a message before a human sees it,
so a `GET` that burned the token would meet the reader with «invalid link» on their first real
click and the cause would never be found. The page therefore renders a form and **calls nothing on
load**.

**The error shapes differ and both must be read.** CoinePro-FX answers `{"detail": {"code",
"message"}}`; TradeYar answers RFC 7807 with `detail` as a **string** (`TYR-003`, «این لینک بازیابی
معتبر یا فعال نیست»). A page that reached for `detail.message` would have rendered nothing at all
against the new backend — an empty error is worse than an ugly one, because the reader concludes
the button is broken rather than the token is stale.

**And the sentence that was removed should go back.** «Every other device was signed out» was read
out of CoinePro-FX's source; when the destination became TradeYar, nobody had read TradeYar's, so
dropping it was right — a claim kept by habit about a server nobody has looked at is the same
mistake as inventing one. Now somebody has looked: `password_reset` in
`app/api/routers/mobile/auth.py` calls `revoke_all_for_user(platform_id, reason="password_changed")`
**unconditionally**, before it returns. The sentence is true here too, so it returns — because it
was read, not because it was already written.

**What must not be added**, though it is also true: TradeYar sends a «your password changed» mail
afterwards. It is conditional on the account having an address and wrapped in a `try/except` that
logs and swallows a failure — by design, so a mail server cannot fail a reset that already
succeeded. A page that promised the mail would be lying in exactly the case where it matters.

**Success is `{"reset": true}`**, read from the same function rather than inferred.

**One thing is left and it is a variable on TradeYar's machine, not work here.** The reset mail
carries a link only when `MOBILE_RESET_DEEP_LINK_BASE` is set; TradeYar's own comment says it is
«never given a guessed default, because a link that 404s is worse than no link» — which is the
argument this document made from the other side two days ago, when it decided `/reset` had to exist
before any mail named it. **It exists now**, so the variable can point at
`https://pro-chart.com/reset`. Until it does, TradeYar's mail ships the token alone and the reader
pastes it, which works: the page's field is always visible for exactly that reason.

---

# Document 4 of 5 — `SERVER.md` §4.5, verbatim

### 4.5 What the server owns itself

Three documents per account, and nothing else. Each is a JSON blob with a version number,
last-writer-wins, exactly as the watchlist sync already works on the phone
(`WatchlistSyncController`):

| `pro-chart.com` | what | already on the phone |
| --- | --- | --- |
| `GET,PUT /api/sync/watchlists` | the reader's lists | `WatchlistSyncController` — this route exists upstream today |
| `GET,PUT /api/sync/layouts` | saved chart layouts and workspaces | `ChartLayoutStore`, `ChartWorkspaceStore` — **needs the pair building** |
| `GET,PUT /api/sync/drawings` | drawn objects per symbol | `DrawingSyncStore` — **needs the pair building** |

These are the only tables. Everything else the server holds is a cache and may be thrown away at any
moment without the product noticing.

---

# Document 5 of 5 — `SERVER.md` §6 (the relay and its rate limits), then §8 (the owner's decisions)

## 6. The relay itself

**FastAPI on Python 3.12**, unless whoever builds it prefers Node. The reason to name one is that
CoinePro-FX is FastAPI already: the owner's team knows the shape, the deployment and the logging, and
a second language on a second server is a second thing to learn at three in the morning.

What it is, in whole: an HTTP client with a Redis cache in front, a WebSocket hub, three tables, and
a cookie. No ORM beyond SQLAlchemy for the three documents. No background workers except the nightly
candle pull and the two upstream sockets.

### Rate limits, and the number that was wrong

**60 a minute per IP on `/api/*` was written before §4.2.1 existed, and §4.2.1 broke it.** The
relay's author did the arithmetic and did not change the figure unilaterally, which was right — so
here it is, corrected.

Two seconds of polling is thirty requests a minute. **Two tabs from one address reach a sixty-a-minute
ceiling on that one route**, before `fx/prices`, a candle load or the news take any share at all. And
a per-IP limit is blunt in this product's own market: `NetworkFactory`'s KDoc already says why the
backends cannot rate-limit the phone by address — «carrier-grade NAT puts a very large number of
Iranian mobile subscribers behind one address» — which is exactly as true of an office, a household,
or a floor of a building behind one NAT.

**A limit should be proportional to what a request costs**, and these do not cost the same:

| bucket | routes | per IP |
| --- | --- | --- |
| **cached reads** | `/api/crypto/prices`, `/api/fx/prices`, `/api/app/latest`, the legal pages | **240 a minute** |
| **everything else under `/api/*`** | candles, news, signals, membership, health | **60 a minute** |
| **auth** | `/api/auth/*` (Phase 4) | **10 a minute** |
| **socket** | `wss://…/api/stream` | one per IP, 200 symbols |

The first row is generous because it is nearly free: those responses are served from a two-second
Redis cache and cost the upstreams **nothing at all** — a hundred tabs are one upstream call either
way. Refusing them is protecting a resource that is not scarce. The second row stays at sixty
because a candle miss really does reach a backend.

**Bucket by `(address, client id)` where a client id is present**, falling back to the address
alone. The terminal sends a random per-browser identifier the way the app sends `X-Install-Id`, and
for the same reason: so that honest readers behind one NAT do not collide. It is **not** a
replacement for the address limit — a browser can mint identifiers — so the per-address ceiling stays
as the backstop, an order of magnitude above the per-client one.

And the terminal's side of the bargain, which is not optional: **stop polling when the tab is
hidden** (`document.visibilityState`). A background tab has no reader. If a `429` is ever seen in
the terminal, this is the first place to look and the relay is the second.

### 6.1 As built, and measured from outside

The relay implements the table above as **two zones per bucket**: a per-client key of
`{client_ip}|{X-Install-Id}{X-Client-Id}` and a per-address key of `{client_ip}` at ten times the
figure (2400 / 600 / 100). Concatenating both headers rather than choosing between them is a Caddy
constraint — it has no fallback expression — and it lands on the right behaviour anyway: absent
headers expand to empty, so a client that sends neither collapses to the address alone, which is
this section's fallback with no conditional to get wrong. `X-Install-Id` is the name because it is
already this product's own (`NetworkFactory`); `X-Client-Id` is accepted as an alias so a browser
need not borrow the app's header.

Measured against the live host rather than read from a report:

| probe | result |
| --- | --- |
| `/api/app/latest` × 70, one address | 70 × 200 — above the 60 ceiling, so the cached-read bucket is in force |
| `/api/news` × 70, one address | 60 × 200, then 10 × 429 — the default bucket, exactly |
| `/api/news` × 40 + × 40 under two `X-Client-Id` values, then × 5 with none | 85 × 200, no 429 — the per-client key is real, and the 600 per-address backstop is what is left |

Two consequences worth writing down:

* **The buckets are shared, not per route.** Spending `/api/app/latest` also spends
  `/api/crypto/prices` and the legal pages, because they are one bucket; `/api/health` is in the
  other and is unaffected. That is the intent — the bucket is a budget for a class of cost, not a
  quota per URL — but it means a terminal that hammers one cached route starves the rest of its own
  cached reads.
* **The app sends no client id to this host.** `appUpdateGateway` builds its own unauthenticated
  client with no `installId` provider (`AppModule`), deliberately, so the phone's update check
  lands in the address bucket. It costs one request per launch against a ceiling of 240, which is
  the right trade: an install identifier does not belong on a request that carries no account and
  asks a public question.

The socket is deliberately in no per-minute bucket. This section gives it no figure, and a
self-invented one would punish a reconnect during a flap — the case where a reader is already
having a bad time. Its limits are the one-per-address and 200-symbol rules, enforced in the relay
where the subscription is read rather than at the edge where it cannot be seen.

### 6.2 Serving the bundle

A Wasm terminal is 5–15 MB, which is a different kind of object from everything else this host
serves and needs three things right. All three are in place and were **proved against a synthetic
bundle before the real one exists**, which is the right order: nothing to discover on the day it
lands.

| rule | why |
| --- | --- |
| `*.wasm` → `Content-Type: application/wasm` | without it the browser drops out of streaming compilation and reads the whole file before starting |
| `*.wasm`, `*.js` → `Content-Encoding: br`, **pre-compressed on disk at quality 11** | compressing ten megabytes per reader is a CPU bill, not a cache. Written once by `bin/precompress.sh` |
| a content hash in the name (`[.-]<8+ hex>.<ext>`) → `max-age=31536000, immutable`; `*.html` → `no-cache` | the usual pair, and for a file this size it is the difference between one download and every download |

The rules key on **extension and filename, not on path**, which is what makes §3.1's mount point a
decision the server does not have to care about.

### 6.3 Two things that look like faults and are not

Written down because each will be found again by somebody with `curl`, and a false alarm costs more
than the line it takes to pre-empt it.

* **`HEAD /api/app/latest` reports `content-length: 20`; `GET` returns 989 bytes.** Cloudflare
  answers a `HEAD` with the *compressed* length (its ETag carries the `-gzip` suffix), while the
  origin's own answer to the same request is 989. The app uses `GET`, which returns the whole
  document. `curl -I` against this route is measuring the edge's compression, not a truncated file.
* **The legal pages are `no-cache` rather than `max-age=300`.** A document whose entire value is
  being current should not be served stale for five minutes; the cost is one revalidation and a
  `304`, not a re-download. The app bundles its own copies anyway, so a reader is never stranded by
  a revalidation that fails (`docs/release/DOMAINS.md`).

---

---

## 8. What is still the owner's to decide

**Cite these as «`SERVER.md` §8.<n>».** They used to be cited as «`PLAN.md` §6.<n>», which was fine
until this document grew a §6.2 of its own about serving the bundle — and «§6.2» now names two
different things in two documents, one of which is an owner decision and the other a cache header.
The numbering below is the canonical one.

0. ~~The provider and the machine.~~ **Done** — provisioned, on the same Hetzner private network as
   TradeYar and CoinePro-FX, with an agent on it. `SERVER_BUILD_PROMPT.md` is what it works from.

1. ~~**Which backend owns the account.**~~ **Answered twice.** «TradeYar», 2026-09-21; then, on
   2026-09-22, **neither — the account is Pro Chart's own and depends on no backend.** The second
   answer is the one that stands and it is a bigger decision than the first: it makes this server
   the holder of people's passwords, which §4.4 and `ACCOUNT.md` spell out. The year-old question
   in `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md` is answered by refusing its premise — it asked
   *which of the two*, and the answer is *neither*.
2. ~~**Whether the terminal is open, member-only, or a read-only guest page.**~~ **Answered
   2026-09-21: open and read-only, no account.** This one needs no work at all — it is exactly what
   Phases 2 and 3 already serve — and it unblocks a schedule: `PARITY.md`'s W1→W3 can ship to
   readers without waiting for W4, because there is nothing to sign in to.
3. **Whether the candle archive is built on day one** (§5). It is the difference between a reader
   panning to the edge of the backend's window and panning as far as the product has history.
4. ~~**The App Signing certificate's SHA-256 fingerprint**, from Play Console.~~ **Settled, and the
   answer turned out to be simpler than the question.** Google Play does not serve Iran and this
   app is installed from a downloaded APK (`docs/release/DISTRIBUTION.md`), so there is no Play App
   Signing in the path and no re-signed key to ask a console about: the fingerprint
   `assetlinks.json` needs is the **release keystore's own** SHA-256. Three ways to read it, in
   `DISTRIBUTION.md` §5 — the shortest being the app's own «ایمنی و انتشار» screen, which prints
   the certificate of the running install with a copy button. The owner still has to *hand it over*;
   they no longer have to find it.
5. **Whether the APK is served from this host as well as from GitHub** (§4.6). Nothing breaks if it
   is not — the update document may point at the GitHub release — but a download that comes from
   the same host as the document is one fewer thing a reader has to trust.

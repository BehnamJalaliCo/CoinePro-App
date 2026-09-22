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
5. **The three sync documents** — watchlists, layouts, drawings — per Pro Chart account. Last,
   because they are worth nothing until there is an account to hang them on.
   **Build them to `SERVER.md` §4.5's table, which is optimistic concurrency and not
   last-writer-wins**: never-synced answers `version: 0` and `{}` rather than `404`; a stale `PUT`
   is a **`409` carrying the whole current document**; over the cap is `413` with nothing written;
   and `max_bytes` rides on every response. A `200` to a stale write passes every one-device test
   and eats the reader's second handset.

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
* **Rate limits that are not the read buckets.** `/api/auth/*` is 10 a minute per **network**
  address (§6), and on top of that a backoff **keyed on the e-mail address that was typed, whether
  or not an account exists behind it** — `ACCOUNT.md` rule 2, corrected 2026-09-23. Keying it on the
  account is an oracle: a real address slows down on the fourth try and an unregistered one never
  does, which answers the question rule 3 exists to refuse. Lock nothing permanently: a lockout a
  stranger can trigger is a denial of service against the account's owner.
  The curve: two free failures, then 2ⁿ seconds capped at five minutes, cleared by a success, and
  the counter expires after an hour.
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

## 6a. Two calls already made, so nobody re-opens them

* **`DELETE /api/me` asks for the password again.** `ACCOUNT.md` does not require it; the server's
  agent added it and asked. **Keep it.** It is the one irreversible thing in the phase, and a second
  factor of «you are the person, not just the session» is cheap against a cost that cannot be
  undone.
* **`/api/link/*` belongs in the 10/min bucket, not the 60.** The agent put it in 60 because §6's
  table names only `/api/auth/*` at 10, and flagged it. The flag was right and the placement moves:
  the route **accepts an upstream credential**, which makes it an authentication surface whatever
  its path says. §6's table is the thing that was incomplete.

## 7. Stop and ask on

* **`MOBILE_RESET_DEEP_LINK_BASE` pointing at `pro-chart.com/reset`** (§2) — the proxy stays.
* **The mail sender** — domain and provider. Do not guess one.
* **Migrating the backends' readers** — build the link, not the migration.
* Anything that would change either live backend.
* Phase 5, the candle archive, at all: it is the first thing on this server that would *store*
  market data rather than cache it, and it needs the owner's yes before a line of it.

Report after the phase, not during: the acceptance output verbatim, what you changed, and anything
that surprised you. Keep it short.

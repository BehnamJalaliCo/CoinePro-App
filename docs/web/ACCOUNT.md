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

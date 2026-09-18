# RUN Ψ — blocked

Two things, and each carries what is in the build meanwhile and the exact thing the owner must
supply. **§Ψ16 is answered and closed** — the answer is at the foot of it.

---

## §Ψ10 — the Pro Chart server

**What cannot be done here.** There is no machine. `pro-chart.com` does not answer, and nothing in
this container can make it.

**What is in the build instead.** `docs/web/SERVER.md`, which is everything that can be decided
without the machine: the sizing, the four containers, the names, every route the relay carries with
its upstream and its cache policy, the socket fan-out, the three documents it owns, the rate limits,
and a seven-step go-live order with a proof for each step. When the machine exists, the work is
configuration rather than design.

**What the owner must supply**, in order:

1. **The machine.** 4 vCPU, 8 GB, 80 GB SSD, a fixed IP, Ubuntu 24.04. Hetzner is assumed because
   CoinePro-FX is already there and the two being neighbours makes the upstream hop a local one;
   anything of that size does the job.
2. **The DNS for `pro-chart.com`** pointed at it.

Steps 1 and 2 of §7 — TLS, the legal pages and `assetlinks.json` — are an afternoon after that, and
they are worth doing on their own account: every legal link in the shipping app already points at
`pro-chart.com/legal/…` and that host answers nothing today.

**Three product questions gate the later steps**, and none of them blocks the first four:

* **Which backend owns the account** on the web (`PLAN.md` §6.2 and
  `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md` — the same question, still unanswered). Step 5
  cannot start without it.
* **Whether the terminal is open, member-only, or a read-only guest page** (`PLAN.md` §6.3).
* **Whether the candle archive is built on day one** (`SERVER.md` §5). It is the difference between
  a reader panning to the edge of a backend's window and panning as far as the product has history.

---

## §Ψ16 — the LBank copy-trading sentences *(closed — «it stays»)*

**What was done.** Copy trading is gone from the app: the modules, the screens, the routes and every
string a reader could meet. That is the instruction, executed.

**What was not, and why.** Two places still describe automatic copy trading **on LBank** — the
crypto venue, on TradeYar:

| where | what it says |
|---|---|
| `docs/legal/TERMS.md` §6-3, `TERMS_EN.md` §6.3 | «کپی‌تریدینگ خودکار روی LBank ارائه می‌شود» — automatic copy trading is offered on LBank; Ourbit does not have it |
| `membership_copytrade_note`, on the membership gate | the same sentence, in the app |
| `docs/legal/PRIVACY_POLICY.md` | lists «بروکر MT5» among the recipients, «if you enable copy trading» |

The instruction was «**فارکس** دیگه کپی ترید نداریم», and the app's copy-trading surface was
forex-only. These three are about the **crypto** service, which may well still run outside this app
— the web panel has had those routes all along. Rewriting them would be this run deciding something
about that service it was not told, in the document a reader legally agrees to.

**What the owner must say.** One word:

* **«crypto copy trading is also gone»** — then the three passages come out, the terms and the
  privacy note are re-synced through `scripts/release/sync-legal-documents.py`, and the membership
  gate loses a note.
* **«it stays, it is the web panel's»** — then nothing changes and this section closes, and the app
  is simply a product that does not carry a feature the service has.

### The answer

> «کپی ترید البنک میماند»

**The second branch.** LBank copy trading stays; it is the service's, reached from the web panel,
and this app does not carry it. So the three passages stand exactly as written and **nothing is
edited** — `TERMS.md` §6-3, `TERMS_EN.md` §6.3, `membership_copytrade_note` and the MT5 broker line
in `PRIVACY_POLICY.md` all describe a service the reader really can have, and a reader who agrees to
them is agreeing to something true.

Worth writing down, because the next reader will meet the asymmetry and wonder whether it is a
leftover: **the app carries no copy trading and the terms describe some, and both are correct.** The
terms are the *service's*, not this binary's — they are the same document the web panel serves — and
a document that described only what one client happens to draw would be the wrong document. The
membership gate's note is the one place a reader could be misled, and it is not: it says the
membership includes it, which it does, on LBank, through the panel.

This section is closed. Nothing in the build changed on the strength of it, which is the point.

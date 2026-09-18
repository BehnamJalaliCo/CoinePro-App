# The brief for the Pro Chart server

Hand this to the Claude Code running **on the server**. It is written to be read cold, by an agent
that has none of this conversation: everything it needs is either in here or discoverable on the
machine.

Read `SERVER.md` next to this file for the full specification — the route table, the cache policy
and the reasoning. This document is the order of work, the acceptance check for each step, and the
rules that do not bend.

---

## What you are building

**One origin in front of two backends.** Two servers already hold this product's data — **TradeYar**
(crypto, over LBank) and **CoinePro-FX** (gold and the dollar, over Finnhub). The Android app talks
to both directly and that is fine for a phone. A browser cannot: two origins mean two CORS
negotiations on servers we do not want to change, two cookies, two sessions, and a thousand open
tabs mean a thousand upstream sockets where the phone opened one.

So this server relays what a browser needs, caches it per field, holds one upstream socket per venue
and fans it out, keeps the three documents that are genuinely the web's own, and serves the static
pages.

It also does one thing that has nothing to do with the browser: **it is the Android app's update
channel.** Google Play does not operate in Iran, so the app is downloaded and installed by hand, and
a static JSON file on this host is the only way a reader ever learns that a newer build exists.
Phase 1½, and it is two files.

**All three machines are on one Hetzner private network.** Use it. Upstream calls go over the
private address, not out to the public internet and back, and neither backend needs a firewall
change, a CORS header or any other modification. If a backend answers only on its public name, say
so and stop rather than opening a port.

---

## The rules that do not bend

1. **This server is never a source of truth.** It must not compute a price, a candle, a signal or a
   percentage. It relays and it caches. Where an upstream is down it says so and serves the last
   good answer **with its age**; it never fabricates one and never serves a stale answer silently.
2. **Bodies come back unchanged.** No reshaping, no renaming, no "tidying up" a payload. The Android
   app has one contract per backend and the browser terminal will use the same types. A relay that
   rewrites a payload is a second contract to keep in step with the first.
3. **Never invent an endpoint.** Every route you relay is in `SERVER.md §4` with its upstream. If
   something seems missing, write it down as a question; do not guess a URL at either backend.
4. **No secret in git, ever.** Everything sensitive is an environment variable in `/opt/prochart/.env`,
   which is `chmod 600` and in `.gitignore`. When you report, print **variable names only** — never
   values, never a fragment of a value, not even in an error message you are quoting.
5. **Nothing is done until its acceptance check passes.** Each phase below ends with a command and
   the answer it must give. Run it, paste the real output, and do not move on if it disagrees.
6. **Ask the bare snapshot.** Both backends answer a call with **no `symbols` parameter** by
   returning everything they quote. That is the only discovery mechanism this product has. Never
   name a symbol list in a relayed request; a list here would cap the web's universe at whatever you
   happened to know the day you wrote it.
7. **Stop and ask on anything destructive**: dropping a database, changing DNS, touching either
   backend's configuration, or anything that would take the two live servers off the air.

---

## Phase 0 — find out what is true

Before writing anything, establish and report:

- the server's public IPv4 and IPv6, and its private address on the Hetzner network;
- the **private** addresses of the TradeYar and CoinePro-FX machines, and whether each answers over
  that network — the two public bases the app uses today are
  `https://tradeyar.trade-future.ir/` and `https://coineprofx.com/api/`, and what you want is the
  same service reached privately;
- what is already installed (Docker, Compose, a web server on 80/443, anything else listening);
- whether `pro-chart.com` resolves to this machine yet.

For each backend, prove it answers by fetching the bare snapshot and reporting **only the status
code and how many symbols came back** — never the body:

```
GET <tradeyar-base>/api/mobile/v1/ws/snapshot
GET <coineprofx-base>/../ws/snapshot          # note: the app's FX base ends in /api/, this route sits beside it
```

If either refuses, or answers a different shape from `SERVER.md §4.1`, stop and report. Everything
below assumes both answer.

---

## Phase 1 — the names, TLS, and the pages that already have links pointing at them

**This is the part that is worth doing on its own account.** Every legal link in the shipping
Android app already points at `pro-chart.com/legal/…`, and that host answers nothing today. A reader
who taps «قوانین» in the app gets a browser on a dead host. Closing that is an afternoon.

1. **Caddy 2** as the edge, in Docker. TLS from Let's Encrypt for `pro-chart.com`, `www` 301s to the
   apex, HTTP/3 on, `Content-Encoding: br` for text and Wasm.
2. **The legal pages**, rendered to static HTML from the Markdown in the app's own repository —
   `github.com/BehnamJalaliCo/CoinePro-App`, `docs/legal/`. Clone it read-only and render; do not
   retype the text, and do not translate anything. The four sources are `TERMS.md` (Persian),
   `TERMS_EN.md`, `PRIVACY_POLICY.md` (which carries both languages), and `README.md` (which is the
   note on how they are kept in step — not a page).

   The addresses are fixed by what the app and the documents already link to, and are not yours to
   choose:

   | path | content |
   |---|---|
   | `/legal/terms/` | `TERMS.md`, with the English of `TERMS_EN.md` under it or on a language switch |
   | `/legal/privacy/` | `PRIVACY_POLICY.md` |
   | `/legal/delete-account/` | the account-deletion instructions — **see below** |

   `/legal/delete-account/` is **not** one of the four files. It is a page you write from the
   deletion section of `PRIVACY_POLICY.md` and `TERMS.md`: what deleting removes, what is kept and
   for how long, and the way to ask for it from outside the app. Persian first, English under it.
   Google Play requires this page to answer, so it must stand on its own with no app installed.

   Right-to-left, Persian first, and **no web font** — the product's typeface is licensed for the
   app, so the pages use the reader's own system stack. Plain, readable, dark or light by
   `prefers-color-scheme`. These are legal documents, not a landing page.

3. **`/.well-known/assetlinks.json`.** The Android app claims `https://pro-chart.com/reset` as an
   App Link and Android verifies it by fetching this file. **The owner must give you the SHA-256
   certificate fingerprint**; ask for it, it is not in the repository and must not be guessed. The
   package name is in the app's `build.gradle.kts`. The file must be served as `application/json`
   with no redirect.

   Tell them where to get it, because the obvious answer is the wrong one: **it is the release
   keystore's own fingerprint, not a Play Console one.** Google Play does not serve this product
   — it does not operate in Iran — so the app is installed from a downloaded APK and there is no
   Play App Signing re-signing it with a key only Google holds. The shortest route is the app
   itself: its «ایمنی و انتشار» screen prints the certificate of the running install, SHA-1 and
   SHA-256, with a copy button. `scripts/release/print-assetlinks.sh` in the app's repository prints
   the whole file from the keystore, and `docs/release/DISTRIBUTION.md` §5 is the reasoning. If the
   owner ever does get onto Play, list both fingerprints — the field is an array.

4. **A holding page at `/`.** One screen: the mark, the product's name, one sentence, and a link to
   the app. Not a marketing site — that is a later decision and not yours.

**Acceptance:**

```bash
curl -sI https://pro-chart.com/legal/privacy/ | head -1        # 200
curl -sI https://pro-chart.com/legal/terms/ | head -1          # 200
curl -sI https://pro-chart.com/legal/delete-account/ | head -1 # 200
curl -s  https://pro-chart.com/.well-known/assetlinks.json | head -c 200   # valid JSON, correct package + fingerprint
curl -sI https://www.pro-chart.com/ | head -1                  # 301 to the apex
```

Report the five outputs. When they pass, tell the owner — the three Play listing URLs can move from
`coineprofx.com/legal/…` to these, and that is their action, not yours.

---

## Phase 1½ — the update document and the APK

**Small, static, and the one thing on this server the Android app cannot do without for ever.**

Google Play does not serve Iran, so the app is downloaded and installed by hand. That works — except
that nothing ever tells a reader a newer build exists. The app now asks this server, and until this
server answers, every release that goes out is another cohort of people who will never hear about
the next one. It gets worse with time, which is why it is not at the back of the queue.

Two things, both static:

* **`GET /api/app/latest`** — a JSON file. Not computed, not relayed, not cached from anywhere:
  neither backend knows or should know what the Android release is. Written by the owner's release
  process; for now, written by hand when they tell you a version has shipped.
* **`GET /download/pro-chart-X.Y.Z.apk`** — the file itself, `application/vnd.android.package-archive`,
  no redirect to another host. Optional: the document may point at the GitHub release instead, and
  it is worth asking the owner which they want. Serving it here means the document and the file come
  from one host the reader is already trusting.

```json
{
  "version_code": 50000000,
  "version_name": "5.0.0",
  "url": "https://pro-chart.com/download/pro-chart-5.0.0.apk",
  "sha256": "…64 lowercase hex characters, the APK's own digest…",
  "notes_fa": "چند اصلاح کوچک.",
  "notes_en": "A few small fixes.",
  "mandatory": false
}
```

**The app enforces four things, and a release that fails any of them is silently not offered to
anybody** — no error, no message, the card simply never appears. So get them right:

1. `version_code` is the only field compared. It is the integer Android orders installs by.
2. `url` must be HTTPS and its host must be `pro-chart.com`, `www.pro-chart.com` or `github.com`.
3. `sha256` must be exactly 64 hex characters, and must be the digest of the file at `url` — the
   app shows it to the reader so they can check what they downloaded. A digest that does not match
   is worse than none.
4. `mandatory` changes one sentence on a card. It is **not** a kill switch, the app does not
   implement one, and no future request should ask you to make it into one.

Cache it a few minutes at the edge and no longer: the day a release goes out is the day somebody is
looking.

**Acceptance:**

```bash
curl -s  https://pro-chart.com/api/app/latest | python3 -m json.tool          # parses; seven fields
curl -sI https://pro-chart.com/api/app/latest | grep -i content-type          # application/json
curl -sI "$(curl -s https://pro-chart.com/api/app/latest | python3 -c 'import json,sys; print(json.load(sys.stdin)["url"])')" | head -1   # 200
```

And, if you serve the APK here, the digest — `sha256sum` of the served file against the `sha256` in
the document. Report both; they must be identical.

---

## Phase 2 — the read-only relay

FastAPI on Python 3.12, in Docker, behind Caddy at `/api/`. Redis for the cache. Postgres can wait
until Phase 4 — do not create tables you are not using yet.

Build exactly the routes in `SERVER.md §4.1` and `§4.3`, with the cache policy written there. Two
things in that table are load-bearing and easy to get subtly wrong:

- **The bare snapshot** (rule 6 above).
- **Candles cache by whether the bar is closed.** A closed bar never changes: cache it indefinitely,
  keyed `venue:symbol:interval:openTime`. Only the newest bar has a short life — one interval tick
  at most, 2 s on a minute chart, 30 s on an hour. This is where the relay earns its keep: a hundred
  tabs on BTCUSDT H1 become one upstream call an hour plus one live bar.

Add a `/api/health` that reports, per upstream: reachable or not, the age of the last good answer,
and the cache hit rate. No account, no socket, no writes in this phase.

Rate limits at the edge: 60 requests a minute per IP on `/api/*`.

**Acceptance:**

```bash
curl -s  https://pro-chart.com/api/fx/snapshot | head -c 200        # same shape as the backend's own
curl -s  https://pro-chart.com/api/crypto/snapshot | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["prices"]))'
curl -sw '%{time_total}\n' -o /dev/null https://pro-chart.com/api/crypto/candles?symbol=BTCUSDT\&interval=1h   # twice; the second under 5 ms
curl -s  https://pro-chart.com/api/health
```

Report the symbol count from the second command. **If it is under a hundred on crypto, say so
loudly** — the Android app is proved to render 1 200 and the phone has been seeing a short list.
That would be a backend question, not a relay bug, and the owner needs to know.

---

## Phase 3 — the socket

One upstream connection per venue, held for as long as this server is up. Browsers subscribe by
symbol at `wss://pro-chart.com/api/stream` and you fan out. The contract the browser sees is the
app's own — **snapshot, then stream**.

A subscriber count of zero on a symbol does **not** unsubscribe upstream: both venues send
everything on one socket anyway, and a relay that renegotiated on every tab close would spend its
life renegotiating.

One WebSocket per IP, 200 symbols per subscription.

**Acceptance:** open two clients on one symbol; show that exactly one upstream connection exists,
and that both clients receive the same tick. Report the two counts.

---

## Phase 4 — the three documents *(blocked — do not start)*

Watchlists, layouts and drawings, per account, as versioned JSON with last-writer-wins. These need
the answer to a question the owner has not settled: **which backend owns the account on the web**.
Do not begin, do not create the tables, and do not proxy `/api/auth/*` until they say.

There is **no broker account and no route for one**. `user/account/link`, `DELETE user/account` and
`user/copy-status` are not relayed. Copy trading was removed from the product; if you find yourself
adding one of those three, stop — it is a product decision, not a configuration one.

---

## Phase 5 — the candle archive *(ask first)*

A nightly pull into Postgres, one row per `venue, symbol, interval, openTime`, so a reader can pan
back further than either backend's own window. Worth doing; not worth doing without the owner
saying yes, because it is the first thing on this server that stores market data rather than
caching it.

---

## What to report, and how

After each phase: the acceptance output verbatim, what you changed, and anything that surprised you.
Keep it short. No progress reports between phases — finish the phase.

Stop and ask on: a backend that answers a different shape from `SERVER.md`, a missing fingerprint, a
short symbol list, anything in Phase 4 or 5, and anything that would change either live backend.

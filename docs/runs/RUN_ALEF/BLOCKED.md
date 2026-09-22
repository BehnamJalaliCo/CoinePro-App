# RUN א — blocked

**None left on a backend. Nothing here is waiting on anybody but the owner**, and what the owner
owes is two product decisions rather than a fix: `SERVER.md` §8.1 (which backend owns the account
on the web) and §8.2 (whether the terminal is open, member-only or a read-only guest page).

**§א20 and §א22 closed on 2026-09-21, and closed themselves.** Gold and silver are in
CoinePro-FX's snapshot *and* on its socket, and the `source` on every row is `finnhub` rather than
`yfinance`. **No message was ever sent to that team.** Both were written down here, measured, and
left alone on the principle that a symbol the feed does not list is a symbol the app must not
invent — and the backend moved on its own. The record of each is kept below with the measurement
that closed it.

**One thing each section leaves behind, and neither is a blocker:** `bid == ask == price` on all 19
rows, so this feed still carries no spread; and `CandleGateway.sourceName` still prints
«MetaTrader 5» beside a quote that now says Finnhub. Two venues on one screen is legitimate — the
candles and the quotes need not share a source — but only one of them is named. Both are in
`SERVER.md` §4.10.1.

§א19 (the update document) and §א21 (the Cloudflare certificate) are both closed — `pro-chart.com`
is live, and the work in this run is in front of readers. Every section is kept below with what
closed it, because a blocker's record is worth more than its absence.

---

## §א19 — the update document has to be served

**What cannot be done here.** `pro-chart.com` does not answer yet. Until it does,
`GET /api/app/latest` fails, the gateway answers null, the card never appears, and the app behaves
exactly as it did before this run — which is the honest fallback and not a fault, but it is also the
whole point of the run left undelivered.

**What is in the build meanwhile.** Everything on the app's side: the comparison, the refusals, the
card, the browser hand-off, the strings in both languages, the tests. The moment the document
answers, every install already carrying 5.0.0 starts checking. Nothing further has to be shipped to
the phone for the channel to open — **and that matters more than usual here**, because a build with
no update channel cannot ship the update channel to itself.

**What the owner (or the server's agent) must do.** `SERVER_BUILD_PROMPT.md` **Phase 1½**, which is
written to be handed over as it stands. In short:

1. Serve `GET /api/app/latest` — static JSON, `application/json`, cached a few minutes at most.
2. Decide whether the APK is served from `pro-chart.com/download/…` or the document points at the
   GitHub release. Either works; the first means the file and the document come from one host the
   reader is already trusting. This is a question for the owner and the brief says to ask it.
3. Write the document when a release goes out. Four fields the app enforces, and **a release that
   breaks any of them is silently not offered to anybody**:
   * `version_code` — the integer from `python3 scripts/release/version.py --code`;
   * `url` — HTTPS, host `pro-chart.com`, `www.pro-chart.com` or `github.com`;
   * `sha256` — 64 hex characters, the actual digest of the file at `url`, because the app shows it
     to the reader before they fetch it;
   * `mandatory` — a word on a card. Never a switch that stops the app working, and no future
     request should turn it into one.

**The acceptance check**, from the brief:

```bash
curl -s  https://pro-chart.com/api/app/latest | python3 -m json.tool
curl -sI https://pro-chart.com/api/app/latest | grep -i content-type
```

and the served `version_code` against `python3 scripts/release/version.py --code` for the published
tag.

### Served — and verified from this side

The Pro Chart machine built it. Every claim in its acceptance report was re-checked here against the
published artefacts rather than taken on trust, and all of it holds:

| claim | checked how | result |
| --- | --- | --- |
| `version_code: 50000004` | `aapt2 dump badging` on the release APK | `versionCode='50000004'`, `versionName='5.0.0+4'` — exact |
| `sha256: f6f9925…738c2` | `sha256sum` of the APK downloaded from the GitHub release | identical |
| the certificate it read from the APK | `apksigner verify --print-certs` on a *different* release (5.0.0+2) | `9612ab6c…fbd0`, `CN=CoinePro, OU=Mobile, O=CoinePro, L=Tehran, C=IR` — identical |
| `publishable → True` | the four rules in `AppUpdate.publishable`, read against the document | holds: HTTPS, host `pro-chart.com`, 64 hex, name and code present |

So the document is correct and the app would act on it. **What it is not yet is reachable**: the
origin answers 200 on `localhost` and `pro-chart.com` answers 526 from outside, because Cloudflare
refuses the origin's certificate. That is §א21, and it is the only thing between this work and a
reader seeing it.

The fingerprint no longer needs a phone to confirm. It was read three ways — by the server from the
APK's v2 signing block, by `openssl x509` independently of that parser, and here by `apksigner` on a
different release — and all three agree. `docs/release/DISTRIBUTION.md` §5 carries the value.

---

## §א21 — the Cloudflare origin certificate *(closed — 2026-09-19)*

**Closed.** The owner created it; the server installed it after checking the key and certificate
were a pair, and changed nothing else — verification stayed on, the origin was never given plain
HTTP, the zone went to Full (strict). Valid to 2041, covering `pro-chart.com` and `*.pro-chart.com`.

`pro-chart.com` is live. Measured from outside, through Cloudflare: the three legal pages answer
`200` with **zero redirects**, `assetlinks.json` answers `application/json` with the right
fingerprint, the update document answers and names an APK **byte-identical to the GitHub release**,
`www` `301`s to the apex, and the Phase-2 relay answers 857 crypto rows and 19 forex rows with gold
first. `SERVER.md` §4.8 is the table.

**This closes the oldest loose end in the product.** Every legal link in the shipping app has
pointed at `pro-chart.com/legal/…` since 4.47.0, and until today a reader who tapped «قوانین» got a
browser on a host that did not answer. `docs/release/DOMAINS.md` said so for a year; it does not any
more.

The rest of this section is kept as written, because the reasoning is what made it one ask rather
than a week of guessing.

**What could not be done here, or on the server.** `pro-chart.com` is behind Cloudflare, and Cloudflare
will not talk to an origin whose certificate it does not trust: that is what a **526** is. Let's
Encrypt cannot complete a challenge through an orange-clouded record, so the origin needs a
**Cloudflare Origin CA** certificate, which only the zone's owner can create.

**What the owner does**, once:

> Cloudflare dashboard → the `pro-chart.com` zone → **SSL/TLS → Origin Server → Create
> Certificate**. Accept the defaults, hand the **certificate** and the **private key** to the
> server. Then set **SSL/TLS → Overview → Full (strict)**.

The server puts them in `caddy/origin/` and serves `tls <cert> <key>`. It has been told not to make
the 526 disappear any other way — disabling verification, plain HTTP to the origin, or a Flexible
zone each hide it while leaving the hop unencrypted, on the host that serves this product's legal
pages.

**One consequence, and it is already a rule.** A Cloudflare-fronted `pro-chart.com` must never be
certificate-pinned in the app: Cloudflare rotates the edge certificate on its own schedule and a
pinned build cannot be told. `docs/release/DOMAINS.md` and `docs/security/PINNING.md` both say so —
the same reason `coineprofx.com` is not pinned. Nothing needs changing; it needs not being
forgotten.

---

## §א20 — gold is not in CoinePro-FX's snapshot, so it is not in the app's forex market list

> **CLOSED 2026-09-21, by the backend, unprompted.** The bare `GET api/ws/snapshot` now returns
> **19** with `XAUUSD` and `XAGUSD` among them — the same 19 `api/public/prices/live` serves, so
> the two routes finally agree — and the forex socket carries **9** rather than 7, the seven majors
> plus gold and silver. The headline instrument has a live stream. Measured from here against
> `coineprofx.com`, not read from a report. `MarketCatalogGateway`'s KDoc keeps both dates.


**Found while correcting `SERVER.md` against the live backends, and it is a product fault rather
than a specification one.**

Measured from here on 2026-09-18, against `coineprofx.com` over the public internet:

```
GET api/ws/snapshot          → 200, 17 symbols, NO XAUUSD, NO XAGUSD
GET api/public/prices/live   → 200, 19 symbols — the same 17 plus XAUUSD and XAGUSD, first in the list
```

The 17 are the majors, three indices and crude: AUDJPY AUDUSD DE40 EURAUD EURGBP EURJPY EURUSD
GBPJPY GBPUSD NAS100 NZDUSD US30 US500 USDCAD USDCHF USDJPY XTIUSD.

**Why this reaches the reader.** `MarketCatalogGateway` builds the forex catalogue from
`ws/snapshot` **and from nothing else** — there is no bundled fallback list, deliberately, because a
hand-written symbol list is exactly what that class was written to remove. So the metals are absent
from the phone's forex market list today.

And run Ψ narrowed the forex signal list to gold: `ForexSignalScope` shows `XAU*` and withholds
everything else. Put the two together and **a reader can be shown a gold call and find no gold
market to open**. The signal detail's chart, the search screen, the watchlist: none of them can
offer a symbol the catalogue does not contain.

**What is in the build meanwhile.** Nothing, on purpose. The app must not invent a market the feed
does not list — that is the rule the catalogue exists to enforce, and breaking it here to paper over
a backend gap would put a symbol on screen with no price behind it. `MarketCatalogGateway`'s KDoc
now records the measurement instead of the claim it used to make.

**What the owner must ask CoinePro-FX's team**, and it is one line:

> `api/ws/snapshot` with no `symbols` should return the same set as `api/public/prices/live` —
> 19 symbols, including XAUUSD and XAGUSD. It returns 17 and omits both metals.

**A third witness, found on 2026-09-19.** `api/public/signals/stats` on that same host reports
`symbols_covered: 19`. So the desk's own count of its forex universe is nineteen; `prices/live`
serves nineteen; and the snapshot the app reads serves seventeen. The odd one out is the route the
phone depends on, which is the whole of the ask.

**And a second sentence, from the socket.** Phase 3 on the Pro Chart server found that the forex
**stream** carries **seven** symbols — AUDUSD, EURUSD, GBPUSD, NZDUSD, USDCAD, USDCHF, USDJPY. No
metals, no indices, no crude, no crosses. So there are three universes on one backend: **19** from
`prices/live`, **17** from `ws/snapshot`, **7** from the socket.

For gold that means it is missing twice over: no row in the snapshot the app's catalogue is built
from, and **no live tick even for a client that knows its name**. A subscriber to `XAUUSD` gets the
opening value and never another frame. So the ask is really two lines:

> `ws/snapshot` and the price socket should both carry the same set `public/prices/live` does — 19
> symbols, XAUUSD and XAGUSD among them. Today the snapshot serves 17 and the socket serves 7, and
> gold is in neither.

No app release is needed once it does: the catalogue is fetched, not compiled in, so gold appears
the next time the screen is opened.

**Until then**, `SERVER.md` §4.1 tells the web relay to read `api/public/prices/live` rather than
the snapshot, so the browser will have gold before the phone does. That is worth knowing and it is
not a reason to delay the ask.

---

## §א23 — the pins, and the one digest that decides whether 4.88.0 is bricked *(closed — 2026-09-22)*

> **CLOSED. Pinning is not the cause, and it is not close to being the cause.** The owner measured
> the leaf's SPKI from outside this container:
>
> ```
> RO8XwxTQmKWLxQ7Ij7dkTd5vWTS4aC2pROWNg3Sh25c=
> ```
>
> **Byte for byte the pin that shipped at 4.57.0** — so the server's certbot still has
> `reuse_key = True` and the primary pin has survived every renewal since. And the chain ends where
> the backstops are:
>
> ```
> 0 leaf  tradeyar.trade-future.ir   ← YE2
> 1       Let's Encrypt YE2          ← ISRG Root YE
> 2       ISRG Root YE               ← ISRG Root X2
> 3       ISRG Root X1
> ```
>
> `ISRG Root X2` and `ISRG Root X1` are both pinned, so **two independent things** would have had to
> fail before an install refused this server: the leaf key *and* the root. Neither did. Every build
> since 4.57.0 reaches this host, and «پاسخی نرسید» came from somewhere else — §א24.

### One thing the chain shows that did not exist when the pins were measured

`ISRG Root YE` sits between `YE2` and `X2`, cross-signed by X2 so that today's devices path-build to
a root they already trust. **That cross-signature is what the pins are currently relying on.** The
day an Android trust store ships `Root YE` as an anchor of its own, a device may build the short
path — leaf → YE2 → Root YE — and stop there, with neither X1 nor X2 anywhere in the verified
chain. The leaf pin would be the only one left holding, and the leaf pin is one `reuse_key = False`
away from being wrong.

It is not urgent: an Android trust store is years old by the time it is in most hands, and
`DEFAULT_CERTIFICATE_PINS_UNTIL` bounds any mistake at 2027-03-01 regardless. It is also one line.
**What it needs is the digest, measured rather than guessed** — the same command, run against the
third certificate in the chain. Until somebody measures it, the pin is not written, because a pin
this repository invented is the one failure with no remote fix.

---

### The section as it stood while it was open


**Opened 2026-09-22, after the owner measured the certificate from outside this container.**

```
issuer=C = US, O = Let's Encrypt, CN = YE2
subject=CN = tradeyar.trade-future.ir
```

**What that settles.** The certificate is Let's Encrypt's, issued for the host itself, and the
handshake completes. The server is healthy and reachable. It also **withdraws** the reading this
run reported on the same day: through the sandbox's egress proxy the subject read
`CN = *.trade-future.ir`, and a move to a wildcard was offered as the likely reason a leaf pin had
stopped matching. There is no wildcard. The proxy re-terminates TLS and does not copy the subject
faithfully either — so through it, **only the HTTP body is evidence.** That is the third instrument
error in three days and the first one that reached a report.

**What it does not settle, and this is the whole question.** A pin is a digest of a **key**. An
issuer name is not one and a subject is not one. `DEFAULT_CERTIFICATE_PINS` holds four digests for
this host:

| Pin | What it is | Holds when |
| --- | --- | --- |
| `RO8XwxTQ…` | the leaf's SPKI, measured at 4.57.0 | the server's certbot still has `reuse_key = True` |
| `Q1JB2C45…` | their offline backup key | that key is what a rotation moves to |
| `C5+lpZ7t…` | ISRG Root X1 | the chain still ends at Let's Encrypt's RSA root |
| `diGVwiVY…` | ISRG Root X2 | the chain still ends at Let's Encrypt's ECDSA root |

`YE2` is an intermediate this repository has never measured. If it chains to X1 or X2, the roots
catch it and **nothing is wrong** whatever the leaf key did. If it chains to anything else, every
install since 4.57.0 has been refusing this server, and that is «پاسخی نرسید».

**The command, on any machine that is not this one:**

```bash
host=tradeyar.trade-future.ir
openssl s_client -connect "$host:443" -servername "$host" </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | openssl enc -base64
openssl s_client -connect "$host:443" -servername "$host" -showcerts </dev/null 2>/dev/null \
  | grep -E "^ *[0-9]+ s:|^ *i:"
```

The first prints the leaf's SPKI digest — compare it to `RO8XwxTQmKWLxQ7Ij7dkTd5vWTS4aC2pROWNg3Sh25c=`
as `app/build.gradle.kts` spells it. The second prints the chain, which names the root.

**Neither outcome needs a guess afterwards.** A match means pinning is not the cause and the Cafe
Bazaar failure is something else. A mismatch means the pins are replaced from that measurement and
shipped, and `app/src/main/res/xml/network_security_config.xml` moves with them —
`NetworkSecurityPinsTest` holds the two lists equal.

---

## §א24 — what is left of the Cafe Bazaar refusal, now that pinning is ruled out

**Everything the app could be wrong about has been measured and is right.** The server answers
(`/api/mobile/v1/auth/methods` → `200` with real JSON, measured from here). The certificate is
valid, for this host, from Let's Encrypt. The pinned leaf digest matches byte for byte and the
chain ends at two pinned roots. The three sign-in faults found on 2026-09-22 are fixed and shipped
in 5.0.4.

So the reviewer's «پاسخی نرسید» was an `IOException` that was **neither a pin nor a dead server**.
What is left is the path between their device and `trade-future.ir`, and that is not something this
repository can measure: every probe here leaves through a proxy in another country.

**The hypothesis, and it is the last one standing:** a Cafe Bazaar reviewer opens the app on an
Iranian mobile network, and `tradeyar.trade-future.ir` does not answer from there — filtered,
rate-limited by the carrier, or simply unreachable on that route. Nothing in the app would look
different from a server being down.

**The test, and it needs a phone in Iran on mobile data — not Wi-Fi, and no VPN:**

```
https://tradeyar.trade-future.ir/api/mobile/v1/auth/methods
```

opened in the phone's browser. JSON means the route is fine and the hypothesis is wrong. A timeout,
a reset or a filtering page means it is right, and the fix is not in the client.

**If it is right, the fix is already specified and is not a workaround.** `ACCOUNT.md` has Pro Chart
owning identity, and `pro-chart.com` is behind Cloudflare — which is reachable where a bare origin
may not be. Moving sign-in onto Pro Chart's own route is `SERVER_BUILD_PROMPT.md` Phase 4, decided
on its own merits before any of this, and it happens to be the thing that makes the store's
reviewer able to sign in.

**What 5.0.4 already does for the case where it is right.** The screen no longer goes blank: e-mail
is offered, the failure stays visible beside it, and the three causes now read as three different
sentences instead of one. A reviewer sees an app that tried and said why, rather than an app with
no way in.

---

## §א22 — the forex prices are Yahoo Finance, and the chart beside them says MetaTrader 5

> **HALF CLOSED 2026-09-21.** `source` is now **`finnhub`** on all 19 rows, so the venue question is
> answered and `SERVER.md` §1's year-old «over Finnhub» is true again. The app needed **no change**:
> `MarketDataController` has always mapped the string `finnhub`, so these quotes move from
> `QuoteSource.UNKNOWN` to `FINNHUB` and inherit the 90-second staleness window instead of 30 —
> which is what refusing to add a `YFINANCE` enum bought, since the app had not encoded somebody
> else's mistake.
>
> **CLOSED on the app's side 2026-09-22 (5.0.2).** The half that was ours was worse than «unnamed»:
> the caption read «منبع قیمت: MetaTrader 5» — *price* source — from `CandleGateway.sourceName`,
> which names the candles. Both venues are now named, each read off its own feed: the bars' from the
> gateway, the price's from `QuoteSource.displayName` carried on `PriceTick.sourceName`. Same venue
> prints one line; an unrecognised `source` prints none, because `UNKNOWN`'s name is empty rather
> than «نامشخص». No venue is assumed from the platform.
>
> **Still open and not a blocker, and it is the backend's:** `bid == ask == price` on 19 of 19, so
> this feed carries no spread. §4.10.1.


**Measured, not inferred.** Every row of `coineprofx.com/api/ws/snapshot` on 2026-09-19:

```json
{"symbol":"EURUSD","price":1.1490291357040405,
 "bid":1.1490291357040405,"ask":1.1490291357040405,
 "ts":1789857617149,"source":"yfinance"}
```

`source: "yfinance"` on all seventeen, and **`bid == ask == price` exactly** — one number echoed
into three fields, so the feed carries no spread. The crypto side, for contrast, reports
`source: "lbank-ws"` and means it. `SERVER.md` §1 has called this backend «gold and the dollar, over
Finnhub» for a year; that line is now corrected.

**Why this is a product question and not a note.** The app prints the provenance of its forex
candles, and `CandleGateway.sourceName`'s own KDoc says why:

> The loudest accusation in Persian-language reviews of this whole category of app is «کندل‌سازی» —
> that the broker manufactures its candles. […] So every gateway names its venue, the chart prints
> it, and the claim becomes falsifiable — a reader can hold this chart against that venue's own.

The forex gateway names **«MetaTrader 5»**, and describes those bars as «the prices the copied
account trades at, not an index or a composite». On one screen today, the candles say MetaTrader 5
and the last price above them comes from Yahoo Finance, with nothing saying so. A reader who took
the app's own advice and held the chart against MT5 would be comparing two different venues.

**What the app does with it now**, and it is safe but unlabelled: `MarketDataController` maps a
source containing `finnhub` or `lbank` by name and everything else to `QuoteSource.UNKNOWN`, which
carries a 30-second staleness budget. So `yfinance` is already treated as an unknown venue rather
than mislabelled. Nothing is wrong on screen; nothing is named either.

**What is deliberately not done here.** Adding `YFINANCE` to `QuoteSource` would be this run
deciding that Yahoo Finance is an acceptable quote source for a paid forex product, which is the
owner's decision and not a client's. Renaming the chart's source label would be worse — inventing
provenance is the fault the label exists to prevent.

**What the owner must ask**, and it is one question with two acceptable answers:

> Which venue do `api/ws/snapshot` and the price socket quote from? They report `source: "yfinance"`
> with `bid == ask`. The app's chart names MetaTrader 5 as the candle source. If the quotes really
> are Yahoo Finance, the two do not match and the app is labelling one screen with two venues; if
> they are MT5 and the field is stale, the field should say so.

Once answered, the app's side is small: either `QuoteSource` gains a named venue and the chart's
label is qualified, or nothing changes because the field was simply wrong. Either way the app must
not guess.

---

## Still open from earlier runs

Nothing here changes any of them; they are listed so this file is the one place to look.

* **`RUN_PSI/BLOCKED.md §Ψ10`** — the three product questions that gate the server's later phases:
  which backend owns the account on the web, whether the terminal is open or member-only, and
  whether the candle archive is built on day one. The machine now exists, so these are next.
* **`RUN_XI/BLOCKED.md §Ξ21`** — whether the signals route can distinguish «entitled but the
  account is not linked». One field on a 403 body.
* **`RUN_T2`'s backlog** — B6 offline-as-a-state, B7 the single-symbol widget, B8 Picture-in-Picture,
  C1's surface, C2's two gaps, C3, C4, C6. None needs a backend.
* **The `assetlinks.json` fingerprint itself.** No longer a question of *where to find it* — §א13
  settled that — but the owner still has to hand the value to whoever configures the host. The app's
  «ایمنی و انتشار» screen prints it with a copy button.

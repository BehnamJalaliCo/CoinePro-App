# RUN א — report

One message, two halves, and the second half was not an instruction at all:

> «کپی ترید البنک میماند و در ضمن گوگل پلی به ما ایرانیا خدمات نمیده در حال ایمیل زدن و مکاتبه
> هستیم باهاشون»

The first half is an answer to a question this project asked at the end of run Ψ and it takes two
paragraphs to record. The second half is a statement about the world — «Google Play does not serve
us Iranians; we are corresponding with them» — phrased as an aside. Most of this run is what that
aside turned out to mean.

---

## 1. LBank copy trading stays, and the asymmetry is correct

`RUN_PSI/BLOCKED.md §Ψ16` set out two branches and asked for one word. The answer is the second:
copy trading on LBank is the service's, reached from the web panel, and this app simply does not
carry it. **Nothing was edited**, which is the whole of the work and is why the row now reads ✅ with
«the evidence is that the diff is empty».

What is worth writing down is the thing a future reader will trip over. The app has no copy trading
anywhere in it, and `docs/legal/TERMS.md` §6-3 describes some. That looks exactly like an oversight
and it is not:

**The terms are the service's, not this binary's.** They are the same document the web panel serves.
A reader who agrees to them really can have the feature — on LBank, through the panel — and a
document that described only what one client happens to draw would be the wrong document to put in
front of them. The membership gate's note is the one place a reader could be misled and it is not:
it says the membership includes it, which is true.

---

## 2. «Google Play does not serve us» — what it actually costs

The owner mentioned it in passing. Read properly it is a statement about how the product reaches
every one of its readers, and three separate things in this repository were quietly assuming
otherwise.

`docs/PLAY_COUNTRIES.md` had already done the reading, months ago, off Google's own pages: Iran is
not a country a Play Console developer account may be registered from, and under the sanctions
regime Play does not serve installs into the country either. Two refusals, and the second cannot be
worked around at all — even a listed app would not reach the readers this one is written for.

So `docs/release/DISTRIBUTION.md` is new, and it is the document that now describes how the product
ships. The Play documents are kept and re-framed as preparation rather than description, because the
owner is corresponding with Google and the day that succeeds they are the day's work already done.

### The hole, which nobody had noticed

An app store does four things a reader never thinks about. Three of them this app already did for
itself. The fourth it did not do at all.

| what a store does | before this run |
|---|---|
| vouch that the package is the publisher's | `AppIntegrity.check` — a repackaged copy refuses to run |
| let somebody verify what they installed | `AppIntegrity.fingerprints` — **written, and called by nothing on the safety screen** |
| distribute the file | GitHub Releases, signed by CI |
| **tell the reader a newer build exists** | **nothing. Not a line of code.** |

`grep -rn "AppUpdate\|updateAvailable\|latestVersion\|versionCheck\|InAppUpdate"` over
`app/src/main core feature` returned nothing.

That is not a missing nicety. It means an old build is **silently permanent**: the reader has no
reason to look, nothing tells them, and a fix shipped today reaches only the people who happen to
read the channel it was announced on. Every release makes the gap wider. It is the one part of the
store that has to be rebuilt rather than done without, and it is the largest thing in this run.

---

## 3. What was built

### `:core:update` — a decision, not a downloader

Three files and a test. The whole of the comparison is:

```kotlin
fun decide(installed: Long, release: AppRelease?): AppUpdateStatus = when {
    release == null -> AppUpdateStatus.Unknown
    !publishable(release) -> AppUpdateStatus.Unknown
    release.versionCode > installed -> AppUpdateStatus.Available(release)
    else -> AppUpdateStatus.Current
}
```

Four decisions in it, each of which could have gone the other way:

**Only `versionCode` is compared.** It is the integer the package manager itself orders installs by,
and `scripts/release/version.py` derives it from the name for exactly that reason. A published
release whose *name* reads newer over a *code* that is not changes nothing — there is a test for it,
because that is the shape a release-process mistake takes.

**An offer is refused rather than trusted.** `publishable()` requires HTTPS, a host on
`PUBLISHING_HOSTS`, a 64-character digest and a finished record. The reason this route gets an
allow-list when nothing else in the app does is that **every other response the app handles ends up
as text on a screen, and this one ends up as an installable package on somebody's phone**. A host
that could name any address could send a reader anywhere with the product's own voice behind it,
which is a better phishing page than a phishing page. The `https://pro-chart.com@evil.example/x`
case is in the test, and it is why the URL is parsed by hand rather than trusted to `startsWith`.

**Every refusal is `Unknown`, which draws nothing.** Not an error, not a toast. The reader did not
do anything wrong and cannot do anything about it, and the three ways of knowing nothing — not asked,
no answer, unusable answer — are one state because no reader has a use for the difference.

**The app does not download and does not install.** That would mean holding
`REQUEST_INSTALL_PACKAGES` — permission to put *any* package on the phone — plus a download manager,
a file provider and a directory of half-fetched APKs, purchased to save the reader three taps they
have already performed once, on the day they installed this app. The button opens the browser. The
digest is on the card so they can check what arrives.

And one thing that is not in the code and is the most important: **`mandatory` changes a sentence and
never a door.** A flag on a host that can stop an installed app from opening is a remote kill switch,
and a kill switch is one compromised host away from being everybody's app at once. A reader on an
old build is a reader this product still owes a working chart to.

### The card, and why it is on the safety screen

«ایمنی و انتشار» — *safety and release* — already existed and is already the screen that answers
«what is this app, what does it do to me, what version am I on». An update card is the same
question's other half, and a reader who opened that screen is asking rather than being interrupted.

It is also why the check fires there rather than at launch: one request per visit to one screen,
instead of one per cold start on networks where a request costs something, for a fact that changes a
few times a year.

Silent in both non-states. That is the property that lets it live on a screen every reader visits,
and it is the one a screenshot cannot prove, so `AlefProofTest` asserts it twice — each time against
a render that first proves it is the right screen, because otherwise «no update card» is satisfied by
an empty one.

### The client with no credential on it

A third OkHttp client, built with no `bearerToken`. Both existing clients attach a session token to
everything they carry; the brand host is neither platform's, so a call on either of them would hand
that platform's bearer token to a machine with no business holding one. It is the same argument that
already keeps the forex and crypto clients apart, applied one host further out. Unpinned, too:
pinning a host whose certificate this repository has never seen is how an app locks itself out of
its own server.

---

## 4. `assetlinks.json`, where the bad news makes something simpler

This is the one place the absence of a store *helps*.

With Play App Signing, Google re-signs an upload with a key only they hold, so the fingerprint that
verifies an App Link is theirs rather than the one in the keystore. `print-assetlinks.sh` carried a
warning about it, `SERVER.md` §8 listed «the App Signing certificate's SHA-256, from Play Console»
among the things the owner must supply, and `SERVER_BUILD_PROMPT.md` told the server's agent to ask
for it from a console the owner may never have.

With no Play in the path there is no re-signing. **The key that signs the build is the key on the
phone**, so the fingerprint is the release keystore's own — which the owner can already produce, and
which `print-assetlinks.sh` has been printing all along.

And now the shortest route of all: the safety screen prints the certificate of the running install,
SHA-1 and SHA-256, with a copy button. `AppIntegrity.fingerprints` was written for this and was
called by nothing on that screen; `LaunchReadinessScreen` carried **a parameter's KDoc with no
parameter under it**, explaining at length why the fingerprint belongs on the glass. That
documentation is now true.

Four files said the old thing and now say the new one, each with the reasoning rather than the
instruction, because an instruction without its reason is the thing that goes stale next time.

---

## 5. What the server now has to serve

`SERVER.md` §4.6 is new and it is the one route on that machine which is **not** a relay. Nothing
upstream knows or should know what the Android release is: TradeYar serves crypto and CoinePro-FX
serves forex, and neither has any business holding the version number of an Android build. It is a
static JSON file the release process writes, plus optionally the APK beside it.

In `SERVER_BUILD_PROMPT.md` it is **Phase 1½**, between the legal pages and the relay. Placed there
on an argument rather than by taste: it is the only step whose absence gets *worse* over time.
Everything else on that server is a capability the product does not have yet; this one is a debt
that compounds with every release that ships without it.

The brief states the four rules with their consequence attached — a release that breaks any of them
is **silently not offered to anybody** — because a server author who does not know that will ship a
document with a missing digest and spend a day wondering why no phone reacts.

---

## 6. 5.0.0, and why it is not 4.100.0

The version scheme reserves two digits for MINOR:

```
versionCode = MAJOR×10,000,000 + MINOR×100,000 + PATCH×1,000 + BUILD
```

4.99.0 is the last of the fours, and `version.py --bump minor` refuses rather than producing a
number that would collide with major 5. So the major bump is the scheme deciding, not taste.

That it lands on the release where the product stops waiting for an American store and starts
distributing itself is a coincidence. It is a convenient one, and it is the honest thing to put in
the release note.

---

## 7. The server answered back, and four rows of the spec were wrong

`docs/web/SERVER.md` was written in run Ψ against a machine that did not exist. It now exists, its
agent finished Phase 0 and Phase 1, and it reported four disagreements with the live backends. All
four were real, and they share one cause worth stating plainly:

**The spec was written from what the Android app calls, and the Android app signs in.**

Every route in §4 was lifted from a gateway in this repository. Those gateways carry a bearer token,
because the app has an account by the time they run. A Phase-2 relay has no account by definition —
so it met `401 TYR-004 Auth Token Missing` on route after route and read that as a blocker. It is
not a blocker. It is the authenticated surface doing its job, and there is a public surface beside
it that the spec never mentioned.

New `§4.0` says so, and the useful part is that **the app already has a client for it**. `:core:guest`
exists because a reader with no account still has to see markets: `GuestApi` reads
`api/v1/public/prices` and `api/v1/public/candles/{symbol}`, and `GuestMarketGateways.kt` adapts them
to the *same two interfaces* the signed-in chart uses, so the whole surface works with no `if (guest)`
anywhere in it. **The web's Phase 2 is the guest tier.** That is not a compromise forced by a 401;
it is the same answer the phone reached two runs ago, and it means Phase 2 does not wait on
`PLAN.md` §6.2 at all — that question gates the account, and the guest tier does not have one.

The other three were flat errors:

* **§4.1's FX candle row named a TradeYar route.** `api/v1/public/candles/{symbol}` is TradeYar's;
  it had been copied into the forex column. The app's own forex candles come from
  `academy/chart/{symbol}`, behind a second token minted from the mobile one, which no relay without
  an account can hold. The public route is `api/public/prices/series`, and it is narrower in ways the
  terminal has to know rather than the relay hide: **four timeframes** (M15, H1, H4, D1 — measured;
  M5, M30 and W1 all answer `422`), `limit` bounded 20–400, `t` an ISO-8601 string, no volume and no
  paging. Four rather than the five the academy route serves: **the public route is the academy route
  minus the five-minute bar.**
* **§4.3 was Phase 4 wearing Phase 2's clothes.** `public/signals/*` sits under a path called
  *public* on CoinePro-FX and is still behind VIP — `EndpointCatalog` says exactly that, and the live
  server agrees. Same for the calendar and both market-intelligence routes. What Phase 2 *can* carry
  is now its own table, and the interesting row in it is `api/demo/signals`: a badly named route
  whose every row is a real published signal that has already closed, with the outcome it banked. A
  track record, not a demonstration, and the only signal content this product shows a stranger.
* **Rule 6 was half true.** «Both backends answer the bare call with everything they quote» — and
  that is the one that mattered.

### The bare snapshot, and the gold that is not in it

TradeYar answers the bare call with 857 symbols. CoinePro-FX answers with **seventeen**, and the two
it omits are **XAUUSD and XAGUSD**. `api/public/prices/live` on the same host answers nineteen, gold
and silver first in the list. Measured from here, independently of the server, and recorded with the
date in `SERVER.md` §4.7 so the next reader argues with a number.

That is not a spec bug. `MarketCatalogGateway` builds the app's forex catalogue from `ws/snapshot`
**and nothing else** — deliberately, because a hand-written symbol list is the thing that class was
written to abolish. So the metals are absent from the phone's forex market list today.

And run Ψ narrowed the forex *signals* to gold. Put the two together: **a reader can be shown a gold
call and find no gold market to open.** The chart on the signal detail, the search screen, the
watchlist — none of them can offer a symbol the catalogue does not contain.

Nothing in the app may fix it, and that restraint is the point. Inventing a market the feed does not
list is precisely the fault the catalogue exists to prevent, and a symbol on screen with no price
behind it is worse than a short list. What changed here is the KDoc that claimed both backends
answer alike; it records the measurement now. `BLOCKED.md §א20` carries the ask, and it is one line
to CoinePro-FX's team — after which no app release is needed, because the catalogue is fetched.

### Two things only the owner can do

The server asked for the **App Signing SHA-256 from Play Console**. It had already been corrected
once, in Phase 1; it is now **rule 7** at the top of the brief as well, where a reader meets it
before any phase. There is no Play in this product's path, nothing re-signs the upload, and the
value is the release keystore's own — which the app prints on its own «ایمنی و انتشار» screen with a
copy button.

And `pro-chart.com` answers **526**: Cloudflare reached the origin and refused its certificate.
Let's Encrypt cannot complete a challenge through an orange-clouded record, so the answer is a
Cloudflare **Origin CA** certificate from the owner's dashboard. The new Phase 1 step says so, and
says plainly what not to do — turning off verification, serving plain HTTP to the origin, or
switching the zone to Flexible each make the 526 disappear while leaving the hop unencrypted, on the
host that serves this product's legal pages. It also records the consequence that was already a
rule: a Cloudflare-fronted `pro-chart.com` must never be certificate-pinned, for the same reason
`coineprofx.com` is not.

---

## 8. The host answers

The owner made the Origin CA certificate; the server installed it after checking the key and the
certificate were a pair, and disabled nothing to do it. `pro-chart.com` is live, and with it Phases
1, 1½ and 2.

Everything worth saying about that was measured from outside, through Cloudflare, rather than read
from the server's report — `SERVER.md` §4.8 is the table. But one line of it is not a measurement,
it is the end of something:

**Every legal link in the shipping app now opens a page.** Those three addresses have been compiled
into the binary since 4.47.0. `docs/release/DOMAINS.md` has carried the sentence «a legal link from
inside the app opens a browser on a host that does not answer» for a year, under a status column
reading **not serving**. A reader who tapped «قوانین» got a dead host, and the only reason it was not
worse is that the documents are bundled and the in-app reader shows them anyway. That sentence is
gone from the document because the fact is gone from the world.

The rest holds end to end. `assetlinks.json` answers as `application/json` with no redirect and
carries the release keystore's own fingerprint — checked here against `apksigner`'s reading of the
APK, not against the server's report of it — so the App Link on `/reset` can verify. The update
document answers, its notes are byte-identical to `UPDATE_NOTES.md`, and the APK it names is
byte-identical to the GitHub release. Four artefacts, one chain, no weak link in it.

And the relay carries 857 crypto rows and 19 forex rows with gold first: the same two numbers §4.7
measured against the backends directly, so nothing was lost in the hop.

### The row worth dwelling on

`M5`, `M30` and `W1` come back from `/api/fx/candles` as the backend's own `422`, with the backend's
own message. **The relay does not translate a refusal.** It would have been easy and superficially
kind to serve the nearest timeframe it could — and the result would be a chart of the wrong bars
with nothing anywhere saying so, which is the exact failure this product spent run Ψ removing from
its own client.

### Where the implementer was right and the spec was wrong

§4.1 said to cache candles keyed `venue:symbol:interval:openTime`, one entry per bar. The server
declined, and its reason is better than the rule: rebuilding a response body out of individually
cached bars means the relay has to write `server_time_ms` itself, and **rule 1 is that this server
is never the author of any number.** Caching the body whole, unmodified, with a TTL that runs to the
moment the newest bar closes gets exactly the effect §4.1 wanted — 657 ms cold, 3.5 ms warm, a
hundred tabs on one symbol becoming one upstream call an hour — without the relay acquiring a second
contract to keep in step.

So §4.9 records the decision and the spec now matches the implementation. That is the right
direction of travel when the implementer's argument is the better one, and it is worth saying
plainly: the rule existed to prevent a class of fault, the implementation found a way to prevent it
harder, and a spec that insisted on its own wording would have made the product worse.

### Two small things

A health check that reported a fault which was not there: TradeYar's `/healthz` answers `307` to a
login page, and a relay that does not follow redirects reads that as «degraded». The probe moved to
`api/v1/system/health`. Worth recording because a monitor that cries wolf teaches everybody to
ignore the one that is telling the truth.

And one thing still to change, named rather than fixed because it is the server's: `/api/health`
prints each upstream's private address to the public internet. Neither is a secret and neither is
reachable from outside, but it is a network diagram handed to anybody who asks, and the fields a
reader actually needs — reachable, probe, latency, cache rate — are all there without it.

### A third witness for the gold

`public/signals/stats` on CoinePro-FX reports **`symbols_covered: 19`**. So the desk's own count of
its forex universe is nineteen; `prices/live` serves nineteen; and `ws/snapshot`, the one route the
Android app builds its forex market list from, serves seventeen and drops exactly the two metals the
product's forex side is about.

That is now the only blocked item in this run, and it is one line to one team.

---

## 9. The socket, and what it found behind the two backends

Phase 3 is built and it is **forex-complete and crypto-closed**. Both halves of that sentence are
facts about the backends rather than about the relay, and the second one is the same wall §4.0
described: the crypto socket is the authenticated surface. Seven candidate paths on TradeYar — two
time out at the handshake, three answer `404`, and the two that resolve close `4001` and `4401
unauthorized`, the latter being the one the Android app itself opens. §4.2 said «one upstream
connection per venue»; for crypto, without an account, the number available is **zero**, not one.

What the relay does about that is the part worth keeping. It declares the venue and says it is not
live, in the first frame every client receives:

```json
{"type":"welcome","venues":{
   "forex":  {"live": true,  "reason": null},
   "crypto": {"live": false, "reason": "upstream requires an account (PLAN.md §6.2, Phase 4)"}}}
```

**The difference between a chart that says «there is no crypto feed» and a chart that simply never
ticks is that one frame.** This product has spent three runs removing silent failures from its own
client; it would have been absurd to accept one in its own protocol.

### The question, and why the answer is «wait»

The relay could bridge crypto onto the socket: poll the public prices route every two seconds and
push the differences. It asked rather than deciding, which was right, and the answer is no.

Not for effort — the values would be relayed unchanged, and rule 1 would survive that reading. The
problem is what a tick *is*. A tick asserts **when something happened**. A poll can only assert «my
two samples, two seconds apart, differed», so the frame would have to carry either the poll's own
clock — a number this server authored — or the upstream's timestamp on a frame arriving up to two
seconds late with nothing saying so. Either way the server becomes the author of something, and the
phone's contract of «snapshot, then the venue's stream» would quietly mean two different things on
the two platforms.

And refusing costs nothing. **A tab polling `/api/crypto/prices` every two seconds is the same
upstream traffic as the bridge**, because the relay's two-second cache collapses a hundred tabs into
one call either way. The only thing that changes is whether the browser is told the truth about what
it is receiving. When a design would make something up, the first question is what refusing costs;
here the answer was nothing at all.

### A third universe, and gold is in none of the small ones

The forex socket carries **seven** symbols: the majors. No metals, no indices, no crude, no crosses.
So one backend now presents three different answers to «what do you quote?» — **19** from
`prices/live`, **17** from `ws/snapshot`, **7** from the socket.

For gold that is a second absence on top of §א20's first. It has no row in the snapshot the app's
catalogue is built from, and **no live tick even for a client that knows its name**: a subscriber to
`XAUUSD` receives its opening value and never another frame. The ask to that team is two lines now.

### The thing nobody had read

Every row of that snapshot carries `source: "yfinance"`, and `bid == ask == price` exactly — one
number echoed into three fields, so the feed has no spread in it. `SERVER.md` §1 has described this
backend as «gold and the dollar, **over Finnhub**» for a year. Nobody had checked.

It matters because of a promise the app makes on purpose. `CandleGateway.sourceName` exists so the
chart can print where its bars come from, and its KDoc is explicit about why: the commonest
accusation against this category of app is «کندل‌سازی», and the only answer to it is provenance a
reader can verify. The forex gateway names **MetaTrader 5** and describes those bars as the prices
the copied account actually trades at.

So on one screen today the candles say MetaTrader 5 and the last price above them is Yahoo Finance,
with nothing saying so. A reader who took the app's own advice — hold this chart against that
venue's own — would be comparing two different venues and concluding the wrong thing about both.

Nothing was done about it in code, and that restraint is the answer rather than an omission. The app
already files these quotes under `QuoteSource.UNKNOWN`, so nothing on screen is *mislabelled*;
adding `YFINANCE` to the enum would be this run deciding that Yahoo Finance is an acceptable quote
source for a paid forex product, and renaming the chart's label would be inventing provenance, which
is the exact fault the label was built to prevent. `BLOCKED.md §א22` is the question, with the two
answers that are both fine and the one thing the app must not do: guess.

### Two small keepers

`/api/health` no longer prints the upstreams' private addresses — asked for in §4.9, done.

And a measurement the server needed in order to test at all, kept because it explains a frozen
chart: **the forex market is shut at the weekend.** Upstream sends a frame a second with unchanged
values — 45 seconds, 45 frames, zero change — and re-samples about every two minutes, which is when
the timestamp moves. The relay forwards changes rather than frames, so a closed market costs nothing
downstream, and a tick is real even when its price has not moved because nothing is trading.

---

## 10. The number the relay's author was right to question

The server did the arithmetic on §6 and did not change the figure, because §6 is the spec and the
spec was not theirs to edit. That was the correct instinct and it surfaced a mistake of mine.

§6 said **60 requests a minute per IP on `/api/*`**. That number was written when the relay was
imagined as serving page loads, with the socket carrying anything live. Then §4.2.1 — decided in
this same run — concluded that crypto cannot use the socket and the terminal must poll
`/api/crypto/prices` every two seconds.

Thirty a minute. **Two tabs from one address exhaust that one route**, before `fx/prices`, a candle
load or the news takes any share at all. And per-IP is a blunt instrument in this product's own
market: `NetworkFactory`'s KDoc has said for a year that the backends cannot rate-limit the phone by
address, because «carrier-grade NAT puts a very large number of Iranian mobile subscribers behind
one address». An office, a household, a floor of a building — all one address.

Neither section was wrong when it was written. The fault is that **a decision in one place changed a
number in another and nothing connected them.**

The fix is not a bigger number; it is the right shape. A limit should be proportional to what a
request costs, and these do not cost the same thing: the two price routes are served from a
two-second Redis cache and cost the upstreams **nothing at all**, because a hundred tabs collapse
into one call either way. Refusing them is protecting a resource that is not scarce. A candle miss,
by contrast, really does reach a backend.

So: 240 a minute for the cached reads, 60 for everything else, 10 for auth, bucketed by
`(address, client id)` where the browser sends one — the same idea as the app's `X-Install-Id`,
widening the limit for honest readers behind one NAT without replacing the address ceiling, since a
browser can mint identifiers and a phone's install id can too.

And the terminal's half of it, which is not optional: stop polling when the tab is hidden.

---

## 11. What the digest does not prove

One machine now serves the update document *and* the APK it names. That is the right arrangement —
one host a reader already trusts — and it means the `sha256` in that document proves something
narrower than it looks.

**It proves transport, not publication.** A reader who checks the file against the digest has
established that the bytes arrived intact and that the host meant to serve that file. They have not
established that this project made it, because the same host wrote both numbers. Anybody with root
on `pro-chart.com` could serve a different APK and a matching digest, and every check would pass.

The thing that actually protects a reader is the **signature**, and it is not on the server at all.
Android refuses to install an APK over an existing one unless the signing certificate is identical,
and the key that signs these releases is a GitHub Actions secret used in CI and nowhere else. So the
worst a compromised host can do to somebody who already has the app is offer a package their phone
declines with «app not installed». `AppIntegrity.check` is the second line, for a reader who
uninstalls first.

Three things follow, and they are why the design already has the shape it has: **keep the GitHub
release**, because it is the independent copy and the workflow that builds it holds the key while
the host that serves it does not; **never put the signing key on the server**, because that is the
moment two independent things become one; and know that **a first install is the exposed case**,
which is why §5's fingerprint is written down in this repository rather than left to be looked up,
and why the app prints the installed certificate on its own screen afterwards.

None of that makes the digest pointless — it catches a truncated download, a mirror, a proxy that
rewrote something. It is why the card shows it. It is also why the app never installs anything
itself.


---

## 12. The web target, and what a browser found that a phone had hidden

«کی ورژن وب آماده می‌شود؟» has been answerable only as a guess, because the largest unknown in the
plan — *does this code even build for a browser?* — had never been tested. `PLAN.md` §2 said the
Kotlin/Wasm toolchain was not in the repository and a target could not be added here.

That sentence was true of the environment that wrote it and false of the repository. Every build in
this run goes through a wrapper that forces `--offline`; the claim was inherited from that and never
re-checked. One `curl` at Maven Central was enough to find it out.

**So the target went in, and both modules compile for WebAssembly.** The engine — eighteen chart
types, eighty-three indicators, the geometry behind eighty-five drawing tools, the backtester, the
replay, the object tree — and the language — lexer, parser, type checker, interpreter, sixty-one
shipped strategies. `:chart-core:compileKotlinWasmJs :namascript:compileKotlinWasmJs` now runs in
CI on every push, so the thing that was an argument is a task with an exit code.

Three of §2's four predicted steps landed exactly as written. The fourth was a one-line surprise:
`@JvmInline` on `ChartIcon` needed `import kotlin.jvm.JvmInline` spelled out, because the
annotation is common-code stdlib but `kotlin.jvm.*` is a default import on the JVM targets and on
no other.

### The defect the browser found, which was a defect on the phone too

`NamaScript` caught `StackOverflowError` so that a deeply nested script — `((((((…))))))` — came
back as `E405` instead of a crash. On the JVM that is sound: the stack unwinds and nothing else in
the process notices.

In WebAssembly an exhausted stack is a **trap**, and a trap does not unwind. There is no `catch`
afterwards because there is no afterwards — the instance is gone, and with it the terminal page,
not merely the script. A defence that reads as universal turned out to be implemented on exactly
one target.

The tempting fix is `catch (Throwable)`, and it is the wrong one: it compiles everywhere and makes
every genuine bug in the interpreter reach the reader as «your script is nested too deeply», which
is a lie told by the error handler. So the fix went upstream instead — `Parser.MAX_NESTING`, a
depth counter around the two places the recursive descent re-enters itself, refusing an over-nested
script **before** the recursion starts, with a line and a column the editor can point at. That runs
on every target. The JVM's catch stayed behind an `expect`/`actual` (`DeepNesting.kt`) as a second
line, and the browser's `actual` says plainly that it cannot catch anything.

The general shape, because it will recur: **the web target is a reviewer.** What it rejects is
usually wrong on the phone as well — the phone was just quieter about it. The phone's version of
this bug was a script that could kill its own stack and be reported with no position at all.

### And the answer to the question

`docs/web/PARITY.md` is the schedule, and the first thing it does is refuse the word «exactly» for
one of the three things it could mean. Chart parity is reachable and largely reached. Terminal
parity is a port of surfaces that already exist. **Network parity — their data licences, their
hundreds of thousands of published scripts, their broker integrations — is not reachable**, and a
document that implied it was would be making a promise on somebody else's behalf.

What is left is honest and has a number on it: a usable web terminal — chart, tools, scripts,
watchlist, no account — is **18 to 28 working days**, and nothing is blocking it. Everything past
that needs an account, and the account needs an answer to a question that has been open since
`SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md`: which backend owns the reader.

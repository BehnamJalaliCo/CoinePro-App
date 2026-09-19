# RUN א — checklist

States: ✅ done · ❌ not done, or narrowed (the row says exactly how) · ⏳ owed to the owner.

The Greek runs are finished at Ψ and Ω is taken, so this one is named for the first letter of the
Persian alphabet. Fitting, as it happens: it is the run where the product stops waiting for an
American store and starts distributing itself.

The owner's brief, in their own words, was one sentence with two halves:

> «کپی ترید البنک میماند و در ضمن گوگل پلی به ما ایرانیا خدمات نمیده در حال ایمیل زدن و مکاتبه
> هستیم باهاشون»

The first half closes a question left open at the end of run Ψ. The second half is a fact about the
world, not an instruction — and it turned out to have a great deal of work hiding inside it.

---

## א1 — the answer to `RUN_PSI/BLOCKED.md §Ψ16`

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 1 | LBank copy trading: gone, or the web panel's? | ✅ **answered: it stays** | `RUN_PSI/BLOCKED.md §Ψ16` carries the answer and is closed; `RUN_PSI/CHECKLIST.md` row 16 is ✅ and `RESUME.md`'s first open question is struck through. The second of the two branches that section named, so the work it would have triggered — three passages out of the terms, a re-sync, a note off the membership gate — does not happen | — **a decision whose evidence is that the diff is empty.** `sync-legal-documents.py --check` passes on the unchanged documents |
| 2 | Saying *why* the app carries none and the terms describe some | ✅ | Written down rather than left for the next reader to wonder at: **the terms are the service's, not this binary's.** They are the same document the web panel serves, a reader who agrees to them really can have the feature, and a document describing only what one client happens to draw would be the wrong document | — **prose.** `RUN_PSI/BLOCKED.md §Ψ16`, «The answer» |

---

## א2 — no Google Play, and the hole that leaves

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 3 | What the fact actually costs, written down once | ✅ | `docs/release/DISTRIBUTION.md` — the two separate refusals (no developer account from Iran; no installs served into it), how a reader really gets the app, **the four things a store would do that the app now has to do for itself**, the update document's contract, and which `assetlinks.json` fingerprint is the right one | — **a document.** It is the deliverable |
| 4 | The app had **no** update mechanism at all | ✅ **built** | `grep -rn "AppUpdate\|updateAvailable\|latestVersion\|InAppUpdate" app/src/main core feature` returned nothing before this run. With no store and no check, an old build was silently permanent: a fix shipped today reached only whoever happened to read the channel it was announced on. `:core:update` is the answer | — **an absence, now filled.** The module and its test |
| 5 | The comparison, and the four ways a published release is refused | ✅ | `AppUpdate.decide` compares **`versionCode` only** — the integer the package manager itself orders installs by. `publishable()` refuses a non-HTTPS address, an address off `PUBLISHING_HOSTS`, a missing or malformed digest, and a record the host has not finished writing. Each refusal is `Unknown`, which draws nothing | — **a decision function, and a still cannot state one.** `AppUpdateTest`, thirteen cases, including `https://pro-chart.com@evil.example/…` |
| 6 | The card on «ایمنی و انتشار» | ✅ | Version, what changed in the reader's own language, the file's SHA-256 before they fetch it, and one button. Silent in both non-states, which is the behaviour that lets it live on a screen every reader visits | `alef-update-available-fa`, `alef-update-mandatory-fa`, `alef-update-available-en-light` |
| 7 | Silent when there is nothing to say | ✅ | The state a frame cannot photograph, so it is asserted twice — `Current` and `Unknown` — each against a render that first proves it is the right screen, or «no card» would be satisfied by an empty one | — **an absence.** `AlefProofTest`, three cases, each asserting «ایمنی و انتشار» is present before asserting the card is not |
| 8 | The app does not download and does not install | ✅ **deliberately narrowed** | `UpdateHandoff` hands the address to the browser. In-process installing means `REQUEST_INSTALL_PACKAGES` — permission to put *any* package on the phone — plus a download manager, a file provider and a directory of half-fetched APKs, bought to save three taps the reader has already performed once. The permission is not in the manifest and is not wanted | — **an absence.** No `REQUEST_INSTALL_PACKAGES` in `AndroidManifest.xml` |
| 9 | `mandatory` changes a sentence and never a door | ✅ | A flag on a host that could stop an installed app from opening is a remote kill switch, and a kill switch is one compromised host away from being everybody's app at once. The word is drawn in the warning colour and the screen is otherwise identical | `alef-update-mandatory-fa`, and `AppUpdateTest` asserts the decision is unchanged by the flag |
| 10 | The check carries no credential | ✅ | Its own OkHttp client, built with no `bearerToken`. Both existing clients attach a session token to everything they carry, and the brand host is neither platform's — a call on either would hand that platform's bearer token to a machine with no business holding one. Unpinned too, because `pro-chart.com`'s certificate has never been seen from here | — **a client configuration.** `AppModule.appUpdateGateway`, and its note |
| 11 | Asked once per visit to the screen, not at every launch | ✅ | A reader who has opened «ایمنی و انتشار» is already asking the app about itself, which is the one moment «there is a newer one» is an answer rather than an interruption. A launch-time check would be a request on every cold start, on networks where a request costs something, for a fact that changes a few times a year | — **a `LaunchedEffect` keyed on the gateway.** The route in `CoineProApp.kt` |
| 12 | Strings in both locales, inside the note policy | ✅ | Nine keys in `values/` and `values-fa/`. `update_manual_body` and `safety_signing_body` are `visible` in `notes.tsv` and in `NotePolicy.visible` — drawn inline rather than folded into an ⓘ, because «you fetch the file and install it yourself» is an instruction and an ⓘ is where an instruction goes to be missed | — **text.** `lint_strings.py`: 41 modules clean |

---

## א3 — `assetlinks.json`, which the same fact makes simpler

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 13 | Which fingerprint the file must name | ✅ **corrected** | It was «the App Signing key, from Play Console». With no Play in the path nothing re-signs the upload, so it is the **release keystore's own** SHA-256. `print-assetlinks.sh`'s header, `SERVER.md §8.4`, `SERVER_BUILD_PROMPT.md` Phase 1 and `DOMAINS.md` step 3 all said the old thing and all now say this one, with the reason rather than the instruction | — **comments and documents.** Four files |
| 14 | The owner can read it off their own phone | ✅ **the dangling KDoc is closed** | `LaunchReadinessScreen` carried a parameter's documentation — «the certificate this install is actually signed with» — with **no parameter under it**. The parameter now exists: `signingFingerprints`, SHA-1 and SHA-256, monospace, left to right, with a copy button. `AppIntegrity.fingerprints` was already there and nothing on this screen called it | `alef-signing-certificate-fa` |
| 15 | A build that cannot read its own certificate | ✅ | Draws no card at all rather than an empty one. `AppIntegrity.fingerprints` answers an empty list on any failure and the screen takes that as «nothing to say» | — **an absence.** `AlefProofTest`, the empty-list case |

---

## א4 — what the server has to serve, now that it is the update channel

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 16 | The contract for `GET /api/app/latest` | ✅ | `SERVER.md §4.6`, new: the two routes, the document field for field, and the four things the app enforces — with the consequence stated plainly, that a release failing any of them is **silently not offered to anybody**. It is the one route on that server that is not a relay, because nothing upstream knows what the Android release is | — **a specification** |
| 17 | The brief the server's agent works from | ✅ | `SERVER_BUILD_PROMPT.md` gains **Phase 1½**, between the legal pages and the relay, with its own acceptance commands. Placed there on an argument rather than by taste: it is the only step whose absence gets *worse* with time, because every release that ships without it is another cohort with no way to hear about the next one | — **a brief, and the acceptance commands in it are the test.** Phase 1½ ends with three `curl`s and the answers they must give |
| 18 | Where it sits in the go-live order | ✅ | `SERVER.md §7` step 2½ and `DOMAINS.md` step 4, both with the proof that closes them — the served `version_code` against `version.py --code` for the published tag | — **an order of work** |
| 19 | The server stands it up | ✅ **built, and verified from this side** | Phase 1½ is served. Every claim in the server's acceptance report was re-checked here against the published artefacts rather than trusted: `aapt2` says `versionCode='50000004'`, `sha256sum` of the release APK is the `f6f9925…738c2` the document names, and `AppUpdate.publishable` holds on all four rules. **Not yet reachable** — 200 on the origin, 526 through Cloudflare (§א21) | — **a document and a file, and the evidence is that four independent readings agree.** `BLOCKED.md §א19`, the table |

---

## א5 — the two Play documents, which are now preparation rather than description

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 20 | `docs/PLAY_LISTING.md` | ✅ **kept, and re-framed** | A note at the head says the product is not on Play and points at `DISTRIBUTION.md` for how it actually ships. Kept rather than deleted because the owner is corresponding with Google and the day that succeeds this is the day's work already done — but **nothing in the build may assume any of it** | — **a document's own framing, which no frame can photograph.** The note is the first thing in the file |
| 21 | `docs/PLAY_COUNTRIES.md` | ✅ | Already said Iran is not a country a Play Console account may be registered from, and that the real route is a direct APK. It gains the one sentence it was missing: that the app now carries the update channel itself, because there is no store to carry one | — **a paragraph in a document, not a screen.** The note above «اول، چیزی که همه‌چیز را عوض می‌کند» |

---

## א6 — executed in full

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 22 | The new surface rendered at a tablet width **on the day it was written** | ✅ | `RUN_PSI/RESUME.md`'s second instruction to the next session, followed rather than read: two of the last three full-screen surfaces were wrong on a tablet, and both were caught a run late. The card has a pixel-tablet frame in the same commit as the card | `alef-update-available-pixel-tablet-fa` |
| 23 | Every gate, the whole suite, a signed build | ✅ | Nine gates green; the full unit suite green; shipped as 5.0.0 | — **the commit and the release** |
| 24 | Why 5.0.0 and not 4.100.0 | ✅ | The versioning scheme reserves two digits for MINOR — `versionCode = MAJOR×10,000,000 + MINOR×100,000 + …` — so 4.99.0 is the last of the fours and `version.py --bump minor` refuses to go further. It is the scheme choosing, not taste; that it lands on the release where the product leaves the store behind is a coincidence and a convenient one | — **arithmetic.** `scripts/release/version.py`, `MAX_MINOR = 99` |

---

## א7 — Android CI, red for six commits before this run touched it

Not in the brief, and found because 5.0.0's push was watched rather than assumed. Three jobs were
failing and none of the three failures was new; the owner's own gate list is green throughout, which
is exactly how a workflow stays red without anybody noticing.

**Android CI is green on `0f33dbb`** — the first time in at least six commits.

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 25 | `:benchmark` had not compiled for weeks | ✅ **fixed** | A KDoc in `ChartFlingBenchmark` documented an output path ending `…/*.json`. **Kotlin block comments nest** — unlike Java's — so the slash-star inside the comment opened a second one, the closing delimiter shut only that, and the remaining two hundred lines of the file were comment. The error read «Unclosed comment» against the last line of the file, which is about as far from the cause as a diagnostic gets. The path is written without a glob now, and the note beside it says why | — **a compiler error, and the evidence is its absence.** `:benchmark:compileBenchmarkKotlin` green, built without `--offline` so the macrobenchmark artifact could be fetched |
| 26 | `:app:lintDebug` failed on two proof tests | ✅ **fixed** | `StateFlowValueCalledInComposition`: `controller.state.value.signals` read *inside* a `proof { }` composable in `RunOmegaFixProofTest` and `RunOmegaProofTest`. Hoisted above the lambda in all three places. The rule is right even in a test — a frame whose state cannot recompose is a picture of the wrong moment, which is the same fault run Ψ found in the welcome captures | — **a lint rule.** `:app:lintDebug` green here, 0 errors against 41 warnings |
| 27 | The on-device proof test died before it drew anything | ✅ **fixed, unverified** | `DeviceProofTest` swaps the locale with `createConfigurationContext`, which builds a context from the **base** rather than from the activity — so the wrapper chain no longer reaches a `ComponentActivity`, and `rememberLauncherForActivityResult` could not find its owner. Any scene with a picker in it threw. `LocalActivityResultRegistryOwner` is now provided from the rule's own activity | — **an emulator test; this container has none.** It compiles (`:app:compileDebugAndroidTestKotlin`), and CI's emulator is the verdict |
| 28 | And the fault the comment had been hiding | ✅ **fixed** | Once `:benchmark` parsed again it stopped on `device.performMultiPointerGesture` — a method `UiDevice` does not have. It is on `UiObject`, the `UiSelector` API, and on nothing else: not on `UiDevice`, which carries only the single-pointer `swipe`, and not on the `UiObject2` the neighbouring `pinch()` gets from `findObject(By…)`. **A file that does not parse hides every error after the first**, and this one had been hidden for as long as the comment was open | — **a compiler error.** Same task, same run, green |

---

## א8 — the server came back, and four of `SERVER.md`'s rows were wrong

The Pro Chart machine finished Phase 0 and Phase 1 and reported four disagreements between the spec
and the live backends. All four were real. The spec was written from **what the Android app calls**,
and the Android app signs in — so every route in it was the authenticated surface, and a relay with
no account reaches none of them.

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 29 | The missing distinction: each backend has **two** surfaces | ✅ **new `SERVER.md` §4.0** | `api/mobile/v1/*` and `user/*` are authenticated and answer 401 to a relay with no account, which is correct rather than broken. Beside them sit `api/v1/public/*` (TradeYar) and `api/public/*` (CoinePro-FX). **The web's Phase 2 is the guest tier**, and the guest tier is already specified, shipped and proved on the phone — `:core:guest` adapts those exact routes to the same two interfaces the signed-in chart uses. So Phase 2 does **not** wait on `PLAN.md` §6.2; that question gates the account | — **a specification, and the proof is that the app already ships a client for it.** `GuestApi`, `GuestMarketGateways.kt` |
| 30 | §4.1's FX candle row named a TradeYar route | ✅ **corrected** | `api/v1/public/candles/{symbol}` is TradeYar's, copied into the forex column. The app's own FX candles come from `academy/chart/{symbol}` behind a second token, which no Phase-2 relay can hold. The public route is `api/public/prices/series` — **four timeframes** (`M15 H1 H4 D1`; `M5`, `M30`, `W1` are a `422`, all seven measured), `limit` bounded `20..400`, `t` an ISO-8601 string and no volume. That is the academy route minus the five-minute bar | — **a route table.** `SERVER.md` §4.1 and the probe log in §4.7 |
| 31 | The bare snapshot is not the FX discovery mechanism | ✅ **corrected, and it found a product fault** | Rule 6 said both backends answer the bare call with everything. TradeYar does — 857. **CoinePro-FX answers 17 and omits gold and silver**, while `api/public/prices/live` answers 19 with both. Verified here independently of the server | — **two numbers.** `SERVER.md` §4.7, and §א20 |
| 32 | §4.3 was mostly Phase 4 pretending to be Phase 2 | ✅ **split** | `public/signals/*` sits under a path called *public* on CoinePro-FX and is still behind VIP — `EndpointCatalog` says so in as many words and the live server agrees. The calendar and both market-intelligence routes likewise. What Phase 2 **can** carry is now its own table: `api/v1/news/list`, `api/demo/signals` (closed signals with their real outcome — a track record, not a demonstration), `api/v1/public/community`, `api/v1/public/membership` | — **a route table.** `SERVER.md` §4.3 |
| 33 | Gold is missing from the phone's forex list too | ⏳ **owed to the owner — one line to CoinePro-FX** | `MarketCatalogGateway` builds the forex catalogue from `ws/snapshot` and nothing else, on purpose. So the metals are absent from the app's forex market list today, while `ForexSignalScope` narrows the forex **signals** to gold: a reader can be shown a gold call and find no gold market to open. Nothing in the app may fix it — inventing a market the feed does not list is the fault the catalogue exists to prevent. The KDoc that claimed both backends answer alike now records the measurement instead | — **a backend's answer.** `BLOCKED.md §א20` carries the ask verbatim |
| 34 | The fingerprint question, asked again | ✅ **made unmissable** | The server asked for the Play Console App Signing key. It had already been corrected in Phase 1; it is now **rule 7** at the top of the brief as well, where a reader meets it before the phases. There is no Play in this product's path, nothing re-signs the upload, and the value is the release keystore's own — which the app prints on its own «ایمنی و انتشار» screen | — **a rule.** `SERVER_BUILD_PROMPT.md`, rules 7 and Phase 1 step 3 |
| 35 | `pro-chart.com` answers 526 | ✅ **named, with the refusals spelled out** | Cloudflare reached the origin and refused its certificate. Let's Encrypt cannot complete a challenge through an orange-clouded record, so the answer is a Cloudflare **Origin CA** certificate from the owner's dashboard. The step says so, and says plainly that turning off verification, serving plain HTTP to the origin or switching the zone to Flexible all make the 526 disappear while leaving the hop unencrypted | — **a configuration step.** `SERVER_BUILD_PROMPT.md` Phase 1 step 5 |
| 36 | The fingerprint, confirmed without a phone | ✅ | The server read it from the published APK's **v2 signing block** — the APK carries no v1 signature at all, which is why `keytool -printcert -jarfile` answered nothing — and checked three releases. Confirmed here independently with `apksigner verify --print-certs` on a *fourth*: `9612ab6c…fbd0`, `CN=CoinePro, OU=Mobile, O=CoinePro, L=Tehran, C=IR`. The value is written down in `DISTRIBUTION.md` §5, because a fingerprint nobody has recorded is one nobody can notice changing | — **a hex string, and four readings of it that agree.** `apksigner` output, quoted in `BLOCKED.md §א19` |
| 37 | `releases/latest` had been dead for thirty releases | ✅ **fixed** | The workflow hard-coded `prerelease: true`, so GitHub had no latest release to point at and `releases/latest` answered `302` to the *list*. The website's «download the app» button was aimed at exactly that. It is `${{ steps.ver.outputs.build != '0' }}` now: the named version is a release, the `-bN` builds on top of it are pre-releases — which is what semver means by the `-` as well as what GitHub means. Nothing in the app noticed because the update document names an exact URL, and that is how it went unseen | — **a workflow input.** `.github/workflows/android-apk.yml`, and the note above it |
| 38 | The card's own sentence had no author | ✅ | `notes_fa` / `notes_en` are the one place this product speaks to a reader about itself, and they were being written by whoever was publishing, from a boilerplate release body and a `CHANGELOG.md` that stopped at 0.2.0. `docs/release/UPDATE_NOTES.md` is the source now — per version, both languages, with the rule that anything which cannot be said in both is said in neither | — **text.** The file, and 5.0.0's entry in it |
| 39 | The notes file became an interface, so it got a gate | ✅ | The server does not retype those sentences — `app-latest.py` **parses** `UPDATE_NOTES.md`, keyed on the released version (`5.0.0+4` reads `## 5.0.0`), unwraps the blockquote and copies the result byte for byte. That turns a document into a contract, and a contract nobody checks breaks silently **on a screen most readers open twice a year**. `scripts/release/check-update-notes.py` parses it the same way and fails the build on a missing entry, a missing language, Persian that breaks the house orthography, or either language past 400 characters. In both workflows | — **a parser, and a still cannot run one.** The gate prints what a reader will see; the CI log is the evidence |
| 40 | Its reading and mine agree byte for byte | ✅ | The server reported `notes_fa` 244 characters and `notes_en` 274. Reproducing its rule here gives **244 and 274** — the same numbers from a parser written without sight of theirs, which is the only kind of agreement worth having between two programs that have to read one file | — **two integers.** The gate's own output |

---

## א9 — the host answers, and the oldest loose end in the product is closed

The owner made the Origin CA certificate; the server installed it without disabling anything;
`pro-chart.com` is live and Phases 1, 1½ and 2 are all serving. Everything below was measured from
outside, through Cloudflare, as a reader's phone would — not read from the server's report.

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 41 | Every legal link in the shipping app now opens a page | ✅ | `/legal/terms/`, `/legal/privacy/`, `/legal/delete-account/` → `200`, **zero redirects**, `text/html`. Those three addresses have been compiled into the app since 4.47.0 and answered nothing until today; `DOMAINS.md` carried the sentence «a legal link from inside the app opens a browser on a host that does not answer» for a year. It does not any more | — **three status codes.** `SERVER.md` §4.8 |
| 42 | `assetlinks.json`, and the key in it | ✅ | `200`, `application/json`, no redirect — the three things Android's verifier asks. The fingerprint it serves is `96:12:AB:…:FB:D0`, which is the release keystore's, checked here against `apksigner`'s own reading of the APK rather than against the server's report of it. The App Link on `/reset` can verify now | — **a hex string compared to a hex string.** §4.8 |
| 43 | The update card is live for every install below 50000004 | ✅ | `/api/app/latest` answers `200 application/json`; the notes are **byte-identical** to `UPDATE_NOTES.md`; the APK it names answers `200` and `sha256sum` makes it **byte-identical to the GitHub release**. The whole chain — document, digest, file, signature — holds end to end from outside | — **four artefacts that agree.** `BLOCKED.md §א19` and §4.8 |
| 44 | The relay, and the thing it refuses to do | ✅ | 857 crypto rows and 19 forex rows with gold first — the same two numbers §4.7 measured against the backends directly, so nothing was lost in the hop. And **it does not translate a refusal**: `M5`, `M30` and `W1` come back as the backend's own `422` rather than as the nearest timeframe the relay could serve. A substituted timeframe would draw a chart of the wrong bars with nothing anywhere saying so | — **seven status codes and two counts.** §4.8 |
| 45 | The candle-cache decision, which went against the spec and was right | ✅ **spec amended to match** | §4.1 said to key each bar `venue:symbol:interval:openTime`. Rebuilding a response from individually cached bars means the relay writes `server_time_ms` itself — and **rule 1 is that this server is never the author of any number**. Caching the body whole with a TTL that runs to the bar's close gets §4.1's effect (657 ms cold, **3.5 ms** warm) without the relay acquiring a second contract. §4.9 records it | — **a cache policy, and two timings** |
| 46 | A health check that reported a fault which was not there | ✅ | TradeYar's `/healthz` answers `307` to `/login?from=/healthz`; the relay does not follow redirects, so the first reading was a false «degraded». Probe moved to `api/v1/system/health`, which answers `200` directly. Recorded because that failure mode is the dangerous one — a check that cries wolf teaches everybody to ignore it | — **two status codes.** §4.9 |
| 47 | The showcase route, read off the OpenAPI rather than taken on my word | ✅ | §4.3 said «read it off `/api/openapi.json` rather than taking this paragraph's word for it», and that is what was done: `public/signals/showcase` exists, answers without a token, and is relayed at `/api/fx/showcase`. It returns `{"signal": null, "live": null}` today, which is **empty rather than broken** — `public/signals/stats` beside it says `total_signals: 0` | — **a route that now exists in the table.** `SERVER.md` §4.3 |
| 48 | A third witness for the missing gold | ✅ | That same `stats` route reports **`symbols_covered: 19`**. The desk's own count of its forex universe is nineteen, `prices/live` serves nineteen, and the snapshot the phone reads serves seventeen. The route the app depends on is the odd one out | — **one integer.** `BLOCKED.md §א20` |
| 49 | One thing to change on the server | ❌ **named, not fixed — it is the server's to do** | `/api/health` prints each upstream's private address (`10.10.1.2`, `10.10.1.3`) to the public internet. Neither is a secret nor reachable from outside, but it is a network diagram for anybody who asks and it buys a reader nothing: `reachable`, `probe`, `latency_ms` and the cache rate are the fields that matter. Drop `address`, or keep the detailed route private and serve a bare `{"status":"ok"}` publicly |

---

## א10 — the socket, and the two walls behind it

Phase 3 is built. It is **forex-complete and crypto-closed**, and both halves of that are facts
about the backends rather than about the relay.

| # | Item | State | Evidence | Frame |
|---|---|---|---|---|
| 50 | One upstream connection, two clients | ✅ | Two browsers on EURUSD for 150 s → **one** TCP connection from the relay to CoinePro-FX, counted from `/proc/net/tcp` inside the relay's own container rather than from its own statistics. Both clients received the same tick, identical timestamp and last. The ceiling **refuses** rather than truncates: 201 symbols is an error frame, 200 is accepted, a second socket from one address is `403` | — **two integers, and where they were counted from.** `SERVER.md` §4.11 |
| 51 | §4.2 assumed a crypto socket that does not exist | ✅ **spec corrected** | Seven candidate paths on TradeYar: two time out at the handshake, three answer `404`, and the two that resolve close `4001` / `4401 unauthorized` — the second being the one the Android app opens. So the crypto socket is the §4.0 wall again: authenticated surface, and a relay with no account can open **zero** connections, not one | — **seven probes.** §4.2 |
| 52 | The venue that is not live says so, in the first frame | ✅ | `{"type":"welcome","venues":{"forex":{"live":true},"crypto":{"live":false,"reason":"upstream requires an account…"}}}`. **The difference between a chart that says «there is no crypto feed» and a chart that simply never ticks is this one frame** — and a silently dead feed is the failure this product has spent three runs designing out | — **a protocol frame, now in §4.2** |
| 53 | Crypto on the socket: bridge it, or wait? | ✅ **decided: wait** | The relay could poll the public route every 2 s and push the differences. It would relay values unchanged — but it would invent the one thing a tick is *for*, **when it happened**, carrying either the poll's own clock or an upstream timestamp up to 2 s late with nothing saying so. Rule 1 again. And refusing costs nothing: a tab polling `/api/crypto/prices` is the **same upstream traffic**, because the 2 s cache collapses it either way. §4.2.1 records the argument and the two notes the terminal needs | — **a decision, and an argument a still cannot carry** |
| 54 | A third forex universe, and gold is in none of the small ones | ✅ **measured** | `prices/live` 19, `ws/snapshot` 17, **the socket 7** — the majors and nothing else. `XAUUSD` gets a snapshot value and **never another frame**. §א20's ask is two lines now rather than one | — **three counts.** §4.10, `BLOCKED.md §א20` |
| 55 | The forex quotes are Yahoo Finance | ❌ **named, and the app must not paper over it** | `source: "yfinance"` on all seventeen rows, with `bid == ask == price` exactly — no spread, one number in three fields. The app already files them under `QuoteSource.UNKNOWN`, so nothing on screen is mislabelled; but **the chart beside them names «MetaTrader 5»**, which `CandleGateway.sourceName`'s KDoc prints precisely so a reader can check the claim. Two venues on one screen, one named. Adding `YFINANCE` to the enum would be this run deciding Yahoo Finance is an acceptable quote source for a paid product — the owner's call. `BLOCKED.md §א22` is the question |
| 56 | `SERVER.md` said «over Finnhub» for a year | ✅ **corrected** | §1 of both the spec and the build brief. It was never measured; it is now, and §4.10 carries the reading with its date | — **one sentence in two files** |
| 57 | The weekend measurement, kept because it explains a frozen chart | ✅ | The forex market is shut at the weekend: upstream sends a frame a second with unchanged values — 45 frames, zero change — and re-samples about every two minutes. The relay forwards **changes**, not frames, so a closed market costs nothing downstream and a tick is real even when its price has not moved | — **a rate and a duration.** §4.11 |
| 58 | `/api/health` no longer draws the network | ✅ | The public fields are `reachable`, `probe`, `latency_ms`, `last_good_age_s`. No address, no host | — **an absence.** §4.9 asked for it; the server did it |

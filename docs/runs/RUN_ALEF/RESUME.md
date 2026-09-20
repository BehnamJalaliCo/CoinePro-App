# RUN א — resume

Written as the last action of every session, per R5.

---

## Where the run is

**Both halves of the owner's message are executed.** Twenty-three of twenty-four rows are ✅; the
last is ⏳ on two static files somebody has to put on a host that already exists.

* **`§Ψ16` is closed.** LBank copy trading stays; nothing was edited; the reason the app carrying
  none and the terms describing some is *correct* is now written down where the next reader will
  find it rather than left to be discovered as an apparent oversight.
* **The app has an update channel**, because there is no store to provide one. `:core:update`, a
  card on «ایمنی و انتشار», a browser hand-off, thirteen cases of test, and an allow-list on the
  download address because this is the one response in the product that ends as an installable
  package rather than as text.
* **`assetlinks.json` is unblocked** — the fingerprint is the release keystore's own, and the app
  now prints the installed certificate on its own safety screen, which closes a KDoc that had been
  documenting a parameter that did not exist.
* **`docs/release/DISTRIBUTION.md`** is the new document of record for how the product ships. The
  two Play documents are re-framed as preparation and neither is now describing reality.

**Shipped:** 5.0.0. Every gate green, the whole unit suite green, the signed APK published as
`v5.0.0`. Android CI was red on arrival for reasons predating this run — see the last section.

**Live since 2026-09-19:** `pro-chart.com`. The legal pages every shipping build links to, the
`assetlinks.json` that lets the App Link verify, the update document, and the read-only relay — all
answering, all measured from outside rather than reported. The update card is in front of readers. The socket is live too, **for forex**: one upstream
connection fanned out to many clients, a ceiling that refuses rather than truncates, and a welcome
frame that says out loud which venue is not live and why.

---

## What the next session does, in order

1. **`BLOCKED.md §א20` and `§א22` — both are one message to CoinePro-FX's team.** §א20: the forex
   universe is 19 by the desk's own count, 17 in the snapshot the app's catalogue is built from and
   **7 on the socket**, and gold is missing from the last two. §א22: the quotes report
   `source: "yfinance"` with `bid == ask`, while the chart beside them names MetaTrader 5 — two
   venues on one screen, one of them labelled. Neither needs an app release; both need an answer.
2. **Phase 4 on the server** is gated on `RUN_PSI/BLOCKED.md §Ψ10` — which backend owns the account
   on the web, whether the terminal is open or member-only, whether the candle archive is built on
   day one. Phases 1, 1½, 2 and 3 are live; Phase 3 is forex-complete and crypto-closed, and stays
   that way until §6.2 is answered.
3. **The RUN Τ2 backlog**, still the largest thing outstanding and still untouched: B6 (offline as a
   first-class state), B7 (the single-symbol widget), B8 (Picture-in-Picture), C1's surface, C2's
   two gaps, C3, C4, C6. `RUN_T2/RESUME.md` names the files and the trap in each; none needs a
   backend.
4. **`RUN_XI/BLOCKED.md §Ξ21`** — the signals route's «entitled but unlinked».

---

## Traps this session hit, so the next one does not

* **A KDoc can outlive its parameter, and nothing in the toolchain minds.**
  `LaunchReadinessScreen` carried four paragraphs documenting «the certificate this install is
  actually signed with» with **no parameter under it** — the doc comment sat directly above the
  closing `) {`. It compiled, it rendered, and it had been describing a feature the screen did not
  have for several releases. If a parameter's documentation reads oddly specific for what the
  function does, check that the parameter is there.
* **A capability with no caller is the same as an absent capability.**
  `AppIntegrity.fingerprints` had been written, tested and argued for, and the only thing calling it
  was a Google-sign-in error hint. The days that cost were spent on exactly the question it answers.
  Written is not shipped.
* **«The app has no X» is worth `grep`-ing for rather than assuming.** The update check was assumed
  to exist in some form — most Android apps have one — and five patterns over three source roots
  returned nothing at all.
* **Bumping minor past 99 is refused, and that is the scheme working.**
  `versionCode = MAJOR×10,000,000 + MINOR×100,000 + …`, so 4.99.0 is the last of the fours.
  `version.py --bump minor` raises rather than producing a number that would collide with major 5.
  Use `--bump major`.
* **Adding a module is four files, not one.** `settings.gradle.kts`, the module's own
  `build.gradle.kts`, the consumer's dependency block, **and the module map in
  `docs/PRODUCT_ROADMAP.md`** — `check-cross-phase-consistency.py` compares the two lists in order.
* **A new `_body` / `_note` / `_hint` string key needs two registrations.**
  `tools/i18n/notes.tsv` *and*, for class `visible`, `NotePolicy.visible` in `:core:designsystem`.
  The lint checks both directions and fails on either alone.
* **Asserting an absence needs a positive assertion beside it.** «No update card» is satisfied by a
  render that drew nothing at all. Every absence case in `AlefProofTest` asserts the screen's own
  title first.
* **`testDebugUnitTest` and `:app:assembleRelease` are still separate invocations**, or they run out
  of memory. Unchanged from run Ψ, and still true.
* **The owner's gate list does not cover what CI runs, and CI had been red for six commits.**
  Nine gates green and the whole unit suite green says nothing about `:app:lintDebug`,
  `:benchmark:compileBenchmarkKotlin` or the emulator tests, and all three were failing before this
  run went near them (§א7). **Read the workflow result after a push** — it is one `curl` against
  `api.github.com/repos/.../actions/runs`, and the alternative is finding out six releases later.
* **Kotlin block comments nest.** `/*` inside a KDoc opens a second comment, and the closing
  delimiter then shuts only the inner one — so a path like `…/*.json` written in a doc comment turns
  the rest of the file into a comment. The compiler reports «Unclosed comment» against the *last*
  line, naming neither the file's real problem nor anything near it. `:benchmark` had been in that
  state for weeks — and **a file that does not parse hides every error after the first**: behind the
  comment sat a second fault, `device.performMultiPointerGesture`, which `UiDevice` has never had.
* **A spec written from what the app calls is a spec for an app that signs in.** Every route in
  `SERVER.md` §4 came from the Android client, and the Android client holds a bearer token — so the
  server's relay, which has no account, met 401 on all of them and read it as a blocker. Both
  backends have a public surface beside the authenticated one, and the app already ships a client
  for TradeYar's (`:core:guest`). Before specifying a route for something with no account, ask
  **which surface**, not just which path.
* **«Both backends do X» is two claims, and one of them had been false for months.**
  `MarketCatalogGateway` said both answer the bare snapshot with everything they quote. TradeYar
  does; CoinePro-FX answers 17 of 19 and drops exactly the two metals the product's forex side is
  *about*. Nothing failed, no test broke, and the list simply had no gold in it. When a doc comment
  asserts something about a server, the only way to keep it true is to measure it — `SERVER.md` §4.7
  is that measurement, dated, so the next reader argues with a number.
* **A hard-coded `prerelease: true` cost thirty releases and nobody saw it.** GitHub marks the
  newest *non*-prerelease as latest; with every release flagged, `releases/latest` answered `302` to
  the list of releases and the website's download button pointed at a page instead of a file. The
  app never noticed because the update document names an exact URL — **the thing that hid it was the
  same thing that made it not matter yet.** Fixed as `build != '0'`.
* **The APK has no v1 signature**, so `keytool -printcert -jarfile` finds nothing and says so
  unhelpfully. The certificate is in the v2 signing block: `apksigner verify --print-certs`. The
  Android SDK in the scratchpad has it at `build-tools/36.0.0/apksigner`, and one command settles a
  question that had been costing days.
* **Verify a report against the artefact, not against the reporter.** The server's Phase 1½
  acceptance was correct in every particular — and it was worth spending four commands to know that
  rather than to assume it: `aapt2 dump badging` for the version code, `sha256sum` for the digest,
  `apksigner` for the certificate on a *different* release than the one it read.
* **The moment another program parses a document, the document is an interface.**
  `docs/release/UPDATE_NOTES.md` was written as prose for whoever publishes a release. The Pro Chart
  server now reads it directly, so a reformatted heading or a version bumped without an entry would
  publish a card that says nothing — with no error anywhere, on a screen most readers open twice a
  year. `scripts/release/check-update-notes.py` is the answer, and the general rule is: when
  something outside this repository starts reading a file inside it, that file needs a gate the same
  day, not the run after.
* **A spec is not automatically right when an implementer disagrees with it.** §4.1 told the relay
  to cache candles per bar. Doing that would have forced the relay to write `server_time_ms` when
  rebuilding a response — against rule 1, which says it is never the author of any number. Caching
  the body whole with a close-derived TTL gets the same effect and keeps the rule. **The spec was
  amended to match the implementation**, which is the right direction when the implementer's reason
  is better than the rule's.
* **A health check that cries wolf is worse than none.** TradeYar's `/healthz` answers `307` to a
  login page; a relay that does not follow redirects reads that as «degraded». The probe is
  `api/v1/system/health`. A monitor that reports a fault which is not there teaches everybody to
  ignore the one that is.
* **A relay that polls is not a relay that streams, and the difference is a timestamp.** The server
  asked whether it should bridge crypto onto the socket by polling the public route every two
  seconds. It would have relayed the values unchanged — but a tick is an assertion about *when*
  something happened, and a poll can only assert «my two samples differed». Answer: no, and the cost
  of refusing is zero, because a tab polling the cached route is the same upstream traffic.
  **When a design would make something up, check whether refusing actually costs anything; here it
  cost nothing at all.**
* **A feed's `source` field is worth reading, and nobody had read this one.** CoinePro-FX's quotes
  say `yfinance` with `bid == ask`; `SERVER.md` had called that backend «over Finnhub» for a year,
  and the app's chart names MetaTrader 5 a few pixels away. Nothing was broken, no test failed, and
  the app files the venue as `UNKNOWN` — so the only symptom was a label that quietly meant two
  things. **Read the metadata fields, not only the numbers.**
* **A decision in one section can invalidate a number in another, and nothing will say so.**
  §4.2.1 decided the terminal polls crypto every two seconds; §6 had said 60 requests a minute per
  IP. Thirty and sixty: two tabs from one address exhaust one route. Neither section was wrong when
  it was written. **When a design choice changes a rate, go and find the rate** — the implementer
  will otherwise find it at three in the morning, or a reader will find it as a `429`.
* **A digest served by the same host as the file proves transport, not publication.** Worth knowing
  before somebody treats it as a security guarantee: the anchor for this product is the APK
  signature, which Android checks and which lives in CI rather than on the server. `DISTRIBUTION.md`
  §4½ writes the model out, including the one exposed case — a first install, with no previous
  signature to compare against.
* **A wrapped `LocalContext` loses the activity.** `createConfigurationContext` builds from the base
  context, so anything that finds its owner by walking the context chain — `rememberLauncherForActivityResult` is the one that bit — stops working the moment a test swaps the locale that way.
  Provide the owner explicitly.

---

## Open questions for the owner

1. **`BLOCKED.md §א20`** — gold is in neither the snapshot (17) nor the socket (7), while the desk's
   own `symbols_covered` says 19.
2. **`BLOCKED.md §א22`** — which venue the forex quotes come from. They say `yfinance`; the chart
   says MetaTrader 5. One question, two acceptable answers, and the app must not guess either.
3. **`RUN_PSI/BLOCKED.md §Ψ10`** — which backend owns the account on the web, whether the terminal
   is open or member-only, and whether the candle archive is built on day one. These gate Phase 4,
   and Phase 3's crypto half with it.
4. **`RUN_XI/BLOCKED.md §Ξ21`** — the signals route's «entitled but unlinked».
5. Everything still open from RUN Τ2: the fling's distance-versus-time, the four alert tones,
   `membership_open_ourbit`, C5's separate scales, and the watchlist's «تحلیل».

*Settled since this file was first written:* the `assetlinks.json` fingerprint, the Cloudflare
Origin CA certificate, and whether the relay should bridge crypto onto the socket (it should not —
`SERVER.md` §4.2.1).

---

## Android CI

Three jobs were red when 5.0.0 was pushed and none of the three failures was new (§א7 in the
checklist). **All three are fixed and the workflow is green** on `0f33dbb` — the first green Android
CI in this repository for at least six commits. How each was verified:

* **Compose UI** — green on CI after the `LocalActivityResultRegistryOwner` fix. Confirmed.
* **`:app:lintDebug`** — green here.
* **`:benchmark`** — two faults, one hidden behind the other. Both fixed;
  `:benchmark:compileBenchmarkKotlin` is green here, built **without** `--offline` so the
  macrobenchmark artifact could be fetched. The wrapper script in the scratchpad forces `--offline`
  and this module is the one place that matters.

Keep reading the workflow after a push rather than assuming it; that habit is what found all
of this:

```
curl -s "https://api.github.com/repos/BehnamJalaliCo/CoinePro-App/actions/runs?per_page=6"
```

The signed release is published either way: "Build Android APK" is a separate workflow and it
succeeded — `v5.0.0`, with `pro-chart-5.0.0.apk`.


---

## The web target, and the trap that is not a catch

Two things to carry forward, both learned the hard way in the hour the `wasmJs` target went in.

**`PLAN.md` §2 said the toolchain was not in the repository, and that was true of the environment
that wrote it, not of the repository.** The Gradle wrapper in the scratchpad forces `--offline`, so
every build in this run was offline by default and the sentence «a target cannot be added and
compiled here» was inherited rather than re-checked. The moment `curl` reached Maven Central, the
whole of §2 was an afternoon. **Re-check an environmental blocker before quoting it**; the note in
`RESUME` about `:benchmark` needing a run without `--offline` was the same lesson and it did not
generalise on its own.

**A catch that cannot run is worse than no catch.** `catch (error: StackOverflowError)` reads like
defence on every target and is defence on exactly one: WebAssembly traps, and a trap does not
unwind. The fix was not to catch something wider — `catch (Throwable)` would have reported every
genuine bug in the interpreter as «your script is nested too deeply», which is a lie told by the
error handler — but to make the failure impossible upstream, with a limit in the parser that runs
everywhere. **When a target cannot implement a defence, move the defence to where every target can
run it**, and leave the platform-specific half as the second line rather than the only one.

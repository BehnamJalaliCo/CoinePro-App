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

---

## What the next session does, in order

1. **`BLOCKED.md §א19` — the update document.** Two static files on the Pro Chart machine.
   `SERVER_BUILD_PROMPT.md` Phase 1½ is written to be handed over as it stands, with its own
   acceptance commands. Until it answers, the work in this run is real code that never runs.
2. **The rest of the server**, which the owner has provisioned and which now has three phases
   waiting: the legal pages (the oldest loose end in the product — every legal link in the shipping
   app points at a host that does not answer), the read-only relay, and the socket.
   `RUN_PSI/BLOCKED.md §Ψ10` carries the three product questions that gate the ones after that.
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
  state for weeks.
* **A wrapped `LocalContext` loses the activity.** `createConfigurationContext` builds from the base
  context, so anything that finds its owner by walking the context chain — `rememberLauncherForActivityResult` is the one that bit — stops working the moment a test swaps the locale that way.
  Provide the owner explicitly.

---

## Open questions for the owner

1. **`BLOCKED.md §א19`** — the update document, and whether the APK is served from `pro-chart.com`
   or the document points at the GitHub release.
2. **The `assetlinks.json` fingerprint value**, for whoever configures the host. It is on the app's
   own «ایمنی و انتشار» screen with a copy button; it just has to be handed over.
3. **`RUN_PSI/BLOCKED.md §Ψ10`** — which backend owns the account on the web, whether the terminal
   is open or member-only, and whether the candle archive is built on day one.
4. **`RUN_XI/BLOCKED.md §Ξ21`** — the signals route's «entitled but unlinked».
5. Everything still open from RUN Τ2: the fling's distance-versus-time, the four alert tones,
   `membership_open_ourbit`, C5's separate scales, and the watchlist's «تحلیل».

---

## Android CI

Three jobs were red when 5.0.0 was pushed and none of the three failures was new (§א7 in the checklist). Two are fixed and verified here: `:app:lintDebug` passes, and the nested-comment
fault that stopped `:benchmark` compiling is gone. The third — `DeviceProofTest` on the emulator —
is fixed but **unverifiable in this container**, so the next session's first act should be to read
the workflow result rather than assume it:

```
curl -s "https://api.github.com/repos/BehnamJalaliCo/CoinePro-App/actions/runs?per_page=6"
```

The signed release is published either way: "Build Android APK" is a separate workflow and it
succeeded — `v5.0.0`, with `pro-chart-5.0.0.apk`.

# RUN Ψ — resume

Written as the last action of every session, per R5.

---

## Where the run is

**All three instructions are executed.** Sixteen of eighteen rows are ✅; one is ⏳ on a machine the
owner will buy, and one is ❌ because it asks a question only the owner can answer.

* **The tablet audit** found two faults in eleven surfaces — the welcome slides and the chart's
  loading skeleton — and both are fixed, with frames for one and arithmetic for the other. The other
  nine were right, and the reason they were right is worth carrying forward: they measure the space
  they are given rather than asking «is this a phone».
* **The web** has a server specification somebody can provision against (`docs/web/SERVER.md`). The
  machine is the owner's next move; everything decidable without it is decided.
* **Copy trading is gone** — two modules, a screen, a route, a card, twenty MetaTrader strings and
  eleven notes — and the forex signal list is the gold call, with a rule and a test rather than a
  filter typed at a call site.

**Shipped:** 4.99.0. Every gate green, the whole unit suite green on a forced re-run.

---

## What the next session does, in order

1. **Read the two questions in `BLOCKED.md` first.** §Ψ16 is one word and closes a row. §Ψ10 is the
   server, and the moment it exists steps 1 and 2 of `SERVER.md` §7 are an afternoon that closes the
   oldest loose end in the product: every legal link in the shipping app points at a host that does
   not answer.
2. **The tablet rule now has a name — use it.** Two new surfaces in three runs measured the window
   instead of the space they were given. The cheapest guard is a render at a tablet width the day a
   full-screen surface is written, not the run after. The welcome is the example: it sat in the
   parity matrix's `other` row with a dash under every tablet column, and that dash *was* the bug
   report.
3. **The RUN Τ2 backlog**, untouched and still the largest thing outstanding: B6 (offline as a
   first-class state), B7 (the single-symbol widget), B8 (Picture-in-Picture), C1's surface, C2's
   two gaps, C3, C4, C6. `RUN_T2/RESUME.md` names the files, the data each reads and the trap in
   each; none needs a backend.
4. **`RUN_XI/BLOCKED.md §Ξ21`** — whether the signals route can distinguish «entitled but the account
   is not linked». Still open, still one field on a 403 body.

---

## Traps this session hit, so the next one does not

* **A proof frame of a screen with a hold on it photographs the hold.** `waitForIdle` settles
  composition; it does not move a wall clock. Every welcome frame this rig produced between run Ξ
  and today was a picture of the 600 ms brand frame. Use `composeRule.mainClock.advanceTimeBy(...)`
  past any `delay` before capturing, and capture the held frame separately if it is worth having.
* **The proof PNGs carry a faint duplicate of the last line across their top few pixels.** It is the
  capture rig, not the product — driven directly, the sentence resolves to one semantics node at the
  bottom. Do not chase it.
* **A bound chosen without measuring the widest case is the same bug one size up.** The skeleton's
  line cap was eleven until the test drove a 1 973 dp Tab S9 Ultra and found eleven rules 164 dp
  apart. Drive the extremes, not the middle.
* **Deleting a module breaks five tests, and every one of them is a claim worth rewriting.** The
  temptation is to delete the assertions with the feature. Each of the five said something true
  about the product that is now differently true — `NotePolicyTest`'s money example, the menu's
  descriptive-row count, the connections surface set — and saying the new thing is how the next
  reader learns it from the file rather than from a diff.
* **`git grep`-based gates read tracked files.** `git add` before running them, or they pass by not
  looking. The module map in `PRODUCT_ROADMAP.md`, `notes.tsv` and `NotePolicy.visible` all had to
  lose their copy-trading entries before the gates would go green, and each one failed separately.
* **`-Dcoinepro.golden.record=true` on the whole `:app` suite runs out of memory** — record with
  `--tests` naming the two golden classes.
* **`testDebugUnitTest` and `:app:assembleRelease` are separate invocations**, or they run out of
  memory. A wedged daemon looks exactly like a slow suite: `./gradlew --stop` and re-run.

---

## Open questions for the owner

1. **`BLOCKED.md §Ψ16`** — is crypto copy trading on LBank also gone, or does it stay as the web
   panel's? One word, and it decides three passages in the terms and the privacy note.
2. **`BLOCKED.md §Ψ10`** — the server. And, once it exists, the three product questions in
   `PLAN.md` §6 that gate the account and the terminal.
3. **`RUN_XI/BLOCKED.md §Ξ21`** — the signals route's «entitled but unlinked».
4. Everything still open from RUN Τ2: the fling's distance-versus-time, the four alert tones,
   `membership_open_ourbit`, C5's separate scales, and the watchlist's «تحلیل».

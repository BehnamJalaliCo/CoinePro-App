# RUN Ω — where to pick up

Written at the end of every session, without exception (R5). Read this first; it is shorter than the
report and it is the only file that says what to do *next*.

## Where the run stands

| Phase | State |
|---|---|
| Ω1 — Signal Layer & Explain | **done**, 4.75.0 `5750f44` |
| Ω2 — chart owns the screen, one design language, feedback | **done bar two items**, 4.76.0 `f66edd4`, 4.76.1 `15ca1d3` |
| Ω3 — Simple/Full mode, first run, Chart Preview | **done**, this commit |
| Ω4 — Arena, Rasad, Market Mood, share cards | **not started** |
| Ω5 — tablet parity, web readiness doc | **not started** |

`CHECKLIST.md` is current. E2, E3 and **E7** are ✅; E6 and the new Ω3 table are broken out item by
item; E1 is ❌ on one clause only — Rasad's three sentences, which are Ω4. Seventeen recorded
decisions, four narrowings.

## Start here

**Ω4, item 1: the Replay Arena.** It is the biggest single piece left and everything it needs is
already in the build:

* `Replay` in `:chart-core` is the rewind engine — `Replay.enter` wants thirty bars, `MINIMUM_BARS`
  — and `ChartController.enterReplay` already drives it from the chart's own hub.
* `PaperTradeController` + `PaperFills.marketable` is the order book the Arena scores. A limit at the
  drawn entry, the stop and the target both carried: see the `onPaperTrade` lambda in `CoineProApp`,
  which already builds exactly the request the Arena wants.
* The daily challenge has **no endpoint** — `BLOCKED.md` entry 2 — so the client picks from a date
  seed. Write that as one function so the endpoint replaces the seed and nothing else moves.
* The discipline half of the score is the interesting half: «SL respected, no revenge trades» is
  computable from the paper trades of one session, and `ReplayReport` already carries the
  stop-vs-early-close split the brief's «discipline» means. Read its own note first — it explains why
  a strategy backtest can never report it.
* The share card is 1080×1080 and `ChartShare.share` already turns the chart layer into a bitmap and
  hands it to the Android share sheet. The card is a second renderer over the same seam.

Then Ω4 item 2, **Rasad**: deterministic templates over `ChartSignalLayer` — which is exactly what
`SignalSpec`'s sixteen phrasings already are, so «read this chart» is three of those sentences
composed, not a new engine. This is also the clause that keeps E1 at ❌, so it closes two lines at
once.

## The two Ω2 rows still ❌, and why they are not a blocker

1. **«Zero explanatory prose» is a mechanism, not a decision.** Every tip-class note in
   `feature:chart` goes through `CoineProNote`, which is the one place that folds a tip into an ⓘ
   instead of drawing it — so the lever exists and `NotePolicy` is where it is pulled. What has *not*
   happened is deciding, per note, whether it earns its line. Thirty-four are registered in
   `tools/i18n/notes.tsv`, all as `tip`. The timeframe sheet, the library cards and the sync banner
   are untouched. This is an afternoon of judgement, not of code.
2. **Before/after pairs.** Every chart golden was re-recorded, so the *after* exists for every
   surface Ω2 touched — `app/src/test/goldens/chart-*.png`, forty files. Nobody assembled the pairs;
   `git show <the commit before 4.76.0>:app/src/test/goldens/<name>` is the *before* for each.

## The i18n sweep, outside `feature:chart`

`feature:chart` went 397 → **0** hard-coded Persian literals. The same class of leak remains
elsewhere and is named in `CHECKLIST.md`'s narrowing 1. The ones worth doing next, in order of how
often a reader sees them:

* `CandlePatterns.persianName` in `:chart-core` — sixty-odd pattern names, and the module has no
  Android, so this one needs the `List<Pair<Int, …>>` shape `ChartRange` now uses *or* a lookup in
  `feature:chart`. Prefer the second: `:chart-core` should not gain an `R`.
* `WatchlistFlag.persianName` in `:core:datastore`.
* `DrawingTools`' own labels.
* `*_persianLabel` extensions in `feature/search` and `feature/profile`.

The recipe that worked: extract the literals with a regex over non-comment lines, mint keys, write
both `values/` and `values-fa/` in one script, then replace. **Escape every apostrophe in `values/`** —
`aapt2` reports an unescaped one as «invalid unicode escape sequence» on whatever line it lands on.
And expect the string lint to start catching vocabulary in the newly-extracted strings that it could
never see while they were Kotlin: «نمودار» → «چارت», «بازه‌ی زمانی» → «تایم‌فریم», informal
imperatives, hamza-on-heh ezafe («دربارهٔ» is rejected; write «ه‌ی»), and any `*_note` / `*_hint` /
`*_body` key needing a row in `tools/i18n/notes.tsv` and a `CoineProNote` at the call site — or, where
the string is a card's own content rather than a tip, a rename off the `_body` suffix the way
`first_run_*_blurb` and the five `studio_*_blurb` keys went.

## Standing facts about this environment

* Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=<scratchpad>/android-sdk ./gradlew --offline --no-parallel …`
* A full `testDebugUnitTest :app:assembleRelease` is about **six minutes**. Background it and poll
  with `until grep -q "^exit" $LOG; do sleep 25; done`. The daemon has died once mid-run on this
  container — «Gradle build daemon disappeared unexpectedly» with no compile error above it — and the
  answer is simply to run it again.
* Goldens re-record with `-Dcoinepro.golden.record=true`. The chart goldens move on any change to the
  plot, the legend or the palette, so expect to re-record.
* No device, no emulator, no `/dev/kvm` — see `BLOCKED.md` entry 1 for the full list of what that
  rules out and what stands in for it. A Material bottom **sheet** cannot be photographed by the
  Robolectric rig either: it renders into a window of its own and the rig captures the activity's
  decor view, so a sheet's proof frame is always its *body* composable, and where that body is
  `internal` to a module with no rig of its own the evidence is a unit test instead.
* The five gates are cheap (seconds). Run them before every commit; the consistency gate is the one
  that catches vocabulary, sheet-enum drift, the 4 dp grid, and — since Ω3 — a markless empty state
  and a screen with two coach marks on it.

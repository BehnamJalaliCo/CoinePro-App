# RUN Ω — where to pick up

Written at the end of every session, without exception (R5). Read this first; it is shorter than the
report and it is the only file that says what to do *next*.

## Where the run stands

| Phase | State |
|---|---|
| Ω1 — Signal Layer & Explain | **done**, 4.75.0 `5750f44` |
| Ω2 — chart owns the screen, one design language, feedback | **done bar two items**, 4.76.0 `f66edd4`, 4.76.1 `15ca1d3` |
| Ω3 — Simple/Full mode, first run, Chart Preview | **done**, 4.77.0 `d5ff453` |
| Ω4 — Arena, Rasad, Market Mood, share cards | **done**, 4.78.0 |
| Ω5 — tablet parity, web readiness doc | **not started** |

`CHECKLIST.md` is current. **E1–E5 and E7–E9 are ✅.** Two lines are open: E6, which is broken out
item by item and has two rows left (the per-note prose decision and the before/after pairs), and E10,
which is Ω5. Twenty-two recorded decisions, five narrowings.

## Start here

**Ω5 — the tablet, and the web readiness doc.** Two items and the first is the larger:

1. **Every Ω1–Ω4 surface in the adaptive tablet layout.** What Ω1–Ω4 added and what each needs:
   * **The Now strip and رصد's line** are in the chart's own column, so they are already on the
     tablet — check them at 1280 dp, where the line has three times the width and should carry more
     of the sentence rather than the same truncation.
   * **Explain as a side panel.** The brief is explicit. `ChartSidePanel` is the mechanism and
     `ExplainSheetBody` is already the sheet's *body* rather than the sheet, precisely so it can
     dock — see the note in `RunOmegaProofTest` on why the proof frame is the body.
   * **رصد** is the same shape: `RasadSheetBody` docks beside the plot rather than opening over it.
   * **The Arena** wants two panes — the chart and the result — rather than a sheet that covers the
     chart the reader is being scored on.
   * **The chart preview** is list-detail: `CoineProListDetail` already does markets → chart, and
     the preview is the same relationship one level shallower.
   * **The mood strip** is a Home card and needs nothing but a check at width.
   Then `docs/qa/PARITY.md` to 100 %.
2. **`docs/web/PLAN.md`**: how the Signal Layer, Confidence, رصد and the Arena map to the future
   Compose-Multiplatform terminal. All four are already in `:chart-core` with no Android on the
   classpath and `ArchitectureTest` holding that, so this is a document rather than a port — say
   which types cross, which five `expect` declarations the web target has to fill, and that the
   Arena's daily pick is arithmetic rather than a platform `Random` for exactly this reason.

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

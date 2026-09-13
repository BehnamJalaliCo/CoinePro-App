# RUN Ω — where to pick up

Written at the end of every session, without exception (R5). Read this first; it is shorter than the
report and it is the only file that says what to do *next*.

## Where the run stands

| Phase | State |
|---|---|
| Ω1 — Signal Layer & Explain | **done**, 4.75.0 `5750f44` |
| Ω2 — chart owns the screen, one design language, feedback | **done bar two items**, 4.76.0 `f66edd4` + this commit |
| Ω3 — Simple/Full mode, first run, Chart Preview | **not started** |
| Ω4 — Arena, Rasad, Market Mood, share cards | **not started** |
| Ω5 — tablet parity, web readiness doc | **not started** |

`CHECKLIST.md` is current: E2 and E3 are ✅, E6 is broken out item by item with two rows still ❌, and
the three narrowings are written down under «Narrowings, per R3».

## Start here

**Ω3, item 1: the first-launch question.** Nothing about it exists yet. The pieces it needs are all
in place:

* `UserPreferencesStore` is where the answer belongs — add a `ReaderMode` enum beside `ThemeMode` and
  follow `ThemeMode`'s shape exactly (a stable `id`, `fromId` falling forward, the `when` on the enum
  rather than at the call sites; `ThemeMode.isDark` is the pattern to copy).
* `LaunchSplash` already draws over the app before the first frame, and `MainActivity:345` is where it
  hands over. The question goes between.
* «Simple hides the rail, the DOM, the editor, multi-chart and replay from the chrome» is a filter
  over `ChartMoreSheetBody`'s tiles and `ChartToolRailColumn`, not a second screen. `ChartChrome`'s
  own note says which control lives where.
* The one-tap toggle goes in the «…» hub beside `onReadings`, and in `AppearanceOptions`.

Then Ω3 item 2, the Chart Preview: `MarketPreviewSheet` in `feature:search` **is** most of it
already — it has the price (with rolling digits since Ω2), the percent pill, the sparkline and three
actions. What it lacks is the range chips, the scrub with its haptic, and the Signal Layer summary
line. `ChartSignalEngine.evaluate` is pure and synchronous and takes a `CandleSeries`, so the summary
line is one call.

## The two Ω2 rows still ❌, and why they are not a blocker

1. **«Zero explanatory prose» is a mechanism, not a decision.** Every tip-class note in
   `feature:chart` now goes through `CoineProNote`, which is the one place that folds a tip into an ⓘ
   instead of drawing it — so the lever exists and `NotePolicy` is where it is pulled. What has *not*
   happened is deciding, per note, whether it earns its line. Thirty-four are registered in
   `tools/i18n/notes.tsv`, all as `tip`. The timeframe sheet, the library cards and the sync banner
   are untouched. This is an afternoon of judgement, not of code.
2. **Before/after pairs.** Every chart golden was re-recorded, so the *after* exists for every
   surface Ω2 touched — `app/src/test/goldens/chart-*.png`, forty files. Nobody assembled the pairs;
   `git show HEAD~1:app/src/test/goldens/<name>` is the *before* for each.

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
`aapt2` reports an unescaped one as «invalid unicode escape sequence» on whatever line it lands on,
which cost two build cycles this session. And expect the string lint to start catching vocabulary in
the newly-extracted strings that it could never see while they were Kotlin: «نمودار» → «چارت»,
«بازه‌ی زمانی» → «تایم‌فریم», informal imperatives, and any `*_note` / `*_hint` / `*_body` key needing
a row in `tools/i18n/notes.tsv` and a `CoineProNote` at the call site.

## Standing facts about this environment

* Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=<scratchpad>/android-sdk ./gradlew --offline --no-parallel …`
* A full `testDebugUnitTest :app:assembleRelease` is about **six minutes**. Background it and poll
  with `until grep -q "^exit" $LOG; do sleep 25; done`.
* Goldens re-record with `-Dcoinepro.golden.record=true`. The chart goldens move on any change to the
  plot, the legend or the palette, so expect to re-record.
* No device, no emulator, no `/dev/kvm` — see `BLOCKED.md` entry 1 for the full list of what that
  rules out and what stands in for it.
* The five gates are cheap (seconds). Run them before every commit; the consistency gate is the one
  that catches vocabulary, sheet-enum drift and the 4 dp grid.

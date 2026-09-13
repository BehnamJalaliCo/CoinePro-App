# RUN Ω — where to pick up

Written at the end of every session, without exception (R5). Read this first; it is shorter than the
report and it is the only file that says what to do *next*.

## Where the run stands

| Phase | State |
|---|---|
| Ω1 — Signal Layer & Explain | **done**, 4.75.0 `5750f44` |
| Ω2 — chart owns the screen, one design language, feedback | **done**, 4.76.0 `f66edd4`, 4.76.1 `15ca1d3`; its last two rows closed in 4.79.0, and one of its rows was **wrong** until 4.80.0 — see below |
| Ω3 — Simple/Full mode, first run, Chart Preview | **done**, 4.77.0 `d5ff453` |
| Ω4 — Arena, Rasad, Market Mood, share cards | **done**, 4.78.0 `806c718` |
| Ω5 — tablet parity, web readiness doc | **done**, 4.79.0 `c38667f` |
| Ω-FIX — the eight the device found | **done**, 4.80.0 |

## What happened between 4.79.0 and 4.80.0, and why it should change how the next run works

4.79.0 was tested on a phone. The thesis holds — the owner's verdict was «تز «چارتی که حرف می‌زند»
واقعاً پیاده شده و روی دستگاه کار می‌کند», with the experience at 7.2 out of ten against 4.6 before
the run, and performance untouched. What did not hold was **this repository's own documentation**:
eight rows of `CHECKLIST.md` disagreed with the device, and three of them were claims made on the
strength of a reading of the code with no test behind them.

The worst was the E6 table's first row. «The top «→» row is gone» carried a ✅ and an evidence cell.
The row was in every portrait frame of both recordings — Ω2 had removed the app bar's *contents* and
left the band.

Two rules came out of that and they apply to whatever runs next:

1. **A claim about a screen is not evidence about a screen.** If the only thing behind a row is a
   sentence about what the code does, the row is not ✅ yet. `showsTopBar` is a function now
   precisely so a test can call it; do the same for the next thing that is decided in the middle of
   a large composable.
2. **A checklist row names a frame or says why it cannot have one.** Seven E6 rows are motion,
   timing or feel and genuinely cannot be photographed; those seven now say so in the table and name
   the gate that fails instead. A blank evidence cell is how the «→» row shipped.

## Start here

**Nothing in the run is open against code.** What is owed is owed to a device, and to the owner:

1. **The thirty-second recording the fix list ends with.** Chart portrait with no top row → scrub →
   «+» on the axis → alert → the Setup sheet → the Rasad sheet → an Arena result → a share card.
   That is the acceptance test for 4.80.0 and nobody without a phone can take it. `BLOCKED.md` entry
   1 lists everything else a container cannot produce — every MP4 across runs B, G, H, K and Ω, the
   fling benchmark, a 120 fps pan, NamaScript timings on a Pixel 6a, Pixel Fold frames, the tablet
   soak. `docs/qa/DEVICE_PROOFS.md` carries the `adb` commands so they can be taken in one pass.
2. **The owner's own «آزمون خودتان».** A fresh install, fifteen minutes of aimless use, and a
   recording of the moments they got bored. That is the next input, and it is not something this
   repository can generate for itself.
3. **The two endpoints.** `BLOCKED.md` entries 2 and 3: «today's challenge» and the friends league.
   Both have a working client-side answer and a one-function seam, so neither blocks anything.
4. **The i18n sweep outside `feature:chart`**, below. Narrowing 1, and still the only one of the five
   that is open against code rather than against a device.
5. **The web**, when the owner accepts this version: `docs/web/PLAN.md`, on the same `:chart-core`.
   Ω-FIX made that easier rather than harder — the Setup score, the state bands and the coach's
   contradiction gate all landed in `:chart-core`, which has no Android on it.

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
  with `until grep -q "^exit" $LOG; do sleep 25; done`. The daemon has died twice mid-run on this
  container — «Gradle build daemon disappeared unexpectedly» with no compile error above it — and the
  answer is simply to run it again.
* **`--offline` cannot resolve anything new.** Ω-FIX tried to add `androidx.compose.ui:ui-test-junit4`
  to `:chart-ui` so the alert chip's frame could be taken in the module that owns it; the transitive
  `com.google.code.findbugs:jsr305:2.0.2` is not in this container's cache and the configuration
  failed. The chip's position is proved by arithmetic over nineteen gutter widths instead, which is
  the stronger claim anyway — but the general lesson is that a new test dependency is not free here.
* Goldens re-record with `-Dcoinepro.golden.record=true`. The chart goldens move on any change to the
  plot, the legend, the palette **or the height of anything under the plot** — the coach's line
  wrapping to two rows moved the fold-closed English pair in 4.80.0.
* No device, no emulator, no `/dev/kvm` — see `BLOCKED.md` entry 1. A Material bottom **sheet**
  cannot be photographed by the Robolectric rig either: it renders into a window of its own and the
  rig captures the activity's decor view, so a sheet's proof frame is always its *body* composable,
  and where that body is `internal` to a module with no rig of its own the evidence is a unit test
  instead.
* Robolectric qualifiers: `port` and an explicit `w…dp-h…dp` are **mutually exclusive** —
  `IllegalArgumentException at Qualifiers.java:28`. Give the width and the height and let the
  orientation follow from them.
* The five gates are cheap (seconds). Run them before every commit; the consistency gate is the one
  that catches vocabulary, sheet-enum drift, the 4 dp grid, a markless empty state and a screen with
  two coach marks on it.

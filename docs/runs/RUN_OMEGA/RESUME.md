# RUN Ω — where to pick up

Written at the end of every session, without exception (R5). Read this first; it is shorter than the
report and it is the only file that says what to do *next*.

## Where the run stands

| Phase | State |
|---|---|
| Ω1 — Signal Layer & Explain | **done**, 4.75.0 `5750f44` |
| Ω2 — chart owns the screen, one design language, feedback | **done**, 4.76.0 `f66edd4`, 4.76.1 `15ca1d3`; its last two rows closed in 4.79.0 |
| Ω3 — Simple/Full mode, first run, Chart Preview | **done**, 4.77.0 `d5ff453` |
| Ω4 — Arena, Rasad, Market Mood, share cards | **done**, 4.78.0 `806c718` |
| Ω5 — tablet parity, web readiness doc | **done**, 4.79.0 |

`CHECKLIST.md` is current. **All ten end states are ✅**, each with its evidence path, and E11 — the
checklist itself — is that document. Twenty-four recorded decisions; five narrowings, of which two
closed in 4.79.0 and are kept struck through rather than deleted.

## Start here

**Nothing in the run is open.** What is owed is owed to a device, and to the owner:

1. **The recordings.** `BLOCKED.md` entry 1 lists exactly what a container cannot produce — every
   MP4 across runs B, G, H, K and Ω, the fling benchmark with three script indicators, a 120 fps pan,
   NamaScript timings on a Pixel 6a, Pixel Fold frames, the tablet soak.
   `docs/qa/DEVICE_PROOFS.md` carries the `adb` commands so they can be taken in one pass. The brief
   asks for a thirty-second recording after Ω1 and Ω2 so the owner can see the chart «really talk»;
   that is the one thing here nobody but a person with a phone can do.
2. **The two endpoints.** `BLOCKED.md` entries 2 and 3: «today's challenge» and the friends league.
   Both have a working client-side answer and a one-function seam, so neither blocks anything — but
   the Arena becomes a different product the day two people can compare a score.
3. **The i18n sweep outside `feature:chart`**, below. It is narrowing 1 and the only one of the five
   that is still open against code rather than against a device.

If a new run starts, the first question is which of those three the owner wants, because the
repository's own answer to «what next» is now the sweep and nothing else.

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

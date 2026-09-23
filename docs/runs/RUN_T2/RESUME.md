# RUN Τ2 — resume

Written as the last action of every session, per R5.

---

## Where the run is

**Phase A: audited and closed.** Its premise was wrong — run Τ shipped in 4.90.0 and the 4.92 APK
carries it — so the phase became an audit with every number re-measured on this tree, plus the one
clause run Τ left untested. See `CHECKLIST.md`, which opens with the correction, and `REPORT.md`
for the measurements.

**Phase B: five of ten done.** B1 and B4 were already shipped by runs Φ and Υ and are audited rows.
B2, B3, B5, B9 and B10 are this session's work — B9 narrowed on its bundled sound set, which needs
audio this repository does not hold (`BLOCKED.md §B9`). B6, B7 and B8 are not started.

**Phase C: three pieces built, the rest audited.** C5's legend figure is new; C1's rule —
`NotableBars` — is built and tested with its surface still to come, and both rows are ❌ with the
checklist saying exactly what is missing. **C2 is ✅.** C3, C4 and C6 do not exist.

**C2 was not the small row it looked like.** «Mostly shipped already» was true of the engine and
false of the product: deleting a drawing killed its alerts *silently*, and the alert then sat in
the centre under «فعال» unable ever to fire. The audit also turned up a second defect nobody had
reported — the «واگرد» offered beside every deleted drawing had never restored one, because the
delete bypassed the only place a drawing step was recorded. Both are fixed and both have a test
that fails without the fix. The lesson for the rest of this phase: **a row that reads «the engine
exists» is a row nobody has used end to end.**

**Shipped:** 4.93.0 (phase A + B2/B3/B5/B10), 4.94.0 (B9), 4.95.0 (C5's legend), 4.96.0 (C1's rule),
5.1.0 (C2). Every gate green and the full unit suite passing on each.

**Not done, and the next session's list in order:** B6, B7, B8, C1's surface, C3, C4, C6. Each is
named below with its files, the data it reads and the trap in it.

---

## What is next, in the order it should be taken

**B9's remaining half** is the four tones in `BLOCKED.md §B9`, and it is blocked on the owner rather
than on work: once the files are in `res/main/res/raw/`, it is four more entries in
`NotificationChannels` and a tone field on the alert beside `soundLevel`.

### B6 — offline as a first-class state

* **Exists:** `CoineProOfflineBar` (one line, no dismiss, no retry — read its KDoc before changing
  it, the argument is good), `NetworkStatus`, `CandleCache` and `CandleArchive` for bars, the
  watchlist in preferences.
* **Missing:** the banner that says **how old** the cached picture is — «ذخیره‌شده · %s پیش» /
  "Saved · %s ago" — on the chart, the watchlist, news and the calendar; the **queued alert** (an
  alert created offline, with a badge, flushed on reconnect).
* **Note:** this is a new component beside `CoineProOfflineBar`, not a change to it. «No network»
  and «this is from an hour ago» are different sentences and a reader needs the second one even
  when the first is not true.

### B8 — Picture-in-Picture

Nothing exists. The shape it wants:

1. `android:supportsPictureInPicture="true"` on `MainActivity` in the manifest, with
   `configChanges` covering `screenSize|smallestScreenSize|screenLayout`.
2. An app-scoped holder the chart writes its snapshot into (symbol, interval, last price, change,
   the last N closes, the bar-close instant) and `MainActivity` reads — the chart is deep inside the
   graph and the activity is what enters PiP.
3. A compact composable drawn when `isInPictureInPictureMode`, capped at one update a second.
4. A «همچنان تماشا کن» tile in the hub (`ChartChrome.kt`, the MORE section) behind an
   `onKeepWatching` callback.
* **Cannot be proved here.** Robolectric does not enter PiP. Whatever lands must be marked ⏳ and
  verified on the owner's phone.

### B7 — the second widget, and Glance

* **Exists:** `MarketsWidget` (RemoteViews, `app/src/main/kotlin/com/coinepro/app/widget/`) with
  `WidgetConfigureActivity`, `WidgetRefreshWorker` and `WidgetSnapshotStore` — the configuration
  activity, the WorkManager refresh and the deep link B7 asks for are all already there for the
  list widget.
* **Missing:** the **single-symbol** widget (price, change, the nearest level from the Signal
  Layer), and the brief's «Glance». Consider whether the second widget is worth a second toolkit:
  the existing one works and a Glance rewrite of a working widget is risk with no reader-visible
  gain. If it stays RemoteViews, the row says so and stays ❌.

### C1 — «Why this move?»

**The rule is built and tested**: `NotableBars` in `:chart-core`, and `NotableBarsTest` beside it.
What is left is the surface, and the design decision worth inheriting is *where the dot goes*.

**Not in the plot's gestures.** A tap on the plot already means a drawing, an eraser stroke or a
trade ring, and a long press already opens the context menu; taking one of them over for this would
put the app's most delicate gesture path at risk for a feature nobody has asked for yet.

**In the event strip**, under the bars, where `EventMark` glyphs already live. That strip is already
«things that happened at this time», an unusually large bar is exactly that, and the whole hit-test
is already written — `ChartEvents.markAt`, called from `CoineProChart.kt:2527`, confined to the few
points of height below `timeAxisTop`. The work is: carry the notable indices on the decoration
beside `decoration.events`, draw a dot for the visible ones in `drawEventMarks`, extend the strip's
hit-test to answer with a bar index, and open a sheet.

**The sheet's content needs no backend.** News and the economic calendar are already on the chart —
`ChartEventController` holds them for the window in front of the reader, unfiltered — and the Signal
Layer's events are on `decoration.signal`. The bar's own window is `series.time[index]` to the next
bar's time. Where nothing falls inside it, one plain sentence and «news for this time».

### C4 — My week

Reads `JournalController` (the discipline chart), the alert audit trail
(`core/datastore/…/AlertAuditStore.kt`), `ArenaStore` for practice sessions, and the Signal Layer
over the watchlist. The share image goes through the existing generator —
`core/designsystem/…/ShareCard.kt`.

### C6 — Rasad's morning brief

`app/…/sync/BackgroundSyncScheduler.kt` is the WorkManager pattern to copy, and the Rasad templates
already produce the sentences. The sparkline image is the one new piece.

### C3 — Duel with the past

The largest of the six, and the one to take last: it is a Replay session seeded a year back with a
prediction and a score. `ArenaStore` and the replay engine are the pieces; the new part is the
seeding and the scoring.

### C5 and C2 — finish rather than build

* **C5:** the legend figure landed in 4.95.0 (`changeAcrossVisible`). What is left are the two
  clauses the checklist argues against rather than defers: **separate scales** — a second price axis
  per compared series, which is real work in the renderer and is the only honest reading of that
  phrase — and pointing the watchlist's «تحلیل» at the overlay, which would cost the side-by-side
  panes. Both want the owner's word before anybody builds them.
* **C2: done in 5.1.0.** `AlertDrawingLinks` (`core:notifications`) is the pure tie between an
  alert and a line, and it carries the one rule worth remembering: **not knowing a symbol's
  drawings is not knowing it has none**, so an unread symbol gets no verdict and the first frame of
  the alert centre marks nothing. `DrawingAlerts` is the four-line seam the chart gets — a list it
  can count and two verbs — and it stays null on any build that does not want it.

---

## Open questions for the owner

1. **`BLOCKED.md §A`** — distance or time on the fling. One word. Nothing is waiting on it.
2. **B7's Glance** — whether a working RemoteViews widget should be rewritten for the toolkit's own
   sake. The recommendation is no.
3. **B3's `membership_open_ourbit`** — kept deliberately, because it is the crypto venue's
   sub-account check and not a forex door. Say if that reading is wrong and it goes.
4. **C5's «separate scales»** — a second price axis per compared series is renderer work and is not
   what ABSOLUTE does. Worth building, or is the normalised view enough?
5. **C5 from the watchlist** — «تحلیل» opens the side-by-side panes today. Should it keep them, or
   offer both?

---

## Standing rules that bit this session, so the next one does not relearn them

* `check-motion-policy.sh` and `ForexSurfaceReachabilityTest` both read **tracked** files
  (`git grep`, and the shell's source). `git add` a new file *before* running the gates or they
  pass by not looking.
* `GoldenScreenshotTest` re-records **all** 139 goldens under `-Dcoinepro.golden.record=true`.
  Revert the families that were green (`git checkout -- app/src/test/goldens/<family>-*.png`)
  before committing, or the diff claims changes that are noise.
* `testDebugUnitTest` and `:app:assembleRelease` are **separate invocations**. Together they run out
  of memory.
* A **wedged Gradle daemon** looks exactly like a slow test suite. One run in this session sat for
  over an hour producing no test results, with the daemon at 5.4 GB resident and 65 % of a core;
  `./gradlew --stop` and a re-run finished the identical work in 4m 50s. If a run that normally
  takes five minutes passes fifteen with no new XML under `*/build/test-results/`, stop the daemon
  rather than waiting — and rather than suspecting the change.
* The menu is a `LazyColumn`: anything in its footer is not composed until it is scrolled to, so a
  semantics assertion on it fails. That is what moved B10's sentence to the top, and the move is
  right for the reader as well.

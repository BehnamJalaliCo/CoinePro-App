# RUN Τ2 — resume

Written as the last action of every session, per R5.

---

## Where the run is

**Phase A: audited and closed.** Its premise was wrong — run Τ shipped in 4.90.0 and the 4.92 APK
carries it — so the phase became an audit with every number re-measured on this tree, plus the one
clause run Τ left untested. See `CHECKLIST.md`, which opens with the correction, and `REPORT.md`
for the measurements.

**Phase B: six of ten done.** B1 and B4 were already shipped by runs Φ and Υ and are audited rows.
B2, B3, B5, B9, B10 and now B6 are built — B9 narrowed on its bundled sound set, which needs audio
this repository does not hold (`BLOCKED.md §B9`). B7 and B8 are not started.

**Phase C: done but for C5.** C5's legend figure is new and its row stays ❌ on two clauses the
checklist argues against rather than defers. **C1, C2, C3, C4 and C6 are ✅.**

**C2 was not the small row it looked like.** «Mostly shipped already» was true of the engine and
false of the product: deleting a drawing killed its alerts *silently*, and the alert then sat in
the centre under «فعال» unable ever to fire. The audit also turned up a second defect nobody had
reported — the «واگرد» offered beside every deleted drawing had never restored one, because the
delete bypassed the only place a drawing step was recorded. Both are fixed and both have a test
that fails without the fix. The lesson for the rest of this phase: **a row that reads «the engine
exists» is a row nobody has used end to end.**

**Shipped:** 4.93.0 (phase A + B2/B3/B5/B10), 4.94.0 (B9), 4.95.0 (C5's legend), 4.96.0 (C1's rule),
5.1.0 (C2), 5.2.0 (C6), 5.3.0 (C1's surface), 5.4.0 (C4), 5.5.0 (C3), 5.6.0 (B6). Every gate green
and the full unit suite passing on each.

**Not done, and the next session's list in order:** B7, B8. Each is named below with its
files, the data it reads and the trap in it.

---

## What is next, in the order it should be taken

**B9's remaining half** is the four tones in `BLOCKED.md §B9`, and it is blocked on the owner rather
than on work: once the files are in `res/main/res/raw/`, it is four more entries in
`NotificationChannels` and a tone field on the alert beside `soundLevel`.

### B6 — offline as a first-class state: done in 5.6.0

The note above was right and is worth keeping: it is a **new component beside**
`CoineProOfflineBar`, not a change to it. Four more things a next session should not rediscover:

* **The second half was not a queue, and building one would have been machinery that could never
  run.** A local alert is not sent anywhere; `LocalAlertWorker` compares it against prices under a
  *connected* constraint. So «created offline, flushed on reconnect» is really «never once read
  against a market», which is this run's recurring failure shape and now says «هنوز بررسی نشده».
  If a **server**-side alert queue is ever wanted, that is a different feature with a different
  store; do not attach it to `AlertReach`.
* **The check stamp is written only after the price route answered.** Stamping at the start of a
  pass would clear the pill on exactly the passes that checked nothing. `AlertEvaluatorTest` holds
  both directions; do not move the `checks.markChecked` call.
* **Each surface dates what it is showing, not when it last tried.** News and the calendar are
  per section because the controller holds a section independently; the chart uses the cache's
  write time because a daily candle is legitimately twenty hours old on a live chart.
* **`rememberSavedAge` ticks.** A frozen «۲ دقیقه پیش» is the lie the bar exists to remove, and it
  looks identical to a current one. Anything that reads `SavedAge` on a screen that stays open
  should use that helper rather than computing once.

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

### C1 — «Why this move?»: done in 5.3.0

This section's plan was followed almost exactly — the strip, the decoration field, the hit-test, the
sheet — and three things it did not say are worth keeping:

* **The precedence between a glyph and a dot has to be written down in both places.** The renderer
  skips a dot on a bar an event glyph holds, and `ChartEvents.notableAt` takes the same set as
  `exclude`. Either alone leaves a mark a reader can see and cannot open, or a tap that opens the
  wrong thing.
* **The sheet must not re-derive the figure.** `NotableBars.ratioAt` lives beside `of` and repeats
  its rules exactly, and the first case in `NotableBarReadingTest` asserts the two agree. A dot on
  a bar whose sheet says nothing unusual happened is the one outcome worse than no dot.
* **The empty state is the feature, not a gap.** Most large bars have nothing on the calendar
  inside them. The sheet says so in a sentence; reaching for the nearest headline would be this app
  manufacturing a cause, which is the thing `RasadCoach` refuses to do on the chart itself.

### C4 — My week: done in 5.4.0

Three things worth inheriting:

* **The week is `WeekStart`'s, in `core:common`.** It was private in `core:marketdata`, serving the
  weekly candle; the reader's own week has to open on the same day, so it moved rather than being
  copied. Anything that needs a week boundary uses that one.
* **`MyWeek.MINIMUM_TRADES` is a product rule, not a formatting choice.** Under it there is no
  percentage anywhere — not on the card, not on the share image — and the card has no fallback. If
  a future surface wants one it has to add it deliberately, which is the point.
* **The mover row is wired to nothing on purpose.** The app holds a 24-hour change for a watchlist
  and nothing weekly, and «moved most this week» fed by a daily figure is the failure this phase
  spent three items removing. `JournalWeekInputs.marketChanges` is where to hand one in when
  something measures a week.

### C6 — Rasad's morning brief: done in 5.2.0

The file this section named does not exist; the pattern actually copied was `LocalAlertWorker` and
`WidgetRefreshWorker`. Three things a next session should know rather than rediscover:

* **`PeriodicWorkRequest` is the wrong tool for a time of day.** It takes an interval, runs anywhere
  inside it, and cannot ask what time it is — so it drifts and cannot follow a time zone.
  `MorningBriefWorker` is one-time work that books its own successor, and `CoineProApp` re-arms the
  chain at every start so a dropped run does not end it for good.
* **Do not test a bitmap in `:app`.** Robolectric's default graphics rasterise nothing, so an
  assertion about pixels passes against an empty image; `@GraphicsMode(NATIVE)` makes it real and
  then loads a graphics library **outside the JVM heap**, which took this suite past the container
  and had it killed with SIGKILL — exit 137, no failing test anywhere, and `maxHeapSize` does not
  bound it. Pull the geometry out into a pure object instead. `BriefSparklineShape` is the pattern.
* **The composer refuses more than it says.** `RasadBrief` is where every «do not send this» lives,
  and each refusal has a reason written beside it.

### C3 — Duel with the past: done in 5.5.0

This section's plan was half right. The replay engine was indeed the piece to reuse, and the seeding
is `Duel.roundFor`. `ArenaStore` was **not** reused: the Arena keeps a row per day because its screen
shows a history, and a duel has nothing to show per round — «you said up on the 14th» is not a thing
anybody looks back at — so `DuelStore` keeps three counters and a day. Three integers cannot go out
of step with a list that does not exist.

Four things worth inheriting:

* **`TOO_CLOSE` is the feature.** A move under `Duel.FLAT_PERCENT` is neither right nor wrong. Score
  it as a win and the app teaches that noise is a read, which is the habit this product argues
  against. Any future surface over this record has to carry the third verdict, not fold it into one
  of the other two.
* **The hidden bars are hidden by `ReplayState.visible`**, not by a copy of it. The chart in a duel
  is in replay stopped at `round.atBar`; `Duel.judge` is the only thing in the feature that ever
  looks past that bar, and it runs after the call. There is no path by which the answer is on screen
  early, and there must not become one.
* **`DuelStore.answer` returns a boolean and the band prints the refusal.** The once-a-day guard is
  a read and a write inside one `edit`. A refusal the screen swallowed would look exactly like a
  counter that stopped working — the same class of fault as the alert that reads as armed and cannot
  fire.
* **The pending→navigate→`enterReplay`→`replayGoTo` dance is duplicated from the Arena on purpose,
  and it is not a copy.** The two pick by different arithmetic, one needs a paper-book mark and the
  other does not, and a shared helper would be a helper with a boolean in it. The duel's version
  carries a **boolean** rather than the round: the Arena measures its window against the chart the
  reader was on and then waits for the new one to be long enough, which on a shorter instrument is a
  wait that never ends. The duel re-reads the round from the loaded chart instead — the same round,
  because the instrument comes from the date alone. **The Arena still has the original shape; if
  anybody reports «میدان did nothing», that is where it is.**

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

# TradingView final parity report

The report the pixel-parity execution produces. It is regenerated whenever the comparison is run;
the numbered results below are what the last run actually returned, not what anybody hoped for.

---

## Verdict

```
COINEPRO REGRESSION GATES:  PASS
TRADINGVIEW VISUAL PARITY:  NOT YET PROVEN
BLOCKER:                    REFERENCE_MISSING
```

There is no capture of the real TradingView Android app in this repository, and none can be made on
the build host — it needs a device with their app installed. Every part of the pipeline that does
not depend on that reference is built, runnable and green. Nothing was substituted for the
reference: no web screenshot, no store image, no JPG, no resized PNG.

The phrases **Pixel Perfect**, **100% TradingView Parity** and **Exact TradingView Match** do not
appear as claims anywhere in this repository, and will not until all of
[§ Definition of done](#definition-of-done) holds.

---

## Environment

| | |
|---|---|
| CoinePro SHA | `a038c7bcd9572db20b741e7dbdcfaf01d4399fcf` |
| CoinePro version | `4.32.0` (versionCode `43200000`) |
| TradingView version | **— not captured** |
| Android API | canonical target 35; **no reference run** |
| Device | canonical target Pixel 6; **no reference run** |
| Resolution | canonical target `1080 × 2400`; **no reference run** |
| Density | canonical target `420 dpi`; **no reference run** |
| Font scale | canonical target `1.0`; **no reference run** |
| Locale | comparison locale `en-US` (LTR); CoinePro regression locale `fa-IR` (RTL) |
| Theme | dark for the full matrix, light for watchlist / explore / menu |

### Why two locales and not one

TradingView structural parity runs in **English, left-to-right**: both apps then draw the same
direction, text is directly comparable, baselines are baselines rather than mirror images, and far
less has to be masked. Comparing our Persian against their English would mask most of both frames
and measure almost nothing.

CoinePro's own regression stays **Persian, right-to-left**, because that is the product that ships.
Layer A's fourteen goldens are `fa-rIR-ldrtl` at 411 and 393 with one English case, and
`FoldMetricsTest` and `MenuRowMetricsTest` assert in both directions.

They are two different questions and are not merged.

---

## Reference SHA-256

| Screenshot | SHA-256 |
|---|---|
| `bottom-bar-watchlist-dark.png` | — not captured |
| `bottom-bar-chart-dark.png` | — not captured |
| `bottom-bar-explore-dark.png` | — not captured |
| `bottom-bar-ideas-dark.png` | — not captured |
| `bottom-bar-menu-dark.png` | — not captured |
| `watchlist-dark.png` | — not captured |
| `watchlist-light.png` | — not captured |
| `chart-dark.png` | — not captured |
| `explore-dark.png` | — not captured |
| `explore-light.png` | — not captured |
| `ideas-dark.png` | — not captured |
| `menu-dark.png` | — not captured |
| `menu-light.png` | — not captured |

Thirteen captures owed. The tool prints the same list:

```
$ python3 scripts/visual/compare_tradingview_reference.py --all --level certification
REFERENCE_MISSING

The TradingView → CoinePro gate is halted. It has no reference to measure against,
and a baseline invented once is a baseline trusted forever by people who were not
there when it was invented. This is a failure, not a pass.
  → exit 3
```

---

## Results

| Screen | Max anchor drift | Max edge shift | Mean edge shift | Masked | Unexplained static px | Result |
|---|---|---|---|---|---|---|
| Watchlist | — | — | — | — | — | `REFERENCE_MISSING` |
| Chart | — | — | — | — | — | `REFERENCE_MISSING` |
| Explore | — | — | — | — | — | `REFERENCE_MISSING` |
| Ideas | — | — | — | — | — | `REFERENCE_MISSING` |
| Menu | — | — | — | — | — | `REFERENCE_MISSING` |
| Bottom Bar | — | — | — | — | — | `REFERENCE_MISSING` |

No number was estimated, interpolated or carried over from a previous document.

### What each column will contain

| Metric | Gate |
|---|---|
| `maxAnchorDriftPx` | ≤ 1, target 0 |
| `maxEdgeShiftPx` | ≤ 1, target 0 |
| `meanEdgeShiftPx` | reported; a whole-frame drift shows here before it shows anywhere else |
| `maskedAreaRatio` | ≤ 5 % (watchlist, bottom bar) or ≤ 8 % (the rest) at certification |
| `unexplainedStaticPixelCount` | **0** |

---

## Anchors, per screen

Every anchor names two locators — a `testTag` and an edge on our side, and either a structurally
detected feature or a recorded coordinate on theirs. An anchor the reference cannot supply is
`ANCHOR_MISSING` and a non-zero exit; the comparator never guesses one.

| Screen | Anchors | Cadence | Notes |
|---|---:|---|---|
| Bottom bar | 14 | — | `barTop`, `dividerY`, `dividerThickness`, `barBottom`, `barHeight`, five `itemNCenterX`, `selectedIconBounds`, `iconCenterY`, `labelBaselineY`, `selectedStateBounds` |
| Watchlist | 16 | 5 rows | Page top through bottom-bar top, including four text baselines and the divider inset |
| Explore | 14 | — | Plus a **loading→loaded** self-stability gate on three elements |
| Ideas | 8 | 3 items | Plus a semantic assertion: exactly one root heading |
| Menu | 15 | **10 rows** | Section heading, row box, glyph column, title baseline, divider inset, chevron, next section |

Four of the bottom bar's anchors are structural and need nobody's help: `barTop`, `dividerY`,
`barBottom` and `barHeight` all derive from the bar's own hairline, which is found by contrast in
either app. The rest are `manual` and are honestly reported missing until a person records them.

---

## Remaining differences

Every entry carries exactly one classification. Nothing is described as "approximately fine".

| Classification | Screen | What |
|---|---|---|
| `INTENTIONAL_ACCESSIBILITY_DEVIATION` | Watchlist, Explore | Light-theme `marketUp #057A66` and `marketDown #D01427` against their `#089981` / `#F23645`. Theirs measure below 4.5:1 on our light surface; ours are 4.62:1 and 5.02:1. Dark theme uses their values unchanged. Accessibility is not traded for a score. |
| `INTENTIONAL_BRAND_DIFFERENCE` | Bottom bar | The selected tab sits on a raised neutral plate — the plate this design system's navigation rail already draws. If the reference proves they mark selection by ink alone, `selectedStateBounds` is where that surfaces. |
| `INTENTIONAL_BRAND_DIFFERENCE` | Bottom bar | Five destinations with our own names and glyphs. Root navigation is frozen. |
| `INTENTIONAL_BRAND_DIFFERENCE` | Watchlist | The header carries our own wordmark and its stream animation. |
| `INTENTIONAL_BRAND_DIFFERENCE` | Explore | Section order and the set of sections. Content architecture is frozen; geometry is what is compared. |
| `INTENTIONAL_BRAND_DIFFERENCE` | Ideas | Two faces behind one switch. Product architecture is frozen. |
| `INTENTIONAL_BRAND_DIFFERENCE` | Menu | The set of rows, their grouping and their order; and the seven rows that carry a second line (`MenuCatalogue.DESCRIPTIVE_ROWS`), which measure 51.0dp and are excluded from the ten-row cadence. |
| `COPY_DIFFERENCE` | Bottom bar, Menu | Words naming this product's screens. Masked at the glyphs; the box and its baseline stay measured. |
| `DYNAMIC_CONTENT` | Watchlist, Explore, Ideas | Prices, percentages, sparklines, symbol artwork, story and post ages, authors, avatars, price levels. Masked at the interior with a halo, so bounding boxes, gaps and dividers stay measured. |
| **Open — awaiting reference** | Chart | Time-axis type size: their published Charting Library says 11 px, our measurement of the phone product says 12 sp. Pinned by `TradingViewSourceConstantsTest` and not changed on the strength of a document. |
| **Open — awaiting reference** | Chart | Light-theme grid: `#e0e3e8` published, `#D5D5D5` measured here. |
| **Open — awaiting reference** | Chart | Their dark palette is unpublished; the widely quoted `#131722 / #1E222D / #2A2E39 / #D1D4DC` is folklore. This app does not use it. |

The three open items are **not** classified yet, deliberately. A published source constant beats a
colour sampled from a screenshot, but it does not beat the Android client on a question about the
Android client. A capture settles them; a document does not.

No `BUG` entries: nothing has been measured against a reference, so nothing can honestly be called
one.

---

## CoinePro regression gates

These do not depend on the reference and all pass.

| Gate | Result |
|---|---|
| Explore loading/loaded geometry, 411 + 393 × dark + light | `Δ = 0.0dp (0.0px)` on all four |
| Watchlist pre-row chrome, exact | `117.0dp` at fa-411, fa-393, en-411 (target 117 ±1, budget ≤125) |
| Bottom bar app chrome, exact | `65.0dp` = 1.0 divider + 64.0 content (target 65 ±0.5, budget ≤70) |
| Menu ordinary rows | `50.0dp`, all of them, ±0.5 |
| Menu descriptive rows | `51.0dp`, seven whitelisted, none above the 52dp ceiling |
| Golden screenshots | 14 committed frames, all matching |
| TradingView published constants | 11 assertions, all matching |
| Visual-parity specs | 5 specs well-formed |
| Reference manifest | `REFERENCE_MISSING` (exit 3) — the documented state |

---

## CI

Every job must be green on **one** SHA for a release candidate to be valid.

| Workflow | Job | Result |
|---|---|---|
Certifying SHA: **`a038c7b`**, released as **`v4.32.0`**.

| Workflow | Job | Run | Duration | Result |
|---|---|---|---:|---|
| Android CI | Lint, test, build | #558 | 38m | ✅ success |
| Android CI | Compose UI | #558 | 8m | ✅ success |
| Android CI | Performance smoke | #558 | 12m | ✅ success |
| Security CI | — | #389 | 1m | ✅ success |
| Build Android APK | — | #134 | 15m | ✅ success |

Released artefact: `CoinePro-4.32.0.apk`, 17,589,420 bytes, SHA-256
`0119d97e08fb04917d3aec7114760377c66e53c4b5ca46562bbb49940e3e8738`, signing certificate SHA-1
`5de87f4bb3e8356b4e981ed4da630ba7775f9aa8` (`CN=CoinePro, OU=Mobile, O=CoinePro, L=Tehran, C=IR`),
`versionCode 43200000`. Downloaded from the release and verified against the release digest, not
against the build log.

### The instrumented capture, now that it is real

`coinepro-actual-menu-screenshot` is **154,343 bytes, 1080 × 2400 PNG**. For nine releases it was
forty-two bytes reading `run-as: unknown package: com.coinepro.app` — Gradle uninstalls the app
after `connectedAndroidTest`, so the workflow's `run-as` had no package to enter, and its `test -s`
check passed the error message because an error message is not empty. It is now pulled from
`/sdcard`, which outlives the uninstall, and checked for the PNG magic bytes rather than for being
non-empty.

Two pushes were spent learning the same lesson twice: `android-emulator-runner` feeds its `script:`
to `sh` **a line at a time**, so nothing in it may span lines — not a backslash continuation, not a
`case`. Both symptoms had one cause and the second was misread as a quoting problem. The check is a
single line, verified by extracting that exact line from the YAML and running it through `sh -c`
against a real golden and against the forty-two bytes it exists to catch.

Incidentally, that capture confirms the CI emulator is the canonical profile: **1080 × 2400**, which
is what `VisualParityCaptureTest` requires before it will write anything.

The `Lint, test, build` job covers, on the same SHA: five quality gates, the release version
contract, visual-parity spec validation, reference-pack verification, three lint variants, the unit
suite of twelve modules plus the app (goldens, fold metrics, explore geometry, menu row metrics,
TradingView source constants), four assembles, and the protected signing path.

CI never records a golden, never overwrites a reference, and never accepts a changed capture
because a comparison failed.

---

## Definition of done

| # | Condition | Status |
|---:|---|---|
| 1 | TradingView captured on the canonical device | ❌ |
| 2 | Their version recorded off the device | ❌ |
| 3 | Screenshots carry SHA-256 | ❌ |
| 4 | Reference manifest verifies | ❌ |
| 5 | CoinePro actual from the same device configuration | ❌ |
| 6 | Nothing resized | ❌ *(nothing to resize)* |
| 7 | System UI normalised or cropped, crop recorded | ❌ |
| 8 | Bottom bar anchors checked | ❌ |
| 9 | Watchlist 5-row cadence checked | ❌ |
| 10 | Explore loading/loaded checked **on device** | ❌ (asserted in points by `ExploreGeometryTest` ✅) |
| 11 | Ideas header/switch checked | ❌ |
| 12 | Menu 10-row cadence checked | ❌ |
| 13 | Typography baselines checked | ❌ |
| 14 | Static colours checked | ❌ |
| 15 | Mask budget passes | ❌ |
| 16 | Max critical anchor drift ≤ 1px | ❌ |
| 17 | Max critical edge shift ≤ 1px | ❌ |
| 18 | Unexplained static pixels = 0 | ❌ |
| 19 | All CoinePro goldens pass | ✅ |
| 20 | All CI green on one SHA | ✅ `a038c7b` |

Eighteen of twenty are blocked on one thing.

---

## What is needed to finish

**A device, or an AVD with the Pixel 6 profile at API 35, with the official TradingView Android app
installed.** Then:

1. pin the device and read every setting back (`docs/design/TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md` § 1);
2. read their `versionName` and `versionCode` off `dumpsys` (§ 2);
3. take the thirteen captures with `adb exec-out screencap -p` (§ 3–4);
4. capture the CoinePro side on the **same device in the same session** with `VisualParityCaptureTest`;
5. file the pack under `docs/design/reference/tradingview-android/<versionName>/` with its manifest
   and checksums (§ 6–7);
6. record the `manual` anchor coordinates into the five specs;
7. run `python3 scripts/visual/compare_tradingview_reference.py --all --level certification`.

No code change is needed for any of that. The gate runs the moment the pack exists.

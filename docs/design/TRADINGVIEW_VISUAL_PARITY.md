# TradingView visual parity — what "pixel perfect" is allowed to mean

This is the policy the comparison runs under. It exists because "pixel perfect" is a phrase that
survives almost any amount of difference unless somebody writes down, in advance, what would falsify
it.

> **Status: `PARITY NOT YET PROVEN`.**
> The pipeline in this document is built and runnable. No TradingView reference capture exists in
> this repository, so the TradingView layer of the gate halts with `REFERENCE_MISSING` and reports
> nothing. See [Rule Zero](#rule-zero-what-may-be-used-as-a-reference) and
> [TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md](TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md).

---

## Rule Zero: what may be used as a reference

A pixel golden is a claim that *these exact pixels are what the other app draws*. That claim can only
be made from a capture of the other app.

**Forbidden as a pixel golden, without exception:**

* a screenshot found on the web, in an article, in a review, in a store listing or in a press kit;
* a Play Store or App Store thumbnail;
* any JPG or WebP, because both are lossy and neither can carry an exact colour;
* any image that has been resized, cropped by a viewer, re-encoded, or had its aspect ratio changed;
* an image whose provenance — device, density, font scale, theme, locale, app version — is unknown;
* a re-render of somebody else's design in a design tool, however faithful.

**Required of a pixel golden:**

* a PNG taken from the real TradingView Android app,
* on the same device profile, resolution and density as the CoinePro capture it will be compared to,
* at the same font scale, navigation mode, theme, locale and orientation,
* recorded with the metadata in
  [TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md](TRADINGVIEW_ANDROID_REFERENCE_CAPTURE.md),
* checksummed, so the file that was measured is provably the file in the repository.

**If no such capture exists, the gate halts.** It does not substitute a web image, it does not scale
something close, and it does not "approximate for now". A baseline invented once is a baseline that
is trusted forever by people who were not there when it was invented.

### Three classes of evidence, and what each one may decide

Rule Zero governs *pixel goldens*. It is not a claim that a screenshot is the only thing that can
ever be known about TradingView, and treating it that way throws away the strongest evidence there
is. There are three classes, and the mistake to avoid is letting one answer a question that belongs
to another.

| Class | What it is | May decide | May **not** decide |
|---|---|---|---|
| **Published source constant** | A value TradingView itself publishes: the Charting Library's `ChartPropertiesOverrides` defaults, the open-source Lightweight Charts renderer's arithmetic. | Colour hexes, dash tables, renderer formulas, type sizes *of the library*. | Anything about layout on a phone. A default is not a rendered frame. |
| **Device capture** | A PNG off the real Android app on the canonical device, filed per the capture protocol. | Position, size, gap, baseline, divider, cadence — everything that only exists once something has been drawn. | Nothing; this is the strongest class, and it is the one this repository does not have. |
| **Qualitative reference** | A web screenshot, a store image, an article, a JPG. | Composition, navigation order, whether a thing exists at all, direction of travel. | Any pixel, any colour, any measurement. |

A source constant beats a colour sampled from a lossy screenshot, because there is no encoder, no
scale factor and no display profile between it and the truth. It does **not** beat the Android
client on a question about the Android client — a phone app is entitled to override a library
default, and the Charting Library is not the phone app.

Class one is executable here: `core/chart/.../TradingViewSourceConstantsTest` holds this app's
renderer against TradingView's published arithmetic — the 5/1, 9/3, 16/4 body-and-gap table, the
28-pixel time axis at 12-point type, the price axis as fixed chrome plus label rounded to an even
count, the five dash patterns, and the candle pair.

#### Two corrections that class one produced

* **The dark palette in circulation is folklore.** `#131722 / #1E222D / #2A2E39 / #D1D4DC` is
  quoted everywhere and TradingView has published none of it. Two neighbours of it *are* confirmed
  (`#131722` as an axis text colour, `#363A45` as a dark-theme crosshair label ground), which is a
  hint and not a proof. This app does not use that set — it draws `#0F0F0F` / `#282828`, measured —
  and nothing here should start quoting the folklore as a source.
* **The light grid has two answers.** The published Charting Library default is `#e0e3e8`; this
  app draws `#D5D5D5`, measured off the phone product. Both are honestly sourced and they are not
  the same number. It is an open question of the same shape as the time-axis type size, and it is
  settled the same way: by a capture of the Android client, not by preferring whichever source is
  nearer to hand.

Every such open question stays written down at the point where the number is set, so that the next
person to read the constant meets the argument rather than the conclusion.

### The JPGs already in this repository are not goldens

`docs/design/reference/tradingview-phone/*.jpg` are seven lossy web captures. They were used to read
*intent* — that the bar has no pill, that the axis ink is grey — and that is a legitimate use. They
are **not** admissible as pixel goldens under this policy and the comparator will not accept them:
they are JPGs, their provenance is a browser, and their density is not a phone's.

---

## Pixel Perfect means Zero Unexplained Diff

Not "small diff". Not "0.1 % of the frame is fine". **Zero unexplained diff**: every differing pixel
is either fixed or is inside a named, argued exception.

There are exactly four kinds of explained difference, and each has to be written into the screen's
spec file with a reason:

| Label | What it means |
|---|---|
| `INTENTIONAL_BRAND_DIFFERENCE` | Ours on purpose: our wordmark, our gold, our Persian type. |
| `INTENTIONAL_ACCESSIBILITY_DEVIATION` | Ours on purpose because theirs fails a contrast floor we hold. |
| `DYNAMIC_CONTENT` | A price, a timestamp, an avatar — content, not layout. Masked. |
| `REFERENCE_VERSION_DIFFERENCE` | Their app changed; the reference pack is older than what ships now. |

Anything else is a `BUG` and is fixed. A difference with no label is not a tolerance, it is an
unfinished sentence.

### What a global tolerance would buy, and why it is not bought

A 0.1 % frame tolerance on a 1080 × 2400 phone is **2,592 pixels**. That is a whole misplaced
divider, a mis-set label, a row eight points too tall — every fault this project has actually
shipped, waved through. Frame-share tolerance is a measure of how much of the screen is wrong, which
is not a question anybody was asking. The questions are *is this in the right place* and *is this
the right colour*, and those are answered per-measurement, in pixels, at ≤ 1.

---

## Two golden systems, kept apart

They answer different questions and must never be merged into one number.

### Layer A — CoinePro self-regression

`app/src/test/goldens/*.png`, compared by `GoldenScreenshot`. **Did our own screens change since the
last commit?** Robolectric, `xxhdpi`, our fixtures, our locales. Fast, runs on every push, and is
about *us*.

It carries a small frame tolerance on purpose (anti-aliasing moves between JDK builds), and that is
correct for a self-regression: the baseline and the render come from the same renderer, so a real
change moves far more than the tolerance.

### Layer B — TradingView structural parity

`visual-parity/` — device captures on both sides, compared by
`scripts/visual/compare_tradingview_reference.py`. **Do our geometry, our type roles and our colours
agree with theirs?** Real emulator or device, real density, no simulation.

Layer B has **no frame tolerance at all**. Its gates are per-measurement and its default is zero.

Layer A going green says nothing about Layer B, and Layer B is not a substitute for Layer A. A
release needs both.

---

## The canonical device

| Property | Value | How it is verified |
|---|---|---|
| Device | Pixel 6 | `adb shell getprop ro.product.model` |
| Resolution | 1080 × 2400 | `adb shell wm size` |
| Density | ~420 dpi (`420`) | `adb shell wm density` |
| Font scale | 1.0 | `adb shell settings get system font_scale` |
| Orientation | portrait | fixed by the capture test |
| Logical size | ≈ 411.4 × 914.3 dp | 1080 / (420/160), 2400 / (420/160) |

Every one of those is read **off the device** and written into the manifest. None of them is assumed.

### Robolectric's `xxhdpi` is not a Pixel 6

`xxhdpi` is 480 dpi. A Pixel 6 is 420. So a Robolectric frame at `w411dp-h914dp-xxhdpi` is
1233 × 2742 physical pixels and a Pixel 6 frame at the same logical size is 1080 × 2400. **Those two
images can never be compared to each other**, and the fact that both are "411 dp wide" is exactly the
trap. Layer A lives at 480 dpi and stays there; Layer B lives at 420 dpi on a real AVD and stays
there.

### The narrow profile

393 dp remains a regression width — it is the phone most readers actually hold — but it is Layer A's.
If a Layer B capture is ever taken at 393, its own resolution and density are recorded in the
manifest beside it and it is compared only against a TradingView capture at the same two numbers.

### Scaling is forbidden

Not discouraged. **Forbidden.** If the reference and the actual differ in width or height by a single
pixel, the comparator reports `SIZE_MISMATCH` and fails. Resampling invents pixels that neither app
drew, and every measurement taken afterwards is a measurement of the resampler.

### Registration is forbidden

The comparator does not search for the alignment that minimises the difference. Images are compared
at (0, 0). If a screen needs to be translated by more than 1 physical pixel to line up, **that
translation is the finding** — it is a real misplacement of the whole layout, and hiding it under an
automatic offset is how a systematically shifted UI passes a parity gate.

---

## Masks

A mask is a promise that what is underneath is content rather than layout. It is the single easiest
place for a parity gate to become theatre, so:

* **Only named semantic classes may be masked**: `price`, `percent-change`, `timestamp`, `avatar`,
  `sparkline`, `chart-plot`, `user-name`. Nothing else, and no free-form rectangles.
* **Every mask is declared in the screen's spec file**, with its class and its reason.
* **A halo is preserved.** A mask covers the glyphs, never the geometry around them: the row's
  bounds, its dividers, its column edges stay visible and stay compared. A mask that swallows the
  edge it was meant to prove is not a mask, it is a deletion.
* **`masked_area_ratio` is reported on every run**, per screen and in total.
* **The budget is 15 % of the frame per screen.** Past that the comparator fails with
  `MASK_BUDGET_EXCEEDED` — because at some point a masked comparison is a comparison of the mask.

---

## The gates

All distances are **physical pixels** on the canonical device.

| Gate | Budget |
|---|---|
| Element position (x, y) | ≤ 1 px |
| Element size (w, h) | ≤ 1 px |
| Gap between siblings | ≤ 1 px |
| Divider thickness and inset | ≤ 1 px |
| Centre alignment | ≤ 1 px |
| Required registration translation | ≤ 1 px, else `FAIL` |
| Frame size | exact, else `SIZE_MISMATCH` |
| Colour, centre of a fill | 0 per channel |
| Masked area, per screen | ≤ 15 % |

### Typography is compared by role, not by raster

Their font is not our font and never will be: this app is Persian-first and set in IRANYekanX. So
glyph rasters are not compared and a diff of them would be noise. What is compared is what a
typographic system actually promises:

* the **role** each string plays (title, secondary, numeric) and that it is consistent down a list;
* the **baseline** of the first line of each role, against the row's own box;
* the **bounding box** of the text block, so a size or leading change is caught;
* **alignment and direction**, which is where a mirrored layout goes wrong.

### Colour is sampled from direct PNGs only

A colour read from a JPG is a colour the encoder invented. Samples are taken from the centre of a
fill — never on an edge, where anti-aliasing is legitimate — and must match on all three channels
exactly.

Light-theme `marketUp` and `marketDown` are a deliberate exception, already documented in the
palette: TradingView's `#089981` and `#F23645` fall below 4.5:1 on our light surface, so we draw
`#057A66` (4.62:1) and `#D01427` (5.02:1). That is an `INTENTIONAL_ACCESSIBILITY_DEVIATION`, it is
declared in the spec files, and it is the only colour deviation permitted without a bug.

---

## What is compared, screen by screen

| Screen | What the audit asserts |
|---|---|
| Bottom bar | Total height, divider thickness, tab count, per-tab centre x, glyph box, label baseline, selected-state treatment. |
| Watchlist | Row height and the cadence of five consecutive rows — each gap measured against the first, so drift accumulating down a list is caught rather than averaged away. Column edges, divider inset. |
| Explore | Section order, card strip height, card width and gap, chip row height, first story y — **in both the loading and the loaded state**, because a fold that is right in one and wrong in the other is the fault this pass was opened for. |
| Ideas | Face switch geometry, and a semantic assertion that there is **exactly one** root heading — the duplicated-header fault is structural, not pixel, and a picture alone would not name it. |
| Menu | Eight to ten consecutive rows, each row's height and each gap, so a whitelist exception cannot hide inside an average. |
| Chart | Sanity only: that it draws, and that shared tokens have not leaked into the plot. Chart geometry and palette are settled and out of scope. |

---

## Running it

```bash
python3 -m pip install -r scripts/visual/requirements.txt
python3 scripts/visual/verify_reference_manifest.py            # provenance and checksums
python3 scripts/visual/compare_tradingview_reference.py --all   # the comparison
```

Outputs, per screen, under `build/visual-parity/<screen>/`:

`reference.png`, `actual.png`, `overlay-50.png`, `absolute-diff.png`, `edge-diff.png`,
`mask-preview.png`, `measurement-report.json` — plus `build/visual-parity/summary.json` and
`build/visual-parity/report.md`.

Without a reference pack every screen reports `REFERENCE_MISSING`, the run exits non-zero, and the
report says which captures are owed.

---

## Definition of done

The words "Pixel Perfect" and "100 % TradingView parity" may be written **only** when all eight hold:

1. A real TradingView Android capture pack exists, with full metadata and checksums.
2. Both sides were captured on the same device profile, at the same density and font scale.
3. No image was scaled, re-encoded, or registered.
4. Every geometry gate passes at ≤ 1 physical pixel.
5. Every colour sample matches exactly, except declared accessibility deviations.
6. Masked area is inside budget and every mask is a named class with a reason.
7. Every remaining difference carries one of the four labels.
8. Every CI job is green on one SHA.

Until then the honest sentence is **`PARITY NOT YET PROVEN`**, and that is what this repository says.

# Design report — Pro Chart → TradingView-grade

Every sprint of the visual master prompt, with what changed, the numbers before and after, the
pictures, an honest score per screen, and the decisions left open. Pictures live in
`docs/design/before/` and `docs/design/after/`, forty each: ten screens × {dark, light} × {fa, en},
rendered on the Pixel 6a profile (411×914 dp, 2.625×) through the app's native-Skia screenshot
rig (`DesignCaptureTest`, the same camera the goldens use — Roborazzi's job, without adding a
dependency that draws the same pixels).

## Corrections to the prompt's context, read from the tree at 4.41.0

The prompt was written against 4.33.0.8. Several of its "nothing changed" statements were true
then and are not now; several were never true. Recorded here so the sprints start from the real
baseline:

| Claim | Reality at 4.41.0 |
| --- | --- |
| "no image-loading library (no symbol logos, flags, avatars, sparklines)" | Logos and flags are **drawn**: `CoineProAssetLogo` / `CoineProPairLogo` render vendored vector artwork for every listed symbol (BTC, ETH, SOL, gold + the dollar flag are in `before/watchlist-fa-dark.png`). The owner's standing rule is that no symbol without artwork reaches a list. Sparklines exist on the markets tab (`sparklineStore`) but not on the watchlist rows or home holdings. There is no Coil; avatars are initials. |
| "animation is 13× tween vs 1× spring" | 47 `tween(` / 6 `spring(` across the tree; the navigation slide and the three spatial springs came in 4.41.0 (`CoineProMotionSpecs`). |
| "no predictive back" | `enableOnBackInvokedCallback="true"` since 4.41.0. |
| "no haptics on tools" | 23 files use `CoineProHaptics`; the magnet snap and the crosshair-over-a-level tick came in 4.41.0. |
| "chart engine untouched (no decay / rubber-band / spring auto-scale)" | Fling is the platform spline (`FlingSpline`) since 4.41.0; rubber band existed (`stretchEdge`); auto-scale is not animated — Sprint C. |
| "one shimmer reference" | `CoineProSkeleton` / `CoineProSkeletonRows` used in 7 feature files; six list screens still show a centred spinner somewhere — Sprint A5. |
| "no certificate pinning" | Present behind `COINEPRO_CERTIFICATE_PINS`; inert until the owner supplies pins. |
| "`values/` is still Persian" | True, by the owner's decision (Phase 0). Sprint E1 reopens it; see *Open decisions*. |

## Sprint A0 — tooling and the BEFORE set

**What changed.** `DesignCaptureTest` (app) renders the ten screens through `ChartDesignPreviews`
(feature/chart), a public door onto the internal timeframe, drawing-tools, drawing-settings and
analysis-hub sheets. `ChartFlingBenchmark` already existed (4.41.0) with `FrameTimingMetric` over
a 3 s fling, pinch cycles, drags and a long press; `check-benchmark-thresholds.py` judges its JSON.

**Baseline numbers (4.41.0).**

| Measure | Value |
| --- | --- |
| `tween(` / `spring(` in main sources | 47 / 6 |
| Files with a centred `CircularProgressIndicator` | 6 |
| Files using `CoineProSkeleton*` | 7 |
| `androidx.compose.material.icons` imports outside the design system | 0 (own icon set, gated) |
| Font files | IRANYekanX Regular + Bold (static, not variable) |
| Image loader | none |
| Universal APK | 17.0 MB; per-device download 13.7–13.9 MiB |
| Help images in the base APK | 215 WebP, 9.5 MB (`core/help/src/main/assets/help`) |
| Chart frame times | **not measurable here** — no device or GPU in this environment; the benchmark and its CI judge are in the tree, the number is a phone's to produce |

**BEFORE pictures.** `docs/design/before/*.png` — 40 files.

**Self-score before (dark, fa; the harder half), out of 100, against the TradingView reference:**

| Screen | Score | Why |
| --- | --- | --- |
| Home | 55 | Balance hero and cards are right; numerals proportional, teaching banner takes the top, no sparkline on holdings. |
| Watchlist | 60 | Logos, tabular-looking columns, correct colours; a 4-line explanatory banner above the rows, no sparkline, 20 dp of dead air per row. |
| Symbol + chart | 70 | Grid, wicks, last-price label, legend, volume are close; header lacks the full-name/market-state line, axis labels one weight, toolbar icons mixed metaphors. |
| Drawings sheet | 45 | A rail of rows, not the 3-column tile grid; no search, no tabs, no favourites strip. |
| Indicators sheet | 55 | List with descriptions; no favourites/recent chips, no auto-focused search. |
| Drawing settings | 50 | Colour and width exist; no tabs, no style/fill, no templates UI parity. |
| Timeframe sheet | 65 | Groups and starring exist; no date-range chips, chips are not 48-tall pills. |
| Analysis hub | 70 | Tiles already TradingView-shaped; spacing and the broker card border differ. |
| DOM | 60 | Ladder is right; numerals proportional, no depth bars behind rows. |
| Menu | 65 | Clean rows; subtitles under a third of them, header card heavy. |

## Open decisions

1. **IRANYekanX Medium / SemiBold.** The shipped files are static Regular and Bold; a variable file or the two extra weights are the owner's licence to obtain. Until then Persian headings stay Bold and Latin/numerals get Inter's Medium and SemiBold (Sprint A2).
2. **Symbol logos from the network (A4).** The app vendors vector artwork and refuses to list a symbol without it (owner's rule). Sprint A4 keeps that as the first source and adds Coil for avatars and as a *fallback* provider for symbols outside the vendored set; it does not replace drawn logos with downloads.
3. **Locale inversion (E1)** conflicts with the owner's Phase 0 decision that Persian stays the default. Sprint E will not flip `values/` without the owner saying so again; the build check for "no Arabic script in `values/`" is meaningless under the current layout and is replaced by the existing "English locale is English" gate.
4. **Product flavours (E7)** were replaced in Phase 2 by a `BuildConfig` gate plus an APK-reading check; the store build already carries no admin tooling.

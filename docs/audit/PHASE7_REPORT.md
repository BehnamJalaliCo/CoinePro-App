# Phase 7 — visual design system, chart aesthetics, scroll feel

## 7.1 Tokens

| Item | State |
| --- | --- |
| Single source | Already one: `core/designsystem` (`CoineProPalette`, `CoineProSpacing`, `CoineProShapes`, `CoineProType`, `CoineProMotionSpecs`, `CoineProPress`) and `core/chart/TradingViewPalette` for the plot. The audit's `ui/theme/Tokens.kt` is these files under their existing names; nothing was moved for the sake of a path. |
| `docs/design/TOKENS.md` | **Written** — every role, value and rule, with the file it comes from. |
| Dynamic colour | Off; no `dynamic*ColorScheme` in the tree. |

## 7.2 Imagery

Not done. The help catalogue is text; there is no illustration set, and the asset-pack plugin is
not in the offline cache (Phase 2). This is the owner's artwork to commission; the on-demand
delivery mechanism is a build change on top of it, not before it.

## 7.3 Chart aesthetics

Reviewed against the pixel spec, not changed: the grid is TradingView's measured `#282828` at full
alpha (it used to be a 12 % white at 35 % alpha — four percent of ink — and was fixed before this
audit), hairlines 0.8 dp, lines 1.6 dp, axis padding 10 dp, candle body 72 % of the slot with a
3 dp pin between 2.5 and 4 dp spacing, wicks and bodies snapped to device pixels, up/down
`#089981` / `#F23645`. Session breaks, weekend shading, watermark and event glyphs are drawn under
the bars. Label collision on the price axis was not re-audited pixel by pixel.

## 7.4 Scroll and zoom physics

| Item | State |
| --- | --- |
| Fling curve | **Changed in Phase 4**: the platform's `SplineOverScroller` spline, density-aware, ends at a known time. |
| Rubber band, edge load, centroid pinch, axis drag, double-tap reset | Present (Phase 4 report names the lines). |
| Draw-phase invalidation, crosshair on its own layer, cached static layers | Present. |
| Measured frame times | `ChartFlingBenchmark` exists; needs a phone. |

## 7.5 Shell polish

| Item | State |
| --- | --- |
| Icon lint | **Added**: `check_icon_sources` in the consistency gate refuses `androidx.compose.material.icons` and `Icons.Filled/Default/…` anywhere but the design system. The two `Icons.Filled` the audit counted are `CoineProIcons.Filled.Chart` — the app's own filled variant for the selected tab — and are allowed. |
| Springs, skeletons, predictive back, haptics | Phase 5. |
| Fonts | Regular + Bold shipped; more weights await the owner's licence. |

## 7.6 Definition of done

`docs/design/BEFORE_AFTER.md` records what changed on screen and what only a phone can show.
Gates, every unit test and `:app:assembleRelease` pass on the final tree.

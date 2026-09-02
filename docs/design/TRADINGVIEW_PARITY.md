# TradingView parity — measured, not remembered

The owner's brief: the chart must be TradingView's, point for point — not "a chart with the same
tools". This document is the measurement the work is done against. Every number below was read
off a real render of `tradingview.com/chart` on 2026-09-02, and each line names where the app
implements it. A line without an implementation is a line still owed.

## How the reference was taken

Headless Chromium (Playwright) at a **411 × 914 css px** viewport, device scale factor **2**,
mobile user agent, dark and light themes. TradingView's TLS is not reachable from this build host
directly, so every page request was routed through Node's proxied `fetch` and the chart's data
socket was bridged through a proxied WebSocket client (`scratchpad/tv/shot2.js`). The pixels were
then sampled with PIL. Colours are device-pixel exact; sizes are css px unless stated.

Also rendered: TradingView's own open-source engine, Lightweight Charts 4.2, with its defaults, at
the same viewport — the app's `ChartPixels.kt` already carries that engine's bar-width, wick and
axis-margin arithmetic, ported line for line.

## The palette (2025 neutral theme)

| Element | TradingView | App | Where |
|---|---|---|---|
| Pane | `#0F0F0F` | dark template `background`; theme mode draws on the page stage | `TradingViewPalette.DARK_BACKGROUND` |
| Grid | `#282828`, **dotted** 1 on / 3 off | `#282828` opaque, dotted `[1dp, 3dp]` | `CoineProChart.drawGrid`, `GRID_ALPHA = 1f` |
| Candle up / down | `#089981` / `#F23645` | same | `TradingViewPalette.UP / DOWN`, built-in templates |
| Volume | candle colour at 50 % (`#1A5A54` measured) | `VOLUME_ALPHA = 0.5f` | `CoineProChart.drawVolume` |
| Axis label ink | brightest pixel `#A6`–`#B5` (12 px text) → `#B2B2B2` | `DARK_TEXT` | `ChartPalette.text` |
| Legend title ink | `#DBDBDB` | `DARK_TEXT_PRIMARY` | `ChartPalette.title` |
| Chrome hairlines | `#2E2E2E`, 1 px | `BorderStrong` divider under the header | `ChartScreen.Header` |
| Symbol pill | `#3D3D3D`, 28 px tall, 14 px text | `SurfaceRaised` pill, 28 dp, `labelLarge` | `ChartScreen.Header` |
| Live-price tag | fill = candle colour, **white** text | `TAG_INK = Color.White` | `drawAxisTag` |
| Crosshair tag | white text | `TAG_INK` | `drawCrosshair` |
| Light pane / grid / text | `#FFFFFF` / `#E6E6E6` / `#5C5C5C` | light template | `ChartLayoutStore.Light` |

## Geometry

| Element | TradingView | App | Where |
|---|---|---|---|
| Price axis width | 72 px for `83,000.00` at 12 px | `priceAxisWidth` = 21 + label width (Lightweight Charts' formula) | `ChartPixels.priceAxisWidth` |
| Label inset from axis edge | 10 px (tick 5 + inner 5) | `AXIS_PADDING_DP = 10.dp` | `CoineProChart` |
| Price label size | 12 px | 12 sp | `PRICE_AXIS_FONT_SP` |
| Time label size | 12 px | 12 sp (was 11) | `TIME_AXIS_FONT_SP` |
| Time axis height | 28 px | `timeAxisHeight(12sp)` = 1 + 5 + 12 + 3 + 3 + 4 = 28 | `ChartPixels.timeAxisHeight` |
| Major tick labels | every fifth rung **bold** (`80,000` among `79,000`, `81,000`) | `isMajorTick(price, step)` → bold | `drawPriceAxis` |
| Thousands | grouped, `77,310.00` | `groupThousands` on axis, tag and legend | `CoineProChart`, `ChartLegendOverlay` |
| Live-price tag | 30 px tall: price line over countdown line, one fill | two-line `drawAxisTag(secondLine = countdown)` | `drawLastPrice` |
| Last-price line | dotted | `SPARSE_DOTTED` | `drawLastPrice` |
| Candle body | `optimalBarWidth` (Lightweight Charts) | same function | `ChartPixels.optimalBarWidth` |
| Header bar | 38 px, 1 px rule under | 28 dp pill + 5 dp padding = 38 dp, `HorizontalDivider` | `ChartScreen.Header` |
| Toolbar buttons | 38 px | interval keys 38 dp | `INTERVAL_KEY_HEIGHT` |

## The legend

TradingView, phone width:

```
[mark] Bitcoin / TetherUS  ●          16 px, #DBDBDB, bold-ish
O 77,004.19 H 77,182.00 L 76,748.01 C 77,058.57  −403.47 (−0.52%)   13 px, letters muted, figures in the bar's colour
Vol · BTC  474                        13 px
```

No plate behind it. The app's legend now: title at 1.25 × the value size in the primary ink,
values in **O H L C** order with the letters in the axis ink and the figures in the direction
colour (`ohlcAnnotated`), the change row under it, every figure grouped, and the plate gone
(`LEGEND_PLATE_ALPHA = 0f`).

## What is deliberately not copied

* **The font.** TradingView sets `-apple-system, Trebuchet MS, Roboto`; this app is IRANYekanX by
  the owner's standing rule. Every size above is matched in sp; the glyphs are ours.
* **The frame.** TradingView's 2025 theme draws no line between the pane and its scales. The owner
  asked for a frame in the 4.19.0 round («چارت چهارچوب نداره») and it stays.
* **The left tool rail.** TradingView's phone *web* page keeps a desktop rail on the left; its
  Android app puts the tools in a bottom sheet, which is what this app does.

## Still owed

* A side-by-side render diff (app screenshot vs TradingView screenshot, same viewport) as a
  checked-in test artefact rather than a manual comparison.
* The interval picker as a text row (`1m 30m 1h ▾`) rather than pills, measured at 38 px.
* Crosshair label geometry (TradingView's is 24 px tall with 8 px side padding) — the app's tag
  uses the price tag's padding.

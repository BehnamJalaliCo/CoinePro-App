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
| Chrome hairlines | `#2E2E2E`, 1 px (web); `#EBEBEB` on white (phone) | `BorderSubtle` divider above the toolbar | `ChartCommandBand` |
| Symbol pill (web only) | `#3D3D3D`, 28 px tall | not drawn: the phone app has no header, the symbol sits on the toolbar | `ChartCommandBand` |
| Live-price tag | fill = candle colour, **white** text | `TAG_INK = Color.White` | `drawAxisTag` |
| Crosshair tag | white text | `TAG_INK` | `drawCrosshair` |
| Light pane / grid / text | `#FFFFFF` / `#D5D5D5` / `#0F0F0F` (phone, measured) | light template | `ChartLayoutStore.Light` |

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
| Toolbar buttons | 38 px (web) / 44 pt (phone) | 44 dp toolbar and targets | `TOOLBAR_HEIGHT` |

## The legend

TradingView, phone width:

```
[mark] Bitcoin / TetherUS  ●          16 px, #DBDBDB, bold-ish
O 77,004.19 H 77,182.00 L 76,748.01 C 77,058.57  −403.47 (−0.52%)   13 px, letters muted, figures in the bar's colour
Vol · BTC  474                        13 px
```

No plate behind it. The app's legend now: title at 1.21 × the value size in the primary ink,
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

## The phone app (owner's screenshots, iPhone 3×, light)

| Element | TradingView phone | App | Where |
|---|---|---|---|
| Legend mark | 17 pt disc, 9 pt from the left | `LEGEND_LOGO_DP = 17`, inset 9 | `ChartLegendOverlay.LegendHead` |
| Legend title | 17 pt, `#0F0F0F` | 14 sp × 1.21, primary ink | `TITLE_SCALE` |
| Legend price line | 14 pt, direction colour, `77,414.00 −17.01 (−0.02%)` | same string, resting | `restingHead` |
| Market dot | 15 pt disc at 15 %, 7 pt dot | `STATUS_DISC_DP / STATUS_DOT_DP` | `LegendHead` |
| Quote chip | 74 × 26 pt, 1 px hairline, 4 pt in from top-right | `QuoteChip`, 26 dp, `AbsoluteAlignment.TopRight` | `ChartScreen` |
| Grid (light) | solid `#D5D5D5` | `LIGHT_GRID` | `TradingViewPalette` |
| Scale labels (light) | `#0F0F0F`, 14 pt, majors bold | `LIGHT_TEXT`, 12 sp, `isMajorTick` | `drawPriceAxis` |
| Live tag | 31 pt tall, two lines, white on the candle colour | `drawAxisTag(secondLine)` | `drawLastPrice` |
| Toolbar | 44 pt, hairline above, symbol + interval bold 16 pt left, 22 pt glyphs right | `ChartCommandBand` | `ChartChrome` |
| Sheet title | 24 pt bold + 40 pt round close | `headlineSmall` bold + `SHEET_CLOSE = 40.dp` | `CoineProSheetBody` |
| Hub tiles | 100 pt, 12 pt corners, 3 across outlined / 2 across plates | `HUB_TILE = 100.dp`, `HubGrid` | `ChartMoreSheetBody` |
| Interval chips | 44 pt tall, grey plate, chosen inverted | `IntervalPill` | `ChartScreen` |
| Tool tiles | grey plate, armed inverted | `ToolCell` | `ToolRail` |

## The second round of circled items (ten annotated screenshots)

| Element | TradingView phone | App | Where |
|---|---|---|---|
| Live tag | 100 × 31 pt; price 14 pt white, countdown 12 pt white at ~70 % under it | second line 10.5 sp at 0.7 alpha (same ratio to a 12 sp axis) | `tagSecondLineStyle`, `drawAxisTag` |
| Toolbar wheel | current ticker 16 pt bold on the bar's centre, next one 18 pt under it in a faint ink, cut by the bar's edge; 80 pt wide before the interval | 18 dp rows in a clipped 44 dp cell, 80 dp wide, bold `titleMedium`, neighbours in `TextDisabled` | `SymbolScrollWheel` |
| Wheel picker (mid-drag) | 197 pt card, 16 pt corners, white ~92 % over the bars; rows 32 / 26 / 20 / 15 / 11 pt outward with logos of the same size, fading | `SymbolWheelOverlay`, `OVERLAY_RUNGS`, shown while `onDragging` | `SymbolWheel.kt`, `ChartScreen` |
| Watermark | the mark 12 pt from the pane's left and 12 pt above the time axis, `#0F0F0F`; the full wordmark in two of seven shots | `ProChartLockup` at a 56 dp name, `WATERMARK_INSET = 12.dp` | `ChartWatermark` |
| Trade ring | 20 pt ring, 1.5 pt stroke, `#8D32A9`, bolt inside, centred under the newest bar, 3 pt above the time axis | `drawTradeRing`, `TradingViewPalette.TRADE`, tap → trade sheet / terminal | `CoineProChart`, `ChartScreen.onTrade` |
| Hub — broker card | 72 pt plate across the sheet, 12 pt corners, 1.5 pt rose→violet→blue rim | `TradeCard`, `Modifier.spectrumRim` | `ChartChrome`, `CoineProSurfaces` |
| Hub — sections | TOOLS (7 tiles, 2 across), MORE (4 tiles), «Help Center» centred at the foot | TOOLS 7 tiles incl. bar replay; MORE events / studio / terminal; `HelpCenterRow` → help entry `chart` | `ChartMoreSheetBody` |
| Drawings — search | 40 pt grey field, 10 pt corners, no edge, 17 pt placeholder | `CoineProSheetSearch` 40 dp, `small` shape, `bodyLarge` | `CoineProSheet.kt` |
| Drawings — tabs | bold text, chosen one on a 40 pt grey pill, others muted with no edge | `RailTabs` | `ToolRail` |
| Drawings — mode tiles | 72 pt tiles 3 across, first row on plates, the rest outlined, the one in force inverted, «⋮» column on tiles with a menu | `ModeTileGrid` at the head of the unfiltered grid | `ToolRail` |

## Still owed

* A side-by-side render diff (app screenshot vs TradingView screenshot, same viewport) as a
  checked-in test artefact rather than a manual comparison.
* Crosshair label geometry (TradingView's is 24 px tall with 8 px side padding) — the app's tag
  uses the price tag's padding.

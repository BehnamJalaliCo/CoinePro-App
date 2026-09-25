# CHART — desktop chart workspace vs TradingView /chart/

Build audited: pro-chart.com/terminal (5.18.1, `124213f`). Reference: tradingview.com/chart, anonymous, BINANCE:BTCUSDT 4h,
same viewports. Screens: `/terminal/BTCUSDT/4h`, 1600x900 and 1366x768, dark and light, fa (default) and en
(`localStorage["app_language/language_tag"]="en"`; context locale alone does not switch it).
Shots: `scratchpad/audit/shots/CHART/` (`ours-*` / `tv-*`; `z-*` are zoomed crops).

How TV was measured: DOM computed styles (TV's chrome is HTML) + pixel sampling of the PNGs. TV's chart data websocket
returns 426 through Chromium+proxy; it was bridged through Node (`pw/cha_audit_srv.js`, `TVBRIDGE=1`), so the TV
shots are the real live chart.

**Note on TV's 2025 theme:** TV's dark is no longer `#131722`/`#D1D4DC`/`#2A2E39`. Measured now: panels `#0F0F0F`,
4 px gutters between panels `#2E2E2E`, text/icons `#DBDBDB`, hover `#2E2E2E`, active/pressed `#3D3D3D`, separators
`#4A4A4A`, flyout/popup `#1F1F1F`. Light: panels `#FFFFFF`, gutters/separators `#EBEBEB`.
TV geometry: top bar 38 px; left drawing bar 52 px wide, 38 px pitch, 34x34 hover/active plate r4, 28 px icon box
(ink 21–22 px, 1 px stroke); right widget bar 45 px wide, 44x44 buttons, 28 px icon box; bottom bar 38 px; time axis 28 px.

---

## Defects (most visible first)

### CHART-01 · P0 · Legend hover buttons render as stray punctuation («•», «¦», «−», «…»)
- Where: legend, every locale/theme/viewport, on hovering the legend plate (with any indicator: eye / settings / remove / more).
- What: the web build substitutes text glyphs the single typeface has: eye = `•`, settings = `¦` (broken bar), remove
  = `×`, more/expand = `…`, collapse = `−`. The result reads as garbage characters after each value
  («84,133.3 +19869.2 +30.92%  •  ¦» / «SMA 20 · 84,650.2  •  ¦  ×  …»). At rest the head's disclosure is a 14 px `…`
  floating 10 px right of the status dot, off the title's baseline.
- Shots: `z-ours-legend-hover-sma.png`, `z-ours-legend-hover.png` vs `z-tv-legend-hover.png`, `tv-legend-hover.png`.
- TV: on row hover, 4 SVG icons (eye, gear, ×, ⋯) in 24x24 hit boxes, 18 px glyph, `#DBDBDB`, 1 px stroke; the hovered
  title row gets a 1 px `#4A4A4A` r4 outline plate. Nothing is drawn at rest.
- Fix: `chart/ui/src/wasmJsMain/.../ChartUiPlatform.wasmJs.kt:399-415` (`ChartMarks.visible/hidden/settings/remove/
  expand/collapse`) must stop being text. In `ChartLegendOverlay.kt:1115-1134` (`LegendButton`) replace the `Text(glyph)`
  default with vector marks drawn in `Canvas` (eye, eye-off, gear, ×, ⋯ — core:chart has no drawables, so paths),
  18 dp glyph in the existing 24 dp footprint, `palette.text`, 1 dp stroke. Hide the head disclosure until plate hover
  (`LegendHead`, `ChartLegendOverlay.kt:1271-1278`).

### CHART-02 · P0 · English UI opens a Persian Indicators sheet (and the chip row is clipped)
- Where: toolbar «Indicators», en, 1600 dark (both themes).
- What: title «Indicators» is English but the count is «۱۱۴ indicators» (Persian digits in English prose), the search
  placeholder «جست‌وجوی اندیکاتور», every category chip (همه، برگزیده‌ها، اخیر، روند…) and every indicator name
  (میانگین متحرک ساده…) are Persian. The chip row runs out of the sheet: «حجم 17» is cut at the right edge. It is also a
  phone bottom sheet (drag handle, 28 dp top radius) floating mid-screen on a desktop.
- Shots: `ours-indicators-sheet.png` (no TV pair needed; TV's is an English centred 3-column dialog).
- Fix: the names/chips come from a Persian-only source in the indicator catalogue — route them through `values/` vs
  `values-fa/` like every other string; count digits via `proseDigits()` only when Persian. Chip row: the sheet's
  horizontal row needs its end padding (same defect class as the timeframe sheet being fixed). File the sheet body
  under the indicators area if another auditor owns it; the entry point is `ChartDesktopToolbar.kt:150-155`.

### CHART-03 · P1 · Persian layout is the mirror image of TradingView's RTL layout
- Where: fa, all viewports/themes.
- What: in Persian the drawing rail moves to the far right, jammed against the price axis, and the panel/widget rail moves
  to the left edge beside the symbol button — while the top toolbar and bottom bar are forced LTR. TradingView's Arabic
  chart does the exact opposite: drawing toolbar stays left, widget bar stays right, price axis stays right, legend stays
  LTR top-left; only the *top toolbar* and *bottom bar* contents mirror (symbol at the right, ranges right, clock left).
- Shots: `ours-fa-dark-1600.png` vs `tv-ar-rtl-1600.png` (ar.tradingview.com) and `tv-dark-1600.png`.
- Fix: `ChartWorkbench.kt:176-183` and `ChartSidePanels.kt:159-221` — wrap the workbench `Row` and the panel host `Row` in
  `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` (re-providing the reader's direction
  inside the flyout/panel content), so the rails never swap. Conversely drop the forced LTR on the toolbar rows
  (`ChartDesktopToolbar.kt:93`, `:336`) or keep it — but then the rails must stay LTR too; the current mix is the one
  arrangement no terminal uses.

### CHART-04 · P1 · Plot is 100–160 px shorter than TradingView's; three rows of footer under the bottom bar
- Where: all; worst at 1366x768.
- What: under the 32 px bottom bar we stack the signals strip (ChartNowStrip, 26 px), the Rasad card (≈36 px incl. margins),
  and the ChartUnderline footer («H4 / 300 bars 20:00» and «H 87361.5 · L 62499.2», ≈40 px + 60 px of empty stage below).
  Measured plot+time-axis height: 1600x900 ours 43→761 = 718 px (683 px with one indicator) vs TV 42→861 = 819 px;
  1366x768 ours 42→602 = 560 px (73 %) vs TV 42→700 = 658 px (86 %). The footer repeats the interval already in the
  toolbar and the H/L already on the axis.
- Shots: `ours-en-dark-1600-sma.png`, `ours-en-dark-1366.png` vs `tv-dark-1600.png`, `tv-dark-1366.png`.
- TV: nothing below its 38 px bottom bar; plot runs to y = 861 of 900.
- Fix: `ChartScreen.kt:2446-2451` — on `desktopChrome` do not draw `ChartUnderline` (move H/L/bars/source into the
  data window or a tooltip on the clock). `ChartScreen.kt:2411-2425` — on desktop render `ChartNowStrip` and `RasadLine`
  as one 28 px row (Setup chip, study pills, then the Rasad sentence truncating) or dock them into the Readings side panel.
  Target: plot bottom at `viewport − 38 (bottom bar) − 28 (time axis)`.

### CHART-05 · P1 · Event marks sit on top of the time-axis dates
- Where: time axis, every locale/theme (en: «21 Sep», fa: «۳۰ شهریور» both overprinted).
- What: 12 dp squares/bolts are centred at `axisTop + 3 + 6 = axisTop + 9` px — exactly the text band of the 12 sp labels
  (ink y 746–757 in a 735–761 axis). Dates vanish under yellow/teal squares; the crosshair time tag also lands on them.
- Shots: `z-ours-en-axis.png`, `ours-crosshair-1600.png` vs `tv-dark-1600.png` (TV's news bolt sits inside the plot, 8 px
  above the axis at x 1416, y 820).
- Fix: `CoineProChart.kt:3834-3845` (`drawEventMarks`): place `centreY = axisTop − EventGlyphs.AXIS_GAP_DP − side/2`
  (inside the plot's bottom edge, above the volume lid like TV), or grow the time axis by a 16 px lane *below* the labels.
  Constants `ChartEvents.kt:144-149` (`SIZE_DP 12`, `AXIS_GAP_DP 3`). Light theme: the Medium-importance fill is the
  `warning` brown `#8A5606` (`CoineProPalette.kt:340`) and reads as mud — use TV's `#FF9800`/`#F7C948`.

### CHART-06 · P1 · Drawing-tool flyout reflows the whole workspace instead of floating
- Where: left rail (en) / right rail (fa), any group.
- What: opening a group inserts a 216 dp column that pushes the toolbar, legend and plot 217 px sideways (the symbol button
  jumps from x 60 to x 277; candles re-lay). Rows are 38 px, 12 sp Medium, no shortcuts, no section headers; the «Lines»
  heading is 11 sp muted jammed 8 px under the top edge; flat full-height slab `#10141B`.
- Shots: `ours-flyout-lines.png` vs `tv-flyout-lines.png`.
- TV: overlay popup anchored to the button, 274 px wide, `#1F1F1F`, r6, drop shadow; rows 40 px; 28 px icon box; 14 px
  Regular `#DBDBDB`; shortcut right-aligned 12 px `#8C8C8C` («Alt + T»); uppercase 11 px `#8C8C8C` section headers with
  1 px `#4A4A4A` separators; chart does not move.
- Fix: `ChartToolRailColumn.kt:141-197` → `Popup(alignment = TopStart, offset = (railWidth, buttonTop))` with
  `Surface(color = SurfaceOverlay, shape = RoundedCornerShape(6.dp), shadowElevation = 8.dp)`, width 272 dp, rows
  `height(40.dp)`, `bodyMedium`(14/Normal), trailing shortcut in `TextMuted`, section headers from `DrawingTools` groups.
  (The "absent from a screenshot" reason in the comment is solved by `chartLayer.record` already scoping the share image
  to the canvas.) `FLYOUT_WIDTH` `:246`.

### CHART-07 · P1 · Legend never shows OHLC with a mouse; head is phone-sized and two lines
- Where: legend, desktop, all.
- What: at rest and while the mouse crosshair moves, the legend prints `close  +change  +%` only — the OHLC line appears
  only for a touch-hold (`tracking`). Title is 17 sp Bold with a back-arrow disc in front of it; values wrap to a second
  line at 14 sp; no interval or venue.
- Shots: `ours-crosshair-1600.png`, `z-ours-en-left.png` vs `tv-crosshair-1600.png`.
- TV (DOM): title row `Bitcoin / TetherUS · 4h · Binance` 16 px/400 `#DBDBDB`, then on the same line `O H L C Δ(Δ%)` 13 px,
  letters `#DBDBDB`, figures in bar colour (`#089981` / `#F23645`), updating under the mouse crosshair.
- Fix: `ChartLegendOverlay.kt:598` — use `rows.first()` (OHLC) whenever a crosshair exists, not only when `tracking`;
  on wide windows lay title + values on one row. `ChartPixels.kt:723` `LEGEND_FONT_SP` → 13 on desktop,
  `ChartLegendOverlay.kt:1179` `TITLE_SCALE` → 16/13 ≈ 1.23 with `FontWeight.Normal` (`:1247`), append `· 4h · venue`.
  Keep the back arrow off the legend on desktop (the app rail already has navigation) — `LegendHead` `:1224-1240`.

### CHART-08 · P1 · Icons ~25 % smaller and greyer than TradingView's, from three different icon families
- Where: top toolbar, drawing rail, side rail; all.
- What (ink bbox measured): toolbar alert 16x15 `#B7B9BA`, settings 16x14, camera 16x13 — TV 21x21, 22x18, 21x17, all
  `#DBDBDB`. Drawing rail trend-line 18x18 `#68707D` vs TV 22x22 `#BFBFBF`–`#DBDBDB`. The toolbar mixes Phosphor
  (`icon_*`, viewport 256), TradingView-style (`tv_*`, viewport 28) and one-offs: `tv_layout_grid` (viewport 21) renders as a
  heavy bright 18 px square (`#F0F1F2`) next to thin 16 px grey glyphs; in the side rail `tv_help_circle` (viewport 44)
  renders at 14 px `#495058` and `tv_chart_columns` at 15x12 `#616974` — visibly smaller and dimmer than `icon_star`
  (20x19) and `icon_info` (18x18) above/below them.
- Shots: `z-ours-top.png` vs `z-tv-top.png`; `ours-en-dark-1600.png` right rail vs `tv-dark-1600.png`.
- Fix: one family (the `tv_*` 28-viewport set) everywhere, box 28 dp, tint `TextPrimary`-at-86 % (`#DBDBDB`-equivalent),
  1 dp stroke. `ChartDesktopToolbar.kt:308` `DESKTOP_GLYPH 20→28.dp`; `ChartToolRailColumn.kt:236,243` tint `TextMuted→
  TextSecondary`, `RAIL_GLYPH 22→28.dp`; `ChartSidePanels.kt:255,263` same. Swap `icon_plus, icon_caret_down,
  icon_sliders_horizontal, icon_rewind, icon_arrow_*` (toolbar `:120-176`) and `icon_info, icon_list_bullets, icon_sparkle,
  icon_star, icon_bell` (side rail; `ChartWorkbench.kt:135`, `ChartScreen.kt:2091,2142`, `CoineProApp.kt:3385,3432`) for
  28-viewport equivalents; re-cut `tv_help_circle` (44→28) and `tv_layout_grid`/`tv_code2` (21/18→28 with 5 px margin).

### CHART-09 · P1 · Rail hover/active states are 48 px Material circles, not 34 px plates
- Where: drawing rail and side rail (M3 `IconButton` state layer); cursor tool shows a grey 48 px disc at rest.
- What: hover = 48 px circle `#262B31`; two adjacent circles touch and form a pill-shaped blob; the selected pointer tool
  carries a permanent 48 px disc while its icon stays grey. Toolbar hover is a 32 px r8 plate `#1D2023` (different shape
  again).
- Shots: `z-hover-rails.png`, `z-hover-top.png` (`ours-hover-*.png` vs `tv-hover-*.png`).
- TV: every bar uses the same plate — 34x34, r4, hover `#2E2E2E`, active `#3D3D3D` with icon `#DBDBDB`; top-bar plate is
  34 px tall inside the 38 px bar (2 px inset).
- Fix: replace `IconButton` in `ChartToolRailColumn.kt:227-239` and `ChartSidePanels.kt:248-258` with a `Box(size 34.dp)
  .clip(RoundedCornerShape(4.dp)).hoverable/clickable(indication = plate)` using `SurfaceHover`/`SurfacePressed`
  (`CoineProPalette.kt:161-162`) centred in a 38 dp (tool) / 44 dp (side) cell. Toolbar: `ChartDesktopToolbar.kt:245,264`
  `CoineProShapes.small (8)` → `extraSmall (4)`, `DESKTOP_CONTROL 32→34.dp`.

### CHART-10 · P1 · Crosshair time tag drops the date and collides with the axis
- Where: time axis under the crosshair, all.
- What: the tag reads «07:30» only (no day) — on a 4h chart that is ambiguous; it is drawn over «21 Sep» and the event marks.
  Tags are `#787878` with a 4 dp drop shadow (price tag 24 px tall).
- Shots: `ours-crosshair-1600.png` vs `tv-crosshair-1600.png`.
- TV: time tag «Mon 07 Sep '26 16:00», 24 px tall, 8 px side padding; price tag 21 px; both `#3D3D3D`, white text, no shadow;
  the tag replaces (hides) the axis labels it overlaps.
- Fix: `CoineProChart.kt:6318-6330` use a date+time pattern (`EEE dd MMM ''yy HH:mm`; Jalali in fa) and suppress axis
  labels intersecting the tag; `:7192` `CROSSHAIR_SHADOW_DP 4→0`; crosshair fill in the palette `crosshair` → `#3D3D3D`
  dark / `#6A6D78` light.

### CHART-11 · P1 · No tooltips on icon-only controls
- Where: top toolbar (templates, undo, redo, layout, settings, fullscreen, camera, more, compare, chart type), both rails.
- What: hovering 2 s shows nothing (`ours-hover-settings.png`). Eleven of the toolbar's controls and every rail glyph have
  no visible name on desktop.
- TV: tooltip after ~300 ms, 12–13 px, `#3D3D3D` plate r4, 6x8 padding, placed below the button (`tv-hover-settings.png`,
  «Settings»; `tv-dark-1366.png` «Help Center»).
- Fix: wrap `GlyphButton` (`ChartDesktopToolbar.kt:255-279`), `RailGlyph` (`ChartToolRailColumn.kt:221`) and the side-rail
  button (`ChartSidePanels.kt:248`) in `TooltipBox(PlainTooltip)` using the existing `label`/`description` strings.

### CHART-12 · P1 · Price-axis hover tag stays after the pointer leaves the plot
- Where: price axis, en 1600 dark.
- What: after crossing the price axis on the way to the side rail, the grey «87,790.2 ⏰ ⚙» axis tag remains painted while
  the pointer is on the rail. TV clears crosshair and axis tag on `mouseleave`.
- Shot: `ours-hover-siderail-help.png` (bottom panel of `z-tooltips.png`).
- Fix: in `CoineProChart.kt` pointer handling, on `PointerEventType.Exit` of the canvas set `crosshair = null` and
  `invalidate(Invalidation.CURSOR)` (as the tap path does at `:2657-2660`), and clear the axis-alert affordance state.

### CHART-13 · P1 · Last-price tag wears a radial glow
- Where: price axis, all themes (red or green haze ≈ 150 px across, visible over the rail).
- What: `LAST_PRICE_GLOW_SPREAD 1.1 × gutter`, 16 % alpha radial gradient behind the tag — reads as a smudge on the axis and
  bleeds past the plot into the side rail in fa. No terminal draws this; it is the only glow in the product.
- Shots: `z-ours-tag.png`, `ours-fa-dark-1366.png` vs `tv-dark-1600.png` (flat tag).
- TV: flat tag, r2, fill = bar colour, 12 px white, countdown line 11 px at 70 %; nothing behind it.
- Fix: delete the glow block `CoineProChart.kt:5377-5400` and constants `:5423-5426`.

### CHART-14 · P2 · Toolbar type is smaller and heavier than TV's
- Where: top toolbar, both locales.
- What: labelled buttons 13 sp Medium, intervals 12 sp Medium, cap height 9–10 px, ink `#F0F1F2`; symbol 14 sp Bold.
- TV: 14 px Regular `#DBDBDB` for labels and intervals (cap 11 px); symbol 14 px 600 in a 28 px `#3D3D3D` pill r14.
- Fix: `ChartDesktopToolbar.kt:136,293` → `bodyMedium` 14 sp `FontWeight.Normal`, colour `TextSecondary` (≈`#B7BDC6`)
  rising to `TextPrimary` on hover; active interval keep accent but `FontWeight.Medium` not Bold (`:137`).

### CHART-15 · P2 · Toolbar/bottom-bar separators are nearly invisible; top has a double hairline
- What: separators are 1x20 px `#242729` on `#0B0E11` (Border = 10 % white, ≈1.2:1). Above the toolbar a 1 px `#37393C`
  (BorderStrong) rule at y 0; below it two stacked hairlines (y 41 `#1E2124` from the toolbar's own divider + y 42
  `#242729` from the page's pre-canvas divider). Same double rule above the bottom bar.
- TV: separators 1x22 px `#4A4A4A` dark / `#EBEBEB` light, 8 px from top/bottom; one boundary under the bar.
- Fix: `ChartDesktopToolbar.kt:298-303` `height(22.dp)`, colour `BorderStrong`; delete `ChartScreen.kt:2310` (top rule)
  and either `ChartDesktopToolbar.kt:206` or `ChartScreen.kt:2361` (keep one); same for `:2374`/`:2398`.

### CHART-16 · P2 · Bottom bar: 32 px, muted 12 sp, no "Go to date", no auto
- What: 32 px tall (y 762–794); ranges 12 sp Medium `#848E9C` (cap 9 px); clock `#848E9C`.
- TV: 38 px; 30 px-wide range buttons, 14 px Regular `#DBDBDB` (cap 10 px); calendar «Go to» icon button after «All» with a
  separator; clock `#DBDBDB`, opens timezone menu.
- Fix: `ChartDesktopToolbar.kt:405` `DESKTOP_BOTTOM_HEIGHT 32→38.dp`; `:355-359`, `:366-367`, `:383-385` colour
  `TextSecondary`/`TextPrimary`, 13–14 sp Normal; add a `GlyphButton(tv_calendar…)` → existing go-to-date sheet
  (`ChartKeyAction.GO_TO_DATE` path) and an `auto` toggle next to `%`/`log`.

### CHART-17 · P2 · Side (widget) rail: 48 px pitch, no grouping, muted
- What: 56 px wide, 8 glyphs at 48 px pitch in one run from the top, `TextMuted #848E9C`, no separators.
- TV: 45 px wide, 44 px pitch, two groups (watchlist/alerts/object tree at top; screeners…help at the bottom), `#DBDBDB`.
- Fix: `ChartSidePanels.kt:107` `CHART_SIDE_RAIL_WIDTH 56→48.dp`; `:237-259` 44 dp cells, a `Spacer(weight 1f)` between
  «panels» and «app» groups, 1 px `BorderStrong` 28 dp separators.

### CHART-18 · P2 · Drawing rail lacks TV's rail-level switches and group separators
- What: groups then a single «all tools» square; magnet, stay-in-drawing, lock, hide and remove-all live only in the sheet.
  No separators between tool families.
- TV: after 8 families a separator, measure/zoom, separator, magnet, stay-drawing, lock, hide, separator, trash; separators
  36x1 px `#4A4A4A`, 13 px gap.
- Fix: `ChartToolRailColumn.kt:112-138` — add rail cells for `controller.cycleMagnet`, `setKeepDrawing`,
  `setLockAllDrawings`, `setAllLayersHidden`, `clearDrawings` (already passed to `ToolRail` in `ChartWorkbench.kt:279-290`),
  plus `HorizontalDivider(36.dp, BorderStrong)` between families.

### CHART-19 · P2 · Legend/footer figures lose thousands separators
- What: legend change «+19869.2» next to price «84,133.3»; footer «H 87361.5 · L 62499.2». TV groups every figure
  («+543.83», «84,043.90»).
- Fix: `ChartLegendOverlay.kt:603` wrap `legendChangeRow` alternatives in `groupThousands`; `ChartScreen.kt:5388-5389`
  `groupThousands(formatPrice(...))`.

### CHART-20 · P2 · Light theme: plot sits on off-white, rails on white (inverted hierarchy)
- What: plot/toolbar `#F7F8FA`, drawing rail `#FFFFFF` — the rail looks raised over the chart. Separators `#E5E6E8`.
- TV: every panel `#FFFFFF`, panels split by 4 px `#EBEBEB` gutters; separators `#EBEBEB`.
- Shots: `ours-en-light-1600.png`, `ours-fa-light-1600.png` vs `tv-light-1600.png`.
- Fix: `CoineProPalette.kt:270-274` light `terminal` → `#FFFFFF` (chart ground), or rails `Surface→Stage` in
  `ChartToolRailColumn.kt:95,156`.

### CHART-21 · P2 · Watermark is a half-hidden smudge behind the volume bars
- What: the 6 % «Pro Chart» signature sits 12 dp above the axis, inside the volume histogram; only «ro C» shows between bars.
- TV: logo+wordmark at bottom-left, drawn above the bars, ≈40 % ink, 12 px from axis.
- Shot: `z-ours-watermark.png` vs `tv-dark-1600.png`.
- Fix: `ChartScreen.kt:4169,4203-4206` — draw above the histogram with ≥30 % alpha, or lift it above the volume lid.

### CHART-22 · P2 · Volume overlay has a full-width lid line through the candles
- What: a 1 px rule at the top of the 18 % volume band (y 609 at 1600) crosses the whole plot and candles.
- TV: no rule for the volume overlay.
- Fix: `CoineProChart.kt:7349` `VOLUME_LID_ALPHA 0.5f → 0f` (or don't draw the lid for the inline band).

### CHART-23 · P2 · Interval spellings are MetaTrader's, not TradingView's
- What: «M1 M5 M15 H1 H4 D1». TV: «1m 5m 15m 1h 4h D» (intervals button reads «4h»).
- Fix: `ChartDesktopToolbar.kt:134-139` display label from a `tvLabel(interval)` map; keep the wire value internally.

---

## Already better than TradingView (keep)
- Favourite intervals inline on the toolbar (TV shows one «4h» button + a menu): one click to switch.
- Bottom-bar clock shows the zone offset («UTC+3:30») and the axis dates are Jalali in fa — TV has no Jalali.
- Last-price tag with countdown matches TV's arrangement exactly (price over countdown, one fill); candle colours
  `#089981`/`#F6465D` and 50 % volume match TV.
- Legend market-state dot and the Setup/Rasad reading strip have no TV equivalent and are genuinely useful (keep, but see
  CHART-04 for their height).
- Price labels round to the needed precision (`88,000`) instead of TV's always-two-decimals (`88,000.00`) — calmer axis.

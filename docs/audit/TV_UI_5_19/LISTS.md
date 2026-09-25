# LISTS audit: Pro Chart web terminal vs TradingView

Build audited: `https://pro-chart.com/terminal/`, BUILD.txt `version 5.18.1`, commit `124213fd`, built 2026-09-25T18:34Z.
Viewports: 1600x900 and 412x900. Themes: dark and light. Locales: fa (default, RTL) and en (switched in Menu → Appearance and language → English).
Screenshots are relative to `scratchpad/audit/`. Every `ours-*` shot was taken on the live site. Every `tv-*` shot was taken on tradingview.com.

**Caveat about TradingView.** TV's right-hand watchlist needs `data.tradingview.com` websockets, and those never connected through the proxy (the TV chart spun for 3+ minutes, even with the ws bridge). So the TV watchlist chrome (panel width, header row) is measured from `tv-watchlist-panel-light-1600.png`, and TV row metrics come from the TV Screener, which loaded in both themes. The TV watchlist row height (28–32 px) is the figure given in the brief.

## TradingView reference values (measured on the PNGs)

| Item | TV dark | TV light |
|---|---|---|
| Screener row pitch | 41 px (40 + 1 px divider `#2E2E2E`) | 41 px (divider `#F2F2F2`) |
| Screener header row | 48 px; 1 px top and bottom rules `#4A4A4A`; label ink `#8C8C8C`; ~13 px | 48 px; rules `#EBEBEB`; ink `#707070` |
| Row hover | `#2E2E2E` on `#000000` (full-row) | `#F2F2F2` on `#FFFFFF` |
| Body text | `#DBDBDB`, 14 px | `#0F0F0F`, 14 px |
| Up / down **text** | `#22AB94` / `#F7525F` | `#089981` / `#F23645` (AA-sampled `#06806B` / `#CC2F3C`) |
| Filter pill | 34 px tall, 1 px border `#4A4A4A`, radius ~8, 16 px label | 34 px, border `#EBEBEB`, label `#707070` |
| Sort marker | a ↓/↑ arrow glyph *before* the label, same ink as the label (`↓ Mkt cap`, `↑ Rank`) | same |
| Ticker | 13 px bold inside a 4 px-radius chip `#2E2E2E` | chip `#F2F2F2` |
| Watchlist panel | 300 px wide + 4 px splitter `#EBEBEB`; header "Symbol / Last / Chg / Chg%" 13 px `#6A6D78`-ish, 1 px rule under it; one title row ("Watchlist ▾" + three bare 28 px glyphs, no plates) | |

Our values: stage `#0B0E11`, divider `#1E2124`, hover `#1D2023`, ticker/figures 12 sp Medium, headings 11 sp `#6B7482`, up `#089981`, down `#F6465D` (light: `#D01427` / `#1F8A78`), and a row pitch of **59 px** in every list.

---

## Defects (ranked, most visible first)

### LISTS-01 · P0 · Clicking any watchlist column heading crashes the web app, and the UI stays frozen
- **Where:** the home watchlist and the watchlist side panel on the chart. 1600 and 412. Both themes, both locales.
- **What:** a click on «آخرین» / «تغییر ٪» / «روند» (or Last / Change % / Trend) logs `kotlin.ClassCastException: Cannot cast null to kotlin.String: target type is non-nullable`. From then on nothing responds: hover dies, the nav rail does nothing, the heading's pressed plate stays drawn, and prices stop moving. Only a page reload recovers. Reproduced 3 times.
  Shots: `shots/LISTS/ours-home-sort-click-crash.png` (click), `ours-P0-frozen-after-sort.png` (a later nav-rail click on «فیلتر بازار» is ignored, and the grey pressed plate stays on «تغییر ٪»), and `ours-chart-watchlist-sorted*.png` (the side panel never sorts).
- **TV:** a click sorts the column and shows ↓ before the label.
- **Root cause:** the web DataStore shim, `web/src/shims/kotlin/androidx/datastore/preferences/core/Preferences.kt:56`:
  `fun <T> remove(key: Key<T>): T = map.remove(key) as T`.
  `WatchlistStore.writeLists()` (`core/datastore/.../WatchlistStore.kt:1125`) always calls `preferences.remove(LEGACY_SYMBOLS)`. That key is absent, so `null as String` throws on Kotlin/Wasm (on the JVM the cast is unchecked). The same path runs for every write that goes through `writeLists`/`writeTouched`: setSort, setColumns, toggle star, add, and the screener's «افزودن هر ۸۶۲ نماد به دیده‌بان». So **every watchlist mutation on the web kills the app**. The same pattern also appears in `ChartLayoutStore`, `DuelStore`, `RecentSearchStore`, `TeachingStore` and `SymbolChartStateStore`.
- **Fix:** in the shim, change line 56 to `fun <T> remove(key: Key<T>): T? = map.remove(key) as T?`. Callers ignore the return value, so they still compile. Add a wasm/JVM test that calls `edit { it.remove(absentKey) }`.

### LISTS-02 · P1 · The desktop screener is a 360 px strip beside ~960 px of «یک مورد را از فهرست انتخاب کنید…»
- **Where:** `/terminal/screener`, 1600, all themes and locales.
- **What:** the table is squeezed into the list pane of a list-detail split. The detail pane is an empty placeholder until a row is clicked. Only 2 of the 3 default columns are visible (see 03). TV fills the whole width with 12+ columns (Price, Chg %, Vol, Rel vol, Mkt cap, P/E, …, Rating).
  Pair: `ours-screener-fa-dark-1600.png`, `ours-screener-en-light-1600.png` vs `tv-screener-dark-1600.png`, `tv-screener-light-1600.png`.
- **Source:** `core/designsystem/.../CoineProListDetail.kt:55` sets `LIST_WIDTH = 360.dp`. The screener is wrapped in `CoineProListDetail` at `app/.../CoineProApp.kt:4951`.
- **Fix:** give the screener no detail pane on expanded width. Let `ScreenerScreen` take the full width. Once width > 900 dp, show every `ScreenerField` column plus the indicator columns without a horizontal strip, using fixed 96 dp figure columns and a weighted name column. Alternatively, if the split stays: `LIST_WIDTH = max(640.dp, window * 0.45f)` for this route, and auto-open the top row in the detail pane instead of the placeholder.

### LISTS-03 · P1 · Screener third column (Volume) is cut off: a bare «–» / «K» with no heading
- **Where:** screener, 1600 and 412, fa. In en the column is not visible at all.
- **What:** in fa, the far-left column shows «–» on every row, and «…K» is clipped mid-number on BTC (`ours-screener-sort-price.png`, x≈1015). Its heading is scrolled out of view. The figure strip is 3×88 + 2×8 = 280 dp wide inside a ~200 dp viewport, and there is no fade or chevron to show that it scrolls.
- **Source:** `feature/screener/.../ScreenerScreen.kt:845–848` (`SYMBOL_COLUMN = 96.dp`, `FIGURE_COLUMN = 88.dp`); `model/ScreenerField.kt:168` (`DEFAULT_COLUMNS = LAST_PRICE, CHANGE_PERCENT, VOLUME`).
- **Fix:** (a) fix LISTS-02 so the columns fit. (b) On narrow widths, drop VOLUME from the default columns, or narrow the figure columns to 72 dp. (c) When `valuesScroll.maxValue > 0`, draw a 16 dp edge fade on the strip's trailing side.

### LISTS-04 · P1 · Screener column headings sit at the wrong edge of their column in Persian
- **Where:** screener, fa, all widths. The en layout is correct.
- **What:** in `ours-screener-hdr-rtl-zoom.png`, «آخرین قیمت» spans x 1125–1185 while its figures end at x 1213 (28 px off). «تغییر روزانه» spans 1030–1097 while its figures end at 1117. The labels hug the *left* of each 88 dp cell while the numbers are right-aligned.
- **Source:** `ScreenerScreen.kt:506`, `horizontalArrangement = Arrangement.End` in `Heading()`. In RTL, End is the left. The figures use `TextAlign.Right` (line 647).
- **Fix:** replace it with `Arrangement.Absolute.Right`, the same absolute alignment the cells use. The watchlist heading already does this correctly with `TextAlign.Right` (`MarketListRow.kt:437`).

### LISTS-05 · P1 · Rows are 59 px everywhere; desktop density is about half of TV's
- **Where:** home watchlist, chart side panel, screener and markets list, at 1600.
- **What:** the row pitch is 58 + 1 px in every list. The chart panel shows 12 rows in 740 px. TV's watchlist shows ~25 rows at 28–32 px, and the TV screener 41 px rows with a single text line.
  Pair: `ours-chart-watchlist-fa-dark-1600.png` vs `tv-screener-dark-1600.png` (and `tv-watchlist-panel-light-1600.png` for the panel chrome).
- **Source:** `feature/search/.../MarketListRow.kt:554` (`MarketRowHeight = 58.dp`), row padding at line 190 (`vertical = 8.dp`), `LogoSize = 28.dp` at 526, and the two-line ticker/name column at 256–273. `ScreenerScreen.kt:550` (`minHeight = 58.dp`) and 557.
- **Fix:** add a desktop density, `coineProWindowClass().isExpanded`, with a row height of 36 dp. That means a single line (ticker 13 sp Medium + name 12 sp muted on the same baseline, ellipsised), `LogoSize` 20 dp, vertical padding 6 dp, and the sparkline 48×18. Keep 58 dp on phones, where the two-line row is right. `ReorderHandle` divides by `MarketRowHeight`, so make that value density-aware instead of a constant.

### LISTS-06 · P1 · The chart's watchlist panel says «دیده‌بان» three times and spends 140 px before the first row
- **Where:** chart side panel, 1600, fa and en.
- **What:** `ours-chart-watchlist-head-zoom.png` shows «دیده‌بان» 3× (15 sp host title, then a 20 sp page title, then «دیده‌بان ▾» as the list picker). There is also a row of four 34 px bordered buttons, and the column headings start at y = 128. TV has one 44 px title row ("Watchlist ▾" + bare glyphs) and then the column header, so the first row starts at y ≈ 118 including the chart top bar.
- **Source:** `feature/chart/.../ChartSidePanels.kt:199–208` (host title Text); `feature/search/.../WatchlistScreen.kt:116, 177–222` (`WatchlistHeader` titleLarge); `WatchlistPanel.kt:533–541` (picker name).
- **Fix:** add a `showTitle: Boolean = true` parameter to `WatchlistScreen` and pass `false` from the side panel (`CoineProApp.kt:3386`). In the side panel, drop the host title for panels that bring their own. That leaves the picker as the title: a 14 sp Bold name with a caret.

### LISTS-07 · P1 · Watchlist toolbar floats in the middle of the page on desktop
- **Where:** home watchlist, 1600, fa and en.
- **What:** «+ ▢ ≡ •••» sits at x 503–663 in fa (`ours-watchlist-home-fa-dark-1600.png`) and x 960–1120 in en (`ours-watchlist-home-en-light-1600.png`). It is aligned to neither the list's end edge (16 / 1584) nor the column block.
- **Source:** `WatchlistPanel.kt:521–557`. The picker Row has `.weight(1f, fill = false)` (line 523), and the `Spacer(Modifier.weight(1f))` at line 557 splits the leftover width in half with it.
- **Fix:** remove `.weight(1f, fill = false)` from the picker Row and cap its width instead (`widthIn(max = 220.dp)`), so that the Spacer takes all the slack and the toolbar lands on the end edge.

### LISTS-08 · P1 · Forex, metals and indices rows never get a price: «–», «–» and a grey flat line
- **Where:** home watchlist (7 of 14 default rows), chart panel, and markets «Top» (9 of 11 visible rows). All widths and themes.
- **What:** XAUUSD, XAGUSD, EURUSD, GBPUSD, USDJPY and others show only dashes (`ours-watchlist-home-fa-dark-1600.png`, `ours-markets-en-light-1600.png`). The app log shows `GET /api/v1/symbols platform=COINEPRO_FX status=404` and `/api/mobile/v1/symbols platform=TRADEYAR status=404`. The markets header tiles (Market cap / 24h volume / BTC dominance / Fear & greed) are also all «–». TV always quotes these.
- **Source:** data availability, not layout. But the list filter in `WatchlistPanel.kt:173–176` and `MarketsScreen` lets unquoted rows lead the default «Top» ranking.
- **Fix:** web-side, point the FX quote source at a host that answers (or at the relay). Until then, sort rows with `quote == null` after quoted rows in «Top», and hide the four headline tiles when all of them are null instead of printing four dashes.

### LISTS-09 · P1 · Screener chrome never scrolls away: about 400 px of 836 px on the phone, about 390 px on desktop
- **Where:** screener, 412 (`ours-p-screener-fa-dark-412.png` → `ours-p-screener-scrolled.png`: after a 1200 px wheel scroll, the header is still pinned) and 1600.
- **What:** the title, CSV/Filters, mode chips, timeframe chips, category chips, count, «افزودن هر ۸۶۲ نماد…» button and column headings are all outside the `LazyColumn`. On a phone that leaves 7 rows visible. TV's screener header scrolls with the page, and only the 48 px column header sticks.
- **Source:** `ScreenerScreen.kt:169–226` (everything above `ColumnHeadings` is outside the LazyColumn at 260).
- **Fix:** move `Header`, `ModeChips`, `TimeframeChips`, `ScanControls`, `CategoryChips`, `ResultCount` and the add button into `item {}` blocks. Make `ColumnHeadings` a `stickyHeader {}` with `background(CoineProColors.Stage)` and a 1 dp `Border` bottom rule.

### LISTS-10 · P1 · Screener progress and result lines contradict the table
- **Where:** screener, all.
- **What:** «۰ بازار از ۸۶۲ بررسی شد» / "0 of 862 markets read" is shown while 862 rows are already listed with prices. In Growth signals: "0 markets · 0 of 862 markets read · 862 markets have no figure…". The console shows dozens of **HTTP 429 Too Many Requests** while the screener scans (`ours-screener-growth-en-light-1600.png`), so every setup scan comes back empty on the web. When the progress line disappears, the table jumps up 15 px.
- **Source:** `ScreenerScreen.kt:397–431` (`ResultCount`, the `state.resolving` line). The request fan-out is in `ScreenerController` (visible-window klines).
- **Fix:** throttle the scan to ≤ 5 concurrent requests with backoff on 429, and count a 429 as "not yet read" rather than as "no figure". Reserve the progress line's height (fixed 15 dp slot) so the table does not jump. Word it as «در حال بررسی ۱۲۰ از ۸۶۲» while it is moving.

### LISTS-11 · P1 · In English, the Filters sheet shows indicator names in Persian
- **Where:** Screener → Filters, en, 1600 (`ours-screener-filtersheet-en-light.png`).
- **What:** the Indicator chips read «میانگین متحرک ساده», «میانگین متحرک نمایی», … in an English UI. The indicator search only matches Persian names.
- **Source:** `feature/screener/.../ScreenerIndicatorCatalog.kt:204` (`ChartCatalog.INDICATORS…label`, which ignores `english`), line 212 (`Option.label = option.label`), and line 165 (`matching` searches Persian names).
- **Fix:** carry an English label on `IndicatorOption` (or look it up from the same source the chart's indicator dialog uses in English), use it in `labelOf(id, english)` and in `Option`, and match `query` against both.

### LISTS-12 · P1 · Price overlaps the ticker in screener rows (no gap between the symbol column and the figures)
- **Where:** screener, all widths and locales. `ours-screener-hdr-rtl-zoom.png`: «0.003980» touches «MEMESTOCKU…» with 0 px between them.
- **Source:** `ScreenerScreen.kt:569–598`. `Column(width(SYMBOL_COLUMN))` is followed directly by the figure `Row` with no spacer. The watchlist row puts `RowGap = 8.dp` there.
- **Fix:** add `Spacer(Modifier.width(CoineProSpacing.One))` after the symbol column in both `ScreenerTableRow` and `ColumnHeadings` (the heading's symbol width at line 462 must add the same 8 dp).

### LISTS-13 · P1 · Markets list: the «24h trend» heading is 30 px off its column
- **Where:** markets list, 1600, en light (`ours-markets-en-light-1600.png`). The heading spans x 398–444 while the sparklines span 428–485.
- **Source:** `feature/search/.../MarketsScreen.kt:784–812`. `ColumnHeadings` reserves the star and the logo but not the **rank** column that `MarketListRow` draws (`RankColumn = 24.dp` + `RowGap`, `MarketListRow.kt:235–244, 565`).
- **Fix:** pass `ranked: Boolean` into `ColumnHeadings` and add `Spacer(Modifier.width(RankColumn))` before the logo spacer (make `RankColumn` internal).

### LISTS-14 · P2 · Lettered discs overflow their circle («MAR», «SHR», «BON», «MOO», «US3», «UK1»)
- **Where:** screener and markets lists, all (`ours-monogram-zoom.png`).
- **What:** three letters at 11 sp in a 26 dp disc run into or past the 1 dp ring. «BTCDOM» shows «BTC», which reads as Bitcoin. TV uses 1–2 letters (BH, JP) in an 18–20 px disc.
- **Source:** `core/designsystem/.../CoineProSurfaces.kt:490–503` (labelSmall for ≥ 3 chars) and `CoineProAssetLogo.kt:120–124` (`MONOGRAM_LETTERS = 3`).
- **Fix:** scale the label with the disc, `fontSize = (size.value * 0.34f).sp` with `letterSpacing = (-0.02).em`, so a 26 dp disc gets 8.8 sp. Or use 2 letters when `size < 32.dp`. Special-case the `*DOM` bases to "DOM".

### LISTS-15 · P2 · Sort marker is a 7 px trend squiggle in gold, not an arrow
- **Where:** screener headings, all (`ours-screener-sort-price.png`, and «~ تغییر روزانه» in `ours-screener-fa-dark-1600.png`).
- **What:** the icon is `CoineProIcons.TrendDown/TrendUp` at 11 dp with 4 dp internal padding, so it renders at about 7 px, and the whole heading turns accent gold (`#997737` light / `#D8A848` dark). TV uses a plain ↓/↑ before the label in the label's own (slightly stronger) ink.
- **Source:** `ScreenerScreen.kt:509–522`.
- **Fix:** use the same " ↓" / " ↑" text marker the watchlist uses (`MarketListRow.kt:430–434`, the glyphs verified in IRANYekanX). Set colour `TextPrimary` when sorted and `TextMuted` otherwise, and keep gold for actions. One sort vocabulary across the three lists.

### LISTS-16 · P2 · Headings are too small and too faint, have no header rule, and are not sticky-styled
- **Where:** all lists. Headings are 11 sp Regular `#6B7482` (dark) / `#8F96A2` (light) with 0.3 sp tracking, sitting on a ~20 px row with no rule. TV uses 13 px `#8C8C8C` on a 48 px row with 1 px `#4A4A4A` rules above and below.
- **Source:** `MarketListRow.kt:429–442` (`labelSmall`, `TextDisabled`); `ScreenerScreen.kt:499–527`; `WatchlistPanel.kt:664–695`.
- **Fix:** use `labelMedium` (12 sp) with `TextMuted` (`#848E9C`), a heading row height of 32 dp on desktop, and a `HorizontalDivider(color = Border)` under it. Also make the heading's hit area the full cell height (`.heightIn(min = 32.dp)`). Today it is only the 15 px text line.

### LISTS-17 · P2 · Dark hover is almost invisible
- **Where:** all lists, dark. Hover is `#1D2023` on `#0B0E11` (ΔL* ≈ 5) (`ours-chart-watchlist-hover.png`). TV dark is `#2E2E2E` on `#000` (ΔL* ≈ 19). Light is fine (`#E5E6E8` on `#F7F8FA`, TV `#F2F2F2` on white).
- **Source:** Material's default 8 % state layer from `combinedClickable` at `MarketListRow.kt:178` and `clickable` at `ScreenerScreen.kt:553`.
- **Fix:** add a row `Indication` that paints `CoineProColors.SurfaceHover` (`#252A31` dark / `#E9EDF2` light) on hover and `SurfacePressed` on press, and use it in both rows.

### LISTS-18 · P2 · Figures and tickers are 12 sp on desktop; TV uses 14 px
- **Where:** all lists at 1600.
- **What:** tickers and figures are 12 sp Medium and the names 11 sp. At 1600 px they read as mobile type in a desktop terminal.
- **Source:** `CoineProType.kt:164–165` (`labelMedium` 12/16, `labelSmall` 11/15). Used by `MarketListRow.kt:259, 388`, `ScreenerScreen.kt:574, 641`, and `CoineProPercentText`.
- **Fix:** add expanded-width list styles: ticker 13 sp Medium, figure 13 sp `numeric()`, name 12 sp. Keep 12 sp on phones.

### LISTS-19 · P2 · Two chip systems on one screen, both undersized next to 44 px pill buttons
- **Where:** screener, 1600 (`ours-screener-en-light-1600.png`, `ours-screener-growth-en-light-1600.png`).
- **What:** the mode, timeframe and category chips are 23 px tall with 11 sp text (`#F0F3FA` fill, `#E2E5EC` ring). The Growth "Setups" chips are Material `FilterChip`s (32 px, 8 dp radius, outlined). CSV and Filters are 44 px pills. TV's filter pills are one style: 34 px, 1 px border, radius 8, 14–16 px label.
- **Source:** `ScreenerScreen.kt:659–683, 710–723` (`FilterChip`); `CoineProChipRow(compact = true)`.
- **Fix:** use one chip at 32 dp with a 13 sp label for all rows on expanded width (drop `compact` there), replace the `FilterChip` with `CoineProChip`, and bring CSV and Filters down to 32 dp so the header row reads as one control band.

### LISTS-20 · P2 · Filters sheet: the value chip row is clipped at the sheet edge, and the last section is empty
- **Where:** Screener → Filters, en, 1600 (`ours-screener-filtersheet-en-light.png`).
- **What:** "Day rang|" is cut at the dialog's inner edge with no fade. The indicator chip row is cut the same way. A "Condition" heading at the bottom has nothing under it until an indicator is picked.
- **Source:** `ScreenerFilterSheet.kt:215–230, 340–350`.
- **Fix:** use `contentPadding = PaddingValues(horizontal = 16.dp)` on the chip rows plus a 24 dp trailing edge fade, or wrap them in a `FlowRow` (the sheet has the height). Hide the second "Condition" heading until an indicator is selected. This is the same pattern as the timeframe sheet fix that is already under way, so apply it to this sheet too.

### LISTS-21 · P2 · Persistent sync footer eats ~70 px under every watchlist
- **Where:** home watchlist, 1600 and 412, and the chart panel. «هنوز همگام نشده است.» + «همگام‌سازی» appear permanently (`ours-p-watchlist-fa-dark-412.png`, `ours-p-bottombar-zoom.png`). TV has none.
- **Source:** `WatchlistPanel.kt:374–404`.
- **Fix:** move sync into the «•••» sheet, and show the footer only on error or when the last sync is more than 24 h old. That gives one more row on a phone.

### LISTS-22 · P2 · Loading sparklines look identical to "no data"
- **Where:** watchlist, light (`ours-watchlist-home-light-1600.png`). All 7 crypto rows show a grey flat line while the lines load or are rate-limited, exactly like the forex rows that will never get one.
- **Source:** `CoineProSurfaces.kt:626–637` (flat `TextDisabled` rule for `< 2` values).
- **Fix:** separate *pending* from *absent*. `SparklineStore` knows whether a request is in flight: draw a 30 % `TextDisabled` dashed rule (or the existing skeleton shimmer) while pending, and the solid rule only when the answer was empty.

### LISTS-23 · P2 · Up green is darker on dark than TV's dark-theme text green
- **Where:** all lists, dark. Our up text is `#089981` (TV's *candle* colour). TV's dark-theme **text** green is `#22AB94` (sampled from `tv-screener-dark-1600.png`). The down red `#F6465D` is already close to TV's dark text red `#F7525F`, so keep it.
- **Source:** `core/tokens/.../CoineProPalette.kt:204` (`marketUp = 0xFF089981`).
- **Fix:** on the dark palette only, set `marketUp = Color(0xFF22AB94)` for text (≈ 6.3:1 on `#171C24`) and keep `#089981` for candle fills (those come from `TradingViewPalette`, not this token).

### LISTS-24 · P2 · Screener sub-line repeats the ticker instead of the name
- **Where:** screener rows. «BREWUSDT / BREW», «MEMESTOCKU… / MEMESTOCK», «US30USDT / US30». The watchlist shows «بیت‌کوین/تتر». TV shows the full name ("Bitcoin", "NVIDIA Corporation").
- **Source:** `ScreenerScreen.kt:579–586` (`row.meta.localRowName()` falls back to the base).
- **Fix:** when `localRowName()` equals the base, draw nothing and centre the ticker vertically. That also helps LISTS-05's single-line desktop row.

### LISTS-25 · P2 · The selected rail item in light is barely distinguishable
- **Where:** navigation rail, light, 1600 (`ours-screener-light-1600.png`). The selected plate is `#F0F3FA` on stage `#F7F8FA`, ΔL* ≈ 1.5. The label weight is the only real cue. Dark is fine (`#171C24` on `#0B0E11`).
- **Source:** `core/designsystem/.../CoineProNavigationRail.kt:208–209` (`SurfaceElevated`).
- **Fix:** use `SurfaceRaised` (`#E8ECF4`) for the plate in light, or add a 3 dp accent-ink bar on the reading edge.

### LISTS-26 · P2 · The chart side rail has no hover or tooltip, and its glyph sizes are uneven
- **Where:** chart, 1600, the side-panel rail at the start edge (`ours-chart-siderail-hover-zoom.png`).
- **What:** hover is a 40 px circular M3 state layer with no label. The «?» glyph renders at about 14 px and the depth «ılı» glyph at about 16 px, beside 22 px glyphs. TV's widget bar has a 36 px rounded-square hover with a tooltip naming the panel.
- **Source:** `feature/chart/.../ChartSidePanels.kt:246–262`.
- **Fix:** wrap each `IconButton` in `TooltipBox(PlainTooltip { Text(stringResource(panel.labelRes)) })`, use a 36 dp `RoundedCornerShape(6.dp)` hover plate, and normalise the drawables to the same 22 dp optical box.

### LISTS-27 · P2 · The menu's watchlist count disagrees with the list
- **Where:** Menu, both locales. «دیده‌بان … ۷» / "Watchlist 7", while the watchlist header says «۱۴ نماد» / "14 symbols" (`ours-menu-en-light-1600.png` vs `ours-watchlist-home-en-light-1600.png`).
- **Source:** `CoineProApp.kt:5086` (`watchlistCount = watchlist.size`, the subscribed/starred set) vs `WatchlistPanel.kt:552` (`order.size`).
- **Fix:** feed the menu from `WatchlistStore.lists()` for the active list (the same `order.size`).

### LISTS-28 · P2 · The CSV export feedback is a stray, permanent "Saved" line that pushes the table down
- **Where:** screener, en, 1600 (`ours-screener-csv-click.png`). After CSV, an 11 sp muted "Saved" appears under the title, shifts everything down 15 px, and never goes away. It also does not say where the file went.
- **Source:** `ScreenerScreen.kt:175–182`.
- **Fix:** show it as a `CoineProToast` («فایل screener.csv ذخیره شد») and clear `exportOutcome` after 3 s.

---

## Where we are already better than TradingView (keep these)
1. **Screener on a phone.** TV has no mobile screener. Ours is fully functional at 412 px, with filters, growth signals, CSV and "add all to watchlist".
2. **A trend sparkline in the watchlist** (52×24 with a fill), coloured by the day's move. TV's watchlist has no sparkline by default.
3. **Artwork on every row, plus a native-language name under the ticker** («بیت‌کوین/تتر»). TV shows only the ticker in its watchlist.
4. **Figures:** Latin tabular figures that are strictly `TextAlign.Right` in both directions, so decimal points line up in RTL too (watchlist and markets). This is correct, and harder than TV's LTR-only case.
5. **Price flash on tick** (`coineProPriceFlash`), limited to the price cell so a row does not double-flash.
6. **Free features TV charges for:** unlimited lists, colour flags, notes and sections, CSV export, growth-signal scans and screener alerts.
7. **Contrast work on the red.** The figure red `#F6465D` (4.89:1 on the elevated card) is effectively TV's own dark-text red `#F7525F`. Keep it.
8. **Honest empty cells.** An em dash rather than 0, and an empty-state copy for a failure that is separate from the one for "no match".
9. **Three-state sort** (desc → asc → the reader's own order) in the watchlist, once LISTS-01 is fixed. TV has no way back to manual order without dragging.
10. **The phone bottom bar:** 64 px, a selected plate behind the glyph, and a bold label. It is clean and on par with TV mobile.

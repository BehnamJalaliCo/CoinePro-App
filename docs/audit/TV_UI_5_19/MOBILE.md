# MOBILE audit: Pro Chart phone and tablet vs TradingView

Build under test: https://pro-chart.com/terminal/ (Compose wasm, the phone's own sources), 2026-09-25.
Viewports: phone 412x900 (isMobile, hasTouch, DPR 2), tablet 1024x1366 portrait and 1366x1024 landscape (touch, DPR 1).
Covered: fa dark, fa light, en dark, en light.
Screenshots are in `scratchpad/audit/shots/MOBILE/`. Pixel values in this report are CSS px/dp. At DPR 2, divide PNG px by 2.

**Limits on the TradingView side.** The machine sat at load average 200–260 during the run (a wasm-opt or Gradle build was running). The proxy also refuses WebSocket upgrades, so TradingView's chart never got data and its bottom sheets could not be opened. The TradingView references are therefore:
- its mobile chart chrome (`tv-p-chart-dark.png`, `tv-p-chart-light.png`)
- the mobile markets list (`tv-p-markets-dark.png`)
- the mobile symbol page (`tv-p-symbol-dark.png`)

Where a TradingView number is from its phone app rather than measured here, the text says so. I did not reach Alerts through navigation before the page crashed (OOM under load). The alert sheets are covered from source in section B.

---

## A. Defects, most visible first

### MOBILE-01 · P0 · Missing glyphs show as empty boxes (em dash, middle dot, en dash, arrows) on every web screen
- **Where:** phone and tablet, fa and en, both themes.
  - The Rasad band under the chart reads «اینجا روندی نیست ▯ بازار…» / "There is no trend here ▯ the market…".
  - The tablet indicator-template menu has the same problem.
- **Screens:** `ours-p-chart-fa-dark.png` (crop `crop-band.png`), `ours-p-chart-en-dark.png`, `ours-tp-chart-fa-dark.png`, `ours-tl-chart-fa-dark.png`.
- **Cause:** checked with fontTools. `iranyekanx_*.ttf` (all four weights) has **no** U+2014 `—`, U+2013 `–`, U+00B7 `·`, U+2190/2192 `←→` or U+2010–2012.
  - Android falls back to Roboto, which quietly breaks the one-typeface rule.
  - Wasm has no fallback font, so these characters render as tofu (empty boxes).
- **The font does have:** U+002D `-`, U+2212 `−`, U+2022 `•`, U+060C `،`, U+061B `؛`, U+2026 `…`, U+00AB/BB `«»`.
- **Scale:** 199 `<string>` lines in `values*/strings.xml` (— ×135, · ×59, → ×2, ← ×2, – ×1). Also 206 Kotlin string literals. Top files:
  - `chart/core/.../RasadCoach.kt:207,209,214…`
  - `feature/chart/.../ChartDesktopToolbar.kt:220` (`"  ·  "`)
  - `namascript/.../ScriptLessons.kt`
  - `feature/activity/.../ActivityScreen.kt`
  - `core/marketintel/.../CalendarPersian.kt`
- **Fix, either of:**
  - (a) Re-subset IRANYekanX from the full family with U+2013/2014/00B7/2190/2192 included. It is still the same typeface, so this is the cleanest fix.
  - (b) Replace the characters:
    - fa `—` → `؛ ` or `، `
    - en `—` → ` - `
    - `·` → `•`
    - `→` → `›`
- **Either way:** add a gate to `check-cross-phase-consistency.py`. It should fail on any code point in UI strings or Kotlin literals that is not in the font's cmap.
- **TradingView:** never shows tofu (it uses system fonts).

### MOBILE-02 · P0 · Many sheet bodies have no side padding, so text is cut at the sheet edge and switches touch it
- **Where:** phone, every locale and theme.
- **Screens:**
  - `ours-p-scale.png` (Price scale): labels at x=0; «فاصله‌های…» and «قفل نسبت…» are cut at the right edge; switches sit at x=0.
  - `ours-p-settings.png` (Chart settings): «رنگ کندل بر پایه‌ی…» loses its first letter at the right edge; switches at x=0.
  - `ours-p-layouts.png` (Layouts): the text field and «ذخیره‌ی چیدمان فعلی» are 0 px from both edges; the «تم برنامه» chip touches the right edge.
  - `ours-p-partners.png` (Broker): the LBank card is flush to both edges; the disclaimer is cut at x=0.
- **TradingView:** content sits 16 px from the sheet edge (their mobile pages use 20 px: `tv-p-markets-dark.png`, list text starts at x=20).
- **Cause:** `CoineProSheetBody` (`core/designsystem/.../CoineProSheet.kt:148-199`) pads only its title row.
- **Fix:** each body in the table in section B must wrap its root in `.padding(horizontal = CoineProSpacing.Gutter)`. A better systemic fix: give `CoineProSheetBody` a `contentPadding: PaddingValues = PaddingValues(horizontal = Gutter)` parameter. Let the few full-bleed bodies (lists that pad their own rows) opt out with `PaddingValues(0.dp)`.

### MOBILE-03 · P0 · Chart page on phone leaves a 125 dp empty black block under the toolbar
- **Where:** phone, all four combinations.
- **Screens:** `ours-p-chart-fa-dark.png`, `ours-p-chart-en-light.png`, `ours-p-chart-en-scrolled.png`. The last one shows that scrolling reveals nothing below.
- **What happens:** the plot is `screenHeightDp × 0.72` = 648 dp. The band, H/L row and toolbar add about 127 dp, so content ends at y=775. The page is 900 tall, so y 775–900 is dead `#0B0E11`.
- **TradingView:** the chart fills to the bottom toolbar, and the toolbar sits on the bottom edge.
- **Source:** `feature/chart/.../ChartScreen.kt:1996-2004` (plotHeight) and `:2365` (`if (fills) weight(1f) else height(plotHeight)`); `PLOT_SCREEN_FRACTION = 0.72f` at `:5965`.
- **Fix:** on the compact phone layout, use `Modifier.weight(1f)` for the plot, the same as the tablet `fills` branch. Keep `PLOT_MIN` as a `heightIn(min=)`. The toolbar then lands on the bottom edge like TradingView's.

### MOBILE-04 · P1 · Phone chart toolbar is 32 dp wider than the screen: fullscreen icon shrunk to half size, both ends touch the edges
- **Where:** phone 412 dp, all combinations.
- **Screens:** `crop-band.png`, `ours-p-chart-en-dark.png`.
- **Measured:**
  - The fullscreen glyph's ink is 9.5 dp wide against 17.5 dp for undo, so it is squeezed.
  - Fa: its ink starts 6 dp from the left edge. En: it is at the right edge.
  - The symbol pill's outer edge is 4.5 dp from the screen edge.
- **Content sum:** 4 + 132 (wheel) + 56 (interval) + 9 + 5×46 + 9 + 4 = **444 dp > 412**.
- **TradingView:** the phone bottom bar has a 12–16 dp outer gutter and no clipped glyphs. Its mobile web header puts the hamburger at x=17 (`tv-p-chart-dark.png`).
- **Source:** `feature/chart/.../ChartChrome.kt:184-258`; `TOOLBAR_TARGET = 46.dp` at `:1328`; row padding `CoineProSpacing.Half` at `:188`.
- **Fix:**
  - Set the row padding to `CoineProSpacing.OneHalf` (12).
  - Set `TOOLBAR_TARGET` to 40 dp on compact widths (still ≥ 40 dp with 22 dp ink).
  - Drop the undo button from the phone bar, since undo is already in the «…» hub.
  - Or cap the wheel at `WHEEL_SCROLL_WIDTH_MAX = 88.dp` (`SymbolWheel.kt:938`) and the ticker at 15 sp (see MOBILE-12).
  - Sum after the fix: 12+118+50+9+4×40+9+12 = 370 dp.

### MOBILE-05 · P1 · Tapping the symbol pill does nothing
- **Where:** phone chart toolbar. I tried a touch tap and a mouse click. Screen: `ours-p-symsheet.png`, unchanged after the tap.
- **TradingView:** tapping the symbol opens symbol search. Its drag-picker is extra, not a replacement.
- **Source:** `feature/chart/.../SymbolWheel.kt:559-593`. The pointerInput only detects a hold, and `draggable` only a drag. There is no `clickable`.
- **Fix:** add `onClick` to `SymbolScrollWheel` and wire it at `ChartChrome.kt:195` to `onOpen(ChartSheet.SEARCH)`, or to the same handler as `ChartDesktopToolbar`'s `onSymbolSearch`.

### MOBILE-06 · P1 · «مقیاس قیمت» tile: its second line «عادی» is cut in half
- **Where:** phone and tablet «…» hub.
- **Screens:** `ours-p-more.png` (top row, middle tile), `ours-tl-more.png`.
- **Cause:** the outlined tile is 56 dp tall. It holds a 26 dp glyph + 4 dp gap + 16 dp label + 15 dp note, which is 61 dp or more.
- **Source:** `feature/chart/.../ChartChrome.kt:1189` (`.height(if (outlined) HUB_OUTLINED_TILE else HUB_TILE)`); `HUB_OUTLINED_TILE = 56.dp` at `:1344`.
- **Fix:** use `heightIn(min = 56.dp)`, or 64 dp for the whole outlined row whenever any tile carries a `note`. Or drop the glyph to 22 dp inside outlined tiles.

### MOBILE-07 · P1 · English UI shows hard-coded Persian (drawing tools and chart-type picker) and the informal imperative «کن»
- **Where:** tools sheet (`ours-p-tools.png`). The hint reads «ابزار انتخاب‌شده را با ستاره اینجا سنجاق **کن**.»
- **Rule broken:** no «کن». The copy also has no English version.
- **Source:** 95 Arabic-script literals in `chart/ui/src/*Main`, not gated on locale:
  - `ToolRail.kt` ×37, e.g. `:193` «جست‌وجوی ابزار», `:641` the «کن» sentence, `:836` «راهنما»
  - `ChartPickers.kt` ×17, e.g. `:98` «زمان‌محور», `:251-253` the empty states
  - `ChartUiPlatform.wasmJs.kt` ×20
  - `DrawingRenderer.kt` ×14
- **Fix:**
  - Move these to `chart/ui` string resources (`values/` English, `values-fa/` Persian).
  - Change `:641` to «ابزار انتخاب‌شده را با ستاره اینجا سنجاق کنید.»
  - The `checkDefaultLocaleIsEnglish` idea should extend to Kotlin literals in `chart/ui`.

### MOBILE-08 · P1 · Watchlist name truncated to "Watc…" with plenty of space free
- **Where:** phone, en (`ours-p-menu-en-dark.png`, `ours-p-home-en-light.png`). In fa it only fits because «دیده‌بان» is short.
- **Cause:** the name row has `weight(1f, fill=false)` and the `Spacer(weight(1f))` after the count splits the leftover 50/50. The name gets about 45 dp.
- **Source:** `feature/search/.../WatchlistPanel.kt:521-557`.
- **Fix:** delete the `Spacer(Modifier.weight(1f))` at `:557`. Give the picker row `Modifier.weight(1f)` (fill=true), so the icon cluster is pushed to the end. Or move the «14 symbols» count under the name as a second line in 11 sp.

### MOBILE-09 · P1 · Root tabs spend a 64 dp app bar on one lone avatar, then repeat the title below
- **Where:** Watchlist, News, Community.
- **Screens:** `ours-p-home-fa-dark.png`, `ours-p-news-fa-light.png`, `ours-p-community.png`. On tablet: `ours-tp-home-fa-dark.png`.
- **Measured:** the 30 dp gold-ring avatar sits alone in a 64 dp bar, and the title («دیده‌بان» 20 sp) starts at y=85. Watchlist says «دیده‌بان» three times: page title, list picker, tab label.
- **TradingView:** mobile header is 38 dp with the title/symbol inline (`tv-p-chart-dark.png`: header 0–38, chip at y=19). The markets page title sits directly under a 52 dp bar that holds the menu, logo, search and CTA together.
- **Source:** `app/.../CoineProApp.kt:2994-3037`. The avatar is in `TopAppBar.actions` and `SELF_TITLED` (`:879-900`) empties the title.
- **Fix:** for root routes in `SELF_TITLED`, don't draw the `TopAppBar`. Pass the avatar into the screen's own header (`CoineProListHeader` actions slot) as the trailing element. That saves 64 dp on every tab.

### MOBILE-10 · P1 · Event markers draw over the time-axis labels and pile on each other
- **Where:** phone and tablet chart.
- **Screen:** `crop-band.png`.
  - «12:00» and «مهر ۲» are covered by 12 dp squares.
  - A run of 12 purple news bolts overlaps into one smear at x 470–625.
- **TradingView:** event icons sit in their own lane above the date labels. Icons closer than their width merge into one cluster badge with a count.
- **Source:**
  - `chart/core/.../ChartEvents.kt:146,149` (`SIZE_DP=12f`, `AXIS_GAP_DP=3f`)
  - `chart/ui/.../CoineProChart.kt:3836-3874` (`drawEventMarks`, where `centreY = axisTop + 3dp + 6dp` lands on the label baseline)
- **Fix:**
  - Draw the lane at `axisTop - SIZE_DP - 2dp`, inside the plot's bottom margin, or reserve 16 dp between plot and dates.
  - In `drawEventMarks`, merge marks whose x are within `side + 2dp` into one cluster glyph.

### MOBILE-11 · P1 · Tablet onboarding and starter screens stretch to the full 990 dp width
- **Screens:** `ours-tp-onb2.png` (four rows of 330 dp pills and a 990 dp CTA), `ours-tp-onb3.png` (persona cards 990 dp wide, chevron 950 dp from its text).
- **What is odd:** the welcome page caps its buttons at 416 dp (`ours-tp-onb1b.png`), so the flow changes width mid-way.
- **TradingView:** forms and cards on tablet are capped at about 600–720 px.
- **Source:** `app/.../StarterPreferences.kt:75-135`; the persona screen that shows «چه چیزی روی چارت شما باشد؟».
- **Fix:** add `.widthIn(max = CONTENT_MAX_WIDTH)` (`CoineProSheet.kt:534`, 720 dp) centred, as the welcome page already does.

### MOBILE-12 · P1 · Chart toolbar type is oversized: 18 sp bold ticker and interval
- **Measured:** «BTCUSDT» and «H1» are 18 sp bold, with 13 dp cap height (26 PNG px).
- **TradingView:** mobile symbol chip is 14–15 px bold (`tv-p-chart-dark.png`, «BTCUSDT» cap ≈ 10 px). Its phone app bar is 15–16.
- **Source:**
  - `SymbolWheel.kt:743` and `ChartChrome.kt:362` use `MaterialTheme.typography.titleMedium`, which is 18 sp Medium (`CoineProType.kt:156`).
  - The code comment at `SymbolWheel.kt:737` says "16 sp bold", but the style really is 18.
- **Fix:** use `titleSmall` (15 sp) with Bold, or an explicit `fontSize = 16.sp`. That also frees about 14 dp for MOBILE-04.

### MOBILE-13 · P1 · Drawing-tools sheet category tabs are 22 sp bold and look like page headings
- **Screen:** `ours-p-tools.png`. «همه / حالت / خطوط / کانال‌ها / فیبوناچی» are 22 sp bold with a pill behind «همه».
- **TradingView:** category tabs are 14 px medium; active is 600 weight or underlined.
- **Mixed tile styles:** the same sheet mixes outlined tiles, filled tiles and split tiles with kebab menus. It also has 10 dp «?» badges in tile corners, and the grid gutter is 12 dp while the search field has 16.
- **Source:** `chart/ui/.../ToolRail.kt` (category row and tiles).
- **Fix:**
  - Tabs: `labelLarge` (14 sp, SemiBold for active).
  - Tiles: one tile style (the filled plate).
  - Help: move into the long-press or the kebab.
  - Grid: `Gutter` padding.

### MOBILE-14 · P1 · Lone ⓘ icons take whole rows, sit apart from what they explain, and are 24 dp targets
- **Screens:** `ours-p-menu.png` (ⓘ alone at y=433 under the language control), `ours-p-scale.png` (ⓘ under «معکوس»), `ours-p-layouts.png` (ⓘ on a black square at the right edge), `ours-p-ind.png`.
- **TradingView:** help lives in a trailing icon on the same row as the label, or in a tooltip on the label, with a ≥ 32 px target.
- **Source:** `core/designsystem/.../CoineProNote.kt:135-148` (the collapsed branch renders `CoineProInfoTip` as its own node), `:193-194` (`INFO_TIP_TARGET = 24.dp`).
- **Fix:**
  - Give `CoineProNote` a trailing-slot variant, so call sites put the ⓘ in the label `Row`.
  - Set `INFO_TIP_TARGET` to 40 dp (glyph stays 16).
  - Remove the black background behind the ⓘ in the Layouts sheet.

### MOBILE-15 · P1 · Compact chips in sheets are 23 dp tall; decimal chips are 23 dp circles
- **Screen:** `ours-p-scale.png`.
  - «عادی / لگاریتمی / درصدی / شاخص ۱۰۰» chips measure 46 PNG px = 23 dp.
  - The «۰ ۲ ۴ ۸» discs measure 23 dp.
- **TradingView:** mobile chips are 34 px tall (`tv-p-markets-dark.png`: «All coins» pill y 460–528 = 34 px), pill radius, 16 px text.
- **Source:** `CoineProChipRow(compact = true)` in `core/designsystem/.../CoineProSheet.kt:230-300`, via `CoineProToggleChip` `:303-400`.
- **Fix:** `heightIn(min = 32.dp)` for compact and 36 dp for regular, with `minimumInteractiveComponentSize()`.

### MOBILE-16 · P1 · Watchlist toolbar buttons are 34 dp boxed squares, heavier than anything else on the page
- **Screen:** `ours-p-home-fa-dark.png`. Four 34 dp squares with a fill and a hairline («…», list, □, +). The □ glyph means nothing.
- **TradingView:** bare 24 px glyphs with a 44 px target and no box. Its header icons have no plates.
- **Source:** `feature/search/.../WatchlistPanel.kt:811-820` (`size(34.dp)`, `background(SurfaceElevated)`, `border`).
- **Fix:**
  - Drop the background and border at rest and keep them only for `active`.
  - `size(40.dp)` target with `minimumInteractiveComponentSize()`.
  - Replace the □ (`tv_layout_grid` "compare") with a labelled split-view glyph.

### MOBILE-17 · P1 · Tablet portrait watchlist: all columns crammed into the right 40%, a 550 dp gap on the left
- **Screen:** `ours-tp-home-fa-dark.png`. Symbol, last, change and trend occupy x 560–930. Row dividers run the full 1000 dp. The icon cluster floats mid-screen at x 295–455.
- **TradingView:** at tablet width the markets table adds columns (volume, market cap, rank) or caps the table width (`tv-p-markets-dark.png` shows the column set).
- **Fix, either of:**
  - Cap the watchlist pane at `CONTENT_MAX_WIDTH` (720 dp) and centre it.
  - Or add columns (24h high/low, volume) when width > 700 dp, in `WatchlistPanel`'s row layout.

### MOBILE-18 · P1 · Tablet chart toolbar: «بازپخش» label cut to one letter at the scroll edge; undo/redo hidden
- **Screen:** `ours-tp-chart-fa-dark.png` (crop `crop-tp-toolbar.png`: «⏪ ،»).
- **TradingView:** at narrow widths the top bar drops labels and shows icons only; nothing is cut mid-word.
- **Source:** `feature/chart/.../ChartDesktopToolbar.kt:103-177` (a scrolling row with no fade and no collapse).
- **Fix:** below about 1200 dp, render `LabelledButton` as glyph-only. Also add a 24 dp edge fade to the scroll row.

### MOBILE-19 · P1 · News: title inset 32 dp while cards are at 16 dp; 214 dp hero image per story
- **Screen:** `ours-p-news-fa-light.png`. The title «اخبار بازار» right edge is at 380 dp (32 dp gutter); cards are at 396 dp (16 dp). About 1.3 stories fit per screen.
- **TradingView:** the mobile news list uses about 72–88 dp rows with a small thumb (phone app). Gutter 20 px.
- **Source:** `feature/news/.../PublicNewsScreen.kt:136-148`. The column pads by `Gutter` and `CoineProListHeader` pads again. `NewsScreen.kt:353` already passes `Modifier.padding(horizontal = 0.dp)`.
- **Fix:**
  - Pass `modifier = Modifier.padding(horizontal = 0.dp)` at `PublicNewsScreen.kt:143`.
  - Use a compact row (96 dp thumb trailing) for every story after the first.

### MOBILE-20 · P1 · The «…» hub mixes three tile styles, two grids and a rainbow rim
- **Screens:** `ours-p-more.png`, `ours-p-more2.png`.
- **What is mixed:**
  - Outlined 3-column tiles (56 dp) and filled 2-column plates (88 dp), then filled 3-column.
  - A pink-to-blue `spectrumRim` card, the only rainbow on a gold/neutral product.
  - Glyph sizes and strokes vary: «عمق بازار» ink about 14 dp, «رصد» about 24 dp, «مقایسه» thin.
- **TradingView:** this is its phone app's hub, and it keeps one plate style per section.
- **Source:** `feature/chart/.../ChartChrome.kt:830-1042` (HubGrid/HubTile), `:1060-1096` (TradeCard with `spectrumRim`).
- **Fix:**
  - One plate style everywhere (filled `SurfaceElevated`, 12 dp radius), with one column count per section.
  - Use a gold hairline, not spectrum, on the trade card.
  - Normalise the icon set to 24 dp at 1.5 px stroke.

### MOBILE-21 · P2 · Onboarding/starter: the label on a selected (gold) pill reads lighter than on unselected ones
- **Screens:** `ours-p-onb2.png`, `ours-tp-onb2.png`. «سیستم / فارسی / USDT / سبز» on gold look Regular; «روشن / English / BTC» look bold. The same happens with «شروع» vs «ورود به حساب» (`ours-p-onb1.png`).
- **Cause:** both use `labelMedium`/`labelLarge` Medium, but dark ink on `#D8A848` looks optically thinner.
- **Source:** `app/.../StarterPreferences.kt:181-190`; `CoineProSurfaces.kt:440-446` (ButtonContent).
- **Fix:** `fontWeight = FontWeight.SemiBold` when `on`, and in `CoineProPrimaryButton`.

### MOBILE-22 · P2 · Primary button has a dark bronze 1 dp rim
- **Measured:** `#8B6B2B` outline around the `#D8A848` fill (`ours-p-menu-en-light.png` at y=389). It reads as a drawn border, dated on light surfaces.
- **TradingView:** filled CTAs have no border (`tv-p-markets-dark.png`, «Get started»).
- **Source:** `core/designsystem/.../CoineProSurfaces.kt:359-363`.
- **Fix:** `border = null` when enabled. Keep the rim only for the disabled state.

### MOBILE-23 · P2 · Guest avatar shows «?» in the app bar and «م» in the menu
- **Screens:** `ours-p-home-fa-dark.png` shows «?»; `ours-p-menu.png` and `ours-p-rasad.png` show «م». In en: «?» vs «G».
- **Source:** `app/.../CoineProApp.kt:3027-3031` (`initial = (profile.displayName ?: accountName)?.take(1) ?: ""`).
- **Fix:** fall back to `stringResource(R.string.guest_name).take(1)`, the same as the menu card.

### MOBILE-24 · P2 · Chart legend back button sits on the far left pointing right in Persian, so it reads as "forward"
- **Screen:** `crop-head.png`.
- **Source:** `chart/ui/.../ChartLegendOverlay.kt:1224-1238` (`GLYPH_BACK_RTL` is used while the legend stays left-anchored).
- **Fix:** while the legend is left-anchored, use the edge-pointing glyph (←) in both locales.
- **Related:** the bare «…» legend disclosure beside the status dot has no label. Give it a 28 dp chip with a caret.

### MOBILE-25 · P2 · Tablet sheet-as-dialog still draws a grab handle
- **Screen:** `ours-tl-more.png` (handle at top of a centred dialog that cannot be dragged).
- **Source:** `CoineProSheet.kt:103` → `CoineProSheetBody` always calls `SheetHandle()` (`:149`).
- **Fix:** add `showHandle: Boolean = true` and pass `false` from the Dialog branch.

### MOBILE-26 · P2 · Grab handle is almost invisible in dark
- **Measured:** 36×4 dp `#282C32` on `#10141B`, contrast about 1.3:1 (`ours-p-more.png`).
- **TradingView:** phone app handle is about 32×4, mid-grey (about `#5D606B` on `#1E222D` in its dark theme; phone app, not measured here).
- **Source:** `CoineProSheet.kt:211-217` (`CoineProColors.Border`).
- **Fix:** `CoineProColors.BorderStrong` (or `TextDisabled`), 32×4 dp, top padding 8.

### MOBILE-27 · P2 · Sheet top radius 28 dp (Material 3 default)
- **Measured:** about 26–28 dp (`ours-p-more.png` corner fit).
- **Why it matters:** with full-height sheets, the chart shows only as a 28 dp sliver of rounded corner.
- **TradingView:** 12–16 dp on phone sheets (phone app; not measurable here).
- **Source:** `ModalBottomSheet(...)` at `CoineProSheet.kt:109` passes no `shape`.
- **Fix:** `shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)`.
- **Related:** the More, Indicators and Tools sheets open at full height and cover the whole page. Also consider `skipPartiallyExpanded = false` with a 60% peek for long sheets (`:68`).

### MOBILE-28 · P2 · Rasad sentence in English: "with Low swing"
- **Screen:** `ours-p-chart-en-dark.png`.
- **Source:** `chart/core/.../RasadCoach.kt:207,214,219`. `volatilityLabel(english)` returns a capitalised label.
- **Fix:** use `swing.lowercase()` in the English templates.

### MOBILE-29 · P2 · Filter chips show zero counts as «۰», which reads like a dot or «ه»
- **Screen:** `ours-p-ind.png` («برگزیده‌ها ۰», «اخیر ۰»).
- **Source:** `chart/ui/.../ChartPickers.kt` (IndicatorPicker chip labels).
- **Fix:** omit the count when it is 0.

### MOBILE-30 · P2 · Hover and flash backgrounds are full-bleed while dividers are inset 16 dp
- **Screens:**
  - Watchlist SOL/BNB rows flash full width. I saw this on screen, but the capture was overwritten; the price-cell flash rectangle is also visible behind «121.70».
  - The XAUUSD hover (`ours-p-home-en-light.png`) and the menu «کاوش» hover (`ours-p-menu2-fa-light.png`) run to both edges.
  - Menu dividers run to the far edge on the end side (x=0 in fa, x=412 in en).
- **TradingView:** row hover spans the row, and dividers span the same box.
- **Source:** `feature/menu/.../MenuScreen.kt:199-203` (`ROW_DIVIDER_INSET` only on the start side).
- **Fix:** add `end = CoineProSpacing.Gutter` to the divider. Clip the row highlight to the gutter box, or make the dividers full-bleed. Pick one and use it everywhere.

### MOBILE-31 · P2 · Setup page: 410 dp of empty space between the last choice and the CTA
- **Screen:** `ours-p-onb2.png`.
- **Fix:** put the CTA directly under the content (`Arrangement.spacedBy`) when content is shorter than the viewport, or top-align the CTA 24 dp under the last group.
- **Related:** the «رشد یعنی سبز / قرمز» chips should preview the colour with a 8 dp swatch.
- **Source:** `app/.../StarterPreferences.kt:82-135`.

### MOBILE-32 · P2 · Web launch flashes dark → white → dark
- **Screens:** `ours-p-menu-en-dark.png` is taken during the reload: it shows the `index.html` splash «Pro Chart» in system-ui on dark. It is followed by the Compose white splash (`ours-tp-onb1.png`), then the dark app.
- **Also:** the HTML splash uses system-ui, not IRANYekanX, and Latin text in a Persian-default app.
- **Source:** `web/src/wasmJsMain/resources/index.html:17-26`; `app/.../LaunchSplash.kt:160` (`Color.White` by design).
- **Fix:** make the `index.html` splash white with the black mark (inline SVG). That gives one white frame sequence into the app, not three swaps.

### MOBILE-33 · P2 · Mixed languages in the tablet range strip
- **Screen:** `ours-tp-chart-fa-dark.png`. The strip reads «1D 5D 1M 3M 6M YTD 1Y 5Y **همه**».
- **Fix:** use «همه» as "All" in the same Latin set, or localise the whole strip. Latin codes are fine for market figures but should not be mixed within one control.

### MOBILE-34 · P2 · Menu «ژورنال معاملات» uses a map-pin icon
- **Screen:** `ours-p-menu3-fa-light.png`.
- **Fix:** use a notebook/book glyph (`tv_notebook` or Phosphor `NotebookSimple`).

### MOBILE-35 · P2 · Menu shows «دیده‌بان ۷» while the watchlist header says «۱۴ نماد»
- **Screens:** `ours-p-menu.png` vs `ours-p-home-fa-dark.png`.
- **Fix:** both should read the same count source, or the menu badge should say what 7 counts.

---

## B. Sheets whose body has no horizontal padding

`CoineProSheetBody` pads only its title. Each body below lacks `padding(horizontal = Gutter)` at its root, and its children do not pad themselves.

| # | Sheet (call site) | Body root without side padding | Evidence |
|---|---|---|---|
| 1 | Price scale — `feature/chart/.../ChartScreen.kt:2881` (also inside Settings `:2896`) | `ChartScreen.kt:4548` `PriceScaleSheetBody` Column (only `verticalScroll`) | `ours-p-scale.png` |
| 2 | Chart settings — `ChartScreen.kt:2896` | `feature/chart/.../ChartSettingsSheet.kt:73` `ChartSettingsBody` Column | `ours-p-settings.png` |
| 3 | Layouts — `ChartScreen.kt:2821` | `feature/chart/.../LayoutSheet.kt:71` `LayoutSheetBody` Column | `ours-p-layouts.png` |
| 4 | Layouts / Settings colour block | `feature/chart/.../ColourTemplateSection.kt:81` `ColourTemplateSection` Column (its chip row has no contentPadding) | `ours-p-layouts.png` («تم برنامه» at edge) |
| 5 | Broker partners — `ChartScreen.kt:3181` | `feature/chart/.../TradePartnersSheet.kt:61` Column; `PartnerCard` `fillMaxWidth` | `ours-p-partners.png` |
| 6 | Compare — `ChartScreen.kt:2948` | `ChartScreen.kt:4679` `ComparisonSheetBody` Column; `SheetLabel` (`ChartScreen.kt:4886`) has no padding | source |
| 7 | Setup (trade from chart) — `ChartScreen.kt:2998` | `feature/chart/.../SetupSheet.kt:82` Column | source |
| 8 | Live trade ticket — `ChartScreen.kt:2987` | `feature/chart/.../LiveTradeSheet.kt:128` Column | source |
| 9 | Backtest — `ChartScreen.kt:2967` (modifier = verticalScroll only) | `feature/chart/.../BacktestSheet.kt:189` Column (`CoineProTeachingStrip(gutter = false)` at `:193`) | source |
| 10 | Drawings / object tree — `ChartScreen.kt:3330` | `feature/chart/.../SelectionToolbar.kt:246` `DrawingClipboardRow` (Column `padding(bottom)` only); `feature/chart/.../ObjectTreeSheet.kt:114` Column + group header `:174` (vertical padding only; rows at `:265` pad 12, not 16) | source |
| 11 | Watchlist «more» sheet — `feature/search/.../WatchlistSheets.kt:882` | rows `MoreRow` `WatchlistSheets.kt:906` pad only `CoineProSpacing.Half` (4 dp) | source |
| 12 | Indicators → templates, **empty-search path** | `chart/ui/.../ChartPickers.kt:258` calls `trailing?.invoke()` without the Gutter wrapper that `:306` applies; `feature/chart/.../IndicatorTemplateMenu.kt:23/26` rows have vertical padding only | source |
| 13 | Webhook editor — `feature/alerts/.../WebhookSheet.kt:73` | `WebhookSheet.kt:173` `WebhookEditor` Column (`padding(bottom)` only). `WebhookList` `:88` is fine because `WebhookRow` `:149` pads Gutter | source |
| 14 | Alert editor form — `feature/alerts/.../AlertEditorSheet.kt:97` | `AlertEditorSheet.kt:183` `EditorForm` Column has none. Children pad one by one (`ChosenSymbol :290`, `ConditionBlock :331`), but `ScopeRow :601` → `FieldLabel` does not. Check every child | source |
| 15 | Avatar composer — `feature/profile/.../AvatarComposerSheet.kt:66` | `feature/profile/.../AvatarComposer.kt:185` Column (`modifier.fillMaxWidth()` only). `RingRow :175` pads, the rest do not | source |

Close to the rule but misaligned (12 dp against a 16 dp title):
- `chart/ui/.../ChartPickers.kt` `PickerRow` (horizontal `OneHalf`) under `GroupHeader :322` (Gutter).
- `feature/chart/.../ChartWatchlistSplit.kt:72` `WatchlistTickerRow` contentPadding `OneHalf`.

Checked and fine:
- AppearanceSheet `:124`
- MarketFilterSheet `:136`
- AlertComposer `:61`
- ScriptPasteSheet `:86/:269`
- IntervalSheetBody `ChartScreen.kt:4329`
- DrawingStyleSheet `:211`
- IndicatorSettingsSheet `:118`
- ExplainSheet `:75`
- RasadSheet `:128`
- ChartMoreSheetBody `ChartChrome.kt:826`
- ChartEventSettings `:49`
- HeatmapSettingsSheet (its `Section`/tabs pad 16)
- Academy extras (LazyColumn contentPadding)
- CoineProHelpSheet (its own ModalBottomSheet, contentPadding Gutter; it also has no handle and no close button, unlike every other sheet)

---

## C. Where we are already better than TradingView (keep these)
1. **Full Persian RTL** with correct Latin market figures and Persian prose counts. TradingView has no fa UI.
2. **A real bottom tab bar** on phone (64 dp, labelled, plate on the selected tab). TradingView's mobile web has none and hides everything behind the hamburger.
3. **Watchlist rows** carry logos (no blank squares; lettered discs where allowed) and a 7-day sparkline with a soft area fill. TradingView's mobile list has neither sparkline nor subtitle (`tv-p-markets-dark.png` vs `ours-p-home-en-light.png`).
4. **Pastel change pills on Home** (`ours-p-rasad.png`: `+66.57%` on a tinted plate). This is softer and more legible than TradingView's plain coloured text.
5. **Sheet close disc** (32 dp, `SurfaceElevated`) on every CoineProSheet, plus a 40% scrim that keeps the chart readable behind partial sheets (`ours-p-scale.png`, `ours-p-layouts.png`).
6. **Light theme** is calm and well contrasted (`#F7F8FA` page, `#FFFFFF` cards, `#E9E9EC` dividers). It is closer to "pastel/soft" than TradingView's pure white.
7. **Toolbar count badges** on indicators and drawings, and the Rasad one-line reading under the chart once the tofu (MOBILE-01) is fixed. TradingView has nothing like the reading.
8. **Tablet dialog-instead-of-sheet** with a 560 dp cap (`ours-tl-more.png`). This is right for large glass; only the handle is wrong (MOBILE-25).

## Screenshot index (`scratchpad/audit/shots/MOBILE/`)
- **Ours, phone:**
  - onboarding: `ours-p-onb1.png`, `onb1b`, `onb2`, `onb3`
  - watchlist: `ours-p-home-fa-dark.png`, `home-en-light`
  - chart: `ours-p-chart-fa-dark.png`, `chart-en-dark`, `chart-en-light`, `chart-en-scrolled`
  - sheets: `ours-p-more.png`, `more2`, `scale`, `layouts`, `settings`, `partners`, `ind`, `tools`, `tf`, `symsheet`
  - other tabs: `ours-p-rasad.png`, `community`, `menu`, `menu-en-dark`, `menu-en-light`, `menu-fa-light`, `menu2-fa-light`, `menu3-fa-light`, `news-fa-light`
- **Ours, tablet:** `ours-tp-onb1.png`, `onb1b`, `onb2`, `onb3`, `home-fa-dark`, `chart-fa-dark`; landscape `ours-tl-chart-fa-dark.png`, `ours-tl-more.png`.
- **Crops:** `crop-band.png`, `crop-head.png`, `crop-tp-toolbar.png`.
- **TradingView:** `tv-p-chart-light.png`, `tv-p-chart-dark.png`, `tv-p-markets-dark.png`, `tv-p-symbol-dark.png`. `tv-p-interval-sheet.png` is still loading, because WebSockets are blocked.

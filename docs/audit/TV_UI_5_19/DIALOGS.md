# DIALOGS — chart sheets, dialogs and menus vs TradingView

Audited live on https://pro-chart.com/terminal/BTCUSDT/4h (build served 2026-09-25), headless Chromium, 1600x900 and
412x900, dark and light (`prefers-color-scheme`), Persian (default) and English (set via
`localStorage["app_language/language_tag"]="en"`, the web shim's key for `AppLanguageStore`).
Screenshots: `scratchpad/audit/shots/DIALOGS/` (paths below are relative to that folder).
Console errors were captured with a Playwright `page.on('console'|'pageerror')` hook.

**TradingView reference caveat.** The anonymous tradingview.com/chart session loaded the chrome but never
loaded bars (websocket data never arrived), and after ~6 interactions it put up a non-dismissable
"Look first / Then leap — Join for free" wall (`tv-03-signup-wall.png`). The TV pixels measured here are from
the Indicators dialog (light + dark) and the main menu, which opened before the wall; for chart settings,
legend and context menu the TV spec is the one in the brief (radius 6, header 56, row 32, inputs 34,
hover #2A2E39) plus TV's well-known layout, and is marked "(brief/known)" where not measured.

TV measured (dark, `tv-10-indicators-dark.png`): dialog 840x638 at x380-1220, radius ~8, bg #1F1F1F,
**no scrim** (backdrop stays #0F0F0F), title 20px bold #DBDBDB at 20px inset, close = bare 18px × (#CBCBCB)
with no plate; search 40px, transparent fill, 1px #4A4A4A border, radius ~6; left category column 200px,
rows 40px, selected plate #3D3D3D (light #EBEBEB) radius 6; type pills 28px tall, selected #F2F2F2 (light
#2E2E2E), unselected #414141/#F2F2F2; list pitch 32px. Menu (`tv-02-menu.png`): 260px wide, radius ~6,
drop shadow, no border, rows 40px, 14px regular text, leading 20px icons, separators between groups.

Ours measured (dark, `ours-10-indicators-desk-dark-fa.png`): every sheet on desktop is the same
528x778 dialog (x536-1063, y61-838), radius 16, bg #10141B, 1px #282C32 border, 60% black scrim (backdrop
#0B0E11 -> #040607, a #F23645 candle -> #61161C), a 36x4 drag handle, header 88px, filled 32px close disc
(#171C24), 44px filled search plate (#171C24, radius 12, no border), list rows 58px.

---

## Defects (most visible first)

### DIALOGS-01 · P0 · Scrolling the Indicators list crashes the app
- Where: Indicators dialog, desktop 1600 and phone, any theme/locale, "همه" (All) filter.
- What: a mouse wheel of ~1500px (i.e. scrolling far enough that a second «در پنل جدا» heading composes)
  throws `IllegalArgumentException: Key "h-SEPARATE" was already used` and Compose stops drawing — the
  canvas freezes (clock and hover stop; page JS still alive). A reload is the only way out.
  Shots: `ours-13-ind-listwheel.png` -> `ours-15-b.png` (frozen, hover no longer moves), log captured in the run.
- TV: Indicators list scrolls freely, sections never repeat.
- Source: `chart/ui/src/androidMain/kotlin/com/coinepro/core/chart/ChartPickers.kt:269-275` — the heading
  item is emitted every time `option.pane` changes, and the catalogue (`ChartCatalog.kt:367-440`) interleaves
  PRICE and SEPARATE studies, so `item(key = "h-SEPARATE")` is emitted twice.
- Fix: group before emitting — `val ordered = if (grouped) shown.sortedBy { it.pane.ordinal } else shown`
  (stable sort keeps catalogue order inside a pane) and iterate `ordered`; keep `key = "h-${heading.name}"`.
  Add a unit test that the emitted keys are unique for `ChartCatalog.indicatorsFor(true/false)`.

### DIALOGS-02 · P0 · Chart settings → «مقیاس‌ها / Scales» tab crashes; every tab is blank afterwards
- Where: Chart settings (gear), all viewports/themes/locales.
- What: opening the Scales tab throws `IllegalStateException: Vertically scrollable component was measured
  with an infinity maximum height constraints…` (captured as PAGEERROR). The dialog body dies: Scales,
  Canvas, Trading, Events all render as an empty band between two dividers until the page is reloaded.
  Shots: `ours-L5-set-status.png` (before), `ours-L6-set-scales.png`, `ours-L7c-set-canvas-move.png` (after);
  Canvas works when opened first after a reload: `ours-51-set-canvas.png`.
- TV: "Scales and lines" tab (brief/known) — a plain form.
- Source: `feature/chart/.../ChartSettingsSheet.kt:73-77` wraps the tab in `verticalScroll`, and the Scales tab
  calls `scales()` = `PriceScaleSheetBody` which scrolls itself (`ChartScreen.kt:4548-4551`,
  `.verticalScroll(rememberScrollState())`).
- Fix: give `PriceScaleSheetBody` a `scrolls: Boolean = true` parameter and pass `false` from
  `ChartScreen.kt:2907` (the settings tab); keep `true` for the standalone SCALE sheet (`ChartScreen.kt:2886`).

### DIALOGS-03 · P1 · No side padding: text and switches touch/clip at the dialog edge
- Where: Chart settings (all tabs), Compare, «معامله با کارگزار» (broker partners); desktop and phone; fa and en.
- What: the body has 0px horizontal inset. fa: «رنگ کندل بر پایه‌ی…» is cut at x=1063 (dialog edge), switches
  sit at x=537, «بازنشانی تنظیمات» clipped; Compare cuts the ETH/SOL logos and «از دیده‌بان» at the edge;
  broker disclaimer runs edge to edge and is clipped both sides. Phone: toggles at x=0, labels at x=404.
  en: switches flush right, "Colour bars…" flush left while the title sits at a 16px gutter.
  Shots: `ours-50-chartsettings.png`, `ours-80-compare.png`, `ours-F0-trade-ticket.png`, `ours-P3-phone-settings.png`,
  `ours-N2-en-settings.png`, `ours-L3-settings-light.png`.
- TV: 20px content inset in every dialog (title and body share one left edge).
- Source: `ChartSettingsSheet.kt:73-79` (Column has no padding), `ChartScreen.kt:4679-4683`
  (`ComparisonSheetBody`), `TradePartnersSheet.kt:61`. Root cause: `CoineProSheetBody`
  (`core/designsystem/.../CoineProSheet.kt:198`) calls `content()` with no inset and leaves it to each body.
- Fix: add `.padding(horizontal = CoineProSpacing.Gutter)` to the three Columns now; systemically add
  `contentPadding: Dp = CoineProSpacing.Gutter` to `CoineProSheet/CoineProSheetBody` and wrap `content()` in it
  (list bodies that pad their own rows — IndicatorPicker, ChartTypePicker — pass `0.dp`).

### DIALOGS-04 · P1 · Symbol search opens a full-page screen on desktop
- Where: click «BTCUSDT» in the toolbar, desktop 1600, fa/en.
- What: navigates away from the chart to a full-width page: search field 1568px wide, rows 73px, the price at
  x=16 and the symbol at x=1480 (1464px apart), forex rows show "–", a coach-mark bubble overlaps the chips,
  Esc does nothing (must click the back arrow). Shots: `ours-70-symbolsearch.png`, `ours-71-search-wheel.png`.
- TV (known): centred "Symbol search" dialog ~800x680 over the chart, 56px header, 40px search, source tabs,
  rows ~48px with symbol / description / exchange, Esc closes.
- Source: `app/src/main/kotlin/com/coinepro/app/CoineProApp.kt:3623` `onOpenSymbolSearch = { navController.navigate(MARKET_SEARCH_ROUTE) }`
  (and the same callback at `ChartScreen.kt:2227/2323/1821`).
- Fix: on `coineProWindowClass().showsTwoPanes` open `SearchScreen`'s body
  (`feature/search/.../SearchScreen.kt:111`) inside `CoineProSheet` sized `widthIn(max = 800.dp)` and
  `heightIn(max = 680.dp)`, rows capped by `CONTENT_MAX_WIDTH`; wire `Key.Escape` to dismiss; keep the route on phone.

### DIALOGS-05 · P1 · Legend «⋯» menu opens on the opposite side of the chart
- Where: indicator row in the legend, desktop, fa (RTL).
- What: the SMA row's «⋯» is at x≈296,y≈121; its menu opens at x1379-1550, y92-393 — 1100px away, flush
  against the right tool rail. The legend also collapses when it opens. Shot: `ours-23-legend-more-menu.png`.
- TV (known): menu drops from the button, left-aligned to it.
- Source: `feature/chart/.../ChartScreen.kt:1714` `Box(Modifier.align(Alignment.TopStart).padding(top = 48.dp))` —
  `TopStart` is the right edge in RTL while the legend plate is laid out LTR (see `ChartLegendOverlay.kt`).
- Fix: record the button's window position (`onGloballyPositioned` in `LegendButton`, `ChartLegendOverlay.kt:1116`)
  into `seriesMenu`, then anchor like the context menu does: `Box(Modifier.align(AbsoluteAlignment.TopLeft))` +
  `DropdownMenu(offset = DpOffset(buttonX, buttonBottom))` under an LTR `CompositionLocalProvider`
  (same pattern as `ChartScreen.kt:1757-1764`).

### DIALOGS-06 · P1 · Legend controls on web are typographic stand-ins, not icons
- Where: price/indicator legend rows, desktop and phone, all themes/locales.
- What: eye = «•», settings = «¦» (broken bar), remove = «×», more = «…», collapse = «−», overflow = «› +1»,
  change label = «±». They read as stray punctuation, sit on different baselines and give no hover plate.
  Shots: `ours-21-legend-zoom.png` (4x), `ours-19-after-add.png`, `ours-N4-en-context.png`.
- TV: 18px SVG icons (eye, gear, trash/×, ⋯) inside 22-24px buttons, hover plate #2A2E39 radius 4.
- Source: `chart/ui/src/wasmJsMain/kotlin/com/coinepro/core/chart/ChartUiPlatform.wasmJs.kt:399-415`
  (`ChartMarks`), drawn as `Text` in `ChartLegendOverlay.kt:1116-1134` (`LegendButton`'s default `mark`).
- Fix: replace the glyph `Text` with `Icon(imageVector = …)` built in commonMain with `ImageVector.Builder`
  (path data for eye / eye-off / settings / trash / more-horizontal / chevron) at 16dp inside the 24dp
  `LEGEND_BUTTON_DP`, tint `palette.text`, plus a hover background `SurfaceRaised` radius 4dp. Delete the
  per-platform glyph table for these five marks.

### DIALOGS-07 · P1 · Menus are Material's lavender, not our palette
- Where: right-click context menu, legend «⋯» menu, indicator-templates menu, time-axis menu; dark and light.
- What: container #211F26 (dark) / **#F3EDF7 lavender** (light), hover #313036 / #E1DBE6 — a purple cast
  beside our navy #10141B / white surfaces. Shots: `ours-A0-context.png`, `ours-L2-context-light.png`,
  `ours-24-legend-menu-hover.png`, `ours-90-layout.png`.
- TV: menu = dialog surface (#1F1F1F dark / #FFFFFF light), hover #2A2E39 / #F0F3FA, radius 6, shadow.
- Source: `core/designsystem/.../CoineProTheme.kt:24-50` sets only surface/surfaceVariant; M3 1.4 menus read
  `surfaceContainer`, which falls back to the baseline purple.
- Fix: in both `darkColorScheme` and `lightColorScheme` add `surfaceContainerLowest = stage`,
  `surfaceContainerLow = surface`, `surfaceContainer = surface`, `surfaceContainerHigh = surfaceElevated`,
  `surfaceContainerHighest = surfaceRaised`, `surfaceBright = surfaceRaised`, `surfaceDim = stage`,
  `surfaceTint = Color.Transparent`. This fixes every DropdownMenu/tooltip at once.

### DIALOGS-08 · P1 · 60% black scrim on every dialog; live-preview sheets hide what they edit
- Where: all desktop dialogs; worst on Indicator settings and Drawing style (meant to preview live), and in light.
- What: the web Dialog applies Compose-Multiplatform's default 0x99000000 scrim and ignores `scrimAlpha`
  (0.4 default, 0.2 for preview sheets). A drawn trend line is barely visible behind its own style sheet; the
  light page #F7F8FA turns #636364. Shots: `ours-30-ind-settings.png`, `ours-D0-drawstyle.png`, `ours-L1-indicators-light.png`.
- TV: no dim at all behind Indicators / Settings / drawing settings (backdrop #0F0F0F unchanged, `tv-10-indicators-dark.png`).
- Source: `core/designsystem/.../CoineProSheet.kt:82-85` — `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`;
  `scrimAlpha` is only used on the phone path (line 115).
- Fix: an `expect fun sheetDialogProperties(scrimAlpha: Float): DialogProperties` — web `actual` returns
  `DialogProperties(usePlatformDefaultWidth = false, scrimColor = Color.Black.copy(alpha = scrimAlpha))`,
  Android `actual` sets the window dim amount; use 0.2 default on desktop and 0 for `SHEET_PREVIEW_SCRIM_ALPHA`.

### DIALOGS-09 · P1 · English Indicators dialog is still Persian
- Where: Indicators dialog, en, desktop and phone.
- What: title/subtitle are English but the search placeholder «جست‌وجوی اندیکاتور», all chips
  («همه، برگزیده‌ها، اخیر، روند، مومنتوم، نوسان، حجم»), the section «روی قیمت» and all 114 indicator names are
  Persian; the subtitle reads «۱۱۴ indicators» with Persian digits. Shot: `ours-N1-en-indicators.png`.
- Source: hard-coded strings `ChartPickers.kt:217, 243, 251-253, 316-318, 623-632`; category labels
  `chart/core/.../ChartCatalog.kt:123-130`; indicator labels `ChartCatalog.kt:367-440` (Persian only);
  `ChartScreen.kt:2731` uses `toPersianDigits()` regardless of locale.
- Fix: move the picker strings to `values/` + `values-fa/` resources; give `IndicatorOption` and
  `IndicatorCategory` a `labelEn` (parameters already have one) and pick by `AppLanguage`; use `proseDigits()`
  (locale-aware) for the count.

### DIALOGS-10 · P1 · One fixed 528x778 box for every dialog, regardless of content
- Where: all desktop sheets.
- What: Indicator settings shows one row («دوره 20») in a 778px-tall box; Compare 6 rows; broker 1 card; the
  alert composer ends at y≈480 leaving 350px empty. Meanwhile Indicators is too narrow (528 vs TV 840) to hold a
  category column. Shots: `ours-30-ind-settings.png`, `ours-80-compare.png`, `ours-60-alert.png`, `ours-F0-trade-ticket.png`.
- TV: dialogs size to content (settings ≈ 480-560 tall) with a max; Indicators 840x638; Settings ~640x560.
- Source: `CoineProSheet.kt:86-90` — `.widthIn(max = SHEET_DIALOG_MAX_WIDTH).fillMaxHeight(0.9f)` (+16dp outer padding).
- Fix: replace `fillMaxHeight(0.9f)` with `heightIn(max = maxHeight * 0.9f)` (wrap content), and add a
  `dialogWidth: Dp = 560.dp` parameter so Indicators/Symbol search can ask for 840/800.

### DIALOGS-11 · P1 · Selected colour swatch is invisible
- Where: Indicator settings → «ظاهر», Drawing style → «ظاهر»; all themes.
- What: the chosen swatch gets a 2px **gold** ring; the default indicator/drawing colour *is* gold (#D8A848),
  so the selected swatch looks identical to the others (sampled: disc #D8A848 to its edge, no ring).
  Shots: `ours-31-ind-settings-style.png`, `ours-D0-drawstyle.png`.
- TV (known): selected swatch = 2px gap + 1-2px ring in the text/accent colour, or a check mark.
- Source: `IndicatorSettingsSheet.kt:218-222`, `DrawingStyleSheet.kt:903-907`.
- Fix: draw the ring outside a gap: `Modifier.border(2.dp, CoineProColors.TextPrimary, CircleShape).padding(3.dp).clip(CircleShape).background(colour)`
  on a `SWATCH + 6.dp` box; unselected keeps the 1dp `Border` hairline.

### DIALOGS-12 · P1 · Heavy black-thumb switches where TV uses light toggles/checkboxes
- Where: Chart settings (all toggle tabs), Indicator settings → «نمایش»; dark and light.
- What: Material Switch 52x32, gold track with a **near-black thumb** (#111318); in light theme it is a black
  disc on gold — the heaviest object in the dialog. Shots: `ours-L3-settings-light.png`, `ours-51-set-canvas.png`, `ours-32-ind-settings-vis.png`.
- TV (known): 18px checkboxes in settings forms; where a switch is used it is ~34x18 with a white thumb.
- Source: `ChartSettingsSheet.kt:180-190`, `IndicatorSettingsSheet.kt:269-278, 300-312`
  (`checkedThumbColor = CoineProColors.OnAccent`).
- Fix: one `CoineProSwitch` in designsystem: 36x20 track, 16dp white thumb (`Color.White` both themes),
  checked track `pageAccent`, unchecked `SurfaceRaised`; or a 18dp `CoineProCheckbox` for settings lists.

### DIALOGS-13 · P1 · List rows are 58px; TV's are 32-40px
- Where: Indicators, Chart type, desktop.
- What: 9 indicator rows fit in 778px (TV shows ~14 in 480px of list). Row = 3dp gap + 12dp pad + 24sp body +
  12dp pad + 3dp gap, with 28dp star/help boxes. Hover plate radius 8. Shots: `ours-10-indicators-desk-dark-fa.png`, `ours-40-charttype.png`.
- TV: 32px row pitch, 14px text, hover #2A2E39 (dark) / #F0F3FA (light), radius 6 on the category column.
- Source: `ChartPickers.kt:352-377` (`ROW_GAP` line 646, `CoineProSpacing.OneHalf` padding, `bodyLarge`).
- Fix: when `showsTwoPanes`: `ROW_GAP = 0`, vertical padding 6dp, `bodyMedium` (15/22) → 34dp rows; star/help
  boxes 24dp with 16dp glyphs; keep 48dp rows on the phone.

### DIALOGS-14 · P1 · Category chips run off the dialog; two categories are unreachable by sight
- Where: Indicators, desktop (fa: off the left edge; en: off the right edge) and phone.
- What: 9 chips in a 528px dialog; «بیل ویلیامز» and «ساختار بازار» are fully hidden, the last visible chip
  sits 4px from the edge with no fade/arrow, and a vertical wheel over the row does nothing.
  Shots: `ours-12-ind-chipwheel.png`, `ours-N1-en-indicators.png`, `ours-P1-phone-indicators.png`.
- TV: categories are a vertical left column (200px, 40px rows) — nothing is ever off-screen.
- Source: `ChartPickers.kt:233-244` → `CoineProChipRow` (`CoineProSheet.kt:248-256`); categories `ChartCatalog.kt:123-130`.
- Fix (desktop): lay the picker out as `Row { CategoryColumn(width = 200.dp); list }` inside an 840dp dialog;
  (phone) keep the chip row but add a 24dp end fade (`drawWithContent` + gradient to `Surface`).

### DIALOGS-15 · P1 · Chart settings: tiny chip tabs + duplicated tab name as subtitle
- Where: Chart settings, desktop/phone, fa/en.
- What: the six tabs are 24px compact chips (gold selected) jammed in one line, and the header subtitle repeats
  the selected tab («نماد» under «تنظیمات چارت», with «نماد» chip right below). No Cancel/Ok footer.
  Shots: `ours-50-chartsettings.png`, `ours-N2-en-settings.png`.
- TV (known): left vertical tab list with icons (Symbol, Status line, Scales and lines, Canvas, Trading, Alerts,
  Events), 40px rows, selected plate; footer "Template ▾ · Cancel · Ok".
- Source: `ChartScreen.kt:2898` (`subtitle = stringResource(settingsTab.labelRes)`), `ChartSettingsSheet.kt:80-86`.
- Fix: drop the subtitle; on desktop render `Row { TabColumn(200.dp) ; VerticalDivider ; content }` with the
  existing `ChartSettingsTab` entries; phone keeps chips but in a scrollable row with the 16dp gutter.

### DIALOGS-16 · P1 · Two different "New alert" editors, and the full one hides its Save button
- Where: toolbar «هشدار» vs the left-rail alerts panel «ساختن هشدار»; desktop, fa.
- What: the toolbar opens a compact composer (price, 6 condition chips, frequency, Create/Cancel —
  `ours-60-alert.png`); the panel opens a long editor (scope, 5 condition tabs, repeat, expiry, delivery, volume,
  text) whose primary «ذخیره‌ی هشدار» is the last item of the scroll, disabled-grey until a price is typed, and
  looks like a secondary pill (`ours-E0-alerts-list.png`, `ours-E2-alert-editor-end.png`). Chip rows in it run 8px
  past the 16px gutter (e.g. «همین نماد» to x=1055, «عبور» to 1051).
- TV (known): one "Create alert" dialog everywhere, pinned footer with Cancel + blue Create.
- Source: `feature/notifications/.../AlertComposer.kt` (toolbar) vs `feature/alerts/.../AlertEditorSheet.kt:188`
  (scrolling Column) and `:277-284` (button inside the scroll).
- Fix: route the toolbar to `AlertEditorSheet` (price pre-filled) or vice versa; move the primary button out of
  the `verticalScroll` Column into a pinned footer Row (`Cancel` secondary + `Create` primary, 40dp).

### DIALOGS-17 · P1 · Phone: active indicator names wrap to two lines
- Where: Indicators sheet, 412px, fa.
- What: on a selected row the stepper (‹ 20 ›) + check + star + «؟» take ~210px, so «میانگین متحرک ساده»
  wraps and the row grows to 70px while its neighbours are 58px. Shot: `ours-P1-phone-indicators.png`.
- Source: `ChartPickers.kt:393-425`.
- Fix: `maxLines = 1, overflow = Ellipsis` on the label, and move the period stepper to a second line
  (`Column`) only when the row is selected on compact width, or drop the «؟» on selected rows (long-press already opens help).

### DIALOGS-18 · P2 · Mobile drag handle drawn on desktop dialogs
- Where: every desktop dialog (y=74, 36x4 #282C32 bar). Shots: all `ours-*` desktop dialogs.
- TV: none on desktop.
- Source: `CoineProSheet.kt:149` (`SheetHandle()` always) / `205-219`.
- Fix: `if (!coineProWindowClass().showsTwoPanes) SheetHandle()` — or pass `showHandle = false` from the Dialog branch.

### DIALOGS-19 · P2 · Header 88px with a filled close disc; TV is 56-64px with a bare ×
- Where: all desktop dialogs.
- What: handle (28) + 20sp title + 11sp subtitle = 88px before content; close is a 32px #171C24 disc.
- TV: title row ~64px (20px bold), close = 18px × glyph in a 28px hover-only button.
- Source: `CoineProSheet.kt:150-196` (`SHEET_CLOSE` 32dp, `.background(CoineProColors.SurfaceElevated)` line 185).
- Fix: on desktop remove the handle, top padding 20dp, close button background only on hover
  (`coineProControl` hover plate), glyph 18dp; subtitle only where it adds information (see -15, -24).

### DIALOGS-20 · P2 · Search field style differs from TV and from our own inputs
- Where: Indicators search (desktop/phone). Shot: `ours-10-indicators-desk-dark-fa.png` (y149-192).
- What: 44px filled plate #171C24, radius 12, no border; our form fields elsewhere are 56-58px outlined (-24).
- TV: 40px, transparent, 1px #4A4A4A (dark) / #E0E3EB (light) border, radius 6, 16px text, focus border blue.
- Source: `CoineProSheet.kt:442-451`, `SHEET_SEARCH_HEIGHT` line 498.
- Fix (desktop): height 40dp, `CoineProShapes.small` (8) or 6dp, `border(1.dp, Border)`, transparent fill,
  focus border `pageAccent`.

### DIALOGS-21 · P2 · Menu rows 48px, Medium weight, no icons, no separators, no shortcuts
- Where: context menu (10 items, 494px tall), legend menu, templates menu; desktop fa/en.
- What: M3 `DropdownMenuItem` default 48dp rows and `labelLarge` 14 Medium (reads bold in IRANYekanX);
  no grouping. Shots: `ours-A0-context.png`, `ours-N4-en-context.png`.
- TV: 32-40px rows, 14px regular, 20px leading icons, dividers between groups, right-aligned shortcut hints
  (e.g. "Alt+R" reset, "Alt+A" alert).
- Source: `ChartScreen.kt:1759-1830` (context), `1715-1750` (legend), `ChartDesktopToolbar.kt:215-230` (templates).
- Fix: a `CoineProMenuItem(icon, label, shortcut)` with `contentPadding = PaddingValues(horizontal = 12.dp)`,
  `modifier = Modifier.height(36.dp)`, text `bodyMedium` `FontWeight.Normal`, trailing shortcut in `TextMuted`;
  `HorizontalDivider` between alert/copy, scale/settings/data window, replay, picture, help groups.

### DIALOGS-22 · P2 · Indicator-templates menu: name and summary fused in one bold string
- Where: toolbar templates button, desktop fa/en. Shots: `ours-90-layout.png`, `ours-N5-en-templates.png`.
- What: «سه خط بیل ویلیامز • Alligator» all in one weight/colour; «•» separators; no header or "Save template…".
- TV (known): header "Indicator templates", "Save indicator template…", rows = name (primary) + faint summary.
- Source: `ChartDesktopToolbar.kt:215-230` (`stringResource(templateName(...)) + "  ·  " + templateSummary(...)`).
- Fix: `text = { Column { Text(name, bodyMedium) ; Text(summary, labelSmall, TextMuted) } }`, plus a disabled
  header item and a divider.

### DIALOGS-23 · P2 · Indicator settings: stepper instead of an input, no footer, orphan info icons
- Where: Indicator settings, desktop/phone. Shots: `ours-30-ind-settings.png`, `ours-32-ind-settings-vis.png`.
- What: Inputs tab has only a ‹ 20 › stepper (no Source/Offset), the (i) of `CoineProNote` sits alone on the next
  line under the label; Visibility tab puts «جای پنل» heading, an (i), then a switch labelled «در پنل جدا» — three
  lines for one choice; tabs are a 40px segmented pill.
- TV (known): tabs Inputs / Style / Visibility as underline tabs; each input a 34px number field with spinner;
  footer "Defaults ▾ · Cancel · Ok".
- Source: `IndicatorSettingsSheet.kt:145-153` (segmented), `169-197` (stepper rows), `261-268` and `282-287` (note placement).
- Fix: underline tabs (text 14, 2px indicator), `CoineProNumberField` 34dp for each parameter, info as a
  trailing 16dp icon inside the label Row, pinned footer.

### DIALOGS-24 · P2 · Drawing style sheet: duplicated title, 58px inputs, heavy full-width buttons
- Where: Drawing style (trend line), desktop. Shot: `ours-D0-drawstyle.png`.
- What: title «خط روند» and subtitle «خط روند · 00:00 12:00» repeat the name; custom-colour and template-name
  fields are 58px outlined fields; «ذخیره به‌عنوان پیش‌فرض…» and «ذخیره‌ی رنگ و ضخامت…» are 44px full-width pills.
- TV (known): tabs Style / Text / Coordinates / Visibility, 34px inputs, compact colour picker popover,
  "Template ▾" in the footer.
- Source: `DrawingStyleSheet.kt:133-134` (title/subtitle), `334`, `498`, `541` (`CoineProTextField`), `470`, `505` (buttons).
- Fix: subtitle = coordinates only («00:00 → 12:00»); a 34dp dense text-field variant; move template save/apply
  into a footer "Template ▾" menu.

### DIALOGS-25 · P2 · Analysis hub (⋯): clipped tile note, rainbow tile, mixed tile sizes
- Where: toolbar «⋯» (desktop) / «…» (phone). Shots: `ours-B0-overflow.png`, `ours-P2-phone-more.png`.
- What: «مقیاس قیمت» tile's note «عادی» is clipped at the tile's bottom edge (56dp tile holds 26 glyph + 16 label
  + 4 + 15 note); «معامله با کارگزار» has a pink-to-blue spectrum rim that nothing else in the product uses;
  56dp 3-up tiles, a 72dp card, then 88dp 2-up tiles; range chips run off the left edge.
- TV: its "More" menu is a plain 40px-row menu.
- Source: `feature/chart/.../ChartChrome.kt:1189` + `HUB_OUTLINED_TILE = 56.dp` (1344), note at 1234-1240;
  `TradeCard` `.spectrumRim(...)` at 1073.
- Fix: `HUB_OUTLINED_TILE = 64.dp` or drop the note into the label («مقیاس: عادی», maxLines 1); replace
  `spectrumRim` with the 1dp `BorderSubtle` of its neighbours.

### DIALOGS-26 · P2 · Four chip styles inside the chart dialogs
- Where: Indicators (gold filled pill), Settings/alert (compact gold pill), alert editor delivery/volume
  (text-only unselected + raised neutral selected), Canvas colour templates (rounded-rect outlined gold).
  Shots: `ours-10…`, `ours-50…`, `ours-E2-alert-editor-end.png`, `ours-51-set-canvas.png`.
- TV: one pill: 28px, radius 14, selected inverted (#F2F2F2 on dark / #2E2E2E on light), unselected #414141/#F2F2F2.
- Source: `CoineProSheet.kt:303-413` (`CoineProToggleChip`, `neutral`/`compact`), `ColourTemplateSection.kt`,
  `AlertEditorSheet.kt` chip rows.
- Fix: filters in dialogs use `neutral = true` everywhere (gold reserved for the primary action), one height
  (28dp), and the colour-template chooser uses the same pill.

### DIALOGS-27 · P2 · Drawing-tool flyout docks and reflows the chart
- Where: right tool rail → any group, desktop. Shot: `ours-C0-line-drawn.png` (chart shrinks by 215px, price axis
  jumps from x=1490 to 1275; group heading «خط‌ها» at y=14 is cut by the top edge).
- TV: the flyout is an overlay popover beside the rail; the chart never moves.
- Source: `feature/chart/.../ChartToolRailColumn.kt:141-160` ("The flyout is layout, not a popup").
- Fix: render the same Column in a `Popup` anchored to the rail button on desktop (keep layout on tablet if
  wanted), with 8dp top padding for the heading.

### DIALOGS-28 · P2 · Coach-mark bubbles overlap dialogs and run off the edge
- Where: alerts panel open (bubble at x1305-1583 over the tool rail), symbol search (bubble over the chips).
  Shots: `ours-E0-alerts-list.png`, `ours-70-symbolsearch.png`.
- Fix: suppress coach marks while any `CoineProSheet`/dialog is open and clamp the bubble to the window minus 16dp.

### DIALOGS-29 · P2 · Alerts panel title shown twice
- Where: left-rail bell, desktop fa. Shot: `ours-E0-alerts-list.png` — «هشدارها» in the window bar (y=17) and again
  as the panel heading (y=66).
- Source: the panel heading in `feature/alerts/.../AlertCenterScreen.kt` (file located; exact line not traced)
  plus the side-panel host's own title bar.
- Fix: draw the heading only when the screen is a full page, not when hosted as the side panel.

### DIALOGS-30 · P2 · Compare has no search
- Where: Compare, desktop/phone. Shot: `ours-80-compare.png`.
- What: only the watchlist (6 rows) is offered; a symbol outside it cannot be compared.
- TV (known): "Compare symbol" dialog = the symbol-search dialog with Add / Overlay actions.
- Source: `ChartScreen.kt:4654-4662` (deliberately watchlist-only).
- Fix: reuse the search dialog from DIALOGS-04 with a "compare" mode.

---

## Where we are already better than TradingView (keep)
- Per-row «؟» on indicators and chart types, plus long-press, opening an in-app explanation (`ChartPickers.kt:73-80`).
- Inline period stepper on switched-on indicators in the picker (`ours-18-ind-selected.png`) — TV needs a second dialog.
- Favourites / Recent chips with counts, and indicator icons tinted in the study's own line colour.
- Chart-type list grouped into time-based / price-driven / derived with one explanatory heading (`ours-40-charttype.png`).
- Alert composer states the live price and "an alert only tells you, it never places an order" (`ours-N3-en-alert.png`).
- Context menu has "Alert at <exact price>" and "Replay from here" at the pointer (`ours-A0-context.png`).
- RTL mirroring of the dialog chrome is correct (close on the left, title on the right, chips start at the right).
- Footprint/TPO hidden on feeds without volume instead of drawing zeros.

## Not reproduced / not reachable
- "Trade on LBank" order ticket: only the broker-partners sheet is reachable (`ours-F0-trade-ticket.png`,
  «افتتاح حساب»); no in-app ticket appeared without an account.
- Layouts sheet (`ChartSheet.LAYOUTS`) is reachable only from the hub tile «چیدمان‌ها»; not captured separately.

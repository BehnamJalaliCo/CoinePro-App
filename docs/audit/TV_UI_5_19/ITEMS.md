# TradingView UI audit — every item and its status

116 items from the four audits beside this file. Status after 5.19.1.

| ID | P | Item | Status |
|---|---|---|---|
| CHART-01 | P0 | Legend hover buttons render as stray punctuation («•», «¦», «−», «…») | Fixed |
| CHART-02 | P0 | English UI opens a Persian Indicators sheet (and the chip row is clipped) | Fixed |
| CHART-03 | P1 | Persian layout is the mirror image of TradingView's RTL layout | Fixed |
| CHART-04 | P1 | Plot is 100–160 px shorter than TradingView's; three rows of footer under the bottom bar | Fixed |
| CHART-05 | P1 | Event marks sit on top of the time-axis dates | Fixed |
| CHART-06 | P1 | Drawing-tool flyout reflows the whole workspace instead of floating | Fixed |
| CHART-07 | P1 | Legend never shows OHLC with a mouse; head is phone-sized and two lines | Fixed |
| CHART-08 | P1 | Icons ~25 % smaller and greyer than TradingView's, from three different icon families | Partly fixed — 5.19.1: the toolbar, the drawing rail and three of the five side-rail panels use one 28-viewport tv_* family; the objects and explain panels keep their old glyphs at the ink-matched 22 dp, because swapping those two made the web build's chart screen crash on its first recomposition (bisected; see STATUS.md) |
| CHART-09 | P1 | Rail hover/active states are 48 px Material circles, not 34 px plates | Fixed |
| CHART-10 | P1 | Crosshair time tag drops the date and collides with the axis | Fixed — 5.19.1: dated tag in 5.19.0; tag ground #3D3D3D / #6A6D78 in 5.19.1 |
| CHART-11 | P1 | No tooltips on icon-only controls | Fixed |
| CHART-12 | P1 | Price-axis hover tag stays after the pointer leaves the plot | Fixed |
| CHART-13 | P1 | Last-price tag wears a radial glow | Fixed |
| CHART-14 | P2 | Toolbar type is smaller and heavier than TV's | Fixed |
| CHART-15 | P2 | Toolbar/bottom-bar separators are nearly invisible; top has a double hairline | Fixed |
| CHART-16 | P2 | Bottom bar: 32 px, muted 12 sp, no "Go to date", no auto | Fixed |
| CHART-17 | P2 | Side (widget) rail: 48 px pitch, no grouping, muted | Fixed |
| CHART-18 | P2 | Drawing rail lacks TV's rail-level switches and group separators | Fixed |
| CHART-19 | P2 | Legend/footer figures lose thousands separators | Fixed |
| CHART-20 | P2 | Light theme: plot sits on off-white, rails on white (inverted hierarchy) | Fixed — rails moved onto the page ground, so rails and plot share one ground in light; the plot keeps the ladder's off-white |
| CHART-21 | P2 | Watermark is a half-hidden smudge behind the volume bars | Fixed |
| CHART-22 | P2 | Volume overlay has a full-width lid line through the candles | Fixed |
| CHART-23 | P2 | Interval spellings are MetaTrader's, not TradingView's | Fixed |
| DIALOGS-01 | P0 | Scrolling the Indicators list crashes the app | Fixed |
| DIALOGS-02 | P0 | Chart settings → «مقیاس‌ها / Scales» tab crashes; every tab is blank afterwards | Fixed |
| DIALOGS-03 | P1 | No side padding: text and switches touch/clip at the dialog edge | Fixed |
| DIALOGS-04 | P1 | Symbol search opens a full-page screen on desktop | Fixed |
| DIALOGS-05 | P1 | Legend «⋯» menu opens on the opposite side of the chart | Fixed |
| DIALOGS-06 | P1 | Legend controls on web are typographic stand-ins, not icons | Fixed |
| DIALOGS-07 | P1 | Menus are Material's lavender, not our palette | Fixed |
| DIALOGS-08 | P1 | 60% black scrim on every dialog; live-preview sheets hide what they edit | Fixed |
| DIALOGS-09 | P1 | English Indicators dialog is still Persian | Fixed |
| DIALOGS-10 | P1 | One fixed 528x778 box for every dialog, regardless of content | Fixed |
| DIALOGS-11 | P1 | Selected colour swatch is invisible | Fixed |
| DIALOGS-12 | P1 | Heavy black-thumb switches where TV uses light toggles/checkboxes | Fixed |
| DIALOGS-13 | P1 | List rows are 58px; TV's are 32-40px | Fixed |
| DIALOGS-14 | P1 | Category chips run off the dialog; two categories are unreachable by sight | Fixed |
| DIALOGS-15 | P1 | Chart settings: tiny chip tabs + duplicated tab name as subtitle | Fixed |
| DIALOGS-16 | P1 | Two different "New alert" editors, and the full one hides its Save button | Fixed — 5.19.1: every «new alert» (chart, toolbar, market row, notifications page) opens the one full editor, price pre-filled |
| DIALOGS-17 | P1 | Phone: active indicator names wrap to two lines | Fixed |
| DIALOGS-18 | P2 | Mobile drag handle drawn on desktop dialogs | Fixed |
| DIALOGS-19 | P2 | Header 88px with a filled close disc; TV is 56-64px with a bare × | Fixed |
| DIALOGS-20 | P2 | Search field style differs from TV and from our own inputs | Fixed |
| DIALOGS-21 | P2 | Menu rows 48px, Medium weight, no icons, no separators, no shortcuts | Fixed |
| DIALOGS-22 | P2 | Indicator-templates menu: name and summary fused in one bold string | Fixed |
| DIALOGS-23 | P2 | Indicator settings: stepper instead of an input, no footer, orphan info icons | Fixed |
| DIALOGS-24 | P2 | Drawing style sheet: duplicated title, 58px inputs, heavy full-width buttons | Fixed |
| DIALOGS-25 | P2 | Analysis hub (⋯): clipped tile note, rainbow tile, mixed tile sizes | Fixed |
| DIALOGS-26 | P2 | Four chip styles inside the chart dialogs | Fixed |
| DIALOGS-27 | P2 | Drawing-tool flyout docks and reflows the chart | Fixed |
| DIALOGS-28 | P2 | Coach-mark bubbles overlap dialogs and run off the edge | Fixed — 5.19.1: coach marks hide while any sheet or dialog is open and sit a gutter inside the window |
| DIALOGS-29 | P2 | Alerts panel title shown twice | Fixed |
| DIALOGS-30 | P2 | Compare has no search | Fixed — 5.19.1: Compare has a search field that also takes any ticker typed in full |
| LISTS-01 | P0 | Clicking any watchlist column heading crashes the web app, and the UI stays frozen | Fixed |
| LISTS-02 | P1 | The desktop screener is a 360 px strip beside ~960 px of «یک مورد را از فهرست انتخاب کنید…» | Fixed |
| LISTS-03 | P1 | Screener third column (Volume) is cut off: a bare «–» / «K» with no heading | Fixed |
| LISTS-04 | P1 | Screener column headings sit at the wrong edge of their column in Persian | Fixed |
| LISTS-05 | P1 | Rows are 59 px everywhere; desktop density is about half of TV's | Fixed |
| LISTS-06 | P1 | The chart's watchlist panel says «دیده‌بان» three times and spends 140 px before the first row | Fixed |
| LISTS-07 | P1 | Watchlist toolbar floats in the middle of the page on desktop | Fixed |
| LISTS-08 | P1 | Forex, metals and indices rows never get a price: «–», «–» and a grey flat line | Fixed |
| LISTS-09 | P1 | Screener chrome never scrolls away: about 400 px of 836 px on the phone, about 390 px on desktop | Fixed |
| LISTS-10 | P1 | Screener progress and result lines contradict the table | Fixed |
| LISTS-11 | P1 | In English, the Filters sheet shows indicator names in Persian | Fixed |
| LISTS-12 | P1 | Price overlaps the ticker in screener rows (no gap between the symbol column and the figures) | Fixed |
| LISTS-13 | P1 | Markets list: the «24h trend» heading is 30 px off its column | Fixed |
| LISTS-14 | P2 | Lettered discs overflow their circle («MAR», «SHR», «BON», «MOO», «US3», «UK1») | Fixed |
| LISTS-15 | P2 | Sort marker is a 7 px trend squiggle in gold, not an arrow | Fixed |
| LISTS-16 | P2 | Headings are too small and too faint, have no header rule, and are not sticky-styled | Fixed |
| LISTS-17 | P2 | Dark hover is almost invisible | Fixed |
| LISTS-18 | P2 | Figures and tickers are 12 sp on desktop; TV uses 14 px | Fixed |
| LISTS-19 | P2 | Two chip systems on one screen, both undersized next to 44 px pill buttons | Fixed |
| LISTS-20 | P2 | Filters sheet: the value chip row is clipped at the sheet edge, and the last section is empty | Fixed |
| LISTS-21 | P2 | Persistent sync footer eats ~70 px under every watchlist | Fixed |
| LISTS-22 | P2 | Loading sparklines look identical to "no data" | Fixed — 5.19.1: the sparkline store reports its in-flight set; the watchlist reads it |
| LISTS-23 | P2 | Up green is darker on dark than TV's dark-theme text green | Fixed |
| LISTS-24 | P2 | Screener sub-line repeats the ticker instead of the name | Fixed |
| LISTS-25 | P2 | The selected rail item in light is barely distinguishable | Fixed |
| LISTS-26 | P2 | The chart side rail has no hover or tooltip, and its glyph sizes are uneven | Fixed |
| LISTS-27 | P2 | The menu's watchlist count disagrees with the list | Fixed |
| LISTS-28 | P2 | The CSV export feedback is a stray, permanent "Saved" line that pushes the table down | Fixed |
| MOBILE-01 | P0 | Missing glyphs show as empty boxes (em dash, middle dot, en dash, arrows) on every web screen | Fixed |
| MOBILE-02 | P0 | Many sheet bodies have no side padding, so text is cut at the sheet edge and switches touch it | Fixed |
| MOBILE-03 | P0 | Chart page on phone leaves a 125 dp empty black block under the toolbar | Fixed — 5.19.0 lead fix: the page reports its natural height, the plot fills the phone |
| MOBILE-04 | P1 | Phone chart toolbar is 32 dp wider than the screen: fullscreen icon shrunk to half size, both ends touch the edges | Fixed |
| MOBILE-05 | P1 | Tapping the symbol pill does nothing | Fixed |
| MOBILE-06 | P1 | «مقیاس قیمت» tile: its second line «عادی» is cut in half | Fixed |
| MOBILE-07 | P1 | English UI shows hard-coded Persian (drawing tools and chart-type picker) and the informal imperative «کن» | Fixed — ChartPickers and ToolRail are bilingual; no Persian-only literal is left |
| MOBILE-08 | P1 | Watchlist name truncated to "Watc…" with plenty of space free | Fixed |
| MOBILE-09 | P1 | Root tabs spend a 64 dp app bar on one lone avatar, then repeat the title below | Fixed |
| MOBILE-10 | P1 | Event markers draw over the time-axis labels and pile on each other | Fixed |
| MOBILE-11 | P1 | Tablet onboarding and starter screens stretch to the full 990 dp width | Fixed |
| MOBILE-12 | P1 | Chart toolbar type is oversized: 18 sp bold ticker and interval | Fixed |
| MOBILE-13 | P1 | Drawing-tools sheet category tabs are 22 sp bold and look like page headings | Fixed |
| MOBILE-14 | P1 | Lone ⓘ icons take whole rows, sit apart from what they explain, and are 24 dp targets | Fixed |
| MOBILE-15 | P1 | Compact chips in sheets are 23 dp tall; decimal chips are 23 dp circles | Fixed |
| MOBILE-16 | P1 | Watchlist toolbar buttons are 34 dp boxed squares, heavier than anything else on the page | Fixed |
| MOBILE-17 | P1 | Tablet portrait watchlist: all columns crammed into the right 40%, a 550 dp gap on the left | Fixed |
| MOBILE-18 | P1 | Tablet chart toolbar: «بازپخش» label cut to one letter at the scroll edge; undo/redo hidden | Fixed |
| MOBILE-19 | P1 | News: title inset 32 dp while cards are at 16 dp; 214 dp hero image per story | Fixed |
| MOBILE-20 | P1 | The «…» hub mixes three tile styles, two grids and a rainbow rim | Fixed |
| MOBILE-21 | P2 | Onboarding/starter: the label on a selected (gold) pill reads lighter than on unselected ones | Fixed |
| MOBILE-22 | P2 | Primary button has a dark bronze 1 dp rim | Fixed |
| MOBILE-23 | P2 | Guest avatar shows «?» in the app bar and «م» in the menu | Fixed |
| MOBILE-24 | P2 | Chart legend back button sits on the far left pointing right in Persian, so it reads as "forward" | Fixed |
| MOBILE-25 | P2 | Tablet sheet-as-dialog still draws a grab handle | Fixed |
| MOBILE-26 | P2 | Grab handle is almost invisible in dark | Fixed |
| MOBILE-27 | P2 | Sheet top radius 28 dp (Material 3 default) | Fixed |
| MOBILE-28 | P2 | Rasad sentence in English: "with Low swing" | Fixed — 5.19.1: labels lower-cased inside English Rasad sentences |
| MOBILE-29 | P2 | Filter chips show zero counts as «۰», which reads like a dot or «ه» | Fixed — 5.19.1: zero counts omitted on the indicator picker chips too |
| MOBILE-30 | P2 | Hover and flash backgrounds are full-bleed while dividers are inset 16 dp | Fixed |
| MOBILE-31 | P2 | Setup page: 410 dp of empty space between the last choice and the CTA | Fixed |
| MOBILE-32 | P2 | Web launch flashes dark → white → dark | Fixed — fixed differently: the page and manifest launch white (the in-app splash is white in every theme by the owner's decision), and the browser theme-color follows the stored theme |
| MOBILE-33 | P2 | Mixed languages in the tablet range strip | Fixed |
| MOBILE-34 | P2 | Menu «ژورنال معاملات» uses a map-pin icon | Fixed |
| MOBILE-35 | P2 | Menu shows «دیده‌بان ۷» while the watchlist header says «۱۴ نماد» | Fixed |

# TradingView UI audit — status after 5.19.0

Four audits (chart workspace, dialogs, lists, phone/tablet) compared the web terminal, the phone and
the tablet with TradingView shot by shot; the reports are beside this file. 116 defects were found;
four teams closed them in 5.19.0, each on its own files.

## Fixed in 5.19.0
- P0: indicator list crash (duplicate key), Scales tab crash, watchlist-sort crash (web datastore
  shim), missing glyphs on the web (chart-core / chart-ui / namascript literals now use the
  typeface's own glyphs; the consistency gate is strict for those three modules), sheets without side
  padding, the phone chart's empty band under the toolbar, a crash on the chart's first
  recomposition caused by a `composed {}` modifier (mouse drag is now a modifier node).
- Every horizontal row can be dragged with a mouse (`coineProHorizontalScroll`, `CoineProLazyRow`);
  sheets wrap their chips instead of scrolling sideways.
- One pastel chip/key family, `CoineProSwitch`, `CoineProMenuItem`, `CoineProNotedLabel`; Material's
  lavender menu surfaces replaced by the palette; dialogs wrap their content, no grab handle, light
  scrim that leaves live-preview sheets visible.
- Chart: drawn legend icons on the web, TradingView's RTL placement, taller plot, event marks off the
  axis labels, floating drawing flyout, OHLC on hover, 34 dp plates, tooltips, dated crosshair tag,
  no glow on the price tag, TradingView interval spellings (1m, 4h, 1D…), Go-to-date and auto.
- Dialogs: English Indicators dialog end to end, category column, compact rows, visible swatch
  selection, number fields and a Defaults · Cancel · OK footer, underline tabs.
- Lists: full-width desktop screener, compact desktop rows, headings aligned, FX/metal prices filled
  from the other catalogue, 429 retry in the scan, symbol search as a dialog on desktop.

## Closed in 5.19.1
The eight items listed open after 5.19.0 are closed: DIALOGS-16 (one alert editor everywhere, price
pre-filled), DIALOGS-28 (no coach marks over sheets), DIALOGS-30 (Compare search), CHART-08 (partly: one
28-viewport icon family on the toolbar, the drawing rail and three of the five side panels), CHART-10 (TradingView's tag grounds),
CHART-20 (rails on the page ground), LISTS-22 (the store's in-flight set), MOBILE-07 (no Persian-only
literal left in the pickers); and MOBILE-28 / MOBILE-29 were finished (English Rasad sentence case,
zero counts on the picker chips). Every one of the 116 items and its status is in `ITEMS.md`.

## Known fragility on the web (read before touching `ChartScreen`)
Twice in this run a change that is correct on Android made the web build's chart screen crash on its
first recomposition with `RuntimeError: array element access out of bounds` inside
`getInterfaceVTable`, reading the chart's time-zone state: a `composed {}` modifier (5.19.0, replaced
by a modifier node) and swapping the objects/explain side-panel icons to `tv_list` / `tv_sparkle`
(5.19.1, reverted). Both were found by bisecting with the unoptimised wasm. The composition of
`ChartScreen` on wasm is sensitive to changes that should be inert; every change to the chart
screen must be opened in a real browser (pro-chart.com/terminal/BTCUSDT/4h and a click on the
toolbar) before release, not only compiled.

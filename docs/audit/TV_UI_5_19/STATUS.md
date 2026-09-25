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

## Still open
- DIALOGS-16: the toolbar «Alert» button still opens the quick composer, not the full editor.
- DIALOGS-28: coach marks are not hidden while a sheet is open.
- DIALOGS-30: Compare has no search field.
- CHART-08: a few toolbar glyphs are still from the second icon family (no 28-viewport twins yet).
- CHART-20: the light theme's plot ground stays off-white (`SurfaceLadderTest` requires it).
- CHART-10: crosshair tag colour is the palette's, not TradingView's #3D3D3D.
- LISTS-22: the sparkline "pending" state is a 10-second window, not the store's in-flight set.
- MOBILE-07: about 17 Persian literals remain in `ChartPickers.kt` (chart-type picker empty states).

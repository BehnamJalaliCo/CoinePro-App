package com.coinepro.core.chart

/**
 * TradingView's chart colours, measured rather than remembered.
 *
 * Every value here was read off a screenshot of `tradingview.com/chart` rendered at a 411 × 914
 * phone viewport at 2× on 2026-09-02, dark and light — the procedure and the raw numbers are in
 * `docs/design/TRADINGVIEW_PARITY.md`. They are the current (2025) neutral palette, not the older
 * `#131722` blue-black that most clones copy from the open-source Lightweight Charts defaults:
 * the pane is a plain `#0F0F0F`, the grid is a dotted `#282828`, and the chrome is separated by
 * `#2E2E2E` hairlines.
 *
 * The candle colours are the ones every trader recognises as TradingView's — `#089981` up and
 * `#F23645` down — and they are deliberately **not** this app's own `Buy`/`Sell` pair. The owner's
 * brief is a chart that is point-for-point TradingView's; the app's semantic greens stay on every
 * screen that is not the chart.
 *
 * ARGB longs rather than Compose colours, so the datastore's built-in templates can carry the same
 * numbers without a Compose dependency.
 */
object TradingViewPalette {
    const val UP = 0xFF089981
    const val DOWN = 0xFFF23645

    /** The pane. */
    const val DARK_BACKGROUND = 0xFF0F0F0F

    /** The dotted grid, opaque: measured as the on-pixels of the dots. */
    const val DARK_GRID = 0xFF282828

    /** Axis labels. The brightest pixel of a 12 px label measured `#A6A6A6`–`#B5B5B5`. */
    const val DARK_TEXT = 0xFFB2B2B2

    /** The crosshair and its axis tags. */
    const val DARK_CROSSHAIR = 0xFF787878

    /** Hairlines between the chrome and the chart. */
    const val DARK_SEPARATOR = 0xFF2E2E2E

    /** The symbol pill in the header. */
    const val DARK_CHIP = 0xFF3D3D3D

    /** Primary text in the chrome and the legend title. */
    const val DARK_TEXT_PRIMARY = 0xFFDBDBDB

    // The phone app, light, measured off the owner's own screenshots (iPhone, 3×): a solid
    // `#D5D5D5` grid on white and near-black scale labels — darker than the web's greys.
    const val LIGHT_BACKGROUND = 0xFFFFFFFF
    const val LIGHT_GRID = 0xFFD5D5D5
    const val LIGHT_TEXT = 0xFF0F0F0F
    const val LIGHT_CROSSHAIR = 0xFF8C8C8C
    const val LIGHT_SEPARATOR = 0xFFE0E0E0
    const val LIGHT_CHIP = 0xFFEFEFEF
    const val LIGHT_TEXT_PRIMARY = 0xFF0F0F0F
}

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

    /**
     * Axis labels: `scalesProperties.textColor`, the reference's `color-cold-gray-300` (5.28.0). It
     * was `#B2B2B2`, the brightest pixel measured off an anti-aliased label; read from the chart's
     * own theme it is `#B8B8B8`.
     */
    const val DARK_TEXT = 0xFFB8B8B8

    /**
     * The crosshair: `crossHairProperties.color`, `color-cold-gray-400`, the same in both themes
     * (5.28.0). The dashes read darker than the value off a screenshot — half of every dash is
     * anti-aliased — which is where the measured `#787878` came from.
     */
    const val DARK_CROSSHAIR = 0xFF9C9C9C

    /** Hairlines between the chrome and the chart. */
    const val DARK_SEPARATOR = 0xFF2E2E2E

    /** The symbol pill in the header. */
    const val DARK_CHIP = 0xFF3D3D3D

    /** Primary text in the chrome and the legend title. */
    const val DARK_TEXT_PRIMARY = 0xFFDBDBDB

    /**
     * The trade button's purple — the ring with the lightning bolt under the live bar. Measured
     * `#8D32A9` off the phone app, the same in both themes.
     */
    const val TRADE = 0xFF8D32A9

    // The phone app, light, measured off the owner's own screenshots (iPhone, 3×): a solid
    // `#D5D5D5` grid on white and near-black scale labels — darker than the web's greys.
    const val LIGHT_BACKGROUND = 0xFFFFFFFF
    const val LIGHT_GRID = 0xFFD5D5D5
    const val LIGHT_TEXT = 0xFF0F0F0F
    const val LIGHT_CROSSHAIR = 0xFF9C9C9C

    /** `paneProperties.separatorColor` on light: `color-cold-gray-150` (5.28.0). */
    const val LIGHT_SEPARATOR = 0xFFEBEBEB
    const val LIGHT_CHIP = 0xFFEFEFEF
    const val LIGHT_TEXT_PRIMARY = 0xFF0F0F0F

    /**
     * The candles on a **white** pane: the reference's own pair, unchanged (5.28.0).
     *
     * From run Ω2 to 5.27 the light pane took these down to `#057A66` / `#D01427`, the light
     * palette's text greens, on the argument that `#089981` measures 3.3:1 on white. TradingView's
     * light theme ships `candleStyle.upColor` and `downColor` as `color-minty-green-500` and
     * `color-ripe-red-500` — the very values of [UP] and [DOWN] — and the owner asked for the chart
     * to match it. A candle is a filled shape, not a line of text, so the text-contrast floor does
     * not bind it; the darker pair stays where it does, on the figures in the lists.
     */
    const val LIGHT_UP = UP
    const val LIGHT_DOWN = DOWN
}

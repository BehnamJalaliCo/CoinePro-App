package com.coinepro.feature.free

import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ToolGroup
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.notifications.LocalPriceAlert

/**
 * What this app actually gives away, counted from the app itself.
 *
 * ### Why these numbers are read and not typed
 *
 * This screen is a marketing claim, and a marketing claim that drifts from the product is the
 * worst kind of bug: nothing fails, nobody notices, and the app quietly starts lying to the one
 * reader who came here to decide whether to trust it. If somebody adds an indicator, removes a
 * drawing tool or changes a cap, a typed number here would keep saying the old figure forever.
 *
 * So every figure below is derived from the same catalogue or the same constant the feature itself
 * reads. `FreeFactsTest` asserts each one is non-zero and that the caps are the caps — not to check
 * arithmetic, but so that a rename or a deletion breaks the build here rather than shipping a false
 * comparison.
 *
 * ### And why the competitors' numbers *are* typed
 *
 * They are somebody else's price list, they were read from the vendors' own pages on a date, and
 * they will go stale — so they carry that date, in [PRICES_CHECKED], and the screen prints it. A
 * price we cannot verify is worse than no price: quoting a rival's fee wrong is the one thing on
 * this screen that could be answered with a screenshot.
 *
 * ### Everything here is set in Persian digits, prices included
 *
 * That reads like a violation of «Latin digits for market figures» and is not. That rule exists so
 * a trader can check a number here against the same number in another terminal — a price, a
 * percentage, a volume. Nothing on this screen is one. A subscription fee, a count of indicators
 * and a rival's free-tier cap are all figures inside a sentence somebody *reads*, and setting them
 * in Latin would scatter Western numerals through a Persian paragraph for no reader's benefit.
 */
object FreeFacts {

    /** Indicators the chart offers, from the same list the indicator picker is built from. */
    val indicators: Int = ChartCatalog.INDICATORS.size

    /**
     * Drawing tools, excluding the rail's modes.
     *
     * The rail's first group is the pointer, the selection mode, the magnet and the eraser — how a
     * reader gets *out* of a tool rather than tools themselves. Counting them would inflate the
     * figure by six and the inflation would be indefensible the moment anybody looked.
     */
    val drawingTools: Int = DrawingTools.ALL.count { it.group != ToolGroup.MODES }

    /** Chart types — candles, Heikin-Ashi, Renko, Kagi, point and figure, and the rest. */
    val chartTypes: Int = ChartCatalog.CHART_TYPES.size

    /** Price alerts a reader may hold at once. */
    val alerts: Int = LocalPriceAlert.MAX_ALERTS

    /** Watchlists. Not symbols per list — lists. */
    val watchlists: Int = WatchlistStore.MAX_LISTS

    /** Saved chart layouts. */
    val layouts: Int = ChartLayoutStore.MAX_LAYOUTS

    /**
     * When the competitors' prices below were last read from their own pages.
     *
     * Printed on the screen. A comparison table with no date is a comparison table nobody can
     * check, and this one is making claims about other people's businesses.
     */
    const val PRICES_CHECKED = "۱۴۰۵/۰۶/۰۷"
}

package com.coinepro.feature.screener.model

import com.coinepro.core.chart.GrowthScan

/**
 * A growth scan the reader asked to be told about (5.17.0) — TradingView's screener alerts.
 *
 * The scan's own settings, the markets it runs over, and the markets that matched last time. The
 * last is what makes it an alert rather than a report: a market is announced when it *enters* the
 * scan, once, and not again on every pass while it stays in.
 *
 * [symbols] is fixed when the watch is made — the most liquid markets the screen was scanning —
 * because a background pass has no catalogue to rank and a scan over a thousand markets every half
 * hour is not something a phone should do on its own.
 */
data class ScanWatch(
    val id: String,
    val scanIds: Set<String>,
    val withinBars: Int,
    val minGrowth: Double?,
    /** The interval's wire code, `H4`. */
    val timeframe: String,
    val symbols: List<String>,
    val known: Set<String> = emptySet(),
) {
    /** Whether [scan] passes this watch — the same rule the screen applies. */
    fun matches(scan: GrowthScan.Scan): Boolean {
        if (scan.growth < (minGrowth ?: 0.0)) return false
        if (scanIds.isEmpty()) return true
        return scanIds.any { id ->
            val kind = GrowthScan.Kind.of(id) ?: return@any false
            (scan.barsAgo[kind] ?: return@any false) <= withinBars
        }
    }

    /** The markets in [matched] that were not in the scan last time. */
    fun entrants(matched: Set<String>): List<String> = matched.filterNot(known::contains).sorted()

    companion object {
        /** How many markets a watch scans in the background. */
        const val MAX_SYMBOLS: Int = 150
    }
}

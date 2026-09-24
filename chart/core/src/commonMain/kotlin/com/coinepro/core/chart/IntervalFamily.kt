package com.coinepro.core.chart

/**
 * The families TradingView's «Visibility on intervals» lists (5.16.1), by the bar's length in
 * seconds: under a minute, under an hour, under a day, under a week, under four weeks, and longer.
 */
enum class IntervalFamily(val id: String) {
    SECONDS("s"),
    MINUTES("m"),
    HOURS("h"),
    DAYS("d"),
    WEEKS("w"),
    MONTHS("mo"),
    ;

    companion object {
        fun ofSeconds(seconds: Long): IntervalFamily = when {
            seconds < 60 -> SECONDS
            seconds < 3_600 -> MINUTES
            seconds < 86_400 -> HOURS
            seconds < 604_800 -> DAYS
            seconds < 2_419_200 -> WEEKS
            else -> MONTHS
        }

        /** The stored spelling back — `m,h` — skipping anything a newer build wrote. */
        fun parseSet(stored: String?): Set<IntervalFamily> =
            stored.orEmpty().split(',').mapNotNull { id -> entries.firstOrNull { it.id == id } }.toSet()

        fun encodeSet(families: Set<IntervalFamily>): String =
            entries.filter { it in families }.joinToString(",") { it.id }
    }
}

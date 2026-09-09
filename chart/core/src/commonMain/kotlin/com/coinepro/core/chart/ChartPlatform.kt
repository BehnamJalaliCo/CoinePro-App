package com.coinepro.core.chart

/**
 * What the engine needs from the platform it runs on, and nothing more.
 *
 * Five things: the wall clock, a time zone, and three formatters — fixed decimals, a local
 * date-time, and the zone the device is set to. Everything else in this module is arithmetic on
 * arrays and is the same on every target. Keeping the seam this narrow is what lets the module
 * compile for the JVM (its tests), Android (the app) and, later, the web terminal without a
 * rewrite: a new target implements these five and inherits the rest.
 *
 * The `actual`s for both JVM-based targets live in `src/jvmShared` and use `java.time` and
 * `String.format`, which is exactly what the code did before it was extracted — the axis labels
 * and the drawing ratios are byte-identical to 4.47.0's.
 */

/** A time zone as the chart needs it: the UTC offset, in seconds, at one moment. */
fun interface ChartZone {
    fun offsetSeconds(epochSeconds: Long): Long

    companion object {
        /** UTC itself. Tests and the axis' fallback use it. */
        val UTC: ChartZone = ChartZone { 0L }

        /** A fixed offset — for a test, or a reader who pinned one. */
        fun fixed(offsetSeconds: Long): ChartZone = ChartZone { offsetSeconds }
    }
}

/** The device's current zone. */
expect fun systemChartZone(): ChartZone

/** Milliseconds since the epoch. Drawings fade on it; nothing is scheduled by it. */
expect fun currentTimeMillis(): Long

/**
 * [value] at exactly [decimals] places, Latin digits, a `.` and no grouping — an axis label, a
 * Fibonacci ratio. Equivalent to `String.format(Locale.US, "%.${decimals}f", value)`.
 */
expect fun formatFixed(value: Double, decimals: Int): String

/**
 * [epochSeconds] in the device's zone under [pattern] — the `java.time` pattern letters
 * (`d MMM`, `HH:mm`) with English month names, which is what the object tree prints.
 */
expect fun formatLocalMoment(epochSeconds: Long, pattern: String): String

/** A name for a drawable the UI layer owns. See `ChartIcons` in `:chart-ui` for the mapping. */
@JvmInline
value class ChartIcon(val name: String)

/**
 * The proleptic Gregorian calendar date of a day number (days since 1970-01-01), the same
 * arithmetic `java.time.LocalDate.ofEpochDay` does. Here because the time axis asks for the year
 * and month of two adjacent bars several hundred times per draw pass and must not allocate a
 * calendar object per ask, and because `java.time` is not on every target.
 */
internal data class CivilDate(val year: Int, val month: Int, val day: Int) {
    companion object {
        // Howard Hinnant's `civil_from_days`, unchanged.
        fun ofEpochDay(epochDay: Long): CivilDate {
            val z = epochDay + 719468
            val era = (if (z >= 0) z else z - 146096) / 146097
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            return CivilDate((if (m <= 2) y + 1 else y).toInt(), m, d)
        }
    }
}

package com.coinepro.core.chart

/**
 * The trading sessions, the period rules and the prior period's levels — three studies Pro-Chart's
 * terminal drew and this app did not until 5.12.0 (`scales_crosshair.js` in that repository).
 *
 * Everything here is arithmetic on bar times. The one piece of calendar knowledge is when the
 * three session cities that keep daylight saving move their clocks, and that is written out below
 * rather than borrowed from a time-zone database, because `:chart-core` runs on targets with no
 * database of its own (the browser's is behind `Intl`, and this module does not reach it).
 */
object Sessions {

    /** One of the four sessions the terminal shaded, with its local hours and its colour. */
    enum class Session(
        /** The short name printed at the band's top edge — the same on every language. */
        val short: String,
        /** Opening, in minutes after local midnight. */
        val openMinute: Int,
        /** Closing, in minutes after local midnight. */
        val closeMinute: Int,
        /** ARGB, faint on purpose: the band is behind the candles and must not compete with them. */
        val colour: Long,
    ) {
        SYDNEY("SYD", 7 * 60, 16 * 60, 0x1A8B5CF6),
        TOKYO("TYO", 9 * 60, 18 * 60, 0x1AF59E0B),
        LONDON("LDN", 8 * 60, 17 * 60, 0x1A3B82F6),
        NEW_YORK("NY", 8 * 60, 17 * 60, 0x1A22C55E),
    }

    /** The UTC offset of [session]'s city at [epochSeconds], daylight saving included. */
    fun offsetSeconds(session: Session, epochSeconds: Long): Long = when (session) {
        Session.TOKYO -> 9 * HOUR
        Session.LONDON -> if (europeanSummer(epochSeconds)) HOUR else 0L
        Session.NEW_YORK -> if (americanSummer(epochSeconds)) -4 * HOUR else -5 * HOUR
        Session.SYDNEY -> if (australianSummer(epochSeconds)) 11 * HOUR else 10 * HOUR
    }

    /**
     * Every session that overlaps the series, as shaded bands, plus the London/New York overlap in
     * a slightly stronger shade — the hours that move the majors and gold most.
     *
     * Only for bars shorter than a day: on a daily chart a session is a fraction of one candle and
     * a band would cover it entirely, which is noise. Weekend days carry no session.
     */
    fun sessionBands(series: CandleSeries): List<TimeBand> {
        if (series.size < 2) return emptyList()
        val spacing = typicalSpacing(series)
        if (spacing <= 0 || spacing >= DAY) return emptyList()
        val first = series.time.first()
        val last = series.time.last() + spacing
        val bands = ArrayList<TimeBand>()
        var day = floorDiv(first, DAY) - 1
        val lastDay = floorDiv(last, DAY) + 1
        while (day <= lastDay) {
            val windows = HashMap<Session, LongRange>()
            for (session in Session.entries) {
                // The session opens on this *local* calendar day. Its UTC moment is the local
                // midnight of that day less the city's offset at roughly that time.
                val probe = day * DAY + session.openMinute * 60L
                val offset = offsetSeconds(session, probe)
                val open = day * DAY + session.openMinute * 60L - offset
                val close = day * DAY + session.closeMinute * 60L - offset
                if (weekend(open + offset)) continue
                if (close <= first || open >= last) continue
                windows[session] = open until close
                bands += TimeBand(from = open, to = close, colour = session.colour, label = session.short)
            }
            val london = windows[Session.LONDON]
            val newYork = windows[Session.NEW_YORK]
            if (london != null && newYork != null) {
                val from = maxOf(london.first, newYork.first)
                val to = minOf(london.last + 1, newYork.last + 1)
                if (to > from) bands += TimeBand(from = from, to = to, colour = OVERLAP_COLOUR, label = null)
            }
            day++
        }
        return bands
    }

    /** Which boundary [periodSeparators] rules: the next period up from the bar length. */
    enum class Period { DAY, WEEK, MONTH, YEAR }

    /** The period a chart of [series] is separated by — day for intraday, month for daily, year above. */
    fun separatorPeriod(series: CandleSeries): Period? {
        val spacing = typicalSpacing(series)
        return when {
            spacing <= 0 -> null
            spacing < DAY -> Period.DAY
            spacing < 7 * DAY -> Period.MONTH
            else -> Period.YEAR
        }
    }

    /**
     * A vertical rule at the first bar of every new period — day, month or year, whichever suits the
     * bar length ([separatorPeriod]). In UTC: the forex day and every exchange's stamp.
     */
    fun periodSeparators(series: CandleSeries): List<TimeBand> {
        val period = separatorPeriod(series) ?: return emptyList()
        val rules = ArrayList<TimeBand>()
        for (index in 1 until series.size) {
            if (periodKey(series.time[index], period) != periodKey(series.time[index - 1], period)) {
                val t = series.time[index]
                rules += TimeBand(from = t, to = t, colour = SEPARATOR_COLOUR, label = null)
            }
        }
        return rules
    }

    /** The four lines [previousPeriodLevels] draws, in order. */
    data class PeriodLevels(val high: Line, val low: Line, val close: Line, val open: Line)

    /**
     * The previous period's high, low and close, and this period's open, drawn across every bar of
     * the period they apply to — the levels the terminal called «سقف، کف و بسته شدن دیروز».
     *
     * The period is the next one up from the bar length: yesterday on an intraday chart, last week
     * on a daily one, last month on a weekly one. The first period in the series has no previous
     * one and is left empty rather than guessed.
     */
    fun previousPeriodLevels(series: CandleSeries): PeriodLevels? {
        val spacing = typicalSpacing(series)
        if (series.size < 2 || spacing <= 0) return null
        val period = when {
            spacing < DAY -> Period.DAY
            spacing < 7 * DAY -> Period.WEEK
            else -> Period.MONTH
        }
        val size = series.size
        val high = arrayOfNulls<Double>(size)
        val low = arrayOfNulls<Double>(size)
        val close = arrayOfNulls<Double>(size)
        val open = arrayOfNulls<Double>(size)
        var key = periodKey(series.time[0], period)
        var runHigh = series.high[0]
        var runLow = series.low[0]
        var runClose = series.close[0]
        var periodOpen = series.open[0]
        var prevHigh: Double? = null
        var prevLow: Double? = null
        var prevClose: Double? = null
        for (index in 0 until size) {
            val k = periodKey(series.time[index], period)
            if (k != key) {
                prevHigh = runHigh
                prevLow = runLow
                prevClose = runClose
                key = k
                runHigh = series.high[index]
                runLow = series.low[index]
                periodOpen = series.open[index]
            } else if (index > 0) {
                runHigh = maxOf(runHigh, series.high[index])
                runLow = minOf(runLow, series.low[index])
            }
            runClose = series.close[index]
            high[index] = prevHigh
            low[index] = prevLow
            close[index] = prevClose
            open[index] = if (prevHigh != null) periodOpen else null
        }
        // Broken at each boundary, so the step from one period's level to the next is a gap and
        // not a vertical stroke through the candles.
        fun stepped(values: Array<Double?>): Line = Line.of(size) { index ->
            val boundary = index > 0 && periodKey(series.time[index], period) != periodKey(series.time[index - 1], period)
            if (boundary) null else values[index]
        }
        return PeriodLevels(stepped(high), stepped(low), stepped(close), stepped(open))
    }

    // ── calendar arithmetic ──────────────────────────────────────────────────────────────

    private const val HOUR = 3_600L
    private const val DAY = 86_400L
    private const val OVERLAP_COLOUR = 0x26E0A85CL
    private const val SEPARATOR_COLOUR = 0x40848E9CL

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
    }

    private fun floorMod(a: Long, b: Long): Long = a - floorDiv(a, b) * b

    private fun floorMod(a: Int, b: Int): Int = floorMod(a.toLong(), b.toLong()).toInt()

    /** The most common gap between bars — not the last one, which a weekend can stretch. */
    private fun typicalSpacing(series: CandleSeries): Long {
        if (series.size < 2) return 0
        val sample = minOf(series.size - 1, 50)
        val gaps = LongArray(sample) { series.time[series.size - 1 - it] - series.time[series.size - 2 - it] }
        gaps.sort()
        return gaps[sample / 2]
    }

    private fun periodKey(epochSeconds: Long, period: Period): Long {
        val day = floorDiv(epochSeconds, DAY)
        val civil = CivilDate.ofEpochDay(day)
        return when (period) {
            Period.DAY -> day
            // Weeks start on Monday; 1970-01-01 was a Thursday, so day 4 is the first Monday.
            Period.WEEK -> floorDiv(day - 4, 7)
            Period.MONTH -> civil.year * 12L + civil.month
            Period.YEAR -> civil.year.toLong()
        }
    }

    /** Saturday or Sunday at the given *local* moment. */
    private fun weekend(localSeconds: Long): Boolean {
        val weekday = floorMod(floorDiv(localSeconds, DAY) + 3, 7L) // 0 = Monday
        return weekday >= 5
    }

    /** Epoch day of the [nth] [weekday] (0 = Monday) of [month] in [year]; nth = -1 is the last. */
    private fun nthWeekday(year: Int, month: Int, weekday: Int, nth: Int): Long {
        if (nth > 0) {
            val first = epochDay(year, month, 1)
            val firstWeekday = floorMod(first + 3, 7L).toInt()
            return first + floorMod(weekday - firstWeekday, 7) + (nth - 1) * 7L
        }
        val nextMonthFirst = if (month == 12) epochDay(year + 1, 1, 1) else epochDay(year, month + 1, 1)
        val last = nextMonthFirst - 1
        val lastWeekday = floorMod(last + 3, 7L).toInt()
        return last - floorMod(lastWeekday - weekday, 7)
    }

    /** Howard Hinnant's `days_from_civil`. */
    private fun epochDay(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }

    private fun yearOf(epochSeconds: Long): Int = CivilDate.ofEpochDay(floorDiv(epochSeconds, DAY)).year

    /** EU and UK: from 01:00 UTC on the last Sunday of March to 01:00 UTC on the last Sunday of October. */
    private fun europeanSummer(epochSeconds: Long): Boolean {
        val year = yearOf(epochSeconds)
        val start = nthWeekday(year, 3, SUNDAY, -1) * DAY + HOUR
        val end = nthWeekday(year, 10, SUNDAY, -1) * DAY + HOUR
        return epochSeconds in start until end
    }

    /** US: from 02:00 local on the second Sunday of March to 02:00 local on the first Sunday of November. */
    private fun americanSummer(epochSeconds: Long): Boolean {
        val year = yearOf(epochSeconds)
        val start = nthWeekday(year, 3, SUNDAY, 2) * DAY + 2 * HOUR + 5 * HOUR
        val end = nthWeekday(year, 11, SUNDAY, 1) * DAY + 2 * HOUR + 4 * HOUR
        return epochSeconds in start until end
    }

    /** New South Wales: from 02:00 local on the first Sunday of October to 03:00 local on the first Sunday of April. */
    private fun australianSummer(epochSeconds: Long): Boolean {
        val year = yearOf(epochSeconds)
        val end = nthWeekday(year, 4, SUNDAY, 1) * DAY + 3 * HOUR - 11 * HOUR
        val start = nthWeekday(year, 10, SUNDAY, 1) * DAY + 2 * HOUR - 10 * HOUR
        return epochSeconds < end || epochSeconds >= start
    }

    private const val SUNDAY = 6
}

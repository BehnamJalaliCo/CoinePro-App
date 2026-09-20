@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.core.chart

/**
 * The five things the engine needs, on a browser.
 *
 * Nothing here decides anything: the whole module's arithmetic is in `commonMain` and identical on
 * every target, which is the point of keeping this seam at five functions. What a browser supplies
 * is a clock, its own UTC offset, and two ways of printing.
 *
 * **`Intl` is deliberately not used.** The reader's browser is set to their locale, and the three
 * things printed here — a Fibonacci ratio, an axis label, a drawing's moment — are not localised
 * text: they are Latin digits with a `.` and English month names, the same bytes `Locale.US`
 * produces on the phone (`ChartPlatform.jvm.kt` says why). Asking `Intl` for them and then fighting
 * its idea of the reader's locale would be a longer way to the same string with a Persian-digit
 * bug in it.
 */

private fun nowMillis(): Double = js("Date.now()")

/** Minutes *behind* UTC, which is what `getTimezoneOffset` returns — hence the sign flip below. */
private fun timezoneOffsetMinutes(epochMillis: Double): Double =
    js("new Date(epochMillis).getTimezoneOffset()")

/**
 * The browser's own zone, asked per moment rather than once.
 *
 * `getTimezoneOffset` is evaluated at the instant given, so a chart that spans a daylight-saving
 * change gets the offset in force at each bar — the same behaviour `ZoneId.rules.getOffset` has on
 * the phone, and the reason [ChartZone] takes a moment at all.
 */
actual fun systemChartZone(): ChartZone = ChartZone { epochSeconds ->
    -(timezoneOffsetMinutes(epochSeconds.toDouble() * 1000.0).toLong()) * 60L
}

actual fun currentTimeMillis(): Long = nowMillis().toLong()

/**
 * `toFixed` is the browser's own rounding and is locale-independent by specification: always a
 * `.`, always Latin digits, never a grouping separator. That is exactly what
 * `String.format(Locale.US, "%.${decimals}f", …)` gives, which is what the axis and the Fibonacci
 * levels are checked against.
 */
private fun toFixed(value: Double, decimals: Int): String = js("value.toFixed(decimals)")

actual fun formatFixed(value: Double, decimals: Int): String = toFixed(value, decimals)

private val MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * The `java.time` pattern letters the object tree actually uses, and no others.
 *
 * Four patterns reach here — `MMM yy`, `d MMM`, `HH:mm` and `d MMM HH:mm` (`ObjectTree`) — so this
 * reads the letters those need rather than implementing a date-time formatter. An unknown letter
 * is copied through, which makes a mistake visible in the label instead of silently printing
 * something plausible. [CivilDate] does the calendar arithmetic, in common code, so a browser and
 * a phone agree on which day a moment falls in.
 */
actual fun formatLocalMoment(epochSeconds: Long, pattern: String): String {
    val local = epochSeconds + systemChartZone().offsetSeconds(epochSeconds)
    val day = floorDiv(local, 86_400L)
    val secondOfDay = local - day * 86_400L
    val date = CivilDate.ofEpochDay(day)
    val hour = (secondOfDay / 3_600L).toInt()
    val minute = ((secondOfDay % 3_600L) / 60L).toInt()
    val second = (secondOfDay % 60L).toInt()

    val out = StringBuilder(pattern.length + 4)
    var index = 0
    while (index < pattern.length) {
        val letter = pattern[index]
        var run = 1
        while (index + run < pattern.length && pattern[index + run] == letter) run++
        when {
            letter == 'y' && run == 2 -> out.append(pad2(date.year % 100))
            letter == 'y' -> out.append(date.year)
            letter == 'M' && run >= 3 -> out.append(MONTH_NAMES[date.month - 1])
            letter == 'M' && run == 2 -> out.append(pad2(date.month))
            letter == 'M' -> out.append(date.month)
            letter == 'd' && run >= 2 -> out.append(pad2(date.day))
            letter == 'd' -> out.append(date.day)
            letter == 'H' && run >= 2 -> out.append(pad2(hour))
            letter == 'H' -> out.append(hour)
            letter == 'm' && run >= 2 -> out.append(pad2(minute))
            letter == 'm' -> out.append(minute)
            letter == 's' && run >= 2 -> out.append(pad2(second))
            letter == 's' -> out.append(second)
            else -> repeat(run) { out.append(letter) }
        }
        index += run
    }
    return out.toString()
}

private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()

/** `Long.floorDiv` with the sign behaviour the calendar needs, spelled out for every target. */
private fun floorDiv(value: Long, divisor: Long): Long {
    val quotient = value / divisor
    return if (value % divisor != 0L && (value xor divisor) < 0L) quotient - 1L else quotient
}

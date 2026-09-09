package com.coinepro.core.chart

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** [ZoneId] as the engine sees it. The rules table is walked per ask, as before. */
fun ZoneId.asChartZone(): ChartZone = ChartZone { epochSeconds ->
    rules.getOffset(Instant.ofEpochSecond(epochSeconds)).totalSeconds.toLong()
}

actual fun systemChartZone(): ChartZone = ZoneId.systemDefault().asChartZone()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/**
 * `Locale.US` is not optional. The device locale is Persian, and `%.3f` against it emits Persian
 * digits and a Persian decimal separator, which would put «۰٫۶۱۸» on a Fibonacci level.
 */
actual fun formatFixed(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)

/** [Locale.US] formats the month name too, so a Persian device reads «12 Mar», not a Persian one. */
actual fun formatLocalMoment(epochSeconds: Long, pattern: String): String =
    Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, Locale.US))

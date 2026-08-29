package com.coinepro.feature.portfolio

/**
 * A span of time broken into the three units a trader talks in.
 *
 * Days, hours and minutes and nothing below or above. Weeks are not a unit anybody quotes a holding
 * time in, and seconds stop being informative the moment the average is over a minute — «۲ روز و ۳
 * ساعت و ۱۴ دقیقه و ۹ ثانیه» is a number pretending to a precision that an average over sixty
 * trades does not have.
 */
internal data class DurationParts(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)

/**
 * Split a duration into whole days, hours, minutes and seconds.
 *
 * Pure and separate from the composable that renders it, because the rendering needs string
 * resources and a test cannot have them — and the part worth testing is the arithmetic, not the
 * Persian. A negative input is clamped to zero rather than producing negative hours: a trade that
 * closed before it opened is bad data from a server, and the honest rendering of it is "no time at
 * all" rather than a minus sign in the middle of a sentence.
 */
internal fun durationParts(totalSeconds: Long): DurationParts {
    val safe = totalSeconds.coerceAtLeast(0)
    return DurationParts(
        days = safe / 86_400,
        hours = safe % 86_400 / 3_600,
        minutes = safe % 3_600 / 60,
        seconds = safe % 60,
    )
}

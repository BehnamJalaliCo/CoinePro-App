package com.coinepro.core.common

import java.time.DayOfWeek
import java.time.ZoneId

/**
 * The day a week starts on, which is a regional fact and not a property of the offset.
 *
 * Iran's week starts on Saturday, as it does across the Gulf. A week that opened on Monday would
 * put the two quietest days of an Iranian week — Thursday and Friday — in the middle of it rather
 * than at its end, which is wrong for a weekly bar and wrong for a «هفته‌ی من» that is supposed to
 * be *the reader's* week.
 *
 * ### Why it lives here
 *
 * It was in `core:marketdata`, private, serving the weekly candle alone. The moment a second thing
 * needed a week — the reader's own — the choice was a second copy or a shared one, and a second
 * copy of a boundary is the copy that gets corrected alone. This module already owns the calendar:
 * [JalaliDate] knows the Persian weekday names and [PersianDateTime] formats them.
 *
 * ### The zone is the closest thing to a region this app has
 *
 * There is no locale switch for this and there should not be: a reader's *calendar* follows where
 * they are, not what language they read the app in, and somebody reading the English build in
 * Tehran still has an Iranian week. The zone is what every caller already passes.
 */
object WeekStart {

    /** The day the week starts on in [zone]. */
    fun of(zone: ZoneId): DayOfWeek =
        if (zone.id in SATURDAY_ZONES) DayOfWeek.SATURDAY else DayOfWeek.MONDAY

    /**
     * The zones whose week begins on Saturday.
     *
     * A list rather than a rule, because there is no rule: it is which countries decided what, and
     * a list that is wrong about one of them is wrong about that one only. Iran and Afghanistan
     * plus the Gulf states, which is the set this product's readers are actually in.
     */
    val SATURDAY_ZONES: Set<String> = setOf(
        "Asia/Tehran",
        "Asia/Kabul",
        "Asia/Baghdad",
        "Asia/Bahrain",
        "Asia/Dubai",
        "Asia/Kuwait",
        "Asia/Muscat",
        "Asia/Qatar",
        "Asia/Riyadh",
    )
}

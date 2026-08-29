package com.coinepro.core.notifications

/**
 * How often one alert may fire, in terms of bars rather than of wall-clock time.
 *
 * ### Why this exists beside [AlertRepeat]
 *
 * [AlertRepeat] answers the question a price-tick evaluator can answer: never again, not for a day,
 * not for fifteen minutes. It knows nothing about candles, because the evaluator that uses it knows
 * nothing about candles either.
 *
 * A reader looking at a chart does not think in fifteen-minute cooldowns. They think in bars, and
 * they think in *closes* — because the whole discipline of reading a chart is the difference
 * between what a candle is doing and what it finally did. This is the policy for the evaluator that
 * has a timeframe in hand, and it is the one that belongs on any alert made from the chart.
 *
 * ### [ONCE_PER_BAR_CLOSE] is the entry the rest of this is for
 *
 * Price wicks through a level and comes back. On [ONCE_PER_BAR] that is a notification, and the
 * candle that finally closes says nothing happened. On [ONCE_PER_BAR_CLOSE] there is no
 * notification, because nothing that survived the bar happened. Traders who ask for "close only"
 * are asking to not be woken by wicks, and an app that offers the setting and then fires mid-bar
 * anyway has broken the one promise the setting makes. That is why [shouldFire] takes `barClosed`
 * as its own parameter rather than inferring it from a clock: inferring it is how it gets wrong.
 */
enum class AlertFrequency(val id: String) {
    /** Fires the first time and never again. What most people mean, and the safe default. */
    ONCE("once"),

    /** At most once inside any one bar, the moment the condition first holds. */
    ONCE_PER_BAR("once_per_bar"),

    /** At most once per bar, and only on a bar that has closed. Never mid-bar. */
    ONCE_PER_BAR_CLOSE("once_per_bar_close"),

    /** Every evaluation on which the condition holds. Loud by design; the reader asked for it. */
    EVERY_TIME("every_time"),
    ;

    /**
     * Whether the frequency policy permits a firing now.
     *
     * This says nothing about whether the *condition* holds — that is [AlertTrigger.evaluate]'s
     * job, and keeping the two apart is what makes both testable. Both must say yes.
     *
     * Pure and total, and every input is a parameter including the clock, so "does close-only fire
     * mid-bar" is a unit test rather than an hour of waiting. [barStart] is the opening timestamp of
     * the bar the sample belongs to, and [barClosed] is whether that bar has finished — the caller
     * knows both, because it is the caller that assembled the bar.
     *
     * A [lastFiredAt] in the future is treated as never fired. Device clocks move backwards — a
     * manual change, a network sync after a flat battery — and an alert that silently stopped
     * working until the calendar caught up would be indistinguishable from an alert that is broken.
     */
    fun shouldFire(now: Long, lastFiredAt: Long?, barStart: Long, barClosed: Boolean): Boolean {
        val last = lastFiredAt?.takeIf { it <= now }
        return when (this) {
            ONCE -> last == null
            ONCE_PER_BAR -> last == null || last < barStart
            ONCE_PER_BAR_CLOSE -> barClosed && (last == null || last < barStart)
            EVERY_TIME -> true
        }
    }

    companion object {
        fun fromId(id: String?): AlertFrequency? = entries.firstOrNull { it.id == id }
    }
}

package com.coinepro.core.symbols

import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneOffset

/**
 * Whether a market is open, decided on the device.
 *
 * The client owns this rather than the server, which is the opposite of the usual rule and is
 * deliberate. CoinePro-FX's `market_open` flag is a single boolean for a feed that carries several
 * asset classes with different calendars, so it read "open" on a Saturday whenever the crypto side
 * was up — and a Saturday gold price labelled live is worse than no label at all.
 *
 * The weekend is the one part of the calendar that is fully knowable offline: forex trades from
 * Sunday evening to Friday evening, everywhere, every week. So the client decides the weekend and
 * the server is still believed about everything it knows better — a holiday, a halt, a broker
 * outage — through [serverOpen].
 *
 * Holidays are not modelled. There is no offline calendar of them worth shipping, they move, and
 * getting one wrong would close a market that is trading. That is the server's to report.
 */
object MarketHours {

    /**
     * The standard forex week, in UTC: open from Sunday 22:00, closed from Friday 22:00.
     *
     * Those two boundaries are the New York close, which is what every broker's week turns on. They
     * shift an hour with US daylight saving; that hour is not modelled, because being wrong about
     * whether the market opened at 21:00 or 22:00 on one Sunday evening costs nothing next to being
     * wrong about all of Saturday.
     */
    fun isForexOpen(clock: Clock = Clock.systemUTC()): Boolean {
        val now = clock.instant().atZone(ZoneOffset.UTC)
        return when (now.dayOfWeek) {
            DayOfWeek.SATURDAY -> false
            DayOfWeek.FRIDAY -> now.hour < CLOSE_HOUR_UTC
            DayOfWeek.SUNDAY -> now.hour >= OPEN_HOUR_UTC
            else -> true
        }
    }

    /**
     * The status of one market.
     *
     * @param serverOpen what the backend says, when it says anything. Believed only inside the
     *   trading week — a server claiming "open" on a Saturday is the bug this function exists for.
     */
    fun statusOf(
        symbol: String,
        serverOpen: Boolean? = null,
        clock: Clock = Clock.systemUTC(),
    ): MarketStatus = statusOf(SymbolClassifier.classify(symbol), serverOpen, clock)

    fun statusOf(
        meta: SymbolMeta,
        serverOpen: Boolean? = null,
        clock: Clock = Clock.systemUTC(),
    ): MarketStatus = when {
        meta.category == SymbolCategory.CRYPTO -> MarketStatus(open = true, weekend = false)
        !isForexOpen(clock) -> MarketStatus(open = false, weekend = true)
        serverOpen == false -> MarketStatus(open = false, weekend = false)
        else -> MarketStatus(open = true, weekend = false)
    }

    private const val OPEN_HOUR_UTC = 22
    private const val CLOSE_HOUR_UTC = 22
}

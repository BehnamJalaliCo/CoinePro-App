package com.coinepro.core.signals

import java.util.Locale

/**
 * **On the forex side this product publishes one thing: the gold signal** (run Ψ).
 *
 * ### What changed, and why it is a rule rather than a filter somebody typed
 *
 * The forex half of Pro Chart used to be an account: introducing brokers, a MetaTrader link, and a
 * copy service that mirrored approved calls onto it. None of that exists any more. What is left on
 * that side is a chart a reader can study, alerts they can set, and **one published call — gold**.
 *
 * So the signal list has to agree with the product. A reader who opens it expecting the thing they
 * were told about, and finds a EURUSD call from some earlier configuration of the desk, has been
 * shown something the app cannot stand behind; a reader who finds nothing has been shown a screen
 * that looks broken. This decides which of the two happens, in one place, with a test on it.
 *
 * ### Why the client and not the server
 *
 * Because the client is where the promise is made. The list route answers `market=forex` and
 * returns what the desk has published under that word — which is the desk's business and may well
 * be wider than this product's. Narrowing here is the app keeping its own copy honest, and it costs
 * nothing when the two already agree: [withheld] is zero on every response that was gold-only, which
 * is what makes the disagreement visible rather than silent the day there is one.
 *
 * Crypto is untouched. It is a venue with hundreds of markets and the desk calls what it calls.
 */
object ForexSignalScope {

    /**
     * Gold's base, as every feed in this product spells it.
     *
     * `XAUUSD` on CoinePro-FX, `XAU/USD` and `XAUUSD.m` at various brokers — the base is the stable
     * part and the quote and the suffix are not, so the test is on the prefix after the separators
     * come out. Silver is deliberately **not** here: `XAG` was in the bundled symbol list because
     * the chart carries it, and a chart is not a call.
     */
    const val GOLD_BASE: String = "XAU"

    /** Whether a symbol names gold, whatever the feed's punctuation and suffix. */
    fun isGold(symbol: String): Boolean =
        symbol.uppercase(Locale.US).filter(Char::isLetterOrDigit).startsWith(GOLD_BASE)

    /**
     * The signals this product will show for [market].
     *
     * Identity on crypto — no copy, no allocation, nothing to decide. On forex, gold only.
     */
    fun scoped(market: SignalMarketFilter, items: List<TradingSignal>): List<TradingSignal> =
        if (market == SignalMarketFilter.FOREX) items.filter { isGold(it.symbol) } else items

    /**
     * How many of [items] this product will not show for [market].
     *
     * Reported rather than swallowed, and carried onto [SignalsState.withheld] so it is countable
     * from a test and from the screen. A number that is ever non-zero means the desk and the app
     * disagree about what the forex side is, and that is a conversation to have with the desk
     * rather than a bug to hunt in the client.
     */
    fun withheld(market: SignalMarketFilter, items: List<TradingSignal>): Int =
        items.size - scoped(market, items).size
}

package com.coinepro.core.notifications

/**
 * What a price alert can watch for.
 *
 * Six conditions, and the split between the first two and the rest is the whole design. Above and
 * below are what an app offers when it assumes the reader has a number in mind. The four after them
 * are what people actually think in — "tell me if it moves five percent" — and every serious app in
 * this market ships them: Binance has *Change is over* and *24H change is over*, TradingView has
 * *Moving Up %*, Binance.US calls it *Target Percent*. An alert screen without them makes the
 * reader do arithmetic before they can ask a question.
 */
enum class LocalAlertCondition(val id: String) {
    /** The price rises to or past [LocalPriceAlert.value]. */
    ABOVE("above"),

    /** The price falls to or past it. */
    BELOW("below"),

    /** It rises [LocalPriceAlert.value] percent from where it was when the alert was made. */
    PERCENT_UP("percent_up"),

    /** It falls that far from the same reference. */
    PERCENT_DOWN("percent_down"),

    /** The **24-hour** change rises past that many percent. Independent of when the alert was made. */
    CHANGE_24H_OVER("change_24h_over"),

    /** The 24-hour change falls past minus that many percent. */
    CHANGE_24H_UNDER("change_24h_under"),
    ;

    /** Whether the number the reader types is a percentage rather than a price. */
    val isPercent: Boolean
        get() = this != ABOVE && this != BELOW

    companion object {
        fun fromId(id: String?): LocalAlertCondition? = entries.firstOrNull { it.id == id }
    }
}

/**
 * How often one alert may fire.
 *
 * **Not optional, and shipping without it is a bug rather than a missing feature.** An "above
 * 65,000" alert with no repeat policy fires on every tick that crosses the line, which around a
 * threshold is dozens of notifications in a minute. Binance.US words the same three choices as
 * *one-time / once per day / always*; TradingView's are *Only Once / Once Per Bar / Every Time*.
 */
enum class AlertRepeat(val id: String) {
    /** Fires once, then deactivates itself. The default, and what most people mean. */
    ONCE("once"),

    /** At most once in any twenty-four hours. */
    DAILY("daily"),

    /** Every time the condition becomes true again, with [LocalPriceAlert.COOLDOWN_MILLIS] between. */
    ALWAYS("always"),
    ;

    companion object {
        fun fromId(id: String?): AlertRepeat? = entries.firstOrNull { it.id == id }
    }
}

/**
 * An alert that lives on this phone.
 *
 * ### Why local, when the server has an alerts API
 *
 * Because the server's needs an account, and this app's first screen does not.
 *
 * That turns out to be a genuine difference rather than a shortcut. Of the seven trading apps this
 * design was measured against — Binance, Bybit, Coinbase, OKX, Kraken, eToro, TradingView — **not
 * one lets somebody set a price alert without signing in**, and neither do CoinGecko or
 * CoinMarketCap. A reader who has just installed CoinePro, has no account, and wants to be told
 * when Bitcoin reaches a number is asking for the most reasonable thing in the product, and
 * everybody in this market answers it with a sign-up form.
 *
 * So: evaluated on the device, against the same public feed the guest home already polls, stored in
 * the same preferences file as the watchlist. No identity, no round trip, and it works on the first
 * screen of a fresh install.
 *
 * ### What it cannot do, said plainly
 *
 * A phone is not a server. This fires while the app is open, and otherwise when Android next runs
 * the app's periodic work — which the platform schedules at its own convenience and not more often
 * than every fifteen minutes. A move that happens and reverses inside that window can be missed.
 * The screen says so. The server's alerts, once there is an account, do not have that limit, and
 * the app says that too rather than letting somebody discover it during a move that mattered.
 */
data class LocalPriceAlert(
    val id: String,
    val symbol: String,
    val condition: LocalAlertCondition,
    /** A price for [LocalAlertCondition.ABOVE]/[LocalAlertCondition.BELOW], a percentage otherwise. */
    val value: Double,
    val repeat: AlertRepeat = AlertRepeat.ONCE,
    /**
     * The price when the alert was created.
     *
     * Only the percent-from-creation conditions use it, and they cannot work without it: "up 5%"
     * has no meaning until it is 5% *from something*. Captured once and never updated, so an alert
     * does not quietly re-base itself every time the screen is opened.
     */
    val referencePrice: Double? = null,
    val active: Boolean = true,
    val createdAtEpochMillis: Long = 0L,
    val lastFiredAtEpochMillis: Long? = null,
) {
    companion object {
        /** Between two firings of an [AlertRepeat.ALWAYS] alert. Long enough not to be a stream. */
        const val COOLDOWN_MILLIS = 15 * 60 * 1000L

        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /**
         * How many one phone may hold.
         *
         * A cap because every one of them is evaluated on every price tick, and because a list
         * nobody can read is a list nobody manages. Binance allows fifty across all pairs and ten
         * per pair; this is the same order of magnitude, chosen for the same reason.
         */
        const val MAX_ALERTS = 40

        /**
         * Whether this alert should fire now.
         *
         * Pure, and every input is a parameter — including the clock — so the repeat rules can be
         * tested at their boundaries rather than by waiting a day. It is also why this is a
         * companion function rather than a method: the evaluation is the part that must be right,
         * and it should be readable in one place with nothing else in scope.
         */
        fun due(
            alert: LocalPriceAlert,
            price: Double,
            /** The feed's own 24-hour percentage, or null where it sent none. */
            changePercent24h: Double?,
            nowEpochMillis: Long,
        ): Boolean {
            if (!alert.active) return false
            if (!alert.repeatAllows(nowEpochMillis)) return false
            return when (alert.condition) {
                LocalAlertCondition.ABOVE -> price >= alert.value
                LocalAlertCondition.BELOW -> price <= alert.value
                LocalAlertCondition.PERCENT_UP -> {
                    val from = alert.referencePrice ?: return false
                    if (from <= 0.0) return false
                    (price - from) / from * 100.0 >= alert.value
                }
                LocalAlertCondition.PERCENT_DOWN -> {
                    val from = alert.referencePrice ?: return false
                    if (from <= 0.0) return false
                    (from - price) / from * 100.0 >= alert.value
                }
                // Null is not zero. A feed that sent no 24-hour figure has not told us the market
                // is flat, and treating the absence as a reading would fire every "fell 5%" alert
                // the moment a quote arrived without one.
                LocalAlertCondition.CHANGE_24H_OVER -> (changePercent24h ?: return false) >= alert.value
                LocalAlertCondition.CHANGE_24H_UNDER -> (changePercent24h ?: return false) <= -alert.value
            }
        }
    }

    private fun repeatAllows(nowEpochMillis: Long): Boolean {
        val last = lastFiredAtEpochMillis ?: return true
        return when (repeat) {
            AlertRepeat.ONCE -> false
            AlertRepeat.DAILY -> nowEpochMillis - last >= DAY_MILLIS
            AlertRepeat.ALWAYS -> nowEpochMillis - last >= COOLDOWN_MILLIS
        }
    }

    /** The alert after it has fired: stamped, and deactivated where it was a one-shot. */
    fun fired(atEpochMillis: Long): LocalPriceAlert = copy(
        lastFiredAtEpochMillis = atEpochMillis,
        active = repeat != AlertRepeat.ONCE,
    )
}

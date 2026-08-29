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
    /**
     * The richer condition, where the reader chose one.
     *
     * Null means the alert is one of the six flat [condition]s and nothing has changed for it. Both
     * live side by side rather than one replacing the other, because every alert already on a phone
     * is a [condition] and rewriting them all during an upgrade — silently, with no way to check
     * the result — is not a migration anybody should run on somebody else's alerts. Where this is
     * present it wins; see the [due] overload that takes a previous sample.
     */
    val trigger: AlertTrigger? = null,
    /**
     * What the alert is about, where that is more than the one [symbol].
     *
     * Null means [symbol] and nothing else, which is what every stored alert written before
     * watchlist alerts existed means. Read it through [effectiveScope] rather than directly.
     */
    val scope: AlertScope? = null,
    /**
     * The bar-aware repeat policy, for an alert made from a chart.
     *
     * Null means this alert has none and the older [repeat] governs it. The two are not merged
     * because they answer in different units — [repeat] in wall-clock time, [frequency] in bars —
     * and mapping "once a day" onto a bar policy would be inventing an answer the reader never
     * gave. An alert that has a [frequency] is evaluated by the chart-aware evaluator, which knows
     * the timeframe; one that does not is evaluated on price ticks.
     */
    val frequency: AlertFrequency? = null,
    /**
     * When this alert stops being evaluated, or **null for never** — and null is the default.
     *
     * Stated plainly because the industry default is the opposite and readers have been burned by
     * it: TradingView's free tier expires an alert after roughly a month, so an alert set for a
     * level the market might reach next quarter quietly stops existing before it gets there, and
     * the reader finds out by not being told. Nothing here expires anything on its own. An alert
     * with no expiry is evaluated until the reader deletes it, and the only expiry that can exist
     * is one they typed themselves.
     */
    val expiresAt: Long? = null,
    /** How this one alert may reach the reader. Per alert, not per app; see [AlertChannel]. */
    val channels: Set<AlertChannel> = AlertChannel.DEFAULTS,
    /** How loud its own sound is, independently of the app's other notifications. See [AlertSound]. */
    val soundLevel: Float = AlertSound.DEFAULT_LEVEL,
    /**
     * The reader's own wording, with `{symbol}`, `{price}`, `{time}` and `{tf}` filled in.
     *
     * Null for the app's own wording. Rendered by [AlertMessageTemplate.render], never by string
     * concatenation at a call site — the digits have to be Latin and there is one place that
     * guarantees it.
     */
    val message: String? = null,
) {

    /**
     * The symbols this alert covers, resolved now rather than when it was made.
     *
     * A watchlist alert whose list has gained a symbol covers it from this call onwards, which is
     * the whole promise of [AlertScope.Watchlist]. An alert with no scope is its own [symbol].
     */
    fun symbols(membersOf: (String) -> List<String>): List<String> =
        effectiveScope.resolve(membersOf)

    /** The scope as written, or the single [symbol] for every alert stored before scopes existed. */
    val effectiveScope: AlertScope
        get() = scope ?: AlertScope.Symbol(symbol)

    /** [soundLevel] clamped into range, so a corrupt stored value cannot reach an audio player. */
    val effectiveSoundLevel: Float
        get() = AlertSound.coerce(soundLevel)

    /**
     * Whether the reader's own expiry has passed.
     *
     * False whenever [expiresAt] is null, which is the default and the common case. Inclusive at
     * the instant itself: an alert set to expire at nine o'clock does not fire at nine o'clock.
     */
    fun hasExpired(nowEpochMillis: Long): Boolean =
        expiresAt != null && nowEpochMillis >= expiresAt

    companion object {
        /** Between two firings of an [AlertRepeat.ALWAYS] alert. Long enough not to be a stream. */
        const val COOLDOWN_MILLIS = 15 * 60 * 1000L

        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /**
         * How many one phone may hold.
         *
         * ### Why there is a cap at all
         *
         * Not because two hundred alerts are hard to evaluate — they are not; the whole evaluation
         * is arithmetic over a list. The cap is about **storage**. These live in one delimited
         * string inside a preferences file, which is read whole, parsed whole and written whole on
         * every change. That is the right shape for a feature that has to work on the first screen
         * of a fresh install with no account and no database, and it is the wrong shape for
         * unbounded growth: past a few hundred rows the parse starts to show up on a cold start,
         * and a single half-written write loses more than anybody should lose at once.
         *
         * ### Why forty was too few
         *
         * Forty was chosen against Binance's fifty. It is the wrong comparison, because Binance's
         * alerts are one-per-instrument prices and these are not: a reader with a watchlist alert,
         * a channel on each of half a dozen instruments and a handful of indicator conditions
         * reaches forty without doing anything unusual, and then the app tells them their list is
         * full. Two hundred is the number at which the preference is still comfortably small and at
         * which no reader running out of alerts is doing anything a trading app should refuse.
         *
         * The day this needs to be larger is the day it needs to be a database rather than a
         * bigger number here.
         */
        const val MAX_ALERTS = 200

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
            if (alert.hasExpired(nowEpochMillis)) return false
            if (!alert.repeatAllows(nowEpochMillis)) return false
            return conditionMet(alert, price, changePercent24h)
        }

        /**
         * The same question, for a caller that has the previous sample and the recent closes.
         *
         * This is the overload the chart-aware evaluator uses, and it is the only one that can
         * answer a crossing, a channel transition, a multi-bar move or a drawing touch — all of
         * which need to know where the price came from. Where the alert has no [trigger] it falls
         * straight through to the flat [condition], so one evaluator can drive both kinds.
         *
         * [barStart] and [barClosed] describe the bar this sample belongs to and are used only when
         * the alert has a [frequency]; an alert without one is governed by [repeat] as before. Pure,
         * like its sibling: every input including the clock is a parameter.
         */
        fun due(
            alert: LocalPriceAlert,
            previous: Double?,
            price: Double,
            series: DoubleArray?,
            changePercent24h: Double?,
            nowEpochMillis: Long,
            barStart: Long,
            barClosed: Boolean,
        ): Boolean {
            if (!alert.active) return false
            if (alert.hasExpired(nowEpochMillis)) return false
            val frequency = alert.frequency
            val allowed = if (frequency == null) {
                alert.repeatAllows(nowEpochMillis)
            } else {
                frequency.shouldFire(
                    now = nowEpochMillis,
                    lastFiredAt = alert.lastFiredAtEpochMillis,
                    barStart = barStart,
                    barClosed = barClosed,
                )
            }
            if (!allowed) return false
            val trigger = alert.trigger
                ?: return conditionMet(alert, price, changePercent24h)
            return trigger.evaluate(previous, price, series)
        }

        /**
         * The flat [LocalAlertCondition] half, split out so both overloads share one copy.
         *
         * Separate from the repeat and expiry rules on purpose: this answers only "is the condition
         * true", which is the part a screen may also want in order to warn that an alert would fire
         * the moment it is created.
         */
        fun conditionMet(
            alert: LocalPriceAlert,
            price: Double,
            changePercent24h: Double?,
        ): Boolean {
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

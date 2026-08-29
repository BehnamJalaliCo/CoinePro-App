package com.coinepro.core.notifications

/**
 * Everything this app can interrupt somebody about, as one closed list.
 *
 * ### Why a category enum rather than a pile of booleans
 *
 * A trading app's notifications are not one thing. "A signal was published" and "your copy trade
 * filled" and "Bitcoin passed the number you asked about" are three different promises, and a
 * reader who wants the third and not the first has no way to say so if the app offers one switch.
 * Every serious app in this market — Binance, OKX, Bybit, Kraken, Coinbase — splits them, and the
 * splits agree with each other to a striking degree: market alerts, trading events, security,
 * marketing, news. This list is that consensus, with the two categories this product actually has
 * that they do not (copy trading, and an AI that finds setups) added to it.
 *
 * ### Each one is also an Android notification channel
 *
 * That is the part worth understanding, because it is what makes this more than a settings screen.
 * A channel is the operating system's own per-category control: long-press a notification and the
 * reader can silence *that kind* without opening this app, give it its own sound, or let it through
 * Do Not Disturb. Building our own sound picker — OKX ships five sounds — would be re-implementing,
 * worse, something the platform already does better and that people already know how to use.
 *
 * So the in-app switch answers "send me this at all", and the channel answers "and how loudly".
 *
 * ### [server] is where the honesty is
 *
 * Both backends accept exactly three flags: new signals, signal updates, price alerts. A category
 * that maps to one of them can be turned off *at the server*, which is the only kind of "off" that
 * saves the reader's battery and data — nothing is sent at all. Everything else can only be
 * filtered when it arrives, which is a real difference and is stated in the UI rather than hidden:
 * the message is delivered to the phone and dropped before it is shown.
 */
enum class NotificationCategory(
    /** Stable key for storage and for the Android channel id. Never localise or reuse. */
    val id: String,
    /** Which of the server's three flags silences this at the source, or null for none. */
    val server: ServerSwitch?,
    /** Whether it starts on. Anything that costs money or misses an opportunity does. */
    val defaultOn: Boolean,
    /** Whether an account is needed for this to ever fire. */
    val needsAccount: Boolean,
) {
    /* ---------------------------------------------------------------- signals */

    /** A new signal was published. The reason most readers install this app. */
    NEW_SIGNAL("new_signal", ServerSwitch.NEW_SIGNALS, defaultOn = true, needsAccount = true),

    /** A target was reached — the good news, and the one people most want to be told. */
    TARGET_HIT("target_hit", ServerSwitch.SIGNAL_UPDATES, defaultOn = true, needsAccount = true),

    /**
     * A stop was hit.
     *
     * On by default, and it should stay that way. It is the unwelcome half of the same event, and
     * an app that tells you only about the wins is not reporting, it is advertising.
     */
    STOP_HIT("stop_hit", ServerSwitch.SIGNAL_UPDATES, defaultOn = true, needsAccount = true),

    /** A signal closed, whatever it did. */
    SIGNAL_CLOSED("signal_closed", ServerSwitch.SIGNAL_UPDATES, defaultOn = true, needsAccount = true),

    /* ----------------------------------------------------------- copy trading */

    /** A copy order was placed on the reader's account. Money moved; they are told. */
    COPY_OPENED("copy_opened", null, defaultOn = true, needsAccount = true),

    /** A copied position closed. */
    COPY_CLOSED("copy_closed", null, defaultOn = true, needsAccount = true),

    /**
     * A copy could not be placed.
     *
     * The most important notification in this list and the least pleasant to receive: an exchange
     * key that expired or a balance too small means the reader is *not* in a trade they believe
     * they are in. Silencing it is offered, because it is theirs to silence, and it is the one
     * switch the screen argues against.
     */
    COPY_FAILED("copy_failed", null, defaultOn = true, needsAccount = true),

    /* ----------------------------------------------------------------- market */

    /** A price alert the reader created reached its condition. */
    PRICE_ALERT("price_alert", ServerSwitch.PRICE_ALERTS, defaultOn = true, needsAccount = false),

    /**
     * An unusual move on a market the reader stars, with no threshold to choose.
     *
     * Kraken's idea and the best one in this whole area: the reader does not have to know what
     * number to watch for, they only have to say which markets they care about. Starring a symbol
     * *is* the subscription.
     */
    WATCHLIST_MOVE("watchlist_move", null, defaultOn = true, needsAccount = false),

    /** Headlines the server marked important. Off by default: news is a stream, not an event. */
    NEWS("news", null, defaultOn = false, needsAccount = false),

    /**
     * Something the service itself announced — an outage, a release, a new market, a change to how
     * membership works.
     *
     * **On by default, unlike [NEWS], and the contrast is the whole point.** News is a stream a
     * reader chooses to follow; an announcement is addressed to this reader about the service they
     * are using. The durable list at the announcements route is there for whoever misses the push,
     * which is why the server built it as its own route rather than a flag on the news feed.
     */
    ANNOUNCEMENT("announcement", ServerSwitch.ANNOUNCEMENTS, defaultOn = true, needsAccount = false),

    /** A high-importance economic release is due. Off by default for the same reason. */
    CALENDAR("calendar", null, defaultOn = false, needsAccount = false),

    /* --------------------------------------------------------------------- ai */

    /** The AI found a setup worth looking at. */
    AI_SETUP("ai_setup", null, defaultOn = true, needsAccount = true),

    /* ---------------------------------------------------------------- account */

    /**
     * A sign-in, a password change, a new device.
     *
     * Cannot be switched off, and the screen says so rather than showing a switch that does
     * nothing. Every serious platform in this market treats security mail the same way, for the
     * obvious reason: the person who wants these silenced is usually not the account's owner.
     */
    SECURITY("security", null, defaultOn = true, needsAccount = true),

    /** Membership, verification, subscription — the state of the reader's standing. */
    ACCOUNT("account", null, defaultOn = true, needsAccount = true),

    /* -------------------------------------------------------------- marketing */

    /**
     * Offers and announcements. **Off by default and separated from everything else.**
     *
     * This is the switch people go looking for, and burying it inside "other" is how an app loses
     * the reader's trust in the whole screen. It is first-class here and it starts off.
     */
    MARKETING("marketing", null, defaultOn = false, needsAccount = false),
    ;

    /** Whether the reader is allowed to turn this off at all. */
    val silenceable: Boolean get() = this != SECURITY

    companion object {
        fun fromId(id: String?): NotificationCategory? = entries.firstOrNull { it.id == id }

        /**
         * Which category a push belongs to, from the `kind` the server stamps on it.
         *
         * Unknown kinds map to null and an unknown kind is **shown**, never dropped. A server that
         * adds a category the app has not heard of must not have its message silently swallowed —
         * the failure would be invisible on both ends, and the first thing lost would be whatever
         * was new enough to be worth announcing.
         */
        fun forKind(kind: String?): NotificationCategory? = when (kind?.trim()?.lowercase()) {
            "signal", "new_signal", "signal_new" -> NEW_SIGNAL
            "tp", "target", "target_hit", "take_profit" -> TARGET_HIT
            "sl", "stop", "stop_hit", "stop_loss" -> STOP_HIT
            "signal_closed", "closed" -> SIGNAL_CLOSED
            "copy", "copy_opened", "copy_filled" -> COPY_OPENED
            "copy_closed" -> COPY_CLOSED
            "copy_failed", "copy_error" -> COPY_FAILED
            "alert", "price_alert" -> PRICE_ALERT
            "watchlist", "watchlist_move", "volatility" -> WATCHLIST_MOVE
            "news" -> NEWS
            "announcement", "announcements", "notice" -> ANNOUNCEMENT
            "calendar", "economic" -> CALENDAR
            "ai", "ai_setup", "setup" -> AI_SETUP
            "security", "login" -> SECURITY
            "account", "membership", "kyc", "subscription" -> ACCOUNT
            "marketing", "promo", "campaign" -> MARKETING
            else -> null
        }
    }
}

/**
 * The flags both backends accept. See [NotificationCategory.server].
 *
 * [ANNOUNCEMENTS] is TradeYar's and arrived with the announcements route. CoinePro-FX ignores an
 * unknown key rather than rejecting the whole document, so it is sent to both.
 */
enum class ServerSwitch { NEW_SIGNALS, SIGNAL_UPDATES, PRICE_ALERTS, ANNOUNCEMENTS }

/**
 * Hours in which nothing is shown.
 *
 * **Not copied from anywhere, and that is the point.** Of the seven apps this design was measured
 * against, not one offers quiet hours — they all point at the operating system's Do Not Disturb
 * instead. That is a reasonable answer in a market where the reader is awake when their market is,
 * and a poor one here: a Persian reader following the crypto market is being notified through the
 * night by a market that never closes, and telling them to go and configure a system-wide setting
 * is telling them to silence their alarm clock too.
 *
 * [from] and [to] are minutes since local midnight. A window that wraps past midnight is the
 * ordinary case rather than the edge one — 23:00 to 07:00 is what somebody will actually pick — so
 * it is handled first in [contains] rather than bolted on.
 */
data class QuietHours(
    val enabled: Boolean = false,
    val fromMinuteOfDay: Int = 23 * 60,
    val toMinuteOfDay: Int = 7 * 60,
    /**
     * Categories that come through anyway.
     *
     * Money that moved while the reader slept is not something to hold until morning: a copy trade
     * that filled, a copy that failed, and anything about the security of the account are worth
     * waking somebody for, and everything else is not.
     */
    val alwaysThrough: Set<NotificationCategory> = setOf(
        NotificationCategory.COPY_OPENED,
        NotificationCategory.COPY_FAILED,
        NotificationCategory.SECURITY,
    ),
) {
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled) return false
        if (fromMinuteOfDay == toMinuteOfDay) return false
        return if (fromMinuteOfDay < toMinuteOfDay) {
            minuteOfDay >= fromMinuteOfDay && minuteOfDay < toMinuteOfDay
        } else {
            // Wraps midnight: inside means "after the start OR before the end".
            minuteOfDay >= fromMinuteOfDay || minuteOfDay < toMinuteOfDay
        }
    }
}

/**
 * What the reader has decided about being interrupted.
 *
 * Kept on the device rather than on a server, because most of it has no server to keep it on: two
 * of the sixteen categories map to a backend flag and the rest are the app's own. Sending the whole
 * set to one of two backends would also mean a reader who switches platform finds their choices
 * half-applied, which is worse than local and honest.
 */
data class NotificationSettings(
    /** The master switch. Off silences everything except what cannot be silenced. */
    val enabled: Boolean = true,
    /** Silenced until this instant, or null. The pause that is not a delete. */
    val mutedUntilEpochMillis: Long? = null,
    val categories: Map<NotificationCategory, Boolean> =
        NotificationCategory.entries.associateWith { it.defaultOn },
    val quietHours: QuietHours = QuietHours(),
) {
    fun isOn(category: NotificationCategory): Boolean =
        if (!category.silenceable) true else categories[category] ?: category.defaultOn

    /**
     * Whether a message in [category] should be put in front of the reader right now.
     *
     * [nowEpochMillis] and [minuteOfDay] are passed in rather than read from the clock, so this is
     * a pure function and the quiet-hours arithmetic can be tested at every boundary without
     * waiting for eleven at night.
     */
    fun shouldShow(
        category: NotificationCategory?,
        nowEpochMillis: Long,
        minuteOfDay: Int,
    ): Boolean {
        // An unknown category is shown. See [NotificationCategory.forKind].
        val known = category ?: return true
        if (!known.silenceable) return true
        if (!enabled) return false
        if (mutedUntilEpochMillis?.let { nowEpochMillis < it } == true) return false
        if (!isOn(known)) return false
        if (quietHours.contains(minuteOfDay) && known !in quietHours.alwaysThrough) return false
        return true
    }

    /** The three flags to send the server, derived from the categories that map onto them. */
    fun serverPreferences(): PushPreferences = PushPreferences(
        newSignals = enabled && isOn(NotificationCategory.NEW_SIGNAL),
        // On if *any* of the three update categories is wanted: the server sends them under one
        // flag, so turning it off to satisfy one would silence the other two as well. The ones the
        // reader did not want are dropped on arrival instead.
        signalUpdates = enabled && listOf(
            NotificationCategory.TARGET_HIT,
            NotificationCategory.STOP_HIT,
            NotificationCategory.SIGNAL_CLOSED,
        ).any(::isOn),
        priceAlerts = enabled && isOn(NotificationCategory.PRICE_ALERT),
        announcements = enabled && isOn(NotificationCategory.ANNOUNCEMENT),
    )
}

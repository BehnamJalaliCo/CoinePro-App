package com.coinepro.core.guest

/**
 * A price a reader can see before they have an account.
 *
 * Separate from the signed-in feed's model rather than shared, and deliberately so: this one comes
 * from a public route with a coarser update rate and no per-reader subscription, and merging the
 * two would let a screen show a guest quote where it promised a live one.
 */
data class GuestQuote(
    val symbol: String,
    val price: Double,
    /** Null where the server omitted it. Omitted is not zero, and a zero would draw a flat day. */
    val changePercent24h: Double?,
    val high24h: Double?,
    val low24h: Double?,
    val volume24h: Double?,
)

/**
 * The public snapshot, with the server's own freshness verdict carried through.
 *
 * [stale] is the server's, not a threshold recomputed here. The web site reads the same flag, and a
 * client that decided for itself would disagree with it about the same feed.
 */
data class GuestPrices(
    val quotes: List<GuestQuote>,
    val stale: Boolean,
    val ageMillis: Long?,
    /**
     * How many instruments the feed carries in total, or null before the first full read.
     *
     * Worth a line on screen: a guest looking at twenty rows has no other way to know the app
     * quotes six hundred, and "twenty markets" is a much smaller product than the real one.
     */
    val universeSize: Int? = null,
)

data class GuestHeadline(
    val slug: String,
    val title: String,
    val summary: String?,
    val source: String?,
    /** ISO-8601 as the server sent it, unparsed — the screen shows the source, not a computed age. */
    val publishedAt: String?,
)

/**
 * One signal that has already closed, with what it did.
 *
 * Deliberately not the app's `Signal` type. That one is a live instruction — it has an entry to
 * act on and targets still ahead of it — and this is a finished record. Sharing a type would let a
 * screen offer to execute something that closed last week.
 */
data class TrackRecordEntry(
    val symbol: String,
    val timeframe: String?,
    val buy: Boolean,
    val win: Boolean,
    /** Percentage gain as the ladder actually banked it. The server forbids recomputing it. */
    val percentGain: Double,
    val riskReward: Double?,
)

/**
 * The published track record.
 *
 * [available] is the server's own word, carried through rather than inferred from the list being
 * empty. Empty-because-the-query-failed and empty-because-nothing-closed are different sentences
 * to put in front of somebody deciding whether to trust the product.
 */
data class GuestTrackRecord(
    val entries: List<TrackRecordEntry>,
    val available: Boolean,
) {
    val wins: Int get() = entries.count(TrackRecordEntry::win)

    /** Null rather than zero on an empty record: a win rate of nothing is not a win rate of 0%. */
    val winRate: Double? get() = if (entries.isEmpty()) null else wins * 100.0 / entries.size
}

/**
 * A count the server may or may not have been able to fetch.
 *
 * The community route fetches its member counts from Telegram at request time, and Telegram is
 * allowed to say no. The route's own documentation is explicit that a channel it could not read
 * must be shown as unavailable — never as a zero, and never as the number it returned last time.
 *
 * That rule is why this is a type and not a `Long?`. A nullable number invites `?: 0` at the call
 * site, and a channel with fifty thousand members drawn as «۰ عضو» is not a smaller claim than the
 * truth, it is a false one. [Unavailable] has nothing to accidentally render.
 */
sealed interface MemberCount {
    data class Known(val value: Long) : MemberCount
    data object Unavailable : MemberCount
}

/**
 * One public channel — a Telegram group, a channel, the bot.
 *
 * [url] is the server's, not built here from [username]. Which channels exist and where they live
 * is the product's business and it changes; a link assembled in the app is one that keeps pointing
 * at last year's group after the server has moved on.
 */
data class CommunityChannel(
    val key: String,
    val label: String,
    val url: String?,
    val members: MemberCount,
)

/**
 * The public community, as the server reports it.
 *
 * [total] is read from the server's own total rather than summed from [channels], and the two are
 * genuinely different numbers: a total summed here would silently omit every channel whose count
 * failed, producing a figure that is confidently wrong instead of honestly partial.
 */
data class GuestCommunity(
    val channels: List<CommunityChannel>,
    val total: MemberCount,
    val botUsers: MemberCount,
    val note: String?,
) {
    /** True when there is nothing to draw — no channel and no number worth a section heading. */
    val isEmpty: Boolean
        get() = channels.isEmpty() && total is MemberCount.Unavailable && botUsers is MemberCount.Unavailable
}

/**
 * How somebody becomes a member, as the server states it.
 *
 * Every field here is the server's, and the referral links most of all. A link compiled into the
 * app is wrong the day it changes, and a wrong one does not fail visibly — the exchange simply
 * never records the account as CoinePro's, so the reader funds it, submits their UID, and is
 * refused for a reason nothing on screen can explain.
 */
data class MembershipTerms(
    val lbankReferralUrl: String?,
    val ourbitReferralUrl: String?,
    val minDepositUsdt: Double?,
    /** Where copy trading can actually run. A subset of [uidExchanges]. */
    val copyTradeExchanges: List<String>,
    /**
     * Where a UID earns membership.
     *
     * Kept apart from [copyTradeExchanges] rather than merged. An Ourbit UID is a real membership
     * and never a tradeable one; offering copy trading against it would promise something the
     * platform cannot do.
     */
    val uidExchanges: List<String>,
    /** The server's own sentence to print above the links. */
    val noticeFa: String?,
) {
    /** Nothing worth drawing — no link to send anybody to. */
    val isEmpty: Boolean
        get() = lbankReferralUrl == null && ourbitReferralUrl == null
}

/**
 * One bar, as the public feed sends it.
 *
 * Deliberately not the signed-in chart's candle type. [closed] is the difference that matters: the
 * newest bar is still forming, and an indicator that treats a partial bar as final produces a
 * crossover that un-happens a minute later.
 */
data class GuestCandle(
    /** Unix seconds, at the bar's **open**. */
    val timeSeconds: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double?,
    val closed: Boolean,
)

/**
 * A public candle series.
 *
 * [timeframe] is the server's normalised label — `1h` is asked for and `H1` comes back — so it is
 * carried rather than assumed. Anything keying a cache on the requested value would miss every time.
 */
data class GuestCandles(
    val symbol: String,
    val timeframe: String,
    val candles: List<GuestCandle>,
)

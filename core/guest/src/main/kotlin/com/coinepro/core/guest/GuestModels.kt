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
)

data class GuestHeadline(
    val slug: String,
    val title: String,
    val summary: String?,
    val source: String?,
    /** ISO-8601 as the server sent it, unparsed — the screen shows the source, not a computed age. */
    val publishedAt: String?,
)

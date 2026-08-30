package com.coinepro.core.marketdata

import java.time.ZoneId

/**
 * How a backward fill ended.
 *
 * Every entry is a different sentence to a reader and a different decision for the caller, which is
 * why this is an enum and not a boolean: "there is no more" and "there is more but I stopped" look
 * identical on a chart and mean opposite things about whether to offer «بیشتر».
 */
enum class HistoryStop {
    /** The archive reached its ceiling. There may well be more at the venue; we stopped asking. */
    CEILING,

    /**
     * The venue ran out. This is the whole of its history for this series, and no amount of paging
     * will produce another bar until it archives more itself.
     */
    VENUE_EXHAUSTED,

    /**
     * A page arrived that added nothing.
     *
     * The case this exists for is real and is running in production: TradeYar's **public** route
     * takes no `before` at all, so a guest paging backwards is handed the same newest page over and
     * over. Without this stop the fill is an infinite loop of identical requests against a server
     * that is answering perfectly. It is not an error and it is not reported as one — it is the
     * honest end of the road on a route with no paging.
     */
    NO_PROGRESS,

    /** The page budget for this call ran out. The next call picks up where this one stopped. */
    PAGE_BUDGET,
}

/**
 * What one series' history looks like after a fill.
 *
 * [stop] is the interesting field and the rest are what a caller shows: how deep the archive now
 * is, and where its far edge sits. [reachedCeiling] and [venueExhausted] are derived from [stop]
 * rather than tracked separately, so they cannot disagree with it.
 */
data class HistoryDepth(
    /** How many bars the archive holds for this series now. */
    val bars: Int,
    /** The open time of the oldest bar held, or null when the fill found nothing at all. */
    val oldest: Long?,
    /** The open time of the newest bar held, or null on an empty series. */
    val newest: Long?,
    /** How many pages this call actually fetched. Zero means the archive already went deep enough. */
    val pages: Int,
    /** How many bars this call added that were not already held. */
    val added: Int,
    val stop: HistoryStop,
) {
    /** Whether the archive stopped because it is full rather than because the venue is empty. */
    val reachedCeiling: Boolean get() = stop == HistoryStop.CEILING

    /** Whether this is genuinely all the venue has. The honest basis for hiding «بیشتر». */
    val venueExhausted: Boolean get() = stop == HistoryStop.VENUE_EXHAUSTED || stop == HistoryStop.NO_PROGRESS
}

/**
 * Page backwards from what is already held, and keep what comes back.
 *
 * ### What this is for, and what it is not
 *
 * The chart's own «بیشتر» pages one screenful at a time because that is what a reader dragging the
 * left edge asked for. This is the other motion: deepen the archive for a series the reader is
 * actually using, so that the window they have is wider tomorrow than it is today. It is the whole
 * of "the archive grows over time rather than resetting" — nothing here is a one-shot download of a
 * history that does not exist yet.
 *
 * **It is capacity, not history.** [CandleArchive.MAX_BARS_PER_SERIES] is fifty thousand and every
 * venue this app talks to holds a small fraction of that today, so an honest fill against a healthy
 * venue ends at [HistoryStop.VENUE_EXHAUSTED] long before the ceiling. That is the distinction
 * [HistoryDepth.stop] exists to keep, and a caller must never word "50,000 candles" at a reader on
 * the strength of a constant. Print [HistoryDepth.bars], which is a fact.
 *
 * ### Why it stops in four ways
 *
 * A backward fill has four ends and three of them are not errors. The venue runs out; the ceiling
 * is reached; the budget for this call is spent; or a page adds nothing, which on a route with no
 * `before` is the only way to find out the route has no `before`. Each is reported rather than
 * flattened into "done", because the caller's next decision differs in each case.
 *
 * ### Cost, and why the budget is small
 *
 * [maxPages] defaults to twelve, which is at most six thousand bars and a dozen requests. Filling
 * fifty thousand bars at five hundred a page is a hundred round trips — three minutes of continuous
 * requests against a venue that is doing us a favour, on a mobile connection somebody is paying
 * for. So a call deepens the archive by a session's worth and returns; the growth is meant to be
 * measured in openings of the app, not in one long wait behind a spinner.
 *
 * Throws [CandleIntervalUnavailableException] if the venue has no feed that can produce [interval]
 * — the same refusal a plain load gives, for the same reason.
 */
suspend fun CandleGateway.fillHistory(
    symbol: String,
    interval: ChartInterval,
    archive: CandleArchive,
    /** How deep to go before stopping. Clamped to what the archive will keep. */
    target: Int = CandleArchive.MAX_BARS_PER_SERIES,
    /** Drawn bars per page. Five hundred is one page on every route in this app. */
    pageBars: Int = HISTORY_PAGE_BARS,
    /** How many requests this call may make. See the note on cost. */
    maxPages: Int = HISTORY_PAGE_BUDGET,
    zone: ZoneId = CHART_TIME_ZONE,
): HistoryDepth {
    val ceiling = target.coerceIn(1, CandleArchive.MAX_BARS_PER_SERIES)
    var span = archive.span(symbol, interval)
    var added = 0
    var pages = 0
    var stop = HistoryStop.PAGE_BUDGET
    // The far edge of what is held, which is where the next page starts. Null on an empty archive,
    // which asks the venue for the live edge — the same first request a chart open makes.
    var before: Long? = span?.oldest

    while (pages < maxPages) {
        if ((span?.count ?: 0) >= ceiling) {
            stop = HistoryStop.CEILING
            break
        }
        val page = load(symbol, interval, pageBars, before, zone)
        pages++
        if (page.candles.isEmpty()) {
            stop = HistoryStop.VENUE_EXHAUSTED
            break
        }
        val fresh = archive.write(symbol, interval, page.candles)
        added += fresh
        span = archive.span(symbol, interval)
        val next = page.candles.first().t
        // Two ways a page can fail to move: it added nothing, or its oldest bar is not older than
        // where we already were. Either one means the next request would be this same request, so
        // the loop ends here rather than asking a server the same question until the reader leaves.
        if (fresh == 0 || (before != null && next >= before)) {
            stop = HistoryStop.NO_PROGRESS
            break
        }
        before = next
        if (!page.hasMore) {
            stop = HistoryStop.VENUE_EXHAUSTED
            break
        }
        if ((span?.count ?: 0) >= ceiling) {
            stop = HistoryStop.CEILING
            break
        }
    }

    return HistoryDepth(
        bars = span?.count ?: 0,
        oldest = span?.oldest,
        newest = span?.newest,
        pages = pages,
        added = added,
        stop = stop,
    )
}

/**
 * Drawn bars per fill page.
 *
 * Five hundred rather than a thousand, because five hundred is the smallest of the three ceilings
 * in this app — TradeYar's public route refuses more with a `422` rather than truncating — and a
 * page size that works everywhere is worth more than a hundred extra bars on two routes out of
 * three. A folded interval asks for this many times its fold factor and is clamped by the venue's
 * own cap, which [CandleRequestPlan.truncated] reports.
 */
const val HISTORY_PAGE_BARS = 500

/**
 * Requests one fill may make: twelve.
 *
 * Six thousand bars at [HISTORY_PAGE_BARS], which is deeper than any single reader will pan in one
 * sitting and shallow enough to be over in a few seconds on a connection that works. The ceiling is
 * reached across sessions, which is the design and not a compromise.
 */
const val HISTORY_PAGE_BUDGET = 12

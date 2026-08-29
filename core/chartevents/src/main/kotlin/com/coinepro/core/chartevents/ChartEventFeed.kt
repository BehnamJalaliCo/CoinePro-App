package com.coinepro.core.chartevents

import com.coinepro.core.chart.ChartEvent
import com.coinepro.core.chart.EventKind
import com.coinepro.core.chart.Importance
import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelGateway
import com.coinepro.core.marketintel.MarketIntelSnapshot
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.relevanceForSymbol

/**
 * The kinds the market-intelligence document actually carries today.
 *
 * News and the economic calendar, and nothing else. The other three kinds are in the model because
 * a chart of a stock needs them and the placement maths is identical, but no backend this app talks
 * to publishes an earnings date, a dividend or a split — so the settings section offers those three
 * switched off and disabled rather than pretending a switch does something. The day a feed carries
 * one, adding it here is the whole change.
 *
 * That is a fact about the two backends rather than a gap in this module, and it is checkable:
 * `docs/PHASE10_MARKET_INTELLIGENCE_CONTRACT.md` is the whole shape of the document and carries two
 * collections, `news` and `calendar`. Neither platform lists an equity — CoinePro-FX serves metals
 * and the macro calendar that moves them, TradeYar serves coins — and an instrument with no issuer
 * has no earnings date, pays no dividend and cannot be split. There is nothing to ask either server
 * for.
 */
val SERVED_EVENT_KINDS: Set<EventKind> = setOf(EventKind.NEWS, EventKind.ECONOMIC)

/**
 * Where the chart's axis marks come from.
 *
 * An interface with one production implementation, because the thing behind it is a network call
 * and the cache in front of it has to be testable without one. Nothing here invents an endpoint:
 * the app already serves one market-intelligence document per platform, the news list and the
 * economic calendar are both already reading it, and a third route carrying the same rows keyed by
 * symbol would be a second version of the same truth to keep in step.
 */
interface ChartEventFeed {
    /**
     * Everything that happened to [symbol] between the two unix seconds, inclusive.
     *
     * The window is a filter, not a query: see [MarketIntelChartEventFeed] for what the server
     * actually answers and why asking it for an older window returns nothing rather than history.
     */
    suspend fun events(symbol: String, fromSeconds: Long, toSeconds: Long): List<ChartEvent>
}

/**
 * The market-intelligence snapshot, read as chart events.
 *
 * ### The window is honoured, not requested
 *
 * The snapshot route takes no parameters. It answers with whatever the backend currently publishes
 * — a rolling window of recent headlines and the calendar around today — so this filters what came
 * back rather than asking for a range. The consequence is worth stating plainly rather than
 * discovering: a reader who pans a daily chart to last spring sees no marks, and that is the truth
 * about the feed rather than a bug in the placement. A dated query would need a route that does not
 * exist yet, and guessing one would fail as an ordinary HTTP error that looks like an outage.
 *
 * ### One of the two platforms does not serve it at all
 *
 * `docs/BACKEND_ROUTE_MAP.md` records `api/mobile/v1/market-intelligence` as requested of TradeYar
 * and not yet built — the news rows exist in its `news_posts` table and are served on a public
 * router in a different shape, and the adapter onto the mobile route is what was asked for. So on
 * that platform this read is a 404 until it is. That is not something this module can route around:
 * the path lives in `NetworkMarketIntelGateway`, one document per backend, and reading the public
 * router's own shape here would be a second parser for the same rows kept in step by hand. What it
 * does instead is name the condition — [ChartEventNotice.UNSERVED] — so a crypto reader is told the
 * feed is missing rather than left looking at a bare axis that reads as "nothing has happened".
 *
 * ### Timestamps are precise enough to place a mark
 *
 * Both rows carry an ISO instant that `parseWireInstant` has already normalised, which is a real
 * moment to the second — so a mark lands on the bar the release actually happened in. Nothing here
 * rounds, snaps or infers a time; a row whose timestamp did not parse never became a domain object
 * upstream and so never reaches this file at all.
 */
class MarketIntelChartEventFeed(private val gateway: MarketIntelGateway) : ChartEventFeed {
    override suspend fun events(symbol: String, fromSeconds: Long, toSeconds: Long): List<ChartEvent> =
        gateway.snapshot().chartEventsFor(symbol).filter { it.at in fromSeconds..toSeconds }
}

/**
 * One snapshot's rows as events for one instrument, earliest first.
 *
 * ### What belongs to a symbol
 *
 * News is filtered by the market it was tagged with, because a token unlock has no bearing on
 * bullion and a rate cut reported as a gold story has no bearing on a listing. An item the server
 * tagged with nothing is general market news and belongs to every chart — that is the case the news
 * screen itself labels «بازار عمومی».
 *
 * The calendar is **not** filtered by instrument, and that is deliberate and matches the calendar
 * screen's own decision: the macro releases are what move both platforms, and filtering them per
 * symbol hid exactly the releases a reader most needed to see beside their candles.
 */
fun MarketIntelSnapshot.chartEventsFor(symbol: String): List<ChartEvent> {
    val relevance = relevanceForSymbol(symbol)
    val headlines = news
        .filter { item -> item.relevance.isEmpty() || (relevance != null && relevance in item.relevance) }
        .map(MarketNewsItem::toChartEvent)
    return (headlines + calendar.map(EconomicEvent::toChartEvent)).sortedBy(ChartEvent::at)
}

/** A headline, at the second it was published. The summary is the body a tapped mark opens. */
internal fun MarketNewsItem.toChartEvent(): ChartEvent = ChartEvent(
    at = publishedAt.epochSecond,
    kind = EventKind.NEWS,
    title = title,
    detail = summary,
    importance = impact.toImportance(),
    source = source,
)

/**
 * A release, at the second it was scheduled for.
 *
 * [ChartEvent.source] takes the issuing country and currency, which is the only attribution this
 * feed carries — a statistics office publishes a release, no wire reports it — and null where it
 * carries neither, rather than a made-up agency name.
 */
internal fun EconomicEvent.toChartEvent(): ChartEvent = ChartEvent(
    at = scheduledAt.epochSecond,
    kind = EventKind.ECONOMIC,
    title = title,
    detail = figuresLine(),
    importance = impact.toImportance(),
    source = listOfNotNull(country, currency).joinToString(" · ").takeIf(String::isNotEmpty),
)

/**
 * «واقعی 3.2% · پیش‌بینی 3.1% · قبلی 3.0%», and null when the release has published no figure yet.
 *
 * The labels are Persian and hardcoded here rather than taken from a string resource, for the same
 * reason [EventKind.label] is: this is a model-layer sentence that both the sheet and any future
 * reader of an event share, and pulling it from resources would need a `Context` in a mapper that
 * runs off the main thread and has none. The **values** are the server's own strings, untouched —
 * a market figure stays in Latin digits, and reparsing one to reformat it would risk printing a
 * number nobody published.
 */
private fun EconomicEvent.figuresLine(): String? = listOfNotNull(
    actual?.let { "واقعی $it" },
    forecast?.let { "پیش‌بینی $it" },
    previous?.let { "قبلی $it" },
).joinToString(" · ").takeIf(String::isNotEmpty)

/**
 * How loudly the axis draws it.
 *
 * An impact the source did not declare is drawn at the quietest weight and never at a louder one.
 * That is not the app guessing "low": the calendar screen says in as many words that a structured
 * source which declared no impact is not second-guessed, and the equivalent promise on a chart is
 * that an undeclared release never shouts. What it says when opened is still exactly what arrived.
 */
internal fun MarketImpact.toImportance(): Importance = when (this) {
    MarketImpact.HIGH -> Importance.HIGH
    MarketImpact.MEDIUM -> Importance.MEDIUM
    MarketImpact.LOW -> Importance.LOW
    MarketImpact.UNKNOWN -> Importance.LOW
}

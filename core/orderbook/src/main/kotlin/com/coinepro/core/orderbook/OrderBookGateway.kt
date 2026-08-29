package com.coinepro.core.orderbook

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Why a market has no order book to show, when nothing went wrong.
 *
 * This is the distinction the whole feature turns on. A depth ladder with no data is nearly always
 * *not* a failure: it is a feed that has never published depth and never will, and dressing that up
 * as an error invites the reader to retry something that cannot succeed. Every value here means
 * "correct answer, and the answer is no".
 */
enum class DepthUnavailableReason {
    /**
     * The venue behind this feed does not publish Level II at all.
     *
     * This is CoinePro-FX's case and it is a property of the *broker*, not of the app or the
     * backend. MetaTrader 5 exposes depth only through `MarketBookAdd`/`MarketBookGet`, and those
     * return nothing unless the broker has switched market-depth publication on for the symbol.
     * Most retail forex brokers have not. No amount of work downstream changes it — see
     * `docs/SERVER_ASKS_DOM.md`, where the question is put to the backend before anything is built
     * on top of an answer nobody has yet.
     */
    FEED_PUBLISHES_NO_DEPTH,

    /**
     * The venue publishes depth but this backend does not relay it yet.
     *
     * This is TradeYar's case today. LBank serves a public book on `/v2/depth.do` and TradeYar
     * already relays that exchange's candles and trades; the depth route is asked for and not yet
     * built. Distinct from [FEED_PUBLISHES_NO_DEPTH] because the two have different futures and the
     * reader deserves the difference: one is "not here yet", the other is "not possible here".
     */
    ENDPOINT_NOT_SERVED,
}

/**
 * The refusal, as a throwable, so it can ride in [AppResult.Failure.cause].
 *
 * A separate exception type rather than a new [ErrorKind] for the same reason
 * `ExecutionUnsupportedException` is one: [ErrorKind] is a small closed vocabulary shared by every
 * gateway in the app, and "this venue does not offer the concept" is not a transport condition. The
 * kind stays [ErrorKind.VALIDATION] — the request will never become valid for this feed, so nothing
 * downstream should offer a retry — and the reason travels here where a caller can read it and pick
 * the right sentence.
 */
class DepthUnavailableException(val reason: DepthUnavailableReason) : Exception(
    "This feed does not publish order-book depth: $reason",
)

/**
 * The documented refusal, in the shape every caller already handles.
 *
 * Built here rather than at each gateway so the three producers of it — the two platform gateways
 * and the network one, on a route that answers 404 — cannot drift into three different failures for
 * one condition.
 */
fun depthUnavailable(reason: DepthUnavailableReason): AppResult.Failure = AppResult.Failure(
    kind = ErrorKind.VALIDATION,
    cause = DepthUnavailableException(reason),
)

/**
 * Reads the refusal back out of a failure, or null if this failure is something else.
 *
 * The one place a caller should ask "is this feed simply without depth". Matching on
 * [ErrorKind.VALIDATION] alone would also catch a genuinely rejected request, and matching on the
 * message would be matching on prose.
 */
val AppResult.Failure.depthUnavailableReason: DepthUnavailableReason?
    get() = (cause as? DepthUnavailableException)?.reason

/**
 * Resting liquidity, from whichever backend owns the symbol.
 *
 * Modelled on `CandleGateway` — one implementation per platform, chosen by Hilt qualifier, with the
 * asymmetry between the two backends visible in the types rather than hidden behind a flag. Here
 * the asymmetry is larger than it is for candles: one platform has a route to build against and the
 * other may have nothing to build on at all, so the honest implementation for that side is
 * [NoDepthGateway] and it is a first-class member of this file rather than a test double.
 */
interface OrderBookGateway {
    /**
     * Where these levels come from, named the way a reader would name it — "LBank", not the relay.
     *
     * Same reasoning as `CandleGateway.sourceName`, and it bites harder here. A depth ladder is the
     * screen most open to the «کندل‌سازی» accusation, because a book is unverifiable by eye in a way
     * a candle is not: nobody can tell a fabricated wall from a real one. Naming the venue makes the
     * claim checkable against that venue's own book, which is the only answer to the accusation
     * that is worth anything. Empty by default because a fixture has no venue and inventing one for
     * it would put a false name on screen.
     */
    val sourceName: String get() = ""

    /**
     * One snapshot, [depth] levels a side.
     *
     * [depth] is what is asked of the venue, not what the ladder shows. The screen draws fewer rows
     * than this and measures [OrderBook.imbalance] over all of them, so the number on the meter is
     * not a restatement of the eight rows already visible.
     */
    suspend fun load(symbol: String, depth: Int = DEFAULT_DEPTH): AppResult<OrderBook>

    /**
     * The live book, for as long as the collector wants it.
     *
     * Emits **only** books it actually has. It never emits an empty one to mean "still waiting" —
     * an empty book is a claim about liquidity, and a ladder that draws it looks like a market with
     * no orders in it rather than a screen that has not loaded.
     *
     * A stream that cannot start does not hang: it completes. That is the contract that makes
     * [NoDepthGateway] honest, and every implementation here holds to it, because a collector that
     * can distinguish "finished" from "still trying" can show a sentence instead of a spinner that
     * turns for the life of the screen.
     */
    fun stream(symbol: String): Flow<OrderBook>

    companion object {
        /**
         * Twenty levels a side.
         *
         * LBank's public book serves them in steps and twenty is one of them; a broker's market
         * book is usually shallower than this and simply returns what it has. It is also about
         * twice what fits on a phone, which is deliberate — see [load].
         */
        const val DEFAULT_DEPTH = 20

        /** What the ladder shows at rest, per side. Wider than this and the rows stop being legible. */
        const val VISIBLE_LEVELS = 8
    }
}

/**
 * The gateway for a feed that has no order book, which answers so and stops.
 *
 * ### Why this is production code and not a stub
 *
 * It is the correct implementation for CoinePro-FX today, and it may be the correct one forever —
 * MT5 gives an app nothing unless the broker publishes Level II, and most retail forex brokers do
 * not. The alternative implementations are all worse in the same way: a gateway that returns an
 * empty book draws a ladder of empty rungs and claims the market has no resting orders; one that
 * returns a network error invites a retry that will fail identically every time; one that never
 * answers leaves a spinner turning forever, which is the worst of the three because it never even
 * admits that nothing is coming.
 *
 * So this says no, says which kind of no, and completes. The screen turns that into one short
 * Persian sentence. That sentence is the feature on this platform.
 */
class NoDepthGateway(
    private val reason: DepthUnavailableReason,
    override val sourceName: String = "",
) : OrderBookGateway {

    override suspend fun load(symbol: String, depth: Int): AppResult<OrderBook> =
        depthUnavailable(reason)

    /**
     * Completes at once, having emitted nothing.
     *
     * Not a flow that suspends forever, which is the shape this would take if it were written as
     * "there is nothing to send". A collector distinguishes the two, and only one of them lets a
     * screen stop waiting.
     */
    override fun stream(symbol: String): Flow<OrderBook> = emptyFlow()
}

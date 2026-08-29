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
     * The route is not on this server.
     *
     * TradeYar shipped `market/depth` on 2026-08-29, so this is no longer the everyday crypto
     * answer it was written for — it is what a shipped build meets when it is pointed at a server
     * older than that date, which staging and a rolled-back deploy both are. Kept rather than
     * deleted for exactly that: an app that reads a `404`/`501` as an outage tells a reader on an
     * old host to check their connection. Distinct from [FEED_PUBLISHES_NO_DEPTH] because the two
     * have different futures and the reader deserves the difference: one is "not here yet", the
     * other is "not possible here".
     */
    ENDPOINT_NOT_SERVED,

    /**
     * This platform does not carry this market, so it has no book for it and never will.
     *
     * TradeYar answers `422` with `TYR-021` from the same scope gate `market/candles` uses: the
     * symbol is outside the 441-market crypto universe the platform serves. It gets its own value
     * rather than sharing [ENDPOINT_NOT_SERVED] because it is a **client** fault and the opposite
     * kind of fact — the route works, the server is current, and this app asked for an instrument
     * that is not on it. Told as "not served yet" it would send a reader waiting for a relay that
     * has already shipped, and it would hide a symbol-mapping bug in the app behind a sentence
     * about the backend.
     */
    SYMBOL_NOT_SERVED,

    /**
     * The exchange has delisted this market, so the book is gone rather than momentarily missing.
     *
     * LBank answers `error_code 20156` for a retired contract and TradeYar keeps it an error —
     * `502` with `TYR-048` — instead of relaying an empty book, which is right: an empty book is a
     * claim about liquidity in a live market, and a delisted contract is not a live market. No
     * retry, because there is nothing to come back to.
     */
    SYMBOL_DELISTED,
}

/**
 * Why the book could not be fetched *this time*, when something really is wrong upstream.
 *
 * The mirror image of [DepthUnavailableReason] and deliberately a separate type. Everything here
 * is a transient condition on a route that exists and a symbol the platform carries, so every value
 * here keeps its retry button — which is exactly why it cannot live in the enum whose whole meaning
 * is "asking again will not help". What it buys is a true sentence: the app's generic transport copy
 * tells the reader to check their connection, and on both of these the reader's connection is fine.
 */
enum class DepthOutageReason {
    /**
     * The relay is up and the exchange behind it is not.
     *
     * TradeYar's `502` without a `TYR-048` code. It is worth its own sentence because it is the one
     * failure where the app, the phone's network and the platform are all healthy and there is
     * still no book — and a reader told to check their connection over it will spend the outage
     * restarting a router.
     */
    EXCHANGE_UNREACHABLE,

    /**
     * The relay itself has no exchange host configured — TradeYar's `503`.
     *
     * A deployment fault rather than a market one. Retryable in the sense that a deploy fixes it
     * and nothing in the app needs to change, but not something a reader can influence, so the copy
     * says the server is the thing that is not ready rather than implying the phone might be.
     */
    RELAY_NOT_CONFIGURED,
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
 * The upstream outage, as a throwable, so it rides the same channel the refusal does.
 *
 * Mirrors [DepthUnavailableException] on purpose: one place a screen looks, two questions it can
 * ask, and no third failure shape to keep in step. The kind stays [ErrorKind.SERVER] because that
 * is what it is — the request was well formed and the server could not answer it — so everything
 * downstream that decides on kind alone keeps offering the retry these conditions deserve.
 */
class DepthOutageException(val reason: DepthOutageReason) : Exception(
    "The depth relay could not reach the venue: $reason",
)

/** The documented outage, built in one place for the same reason [depthUnavailable] is. */
fun depthOutage(reason: DepthOutageReason): AppResult.Failure = AppResult.Failure(
    kind = ErrorKind.SERVER,
    cause = DepthOutageException(reason),
)

/**
 * Reads the outage back out of a failure, or null when the failure is an ordinary one.
 *
 * Null is the common case and is not a gap: a dropped connection or a parse failure has no upstream
 * story to tell, and the screen's generic transport sentence is the true thing to say about it.
 */
val AppResult.Failure.depthOutageReason: DepthOutageReason?
    get() = (cause as? DepthOutageException)?.reason

/**
 * Resting liquidity, from whichever backend owns the symbol.
 *
 * Modelled on `CandleGateway` — one implementation per platform, chosen by Hilt qualifier, with the
 * asymmetry between the two backends visible in the types rather than hidden behind a flag. Here
 * the asymmetry is larger than it is for candles: one platform has a route to build against and the
 * other may have nothing to build on at all, so the honest implementation for that side is
 * [NoDepthGateway] and it is a first-class member of this file rather than a test double.
 *
 * ### The book must come from the same venue as the ticker, or the screen lies
 *
 * This is a requirement on every implementation of this interface and not a note about one of them.
 * The depth screen's entire subject is the distance between the ladder and the last price, and the
 * last price comes from somewhere else — the ticker feed, printed directly above the ladder. Sourced
 * from a different venue the two disagree by that venue's own basis, and the ladder sits a long way
 * from the price above it with nothing on screen to explain the gap.
 *
 * TradeYar measured it on 2026-08-29, one instant, one exchange: LBank **futures** BTCUSDT best bid
 * `77588.0`, LBank **spot** `btc_usdt` best bid `77610.72` — 22.6 USDT apart, about 0.03%, which on
 * a 0.1 tick is roughly two hundred ticks of unexplained offset. This app's ticker and its candles
 * both come from the perpetual futures book, so its depth does too. Coverage says the same thing
 * independently: 62 of the app's 441 crypto symbols have no spot pair at all, so a spot relay would
 * have answered "no book" for 14% of the markets whose chart and price were working.
 */
interface OrderBookGateway {
    /**
     * Where these levels come from, named the way a reader would name it — "LBank Futures", not the
     * relay.
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
     * [depth] is what is asked of the venue and it is wider than anything the screen reads at once,
     * on purpose. Three different windows come out of one request: the ladder draws
     * [VISIBLE_LEVELS] rows, the bid-share meter is measured over [IMBALANCE_LEVELS], and the depth
     * curve uses everything that arrived. Each has its reasoning on its own constant, and none of
     * them may be quietly swapped for another — a meter measured over a hundred levels and a meter
     * measured over twenty are different claims about the market wearing the same percent sign.
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
         * A hundred levels a side, because the wide fetch turned out to be free.
         *
         * This was twenty, chosen to be modest about a route that did not exist yet. TradeYar then
         * measured the route that does, from their host, ten serial samples each:
         *
         * | depth | LBank median | p90 | body | gzipped at the edge |
         * |---|---|---|---|---|
         * | 20 | 249 ms | 251 ms | 0.8 KiB | 0.4 KiB |
         * | 100 | 255 ms | 260 ms | 3.4 KiB | 1.1 KiB |
         * | 200 | 260 ms | 265 ms | 6.7 KiB | 2.1 KiB |
         * | 1000 | 295 ms | 302 ms | — | — |
         *
         * A hundred levels cost **six milliseconds** more than twenty, and two requests a second at
         * a hundred held for fifteen seconds gave thirty successes at a 253 ms median with no `429`
         * and no degradation. Depth on this endpoint is nearly free because the cost is the network
         * round trip and not the number of levels. LBank honours the requested depth exactly, so a
         * hundred is a hundred, and TradeYar have confirmed two hundred is fine too — going deeper
         * for the curve needs no further permission, only a reason.
         *
         * The one figure worth watching is not latency, it is the reader's data. nginx gzips the
         * response at `comp_level 6` and an order book compresses unusually well, since neighbouring
         * prices share a prefix — 3.4 KiB becomes 1.1 KiB. At one-second polling that is roughly
         * **4 MB per hour of mobile data**, which is why the screen polls only while it is open. See
         * `OrderBookController`.
         *
         * What that buys is the depth curve. Twenty levels describes the shape immediately around
         * the touch and says nothing about where the size actually sits; a wall four hundred ticks
         * out is invisible at twenty and is the whole reason a trader opens this screen. **Do not
         * narrow this back to twenty as an optimisation** — six milliseconds and 0.7 KiB is the
         * entire saving, and the cost is flattening the one picture the curve exists to draw.
         *
         * Fetching wide is not the same as reading wide. [VISIBLE_LEVELS] still draws eight rows
         * and [IMBALANCE_LEVELS] still measures pressure near the touch; see both.
         */
        const val DEFAULT_DEPTH = 100

        /** What the ladder shows at rest, per side. Wider than this and the rows stop being legible. */
        const val VISIBLE_LEVELS = 8

        /**
         * The band the bid-share meter is measured over: twenty levels a side, near the touch.
         *
         * Not [DEFAULT_DEPTH], and the difference is the point. Imbalance is read as "which side is
         * pressing now", and only orders close enough to trade support that reading. Resting size a
         * percent out is pulled and replaced constantly and rarely fills, so folding the full
         * hundred into the meter would make it smoother, steadier and less true — a number that
         * stops moving when the market does. Twenty a side is wide enough not to be a restatement
         * of the eight rows already on screen and narrow enough to still be about the touch.
         *
         * The screen prints this band beside the meter, because imbalance over twenty levels and
         * over a hundred are different quantities and a bare percentage cannot say which it is.
         */
        const val IMBALANCE_LEVELS = 20
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

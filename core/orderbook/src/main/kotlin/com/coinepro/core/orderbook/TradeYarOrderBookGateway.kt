package com.coinepro.core.orderbook

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.network.ApiErrors
import com.google.gson.annotations.SerializedName
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

internal interface CryptoDepthApi {
    /**
     * `GET /api/mobile/v1/market/depth?symbol=&depth=`, live since 2026-08-29.
     *
     * The prefix is written out because TradeYar's base address is the bare host and every one of
     * its mobile routes carries `api/mobile/v1/` itself — see `docs/BACKEND_ROUTE_MAP.md`. A
     * relative `market/depth` against that base resolves to the host root, which is not a route on
     * that server and answers `404`; this gateway would then read its own misspelling as
     * [DepthUnavailableReason.ENDPOINT_NOT_SERVED] and tell every reader the relay had not shipped.
     */
    @GET("api/mobile/v1/market/depth")
    suspend fun depth(
        @Query("symbol") symbol: String,
        @Query("depth") depth: Int,
    ): CryptoDepthDto
}

/**
 * The wire shape TradeYar actually built, which is not in every respect the one asked for.
 *
 * Both sides arrive as numeric arrays rather than objects: the source is LBank's futures book,
 * whose levels are `{"price", "volume", "orders"}` strings, and the relay flattens and converts
 * them so the app holds numbers and not a re-modelling of a re-modelling. Every property is
 * nullable and every default is empty, because a build in the field can meet a server older than
 * the route and a parser that throws on a missing field turns "older server" into a crash.
 *
 * ### There is no `ts`, and there must never be one
 *
 * The ask said: send the exchange's own time, and if the exchange has none, send nothing rather
 * than your own clock. LBank's futures book has none — its `data` object is exactly `symbol`,
 * `asks`, `bids` at every depth — so the field is not here at all rather than here and always null.
 * A nullable `ts` is an invitation: the next hand to touch this file fills it from
 * [serverTimeMs] because the types line up, and the ladder starts claiming a freshness nothing
 * measured.
 *
 * The two fields that did arrive are named so they cannot be swapped for it. See each below.
 */
internal data class CryptoDepthDto(
    val symbol: String? = null,
    val depth: Int? = null,
    /**
     * Measured by the relay, not guessed: it asks LBank for `depth + 1` and serves `depth`, so more
     * than `depth` coming back means the book continues past the page. LBank honours the requested
     * depth exactly and never truncates silently, which is what makes the extra level a real test
     * rather than the "is the page full" inference this app used before it.
     */
    val truncated: Boolean? = null,
    /**
     * `[price, quantity]`, or `[price, quantity, orders]` where the venue counted.
     *
     * The third element is **omitted** when the count is unknown; it is never sent as `0`. That is
     * TradeYar's rule and it is the right one: `orders: 0` beside a positive quantity would claim
     * liquidity nobody placed, which is a contradiction, and a two-element row is the only honest
     * way to say "not known". So a short row means *absent*, not *none* — see [toDepthLevels],
     * which keeps it null rather than filling a zero in.
     */
    val bids: List<List<Double>> = emptyList(),
    val asks: List<List<Double>> = emptyList(),
    /**
     * The **relay's** clock at the moment it serialised this response, in epoch milliseconds.
     *
     * It is useful for one thing — measuring round-trip time against the phone's own clock — and it
     * is not the age of the book. Nothing here or downstream may put it into [OrderBook.at] or into
     * any staleness figure: it is later than the exchange snapshot by however long the relay's cache
     * had been holding it, so shown as freshness it makes a half-second-old book look brand new
     * every single time, on the one screen where age is the entire subject.
     */
    @SerializedName("server_time_ms")
    val serverTimeMs: Long? = null,
    /**
     * The honest upper bound on staleness: this book is at most this many milliseconds old, plus
     * flight time.
     *
     * TradeYar serves a 500 ms cache in front of LBank, so this is what a reader can be told when
     * the venue publishes no timestamp of its own. It is the *only* number on this response that
     * means anything about age, and [OrderBook.maxAgeMillis] is where it goes.
     */
    @SerializedName("cache_ttl_ms")
    val cacheTtlMs: Long? = null,
)

/**
 * Crypto depth, relayed from LBank's **perpetual futures** book by TradeYar.
 *
 * ### Futures, not spot, and that is the whole point
 *
 * The original ask was for a relay of `/v2/depth.do`, which is LBank's spot book. TradeYar declined
 * it and was right to. This platform is not a spot venue: the `ws/prices` ticker and the
 * `market/candles` history both come from LBank's perpetual futures (`/cfd/openApi/v1/pub/…`), and
 * this book now comes from `/pub/marketOrder` on the same host. The reasoning is on
 * [OrderBookGateway] with the two prices that settle it — 22.6 USDT between the two books at one
 * instant, about 0.03%, on a screen whose subject is the distance from the ladder to the last price
 * printed directly above it. Coverage agreed independently: 62 of the app's 441 crypto symbols have
 * no spot pair at all.
 *
 * ### The book is rebuilt, not trusted
 *
 * Everything from the wire goes through [OrderBook.of], which sorts both sides, sums duplicate
 * prices and drops rows the relay could not fill. The relay already sorts and already drops zero
 * volumes, and this changes nothing when it does; a relay that ever reorders, pages or merges is
 * caught here rather than on the ladder, where a mis-sorted book draws perfectly and lies.
 *
 * ### What the errors mean
 *
 * The route exists, so a failure now says something specific and each branch says a different true
 * thing. `422`/`TYR-021` is the platform's scope gate — the app asked for a market this backend
 * does not carry, which is a bug on this side and not a gap on theirs. `502`/`TYR-048` is LBank's
 * `error_code 20156`, a delisted contract. A bare `502` is the exchange being unreachable and a
 * `503` is the relay having no exchange host configured; both are outages that a retry can outlive,
 * so both keep their button. `404`/`501` survives only for a build pointed at a server older than
 * the route.
 */
class TradeYarOrderBookGateway(
    retrofit: Retrofit,
    private val pollMillis: Long = DEFAULT_POLL_MILLIS,
) : OrderBookGateway {

    private val api = retrofit.create(CryptoDepthApi::class.java)

    // The exchange and the half of it these levels are from, not the relay. "LBank" alone would be
    // ambiguous now that the same exchange's spot book is 22.6 USDT away and a reader checking this
    // ladder against the wrong one of the two would conclude the app was inventing prices. See
    // `OrderBookGateway.sourceName`.
    override val sourceName: String = "LBank Futures"

    override suspend fun load(symbol: String, depth: Int): AppResult<OrderBook> = try {
        val requested = depth.coerceIn(1, MAX_DEPTH)
        val response = api.depth(symbol.uppercase(), requested)
        AppResult.Success(
            OrderBook.of(
                // The server's spelling of the symbol wins where it sent one, exactly as the candle
                // gateway does: a saved layout can carry an alternate spelling, and the venue's own
                // answer is the one to believe about which market these levels belong to.
                symbol = response.symbol ?: symbol,
                bids = response.bids.toDepthLevels(),
                asks = response.asks.toDepthLevels(),
                at = NO_VENUE_TIME,
                // Claimed by the server, which measures it. The old inference is kept behind it for
                // a server that predates the flag, and it errs toward saying "there is more" —
                // the safe direction, since a reader told the book continues has lost nothing.
                truncated = response.truncated
                    ?: (response.bids.size >= requested || response.asks.size >= requested),
                // A non-positive TTL is not a fresher book, it is a relay that did not answer the
                // question. Left null, the screen says nothing about age rather than claiming zero.
                maxAgeMillis = response.cacheTtlMs?.takeIf { it > 0L },
            ),
        )
    } catch (error: HttpException) {
        when (error.code()) {
            404, 501 -> depthUnavailable(DepthUnavailableReason.ENDPOINT_NOT_SERVED)
            // `TYR-021`. The code is read for the log and not required for the branch: `symbol` is
            // the only parameter this call sends that the app does not already clamp, so a 422 here
            // can be about nothing else, and falling through would hand a reader a retry button
            // over a symbol that will be out of scope every time it is pressed.
            422 -> depthUnavailable(DepthUnavailableReason.SYMBOL_NOT_SERVED)
            502 -> if (ApiErrors.from(error).code == DELISTED) {
                depthUnavailable(DepthUnavailableReason.SYMBOL_DELISTED)
            } else {
                depthOutage(DepthOutageReason.EXCHANGE_UNREACHABLE)
            }
            503 -> depthOutage(DepthOutageReason.RELAY_NOT_CONFIGURED)
            401, 403 -> AppResult.Failure(ErrorKind.AUTH, cause = error)
            429 -> AppResult.Failure(ErrorKind.RATE_LIMIT, cause = error)
            in 500..599 -> AppResult.Failure(ErrorKind.SERVER, cause = error)
            else -> AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
        }
    } catch (error: IOException) {
        AppResult.Failure(ErrorKind.NETWORK, cause = error)
    } catch (error: Throwable) {
        AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
    }

    /**
     * Polls once a second, which is the cadence both ends have agreed to keep.
     *
     * ### Why a second is not too fast, at a hundred levels
     *
     * TradeYar put no rate limiter on the route and found none on LBank's side either — sixty
     * concurrent requests, sixty successes, in 0.7 s. LBank answers them from that host in 243–256
     * milliseconds, and the page size barely moves it: 249 ms at depth 20, 255 ms at 100, 260 ms at
     * 200, for gzipped bodies of 0.4, 1.1 and 2.1 KiB. Two requests a second at depth 100 held for
     * fifteen seconds gave thirty successes at a 253 ms median. That is why
     * [OrderBookGateway.DEFAULT_DEPTH] is a hundred and not twenty — the wide book the depth curve
     * needs costs six milliseconds.
     *
     * In front of LBank sits a 500 ms cache and a single-flight lock, which together mean a phone
     * asking once a second never receives a book older than about half a second, and a hundred
     * phones on BTCUSDT collapse into two upstream calls a second rather than a hundred. They asked
     * us to keep the one-second number rather than raise it, so it is kept.
     *
     * ### Why this is not a socket, and why that is not an oversight
     *
     * LBank does broadcast this book over WebSocket — topic `x=3`, 25 levels — so removing the poll
     * is technically available. TradeYar recommend against it today and the reasoning is worth
     * recording so nobody reopens it casually: their relay's live channel currently consumes only
     * the ticker topic (`x=1`), and adding a second topic touches the platform's live price path —
     * the same path signals and order execution sit on. Risking that for one screen a snapshot
     * already serves is a bad trade. If this screen ever becomes heavily trafficked, it is worth
     * asking again; until then the poll is the deliberate answer, not the unfinished one.
     *
     * Two rules keep this from lying when things go wrong. A depth-unavailable answer **ends** the
     * flow at once, so the screen stops waiting and says so. A run of [MAX_CONSECUTIVE_FAILURES]
     * transport failures ends it too: past that point the ladder on screen is a book from a minute
     * ago being presented as live, and stopping hands the screen back the chance to say the feed is
     * gone. A single failure is skipped rather than emitted — one dropped snapshot is not news.
     */
    override fun stream(symbol: String): Flow<OrderBook> = flow {
        var consecutiveFailures = 0
        while (true) {
            when (val result = load(symbol, OrderBookGateway.DEFAULT_DEPTH)) {
                is AppResult.Success -> {
                    consecutiveFailures = 0
                    emit(result.value)
                }
                is AppResult.Failure -> {
                    if (result.depthUnavailableReason != null) return@flow
                    consecutiveFailures += 1
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) return@flow
                }
            }
            delay(pollMillis)
        }
        // Identical snapshots are common on a quiet market and each one would recompose the whole
        // ladder for no visible change.
    }.distinctUntilChanged()

    private companion object {
        /**
         * TradeYar's own ceiling for the `depth` parameter: above it they answer `422`.
         *
         * Clamped here rather than sent and rejected, so a caller that asks for more gets the
         * deepest book on offer instead of an error screen. LBank itself serves up to a thousand
         * and TradeYar say lifting their cap is a one-line change on their side — but the cap is
         * the contract as it stands today, and a client that sends 500 on the strength of a
         * sentence in a chat log is a client that breaks on the deploy that enforces it.
         */
        const val MAX_DEPTH = 200

        /** One second between snapshots. See [stream]. */
        const val DEFAULT_POLL_MILLIS = 1_000L

        /** Roughly five seconds of silence at the default cadence before the stream gives up. */
        const val MAX_CONSECUTIVE_FAILURES = 5

        /** `TYR-048` on a `502`: LBank's `error_code 20156`, a contract this exchange has retired. */
        const val DELISTED = "TYR-048"

        /**
         * LBank's futures book publishes no time of its own, so [OrderBook.at] is zero here.
         *
         * Named rather than written as a bare `0L` at the call site so that it reads as a decision
         * about the venue instead of a placeholder somebody forgot to fill. The staleness the
         * screen can honestly show comes from `cache_ttl_ms` instead — see [CryptoDepthDto].
         */
        const val NO_VENUE_TIME = 0L
    }
}

/**
 * `[price, quantity]` or `[price, quantity, orders]` to levels, dropping anything shorter than a
 * pair.
 *
 * A one-element row is not a level at quantity zero — it is a row that arrived malformed, and
 * reading its missing half as zero would put a rung on the ladder with nothing behind it.
 * [OrderBook.of] drops the zeroes that get through; this drops the rows that never had two numbers
 * to begin with.
 *
 * ### The third element, and the difference between absent and one
 *
 * It is the resting-order count, live since 2026-08-29 and present on every level of every
 * non-empty symbol TradeYar sampled. It is still optional in both directions: absent on a server
 * older than that date, and absent forever on any venue that does not count — MT5 never will.
 *
 * When the relay does not know the count it **omits** the element rather than sending `0`, so a
 * two-element row means "not known" and this returns null for it. Null and one are different facts
 * about a price and nothing downstream may flatten one into the other: a level of forty with one
 * order behind it is a single participant who can withdraw the whole wall in one message, and a
 * level whose count nobody published says nothing about that at all. A non-positive or non-finite
 * third element is read the same way — as absent — because "0 orders" printed beside a quantity
 * that is plainly there is worse than no figure at all.
 *
 * Internal rather than private so the wire shape can be pinned by a test against the relay's own
 * payload. The gateway is the only caller.
 */
internal fun List<List<Double>>.toDepthLevels(): List<DepthLevel> = mapNotNull { row ->
    if (row.size < 2) {
        null
    } else {
        DepthLevel(
            price = row[0],
            quantity = row[1],
            orders = row.getOrNull(2)?.takeIf { it.isFinite() && it >= 1.0 }?.toInt(),
        )
    }
}

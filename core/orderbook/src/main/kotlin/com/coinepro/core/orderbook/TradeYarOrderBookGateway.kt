package com.coinepro.core.orderbook

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
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
    @GET("market/depth")
    suspend fun depth(
        @Query("symbol") symbol: String,
        @Query("depth") depth: Int,
    ): CryptoDepthDto
}

/**
 * The wire shape asked of TradeYar in `docs/SERVER_ASKS_DOM.md`, and nothing beyond it.
 *
 * Both sides arrive as `[price, quantity]` pairs because that is the shape LBank's own
 * `/v2/depth.do` uses, and the ask is for a relay rather than a re-modelling: every field the relay
 * invents is a field that can disagree with the exchange. Every property is nullable and every
 * default is empty — a route that is only specified and not yet built will answer something else
 * first, and a parser that throws on a missing field turns "not built yet" into a crash.
 */
internal data class CryptoDepthDto(
    val symbol: String? = null,
    val ts: Long? = null,
    val depth: Int? = null,
    val truncated: Boolean? = null,
    val bids: List<List<Double>> = emptyList(),
    val asks: List<List<Double>> = emptyList(),
)

/**
 * Crypto depth, relayed from LBank by TradeYar.
 *
 * ### It is built against a route that does not answer yet
 *
 * That is deliberate and it is the shape of the whole item. LBank publishes a public book, TradeYar
 * already relays that exchange's candles and quotes, and the depth relay is a small addition rather
 * than a new system — the ask is written out in `docs/SERVER_ASKS_DOM.md`. Until it lands, this
 * gateway's own 404 handling is what the reader sees, and it is the reason that handling is
 * specific: `404` and `501` here mean **the route is not served**, which is
 * [DepthUnavailableReason.ENDPOINT_NOT_SERVED] and reads on screen as "not yet", not as an outage.
 * Every other status is a real failure and is reported as one.
 *
 * ### The book is rebuilt, not trusted
 *
 * Everything from the wire goes through [OrderBook.of], which sorts both sides, sums duplicate
 * prices and drops rows the relay could not fill. A relay that hands back LBank's own ordering is
 * doing the right thing and this changes nothing; a relay that ever reorders, pages or merges is
 * caught here rather than on the ladder, where a mis-sorted book draws perfectly and lies.
 */
class TradeYarOrderBookGateway(
    retrofit: Retrofit,
    private val pollMillis: Long = DEFAULT_POLL_MILLIS,
) : OrderBookGateway {

    private val api = retrofit.create(CryptoDepthApi::class.java)

    // The exchange, not the relay. See `OrderBookGateway.sourceName`: a reader who wants to check
    // this ladder needs the name of the book it can be checked against.
    override val sourceName: String = "LBank"

    override suspend fun load(symbol: String, depth: Int): AppResult<OrderBook> = try {
        val requested = depth.coerceIn(1, MAX_DEPTH)
        val response = api.depth(symbol.uppercase(), requested)
        AppResult.Success(
            OrderBook.of(
                // The server's spelling of the symbol wins where it sent one, exactly as the candle
                // gateway does: a saved layout can carry an alternate spelling, and the venue's own
                // answer is the one to believe about which market these levels belong to.
                symbol = response.symbol ?: symbol,
                bids = response.bids.toLevels(),
                asks = response.asks.toLevels(),
                // No timestamp is not "now". A relay that omits it has told us nothing about the
                // age of the snapshot, and stamping it with the phone's clock would present an
                // unknown age as a fresh one — on the one screen where age is the whole question.
                at = response.ts ?: 0L,
                // Truncation is claimed by the server or inferred from the page being full. The
                // inference is the weaker signal and it errs toward saying "there is more", which
                // is the safe direction: a reader told the book continues has lost nothing.
                truncated = response.truncated
                    ?: (response.bids.size >= requested || response.asks.size >= requested),
            ),
        )
    } catch (error: HttpException) {
        when (error.code()) {
            404, 501 -> depthUnavailable(DepthUnavailableReason.ENDPOINT_NOT_SERVED)
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
     * Polls, because a snapshot route is what is being asked for.
     *
     * A socket would be better and is not what the ask requests: TradeYar's realtime channel is a
     * quote fan-out, and putting a full book on it every tick is a different piece of work with a
     * different bandwidth story. A second between snapshots is fast enough to watch a wall move and
     * slow enough not to be the reason a phone is warm — the cadence is stated in the server ask so
     * that both ends agree on it rather than discovering it under load.
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
        /** LBank's own ceiling for a public book page. Larger is truncated there, so it is clamped here. */
        const val MAX_DEPTH = 200

        /** One second between snapshots. See [stream]. */
        const val DEFAULT_POLL_MILLIS = 1_000L

        /** Roughly five seconds of silence at the default cadence before the stream gives up. */
        const val MAX_CONSECUTIVE_FAILURES = 5

        /**
         * `[price, quantity]` pairs to levels, dropping anything shorter than a pair.
         *
         * A one-element row is not a level at quantity zero — it is a row that arrived malformed,
         * and reading its missing half as zero would put a rung on the ladder with nothing behind
         * it. [OrderBook.of] drops the zeroes that get through; this drops the rows that never had
         * two numbers to begin with.
         */
        fun List<List<Double>>.toLevels(): List<DepthLevel> = mapNotNull { row ->
            if (row.size < 2) null else DepthLevel(price = row[0], quantity = row[1])
        }
    }
}

package com.coinepro.core.orderbook

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

internal interface LBankFuturesDepthApi {
    /**
     * `GET /cfd/openApi/v1/pub/marketOrder?symbol=&depth=` — the exchange's own perpetual-futures
     * book, published to anybody.
     *
     * This is not a route this app invented or guessed at. It is the **same endpoint TradeYar's
     * relay reads**: their `app/api/mobile/depth.py` holds it as `_DEPTH_PATH =
     * "/cfd/openApi/v1/pub/marketOrder"`, and `docs/SERVER_ASKS_DOM.md` records the same path as the
     * agreed source. So the book that arrives here is byte-for-byte the book the relay would have
     * relayed, from the same venue as this app's ticker and its candles — which is the requirement
     * written on [OrderBookGateway], not a nicety.
     *
     * `pub` is the exchange's own word for it: no key, no signature, no account. Measured against
     * the live host on 2026-08-31 — `depth=1`, `100`, `200`, `201` and `1000` each returned exactly
     * that many levels a side, bids already descending and asks already ascending, with `orders`
     * present on every row.
     */
    @GET("cfd/openApi/v1/pub/marketOrder")
    suspend fun marketOrder(
        @Query("symbol") symbol: String,
        @Query("depth") depth: Int,
    ): LBankDepthDto
}

/**
 * LBank's envelope, in which the interesting failures arrive with **HTTP 200 on them**.
 *
 * Named `…Dto` for the same reason every other wire model in this app is: R8 keeps
 * `com.coinepro.**Dto` outright, and a wire model that is only ever referenced through a Retrofit
 * return type is otherwise removed in a release build — after which Retrofit reads the erased
 * signature as `Continuation<Object>` and nothing parses. It is a naming convention doing load-
 * bearing work, and `app/proguard-rules.pro` says so at length.
 *
 * That is the whole reason this type exists rather than the handler reading the status code and
 * being done. Asked for a contract the exchange has retired, the host answers `200` with
 * `{"error_code": 20156, "msg": "This product has been delisted…", "result": "false"}` and no
 * `data` at all — measured, not assumed. An implementation that trusted the status would hand that
 * body to the parser, get an empty book out of it, and draw «در این لحظه سفارشی در دفتر نیست» over
 * a market that no longer exists: a claim about liquidity, made about a contract with none.
 *
 * Every field is nullable because every one of them is absent in some real answer, and a parser that
 * throws on a missing field turns a delisting into a crash report.
 */
internal data class LBankDepthDto(
    /** `0` is success. `20156` is the delisting TradeYar relay as `TYR-048`. See the handler. */
    @SerializedName("error_code")
    val errorCode: Int? = null,
    val msg: String? = null,
    val data: LBankDepthBookDto? = null,
)

/** Exactly three keys, and — as TradeYar measured before us — never a timestamp among them. */
internal data class LBankDepthBookDto(
    val symbol: String? = null,
    val bids: List<LBankDepthRowDto> = emptyList(),
    val asks: List<LBankDepthRowDto> = emptyList(),
)

/**
 * One level, as **strings**, which is how the exchange sends them and why the relay exists.
 *
 * `{"volume":"9.7759","price":"77766.4","orders":"1"}` is a real row from the live host. The relay's
 * own tests pin that it converts these to numbers before the app sees them — "what matters is that
 * the app never receives `\"100.5\"`" is their comment — so on the relayed path this shape is never
 * met. On this path it is, and the conversion happens in [toDepthLevels] instead.
 *
 * Kept as `String?` rather than declared `Double` and left to the parser: Gson would coerce a
 * well-formed numeric string quietly and throw on anything else, which turns one malformed row into
 * a whole book that fails to load. Read here, a row that will not parse is dropped and its
 * neighbours still draw.
 */
internal data class LBankDepthRowDto(
    val price: String? = null,
    val volume: String? = null,
    val orders: String? = null,
)

/**
 * The order book read straight from LBank, for the reader the relay will not serve.
 *
 * ### Why an app that has a relay reads the exchange directly
 *
 * It does not, normally. [TradeYarOrderBookGateway] is the path, and everything about it is better:
 * a 500 ms cache, a single-flight lock so a hundred phones on BTCUSDT collapse into two upstream
 * calls a second, a measured `truncated` flag, and one place to change when the venue changes. This
 * gateway is not a replacement for it and must never be promoted to one.
 *
 * It exists because of a hole the relay cannot close from its side.
 * `api/mobile/v1/market/depth` requires a TradeYar session and has **no public twin**, while the
 * chart and the price beside it do — `api/v1/public/candles/{symbol}` and
 * `api/v1/public/prices/{symbol}` both answer without a token. And
 * `docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md` records that no reader holds both platforms'
 * sessions: an account made before 1.27.0 exists on CoinePro-FX and not on TradeYar, one made in the
 * app is the reverse, and a guest holds neither. So for a large share of readers the crypto chart
 * drew, the price ticked, and «عمق بازار» answered `401` — which the app then dressed up as a
 * connection problem. See [DepthUnavailableReason.SESSION_REQUIRED] for the measurements.
 *
 * The book itself was never the private part. The exchange publishes it to anybody, the relay reads
 * exactly this endpoint, and the reader is entitled to the same numbers whether or not the platform
 * knows who they are.
 *
 * ### What this must never carry
 *
 * **No credential of this app's ever goes to this host.** It is built on its own [OkHttpClient] for
 * that single reason: the platform clients attach the reader's bearer to every request they make,
 * and pointing one of those at an exchange would post a TradeYar session token to a third party on a
 * one-second timer. The client here has no auth interceptor, no install id and no app-version
 * header — nothing that identifies the reader — because the request needs none of it and a public
 * endpoint is not a place to volunteer such things.
 *
 * ### What it gives up, said plainly
 *
 * There is no cache in front of this and no single-flight behind it, so a phone polling once a
 * second is one request a second at the venue rather than a share of two. TradeYar measured LBank
 * for us at sixty concurrent requests answered sixty times in 0.7 s with no rate limiter found, so
 * the cadence is kept at the relay's — a reader should not be able to tell which source answered by
 * watching the ladder move. Staleness is the one visible difference and it is handled by saying
 * nothing: [OrderBook.maxAgeMillis] stays null here, because the relay's `cache_ttl_ms` is a bound
 * on *its* cache and this path has no cache to bound. An unstated age is honest; a borrowed one is
 * not.
 */
class LBankPublicOrderBookGateway(
    retrofit: Retrofit = defaultRetrofit(),
    private val pollMillis: Long = DEFAULT_POLL_MILLIS,
) : OrderBookGateway {

    private val api = retrofit.create(LBankFuturesDepthApi::class.java)

    // The same words the relayed path uses, because it is the same book from the same half of the
    // same exchange. A second name for one venue would tell a reader checking the ladder against
    // LBank that the app had two different ideas about where its numbers come from.
    override val sourceName: String = LBANK_FUTURES_VENUE

    override suspend fun load(symbol: String, depth: Int): AppResult<OrderBook> = try {
        val requested = depth.coerceIn(1, MAX_DEPTH)
        // One level more than will be kept, which is how `truncated` becomes a measurement instead
        // of the "was the page full" guess it would otherwise be. LBank honours the requested depth
        // exactly and never truncates silently — verified at 1, 100, 200, 201 and 1000 levels — so
        // an extra level coming back means the book genuinely continues past the page. This is the
        // relay's own technique, kept here so the flag means the same thing on both paths.
        val envelope = api.marketOrder(symbol.uppercase(), requested + 1)
        val data = envelope.data
        when {
            envelope.errorCode == DELISTED -> depthUnavailable(DepthUnavailableReason.SYMBOL_DELISTED)
            // A non-zero code the app has no name for is the venue refusing, not the venue being
            // absent, so it keeps its retry. Null is read as success because the field is absent on
            // some healthy answers and a missing code is not a reported fault.
            envelope.errorCode != null && envelope.errorCode != OK -> {
                depthOutage(DepthOutageReason.EXCHANGE_UNREACHABLE)
            }
            data == null -> depthOutage(DepthOutageReason.EXCHANGE_UNREACHABLE)
            else -> AppResult.Success(
                OrderBook.of(
                    // The venue's own spelling wins where it sent one, exactly as on the relayed
                    // path: a saved layout can carry an alternate spelling and the exchange is the
                    // authority on which market these levels belong to.
                    symbol = data.symbol ?: symbol,
                    bids = data.bids.toDepthLevels(),
                    asks = data.asks.toDepthLevels(),
                    at = NO_VENUE_TIME,
                    // No cache in front of this call, so there is no declared bound on the age and
                    // none is invented. See the note on the class.
                    maxAgeMillis = null,
                    // Cut back to what was asked for *after* sorting, so the levels that survive are
                    // the ones nearest the touch whatever order they arrived in, and `truncated` is
                    // set by the cut rather than asserted. `top` returns the book unchanged when
                    // nothing needed cutting, which is precisely "the whole book fitted".
                ).top(requested),
            )
        }
    } catch (error: HttpException) {
        when (error.code()) {
            429 -> AppResult.Failure(ErrorKind.RATE_LIMIT, cause = error)
            in 500..599 -> depthOutage(DepthOutageReason.EXCHANGE_UNREACHABLE)
            else -> AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
        }
    } catch (error: IOException) {
        // The likeliest failure on this path by a distance, and it is not the reader's router: this
        // app's readers are largely in Iran and an exchange host is exactly the kind of address that
        // is filtered there. `NETWORK` keeps the retry and keeps the generic sentence, which for a
        // request that never left the phone is the true one.
        AppResult.Failure(ErrorKind.NETWORK, cause = error)
    } catch (error: Throwable) {
        AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
    }

    /**
     * Polls at the relay's cadence, for the reasons written on [TradeYarOrderBookGateway.stream].
     *
     * The two rules that keep a ladder from lying are the same here and are the reason this is a
     * loop rather than a single fetch: a refusal ends the flow at once so the screen can say so, and
     * a run of [MAX_CONSECUTIVE_FAILURES] failures ends it too, because past that point the rows on
     * screen are a book from a minute ago being presented as live.
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
    }.distinctUntilChanged()

    companion object {
        /**
         * The exchange's perpetual-futures host, which is not the host its spot API lives on.
         *
         * Checked rather than assumed, on 2026-08-31: `/cfd/openApi/v1/pub/marketOrder` answers
         * `200` here and `404` on `api.lbkex.com` and `api.lbank.info`, which are the spot
         * addresses. The trailing slash is Retrofit's requirement for a base URL.
         */
        private const val BASE_URL = "https://lbkperp.lbank.com/"

        /**
         * `error_code` on a healthy answer.
         *
         * Worth a name because the field is the only thing separating a book from a refusal on this
         * endpoint: the HTTP status is `200` either way.
         */
        private const val OK = 0

        /** `error_code 20156`: a contract this exchange has retired. TradeYar relay it as `TYR-048`. */
        private const val DELISTED = 20156

        /**
         * The same ceiling the relay enforces, deliberately, although the venue serves far more.
         *
         * LBank answered a thousand levels a side without complaint, so this cap is not the
         * exchange's — it is TradeYar's contractual `depth` maximum, kept here so that the ladder and
         * the depth curve are drawn from a book of the same size whichever source answered. A reader
         * should not be able to tell which path served them by counting the levels under the curve.
         */
        private const val MAX_DEPTH = 200

        /** One second between snapshots, matching the relayed path. See [stream]. */
        private const val DEFAULT_POLL_MILLIS = 1_000L

        /** Roughly five seconds of silence at the default cadence before the stream gives up. */
        private const val MAX_CONSECUTIVE_FAILURES = 5

        /**
         * LBank's futures book publishes no time of its own, so [OrderBook.at] is zero here too.
         *
         * Named rather than written as a bare `0L` so it reads as a decision about the venue rather
         * than a placeholder. Its `data` object is exactly `symbol`, `asks`, `bids` at every depth —
         * TradeYar measured that before this gateway existed and it is still true.
         */
        private const val NO_VENUE_TIME = 0L

        /**
         * A client that carries nothing about the reader, built here so it cannot be handed one that
         * does.
         *
         * The parameter is a default rather than a hard-wired field so a test can point this at a
         * local server, and it is a *default* rather than a required argument so the injector cannot
         * absent-mindedly pass one of the platform clients. Those attach the reader's bearer to
         * every request they make; this host must never receive one. See the note on the class.
         *
         * The timeouts are shorter than the platform clients' thirty seconds on purpose. This is a
         * fallback on a one-second poll, and a request still waiting after ten has already been
         * overtaken by the next one — holding it open only stacks sockets against a venue the app
         * has no relationship with.
         */
        fun defaultRetrofit(): Retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build(),
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

/**
 * String rows to levels, dropping anything that will not read as a pair of numbers.
 *
 * A row whose price or volume does not parse is not a level at zero — it is a row that arrived
 * malformed, and reading its missing half as zero would put a rung on the ladder with nothing behind
 * it. [OrderBook.of] drops the zeroes that get through; this drops the rows that never held two
 * numbers.
 *
 * ### The order count keeps the same rule it has on the relayed path
 *
 * Absent, unparseable, zero or negative all mean **not known**, and all of them give null. Null and
 * one are different facts about a price and neither the drawing nor the spoken text may flatten one
 * into the other: a level of forty with a single order behind it is one participant who can withdraw
 * the whole wall in one message, and a level whose count nobody published says nothing about that.
 * `orders: 0` beside a quantity that is plainly there would claim liquidity nobody placed.
 *
 * The exchange sends the count as a string here — `"orders":"1"` — where the relay converts it to a
 * number first. Both end in the same nullable `Int`, which is what the ladder reads, so a reader
 * cannot tell the two paths apart by what the bars say.
 *
 * Internal rather than private so the wire shape can be pinned by a test against the venue's own
 * payload. The gateway is the only caller.
 */
internal fun List<LBankDepthRowDto>.toDepthLevels(): List<DepthLevel> = mapNotNull { row ->
    val price = row.price?.toDoubleOrNull()
    val quantity = row.volume?.toDoubleOrNull()
    if (price == null || quantity == null) {
        null
    } else {
        DepthLevel(
            price = price,
            quantity = quantity,
            orders = row.orders?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 1.0 }?.toInt(),
        )
    }
}

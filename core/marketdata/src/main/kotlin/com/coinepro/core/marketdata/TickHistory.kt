package com.coinepro.core.marketdata

import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * One trade, as the venue printed it (5.17.0). [tMs] is unix **milliseconds** — a tick is finer
 * than a second, and two in the same second are two ticks, not one.
 */
data class Tick(
    val tMs: Long,
    val price: Double,
    val volume: Double = 0.0,
    /** `buy`, `sell`, or empty where the venue does not say who crossed the spread. */
    val side: String = "",
)

/**
 * Trades and sub-minute bars from the server (5.17.0) — the history a seconds or tick chart never
 * had. Until this, a ten-second chart began empty and grew only from the price feed while it was
 * open; now it opens on the venue's own recent bars, and a tick chart exists at all.
 *
 * Both venues are served from CoinePro-FX's public route: crypto from the exchange's own trades,
 * forex and metals from the broker feed's ticks the server now keeps.
 */
interface TickHistory {

    /** The newest [limit] trades before [beforeMs] (exclusive), oldest first. Empty when there are none. */
    suspend fun ticks(symbol: String, limit: Int = DEFAULT_TICKS, beforeMs: Long? = null): List<Tick>

    /** [seconds]-second bars before [before] (unix seconds, exclusive), oldest first. */
    suspend fun seconds(symbol: String, seconds: Int, limit: Int = DEFAULT_BARS, before: Long? = null): List<OhlcBar>

    companion object {
        const val DEFAULT_TICKS: Int = 2_000
        const val DEFAULT_BARS: Int = 500
    }
}

internal interface TickApi {
    @GET("public/market/ticks")
    suspend fun ticks(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int,
        @Query("before") before: Long?,
    ): TickPageDto

    @GET("public/market/seconds")
    suspend fun seconds(
        @Query("symbol") symbol: String,
        @Query("seconds") seconds: Int,
        @Query("limit") limit: Int,
        @Query("before") before: Long?,
    ): SecondsPageDto
}

internal data class TickDto(val t: Long = 0, val p: Double = 0.0, val v: Double = 0.0, val s: String? = null)

internal data class TickPageDto(val symbol: String? = null, val source: String? = null, val ticks: List<TickDto> = emptyList())

internal data class SecondsBarDto(
    val t: Long = 0,
    val o: Double = 0.0,
    val h: Double = 0.0,
    val l: Double = 0.0,
    val c: Double = 0.0,
    val v: Double = 0.0,
    val closed: Boolean? = null,
)

internal data class SecondsPageDto(
    val symbol: String? = null,
    val seconds: Int? = null,
    val source: String? = null,
    val candles: List<SecondsBarDto> = emptyList(),
)

/** [TickHistory] over CoinePro-FX's public route. Failures are empty lists: a chart with nothing is not an error. */
class CoineProFxTickHistory(retrofit: Retrofit) : TickHistory {

    private val api = retrofit.create(TickApi::class.java)

    override suspend fun ticks(symbol: String, limit: Int, beforeMs: Long?): List<Tick> = runCatching {
        api.ticks(symbol.uppercase(), limit.coerceIn(1, MAX_TICKS), beforeMs).ticks
            .filter { it.t > 0 && it.p.isFinite() && it.p > 0.0 }
            .map { Tick(it.t, it.p, it.v, it.s.orEmpty()) }
            .sortedBy(Tick::tMs)
    }.getOrDefault(emptyList())

    override suspend fun seconds(symbol: String, seconds: Int, limit: Int, before: Long?): List<OhlcBar> = runCatching {
        api.seconds(symbol.uppercase(), seconds, limit.coerceIn(1, MAX_BARS), before).candles
            .filter { it.t > 0 && it.c.isFinite() && it.c > 0.0 }
            .map { OhlcBar(t = it.t, o = it.o, h = it.h, l = it.l, c = it.c, v = it.v, closed = it.closed ?: true) }
            .sortedBy(OhlcBar::t)
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_TICKS = 5_000
        const val MAX_BARS = 1_000
    }
}

/**
 * Tick bars — TradingView's `1T`, `100T`: a bar is every [count] trades, whatever the clock says.
 *
 * The bar's time is its first trade's, in seconds, so two bars can share a second on a busy market;
 * the series stays in order because trades arrive in order.
 */
object TickBars {

    /** [ticks] folded into bars of [count] trades, and how many trades the last bar holds. */
    fun fold(ticks: List<Tick>, count: Int): Pair<List<OhlcBar>, Int> {
        if (count <= 0 || ticks.isEmpty()) return emptyList<OhlcBar>() to 0
        val bars = ArrayList<OhlcBar>(ticks.size / count + 1)
        var filled = 0
        for (tick in ticks) {
            if (filled == 0) {
                bars += OhlcBar(tick.tMs / 1_000L, tick.price, tick.price, tick.price, tick.price, tick.volume, closed = false)
            } else {
                val last = bars.last()
                bars[bars.lastIndex] = last.copy(
                    h = maxOf(last.h, tick.price),
                    l = minOf(last.l, tick.price),
                    c = tick.price,
                    v = last.v + tick.volume,
                )
            }
            filled += 1
            if (filled == count) {
                bars[bars.lastIndex] = bars.last().copy(closed = true)
                filled = 0
            }
        }
        return bars to filled
    }

    /**
     * [bars] with [ticks] added to the end, [filled] being how many trades the last bar already
     * holds — the live edge of a tick chart. Returns the new bars and the new fill.
     */
    fun append(bars: List<OhlcBar>, filled: Int, ticks: List<Tick>, count: Int): Pair<List<OhlcBar>, Int> {
        if (ticks.isEmpty() || count <= 0) return bars to filled
        val out = bars.toMutableList()
        var inLast = if (out.isEmpty()) 0 else filled
        for (tick in ticks) {
            if (inLast == 0 || inLast >= count) {
                if (out.isNotEmpty()) out[out.lastIndex] = out.last().copy(closed = true)
                out += OhlcBar(tick.tMs / 1_000L, tick.price, tick.price, tick.price, tick.price, tick.volume, closed = false)
                inLast = 1
            } else {
                val last = out.last()
                out[out.lastIndex] = last.copy(h = maxOf(last.h, tick.price), l = minOf(last.l, tick.price), c = tick.price, v = last.v + tick.volume)
                inLast += 1
            }
        }
        if (inLast >= count) {
            out[out.lastIndex] = out.last().copy(closed = true)
            inLast = 0
        }
        return out to inLast
    }
}

package com.coinepro.core.marketdata

import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One start every [intervalMillis], shared by everybody holding the same pacer.
 *
 * The heat map reads up to two hundred and twenty markets' bars and the screener a few hundred, four
 * at a time each. Opened one after the other they put some four hundred candle requests on one
 * address inside a minute, and both servers cap an address per minute: the browser check of 5.24.2
 * counted 219 refusals with 429, and the tiles and columns those answers were for read «–».
 */
class RequestPacer(private val intervalMillis: Long, private val nowMillis: () -> Long = System::currentTimeMillis) {
    private val lock = Mutex()
    private var next = 0L

    /** Suspends until this caller's turn. */
    suspend fun awaitTurn() {
        val wait = lock.withLock {
            val now = nowMillis()
            val start = maxOf(now, next)
            next = start + intervalMillis
            start - now
        }
        if (wait > 0) delay(wait)
    }

    companion object {
        /** The bulk readers' share of each server's per-address minute (5.24.3). */
        private const val BULK_PER_MINUTE = 45

        /** TradeYar, signed in or not: one address, one cap. */
        val crypto = RequestPacer(60_000L / BULK_PER_MINUTE)

        /** CoinePro-FX. */
        val forex = RequestPacer(60_000L / BULK_PER_MINUTE)
    }
}

/**
 * [gateway], paced by symbol: crypto through [RequestPacer.crypto], everything else through
 * [RequestPacer.forex]. For the bulk readers — the heat map and the screener — and never a chart,
 * which is one request a reader is waiting on.
 */
class PacedCandleGateway(private val gateway: CandleGateway) : CandleGateway {

    override val sourceName: String get() = gateway.sourceName
    override val nativeTimeframes: List<Timeframe> get() = gateway.nativeTimeframes
    override val sourceLimitMax: Int get() = gateway.sourceLimitMax

    private fun pacerFor(symbol: String): RequestPacer =
        if (SymbolClassifier.classify(symbol).category == SymbolCategory.CRYPTO) RequestPacer.crypto else RequestPacer.forex

    override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
        pacerFor(symbol).awaitTurn()
        return gateway.load(symbol, timeframe, limit, before)
    }

    override suspend fun load(
        symbol: String,
        interval: ChartInterval,
        limit: Int,
        before: Long?,
        zone: ZoneId,
    ): CandlePage {
        pacerFor(symbol).awaitTurn()
        return gateway.load(symbol, interval, limit, before, zone)
    }
}

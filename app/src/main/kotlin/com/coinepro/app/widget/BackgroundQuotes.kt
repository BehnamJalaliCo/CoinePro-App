package com.coinepro.app.widget

import com.coinepro.app.di.ForexPlatform
import com.coinepro.core.common.AppResult
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.core.symbols.SymbolMeta
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Prices for work done with the app closed — the widgets and the market notifications (5.27.0).
 *
 * Crypto comes from TradeYar's public price route in one call, as it always did. Forex, gold and
 * the indices were missing from the widget altogether: that route does not carry them. They come
 * from the forex platform's daily bars instead — the last close against the one before — on the
 * guest credential, so a reader with no forex account still sees gold on their home screen.
 */
@Singleton
class BackgroundQuotes @Inject constructor(
    private val guest: GuestGateway,
    @ForexPlatform private val forexCandles: CandleGateway,
) {

    data class Quote(
        val symbol: String,
        val price: Double,
        /** The day's move in percent, or null where nothing said what it was. */
        val changePercent: Double?,
    )

    /** What one read came to: the quotes found, and whether the crypto route called itself stale. */
    data class Read(val quotes: Map<String, Quote>, val stale: Boolean, val failed: Boolean)

    suspend fun read(symbols: List<String>): Read = coroutineScope {
        val wanted = symbols.map { it.trim().uppercase() }.filter(String::isNotEmpty).distinct()
        val (crypto, other) = wanted.partition { SymbolClassifier.classify(it).isCrypto() }
        val cryptoRead = async { if (crypto.isEmpty()) CryptoRead(emptyMap(), stale = false, failed = false) else crypto(crypto) }
        val forexRead = other.take(MAX_FOREX).map { symbol -> async { forex(symbol) } }
        val cryptoResult = cryptoRead.await()
        val forex = forexRead.awaitAll().filterNotNull().associateBy(Quote::symbol)
        Read(
            quotes = cryptoResult.quotes + forex,
            stale = cryptoResult.stale,
            failed = cryptoResult.failed && forex.isEmpty(),
        )
    }

    /** The last [count] hourly closes, oldest first, for a notification's line; empty on failure. */
    suspend fun hourly(symbol: String, count: Int = 24): List<Double> = try {
        val upper = symbol.uppercase()
        if (SymbolClassifier.classify(upper).isCrypto()) {
            when (val result = guest.candles(upper, "1h", count)) {
                is AppResult.Success -> result.value.candles.map { it.close }
                is AppResult.Failure -> emptyList()
            }
        } else {
            forexCandles.load(upper, Timeframe.H1, count, null).candles.map { it.c }
        }.takeLast(count)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        emptyList()
    }

    private data class CryptoRead(val quotes: Map<String, Quote>, val stale: Boolean, val failed: Boolean)

    private suspend fun crypto(symbols: List<String>): CryptoRead = when (val result = guest.prices(symbols)) {
        is AppResult.Success -> CryptoRead(
            quotes = result.value.quotes.associate { quote ->
                quote.symbol.uppercase() to Quote(quote.symbol.uppercase(), quote.price, quote.changePercent24h)
            },
            stale = result.value.stale,
            failed = false,
        )
        is AppResult.Failure -> CryptoRead(emptyMap(), stale = true, failed = true)
    }

    private suspend fun forex(symbol: String): Quote? = try {
        val bars = forexCandles.load(symbol, Timeframe.D1, FOREX_BARS, null).candles
        val last = bars.lastOrNull()
        val previous = bars.getOrNull(bars.size - 2)
        last?.let {
            Quote(
                symbol = symbol,
                price = it.c,
                changePercent = previous?.c?.takeIf { close -> close != 0.0 }?.let { close -> (it.c - close) / close * 100.0 },
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        null
    }

    private fun SymbolMeta.isCrypto(): Boolean = category == SymbolCategory.CRYPTO

    private companion object {
        /** The forex symbols read per pass, one request each: a long list is truncated, not paced. */
        const val MAX_FOREX = 8
        const val FOREX_BARS = 3
    }
}

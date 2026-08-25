package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketQuote

enum class MarketConnectionState {
    IDLE,
    CONNECTING,
    LIVE,
    DEGRADED,
    OFFLINE,
}

enum class MarketDataOrigin {
    NONE,
    CACHE,
    NETWORK,
}

data class MarketDataState(
    val connection: MarketConnectionState = MarketConnectionState.IDLE,
    val quotes: Map<String, MarketQuote> = emptyMap(),
    val lastServerTimeEpochMillis: Long? = null,
    val lastError: String? = null,
    val origin: MarketDataOrigin = MarketDataOrigin.NONE,
    val cacheStoredAtEpochMillis: Long? = null,
)

/**
 * What each platform quotes, and nothing else.
 *
 * There is deliberately **no** combined list. A single `default` containing both the metals and the
 * USDT pairs is how gold ended up in a crypto watchlist: every caller that took the default got a
 * mixed feed, and nothing in the type system objected. Asking for symbols now means naming the
 * platform, so a mixed list cannot be produced by omission.
 *
 * TradeYar is quoted by LBank over its realtime socket; CoinePro-FX by Finnhub, with MetaTrader 5
 * as the execution-side source. Those are different feeds with different symbol spellings, which is
 * the second reason these lists must not merge.
 */
object MarketDataSymbols {

    /** TradeYar — crypto, USDT-quoted, from LBank. */
    val crypto: List<String> = listOf(
        "BTCUSDT",
        "ETHUSDT",
        "SOLUSDT",
        "BNBUSDT",
        "XRPUSDT",
        "ADAUSDT",
        "DOGEUSDT",
        "TRXUSDT",
    )

    /** CoinePro-FX — forex and metals, from Finnhub. */
    val forex: List<String> = listOf(
        "XAUUSD",
        "XAGUSD",
    )

    fun forPlatform(platform: MarketPlatform): List<String> = when (platform) {
        MarketPlatform.TRADEYAR -> crypto
        MarketPlatform.COINEPRO_FX -> forex
    }
}

package com.coinepro.core.model

/**
 * The two independent backends the product runs on.
 *
 * They are separate systems, not two regions of one: each builds its own signals, keeps its own
 * user table, and issues its own credentials. A person with a CoinePro-FX account is not the same
 * account as a TradeYar one, and holding one says nothing about holding the other. Every network
 * call, stored token and session state is therefore scoped by this value.
 *
 * [MarketType] describes what an instrument *is*; this describes *who serves it*. They line up
 * today — CoinePro-FX is Forex-only by design and TradeYar is Crypto — but they are different
 * questions and should not be collapsed.
 */
enum class MarketPlatform(
    /** Stable key for storage, preferences and BuildConfig lookup. Never localise or reuse. */
    val id: String,
    /** The instruments this platform serves. */
    val marketType: MarketType,
) {
    /** CoinePro-FX — Forex. Gold and silver in the current product scope. */
    COINEPRO_FX("coinepro_fx", MarketType.FOREX),

    /** TradeYar — Crypto. USDT pairs. */
    TRADEYAR("tradeyar", MarketType.CRYPTO),
    ;

    companion object {
        fun forMarket(marketType: MarketType): MarketPlatform =
            entries.first { it.marketType == marketType }

        fun fromId(id: String): MarketPlatform? = entries.firstOrNull { it.id == id }
    }
}

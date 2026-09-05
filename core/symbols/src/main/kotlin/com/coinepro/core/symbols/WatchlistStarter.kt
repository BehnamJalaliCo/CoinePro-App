package com.coinepro.core.symbols

/**
 * The markets a fresh install's watchlist opens with.
 *
 * Seven on each side, because the app serves two platforms out of one list and a reader on either
 * should find a full screen rather than the other platform's leftovers: the shell shows the half
 * that belongs to the platform on screen (see `CoineProApp.belongsTo`). Every one of these has
 * artwork — `WatchlistStarterTest` holds that — so the first list a reader ever sees is never a
 * blank square, which is the standing rule for every list in the app.
 *
 * Kept here rather than in the store because the store cannot see [SymbolArtwork], and a seed the
 * artwork does not cover would be filtered out of the very list it was meant to fill.
 */
object WatchlistStarter {
    /** TradeYar — the seven most-traded USDT pairs on LBank, in the order a reader expects them. */
    val CRYPTO: List<String> = listOf(
        "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT",
    )

    /** CoinePro-FX — gold, silver and the five majors every MT5 account carries. */
    val FOREX: List<String> = listOf(
        "XAUUSD", "XAGUSD", "EURUSD", "GBPUSD", "USDJPY", "AUDUSD", "USDCHF",
    )

    /** Both halves, crypto first: the fallback platform is TradeYar. */
    val ALL: List<String> = CRYPTO + FOREX
}

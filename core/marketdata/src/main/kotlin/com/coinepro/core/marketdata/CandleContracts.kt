package com.coinepro.core.marketdata

/**
 * The eight timeframes both backends serve.
 *
 * One enum for both, because they agree on the spellings — `M1`, `H4`, `D1` — and a reader
 * switching between a gold chart and a Bitcoin chart must not find the control relabelled. Where
 * they differ is in what they *accept*: TradeYar also takes `1h`/`15m` and echoes back the
 * canonical spelling, CoinePro-FX takes only these. Sending the canonical spelling to both is the
 * intersection, and costs nothing.
 */
enum class Timeframe(val wire: String, val seconds: Long, val label: String) {
    M1("M1", 60, "۱ دقیقه"),
    M5("M5", 300, "۵ دقیقه"),
    M15("M15", 900, "۱۵ دقیقه"),
    M30("M30", 1_800, "۳۰ دقیقه"),
    H1("H1", 3_600, "۱ ساعت"),
    H4("H4", 14_400, "۴ ساعت"),
    D1("D1", 86_400, "۱ روز"),
    W1("W1", 604_800, "۱ هفته");

    companion object {
        /** Tolerant of either spelling, because a saved layout may carry the other one. */
        fun of(wire: String?): Timeframe? {
            val clean = wire?.trim()?.uppercase() ?: return null
            entries.firstOrNull { it.wire == clean }?.let { return it }
            // `15M` / `1H` — TradeYar's alternate spelling, reversed.
            val alternate = Regex("^(\\d+)([MHDW])$").matchEntire(clean) ?: return null
            return entries.firstOrNull { it.wire == alternate.groupValues[2] + alternate.groupValues[1] }
        }
    }
}

/**
 * One bar, on the wire.
 *
 * Deliberately not `core:chart`'s `Candle`, and the boundary is worth keeping: `core:chart` is a
 * Compose module, and a network layer that depends on it drags a UI toolkit into every gateway.
 * The mapping is four fields wide and lives at the one call site that needs it.
 *
 * [t] is **unix seconds** and is the bar's *open* time. Both backends confirmed that in writing,
 * and both contrasted it with their own price sockets, which use milliseconds — so the one thing
 * that must not happen here is a stray ×1000.
 */
data class OhlcBar(
    val t: Long,
    val o: Double,
    val h: Double,
    val l: Double,
    val c: Double,
    val v: Double,
    /**
     * Whether the bar has finished.
     *
     * TradeYar sends this per bar; CoinePro-FX does not send it at all, and there it is derived
     * from the bar's own open time against the server clock. Either way the last bar of a live
     * chart is usually `false`, and a reader who does not know that will read a half-formed bar as
     * a real one.
     */
    val closed: Boolean = true,
)

/**
 * One page of history, and enough to ask for the next one without guessing.
 *
 * [oldest] and [hasMore] are TradeYar's, and they are the difference between paging that works and
 * paging that stops one page early: without them a caller has to infer "there is more" from the
 * page being full, which is wrong exactly when the last page happens to be full.
 */
data class CandlePage(
    val symbol: String,
    val timeframe: Timeframe,
    /** Oldest first. Both backends promise it and one of them enforces it server-side. */
    val candles: List<OhlcBar>,
    val oldest: Long? = null,
    val hasMore: Boolean = false,
    /** The server's own cap, so the app does not have to carry a number that can change. */
    val limitMax: Int? = null,
) {
    val isEmpty: Boolean get() = candles.isEmpty()
}

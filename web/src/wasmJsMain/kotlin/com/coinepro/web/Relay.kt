package com.coinepro.web

import com.coinepro.core.chart.Candle

/*
 * The terminal's side of pro-chart.com's relay (docs/web/SERVER.md §4.1).
 *
 * Same-origin paths, so the page works wherever it is served from and never names a host. The
 * relay passes each upstream's shape through unchanged, by its own rule 2, so the adapting happens
 * here — the same place `GuestCandleGateway` does it on the phone.
 */

/** Where an instrument's candles and prices come from. */
enum class Venue { CRYPTO, FOREX }

/**
 * An instrument the terminal offers.
 *
 * A short list, chosen, rather than the venue's 862: the terminal's first release is a chart, and
 * a picker over every pair a venue quotes is the screener's job (W2 item 6). Every entry was
 * fetched from the live relay on 2026-09-23 and answered with candles.
 */
data class Instrument(val symbol: String, val venue: Venue, val display: String)

val Instruments = listOf(
    Instrument("BTCUSDT", Venue.CRYPTO, "BTC/USDT"),
    Instrument("ETHUSDT", Venue.CRYPTO, "ETH/USDT"),
    Instrument("SOLUSDT", Venue.CRYPTO, "SOL/USDT"),
    Instrument("BNBUSDT", Venue.CRYPTO, "BNB/USDT"),
    Instrument("XRPUSDT", Venue.CRYPTO, "XRP/USDT"),
    Instrument("DOGEUSDT", Venue.CRYPTO, "DOGE/USDT"),
    Instrument("XAUUSD", Venue.FOREX, "XAU/USD"),
    Instrument("XAGUSD", Venue.FOREX, "XAG/USD"),
    Instrument("EURUSD", Venue.FOREX, "EUR/USD"),
    Instrument("GBPUSD", Venue.FOREX, "GBP/USD"),
    Instrument("USDJPY", Venue.FOREX, "USD/JPY"),
)

/**
 * A timeframe, in the three spellings it has: the address bar's, the crypto route's, the forex
 * route's.
 *
 * Four, because the forex route answers four — `M15 H1 H4 D1` return 200 and `M5 M30 W1` return
 * 422, measured (SERVER.md §4.1). Offering a fifth that works on half the instruments would be a
 * button that is sometimes a dead end.
 */
enum class Timeframe(val path: String, val crypto: String, val forex: String, val seconds: Long) {
    M15("15m", "15m", "M15", 15 * 60L),
    H1("1h", "1h", "H1", 60 * 60L),
    H4("4h", "4h", "H4", 4 * 60 * 60L),
    D1("1d", "1d", "D1", 24 * 60 * 60L),
    ;

    companion object {
        fun fromPath(value: String?): Timeframe? = entries.firstOrNull { it.path.equals(value, ignoreCase = true) }
    }
}

/** Candles for the chart, oldest first, or null when the relay could not give any. */
suspend fun loadCandles(instrument: Instrument, timeframe: Timeframe): List<Candle>? {
    val url = when (instrument.venue) {
        Venue.CRYPTO -> "/api/crypto/candles?symbol=${instrument.symbol}&tf=${timeframe.crypto}&limit=$CRYPTO_LIMIT"
        // The forex route bounds `limit` to 20..400 and answers 422 outside it (SERVER.md §4.1).
        Venue.FOREX -> "/api/fx/candles?symbol=${instrument.symbol}&timeframe=${timeframe.forex}&limit=$FOREX_LIMIT"
    }
    val body = fetchText(url) ?: return null
    val root = parseJson(body) ?: return null
    val rows = jsonArray(root, "candles") ?: return null
    val candles = ArrayList<Candle>(jsonLength(rows))
    for (index in 0 until jsonLength(rows)) {
        val row = jsonItem(rows, index)
        // Crypto stamps a bar in epoch seconds, forex in an ISO-8601 string. Both are read, and a
        // bar with neither is dropped rather than placed at 1970.
        val seconds = jsonNumber(row, "t").takeUnless { it.isNaN() }
            ?: jsonString(row, "t")?.let(::isoToEpochSeconds)?.takeUnless { it.isNaN() }
            ?: continue
        val open = jsonNumber(row, "o")
        val high = jsonNumber(row, "h")
        val low = jsonNumber(row, "l")
        val close = jsonNumber(row, "c")
        if (open.isNaN() || high.isNaN() || low.isNaN() || close.isNaN()) continue
        // Forex carries no volume, and none is invented: a missing volume is null, not zero.
        val volume = jsonNumber(row, "v").takeUnless { it.isNaN() }
        candles += Candle(t = seconds.toLong(), o = open, h = high, l = low, c = close, v = volume)
    }
    return candles.sortedBy { it.t }.distinctBy { it.t }.takeIf { it.isNotEmpty() }
}

/**
 * The instrument's last price from the venue's snapshot, or null.
 *
 * Crypto rows are `{s, p}` under `data`; forex rows are `{raw_symbol, price}` under `items`. Both
 * snapshots are the relay's cached reads — 2 s and 5 s — so polling them is what the relay was
 * built for, and the 240-a-minute bucket is theirs (SERVER.md §6).
 */
suspend fun loadPrice(instrument: Instrument): Double? {
    val (url, list, symbolKey, priceKey) = when (instrument.venue) {
        Venue.CRYPTO -> Quad("/api/crypto/prices", "data", "s", "p")
        Venue.FOREX -> Quad("/api/fx/prices", "items", "raw_symbol", "price")
    }
    val root = fetchText(url)?.let(::parseJson) ?: return null
    val rows = jsonArray(root, list) ?: return null
    return priceIn(rows, symbolKey, priceKey, instrument.symbol).takeUnless { it.isNaN() || it <= 0.0 }
}

private data class Quad(val url: String, val list: String, val symbolKey: String, val priceKey: String)

/**
 * The bars with [price] applied at [nowSeconds]: the forming bar moved, or a new one opened.
 *
 * The same rule the phone's live feed follows. A price inside the forming bar's interval moves its
 * close and stretches its high or low; a price past it opens a bar at the next boundary, flat at
 * that price. Nothing is back-filled: a gap the snapshot did not see stays a gap until the next candle
 * fetch, which replaces the whole series with the venue's own bars.
 */
fun applyPrice(bars: List<Candle>, price: Double, nowSeconds: Long, timeframe: Timeframe): List<Candle> {
    val last = bars.lastOrNull() ?: return bars
    // Counted from the venue's own last stamp rather than from midnight UTC, so a broker whose day
    // opens at another hour still gets its bars where it puts them.
    val elapsed = nowSeconds - last.t
    return if (elapsed < timeframe.seconds) {
        val moved = last.copy(c = price, h = maxOf(last.h, price), l = minOf(last.l, price))
        if (moved == last) bars else bars.dropLast(1) + moved
    } else {
        val opened = last.t + (elapsed / timeframe.seconds) * timeframe.seconds
        bars + Candle(t = opened, o = price, h = price, l = price, c = price, v = if (last.v != null) 0.0 else null)
    }
}

private const val CRYPTO_LIMIT = 500
private const val FOREX_LIMIT = 400

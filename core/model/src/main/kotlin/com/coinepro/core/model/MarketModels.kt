package com.coinepro.core.model

enum class MarketType { FOREX, CRYPTO }

enum class SignalDirection { BUY, SELL, NEUTRAL }

enum class QuoteSource {
    FINNHUB,
    LBANK,
    UNKNOWN,
    ;

    /**
     * The venue's own spelling, for a screen that has to say where a price came from — or empty
     * for [UNKNOWN], because a name is the whole point and «unknown» is not one.
     *
     * A reader who suspects «کندل‌سازی» is asking a question that only a checkable name answers,
     * and the rule this obeys is the one in `CandleGateway.sourceName`: **never invent
     * provenance.** A feed whose `source` field the app does not recognise gets no label rather
     * than a guessed one, which is why this is empty rather than «نامشخص» — an unnamed price is
     * silent, and a wrongly-named one is a lie a reader could act on.
     */
    val displayName: String
        get() = when (this) {
            FINNHUB -> "Finnhub"
            LBANK -> "LBank"
            UNKNOWN -> ""
        }
}

data class Instrument(
    val symbol: String,
    val displayName: String,
    val marketType: MarketType,
)

data class MarketQuote(
    val instrument: Instrument,
    val price: Double,
    val bid: Double? = null,
    val ask: Double? = null,
    val changePercent: Double? = null,
    val timestampEpochMillis: Long,
    val source: QuoteSource = QuoteSource.UNKNOWN,
    val isStale: Boolean = true,
)

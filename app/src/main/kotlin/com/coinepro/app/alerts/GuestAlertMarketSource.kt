package com.coinepro.app.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.IndicatorOption
import com.coinepro.core.chart.IndicatorPane
import com.coinepro.core.chart.Line
import com.coinepro.core.common.AppResult
import com.coinepro.core.datastore.ChartDrawingStore
import com.coinepro.core.datastore.StoredDrawing
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.guest.GuestCandle
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.guest.GuestQuote
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.marketdata.of
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Where the alert model and the chart engine meet, and the only place they are allowed to.
 *
 * ### Why this module is the seam, and why it has to be
 *
 * `core:notifications` defines what an alert watches for, and it deliberately does not depend on
 * `core:chart`. That is not tidiness. If it did, there would be a second implementation of every
 * indicator in this app — one the chart draws with and one the alerts fire on — and the first day
 * they disagreed by a rounding step, a reader would be told that RSI crossed seventy while looking
 * at a chart that says it did not. There is no way to explain that to somebody, and no way for them
 * to find out which of the two was right.
 *
 * `core:chart` cannot depend on `core:notifications` either, for the mirror-image reason: the chart
 * is a drawing engine and has no business knowing what an alert is.
 *
 * So the two meet in the application module, which is the only place that already has both on its
 * classpath. [AlertTrigger.Indicator][com.coinepro.core.notifications.AlertTrigger.Indicator] is
 * written for exactly this arrangement — it takes the indicator's *output* as a parameter and says
 * so — and this class is what produces that output, by running the same `ChartCatalog` the chart
 * runs, over the same candles, and reading its last value. `DrawingTouch` is resolved the same way,
 * from the drawing the reader actually placed.
 *
 * ### Public candles, for the same reason the alerts are local
 *
 * The guest route needs no account, so an alert set ten minutes after installing works, and keeps
 * working after somebody signs in. Only the symbols that have an alert on them are asked for, and
 * candles only for the alerts whose condition genuinely needs bars — see [AlertDataNeeds].
 *
 * ### What a failure means here
 *
 * A failed **price** read fails the whole pass: the route was unreachable, which says nothing about
 * whether any alert should have fired, and the caller leaves every alert armed. A failed **candle**
 * read is narrower — that one symbol simply arrives without bars, its bar-shaped triggers cannot be
 * satisfied, and it is retried on the next pass. Neither path consumes an alert.
 */
@Singleton
class GuestAlertMarketSource @Inject constructor(
    private val gateway: GuestGateway,
    private val drawings: ChartDrawingStore,
    dataStore: DataStore<Preferences>,
) : AlertMarketSource {

    /**
     * The reader's own timeframe per symbol.
     *
     * Built here rather than taken from the graph because the store holds no state of its own —
     * everything it knows is in the preferences file — so an instance made here and one made by a
     * screen are the same store. It matters that the alert is evaluated on the interval the reader
     * left the chart on: an RSI alert set while looking at the four-hour is not the same alert on
     * the five-minute, and answering it on the wrong one is answering a different question.
     */
    private val chartStates = SymbolChartStateStore(dataStore)

    override suspend fun read(requests: List<AlertMarketRequest>): AppResult<Map<String, AlertSample>> {
        if (requests.isEmpty()) return AppResult.Success(emptyMap())
        val symbols = requests.map(AlertMarketRequest::symbol)
        val quotes = when (val result = gateway.prices(symbols)) {
            is AppResult.Success -> result.value.quotes.associateBy(GuestQuote::symbol)
            is AppResult.Failure -> return result
        }
        val timeframes = chartStates.all().first().associate { it.symbol to it.timeframe }
        val at = System.currentTimeMillis()
        val samples = LinkedHashMap<String, AlertSample>(requests.size)
        requests.forEach { request ->
            val quote = quotes[request.symbol] ?: return@forEach
            val interval = intervalFor(timeframes[request.symbol.uppercase()])
            val quoteOnly = AlertSample(
                symbol = request.symbol,
                price = quote.price,
                previousPrice = null,
                changePercent24h = quote.changePercent24h,
                timeframe = interval.wire,
            )
            samples[request.symbol] = if (request.needs.candles) {
                enrich(quoteOnly, request, interval, at)
            } else {
                quoteOnly
            }
        }
        return AppResult.Success(samples)
    }

    /**
     * The same sample with the bars, the indicator outputs and the drawn levels filled in.
     *
     * Returns the quote-only sample unchanged where the bars could not be read. That is the
     * conservative answer: without bars a crossing has no direction and a bar policy has no bar, and
     * every trigger that needs one of those declines to fire rather than guessing.
     */
    private suspend fun enrich(
        sample: AlertSample,
        request: AlertMarketRequest,
        interval: ChartInterval,
        atEpochMillis: Long,
    ): AlertSample {
        val loaded = when (val result = gateway.candles(sample.symbol, interval.wire, CANDLE_LIMIT)) {
            is AppResult.Success -> result.value.candles
            is AppResult.Failure -> return sample
        }
        // The bar now forming is excluded from everything derived here. An indicator that includes
        // it repaints as the candle moves, which is what makes a chart's own alerts signal and then
        // unsignal; a `Move` measured against it is measured against a number that is still changing.
        val currentBucket = interval.bucketStart(atEpochMillis / 1_000L)
        val closed = loaded.filter { it.closed && it.timeSeconds < currentBucket }
        if (closed.isEmpty()) return sample.copy(barStart = currentBucket * 1_000L)
        val series = CandleSeries(closed.map(::toCandle))
        return sample.copy(
            previousPrice = closed.last().close,
            closes = closed.map(GuestCandle::close),
            barStart = currentBucket * 1_000L,
            closedBarStart = closed.last().timeSeconds * 1_000L,
            indicators = request.needs.indicators.mapNotNull { key ->
                readingOf(key, series)?.let { key to it }
            }.toMap(),
            drawingLevels = drawingLevels(sample.symbol, request.needs.drawings, closed),
        )
    }

    /**
     * One indicator's last two values, computed by the chart's own catalogue.
     *
     * ### Which line, when an indicator draws several
     *
     * The first one the catalogue produces, which is the one its legend names — the RSI line rather
     * than its reference bands, the upper Bollinger edge rather than the basis. That is a choice and
     * it is written down here rather than left to be discovered: a band has no single «output», and
     * picking the first keeps the number an alert fires on the same number the chart labels.
     *
     * A structure study — support and resistance, pivots, the swing marks — has no value per bar at
     * all, so it produces nothing and any alert on it never fires. Offering it in the editor is the
     * screen's problem; inventing a number for it would be this one's.
     */
    private fun readingOf(key: AlertIndicatorKey, series: CandleSeries): AlertIndicatorReading? {
        val option = ChartCatalog.INDICATORS.firstOrNull { it.id == key.indicatorId } ?: return null
        val line = lineOf(option, series, key.period) ?: return null
        val current = lastPresent(line, from = line.size - 1) ?: return null
        return AlertIndicatorReading(
            previous = lastPresent(line, from = current.first - 1)?.second,
            current = current.second,
        )
    }

    private fun lineOf(option: IndicatorOption, series: CandleSeries, period: Int?): Line? =
        when (option.pane) {
            IndicatorPane.PRICE -> ChartCatalog.overlayFor(option, series, period).firstOrNull()?.values
            IndicatorPane.SEPARATE -> ChartCatalog.paneFor(option, series, period)?.lines?.firstOrNull()?.values
            IndicatorPane.STRUCTURE -> null
        }

    /** The newest present value at or before [from], with its index, or null across a whole warm-up. */
    private fun lastPresent(line: Line, from: Int): Pair<Int, Double>? {
        var index = minOf(from, line.size - 1)
        while (index >= 0) {
            val value = line[index]
            if (value != null) return index to value
            index--
        }
        return null
    }

    /**
     * Each wanted drawing's own price level at the last two closed bars.
     *
     * Resolved at bar times rather than at the wall clock, so the pair a touch is judged from lines
     * up with the closes it is compared against. A drawing the reader deleted, or one whose tool has
     * no price at all, is left out entirely and its alert simply never fires.
     */
    private suspend fun drawingLevels(
        symbol: String,
        wanted: Set<String>,
        closed: List<GuestCandle>,
    ): Map<String, List<Double>> {
        if (wanted.isEmpty()) return emptyMap()
        val placed = drawings.drawings(symbol).first().associateBy { it.id.toString() }
        val nowBar = closed.last().timeSeconds
        val beforeBar = closed.getOrNull(closed.size - 2)?.timeSeconds
        return wanted.mapNotNull { id ->
            val drawing = placed[id] ?: return@mapNotNull null
            val levelNow = levelOf(drawing, nowBar) ?: return@mapNotNull null
            val levelBefore = beforeBar?.let { levelOf(drawing, it) }
            id to listOfNotNull(levelBefore, levelNow)
        }.toMap()
    }

    private fun levelOf(drawing: StoredDrawing, atEpochSeconds: Long): Double? =
        AlertDrawingLevel.levelAt(drawing.toolId, drawing.points, atEpochSeconds)

    /**
     * The interval an alert on this symbol is evaluated on.
     *
     * The reader's own where they have left the chart on one, and the hourly otherwise. Hourly
     * rather than the finest available because a background pass runs at best every fifteen minutes:
     * an alert evaluated on the one-minute bar would be judged on a bar that closed a quarter of an
     * hour ago and would report a bar close that is already history.
     */
    private fun intervalFor(stored: String?): ChartInterval =
        ChartInterval.of(stored) ?: ChartInterval.Preset(DEFAULT_TIMEFRAME)

    private fun toCandle(candle: GuestCandle) = Candle(
        t = candle.timeSeconds,
        o = candle.open,
        h = candle.high,
        l = candle.low,
        c = candle.close,
        v = candle.volume,
    )

    private companion object {

        /** What an alert is evaluated on when the reader has never chosen a timeframe for the symbol. */
        val DEFAULT_TIMEFRAME = Timeframe.H1

        /**
         * How many bars are asked for.
         *
         * Enough to warm up the longest lookback the indicator catalogue allows — four hundred — with
         * room over it, and inside the public route's own ceiling of five hundred. Fewer would make a
         * two-hundred-period average silently unavailable, which reads to a reader exactly like an
         * alert that does not work.
         */
        const val CANDLE_LIMIT = 450
    }
}

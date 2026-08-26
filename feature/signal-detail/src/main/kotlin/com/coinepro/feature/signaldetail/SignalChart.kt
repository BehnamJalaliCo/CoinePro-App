package com.coinepro.feature.signaldetail

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The bars behind one signal, and nothing else.
 *
 * Deliberately not `feature:chart`'s controller. That one pages, switches timeframe, holds
 * indicators and owns a drawing layer, all of which this screen wants none of: the timeframe is the
 * signal's, the window is one page, and a reader who wants to work on the chart opens the chart.
 * Sharing it would mean importing all of that to use a tenth of it, and would put a feature module
 * inside another feature module for the first time in this app.
 */
data class SignalChartState(
    val series: CandleSeries = CandleSeries.EMPTY,
    val loading: Boolean = false,
    /**
     * The load finished and there is nothing to draw.
     *
     * Distinct from `loading == false && series.isEmpty` before the first attempt, and the screen
     * needs the difference: before, the card should not be there at all; after, it should not
     * either — a signal whose chart failed is still a perfectly readable signal, and an error card
     * over its levels would say the wrong thing about which part is broken.
     */
    val failed: Boolean = false,
)

/**
 * Loads one page of candles for a signal's symbol.
 *
 * Failure is quiet on purpose. This chart is context for numbers that are already on the screen and
 * already correct; if the candles do not arrive, the right outcome is the screen the app had before
 * this existed, not an error where a chart would be.
 */
class SignalChartController(
    private val gateway: CandleGateway,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(SignalChartState())
    val state: StateFlow<SignalChartState> = _state.asStateFlow()

    private var job: Job? = null
    private var loaded: Pair<String, Timeframe>? = null

    /**
     * @param timeframe the signal's own, in whatever spelling the server used. A signal on a
     *   timeframe this app does not carry falls back to H1 rather than showing nothing: the bars
     *   are still that symbol's bars, and the levels still sit where they sit.
     */
    fun load(symbol: String, timeframe: String?) {
        val frame = Timeframe.of(timeframe) ?: Timeframe.H1
        val key = symbol to frame
        if (key == loaded) return
        loaded = key
        job?.cancel()
        _state.value = SignalChartState(loading = true)
        job = scope.launch {
            runCatching { gateway.load(symbol, frame, limit = PREVIEW_BARS) }
                .onSuccess { page ->
                    _state.value = SignalChartState(
                        series = CandleSeries(page.candles.map(OhlcBar::toCandle)),
                        loading = false,
                        failed = page.candles.isEmpty(),
                    )
                }
                .onFailure {
                    // Retried on the next visit rather than here: the reader is looking at a
                    // signal, and a chart that retries itself under them is a chart that flickers.
                    loaded = null
                    _state.value = SignalChartState(failed = true)
                }
            job = null
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        loaded = null
        _state.value = SignalChartState()
    }

    private companion object {
        /**
         * Enough to see the setup in context, and no more.
         *
         * A signal's levels are minutes to days old; two hundred bars puts them comfortably inside
         * the window on every timeframe this app offers, and asking for the gateway's default of
         * three hundred would be a third more wire for bars that fall off the left of a card.
         */
        const val PREVIEW_BARS = 200
    }
}

internal fun OhlcBar.toCandle(): Candle = Candle(t = t, o = o, h = h, l = l, c = c, v = v)

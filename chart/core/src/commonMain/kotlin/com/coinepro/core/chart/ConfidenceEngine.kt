package com.coinepro.core.chart

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What happened the **last** times this study said this, on this instrument, on this bar length.
 *
 * ### Why a number, and why this number
 *
 * «RSI came back over 30» is a fact and it is not yet useful: the reader's next question is *so
 * what*. Every app in this category stops at the fact. The answer this product gives is the only
 * honest one available offline — walk the history that is already on the phone, find every previous
 * time the same study said the same thing, and count how it went.
 *
 * That is not a prediction and it is not advice. It is a **base rate**, and a base rate is exactly
 * what a person new to charts is missing: it is the difference between «the indicator turned up»
 * and «the indicator turned up, and the last eighteen times it did that on gold, eleven of them
 * were higher ten bars later».
 *
 * ### Why the sample size is never hidden
 *
 * Because a win rate without an N is a lie with a decimal point. Six out of eight is seventy-five
 * per cent and means nothing at all. So [ConfidenceReport] carries [samples] everywhere it carries
 * [winRate], the UI prints them together, and below [ConfidenceReport.THIN] the product says «low
 * data» *instead of* a percentage rather than beside it.
 *
 * ### Why the outcome is measured with the study's own stop
 *
 * A signal that is right for six bars and then loses everything is not a signal that worked. So an
 * outcome is decided the way a trade is: the stop from [SignalSpec.defaultStop] is checked against
 * every bar's low (for a buy) before the horizon is reached. Hit the stop first and the trade is a
 * loss of one R whatever the price does afterwards; reach the horizon and the result is the move in
 * units of that same R, which is what makes «average R» a number that can be compared across
 * instruments that trade at different prices.
 */
data class ConfidenceReport(
    /** Which study this is about — a catalogue id, or a script's owner id. */
    val id: String,
    /** How many completed signals were measured. The number the percentage is worthless without. */
    val samples: Int,
    /** The share that reached the horizon in profit, 0..1. */
    val winRate: Double,
    /** The average result in units of risk. Positive is a study that paid for its stops. */
    val averageR: Double,
    /** The newest five outcomes, newest last, for the row of dots on the card. */
    val recent: List<Boolean>,
    /** How many bars ahead each outcome was measured over. See [ConfidenceEngine.HORIZONS]. */
    val horizon: Int,
) {
    /** Whether there is enough here to print a percentage at all. */
    val trustworthy: Boolean get() = samples >= THIN

    /** The percentage as a whole number, for a pill. Zero when [trustworthy] is false. */
    val percent: Int get() = if (trustworthy) (winRate * 100).roundToInt() else 0

    companion object {
        /**
         * Below eight samples the app does not print a percentage.
         *
         * Eight is not a statistical threshold — no small number is — it is the point below which
         * the *shape* of the answer is misleading: one more win moves a five-sample rate by twenty
         * points, and a reader cannot be expected to hold that in their head while looking at a
         * chart. Under it the pill says «low data» and the card still shows the outcomes, because
         * six honest dots are more use than a fabricated sixty-seven per cent.
         */
        const val THIN = 8

        /** A study with nothing to measure: drawn, explained, and silent about its history. */
        fun empty(id: String, horizon: Int = ConfidenceEngine.DEFAULT_HORIZON): ConfidenceReport =
            ConfidenceReport(id, samples = 0, winRate = 0.0, averageR = 0.0, recent = emptyList(), horizon = horizon)
    }
}

/**
 * Measures [SignalRead.events] against the bars that came after them.
 *
 * Pure, synchronous and fast enough to run on every data change: five hundred bars and a hundred
 * events is a few thousand comparisons, which is microseconds — the cost that matters is
 * *recomputing it on the main thread*, and that is the caller's problem, which is why this takes a
 * series rather than a controller and returns a value rather than mutating one.
 */
object ConfidenceEngine {

    /** The three horizons a reader can ask for, in bars. */
    val HORIZONS: List<Int> = listOf(5, 10, 20)

    /** Ten bars: far enough to be a move, near enough that the signal is still the cause. */
    const val DEFAULT_HORIZON: Int = 10

    /**
     * How much history is walked: five hundred bars.
     *
     * Enough for a hundred-odd signals on a normal study, and short enough that the answer is about
     * the market the reader is looking at. A study measured over ten thousand bars of a market that
     * has changed regime three times is a number about history rather than about now.
     */
    const val WINDOW: Int = 500

    /**
     * The base rate for one study's signals.
     *
     * [stopFor] is how the stop for an event is found — [SignalSpec.defaultStop] in the app, and a
     * fixture in a test. Returning null from it measures the outcome without a stop, which is the
     * right reading for a study whose events are not trades.
     */
    fun measure(
        read: SignalRead,
        series: CandleSeries,
        horizon: Int = DEFAULT_HORIZON,
        stopFor: (SignalEvent) -> Double? = { SignalSpec.defaultStop(series, it) },
    ): ConfidenceReport {
        if (series.isEmpty || read.events.isEmpty()) return ConfidenceReport.empty(read.id, horizon)
        val first = maxOf(0, series.size - WINDOW)
        val outcomes = mutableListOf<Double>()
        val wins = mutableListOf<Boolean>()
        for (event in read.events) {
            if (event.bar < first) continue
            // A signal whose horizon is not yet past is not an outcome. Counting the bars that have
            // printed so far would bias every study upward on a rising market and downward on a
            // falling one, which is the most convincing way to be wrong.
            val settle = event.bar + horizon
            if (settle >= series.size) continue
            val entry = series.close[event.bar]
            val stop = stopFor(event)
            val risk = if (stop == null) null else abs(entry - stop).takeIf { it > 0.0 }
            val long = event.side == TradeSide.BUY
            var result: Double? = null
            if (risk != null && stop != null) {
                // Walked bar by bar, because the order matters: a trade that hit its stop on the way
                // to a win is a loss, and a report that reads the horizon's close alone cannot tell
                // the difference.
                for (bar in (event.bar + 1)..settle) {
                    val hit = if (long) series.low[bar] <= stop else series.high[bar] >= stop
                    if (hit) {
                        result = -1.0
                        break
                    }
                }
            }
            if (result == null) {
                val move = (series.close[settle] - entry) * (if (long) 1 else -1)
                result = if (risk != null) move / risk else move / entry
            }
            outcomes += result
            wins += result > 0.0
        }
        if (outcomes.isEmpty()) return ConfidenceReport.empty(read.id, horizon)
        return ConfidenceReport(
            id = read.id,
            samples = outcomes.size,
            winRate = wins.count { it }.toDouble() / outcomes.size,
            averageR = outcomes.sum() / outcomes.size,
            recent = wins.takeLast(RECENT_DOTS),
            horizon = horizon,
        )
    }

    /**
     * The chart's **Setup score**, 0–100, from every switched-on study at once.
     *
     * ### What it is and what it deliberately is not
     *
     * It is an *agreement* measure weighted by each study's own base rate: how much of what is on
     * this chart is pointing the same way, and how much has that pointing been worth on this
     * instrument. A study with no history contributes its direction at half weight rather than
     * nothing, because a reader who has switched on one study and gets a score of zero has been
     * told the app is broken rather than that the study is new.
     *
     * It is **not** a probability, not a price target and not advice, and the number is drawn on a
     * grey→gold scale rather than a red→green one for exactly that reason: green means «up» on this
     * chart and this is not a direction, it is a strength. See the run's colour rule.
     */
    fun setupScore(reads: List<SignalRead>, reports: Map<String, ConfidenceReport>): SetupScore {
        val voting = reads.filter { it.state != MarketState.NEUTRAL }
        if (voting.isEmpty()) return SetupScore(0, MarketState.NEUTRAL, 0, 0)
        var bull = 0.0
        var bear = 0.0
        for (read in voting) {
            val report = reports[read.id]
            // A study's own record on this instrument, or an even coin for one with no record —
            // weighted down, because an unmeasured opinion is still an opinion.
            val weight = when {
                report == null || !report.trustworthy -> UNPROVEN_WEIGHT
                else -> report.winRate
            }
            if (read.state == MarketState.BULL) bull += weight else bear += weight
        }
        val total = bull + bear
        if (total <= 0.0) return SetupScore(0, MarketState.NEUTRAL, 0, voting.size)
        val leading = if (bull >= bear) MarketState.BULL else MarketState.BEAR
        val share = maxOf(bull, bear) / total
        // Rescaled from «half of them agree» to «all of them agree»: a fifty-fifty chart scores
        // nought rather than fifty, because a fifty out of a hundred that means «no information» is
        // the single most misread number a dashboard can print.
        val score = (((share - 0.5) * 2).coerceIn(0.0, 1.0) * 100).roundToInt()
        val signals = reads.sumOf { it.events.size }
        return SetupScore(score, leading, signals, voting.size)
    }

    /** How many of the newest outcomes a card shows as dots. */
    const val RECENT_DOTS: Int = 5

    /** What a study with no measured history counts for in [setupScore]. */
    private const val UNPROVEN_WEIGHT = 0.5
}

/**
 * The header's one number: how much of this chart agrees, and which way.
 *
 * [studies] is how many had an opinion at all, which is what makes the score readable — «72 from
 * three studies» is a different claim from «72 from eleven» and the header prints both.
 */
data class SetupScore(
    val score: Int,
    val side: MarketState,
    val signals: Int,
    val studies: Int,
) {
    val isEmpty: Boolean get() = studies == 0

    /**
     * Where the score sits on the grey→gold scale, 0..1.
     *
     * Identical to [score] as a fraction; it exists so no drawing code divides by a hundred and
     * gets it wrong once.
     */
    val tone: Float get() = score / 100f
}

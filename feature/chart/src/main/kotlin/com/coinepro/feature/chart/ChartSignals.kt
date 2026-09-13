package com.coinepro.feature.chart

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.ConfidenceEngine
import com.coinepro.core.chart.ConfidenceReport
import com.coinepro.core.chart.MarkerGlyph
import com.coinepro.core.chart.MarketState
import com.coinepro.core.chart.NoteShape
import com.coinepro.core.chart.SetupScore
import com.coinepro.core.chart.SignalEvent
import com.coinepro.core.chart.SignalNote
import com.coinepro.core.chart.SignalRead
import com.coinepro.core.chart.SignalSpec
import com.coinepro.core.chart.TradeSide
import com.coinepro.core.common.BidiText

/**
 * The Signal Layer as one chart holds it (run Ω1).
 *
 * ### What this adds to `SignalSpec`
 *
 * `SignalSpec` reads one study. This is the chart's whole answer: every switched-on study and every
 * one of the reader's scripts, each with its base rate, plus the one number the header prints. It is
 * a value — computed off the main thread, published once, read by the legend, the Now strip, the
 * Explain sheet, the markers and the coach — so that five surfaces cannot disagree about what the
 * chart is saying.
 *
 * ### Why a script is in here beside a built-in
 *
 * Because it is an indicator (run I), and the thesis does not hold if the reader's own study is the
 * one thing on the chart that cannot explain itself. A script that calls `signal(...)` says what it
 * means in its author's words; one that does not is read off its own first plot by the same rule a
 * built-in's line is read by. Either way it gets a state, a base rate and a row.
 */
data class ChartSignalLayer(
    val reads: List<SignalRead> = emptyList(),
    val confidence: Map<String, ConfidenceReport> = emptyMap(),
    val setup: SetupScore = SetupScore(0, MarketState.NEUTRAL, 0, 0),
    /** How many bars ahead each base rate was measured over. The reader can change it. */
    val horizon: Int = ConfidenceEngine.DEFAULT_HORIZON,
    /** The sentence a script's author wrote, by owner id, where one was written. */
    val authored: Map<String, String> = emptyMap(),
    /**
     * What to call each study in a pill, by id.
     *
     * A built-in is `RSI`; a reader's script is the name they gave it. Carried rather than looked up
     * at the pill, because the catalogue does not know about scripts and the strip must not have to
     * ask two places what something is called — the frame where it asked the wrong one printed a
     * pill that said «1».
     */
    val names: Map<String, String> = emptyMap(),
    /**
     * The same study, read on other bar lengths — «show on all timeframes» (run Ω1).
     *
     * Keyed by study id and filled only when the reader asks, because each row costs a request for
     * bars this chart does not hold. Absent means «not asked»; an empty list means «asked, and
     * nothing answered», and the sheet says which.
     */
    val across: Map<String, List<TimeframeRead>> = emptyMap(),
) {
    val isEmpty: Boolean get() = reads.isEmpty()

    fun readOf(id: String): SignalRead? = reads.firstOrNull { it.id == id }

    /** What to call this study in a pill or a row. Falls back to the id, which is never empty. */
    fun nameOf(id: String): String = names[id] ?: id.uppercase()

    fun confidenceOf(id: String): ConfidenceReport? = confidence[id]

    /**
     * The sentence for one study, in the reader's language.
     *
     * A script author's own words win over the app's phrasing, and that is deliberate: they know
     * what their script is for and the app is guessing from a line.
     */
    fun sentence(id: String, english: Boolean): String? {
        // Isolated here rather than at each of the five places that draws one (run Ω-FIX item 6).
        // Every sentence this returns has a figure in it — a level, a percentage, a bound — and
        // every one of them is a left-to-right run inside Persian prose. See
        // `BidiText.isolateNumbers`, and the full stop that walked to the front of the line.
        authored[id]?.takeIf { it.isNotBlank() }?.let { return BidiText.isolateNumbers(it) }
        val note = readOf(id)?.note ?: return null
        return if (note.shape == NoteShape.QUIET) null else BidiText.isolateNumbers(note.text(english))
    }

    companion object {
        val EMPTY = ChartSignalLayer()
    }
}

/**
 * One study on one other bar length.
 *
 * ### Why a reader wants this and why it is the honest version of it
 *
 * «The RSI is oversold» is a different claim on the five-minute chart and on the daily one, and the
 * single most common mistake a beginner makes is taking a signal from a bar length they were not
 * looking at. Three lines — the same study, three timeframes, each with its own word — is the whole
 * of multi-timeframe analysis a person needs before they know what that phrase means.
 *
 * It carries the sentence as *text* rather than a note, because it is built on a series this chart
 * does not hold and will not hold again: the bars are fetched, read, and dropped.
 */
data class TimeframeRead(
    /** The bar length's own wire name — `H1`, `D1`. Printed as it is, in both languages. */
    val timeframe: String,
    val state: MarketState,
    val sentence: String,
)

/**
 * Turns the chart's studies into a [ChartSignalLayer].
 *
 * Pure and synchronous: the caller decides which thread. Everything expensive in here is a walk over
 * the bars the chart already holds, and the reason it must not be on the main thread is not the
 * arithmetic — it is that a chart with eleven studies on it does eleven of them at once.
 */
object ChartSignalEngine {

    /**
     * The newest markers only.
     *
     * A study that fired forty times over five hundred bars would put forty triangles on a chart
     * that shows eighty of them, which is not a signal layer, it is a rash. The reader is looking at
     * *now*; the history is what the base rate is for.
     */
    const val MARKERS_PER_STUDY = 6

    fun evaluate(
        series: CandleSeries,
        indicatorIds: List<String>,
        periods: Map<String, Int> = emptyMap(),
        params: Map<String, Map<String, Double>> = emptyMap(),
        scripts: List<ChartScript> = emptyList(),
        draw: ChartScriptDraw = ChartScriptDraw.EMPTY,
        horizon: Int = ConfidenceEngine.DEFAULT_HORIZON,
        english: Boolean = false,
    ): ChartSignalLayer {
        if (series.isEmpty) return ChartSignalLayer.EMPTY
        val reads = mutableListOf<SignalRead>()
        val authored = mutableMapOf<String, String>()
        val names = mutableMapOf<String, String>()

        for (id in indicatorIds) {
            val read = SignalSpec.read(id, series, periods[id], params[id] ?: emptyMap(), english)
            // A study that neither fires nor has a state is drawn and silent — a volume profile, a
            // background. It keeps its row in the legend and stays out of the Now strip.
            if (read.state == MarketState.NEUTRAL && read.events.isEmpty() && read.note.shape == NoteShape.QUIET) continue
            names[id] = ChartCatalog.INDICATORS.firstOrNull { it.id == id }?.helpId?.uppercase() ?: id.uppercase()
            reads += read
        }

        for (script in scripts) {
            val owner = script.ownerId
            val verdicts = draw.verdicts[owner].orEmpty()
            val read = if (verdicts.isNotEmpty()) {
                verdicts.firstOrNull { it.text.isNotBlank() }?.let { authored[owner] = it.text }
                fromVerdicts(owner, verdicts, series)
            } else {
                val overlay = draw.overlays.filterIndexed { index, _ -> draw.overlayOwners.getOrNull(index) == owner }
                val pane = draw.panes.filterIndexed { index, _ -> draw.paneOwners.getOrNull(index) == owner }
                when {
                    overlay.isNotEmpty() ->
                        SignalSpec.readLine(owner, script.displayName, overlay.first().values, series, onPrice = true, english = english)
                    pane.isNotEmpty() && pane.first().lines.isNotEmpty() ->
                        SignalSpec.readLine(owner, script.displayName, pane.first().lines.first().values, series, onPrice = false, english = english)
                    else -> SignalRead.quiet(owner)
                }
            }
            if (read.state == MarketState.NEUTRAL && read.events.isEmpty()) continue
            names[owner] = script.displayName
            reads += read
        }

        val confidence = reads.associate { read ->
            read.id to ConfidenceEngine.measure(read, series, horizon)
        }
        return ChartSignalLayer(
            reads = reads,
            confidence = confidence,
            setup = ConfidenceEngine.setupScore(reads, confidence),
            horizon = horizon,
            authored = authored,
            names = names,
        )
    }

    /**
     * A script's own `signal(...)` calls, as one read.
     *
     * The state is the newest verdict's side, because a script that said «buy» forty bars ago and
     * nothing since is still saying buy — that is what a discrete signal means, and inventing a
     * decay would be inventing an opinion the author did not express.
     */
    private fun fromVerdicts(
        owner: String,
        verdicts: List<com.coinepro.core.script.ScriptVerdict>,
        series: CandleSeries,
    ): SignalRead {
        val events = verdicts
            .flatMap { verdict ->
                verdict.bars.map { bar ->
                    SignalEvent(bar, if (verdict.buy) TradeSide.BUY else TradeSide.SELL, verdict.strength.toFloat())
                }
            }
            .sortedBy { it.bar }
        val newest = events.lastOrNull()
        val state = when (newest?.side) {
            TradeSide.BUY -> MarketState.BULL
            TradeSide.SELL -> MarketState.BEAR
            null -> MarketState.NEUTRAL
        }
        val note = when (newest?.side) {
            TradeSide.BUY -> SignalNote(NoteShape.TURNED_UP, listOf(owner))
            TradeSide.SELL -> SignalNote(NoteShape.TURNED_DOWN, listOf(owner))
            null -> SignalNote(NoteShape.QUIET)
        }
        val stop = newest?.let { SignalSpec.defaultStop(series, it) }
        return SignalRead(owner, state, events, note, stop)
    }

    /**
     * The triangles the chart draws, from every study's newest events.
     *
     * Placed under the low for a buy and over the high for a sell, like every other marker in this
     * app — [ChartMarker] does that placement itself; what this decides is which events are worth
     * drawing at all. The study's colour comes from the catalogue so a mark and the line that made
     * it are the same colour, which is the only thing that makes a chart with three studies on it
     * readable.
     */
    fun markersFor(layer: ChartSignalLayer, series: CandleSeries, hidden: Set<String> = emptySet()): List<ChartMarker> {
        if (layer.isEmpty || series.isEmpty) return emptyList()
        val marks = mutableListOf<ChartMarker>()
        for (read in layer.reads) {
            if (read.id in hidden) continue
            val colour = ChartCatalog.INDICATORS.firstOrNull { it.id == read.id }?.colour ?: SCRIPT_MARK_COLOUR
            for (event in read.events.takeLast(MARKERS_PER_STUDY)) {
                val bar = series.bars.getOrNull(event.bar) ?: continue
                val buy = event.side == TradeSide.BUY
                marks += ChartMarker(
                    time = bar.t,
                    price = if (buy) bar.l else bar.h,
                    above = !buy,
                    colour = colour,
                    glyph = if (buy) MarkerGlyph.ARROW_UP else MarkerGlyph.ARROW_DOWN,
                )
            }
        }
        return marks
    }

    /** Gold, for a script's marks: a script is the reader's own, like their drawings. */
    private const val SCRIPT_MARK_COLOUR = 0xFFD8A848
}

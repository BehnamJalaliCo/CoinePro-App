package com.coinepro.feature.chart

import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.proseDigits
import com.coinepro.core.marketdata.resolveCandleRequest

/**
 * What this chart is not showing, said out loud.
 *
 * ### The accusation, and why naming the venue is only half an answer
 *
 * «کندل‌سازی» — that the broker draws its own prices — is the loudest single accusation in
 * Persian-language reviews of this whole category. `ProvenanceLine` already answers half of it by
 * naming the venue and printing the last bar's time, so a suspicious reader can hold this chart
 * against that venue's own.
 *
 * The other half is what makes the comparison come out wrong when nothing is wrong. TradingView's
 * own users left over exactly this: the chart's volume did not match the exchange's, nobody could
 * find out why, and the conclusion drawn was not "these are different aggregations" but "the data
 * is fake". The sentence that would have settled it — *this feed does not report volume* — costs a
 * line. Their support forum instead has the line the whole product is judged by: "the data on the
 * chart is what traders use to make decisions."
 *
 * So this is the list of every way the picture legitimately differs from the venue's own, in the
 * order the reader is most likely to notice it. Each entry is a fact about *this* chart right now,
 * never a general disclaimer: an entry that is always present is an entry nobody reads.
 *
 * ### Pure, and therefore checkable
 *
 * A function over the state rather than a series of `if`s in the composable, because the thing that
 * matters is *which* sentences appear for which chart, and that is a unit test rather than a
 * screenshot. It also means the same list can be shown twice — under the chart, and in the export's
 * own header — without the two drifting.
 */
fun chartExclusions(state: ChartUiState): List<ChartExclusion> {
    val out = mutableListOf<ChartExclusion>()
    val plan = resolveCandleRequest(state.interval)

    // Folding first: it is the one that changes every candle on screen rather than merely limiting
    // how many there are, and a reader comparing a two-hour bar against the venue's is comparing
    // against something the venue never sent.
    if (plan.factor > 1) {
        out += ChartExclusion(R.string.provenance_folded, listOf(plan.source.wire))
    }
    if (state.historyTruncated) {
        out += ChartExclusion(R.string.provenance_truncated)
    }
    // The volume line is the one the TradingView complaint was actually about. It is said whenever
    // the feed carries no volume column, including on a chart with no volume study switched on —
    // the reader who is going to compare against the exchange has not switched anything on either.
    if (!state.series.isEmpty && !state.series.hasVolume) {
        out += ChartExclusion(R.string.provenance_no_volume)
    }
    if (state.replay.isOn) {
        out += ChartExclusion(R.string.provenance_replay)
    }
    val repainting = RepaintClaims.repaintingAmong(state.activeIndicators)
    if (repainting.isNotEmpty()) {
        // The names are joined here and the claim's own sentence is the second argument, so the
        // colon and the order between them are the resource's business and not this function's —
        // which is what lets an English build read «zigzag: rewrites what is behind it».
        out += ChartExclusion(
            R.string.provenance_repainting,
            listOf(repainting, RepaintClaim.REPAINTS.noteRes),
        )
    }
    return out
}

/**
 * One reason this chart differs from the venue's own, as an id and its arguments (run Ω2).
 *
 * ### Why not a formatted string
 *
 * Because [chartExclusions] is pure and unit-tested, and the words are in `values/` and `values-fa/`
 * where a `Context` is needed to read them. Returning the *identity* of each reason rather than its
 * Persian text keeps the function exactly as testable — better, in fact: `ChartProvenanceTest` now
 * asserts «the volume reason is in the list» rather than «some string contains حجم», which is the
 * thing it always meant and could not say.
 *
 * [args] is `List<Any>` because two of the arguments are themselves resource ids — a claim's own
 * sentence, a study's name — and the composable that renders this resolves them on the way in. An
 * `Int` in here is a resource id by convention; everything else is printed as it is.
 */
data class ChartExclusion(
    /** The sentence's resource id. */
    val res: Int,
    /**
     * What fills its placeholders. A `List<String>` is printed verbatim; a nested `Int` is a
     * resource id and a nested `List<String>` is a list joined with the locale's own separator.
     */
    val args: List<Any> = emptyList(),
)

/**
 * The mark the chart may honestly carry, or null.
 *
 * Only [RepaintClaim.SETTLED] and [RepaintClaim.LATE] earn one, and a chart with nothing switched
 * on that qualifies gets nothing rather than a reassuring generality. The two claims are never
 * merged: a chart carrying pivots and swings together is settled in one study and confirmed-late
 * in the other, and the weaker of the two is the honest headline — a reader must not read
 * «repaint نمی‌کند» and apply it to the swing marker that can still be withdrawn.
 */
fun repaintMark(state: ChartUiState, signalOnChart: Boolean = false): RepaintClaim? {
    val claims = state.activeIndicators.mapNotNull(RepaintClaims::of) +
        if (signalOnChart) listOf(RepaintClaims.SIGNAL) else emptyList()
    // A study that genuinely repaints is named in `chartExclusions` and cancels the mark outright.
    if (claims.any { it == RepaintClaim.REPAINTS }) return null
    val trusted = claims.filter { it.isTrustworthy }
    if (trusted.isEmpty()) return null
    return if (trusted.any { it == RepaintClaim.LATE }) RepaintClaim.LATE else RepaintClaim.SETTLED
}

/**
 * Which studies the mark is actually about, named.
 *
 * A mark with no subject is a slogan. «repaint نمی‌کند» beside a chart carrying eleven things is
 * a claim about which of them, and the answer has to be readable — so the names are listed rather
 * than counted.
 */
fun repaintSubjects(state: ChartUiState, signalOnChart: Boolean = false): List<Any> {
    val studies: List<Any> = RepaintClaims.trustedAmong(state.activeIndicators).map(::labelOf)
    // The signal leads, because it is the one a reader cares most about not having been quietly
    // moved — and because on most charts carrying one it is the only subject there is.
    //
    // `List<Any>` for the same reason [ChartExclusion.args] is: a study's name is a string out of
    // the catalogue and the signal's is a resource id, and the caller resolves the mixture.
    return if (signalOnChart) listOf<Any>(SIGNAL_SUBJECT) + studies else studies
}

/** What the mark calls an AI setup drawn over the bars. */
private val SIGNAL_SUBJECT = R.string.provenance_signal_subject

/**
 * An indicator's Persian name, or its id where the catalogue has never heard of it.
 *
 * The fallback is not decoration: a saved per-symbol row can name a study a later build removed,
 * and printing the raw id is how somebody reports it. It is never a blank.
 */
private fun labelOf(id: String): String =
    ChartCatalog.INDICATORS.firstOrNull { it.id == id }?.label ?: id

/**
 * The exclusions as one line, for the chart's own provenance strip.
 *
 * Joined with a space rather than bulleted, because the strip is two lines of eleven-point text
 * under a chart and a bulleted list there would be a paragraph. Empty when there is nothing to
 * say, and the caller draws nothing at all rather than an empty heading.
 *
 * The sentences arrive already resolved and so does [heading] — `R.string.provenance_exclusions_line`,
 * with its one placeholder — because this is not a composable and the export's header calls it too.
 */
fun exclusionsLine(exclusions: List<String>, heading: String): String =
    if (exclusions.isEmpty()) "" else heading.format(exclusions.joinToString(" "))

/**
 * How many bars the chart is currently drawing, as prose.
 *
 * Beside the exclusions because it is the same kind of fact and the same question: a reader who
 * pans back and finds the chart stops wants to know whether that is all there is. A prose count, so
 * its digits follow the screen's language — and so does the noun, which is why this went from a
 * concatenation to a resource in 4.72.0: «۲۰۰ کندل» was being drawn on the English tablet.
 */
@Composable
@ReadOnlyComposable
fun barCountLine(barCount: Int): String =
    stringResource(R.string.chart_bar_count, barCount.proseDigits())

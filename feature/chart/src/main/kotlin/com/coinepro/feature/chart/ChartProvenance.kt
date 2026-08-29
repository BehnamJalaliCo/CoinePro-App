package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.common.toPersianDigits
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
fun chartExclusions(state: ChartUiState): List<String> {
    val out = mutableListOf<String>()
    val plan = resolveCandleRequest(state.interval)

    // Folding first: it is the one that changes every candle on screen rather than merely limiting
    // how many there are, and a reader comparing a two-hour bar against the venue's is comparing
    // against something the venue never sent.
    if (plan.factor > 1) {
        out += "کندل‌های این بازه روی همین دستگاه از کندل‌های " + plan.source.wire +
            " ساخته می‌شوند و از نیمه‌شب تهران شمرده می‌شوند."
    }
    if (state.historyTruncated) {
        out += "به همین دلیل هر بار کندل کمتری می‌آید. برای دیدن گذشتهٔ بیشتر، نمودار را به عقب بکشید."
    }
    // The volume line is the one the TradingView complaint was actually about. It is said whenever
    // the feed carries no volume column, including on a chart with no volume study switched on —
    // the reader who is going to compare against the exchange has not switched anything on either.
    if (!state.series.isEmpty && !state.series.hasVolume) {
        out += "این فید حجم نمی‌فرستد. اندیکاتورها و ابزارهای حجمی روی این نماد نمایش داده نمی‌شوند."
    }
    if (state.replay.isOn) {
        out += "بازپخش روشن است. کندل‌های بعد از نقطهٔ بازپخش عمداً نشان داده نمی‌شوند."
    }
    val repainting = RepaintClaims.repaintingAmong(state.activeIndicators)
    if (repainting.isNotEmpty()) {
        out += repainting.joinToString("، ") { labelOf(it) } + ": " + RepaintClaim.REPAINTS.note
    }
    return out
}

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
fun repaintSubjects(state: ChartUiState, signalOnChart: Boolean = false): List<String> {
    val studies = RepaintClaims.trustedAmong(state.activeIndicators).map(::labelOf)
    // The signal leads, because it is the one a reader cares most about not having been quietly
    // moved — and because on most charts carrying one it is the only subject there is.
    return if (signalOnChart) listOf(SIGNAL_SUBJECT) + studies else studies
}

/** What the mark calls an AI setup drawn over the bars. */
private const val SIGNAL_SUBJECT = "ستاپ هوش مصنوعی"

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
 */
fun exclusionsLine(exclusions: List<String>): String =
    if (exclusions.isEmpty()) "" else "آنچه در این تصویر نیست — " + exclusions.joinToString(" ")

/**
 * How many bars the chart is currently drawing, as prose.
 *
 * Beside the exclusions because it is the same kind of fact and the same question: a reader who
 * pans back and finds the chart stops wants to know whether that is all there is. A prose count,
 * so Persian digits.
 */
fun barCountLine(barCount: Int): String = barCount.toPersianDigits() + " کندل"

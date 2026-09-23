package com.coinepro.core.chart

import kotlin.math.abs

/**
 * One market as the brief counts it.
 *
 * [changePercent] is **null where the feed did not say**, which is not the same as zero. A symbol
 * whose quote came back without a daily change is left out of the count entirely rather than
 * counted as flat — see [RasadBrief.of], where that distinction is the difference between «two of
 * your five markets rose» and a sentence that quietly describes three markets nobody measured.
 */
data class BriefMarket(
    val symbol: String,
    val changePercent: Double? = null,
)

/**
 * The morning brief, composed and ready to be put on a lock screen.
 *
 * [lines] is never empty where this exists at all, and [mover] is null only where nothing in the
 * reader's list had a measurable move.
 */
data class MorningBrief(
    /** One line: how the reader's markets stand. The notification's title. */
    val headline: String,
    /** The market the brief is about, or null on a day when nothing moved. */
    val mover: String? = null,
    /** The body, longest-form. Two lines at most, and the first is always about [mover]. */
    val lines: List<String> = emptyList(),
) {
    /** The body as one paragraph, for a notification that has no room for a list. */
    val body: String get() = lines.joinToString(" ")
}

/**
 * **رصد صبح** — the reader's own markets, once a day, in three sentences at most.
 *
 * ### Why a brief exists beside the alerts, and what makes it different
 *
 * An alert answers a question the reader already asked: «tell me when gold reaches this». The brief
 * answers the one they cannot phrase, because phrasing it would require already knowing the answer:
 * «did anything happen while I was asleep». Every terminal in this market solves that with a badge
 * on an app icon, which tells them there is something to look at and nothing about what.
 *
 * ### It is sent even on a quiet day, and that is the decision
 *
 * The tempting rule is «only send it when something moved», and it is wrong. A scheduled brief that
 * sometimes does not arrive is indistinguishable from a broken one, and the reader's response is
 * not «nothing happened» — it is to stop relying on it and open the app anyway, which is the whole
 * thing the feature was for. So a flat night says so, in one sentence, and costs the reader two
 * seconds. What is *not* sent is a brief about **nothing**: an empty watchlist, or a pass where no
 * market's change could be read at all, produces null rather than a notification with a blank in it.
 *
 * ### Deterministic, like the rest of Rasad
 *
 * Nothing here calls a model, the sentences are templates over arithmetic the reader can check
 * against their own screen, and the clock is a parameter. That is [RasadCoach]'s argument and it
 * holds here for the extra reason that this one arrives **while the reader is not looking at the
 * chart** — a sentence nobody can immediately check against the picture had better be one that
 * could not have been wrong.
 */
object RasadBrief {

    /**
     * Below this, a move is not worth calling a move.
     *
     * A tenth of a percent, which is inside the spread on most of what this app carries. Naming a
     * 0.04 % night as the day's mover would be the brief manufacturing a story out of noise, and a
     * reader who acted on one would have learned exactly the wrong lesson from it.
     */
    const val QUIET_PERCENT: Double = 0.1

    /**
     * The brief, or null where there is nothing honest to say.
     *
     * @param markets the reader's own list, **in their order** — which decides ties.
     * @param moverSeries the bars of whichever market [of] would pick, where the caller has them.
     *   Optional: the count line stands alone, and a caller that could not fetch candles gets a
     *   shorter brief rather than none. Passing the *wrong* symbol's series is the one way to make
     *   this lie, so callers resolve it with [moverOf] rather than by guessing.
     */
    fun of(
        markets: List<BriefMarket>,
        moverSeries: CandleSeries? = null,
        english: Boolean = false,
    ): MorningBrief? {
        val measured = markets.filter { it.changePercent?.isFinite() == true }
        if (measured.isEmpty()) return null
        val up = measured.count { it.changePercent!! > 0.0 }
        val down = measured.count { it.changePercent!! < 0.0 }
        val mover = moverOf(markets)
        val move = mover?.changePercent ?: 0.0
        if (mover == null || abs(move) < QUIET_PERCENT) {
            return MorningBrief(
                headline = quietHeadline(measured.size, english),
                lines = listOf(quietLine(english)),
            )
        }
        return MorningBrief(
            headline = headline(up, down, english),
            mover = mover.symbol,
            lines = listOfNotNull(
                moverLine(mover.symbol, move, english),
                moverSeries?.let { RasadCoach.readChart(it, english = english).firstOrNull() },
            ),
        )
    }

    /**
     * The market the brief is about: the largest move **either way**.
     *
     * Absolute, because a six-percent fall is the reader's morning as much as a six-percent rise,
     * and a brief that only ever named risers would be the advertisement [RasadCoach] refuses to
     * be. Ties go to whichever comes first in the reader's own list — that is their ordering of
     * what matters, and it is more meaningful than anything this could invent.
     */
    fun moverOf(markets: List<BriefMarket>): BriefMarket? = markets
        .filter { it.changePercent?.isFinite() == true }
        .maxByOrNull { abs(it.changePercent!!) }

    private fun headline(up: Int, down: Int, english: Boolean): String = when {
        up > 0 && down > 0 -> if (english) {
            "$up up, $down down"
        } else {
            "${count(up)} بالا، ${count(down)} پایین"
        }
        up > 0 -> if (english) "$up up" else "${count(up)} بالا"
        down > 0 -> if (english) "$down down" else "${count(down)} پایین"
        // Every measured market at exactly zero. Rare, and it is the quiet branch in all but name.
        else -> quietHeadline(up + down, english)
    }

    private fun quietHeadline(measured: Int, english: Boolean): String = if (english) {
        "A quiet night across your $measured markets"
    } else {
        "شب آرامی برای ${count(measured)} بازار شما"
    }

    private fun quietLine(english: Boolean): String = if (english) {
        "Nothing on your list moved enough to be worth naming."
    } else {
        "هیچ‌کدام از بازارهای فهرست شما آن‌قدر تکان نخورد که ارزش نام‌بردن داشته باشد."
    }

    /**
     * «BTCUSDT ۲٫۴٪ بالا رفت» — the mover, its direction and its figure.
     *
     * The percentage is **Latin digits** and the symbol is untouched, because both are market
     * figures a reader compares against another terminal; the counts in the headline are Persian,
     * because those are prose. That split is the app's rule and this is the one file in the coach
     * where both appear in the same breath.
     */
    private fun moverLine(symbol: String, changePercent: Double, english: Boolean): String {
        val figure = formatPrice(abs(changePercent), 2)
        return if (english) {
            val direction = if (changePercent > 0) "rose" else "fell"
            "$symbol $direction $figure%."
        } else {
            val direction = if (changePercent > 0) "بالا رفت" else "پایین آمد"
            "$symbol $figure٪ $direction."
        }
    }

    /**
     * A prose count, in Persian digits.
     *
     * Duplicated from `core:common`'s `toPersianDigits` rather than depended on, for the reason in
     * this module's own note: `:chart-core` has no Android on its classpath and ships to the web
     * terminal, and `core:common` is not on that path. Four lines, and the rule they encode — prose
     * counts in Persian, market figures in Latin — is stated in both places.
     */
    private fun count(value: Int): String = value.toString().map { character ->
        if (character in '0'..'9') '۰' + (character - '0') else character
    }.joinToString("")
}

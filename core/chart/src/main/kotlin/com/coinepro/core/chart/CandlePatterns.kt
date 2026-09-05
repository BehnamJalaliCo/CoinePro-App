package com.coinepro.core.chart

import androidx.annotation.DrawableRes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.coinepro.core.designsystem.R as DesignR

/**
 * A candlestick pattern the detector knows by name.
 *
 * [bars] is how many bars the shape spans, which is what a caller needs to know before it can say
 * where the pattern starts: a [PatternHit] is reported on the bar that *completes* it, so a
 * three-bar morning star found at bar 240 began at 238.
 *
 * The name changes with the direction where the pattern is symmetric — an engulfing bar is «پوشای
 * صعودی» or «پوشای نزولی» and traders never say just "engulfing" — and stays put where it does not:
 * a hammer is a hammer, and the bearish version of that shape has its own name, «مرد آویزان».
 */
enum class CandlePattern(
    val bars: Int,
    private val bullName: String,
    private val bearName: String = bullName,
) {
    /** A bar that opened and closed at nearly the same price. */
    DOJI(1, "دوجی"),

    /** A long lower shadow after a decline. */
    HAMMER(1, "چکش"),

    /** A long upper shadow after a decline. */
    INVERTED_HAMMER(1, "چکش وارونه"),

    /** The hammer's shape, but after an advance. */
    HANGING_MAN(1, "مرد آویزان"),

    /** The inverted hammer's shape, but after an advance. */
    SHOOTING_STAR(1, "ستاره‌ی دنباله‌دار"),

    /** A body that swallows the one before it, the other way up. */
    ENGULFING(2, "پوشای صعودی", "پوشای نزولی"),

    /** A small body wholly inside the previous long one. */
    HARAMI(2, "هارامی صعودی", "هارامی نزولی"),

    /** A harami whose inside bar is a doji. */
    HARAMI_CROSS(2, "هارامی متقاطع صعودی", "هارامی متقاطع نزولی"),

    /** A rise that closes back above the middle of the previous fall. */
    PIERCING_LINE(2, "خط نفوذی"),

    /** A fall that closes back below the middle of the previous rise. */
    DARK_CLOUD_COVER(2, "پوشش ابر سیاه"),

    /** Fall, pause, rise. */
    MORNING_STAR(3, "ستاره‌ی صبحگاهی"),

    /** Rise, pause, fall. */
    EVENING_STAR(3, "ستاره‌ی شامگاهی"),

    /** Three rising bars, each opening inside the last body. */
    THREE_WHITE_SOLDIERS(3, "سه سرباز سفید"),

    /** Three falling bars, each opening inside the last body. */
    THREE_BLACK_CROWS(3, "سه کلاغ سیاه"),

    /** Two bars rejected from the same high. */
    TWEEZER_TOP(2, "انبرک سقف"),

    /** Two bars held at the same low. */
    TWEEZER_BOTTOM(2, "انبرک کف"),

    /** A bar that is body and almost nothing else. */
    MARUBOZU(1, "ماروبوزوی صعودی", "ماروبوزوی نزولی");

    /** What this pattern is called when it reads [bullish]. */
    fun persianName(bullish: Boolean): String = if (bullish) bullName else bearName
}

/**
 * One pattern found on one bar.
 *
 * [index] is the bar that completed the shape, not the one it started on. [bullish] is what the
 * pattern *says*, which for a doji is a statement about the trend it interrupts rather than about
 * the bar's own colour — a doji after a decline is read as a pause in that decline.
 *
 * [strength] runs 0 to 1 and is a ranking, not a probability. It combines how cleanly the bar
 * matches the shape with how large it is against the recent average range, because the same
 * geometry on a bar a third of the usual size is the same picture drawn much smaller and means
 * proportionally less. Two hits are comparable to each other; neither is a percentage of anything.
 */
data class PatternHit(
    val index: Int,
    val pattern: CandlePattern,
    val bullish: Boolean,
    val strength: Double,
)

/**
 * A pattern as the picker offers it, so it can be switched on the way an indicator is.
 *
 * Deliberately not an [IndicatorOption]. A pattern is not an indicator: it produces no value per
 * bar, has no lookback and no pane, and putting it in [ChartCatalog.INDICATORS] would mean every
 * caller that walks that list — the alert engine, the layout store, the period stepper — had to
 * learn about a row that answers none of their questions. It is its own list with its own ids, and
 * the ids are prefixed so nothing can confuse one with an indicator.
 */
data class PatternOption(
    val id: String,
    val pattern: CandlePattern,
    /** The Persian name, in its bullish form where the pattern has two. */
    val label: String,
    /** ARGB for a bullish hit. A bearish one is drawn in [PATTERN_BEAR]. */
    val colour: Long = PATTERN_BULL,
    @DrawableRes val icon: Int = DesignR.drawable.tv_chart_candles,
)

/** The colour a bullish hit is marked in — the app's own «up», so it matches the candles. */
const val PATTERN_BULL = 0xFF00B15C

/** The colour a bearish hit is marked in. */
const val PATTERN_BEAR = 0xFFF6465D

/**
 * Candlestick patterns, found by shape rather than by price.
 *
 * ### Read this before you trade one
 *
 * **A pattern is a prior, not a signal.** It says the last one, two or three bars had a particular
 * shape; it does not say what the next bar does. On a five-minute crypto chart a «چکش» appears
 * several times an hour, and most of them are followed by more decline — the shape is common
 * because the market is noisy, not because a reversal is at hand. Every one of these is worth
 * something only in a context this detector cannot see: where the bar sits against a level, what
 * the higher timeframe is doing, whether volume confirms it. A reader who takes every hammer will
 * lose money slowly and consistently, and the help text is where they get to find that out before
 * it happens rather than after.
 *
 * That is also why [PatternHit.strength] exists and why it is documented as a ranking. The honest
 * use of this detector is to sort a hundred hits and look at the five strongest, not to act on all
 * hundred.
 *
 * ### Why every rule is a ratio
 *
 * Each test is written against the bar's own range — «the lower shadow is at least 55% of the
 * bar» — and never against a price difference. An implementation that says "the shadow is at least
 * two dollars" works on one instrument and on no other: this app charts gold at 2,400 and coins at
 * 0.08 in the same list, and an absolute threshold makes the same shape a hammer on one and
 * invisible on the other. The one place a comparison to something outside the bar appears is the
 * *size* term in [PatternHit.strength], and it is a ratio too — this bar's range over the average
 * of the last [AVERAGE_BARS].
 *
 * ### The two-sided shapes
 *
 * A hammer and a hanging man are the same drawing; so are an inverted hammer and a shooting star.
 * What separates them is only what came before, so the detector reads the [TREND_BARS] closes
 * before the bar and refuses to call either one in a market that was going sideways. A shape with
 * no trend behind it is not a reversal of anything.
 */
object CandlePatterns {

    /** How many bars the average range is measured over, for the size term in the strength. */
    const val AVERAGE_BARS = 14

    /** How many closes back the detector looks to decide what the market was doing. */
    const val TREND_BARS = 5

    /** Every pattern, as the picker lists them: single bars first, then two, then three. */
    val OPTIONS: List<PatternOption> = listOf(
        PatternOption("pattern_doji", CandlePattern.DOJI, "دوجی"),
        PatternOption("pattern_hammer", CandlePattern.HAMMER, "چکش"),
        PatternOption("pattern_inverted_hammer", CandlePattern.INVERTED_HAMMER, "چکش وارونه"),
        PatternOption("pattern_hanging_man", CandlePattern.HANGING_MAN, "مرد آویزان", PATTERN_BEAR),
        PatternOption("pattern_shooting_star", CandlePattern.SHOOTING_STAR, "ستاره‌ی دنباله‌دار", PATTERN_BEAR),
        PatternOption("pattern_marubozu", CandlePattern.MARUBOZU, "ماروبوزو"),
        PatternOption("pattern_engulfing", CandlePattern.ENGULFING, "پوشا"),
        PatternOption("pattern_harami", CandlePattern.HARAMI, "هارامی"),
        PatternOption("pattern_harami_cross", CandlePattern.HARAMI_CROSS, "هارامی متقاطع"),
        PatternOption("pattern_piercing", CandlePattern.PIERCING_LINE, "خط نفوذی"),
        PatternOption("pattern_dark_cloud", CandlePattern.DARK_CLOUD_COVER, "پوشش ابر سیاه", PATTERN_BEAR),
        PatternOption("pattern_tweezer_top", CandlePattern.TWEEZER_TOP, "انبرک سقف", PATTERN_BEAR),
        PatternOption("pattern_tweezer_bottom", CandlePattern.TWEEZER_BOTTOM, "انبرک کف"),
        PatternOption("pattern_morning_star", CandlePattern.MORNING_STAR, "ستاره‌ی صبحگاهی"),
        PatternOption("pattern_evening_star", CandlePattern.EVENING_STAR, "ستاره‌ی شامگاهی", PATTERN_BEAR),
        PatternOption("pattern_three_soldiers", CandlePattern.THREE_WHITE_SOLDIERS, "سه سرباز سفید"),
        PatternOption("pattern_three_crows", CandlePattern.THREE_BLACK_CROWS, "سه کلاغ سیاه", PATTERN_BEAR),
    )

    /** The option a picker id names, or null for an id this build does not know. */
    fun optionOf(id: String): PatternOption? = OPTIONS.firstOrNull { it.id == id }

    /**
     * Every hit in [series], oldest first.
     *
     * [patterns] is what the reader switched on. Empty means every pattern, which is the useful
     * default for a scan and a terrible one for a chart — seventeen patterns marked at once is a
     * wall of arrows with no reading in it, and the picker exists so a reader chooses two or three.
     *
     * The window is inclusive at both ends and clamped, so a caller can hand it the visible range
     * without checking it first.
     */
    fun detect(
        series: CandleSeries,
        patterns: Set<CandlePattern> = emptySet(),
        fromIndex: Int = 0,
        toIndex: Int = series.size - 1,
    ): List<PatternHit> {
        if (series.isEmpty) return emptyList()
        val wanted = if (patterns.isEmpty()) CandlePattern.entries.toSet() else patterns
        val first = max(0, fromIndex)
        val last = min(series.size - 1, toIndex)
        if (first > last) return emptyList()
        val hits = ArrayList<PatternHit>()
        for (index in first..last) {
            val average = averageRange(series, index)
            for (pattern in CandlePattern.entries) {
                if (pattern !in wanted) continue
                match(pattern, series, index, average)?.let { hits += it }
            }
        }
        return hits
    }

    /**
     * The hits as marks on the chart, for the ids a reader switched on.
     *
     * Bullish under the bar and bearish over it, which is the convention every terminal uses and
     * the only one that keeps the mark off the part of the bar it is talking about. The Persian
     * name rides along as [ChartMarker.text] so a reader who does not recognise an arrow can read
     * what it claims.
     */
    fun markersFor(series: CandleSeries, ids: Set<String>): List<ChartMarker> {
        if (ids.isEmpty() || series.isEmpty) return emptyList()
        val chosen = ids.mapNotNull { optionOf(it) }
        if (chosen.isEmpty()) return emptyList()
        val patterns = chosen.map { it.pattern }.toSet()
        return detect(series, patterns).map { hit ->
            val bar = series[hit.index]
            ChartMarker(
                time = bar.t,
                price = if (hit.bullish) bar.l else bar.h,
                above = !hit.bullish,
                colour = if (hit.bullish) PATTERN_BULL else PATTERN_BEAR,
                glyph = if (hit.bullish) MarkerGlyph.ARROW_UP else MarkerGlyph.ARROW_DOWN,
                text = hit.pattern.persianName(hit.bullish),
            )
        }
    }

    /**
     * One pattern against one bar, or null.
     *
     * A long `when` rather than seventeen entry points, because the shared preamble — the bar, its
     * neighbours, the ratios — is the same for all of them and the alternative is seventeen copies
     * of it that can drift apart.
     */
    private fun match(
        pattern: CandlePattern,
        series: CandleSeries,
        index: Int,
        average: Double,
    ): PatternHit? {
        if (index < pattern.bars - 1) return null
        val bar = series[index]
        val range = bar.range
        if (range <= 0.0) return null
        val body = abs(bar.c - bar.o) / range
        val upper = (bar.h - max(bar.o, bar.c)) / range
        val lower = (min(bar.o, bar.c) - bar.l) / range
        val size = sizeFactor(range, average)
        val trend = trendBefore(series, index, average)
        return when (pattern) {
            CandlePattern.DOJI -> {
                if (body > DOJI_BODY || range < 0.3 * average) return null
                hit(pattern, index, trend < 0, shape = norm(DOJI_BODY - body, 0.0, DOJI_BODY), size = size)
            }
            CandlePattern.HAMMER ->
                if (trend < 0 && isHammerShape(body, upper, lower)) {
                    hit(pattern, index, true, norm(lower, SHADOW_LONG, 0.75), size)
                } else {
                    null
                }
            CandlePattern.HANGING_MAN ->
                if (trend > 0 && isHammerShape(body, upper, lower)) {
                    hit(pattern, index, false, norm(lower, SHADOW_LONG, 0.75), size)
                } else {
                    null
                }
            CandlePattern.INVERTED_HAMMER ->
                if (trend < 0 && isHammerShape(body, lower, upper)) {
                    hit(pattern, index, true, norm(upper, SHADOW_LONG, 0.75), size)
                } else {
                    null
                }
            CandlePattern.SHOOTING_STAR ->
                if (trend > 0 && isHammerShape(body, lower, upper)) {
                    hit(pattern, index, false, norm(upper, SHADOW_LONG, 0.75), size)
                } else {
                    null
                }
            CandlePattern.MARUBOZU ->
                if (body >= MARUBOZU_BODY && upper <= MARUBOZU_SHADOW && lower <= MARUBOZU_SHADOW) {
                    hit(pattern, index, bar.up, norm(body, MARUBOZU_BODY, 1.0), size)
                } else {
                    null
                }
            CandlePattern.ENGULFING -> engulfing(series, index, size)
            CandlePattern.HARAMI -> harami(series, index, size, cross = false)
            CandlePattern.HARAMI_CROSS -> harami(series, index, size, cross = true)
            CandlePattern.PIERCING_LINE -> piercing(series, index, size, bullish = true)
            CandlePattern.DARK_CLOUD_COVER -> piercing(series, index, size, bullish = false)
            CandlePattern.TWEEZER_TOP -> tweezer(series, index, size, trend, top = true)
            CandlePattern.TWEEZER_BOTTOM -> tweezer(series, index, size, trend, top = false)
            CandlePattern.MORNING_STAR -> star(series, index, size, bullish = true)
            CandlePattern.EVENING_STAR -> star(series, index, size, bullish = false)
            CandlePattern.THREE_WHITE_SOLDIERS -> three(series, index, size, rising = true)
            CandlePattern.THREE_BLACK_CROWS -> three(series, index, size, rising = false)
        }
    }

    /**
     * The hammer family's geometry: one dominant shadow, almost nothing on the other side.
     *
     * Written once and called four times with the shadows swapped, because a hammer and an inverted
     * hammer are one test read upside down and two copies of it would eventually disagree.
     */
    private fun isHammerShape(body: Double, short: Double, long: Double): Boolean =
        body <= HAMMER_BODY && long >= SHADOW_LONG && short <= SHADOW_SHORT

    private fun engulfing(series: CandleSeries, index: Int, size: Double): PatternHit? {
        if (index < 1) return null
        val previous = series[index - 1]
        val bar = series[index]
        if (bar.up == previous.up) return null
        val previousBody = abs(previous.c - previous.o)
        val bodyHeight = abs(bar.c - bar.o)
        val previousRange = previous.range
        if (previousRange <= 0.0 || previousBody / previousRange < DOJI_BODY) return null
        if (bodyHeight / bar.range < LONG_BODY / 2) return null
        val top = max(bar.o, bar.c)
        val bottom = min(bar.o, bar.c)
        val previousTop = max(previous.o, previous.c)
        val previousBottom = min(previous.o, previous.c)
        if (top < previousTop || bottom > previousBottom) return null
        if (bodyHeight <= previousBody) return null
        return hit(
            CandlePattern.ENGULFING,
            index,
            bar.up,
            norm(bodyHeight / previousBody, 1.0, 2.0),
            size,
        )
    }

    private fun harami(series: CandleSeries, index: Int, size: Double, cross: Boolean): PatternHit? {
        if (index < 1) return null
        val previous = series[index - 1]
        val bar = series[index]
        // The colour rule is dropped for the cross, and that is not laziness: a true doji closes
        // where it opened, `Candle.up` calls that "up" the way every terminal draws it, and a
        // bearish harami cross would then be rejected for having the wrong colour it does not have.
        if (!cross && bar.up == previous.up) return null
        val previousRange = previous.range
        val range = bar.range
        if (previousRange <= 0.0 || range <= 0.0) return null
        val previousBody = abs(previous.c - previous.o)
        val bodyHeight = abs(bar.c - bar.o)
        if (previousBody / previousRange < LONG_BODY) return null
        val isDoji = bodyHeight / range <= DOJI_BODY
        if (cross != isDoji) return null
        if (bodyHeight > HARAMI_INSIDE * previousBody) return null
        val top = max(bar.o, bar.c)
        val bottom = min(bar.o, bar.c)
        if (top > max(previous.o, previous.c) || bottom < min(previous.o, previous.c)) return null
        val pattern = if (cross) CandlePattern.HARAMI_CROSS else CandlePattern.HARAMI
        return hit(pattern, index, !previous.up, norm(1 - bodyHeight / previousBody, 0.5, 0.9), size)
    }

    /**
     * The piercing line and its mirror, the dark cloud cover.
     *
     * The classical rule wants the second bar to *open past the first bar's extreme*, which is a
     * gap. This app's crypto feeds trade continuously and gap perhaps twice a year, so the strict
     * rule would make both patterns extinct on most of the charts here; the test is against the
     * previous **close** instead. That is a deliberate loosening and it is the reason a piercing
     * line found here is worth a little less than one found on a stock chart.
     */
    private fun piercing(series: CandleSeries, index: Int, size: Double, bullish: Boolean): PatternHit? {
        if (index < 1) return null
        val previous = series[index - 1]
        val bar = series[index]
        val previousRange = previous.range
        if (previousRange <= 0.0) return null
        val previousBody = abs(previous.c - previous.o)
        if (previousBody / previousRange < LONG_BODY) return null
        val middle = (previous.o + previous.c) / 2
        val ok = if (bullish) {
            !previous.up && bar.up && bar.o < previous.c && bar.c > middle && bar.c < previous.o
        } else {
            previous.up && !bar.up && bar.o > previous.c && bar.c < middle && bar.c > previous.o
        }
        if (!ok) return null
        val penetration = abs(bar.c - previous.c) / previousBody
        val pattern = if (bullish) CandlePattern.PIERCING_LINE else CandlePattern.DARK_CLOUD_COVER
        return hit(pattern, index, bullish, norm(penetration, 0.5, 1.0), size)
    }

    private fun tweezer(
        series: CandleSeries,
        index: Int,
        size: Double,
        trend: Int,
        top: Boolean,
    ): PatternHit? {
        if (index < 1) return null
        val previous = series[index - 1]
        val bar = series[index]
        val span = max(previous.range, bar.range)
        if (span <= 0.0) return null
        val gap = if (top) abs(bar.h - previous.h) else abs(bar.l - previous.l)
        if (gap > TWEEZER_TOLERANCE * span) return null
        val ok = if (top) trend > 0 && previous.up && !bar.up else trend < 0 && !previous.up && bar.up
        if (!ok) return null
        val pattern = if (top) CandlePattern.TWEEZER_TOP else CandlePattern.TWEEZER_BOTTOM
        return hit(pattern, index, !top, norm(TWEEZER_TOLERANCE - gap / span, 0.0, TWEEZER_TOLERANCE), size)
    }

    private fun star(series: CandleSeries, index: Int, size: Double, bullish: Boolean): PatternHit? {
        if (index < 2) return null
        val first = series[index - 2]
        val middle = series[index - 1]
        val last = series[index]
        val firstRange = first.range
        if (firstRange <= 0.0) return null
        val firstBody = abs(first.c - first.o)
        if (firstBody / firstRange < LONG_BODY) return null
        if (first.up == bullish) return null
        val middleBody = abs(middle.c - middle.o)
        if (middleBody > STAR_MIDDLE * firstBody) return null
        if (last.up != bullish) return null
        val firstMiddle = (first.o + first.c) / 2
        val middleTop = max(middle.o, middle.c)
        val middleBottom = min(middle.o, middle.c)
        val ok = if (bullish) {
            middleTop <= min(first.o, first.c) + STAR_OVERLAP * firstBody && last.c > firstMiddle
        } else {
            middleBottom >= max(first.o, first.c) - STAR_OVERLAP * firstBody && last.c < firstMiddle
        }
        if (!ok) return null
        val recovery = abs(last.c - firstMiddle) / firstBody
        val pattern = if (bullish) CandlePattern.MORNING_STAR else CandlePattern.EVENING_STAR
        return hit(pattern, index, bullish, norm(recovery, 0.0, 0.5), size)
    }

    private fun three(series: CandleSeries, index: Int, size: Double, rising: Boolean): PatternHit? {
        if (index < 2) return null
        var advance = 0.0
        for (step in 0..2) {
            val bar = series[index - 2 + step]
            val range = bar.range
            if (range <= 0.0) return null
            if (bar.up != rising) return null
            if (abs(bar.c - bar.o) / range < LONG_BODY) return null
            val tail = if (rising) (bar.h - max(bar.o, bar.c)) / range else (min(bar.o, bar.c) - bar.l) / range
            if (tail > THREE_TAIL) return null
            if (step == 0) continue
            val previous = series[index - 3 + step]
            if (rising && bar.c <= previous.c) return null
            if (!rising && bar.c >= previous.c) return null
            val previousTop = max(previous.o, previous.c)
            val previousBottom = min(previous.o, previous.c)
            if (bar.o > previousTop || bar.o < previousBottom) return null
            advance += abs(bar.c - previous.c) / previous.range
        }
        val pattern = if (rising) CandlePattern.THREE_WHITE_SOLDIERS else CandlePattern.THREE_BLACK_CROWS
        return hit(pattern, index, rising, norm(advance / 2, 0.3, 1.0), size)
    }

    /**
     * What the market was doing in the [TREND_BARS] bars before [index]: 1 up, −1 down, 0 sideways.
     *
     * Measured in units of the average range rather than in price, for the reason every other test
     * here is a ratio. Sideways is a real answer and the common one: it is what stops a hammer from
     * being called on every bar with a long lower wick in a market that is going nowhere.
     */
    private fun trendBefore(series: CandleSeries, index: Int, average: Double): Int {
        val back = index - TREND_BARS
        if (back < 0 || average <= 0.0) return 0
        val move = (series.close[index - 1] - series.close[back]) / average
        return when {
            move >= TREND_MOVE -> 1
            move <= -TREND_MOVE -> -1
            else -> 0
        }
    }

    /** The mean bar range over the [AVERAGE_BARS] bars ending at [index]. */
    private fun averageRange(series: CandleSeries, index: Int): Double {
        val first = max(0, index - AVERAGE_BARS + 1)
        var total = 0.0
        for (step in first..index) total += series[step].range
        return total / (index - first + 1)
    }

    /** How large this bar is against its neighbours, clamped to 0..1. */
    private fun sizeFactor(range: Double, average: Double): Double =
        if (average <= 0.0) 0.5 else norm(range / average, 0.5, 1.5)

    private fun hit(
        pattern: CandlePattern,
        index: Int,
        bullish: Boolean,
        shape: Double,
        size: Double,
    ): PatternHit = PatternHit(
        index = index,
        pattern = pattern,
        bullish = bullish,
        // Shape carries more weight than size: a textbook hammer on a quiet bar is still a hammer,
        // while a huge bar that only roughly resembles one is mostly just a huge bar.
        strength = (SHAPE_WEIGHT * shape + (1 - SHAPE_WEIGHT) * size).coerceIn(0.0, 1.0),
    )

    /** [value] mapped onto 0..1 across [at0]..[at1], clamped at both ends. */
    private fun norm(value: Double, at0: Double, at1: Double): Double =
        ((value - at0) / (at1 - at0)).coerceIn(0.0, 1.0)

    /** A body of five percent of the range or less. Below this the bar has no direction. */
    private const val DOJI_BODY = 0.05

    /** A dominant shadow: at least this much of the bar. */
    private const val SHADOW_LONG = 0.55

    /** The shadow on the other side may be at most this much of the bar. */
    private const val SHADOW_SHORT = 0.15

    /** A hammer's body is small — at most this much of its range. */
    private const val HAMMER_BODY = 0.3

    /** What counts as a long body, as a share of the bar's range. */
    private const val LONG_BODY = 0.6

    /** A marubozu is body and almost nothing else. */
    private const val MARUBOZU_BODY = 0.9
    private const val MARUBOZU_SHADOW = 0.05

    /** A harami's inside body may be at most this share of the body that contains it. */
    private const val HARAMI_INSIDE = 0.5

    /** Two highs (or lows) this close, relative to the larger bar, count as the same level. */
    private const val TWEEZER_TOLERANCE = 0.08

    /** A star's middle bar may be at most this share of the first bar's body. */
    private const val STAR_MIDDLE = 0.3

    /** How far the star's middle body may reach back into the first bar's body. */
    private const val STAR_OVERLAP = 0.25

    /** The trailing shadow allowed on each of the three soldiers or crows. */
    private const val THREE_TAIL = 0.25

    /** A move of this many average ranges over [TREND_BARS] bars is a trend rather than drift. */
    private const val TREND_MOVE = 0.5

    /** How much of [PatternHit.strength] is the shape rather than the size. */
    private const val SHAPE_WEIGHT = 0.6
}

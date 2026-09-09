package com.coinepro.core.chart

/**
 * How far across the plot a setup's shading is allowed to reach.
 *
 * ### Why this is a type rather than two floats inside the draw pass
 *
 * Because the answer is a *claim about history* and the draw pass cannot be tested. The zones used
 * to be drawn from x zero to the right-hand edge on every chart that carried a [SignalOverlay],
 * which paints the whole visible series green above the entry and red below it — and a reader looks
 * at that and reads "this position was open for all of this". It was not. Before the entry bar
 * there was no stop and no target, and shading that region asserts two prices that did not exist.
 *
 * So the span is worked out here, from the viewport and the setup's own timestamps, and the
 * renderer only fills the rectangle it is handed.
 *
 * Everything is in **plot pixels**: x zero is the plot's left edge, the same space
 * [ChartViewport.xOf] works in.
 */
data class SetupSpan(
    /** Left edge of the shading, never left of the plot and never left of the entry bar. */
    val left: Float,
    /** Right edge: the live edge, or the bar the position closed on. */
    val right: Float,
    /**
     * Where to put the mark that says "it began here", or null when the entry bar is off screen.
     *
     * Null is the ordinary case for a reader who has scrolled forward past a long-running setup:
     * the zone still draws, from the left edge, because the position genuinely was open across
     * every bar on screen — but there is no bar here to mark, and a mark pinned to the edge would
     * claim the position opened at a time it did not.
     */
    val entryX: Float?,
    /**
     * Whether the setup told us when it began.
     *
     * False means no [SignalOverlay.issuedAt] was supplied, and the honest drawing of a setup with
     * no time on it is its prices and nothing else — the levels are true whenever they are read, the
     * shading is not. The renderer draws the hairlines and their labels and skips both fills, rather
     * than falling back to a full-width band that would state the thing we do not know.
     */
    val anchored: Boolean,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)

    /** Nothing to paint: the setup's whole life is off one side of the plot, or it has no width. */
    val isEmpty: Boolean get() = width <= 0f
}

/**
 * Where [signal]'s zones start and stop, against the bars currently on screen.
 *
 * The rules, in the order they matter:
 *
 * * **Nothing left of the entry bar.** The left edge is the leading edge of the bar the position
 *   opened on — the entry candle is *inside* the zone, because that is the candle the reader is
 *   being pointed at — clamped to the plot so an entry that has scrolled off the left draws from
 *   the left edge instead of from a negative x.
 * * **Right to the live edge, or to the close.** An open position runs to the right-hand edge of the
 *   plot, blank slots included: it is still open, and the empty slots are the near future it is open
 *   into. A closed one stops at the bar it closed on, that bar included.
 * * **A setup entirely off screen paints nothing.** A reader panned back before the entry gets an
 *   empty span rather than a band pinned to the edge, and so does the impossible case of a close
 *   before its own entry.
 * * **No entry time, no shading.** See [SetupSpan.anchored].
 *
 * [ChartViewport.xOfTime] interpolates and extrapolates at the current bar spacing, which is what
 * makes this correct for the two awkward timestamps: a setup issued between two bars, and one issued
 * after the newest bar — a signal that fired at 10:03 on a chart whose last bar opened at 10:00
 * belongs a fraction of a slot right of that bar, not on top of it.
 */
fun setupSpan(view: ChartViewport, signal: SignalOverlay): SetupSpan {
    val plotWidth = view.plotWidth.coerceAtLeast(0f)
    val issuedAt = signal.issuedAt
    if (issuedAt == null || view.series.isEmpty || plotWidth <= 0f) {
        return SetupSpan(left = 0f, right = plotWidth, entryX = null, anchored = false)
    }
    // Half a slot, so the entry candle is inside its own zone rather than bisected by the edge of
    // it. `xOf` places a bar at the centre of its slot; the zone starts where the slot does.
    val half = view.barWidth / 2f
    val entryX = view.xOfTime(issuedAt)
    val left = (entryX - half).coerceIn(0f, plotWidth)
    val right = signal.closedAt
        ?.let { (view.xOfTime(it) + half).coerceIn(0f, plotWidth) }
        ?: plotWidth
    return SetupSpan(
        left = left,
        right = if (right < left) left else right,
        entryX = entryX.takeIf { it >= 0f && it <= plotWidth },
        anchored = true,
    )
}

/** Which half of a setup a band is: the money at risk, or the money on offer. */
enum class SetupBandRole {
    /** Entry to stop. Drawn in the sell colour, because reaching it is the loss. */
    RISK,

    /** Entry to the first target. Drawn in the buy colour. */
    REWARD,
}

/**
 * One shaded band of a setup, as two prices and what it means.
 *
 * The vertical half of the same question [setupSpan] answers horizontally, and here for the same
 * reason: which side of the entry the red goes on is the single thing a reader must not have to
 * check, and it is decided by arithmetic that can be asserted rather than inside a draw pass that
 * cannot.
 */
data class SetupBand(val role: SetupBandRole, val from: Double, val to: Double)

/**
 * The bands a setup gets, in draw order.
 *
 * Taken from the prices rather than from [SignalOverlay.isLong], and that is what makes a short
 * invert for free: a short's stop is *above* its entry and its target *below*, so the sell-coloured
 * band lands over the entry line and the buy-coloured one under it without a branch. A rule written
 * as "risk is below entry on a long, above on a short" is the same rule with a way to get it wrong.
 *
 * Only the first take-profit is filled. The rest are drawn as lines: three stacked green areas of
 * different heights say nothing about the trade that the three lines do not, and the shade under the
 * furthest one would be four layers of tint deep.
 */
fun setupBands(signal: SignalOverlay): List<SetupBand> =
    setupBands(signal.entry, signal.stopLoss, signal.takeProfits.firstOrNull())

/**
 * The same rule from three loose prices, for the reader's own «موقعیت خرید/فروش» tool.
 *
 * That tool is the second thing in the app that shades a setup, and it has to stay a separate
 * *renderer* — its bands are dragged by handles, its span is the two points the reader tapped, and
 * its label is the reward multiple rather than a signal's words. What it must not have is a second
 * opinion about which side of the entry the red goes on, which is the half that is shared here.
 */
fun setupBands(entry: Double, stopLoss: Double?, target: Double?): List<SetupBand> = buildList {
    if (!entry.isFinite()) return@buildList
    stopLoss
        ?.takeIf { it.isFinite() && it != entry }
        ?.let { add(SetupBand(SetupBandRole.RISK, entry, it)) }
    target
        ?.takeIf { it.isFinite() && it != entry }
        ?.let { add(SetupBand(SetupBandRole.REWARD, entry, it)) }
}

package com.coinepro.feature.heatmap

import kotlin.math.max
import kotlin.math.min

/**
 * One rectangle of a treemap, in the map's own pixel space.
 *
 * Deliberately not `androidx.compose.ui.geometry.Rect`. The layout that produces these is pure
 * arithmetic and has to stay unit-testable on the JVM without a Compose classpath, and a geometry
 * type from the UI toolkit would drag the whole toolkit into the one file in this feature that
 * genuinely has an algorithm in it. The conversion at the draw site is two field reads.
 *
 * The origin is the top-left of the container in *layout* space. Mirroring for a right-to-left
 * reader happens once, in [mirroredIn], and it is applied to the plan rather than to the canvas so
 * that hit-testing and drawing cannot disagree about where a tile is.
 */
data class Rect4(val x: Float, val y: Float, val w: Float, val h: Float) {

    /** The far edge on the horizontal axis. Named because `x + w` appears in every boundary test. */
    val right: Float get() = x + w

    /** The far edge on the vertical axis. */
    val bottom: Float get() = y + h

    val area: Float get() = w * h

    /**
     * The longer side over the shorter one, so it is always at least one and can be compared
     * between a tall tile and a wide one without a sign convention.
     *
     * A degenerate rectangle reports [Float.MAX_VALUE] rather than infinity or zero: it is the
     * worst possible shape, and returning zero — which a naive `w / h` does when the width is zero
     * — would make it look like the best one to anything taking a maximum.
     */
    val aspect: Float get() = if (w <= 0f || h <= 0f) Float.MAX_VALUE else max(w / h, h / w)

    /** Whether a point is inside, with the far edges exclusive so neighbours cannot both claim it. */
    fun contains(px: Float, py: Float): Boolean = px >= x && px < right && py >= y && py < bottom

    /**
     * The same rectangle reflected about the vertical centre line of a container [width] wide.
     *
     * A treemap puts its largest tile at the origin, and for a Persian reader the origin of a
     * screen is the top *right*. Compose does not mirror canvas coordinates the way it mirrors
     * layout, so without this the biggest market on the map lands under the reader's thumb on the
     * wrong side and the map reads back-to-front.
     */
    fun mirroredIn(width: Float): Rect4 = copy(x = width - x - w)

    /**
     * The same rectangle pulled in by [by] on every side, never past collapsing.
     *
     * Used for the label box rather than for the fill: a tile is drawn edge to edge so the map has
     * no gaps in it, and only the text inside is inset.
     */
    fun inset(by: Float): Rect4 {
        val dx = min(by, w / 2f)
        val dy = min(by, h / 2f)
        return Rect4(x + dx, y + dy, w - dx * 2f, h - dy * 2f)
    }
}

/**
 * The squarified treemap of Bruls, Huizing and van Wijk (2000).
 *
 * ### Why not slice-and-dice
 *
 * The obvious layout — cut the container into strips proportional to the weights — is four lines
 * long and unusable here. On a phone-width map of a real market list the weights span three orders
 * of magnitude, and slice-and-dice turns everything below the top few into slivers a couple of
 * pixels wide: no label fits, no tap lands where the reader aimed, and the areas stop being
 * comparable because the eye cannot judge a 2×300 rectangle against a 40×40 one. The squarified
 * algorithm spends a little arithmetic to keep every tile near square, which is what makes the
 * areas readable and the tiles tappable.
 *
 * ### The algorithm, and the one part that is easy to get wrong
 *
 * Weights are sorted descending and laid out in *rows* along the shorter side of whatever rectangle
 * is left. A row grows one tile at a time for as long as adding the next tile improves — or at
 * least does not worsen — the worst aspect ratio in that row; the moment it worsens, the row is
 * closed, laid down as a band, and the algorithm recurses into the strip that remains. Laying along
 * the *shorter* side is the whole trick: it is what bounds the aspect ratio, and swapping it for
 * the longer side quietly reproduces slice-and-dice.
 *
 * The part that bites is float accumulation. Positions are accumulated in `Double` and the last
 * tile of every row, and the thickness of the last row, are *snapped* to the container's own edges
 * rather than computed. Without that snap the rounding error of a hundred divisions shows up as
 * hairline gaps between tiles — on a dark stage that looks like a rendering fault, and it is
 * precisely what `TreemapTest` asserts is absent.
 */
object Treemap {

    /**
     * Lay [weights] out inside a [width] by [height] container.
     *
     * The returned list is in **input order**, not in the descending order the algorithm works in,
     * so a caller can zip it straight back against its own list. That mapping is done here because
     * every caller would otherwise have to do it, and the one that forgot would colour each tile
     * with another market's change.
     *
     * A weight that is not finite or not positive gets a zero-size rectangle and is left out of the
     * layout entirely. It is not given a minimum size: a tile with no weight has no area, and
     * drawing it at some arbitrary floor would be a claim about the market that the data does not
     * make. Callers are expected to filter those out before drawing.
     */
    fun layout(weights: DoubleArray, width: Float, height: Float): List<Rect4> {
        if (weights.isEmpty()) return emptyList()
        val out = MutableList(weights.size) { ZERO }
        if (width <= 0f || height <= 0f) return out

        val order = weights.indices
            .filter { weights[it].isFinite() && weights[it] > 0.0 }
            .sortedByDescending { weights[it] }
        if (order.isEmpty()) return out

        val total = order.sumOf { weights[it] }
        // Areas rather than weights from here on. Normalising once means the row test compares a
        // pixel area against a pixel side, which is what the published worst-ratio formula assumes.
        val scale = width.toDouble() * height.toDouble() / total
        val areas = DoubleArray(order.size) { weights[order[it]] * scale }

        val placed = tile(areas, Rect4(0f, 0f, width, height))
        placed.forEachIndexed { sorted, rect -> out[order[sorted]] = rect }
        return out
    }

    /** The rectangle a weightless entry gets. Shared so identity comparisons work. */
    val ZERO: Rect4 = Rect4(0f, 0f, 0f, 0f)

    /**
     * The worst aspect ratio a row would have if it held areas between [lo] and [hi] summing to
     * [sum], laid along a side of length [side].
     *
     * This is the paper's `worst` function. It needs only the extremes of the row because the
     * widest and the narrowest tile in a band are always the largest and the smallest area in it.
     */
    private fun worst(lo: Double, hi: Double, sum: Double, side: Double): Double {
        if (sum <= 0.0 || side <= 0.0 || lo <= 0.0) return Double.MAX_VALUE
        val sumSquared = sum * sum
        val sideSquared = side * side
        return max(sideSquared * hi / sumSquared, sumSquared / (sideSquared * lo))
    }

    /**
     * The recursion, written as a loop.
     *
     * A loop rather than actual recursion because the depth is the number of rows, which on a
     * two-hundred-market map is around twenty — deep enough that a stack frame per row buys
     * nothing, and shallow enough that the loop reads exactly like the paper.
     */
    private fun tile(areas: DoubleArray, container: Rect4): Array<Rect4> {
        val out = Array(areas.size) { ZERO }
        var rect = container
        var index = 0
        while (index < areas.size) {
            if (rect.w <= 0f || rect.h <= 0f) {
                // Nothing left to divide. The remainder keeps a zero rectangle rather than being
                // stacked at the edge, where it would draw as a line of one-pixel tiles.
                while (index < areas.size) {
                    out[index] = Rect4(rect.x, rect.y, 0f, 0f)
                    index++
                }
                return out
            }
            val side = min(rect.w, rect.h).toDouble()

            var sum = 0.0
            var lo = Double.MAX_VALUE
            var hi = 0.0
            var best = Double.MAX_VALUE
            var end = index
            while (end < areas.size) {
                val area = areas[end]
                val nextLo = min(lo, area)
                val nextHi = max(hi, area)
                val nextSum = sum + area
                val ratio = worst(nextLo, nextHi, nextSum, side)
                // The first tile always joins: a row of one is the only shape available, however
                // bad, and refusing it would loop forever on a single very large weight.
                if (end > index && ratio > best) break
                lo = nextLo
                hi = nextHi
                sum = nextSum
                best = ratio
                end++
            }

            rect = placeRow(areas, index, end, sum, rect, lastRow = end >= areas.size, out = out)
            index = end
        }
        return out
    }

    /**
     * Lay the tiles `[from, to)` as one band across the shorter side of [rect], and answer with
     * what is left over.
     *
     * Both snaps live here. The band's far edge is the container's own edge when this is the last
     * row, and the last tile in every band ends exactly on the container's along-axis edge, so
     * adjacent rectangles share a float value instead of two values that are nearly equal. Sharing
     * the value is what makes "no gap and no overlap" true rather than nearly true.
     */
    private fun placeRow(
        areas: DoubleArray,
        from: Int,
        to: Int,
        sum: Double,
        rect: Rect4,
        lastRow: Boolean,
        out: Array<Rect4>,
    ): Rect4 {
        // The row runs across the width when the width is the shorter side. That is the paper's
        // rule and the reason the tiles stay square; laying along the longer side is slice-and-dice
        // wearing this function's name.
        val horizontal = rect.w <= rect.h
        val alongLength = (if (horizontal) rect.w else rect.h).toDouble()
        val crossLength = (if (horizontal) rect.h else rect.w).toDouble()

        val crossNear = if (horizontal) rect.y else rect.x
        val crossFarEdge = if (horizontal) rect.bottom else rect.right
        val crossFar = if (lastRow) {
            crossFarEdge
        } else {
            (crossNear + min(sum / alongLength, crossLength)).toFloat().coerceIn(crossNear, crossFarEdge)
        }
        val thickness = (crossFar - crossNear).toDouble()

        val alongNear = if (horizontal) rect.x else rect.y
        val alongFarEdge = if (horizontal) rect.right else rect.bottom
        var offset = 0.0
        for (index in from until to) {
            val near = (alongNear + offset).toFloat().coerceIn(alongNear, alongFarEdge)
            offset += if (thickness > 0.0) areas[index] / thickness else 0.0
            val far = if (index == to - 1) {
                alongFarEdge
            } else {
                (alongNear + offset).toFloat().coerceIn(near, alongFarEdge)
            }
            out[index] = if (horizontal) {
                Rect4(near, crossNear, far - near, crossFar - crossNear)
            } else {
                Rect4(crossNear, near, crossFar - crossNear, far - near)
            }
        }

        return if (horizontal) {
            Rect4(rect.x, crossFar, rect.w, rect.bottom - crossFar)
        } else {
            Rect4(crossFar, rect.y, rect.right - crossFar, rect.h)
        }
    }
}

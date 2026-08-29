package com.coinepro.feature.heatmap

import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The treemap, checked as a partition rather than as a picture.
 *
 * Three properties matter and none of them can be eyeballed on a device. The rectangles have to
 * *tile* — cover the container exactly once, with no gap and no overlap — because a gap draws as a
 * hairline fault on a dark stage and an overlap silently hides a market. They have to be roughly
 * square, because that is the entire reason the squarified algorithm is here instead of four lines
 * of slice-and-dice. And equal weights have to produce equal areas, because that is what the map's
 * "same size for everything" mode promises.
 */
class TreemapTest {

    private val width = 360f
    private val height = 280f

    /** A real market's shape: a few giants and a long tail. */
    private val skewed = doubleArrayOf(100.0, 50.0, 25.0, 12.0, 6.0, 3.0, 2.0, 1.0, 1.0, 1.0)

    @Test
    fun `the rectangles cover the container exactly once`() {
        val rects = Treemap.layout(skewed, width, height)

        // Sampled rather than reasoned about: every point of a grid across the container must fall
        // inside exactly one rectangle. A gap shows up as a point in none, an overlap as a point in
        // two, and both are invisible in an area total that happens to add up.
        var missed = 0
        var doubled = 0
        for (column in 0 until 120) {
            for (row in 0 until 90) {
                val x = (column + 0.5f) / 120f * width
                val y = (row + 0.5f) / 90f * height
                val hits = rects.count { it.contains(x, y) }
                if (hits == 0) missed++
                if (hits > 1) doubled++
            }
        }
        assertEquals("points covered by no tile", 0, missed)
        assertEquals("points covered by more than one tile", 0, doubled)
    }

    @Test
    fun `the areas add up to the container and to the weights that asked for them`() {
        val rects = Treemap.layout(skewed, width, height)
        val total = rects.sumOf { it.area.toDouble() }
        assertEquals(width.toDouble() * height, total, EPSILON)

        val weightTotal = skewed.sum()
        rects.forEachIndexed { index, rect ->
            val expected = skewed[index] / weightTotal * width * height
            // A per-tile delta rather than a proportional one: the snap that removes the gaps moves
            // a boundary by less than a pixel, and on the smallest tiles that is a large fraction
            // of a small number while still being invisible.
            assertEquals("tile $index", expected, rect.area.toDouble(), 2.0)
        }
    }

    @Test
    fun `no two rectangles overlap`() {
        val rects = Treemap.layout(skewed, width, height)
        for (a in rects.indices) {
            for (b in a + 1 until rects.size) {
                assertEquals("tiles $a and $b overlap", 0.0, overlapArea(rects[a], rects[b]), EPSILON)
            }
        }
    }

    @Test
    fun `the worst tile is far squarer than slice-and-dice manages`() {
        val squarified = Treemap.layout(skewed, width, height).maxOf { it.aspect }
        val sliced = sliceAndDice(skewed, width, height).maxOf { it.aspect }

        // The baseline is not a straw man — it is the layout this feature would have had if the
        // four-line version had been shipped, and on this weight set its worst tile is a sliver
        // about two pixels wide. Anything that fits a label has to be an order of magnitude better.
        assertTrue("slice-and-dice should be the bad one, was $sliced", sliced > 100f)
        assertTrue("squarified worst aspect was $squarified", squarified < 8f)
        assertTrue("squarified $squarified is not clearly better than $sliced", squarified * 10f < sliced)
    }

    @Test
    fun `equal weights get equal areas, which is what the mono sizing promises`() {
        val rects = Treemap.layout(DoubleArray(12) { 1.0 }, width, height)
        val expected = width.toDouble() * height / 12.0
        rects.forEach { assertEquals(expected, it.area.toDouble(), 1.0) }
        // And they are still squares rather than twelve full-height columns.
        assertTrue(rects.maxOf { it.aspect } < 2f)
    }

    @Test
    fun `the answer comes back in the caller's order, not in the algorithm's`() {
        // Ascending input, so the sorted order the algorithm works in is the reverse of this one.
        // If the mapping back were dropped, every tile would be coloured with another market's
        // figure and the map would look entirely plausible.
        val rects = Treemap.layout(doubleArrayOf(1.0, 2.0, 4.0, 8.0), width, height)
        assertTrue("the smallest weight got the largest tile", rects[0].area < rects[3].area)
        assertEquals(2.0, rects[1].area.toDouble() / rects[0].area, 0.05)
        assertEquals(2.0, rects[3].area.toDouble() / rects[2].area, 0.05)
    }

    @Test
    fun `a weightless entry gets no area rather than a minimum one`() {
        val rects = Treemap.layout(doubleArrayOf(4.0, 0.0, 2.0, Double.NaN, -1.0), width, height)
        assertEquals(0f, rects[1].area, 0f)
        assertEquals(0f, rects[3].area, 0f)
        assertEquals(0f, rects[4].area, 0f)
        // The two that do have weight still tile the whole container between them.
        assertEquals(
            width.toDouble() * height,
            rects[0].area.toDouble() + rects[2].area,
            EPSILON,
        )
    }

    @Test
    fun `a container with no room answers a rectangle per weight and no exception`() {
        val rects = Treemap.layout(skewed, 0f, height)
        assertEquals(skewed.size, rects.size)
        rects.forEach { assertEquals(0f, it.area, 0f) }
        assertTrue(Treemap.layout(DoubleArray(0), width, height).isEmpty())
    }

    @Test
    fun `a single market takes the whole container`() {
        val rects = Treemap.layout(doubleArrayOf(7.0), width, height)
        assertEquals(Rect4(0f, 0f, width, height), rects.single())
    }

    @Test
    fun `mirroring reflects a tile about the centre line and is its own inverse`() {
        val rects = Treemap.layout(skewed, width, height)
        rects.forEach { rect ->
            val there = rect.mirroredIn(width)
            // The far edge becomes the near edge. This is what puts the largest market under a
            // Persian reader's thumb instead of across the screen from it.
            assertEquals(width - rect.right, there.x, TOLERANCE)
            assertEquals(rect.w, there.w, 0f)
            val back = there.mirroredIn(width)
            assertEquals(rect.x, back.x, TOLERANCE)
        }
    }

    /**
     * The layout this feature is not allowed to ship: proportional strips across the full height.
     *
     * Written out here rather than described, so the comparison above is against something real.
     */
    private fun sliceAndDice(weights: DoubleArray, width: Float, height: Float): List<Rect4> {
        val total = weights.sum()
        var x = 0f
        return weights.map { weight ->
            val w = (weight / total * width).toFloat()
            val rect = Rect4(x, 0f, w, height)
            x += w
            rect
        }
    }

    private fun overlapArea(a: Rect4, b: Rect4): Double {
        val w = min(a.right, b.right) - max(a.x, b.x)
        val h = min(a.bottom, b.bottom) - max(a.y, b.y)
        return if (w <= 0f || h <= 0f) 0.0 else w.toDouble() * h
    }

    private companion object {
        /** A square pixel out of a hundred thousand: float rounding, not a gap anybody can see. */
        const val EPSILON = 1.0

        /** A thousandth of a pixel, for a coordinate rather than an area. */
        const val TOLERANCE = 0.001f
    }
}

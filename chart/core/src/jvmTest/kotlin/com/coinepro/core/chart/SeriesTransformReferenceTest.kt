package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The price-driven series types against their definitions, written out independently in
 * `scripts/quality/gen_indicator_reference.py` over the same 120-bar walk the indicators use.
 *
 * Renko, line break, Kagi and point-and-figure each have one textbook construction (StockCharts'
 * and TradingView's agree on all four at their default "traditional" / "close" settings); a
 * transform that drifts from it draws a chart that looks like the type and is not — a brick a
 * hair off the grid, a reversal a bar early. [ChartTransformsTest] checks the behaviours; this
 * checks the numbers.
 */
class SeriesTransformReferenceTest {

    private val fixture = ReferenceFixture.load()
    private val bars = fixture.bars

    @Test
    fun `renko lays the same bricks`() {
        val bricks = ChartTransforms.renko(bars, brick = 1.0)
        fixture.check("renkoOpen", bricks.map { it.o })
        fixture.check("renkoClose", bricks.map { it.c })
        fixture.verify()
    }

    @Test
    fun `three-line break draws the same lines`() {
        val lines = ChartTransforms.lineBreak(bars, count = 3)
        fixture.check("lineBreakOpen", lines.map { it.o })
        fixture.check("lineBreakClose", lines.map { it.c })
        fixture.verify()
    }

    @Test
    fun `kagi turns at the same prices`() {
        fixture.check("kagi", ChartTransforms.kagi(bars, reversal = 1.0).map { it.c })
        fixture.verify()
    }

    @Test
    fun `point and figure keeps its columns on the box grid`() {
        val columns = ChartTransforms.pointAndFigure(bars, box = 0.5, reversal = 3).map { it.c }
        fixture.check("pointAndFigure", columns)
        // Every recorded extreme is a whole number of boxes from the first close — the property a
        // P&F chart is made of, and the one a reversal that started at the raw close would break.
        val origin = bars.first().c
        columns.forEachIndexed { index, price ->
            val boxes = (price - origin) / 0.5
            assertEquals("column $index at $price is off the grid", Math.rint(boxes), boxes, 1e-9)
        }
        fixture.verify()
    }
}

package com.coinepro.core.chart

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The price axis: what it measures, which way up it is, and how it is labelled.
 *
 * Two families of property live here. The first is the drag on the price gutter — the auto-fit
 * range is right for reading a price and wrong for reading a shape, and the properties worth
 * pinning are that stretching is *centred*, so the chart does not slide up the plot as the reader
 * drags, and that it is bounded at both ends.
 *
 * The second is [PriceScaleMode] and the settings around it. Those are mostly arithmetic, and the
 * arithmetic is asserted against numbers worked out by hand rather than against the code's own
 * output — a test that recomputes the formula it is testing passes for ever, including after
 * somebody changes the formula.
 */
class PriceScaleTest {

    private val series = CandleSeries(
        (0 until 120).map { index ->
            val base = 1_000.0 + 40 * kotlin.math.sin(index * 2 * Math.PI / 30)
            Candle(1_700_000_000L + index * 3600, base, base + 5, base - 5, base + 1, 1.0)
        },
    )

    private fun viewport(log: Boolean = false) = ChartViewport(
        series = series,
        barsPerView = 120,
        plotWidth = 900f,
        plotHeight = 600f,
        scaleMode = if (log) PriceScaleMode.LOGARITHMIC else PriceScaleMode.REGULAR,
    )

    private fun midpoint(view: ChartViewport) =
        (view.priceRange.start + view.priceRange.endInclusive) / 2

    /**
     * Four bars whose closes are round numbers, so every percentage below can be read off by eye.
     *
     * The first close is 80, which makes the base something other than a hundred — a base of a
     * hundred would let a formula that forgot to divide by it pass.
     */
    private val plain = CandleSeries(
        listOf(
            Candle(1_700_000_000L, 80.0, 82.0, 78.0, 80.0, 1.0),
            Candle(1_700_003_600L, 80.0, 101.0, 79.0, 100.0, 1.0),
            Candle(1_700_007_200L, 100.0, 101.0, 59.0, 60.0, 1.0),
            Candle(1_700_010_800L, 60.0, 121.0, 59.0, 120.0, 1.0),
        ),
    )

    private fun plainViewport(mode: PriceScaleMode) = ChartViewport(
        series = plain,
        plotWidth = 900f,
        plotHeight = 600f,
        scaleMode = mode,
    )

    @After
    fun restoreLocale() {
        Locale.setDefault(Locale.US)
    }

    // ------------------------------------------------------------------ stretching the axis

    @Test
    fun `zooming out widens the range and zooming in narrows it`() {
        val base = viewport()
        val span = { v: ChartViewport -> v.priceRange.endInclusive - v.priceRange.start }
        assertTrue(span(base.priceZoomedBy(2f)) > span(base))
        assertTrue(span(base.priceZoomedBy(0.5f)) < span(base))
        // Exactly proportional, so a drag of the same length does the same thing wherever it
        // starts from.
        assertEquals(span(base) * 2, span(base.priceZoomedBy(2f)), 1e-6)
    }

    @Test
    fun `the middle of the range does not move`() {
        // The whole feel of the gesture. Widening about the wrong point slides the chart up or
        // down the plot as the reader drags, which reads as the chart running away from them.
        val base = viewport()
        listOf(0.5f, 1.5f, 3f).forEach { factor ->
            assertEquals(midpoint(base), midpoint(base.priceZoomedBy(factor)), 1e-6)
        }
    }

    @Test
    fun `on a log axis the geometric middle does not move`() {
        // Log space has its own centre, and widening about the arithmetic one would slide the
        // chart for exactly the same reason.
        val base = viewport(log = true)
        val geometric = { v: ChartViewport ->
            kotlin.math.sqrt(v.priceRange.start * v.priceRange.endInclusive)
        }
        listOf(0.5f, 2f).forEach { factor ->
            assertEquals(geometric(base), geometric(base.priceZoomedBy(factor)), 1e-6)
        }
    }

    @Test
    fun `it is bounded at both ends`() {
        val base = viewport()
        // A hundred drags in one direction cannot leave the chart a vertical or a horizontal line.
        var out = base
        repeat(100) { out = out.priceZoomedBy(2f) }
        assertEquals(ChartViewport.MAX_PRICE_ZOOM, out.priceZoom, 1e-6f)

        var into = base
        repeat(100) { into = into.priceZoomedBy(0.5f) }
        assertEquals(ChartViewport.MIN_PRICE_ZOOM, into.priceZoom, 1e-6f)
    }

    @Test
    fun `a nonsense factor is ignored rather than propagated`() {
        // A drag on a plot of zero height would produce these. `NaN` reaching `priceRange` makes
        // every price on the chart un-drawable, silently.
        val base = viewport()
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { factor ->
            assertEquals(base.priceZoom, base.priceZoomedBy(factor).priceZoom, 0f)
        }
    }

    @Test
    fun `auto puts it back and touches nothing else`() {
        val stretched = viewport().priceZoomedBy(3f).atOffset(20).copy(barsPerView = 60)
        val reset = stretched.autoPriceScale()
        assertEquals(1f, reset.priceZoom, 0f)
        // The horizontal position and zoom are a different axis and a different gesture.
        assertEquals(20, reset.offset)
        assertEquals(60, reset.barsPerView)
    }

    @Test
    fun `the default is exactly what the chart drew before this existed`() {
        // The guard against the feature having changed every chart in the app by arriving. At
        // zoom 1 the range has to be the old fit-plus-eight-percent, to the bit.
        val view = viewport()
        val low = series.low.min()
        val high = series.high.max()
        val padding = (high - low) * ChartViewport.PRICE_PADDING
        assertEquals(low - padding, view.priceRange.start, 1e-9)
        assertEquals(high + padding, view.priceRange.endInclusive, 1e-9)
    }

    // ------------------------------------------------------------------ the four modes

    @Test
    fun `the boolean the chart shipped with still constructs and still reads`() {
        // Every call site outside this module holds a `logScale: Boolean`, and one of them copies
        // it onto the viewport on every recomposition. If either shape stopped working the app
        // would not compile, so this is here to fail in the module that owns the change instead.
        val log = ChartViewport(series = series, plotWidth = 900f, plotHeight = 600f, logScale = true)
        assertEquals(PriceScaleMode.LOGARITHMIC, log.scaleMode)
        assertTrue(log.logScale)
        assertFalse(viewport().logScale)
        assertEquals(PriceScaleMode.REGULAR, log.copy(logScale = false).scaleMode)
        assertEquals(PriceScaleMode.LOGARITHMIC, viewport().copy(logScale = true).scaleMode)
    }

    @Test
    fun `restoring a stale false does not drag a reader out of percent mode`() {
        // The copy is applied on every recomposition from a saved flag that only ever knew about
        // the log toggle. It has no business resetting a mode it cannot represent.
        val percent = plainViewport(PriceScaleMode.PERCENT)
        assertEquals(PriceScaleMode.PERCENT, percent.copy(logScale = false).scaleMode)
    }

    @Test
    fun `percent is measured from the first visible close`() {
        val view = plainViewport(PriceScaleMode.PERCENT)
        // Base 80: the close of the first bar on screen.
        assertEquals(80.0, view.scaleBase, 1e-12)
        // 100 * (100 − 80) / 80 = 25. 100 * (60 − 80) / 80 = −25. 100 * (120 − 80) / 80 = 50.
        assertEquals(0.0, view.scaleValue(80.0), 1e-12)
        assertEquals(25.0, view.scaleValue(100.0), 1e-12)
        assertEquals(-25.0, view.scaleValue(60.0), 1e-12)
        assertEquals(50.0, view.scaleValue(120.0), 1e-12)
    }

    @Test
    fun `indexed is exactly percent plus a hundred`() {
        val percent = plainViewport(PriceScaleMode.PERCENT)
        val indexed = plainViewport(PriceScaleMode.INDEXED_100)
        assertEquals(100.0, indexed.scaleValue(80.0), 1e-12)
        assertEquals(125.0, indexed.scaleValue(100.0), 1e-12)
        assertEquals(75.0, indexed.scaleValue(60.0), 1e-12)
        // And the general statement, not just the three worked cases: the two modes are the same
        // fact quoted on two scales, and the only thing that may ever separate them is the offset.
        listOf(1.0, 40.0, 79.9, 80.0, 137.25, 4_000.0).forEach { price ->
            assertEquals(
                "price=$price",
                percent.scaleValue(price) + 100.0,
                indexed.scaleValue(price),
                1e-9,
            )
        }
    }

    @Test
    fun `the base follows the reader as they pan`() {
        // Percent mode answers "since the left of *this* screen". A base pinned to the start of
        // the series would make it answer a question nobody asked, and would read 0% off screen.
        val pinned = ChartViewport(
            series = series,
            barsPerView = 20,
            offset = 50,
            plotWidth = 900f,
            plotHeight = 600f,
            scaleMode = PriceScaleMode.PERCENT,
        )
        assertEquals(50, pinned.firstVisible)
        assertEquals(series.close[50], pinned.scaleBase, 1e-12)
        assertTrue(pinned.scaleBase != series.close[0])
        assertEquals(0.0, pinned.scaleValue(pinned.scaleBase), 1e-12)
    }

    @Test
    fun `a negative base still reads up as up`() {
        // Prices are never negative but the indicator panes share this axis, and dividing by a
        // negative base flips the sign: a move from −40 to −20 is a gain and would report −50%.
        assertEquals(50.0, ChartViewport.percentOf(-20.0, -40.0), 1e-12)
        assertEquals(-50.0, ChartViewport.percentOf(-60.0, -40.0), 1e-12)
        assertEquals(150.0, ChartViewport.indexedOf(-20.0, -40.0), 1e-12)
        // And a base of nothing has no percentage of it, rather than an infinity that would take
        // the entire axis with it.
        assertEquals(0.0, ChartViewport.percentOf(17.0, 0.0), 1e-12)
    }

    @Test
    fun `the printed number converts back to a price in every mode`() {
        // A reader can type a level into an alert while the axis is in percent. If this did not
        // invert, the alert would fire at eighteen units instead of at eighteen percent up.
        PriceScaleMode.entries.forEach { mode ->
            val view = plainViewport(mode)
            listOf(60.0, 80.0, 100.0, 137.5).forEach { price ->
                assertEquals(
                    "mode=$mode price=$price",
                    price,
                    view.priceOfScaleValue(view.scaleValue(price)),
                    1e-9,
                )
            }
        }
    }

    @Test
    fun `percent and indexed put every bar in the pixel regular does`() {
        // They are affine rewrites of price, so the layout cannot change — only the labels. This
        // is what lets a drawing survive a mode switch untouched, and it is the assertion that
        // fails the day somebody rebuilds the range in percent space.
        val regular = plainViewport(PriceScaleMode.REGULAR)
        listOf(PriceScaleMode.PERCENT, PriceScaleMode.INDEXED_100).forEach { mode ->
            val other = plainViewport(mode)
            assertEquals(regular.priceRange.start, other.priceRange.start, 1e-12)
            assertEquals(regular.priceRange.endInclusive, other.priceRange.endInclusive, 1e-12)
            listOf(60.0, 80.0, 100.0, 120.0).forEach { price ->
                assertEquals("mode=$mode price=$price", regular.yOf(price), other.yOf(price), 1e-4f)
            }
        }
    }

    @Test
    fun `the log axis still pads multiplicatively and stays positive`() {
        // The bug this file exists to keep dead: eight percent of a 100–10,000 span is 792, so
        // additive padding put the bottom of the axis at −692 — a price with no logarithm, which
        // sent the axis back to the linear fallback and made the toggle do nothing at all.
        val decades = CandleSeries(
            (0 until 200).map { index ->
                val price = 100.0 * Math.pow(100.0, index / 199.0)
                Candle(1_700_000_000L + index * 3600, price, price * 1.01, price * 0.99, price, 1.0)
            },
        )
        val view = ChartViewport(
            series = decades,
            barsPerView = 200,
            plotWidth = 900f,
            plotHeight = 600f,
            scaleMode = PriceScaleMode.LOGARITHMIC,
        )
        assertTrue("the low went non-positive", view.priceRange.start > 0.0)
        assertTrue(view.priceRange.start < decades.low.min())
        assertTrue(view.priceRange.endInclusive > decades.high.max())
        // Multiplicative means the padding is the same *ratio* at both ends.
        val lowRatio = decades.low.min() / view.priceRange.start
        val highRatio = view.priceRange.endInclusive / decades.high.max()
        assertEquals(lowRatio, highRatio, 1e-9)
    }

    @Test
    fun `the toggle walks between regular and logarithmic and nowhere else`() {
        val regular = viewport()
        assertEquals(PriceScaleMode.LOGARITHMIC, regular.toggleLogScale().scaleMode)
        assertEquals(PriceScaleMode.REGULAR, regular.toggleLogScale().toggleLogScale().scaleMode)
        assertEquals(
            PriceScaleMode.LOGARITHMIC,
            regular.withScaleMode(PriceScaleMode.PERCENT).toggleLogScale().scaleMode,
        )
    }

    // ------------------------------------------------------------------ inversion

    @Test
    fun `inverting twice is exactly the identity`() {
        // "Exactly", not "nearly". Inversion is applied once, to the fraction of the plot a price
        // reached, so a round trip has to come back bit for bit — an implementation that flipped
        // the range instead would drift by the padding on every pass.
        val base = viewport()
        assertFalse(base.inverted)
        val twice = base.toggleInverted().toggleInverted()
        assertEquals(base, twice)
        assertFalse(twice.inverted)
        listOf(960.0, 1_000.0, 1_045.5).forEach { price ->
            assertEquals(base.yOf(price), twice.yOf(price), 0f)
        }
    }

    @Test
    fun `inverting reflects the plot and reverses the order`() {
        val base = viewport()
        val flipped = base.toggleInverted()
        listOf(960.0, 1_000.0, 1_045.5).forEach { price ->
            assertEquals(
                "price=$price",
                base.plotHeight.toDouble(),
                (base.yOf(price) + flipped.yOf(price)).toDouble(),
                1e-3,
            )
        }
        // The low is now at the top, which is the entire point.
        assertTrue(flipped.yOf(1_040.0) > flipped.yOf(970.0))
    }

    @Test
    fun `inversion composes with the logarithmic axis`() {
        val log = viewport(log = true)
        val flipped = log.toggleInverted()
        assertEquals(PriceScaleMode.LOGARITHMIC, flipped.scaleMode)
        // Still a reflection, and still logarithmic underneath it: equal ratios are equal
        // distances whichever way up the axis is.
        listOf(965.0, 1_000.0, 1_040.0).forEach { price ->
            assertEquals(
                "price=$price",
                log.plotHeight.toDouble(),
                (log.yOf(price) + flipped.yOf(price)).toDouble(),
                1e-3,
            )
        }
        val first = flipped.yOf(1_020.0) - flipped.yOf(1_000.0)
        val second = flipped.yOf(1_040.4) - flipped.yOf(1_020.0)
        assertEquals(first.toDouble(), second.toDouble(), 0.5)
    }

    @Test
    fun `a finger still lands on the price it touched when the axis is flipped`() {
        // yOf and priceAt are the pairing every drawing tool is built on. Inversion is the kind of
        // change that breaks one of them and not the other, and nothing on screen would say so.
        listOf(PriceScaleMode.REGULAR, PriceScaleMode.LOGARITHMIC).forEach { mode ->
            val view = viewport().withScaleMode(mode).toggleInverted()
            listOf(965.0, 1_000.0, 1_040.0).forEach { price ->
                assertEquals("mode=$mode", 1.0, view.priceAt(view.yOf(price)) / price, 1e-4)
            }
        }
    }

    // ------------------------------------------------------------------ the price-bar lock

    @Test
    fun `the lock keeps the ratio of the two zooms across a horizontal zoom`() {
        // What the lock protects is an angle: a trend line's slope is pixels of price over pixels
        // of time. Hold `priceZoom / barsPerView` and the slope is held with it.
        val locked = ChartViewport(
            series = series,
            barsPerView = 100,
            plotWidth = 900f,
            plotHeight = 600f,
        ).withPriceBarLock(true)
        val ratio = { v: ChartViewport -> v.priceZoom / v.barsPerView }
        val before = ratio(locked)

        val inward = locked.zoomedBy(2f)
        assertEquals(50, inward.barsPerView)
        assertEquals(0.5f, inward.priceZoom, 1e-6f)
        assertEquals(before.toDouble(), ratio(inward).toDouble(), 1e-9)

        val outward = locked.zoomedBy(0.5f)
        assertEquals(200, outward.barsPerView)
        assertEquals(2f, outward.priceZoom, 1e-6f)
        assertEquals(before.toDouble(), ratio(outward).toDouble(), 1e-9)
    }

    @Test
    fun `without the lock a horizontal zoom leaves the price axis alone`() {
        val free = ChartViewport(series = series, barsPerView = 100, plotWidth = 900f, plotHeight = 600f)
        assertFalse(free.priceBarLock)
        val zoomed = free.zoomedBy(2f)
        assertEquals(50, zoomed.barsPerView)
        assertEquals(1f, zoomed.priceZoom, 0f)
    }

    @Test
    fun `the lock follows the bars it actually got, not the ones it asked for`() {
        // At the end of the zoom range the bar count stops moving. A lock that kept multiplying by
        // the requested factor would carry on stretching the price axis on its own, which is the
        // shear it exists to prevent.
        val pinned = ChartViewport(
            series = series,
            barsPerView = ChartViewport.MAX_BARS_PER_VIEW,
            plotWidth = 900f,
            plotHeight = 600f,
        ).withPriceBarLock(true)
        val zoomed = pinned.zoomedBy(0.5f)
        assertEquals(ChartViewport.MAX_BARS_PER_VIEW, zoomed.barsPerView)
        assertEquals(1f, zoomed.priceZoom, 1e-6f)
    }

    // ------------------------------------------------------------------ labels

    @Test
    fun `a price is written in Latin digits under a Persian default locale`() {
        // The app's default locale is Persian and `String.format` follows the default. A price in
        // Persian digits is unreadable next to an order book and a wallet balance that are both in
        // Latin ones, and this has already been the cause of one bug.
        Locale.setDefault(Locale("fa", "IR"))
        val label = viewport().formatPrice(1_234.5)
        assertEquals("1234.50", label)
        val persian = ('۰'..'۹') + ('٠'..'٩')
        label.forEach { character ->
            assertFalse("`$label` carries a non-Latin digit", persian.contains(character))
        }
        assertTrue(label.all { it.isDigit() || it == '.' || it == '-' })
    }

    @Test
    fun `the precision comes from the magnitude of the range`() {
        // One precision for the whole column, taken from the range rather than per label: derived
        // per label, `9.9995` would print above `10.000` and the reader would have to compare them
        // digit by digit.
        assertEquals(2, viewport().effectiveDecimals)

        val cheap = CandleSeries(
            (0 until 40).map { Candle(1_700_000_000L + it * 60, 5.0, 5.2, 4.8, 5.0, 1.0) },
        )
        assertEquals(4, ChartViewport(cheap, plotWidth = 900f, plotHeight = 600f).effectiveDecimals)

        val dust = CandleSeries(
            (0 until 40).map { Candle(1_700_000_000L + it * 60, 4e-4, 4.2e-4, 3.8e-4, 4e-4, 1.0) },
        )
        val dusty = ChartViewport(dust, plotWidth = 900f, plotHeight = 600f)
        assertEquals(8, dusty.effectiveDecimals)
        assertEquals("0.00040000", dusty.formatPrice(4e-4))
    }

    @Test
    fun `an explicit precision overrides the derived one and is bounded`() {
        // The case the derivation cannot see is a venue's own tick size: an instrument quoted to
        // five decimals near 1.0 gets four from the rule, and two adjacent ticks then round to the
        // same label — an axis on which two different prices read identically.
        val pinned = viewport().withDecimals(5)
        assertEquals(5, pinned.effectiveDecimals)
        assertEquals("1234.50000", pinned.formatPrice(1_234.5))
        // Back to derived.
        assertEquals(2, pinned.withDecimals(null).effectiveDecimals)
        // `"%.400f"` is a legal format string and would put four hundred characters into a text
        // measure inside a draw.
        assertEquals(ChartViewport.MAX_DECIMALS, viewport().withDecimals(400).effectiveDecimals)
        assertEquals(0, viewport().withDecimals(-3).effectiveDecimals)
    }

    // ------------------------------------------------------------------ where the gutter goes

    @Test
    fun `the scale side is carried and changes no geometry`() {
        val base = viewport()
        assertEquals(ScaleSide.RIGHT, base.scaleSide)
        ScaleSide.entries.forEach { side ->
            val moved = base.withScaleSide(side)
            assertEquals(side, moved.scaleSide)
            // The renderer decides how much width a gutter costs and hands the plot back through
            // `sized`. Nothing about placing a price inside the plot depends on which edge it is.
            assertEquals(base.yOf(1_000.0), moved.yOf(1_000.0), 0f)
            assertEquals(base.xOf(10), moved.xOf(10), 0f)
            assertEquals(base.priceRange.start, moved.priceRange.start, 0.0)
        }
    }
}

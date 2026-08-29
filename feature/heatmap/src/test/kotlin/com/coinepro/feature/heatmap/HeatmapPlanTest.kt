package com.coinepro.feature.heatmap

import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole map, assembled.
 *
 * This is the level at which the map can be wrong in ways neither the treemap nor the ramp can see
 * on their own: a tile coloured on one market's change and placed on another's rectangle, a group
 * that eats the strip meant for its own name, a right-to-left mirror applied to the picture but not
 * to the hit test. All three of those produce a map that looks entirely plausible until somebody
 * taps it.
 */
class HeatmapPlanTest {

    private val width = 360f
    private val height = 480f

    private fun asset(symbol: String, change: Double?, cap: Double? = null) = HeatmapAsset(
        meta = SymbolClassifier.classify(symbol),
        price = 100.0,
        changePercent = change,
        marketCap = cap,
    )

    private val market = listOf(
        asset("BTCUSDT", 3.0, cap = 1_200.0),
        asset("ETHUSDT", -1.5, cap = 400.0),
        asset("SOLUSDT", 8.0, cap = 90.0),
        asset("XAUUSD", 0.4, cap = 15_000.0),
        asset("EURUSD", -0.2, cap = 8_000.0),
        asset("ADAUSDT", -4.0, cap = 30.0),
    )

    @Test
    fun `the equal sizing gives every tile the same area`() {
        val plan = HeatmapPlanner.plan(
            assets = market,
            options = HeatmapOptions(size = HeatmapSize.MONO),
            width = width,
            height = height,
        )
        val expected = width.toDouble() * height / market.size
        plan.tiles.forEach { assertEquals(it.asset.symbol, expected, it.rect.area.toDouble(), 1.0) }
    }

    @Test
    fun `a weighted map gives the largest market the largest tile`() {
        val plan = HeatmapPlanner.plan(
            assets = market,
            options = HeatmapOptions(size = HeatmapSize.MARKET_CAP),
            width = width,
            height = height,
        )
        val largest = plan.tiles.maxByOrNull { it.rect.area }
        assertEquals("XAUUSD", largest?.asset?.symbol)
        assertEquals(market.size, plan.tiles.size)
        assertEquals(width.toDouble() * height, plan.tiles.sumOf { it.rect.area.toDouble() }, 1.0)
    }

    @Test
    fun `each tile carries its own market's figure and its own market's colour`() {
        val plan = HeatmapPlanner.plan(market, HeatmapOptions(), width, height)
        plan.tiles.forEach { tile ->
            assertEquals(tile.asset.changePercent!!, tile.value!!, 0.0)
            val expected = HeatmapColours.colourFor(tile.value!!, plan.scale, plan.palette, true)
            assertEquals(tile.asset.symbol, expected, tile.argb)
        }
    }

    @Test
    fun `a market with nothing to say draws neutral rather than flat`() {
        // Null and zero are different answers and must not look the same to the code. They do look
        // the same on screen, which is the point: neutral means "no move or nothing known", and the
        // figure on the tile is what separates the two for the reader.
        val plan = HeatmapPlanner.plan(
            assets = listOf(asset("BTCUSDT", null), asset("ETHUSDT", 4.0)),
            options = HeatmapOptions(),
            width = width,
            height = height,
        )
        val quiet = plan.tiles.first { it.asset.symbol == "BTCUSDT" }
        assertNull(quiet.value)
        assertEquals(HeatmapColours.neutralOf(HeatmapPalette.CLASSIC), quiet.argb)
    }

    @Test
    fun `the reader's buy and sell direction reaches the canvas`() {
        val normal = HeatmapPlanner.plan(market, HeatmapOptions(), width, height, risingIsGreen = true)
        val flipped = HeatmapPlanner.plan(market, HeatmapOptions(), width, height, risingIsGreen = false)
        val rising = normal.tiles.first { it.asset.symbol == "SOLUSDT" }
        val flippedRising = flipped.tiles.first { it.asset.symbol == "SOLUSDT" }
        assertTrue("the map ignored the direction preference", rising.argb != flippedRising.argb)
    }

    @Test
    fun `a tap lands on the tile the reader can see, in both writing directions`() {
        val plan = HeatmapPlanner.plan(market, HeatmapOptions(), width, height)
        plan.tiles.forEach { tile ->
            val hit = plan.tileAt(tile.rect.x + tile.rect.w / 2f, tile.rect.y + tile.rect.h / 2f)
            assertEquals(tile.asset.symbol, hit?.asset?.symbol)
        }

        val mirrored = HeatmapPlanner.plan(market, HeatmapOptions(), width, height, mirrored = true)
        mirrored.tiles.forEach { tile ->
            val hit = mirrored.tileAt(tile.rect.x + tile.rect.w / 2f, tile.rect.y + tile.rect.h / 2f)
            assertEquals(tile.asset.symbol, hit?.asset?.symbol)
        }
        // And the mirror actually moved something: the largest tile changes sides.
        val here = plan.tiles.maxByOrNull { it.rect.area }!!
        val there = mirrored.tiles.first { it.asset.symbol == here.asset.symbol }
        assertEquals(width - here.rect.right, there.rect.x, 0.001f)
    }

    @Test
    fun `a tap outside every tile opens nothing`() {
        val plan = HeatmapPlanner.plan(market, HeatmapOptions(), width, height)
        assertNull(plan.tileAt(-4f, 10f))
        assertNull(plan.tileAt(10f, height + 4f))
    }

    @Test
    fun `grouping keeps a class together and reserves a strip for its name`() {
        val plan = HeatmapPlanner.plan(
            assets = market,
            options = HeatmapOptions(grouping = HeatmapGrouping.BY_CLASS),
            width = width,
            height = height,
            groupHeaderHeight = 18f,
        )
        val categories = plan.groups.mapNotNull { it.category }
        assertEquals("one block per class and no more", categories.size, categories.toSet().size)
        assertTrue(SymbolCategory.CRYPTO in categories)

        plan.groups.forEach { group ->
            group.tiles.forEach { tile ->
                assertEquals(group.category, tile.asset.meta.category)
                // No tile may climb into the strip its own class name is written in.
                val header = group.header
                if (header != null) {
                    assertTrue(
                        "${tile.asset.symbol} overlaps its group's header",
                        tile.rect.y >= header.bottom - 0.001f,
                    )
                }
            }
        }
        assertEquals(market.size, plan.tiles.size)
    }

    @Test
    fun `an ungrouped map is one block with no header at all`() {
        val plan = HeatmapPlanner.plan(market, HeatmapOptions(), width, height, groupHeaderHeight = 18f)
        val group = plan.groups.single()
        assertNull(group.header)
        assertNull(group.category)
        assertEquals(market.size, group.tiles.size)
    }

    @Test
    fun `the colour scale is shared across groups, so two classes can be compared`() {
        val grouped = HeatmapPlanner.plan(
            assets = market,
            options = HeatmapOptions(grouping = HeatmapGrouping.BY_CLASS),
            width = width,
            height = height,
            groupHeaderHeight = 18f,
        )
        val flat = HeatmapPlanner.plan(market, HeatmapOptions(), width, height)
        // A per-group scale would make a currency pair up a tenth of a percent look like a coin up
        // eight, because each block would be normalised against its own extremes.
        assertEquals(flat.scale, grouped.scale, 0.0)
        val gold = grouped.tiles.first { it.asset.symbol == "XAUUSD" }
        assertEquals(flat.tiles.first { it.asset.symbol == "XAUUSD" }.argb, gold.argb)
    }

    @Test
    fun `an empty market answers an empty plan rather than dividing by zero`() {
        val plan = HeatmapPlanner.plan(emptyList(), HeatmapOptions(), width, height)
        assertTrue(plan.isEmpty)
        assertTrue(plan.scale > 0.0)
        assertNull(plan.tileAt(10f, 10f))
    }

    @Test
    fun `a canvas with no size answers a plan with no tiles`() {
        val plan = HeatmapPlanner.plan(market, HeatmapOptions(), 0f, height)
        assertTrue(plan.isEmpty)
        assertEquals(HeatmapPalette.CLASSIC, plan.palette)
    }
}

package com.coinepro.feature.heatmap

import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    private fun asset(symbol: String, change: Double?, volume: Double? = null) = HeatmapAsset(
        meta = SymbolClassifier.classify(symbol),
        price = 100.0,
        changePercent = change,
        volume = volume,
    )

    private val market = listOf(
        asset("BTCUSDT", 3.0, volume = 1_200.0),
        asset("ETHUSDT", -1.5, volume = 400.0),
        asset("SOLUSDT", 8.0, volume = 90.0),
        asset("XAUUSD", 0.4, volume = 15_000.0),
        asset("EURUSD", -0.2, volume = 8_000.0),
        asset("ADAUSDT", -4.0, volume = 30.0),
    )

    private val ungrouped = HeatmapOptions(grouping = HeatmapGrouping.NONE)

    @Test
    fun `the equal sizing gives every tile the same area`() {
        val plan = HeatmapPlanner.plan(
            assets = market,
            options = ungrouped.copy(size = HeatmapSize.MONO),
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
            options = ungrouped.copy(size = HeatmapSize.VOLUME),
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
        val plan = HeatmapPlanner.plan(market, ungrouped, width, height)
        plan.tiles.forEach { tile ->
            val value = tile.value!!
            assertEquals(tile.asset.changePercent!!, value, 0.0)
            val expected = HeatmapColours.colourFor(value, plan.scale, plan.palette, true)
            assertEquals(tile.asset.symbol, expected, tile.argb)
        }
    }

    @Test
    fun `a market with nothing to say draws as unknown and never as flat`() {
        // The whole feature turns on this one assertion. Before it, a market nobody had a figure
        // for took the ramp's neutral — the same colour as a market that genuinely did not move —
        // so a feed carrying no changes at all rendered as a market where nothing had happened.
        val plan = HeatmapPlanner.plan(
            assets = listOf(asset("BTCUSDT", null), asset("ETHUSDT", 4.0)),
            options = ungrouped,
            width = width,
            height = height,
        )
        val quiet = plan.tiles.first { it.asset.symbol == "BTCUSDT" }
        assertNull(quiet.value)
        assertFalse("an unknown tile must not report itself as known", quiet.known)
        assertEquals(HeatmapColours.unknown, quiet.argb)
        HeatmapPalette.entries.forEach { palette ->
            assertNotEquals(
                "$palette must not draw unknown and no-change the same",
                HeatmapColours.neutralOf(palette),
                HeatmapColours.unknown,
            )
        }
    }

    @Test
    fun `the plan counts how many of its own tiles carry a figure`() {
        val plan = HeatmapPlanner.plan(
            assets = listOf(asset("BTCUSDT", null), asset("ETHUSDT", 4.0), asset("SOLUSDT", null)),
            options = ungrouped,
            width = width,
            height = height,
        )
        assertEquals(1, plan.known)
        assertEquals(3, plan.size)
    }

    @Test
    fun `the reader's buy and sell direction reaches the canvas`() {
        val normal = HeatmapPlanner.plan(market, ungrouped, width, height, risingIsGreen = true)
        val flipped = HeatmapPlanner.plan(market, ungrouped, width, height, risingIsGreen = false)
        val rising = normal.tiles.first { it.asset.symbol == "SOLUSDT" }
        val flippedRising = flipped.tiles.first { it.asset.symbol == "SOLUSDT" }
        assertTrue("the map ignored the direction preference", rising.argb != flippedRising.argb)
    }

    @Test
    fun `a tap lands on the tile the reader can see, in both writing directions`() {
        val plan = HeatmapPlanner.plan(market, ungrouped, width, height)
        plan.tiles.forEach { tile ->
            val hit = plan.tileAt(tile.rect.x + tile.rect.w / 2f, tile.rect.y + tile.rect.h / 2f)
            assertEquals(tile.asset.symbol, hit?.asset?.symbol)
        }

        val mirrored = HeatmapPlanner.plan(market, ungrouped, width, height, mirrored = true)
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
        val plan = HeatmapPlanner.plan(market, ungrouped, width, height)
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
        val buckets = plan.groups.mapNotNull { it.bucket }
        assertEquals("one block per class and no more", buckets.size, buckets.toSet().size)
        assertTrue(HeatmapBucket.Class(SymbolCategory.CRYPTO) in buckets)

        plan.groups.forEach { group ->
            group.tiles.forEach { tile ->
                assertEquals(group.bucket, HeatmapBucket.Class(tile.asset.meta.category))
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
    fun `grouping by quote currency cuts the same markets a different way`() {
        val plan = HeatmapPlanner.plan(
            assets = market,
            options = HeatmapOptions(grouping = HeatmapGrouping.BY_QUOTE),
            width = width,
            height = height,
            groupHeaderHeight = 18f,
        )
        val buckets = plan.groups.mapNotNull { it.bucket }.toSet()
        assertTrue(HeatmapBucket.Quote("USDT") in buckets)
        assertTrue(HeatmapBucket.Quote("USD") in buckets)
        // Gold and the euro pair are different classes and the same quote currency, which is the
        // whole reason this cut exists.
        val dollars = plan.groups.first { it.bucket == HeatmapBucket.Quote("USD") }
        assertEquals(
            setOf("XAUUSD", "EURUSD"),
            dollars.tiles.map { it.asset.symbol }.toSet(),
        )
    }

    @Test
    fun `a name strip is tappable past its drawn edges, and the strip wins over the tiles`() {
        val plan = HeatmapPlanner.plan(
            assets = market,
            options = HeatmapOptions(grouping = HeatmapGrouping.BY_CLASS),
            width = width,
            height = height,
            groupHeaderHeight = 18f,
        )
        val group = plan.groups.first { it.header != null }
        val header = group.header!!
        val x = header.x + header.w / 2f
        assertEquals(group.bucket, plan.bucketAt(x, header.y + header.h / 2f))
        // Four points below the strip is inside a tile and outside the strip. With slop it is the
        // strip's, because a focus taken by mistake is one tap to undo and a chart opened by
        // mistake is a navigation.
        assertNull(plan.bucketAt(x, header.bottom + 4f))
        assertEquals(group.bucket, plan.bucketAt(x, header.bottom + 4f, slop = 8f))
    }

    @Test
    fun `an ungrouped map is one block with no header at all`() {
        val plan = HeatmapPlanner.plan(market, ungrouped, width, height, groupHeaderHeight = 18f)
        val group = plan.groups.single()
        assertNull(group.header)
        assertNull(group.bucket)
        assertEquals(market.size, group.tiles.size)
    }

    @Test
    fun `a focused map is that block alone, with no strip repeating its name`() {
        val plan = HeatmapPlanner.plan(
            assets = market,
            options = HeatmapOptions(grouping = HeatmapGrouping.BY_CLASS),
            width = width,
            height = height,
            groupHeaderHeight = 18f,
            focus = HeatmapBucket.Class(SymbolCategory.CRYPTO),
        )
        val group = plan.groups.single()
        assertNull(group.header)
        assertTrue(group.tiles.all { it.asset.meta.category == SymbolCategory.CRYPTO })
        // The whole canvas, not the share the class had on the map it was focused from.
        assertEquals(width.toDouble() * height, plan.tiles.sumOf { it.rect.area.toDouble() }, 1.0)
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
        val flat = HeatmapPlanner.plan(market, ungrouped, width, height)
        // A per-group scale would make a currency pair up a tenth of a percent look like a coin up
        // eight, because each block would be normalised against its own extremes.
        assertEquals(flat.scale, grouped.scale, 0.0)
        val gold = grouped.tiles.first { it.asset.symbol == "XAUUSD" }
        assertEquals(flat.tiles.first { it.asset.symbol == "XAUUSD" }.argb, gold.argb)
    }

    @Test
    fun `an empty market answers an empty plan rather than dividing by zero`() {
        val plan = HeatmapPlanner.plan(emptyList(), ungrouped, width, height)
        assertTrue(plan.isEmpty)
        assertTrue(plan.scale > 0.0)
        assertNull(plan.tileAt(10f, 10f))
    }

    @Test
    fun `a canvas with no size answers a plan with no tiles`() {
        val plan = HeatmapPlanner.plan(market, ungrouped, 0f, height)
        assertTrue(plan.isEmpty)
        assertEquals(HeatmapPalette.CLASSIC, plan.palette)
    }
}

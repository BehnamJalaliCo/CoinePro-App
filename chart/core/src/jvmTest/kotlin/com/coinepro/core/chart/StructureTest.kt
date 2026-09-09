package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seven structure studies.
 *
 * The numeric halves — the pivot ladder and the zigzag — are checked against the JavaScript by
 * `IndicatorParityTest`, the same way every other calculation in this module is. What is checked
 * *here* is the part a fixture cannot express: that a swing high really is higher than its
 * neighbours, that a cluster of one is not a level, that a zone is a band and not a line. Those are
 * properties rather than values, and pinning them as values would pin an arrangement instead of a
 * calculation.
 */
class StructureTest {

    /** A deliberate shape: up, sharp reversal, up again — so there are turns to find. */
    private val series = CandleSeries(
        (0 until 120).map { index ->
            val leg = when {
                index < 40 -> index * 0.5
                index < 80 -> 20.0 - (index - 40) * 0.6
                else -> -4.0 + (index - 80) * 0.55
            }
            val base = 100.0 + leg
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = base,
                h = base + 1.0,
                l = base - 1.0,
                c = base + 0.3,
                v = 1_000.0,
            )
        },
    )

    // ── pivots ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a classic pivot is the previous bar's typical price`() {
        val levels = Structure.pivotLevels(110.0, 90.0, 100.0, null, Structure.PivotType.CLASSIC)
        assertEquals(100.0, levels.pivot, 1e-9)
        assertEquals(110.0, levels.r1!!, 1e-9) // 2P − low
        assertEquals(90.0, levels.s1!!, 1e-9) // 2P − high
        assertEquals(120.0, levels.r2!!, 1e-9) // P + range
    }

    @Test
    fun `DeMark defines only one level each side`() {
        // Not an omission to be filled in. DeMark's construction produces a pivot and one level
        // above and below; inventing an R2 for symmetry with the other four would be inventing a
        // price, and somebody would trade it.
        val levels = Structure.pivotLevels(110.0, 90.0, 100.0, 105.0, Structure.PivotType.DEMARK)
        assertEquals(null, levels.r2)
        assertEquals(null, levels.r3)
        assertEquals(null, levels.s2)
        assertEquals(null, levels.s3)
        assertTrue(levels.r1 != null && levels.s1 != null)
    }

    @Test
    fun `DeMark branches on where the bar closed`() {
        val down = Structure.pivotLevels(110.0, 90.0, 95.0, 100.0, Structure.PivotType.DEMARK)
        val up = Structure.pivotLevels(110.0, 90.0, 105.0, 100.0, Structure.PivotType.DEMARK)
        val flat = Structure.pivotLevels(110.0, 90.0, 100.0, 100.0, Structure.PivotType.DEMARK)
        assertTrue("a down close must not give the same pivot as an up close", down.pivot != up.pivot)
        assertTrue(flat.pivot != down.pivot && flat.pivot != up.pivot)
    }

    @Test
    fun `the five conventions genuinely disagree`() {
        // The reason all five are offered. If two of them agreed, one of them would be redundant.
        val pivots = Structure.PivotType.entries.map {
            Structure.pivotLevels(110.0, 90.0, 104.0, 100.0, it).r1
        }
        assertEquals(pivots.size, pivots.toSet().size)
    }

    /**
     * The bars where the fixture crosses a UTC midnight.
     *
     * Computed rather than assumed. The series starts mid-afternoon, so the first day is two bars
     * long — writing "24" for an hourly series looks obviously right and is wrong here, which is
     * exactly the kind of assumption a test should not smuggle in.
     */
    private val dayBoundaries: List<Int> =
        (1 until series.size).filter { series.time[it] / 86_400 != series.time[it - 1] / 86_400 }

    @Test
    fun `there is no pivot until a session has closed`() {
        // The first day of a chart has no completed session behind it, and a pivot invented for it
        // would be a level nobody could have traded.
        val pivot = Structure.pivots(series)[3]
        val firstBoundary = dayBoundaries.first()
        for (index in 0 until firstBoundary) assertEquals("bar $index", null, pivot.values[index])
        assertTrue("no pivot once the first day closed", pivot.values[firstBoundary] != null)
    }

    @Test
    fun `a pivot is flat across its session and steps at the boundary`() {
        // The property that makes it a level. Recomputing per bar — which the web original does —
        // gives a new pivot every hour and a line that describes nothing.
        val pivot = Structure.pivots(series, session = Structure.PivotSession.DAILY)[3]
        val (first, second, third) = Triple(dayBoundaries[0], dayBoundaries[1], dayBoundaries[2])
        val firstDay = (first until second).mapNotNull { pivot.values[it] }.toSet()
        val secondDay = (second until third).mapNotNull { pivot.values[it] }.toSet()
        assertEquals("the pivot moved inside a session", 1, firstDay.size)
        assertEquals(1, secondDay.size)
        assertTrue("the pivot did not step at the session boundary", firstDay != secondDay)
    }

    @Test
    fun `the per-bar mode is still there, because the parity fixture checks the formula with it`() {
        val perBar = Structure.pivots(series, session = Structure.PivotSession.BAR)[3]
        assertEquals(null, perBar.values[0])
        assertTrue(perBar.values[1] != null)
    }

    @Test
    fun `a weekly pivot changes less often than a daily one`() {
        fun steps(session: Structure.PivotSession): Int {
            val line = Structure.pivots(series, session = session)[3]
            return (1 until series.size).count { line.values[it] != line.values[it - 1] }
        }
        assertTrue(steps(Structure.PivotSession.WEEKLY) < steps(Structure.PivotSession.DAILY))
    }

    @Test
    fun `only the pivot itself is solid`() {
        val lines = Structure.pivots(series)
        assertEquals(listOf("P"), lines.filterNot { it.dashed }.map { it.label })
    }

    // ── swings and fractals ───────────────────────────────────────────────────────────

    @Test
    fun `a swing high really is higher than its neighbours`() {
        val markers = Structure.swings(series, left = 5, right = 5)
        assertTrue("nothing found on a series with an obvious peak", markers.isNotEmpty())
        val byTime = series.time.withIndex().associate { (index, time) -> time to index }
        for (marker in markers.filter { it.glyph == MarkerGlyph.ARROW_DOWN }) {
            val index = byTime.getValue(marker.time)
            for (step in 1..5) {
                assertTrue(
                    "bar $index is marked a swing high but bar ${index - step} is not lower",
                    series.high[index - step] < series.high[index],
                )
                assertTrue(series.high[index + step] < series.high[index])
            }
        }
    }

    @Test
    fun `swings cannot be found in the last bars, because they are not knowable yet`() {
        // A swing high is confirmed by the bars after it. Marking one on the live edge would be
        // drawing the future, and it is worth a test because it looks like an off-by-one bug.
        val markers = Structure.swings(series, left = 5, right = 5)
        val lastMarked = markers.maxOf { it.time }
        assertTrue(lastMarked <= series.time[series.size - 6])
    }

    @Test
    fun `a fractal is the same test at two bars each side`() {
        val fractals = Structure.fractals(series, span = 2)
        val swings = Structure.swings(series, left = 2, right = 2)
        assertEquals(swings.map { it.time }.toSet(), fractals.map { it.time }.toSet())
    }

    // ── zigzag ────────────────────────────────────────────────────────────────────────

    @Test
    fun `the zigzag alternates peaks and troughs`() {
        // A zigzag with two peaks in a row is not a zigzag; it means a turn was missed.
        val swings = Structure.zigzagSwings(series, deviationPercent = 5.0)
        assertTrue("no turns found on a series with two", swings.size >= 3)
        for (index in 1 until swings.size) {
            assertTrue(
                "two ${if (swings[index].isPeak) "peaks" else "troughs"} in a row at $index",
                swings[index].isPeak != swings[index - 1].isPeak,
            )
        }
    }

    @Test
    fun `a larger deviation finds fewer turns`() {
        val fine = Structure.zigzagSwings(series, deviationPercent = 2.0)
        val coarse = Structure.zigzagSwings(series, deviationPercent = 15.0)
        assertTrue("a 15% filter found more turns than a 2% one", coarse.size <= fine.size)
    }

    @Test
    fun `the zigzag line has a value only at its turns`() {
        val (line, markers) = Structure.zigzag(series, deviationPercent = 5.0)
        val present = (0 until series.size).count { line.values.isPresent(it) }
        assertEquals(markers.size, present)
        assertTrue("the line must join across its gaps or it is a row of dots", line.connectNulls)
    }

    // ── levels ────────────────────────────────────────────────────────────────────────

    @Test
    fun `auto-Fibonacci spans the last leg and nothing wider`() {
        val levels = Structure.autoFibonacci(series, deviationPercent = 5.0)
        assertEquals(7, levels.size)
        val swings = Structure.zigzagSwings(series, deviationPercent = 5.0)
        val low = minOf(swings[swings.size - 1].price, swings[swings.size - 2].price)
        val high = maxOf(swings[swings.size - 1].price, swings[swings.size - 2].price)
        for (level in levels) {
            assertTrue(
                "level ${level.price} is outside the leg $low..$high",
                level.price >= low - 1e-9 && level.price <= high + 1e-9,
            )
        }
    }

    @Test
    fun `auto-Fibonacci needs two turns and returns nothing on a flat series`() {
        val flat = CandleSeries((0 until 30).map { Candle(it.toLong(), 100.0, 100.0, 100.0, 100.0) })
        assertTrue(Structure.autoFibonacci(flat, 5.0).isEmpty())
    }

    @Test
    fun `a single touch is not a support level`() {
        // The count is the claim. A level touched once is a bar, and drawing it as support says
        // something about the market that nothing observed supports.
        val levels = Structure.supportResistance(series, lookback = 5, tolerancePercent = 0.5)
        for (level in levels) {
            val touches = level.label!!.substringAfter('×').toInt()
            assertTrue("a cluster of $touches was kept", touches >= 2)
        }
    }

    @Test
    fun `a stronger level is drawn more strongly`() {
        val levels = Structure.supportResistance(series, lookback = 3, tolerancePercent = 2.0)
        val strong = levels.filter { it.label!!.substringAfter('×').toInt() >= 4 }
        val weak = levels.filter { it.label!!.substringAfter('×').toInt() == 2 }
        // Not a colour-value assertion — that would pin the palette. The claim is only that the two
        // are distinguishable, which is what the reader needs.
        for (a in strong) for (b in weak) assertTrue(a.colour != b.colour)
    }

    /**
     * The gentle series has no impulse in it — every bar moves 0.3 against an ATR of about 2 — so
     * it can never produce a zone. This one has three, at bars 40, 70 and 100.
     */
    private val withImpulses = CandleSeries(
        (0 until 120).map { index ->
            val base = 100.0 + index * 0.1
            val violent = index in listOf(40, 70, 100)
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = base,
                h = base + if (violent) 12.0 else 1.0,
                l = base - 1.0,
                c = base + if (violent) 10.0 else 0.3,
                v = 1_000.0,
            )
        },
    )

    @Test
    fun `a supply zone is a band, not a line`() {
        val zones = Structure.supplyDemand(withImpulses, impulse = 2.0, atrLength = 14, maxZones = 5)
        assertTrue("no zones on a series with a sharp reversal", zones.isNotEmpty())
        assertEquals("zones must come in pairs — a high and a low", 0, zones.size % 2)
        for (index in zones.indices step 2) {
            assertTrue("the band's top is not above its bottom", zones[index].price >= zones[index + 1].price)
            assertEquals("both edges must share a colour", zones[index].colour, zones[index + 1].colour)
            assertEquals("only one edge is labelled", null, zones[index + 1].label)
        }
    }

    @Test
    fun `only the most recent zones survive`() {
        // A chart carrying every zone since 2024 is a chart of stripes.
        val zones = Structure.supplyDemand(withImpulses, impulse = 2.0, atrLength = 14, maxZones = 3)
        assertTrue("kept ${zones.size} edges for a cap of 3 zones", zones.size <= 6)
    }

    @Test
    fun `a series too short for a study returns nothing rather than throwing`() {
        val tiny = CandleSeries(listOf(Candle(1, 100.0, 101.0, 99.0, 100.5)))
        assertTrue(Structure.pivots(tiny).isEmpty())
        assertTrue(Structure.swings(tiny).isEmpty())
        assertTrue(Structure.fractals(tiny).isEmpty())
        assertTrue(Structure.supplyDemand(tiny).isEmpty())
        assertTrue(Structure.supportResistance(tiny).isEmpty())
        // Zero, not one. A single bar has no turn in it — the unconfirmed final swing that the
        // study normally appends needs a direction, and one bar has not established one.
        assertTrue(Structure.zigzagSwings(tiny, 5.0).isEmpty())
    }
}

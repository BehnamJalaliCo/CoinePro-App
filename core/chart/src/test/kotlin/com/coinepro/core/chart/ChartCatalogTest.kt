package com.coinepro.core.chart

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every chart type and indicator the app offers must have help behind its «؟».
 *
 * The two lists are written by hand in two different modules — the options here, the content in
 * `core:help` — and nothing connects them but a string. A typo produces a «؟» that opens nothing,
 * or worse, opens the wrong tool's explanation. Both were real: `sma` and `williamsr` were wrong on
 * the first pass, because the web terminal calls them `ma` and `willr`.
 *
 * The check reads the shipped JSON directly rather than parsing it through `core:help`, so this
 * module does not have to depend on that one just to be tested.
 */
class ChartCatalogTest {

    private val helpIds: Set<String> = run {
        val file = File("../help/src/main/assets/help/content.json")
        assertTrue(
            "The help catalogue is not where this test expects it: ${file.absolutePath}",
            file.exists(),
        )
        // Top-level keys only. A real parser would be better and would also mean a dependency on
        // Gson here for one line; the ids are the object's own keys and this reads them exactly.
        Regex("^  \"([^\"]+)\":", RegexOption.MULTILINE)
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
            .ifEmpty {
                Regex("\"([A-Za-z0-9_+-]+)\"\\s*:\\s*\\{\\s*\"title\"")
                    .findAll(file.readText())
                    .map { it.groupValues[1] }
                    .toSet()
            }
    }

    @Test
    fun `the help catalogue was actually read`() {
        assertTrue("no ids parsed out of the catalogue", helpIds.size > 150)
    }

    @Test
    fun `every chart type points at a real help entry`() {
        for (option in ChartCatalog.CHART_TYPES) {
            assertTrue(
                "chart type ${option.type} points at '${option.helpId}', which has no help entry",
                option.helpId in helpIds,
            )
        }
    }

    @Test
    fun `every indicator that claims a help entry has one`() {
        for (option in ChartCatalog.INDICATORS) {
            val helpId = option.helpId ?: continue
            assertTrue(
                "indicator ${option.id} points at '$helpId', which has no help entry",
                helpId in helpIds,
            )
        }
    }

    @Test
    fun `exactly these indicators have no help, and the list may only shrink`() {
        // Pinned rather than merely tolerated: a nullable helpId makes it very easy to add one more
        // without noticing, and "no help" is a gap to close, not a default. Nine indicators used to
        // sit here because the web terminal's help predates them; they now have entries written for
        // this app. It shrank again when the six below were written for this app too, leaving only
        // the seven structure studies. It may only ever shrink.
        val withoutHelp = ChartCatalog.INDICATORS.filter { it.helpId == null }.map { it.id }
        assertEquals(
            listOf(
                // Six of the third pack. The shipped catalogue has an entry for every other one of
                // the twenty-seven — `psar`, `alligator`, `vwma`, `massIndex` and the rest — and
                // none for these, so they point at nothing rather than at the nearest neighbour.
                // The seven structure studies, and only these. Their help ids in the web terminal
                // are attached to the drawing tools of the same name — `fib`, `hline` — which
                // explain the tool a reader places by hand, not the study that places it for them.
                // Pointing at those would be worse than pointing at nothing, so these stay null
                // until somebody writes seven entries about the studies themselves.
                //
                // `volstop`, `ppo`, `pvo`, `woodiescci`, `correlation` and `chopzone` were here
                // and are not any more: entries were written for this app rather than borrowed. The
                // tempting wrong move on `woodiescci` was to point it at `cci` — Woodie's is a
                // method read off two lines, and the CCI entry explains neither the turbo line nor
                // one of its patterns.
                "pivots", "swings", "fractals", "zigzag", "autofib", "sr", "supplydemand",
            ),
            withoutHelp,
        )
    }

    @Test
    fun `every structure study actually draws something`() {
        // The structure equivalent of the price-pane check: a study in the list that produces no
        // lines, no levels and no markers is a switch that does nothing when tapped.
        //
        // The series oscillates rather than trending, and that is the whole reason it works. A clean
        // ramp has no support in it at all — price never revisits a level — so support/resistance
        // correctly finds nothing on one, and a fixture that trends tests the study by accident
        // rather than on purpose. This one tops out near the same price four times.
        val series = CandleSeries(
            (0 until 200).map { index ->
                val base = 110.0 + 10 * kotlin.math.sin(index * 2 * Math.PI / 40)
                val violent = index == 100
                Candle(
                    t = 1_700_000_000L + index * 3600,
                    o = base,
                    h = base + if (violent) 12.0 else 0.8,
                    l = base - 0.8,
                    c = base + if (violent) 10.0 else 0.2,
                    v = 500.0,
                )
            },
        )
        for (option in ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.STRUCTURE }) {
            assertTrue("${option.id} draws nothing", !ChartCatalog.structureFor(option, series).isEmpty)
        }
    }

    @Test
    fun `a structure study draws nothing through the ordinary overlay path`() {
        // And the reverse: overlayFor must not quietly return lines for one, or a study would draw
        // twice — once as an overlay and once as structure.
        val series = CandleSeries((0 until 60).map { Candle(it.toLong(), 100.0, 101.0, 99.0, 100.5) })
        for (option in ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.STRUCTURE }) {
            assertTrue(ChartCatalog.overlayFor(option, series).isEmpty())
        }
        for (option in ChartCatalog.INDICATORS.filterNot { it.pane == IndicatorPane.STRUCTURE }) {
            assertTrue(ChartCatalog.structureFor(option, series).isEmpty)
        }
    }

    @Test
    fun `every chart type the engine can draw is offered`() {
        // Otherwise a type exists in the engine, is tested, and no reader can ever reach it.
        assertEquals(ChartType.entries.toSet(), ChartCatalog.CHART_TYPES.map { it.type }.toSet())
    }

    @Test
    fun `no option is listed twice`() {
        assertEquals(
            ChartCatalog.INDICATORS.size,
            ChartCatalog.INDICATORS.map { it.id }.toSet().size,
        )
        assertEquals(
            ChartCatalog.CHART_TYPES.size,
            ChartCatalog.CHART_TYPES.map { it.type }.toSet().size,
        )
    }

    @Test
    fun `every price-pane indicator actually produces lines`() {
        // A price-pane indicator with no overlay is a switch that does nothing when tapped.
        val series = CandleSeries(
            (0 until 120).map { index ->
                val base = 100.0 + index * 0.2
                Candle(1_700_000_000L + index * 3600, base, base + 1, base - 1, base + 0.4, 500.0)
            },
        )
        for (option in ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.PRICE }) {
            val lines = ChartCatalog.overlayFor(option, series)
            assertTrue("${option.id} draws nothing on the price pane", lines.isNotEmpty())
            assertTrue(
                "${option.id} produced a line with no values at all",
                lines.all { line -> (0 until series.size).any { line.values.isPresent(it) } },
            )
        }
    }

    @Test
    fun `a separate-pane indicator draws nothing over the price`() {
        // Plotting RSI's 0-100 against a gold price of 2,600 collapses the price axis to a line.
        val series = CandleSeries(
            (0 until 60).map { Candle(it.toLong(), 100.0, 101.0, 99.0, 100.5, 10.0) },
        )
        for (option in ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.SEPARATE }) {
            assertTrue(
                "${option.id} must not draw on the price pane",
                ChartCatalog.overlayFor(option, series).isEmpty(),
            )
        }
    }

    @Test
    fun `every separate-pane indicator produces a pane`() {
        // The gap this closed: for a long time the option was in the catalogue, the arithmetic was
        // in Indicators, and nothing joined them — switching on an RSI drew nothing at all. An
        // entry here with no pane is that bug coming back.
        val series = wavySeries()
        // `correlation` is excluded and is the only exclusion: it measures this symbol against a
        // second one, and with no second symbol loaded drawing *any* line would be inventing a
        // relationship. It has its own test below, which pins both halves of that.
        val drawable = ChartCatalog.INDICATORS
            .filter { it.pane == IndicatorPane.SEPARATE && it.id != "correlation" }
        for (option in drawable) {
            val pane = ChartCatalog.paneFor(option, series)
            assertNotNull("${option.id} must produce a pane", pane)
            assertTrue(
                "${option.id} must draw at least one line or histogram",
                pane!!.lines.isNotEmpty() || pane.histogram != null,
            )
        }
    }

    @Test
    fun `paneFor refuses an indicator that belongs on the price`() {
        val series = wavySeries()
        for (option in ChartCatalog.INDICATORS.filter { it.pane != IndicatorPane.SEPARATE }) {
            assertNull(ChartCatalog.paneFor(option, series))
        }
    }

    @Test
    fun `an empty series produces no pane rather than an exception`() {
        for (option in ChartCatalog.INDICATORS) {
            assertNull(ChartCatalog.paneFor(option, CandleSeries.EMPTY))
        }
    }

    /**
     * A series with turns and a volume that varies.
     *
     * A flat one would let a broken indicator pass: every oscillator pins to one end of its range,
     * and every volume study returns an empty line because the feed looks like it reported none.
     */
    private fun wavySeries(count: Int = 300): CandleSeries = CandleSeries(
        (0 until count).map { index ->
            val close = 100.0 + kotlin.math.sin(index / 11.0) * 5 + index * 0.05
            Candle(
                t = 1_700_000_000L + index * 3_600L,
                o = close - 0.3,
                h = close + 0.9,
                l = close - 1.0,
                c = close,
                v = 800.0 + (index % 23) * 30,
            )
        },
    )

    @Test
    fun `an empty series produces no overlay rather than an exception`() {
        for (option in ChartCatalog.INDICATORS) {
            assertTrue(ChartCatalog.overlayFor(option, CandleSeries.EMPTY).isEmpty())
        }
    }

    // ── The third pack ───────────────────────────────────────────────────────────────────

    /**
     * The twenty-seven ids the third pack added, written out rather than derived.
     *
     * Deriving them — "everything after `pvt`" — would make this list follow the catalogue instead
     * of checking it, and a row deleted by accident would take its own test with it.
     */
    private val thirdPack = listOf(
        "sar", "alligator", "vwma", "tema", "dema", "chandekroll", "volstop", "volumeprofile_ind",
        "stochrsi", "tsi", "aroon", "dmi", "ppo", "dpo", "kst", "cmo", "coppock", "rvi",
        "woodiescci", "massindex", "ao", "correlation",
        "mfi", "cmf", "pvo", "netvolume",
        "chopzone",
    )

    @Test
    fun `the catalogue is the size the help and the picker were written against`() {
        assertEquals(83, ChartCatalog.INDICATORS.size)
        assertEquals(26, ChartCatalog.INDICATORS.count { it.pane == IndicatorPane.PRICE })
        assertEquals(49, ChartCatalog.INDICATORS.count { it.pane == IndicatorPane.SEPARATE })
        assertEquals(8, ChartCatalog.INDICATORS.count { it.pane == IndicatorPane.STRUCTURE })
        assertEquals(27, thirdPack.size)
        for (id in thirdPack) {
            assertTrue("$id was never registered", ChartCatalog.INDICATORS.any { it.id == id })
        }
    }

    @Test
    fun `every indicator in the third pack draws a value at the right-hand edge`() {
        // Not merely "produces something": an indicator whose only values are in its warm-up draws
        // a stub at the far left of a five-hundred-bar chart and nothing where the reader is
        // looking. The window is the last twenty bars rather than the final one because the
        // Alligator's jaw is displaced eight bars forward and the two trailing stops break their
        // line at a flip, so all three legitimately have no value on the very last bar.
        val series = wavySeries()
        val tail = series.size - 20
        for (id in thirdPack) {
            val option = ChartCatalog.INDICATORS.first { it.id == id }
            when (option.pane) {
                IndicatorPane.PRICE -> {
                    val lines = ChartCatalog.overlayFor(option, series)
                    assertTrue("$id draws nothing on the price pane", lines.isNotEmpty())
                    assertTrue(
                        "$id has no value in the last twenty bars",
                        lines.any { it.values.extent(tail, series.size) != null },
                    )
                }
                IndicatorPane.SEPARATE -> {
                    // `correlation` is the exception and has its own test: without a second symbol
                    // it is supposed to draw nothing at all.
                    if (id == "correlation") continue
                    val pane = ChartCatalog.paneFor(option, series)
                    assertNotNull("$id produces no pane", pane)
                    val drawn = pane!!.lines + listOfNotNull(pane.histogram)
                    assertTrue("$id draws no line and no histogram", drawn.isNotEmpty())
                    assertTrue(
                        "$id has no value in the last twenty bars",
                        drawn.any { it.values.extent(tail, series.size) != null },
                    )
                }
                IndicatorPane.STRUCTURE -> {
                    val overlay = ChartCatalog.structureFor(option, series)
                    assertFalse("$id draws nothing", overlay.isEmpty)
                    assertTrue(
                        "$id marks nothing near the right-hand edge",
                        overlay.markers.any { it.time >= series.time[tail] },
                    )
                }
            }
        }
    }

    @Test
    fun `a feed that reports no volume offers no volume study at all`() {
        // The MT5 forex side. Every one of these is arithmetic on a column that does not exist, and
        // the failure being guarded against is not a crash: it is a Money Flow Index drawn flat at
        // fifty, or a Chaikin Money Flow drawn flat at zero, both of which look like a reading of a
        // balanced market rather than like a feed that sent nothing.
        val silent = CandleSeries(
            (0 until 300).map { index ->
                val close = 100.0 + kotlin.math.sin(index / 11.0) * 5 + index * 0.05
                Candle(
                    t = 1_700_000_000L + index * 3_600L,
                    o = close - 0.3,
                    h = close + 0.9,
                    l = close - 1.0,
                    c = close,
                )
            },
        )
        assertFalse("the fixture is supposed to have no volume", silent.hasVolume)
        for (id in ChartCatalog.VOLUME_ONLY_INDICATORS) {
            val option = ChartCatalog.INDICATORS.first { it.id == id }
            assertTrue(
                "$id must draw no overlay without a volume column",
                ChartCatalog.overlayFor(option, silent).isEmpty(),
            )
            assertNull(
                "$id must produce no pane without a volume column",
                ChartCatalog.paneFor(option, silent),
            )
        }
        assertNull("and no profile to draw from", ChartCatalog.volumeProfileFor(silent))
        // And the picker never offers them in the first place.
        assertEquals(
            ChartCatalog.INDICATORS.size - ChartCatalog.VOLUME_ONLY_INDICATORS.size,
            ChartCatalog.indicatorCount(hasVolume = false),
        )
        assertEquals(ChartCatalog.INDICATORS.size, ChartCatalog.indicatorCount(hasVolume = true))
        assertTrue(
            ChartCatalog.indicatorsFor(hasVolume = false).none { it.id in ChartCatalog.VOLUME_ONLY_INDICATORS },
        )
    }

    @Test
    fun `the same volume studies still draw when the feed does report volume`() {
        // The other half of the previous test. A guard that returns nothing unconditionally would
        // pass it, and would have quietly removed six working indicators from the crypto feed.
        val series = wavySeries()
        for (id in ChartCatalog.VOLUME_ONLY_INDICATORS) {
            val option = ChartCatalog.INDICATORS.first { it.id == id }
            val drew = when (option.pane) {
                IndicatorPane.PRICE -> ChartCatalog.overlayFor(option, series).isNotEmpty()
                else -> ChartCatalog.paneFor(option, series) != null
            }
            assertTrue("$id draws nothing even with a volume column", drew)
        }
    }

    @Test
    fun `the chop zone never leaves its eight colours`() {
        val series = wavySeries()
        val zones = IndicatorsExtC.chopZone(series.high, series.low, series.close, 30)
        assertEquals("the ramp is eight buckets, one per verdict", 8, ChartCatalog.CHOP_ZONE_COLOURS.size)
        assertEquals(
            "two buckets sharing a colour is two verdicts a reader cannot tell apart",
            8,
            ChartCatalog.CHOP_ZONE_COLOURS.toSet().size,
        )
        for (index in zones.indices) {
            assertTrue(
                "bar $index carries palette index ${zones[index]}, which is outside 0..7 and not the warm-up's −1",
                zones[index] == -1 || zones[index] in 0..7,
            )
        }
        assertTrue("the study never warmed up on this fixture", zones.any { it in 0..7 })

        val option = ChartCatalog.INDICATORS.first { it.id == "chopzone" }
        val overlay = ChartCatalog.structureFor(option, series)
        assertTrue("the chop zone is a band, not a line or a level", overlay.lines.isEmpty() && overlay.levels.isEmpty())
        assertEquals(
            "one mark per warmed-up bar and none for the warm-up",
            zones.count { it in 0..7 },
            overlay.markers.size,
        )
        assertTrue(
            "a mark was drawn in a colour that is not in the ramp",
            overlay.markers.all { it.colour in ChartCatalog.CHOP_ZONE_COLOURS },
        )
        assertTrue("the band hangs under the candles", overlay.markers.none { it.above })
    }

    @Test
    fun `correlation draws nothing at all until a second symbol is loaded`() {
        // The wrong answers here are worse than none. Zero would claim the two assets move
        // independently and one would claim they are the same bet, and both are statements about a
        // comparison the reader never made.
        val series = wavySeries()
        val option = ChartCatalog.INDICATORS.first { it.id == "correlation" }

        val alone = ChartCatalog.paneFor(option, series)
        assertNotNull("the pane still has to exist, so the legend can say why it is empty", alone)
        assertTrue("a correlation against nothing must draw no line", alone!!.lines.isEmpty())
        assertNull(alone.histogram)
        assertTrue("and must take no canvas", alone.heightRatio == 0f)

        // A second series that is an exact affine transform of the first: correlation is 1 by
        // definition, which makes this a check on the wiring rather than on the arithmetic.
        val together = ChartCatalog.paneFor(
            option,
            series,
            null,
            ComparisonSeries(
                symbol = "ETHUSDT",
                label = "ETH",
                colour = 0xFF6E8BE0,
                values = DoubleArray(series.size) { series.close[it] * 2 + 5 },
                times = series.time,
            ),
        )
        assertNotNull(together)
        val last = series.size - 1
        val values = together!!.lines.single().values
        assertTrue("the correlation has not warmed up by the last bar", values.isPresent(last))
        assertEquals("two series moving as one must read +1", 1.0, values.raw(last), 1e-9)

        val inverted = ChartCatalog.paneFor(
            option,
            series,
            null,
            ComparisonSeries(
                symbol = "SHORT",
                label = "معکوس",
                colour = 0xFFF6465D,
                values = DoubleArray(series.size) { -series.close[it] },
                times = series.time,
            ),
        )
        assertEquals(
            "and a mirror image must read −1",
            -1.0,
            inverted!!.lines.single().values.raw(last),
            1e-9,
        )
    }

    @Test
    fun `the volume profile indicator names the three prices it can honestly draw`() {
        // A profile is a histogram across the price axis and a ChartLine is one value per bar, so
        // the overlay carries the point of control and the two edges of the value area as flat
        // lines and the renderer asks volumeProfileFor for the rows themselves.
        val series = wavySeries()
        val option = ChartCatalog.INDICATORS.first { it.id == "volumeprofile_ind" }
        val lines = ChartCatalog.overlayFor(option, series)
        assertEquals(3, lines.size)
        assertEquals("POC", lines.first().label)
        val profile = ChartCatalog.volumeProfileFor(series)
        assertNotNull(profile)
        val rows = profile!!
        assertTrue("the point of control must be a real row", rows.pocIndex in rows.rowLow.indices)
        val control = lines.first().values
        assertTrue(
            "the point of control must sit inside the row it came from",
            control.raw(0) >= rows.rowLow[rows.pocIndex] &&
                control.raw(0) <= rows.rowHigh[rows.pocIndex],
        )
        assertTrue(
            "a level is true on every bar or it is not a level",
            (0 until series.size).all { control.isPresent(it) && control.raw(it) == control.raw(0) },
        )
        assertTrue(
            "the value area must contain the point of control",
            rows.valueAreaLow <= rows.pocIndex && rows.valueAreaHigh >= rows.pocIndex,
        )
    }

    @Test
    fun `the Alligator is not displaced a second time on the way to the chart`() {
        // The library bakes the 8/5/3-bar forward shift into its arrays. Shifting again here would
        // still produce three fanning lines that cross, so nothing on screen would look wrong — it
        // would simply be a different indicator, and nobody would ever catch it by eye.
        //
        // The proof is where each line starts. A 13-period average first exists on bar 12 and an
        // eight-bar shift publishes it on bar 20; the teeth move from 7 to 12 and the lips from 4
        // to 7. Shifted twice they would start on 28, 17 and 10 instead.
        val series = wavySeries()
        val option = ChartCatalog.INDICATORS.first { it.id == "alligator" }
        val (jaw, teeth, lips) = ChartCatalog.overlayFor(option, series)
        assertEquals(20, (0 until series.size).first { jaw.values.isPresent(it) })
        assertEquals(12, (0 until series.size).first { teeth.values.isPresent(it) })
        assertEquals(7, (0 until series.size).first { lips.values.isPresent(it) })
    }

    @Test
    fun `the parabolic SAR breaks its line where the stop changes sides`() {
        // A stop that jumps from under the price to over it in one bar, drawn as one continuous
        // line, puts a vertical stroke through the candles that no terminal draws and that reads as
        // a real move. The break is a null, which is what a gap in a Line means.
        val series = wavySeries()
        val raw = IndicatorsExtB.parabolicSar(series.high, series.low)
        val flips = (1 until series.size).filter { index ->
            raw[index].isFinite() && raw[index - 1].isFinite() &&
                (raw[index] > series.close[index]) != (raw[index - 1] > series.close[index - 1])
        }
        assertTrue("the fixture never reverses, so it cannot test this", flips.isNotEmpty())

        val option = ChartCatalog.INDICATORS.first { it.id == "sar" }
        val line = ChartCatalog.overlayFor(option, series).single().values
        for (index in flips) {
            assertFalse("the SAR is drawn straight through its reversal on bar $index", line.isPresent(index))
        }
        // And nowhere else: every other bar the library computed is still drawn.
        for (index in 0 until series.size) {
            if (index in flips || !raw[index].isFinite()) continue
            assertTrue("bar $index was dropped for no reason", line.isPresent(index))
        }
    }

    @Test
    fun `the volatility stop breaks its line where it changes sides`() {
        val series = wavySeries()
        val raw = IndicatorsExtC.volatilityStop(series.high, series.low, series.close, 20)
        val flips = (1 until series.size).filter { index ->
            raw.stop[index].isFinite() && raw.stop[index - 1].isFinite() &&
                raw.isLong[index] != raw.isLong[index - 1]
        }
        assertTrue("the fixture never reverses, so it cannot test this", flips.isNotEmpty())

        val option = ChartCatalog.INDICATORS.first { it.id == "volstop" }
        val line = ChartCatalog.overlayFor(option, series).single().values
        for (index in flips) {
            assertFalse("the stop is drawn straight through its flip on bar $index", line.isPresent(index))
        }
        for (index in 0 until series.size) {
            if (index in flips || !raw.stop[index].isFinite()) continue
            assertTrue("bar $index was dropped for no reason", line.isPresent(index))
        }
    }
}

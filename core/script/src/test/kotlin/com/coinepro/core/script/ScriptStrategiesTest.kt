package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * The ported strategy library, checked against the faults the originals had.
 *
 * Most of this file exists for one test: [a signal never changes once its bar has closed]. Every
 * other fault in the old library was visible to anybody who read the source carefully. Repainting
 * is not — it looks perfect in a screenshot and it is only wrong in time — so it is the one that
 * has to be proved by construction rather than by reading, and proving it is the whole point of
 * porting these at all.
 */
class ScriptStrategiesTest {

    /**
     * A series with the shapes these studies are built to read.
     *
     * Three sine waves of different lengths give trends, pullbacks and chop at once, so an
     * oscillator has somewhere to cross and a channel has somewhere to break. The noise stops
     * consecutive bars from being predictable from each other, which a smooth wave would let a
     * broken indicator hide behind.
     *
     * The **shocks** are the part that is easy to leave out and must not be. Every real chart has a
     * handful of bars several times the size of the ones around them, and a study that is only ever
     * fed a well-behaved series is a study nobody has tested: SuperTrend, in particular, cannot
     * change side at all without one, so a fixture without shocks would let a permanently frozen
     * SuperTrend pass as working.
     */
    private fun waves(count: Int): CandleSeries {
        var seed = 20260830L
        fun noise(): Double {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble()) - 0.5
        }
        var shock = 0.0
        var previous = 100.0
        return CandleSeries(
            List(count) { index ->
                if (index % 83 == 11) shock += if ((index / 83) % 2 == 0) 40.0 else -40.0
                val base = 100.0 + sin(index / 9.0) * 6 + sin(index / 31.0) * 14 +
                    sin(index / 97.0) * 22 + index * 0.02 + noise() * 5 + shock
                val open = previous
                previous = base
                val wick = (0.4 + abs(noise())) * 3
                Candle(
                    t = 1_700_000_000L + index * 3_600L,
                    o = open,
                    h = maxOf(open, base) + wick,
                    l = minOf(open, base) - wick,
                    c = base,
                    v = 900.0 + (index % 17) * 40,
                )
            },
        )
    }

    /** A market that did not move: one price, no range, no volume worth the name. */
    private fun flat(count: Int): CandleSeries = CandleSeries(
        List(count) { index ->
            Candle(t = 1_700_000_000L + index * 3_600L, o = 100.0, h = 100.0, l = 100.0, c = 100.0, v = 0.0)
        },
    )

    /**
     * The first [count] bars, with the last one shown part-way through its life.
     *
     * A live chart's newest bar starts at its open and travels to its close, and at any instant a
     * script reading it sees whatever it has reached so far. [progress] is how far along that
     * journey the bar is: 0 is the tick it opened on, 1 is the bar as history will remember it. The
     * wick is trimmed to the part of the range the bar has actually traded through, because a bar
     * cannot have printed a high it has not reached yet.
     *
     * This is the only way to reproduce a repaint in this engine. Truncating a series produces
     * closed bars, and every calculation here is causal, so truncation alone can never make an
     * earlier answer change — it is the *forming* bar that moves.
     */
    private fun forming(full: CandleSeries, count: Int, progress: Double): CandleSeries {
        val bars = full.bars.take(count).toMutableList()
        val last = bars.removeAt(bars.size - 1)
        val close = last.o + (last.c - last.o) * progress
        bars += last.copy(
            c = close,
            h = maxOf(last.o, close),
            l = minOf(last.o, close),
        )
        return CandleSeries(bars)
    }

    private fun ScriptResult.markedBars(): Map<String, List<Int>> = markers.associate { it.title to it.bars }

    /* ------------------------------------------------------------------ the library itself */

    @Test
    fun `every strategy runs`() {
        val series = waves(600)
        for (strategy in ScriptStrategies.ALL) {
            val result = NamaScript.run(strategy.source, series)
            assertNull(
                "«${strategy.id}» با خطا متوقف شد: ${result.error?.message} (خط ${result.error?.line})",
                result.error,
            )
        }
    }

    @Test
    fun `every strategy marks at least one bar`() {
        // A strategy that runs and marks nothing looks broken to the only person it was written
        // for — and one of the originals genuinely could not fire, so this is not hypothetical.
        val series = waves(600)
        for (strategy in ScriptStrategies.ALL) {
            val marked = NamaScript.run(strategy.source, series).markers.sumOf { it.bars.size }
            assertTrue("«${strategy.id}» هیچ کندلی را نشانه نگذاشت", marked > 0)
        }
    }

    @Test
    fun `every strategy id is unique and resolvable`() {
        val ids = ScriptStrategies.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (id in ids) assertNotNull(ScriptStrategies.byId(id))
    }

    @Test
    fun `every strategy offers the inputs it declares`() {
        val series = waves(600)
        for (strategy in ScriptStrategies.ALL) {
            val result = NamaScript.run(strategy.source, series)
            assertTrue("«${strategy.id}» ورودی اعلام نکرد", result.inputs.isNotEmpty())
        }
    }

    /* ------------------------------------------------------------------ repainting */

    /**
     * The test the whole exercise is for.
     *
     * A bar is fed to the strategy twice: once while it is the newest bar on the chart, and again
     * later when three hundred bars have been drawn after it. If the answer differs, the strategy
     * repaints — it drew an arrow in real time that history then erased, or erased one that history
     * then drew, and a reader scrolling back through it is reading a record of decisions that were
     * never actually offered to them.
     *
     * Checked at several cut points rather than one, because a repaint that only shows up at a
     * particular phase of an indicator's warm-up is exactly the kind that survives a single check.
     */
    @Test
    fun `a signal never changes once its bar has closed`() {
        val full = waves(600)
        val complete = ScriptStrategies.ALL.associate { it.id to NamaScript.run(it.source, full).markedBars() }
        for (cut in listOf(120, 205, 311, 448, 599)) {
            val prefix = CandleSeries(full.bars.take(cut))
            for (strategy in ScriptStrategies.ALL) {
                val early = NamaScript.run(strategy.source, prefix)
                assertNull("«${strategy.id}» روی ${cut} کندل خطا داد", early.error)
                val later = complete.getValue(strategy.id)
                for ((title, bars) in early.markedBars()) {
                    // Up to the *second* to last bar: the newest bar of any run is treated as
                    // unconfirmed, so a prefix is one bar behind by design rather than by accident.
                    assertEquals(
                        "«${strategy.id}» / «$title» با آمدن کندل‌های بعدی عوض شد (برش $cut)",
                        later.getValue(title).filter { it < cut - 1 },
                        bars,
                    )
                }
            }
        }
    }

    @Test
    fun `no strategy marks the bar that is still forming`() {
        // The last bar of any loaded series is the one being traded, and nothing in the data says
        // whether it has closed. Every strategy ends its condition with `and confirmed` for this.
        for (count in listOf(60, 137, 300, 600)) {
            val series = waves(count)
            for (strategy in ScriptStrategies.ALL) {
                val result = NamaScript.run(strategy.source, series)
                assertNull("«${strategy.id}» روی $count کندل خطا داد", result.error)
                for (marker in result.markers) {
                    assertFalse(
                        "«${strategy.id}» روی کندلِ در حال شکل‌گیری نشانه گذاشت ($count کندل)",
                        count - 1 in marker.bars,
                    )
                }
            }
        }
    }

    /**
     * A forming bar is never marked, whatever it currently shows.
     *
     * The same bar is put to every strategy three times — as it opened, half way through, and as it
     * finally closed — and none of them may mark it in any of those states, nor may anything they
     * said about the bars behind it move.
     */
    @Test
    fun `a bar that is still forming is never marked and never disturbs the bars behind it`() {
        val full = waves(600)
        for (count in listOf(180, 264, 351, 470, 588)) {
            for (strategy in ScriptStrategies.ALL) {
                val states = listOf(0.0, 0.5, 1.0).map { NamaScript.run(strategy.source, forming(full, count, it)) }
                for (state in states) {
                    assertNull("«${strategy.id}» روی کندل نیم‌ساخته خطا داد", state.error)
                    for (marker in state.markers) {
                        assertFalse(
                            "«${strategy.id}» کندلِ در حال شکل‌گیری را نشانه گذاشت ($count کندل)",
                            count - 1 in marker.bars,
                        )
                    }
                }
                assertEquals(
                    "«${strategy.id}» با تغییر کندلِ باز، نشانه‌های کندل‌های قبلی را جابه‌جا کرد",
                    states.first().markedBars(),
                    states.last().markedBars(),
                )
            }
        }
    }

    /**
     * Proof that the guard is load-bearing rather than decorative.
     *
     * The same crossover written the way the old library wrote it — with nothing to say the bar has
     * closed — is put to a bar three times as that bar forms. It marks the bar in one state and not
     * in another: an arrow that appears while a reader is watching and is gone a minute later. That
     * is the fault, reproduced. The guarded version marks nothing in any state.
     *
     * Without this the tests above could all be passing because the fixture never happens to cross
     * on a bar that was still moving.
     */
    @Test
    fun `the confirmed guard is what stops a mark from being drawn and then withdrawn`() {
        val body = "ta.crossover(ta.ema(close, 9), ta.ema(close, 21))"
        val unguarded = "marker($body, title = \"خام\", style = \"up\")"
        val guarded = "marker($body and confirmed, title = \"خام\", style = \"up\")"
        val full = waves(600)

        var withdrawn = 0
        for (count in 40..600) {
            val states = listOf(0.0, 0.35, 0.7, 1.0).map { progress ->
                count - 1 in NamaScript.run(unguarded, forming(full, count, progress)).markers.single().bars
            }
            if (states.toSet().size > 1) withdrawn++
            for (progress in listOf(0.0, 0.35, 0.7, 1.0)) {
                assertFalse(
                    "نسخهٔ محافظت‌شده نباید روی کندلِ در حال شکل‌گیری نشانه بگذارد",
                    count - 1 in NamaScript.run(guarded, forming(full, count, progress)).markers.single().bars,
                )
            }
        }
        assertTrue("آزمون بی‌اثر است: نسخهٔ بی‌محافظ هیچ نشانه‌ای را پس نگرفت", withdrawn > 0)
    }

    /* ------------------------------------------------------------------ warm-up */

    @Test
    fun `no strategy signals inside the warm-up it declares`() {
        for (count in listOf(60, 300, 600)) {
            val series = waves(count)
            for (strategy in ScriptStrategies.ALL) {
                val result = NamaScript.run(strategy.source, series)
                for (marker in result.markers) {
                    val early = marker.bars.filter { it < strategy.warmUpBars }
                    assertEquals(
                        "«${strategy.id}» داخل ${strategy.warmUpBars} کندل گرم شدن سیگنال داد: $early",
                        emptyList<Int>(),
                        early,
                    )
                }
            }
        }
    }

    /* ------------------------------------------------------------------ degenerate series */

    @Test
    fun `a market that never moved produces no signal and no error`() {
        // Every division these studies make has a zero denominator here: no range for the ATR, no
        // deviation for the bands, no directional movement for the ADX. A NaN that propagates as a
        // number rather than as an absence is how a study signals on a chart with nothing on it.
        val series = flat(300)
        for (strategy in ScriptStrategies.ALL) {
            val result = NamaScript.run(strategy.source, series)
            assertNull("«${strategy.id}» روی نمودار بی‌حرکت خطا داد: ${result.error?.message}", result.error)
            for (marker in result.markers) {
                assertEquals("«${strategy.id}» روی نمودار بی‌حرکت سیگنال داد", emptyList<Int>(), marker.bars)
            }
            assertNull("«${strategy.id}» روی نمودار بی‌حرکت ستاپ ساخت", result.setup)
        }
    }

    @Test
    fun `every setup a strategy produces is the right way round`() {
        val series = waves(600)
        for (strategy in ScriptStrategies.ALL) {
            val setup = NamaScript.run(strategy.source, series).setup ?: continue
            assertTrue("«${strategy.id}» حد ضرر را بالای ورود گذاشت", setup.stop < setup.entry)
            val reward = setup.riskReward
            assertNotNull("«${strategy.id}» هدفی اعلام نکرد", reward)
            assertTrue("«${strategy.id}» نسبت ریسک به بازده معناداری نداد", reward!! > 0.5)
            assertTrue(
                "«${strategy.id}» ستاپ را روی کندلِ در حال شکل‌گیری ساخت",
                setup.barIndex < series.size - 1,
            )
        }
    }

    /* ------------------------------------------------------------------ the named faults */

    /**
     * The dead Donchian breakout, kept dead in a test so it cannot come back.
     *
     * The old library asked whether the close had crossed above the highest high of a window that
     * **ends at this bar**. That maximum is at least this bar's own high, and a close is never
     * above the high of its own bar, so the condition is false on every bar of every chart that has
     * ever existed. The channel has to be read one bar back, and this pins both halves of that.
     */
    @Test
    fun `a channel that includes the breaking bar can never be broken`() {
        val series = waves(600)
        val including = NamaScript.run(
            "marker(ta.crossover(close, ta.donchian_upper(20)), title = \"شکست\", style = \"up\")",
            series,
        )
        assertEquals(
            "شرطی که کندل شکننده را در پنجره دارد، هرگز نباید برقرار شود",
            emptyList<Int>(),
            including.markers.single().bars,
        )

        val shifted = NamaScript.run(
            "marker(ta.crossover(close, ta.donchian_upper(20)[1]), title = \"شکست\", style = \"up\")",
            series,
        )
        assertTrue("کانالِ یک کندل عقب باید شکسته شود", shifted.markers.single().bars.isNotEmpty())
    }

    /**
     * The confluence score's arrow, which used to appear a whole level late.
     *
     * `crossover(score, 3)` wants the series to end up strictly above 3, and an integer score
     * stepping from 2 to 3 does not — so the arrow only ever appeared when the score reached 4,
     * while the guide line and the legend both said three. "Reached the level on this bar" is the
     * condition that was meant.
     */
    @Test
    fun `reaching a level and crossing it are not the same condition on an integer score`() {
        val series = waves(600)
        val score = "score = (ta.ema(close, 20) > ta.ema(close, 50)) + (ta.rsi(close, 14) > 50) + " +
            "(close > ta.bb_basis(close, 20, 2)) + (ta.macd_hist(close, 12, 26, 9) > 0)\n"

        val crossedRun = NamaScript.run(score + "marker(ta.crossover(score, 3), title = \"س\", style = \"up\")", series)
        val reachedRun = NamaScript.run(
            score + "reached = score >= 3\nmarker(reached and not reached[1], title = \"س\", style = \"up\")",
            series,
        )
        assertNull(crossedRun.error?.message, crossedRun.error)
        assertNull(reachedRun.error?.message, reachedRun.error)
        val crossed = crossedRun.markers.single().bars
        val reached = reachedRun.markers.single().bars

        // The exact shape of the old fault: `crossover(score, 3)` demands the score end up strictly
        // above three, and on an integer score that is the same set of bars as "first reached
        // four". The line drawn at three and the arrow drawn at four were never the same study.
        val fourRun = NamaScript.run(
            score + "atFour = score >= 4\nmarker(atFour and not atFour[1], title = \"س\", style = \"up\")",
            series,
        )
        assertNull(fourRun.error?.message, fourRun.error)
        assertEquals(
            "تقاطع با ۳ روی امتیاز صحیح، در عمل همان «اولین رسیدن به ۴» است",
            fourRun.markers.single().bars,
            crossed,
        )
        assertTrue(
            "اشکالِ نسخهٔ قدیمی باید هنوز قابل نمایش باشد: کندل‌هایی که امتیاز در آن‌ها به ۳ رسید، فلش نمی‌گرفتند",
            (reached - crossed.toSet()).isNotEmpty(),
        )
    }

    /**
     * The Ichimoku cloud, which the original was not reading.
     *
     * Senkou A and B are drawn twenty-six bars ahead of where they are computed, so the cloud a
     * chart shows *at* a bar came from a calculation twenty-six bars earlier. The original compared
     * today's close against an undisplaced span; that is a different series with the same name, and
     * this shows the two disagree rather than being a matter of taste.
     */
    @Test
    fun `the cloud at a bar is not the span computed at that bar`() {
        val series = waves(600)
        val here = NamaScript.run(
            "marker(close > ta.ichimoku_span_b(9, 26, 52), title = \"ب\", style = \"up\")",
            series,
        ).markers.single().bars
        val displaced = NamaScript.run(
            "marker(close > ta.ichimoku_span_b(9, 26, 52)[26], title = \"ب\", style = \"up\")",
            series,
        ).markers.single().bars
        assertFalse("اگر این دو یکی باشند، جابه‌جایی ابر بی‌اثر شده است", here == displaced)
    }

    /* ------------------------------------------------------------------ pinned output */

    /**
     * Every signal every strategy produces on the fixture, written down.
     *
     * Not a golden file for its own sake: these bars are the contract. A change anywhere — in a
     * strategy, in a builtin, in `core:chart`'s indicators — that moves a single arrow on a series
     * that has not changed is a change that moves arrows on real charts too, and it should have to
     * be looked at and re-approved rather than noticed by a reader.
     */
    @Test
    fun `the signals on the fixture are the ones recorded here`() {
        val series = waves(600)
        val expected = mapOf(
            "hull-cross" to listOf(
                listOf(41, 100, 152, 210, 267, 284, 288, 319, 325, 334, 370, 383, 413, 433, 488, 492, 542, 546),
                listOf(65, 124, 184, 235, 283, 286, 292, 322, 332, 349, 378, 408, 414, 460, 489, 516, 543, 583),
            ),
            "supertrend-flip" to listOf(
                listOf(177, 343, 509),
                listOf(94, 260, 426, 592),
            ),
            "channel-breakout" to listOf(
                listOf(170, 177, 222, 229, 343, 345, 393, 395, 397, 402, 509, 515, 561, 565, 568, 570, 573, 580),
                listOf(90, 94, 138, 142, 146, 252, 256, 260, 263, 268, 307, 309, 314, 317, 321, 428, 477, 480, 483, 488, 592),
            ),
            "band-reversion" to listOf(
                listOf(97, 264, 308, 312, 429, 484, 596),
                listOf(57, 181, 230, 347, 403, 512, 563, 567, 569),
            ),
            "trend-pullback" to listOf(
                listOf(253, 257, 259, 364),
                emptyList(),
            ),
            "triple-confluence" to listOf(
                listOf(177, 347, 408, 417, 512, 577),
                listOf(146, 150, 260, 428, 444, 447, 458, 488, 598),
            ),
            "confluence-score" to listOf(
                listOf(75, 162, 164, 198, 210, 213, 343, 384, 456, 460, 464, 466, 509, 533, 553),
            ),
            "supertrend-momentum" to listOf(
                listOf(177, 248, 343, 372, 383, 420, 423, 509, 538, 540, 542, 553),
                listOf(94, 131, 161, 163, 260, 426, 459, 463, 468, 592),
            ),
            "channel-momentum" to listOf(
                listOf(56, 62, 118, 170, 177, 222, 229, 343, 345, 393, 395, 397, 402, 509, 515, 561, 565, 568, 570, 573, 580),
                listOf(90, 94, 138, 142, 146, 252, 256, 260, 263, 307, 309, 314, 317, 321, 379, 425, 428, 477, 480, 483, 488, 592),
            ),
            "ichimoku-cloud" to listOf(
                listOf(219, 343, 389, 511, 559),
                listOf(137, 426, 476),
            ),
            "directional-strength" to listOf(
                listOf(130, 160, 343, 455, 509),
                listOf(79, 252, 379, 425, 543, 592),
            ),
        )
        assertEquals(ScriptStrategies.ALL.map { it.id }.toSet(), expected.keys)
        for (strategy in ScriptStrategies.ALL) {
            val actual = NamaScript.run(strategy.source, series).markers.map { it.bars }
            assertEquals("«${strategy.id}» سیگنال‌هایش عوض شده", expected.getValue(strategy.id), actual)
        }
    }
}

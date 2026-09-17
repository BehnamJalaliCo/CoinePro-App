package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartLegendTarget
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Every study the catalogue offers can be taken off the chart again** (run Υ, item 2).
 *
 * «یه اندیکاتور رو روی چارت میندازیم و ضرب در رو می‌زنیم ولی هنوز اندیکاتوره هست … گزینه‌های روی
 * چارت اندیکاتور نمیادش که من حذفش بکنم.»
 *
 * The legend addresses a study by **where its row sits**: `Overlay(3)` means «the fourth line on the
 * price scale», `Pane(1)` means «the second strip». `ChartController.indicatorFor` turns that
 * position back into an id by reading the owner list kept beside the drawing — and the whole
 * affordance rests on those two lists being the same length and in the same order. Where they are
 * not, the × resolves to null and does nothing, the gear resolves to null and opens the catalogue
 * instead of the study, and the reader is left with a line on their chart and no control that
 * addresses it. That is exactly the report, and it is not a rendering fault: it is arithmetic, so
 * it is checked here over **every** indicator rather than over the two somebody thought of.
 *
 * The rows are built the way `legendRows` builds them — see `ChartLegendOverlay` — because that is
 * the contract being tested: this asserts about what the legend will actually ask for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IndicatorRemovalTest {

    private class FakeGateway : CandleGateway {
        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage =
            CandlePage(
                symbol,
                timeframe,
                (0 until 400).map { index ->
                    val t = 1_000L + index * timeframe.seconds
                    val c = 100.0 + (index % 17) * 0.4 - (index % 5) * 0.3
                    OhlcBar(t = t, o = c - 0.2, h = c + 0.6, l = c - 0.7, c = c, v = 10.0 + index % 7)
                },
            )
    }

    private fun controller(scope: TestScope) = ChartController("BTCUSDT", FakeGateway(), scope)

    /** The targets the legend would draw for the chart as it stands. */
    private fun targetsOf(state: ChartUiState): List<ChartLegendTarget> = buildList {
        state.overlays.forEachIndexed { position, overlay ->
            // `legendRows` skips a line with no name — it has nothing to print — but it keeps the
            // position, so the indices here are the indices the legend uses.
            if (!overlay.label.isNullOrBlank()) add(ChartLegendTarget.Overlay(position))
        }
        state.panes.indices.forEach { position -> add(ChartLegendTarget.Pane(position)) }
        // The studies with nothing on either scale to name them, addressed by their own id.
        state.studyRows.forEach { study -> add(ChartLegendTarget.Study(study.key)) }
    }

    @Test
    fun `every indicator in the catalogue answers the legend row it draws`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        val orphans = mutableListOf<String>()
        val silent = mutableListOf<String>()
        for (option in ChartCatalog.INDICATORS) {
            controller.toggleIndicator(option.id)
            val state = controller.state.value
            val targets = targetsOf(state)
            if (targets.isEmpty()) {
                // A study that draws nothing at all on 400 bars is a different defect; name it
                // rather than passing it off as a resolvable row.
                silent += option.id
            } else if (targets.none { state.indicatorFor(it) == option.id }) {
                orphans += option.id
            }
            controller.toggleIndicator(option.id)
        }
        assertEquals("studies drawing rows that resolve to nobody: $orphans", emptyList<String>(), orphans)
        assertEquals("studies that drew nothing to remove: $silent", emptyList<String>(), silent)
    }

    @Test
    fun `the row's remove takes the study off, one study at a time`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        val stuck = mutableListOf<String>()
        for (option in ChartCatalog.INDICATORS) {
            controller.toggleIndicator(option.id)
            val target = targetsOf(controller.state.value)
                .firstOrNull { controller.state.value.indicatorFor(it) == option.id }
            if (target == null) {
                stuck += option.id
                controller.toggleIndicator(option.id)
                continue
            }
            // What `ChartScreen`'s `onRemoveSeries` does with the legend's target.
            controller.state.value.indicatorFor(target)?.let(controller::toggleIndicator)
            if (option.id in controller.state.value.activeIndicators) stuck += option.id
            controller.state.value.activeIndicators.forEach(controller::toggleIndicator)
        }
        assertEquals("studies the × could not take off: $stuck", emptyList<String>(), stuck)
    }

    @Test
    fun `a study stays addressable while other studies are on the chart with it`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        // A reader's ordinary chart: two lines on the price and two strips under it.
        listOf("ema", "bollinger", "rsi", "macd").forEach(controller::toggleIndicator)
        val state = controller.state.value
        val resolved = targetsOf(state).mapNotNull(state::indicatorFor).toSet()
        assertTrue(
            "every study on the chart has at least one row that addresses it, got $resolved",
            resolved.containsAll(setOf("ema", "bollinger", "rsi", "macd")),
        )
    }

    @Test
    fun `a row hidden by the eye still addresses its own study`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        listOf("ema", "sma", "rsi").forEach(controller::toggleIndicator)
        val before = controller.state.value
        val emaRow = targetsOf(before).first { before.indicatorFor(it) == "ema" }
        controller.toggleIndicatorHidden("ema")

        // The legend keeps a hidden row in place — that is the way back — so the row that was the
        // EMA's has to still be the EMA's, or the × beside it removes somebody else's study.
        val after = controller.state.value
        assertEquals("ema", after.indicatorFor(emaRow))
        assertTrue(emaRow in after.hiddenTargets)
    }
}

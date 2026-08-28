package com.coinepro.app.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coinepro.feature.chart.ChartController
import com.coinepro.core.datastore.ChartDrawingStore
import com.coinepro.core.marketdata.CandleGateway
import kotlinx.coroutines.CoroutineScope

/**
 * One chart controller per symbol, living above the navigation graph.
 *
 * ### The bug this exists to kill
 *
 * The chart screen and the chart studio each built their own `ChartController` inside their own
 * `composable {}` block, with `remember`. They are separate destinations, and a `NavHost` composes
 * only one destination at a time — so they were never two views of one chart. They were two charts.
 *
 * - Arming a drawing tool in the studio wrote to an object the chart screen could not see. Coming
 *   back, nothing was armed, the active-tool bar never appeared, and tapping the chart did nothing.
 *   **The drawing tools did not work for anybody.** The owner reported it as a guest problem
 *   because that is how he had been using the app; there is no guest gate on the chart, and there
 *   never was.
 * - Toggling an indicator in the studio was lost the same way. So was the chart type. So was a
 *   replay in progress.
 * - Worse, navigating chart → studio → chart disposed the chart's own composition, so its viewport,
 *   its drawings and its timeframe were rebuilt from defaults too. `remember` rather than
 *   `rememberSaveable`, so a rotation did the same thing.
 *
 * ### Why a holder rather than a ViewModel
 *
 * A `ViewModel` scoped to the navigation graph would work and is the ordinary answer. This shell
 * builds every one of its twenty-odd controllers by hand and injects each gateway per platform, so
 * a hand-held map keeps the chart consistent with everything around it rather than making it the
 * one thing wired differently.
 *
 * ### Lifetime
 *
 * As long as the shell is composed, which is the session. That is the point: a chart you left ten
 * minutes ago should still have your drawings and your zoom on it. The map is rebuilt when the
 * candle gateway changes, so switching platform rebuilds every chart against the right backend
 * rather than paging a forex symbol out of the crypto route.
 *
 * Bounded at [MAX_CONTROLLERS], because a reader flipping through symbols would otherwise keep one
 * live controller — each holding a candle series — for every symbol they had ever opened. Eviction
 * is least-recently-used and takes that symbol's drawings with it. The cap is high enough that a
 * real session does not reach it.
 */
class ChartControllers(
    private val gateway: CandleGateway,
    private val scope: CoroutineScope,
    /** Where each symbol's drawings are kept between sessions. */
    private val drawings: ChartDrawingStore,
) {
    private val controllers = LinkedHashMap<String, ChartController>()

    fun controllerFor(symbol: String): ChartController {
        val key = symbol.uppercase()
        controllers.remove(key)?.let { existing ->
            // Re-inserted rather than left in place: this is the LRU queue, and reading a symbol
            // has to count as using it or the chart in front of the reader is the next evicted.
            controllers[key] = existing
            return existing
        }
        val created = ChartController(
            symbol = symbol,
            gateway = gateway,
            scope = scope,
            drawings = drawings,
        )
        controllers[key] = created
        while (controllers.size > MAX_CONTROLLERS) {
            controllers.remove(controllers.keys.first())
        }
        return created
    }

    private companion object {
        const val MAX_CONTROLLERS = 8
    }
}

/**
 * The holder for this composition, rebuilt when the platform's candle source changes.
 *
 * [scope] must be the *shell's*, not a screen's. That is the other half of the fix: a scope
 * belonging to the chart destination was cancelled the moment the reader opened the studio, so the
 * load in flight died and the chart came back empty even when the controller survived.
 */
@Composable
fun rememberChartControllers(
    gateway: CandleGateway,
    scope: CoroutineScope,
    drawings: ChartDrawingStore,
): ChartControllers = remember(gateway, scope, drawings) {
    ChartControllers(gateway, scope, drawings)
}

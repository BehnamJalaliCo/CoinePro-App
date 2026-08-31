package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.marketdata.ChartInterval

/**
 * The slice of a chart a reader is allowed to take back.
 *
 * ### Why it is a snapshot and not a list of commands
 *
 * The alternative — a typed `Change` per action, each knowing how to invert itself — is the
 * textbook answer and it is the wrong one here. There are more than a dozen things on this screen
 * that change what the chart says, they interact (switching an indicator on spends the visible
 * window; a range change sets an interval and then a range), and an inverse written per action is
 * a dozen places for the inverse to be subtly wrong. A snapshot of six fields cannot be wrong
 * about what the chart was: it *is* what the chart was.
 *
 * It is cheap because of what it deliberately leaves out. The bars are not here — a snapshot
 * holding a fifty-thousand-candle series would be a memory leak with a stack behind it — and
 * neither is anything derived from them. What is here is the *apparatus*: which bar length, which
 * span, which chart type, which studies at which lookbacks, and the drawing layer. Restoring those
 * six and letting the controller refetch is both smaller and more correct, because the bars a
 * reader wants back are today's bars, not the ones that were on screen when they made the change.
 *
 * ### What is not in it, and why
 *
 * The symbol. An undo that changed which market you are looking at would be a different screen
 * arriving unannounced, and no terminal does it. The pan and the zoom are out for the same reason
 * in reverse: they are continuous, a reader adjusts them constantly, and a history that filled with
 * them would push the change they actually want to reverse off the end of the stack within
 * seconds.
 */
internal data class ChartStep(
    val interval: ChartInterval,
    val range: ChartRange?,
    val chartType: ChartType,
    val indicators: Set<String>,
    val indicatorPeriods: Map<String, Int>,
    val drawing: DrawingState,
)

/**
 * Two stacks and the rules between them.
 *
 * Pure — no state flow, no controller, no clock of its own — so the rules can be held to in a unit
 * test rather than only observed on a device. The clock is passed in because the one rule that is
 * not purely structural is a time rule; see [record].
 *
 * ### The rule that matters: a drag is one step, not sixty
 *
 * A drawing being dragged emits a new state on every frame — that is what makes the line follow
 * the finger. Recording each would fill the stack with a single gesture and make undo useless: the
 * reader taps it forty times and the line creeps back across the chart. So a change that only
 * moves geometry is recorded at most once per [COALESCE_MS], while a change to the *shape* of the
 * layer — a drawing added, deleted, a tap placed or taken back — is always recorded. A long
 * adjustment therefore becomes two or three steps rather than sixty or one, which is what a reader
 * means by "take back what I was just doing".
 */
internal class ChartHistory(
    private val limit: Int = LIMIT,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val back = ArrayDeque<ChartStep>()
    private val forward = ArrayDeque<ChartStep>()

    /** When the last step was pushed, for the coalescing rule above. */
    private var lastRecordedAt = 0L

    val canUndo: Boolean get() = back.isNotEmpty()
    val canRedo: Boolean get() = forward.isNotEmpty()

    /**
     * Push the chart as it was *before* a change.
     *
     * [coalescable] marks a change that is one frame of a continuous gesture. Passing false — which
     * every discrete action does — always records.
     *
     * Recording clears the redo stack, which is the universal rule and the one readers rely on
     * without being able to state: once you have gone a different way, the way you did not go is
     * gone. Keeping it would mean a redo that reapplies a change on top of a chart it was never
     * made against.
     */
    fun record(step: ChartStep, coalescable: Boolean = false) {
        val at = now()
        if (coalescable && back.isNotEmpty() && at - lastRecordedAt < COALESCE_MS) return
        // A step identical to the top of the stack is not a step. Several call sites record before
        // checking whether anything will actually change, which is the safe order — a check that
        // has to be right in a dozen places will eventually be wrong in one — and this is where
        // that safety is paid back.
        if (back.lastOrNull() == step) return
        back.addLast(step)
        forward.clear()
        lastRecordedAt = at
        while (back.size > limit) back.removeFirst()
    }

    /**
     * Take one step back, given where the chart is now. Null when there is nothing to take back.
     *
     * The caller hands in the present so it can be pushed onto the redo stack: undo and redo are
     * the same operation in opposite directions, and neither needs to know how to invert anything.
     */
    fun undo(current: ChartStep): ChartStep? {
        val previous = back.removeLastOrNull() ?: return null
        forward.addLast(current)
        return previous
    }

    /** The mirror of [undo]. */
    fun redo(current: ChartStep): ChartStep? {
        val next = forward.removeLastOrNull() ?: return null
        back.addLast(current)
        return next
    }

    /**
     * Forget everything.
     *
     * Called when the symbol changes. A stack carried across symbols would offer to take back a
     * change made to a different market — the reader taps undo expecting their indicator back and
     * gets somebody else's chart type, on bars that never had it.
     */
    fun clear() {
        back.clear()
        forward.clear()
        lastRecordedAt = 0L
    }

    private companion object {
        /**
         * How many steps are kept.
         *
         * Fifty is far past what anybody walks back through in one sitting, and the whole stack at
         * that depth is a few tens of kilobytes because the bars are not in it.
         */
        const val LIMIT = 50

        /** The window a continuous gesture's frames collapse into. */
        const val COALESCE_MS = 600L
    }
}

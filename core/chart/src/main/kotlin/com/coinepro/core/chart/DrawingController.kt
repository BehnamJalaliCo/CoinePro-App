package com.coinepro.core.chart

/**
 * What the drawing layer looks like right now.
 *
 * A value, not a mutable object, so a screen can hoist it, persist it and hand it back — which is
 * what a saved chart layout is. Nothing here knows about Compose.
 */
data class DrawingState(
    /** Placed drawings, oldest first. The order is z-order. */
    val drawings: List<Drawing> = emptyList(),
    /** The armed tool, or null for the cursor. */
    val tool: DrawingTool? = null,
    /** Points tapped so far on the drawing being placed. */
    val pending: List<ChartPoint> = emptyList(),
    val selectedId: Long? = null,
    /** The reader's chosen colour for the next drawing. */
    val colour: Long = Drawing.DEFAULT_DRAWING_COLOUR,
    /** Snap each tap to the nearest of the bar's open/high/low/close. */
    val magnet: Boolean = false,
) {
    /**
     * What the chart should render: the placed drawings plus the one being built.
     *
     * The in-progress one is included so the reader sees a five-point pattern take shape as they
     * tap it out. Without this an XABCD is four taps into nothing followed by a shape appearing.
     */
    val visible: List<Drawing>
        get() = if (pending.isEmpty() || tool == null) {
            drawings
        } else {
            drawings + Drawing(
                id = PREVIEW_ID,
                toolId = tool.id,
                points = pending,
                colour = colour,
                complete = false,
            )
        }

    /** How many more taps the armed tool needs. Zero when nothing is armed or it is freehand. */
    val remaining: Int
        get() = tool?.let { max0(it.points - pending.size) } ?: 0

    val canUndo: Boolean get() = drawings.isNotEmpty() || pending.isNotEmpty()

    private fun max0(value: Int) = if (value < 0) 0 else value

    companion object {
        /**
         * The id the half-placed drawing carries.
         *
         * Negative, so it can never collide with a real one — ids count up from 1 — and so a
         * `selectedId` of a real drawing never accidentally lights up the preview.
         */
        const val PREVIEW_ID = -1L
    }
}

/**
 * The placement state machine.
 *
 * Pure functions from state to state, which is the reason it is not a `ViewModel` with fields: the
 * tap sequence for a six-point Elliott impulse is exactly the sort of thing that goes wrong once and
 * then goes wrong forever, and this way every step of it is a unit test rather than a device.
 *
 * The rules it enforces, all of which the web terminal learned the hard way:
 *
 * * A tool that needs N taps commits on the Nth, not the (N+1)th.
 * * A two-point tool committed by a drag commits on lift, so a trend line is one gesture.
 * * Arming a tool clears the selection. Being in a drawing mode and having something selected are
 *   different states, and a reader who is in both does not know what the next tap will do.
 * * Undo takes back the in-progress taps before it takes back a finished drawing. Otherwise the
 *   first undo after three taps of an XABCD deletes somebody's trend line from ten minutes ago.
 */
object DrawingActions {

    /** Arm a tool, or pass null for the cursor. Clears anything half-placed. */
    fun arm(state: DrawingState, tool: DrawingTool?): DrawingState = state.copy(
        tool = tool?.takeUnless { it.group == ToolGroup.MODES },
        pending = emptyList(),
        selectedId = null,
    )

    /**
     * A tap in chart space.
     *
     * With no tool armed this selects — [nearest] is whatever the caller's hit test found, which
     * this cannot compute because it has no viewport and therefore no pixels.
     */
    fun tap(state: DrawingState, point: ChartPoint, nearest: Long? = null): DrawingState {
        val tool = state.tool ?: return state.copy(selectedId = nearest)
        if (tool.points <= 0) return state
        val points = state.pending + point
        if (points.size < tool.points) return state.copy(pending = points)
        return commit(state, tool, points)
    }

    /**
     * A drag that placed a two-point tool in one gesture.
     *
     * Only for two-point tools: a three-point channel cannot be dragged out because its third point
     * is not a corner of anything the first two describe.
     */
    fun drag(state: DrawingState, from: ChartPoint, to: ChartPoint): DrawingState {
        val tool = state.tool ?: return state
        if (tool.points != 2) return state
        return commit(state, tool, listOf(from, to))
    }

    /** A freehand stroke, already sampled. */
    fun stroke(state: DrawingState, points: List<ChartPoint>): DrawingState {
        val tool = state.tool ?: return state
        if (tool.points != 0 || points.size < 2) return state
        return commit(state, tool, points)
    }

    /**
     * Take back one step.
     *
     * The half-placed taps first, then the last finished drawing. A reader four taps into a pattern
     * who hits undo means "that tap", not "that trend line".
     */
    fun undo(state: DrawingState): DrawingState = when {
        state.pending.isNotEmpty() -> state.copy(pending = state.pending.dropLast(1))
        state.drawings.isNotEmpty() -> state.copy(
            drawings = state.drawings.dropLast(1),
            selectedId = state.selectedId?.takeIf { it != state.drawings.last().id },
        )
        else -> state
    }

    /** Disarm without placing anything. The way out of a mode. */
    fun cancel(state: DrawingState): DrawingState =
        state.copy(tool = null, pending = emptyList())

    /**
     * Delete one drawing, unless it is locked.
     *
     * The lock is enforced here rather than at the button, so every path to deletion honours it —
     * the drawings list, a swipe, a keyboard shortcut, anything added later. A rule enforced only
     * where somebody remembered to check is a rule with a hole in it.
     */
    fun delete(state: DrawingState, id: Long): DrawingState {
        if (state.drawings.any { it.id == id && it.locked }) return state
        return state.copy(
            drawings = state.drawings.filterNot { it.id == id },
            selectedId = state.selectedId?.takeIf { it != id },
        )
    }

    /** Lock or unlock one drawing. See [Drawing.locked]. */
    fun setLocked(state: DrawingState, id: Long, locked: Boolean): DrawingState = state.copy(
        drawings = state.drawings.map { if (it.id == id) it.copy(locked = locked) else it },
    )

    fun clear(state: DrawingState): DrawingState =
        state.copy(drawings = emptyList(), pending = emptyList(), selectedId = null)

    /** Bring one drawing to the front, which is what makes it the one a tap on an overlap finds. */
    fun bringToFront(state: DrawingState, id: Long): DrawingState {
        val subject = state.drawings.firstOrNull { it.id == id } ?: return state
        return state.copy(drawings = state.drawings.filterNot { it.id == id } + subject)
    }

    fun recolour(state: DrawingState, id: Long, colour: Long): DrawingState = state.copy(
        drawings = state.drawings.map { if (it.id == id) it.copy(colour = colour) else it },
    )

    /**
     * Set what a text, callout, note or price label says.
     *
     * [Drawing.text] has existed since the drawing engine was written and had **no way to be
     * set**: four of the annotation tools rendered the literal «یادداشت» forever, and a note
     * rendered nothing at all beside its dot. A note tool that cannot hold a note is a tool that
     * places a circle.
     *
     * Blank clears it rather than storing an empty string, so a reader who empties the box gets
     * the placeholder back instead of an invisible drawing they cannot find to delete.
     */
    fun setText(state: DrawingState, id: Long, text: String?): DrawingState {
        val cleaned = text?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT_LENGTH)
        return state.copy(
            drawings = state.drawings.map { if (it.id == id) it.copy(text = cleaned) else it },
        )
    }

    /**
     * Whether this tool holds text at all.
     *
     * Four of them do. Asking a trend line for a label would be offering a keyboard on a tool that
     * has nowhere to draw the answer.
     */
    fun holdsText(toolId: String): Boolean = toolId in TEXT_TOOLS

    private val TEXT_TOOLS = setOf("text", "callout", "pricelabel", "note")

    /**
     * As long as a label can be before it stops being a label.
     *
     * Eighty characters is about two lines at the chart's own text size on a phone. Past that a
     * reader is writing a journal entry on top of their prices, and the app has a journal.
     */
    const val MAX_TEXT_LENGTH = 80

    /**
     * Move one point of a placed drawing — the drag of a handle.
     *
     * Chart space in, chart space out. A handle dragged at one zoom and released at another lands
     * where the finger was, because nothing in between was ever a pixel.
     */
    fun movePoint(state: DrawingState, id: Long, index: Int, to: ChartPoint): DrawingState =
        state.copy(
            drawings = state.drawings.map { drawing ->
                // `locked` is checked here, in the transform, rather than in the gesture — so a
                // second call site added later cannot forget it.
                if (drawing.id != id || drawing.locked || index !in drawing.points.indices) {
                    drawing
                } else {
                    drawing.copy(points = drawing.points.toMutableList().also { it[index] = to })
                }
            },
        )

    /** Move a whole drawing by a delta in chart space. */
    fun moveBy(state: DrawingState, id: Long, deltaTime: Long, deltaPrice: Double): DrawingState =
        state.copy(
            drawings = state.drawings.map { drawing ->
                if (drawing.id != id || drawing.locked) {
                    drawing
                } else {
                    drawing.copy(
                        points = drawing.points.map {
                            ChartPoint(it.time + deltaTime, it.price + deltaPrice)
                        },
                    )
                }
            },
        )

    /**
     * The position tool's third point, added on commit rather than tapped.
     *
     * ### Why it is not a third tap
     *
     * A setup is entry and stop — those are the two prices a trader has a *reason* for. The target
     * is a consequence of them, and the two-to-one that this tool draws is the right first answer
     * often enough that asking for it as a third tap would tax every setup to serve the minority
     * that wants a different one.
     *
     * ### Why it is a real point and not a formula
     *
     * Because a formula cannot be dragged. The renderer computed the target as `entry + 2 × risk`
     * and the comment beside it said "a reader who wants a different multiple drags the target line
     * afterwards" — describing behaviour that did not exist, because there was no third handle to
     * drag. Storing it as a point makes the sentence true: `movePoint` already moves handles, so
     * the target became draggable the moment it became a point.
     *
     * A reader who drags it gets their own reward, and the label follows what they dragged rather
     * than continuing to claim 2R.
     */
    private fun withTarget(tool: DrawingTool, points: List<ChartPoint>): List<ChartPoint> {
        if (tool.id != POSITION_TOOL || points.size != 2) return points
        val entry = points[0].price
        val stop = points[1].price
        if (!entry.isFinite() || !stop.isFinite() || entry == stop) return points
        // The target sits at the stop's moment, so the three handles form a vertical column a
        // thumb can tell apart rather than three points at the same x.
        return points + ChartPoint(points[1].time, entry + DEFAULT_REWARD * (entry - stop))
    }

    /** The tool whose commit gains a target. */
    internal const val POSITION_TOOL = "longshort"

    /** The reward the position tool opens at, as a multiple of the risk. */
    internal const val DEFAULT_REWARD = 2.0

    /**
     * Snap a point to the nearest of a bar's four prices.
     *
     * The magnet, and it is a professional's feature rather than a convenience: a trend line meant
     * to touch two lows is wrong if it touches them to within a pixel, and a pixel at this zoom is
     * several dollars of gold.
     */
    fun snap(point: ChartPoint, series: CandleSeries): ChartPoint {
        if (series.isEmpty) return point
        var bestBar = 0
        var bestDistance = Long.MAX_VALUE
        for (index in 0 until series.size) {
            val distance = kotlin.math.abs(series.time[index] - point.time)
            if (distance < bestDistance) {
                bestDistance = distance
                bestBar = index
            }
        }
        val candidates = doubleArrayOf(
            series.open[bestBar],
            series.high[bestBar],
            series.low[bestBar],
            series.close[bestBar],
        )
        val price = candidates.minByOrNull { kotlin.math.abs(it - point.price) } ?: point.price
        return ChartPoint(series.time[bestBar], price)
    }

    private fun commit(state: DrawingState, tool: DrawingTool, points: List<ChartPoint>): DrawingState {
        val id = (state.drawings.maxOfOrNull { it.id } ?: 0L) + 1
        val drawing = Drawing(id = id, toolId = tool.id, points = withTarget(tool, points), colour = state.colour)
        return state.copy(
            drawings = state.drawings + drawing,
            pending = emptyList(),
            // Disarm after placing. A rail that stays armed draws a second trend line the moment
            // the reader taps the chart to look at something, which is the single most reported
            // complaint about every terminal that does it the other way.
            tool = null,
            selectedId = id,
        )
    }
}

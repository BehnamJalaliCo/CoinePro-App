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
    /**
     * Which price channel each pending tap bound to, aligned with [pending].
     *
     * Carried alongside rather than inside the point, because the channel is a property of *how the
     * point was placed* and not of where it is: a tap with the magnet off has no channel and must
     * stay exactly where the finger landed.
     */
    val pendingChannels: List<PriceChannel?> = emptyList(),
    val selectedId: Long? = null,
    /** The reader's chosen colour for the next drawing. */
    val colour: Long = Drawing.DEFAULT_DRAWING_COLOUR,
    /** How hard each tap is pulled onto a bar's open/high/low/close. See [MagnetMode]. */
    val magnetMode: MagnetMode = MagnetMode.OFF,
    /**
     * Whether the armed tool survives a completed drawing.
     *
     * Off by default, which is the behaviour every reader gets first and the safer of the two: a
     * rail that stays armed draws a second trend line the moment somebody taps the chart to look at
     * something. On, it is the mode a reader marking up twenty levels in a row actually wants, and
     * the arming indicator in [ActiveToolBar] is what stops it being a surprise.
     */
    val keepDrawing: Boolean = false,
    /**
     * Whether every drawing on the chart is locked at once.
     *
     * Kept as state as well as pushed onto each [Drawing.locked], so the rail can show the switch in
     * the position the reader left it. A drawing placed *after* the switch was thrown is locked on
     * arrival, which is what "lock all" means to somebody who threw it to stop nudging things.
     */
    val lockedAll: Boolean = false,
    /** Which layers of the chart are hidden. See [DrawingLayer]. */
    val hidden: Set<DrawingLayer> = emptySet(),
    /**
     * Everything selected, newest last. [selectedId] is the last of them.
     *
     * A set rather than a second single id, because the one operation multi-select exists for —
     * recolouring eight lines at once — has to apply to all of them and the drag handles have to
     * apply to one. Both readings come off this.
     */
    val selection: Set<Long> = emptySet(),
    /** What copy put aside, waiting for a paste. Empty until something is copied. */
    val clipboard: List<Drawing> = emptyList(),
    /**
     * Which OHLC channel each magnet-placed point bound to.
     *
     * The point the reader sees is a price, but what was *chosen* was "the low of that bar". Storing
     * the channel rather than the price is what makes a later data revision — a corrected bar, a
     * feed that resends the session — move the anchor with it instead of leaving a trend line
     * hanging a few ticks off the low it was drawn against.
     *
     * A drawing with no entry here was placed with the magnet off and is left exactly where it is,
     * which is also what a row saved by a version before this existed decodes to.
     */
    val bindings: Map<PointRef, PriceChannel> = emptyMap(),
    /**
     * Tool ids the reader pinned to the top of the rail.
     *
     * State rather than a rail-local `remember`, so it survives the sheet closing and can be
     * persisted with the rest of the chart. Ninety-one tools is past the point where scanning is a
     * reasonable ask, and a reader uses six of them.
     */
    val favourites: Set<String> = emptySet(),
    /**
     * The width the next drawing is placed at, in dp.
     *
     * Beside [colour] rather than only on the [Drawing], because the two together are what a saved
     * template applies: a reader who has settled on a 2dp amber trend line is choosing a style for
     * what they are *about* to draw, and a colour that carried over while the width did not would
     * apply half of their template.
     */
    val widthDp: Float = DEFAULT_WIDTH_DP,
    /**
     * Individual drawings the reader has switched off, by id.
     *
     * Separate from [hidden], which hides whole layers. Hiding one object is what the object tree
     * offers and the layer switches cannot: a reader comparing two of eight trend lines wants the
     * other six out of the way for a minute, and their only alternative today is deleting them.
     *
     * Ids rather than a flag on the [Drawing] for the same reason [bindings] is a map: hiding is
     * something done *to* a drawing by the view, not a property of the mark itself, and an id that
     * no longer matches anything is harmless — it costs a set entry and hides nothing.
     */
    val hiddenIds: Set<Long> = emptySet(),
    /**
     * Which of the rail's [ToolGroup.MODES] entries is on.
     *
     * The home those entries never had. [DrawingActions.arm] refuses anything in that group — a
     * mode places no points, so arming it as a tool would be arming a tool that cannot commit —
     * and for a long time "refuse" meant "discard": tapping «نشانگر نقطه‌ای» or «نشانگر پیکانی»
     * did nothing at all, and the eraser only worked because the feature layer kept a second
     * boolean of its own beside this state and passed it to the canvas by hand.
     *
     * One field instead of that. The eraser is [DrawingMode.ERASER] like everything else, and
     * [eraser] reads it, so a screen that wants to know cannot get a different answer from the
     * one the placement machine is acting on.
     */
    val mode: DrawingMode = DrawingMode.CURSOR,
    /**
     * Whether the magnet is being held on for this one placement.
     *
     * Item 38, and the thing readers of the web terminal single out about its magnet: the setting
     * they want is almost never a setting. Snapping to a low is what somebody wants for *this*
     * anchor, and having to visit the rail before and after is two taps around a one-tap decision.
     * Held down — a modifier key, a second finger on the canvas — the magnet is on; released, it is
     * off again, and [DrawingActions.commit] releases it too so a hold that outlives the placement
     * does not carry into the next one.
     */
    val momentaryMagnet: Boolean = false,
    /**
     * Whether the current drag is being constrained to an axis, a diagonal, a square or a circle.
     *
     * Item 48. Set while the reader holds the modifier and read by [DrawingActions.constrain],
     * which is the only thing that acts on it — the canvas asks, it does not decide.
     */
    val constrainAngle: Boolean = false,
    /**
     * The interval the chart is on, stamped onto whatever is placed next. See [Drawing.timeframe].
     *
     * On the state rather than passed to every placement call, because it is a property of the
     * chart and not of the gesture: every one of the six entry points that can commit a drawing
     * would otherwise have to carry it, and the one that was forgotten would silently write
     * unlabelled marks.
     */
    val timeframe: String? = null,
    /** The layout on screen, stamped onto whatever is placed next. See [Drawing.layoutId]. */
    val layoutId: String? = null,
    /**
     * How far the next drawing travels between layouts.
     *
     * A default the reader sets once rather than a question asked per mark, in the same spirit as
     * [colour] and [widthDp]: somebody who works in one layout never touches it, and somebody who
     * keeps three layouts of the same instrument sets it to [DrawingSync.GLOBAL] and stops thinking
     * about it.
     */
    val sync: DrawingSync = DrawingSync.LAYOUT,
) {
    /**
     * What the chart should render: the placed drawings plus the one being built, less what is
     * hidden.
     *
     * The in-progress one is included so the reader sees a five-point pattern take shape as they
     * tap it out. Without this an XABCD is four taps into nothing followed by a shape appearing.
     */
    val visible: List<Drawing>
        get() {
            val now = System.currentTimeMillis()
            val shown = drawings.filter { isShown(it) && !it.hasFaded(now) }
            return if (pending.isEmpty() || tool == null) {
                shown
            } else {
                shown + Drawing(
                    id = PREVIEW_ID,
                    toolId = tool.id,
                    points = pending,
                    colour = colour,
                    widthDp = widthDp,
                    complete = false,
                )
            }
        }

    /**
     * Whether the magnet is on at all.
     *
     * Kept as a property so the call sites that only need the yes/no — the gesture handler that
     * decides whether to snap a touch — read the same way they did when this was a boolean. It
     * answers for [effectiveMagnetMode] rather than for [magnetMode], so a held magnet is a magnet
     * everywhere and not only on the code path that happened to remember the hold.
     */
    val magnet: Boolean get() = effectiveMagnetMode != MagnetMode.OFF

    /**
     * The magnet that is actually in force, hold included.
     *
     * A held magnet on a chart whose magnet is already on leaves the reader's own choice alone: a
     * hold is «snap this one», not «snap this one harder», and silently promoting a weak magnet to
     * a strong one would drag a text label onto a wick in the one gesture where the reader was
     * being careful. From off, the hold is [MagnetMode.STRONG], because somebody reaching for a
     * modifier mid-placement is reaching for a low, not for a suggestion.
     */
    val effectiveMagnetMode: MagnetMode
        get() = when {
            !momentaryMagnet -> magnetMode
            magnetMode == MagnetMode.OFF -> MagnetMode.STRONG
            else -> magnetMode
        }

    /**
     * Whether the eraser is the mode in force.
     *
     * The read that replaces the feature layer's own boolean. Two sources for one fact is how the
     * canvas ends up erasing while the rail shows a trend line armed.
     */
    val eraser: Boolean get() = mode == DrawingMode.ERASER

    /** Whether marks placed now are temporary. See [DrawingMode.DEMONSTRATION]. */
    val demonstrating: Boolean get() = mode == DrawingMode.DEMONSTRATION

    /** Whether a layer is currently hidden. */
    fun isHidden(layer: DrawingLayer): Boolean = layer in hidden

    /** Whether one drawing has been switched off on its own, from the object tree. */
    fun isHidden(id: Long): Boolean = id in hiddenIds

    /**
     * Whether one drawing survives the layer filter and its own switch.
     *
     * A position tool belongs to the positions layer and to nothing else, which is why hiding
     * «رسم‌ها» leaves a trade setup on the chart: a reader hiding their annotations to look at the
     * price is not asking to lose sight of where their stop is.
     *
     * The per-object switch is checked here as well, so every route to the canvas honours it rather
     * than only the ones that remembered to filter.
     */
    private fun isShown(drawing: Drawing): Boolean {
        if (drawing.id in hiddenIds) return false
        val positional = drawing.toolId == DrawingActions.POSITION_TOOL
        val layer = if (positional) DrawingLayer.POSITIONS else DrawingLayer.DRAWINGS
        return layer !in hidden
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

        /**
         * The width a drawing is placed at before anybody chooses one.
         *
         * The same 1.6dp [Drawing] defaults to, named here because this is where it is now a
         * *choice* rather than a constructor default — a template that sets a width has to be able
         * to be cleared back to something, and "whatever the data class says" is not a value the
         * rail can offer.
         */
        const val DEFAULT_WIDTH_DP = 1.6f
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

    /**
     * Arm a tool, a mode, or pass null for the cursor. Clears anything half-placed.
     *
     * The [ToolGroup.MODES] half is the part that used to be missing. Those six entries place no
     * points, so they cannot be armed as tools, and this function used to answer that by dropping
     * them on the floor: «نشانگر نقطه‌ای» and «نشانگر پیکانی» were rail buttons that did nothing,
     * and the eraser worked only because the feature layer kept a private boolean beside this
     * state. Now a mode entry sets [DrawingState.mode] and a magnet entry advances the magnet,
     * which is the one mode whose state already had a home.
     *
     * Arming a real tool clears the mode back to the cursor, with one exception: demonstration mode
     * survives, because it says how long what you draw lasts rather than what a tap does, and a
     * reader who turned it on then picked the highlighter meant both.
     */
    fun arm(state: DrawingState, tool: DrawingTool?): DrawingState {
        val cleared = state.copy(
            pending = emptyList(),
            pendingChannels = emptyList(),
            selectedId = null,
            selection = emptySet(),
        )
        if (tool != null && tool.group == ToolGroup.MODES) {
            // The magnet is the one entry that must not disturb a placement in progress: a reader
            // three anchors into a pattern who reaches for it wants the fourth anchor snapped, not
            // the first three thrown away.
            if (tool.id == MAGNET_TOOL) return cycleMagnet(state)
            val mode = DrawingMode.of(tool.id) ?: return cleared.copy(tool = null)
            return cleared.copy(tool = null, mode = mode)
        }
        return cleared.copy(
            tool = tool,
            mode = if (state.mode.survivesArming) state.mode else DrawingMode.CURSOR,
        )
    }

    /**
     * Set the mode outright, without going through the rail.
     *
     * The entry point a keyboard shortcut or a toolbar button uses. [arm] is the reader's route;
     * this is for a screen that already knows which mode it wants.
     */
    fun setMode(state: DrawingState, mode: DrawingMode): DrawingState = state.copy(
        mode = mode,
        // A mode that changes what a tap *does* cannot coexist with a half-placed drawing: the
        // next tap would be read by the new mode and the anchors already down would be orphaned.
        tool = if (mode.survivesArming) state.tool else null,
        pending = if (mode.survivesArming) state.pending else emptyList(),
        pendingChannels = if (mode.survivesArming) state.pendingChannels else emptyList(),
    )

    /** The rail entry that advances the magnet rather than setting a mode. */
    private const val MAGNET_TOOL = "magnet"

    /**
     * A tap in chart space.
     *
     * With no tool armed this selects — [nearest] is whatever the caller's hit test found, which
     * this cannot compute because it has no viewport and therefore no pixels.
     */
    fun tap(
        state: DrawingState,
        point: ChartPoint,
        nearest: Long? = null,
        channel: PriceChannel? = null,
        /**
         * The viewport, where the caller has one.
         *
         * Only the directed tools read it, and only to decide which of four ways an arrow faces:
         * "up" is a screen direction, and a time delta and a price delta cannot be compared without
         * knowing how many pixels each is worth. With no viewport the answer falls back to the price
         * axis alone, which is up or down and is the honest half of the question.
         */
        view: ChartViewport? = null,
    ): DrawingState {
        val tool = state.tool ?: return select(state, nearest, additive = false)
        val points = state.pending + point
        val channels = state.pendingChannels + channel
        // A path, a polyline or a row of arrow marks has no tap count to reach — it ends when the
        // reader says so, with a double tap or a tap back on the first anchor — so every tap simply
        // extends it.
        if (isVariablePoint(tool.id)) {
            return state.copy(pending = points, pendingChannels = channels)
        }
        if (tool.points <= 0) return state
        if (points.size < tool.points) return state.copy(pending = points, pendingChannels = channels)
        return commit(state, tool, points, channels, view)
    }

    /**
     * The same tap, with the magnet applied and the channel it chose remembered.
     *
     * The entry point a chart with a series in hand should use. [tap] cannot snap on its own — it
     * has no bars — so a caller that snaps first and then taps loses which of the four prices the
     * point was pulled onto, and the binding this whole arrangement exists for never gets written.
     */
    fun tapSnapped(
        state: DrawingState,
        point: ChartPoint,
        series: CandleSeries,
        nearest: Long? = null,
        view: ChartViewport? = null,
    ): DrawingState {
        // The *effective* mode, not the stored one: a magnet held for this placement has to reach
        // the snap, and this is the call every tap goes through.
        val snapped = snap(point, series, state.effectiveMagnetMode)
        return tap(state, snapped.point, nearest, snapped.channel, view)
    }

    /**
     * Whether this tool is placed by an arbitrary number of taps rather than a fixed count.
     *
     * Two of them, and they are the two whose shape the reader is describing rather than
     * constructing: a path and a polyline are however many corners the thing being outlined has.
     * Everything else has a defining count — a channel is three points because a channel *is* three
     * points — and letting one of those run on would produce a shape the tool cannot render.
     */
    fun isVariablePoint(toolId: String): Boolean = toolId in VARIABLE_POINT_TOOLS

    /**
     * End a path or a polyline where it is: the double tap.
     *
     * Fewer than two anchors is a reader who armed the tool and changed their mind, and it disarms
     * rather than placing a single point nobody can see or select.
     */
    fun finish(state: DrawingState): DrawingState {
        val tool = state.tool ?: return state
        if (!isVariablePoint(tool.id)) return state
        if (state.pending.size < 2) return cancel(state)
        return commit(state, tool, state.pending, state.pendingChannels)
    }

    /**
     * Which way an arrow placed by these two points faces.
     *
     * Screen space when a [view] is available, and that is not a nicety: a drag of three bars and
     * two dollars is mostly sideways on one chart and mostly vertical on the next, and comparing
     * seconds against dollars answers neither. Without a viewport the price axis decides, which
     * gives up or down — the two a reader marking a bar almost always means.
     *
     * A drag that went nowhere reports [ArrowDirection.UP], the same as a plain tap: an arrow with
     * no direction is still an arrow, and it has to point somewhere.
     */
    fun directionOf(from: ChartPoint, to: ChartPoint, view: ChartViewport?): ArrowDirection {
        val risen = to.price >= from.price
        if (view == null) return if (risen) ArrowDirection.UP else ArrowDirection.DOWN
        val dx = view.xOfTime(to.time) - view.xOfTime(from.time)
        // Screen y grows downward, so a drag that raises the price has a negative dy. The arrow
        // points the way the finger went.
        val dy = view.yOf(to.price) - view.yOf(from.price)
        if (kotlin.math.abs(dx) <= kotlin.math.abs(dy)) {
            return if (dy <= 0f) ArrowDirection.UP else ArrowDirection.DOWN
        }
        return if (dx >= 0f) ArrowDirection.RIGHT else ArrowDirection.LEFT
    }

    /**
     * The tools whose second tap is a direction rather than an anchor.
     *
     * One of them today. It is a set rather than an `==` because the shape generalises — any
     * fixed-size marker that faces somewhere belongs here — and because `commit` has to ask the
     * question in one place or a second directed tool would silently store a second point.
     */
    private val DIRECTED_TOOLS = setOf("arrowdir")

    /**
     * End a polyline by tapping its first anchor again, which closes it.
     *
     * Closure is recorded by repeating the first anchor at the end rather than by a flag on the
     * drawing, so the renderer reads it off the points and [Drawing] needs no new field. It also
     * means a reader who drags that last handle away re-opens the shape, which is the behaviour
     * somebody who dragged it would expect.
     */
    fun closeShape(state: DrawingState): DrawingState {
        val tool = state.tool ?: return state
        if (!isVariablePoint(tool.id)) return state
        // A row of arrow marks is a variable-point tool that is not a ring: repeating its first
        // anchor would place a second mark on top of the first rather than closing anything, so a
        // tap back on the start simply ends it.
        if (tool.id !in RING_TOOLS) return finish(state)
        if (state.pending.size < 3) return finish(state)
        val points = state.pending + state.pending.first()
        val channels = state.pendingChannels + state.pendingChannels.firstOrNull()
        return commit(state, tool, points, channels)
    }

    /**
     * A drag that placed a two-point tool in one gesture.
     *
     * Only for two-point tools: a three-point channel cannot be dragged out because its third point
     * is not a corner of anything the first two describe.
     */
    fun drag(
        state: DrawingState,
        from: ChartPoint,
        to: ChartPoint,
        view: ChartViewport? = null,
    ): DrawingState {
        val tool = state.tool ?: return state
        if (tool.points != 2) return state
        return commit(state, tool, listOf(from, to), listOf(null, null), view)
    }

    /** A freehand stroke, already sampled. */
    fun stroke(state: DrawingState, points: List<ChartPoint>): DrawingState {
        val tool = state.tool ?: return state
        if (tool.points != 0 || isVariablePoint(tool.id) || points.size < 2) return state
        return commit(state, tool, points, points.map { null })
    }

    /**
     * Take back one step.
     *
     * The half-placed taps first, then the last finished drawing. A reader four taps into a pattern
     * who hits undo means "that tap", not "that trend line".
     */
    fun undo(state: DrawingState): DrawingState = when {
        state.pending.isNotEmpty() -> state.copy(
            pending = state.pending.dropLast(1),
            pendingChannels = state.pendingChannels.dropLast(1),
        )
        state.drawings.isNotEmpty() -> state.copy(
            drawings = state.drawings.dropLast(1),
            selectedId = state.selectedId?.takeIf { it != state.drawings.last().id },
        )
        else -> state
    }

    /** Disarm without placing anything. The way out of a mode. */
    fun cancel(state: DrawingState): DrawingState =
        state.copy(tool = null, pending = emptyList(), pendingChannels = emptyList())

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
            selection = state.selection - id,
            bindings = state.bindings.filterKeys { it.drawingId != id },
            // Dropped with the drawing, so a later drawing that reuses the id — ids count up from
            // the highest in the list — cannot be born invisible.
            hiddenIds = state.hiddenIds - id,
        )
    }

    /** Lock or unlock one drawing. See [Drawing.locked]. */
    fun setLocked(state: DrawingState, id: Long, locked: Boolean): DrawingState = state.copy(
        drawings = state.drawings.map { if (it.id == id) it.copy(locked = locked) else it },
    )

    fun clear(state: DrawingState): DrawingState = state.copy(
        drawings = emptyList(),
        pending = emptyList(),
        pendingChannels = emptyList(),
        selectedId = null,
        selection = emptySet(),
        bindings = emptyMap(),
        hiddenIds = emptySet(),
    )

    /** Bring one drawing to the front, which is what makes it the one a tap on an overlap finds. */
    fun bringToFront(state: DrawingState, id: Long): DrawingState =
        state.copy(drawings = ObjectTree.bringToFront(state.drawings, id))

    fun recolour(state: DrawingState, id: Long, colour: Long): DrawingState = state.copy(
        drawings = state.drawings.map { if (it.id == id) it.copy(colour = colour) else it },
    )

    /** Put one drawing behind everything else. The way out of "my note is covering my chart". */
    fun sendToBack(state: DrawingState, id: Long): DrawingState =
        state.copy(drawings = ObjectTree.sendToBack(state.drawings, id))

    /**
     * Move one drawing to a given place in the z-order — what a drag in the object tree commits.
     *
     * The list arithmetic lives in [ObjectTree.reorder] rather than here, because the tree needs it
     * without holding a whole [DrawingState] and two implementations of "restack without losing
     * anything" is one more than the number that can be kept correct.
     */
    fun reorder(state: DrawingState, id: Long, toIndex: Int): DrawingState =
        state.copy(drawings = ObjectTree.reorder(state.drawings, id, toIndex))

    /**
     * Hide or show one drawing, without deleting it.
     *
     * Hiding is not deleting, and on a chart somebody has spent an hour marking up the difference
     * is the whole point: a reader who wants two of their eight lines out of the way for a minute
     * is not asking to redraw them afterwards. A locked drawing hides like any other — the lock
     * guards the drawing's geometry, not the reader's view of it.
     */
    fun setObjectHidden(state: DrawingState, id: Long, hidden: Boolean): DrawingState = state.copy(
        hiddenIds = if (hidden) state.hiddenIds + id else state.hiddenIds - id,
    )

    /**
     * Bring every hidden drawing back.
     *
     * The escape hatch the object tree needs: somebody who hid twelve objects one at a time must
     * not have to remember which twelve, and a chart that looks empty with no single switch to
     * explain it is the failure this prevents.
     */
    fun showAllObjects(state: DrawingState): DrawingState = state.copy(hiddenIds = emptySet())

    /** The width the next drawing is placed at. Half of what applying a template means. */
    fun setWidth(state: DrawingState, widthDp: Float): DrawingState =
        state.copy(widthDp = widthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP))

    /**
     * Apply a colour and a width to one placed drawing — a template, dropped onto something that
     * already exists.
     *
     * Locked drawings are skipped, because restyling is an edit and the lock exists so that a
     * drawing cannot be edited by accident. The style is *not* adopted as the state's own: applying
     * a template to one old line is not a statement about what the reader wants to draw next.
     */
    fun restyle(state: DrawingState, id: Long, colour: Long, widthDp: Float): DrawingState {
        val width = widthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)
        return state.copy(
            drawings = state.drawings.map {
                if (it.id == id && !it.locked) it.copy(colour = colour, widthDp = width) else it
            },
        )
    }

    /**
     * The same, to everything selected at once.
     *
     * The reason multi-select is worth having: eight levels placed in the default gold become eight
     * in the reader's own template in one gesture rather than eight round trips through a sheet.
     * Unlike [restyle], this *does* adopt the style for the next drawing — a reader who has just
     * restyled their whole selection has said what they want their drawings to look like.
     */
    fun restyleSelection(state: DrawingState, colour: Long, widthDp: Float): DrawingState {
        val width = widthDp.coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)
        return state.copy(
            colour = colour,
            widthDp = width,
            drawings = state.drawings.map {
                if (it.id in state.selection && !it.locked) {
                    it.copy(colour = colour, widthDp = width)
                } else {
                    it
                }
            },
        )
    }

    /**
     * The words on one drawing, in their own colour — or back to following the line.
     *
     * Null is a real argument and not a way of saying «no change»: it puts the drawing back to
     * following [Drawing.colour], which is the only way out of a text colour a reader regrets. A
     * setter that treated null as a no-op would make the toolbar's «پیش‌فرض» button do nothing.
     *
     * Locked drawings are skipped, on the same reading [restyle] takes: colour is an edit.
     */
    fun setTextColour(state: DrawingState, id: Long, colour: Long?): DrawingState = state.copy(
        drawings = state.drawings.map {
            if (it.id == id && !it.locked) it.copy(textColour = colour) else it
        },
    )

    /** The wash inside one drawing, or null to follow the line. See [Drawing.fillColour]. */
    fun setFillColour(state: DrawingState, id: Long, colour: Long?): DrawingState = state.copy(
        drawings = state.drawings.map {
            if (it.id == id && !it.locked) it.copy(fillColour = colour) else it
        },
    )

    /**
     * Solid, dotted or dashed, on one drawing — see [Drawing.lineStyle].
     *
     * [LineStyleKind.SOLID] restores the tool's own default rather than forcing a solid line, which
     * is why the tools that are dashed by construction keep their dashes at this setting.
     */
    fun setLineStyle(state: DrawingState, id: Long, style: LineStyleKind): DrawingState = state.copy(
        drawings = state.drawings.map {
            if (it.id == id && !it.locked) it.copy(lineStyle = style) else it
        },
    )

    /**
     * The thinnest a drawing may be, in dp.
     *
     * Clamped rather than trusted, because a width arrives from a stored template and a row on disk
     * is not a number this build wrote: a zero renders as nothing the reader can find or select,
     * and a negative one is a stroke width the canvas refuses outright.
     */
    const val MIN_WIDTH_DP = 0.5f

    /** The thickest, for the same reason: past this a line hides the candles it was drawn on. */
    const val MAX_WIDTH_DP = 12f

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
     * Eleven of them do, and seven of those were unreachable until now. Every one of the seven
     * renders `drawing.text ?: DEFAULT` — a pin, a table, a comment bubble, a signpost, a price
     * note, an icon, an image frame — and none of them was in this set, so the keyboard never
     * opened and the placeholder was the label for the life of the app. A note tool that cannot
     * hold a note is a tool that places a circle; a signpost that always reads «تابلو» is worse,
     * because it looks deliberate.
     *
     * Asking a trend line for a label would still be offering a keyboard on a tool that has nowhere
     * to draw the answer, which is why this is a set and not a yes.
     */
    fun holdsText(toolId: String): Boolean = toolId in TEXT_TOOLS

    private val TEXT_TOOLS = setOf(
        "text",
        "callout",
        "pricelabel",
        "note",
        "pricenote",
        "pin",
        "tabledraw",
        "comment",
        "signpost",
        ICON_TOOL,
        IMAGE_TOOL,
    )

    /**
     * Whether this tool's text is chosen from [ICON_GLYPHS] rather than typed.
     *
     * The icon tool is the one entry whose «text» is not prose: it is one mark, and a free keyboard
     * on it produces an icon tool holding a sentence — which the renderer then draws at label size
     * inside a diamond meant for a single glyph.
     */
    fun holdsIcon(toolId: String): Boolean = toolId == ICON_TOOL

    /**
     * The marks the icon tool offers.
     *
     * A real set rather than a keyboard, and a small one: ten is the number that fits a phone's
     * width in two rows of five at a tappable size, and every one of them reads at the eight-point
     * size the chart draws labels in. They are text rather than drawables on purpose — an icon is
     * stored in [Drawing.text], so it persists, restyles and recolours through the paths that
     * already exist rather than needing a parallel field and a parallel codec.
     */
    val ICON_GLYPHS: List<String> = listOf("★", "●", "◆", "▲", "▼", "✚", "✖", "⚑", "❗", "◉")

    /** The glyph an icon carries before the reader picks one. */
    const val DEFAULT_ICON_GLYPH = "★"

    /**
     * A plain sentence about what a tool actually does, or null where the label is the whole truth.
     *
     * One tool needs it. The image tool draws a frame and a caption and **cannot draw a picture**:
     * nothing in the chart layer can open a file, and nothing above it hands one down. Left
     * unexplained that is a tool a reader arms expecting a photo and gets an empty rectangle from,
     * which reads as a bug rather than as a boundary. Saying so where the tool is armed is the
     * cheapest honest answer, and it costs nothing on the ninety tools that need no note.
     */
    fun toolNote(toolId: String): String? = when (toolId) {
        IMAGE_TOOL -> "این ابزار قاب و زیرنویس می‌کشد؛ بارگذاری فایل تصویر روی چارت پشتیبانی نمی‌شود."
        ICON_TOOL -> "یکی از نشانه‌های آماده را برای این ابزار انتخاب کن."
        else -> null
    }

    /** The tool whose text is one glyph out of [ICON_GLYPHS]. */
    const val ICON_TOOL = "icon"

    /** The tool that frames a picture it cannot load. See [toolNote]. */
    const val IMAGE_TOOL = "image"

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
    fun snap(point: ChartPoint, series: CandleSeries): ChartPoint =
        snap(point, series, MagnetMode.STRONG).point

    /**
     * The magnet, in both of its strengths, reporting which channel it bound to.
     *
     * [MagnetMode.STRONG] pulls every point onto the nearest of the bar's four prices however far
     * away the finger was, which is what somebody drawing a line between two lows wants and is
     * unusable for anything else — a text label placed in strong magnet lands on a wick.
     * [MagnetMode.WEAK] only pulls when the finger was already close, so the same reader can place
     * a level in open space without turning the magnet off and on again.
     *
     * "Close" is measured against the bar's own range rather than in dollars or in pixels. A fixed
     * price tolerance is a different fraction of a bar on gold and on a token worth four cents, and
     * a pixel tolerance is not available here — this function has no viewport and must not grow
     * one, or the magnet would stop being testable. On a bar with no range at all, weak snaps only
     * on an exact hit, which is the honest reading of "within nothing".
     *
     * The returned [MagnetSnap.channel] is null whenever the point was left alone, and that null is
     * meaningful: it is what tells [resnap] never to move this point.
     */
    fun snap(
        point: ChartPoint,
        series: CandleSeries,
        mode: MagnetMode,
        tolerance: Double = WEAK_TOLERANCE,
    ): MagnetSnap {
        if (mode == MagnetMode.OFF || series.isEmpty) return MagnetSnap(point, null)
        val bar = nearestBar(series, point.time)
        val channel = channelAt(series, bar, point.price)
        val price = priceOf(series, bar, channel)
        if (mode == MagnetMode.WEAK) {
            val reach = (series.high[bar] - series.low[bar]) * tolerance
            if (kotlin.math.abs(price - point.price) > reach) return MagnetSnap(point, null)
        }
        return MagnetSnap(ChartPoint(series.time[bar], price), channel)
    }

    /** Which of a bar's four prices a tapped price is nearest. Ties go to the close. */
    fun channelAt(series: CandleSeries, barIndex: Int, price: Double): PriceChannel {
        var best = PriceChannel.CLOSE
        var bestDistance = kotlin.math.abs(series.close[barIndex] - price)
        for (candidate in PriceChannel.entries) {
            val distance = kotlin.math.abs(priceOf(series, barIndex, candidate) - price)
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        return best
    }

    /** One of a bar's four prices, named. */
    fun priceOf(series: CandleSeries, barIndex: Int, channel: PriceChannel): Double = when (channel) {
        PriceChannel.OPEN -> series.open[barIndex]
        PriceChannel.HIGH -> series.high[barIndex]
        PriceChannel.LOW -> series.low[barIndex]
        PriceChannel.CLOSE -> series.close[barIndex]
    }

    /**
     * Pull every magnet-bound point back onto the channel it was bound to.
     *
     * Called after the series is replaced — a corrected bar, a refetched session, a switch of chart
     * type that rewrites the bars. A point bound to "the low of Tuesday" moves to whatever Tuesday's
     * low now says; a point placed with the magnet off has no binding and is not touched, because
     * the reader put it exactly where they meant it.
     *
     * A locked drawing is not moved either. A lock exists to stop a drawing changing, and a data
     * revision is a change like any other.
     */
    fun resnap(state: DrawingState, series: CandleSeries): DrawingState {
        if (series.isEmpty || state.bindings.isEmpty()) return state
        return state.copy(
            drawings = state.drawings.map { drawing ->
                if (drawing.locked) return@map drawing
                var moved = false
                val points = drawing.points.mapIndexed { index, point ->
                    val channel = state.bindings[PointRef(drawing.id, index)] ?: return@mapIndexed point
                    val bar = nearestBar(series, point.time)
                    val next = ChartPoint(series.time[bar], priceOf(series, bar, channel))
                    if (next != point) moved = true
                    next
                }
                if (moved) drawing.copy(points = points) else drawing
            },
        )
    }

    /** The channels one drawing's points are bound to, aligned with them — what a codec writes. */
    fun channelsOf(state: DrawingState, drawing: Drawing): List<PriceChannel?> =
        drawing.points.indices.map { state.bindings[PointRef(drawing.id, it)] }

    /**
     * Restore one drawing's bindings, as read back from storage.
     *
     * A shorter list than the drawing has points, or a list of nulls, is exactly what a row written
     * before channels existed decodes to, and it leaves the drawing unbound rather than rejected.
     */
    fun withChannels(state: DrawingState, drawingId: Long, channels: List<PriceChannel?>): DrawingState {
        val added = channels.withIndex().mapNotNull { (index, channel) ->
            channel?.let { PointRef(drawingId, index) to it }
        }
        return state.copy(bindings = state.bindings + added)
    }

    // ── modes and layers ──────────────────────────────────────────────────────────────

    /** Set the magnet outright. */
    fun setMagnet(state: DrawingState, mode: MagnetMode): DrawingState = state.copy(magnetMode = mode)

    /**
     * Off, weak, strong, off — one rail button rather than three.
     *
     * Three targets for a setting with three values is most of a row on a phone, and the two that
     * are not current are dead pixels most of the time. The button's own tint says which of the
     * three it is in, which is the part that has to be unambiguous.
     */
    fun cycleMagnet(state: DrawingState): DrawingState = state.copy(
        magnetMode = when (state.magnetMode) {
            MagnetMode.OFF -> MagnetMode.WEAK
            MagnetMode.WEAK -> MagnetMode.STRONG
            MagnetMode.STRONG -> MagnetMode.OFF
        },
    )

    /**
     * Hold the magnet on for this one placement — item 38.
     *
     * The gesture behind it belongs to the canvas: a second finger resting on the plot, or a
     * modifier key on a keyboard. What this owns is the latch, and the rule that it is a latch at
     * all — [commit] drops it, so a hold cannot survive the drawing it was held for.
     */
    fun holdMagnet(state: DrawingState): DrawingState =
        if (state.momentaryMagnet) state else state.copy(momentaryMagnet = true)

    /** Let a held magnet go without placing anything: the finger lifted, or the key came up. */
    fun releaseMagnet(state: DrawingState): DrawingState =
        if (state.momentaryMagnet) state.copy(momentaryMagnet = false) else state

    /**
     * Hold or release the angle constraint — item 48.
     *
     * Stored rather than passed per frame because the constraint has to survive the whole drag: the
     * modifier goes down once and every move event afterwards has to know about it, and a flag
     * threaded through the pointer callbacks would be a flag one of them forgot.
     */
    fun setConstrainAngle(state: DrawingState, held: Boolean): DrawingState =
        if (state.constrainAngle == held) state else state.copy(constrainAngle = held)

    /**
     * Constrain the moving end of a two-point drag — item 48.
     *
     * Pure geometry, and it has to be done in **pixels**: forty-five degrees is a screen angle, a
     * square is square on the glass, and a circle drawn in chart space is an ellipse whose
     * eccentricity changes with the zoom. So the pair is projected, constrained, and read back
     * through the same viewport, which is also why this takes one.
     *
     * Three families, and each gets what the reader means by "hold it straight":
     *
     * * A line snaps its angle to the nearest multiple of forty-five degrees, keeping the length
     *   the finger travelled. Horizontal, vertical and both diagonals, and nothing between.
     * * A box becomes a square — the shorter side wins, so the shape stays inside the drag rather
     *   than jumping past the finger.
     * * An ellipse becomes a circle, by the same rule.
     *
     * Anything else is returned untouched: a five-point pattern has no second point to constrain,
     * and quietly moving one would be worse than doing nothing.
     */
    fun constrain(
        toolId: String,
        from: ChartPoint,
        to: ChartPoint,
        view: ChartViewport,
    ): ChartPoint {
        val family = constraintOf(toolId) ?: return to
        val ax = view.xOfTime(from.time)
        val ay = view.yOf(from.price)
        val bx = view.xOfTime(to.time)
        val by = view.yOf(to.price)
        val dx = bx - ax
        val dy = by - ay
        val end = when (family) {
            Constraint.ANGLE -> {
                val length = kotlin.math.hypot(dx, dy)
                if (length == 0f) return to
                // Rounded to the nearest eighth turn, which is the four axes and the four
                // diagonals. `atan2` is measured from the positive x axis and screen y grows
                // downward, so no sign correction is needed: the same rotation is applied back.
                val step = (kotlin.math.PI / 4).toFloat()
                val angle = kotlin.math.round(kotlin.math.atan2(dy, dx) / step) * step
                Pair(ax + length * kotlin.math.cos(angle), ay + length * kotlin.math.sin(angle))
            }
            Constraint.SQUARE -> {
                val side = kotlin.math.min(kotlin.math.abs(dx), kotlin.math.abs(dy))
                if (side == 0f) return to
                Pair(ax + side * signOf(dx), ay + side * signOf(dy))
            }
        }
        return ChartPoint(view.timeAt(end.first), view.priceAt(end.second))
    }

    /** Which constraint a tool takes, or null for the ones the modifier leaves alone. */
    private fun constraintOf(toolId: String): Constraint? = when (toolId) {
        in ANGLE_CONSTRAINED -> Constraint.ANGLE
        in SQUARE_CONSTRAINED -> Constraint.SQUARE
        else -> null
    }

    /** What holding the modifier does to a drag. */
    private enum class Constraint { ANGLE, SQUARE }

    /** −1, 0 or 1, so a zero-length side does not become a negative one. */
    private fun signOf(value: Float): Float = if (value < 0f) -1f else 1f

    /**
     * The two-point tools whose drag is a *direction*, and so snap to eighths of a turn.
     *
     * The measure tools are in it as well as the line tools, because a date range held horizontal
     * and a price range held vertical are exactly what a reader holds the modifier to get.
     */
    private val ANGLE_CONSTRAINED = setOf(
        "trend",
        "ray",
        "extline",
        "arrow",
        "infoline",
        "angle",
        "ruler",
        "forecast",
        "pricerange",
        "daterange",
        "dprange",
    )

    /** The two-point tools whose drag is an *extent*, and so snap to a square or a circle. */
    private val SQUARE_CONSTRAINED = setOf("rect", "circle", "ellipse", "gannbox", "fibcircles")

    /** Keep the tool armed after a drawing completes, or let it fall back to the cursor. */
    fun setKeepDrawing(state: DrawingState, keep: Boolean): DrawingState = state.copy(keepDrawing = keep)

    /**
     * The interval the next drawing records. See [Drawing.timeframe].
     *
     * Blank is stored as nothing rather than as an empty string, so a mark placed before the chart
     * knew its interval carries "nothing said" rather than a tag that renders as an empty box.
     */
    fun setTimeframe(state: DrawingState, timeframe: String?): DrawingState =
        state.copy(timeframe = timeframe?.trim()?.takeIf { it.isNotEmpty() })

    /** The layout the next drawing belongs to. See [Drawing.layoutId]. */
    fun setLayout(state: DrawingState, layoutId: String?): DrawingState =
        state.copy(layoutId = layoutId?.takeIf { it.isNotBlank() })

    /** How far the next drawing travels. See [DrawingSync]. */
    fun setSyncDefault(state: DrawingState, sync: DrawingSync): DrawingState = state.copy(sync = sync)

    /**
     * Change one placed drawing's reach — items 51 and 188.
     *
     * Locked drawings are changed anyway, and that is deliberate: the lock guards a drawing's
     * *geometry* from a stray thumb, and where a mark is visible is not geometry. A reader who
     * locked a level so they would stop nudging it has not said they never want to see it on their
     * other layout.
     */
    fun setSync(state: DrawingState, id: Long, sync: DrawingSync): DrawingState = state.copy(
        drawings = state.drawings.map { if (it.id == id) it.copy(sync = sync) else it },
    )

    /**
     * The drawings that belong on screen under a given layout — items 51 and 188.
     *
     * [DrawingSync.GLOBAL] ignores the layout entirely, which is the whole request: a level on gold
     * is a fact about gold, not about the apparatus somebody happened to be looking through when
     * they drew it. The other two are shown under the layout they were placed on, including the
     * plain working chart, which is what a null [layoutId] on both sides means.
     */
    fun syncedInto(drawings: List<Drawing>, layoutId: String?): List<Drawing> = drawings.filter {
        it.sync == DrawingSync.GLOBAL || it.layoutId == layoutId
    }

    /** The drawings a layout should be saved with. See [DrawingSync.travels]. */
    fun savedWithLayout(drawings: List<Drawing>, layoutId: String?): List<Drawing> =
        syncedInto(drawings, layoutId).filter { it.sync.travels }

    /**
     * How wide one regression channel's rails sit, in standard deviations — item 8.
     *
     * Clamped rather than trusted. Zero collapses the three lines onto each other and reads as a
     * broken tool; past five the rails leave the plot and the reader is looking at a trend line
     * with two invisible friends. A locked drawing is skipped, the same as every other restyle.
     */
    fun setDeviations(state: DrawingState, id: Long, deviations: Double): DrawingState {
        val value = deviations.coerceIn(MIN_DEVIATIONS, MAX_DEVIATIONS)
        return state.copy(
            drawings = state.drawings.map {
                if (it.id == id && !it.locked) it.copy(deviations = value) else it
            },
        )
    }

    /** The narrowest a regression channel may be: below this the three lines are one line. */
    const val MIN_DEVIATIONS = 0.25

    /** The widest: past this the rails are off the plot and say nothing. */
    const val MAX_DEVIATIONS = 5.0

    /**
     * Drop the demonstration marks whose time is up — item 41.
     *
     * The model-level reaper, separate from the fade the renderer applies. Both read the same
     * [Drawing.fadesAtMillis] against the same clock, so they cannot disagree about whether a mark
     * is gone; what this adds is that the mark leaves the *list*, which is what stops a session of
     * pointing at things filling the reader's saved drawings with invisible rows.
     *
     * Returns the state unchanged when nothing expired, so a caller can run it on a timer without
     * pushing a new state — and a new state on this path is a write to disk.
     */
    fun expire(state: DrawingState, nowMillis: Long): DrawingState {
        val gone = state.drawings.filter { it.hasFaded(nowMillis) }.map { it.id }.toSet()
        if (gone.isEmpty()) return state
        val alive = state.drawings.filterNot { it.id in gone }
        return state.copy(
            drawings = alive,
            selectedId = state.selectedId?.takeUnless { it in gone },
            selection = state.selection - gone,
            bindings = state.bindings.filterKeys { it.drawingId !in gone },
            hiddenIds = state.hiddenIds - gone,
        )
    }

    /**
     * How opaque a demonstration mark is right now — item 41.
     *
     * One over its life, then a straight ramp to nothing over the last [DEMONSTRATION_FADE_MS], and
     * exactly zero once its moment has passed. Finite and state-driven by construction: there is no
     * animation to run and nothing to stop, so a device with animations turned off gets the same
     * marks at the same opacities and simply sees fewer intermediate frames.
     *
     * A permanent drawing is 1 and never asks the clock.
     */
    fun fadeAlpha(drawing: Drawing, nowMillis: Long): Float {
        val fadesAt = drawing.fadesAtMillis ?: return 1f
        val left = fadesAt - nowMillis
        if (left <= 0L) return 0f
        if (left >= DEMONSTRATION_FADE_MS) return 1f
        return (left.toDouble() / DEMONSTRATION_FADE_MS).toFloat()
    }

    /**
     * How long a demonstration mark lives, in milliseconds.
     *
     * Eight seconds, of which the last three are the fade. Long enough to say «this level here»
     * out loud and be understood, short enough that the chart is clean again before the reader has
     * to think about tidying it — which is the whole point of a mode that draws things that leave.
     */
    const val DEMONSTRATION_LIFETIME_MS = 8_000L

    /** The tail of that life spent fading out. See [fadeAlpha]. */
    const val DEMONSTRATION_FADE_MS = 3_000L

    /**
     * Lock or unlock every drawing at once, and remember which way the switch is.
     *
     * The switch is remembered as well as applied because it also governs what happens to the *next*
     * drawing: a reader who locked everything to stop nudging lines does not want the line they draw
     * a moment later to be the one loose object on the chart.
     */
    fun setLockAll(state: DrawingState, locked: Boolean): DrawingState = state.copy(
        lockedAll = locked,
        drawings = state.drawings.map { it.copy(locked = locked) },
    )

    /** Hide or show one layer. */
    fun setHidden(state: DrawingState, layer: DrawingLayer, hidden: Boolean): DrawingState = state.copy(
        hidden = if (hidden) state.hidden + layer else state.hidden - layer,
    )

    /** Hide or show every layer at once — the «همه» entry beside the three. */
    fun setAllHidden(state: DrawingState, hidden: Boolean): DrawingState = state.copy(
        hidden = if (hidden) DrawingLayer.entries.toSet() else emptySet(),
    )

    /** Pin a tool to the rail's favourites row, or take it back out. */
    fun toggleFavourite(state: DrawingState, toolId: String): DrawingState = state.copy(
        favourites = if (toolId in state.favourites) state.favourites - toolId else state.favourites + toolId,
    )

    // ── selection, and what can be done to one ────────────────────────────────────────

    /**
     * Select a drawing, or add it to what is already selected.
     *
     * [selectedId] follows the last thing touched rather than the first, because that is the one the
     * handles belong to and the one a subsequent drag will move.
     */
    fun select(state: DrawingState, id: Long?, additive: Boolean = false): DrawingState {
        if (id == null) return state.copy(selectedId = null, selection = emptySet())
        val selection = if (additive) state.selection + id else setOf(id)
        return state.copy(selectedId = id, selection = selection)
    }

    /** Drop the selection without touching anything in it. */
    fun clearSelection(state: DrawingState): DrawingState =
        state.copy(selectedId = null, selection = emptySet())

    /**
     * Recolour everything selected in one go.
     *
     * The reason multi-select is worth having at all: a reader who has marked eight levels in the
     * default gold and wants them red is otherwise doing eight round trips through a colour sheet.
     * A locked drawing is skipped rather than silently recoloured — colour is an edit.
     */
    fun recolourSelection(state: DrawingState, colour: Long): DrawingState = state.copy(
        colour = colour,
        drawings = state.drawings.map {
            if (it.id in state.selection && !it.locked) it.copy(colour = colour) else it
        },
    )

    /** Put the selection on the clipboard. Nothing selected leaves the clipboard as it was. */
    fun copySelection(state: DrawingState): DrawingState {
        val chosen = state.drawings.filter { it.id in state.selection }
        if (chosen.isEmpty()) return state
        return state.copy(clipboard = chosen)
    }

    /**
     * Paste the clipboard, offset so the copies are not hidden underneath their originals.
     *
     * The offset is the caller's, in chart space, because "a little to the right" is a number of
     * bars at one zoom and a number of pixels at another and only the chart knows which. Pasting
     * with no offset at all is allowed and is what a paste onto a *different* symbol wants.
     */
    fun paste(state: DrawingState, deltaTime: Long = 0L, deltaPrice: Double = 0.0): DrawingState {
        if (state.clipboard.isEmpty()) return state
        var nextId = (state.drawings.maxOfOrNull { it.id } ?: 0L) + 1
        val pasted = state.clipboard.map { source ->
            val copy = source.copy(
                id = nextId,
                points = source.points.map { ChartPoint(it.time + deltaTime, it.price + deltaPrice) },
                locked = state.lockedAll,
            )
            nextId++
            copy
        }
        return state.copy(
            drawings = state.drawings + pasted,
            selectedId = pasted.last().id,
            selection = pasted.map { it.id }.toSet(),
        )
    }

    /** Copy one drawing and paste it in a single step — the long-press "duplicate". */
    fun clone(state: DrawingState, id: Long, deltaTime: Long = 0L, deltaPrice: Double = 0.0): DrawingState {
        val source = state.drawings.firstOrNull { it.id == id } ?: return state
        return paste(state.copy(clipboard = listOf(source)), deltaTime, deltaPrice)
            .copy(clipboard = state.clipboard)
    }

    // ── the eraser ────────────────────────────────────────────────────────────────────

    /** The eraser in its whole-object mode: the same rules as [delete], including the lock. */
    fun erase(state: DrawingState, id: Long): DrawingState = delete(state, id)

    /**
     * The eraser in its partial mode: take out the one leg under the finger.
     *
     * A path, a polyline or a brush stroke is a chain of legs, and a reader who overshot wants the
     * overshoot gone, not the whole stroke. Removing leg *n* splits the chain in two — everything up
     * to anchor *n*, and everything from anchor *n+1* — and each half survives only if it is still
     * two anchors long, so erasing the first leg of a three-point path leaves one line rather than a
     * line and an orphaned dot.
     *
     * Anything that is not a chain has no legs to take out and is deleted whole, which is what the
     * eraser means everywhere else. A locked drawing is refused, the same as every other edit.
     *
     * The pieces keep the original's place in the z-order rather than being appended, so erasing a
     * leg does not quietly bring a stroke to the front of everything drawn after it.
     */
    fun erasePartial(state: DrawingState, id: Long, segmentIndex: Int): DrawingState {
        val index = state.drawings.indexOfFirst { it.id == id }
        if (index < 0) return state
        val source = state.drawings[index]
        if (source.locked) return state
        if (source.toolId !in CHAIN_TOOLS) return delete(state, id)
        if (segmentIndex !in 0 until source.points.size - 1) return state
        val head = source.points.take(segmentIndex + 1)
        val tail = source.points.drop(segmentIndex + 1)
        var nextId = (state.drawings.maxOfOrNull { it.id } ?: 0L) + 1
        val pieces = listOf(head, tail).filter { it.size >= 2 }.map { points ->
            val piece = source.copy(id = nextId, points = points)
            nextId++
            piece
        }
        val drawings = state.drawings.toMutableList()
        drawings.removeAt(index)
        drawings.addAll(index, pieces)
        return state.copy(
            drawings = drawings,
            selectedId = pieces.firstOrNull()?.id,
            selection = pieces.map { it.id }.toSet(),
            bindings = state.bindings.filterKeys { it.drawingId != id },
        )
    }

    /**
     * Which leg of a chain a tap landed on, or −1.
     *
     * Screen space, because a tolerance is a finger's width — the same reasoning [DrawingHitTest]
     * gives — and it delegates to that class's own segment distance so the eraser and the selector
     * cannot disagree about which leg is under the finger.
     */
    fun segmentAt(drawing: Drawing, x: Float, y: Float, view: ChartViewport, tolerancePx: Float): Int {
        val screen = drawing.points.map { view.xOfTime(it.time) to view.yOf(it.price) }
        var best = -1
        var bestDistance = tolerancePx
        for (index in 0 until screen.size - 1) {
            val (ax, ay) = screen[index]
            val (bx, by) = screen[index + 1]
            val distance = DrawingHitTest.distanceToSegment(x, y, ax, ay, bx, by)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    // ── internals ─────────────────────────────────────────────────────────────────────

    /** The bar nearest a moment. Linear, because a drawing is placed at human speed. */
    private fun nearestBar(series: CandleSeries, time: Long): Int {
        var bestBar = 0
        var bestDistance = Long.MAX_VALUE
        for (index in 0 until series.size) {
            val distance = kotlin.math.abs(series.time[index] - time)
            if (distance < bestDistance) {
                bestDistance = distance
                bestBar = index
            }
        }
        return bestBar
    }

    private fun commit(
        state: DrawingState,
        tool: DrawingTool,
        points: List<ChartPoint>,
        channels: List<PriceChannel?>,
        view: ChartViewport? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): DrawingState {
        val id = (state.drawings.maxOfOrNull { it.id } ?: 0L) + 1
        val directed = tool.id in DIRECTED_TOOLS && points.size >= 2
        val placed = if (directed) listOf(points[0]) else withTarget(tool, points)
        val drawing = Drawing(
            id = id,
            toolId = tool.id,
            points = placed,
            colour = state.colour,
            widthDp = state.widthDp,
            locked = state.lockedAll,
            direction = if (directed) directionOf(points[0], points[1], view) else ArrowDirection.UP,
            timeframe = state.timeframe,
            sync = state.sync,
            layoutId = state.layoutId,
            fadesAtMillis = if (state.demonstrating) nowMillis + DEMONSTRATION_LIFETIME_MS else null,
        )
        val bound = channels.take(placed.size).withIndex().mapNotNull { (index, channel) ->
            channel?.let { PointRef(id, index) to it }
        }
        return state.copy(
            drawings = state.drawings + drawing,
            pending = emptyList(),
            pendingChannels = emptyList(),
            // A held magnet is held for *one* placement. Releasing it here rather than waiting for
            // the finger to lift is what makes it momentary: a modifier that stayed latched because
            // nothing cleared it is a magnet the reader turned on by accident and cannot find the
            // switch for.
            momentaryMagnet = false,
            // Disarm after placing, unless the reader asked for the opposite. A rail that stays
            // armed by default draws a second trend line the moment they tap the chart to look at
            // something, which is the single most reported complaint about every terminal that does
            // it the other way — and keeping it armed is exactly what somebody marking twenty levels
            // in a row wants, which is why it is a latch and not a decision made here.
            tool = if (state.keepDrawing) tool else null,
            selectedId = id,
            selection = setOf(id),
            bindings = state.bindings + bound,
        )
    }

    /**
     * The tools a reader ends by saying so rather than by running out of taps.
     *
     * `arrowmarks` joined them because it had no working count at all: the registry said zero
     * points, `tap` refuses a zero-point tool outright, and the renderer's geometry needs at least
     * two anchors before it returns a single mark — so the tool armed, took taps and placed
     * nothing, for every reader, forever. A row of marks is exactly the shape this set describes:
     * however many bars the reader wants to flag.
     */
    private val VARIABLE_POINT_TOOLS = setOf("path", "polyline", "arrowmarks")

    /** The variable-point tools that can be *closed* into a ring by tapping the first anchor. */
    private val RING_TOOLS = setOf("path", "polyline")

    /** The tools whose points are a chain, and so can have one leg erased out of the middle. */
    private val CHAIN_TOOLS = setOf("path", "polyline", "brush", "highlighter")

    /**
     * How close a weak magnet has to be, as a fraction of the bar's own high-low range.
     *
     * A quarter. Half would capture the whole bar — every tap inside the range is within half a
     * range of one of the four prices — and a tenth is close enough that the reader cannot tell it
     * from the magnet being off.
     */
    const val WEAK_TOLERANCE = 0.25
}

/**
 * Whether a mark's moment has passed.
 *
 * A permanent drawing never has one, which is why this is written as "has a deadline and it is
 * behind us" rather than as a comparison against a default: a default deadline would make every
 * ordinary trend line an expiry question.
 */
fun Drawing.hasFaded(nowMillis: Long): Boolean {
    val fadesAt = fadesAtMillis ?: return false
    return nowMillis >= fadesAt
}

/**
 * What the rail's [ToolGroup.MODES] entries actually set.
 *
 * Those six entries were the rail's oldest hole. They are not tools — none of them places a point —
 * so `DrawingActions.arm` refused them, and refusing meant discarding: two of them did nothing at
 * all when tapped, one worked only through a private boolean the feature layer kept beside the
 * state, and the sixth did not exist. This is the field they set instead, and folding the eraser
 * into it is the point: one source for "what does a tap do right now" rather than two that can
 * disagree.
 *
 * The magnet is deliberately **not** here. It has three values rather than two and already had a
 * home in [DrawingState.magnetMode]; the rail's magnet entry advances that instead.
 */
enum class DrawingMode(
    /** The `DrawingTools` id that selects this mode, so the rail and this enum cannot drift. */
    val toolId: String,
    /**
     * Whether this mode outlives arming a drawing tool.
     *
     * True for exactly one of them. Demonstration mode says *how long what you draw lasts*, which
     * is a question about the mark and not about the tap, so arming the highlighter to demonstrate
     * with has to keep it. The other three change what a tap means, and a tap cannot mean two
     * things — arming a trend line while the eraser is on would be a reader who cannot tell whether
     * the next tap draws or deletes.
     */
    val survivesArming: Boolean = false,
) {
    /** The plain crosshair. What every chart starts in and what «بستن» returns to. */
    CURSOR("cursor"),

    /** Tap to select rather than to place. The way to pick a mark up without disarming a tool. */
    SELECT("select"),

    /** An arrow pointer instead of a crosshair, for a reader who finds the full-width lines busy. */
    ARROW_CURSOR("arrowcursor"),

    /** A dot pointer: the smallest of the three, for placing anchors on a crowded chart. */
    DOT("dot"),

    /**
     * A tap removes what is under it.
     *
     * The one mode that already worked, and it worked by being special-cased: the feature layer
     * carried `eraser: Boolean` beside this state and handed it to the canvas by hand, because
     * there was nowhere here to put it. There is now.
     */
    ERASER("eraser"),

    /**
     * Marks placed now fade out and remove themselves — item 41.
     *
     * For pointing at something while somebody is watching: a screen share, a lesson, a message
     * about a level. The alternative readers actually use is drawing a line and then remembering to
     * delete it, and the line they forget is the one that is still on the chart a week later
     * claiming something that was true on Tuesday.
     *
     * The fade is finite and computed from a deadline, not animated: see
     * [DrawingActions.fadeAlpha]. There is no loop to run and nothing to stop when a device has
     * animations turned off.
     */
    DEMONSTRATION(DrawingTools.DEMONSTRATION_TOOL, survivesArming = true),
    ;

    companion object {
        /** The mode a rail entry selects, or null if that id is not a mode. */
        fun of(toolId: String): DrawingMode? = entries.firstOrNull { it.toolId == toolId }
    }
}

/**
 * How hard the magnet pulls.
 *
 * Three states rather than a boolean, because the two useful behaviours are genuinely different
 * tools and readers of every terminal in this category ask for both. Strong is for construction —
 * a trend line between two lows must *touch* both lows — and weak is for annotation, where the
 * reader wants the help only when they are already aiming at a price.
 */
enum class MagnetMode { OFF, WEAK, STRONG }

/**
 * Which of a bar's four prices a magnet-placed point was bound to.
 *
 * The thing that is persisted, rather than the price it happened to be at when the reader tapped.
 * A price is a snapshot of a number that the feed may revise; "the low of that bar" is what the
 * reader actually chose, and it survives the revision.
 */
enum class PriceChannel {
    OPEN,
    HIGH,
    LOW,
    CLOSE,
    ;

    companion object {
        /**
         * A stored channel name, or null.
         *
         * Null for null, for a blank, and for anything unrecognised — which is precisely what a row
         * written before channels existed reads back as, and it must decode to "no channel" rather
         * than to a default that would start moving somebody's old trend line.
         */
        fun decode(text: String?): PriceChannel? = entries.firstOrNull { it.name == text }
    }
}

/** One point of one drawing, as a key. */
data class PointRef(val drawingId: Long, val index: Int)

/** What the magnet did: where the point ended up, and which channel it bound to if it moved. */
data class MagnetSnap(val point: ChartPoint, val channel: PriceChannel?)

/**
 * The layers a reader can hide without deleting anything.
 *
 * Hiding is not deleting and the difference matters on a chart somebody has spent time marking up:
 * a reader who wants to see the price for a moment is not asking to lose their work, and every
 * terminal that offered only "delete all" taught its readers not to draw.
 *
 * Indicators are in the list even though this file draws none of them. The switch belongs beside the
 * other two — a reader hiding "everything" means everything — and the chart reads the flag.
 */
enum class DrawingLayer { DRAWINGS, INDICATORS, POSITIONS }

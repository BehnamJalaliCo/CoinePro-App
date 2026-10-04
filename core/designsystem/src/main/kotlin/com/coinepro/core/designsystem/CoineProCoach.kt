package com.coinepro.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * **The coach** (5.21.0): what a reader can do that nothing on the screen says.
 *
 * The owner's brief, in one line: every place in the app that works by a gesture nobody can see —
 * a pinch on the price column, a hold on a row, a drag on the symbol — taught once, at the moment
 * the reader first reaches it, with the whole app dimmed, the control lit, a two-second animation
 * and one short sentence. Never twice, never a tour at launch, and all of it replayable from the
 * menu's «آموزش‌ها».
 *
 * TradingView teaches almost none of this, which is the point: the owner wants the one chart app
 * that does it well rather than the one that does it loudly. So the rules are written against
 * fatigue first:
 *
 * * **Just in time.** A tip is *requested* by the control it is about, when that control is on
 *   screen ([coachTarget], [CoachAnchor]); nothing is shown on a screen the reader has not reached.
 * * **One at a time, three a session.** After one closes the next waits [COOLDOWN_MS], and no more
 *   than [SESSION_CAP] are shown per launch — except a tip the reader is about to need this second
 *   ([CoachTip.urgent]), such as the drawing cursor the moment a tool is armed.
 * * **Once, for good.** A tip is put away the moment it is shown, in the same store as the screen
 *   coach-marks, so a reader who kills the app mid-tip does not get it again.
 * * **A way out of all of it.** «بلدم، دیگر نشان نده» puts the whole coach away ([OFF_KEY]).
 */
enum class CoachTip(
    /** Stable id, part of the persisted key. Lowercase, `TeachingStore`'s alphabet. */
    val id: String,
    val group: CoachGroup,
    /** The animation, or null for a tip that is one lit control and a sentence. */
    val scene: CoachScene?,
    @StringRes val title: Int,
    @StringRes val text: Int,
    /** The sentence for a mouse, where the gesture differs. Null when the touch one holds. */
    @StringRes val mouseText: Int? = null,
    /** A gesture a mouse does not have; never shown to a mouse. */
    val touchOnly: Boolean = false,
    /** Needed this second: bypasses the cooldown and the session cap. */
    val urgent: Boolean = false,
) {
    DRAW_CURSOR(
        "draw_cursor", CoachGroup.DRAWING, CoachScene.TRACKPAD,
        R.string.coach_draw_cursor_title, R.string.coach_draw_cursor_text, touchOnly = true, urgent = true,
    ),
    DRAW_TOOLBAR(
        "draw_toolbar", CoachGroup.DRAWING, CoachScene.TOOLBAR,
        R.string.coach_draw_toolbar_title, R.string.coach_draw_toolbar_text, R.string.coach_draw_toolbar_mouse,
    ),
    CHART_PINCH(
        "chart_pinch", CoachGroup.CHART, CoachScene.PINCH,
        R.string.coach_chart_pinch_title, R.string.coach_chart_pinch_text, R.string.coach_chart_pinch_mouse,
    ),
    CHART_AXIS(
        "chart_axis", CoachGroup.CHART, CoachScene.AXIS,
        R.string.coach_chart_axis_title, R.string.coach_chart_axis_text, R.string.coach_chart_axis_mouse,
    ),
    CHART_HOLD(
        "chart_hold", CoachGroup.CHART, CoachScene.HOLD,
        R.string.coach_chart_hold_title, R.string.coach_chart_hold_text, R.string.coach_chart_hold_mouse,
    ),
    CHART_DOUBLE(
        "chart_double", CoachGroup.CHART, CoachScene.DOUBLE_TAP,
        R.string.coach_chart_double_title, R.string.coach_chart_double_text, R.string.coach_chart_double_mouse,
    ),
    SYMBOL_WHEEL(
        "symbol_wheel", CoachGroup.CHART, CoachScene.WHEEL,
        R.string.coach_symbol_wheel_title, R.string.coach_symbol_wheel_text,
    ),
    BAND_TOOLS("band_tools", CoachGroup.CHART, null, R.string.coach_band_tools_title, R.string.coach_band_tools_text),
    DRAW_BAND("draw_band", CoachGroup.DRAWING, null, R.string.coach_draw_band_title, R.string.coach_draw_band_text),
    DRAW_HANDLE(
        "draw_handle", CoachGroup.DRAWING, CoachScene.HANDLE,
        R.string.coach_draw_handle_title, R.string.coach_draw_handle_text,
    ),
    SELECT_BAR("select_bar", CoachGroup.DRAWING, null, R.string.coach_select_bar_title, R.string.coach_select_bar_text),
    GUTTER_PLUS(
        "gutter_plus", CoachGroup.CHART, CoachScene.PLUS,
        R.string.coach_gutter_plus_title, R.string.coach_gutter_plus_text, R.string.coach_gutter_plus_mouse,
    ),
    ALERT_LINE(
        "alert_line", CoachGroup.CHART, CoachScene.ALERT_LINE,
        R.string.coach_alert_line_title, R.string.coach_alert_line_text,
    ),
    AXIS_MINIS("axis_minis", CoachGroup.CHART, null, R.string.coach_axis_minis_title, R.string.coach_axis_minis_text),
    LEGEND("legend", CoachGroup.CHART, null, R.string.coach_legend_title, R.string.coach_legend_text),
    CHART_EVENTS(
        "chart_events", CoachGroup.CHART, null,
        R.string.coach_chart_events_title, R.string.coach_chart_events_text, R.string.coach_chart_events_mouse,
    ),
    REALTIME("realtime", CoachGroup.CHART, null, R.string.coach_realtime_title, R.string.coach_realtime_text),
    REPLAY_BAR("replay_bar", CoachGroup.CHART, null, R.string.coach_replay_bar_title, R.string.coach_replay_bar_text),
    INTERVAL_HIDE(
        "interval_hide", CoachGroup.CHART, CoachScene.PILL_HIDE,
        R.string.coach_interval_hide_title, R.string.coach_interval_hide_text, R.string.coach_interval_hide_mouse,
    ),
    ROW_STAR(
        "row_star", CoachGroup.LISTS, CoachScene.SWIPE_STAR,
        R.string.coach_row_star_title, R.string.coach_row_star_text, touchOnly = true,
    ),
    ROW_HOLD(
        "row_hold", CoachGroup.LISTS, CoachScene.ROW_HOLD,
        R.string.coach_row_hold_title, R.string.coach_row_hold_text, R.string.coach_row_hold_mouse,
    ),
    ROW_REORDER(
        "row_reorder", CoachGroup.LISTS, CoachScene.REORDER,
        R.string.coach_row_reorder_title, R.string.coach_row_reorder_text,
    ),
    TREE_SWIPE(
        "tree_swipe", CoachGroup.DRAWING, CoachScene.TREE_SWIPE,
        R.string.coach_tree_swipe_title, R.string.coach_tree_swipe_text,
    ),
    ;

    /** The key the dismissal is stored under, beside the screen coach-marks' own. */
    val key: String get() = "coach.$id"
}

/** How the library groups the tips. */
enum class CoachGroup(@StringRes val title: Int) {
    CHART(R.string.coach_group_chart),
    DRAWING(R.string.coach_group_drawing),
    LISTS(R.string.coach_group_lists),
}

/** The key «بلدم، دیگر نشان نده» stores: the whole coach put away. */
const val OFF_KEY = "coach.off"

/**
 * Where tips ask to be shown, and which one is.
 *
 * One per app, made by [CoineProTeachingHost] and provided as [LocalCoachHost].
 */
@Stable
class CoachHost internal constructor() {
    /** Every tip whose control is on screen right now, with that control's bounds in root pixels. */
    internal val requests = mutableStateMapOf<CoachTip, Rect>()

    /** The tip on screen, spotlit. */
    var showing by mutableStateOf<CoachTip?>(null)
        internal set

    /** A tip played from the library: no spotlight, the card in the middle of the glass. */
    var preview by mutableStateOf<CoachTip?>(null)
        internal set

    internal var shownThisSession by mutableIntStateOf(0)
    internal var cooling by mutableStateOf(false)

    /** Whether anything of the coach is on screen, so the screen coach-marks stand aside. */
    val active: Boolean get() = showing != null || preview != null

    internal fun place(tip: CoachTip, bounds: Rect) {
        if (requests[tip] != bounds) requests[tip] = bounds
    }

    internal fun release(tip: CoachTip) {
        requests.remove(tip)
    }

    /** Plays one tip now, from the library. */
    fun play(tip: CoachTip) {
        preview = tip
    }

    /** A fresh start after «همه را دوباره نشان بده»: the session cap and the cooldown forgotten. */
    fun resetSession() {
        shownThisSession = 0
        cooling = false
    }
}

/**
 * Held while something covers the whole app — the welcome slides, the first-run questions — so no
 * tip comes for a control the reader cannot see. The app sets it; the coach only reads it.
 */
object CoachPause {
    var held by mutableStateOf(false)
}

/** The app's coach. Null outside [CoineProTeachingHost], and then nothing is coached. */
val LocalCoachHost = staticCompositionLocalOf<CoachHost?> { null }

/**
 * Whether this window is driven by a finger rather than a mouse. True on the phone; the web build
 * provides the answer of `(pointer: coarse)`, so a desktop browser is told «کلیک» rather than
 * «انگشت» and is never shown a swipe a mouse cannot make.
 */
val LocalCoachTouchFirst = staticCompositionLocalOf { true }

/**
 * Marks this control as the one [tip] is about, and asks for the tip while it is on screen.
 *
 * [enabled] is the *moment*: false until the context the tip explains exists — a drawing selected,
 * a list with rows in it — so the request is made when the reader can act on the answer.
 */
fun Modifier.coachTarget(tip: CoachTip, enabled: Boolean = true): Modifier = composed {
    val host = LocalCoachHost.current
    if (host == null || !enabled) return@composed Modifier
    DisposableEffect(host, tip) { onDispose { host.release(tip) } }
    Modifier.onGloballyPositioned { coordinates -> host.place(tip, coordinates.boundsInRoot()) }
}

/**
 * The same request with the bounds handed over, for a control drawn inside a canvas rather than
 * laid out as a node — the chart's plot, its price column, its legend.
 */
@Composable
fun CoachAnchor(tip: CoachTip, bounds: Rect?, enabled: Boolean = true) {
    val host = LocalCoachHost.current ?: return
    if (!enabled || bounds == null) return
    LaunchedEffect(host, tip, bounds) { host.place(tip, bounds) }
    DisposableEffect(host, tip) { onDispose { host.release(tip) } }
}

/**
 * A tip inside a sheet, where the root spotlight cannot reach: a sheet is its own window, drawn
 * over the app's. The same card, laid out in the sheet's own column, once.
 */
@Composable
fun CoachInline(tip: CoachTip, modifier: Modifier = Modifier) {
    val host = LocalCoachHost.current
    val dismissals = LocalTeachingDismissals.current
    if (host == null || dismissals is SessionTeachingDismissals || !dismissals.ready) return
    val touch = LocalCoachTouchFirst.current
    if (tip.touchOnly && !touch) return
    // Read once on arrival: the card stays for this visit even though it is put away at once.
    var open by remember(tip) { mutableStateOf(tip.key !in dismissals.dismissed && OFF_KEY !in dismissals.dismissed) }
    LaunchedEffect(open) { if (open) dismissals.dismiss(tip.key) }
    if (!open) return
    CoachCard(
        tip = tip,
        touchFirst = touch,
        onDone = { open = false },
        onNever = {
            dismissals.dismiss(OFF_KEY)
            open = false
        },
        modifier = modifier.padding(vertical = CoineProSpacing.One),
        elevated = false,
    )
}

/**
 * The layer that draws the coach, over everything else in the app's window. Laid out by
 * [CoineProTeachingHost]; nothing else should call it.
 */
@Composable
internal fun CoineProCoachLayer(host: CoachHost) {
    val dismissals = LocalTeachingDismissals.current
    // A preview, a screenshot test or a screen hosted outside the app has no persisted store, and a
    // coach that appeared there would be in every golden.
    val live = dismissals !is SessionTeachingDismissals
    val touch = LocalCoachTouchFirst.current
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowHeight = constraints.maxHeight.toFloat()
        val candidate by remember(host, dismissals, touch, windowHeight) {
            derivedStateOf {
                if (!live || host.active || !dismissals.ready || CoachPause.held) return@derivedStateOf null
                val dismissed = dismissals.dismissed
                if (OFF_KEY in dismissed || CoineProSheetPresence.open > 0) return@derivedStateOf null
                CoachTip.entries.firstOrNull { tip ->
                    val bounds = host.requests[tip] ?: return@firstOrNull false
                    tip.key !in dismissed &&
                        (!tip.touchOnly || touch) &&
                        bounds.width >= MIN_TARGET_PX && bounds.height >= MIN_TARGET_PX &&
                        bounds.bottom > 0f && bounds.top < windowHeight &&
                        (tip.urgent || (!host.cooling && host.shownThisSession < SESSION_CAP))
                }
            }
        }
        LaunchedEffect(candidate) {
            val tip = candidate ?: return@LaunchedEffect
            // Settled first: a control that is still sliding in, or a page the reader is only
            // passing through, does not get a tip.
            delay(if (tip.urgent) URGENT_DELAY_MS else SETTLE_DELAY_MS)
            host.showing = tip
            host.shownThisSession++
            dismissals.dismiss(tip.key)
        }
        fun close() {
            host.showing = null
            host.preview = null
            host.cooling = true
            scope.launch {
                delay(COOLDOWN_MS)
                host.cooling = false
            }
        }
        val showing = host.showing
        val requested = showing?.let { host.requests[it] }
        // The last place the control was seen, so a frame in which its request is being renewed
        // does not blink the spotlight.
        var held by remember(showing) { mutableStateOf<Rect?>(null) }
        if (requested != null) held = requested
        val target = requested ?: held
        // The control went away under the tip — the page changed, a sheet opened — and a spotlight
        // on nothing is worse than none. Given a moment first: a control re-laid out is not gone.
        LaunchedEffect(showing, requested == null, CoineProSheetPresence.open) {
            if (showing == null) return@LaunchedEffect
            if (CoineProSheetPresence.open > 0) {
                close()
            } else if (requested == null) {
                delay(LOST_TARGET_MS)
                if (host.requests[showing] == null) close()
            }
        }
        if (showing != null && target != null) {
            CoachSpotlight(
                tip = showing,
                target = target,
                touchFirst = touch,
                onDone = ::close,
                onNever = {
                    dismissals.dismiss(OFF_KEY)
                    close()
                },
            )
        }
        host.preview?.let { tip ->
            CoachSpotlight(
                tip = tip,
                target = null,
                touchFirst = touch,
                onDone = ::close,
                onNever = null,
            )
        }
    }
}

/**
 * The dimmed app, the lit control and the card: the cinematic part.
 *
 * The light does not simply appear around the control. It closes in on it — an iris from the edge
 * of the glass down to the control's own corners, on a slow spring — while the dark comes up, so
 * the reader's eye is carried to the place before the card says anything about it. The edge of the
 * light is feathered rather than cut, and a pastel ring breathes around it for as long as it is up.
 */
@Composable
private fun CoachSpotlight(
    tip: CoachTip,
    target: Rect?,
    touchFirst: Boolean,
    onDone: () -> Unit,
    onNever: (() -> Unit)?,
) {
    val motion = continuousMotionAllowed()
    val fade = remember(tip) { Animatable(if (motion) 0f else 1f) }
    val iris = remember(tip) { Animatable(if (motion) 0f else 1f) }
    val rise = remember(tip) { Animatable(if (motion) 1f else 0f) }
    LaunchedEffect(tip) {
        launch { fade.animateTo(1f, tween(SCRIM_FADE_MS, easing = CoineProMotionSpecs.Enter)) }
        launch {
            delay(CARD_DELAY_MS)
            rise.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
        }
        iris.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = IRIS_STIFFNESS))
    }
    // Put away by itself after a few loops of the animation: long enough to watch it twice, short
    // enough that a reader who looked away is not still stopped by it when they look back.
    LaunchedEffect(tip) {
        delay(if (tip.scene != null) AUTO_CLOSE_SCENE_MS else AUTO_CLOSE_LINE_MS)
        onDone()
    }
    val breathe = if (motion) {
        val transition = rememberInfiniteTransition(label = "coach-ring")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(RING_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "coach-ring-phase",
        ).value
    } else {
        0f
    }
    val haptics = rememberCoineProHaptics()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Everything under the dark is out of reach for the moment; a tap anywhere is «got it».
            .pointerInput(tip) { detectTapGestures { onDone() } }
            .semantics { contentDescription = "coach-overlay" },
    ) {
        val density = LocalDensity.current
        var origin by remember { mutableStateOf(Offset.Zero) }
        val local = target?.translate(-origin.x, -origin.y)
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(SCRIM.copy(alpha = SCRIM_ALPHA * fade.value))
            if (local != null) {
                val far = (1f - iris.value) * size.maxDimension
                val hole = local.inflate(HOLE_PAD.toPx() + far)
                val corner = HOLE_RADIUS.toPx() + far * 0.5f
                val step = FEATHER_STEP.toPx()
                for (ring in FEATHER_STEPS downTo 1) {
                    val soft = hole.inflate(ring * step)
                    drawRoundRect(
                        color = Color.Black.copy(alpha = FEATHER_ALPHA),
                        topLeft = soft.topLeft,
                        size = soft.size,
                        cornerRadius = CornerRadius(corner + ring * step),
                        blendMode = BlendMode.DstOut,
                    )
                }
                drawRoundRect(
                    color = Color.Black,
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(corner),
                    blendMode = BlendMode.DstOut,
                )
            }
        }
        if (local != null) {
            // The ring: a pastel band turning slowly round the light, brightest as the iris lands.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val far = (1f - iris.value) * size.maxDimension
                val hole = local.inflate(HOLE_PAD.toPx() + far)
                val corner = HOLE_RADIUS.toPx() + far * 0.5f
                val glow = (0.7f + 0.3f * kotlin.math.sin(breathe * 2f * kotlin.math.PI.toFloat())) * fade.value
                // The band travels round the light: the palette sampled a little further on each
                // frame, rather than the frame itself turning.
                val brush = Brush.sweepGradient(ringStops(breathe), center = hole.center)
                // One soft halo and one clean line (5.21.1). Four stepped strokes read, on a
                // phone's price column, as a smeared double edge rather than as light.
                drawRoundRect(
                    brush = brush,
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = RING_HALO.toPx()),
                    alpha = glow * RING_HALO_ALPHA,
                )
                drawRoundRect(
                    brush = brush,
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = RING_LINE.toPx()),
                    alpha = glow,
                )
            }
        }
        // The card: under the light when there is room, over it when not, in the middle of the glass
        // for a tip played from the library.
        val cardWidth = with(density) { minOf(maxWidth - CoineProSpacing.Gutter * 2, CARD_MAX_WIDTH).toPx() }
        val gap = with(density) { CARD_GAP.toPx() }
        var measured by remember(tip) { mutableIntStateOf(0) }
        val estimate = if (measured > 0) {
            measured.toFloat()
        } else {
            with(density) { (if (tip.scene != null) CARD_SCENE_HEIGHT else CARD_LINE_HEIGHT).toPx() }
        }
        val hole = local?.inflate(with(density) { HOLE_PAD.toPx() })
        val top = when {
            hole == null -> (height - estimate) / 2f
            height - hole.bottom - gap >= estimate -> hole.bottom + gap
            hole.top - gap >= estimate -> hole.top - gap - estimate
            // A control that fills the glass — the chart's whole plot: the card sits over its foot.
            else -> (height - estimate - with(density) { CARD_FOOT.toPx() }).coerceAtLeast(gap)
        }.coerceIn(gap, (height - estimate - gap).coerceAtLeast(gap))
        val lift = with(density) { CARD_RISE.toPx() }
        CoachCard(
            tip = tip,
            touchFirst = touchFirst,
            onDone = {
                haptics.select()
                onDone()
            },
            onNever = onNever,
            modifier = Modifier
                .offset { IntOffset(((width - cardWidth) / 2f).roundToInt(), (top + rise.value * lift).roundToInt()) }
                .width(with(density) { cardWidth.toDp() })
                .onSizeChanged { measured = it.height }
                .graphicsLayer {
                    alpha = (1f - rise.value).coerceIn(0f, 1f)
                    val scale = 1f - rise.value * 0.06f
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/**
 * The card: the animation on its stage, a title, one sentence, «فهمیدم» and the way out.
 *
 * Pastel in both themes on purpose. Over the dark of the scrim a light card is the brightest thing
 * on the glass, which is where the eye should be; and the soft palette is the owner's — «پاستیلی».
 */
@Composable
fun CoachCard(
    tip: CoachTip,
    touchFirst: Boolean,
    onDone: () -> Unit,
    onNever: (() -> Unit)?,
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
) {
    val shape = RoundedCornerShape(CARD_RADIUS)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(if (elevated) Modifier.shadow(CARD_SHADOW, shape) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CARD_TOP, CARD_BOTTOM)))
            // The card itself swallows a tap, so reading it does not close it.
            .clickable(interaction, null) {}
            .padding(CARD_PADDING),
    ) {
        tip.scene?.let { scene ->
            CoachStage(
                scene = scene,
                touch = touchFirst || tip.mouseText == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STAGE_HEIGHT)
                    .clip(RoundedCornerShape(STAGE_RADIUS)),
            )
            Spacer(Modifier.height(CoineProSpacing.OneHalf))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(RING_COLOURS)),
            )
            Spacer(Modifier.size(CoineProSpacing.One))
            Text(
                text = stringResource(tip.title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = CARD_INK,
            )
        }
        Spacer(Modifier.height(CoineProSpacing.Half))
        Text(
            text = stringResource(if (!touchFirst && tip.mouseText != null) tip.mouseText else tip.text),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = CARD_PROSE,
        )
        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (onNever != null) {
                Text(
                    text = stringResource(R.string.coach_never),
                    style = MaterialTheme.typography.labelMedium,
                    color = CARD_MUTED,
                    modifier = Modifier
                        .clip(CoineProPillShape)
                        .clickable(onClick = onNever)
                        .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.One),
                )
            } else {
                Spacer(Modifier.size(1.dp))
            }
            Text(
                text = stringResource(R.string.coach_got_it),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .clip(CoineProPillShape)
                    .background(CARD_ACCENT)
                    .clickable(onClick = onDone)
                    .padding(horizontal = CoineProSpacing.Three, vertical = CoineProSpacing.One),
            )
        }
    }
}

/**
 * «آموزش‌ها» in the menu: every tip, playable on demand, and the way to have them all again.
 */
@Composable
fun CoachLibrary(modifier: Modifier = Modifier) {
    val host = LocalCoachHost.current
    val dismissals = LocalTeachingDismissals.current
    var replayed by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = CoineProSpacing.Gutter,
            vertical = CoineProSpacing.Two,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        item(key = "lead") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CARD_RADIUS))
                    .background(Brush.verticalGradient(listOf(CARD_TOP, CARD_BOTTOM)))
                    .padding(CoineProSpacing.Two),
            ) {
                Text(
                    text = stringResource(R.string.coach_library_lead),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = CARD_PROSE,
                )
                Spacer(Modifier.height(CoineProSpacing.OneHalf))
                Text(
                    text = stringResource(if (replayed) R.string.coach_library_replayed else R.string.coach_library_replay),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (replayed) CARD_ACCENT else Color.White,
                    modifier = Modifier
                        .clip(CoineProPillShape)
                        .background(if (replayed) CARD_ACCENT.copy(alpha = 0.14f) else CARD_ACCENT)
                        .clickable(enabled = !replayed) {
                            CoachTip.entries.forEach { dismissals.restore(it.key) }
                            TeachingSurface.entries.forEach { dismissals.restore(it.key) }
                            dismissals.restore(OFF_KEY)
                            host?.resetSession()
                            replayed = true
                        }
                        .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One)
                        .semantics { contentDescription = "coach-replay-all" },
                )
            }
        }
        CoachGroup.entries.forEach { group ->
            item(key = "group-" + group.name) {
                Text(
                    text = stringResource(group.title),
                    style = MaterialTheme.typography.labelLarge,
                    color = CoineProColors.TextSecondary,
                    modifier = Modifier.padding(top = CoineProSpacing.Two, bottom = CoineProSpacing.Half),
                )
            }
            items(CoachTip.entries.filter { it.group == group }, key = { it.id }) { tip ->
                CoachLibraryRow(tip = tip, onPlay = { host?.play(tip) })
            }
        }
    }
}

@Composable
private fun CoachLibraryRow(tip: CoachTip, onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(CoineProColors.SurfaceRaised)
            .clickable(onClick = onPlay)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(if (tip.scene != null) RING_COLOURS else LINE_COLOURS)),
            contentAlignment = Alignment.Center,
        ) {
            // A play mark for an animation, a dot for a sentence.
            Canvas(Modifier.size(14.dp)) {
                if (tip.scene != null) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.25f, size.height * 0.1f)
                        lineTo(size.width * 0.95f, size.height * 0.5f)
                        lineTo(size.width * 0.25f, size.height * 0.9f)
                        close()
                    }
                    drawPath(path, CARD_INK)
                } else {
                    drawCircle(CARD_INK, radius = size.minDimension * 0.28f)
                }
            }
        }
        Spacer(Modifier.size(CoineProSpacing.OneHalf))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(tip.title),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(if (tip.scene != null) R.string.coach_kind_animated else R.string.coach_kind_line),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextSecondary,
            )
        }
    }
}

/** At most this many tips a launch, the urgent ones aside. */
private const val SESSION_CAP = 3

/** The quiet after a tip before the next may come. */
private const val COOLDOWN_MS = 25_000L

/** How long a control may be missing before its tip is put away. */
private const val LOST_TARGET_MS = 900L

/** How long a control must stay on screen before its tip comes. */
private const val SETTLE_DELAY_MS = 1_400L
private const val URGENT_DELAY_MS = 450L

private const val AUTO_CLOSE_SCENE_MS = 11_000L
private const val AUTO_CLOSE_LINE_MS = 7_000L

private const val SCRIM_FADE_MS = 420
private const val CARD_DELAY_MS = 260L
private const val RING_PERIOD_MS = 3_600
private const val IRIS_STIFFNESS = 60f

private const val MIN_TARGET_PX = 8f
private const val SCRIM_ALPHA = 0.8f
private const val FEATHER_STEPS = 6
private const val FEATHER_ALPHA = 0.2f
private const val RING_HALO_ALPHA = 0.22f

private val HOLE_PAD = 8.dp
private val HOLE_RADIUS = 18.dp
private val FEATHER_STEP = 1.2.dp
private val RING_HALO = 6.dp
private val RING_LINE = 1.5.dp
private val CARD_MAX_WIDTH = 380.dp
private val CARD_GAP = 16.dp
private val CARD_RISE = 36.dp
private val CARD_FOOT = 96.dp
private val CARD_SCENE_HEIGHT = 340.dp
private val CARD_LINE_HEIGHT = 160.dp
private val CARD_RADIUS = 28.dp
private val CARD_SHADOW = 24.dp
private val CARD_PADDING = 16.dp
private val STAGE_HEIGHT = 180.dp
private val STAGE_RADIUS = 20.dp

/** The night the app goes into: a deep indigo rather than black, so the pastel reads warm. */
private val SCRIM = Color(0xFF0D0F22)

internal val CARD_TOP = Color(0xFFFFFFFF)
internal val CARD_BOTTOM = Color(0xFFF4F1FF)
internal val CARD_INK = Color(0xFF23264A)
internal val CARD_PROSE = Color(0xFF41456B)
internal val CARD_MUTED = Color(0xFF8186A6)
internal val CARD_ACCENT = Color(0xFF7479F2)

/** Lavender, sky, mint, peach and back: the ring and the play discs. */
internal val RING_COLOURS = listOf(
    Color(0xFFB9B3FF),
    Color(0xFFA9DCFF),
    Color(0xFFA8EDD0),
    Color(0xFFFFD3BE),
    Color(0xFFB9B3FF),
)
private val LINE_COLOURS = listOf(Color(0xFFFFE3A8), Color(0xFFFFCFD9))

/** The ring's palette, turned by [phase] of a revolution and sampled into twelve stops. */
private fun ringStops(phase: Float): List<Color> {
    val span = RING_COLOURS.size - 1
    return List(RING_SAMPLES + 1) { index ->
        val at = ((index.toFloat() / RING_SAMPLES - phase) % 1f + 1f) % 1f * span
        val from = at.toInt().coerceAtMost(span - 1)
        lerp(RING_COLOURS[from], RING_COLOURS[from + 1], at - from)
    }
}

private const val RING_SAMPLES = 12

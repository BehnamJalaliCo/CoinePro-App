package com.coinepro.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The coach's animations (5.21.0): fifteen small films, each one gesture.
 *
 * «پاستیلی و سینمایی» is the brief, and both words are carried by the same few devices:
 *
 * * **A pastel world.** A dawn-coloured stage, mint and rose candles, a lavender finger. Nothing in
 *   it is the app's own chrome, so the gesture reads as *the* gesture rather than as a screenshot
 *   the reader has to find their place in.
 * * **A camera.** Every film pushes in a few per cent over its loop, a band of light crosses the
 *   stage as it opens, two soft out-of-focus glows drift behind the chart, and a vignette holds the
 *   eye in the middle. The loop fades through the stage colour, so it never jumps.
 * * **Weight.** The finger has a shadow and presses in; what it lets go of springs and overshoots
 *   (`easeOutBack`) rather than stopping dead.
 *
 * Drawn rather than played from a file, so every film is sharp at any density, takes the app's own
 * typeface for its few labels, costs nothing to ship and can be changed in code review. Under a
 * reduced-motion setting each film is one held frame from its middle, which still shows the
 * gesture.
 */
enum class CoachScene {
    TRACKPAD,
    TOOLBAR,
    PINCH,
    AXIS,
    HOLD,
    DOUBLE_TAP,
    WHEEL,
    HANDLE,
    PLUS,
    ALERT_LINE,
    PILL_HIDE,
    SWIPE_STAR,
    ROW_HOLD,
    REORDER,
    TREE_SWIPE,
}

/** The stage: a film on a loop, or its held middle frame without motion. */
@Composable
internal fun CoachStage(scene: CoachScene, touch: Boolean, modifier: Modifier = Modifier) {
    val motion = continuousMotionAllowed()
    CoachFilmFrame(scene = scene, touch = touch, frame = null, modifier = modifier, motion = motion)
}

/**
 * One frame of a film, at [frame] of its loop — or the running film when [frame] is null. Public
 * for the render test that puts every film's key frames on a contact sheet.
 */
@Composable
fun CoachFilmFrame(
    scene: CoachScene,
    touch: Boolean,
    frame: Float?,
    modifier: Modifier = Modifier,
    motion: Boolean = frame == null,
) {
    val t = if (frame != null) {
        frame
    } else if (motion) {
        val transition = rememberInfiniteTransition(label = "coach-scene")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SCENE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "coach-scene-clock",
        ).value
    } else {
        STILL_FRAME
    }
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelSmall
    Canvas(modifier = modifier) {
        val film = Film(this, measurer, label, touch, size.height / STAGE_UNITS)
        drawBackdrop(t)
        val push = 1f + PUSH_IN * easeInOut(t)
        withTransform({ scale(push, push, center) }) {
            when (scene) {
                CoachScene.TRACKPAD -> film.trackpad(t)
                CoachScene.TOOLBAR -> film.toolbar(t)
                CoachScene.PINCH -> film.pinch(t)
                CoachScene.AXIS -> film.axis(t)
                CoachScene.HOLD -> film.hold(t)
                CoachScene.DOUBLE_TAP -> film.doubleTap(t)
                CoachScene.WHEEL -> film.wheel(t)
                CoachScene.HANDLE -> film.handle(t)
                CoachScene.PLUS -> film.plus(t)
                CoachScene.ALERT_LINE -> film.alertLine(t)
                CoachScene.PILL_HIDE -> film.pillHide(t)
                CoachScene.SWIPE_STAR -> film.swipeStar(t)
                CoachScene.ROW_HOLD -> film.rowHold(t)
                CoachScene.REORDER -> film.reorder(t)
                CoachScene.TREE_SWIPE -> film.treeSwipe(t)
            }
        }
        drawLightSweep(t)
        drawVignette()
        if (motion) {
            // Through the stage colour at both ends of the loop: a dissolve, never a cut.
            val shown = seg(t, 0f, LOOP_FADE) * (1f - seg(t, 1f - LOOP_FADE, 1f))
            drawRect(STAGE_MID, alpha = 1f - shown)
        }
    }
}

// ── The stage ───────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBackdrop(t: Float) {
    drawRect(Brush.verticalGradient(listOf(STAGE_TOP, STAGE_BOTTOM)))
    // Two out-of-focus glows drifting behind the action: the depth a lens gives a set.
    val drift = sin(t * 2f * PI.toFloat())
    val a = Offset(size.width * (0.18f + 0.03f * drift), size.height * 0.2f)
    val b = Offset(size.width * (0.84f - 0.03f * drift), size.height * 0.86f)
    drawCircle(Brush.radialGradient(listOf(BOKEH_LAVENDER, Color.Transparent), a, size.height * 0.7f), size.height * 0.7f, a)
    drawCircle(Brush.radialGradient(listOf(BOKEH_MINT, Color.Transparent), b, size.height * 0.6f), size.height * 0.6f, b)
}

private fun DrawScope.drawLightSweep(t: Float) {
    val sweep = seg(t, 0.02f, 0.3f)
    if (sweep <= 0f || sweep >= 1f) return
    val x = -size.width * 0.4f + sweep * size.width * 1.8f
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
            start = Offset(x - size.height * 0.6f, 0f),
            end = Offset(x + size.height * 0.2f, size.height),
        ),
    )
}

private fun DrawScope.drawVignette() {
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Transparent, VIGNETTE),
            center = center,
            radius = size.maxDimension * 0.62f,
        ),
    )
}

// ── The films ───────────────────────────────────────────────────────────────────────────────

/**
 * One film's drawing context: the canvas, the text measurer, and [u], the size of one design unit.
 * Every film is laid out on a stage [STAGE_UNITS] units tall and as wide as the card allows.
 */
private class Film(
    val scope: DrawScope,
    val measurer: TextMeasurer,
    val label: TextStyle,
    val touch: Boolean,
    val u: Float,
) {
    val w: Float get() = scope.size.width / u
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun r(left: Float, top: Float, right: Float, bottom: Float) = Rect(left * u, top * u, right * u, bottom * u)

    /** The usual plot: the stage less a price column on the right. */
    val plot: Rect get() = r(10f, 12f, w - 44f, 168f)
    val gutter: Rect get() = r(w - 42f, 12f, w - 6f, 168f)

    fun text(value: String, at: Offset, colour: Color, sizeUnits: Float = 9f, bold: Boolean = false, centred: Boolean = true, alpha: Float = 1f) {
        if (alpha <= 0f) return
        with(scope) {
            val style = label.copy(
                fontSize = (sizeUnits * u).toSp(),
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
                color = colour.copy(alpha = colour.alpha * alpha),
            )
            val layout = measurer.measure(value, style)
            val origin = if (centred) at - Offset(layout.size.width / 2f, layout.size.height / 2f) else at
            drawText(layout, topLeft = origin)
        }
    }

    // ── Pieces ────────────────────────────────────────────────────────────────────────────

    fun grid(area: Rect, alpha: Float = 1f) = with(scope) {
        var y = area.top + 24f * u
        while (y < area.bottom) {
            drawLine(GRID, Offset(area.left, y), Offset(area.right, y), 1f * u * 0.6f, alpha = alpha)
            y += 30f * u
        }
        var x = area.left + 30f * u
        while (x < area.right) {
            drawLine(GRID, Offset(x, area.top), Offset(x, area.bottom), 1f * u * 0.6f, alpha = alpha)
            x += 52f * u
        }
    }

    /**
     * Candles in [area]: [spacing] units apart, centred on [anchorX] (a fraction of the area's
     * width) or packed to its right edge, shifted by [shift] units and stretched by [stretch] about
     * the middle of the area.
     */
    fun candles(
        area: Rect,
        data: List<Bar>,
        spacing: Float = 10f,
        shift: Float = 0f,
        stretch: Float = 1f,
        centred: Boolean = false,
        alpha: Float = 1f,
    ) = with(scope) {
        val lo = data.minOf { it.low }
        val hi = data.maxOf { it.high }
        val mid = area.center.y
        val span = area.height * 0.62f
        fun y(price: Float): Float {
            val raw = mid + span / 2f - (price - lo) / (hi - lo) * span
            return mid + (raw - mid) * stretch
        }
        val step = spacing * u
        val body = step * 0.62f
        clipRect(area.left, area.top, area.right, area.bottom) {
            data.forEachIndexed { index, bar ->
                val x = if (centred) {
                    area.center.x + (index - (data.size - 1) / 2f) * step + shift * u
                } else {
                    area.right - 18f * u - (data.size - 1 - index) * step + shift * u
                }
                if (x < area.left - step || x > area.right + step) return@forEachIndexed
                val up = bar.close >= bar.open
                val edge = if (up) UP_EDGE else DOWN_EDGE
                drawLine(edge, Offset(x, y(bar.high)), Offset(x, y(bar.low)), 1.2f * u, alpha = alpha)
                val top = min(y(bar.open), y(bar.close))
                val bottom = max(y(bar.open), y(bar.close)).coerceAtLeast(top + 1.5f * u)
                drawRoundRect(
                    color = if (up) UP else DOWN,
                    topLeft = Offset(x - body / 2f, top),
                    size = Size(body, bottom - top),
                    cornerRadius = CornerRadius(1.6f * u),
                    alpha = alpha,
                )
            }
        }
    }

    fun priceColumn(area: Rect, stretch: Float = 1f, alpha: Float = 1f, highlight: Boolean = false) = with(scope) {
        drawRoundRect(
            color = if (highlight) ACCENT_SOFT else Color.White.copy(alpha = 0.55f),
            topLeft = area.topLeft,
            size = area.size,
            cornerRadius = CornerRadius(8f * u),
            alpha = alpha,
        )
        clipRect(area.left, area.top, area.right, area.bottom) {
            val mid = area.center.y
            for (index in -4..4) {
                val y = mid + index * 24f * u * stretch
                text(formatPrice(86_000 - index * 500), Offset(area.center.x, y), INK_SOFT, 7.5f, alpha = alpha)
            }
        }
    }

    /** A finger, or a mouse pointer where the reader has one. [press] dents it and lights its halo. */
    fun touch(at: Offset, press: Float, alpha: Float) = with(scope) {
        if (alpha <= 0f) return@with
        if (!touch) {
            pointer(at, press, alpha)
            return@with
        }
        val radius = 13f * u * (1f - 0.12f * press)
        drawCircle(Color.Black.copy(alpha = 0.12f * alpha), radius * 1.08f, at + Offset(0f, 3.5f * u * (1f - press * 0.6f)))
        drawCircle(HALO.copy(alpha = (0.22f + 0.3f * press) * alpha), radius * (1.7f + 0.25f * press), at)
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, FINGER_SHADE),
                center = at - Offset(radius * 0.35f, radius * 0.35f),
                radius = radius * 1.6f,
            ),
            radius,
            at,
            alpha = alpha,
        )
        drawCircle(FINGER_RING, radius, at, alpha = alpha, style = Stroke(1.4f * u))
    }

    private fun pointer(at: Offset, press: Float, alpha: Float) = with(scope) {
        if (press > 0f) drawCircle(HALO.copy(alpha = 0.35f * press * alpha), 11f * u, at)
        val path = Path().apply {
            moveTo(at.x, at.y)
            lineTo(at.x, at.y + 17f * u)
            lineTo(at.x + 4.2f * u, at.y + 13f * u)
            lineTo(at.x + 7.4f * u, at.y + 19.5f * u)
            lineTo(at.x + 10f * u, at.y + 18.3f * u)
            lineTo(at.x + 6.9f * u, at.y + 12f * u)
            lineTo(at.x + 12.4f * u, at.y + 12f * u)
            close()
        }
        drawPath(path, Color.Black.copy(alpha = 0.12f * alpha))
        drawPath(path, Color.White, alpha = alpha)
        drawPath(path, INK, alpha = alpha, style = Stroke(1.2f * u))
    }

    fun ripple(at: Offset, progress: Float) = with(scope) {
        if (progress <= 0f || progress >= 1f) return@with
        val eased = easeOut(progress)
        drawCircle(ACCENT, (10f + 22f * eased) * u, at, alpha = 0.55f * (1f - progress), style = Stroke(2f * u))
        drawCircle(ACCENT, (6f + 12f * eased) * u, at, alpha = 0.35f * (1f - progress), style = Stroke(1.4f * u))
    }

    /** The ring a hold fills, round the fingertip. */
    fun holdRing(at: Offset, progress: Float) = with(scope) {
        if (progress <= 0f || progress >= 1f) return@with
        val radius = 19f * u
        drawArc(
            color = ACCENT,
            startAngle = -90f,
            sweepAngle = 360f * easeInOut(progress),
            useCenter = false,
            topLeft = at - Offset(radius, radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(2.6f * u, cap = StrokeCap.Round),
        )
    }

    fun handle(at: Offset, alpha: Float = 1f, lit: Boolean = false) = with(scope) {
        if (lit) drawCircle(HALO.copy(alpha = 0.45f * alpha), 9f * u, at)
        drawCircle(Color.White, 4.6f * u, at, alpha = alpha)
        drawCircle(ACCENT, 4.6f * u, at, alpha = alpha, style = Stroke(1.6f * u))
    }

    fun card(area: Rect, alpha: Float = 1f, lifted: Float = 0f, fill: Color = Color.White) = with(scope) {
        val radius = CornerRadius(10f * u)
        drawRoundRect(
            Color.Black.copy(alpha = (0.06f + 0.08f * lifted) * alpha),
            topLeft = area.topLeft + Offset(0f, (2f + 5f * lifted) * u),
            size = area.size,
            cornerRadius = radius,
        )
        drawRoundRect(fill, area.topLeft, area.size, radius, alpha = alpha)
    }

    fun sparkline(area: Rect, colour: Color, seed: Int, alpha: Float = 1f, reveal: Float = 1f) = with(scope) {
        val path = Path()
        val count = 16
        val last = (count * reveal).toInt().coerceIn(1, count)
        for (index in 0..last) {
            val fraction = index / count.toFloat()
            val wave = sin((index + seed) * 0.9f) * 0.28f + fraction * 0.5f
            val x = area.left + fraction * area.width
            val y = area.bottom - (0.2f + wave * 0.9f).coerceIn(0f, 1f) * area.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, colour, alpha = alpha, style = Stroke(1.6f * u, cap = StrokeCap.Round))
    }

    // ── 1. The drawing cursor ─────────────────────────────────────────────────────────────

    fun trackpad(t: Float) = with(scope) {
        val area = plot
        grid(area)
        candles(area, BARS, alpha = 0.55f)
        priceColumn(gutter, alpha = 0.7f)
        val start = p(w * 0.5f, 70f)
        val a = p(w * 0.3f, 112f)
        val b = p(w * 0.68f, 42f)
        val firstDrag = easeInOut(seg(t, 0.1f, 0.34f))
        val secondDrag = easeInOut(seg(t, 0.48f, 0.74f))
        val cursor = when {
            t < 0.48f -> lerp(start, a, firstDrag)
            else -> lerp(a, b, secondDrag)
        }
        val fingerHome = p(w * 0.42f, 150f)
        val finger = when {
            t < 0.48f -> fingerHome + (cursor - start)
            else -> fingerHome + (a - start) + (cursor - a)
        }
        val committed = t >= 0.8f
        val anchored = t >= 0.4f
        // The stretched line from the first anchor to the cursor, then the line itself.
        if (anchored) {
            val end = if (committed) b else cursor
            drawLine(
                LINE_BLUE,
                a,
                end,
                if (committed) 2.6f * u else 2f * u,
                cap = StrokeCap.Round,
                pathEffect = if (committed) null else PathEffect.dashPathEffect(floatArrayOf(6f * u, 4f * u)),
            )
            handle(a)
            if (committed) handle(b, lit = true)
        }
        // The pointer: two dashed rules and a dot, the way the chart draws it.
        val cursorAlpha = 1f - seg(t, 0.86f, 0.92f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(5f * u, 4f * u))
        drawLine(CURSOR_BLUE, Offset(cursor.x, area.top), Offset(cursor.x, area.bottom), 1f * u, alpha = 0.7f * cursorAlpha, pathEffect = dash)
        drawLine(CURSOR_BLUE, Offset(area.left, cursor.y), Offset(area.right, cursor.y), 1f * u, alpha = 0.7f * cursorAlpha, pathEffect = dash)
        trail(t, cursorAlpha) { at -> if (at < 0.48f) lerp(start, a, easeInOut(seg(at, 0.1f, 0.34f))) else lerp(a, b, easeInOut(seg(at, 0.48f, 0.74f))) }
        drawCircle(Color.White, 6.4f * u, cursor, alpha = cursorAlpha)
        drawCircle(CURSOR_BLUE, 4.8f * u, cursor, alpha = cursorAlpha)
        val tapOne = seg(t, 0.38f, 0.46f)
        val tapTwo = seg(t, 0.78f, 0.86f)
        ripple(finger, seg(t, 0.39f, 0.6f))
        ripple(finger, seg(t, 0.79f, 1f))
        val press = when {
            t < 0.08f -> 0f
            tapOne > 0f && tapOne < 1f -> 1f - abs(tapOne - 0.5f) * 2f
            tapTwo > 0f && tapTwo < 1f -> 1f - abs(tapTwo - 0.5f) * 2f
            (t in 0.1f..0.34f) || (t in 0.48f..0.74f) -> 0.6f
            else -> 0.2f
        }
        touch(finger, press, seg(t, 0.03f, 0.1f) * (1f - seg(t, 0.88f, 0.95f)))
    }

    /** A fading trail behind a moving point: where it was a few frames ago. */
    private inline fun trail(t: Float, alpha: Float, at: (Float) -> Offset) = with(scope) {
        for (step in 1..7) {
            val then = t - step * 0.012f
            if (then < 0f) break
            drawCircle(CURSOR_BLUE, (3.6f - step * 0.35f) * u, at(then), alpha = 0.16f * (1f - step / 8f) * alpha)
        }
    }

    // ── 2. The floating toolbar ───────────────────────────────────────────────────────────

    fun toolbar(t: Float) = with(scope) {
        val area = plot
        grid(area)
        candles(area, BARS, alpha = 0.4f)
        priceColumn(gutter, alpha = 0.55f)
        val drag = easeOutBack(seg(t, 0.12f, 0.4f))
        val origin = lerp(p(16f, 18f), p(54f, 30f), drag)
        val fold = easeOutBack(seg(t, 0.56f, 0.76f))
        val width = 26f * u
        val tall = lerp(136f, 46f, fold) * u
        val bar = Rect(origin, Size(width, tall))
        card(bar, lifted = if (t in 0.12f..0.42f) 1f else 0f)
        // The grip: six dots.
        for (row in 0..1) for (column in 0..2) {
            drawCircle(GRIP, 1.4f * u, Offset(bar.left + (8f + column * 5f) * u, bar.top + (7f + row * 4.5f) * u))
        }
        clipRect(bar.left, bar.top + 15f * u, bar.right, bar.bottom - 2f * u) {
            for (index in 0 until 5) {
                val cell = Offset(bar.center.x, bar.top + (28f + index * 22f) * u)
                if (index == 0) {
                    drawRoundRect(ACCENT_SOFT, cell - Offset(10f * u, 9f * u), Size(20f * u, 18f * u), CornerRadius(5f * u))
                }
                glyph(index, cell, if (index == 0) CURSOR_BLUE else INK)
            }
        }
        val grip = Offset(bar.center.x, bar.top + 9f * u)
        val finger = when {
            t < 0.44f -> grip + p(4f, 6f)
            else -> grip + p(4f, 6f)
        }
        ripple(finger, seg(t, 0.54f, 0.74f))
        val press = when {
            t in 0.1f..0.42f -> 0.8f
            t in 0.52f..0.58f -> 1f
            else -> 0.15f
        }
        touch(finger, press, seg(t, 0.03f, 0.1f) * (1f - seg(t, 0.66f, 0.74f)))
    }

    /** The toolbar's five glyphs: a trend line, a channel, a fib, a box and a «T». */
    private fun glyph(index: Int, at: Offset, colour: Color) = with(scope) {
        val s = 6f * u
        val stroke = 1.4f * u
        when (index) {
            0 -> drawLine(colour, at + Offset(-s, s), at + Offset(s, -s), stroke, cap = StrokeCap.Round)
            1 -> {
                drawLine(colour, at + Offset(-s, s * 0.3f), at + Offset(s, -s), stroke, cap = StrokeCap.Round)
                drawLine(colour, at + Offset(-s, s), at + Offset(s, -s * 0.3f), stroke, cap = StrokeCap.Round)
            }
            2 -> for (line in 0..3) {
                val y = at.y - s + line * s * 0.66f
                drawLine(colour, Offset(at.x - s, y), Offset(at.x + s, y), stroke * 0.8f)
            }
            3 -> drawRoundRect(colour, at - Offset(s, s * 0.8f), Size(s * 2f, s * 1.6f), CornerRadius(2f * u), style = Stroke(stroke))
            else -> {
                drawLine(colour, at + Offset(-s * 0.8f, -s), at + Offset(s * 0.8f, -s), stroke)
                drawLine(colour, at + Offset(0f, -s), at + Offset(0f, s), stroke)
            }
        }
    }

    // ── 3. Pinch ──────────────────────────────────────────────────────────────────────────

    fun pinch(t: Float) = with(scope) {
        val area = plot
        grid(area)
        val open = easeInOut(seg(t, 0.14f, 0.52f))
        candles(area, BARS, spacing = lerp(8.5f, 16f, open), centred = true)
        priceColumn(gutter)
        val middle = area.center
        val alpha = seg(t, 0.03f, 0.12f) * (1f - seg(t, 0.62f, 0.72f))
        if (touch) {
            val reach = lerp(12f, 48f, open)
            val first = middle + p(-reach, reach * 0.55f)
            val second = middle + p(reach, -reach * 0.55f)
            val press = if (t in 0.12f..0.58f) 0.8f else 0.2f
            touch(first, press, alpha)
            touch(second, press, alpha)
        } else {
            // A pointer and a turning wheel: three chevrons climbing past it while the chart grows.
            touch(middle, 0f, alpha)
            for (chevron in 0..2) {
                val phase = ((t * 6f + chevron / 3f) % 1f)
                val at = middle + p(20f, 10f - phase * 18f)
                val fade = (1f - abs(phase - 0.5f) * 2f) * alpha * if (t in 0.12f..0.54f) 1f else 0f
                drawLine(ACCENT, at + Offset(-4f * u, 3f * u), at, 1.8f * u, cap = StrokeCap.Round, alpha = fade)
                drawLine(ACCENT, at, at + Offset(4f * u, 3f * u), 1.8f * u, cap = StrokeCap.Round, alpha = fade)
            }
        }
    }

    // ── 4. The price column ───────────────────────────────────────────────────────────────

    fun axis(t: Float) = with(scope) {
        val area = plot
        grid(area)
        val drag = easeInOut(seg(t, 0.14f, 0.44f))
        val back = easeOutBack(seg(t, 0.68f, 0.88f))
        val stretch = lerp(lerp(1f, 1.6f, drag), 1f, back)
        candles(area, BARS, stretch = stretch)
        priceColumn(gutter, stretch = stretch, highlight = t in 0.1f..0.7f)
        val top = p(w - 24f, 66f)
        val bottom = p(w - 24f, 112f)
        val finger = if (t < 0.5f) lerp(top, bottom, drag) else bottom
        ripple(finger, seg(t, 0.58f, 0.74f))
        ripple(finger, seg(t, 0.66f, 0.82f))
        val press = when {
            t in 0.12f..0.46f -> 0.85f
            t in 0.57f..0.6f || t in 0.65f..0.68f -> 1f
            else -> 0.15f
        }
        touch(finger, press, seg(t, 0.03f, 0.1f) * (1f - seg(t, 0.84f, 0.92f)))
    }

    // ── 5. Hold ───────────────────────────────────────────────────────────────────────────

    fun hold(t: Float) = with(scope) {
        val area = plot
        grid(area)
        candles(area, BARS)
        priceColumn(gutter)
        val at = p(w * 0.55f, 80f)
        if (touch) {
            holdRing(at, seg(t, 0.14f, 0.42f))
            val shown = seg(t, 0.42f, 0.5f)
            if (shown > 0f) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(5f * u, 4f * u))
                drawLine(ACCENT, Offset(at.x, area.top), Offset(at.x, area.bottom), 1.2f * u, alpha = shown, pathEffect = dash)
                drawLine(ACCENT, Offset(area.left, at.y), Offset(area.right, at.y), 1.2f * u, alpha = shown, pathEffect = dash)
                tag(Offset(gutter.center.x, at.y), formatPrice(85_240), shown)
                tag(Offset(at.x, area.bottom - 8f * u), "14:30", shown)
                drawCircle(ACCENT, 3.4f * u, at, alpha = shown)
            }
            val press = if (t in 0.12f..0.55f) 1f else 0.2f
            touch(at + p(0f, 0f), press, seg(t, 0.05f, 0.13f) * (1f - seg(t, 0.55f, 0.63f)))
        } else {
            // A right click: a press, and the price's own menu springing open beside the pointer.
            ripple(at, seg(t, 0.18f, 0.4f))
            val open = easeOutBack(seg(t, 0.26f, 0.4f))
            if (open > 0f) {
                val menu = Rect(at + p(8f, 6f), Size(84f * u * open, 58f * u * open))
                card(menu, alpha = seg(t, 0.26f, 0.32f))
                for (row in 0..2) {
                    val y = menu.top + (12f + row * 16f) * u * open
                    drawCircle(if (row == 0) BELL else ACCENT_SOFT, 3.2f * u * open, Offset(menu.left + 10f * u, y))
                    drawRoundRect(INK_FAINT, Offset(menu.left + 18f * u, y - 2f * u), Size((40f - row * 8f) * u * open, 4f * u), CornerRadius(2f * u))
                }
            }
            touch(at, if (t in 0.18f..0.24f) 1f else 0f, seg(t, 0.05f, 0.13f))
        }
    }

    private fun tag(at: Offset, value: String, alpha: Float) = with(scope) {
        val box = Rect(at - Offset(19f * u, 6.5f * u), Size(38f * u, 13f * u))
        drawRoundRect(ACCENT, box.topLeft, box.size, CornerRadius(4f * u), alpha = alpha)
        text(value, box.center, Color.White, 7f, bold = true, alpha = alpha)
    }

    // ── 6. Double tap ─────────────────────────────────────────────────────────────────────

    fun doubleTap(t: Float) = with(scope) {
        val area = plot
        grid(area)
        val slide = easeOutBack(seg(t, 0.32f, 0.62f))
        candles(area, BARS, shift = lerp(86f, 0f, slide))
        priceColumn(gutter)
        val last = Offset(area.right - 18f * u, area.center.y - 46f * u)
        val glow = seg(t, 0.6f, 0.68f) * (1f - seg(t, 0.82f, 0.92f))
        if (glow > 0f) {
            drawCircle(Brush.radialGradient(listOf(BOKEH_MINT, Color.Transparent), last, 26f * u), 26f * u, last, alpha = glow)
        }
        val at = p(w * 0.42f, 96f)
        ripple(at, seg(t, 0.16f, 0.36f))
        ripple(at, seg(t, 0.25f, 0.45f))
        val press = if (t in 0.15f..0.19f || t in 0.24f..0.28f) 1f else 0.15f
        touch(at, press, seg(t, 0.04f, 0.12f) * (1f - seg(t, 0.4f, 0.48f)))
    }

    // ── 7. The symbol wheel ───────────────────────────────────────────────────────────────

    fun wheel(t: Float) = with(scope) {
        val area = r(10f, 10f, w - 44f, 120f)
        grid(area)
        val swap = easeInOut(seg(t, 0.3f, 0.56f))
        candles(area, BARS, alpha = 1f - swap, shift = -swap * 20f)
        candles(area, BARS_B, alpha = swap, shift = (1f - swap) * 20f)
        priceColumn(r(w - 42f, 10f, w - 6f, 120f))
        val band = r(8f, 128f, w - 8f, 172f)
        card(band)
        val roll = easeOutBack(seg(t, 0.16f, 0.46f))
        val names = listOf("ETH", "BTC", "SOL", "BNB")
        clipRect(band.left, band.top + 2f * u, band.left + 64f * u, band.bottom - 2f * u) {
            names.forEachIndexed { index, name ->
                val slot = index - 1 - roll
                val y = band.center.y + slot * 15f * u
                val near = 1f - abs(slot).coerceAtMost(1f)
                text(name, Offset(band.left + 32f * u, y), INK, lerp(8f, 11f, near), bold = near > 0.5f, alpha = 0.35f + 0.65f * near)
            }
        }
        text("4h", Offset(band.left + 84f * u, band.center.y), INK_SOFT, 10f, bold = true)
        for (dot in 0..3) drawCircle(INK_FAINT, 3.4f * u, Offset(band.right - (16f + dot * 22f) * u, band.center.y))
        // Beside the ticker rather than on it, so the name the reader is turning stays in sight.
        val finger = lerp(Offset(band.left + 54f * u, band.center.y + 10f * u), Offset(band.left + 54f * u, band.center.y - 8f * u), easeInOut(seg(t, 0.16f, 0.46f)))
        touch(finger, if (t in 0.14f..0.48f) 0.85f else 0.2f, seg(t, 0.04f, 0.12f) * (1f - seg(t, 0.54f, 0.62f)))
    }

    // ── 8. A drawing's handles ────────────────────────────────────────────────────────────

    fun handle(t: Float) = with(scope) {
        val area = plot
        grid(area)
        candles(area, BARS, alpha = 0.7f)
        priceColumn(gutter, alpha = 0.7f)
        val reshape = easeOutBack(seg(t, 0.18f, 0.52f))
        val move = easeOutBack(seg(t, 0.7f, 0.86f))
        val shift = p(0f, 18f) * move
        val first = p(w * 0.22f, 124f) + shift
        val second = lerp(p(w * 0.58f, 84f), p(w * 0.74f, 40f), reshape) + shift
        drawLine(LINE_BLUE, first, second, 2.4f * u, cap = StrokeCap.Round)
        handle(first)
        handle(second, lit = t in 0.14f..0.56f)
        val middle = lerp(first, second, 0.5f)
        val finger = when {
            t < 0.6f -> second
            t < 0.68f -> lerp(second, lerp(first - shift, second - shift, 0.5f), easeInOut(seg(t, 0.6f, 0.68f)))
            else -> middle
        }
        val press = when {
            t in 0.14f..0.54f -> 0.85f
            t in 0.68f..0.88f -> 0.85f
            else -> 0.2f
        }
        touch(finger, press, seg(t, 0.04f, 0.12f) * (1f - seg(t, 0.88f, 0.95f)))
    }

    // ── 9. «+» beside the price ───────────────────────────────────────────────────────────

    fun plus(t: Float) = with(scope) {
        val area = plot
        grid(area)
        candles(area, BARS)
        priceColumn(gutter, highlight = t > 0.18f)
        val y = 84f * u
        val bubble = Offset(gutter.left - 12f * u, y)
        val pop = easeOutBack(seg(t, 0.2f, 0.32f))
        val ring = seg(t, 0.4f, 0.48f)
        val line = easeInOut(seg(t, 0.42f, 0.66f))
        if (line > 0f) {
            drawLine(
                BELL,
                Offset(gutter.left - 4f * u, y),
                Offset(gutter.left - 4f * u - (gutter.left - area.left) * line, y),
                1.6f * u,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * u, 4f * u)),
            )
        }
        if (pop > 0f) {
            drawCircle(if (ring > 0f) BELL else ACCENT, 8f * u * pop, bubble)
            if (ring < 0.5f) {
                drawLine(Color.White, bubble + Offset(-3.6f * u * pop, 0f), bubble + Offset(3.6f * u * pop, 0f), 1.6f * u, alpha = 1f - ring * 2f)
                drawLine(Color.White, bubble + Offset(0f, -3.6f * u * pop), bubble + Offset(0f, 3.6f * u * pop), 1.6f * u, alpha = 1f - ring * 2f)
            } else {
                bell(bubble, 1f, (ring - 0.5f) * 2f)
            }
        }
        tag(Offset(gutter.center.x, y), formatPrice(85_500), seg(t, 0.18f, 0.24f))
        val finger = lerp(p(w * 0.5f, 130f), bubble + p(2f, 3f), easeInOut(seg(t, 0.06f, 0.2f)))
        ripple(bubble, seg(t, 0.36f, 0.56f))
        touch(finger, if (t in 0.35f..0.4f) 1f else 0.15f, seg(t, 0.03f, 0.08f) * (1f - seg(t, 0.56f, 0.64f)))
    }

    private fun bell(at: Offset, scale: Float, alpha: Float, swing: Float = 0f) = with(scope) {
        rotate(swing, pivot = at - Offset(0f, 4f * u)) {
            val s = 4.2f * u * scale
            val path = Path().apply {
                moveTo(at.x - s, at.y + s * 0.6f)
                cubicTo(at.x - s, at.y - s * 1.1f, at.x + s, at.y - s * 1.1f, at.x + s, at.y + s * 0.6f)
                close()
            }
            drawPath(path, Color.White, alpha = alpha)
            drawCircle(Color.White, s * 0.28f, at + Offset(0f, s * 0.95f), alpha = alpha)
        }
    }

    // ── 10. An alert line ─────────────────────────────────────────────────────────────────

    fun alertLine(t: Float) = with(scope) {
        val area = plot
        grid(area)
        candles(area, BARS, alpha = 0.8f)
        priceColumn(gutter, alpha = 0.8f)
        val drag = easeOutBack(seg(t, 0.18f, 0.52f))
        val y = lerp(116f, 52f, drag) * u
        val moving = t in 0.16f..0.56f
        drawLine(
            BELL,
            Offset(area.left, y),
            Offset(gutter.left, y),
            if (moving) 2f * u else 1.5f * u,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * u, 4f * u)),
        )
        val box = Rect(Offset(gutter.left - 2f * u, y - 8f * u), Size(gutter.width + 2f * u, 16f * u))
        drawRoundRect(BELL, box.topLeft, box.size, CornerRadius(5f * u))
        val swing = sin(seg(t, 0.6f, 0.8f) * PI.toFloat() * 5f) * 14f * (1f - seg(t, 0.6f, 0.8f))
        bell(Offset(box.left + 8f * u, box.center.y), 0.8f, 1f, swing)
        val price = lerp(84_200f, 86_950f, drag).toInt() / 50 * 50
        text(formatPrice(price), Offset(box.center.x + 4f * u, box.center.y), Color.White, 6.6f, bold = true)
        val finger = Offset(box.center.x, y + 2f * u)
        touch(finger, if (moving) 0.85f else 0.2f, seg(t, 0.06f, 0.14f) * (1f - seg(t, 0.58f, 0.66f)))
    }

    // ── 11. Hiding a timeframe ────────────────────────────────────────────────────────────

    fun pillHide(t: Float) = with(scope) {
        val labels = listOf("1m", "5m", "15m", "1h", "4h", "1D")
        val gone = 2
        val shrink = easeInBack(seg(t, 0.42f, 0.56f))
        val close = easeOutBack(seg(t, 0.5f, 0.72f))
        val pill = 40f
        val gap = 8f
        val total = labels.size * pill + (labels.size - 1) * gap
        val left = (w - total) / 2f
        val y = 92f
        candles(r(10f, 10f, w - 10f, 64f), BARS, spacing = 9f, alpha = 0.35f)
        labels.forEachIndexed { index, value ->
            var x = left + index * (pill + gap)
            if (index > gone) x -= (pill + gap) * close
            val scale = if (index == gone) 1f - shrink else 1f
            if (scale <= 0.01f) return@forEachIndexed
            val centre = p(x + pill / 2f, y)
            val box = Rect(centre - Offset(pill / 2f * u * scale, 13f * u * scale), Size(pill * u * scale, 26f * u * scale))
            card(box, alpha = scale, fill = if (index == 4) ACCENT_SOFT else Color.White)
            text(value, centre, if (index == 4) CURSOR_BLUE else INK, 9.5f * scale, bold = true, alpha = scale)
        }
        val target = p(left + gone * (pill + gap) + pill / 2f, y)
        holdRing(target, seg(t, 0.14f, 0.42f))
        val press = if (t in 0.12f..0.44f) 1f else 0.2f
        touch(target + p(3f, 8f), press, seg(t, 0.04f, 0.12f) * (1f - seg(t, 0.46f, 0.54f)))
    }

    // ── 12. Swipe to star ─────────────────────────────────────────────────────────────────

    fun swipeStar(t: Float) = with(scope) {
        val rows = listOf("BTC", "ETH", "SOL")
        val swipe = easeInOut(seg(t, 0.14f, 0.42f))
        val back = easeOutBack(seg(t, 0.5f, 0.7f))
        val travel = lerp(swipe, 0f, back) * w * 0.36f
        val starred = t >= 0.44f
        rows.forEachIndexed { index, symbol ->
            val top = 18f + index * 52f
            val row = r(12f, top, w - 12f, top + 44f)
            if (index == 1 && travel > 0.5f) {
                // What the swipe uncovers: a butter-yellow bed and the star filling in.
                drawRoundRect(Brush.horizontalGradient(listOf(BUTTER, PEACH), startX = row.left, endX = row.right), row.topLeft, row.size, CornerRadius(10f * u))
                val pop = easeOutBack(seg(t, 0.42f, 0.52f))
                star(Offset(row.left + 22f * u, row.center.y), 8f * u * (1f + 0.25f * (1f - abs(pop * 2f - 1f))), if (starred) GOLD else Color.White)
            }
            val shift = if (index == 1) travel * u else 0f
            val moved = row.translate(shift, 0f)
            card(moved, lifted = if (index == 1 && travel > 0f) 0.6f else 0f)
            text(symbol, Offset(moved.left + 26f * u, moved.center.y), INK, 10f, bold = true)
            sparkline(Rect(moved.left + w * 0.38f * u, moved.top + 12f * u, moved.left + w * 0.62f * u, moved.bottom - 12f * u), if (index == 2) DOWN_EDGE else UP_EDGE, index * 3)
            text(formatPrice(listOf(85_240, 3_412, 168)[index]), Offset(moved.right - 34f * u, moved.center.y), INK_SOFT, 9f)
            if (index == 1 && starred && t > 0.6f) star(Offset(moved.right - 8f * u, moved.top + 8f * u), 4f * u, GOLD)
        }
        val finger = p(w * 0.32f, 18f + 52f + 22f) + Offset(travel * u, 0f)
        touch(finger, if (t in 0.12f..0.44f) 0.85f else 0.2f, seg(t, 0.04f, 0.12f) * (1f - seg(t, 0.46f, 0.52f)))
    }

    private fun star(at: Offset, radius: Float, colour: Color) = with(scope) {
        val path = Path()
        for (point in 0 until 10) {
            val angle = -PI.toFloat() / 2f + point * PI.toFloat() / 5f
            val reach = if (point % 2 == 0) radius else radius * 0.46f
            val x = at.x + kotlin.math.cos(angle) * reach
            val y = at.y + sin(angle) * reach
            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, colour)
        drawPath(path, GOLD_EDGE, style = Stroke(1f * u))
    }

    // ── 13. Hold a row ────────────────────────────────────────────────────────────────────

    fun rowHold(t: Float) = with(scope) {
        val rows = listOf("BTC", "ETH", "SOL")
        val open = easeOutBack(seg(t, 0.44f, 0.62f))
        rows.forEachIndexed { index, symbol ->
            val top = 18f + index * 52f
            val row = r(12f, top, w - 12f, top + 44f)
            card(row, alpha = 1f - 0.4f * seg(t, 0.44f, 0.52f), fill = if (index == 1 && t in 0.14f..0.44f) ACCENT_SOFT else Color.White)
            text(symbol, Offset(row.left + 26f * u, row.center.y), INK, 10f, bold = true)
            sparkline(Rect(row.left + w * 0.38f * u, row.top + 12f * u, row.left + w * 0.62f * u, row.bottom - 12f * u), UP_EDGE, index * 5)
        }
        val at = p(w * 0.32f, 18f + 52f + 22f)
        holdRing(at, seg(t, 0.14f, 0.42f))
        if (open > 0f) {
            val final = r(w * 0.14f, 14f, w * 0.86f, 166f)
            val from = r(12f, 70f, w - 12f, 114f)
            val sheet = Rect(lerp(from.topLeft, final.topLeft, open), lerp(from.bottomRight, final.bottomRight, open))
            card(sheet, lifted = 1f)
            text("ETH/USDT", Offset(sheet.left + 34f * u, sheet.top + 14f * u), INK, 9.5f, bold = true, alpha = seg(t, 0.5f, 0.58f))
            val chart = Rect(sheet.left + 10f * u, sheet.top + 26f * u, sheet.right - 10f * u, sheet.bottom - 34f * u)
            sparkline(chart, LINE_BLUE, 2, alpha = seg(t, 0.52f, 0.58f), reveal = easeInOut(seg(t, 0.55f, 0.85f)))
            for (button in 0..1) {
                val box = Rect(
                    Offset(sheet.left + 10f * u + button * (sheet.width - 20f * u) / 2f + button * 4f * u, sheet.bottom - 26f * u),
                    Size((sheet.width - 24f * u) / 2f, 16f * u),
                )
                drawRoundRect(if (button == 0) ACCENT else ACCENT_SOFT, box.topLeft, box.size, CornerRadius(8f * u), alpha = seg(t, 0.56f, 0.64f))
            }
        }
        touch(at, if (t in 0.12f..0.45f) 1f else 0.2f, seg(t, 0.04f, 0.12f) * (1f - seg(t, 0.46f, 0.54f)))
    }

    // ── 14. Reorder ───────────────────────────────────────────────────────────────────────

    fun reorder(t: Float) = with(scope) {
        val rows = listOf("BTC", "ETH", "SOL", "XRP")
        val pitch = 38f
        val lift = seg(t, 0.12f, 0.2f) * (1f - seg(t, 0.66f, 0.74f))
        val carry = easeInOut(seg(t, 0.2f, 0.6f))
        val carried = carry * pitch * 2f
        rows.forEachIndexed { index, symbol ->
            if (index == 0) return@forEachIndexed
            // A row steps up the moment the carried one passes its middle, with a little spring.
            val passed = easeOutBack(seg(carried, (index - 0.5f) * pitch, (index - 0.5f) * pitch + 10f).coerceIn(0f, 1f))
            val top = 10f + index * pitch - if (index <= 2) passed * pitch else 0f
            rowWithHandle(r(12f, top, w - 12f, top + 32f), symbol, 0f)
        }
        val top = 10f + carried
        val row = r(12f, top, w - 12f, top + 32f)
        withTransform({ scale(1f + 0.03f * lift, 1f + 0.03f * lift, row.center) }) {
            rowWithHandle(row, rows[0], lift)
        }
        val handle = Offset(row.right - 16f * u, row.center.y)
        touch(handle + p(2f, 4f), if (t in 0.1f..0.66f) 0.9f else 0.2f, seg(t, 0.03f, 0.1f) * (1f - seg(t, 0.7f, 0.78f)))
    }

    private fun rowWithHandle(row: Rect, symbol: String, lifted: Float) = with(scope) {
        card(row, lifted = lifted, fill = if (lifted > 0.1f) Color.White else Color.White)
        text(symbol, Offset(row.left + 24f * u, row.center.y), INK, 9.5f, bold = true)
        sparkline(Rect(row.left + 48f * u, row.top + 9f * u, row.left + 110f * u, row.bottom - 9f * u), UP_EDGE, symbol.length)
        for (line in -1..1) {
            val y = row.center.y + line * 3.4f * u
            drawLine(GRIP, Offset(row.right - 21f * u, y), Offset(row.right - 11f * u, y), 1.4f * u, cap = StrokeCap.Round)
        }
    }

    // ── 15. Swipe a drawing away ──────────────────────────────────────────────────────────

    fun treeSwipe(t: Float) = with(scope) {
        val names = listOf("خط روند", "فیبوناچی", "مستطیل", "متن")
        val pitch = 38f
        val swipe = easeInOut(seg(t, 0.14f, 0.42f))
        val away = easeInBack(seg(t, 0.42f, 0.54f))
        val close = easeOutBack(seg(t, 0.56f, 0.74f))
        names.forEachIndexed { index, name ->
            var top = 10f + index * pitch
            if (index > 1) top -= close * pitch
            val row = r(12f, top, w - 12f, top + 32f)
            if (index == 1) {
                if (away >= 1f) return@forEachIndexed
                drawRoundRect(DOWN, row.topLeft, row.size, CornerRadius(10f * u), alpha = 1f - away)
                bin(Offset(row.right - 18f * u, row.center.y), 1f - away)
                val shift = -(swipe * 0.32f + away * 0.9f) * w * u
                drawingRow(row.translate(shift, 0f), name, index)
            } else {
                drawingRow(row, name, index)
            }
        }
        val start = p(w * 0.62f, 10f + pitch + 16f)
        val finger = start - Offset(swipe * 0.32f * w * u, 0f)
        touch(finger, if (t in 0.12f..0.44f) 0.85f else 0.2f, seg(t, 0.04f, 0.12f) * (1f - seg(t, 0.44f, 0.5f)))
    }

    private fun drawingRow(row: Rect, name: String, index: Int) = with(scope) {
        card(row)
        glyph(listOf(0, 2, 3, 4)[index], Offset(row.right - 18f * u, row.center.y), INK_SOFT)
        text(name, Offset(row.right - 64f * u, row.center.y), INK, 9.5f, bold = true)
        drawCircle(ACCENT_SOFT, 5f * u, Offset(row.left + 16f * u, row.center.y))
        drawCircle(ACCENT_SOFT, 5f * u, Offset(row.left + 32f * u, row.center.y))
    }

    private fun bin(at: Offset, alpha: Float) = with(scope) {
        val s = 5f * u
        val stroke = Stroke(1.4f * u, cap = StrokeCap.Round)
        drawLine(Color.White, at + Offset(-s, -s * 0.8f), at + Offset(s, -s * 0.8f), 1.4f * u, alpha = alpha)
        val body = Path().apply {
            moveTo(at.x - s * 0.75f, at.y - s * 0.8f)
            lineTo(at.x - s * 0.6f, at.y + s)
            lineTo(at.x + s * 0.6f, at.y + s)
            lineTo(at.x + s * 0.75f, at.y - s * 0.8f)
        }
        drawPath(body, Color.White, alpha = alpha, style = stroke)
    }
}

// ── Data, easing, palette ───────────────────────────────────────────────────────────────────

private data class Bar(val open: Float, val high: Float, val low: Float, val close: Float)

private fun bars(moves: FloatArray): List<Bar> {
    var close = 100f
    return moves.mapIndexed { index, move ->
        val open = close
        close = open + move
        val wick = 0.6f + (index % 3) * 0.35f
        Bar(open, max(open, close) + wick, min(open, close) - wick, close)
    }
}

private val BARS = bars(
    floatArrayOf(
        1.2f, -0.8f, 1.6f, 0.9f, -1.4f, -0.6f, 1.1f, 2.2f, -0.9f, 1.4f, 0.7f, -1.8f,
        -0.7f, 1.3f, 1.9f, -0.5f, 1.2f, 2.4f, -1.1f, 0.8f, 1.5f, -0.6f, 1.7f, 1.1f,
    ),
)
private val BARS_B = bars(
    floatArrayOf(
        -1.0f, -0.6f, 0.8f, -1.6f, -0.4f, 1.2f, -1.3f, -0.9f, 0.6f, 1.4f, -0.5f, 0.9f,
        1.6f, -0.7f, 1.1f, 0.4f, -1.2f, 1.8f, 0.9f, -0.4f, 1.3f, 0.6f, -0.8f, 1.4f,
    ),
)

/** «85,240» — a market figure, so Latin, with the thousands grouped. */
private fun formatPrice(value: Int): String {
    val digits = abs(value).toString()
    val grouped = buildString {
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) append(',')
            append(digit)
        }
    }
    return if (value < 0) "-$grouped" else grouped
}

private fun seg(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction
private fun lerp(from: Offset, to: Offset, fraction: Float): Offset = from + (to - from) * fraction
private fun easeOut(x: Float): Float = 1f - (1f - x).pow(3)
private fun easeInOut(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOutBack(x: Float): Float {
    if (x <= 0f) return 0f
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}
private fun easeInBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return c3 * x * x * x - c1 * x * x
}

/** One loop of a film. Long enough to see the gesture land and its result settle. */
private const val SCENE_MS = 3_400

/** The frame a reduced-motion reader is shown: the gesture under way, its result visible. */
private const val STILL_FRAME = 0.5f

/** How much of each end of the loop dissolves through the stage colour. */
private const val LOOP_FADE = 0.06f

/** The camera's push over one loop. */
private const val PUSH_IN = 0.035f

/** The stage's height in design units; one unit is [Film.u] pixels. */
private const val STAGE_UNITS = 180f

private val STAGE_TOP = Color(0xFFEFEBFF)
private val STAGE_BOTTOM = Color(0xFFFFF1EA)
private val STAGE_MID = Color(0xFFF7EEF5)
private val BOKEH_LAVENDER = Color(0x66C9C1FF)
private val BOKEH_MINT = Color(0x55B4F0D6)
private val VIGNETTE = Color(0x1F2A2160)
private val GRID = Color(0x1F6C63FF)

private val UP = Color(0xFF8FDDBE)
private val UP_EDGE = Color(0xFF4CBF95)
private val DOWN = Color(0xFFF7AFC0)
private val DOWN_EDGE = Color(0xFFE47C96)

private val INK = Color(0xFF2B2E55)
private val INK_SOFT = Color(0xFF6A6F95)
private val INK_FAINT = Color(0xFFD9D7EE)
private val ACCENT = Color(0xFF8A8FF8)
private val ACCENT_SOFT = Color(0xFFE6E5FF)
private val CURSOR_BLUE = Color(0xFF5B7CFA)
private val LINE_BLUE = Color(0xFF7A86F7)
private val HALO = Color(0xFFB9B3FF)
private val FINGER_SHADE = Color(0xFFE9E5FF)
private val FINGER_RING = Color(0xFFA6A9F6)
private val GRIP = Color(0xFFB8BAD0)
private val BELL = Color(0xFFF6A98B)
private val BUTTER = Color(0xFFFFEDB0)
private val PEACH = Color(0xFFFFD6C4)
private val GOLD = Color(0xFFFFC94D)
private val GOLD_EDGE = Color(0xFFE8A92E)

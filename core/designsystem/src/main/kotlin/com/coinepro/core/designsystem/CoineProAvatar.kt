package com.coinepro.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.model.AvatarBase
import com.coinepro.core.model.AvatarMark
import com.coinepro.core.model.AvatarRing
import com.coinepro.core.model.AvatarSpec
import java.io.File
import coil3.compose.SubcomposeAsyncImageContent
import coil3.compose.SubcomposeAsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A reader's avatar, whatever they chose it to be.
 *
 * One composable for all four bases, because every place that shows an avatar — the profile hero,
 * the shell's top corner, a comment row that does not exist yet — must show the same thing at a
 * different size and must not each re-derive what the spec means.
 *
 * [size] is the outer diameter, ring included. Everything inside scales from it: the ring is a
 * fixed fraction rather than a fixed thickness, so a 34dp avatar in the app bar and a 96dp one on
 * the profile look like the same object rather than like a thin one and a thick one.
 */
@Composable
fun CoineProAvatar(
    spec: AvatarSpec,
    modifier: Modifier = Modifier,
    /** The letter for [AvatarBase.Initial], which the caller knows and this composable does not. */
    initial: String = "?",
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    val ring = spec.ring.color()
    // A fraction rather than a constant: see the note on [size].
    val stroke = (size.value * RING_FRACTION).dp.coerceAtLeast(1.dp)
    // The inner disc is inset by the ring plus a hair, so the two never touch. A ring drawn flush
    // against the artwork reads as a border on the picture; one with air reads as a frame.
    val inset = if (spec.ring == AvatarRing.NONE) 0.dp else stroke + (size.value * RING_GAP).dp

    // What the artwork inside is tinted with. A ring of NONE is *transparent*, and handing that to
    // a mark drew ten invisible discs the first time this was rendered — so the fallback is the
    // brand gold, which is what an unringed avatar would have been ringed with.
    val ink = if (spec.ring == AvatarRing.NONE) CoineProColors.Accent else ring

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (spec.ring == AvatarRing.NONE) {
                    Modifier
                } else {
                    Modifier.border(stroke, ring, CircleShape)
                },
            )
            .padding(inset)
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val inner = size - inset * 2
        when (val base = spec.base) {
            AvatarBase.Initial -> CoineProAssetToken(
                label = initial.trim().take(1).ifEmpty { "?" },
                tint = ink,
                size = inner,
            )
            is AvatarBase.Symbol -> CoineProAssetLogo(symbol = base.symbol, size = inner)
            is AvatarBase.Mark -> CoineProAvatarMark(mark = base.mark, tint = ink, size = inner)
            is AvatarBase.Photo -> AvatarPhoto(path = base.path, initial = initial, tint = ink, size = inner)
        }
    }
}

/** The ring's colour, from the palette rather than from a literal. */
@Composable
private fun AvatarRing.color(): Color = when (this) {
    AvatarRing.NONE -> Color.Transparent
    AvatarRing.GOLD -> CoineProColors.Accent
    AvatarRing.PREMIUM -> CoineProColors.Premium
    AvatarRing.ANALYSIS -> CoineProColors.Analysis
    AvatarRing.BUY -> CoineProColors.Buy
    AvatarRing.SELL -> CoineProColors.Sell
}

/**
 * The reader's own picture, decoded from a file this app owns.
 *
 * Decoded off the main thread and downsampled to [DECODE_PX], which is more than any avatar on
 * screen needs and small enough that the bitmap is a few hundred kilobytes rather than the twelve
 * megabytes a modern phone camera produces. A full-resolution decode on the composition thread is
 * a visible stall on the one screen whose whole job is to feel personal.
 *
 * A file that has gone — cleared storage, a restore from a backup that did not carry it — falls
 * back to the lettered token rather than to a broken-image glyph. It is the same rule the market
 * rows follow, and for the same reason.
 */
@Composable
private fun AvatarPhoto(path: String, initial: String, tint: Color, size: Dp) {
    // Through the image loader rather than a hand-rolled decode: it downsamples to the size on
    // screen, keeps the result in memory across the screens that show the same face, and shows
    // the shimmer while it works. The initial stays underneath in case the file is gone.
    val file = File(path)
    if (!file.isFile) {
        CoineProAssetToken(label = initial.trim().take(1).ifEmpty { "?" }, tint = tint, size = size)
        return
    }
    SubcomposeAsyncImage(
        model = file,
        contentDescription = null,
        modifier = Modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop,
        loading = { CoineProSkeleton(modifier = Modifier.size(size), height = size, shape = CircleShape) },
        error = { CoineProAssetToken(label = initial.trim().take(1).ifEmpty { "?" }, tint = tint, size = size) },
        success = { SubcomposeAsyncImageContent(modifier = Modifier.size(size).clip(CircleShape)) },
    )
}

/** Bounds-first, then a power-of-two subsample. The cheapest correct way to do this on Android. */

/* ------------------------------------------------------------------ the marks */

/**
 * One of the app's own marks, drawn rather than borrowed.
 *
 * Every mark is authored in a 100×100 space and scaled, so the same path serves a 28dp corner
 * avatar and a 120dp profile hero without a second set of coordinates.
 *
 * **The motion.** Each mark has exactly one gesture and it runs on a single phase in `0..1`,
 * driven by an infinite transition — which this file is allowed to use only because it consults
 * [continuousMotionAllowed] and holds phase at rest when the device has animations turned off.
 * That is not a formality: an avatar is on screen the whole time somebody is using the app, so of
 * everything in this codebase it is the animation most likely to be the one they cannot ignore.
 */
@Composable
fun CoineProAvatarMark(
    mark: AvatarMark,
    modifier: Modifier = Modifier,
    tint: Color = CoineProColors.Accent,
    size: Dp = 44.dp,
) {
    val moving = continuousMotionAllowed()
    val phase = if (moving) {
        val transition = rememberInfiniteTransition(label = "avatar-mark")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = mark.periodMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "avatar-mark-phase",
        )
        value
    } else {
        // The frame the mark looks best held on, not zero: a rocket with no flame and a trend line
        // with no line are both the wrong still.
        REST_PHASE
    }

    val ink = mark.ink(tint)
    val disc = CoineProTint.fill(ink, CoineProColors.SurfaceElevated)
    // Resolved here, because a DrawScope is not a composition and the palette getters are.
    val ground = CoineProColors.Stage
    val warm = CoineProColors.Warning
    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(disc)
            .border(1.dp, CoineProColors.assetRing, CircleShape),
    ) {
        val unit = this.size.minDimension / 100f
        when (mark) {
            AvatarMark.ROCKET -> drawRocket(unit, ink, ground, warm, phase)
            AvatarMark.BULL -> drawBull(unit, ink, ground, warm, phase)
            AvatarMark.BEAR -> drawBear(unit, ink, ground, warm, phase)
            AvatarMark.CANDLE -> drawCandle(unit, ink, ground, warm, phase)
            AvatarMark.DIAMOND -> drawDiamond(unit, ink, ground, warm, phase)
            AvatarMark.FLAME -> drawFlame(unit, ink, ground, warm, phase)
            AvatarMark.BOLT -> drawBolt(unit, ink, ground, warm, phase)
            AvatarMark.TREND -> drawTrend(unit, ink, ground, warm, phase)
            AvatarMark.SHIELD -> drawShield(unit, ink, ground, warm, phase)
            AvatarMark.GLOBE -> drawGlobe(unit, ink, ground, warm, phase)
        }
    }
}

/**
 * A mark's own colour, where it has one.
 *
 * Most take the ring's colour, so a reader who picked a green ring gets a green mark and the avatar
 * reads as one object. The two that do not are the two whose colour *is* their meaning: a bull is
 * the buy colour and a bear is the sell colour in every terminal ever built, and letting somebody
 * paint a bull red would be letting them say the opposite of what they chose.
 */
@Composable
private fun AvatarMark.ink(tint: Color): Color = when (this) {
    AvatarMark.BULL -> CoineProColors.Buy
    AvatarMark.BEAR -> CoineProColors.Sell
    else -> tint
}

/** How long one gesture takes. Slow: an avatar is not a progress bar. */
private val AvatarMark.periodMillis: Int
    get() = when (this) {
        AvatarMark.ROCKET, AvatarMark.FLAME -> 2_600
        AvatarMark.CANDLE -> 3_400
        AvatarMark.DIAMOND -> 4_200
        AvatarMark.BOLT -> 2_200
        AvatarMark.TREND -> 3_800
        AvatarMark.GLOBE -> 9_000
        AvatarMark.BULL, AvatarMark.BEAR, AvatarMark.SHIELD -> 4_000
    }

/** A triangle wave on the phase — 0 → 1 → 0 — for anything that breathes rather than travels. */
private fun breathe(phase: Float): Float = 1f - kotlin.math.abs(phase * 2f - 1f)

private fun DrawScope.drawRocket(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    val lift = (breathe(phase) - 0.5f) * 3f * unit
    // The flame first, so the body sits over it and the two never show a seam.
    val flame = Path().apply {
        moveTo(50f * unit, (72f * unit) + lift)
        cubicTo(
            40f * unit, (78f * unit) + lift,
            42f * unit, (86f + 8f * breathe(phase)) * unit + lift,
            50f * unit, (92f + 6f * breathe(phase)) * unit + lift,
        )
        cubicTo(
            58f * unit, (86f + 8f * breathe(phase)) * unit + lift,
            60f * unit, (78f * unit) + lift,
            50f * unit, (72f * unit) + lift,
        )
        close()
    }
    drawPath(flame, warm.copy(alpha = 0.55f + 0.35f * breathe(phase)))

    val body = Path().apply {
        moveTo(50f * unit, 14f * unit + lift)
        cubicTo(64f * unit, 30f * unit + lift, 66f * unit, 52f * unit + lift, 62f * unit, 72f * unit + lift)
        lineTo(38f * unit, 72f * unit + lift)
        cubicTo(34f * unit, 52f * unit + lift, 36f * unit, 30f * unit + lift, 50f * unit, 14f * unit + lift)
        close()
    }
    drawPath(body, ink)
    // Fins, one per side, drawn as filled wedges rather than strokes so they read at 28dp.
    val leftFin = Path().apply {
        moveTo(38f * unit, 52f * unit + lift)
        lineTo(26f * unit, 74f * unit + lift)
        lineTo(38f * unit, 70f * unit + lift)
        close()
    }
    val rightFin = Path().apply {
        moveTo(62f * unit, 52f * unit + lift)
        lineTo(74f * unit, 74f * unit + lift)
        lineTo(62f * unit, 70f * unit + lift)
        close()
    }
    drawPath(leftFin, ink.copy(alpha = 0.72f))
    drawPath(rightFin, ink.copy(alpha = 0.72f))
    drawCircle(
        color = ground,
        radius = 8f * unit,
        center = Offset(50f * unit, 38f * unit + lift),
    )
}

private fun DrawScope.drawBull(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    // A small toss of the head, which is the one motion a bull has.
    val tilt = (breathe(phase) - 0.5f) * 6f
    rotate(degrees = tilt, pivot = Offset(50f * unit, 62f * unit)) {
        // The horns are the whole mark. Drawn first and drawn *wide* — they have to leave the head
        // and come back up, or the silhouette is a round animal with two bumps, which reads as any
        // number of things that are not a bull.
        val leftHorn = Path().apply {
            moveTo(34f * unit, 44f * unit)
            cubicTo(18f * unit, 44f * unit, 8f * unit, 32f * unit, 10f * unit, 18f * unit)
            lineTo(20f * unit, 22f * unit)
            cubicTo(20f * unit, 32f * unit, 26f * unit, 36f * unit, 35f * unit, 34f * unit)
            close()
        }
        val rightHorn = Path().apply {
            moveTo(66f * unit, 44f * unit)
            cubicTo(82f * unit, 44f * unit, 92f * unit, 32f * unit, 90f * unit, 18f * unit)
            lineTo(80f * unit, 22f * unit)
            cubicTo(80f * unit, 32f * unit, 74f * unit, 36f * unit, 65f * unit, 34f * unit)
            close()
        }
        drawPath(leftHorn, ink)
        drawPath(rightHorn, ink)
        // A head that tapers to a muzzle rather than a circle: the taper is the second half of the
        // silhouette and it is what separates this from the bear.
        val head = Path().apply {
            moveTo(30f * unit, 34f * unit)
            cubicTo(30f * unit, 28f * unit, 70f * unit, 28f * unit, 70f * unit, 34f * unit)
            cubicTo(72f * unit, 58f * unit, 64f * unit, 74f * unit, 50f * unit, 86f * unit)
            cubicTo(36f * unit, 74f * unit, 28f * unit, 58f * unit, 30f * unit, 34f * unit)
            close()
        }
        drawPath(head, ink)
        drawCircle(ground, 3.6f * unit, Offset(41f * unit, 50f * unit))
        drawCircle(ground, 3.6f * unit, Offset(59f * unit, 50f * unit))
        drawCircle(ground, 2.4f * unit, Offset(45f * unit, 72f * unit))
        drawCircle(ground, 2.4f * unit, Offset(55f * unit, 72f * unit))
    }
}

private fun DrawScope.drawBear(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    val tilt = (breathe(phase) - 0.5f) * 5f
    rotate(degrees = -tilt, pivot = Offset(50f * unit, 62f * unit)) {
        // Small ears, set low and mostly behind the head. The first version had them large and
        // high, which is a mouse: on a round head, ear size is the whole difference between the
        // two animals.
        drawCircle(ink, 11f * unit, Offset(27f * unit, 33f * unit))
        drawCircle(ink, 11f * unit, Offset(73f * unit, 33f * unit))
        drawCircle(ink.copy(alpha = 0.4f), 5f * unit, Offset(27f * unit, 33f * unit))
        drawCircle(ink.copy(alpha = 0.4f), 5f * unit, Offset(73f * unit, 33f * unit))
        // A broad head, wider than it is tall, which is the other half of the difference.
        drawOval(
            color = ink,
            topLeft = Offset(19f * unit, 30f * unit),
            size = Size(62f * unit, 56f * unit),
        )
        drawCircle(ground, 3.4f * unit, Offset(40f * unit, 52f * unit))
        drawCircle(ground, 3.4f * unit, Offset(60f * unit, 52f * unit))
        // The muzzle: a light patch with the nose on it. Without it the head is a circle with two
        // dots, which is a face but not a bear's.
        drawOval(
            color = ground.copy(alpha = 0.34f),
            topLeft = Offset(38f * unit, 62f * unit),
            size = Size(24f * unit, 18f * unit),
        )
        drawOval(
            color = ground,
            topLeft = Offset(45f * unit, 64f * unit),
            size = Size(10f * unit, 7f * unit),
        )
    }
}

private fun DrawScope.drawCandle(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    val reach = breathe(phase)
    val stroke = Stroke(width = 4f * unit, cap = StrokeCap.Round)
    // The wick grows and settles, which is what an unclosed bar actually does.
    drawLine(
        color = ink,
        start = Offset(50f * unit, (24f - 6f * reach) * unit),
        end = Offset(50f * unit, 38f * unit),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = ink,
        start = Offset(50f * unit, 66f * unit),
        end = Offset(50f * unit, (76f + 6f * reach) * unit),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
    drawRoundedBody(ink, Offset(36f * unit, 38f * unit), Size(28f * unit, 28f * unit), 3f * unit)
    // Two neighbours, held still, so the moving one reads as the live bar rather than as the mark.
    drawRoundedBody(
        ink.copy(alpha = 0.32f),
        Offset(16f * unit, 48f * unit),
        Size(12f * unit, 22f * unit),
        2.5f * unit,
    )
    drawRoundedBody(
        ink.copy(alpha = 0.32f),
        Offset(72f * unit, 42f * unit),
        Size(12f * unit, 26f * unit),
        2.5f * unit,
    )
}

private fun DrawScope.drawRoundedBody(color: Color, topLeft: Offset, size: Size, radius: Float) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
    )
}

private fun DrawScope.drawDiamond(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    val gem = Path().apply {
        moveTo(50f * unit, 20f * unit)
        lineTo(78f * unit, 42f * unit)
        lineTo(50f * unit, 82f * unit)
        lineTo(22f * unit, 42f * unit)
        close()
    }
    drawPath(gem, ink)
    val facets = Path().apply {
        moveTo(22f * unit, 42f * unit)
        lineTo(78f * unit, 42f * unit)
        moveTo(36f * unit, 42f * unit)
        lineTo(50f * unit, 82f * unit)
        moveTo(64f * unit, 42f * unit)
        lineTo(50f * unit, 82f * unit)
        moveTo(36f * unit, 42f * unit)
        lineTo(50f * unit, 20f * unit)
        moveTo(64f * unit, 42f * unit)
        lineTo(50f * unit, 20f * unit)
    }
    drawPath(facets, ground.copy(alpha = 0.55f), style = Stroke(width = 1.6f * unit))
    // A highlight travelling across one facet — the only thing a cut stone does when you turn it.
    val travel = 24f + 40f * phase
    drawLine(
        color = Color.White.copy(alpha = 0.34f * (1f - kotlin.math.abs(phase - 0.5f) * 2f)),
        start = Offset(travel * unit, 30f * unit),
        end = Offset((travel - 8f) * unit, 52f * unit),
        strokeWidth = 4f * unit,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawFlame(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    val lick = breathe(phase)
    val outer = Path().apply {
        moveTo(50f * unit, (16f - 4f * lick) * unit)
        cubicTo(72f * unit, 34f * unit, 76f * unit, 58f * unit, 62f * unit, 74f * unit)
        cubicTo(54f * unit, 84f * unit, 40f * unit, 84f * unit, 33f * unit, 74f * unit)
        cubicTo(20f * unit, 58f * unit, 30f * unit, 40f * unit, 44f * unit, 30f * unit)
        cubicTo(44f * unit, 40f * unit, 48f * unit, 44f * unit, 50f * unit, (16f - 4f * lick) * unit)
        close()
    }
    drawPath(outer, ink)
    val inner = Path().apply {
        moveTo(50f * unit, (46f - 4f * lick) * unit)
        cubicTo(60f * unit, 56f * unit, 60f * unit, 68f * unit, 50f * unit, 76f * unit)
        cubicTo(40f * unit, 68f * unit, 40f * unit, 56f * unit, 50f * unit, (46f - 4f * lick) * unit)
        close()
    }
    drawPath(inner, ground.copy(alpha = 0.42f + 0.2f * lick))
}

private fun DrawScope.drawBolt(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    val bolt = Path().apply {
        moveTo(58f * unit, 12f * unit)
        lineTo(30f * unit, 54f * unit)
        lineTo(47f * unit, 54f * unit)
        lineTo(42f * unit, 88f * unit)
        lineTo(70f * unit, 44f * unit)
        lineTo(53f * unit, 44f * unit)
        close()
    }
    drawPath(bolt, ink.copy(alpha = 0.72f + 0.28f * breathe(phase)))
}

private fun DrawScope.drawTrend(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    val walk = listOf(
        Offset(18f * unit, 74f * unit),
        Offset(38f * unit, 54f * unit),
        Offset(54f * unit, 64f * unit),
        Offset(82f * unit, 28f * unit),
    )
    // Whole, always. The first version drew the line *up to* the phase, which meant that at the
    // start of every loop the avatar was an empty disc — and the screenshot caught it, because a
    // render holds the clock at zero. A mark has to be itself at every instant it is on screen;
    // motion may decorate it and may not constitute it.
    drawPolyline(walk, 1f, ink, 5f * unit)
    val head = Path().apply {
        moveTo(86f * unit, 22f * unit)
        lineTo(86f * unit, 42f * unit)
        lineTo(66f * unit, 22f * unit)
        close()
    }
    drawPath(head, ink)
    // What moves is a bright short segment running the length of the line — the glint a chart gets
    // when a new bar prints, rather than the line drawing itself.
    val glint = ((phase / 0.7f).coerceIn(0f, 1f))
    if (glint > 0f && glint < 1f) {
        drawPolylineSegment(
            points = walk,
            from = (glint - 0.16f).coerceAtLeast(0f),
            to = glint,
            color = lerp(ink, Color.White, 0.55f),
            width = 5f * unit,
        )
    }
}

private fun DrawScope.drawShield(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    val shield = Path().apply {
        moveTo(50f * unit, 14f * unit)
        lineTo(78f * unit, 26f * unit)
        cubicTo(78f * unit, 60f * unit, 68f * unit, 78f * unit, 50f * unit, 88f * unit)
        cubicTo(32f * unit, 78f * unit, 22f * unit, 60f * unit, 22f * unit, 26f * unit)
        close()
    }
    drawPath(shield, ink)
    val tick = listOf(
        Offset(37f * unit, 50f * unit),
        Offset(46f * unit, 61f * unit),
        Offset(65f * unit, 37f * unit),
    )
    // The tick is always whole, for the same reason the trend line is. See the note there.
    drawPolyline(tick, 1f, ground, 7f * unit)
    // A sheen crossing the face, which is the one thing a shield does.
    val sweep = 8f + 84f * phase
    drawLine(
        color = Color.White.copy(alpha = 0.20f * breathe(phase)),
        start = Offset(sweep * unit, 8f * unit),
        end = Offset((sweep - 22f) * unit, 92f * unit),
        strokeWidth = 10f * unit,
    )
}

/**
 * A polyline, drawn up to [fraction] of its own length.
 *
 * Written by hand rather than with `PathMeasure.getSegment`, and that is not a preference: the
 * measure-and-segment path produced *nothing* under the screenshot renderer, so the trend line and
 * the shield's tick both shipped blank in the first capture and neither was visible in a review of
 * the code. Walking the segments is a dozen lines, has no platform behind it to disagree with, and
 * is the same arithmetic either way.
 */
private fun DrawScope.drawPolyline(points: List<Offset>, fraction: Float, color: Color, width: Float) {
    if (points.size < 2) return
    val clamped = fraction.coerceIn(0f, 1f)
    if (clamped <= 0f) return
    val lengths = points.zipWithNext { from, to ->
        kotlin.math.hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble()).toFloat()
    }
    var remaining = lengths.sum() * clamped
    val path = Path().apply { moveTo(points.first().x, points.first().y) }
    for (index in lengths.indices) {
        val length = lengths[index]
        if (length <= 0f) continue
        if (remaining >= length) {
            path.lineTo(points[index + 1].x, points[index + 1].y)
            remaining -= length
        } else {
            val t = remaining / length
            path.lineTo(
                points[index].x + (points[index + 1].x - points[index].x) * t,
                points[index].y + (points[index + 1].y - points[index].y) * t,
            )
            break
        }
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * The part of a polyline between two fractions of its length — the moving glint.
 *
 * Built on the same walk as [drawPolyline] rather than on a clipped copy of it, so a segment can
 * never drift off the line it is supposed to be travelling along.
 */
private fun DrawScope.drawPolylineSegment(
    points: List<Offset>,
    from: Float,
    to: Float,
    color: Color,
    width: Float,
) {
    if (points.size < 2 || to <= from) return
    val lengths = points.zipWithNext { start, end ->
        kotlin.math.hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat()
    }
    val total = lengths.sum()
    if (total <= 0f) return
    val startAt = total * from.coerceIn(0f, 1f)
    val endAt = total * to.coerceIn(0f, 1f)
    var walked = 0f
    var started = false
    val path = Path()
    for (index in lengths.indices) {
        val length = lengths[index]
        if (length <= 0f) continue
        val segmentStart = walked
        val segmentEnd = walked + length
        walked = segmentEnd
        if (segmentEnd < startAt || segmentStart > endAt) continue
        val head = points[index]
        val tail = points[index + 1]
        fun at(distance: Float): Offset {
            val t = ((distance - segmentStart) / length).coerceIn(0f, 1f)
            return Offset(head.x + (tail.x - head.x) * t, head.y + (tail.y - head.y) * t)
        }
        val a = at(startAt)
        val b = at(endAt)
        if (!started) {
            path.moveTo(a.x, a.y)
            started = true
        }
        path.lineTo(b.x, b.y)
    }
    if (!started) return
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawGlobe(unit: Float, ink: Color, ground: Color, warm: Color, phase: Float) {
    val radius = 34f * unit
    val centre = Offset(50f * unit, 50f * unit)
    drawCircle(ink, radius, centre)
    // Three meridians whose width follows a rotation, which is what a turning sphere looks like
    // when it is drawn as lines rather than as a texture.
    repeat(3) { index ->
        val angle = (phase + index / 3f) * 2f * PI.toFloat()
        val squash = cos(angle)
        val width = kotlin.math.abs(squash) * radius
        if (width > 0.6f * unit) {
            drawOval(
                color = ground.copy(alpha = 0.5f),
                topLeft = Offset(centre.x - width, centre.y - radius),
                size = Size(width * 2f, radius * 2f),
                style = Stroke(width = 1.8f * unit),
            )
        }
    }
    // Two parallels, which do not move: latitude does not rotate with the globe.
    listOf(-0.45f, 0.45f).forEach { fraction ->
        val y = centre.y + radius * fraction
        val half = radius * kotlin.math.sqrt(1f - fraction * fraction)
        drawLine(
            color = ground.copy(alpha = 0.5f),
            start = Offset(centre.x - half, y),
            end = Offset(centre.x + half, y),
            strokeWidth = 1.8f * unit,
        )
    }
    drawCircle(ground.copy(alpha = 0.5f), radius, centre, style = Stroke(width = 1.8f * unit))
    // A single satellite, so the mark has one thing that plainly moves.
    val orbit = phase * 2f * PI.toFloat()
    drawCircle(
        color = lerp(ink, Color.White, 0.4f),
        radius = 4f * unit,
        center = Offset(
            centre.x + (radius + 8f * unit) * cos(orbit),
            centre.y + (radius * 0.32f) * sin(orbit),
        ),
    )
}

/** The ring's thickness and its gap, both as a fraction of the outer diameter. */
private const val RING_FRACTION = 0.055f
private const val RING_GAP = 0.035f

/** Where a held mark sits: past the start of its gesture, so nothing is drawn half-built. */
private const val REST_PHASE = 0.72f

/** Comfortably more than the largest avatar this app draws, at three times the density. */
private const val DECODE_PX = 512

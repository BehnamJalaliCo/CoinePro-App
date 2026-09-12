package com.coinepro.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * **Three moments in this app get confetti, and never a fourth** (run Ω2).
 *
 * ### Why a celebration exists in a trading app at all
 *
 * Because three things a reader does here are genuinely *first* times, and none of them looks like
 * anything when it happens: a script they wrote appears on the chart as one more line among five; an
 * alert they set disappears into a store; a seventh day in a row is a number nobody counted. Each of
 * those is somebody crossing from «using an app» to «this is my tool», and the app's answer to all
 * three was a toast identical to the one it shows for copying a wallet address.
 *
 * ### Why it is once and only once
 *
 * Confetti on the tenth alert is a product congratulating somebody for typing a number. It reads as
 * contempt, it is what makes an app feel like it is managing you, and it is the single most common way
 * this device is got wrong. So the trigger is a [TeachingDismissals] key — the same persisted,
 * once-per-install mechanism the coach marks use — and the burst *marks the key as it fires*. There is
 * no way to show it twice without a reader reinstalling.
 *
 * ### Why it is drawn and not an asset
 *
 * Forty rectangles falling under gravity is a hundred lines of `Canvas`; a Lottie file is a
 * dependency, a parser and about forty kilobytes for the same six hundred milliseconds. And the
 * colours have to be the product's — gold, and the two market colours — which a shipped animation
 * cannot be without being re-exported every time the palette moves.
 *
 * The whole thing honours [continuousMotionAllowed], so a reader who has asked the system to reduce
 * motion gets nothing at all: this is the most decorative motion in the app and the first that should
 * go when somebody says they do not want it.
 */
@Composable
fun BoxScope.CoineProConfetti(
    /**
     * Whether the moment has happened.
     *
     * Passing true a second time does nothing once the key has been marked — so a caller may hold it
     * true for the life of a screen without thinking about it, which is the shape every call site
     * naturally has («this reader now has a script on their chart»).
     */
    celebrate: Boolean,
    /**
     * The once-per-install key. See [CoineProCelebration] for the three that exist.
     *
     * A key rather than a boolean the caller stores, because «once ever» has to survive a process
     * death and the caller is a composable.
     */
    key: String,
    modifier: Modifier = Modifier,
) {
    val dismissals = LocalTeachingDismissals.current
    val animate = continuousMotionAllowed()
    // Not `ready`-gated the way a banner is, and the difference matters: a banner drawn too early
    // flashes and then hides, which a reader sees. A burst fired too early would be a burst the
    // reader *earned* being spent on a launch they were not looking at — so nothing happens until the
    // persisted set is the real answer.
    val spent = !dismissals.ready || key in dismissals.dismissed
    var running by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val pieces = remember(key) { confettiPieces(key) }

    LaunchedEffect(celebrate, spent, animate) {
        if (!celebrate || spent) return@LaunchedEffect
        // Marked first, then shown. If the process dies mid-burst the moment is still spent, which is
        // the right way round: a celebration that could replay after a crash is a celebration that
        // replays on every crash.
        dismissals.dismiss(key)
        if (!animate) return@LaunchedEffect
        running = true
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = BURST_MS))
        running = false
    }

    if (!running) return
    // Resolved here, once, because these are composable reads and the draw lambda below is not a
    // composable scope — the same hoist the chart's overlay makes for its accent.
    val tones = listOf(CoineProColors.Gold, CoineProColors.MarketUp, CoineProColors.MarketDown)
    val travel = progress.value
    Canvas(modifier = modifier.fillMaxSize()) {
        // Fading out over the last third rather than the whole fall, so the pieces read as *falling*
        // and then gone, not as a translucent wash that happens to move.
        val fade = if (travel < FADE_FROM) 1f else 1f - (travel - FADE_FROM) / (1f - FADE_FROM)
        for (piece in pieces) {
            // Launched from the top of the box, across its width, and carried down past the bottom:
            // `1.2` so the last piece has actually left rather than vanishing in view.
            val x = piece.x * size.width + sin((travel + piece.phase) * SWAY_TURNS * 2f * PI.toFloat()) * piece.sway
            val y = (travel * OVERSHOOT - piece.delay) * size.height
            if (y < 0f) continue
            rotate(degrees = piece.spin * travel * FULL_TURN, pivot = Offset(x, y)) {
                drawRect(
                    color = tones[piece.tone].copy(alpha = fade),
                    topLeft = Offset(x, y),
                    size = Size(PIECE_WIDTH_PX * piece.scale, PIECE_HEIGHT_PX * piece.scale),
                )
            }
        }
    }
}

/**
 * A wrapper for a screen that is not already a [Box].
 *
 * The burst has to cover the whole screen, so the composable is `BoxScope`-scoped and most call sites
 * already have one. This is for the ones that do not, and it draws nothing of its own.
 */
@Composable
fun CoineProConfettiHost(celebrate: Boolean, key: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) { CoineProConfetti(celebrate = celebrate, key = key) }
}

/**
 * The three moments. **Adding a fourth is a product decision, not a code change** — see the file note.
 *
 * The string values are storage keys and are never localised, never reused, and never renamed: a
 * rename would spend the moment again for every reader who had already had it.
 */
object CoineProCelebration {

    /** The first time one of the reader's own NamaScript studies draws on the chart. */
    const val FIRST_SCRIPT = "celebrate.first-script"

    /** The first alert they ever set. */
    const val FIRST_ALERT = "celebrate.first-alert"

    /** Seven days in a row. The one that is earned over time rather than in a tap. */
    const val SEVEN_DAY_STREAK = "celebrate.seven-day-streak"

    /** Every key, for a test that the three are distinct and for a debug screen that resets them. */
    val ALL = listOf(FIRST_SCRIPT, FIRST_ALERT, SEVEN_DAY_STREAK)
}

/**
 * One piece of paper.
 *
 * [tone] is an index into the three colours the composable resolves, not a `Color`: these are built
 * outside composition — see [confettiPieces] — and `CoineProColors` is a composable read.
 */
private class ConfettiPiece(
    /** Where it starts across the width, as a fraction. */
    val x: Float,
    /** How far into the fall it appears, as a fraction of the height. Staggers the burst. */
    val delay: Float,
    /** How far it drifts sideways, in pixels. */
    val sway: Float,
    /** Where in its sway cycle it starts, so forty pieces do not swing in unison. */
    val phase: Float,
    /** Turns over the whole fall, signed. */
    val spin: Float,
    val scale: Float,
    /** 0 gold, 1 the rise colour, 2 the fall colour. Resolved by the caller against the theme. */
    val tone: Int,
)

/**
 * Forty pieces, deterministic for a given key.
 *
 * Seeded from the key rather than from the clock, which is what makes the burst reproducible in a
 * screenshot test: the same moment produces the same forty pieces on every run, so a proof frame of
 * the celebration is a frame that can be compared.
 */
private fun confettiPieces(key: String): List<ConfettiPiece> {
    val random = Random(key.hashCode())
    return List(PIECE_COUNT) {
        ConfettiPiece(
            x = random.nextFloat(),
            delay = random.nextFloat() * STAGGER,
            sway = SWAY_MIN_PX + random.nextFloat() * (SWAY_MAX_PX - SWAY_MIN_PX),
            phase = random.nextFloat(),
            spin = (if (random.nextBoolean()) 1f else -1f) * (1f + random.nextFloat() * 2f),
            scale = 0.7f + random.nextFloat() * 0.6f,
            // Gold, and the market's two. Three colours the reader already knows, rather than a
            // party palette that appears once and belongs to nothing.
            tone = random.nextInt(3),
        )
    }
}

/** Enough to read as a burst, few enough to draw in one pass without a layer. */
private const val PIECE_COUNT = 40

/**
 * Six hundred milliseconds.
 *
 * The longest piece of motion in the app by a factor of three, and that is the one place a long
 * animation is right: this fires once per install, the reader is meant to watch it, and it is not in
 * the way of anything — nothing on the screen is blocked while it falls.
 */
private const val BURST_MS = 600

/** How far past the bottom the fall carries, so the last piece leaves rather than vanishing. */
private const val OVERSHOOT = 1.2f

/** How much of the height the staggered starts spread over. */
private const val STAGGER = 0.35f

/** When the fade begins, as a fraction of the fall. */
private const val FADE_FROM = 0.66f

private const val SWAY_MIN_PX = 8f
private const val SWAY_MAX_PX = 28f
private const val SWAY_TURNS = 1.5f
private const val FULL_TURN = 360f
private const val PIECE_WIDTH_PX = 7f
private const val PIECE_HEIGHT_PX = 12f

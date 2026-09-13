package com.coinepro.app

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.coinepro.core.designsystem.CoineProMotionSpecs
import com.coinepro.core.designsystem.PRO_CHART_FA
import com.coinepro.core.designsystem.brandWipe
import com.coinepro.core.designsystem.ProChartMark
import com.coinepro.core.designsystem.ProChartWordmark
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.continuousMotionAllowed
import kotlinx.coroutines.delay

/**
 * The launch: the mark streams in, then the name, black on white, and the app is behind it.
 *
 * ### What the owner asked for
 *
 * TradingView's phone app opens on a white screen, its mark draws itself in, its name follows, and
 * a second or two later the chart is there. «در حد ۱ تا ۲ ثانیه … پس‌زمینه‌ی سفید و لوگو و نوشته
 * کاملاً مشکی». This is that, with the owner's own mark and name.
 *
 * ### How it moves
 *
 * One clock, [DRAW_MS] long and linear, and everything is read off it — each phase eased on its own
 * rather than the clock eased for all of them, for the reason [smooth] gives:
 *
 *  * the **mark** wipes in from left to right over the first half while easing up from 92 % to
 *    full size — a shape being drawn rather than a picture being faded;
 *  * the **name** wipes in from its reading edge — the right in Persian, the left in English —
 *    starting *before the mark has finished*, so the two motions hand over rather than queue;
 *  * both carry a short alpha rise with the wipe, so a frame lost to the app composing underneath
 *    reads as a softer edge rather than as a stopped clip;
 *  * then the sheet holds, still, until the app underneath has drawn a frame, and fades over
 *    [FADE_MS].
 *
 * A wipe rather than a blur or a glow, because the house rules allow neither and because a wipe
 * is what "streaming in" looks like; and one progress value rather than a chain of animations,
 * because a chain is the kind of thing that leaves a frame behind when the reader rotates the phone.
 *
 * ### Why it used to stutter
 *
 * Two reasons, and only the second is about the animation.
 *
 * The first is that **the whole app was composing underneath while this was moving.** A launch is
 * the most expensive composition this app ever does — every controller, every store's first read —
 * and it happens on the same main thread that has to produce a frame every eight milliseconds for
 * the wipe. The wipe is driven from a wall clock, so a blocked thread does not slow it down: it
 * *skips*, which is precisely what «گیر داره» looks like. The sheet now draws its lockup first and
 * the app composes during the **hold**, where nothing is moving and a lost frame is invisible.
 * Waiting is still cheap because it is only a hold, not a longer launch.
 *
 * The second is that the clock ran at a constant speed with a dead stretch in it: the mark
 * finished at 0.36, the name started at 0.34, and from 0.72 to 0.86 nothing at all happened. A
 * constant-speed reveal that stops twice reads as a stall even at a perfect sixty frames. The
 * phases now overlap and each is eased on its own, so both the mark and the name accelerate in and
 * settle out, and neither waits for the other.
 *
 * ### Reduced motion
 *
 * With animations off — `continuousMotionAllowed()` reads the platform scale — the sheet shows the
 * finished lockup for [STILL_MS] and goes. A person who turned animations off asked not to watch
 * shapes draw themselves, and the screenshot renders read the same flag so a capture is the still.
 *
 * ### White and black, in both themes
 *
 * The owner asked for exactly that, and it is also right: the launch has no theme yet to follow,
 * and a sheet that changes colour with the theme is a sheet the reader sees flash from one to the
 * other on the first frame. [Color.White] and [Color.Black] by name, deliberately, not the palette.
 *
 * ### English draws one asset
 *
 * `prochart_wordmark_is_lockup` says the Latin wordmark already carries the mark, so there the
 * lockup alone is drawn and the wipe reveals mark then name on its own. See `ProChartLockup`.
 */
@Composable
fun LaunchSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether to draw the sheet in or show it finished. The device's animation setting, by default. */
    moving: Boolean = continuousMotionAllowed(),
    /**
     * Called once the lockup has finished drawing, before the sheet fades.
     *
     * This is the signal the launch was missing: it tells the caller that the expensive part of the
     * animation is over and the main thread is free, so the app can compose under a *still* sheet
     * rather than under a moving one. See the note on stuttering above. Defaulted to nothing, so a
     * screenshot render or a preview needs to know none of this.
     */
    onDrawn: () -> Unit = {},
    /**
     * Whether the app underneath has drawn a frame and the sheet may go.
     *
     * The hold is bounded by [HOLD_CAP_MS] regardless: a launch that waits forever on a slow first
     * composition is a white screen, which is worse than a chart that arrives half-drawn.
     */
    appReady: Boolean = true,
) {
    val finished by rememberUpdatedState(onFinished)
    val drawn by rememberUpdatedState(onDrawn)
    val ready by rememberUpdatedState(appReady)
    val progress = remember { Animatable(if (moving) 0f else 1f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(moving) {
        if (moving) {
            progress.animateTo(1f, tween(DRAW_MS, easing = LinearEasing))
        } else {
            delay(STILL_MS.toLong())
        }
        // The lockup is complete. Everything from here happens under a sheet that is not moving.
        drawn()
        // A hold, not a wait: the app is composing and the reader is looking at a finished mark.
        // `HOLD_MIN_MS` keeps the lockup on screen long enough to be read even on a fast phone,
        // where the app is ready before the mark has landed.
        val until = HOLD_CAP_MS
        var waited = 0
        while (waited < until && (waited < HOLD_MIN_MS || !ready)) {
            delay(HOLD_STEP_MS.toLong())
            waited += HOLD_STEP_MS
        }
        if (moving) fade.animateTo(1f, tween(FADE_MS, easing = FADE_EASING))
        finished()
    }
    DarkSystemBarIcons()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lockupOnly = booleanResource(DesignR.bool.prochart_wordmark_is_lockup)
    val t = progress.value
    val sheetAlpha = 1f - fade.value
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = sheetAlpha }
            .background(Color.White)
            .semantics { contentDescription = PRO_CHART_FA },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LOCKUP_GAP),
        ) {
            if (!lockupOnly) {
                val drawnFraction = smooth(phase(t, 0f, MARK_UNTIL))
                ProChartMark(
                    tint = Color.Black,
                    modifier = Modifier
                        .height(MARK_HEIGHT)
                        .width(MARK_HEIGHT)
                        .graphicsLayer {
                            val scale = MARK_SCALE_FROM + (1f - MARK_SCALE_FROM) * drawnFraction
                            scaleX = scale
                            scaleY = scale
                            // The alpha rise is the frame-drop insurance: a wipe alone has a hard
                            // edge, and a hard edge that jumps two hundred pixels is a stutter. The
                            // same jump under a rising alpha is a shape arriving.
                            alpha = ease(drawnFraction, ALPHA_OVER)
                        }
                        .brandWipe(drawnFraction, fromLeft = true),
                )
            }
            val named = smooth(
                if (lockupOnly) phase(t, 0f, NAME_UNTIL) else phase(t, NAME_FROM, NAME_UNTIL),
            )
            ProChartWordmark(
                tint = Color.Black,
                modifier = Modifier
                    .width(if (lockupOnly) LOCKUP_WIDTH else NAME_WIDTH)
                    .graphicsLayer { alpha = ease(named, ALPHA_OVER) }
                    // The Latin lockup reads left to right whatever the page does.
                    .brandWipe(named, fromLeft = !rtl || lockupOnly),
            )
        }
    }
}

/**
 * Dark status-bar icons for as long as the sheet is up, and the app's own back when it goes.
 *
 * `enableEdgeToEdge()` leaves the bar transparent and its icons following the *system's* dark
 * mode, which on this app's audience is dark — light icons. Over a white sheet that is a clock and
 * a battery nobody can see for the whole launch. The controller is set here and restored on
 * dispose, so nothing outside this file has to know the launch exists.
 *
 * Skipped where there is no activity window to ask — a preview, a screenshot render — rather than
 * crashing a capture on a cast.
 */
@Composable
private fun DarkSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    DisposableEffect(window) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = true
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

/** Where [t] stands between [from] and [to], clamped to 0..1. */
private fun phase(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

/**
 * Smoothstep: in slowly, out slowly, and the same shape in both directions.
 *
 * **Each phase is eased, and the clock itself is linear** — which is the opposite of the obvious
 * arrangement and is the one that works. An eased clock spends its speed at the front: under a
 * decelerating curve the mark was finished a quarter of the way in and the name spent its last four
 * hundred milliseconds creeping a few pixels, which is the same «گیر» read from the other end.
 * Easing each phase instead gives the mark and the name each their own arrival, inside a clock whose
 * halves are where the numbers say they are.
 */
private fun smooth(fraction: Float): Float {
    val x = fraction.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/** The alpha for a wipe that is [fraction] of the way across, full by [over]. */
private fun ease(fraction: Float, over: Float): Float = smooth((fraction / over).coerceIn(0f, 1f))

/** The draw-in, and the still shown instead when animations are off. */
private const val DRAW_MS = 980
private const val STILL_MS = 900
private const val FADE_MS = 280

/**
 * The hold between the lockup landing and the sheet going.
 *
 * [HOLD_MIN_MS] is the floor: on a fast phone the app is ready before the mark is, and a sheet that
 * vanished the instant it finished drawing would be a flash rather than a launch. [HOLD_CAP_MS] is
 * the ceiling, and it is what stops a slow first composition from becoming a white screen —
 * the app arrives half-drawn, which is what it did before this hold existed.
 */
private const val HOLD_MIN_MS = 260
private const val HOLD_CAP_MS = 900
private const val HOLD_STEP_MS = 20

/** Phases of the one clock, as fractions of [DRAW_MS]. Overlapped: no dead stretch. */
private const val MARK_UNTIL = 0.58f
private const val NAME_FROM = 0.40f
private const val NAME_UNTIL = 1f

/** How much of a wipe an element spends coming up to full opacity. */
private const val ALPHA_OVER = 0.45f

/**
 * The sheet's way out: the house `Exit` curve, accelerating away, because a sheet that lingers at
 * ten per cent opacity over a drawn chart is a grey veil rather than a transition.
 */
private val FADE_EASING = CoineProMotionSpecs.Exit

private const val MARK_SCALE_FROM = 0.92f

private val MARK_HEIGHT = 96.dp
private val NAME_WIDTH = 176.dp
private val LOCKUP_WIDTH = 240.dp
private val LOCKUP_GAP = 24.dp

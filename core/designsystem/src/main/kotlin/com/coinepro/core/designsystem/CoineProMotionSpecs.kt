package com.coinepro.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The three durations and three curves the whole product moves on.
 *
 * Taken from `foundation-v2.css` rather than chosen again, because the app and the web terminal are
 * one product and a reader moving between them should not feel two different machines. The standard
 * curve — `cubic-bezier(.2, 0, 0, 1)` — leaves fast and lands slowly, which is what makes a 160ms
 * transition read as deliberate rather than abrupt.
 */
object CoineProMotionSpecs {

    /** A press, a hover, a colour change under a finger. */
    const val FAST_MS = 100

    /** The default: a sheet, a chip selection, a card expanding. */
    const val STANDARD_MS = 160

    /** A full-screen change, or anything crossing a large distance. */
    const val SLOW_MS = 240

    /** Leaves fast, lands slowly. The default for anything already on screen. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** For something arriving — decelerating into place. */
    val Enter: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** For something leaving — accelerating away. */
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    @Composable
    fun <T> press(): AnimationSpec<T> = remember { tween(FAST_MS, easing = Standard) }

    @Composable
    fun <T> standard(): AnimationSpec<T> = remember { tween(STANDARD_MS, easing = Standard) }

    @Composable
    fun <T> enter(): AnimationSpec<T> = remember { tween(STANDARD_MS, easing = Enter) }

    @Composable
    fun <T> exit(): AnimationSpec<T> = remember { tween(STANDARD_MS, easing = Exit) }
}

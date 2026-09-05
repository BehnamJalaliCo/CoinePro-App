package com.coinepro.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import com.coinepro.core.designsystem.CoineProMotionSpecs
import com.coinepro.core.navigation.AppDestination

/**
 * How one screen becomes the next, and why it is a slide rather than the cross-fade it was.
 *
 * A cross-fade says nothing. Two screens dissolve into each other and the reader is left to work
 * out from the content whether they went deeper or came back — which is the one thing a transition
 * exists to tell them. Every terminal a Persian trader has used, and every system app on their
 * phone, answers that with the same gesture: forward pushes, back pulls. This restores it.
 *
 * ### It is also what makes predictive back real
 *
 * The app targets SDK 36, so the system's predictive back is already on — no manifest opt-in, that
 * flag was removed in Android 15 and is ignored above it. But predictive back has nothing to show
 * unless the pop transition is *seekable*: the gesture drives the animation's progress directly, so
 * a fade could only fade, and a fade that is 30% complete under a half-finished swipe looks like a
 * rendering fault rather than a preview of where Back will land. With a slide, dragging from the
 * edge peels this screen away and reveals the one underneath in proportion to the finger, and
 * letting go part-way springs it back. That is the whole feature, and it costs these four lambdas.
 *
 * ### The direction is Start/End, never left/right
 *
 * Persian is the default locale and the app is right-to-left. `SlideDirection.Start` resolves to
 * *rightwards* under RTL and leftwards under LTR, so one declaration is correct in both: forward
 * always travels the way the script reads, and back always travels against it. Hard pixel offsets
 * would have shipped an app whose Back gesture ran backwards for its primary audience.
 *
 * ### A tab is not a push
 *
 * The five bottom-bar destinations are siblings — nothing is deeper than anything else, and there
 * is no back stack between them. Sliding one into another would claim a hierarchy that does not
 * exist, and would also have to pick a direction with no honest basis for it. Lateral moves keep
 * the cross-fade, which is exactly what a cross-fade is *for*.
 *
 * ### The outgoing screen travels a quarter as far
 *
 * A parallax, and it is not decoration: the screen underneath moving slowly while the one on top
 * moves fast is the depth cue that says the old screen is still there, behind, waiting. Both moving
 * at the same speed reads as two slides on a filmstrip.
 */
private val TAB_ROUTES: Set<String> = AppDestination.entries.map(AppDestination::route).toSet()

/** The fraction of a full slide the screen being covered travels. See the parallax note above. */
private const val PARALLAX = 4

/** Whether both ends of this transition are bottom-bar tabs — siblings, so no push. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.lateral(): Boolean =
    initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES

/**
 * Forward: the arriving screen comes in from the end edge, the covered one drifts a quarter.
 *
 * The slide is a spring and the fade is a tween, on purpose: the slide is *spatial* — a reader can
 * interrupt it with the back gesture half-way and a spring carries the velocity it had — while the
 * fade is an *effect*, and an opacity has no momentum to carry. See `CoineProMotionSpecs`.
 *
 * [motion] is the device's animator scale — "Remove animations" in accessibility, and battery
 * saver. When a reader has turned motion off, screens simply replace each other. Screenshot renders
 * report false as well, so captures are deterministic.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appEnter(motion: Boolean): EnterTransition =
    when {
        !motion -> EnterTransition.None
        lateral() -> fadeIn(tween(CoineProMotionSpecs.STANDARD_MS, easing = CoineProMotionSpecs.Enter))
        else -> slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = CoineProMotionSpecs.defaultSpatialFor(),
        ) + fadeIn(tween(CoineProMotionSpecs.STANDARD_MS, easing = CoineProMotionSpecs.Enter))
    }

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appExit(motion: Boolean): ExitTransition =
    when {
        !motion -> ExitTransition.None
        lateral() -> fadeOut(tween(CoineProMotionSpecs.STANDARD_MS, easing = CoineProMotionSpecs.Exit))
        else -> slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = CoineProMotionSpecs.defaultSpatialFor(),
            targetOffset = { full -> full / PARALLAX },
        ) + fadeOut(tween(CoineProMotionSpecs.SLOW_MS, easing = CoineProMotionSpecs.Exit))
    }

/** Back: the reverse of [appEnter], and the transition the predictive-back gesture seeks through. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopEnter(motion: Boolean): EnterTransition =
    when {
        !motion -> EnterTransition.None
        lateral() -> fadeIn(tween(CoineProMotionSpecs.STANDARD_MS, easing = CoineProMotionSpecs.Enter))
        else -> slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = CoineProMotionSpecs.defaultSpatialFor(),
            initialOffset = { full -> full / PARALLAX },
        ) + fadeIn(tween(CoineProMotionSpecs.STANDARD_MS, easing = CoineProMotionSpecs.Enter))
    }

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopExit(motion: Boolean): ExitTransition =
    when {
        !motion -> ExitTransition.None
        lateral() -> fadeOut(tween(CoineProMotionSpecs.STANDARD_MS, easing = CoineProMotionSpecs.Exit))
        else -> slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = CoineProMotionSpecs.defaultSpatialFor(),
        ) + fadeOut(tween(CoineProMotionSpecs.SLOW_MS, easing = CoineProMotionSpecs.Exit))
    }

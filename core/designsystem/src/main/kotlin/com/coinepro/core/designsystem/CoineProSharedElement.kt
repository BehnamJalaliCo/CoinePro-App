package com.coinepro.core.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The two scopes a shared element needs, published so a screen does not have to be handed them.
 *
 * ### What a shared element is for here
 *
 * A reader taps گلد in the market list and the chart opens. Today the whole page is replaced: the
 * row they were looking at disappears and a header containing the same logo, the same ticker and
 * the same price appears somewhere else. Nothing on screen says the two are the same thing, so the
 * reader has to re-find it — and the app has just told them, in the only language an interface
 * has, that they went *somewhere else* rather than that they opened *this*.
 *
 * A shared element says it instead. The logo and the ticker travel from the row to the header, and
 * the reader's eye follows the one object it was already looking at. That continuity is most of
 * what the owner meant by «روان بودن»: not that the animation is smooth, but that the app never
 * makes you look for something you were already holding.
 *
 * ### Why composition locals rather than parameters
 *
 * The scopes are produced by the navigation host and needed by leaves — a market row inside a lazy
 * list inside a screen, and a header three composables deep in another module. Threading two extra
 * parameters through every layer between them would mean changing the signature of a dozen
 * composables that have no interest in either, and every screen that forgot would silently be the
 * one that does not animate.
 *
 * Null is the ordinary state and must stay harmless: a preview, a screenshot render and a sheet
 * that is not a navigation destination all have no scopes, and [sharedElement] below is a no-op
 * for them.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The destination's own visibility scope. See [LocalSharedTransitionScope]. */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Mark this composable as the same object as the one with the same [key] on the other screen.
 *
 * A no-op wherever either scope is missing, which is every context that is not a navigation
 * destination. That is the whole reason this wrapper exists rather than calling the Compose API
 * directly at each site: a market row is drawn inside the navigation host on one screen and inside
 * a bottom sheet on another, and it must not have to know which.
 *
 * Keys are strings and must be unique across the whole screen pair. They carry the symbol, because
 * two different markets' logos are two different objects and giving them one key would make the
 * gold disc fly to where the bitcoin disc is.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            // The same spring the navigation slide runs on, so the element lands as the page
            // settles rather than arriving early and waiting, or still travelling over a page that
            // has already stopped — and a back gesture half-way through carries its velocity.
            boundsTransform = { _, _ -> CoineProMotionSpecs.defaultSpatialFor() },
        )
    }
}

/** The key for one market's logo, and for its ticker. Built here so the two ends cannot disagree. */
object SharedKeys {
    fun logo(symbol: String): String = "market-logo:" + symbol.uppercase()

    fun ticker(symbol: String): String = "market-ticker:" + symbol.uppercase()

    /** A signal card's mark and ticker, keyed by the signal rather than the market: two cards on one market are two elements. */
    fun signalLogo(id: Long): String = "signal-logo:$id"

    fun signalTicker(id: Long): String = "signal-ticker:$id"
}

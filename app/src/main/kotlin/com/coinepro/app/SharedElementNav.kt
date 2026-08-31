package com.coinepro.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.coinepro.core.designsystem.LocalNavAnimatedScope
import com.coinepro.core.designsystem.LocalSharedTransitionScope

/**
 * A destination that can hand objects to, and take them from, the destination it opens.
 *
 * The same as [composable] with one thing added: the destination's own animation scope is published
 * on [LocalNavAnimatedScope], which is half of what a shared element needs — see
 * `CoineProSharedElement` for the other half and for what a shared element buys.
 *
 * Only the destinations that actually share something use this. Publishing the scope everywhere
 * would cost every screen in the app a composition local it never reads, and would invite a shared
 * key to be added on a pair of screens that are not a pair, which is how an interface ends up
 * flinging a logo across the glass on the way to a settings page.
 *
 * The pair today is the market lists and the chart. A reader taps a row and the row's logo and
 * ticker travel into the chart's header, so what opens is visibly the thing they tapped rather than
 * a new page that happens to be about the same market.
 */
fun NavGraphBuilder.sharedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route = route, arguments = arguments) { entry ->
    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
        content(entry)
    }
}

/**
 * The one coordinate space every shared element travels through.
 *
 * Wraps the navigation graph. The scope it produces is published rather than passed, so a market
 * row buried in a lazy list can take part without every composable between the two learning about
 * transitions; see `CoineProSharedElement`.
 *
 * The experimental opt-in is confined to this file on purpose. The API is the only experimental one
 * the app uses, and keeping the annotation at the two places that need it — here and the modifier —
 * means a version that changes its shape breaks in two files rather than in forty.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedElementHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            content()
        }
    }
}

package com.coinepro.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.CoineProRailItem
import com.coinepro.core.navigation.AppDestination

/**
 * The bottom navigation bar.
 *
 * ### What it is made of
 *
 * The page's own surface, one hairline above it, and a plate under the selected item. Three
 * decisions, each with a reason:
 *
 * * **The surface rather than the stage.** The bar used to take the stage colour so the screen
 *   ended at the device edge, and the result was a row of six glyphs floating on the page with
 *   nothing to say they were a control. A bar is an object; it needs a ground of its own. The
 *   surface rung is one step up, which is the same step every card takes, so the bar reads as part
 *   of the system rather than as a Material default.
 * * **A hairline, not a shadow.** Elevation in this design system is a hairline plus at most one
 *   very soft shadow, and a shadow under a bar that sits on a near-black stage is invisible. The
 *   hairline is what closes the page above it.
 * * **A plate under the selection, not a gold pill.** The gold is spent on the screen's primary
 *   action, and a gold pill down here would put a second one on every screen. The plate is the
 *   same one the navigation rail draws — `SurfaceElevated`, one rung up — so the two chromes
 *   agree about what "you are here" looks like. Weight seconds it: the selected glyph is the
 *   filled one and its label is bold, so the state survives a reader who does not distinguish two
 *   greys and a screenshot compared at low resolution.
 */
/**
 * How a tab tap rearranges the back stack.
 *
 * Here rather than inline at the call site because it is the subject of `BottomBarNavigationTest`,
 * and a copy of these options in a test proves nothing about the ones the bar actually uses.
 *
 * ### Why `restoreState` is conditional
 *
 * The other three lines are the pattern every Android sample ships, and they are right. The
 * conditional is the fix for a bug the owner reported as «from the toolkit, Markets and Chart and
 * everything else switched, and Home did nothing».
 *
 * `popUpTo(start) { saveState = true }` files the entries it pops under the destination the pop
 * lands on, and `restoreState = true` asks for exactly that file. For any tab other than the start
 * destination those are two different keys — Tools is saved under Home, and Markets restores its
 * own — and the pattern works. Tapping **Home** makes them the same key: the toolkit is popped,
 * filed under Home, and then immediately restored on top of it. The reader is returned to the
 * screen they were trying to leave, and no error is raised anywhere, which is why it looked like a
 * dead button rather than a bug.
 *
 * Skipping the restore only on the start destination keeps every other tab's saved stack intact.
 */
internal fun NavOptionsBuilder.tabSwitch(navController: NavHostController, route: String) {
    val start = navController.graph.findStartDestination()
    popUpTo(start.id) { saveState = true }
    launchSingleTop = true
    restoreState = route != start.route
}

@Composable
fun CoineProBottomBar(
    currentRoute: String?,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The edge of the page. See the header: a hairline is how this system elevates.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(CoineProColors.BorderSubtle),
        )
        NavigationBar(
            modifier = Modifier.fillMaxWidth().background(CoineProColors.Surface),
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            AppDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            // Weight marks the selection, not colour: the gold belongs to the
                            // screen's primary action, and a gold tab would put a second one on
                            // every screen.
                            painter = painterResource(destination.icon(selected)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(destination.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CoineProColors.TextPrimary,
                        selectedTextColor = CoineProColors.TextPrimary,
                        unselectedIconColor = CoineProColors.TextMuted,
                        unselectedTextColor = CoineProColors.TextMuted,
                        // The rail's plate, one rung up from the bar — see the header.
                        indicatorColor = CoineProColors.SurfaceElevated,
                    ),
                )
            }
        }
    }
}

/**
 * Kept here rather than on [AppDestination] so `core:navigation` stays a plain module with no
 * Compose dependency — it is consumed by code that has no UI at all.
 */
@DrawableRes
private fun AppDestination.icon(selected: Boolean): Int = when (this) {
    AppDestination.HOME -> if (selected) CoineProIcons.Filled.Home else CoineProIcons.Home
    AppDestination.SIGNALS -> if (selected) CoineProIcons.Filled.Signals else CoineProIcons.Signals
    AppDestination.AI -> if (selected) CoineProIcons.Filled.Ai else CoineProIcons.Ai
    // Markets and Chart have no bespoke nav glyph: they borrow the rising line and the candle,
    // which is where a reader has already met both shapes. They carry both weights like the rest —
    // before, these two alone kept the same outline whether selected or not, so on two of the five
    // tabs the selection was a shade of grey and nothing else.
    // The compass, which is the one glyph in the set that means "look around" rather than
    // "here is a list" — Explore replaced the markets tab and it is not the same promise.
    AppDestination.EXPLORE ->
        if (selected) DesignR.drawable.icon_compass_fill else DesignR.drawable.icon_compass
    AppDestination.CHART -> if (selected) CoineProIcons.Filled.Chart else CoineProIcons.Chart
    AppDestination.COMMUNITY ->
        if (selected) DesignR.drawable.icon_users_fill else DesignR.drawable.icon_users
}

/**
 * The six destinations as [CoineProNavigationRail] wants them.
 *
 * Here rather than in `core:designsystem` for the same reason the glyph pairs are: the rail takes
 * plain items so `core:navigation` stays a module with no Compose dependency at all. One list, so
 * the bar and the rail can never disagree about which glyph belongs to which tab.
 */
@Composable
fun coineProRailItems(): List<CoineProRailItem> = AppDestination.entries.map { destination ->
    CoineProRailItem(
        key = destination.route,
        label = stringResource(destination.labelRes),
        icon = destination.icon(selected = false),
        selectedIcon = destination.icon(selected = true),
    )
}

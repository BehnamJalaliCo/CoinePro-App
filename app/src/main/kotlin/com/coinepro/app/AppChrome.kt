package com.coinepro.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
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
    // **Restore the tab you are going to; do not restore the tab you are already in.**
    //
    // `popUpTo(start) { saveState = true }` files every popped entry under its own destination id,
    // and `restoreState = true` asks for the file belonging to the destination being navigated to.
    // Between two different tabs that is exactly right and is the whole reason a tab remembers
    // where you were in it.
    //
    // Tapping the tab you are *standing in* is the case it gets wrong, because the pop and the
    // restore are then the same file: on the chart tab with the toolkit open, tapping «چارت» pops
    // `[chart-tab, tools]`, saves it under `chart-tab`, and immediately restores it — so the
    // reader is returned to the screen they were trying to leave, with no error raised anywhere.
    // It looks like a dead button rather than a bug, which is how it was reported.
    //
    // This was fixed once, for the start destination only, on the reading that the collision was
    // about `popUpTo`'s own target. It is not: it is about whether the destination being asked for
    // is already on the stack, and the start destination was simply the case that always is. Every
    // other tab had the same fault whenever the screen above it had been opened *from* it.
    //
    // **Asked by lookup rather than by reading the stack.** This used to walk
    // `navController.currentBackStack`, which is a `@RestrictTo` property — public in Kotlin's
    // sense and off-limits in the library's, and lint fails the build over it, correctly: it is
    // the navigation library's own internal state and nothing outside that group is promised it
    // will keep its shape. `getBackStackEntry` is the supported question and answers by throwing
    // when the route is not on the stack, which is why the `runCatching` is here and is not a
    // swallowed error — the exception *is* the answer, and there is no other public way to ask.
    restoreState = runCatching { navController.getBackStackEntry(route) }.isFailure
}

/**
 * The bar's parts, named so a measurement can tell them apart.
 *
 * The height a budget is written against is **app-owned chrome**: the hairline plus the row of
 * tabs. What a gesture-navigation phone adds underneath is the system's, it varies by device and by
 * navigation mode, and adding it into one number would make the budget unmeetable on one phone and
 * trivially met on another. So the two are tagged separately and reported separately.
 */
object AppChromeTestTags {
    /** The whole bar: hairline, tabs, and whatever inset the system asked for. */
    const val BOTTOM_BAR = "bottom-bar"

    /** The hairline that closes the page above the bar. One point, by design. */
    const val BOTTOM_BAR_DIVIDER = "bottom-bar-divider"

    /** The row of five tabs, plus the navigation inset it pads itself with. */
    const val BOTTOM_BAR_CONTENT = "bottom-bar-content"

    /**
     * One tab column, its selection plate, its glyph and its word.
     *
     * Tagged per destination rather than per index, because an index is a fact about the order the
     * enum happens to be written in and a route is a fact about the product. A measurement that
     * says «item 3 is two pixels out» stops meaning anything the day a destination moves.
     */
    fun barItem(route: String): String = "bottom-bar-item-$route"

    fun barPlate(route: String): String = "bottom-bar-plate-$route"

    fun barGlyph(route: String): String = "bottom-bar-glyph-$route"

    fun barLabel(route: String): String = "bottom-bar-label-$route"
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
                .testTag(AppChromeTestTags.BOTTOM_BAR_DIVIDER)
                .background(CoineProColors.BorderSubtle),
        )
        // **This app's own row, not `NavigationBar`.**
        //
        // Material's bar is eighty points tall before anything is in it, and it spends them on a
        // shape this design system does not have: a 64×32 pill that slides under the selected
        // glyph, a tonal elevation over the container colour, and a ripple. The pill is the loudest
        // object on every screen — larger than any chip, any key, any selected state elsewhere in
        // the app — and eighty points is a ninth of a phone given to five words.
        //
        // The reference's bar is a little under seventy, has no pill at all, and marks the selected
        // tab with a filled glyph and the primary ink. That is what this draws: a fixed [BAR_HEIGHT]
        // row of five equal columns, 24 pt glyph over an 11 sp label, a raised neutral behind the
        // selected column and nothing else. No shadow, no tonal elevation, no gold — the gold is the
        // brand and the one commercial action on a page, and a gold tab would put a second one on
        // every screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CoineProColors.Surface)
                // The gesture inset, and only the bottom one. Without it the labels sit under the
                // home indicator on a gesture-navigation phone; with the whole `safeDrawing` set
                // the bar would also take the status bar's inset at the *foot* of the screen.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(BAR_HEIGHT)
                .testTag(AppChromeTestTags.BOTTOM_BAR_CONTENT)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppDestination.entries.forEach { destination ->
                BarItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onSelect = { onSelect(destination) },
                    // Equal columns, so the five never redistribute themselves around the longest
                    // Persian word. Material's own bar weights by content and the bar shifted every
                    // time the language changed.
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One tab: the glyph over its word, on a plate when it is the one in force.
 *
 * The plate is [CoineProColors.SurfaceElevated] — the same raised neutral the chart's interval keys
 * and the Ideas switch use — and it is inset rather than full-bleed so the five columns still read
 * as five. `selectable` rather than `clickable`, because this is a choice among five and that is
 * what TalkBack announces.
 */
@Composable
private fun BarItem(
    destination: AppDestination,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = if (selected) CoineProColors.TextPrimary else CoineProColors.TextMuted
    Column(
        modifier = modifier
            .fillMaxHeight()
            .testTag(AppChromeTestTags.barItem(destination.route))
            .selectable(
                selected = selected,
                role = Role.Tab,
                // No indication. Material's ripple on a bar item is a 64 pt circle that outlives
                // the tap and lands on the screen the reader has already navigated to.
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onSelect,
            )
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = PLATE_WIDTH, height = PLATE_HEIGHT)
                .testTag(AppChromeTestTags.barPlate(destination.route))
                .clip(CoineProShapes.small)
                .background(if (selected) CoineProColors.SurfaceElevated else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                // The filled cut marks the selection, not colour.
                painter = painterResource(destination.icon(selected)),
                contentDescription = null,
                tint = ink,
                modifier = Modifier
                    .size(GLYPH)
                    .testTag(AppChromeTestTags.barGlyph(destination.route)),
            )
        }
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontSize = LABEL_SIZE,
            lineHeight = LABEL_LINE,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = ink,
            maxLines = 1,
            // A label that does not fit takes an ellipsis rather than a second line: the bar's
            // height is fixed, and a wrapped word would be clipped mid-stroke instead.
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 1.dp)
                .testTag(AppChromeTestTags.barLabel(destination.route)),
        )
    }
}

/**
 * The bar's own height, above the gesture inset.
 *
 * Sixty-four: the 26 pt plate, an 11 sp label with its leading, and the air around them. Material's
 * default is eighty and the reference's is a little under seventy, so this is inside the band the
 * owner measured and it gives a phone back sixteen points of page. The insets are added on top,
 * which is why this is a content height rather than a total.
 */
private val BAR_HEIGHT = 64.dp

/** The plate behind the selected glyph. Wide enough to read as a plate, narrow enough to fit five. */
private val PLATE_WIDTH = 48.dp
private val PLATE_HEIGHT = 26.dp

/** The glyph itself, at the size every other 24 pt icon in the app is drawn. */
private val GLYPH = 22.dp

/**
 * Ten and a half points, and the leading that goes with it.
 *
 * `labelSmall` is eleven with 0.4 of tracking, which on the two longest Persian labels — «دیده‌بان»
 * and «ایده‌ها» — is a hair over a fifth of a 393 pt phone. Half a point down and the tracking the
 * style already carries, and all five fit at every width this app supports without ellipsis.
 */
private val LABEL_SIZE = 10.5.sp
private val LABEL_LINE = 13.sp

/**
 * Kept here rather than on [AppDestination] so `core:navigation` stays a plain module with no
 * Compose dependency — it is consumed by code that has no UI at all.
 */
@DrawableRes
private fun AppDestination.icon(selected: Boolean): Int = when (this) {
    // The watchlist's own mark, which is the shape a reader has already met on every row they
    // starred. Borrowing it here rather than drawing a new one is the point: the tab and the
    // action that fills it are the same idea.
    AppDestination.WATCHLIST ->
        if (selected) DesignR.drawable.brand_watchlist_fill else DesignR.drawable.brand_watchlist
    AppDestination.CHART -> if (selected) CoineProIcons.Filled.Chart else CoineProIcons.Chart
    // The compass, which is the one glyph in the set that means "look around" rather than
    // "here is a list" — Explore replaced the markets tab and it is not the same promise.
    AppDestination.EXPLORE ->
        if (selected) DesignR.drawable.icon_compass_fill else DesignR.drawable.icon_compass
    // The four-pointed burst, and **not** the nav set's signal glyph. That one is a pair of
    // faders, which at 24 dp is two vertical bars with a knob on each — a shape a reader cannot
    // tell apart from the candle pair one position over, which is the whole failure a five-glyph
    // bar has to avoid. The burst is the mark this app already uses for a signal, it is the only
    // radial shape in the bar, and it reads as "something worth looking at" rather than as a
    // second chart.
    AppDestination.IDEAS ->
        if (selected) DesignR.drawable.brand_signal_fill else DesignR.drawable.brand_signal
    // Four bars, unweighted. The menu is a directory and the one tab whose selected state does not
    // need to compete: nothing on it is live, and a filled variant would make the quietest
    // destination the loudest shape in the bar.
    AppDestination.MENU -> DesignR.drawable.icon_list_bullets
}

/**
 * The five destinations as [CoineProNavigationRail] wants them.
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

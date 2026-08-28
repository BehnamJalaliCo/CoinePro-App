package com.coinepro.app

import androidx.compose.foundation.background
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.navigation.AppDestination

/**
 * The bottom navigation bar.
 *
 * Deliberately unlike the Material default in two ways, both of them the "آرام" direction speaking:
 * the bar takes the page's own background rather than a raised surface, so the screen ends at the
 * device edge instead of at a second panel; and the selected item is marked by brightness alone,
 * with the indicator pill turned off. The gold is spent on the primary action, and a gold pill down
 * here would put a second one on every screen.
 */
@Composable
fun CoineProBottomBar(
    currentRoute: String?,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth().background(CoineProColors.Stage),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination) },
                icon = {
                    val selected = currentRoute == destination.route
                    Icon(
                        // Weight marks the selection, not colour: the gold belongs to the screen's
                        // primary action, and a gold tab would put a second one on every screen.
                        painter = painterResource(destination.icon(selected)),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CoineProColors.TextPrimary,
                    selectedTextColor = CoineProColors.TextPrimary,
                    unselectedIconColor = CoineProColors.TextMuted,
                    unselectedTextColor = CoineProColors.TextMuted,
                    indicatorColor = Color.Transparent,
                ),
            )
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
    AppDestination.MARKETS -> if (selected) CoineProIcons.Filled.Markets else CoineProIcons.Markets
    AppDestination.CHART -> if (selected) CoineProIcons.Filled.Chart else CoineProIcons.Chart
}

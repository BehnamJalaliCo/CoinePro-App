package com.coinepro.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
                    Icon(
                        imageVector = destination.icon(),
                        contentDescription = null,
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
private fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.HOME -> CoineProIcons.Home
    AppDestination.SIGNALS -> CoineProIcons.Signal
    AppDestination.AI -> CoineProIcons.Ai
    AppDestination.TOOLS -> CoineProIcons.Tools
    AppDestination.ACTIVITY -> CoineProIcons.Activity
}

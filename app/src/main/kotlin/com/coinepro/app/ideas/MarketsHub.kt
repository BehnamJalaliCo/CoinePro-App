package com.coinepro.app.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coinepro.app.R
import com.coinepro.core.designsystem.CoineProColors

/**
 * «بازارها» as one place (5.24.0): the market list, the heat map and the screener behind one switch.
 *
 * They were five doors onto one catalogue — markets, market search, explore, heat map and screener —
 * each a menu row of its own, so a reader looking for «the market» met it five times. Now the list,
 * the map and the scan are three faces of one destination and search is the magnifier on the list.
 * The routes stay, so a deep link or a saved back stack still opens exactly the screen it names.
 */
@Composable
fun MarketsHub(
    markets: @Composable () -> Unit,
    heatmap: @Composable () -> Unit,
    screener: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var face by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        TraySwitch(
            labels = listOf(
                stringResource(R.string.hub_markets),
                stringResource(R.string.hub_heatmap),
                stringResource(R.string.hub_screener),
            ),
            selected = face,
            onSelect = { face = it },
        )
        HorizontalDivider(color = CoineProColors.BorderSubtle)
        when (face) {
            1 -> heatmap()
            2 -> screener()
            else -> markets()
        }
    }
}

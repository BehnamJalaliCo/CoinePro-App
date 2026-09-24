package com.coinepro.feature.chart

import com.coinepro.core.designsystem.pageAccentInk
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles

/**
 * «?» — every keyboard shortcut on the chart, grouped the way the terminal's dialog grouped them
 * (5.14.0). Built from [ChartKeyAction] itself, so what it lists is what the keys do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChartShortcutsSheet(onDismiss: () -> Unit) {
    CoineProSheet(title = stringResource(R.string.keys_title), onDismiss = onDismiss) {
        ChartShortcutsList(modifier = Modifier.verticalScroll(rememberScrollState()))
    }
}

/** The list itself, public so the proof frames can draw it without a sheet's window around it. */
@Composable
fun ChartShortcutsList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One)
            .semantics { contentDescription = "chart-shortcuts" },
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        for (group in ChartKeyGroup.entries) {
            val actions = ChartKeyAction.entries.filter { it.group == group }
            if (actions.isEmpty()) continue
            Text(
                text = stringResource(group.title),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.pageAccentInk,
                modifier = Modifier.padding(top = CoineProSpacing.OneHalf, bottom = 4.dp),
            )
            for (action in actions) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(action.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    // The keys as printed on a keyboard: Latin in both languages, in a key cap.
                    Text(
                        text = BidiText.isolateLtr(action.combo),
                        style = CoineProTextStyles.Numeric.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = CoineProColors.TextPrimary,
                        modifier = Modifier
                            .clip(CoineProShapes.small)
                            .background(CoineProColors.SurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

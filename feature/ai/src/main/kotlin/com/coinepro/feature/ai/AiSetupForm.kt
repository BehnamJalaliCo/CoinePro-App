package com.coinepro.feature.ai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors

/**
 * A row of mutually exclusive choices.
 *
 * Selecting nothing is a real state, not a missing one: the server treats an absent control as
 * "you decide" and shapes the setup itself, so every group here can be cleared back to unset by
 * tapping the active chip again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> AiChoiceRow(
    label: String,
    // Value/label pairs rather than a lambda: stringResource is composable and cannot be called
    // from inside the non-composable mapper a lambda would give us.
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, optionLabel) ->
                AiChip(
                    text = optionLabel,
                    selected = value == selected,
                    onClick = { onSelect(if (value == selected) null else value) },
                )
            }
        }
    }
}

@Composable
internal fun AiChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (selected) CoineProColors.Gold.copy(alpha = 0.16f) else CoineProColors.Surface,
        border = BorderStroke(
            1.dp,
            if (selected) CoineProColors.Gold.copy(alpha = 0.7f) else CoineProColors.Border,
        ),
    ) {
        Text(
            text,
            // A filter chip, not a button. At `labelLarge` with 8dp of padding these were 40dp
            // tall and there are twenty of them on this screen — a wall of chips a reader scrolls
            // past to reach the action. The medium label at 6dp is 30dp, which is where every
            // reference app puts one.
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) CoineProColors.GoldBright else CoineProColors.TextSecondary,
        )
    }
}

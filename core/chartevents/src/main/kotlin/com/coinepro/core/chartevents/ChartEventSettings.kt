package com.coinepro.core.chartevents

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coinepro.core.chart.EventKind
import com.coinepro.core.chart.EventVisibility
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * The five switches, one per kind of event.
 *
 * ### Why this is a settings section and not a toolbar control
 *
 * Five toggles on a chart's toolbar would cost five permanent slots to a decision a reader makes
 * once and then leaves alone for months. Every terminal that ships this puts it under settings for
 * the same reason, and a reader looking for it looks there. What belongs on the chart itself is the
 * mark, not the switch that governs it.
 *
 * ### Kinds nothing feeds are shown, off and disabled
 *
 * Three of the five have no source in this app yet — see [SERVED_EVENT_KINDS]. They are drawn
 * greyed with the reason written under them rather than hidden, because a reader who has seen an
 * earnings marker in another terminal will go looking for the switch, and "it is not here" is a
 * worse answer than "the feed does not send it". A disabled switch cannot be turned on, so nobody
 * ends up with a filter that quietly does nothing.
 */
@Composable
fun ChartEventSettings(
    visibility: EventVisibility,
    onChange: (EventVisibility) -> Unit,
    modifier: Modifier = Modifier,
    served: Set<EventKind> = SERVED_EVENT_KINDS,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Text(
            text = stringResource(R.string.chart_events_settings_title),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.chart_events_settings_note),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        EventKind.entries.forEach { kind ->
            val available = kind in served
            EventKindRow(
                label = kind.label,
                note = stringResource(kind.noteRes(available)),
                // A kind with no feed reads as off whatever is stored, because it is: switching it
                // on would filter for something that never arrives.
                checked = available && visibility.isOn(kind),
                enabled = available,
                onChange = { on -> onChange(visibility.with(kind, on)) },
            )
        }
    }
}

@Composable
private fun EventKindRow(
    label: String,
    note: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = CoineProSpacing.One)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) CoineProColors.TextPrimary else CoineProColors.TextDisabled,
            )
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CoineProColors.OnAccent,
                checkedTrackColor = CoineProColors.Accent,
                uncheckedThumbColor = CoineProColors.TextMuted,
                uncheckedTrackColor = CoineProColors.Surface,
                uncheckedBorderColor = CoineProColors.Border,
            ),
        )
    }
}

/** What the row says under the kind's name: what it shows, or why it cannot show anything. */
@StringRes
private fun EventKind.noteRes(available: Boolean): Int = when {
    !available -> R.string.chart_events_note_unserved
    this == EventKind.NEWS -> R.string.chart_events_note_news
    else -> R.string.chart_events_note_economic
}

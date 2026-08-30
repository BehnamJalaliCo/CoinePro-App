package com.coinepro.feature.admin

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.diagnostics.HubTone
import com.coinepro.core.diagnostics.LogLevel
import com.coinepro.core.diagnostics.LogTag
import com.coinepro.core.diagnostics.LogWindow
import com.coinepro.core.diagnostics.ProbeOutcome
import com.coinepro.core.diagnostics.PushPermission
import com.coinepro.core.model.MarketPlatform

/**
 * The small pieces every section of the panel is built from.
 *
 * Gathered in one file rather than repeated per section because the panel's whole legibility rests
 * on four hundred rows looking like four hundred instances of six shapes. A section that invented
 * its own row spacing would read as a different screen.
 *
 * ### The number rule, stated once
 *
 * A figure a reader compares against something outside this app — a status code, a duration, a byte
 * count, a version, a path — stays Latin and is wrapped in [BidiText.isolateLtr] so it does not
 * reorder inside a Persian line. A count inside a sentence is prose and takes Persian digits. The
 * two helpers below are named for which is which, so a call site has to choose deliberately.
 */

/** A machine figure: Latin, isolated, safe to sit in the middle of a right-to-left line. */
internal fun figure(value: Any): String = BidiText.isolateLtr(value.toString())

/** A count in prose: Persian digits, because this one is being read as a word not compared. */
internal fun count(value: Int): String = value.toPersianDigits()

@Composable
internal fun CardHead(@DrawableRes icon: Int, @StringRes title: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = CoineProColors.TextSecondary,
        )
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
        )
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** The second action, where a section has one. Null draws a single action. */
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CoineProColors.TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        }
        Row {
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, style = MaterialTheme.typography.bodySmall, color = CoineProColors.Accent)
                }
            }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) {
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
internal fun Field(@StringRes label: Int, value: String, accent: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = accent ?: CoineProColors.TextPrimary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CoineProColors.Border))
}

@Composable
internal fun Muted(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = CoineProSpacing.One),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
}

@Composable
internal fun EmptyNote(@StringRes text: Int) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
internal fun Rows(count: Int, row: @Composable (Int) -> Unit) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One),
    ) {
        repeat(count) { index ->
            if (index > 0) Divider()
            row(index)
        }
    }
}

@Composable
internal fun StatusRail(colour: Color) {
    Box(Modifier.width(3.dp).height(28.dp).background(colour, RoundedCornerShape(2.dp)))
}

@Composable
internal fun StatusPill(text: String, colour: Color) {
    Text(
        text = text,
        modifier = Modifier
            .background(colour.copy(alpha = 0.12f), CoineProPillShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        style = MaterialTheme.typography.bodySmall,
        color = colour,
        fontWeight = FontWeight.Bold,
    )
}

/** Neutral on purpose — the verb is context, and colouring it would compete with the status. */
@Composable
internal fun MethodChip(method: String) {
    Text(
        text = figure(method),
        modifier = Modifier
            .background(CoineProColors.SurfaceElevated, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
}

/**
 * A selectable chip, for the tag filter.
 *
 * No ripple, because thirteen of them in a flow all rippling is noise; the fill and the border
 * carry the state instead, which is also what a reader can see in a screenshot.
 */
@Composable
internal fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .background(
                if (selected) CoineProColors.AccentFill.copy(alpha = 0.16f) else CoineProColors.Surface,
                CoineProPillShape,
            )
            .border(
                1.dp,
                if (selected) CoineProColors.Accent else CoineProColors.BorderSubtle,
                CoineProPillShape,
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
        color = if (selected) CoineProColors.Accent else CoineProColors.TextMuted,
    )
}

@Composable
internal fun Toggle(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CoineProColors.OnAccent,
                checkedTrackColor = CoineProColors.AccentFill,
                uncheckedThumbColor = CoineProColors.TextMuted,
                uncheckedTrackColor = CoineProColors.SurfaceElevated,
            ),
        )
    }
}

@Composable
internal fun Tally(modifier: Modifier, label: String, value: String, colour: Color) {
    Column(
        modifier = modifier
            .background(CoineProColors.SurfaceElevated, MaterialTheme.shapes.medium)
            .padding(vertical = CoineProSpacing.OneHalf),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = CoineProTextStyles.RowFigure, color = colour)
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
    }
}

/* ----------------------------------------------------------- translations */

@Composable
internal fun MarketPlatform.label(): String = stringResource(
    when (this) {
        MarketPlatform.COINEPRO_FX -> R.string.admin_platform_forex
        MarketPlatform.TRADEYAR -> R.string.admin_platform_crypto
    },
)

@Composable
internal fun HubTone.colour(): Color = when (this) {
    HubTone.GOOD -> CoineProColors.Buy
    HubTone.WARN -> CoineProColors.Warning
    HubTone.BAD -> CoineProColors.Sell
    HubTone.IDLE -> CoineProColors.TextMuted
}

@Composable
internal fun LogLevel.colour(): Color = when (this) {
    LogLevel.ERROR -> CoineProColors.Sell
    LogLevel.WARN -> CoineProColors.Warning
    LogLevel.INFO -> CoineProColors.TextSecondary
    else -> CoineProColors.TextDisabled
}

/**
 * The level is a letter, not a word.
 *
 * At this density the eye is scanning for the red ones, and «هشدار» spelled out on every row would
 * push the message off the edge. The letters are also what the exported file uses, so an operator
 * reading the screen and a developer reading the file are reading the same alphabet.
 */
internal fun LogLevel.initial(): String = name.first().toString()

@StringRes
internal fun LogWindow.labelRes(): Int = when (this) {
    LogWindow.FIVE_MINUTES -> R.string.admin_window_5m
    LogWindow.ONE_HOUR -> R.string.admin_window_1h
    LogWindow.ONE_DAY -> R.string.admin_window_1d
    LogWindow.ALL -> R.string.admin_window_all
}

/** Tags stay in their own alphabet: they are what the exported file is grepped by. */
internal fun LogTag.label(): String = name

@StringRes
internal fun PushPermission.labelRes(): Int = when (this) {
    PushPermission.NOT_CONFIGURED -> R.string.admin_push_not_configured
    PushPermission.NOT_REQUIRED -> R.string.admin_push_not_required
    PushPermission.AVAILABLE -> R.string.admin_push_available
    PushPermission.DENIED -> R.string.admin_push_denied
    PushPermission.GRANTED -> R.string.admin_push_granted
}

/** Sort key: what is broken comes first, what was never fired comes last. */
internal fun ProbeOutcome.severity(): Int = when (this) {
    ProbeOutcome.NOT_FOUND -> 0
    ProbeOutcome.SERVER_ERROR -> 1
    ProbeOutcome.UNREACHABLE -> 2
    ProbeOutcome.REACHED -> 3
    ProbeOutcome.UNAUTHORIZED -> 4
    ProbeOutcome.SKIPPED -> 5
}

@Composable
internal fun ProbeOutcome.colour(): Color = when (this) {
    ProbeOutcome.REACHED -> CoineProColors.Buy
    // A refusal proves something is listening, which is the answer the prober is really after.
    ProbeOutcome.UNAUTHORIZED -> CoineProColors.TextSecondary
    ProbeOutcome.NOT_FOUND -> CoineProColors.Sell
    ProbeOutcome.SERVER_ERROR, ProbeOutcome.UNREACHABLE -> CoineProColors.Warning
    ProbeOutcome.SKIPPED -> CoineProColors.TextMuted
}

@StringRes
internal fun ProbeOutcome.labelRes(): Int = when (this) {
    ProbeOutcome.REACHED -> R.string.admin_outcome_reached
    ProbeOutcome.UNAUTHORIZED -> R.string.admin_outcome_unauthorized
    ProbeOutcome.NOT_FOUND -> R.string.admin_outcome_not_found
    ProbeOutcome.SERVER_ERROR -> R.string.admin_outcome_server_error
    ProbeOutcome.UNREACHABLE -> R.string.admin_outcome_unreachable
    ProbeOutcome.SKIPPED -> R.string.admin_outcome_skipped
}

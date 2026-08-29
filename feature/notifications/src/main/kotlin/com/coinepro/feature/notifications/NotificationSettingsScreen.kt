package com.coinepro.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPageHeading
import com.coinepro.core.designsystem.CoineProPress
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProRowDivider
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.pressScale
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.core.notifications.NotificationSettings

/** One group of categories, with the heading the screen puts over it. */
data class NotificationSection(
    val title: String,
    val categories: List<NotificationCategory>,
)

/**
 * Everything the app may interrupt somebody about, and the alerts they set themselves.
 *
 * ### The shape is borrowed on purpose
 *
 * Sectioned category switches, marketing separated and off, an operating-system permission row at
 * the top, and a dedicated place for the reader's own alerts. That is what Binance, OKX, Bybit,
 * Kraken and Coinbase all converge on, and converging with them is the point: somebody who has used
 * an exchange app should not have to learn a new idea to find the switch they came for.
 *
 * ### Two things here are not borrowed
 *
 * **Quiet hours.** Not one of those apps has it; they all point at the phone's Do Not Disturb
 * instead. That is a fair answer where the market closes at night and a poor one here, where the
 * reader follows a market that never does — telling somebody to silence their whole phone is
 * telling them to silence their alarm too.
 *
 * **Alerts without an account.** Also not one of them, and not the trackers either. A guest's
 * alerts are evaluated on this device against the public feed, which is why they exist at all and
 * why the screen is honest about what that costs: see [LocalAlertsCard].
 *
 * The screen is a body rather than a controller: every value is a parameter and every change is a
 * callback, so the whole of it renders in a screenshot test and none of it needs a coroutine to be
 * looked at.
 */
@Composable
fun NotificationSettingsScreen(
    settings: NotificationSettings,
    sections: List<NotificationSection>,
    alerts: List<LocalPriceAlert>,
    modifier: Modifier = Modifier,
    /** Null once the reader has granted it, so the row disappears rather than nagging. */
    onOpenSystemSettings: (() -> Unit)? = null,
    systemPermissionGranted: Boolean = true,
    onSetEnabled: (Boolean) -> Unit = {},
    onSetCategory: (NotificationCategory, Boolean) -> Unit = { _, _ -> },
    onSetQuietHours: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onAddAlert: () -> Unit = {},
    onToggleAlert: (LocalPriceAlert, Boolean) -> Unit = { _, _ -> },
    onDeleteAlert: (LocalPriceAlert) -> Unit = {},
    labelFor: (NotificationCategory) -> String,
    noteFor: (NotificationCategory) -> String,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = CoineProSpacing.Four),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        item {
            CoineProPageHeading(
                title = stringRes(R.string.notifications_title),
                eyebrow = stringRes(R.string.notifications_eyebrow),
                subtitle = stringRes(R.string.notifications_subtitle),
            )
        }

        // First, and only while it matters. Every app in this market puts the operating system's
        // own permission at the top of its notification settings, because it is the single largest
        // cause of "your notifications do not work" — and a row that keeps asking after it has been
        // granted is the second largest cause of people not reading this screen.
        if (!systemPermissionGranted && onOpenSystemSettings != null) {
            item { PermissionCard(onOpen = onOpenSystemSettings) }
        }

        item { MasterCard(settings = settings, onSetEnabled = onSetEnabled) }

        item {
            QuietHoursCard(
                settings = settings,
                onSet = onSetQuietHours,
            )
        }

        item {
            LocalAlertsCard(
                alerts = alerts,
                onAdd = onAddAlert,
                onToggle = onToggleAlert,
                onDelete = onDeleteAlert,
            )
        }

        sections.forEach { section ->
            item {
                SectionHeading(
                    title = section.title,
                    section = section,
                    settings = settings,
                    onSetCategory = onSetCategory,
                )
            }
            item {
                CoineProCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    section.categories.forEachIndexed { index, category ->
                        if (index > 0) CoineProRowDivider()
                        CategoryRow(
                            label = labelFor(category),
                            note = noteFor(category),
                            checked = settings.isOn(category),
                            enabled = category.silenceable && settings.enabled,
                            onChange = { on -> onSetCategory(category, on) },
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringRes(R.string.notifications_channel_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            )
        }
    }
}

/**
 * A section's name, and one control that silences the whole section.
 *
 * ### Why a group switch on top of fifteen individual ones
 *
 * Reading a corpus of reviews of this category of app, the two loudest notification complaints are
 * "I get too many" and "I get none" — in that order of volume, and almost never "I want finer
 * control". Fifteen switches answer a question nobody asked while making the question everybody
 * asks take fifteen taps. The individual switches stay, because somebody does want the stop-loss
 * notice and not the news; what changes is that the common case is now one tap.
 *
 * ### Three states, and the third is the honest one
 *
 * All on, all off, and *some* — a section where the reader has made a choice per row. The control
 * reports which, and tapping it does the thing that changes something: a section with anything on
 * turns off, and a section entirely off turns on. Never a checkbox that pretends "some" is "off".
 *
 * Categories that cannot be silenced at all — security — are excluded from both the count and the
 * action, so a "security" section reads «۱ روشن» and offers nothing, rather than offering a switch
 * that would do nothing if tapped.
 */
@Composable
private fun SectionHeading(
    title: String,
    section: NotificationSection,
    settings: NotificationSettings,
    onSetCategory: (NotificationCategory, Boolean) -> Unit,
) {
    val silenceable = section.categories.filter { it.silenceable }
    val on = silenceable.count(settings::isOn)
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                top = CoineProSpacing.One,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        if (silenceable.isEmpty() || !settings.enabled) return@Row
        val turningOn = on == 0
        Text(
            // A count in Persian digits: this is a prose count of rows on a settings screen, not a
            // market figure. The app's rule, and it is the reason this reads «۲ از ۴» and the price
            // beside it reads 92,140.
            text = stringRes(
                if (turningOn) R.string.notifications_section_all_on else R.string.notifications_section_all_off,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.Gold,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    haptics.select()
                    silenceable.forEach { onSetCategory(it, turningOn) }
                }
                .padding(horizontal = CoineProSpacing.Half, vertical = 4.dp),
        )
    }
}

@Composable
private fun PermissionCard(onOpen: () -> Unit) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter),
        accent = CoineProColors.Warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = stringRes(R.string.notifications_permission_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringRes(R.string.notifications_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            CoineProPrimaryButton(
                text = stringRes(R.string.notifications_permission_open),
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MasterCard(settings: NotificationSettings, onSetEnabled: (Boolean) -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter)) {
        CategoryRow(
            label = stringRes(R.string.notifications_master),
            note = stringRes(R.string.notifications_master_note),
            checked = settings.enabled,
            enabled = true,
            onChange = onSetEnabled,
            padded = false,
        )
    }
}

/**
 * The window, as two rows of hours rather than a clock dialog.
 *
 * A time picker for something read as "from about eleven until about seven" is three taps and a
 * confirmation for a decision nobody makes to the minute. Stepping by the hour is one tap, and the
 * hours are Persian digits because this is prose about a person's night, not a market figure.
 */
@Composable
private fun QuietHoursCard(
    settings: NotificationSettings,
    onSet: (Boolean, Int, Int) -> Unit,
) {
    val quiet = settings.quietHours
    CoineProCard(modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter)) {
        Column {
            CategoryRow(
                label = stringRes(R.string.notifications_quiet),
                note = stringRes(R.string.notifications_quiet_note),
                checked = quiet.enabled,
                enabled = settings.enabled,
                onChange = { on -> onSet(on, quiet.fromMinuteOfDay, quiet.toMinuteOfDay) },
                padded = false,
            )
            if (quiet.enabled) {
                CoineProRowDivider()
                HourRow(
                    label = stringRes(R.string.notifications_quiet_from),
                    minuteOfDay = quiet.fromMinuteOfDay,
                    onChange = { minute -> onSet(true, minute, quiet.toMinuteOfDay) },
                )
                HourRow(
                    label = stringRes(R.string.notifications_quiet_to),
                    minuteOfDay = quiet.toMinuteOfDay,
                    onChange = { minute -> onSet(true, quiet.fromMinuteOfDay, minute) },
                )
                Text(
                    text = stringRes(R.string.notifications_quiet_exception),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.padding(top = CoineProSpacing.One),
                )
            }
        }
    }
}

@Composable
private fun HourRow(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    val hour = minuteOfDay / 60
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stepper(label = "−") { onChange(((hour + 23) % 24) * 60) }
            Text(
                text = stringRes(R.string.notifications_quiet_hour, hour.toPersianDigits()),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
            )
            Stepper(label = "+") { onChange(((hour + 1) % 24) * 60) }
        }
    }
}

/**
 * One step of the quiet-hours clock.
 *
 * A bare gold «+» floating beside a number is not a control — it is punctuation that happens to be
 * tappable, with a target barely wider than the glyph, and a reader who misses it twice concludes
 * the hour cannot be changed. So it is a disc: the elevated surface the rest of the app uses for a
 * neutral control, at the 36dp minimum a thumb can actually find, with the same press compression
 * and tick every other control in the app has.
 *
 * The glyph is the ordinary ink rather than gold. Gold in this app means the primary action of the
 * screen, and a settings page's primary action is not "add an hour".
 */
@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .pressScale(interaction, CoineProPress.CONTROL)
            .size(36.dp)
            .clip(CircleShape)
            .background(CoineProColors.SurfaceElevated)
            .clickable(interaction, null) {
                haptics.select()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = CoineProColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The reader's own alerts, and one paragraph of truth about them.
 *
 * The paragraph is the part that matters. These are evaluated on the phone, so they fire while the
 * app is open and otherwise whenever Android next runs its periodic work — which is never more
 * often than every quarter of an hour. Saying so costs a sentence; not saying so costs the reader's
 * trust the first time a move happens between two checks.
 */
@Composable
private fun LocalAlertsCard(
    alerts: List<LocalPriceAlert>,
    onAdd: () -> Unit,
    onToggle: (LocalPriceAlert, Boolean) -> Unit,
    onDelete: (LocalPriceAlert) -> Unit,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter)) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(R.string.notifications_alerts_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = stringRes(
                        R.string.notifications_alerts_count,
                        alerts.size.toPersianDigits(),
                        LocalPriceAlert.MAX_ALERTS.toPersianDigits(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }

            if (alerts.isEmpty()) {
                Text(
                    text = stringRes(R.string.notifications_alerts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            } else {
                alerts.forEach { alert ->
                    AlertRow(
                        alert = alert,
                        onToggle = { on -> onToggle(alert, on) },
                        onDelete = { onDelete(alert) },
                    )
                }
            }

            CoineProPrimaryButton(
                text = stringRes(R.string.notifications_alerts_add),
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringRes(R.string.notifications_alerts_local_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

@Composable
private fun AlertRow(alert: LocalPriceAlert, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = CoineProSpacing.One)) {
            Text(
                text = alertSentence(alert),
                style = MaterialTheme.typography.bodyMedium,
                color = if (alert.active) CoineProColors.TextPrimary else CoineProColors.TextMuted,
            )
            Text(
                text = stringRes(alert.repeat.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = alert.active,
                onCheckedChange = onToggle,
                colors = switchColours(),
            )
            // The bin, not the word. «حذف» in red beside a switch reads as a warning label about
            // the switch rather than as a second control, and it was the only text in the app that
            // was really a button. The glyph keeps the refusal colour and gains a target.
            Icon(
                painter = painterResource(CoineProIcons.Delete),
                contentDescription = stringRes(R.string.notifications_alerts_delete),
                tint = CoineProColors.Sell,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .padding(start = CoineProSpacing.Half)
                    .clip(CircleShape)
                    .clickable {
                        haptics.commit()
                        onDelete()
                    }
                    .padding(8.dp)
                    .size(18.dp),
            )
        }
    }
}

/**
 * One alert as a sentence rather than a row of fields.
 *
 * «BTCUSDT بالای ۶۵٬۰۰۰» reads; a table of symbol / condition / value does not, and a reader
 * checking whether they set the alert they meant to is reading, not auditing. The figure is Latin
 * because it is a market number — the rule the whole app follows.
 */
@Composable
private fun alertSentence(alert: LocalPriceAlert): String {
    // Isolated left-to-right. Without it the bidi algorithm moves the percent sign to the visual
    // left of its own number inside a Persian sentence, so «۵٪ پایین‌تر» renders as «٪۵» — the
    // figure is right and reads wrong, which is the worst of both.
    val amount = BidiText.isolateLtr(
        if (alert.condition.isPercent) {
            stringRes(R.string.notifications_alert_percent, MarketNumberFormatter.price(alert.value, 1))
        } else {
            MarketNumberFormatter.priceAuto(alert.value)
        },
    )
    return stringRes(alert.condition.labelRes(), alert.symbol, amount)
}

@Composable
private fun CategoryRow(
    label: String,
    note: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    padded: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (padded) CoineProSpacing.Two else 0.dp,
                vertical = CoineProSpacing.One,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(end = CoineProSpacing.One).weight(1f)) {
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
            colors = switchColours(),
        )
    }
}

@Composable
private fun switchColours() = SwitchDefaults.colors(
    checkedThumbColor = CoineProColors.OnAccent,
    checkedTrackColor = CoineProColors.AccentFill,
    uncheckedThumbColor = CoineProColors.TextMuted,
    uncheckedTrackColor = CoineProColors.Surface,
    uncheckedBorderColor = CoineProColors.Border,
)

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringRes(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)

internal fun LocalAlertCondition.labelRes(): Int = when (this) {
    LocalAlertCondition.ABOVE -> R.string.alert_condition_above
    LocalAlertCondition.BELOW -> R.string.alert_condition_below
    LocalAlertCondition.PERCENT_UP -> R.string.alert_condition_percent_up
    LocalAlertCondition.PERCENT_DOWN -> R.string.alert_condition_percent_down
    LocalAlertCondition.CHANGE_24H_OVER -> R.string.alert_condition_24h_over
    LocalAlertCondition.CHANGE_24H_UNDER -> R.string.alert_condition_24h_under
}

internal fun com.coinepro.core.notifications.AlertRepeat.labelRes(): Int = when (this) {
    com.coinepro.core.notifications.AlertRepeat.ONCE -> R.string.alert_repeat_once
    com.coinepro.core.notifications.AlertRepeat.DAILY -> R.string.alert_repeat_daily
    com.coinepro.core.notifications.AlertRepeat.ALWAYS -> R.string.alert_repeat_always
}

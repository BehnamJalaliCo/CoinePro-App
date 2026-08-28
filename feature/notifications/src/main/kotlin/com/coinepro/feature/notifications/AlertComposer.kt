package com.coinepro.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert

/**
 * Where an alert is made.
 *
 * ### The four decisions, in the order somebody makes them
 *
 * Which market, what has to happen, at what number, and how often to be told. Nothing else is
 * asked, and the fourth is asked out loud rather than assumed — an "above 65,000" alert with no
 * repeat rule fires on every tick that crosses the line, which around a threshold is a notification
 * every few seconds until the reader turns the app off entirely.
 *
 * ### Percentages are first-class
 *
 * Half the conditions are percentages, because that is how people hold a position in their head:
 * "tell me if it drops five percent" is one thought, and "tell me if it goes below 61,750" is that
 * thought plus arithmetic done under pressure. Binance, Binance.US and TradingView all ship both
 * kinds for the same reason.
 *
 * ### The number is typed in whichever digits the reader's keyboard gives
 *
 * Persian keyboards produce Persian digits and `toDouble` does not read them. Folding to Latin
 * before parsing is the difference between an alert that saves and one that silently refuses on a
 * phone configured the way most of this app's readers configure theirs.
 */
@Composable
fun AlertComposerBody(
    symbol: String,
    onCreate: (LocalPriceAlert) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** The market's price now. Percent conditions are measured from it and it is shown. */
    currentPrice: Double? = null,
    /** True when the device is already holding [LocalPriceAlert.MAX_ALERTS]. */
    full: Boolean = false,
    newId: () -> String = { java.util.UUID.randomUUID().toString().replace("-", "").take(16) },
    nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    var condition by rememberSaveable { mutableStateOf(LocalAlertCondition.ABOVE) }
    var repeat by rememberSaveable { mutableStateOf(AlertRepeat.ONCE) }
    var raw by rememberSaveable(symbol) {
        // Seeded with the current price, so the commonest alert — "a bit above where it is now" —
        // is an edit rather than a blank field somebody has to look the price up to fill.
        mutableStateOf(currentPrice?.let { MarketNumberFormatter.priceAuto(it) }.orEmpty())
    }

    val value = raw.foldDigitsToLatin().filter { it.isDigit() || it == '.' }.toDoubleOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = CoineProColors.TextPrimary,
        )
        currentPrice?.let {
            Text(
                text = stringResource(R.string.alert_new_reference, MarketNumberFormatter.priceAuto(it)),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }

        CoineProChipRow(
            options = LocalAlertCondition.entries.map { entry ->
                CoineProChip(id = entry.id, label = stringResource(entry.shortLabelRes()))
            },
            selectedId = condition.id,
            onSelect = { id -> LocalAlertCondition.fromId(id)?.let { condition = it } },
            compact = true,
        )

        CoineProTextField(
            value = raw,
            onValueChange = { raw = it },
            label = stringResource(
                if (condition.isPercent) R.string.alert_new_value_percent else R.string.alert_new_value_price,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        CoineProChipRow(
            options = AlertRepeat.entries.map { entry ->
                CoineProChip(id = entry.id, label = stringResource(entry.labelRes()))
            },
            selectedId = repeat.id,
            onSelect = { id -> AlertRepeat.fromId(id)?.let { repeat = it } },
            compact = true,
        )

        if (full) {
            Text(
                text = stringResource(
                    R.string.alert_new_full,
                    LocalPriceAlert.MAX_ALERTS.toPersianDigits(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Warning,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            CoineProPrimaryButton(
                text = stringResource(R.string.alert_new_save),
                onClick = {
                    val amount = value ?: return@CoineProPrimaryButton
                    onCreate(
                        LocalPriceAlert(
                            id = newId(),
                            symbol = symbol,
                            condition = condition,
                            value = amount,
                            repeat = repeat,
                            // Captured now and never updated, so a percentage alert does not
                            // re-base itself every time this sheet is opened.
                            referencePrice = currentPrice,
                            createdAtEpochMillis = nowEpochMillis(),
                        ),
                    )
                },
                enabled = value != null && value > 0.0 && !full,
                modifier = Modifier.weight(1f),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.alert_new_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
        }

        // Coinbase says this on their own alerts screen and they are right to. A reader who has
        // just described a price and a direction has described an order; the app has to be the one
        // to say that it is not placing one.
        Text(
            text = stringResource(R.string.alert_new_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/** The composer in a sheet. Split for the reason every sheet in this app is — see `CoineProSheet`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertComposerSheet(
    symbol: String,
    onCreate: (LocalPriceAlert) -> Unit,
    onDismiss: () -> Unit,
    currentPrice: Double? = null,
    full: Boolean = false,
) {
    CoineProSheet(
        title = stringResource(R.string.alert_new_title),
        subtitle = stringResource(R.string.alert_new_subtitle),
        onDismiss = onDismiss,
    ) {
        AlertComposerBody(
            symbol = symbol,
            currentPrice = currentPrice,
            full = full,
            onCreate = onCreate,
            onCancel = onDismiss,
        )
    }
}

/** Short enough for a chip: the sentence forms in [labelRes] carry the symbol and the number. */
internal fun LocalAlertCondition.shortLabelRes(): Int = when (this) {
    LocalAlertCondition.ABOVE -> R.string.alert_short_above
    LocalAlertCondition.BELOW -> R.string.alert_short_below
    LocalAlertCondition.PERCENT_UP -> R.string.alert_short_percent_up
    LocalAlertCondition.PERCENT_DOWN -> R.string.alert_short_percent_down
    LocalAlertCondition.CHANGE_24H_OVER -> R.string.alert_short_24h_over
    LocalAlertCondition.CHANGE_24H_UNDER -> R.string.alert_short_24h_under
}

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.designsystem.proseDigits
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProNote
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
    var everyMinutes by rememberSaveable { mutableIntStateOf(LocalPriceAlert.DEFAULT_REPEAT_MINUTES) }
    // **A price, or nothing — never a zero.**
    //
    // Callers reach this from a chart, where there is always a last price, and now also from a
    // watchlist row, where there may not be: a market the feed has not quoted yet has no price and
    // the caller has no way to say so except by passing what it has. Zero is not a price for any
    // instrument this app carries, so it is read here as "not quoted" — seeding the field with
    // «0» would put a number in front of the reader that is not the market's, and printing
    // «قیمت فعلی: 0» under it would be worse.
    val reference = currentPrice?.takeIf { it.isFinite() && it > 0.0 }
    var raw by rememberSaveable(symbol) {
        // Seeded with the current price, so the commonest alert — "a bit above where it is now" —
        // is an edit rather than a blank field somebody has to look the price up to fill.
        mutableStateOf(reference?.let { MarketNumberFormatter.priceAuto(it) }.orEmpty())
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
        reference?.let {
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

        // **How often «until I see it» speaks** (run Τ2, B9).
        //
        // Drawn only for the policy it belongs to, because an interval beside «once» is a control
        // with nothing to control. Four intervals rather than a number field: the useful answers
        // are five minutes to an hour, a keyboard for that is three taps where this is one, and a
        // field would let somebody type «0» and then wonder why the phone would not stop.
        if (repeat == AlertRepeat.UNTIL_ACKNOWLEDGED) {
            CoineProChipRow(
                options = REPEAT_MINUTES.map { minutes ->
                    CoineProChip(
                        id = minutes.toString(),
                        label = stringResource(R.string.alert_repeat_minutes, minutes.proseDigits()),
                    )
                },
                selectedId = everyMinutes.toString(),
                onSelect = { id -> id?.toIntOrNull()?.let { everyMinutes = it } },
                compact = true,
            )
            CoineProNote(
                R.string.alert_repeat_until_ack_note,
                everyMinutes.proseDigits(),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (full) {
            Text(
                text = stringResource(
                    R.string.alert_new_full,
                    LocalPriceAlert.MAX_ALERTS.proseDigits(),
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
                            // Carried only where it means something. Writing it on every alert
                            // would put an interval on a one-shot, which reads in the stored row
                            // as a policy the reader never chose.
                            repeatEveryMinutes = everyMinutes
                                .takeIf { repeat == AlertRepeat.UNTIL_ACKNOWLEDGED },
                            // Captured now and never updated, so a percentage alert does not
                            // re-base itself every time this sheet is opened.
                            referencePrice = reference,
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

/**
 * The intervals «تا وقتی ببینمش» offers.
 *
 * Five to sixty, because that is the band where the policy does anything: under five the phone's
 * own background scheduler cannot keep up — it runs this work no more often than every fifteen
 * minutes with the app closed, which the alerts screen already says — and over an hour the reader
 * is describing a second alert rather than a repeat. `LocalPriceAlert.effectiveRepeatMillis`
 * clamps anything outside the band, so a stored value from elsewhere cannot produce a stream.
 */
private val REPEAT_MINUTES = listOf(5, 15, 30, 60)

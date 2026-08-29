package com.coinepro.feature.papertrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProConfirmDialog
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.papertrade.PaperRules
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.papertrade.PaperTradeUiState

/**
 * The fill rules, on the screen, in the reader's language.
 *
 * This is not a disclaimer and it is not fine print — it is the specification of the thing the
 * reader is about to use, printed with *their* numbers substituted into it, so the sentence about
 * the assumed spread names the spread they are actually being charged. The brief this feature was
 * built to called it the single most important thing here, and the reasoning is short: a simulator
 * whose fill rule cannot be read is a simulator that has to be trusted, and a trading tool nobody
 * can check is a trading tool nobody should believe.
 *
 * The last three lines are the ones most products leave out. They are the places this simulation is
 * *kinder* than a real venue — a resting order that fills on touch, a wick between two observed
 * prices that never happened, and the fact that this app's own one-tap execution sends market
 * orders only. Leaving them out would make the honest list into an advertisement.
 */
@Composable
fun PaperRulesCard(rules: PaperRules, modifier: Modifier = Modifier) {
    CoineProCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Text(
                text = stringResource(R.string.paper_rules_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Rule(stringResource(R.string.paper_rules_intro), CoineProColors.TextSecondary)
            Rule(stringResource(R.string.paper_rule_spread, percent(rules.assumedSpreadPercent)))
            Rule(stringResource(R.string.paper_rule_slippage, percent(rules.slippagePercent)))
            Rule(stringResource(R.string.paper_rule_resting))
            Rule(stringResource(R.string.paper_rule_stop))
            Rule(stringResource(R.string.paper_rule_unwatched))
            Rule(stringResource(R.string.paper_rule_stale))
            Rule(stringResource(R.string.paper_rule_touch), CoineProColors.Warning)
            Rule(stringResource(R.string.paper_rule_gap), CoineProColors.Warning)
            Rule(stringResource(R.string.paper_rule_execution), CoineProColors.Warning)
        }
    }
}

@Composable
private fun Rule(text: String, tone: androidx.compose.ui.graphics.Color = CoineProColors.TextMuted) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = tone,
        textAlign = TextAlign.Right,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Where the reader sets what the simulation charges them.
 *
 * Editable rather than fixed because there is no honest single value: LBank spot, LBank futures and
 * an MT5 broker's commission are three different numbers, and choosing one would quietly assert a
 * venue this reader may not trade. The sheet says so above the fields.
 *
 * The two destructive actions are both here and both behind a confirmation, and they are different
 * actions on purpose. Starting again keeps every closed trade — a reader who blew an account and
 * wants another go should not have to delete the evidence to get one. Erasing takes the record with
 * it, and says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperAssumptionsSheet(
    state: PaperTradeUiState,
    controller: PaperTradeController,
    onDismiss: () -> Unit,
) {
    val rules = state.book.rules
    var start by rememberSaveable { mutableStateOf(plain(rules.startingBalance)) }
    var leverage by rememberSaveable { mutableStateOf(plain(rules.leverage)) }
    var taker by rememberSaveable { mutableStateOf(plain(rules.takerFeePercent)) }
    var maker by rememberSaveable { mutableStateOf(plain(rules.makerFeePercent)) }
    var slippage by rememberSaveable { mutableStateOf(plain(rules.slippagePercent)) }
    var spread by rememberSaveable { mutableStateOf(plain(rules.assumedSpreadPercent)) }
    var stopOut by rememberSaveable { mutableStateOf(plain(rules.stopOutPercent)) }
    var confirmingReset by rememberSaveable { mutableStateOf(false) }
    var confirmingWipe by rememberSaveable { mutableStateOf(false) }

    CoineProSheet(
        title = stringResource(R.string.paper_settings_title),
        subtitle = stringResource(R.string.paper_settings_note),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Field(stringResource(R.string.paper_setting_start), start) { start = it }
            Field(stringResource(R.string.paper_setting_leverage), leverage) { leverage = it }
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                Field(stringResource(R.string.paper_setting_taker), taker, Modifier.weight(1f)) { taker = it }
                Field(stringResource(R.string.paper_setting_maker), maker, Modifier.weight(1f)) { maker = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                Field(stringResource(R.string.paper_setting_slippage), slippage, Modifier.weight(1f)) { slippage = it }
                Field(stringResource(R.string.paper_setting_spread), spread, Modifier.weight(1f)) { spread = it }
            }
            Field(stringResource(R.string.paper_setting_stopout), stopOut) { stopOut = it }

            CoineProPrimaryButton(
                text = stringResource(R.string.paper_save),
                onClick = {
                    controller.applyRules(
                        rules.copy(
                            startingBalance = start.asNumber() ?: rules.startingBalance,
                            leverage = leverage.asNumber() ?: rules.leverage,
                            takerFeePercent = taker.asZeroOrMore() ?: rules.takerFeePercent,
                            makerFeePercent = maker.asZeroOrMore() ?: rules.makerFeePercent,
                            slippagePercent = slippage.asZeroOrMore() ?: rules.slippagePercent,
                            assumedSpreadPercent = spread.asZeroOrMore() ?: rules.assumedSpreadPercent,
                            stopOutPercent = stopOut.asZeroOrMore() ?: rules.stopOutPercent,
                        ),
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.paper_reset),
                onClick = { confirmingReset = true },
                modifier = Modifier.fillMaxWidth(),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.paper_wipe),
                onClick = { confirmingWipe = true },
                modifier = Modifier.fillMaxWidth(),
            )
            PaperRulesCard(rules, modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (confirmingReset) {
        CoineProConfirmDialog(
            title = stringResource(R.string.paper_reset_title),
            message = stringResource(R.string.paper_reset_message),
            confirmLabel = stringResource(R.string.paper_confirm),
            dismissLabel = stringResource(R.string.paper_dismiss),
            onConfirm = {
                controller.reset(start.asNumber())
                confirmingReset = false
                onDismiss()
            },
            onDismiss = { confirmingReset = false },
            destructive = true,
        )
    }
    if (confirmingWipe) {
        CoineProConfirmDialog(
            title = stringResource(R.string.paper_wipe_title),
            message = stringResource(R.string.paper_wipe_message),
            confirmLabel = stringResource(R.string.paper_confirm),
            dismissLabel = stringResource(R.string.paper_dismiss),
            onConfirm = {
                controller.wipe()
                confirmingWipe = false
                onDismiss()
            },
            onDismiss = { confirmingWipe = false },
            destructive = true,
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    CoineProTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** A number for an input field: Latin digits, no grouping, no isolate marks. */
private fun plain(value: Double): String =
    BidiStrip.plain(MarketNumberFormatter.price(value, if (value >= 100.0) 0 else 2))

/** The percentage inside a rule sentence, isolated so it survives inside right-to-left prose. */
private fun percent(value: Double): String = MarketNumberFormatter.price(value, 2)

/** Zero is a legitimate answer for a cost. [asNumber] refuses it, because a size of zero is not. */
private fun String.asZeroOrMore(): Double? = foldDigitsToLatin()
    .trim()
    .replace('٫', '.')
    .replace(",", "")
    .toDoubleOrNull()
    ?.takeIf { it.isFinite() && it >= 0.0 }

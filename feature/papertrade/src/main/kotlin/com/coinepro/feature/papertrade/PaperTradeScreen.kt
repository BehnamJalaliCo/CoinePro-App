package com.coinepro.feature.papertrade

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.database.PaperTradeEntity
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.papertrade.PaperTrading

/**
 * Trading with no money.
 *
 * For the reader who has installed the app and has not funded an exchange account — which, given
 * that membership needs fifty tether, is most first-day readers. Practising the decision is the
 * only thing they can do with the product on day one, and giving them nothing to do is how an app
 * gets opened once.
 *
 * The screen states what it does not model — fees, spread, swap, funding, slippage — rather than
 * leaving it to be discovered by comparing against a real fill. A simulation that quietly guesses
 * a broker's fee schedule produces a number that *looks* like a real result and is not, which is
 * worse than one that plainly says what it left out.
 *
 * [priceFor] is the live price for a symbol. Passed in rather than fetched here so an open position
 * marks against the same feed the rest of the app is showing — a second source would let this
 * screen and the market list disagree about the same instrument.
 */
@Composable
fun PaperTradeScreen(
    controller: PaperTradeController,
    priceFor: (String) -> Double?,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    var symbol by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf("") }
    var buy by rememberSaveable { mutableStateOf(true) }

    val price = priceFor(symbol.trim().uppercase())
    val quantity = size.foldDigitsToLatin().trim().toDoubleOrNull()
    val armed = price != null && quantity != null && quantity > 0

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        item {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                    Text(
                        text = stringResource(R.string.paper_record_closed, state.record.closed.toPersianDigits()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                    state.record.winRate?.let {
                        Text(
                            text = stringResource(
                                R.string.paper_record_winrate,
                                MarketNumberFormatter.price(it, 1),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (it >= 50) CoineProColors.Buy else CoineProColors.Sell,
                        )
                    }
                    Text(
                        text = stringResource(R.string.paper_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
        }

        item {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                    CoineProTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        label = stringResource(R.string.paper_symbol),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // The live price, shown before the tap. A simulation that fills at a number the
                    // reader never saw is teaching them something untrue about market orders.
                    Text(
                        text = price?.let { MarketNumberFormatter.priceAuto(it) }
                            ?: stringResource(R.string.paper_no_price),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (price == null) CoineProColors.TextMuted else CoineProColors.TextPrimary,
                    )
                    CoineProTextField(
                        value = size,
                        onValueChange = { size = it },
                        label = stringResource(R.string.paper_size),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                        Chip(stringResource(R.string.paper_buy), buy, CoineProColors.Buy) { buy = true }
                        Chip(stringResource(R.string.paper_sell), !buy, CoineProColors.Sell) { buy = false }
                    }
                    CoineProPrimaryButton(
                        text = stringResource(R.string.paper_open),
                        onClick = {
                            controller.open(symbol, buy, price ?: return@CoineProPrimaryButton, quantity ?: return@CoineProPrimaryButton)
                            size = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = armed,
                    )
                }
            }
        }

        if (state.open.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.paper_open_title)) }
            items(state.open, key = PaperTradeEntity::id) { trade ->
                TradeCard(trade, priceFor(trade.symbol)) { live ->
                    controller.close(trade, live)
                }
            }
        }
        if (state.closed.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.paper_closed_title)) }
            items(state.closed, key = PaperTradeEntity::id) { trade ->
                TradeCard(trade, null, null)
            }
        }
    }
}

@Composable
private fun TradeCard(
    trade: PaperTradeEntity,
    livePrice: Double?,
    onClose: ((Double) -> Unit)?,
) {
    val profit = PaperTrading.profit(trade, livePrice)
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = BidiText.isolateLtr(trade.symbol),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = stringResource(if (trade.buy) R.string.paper_buy else R.string.paper_sell) +
                        " · " + MarketNumberFormatter.priceAuto(trade.entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // A dash, not a zero. An open position with no live price has an unknown
                    // result, and a zero would read as a trade going nowhere.
                    text = profit?.let { MarketNumberFormatter.priceAuto(it) } ?: "—",
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        profit == null -> CoineProColors.TextMuted
                        profit >= 0 -> CoineProColors.Buy
                        else -> CoineProColors.Sell
                    },
                )
                if (onClose != null && livePrice != null) {
                    Text(
                        text = stringResource(R.string.paper_close),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Accent,
                        modifier = Modifier
                            .clip(CoineProShapes.small)
                            .clickable { onClose(livePrice) }
                            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = CoineProColors.TextPrimary)
}

@Composable
private fun Chip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
    )
}

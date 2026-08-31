package com.coinepro.feature.papertrade

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.papertrade.PaperFills
import com.coinepro.core.papertrade.PaperOrderRequest
import com.coinepro.core.papertrade.PaperOrderType
import com.coinepro.core.papertrade.PaperQuote
import com.coinepro.core.papertrade.PaperSide
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.papertrade.PaperTradeUiState

/**
 * The order ticket.
 *
 * The part of it worth defending is [FillPreview]. Before the reader commits, the screen prints the
 * price this order is expected to fill at, the fee it will pay, what the spread costs and what
 * slippage adds — the same numbers `PaperFills` will actually charge, computed by the same
 * functions. A ticket that shows the last price and then fills at something else is how a reader
 * concludes the simulator is broken; a ticket that shows the last price and *does* fill there is
 * how they conclude a real broker is cheating them.
 */
@Composable
fun PaperTicket(
    state: PaperTradeUiState,
    controller: PaperTradeController,
    symbol: String,
    onSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var side by rememberSaveable { mutableStateOf(PaperSide.BUY) }
    var type by rememberSaveable { mutableStateOf(PaperOrderType.MARKET) }
    var size by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("") }
    var stop by rememberSaveable { mutableStateOf("") }
    var stopLoss by rememberSaveable { mutableStateOf("") }
    var takeProfit by rememberSaveable { mutableStateOf("") }
    var reduceOnly by rememberSaveable { mutableStateOf(false) }

    val ticker = symbol.trim().uppercase()
    val quote = state.quoteFor(ticker)
    val quantity = size.asNumber()
    val limitPrice = limit.asNumber()
    val stopPrice = stop.asNumber()
    val armed = quote != null &&
        quantity != null &&
        (!type.needsLimit || limitPrice != null) &&
        (!type.needsStop || stopPrice != null)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        item {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                    CoineProTextField(
                        value = symbol,
                        onValueChange = { onSymbol(it.uppercase()) },
                        label = stringResource(R.string.paper_symbol),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Only once there is a symbol to have a price for. With the field empty the
                    // ticket opened on «قیمتی برای این نماد نداریم», which is a report of a failure
                    // that has not happened: there is no symbol yet, so nothing was looked up.
                    if (symbol.isNotBlank()) QuoteLine(quote)
                    SideChips(side) { side = it }
                    TypeChips(type) { type = it }
                    CoineProTextField(
                        value = size,
                        onValueChange = { size = it },
                        label = stringResource(R.string.paper_size),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    SizeShares(state, quote, side, type, limitPrice) { size = it }
                    if (type.needsLimit) {
                        CoineProTextField(
                            value = limit,
                            onValueChange = { limit = it },
                            label = stringResource(R.string.paper_limit_price),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    if (type.needsStop) {
                        CoineProTextField(
                            value = stop,
                            onValueChange = { stop = it },
                            label = stringResource(R.string.paper_stop_price),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                        CoineProTextField(
                            value = stopLoss,
                            onValueChange = { stopLoss = it },
                            label = stringResource(R.string.paper_stop_loss),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        CoineProTextField(
                            value = takeProfit,
                            onValueChange = { takeProfit = it },
                            label = stringResource(R.string.paper_take_profit),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    ToggleRow(
                        label = stringResource(R.string.paper_reduce_only),
                        on = reduceOnly,
                        onToggle = { reduceOnly = !reduceOnly },
                    )
                }
            }
        }

        item {
            FillPreview(
                state = state,
                quote = quote,
                side = side,
                type = type,
                size = quantity,
                limitPrice = limitPrice,
            )
        }

        item {
            CoineProPrimaryButton(
                text = stringResource(
                    if (type == PaperOrderType.MARKET) R.string.paper_open else R.string.paper_place,
                ),
                onClick = {
                    controller.place(
                        PaperOrderRequest(
                            symbol = ticker,
                            side = side,
                            type = type,
                            size = quantity ?: return@CoineProPrimaryButton,
                            limitPrice = limitPrice,
                            stopPrice = stopPrice,
                            stopLoss = stopLoss.asNumber(),
                            takeProfit = takeProfit.asNumber(),
                            reduceOnly = reduceOnly,
                        ),
                    )
                    // The size clears and the symbol does not. A reader placing a second order on
                    // the same instrument is the common case; one placing the same size twice by
                    // accident is the expensive one.
                    size = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = armed && state.loaded,
            )
        }

        item { PaperRulesCard(state.book.rules) }
    }
}

/** The market, before the tap. Both sides where the feed sends them, and a word when it does not. */
@Composable
private fun QuoteLine(quote: PaperQuote?) {
    if (quote == null) {
        Text(
            text = stringResource(R.string.paper_no_price),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextMuted,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = PaperFormat.price(quote.last),
            style = MaterialTheme.typography.titleMedium,
            color = if (quote.stale) CoineProColors.TextMuted else CoineProColors.TextPrimary,
        )
        Text(
            text = if (quote.quotedBook) {
                stringResource(
                    R.string.paper_quote_book,
                    PaperFormat.price(quote.bid),
                    PaperFormat.price(quote.ask),
                )
            } else {
                stringResource(R.string.paper_spread_assumed)
            },
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/**
 * Buy or sell, in the colours the rest of the app gives those two words.
 *
 * Not the chip row. Drawn gold, «خرید» was a third gold object on a screen that already had the
 * selected tab and the order type, and it said nothing about *which* side was chosen beyond being
 * filled — the journal's own buy and sell chips are green and red, and the two screens disagreed
 * about the same control. Direction is the one thing on this ticket that must be readable without
 * reading, so it is the one thing that takes a semantic colour.
 */
@Composable
private fun SideChips(selected: PaperSide, onSelect: (PaperSide) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        SideChip(
            label = stringResource(R.string.paper_buy),
            tone = CoineProColors.Buy,
            selected = selected == PaperSide.BUY,
        ) { onSelect(PaperSide.BUY) }
        SideChip(
            label = stringResource(R.string.paper_sell),
            tone = CoineProColors.Sell,
            selected = selected == PaperSide.SELL,
        ) { onSelect(PaperSide.SELL) }
    }
}

/** Filled in its own colour when chosen, an outline when not. */
@Composable
private fun SideChip(label: String, tone: Color, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberCoineProHaptics()
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clickable {
                haptics.select()
                onClick()
            }
            .background(if (selected) tone else Color.Transparent, CoineProPillShape)
            .border(
                1.dp,
                if (selected) Color.Transparent else CoineProColors.Border,
                CoineProPillShape,
            )
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
    )
}

@Composable
private fun TypeChips(selected: PaperOrderType, onSelect: (PaperOrderType) -> Unit) {
    val options = PaperOrderType.entries.map { CoineProChip(it.name, PaperFormat.typeLabel(it)) }
    CoineProChipRow(
        options = options,
        selectedId = selected.name,
        onSelect = { id -> id?.let { onSelect(PaperOrderType.valueOf(it)) } },
        compact = true,
    )
}

/**
 * Size as a share of what the account can actually carry.
 *
 * Typed in units, most readers guess. Offered as a share of free margin, they are choosing risk,
 * which is the decision they came here to practise. The share is multiplied by leverage because
 * that is what leverage does, and a reader who presses «۱۰۰٪» at twenty times should see the size
 * that follows from it rather than a hundredth of it.
 */
@Composable
private fun SizeShares(
    state: PaperTradeUiState,
    quote: PaperQuote?,
    side: PaperSide,
    type: PaperOrderType,
    limitPrice: Double?,
    onSize: (String) -> Unit,
) {
    val price = when {
        type.needsLimit -> limitPrice
        quote != null -> PaperFills.taking(side, quote, state.book.rules).price
        else -> null
    } ?: return
    val free = state.freeMargin
    if (free <= 0.0 || price <= 0.0) return

    val shares = listOf(25, 50, 75, 100)
    CoineProChipRow(
        options = shares.map { share ->
            CoineProChip(
                share.toString(),
                stringResource(
                    R.string.paper_share_of_margin,
                    BidiText.percent(PaperFormat.count(share)),
                ),
            )
        },
        selectedId = null,
        onSelect = { id ->
            val share = id?.toIntOrNull() ?: return@CoineProChipRow
            val units = free * share / 100.0 * state.book.rules.leverage / price
            onSize(PaperFormat.size(units).let { BidiStrip.plain(it) })
        },
        compact = true,
    )
}

/**
 * What this order is expected to cost, before it is placed.
 *
 * Computed with `PaperFills`, not with a second formula: if this panel and the fill could ever
 * disagree, the panel would be an advertisement rather than a preview.
 */
@Composable
private fun FillPreview(
    state: PaperTradeUiState,
    quote: PaperQuote?,
    side: PaperSide,
    type: PaperOrderType,
    size: Double?,
    limitPrice: Double?,
) {
    if (quote == null || size == null) return
    val rules = state.book.rules
    val marketable = type == PaperOrderType.MARKET ||
        (type == PaperOrderType.LIMIT && limitPrice != null && PaperFills.marketable(side, limitPrice, quote, rules))

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.paper_preview_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                PaperBadge()
            }
            if (marketable) {
                val priced = PaperFills.taking(side, quote, rules, cap = limitPrice)
                val fee = PaperFills.feeFor(priced, size, rules)
                Reading(stringResource(R.string.paper_preview_fill), PaperFormat.price(priced.price))
                Reading(stringResource(R.string.paper_preview_spread), PaperFormat.money(priced.spreadPerUnit * size))
                Reading(stringResource(R.string.paper_preview_slippage), PaperFormat.money(priced.slippagePerUnit * size))
                Reading(stringResource(R.string.paper_preview_fee), PaperFormat.money(fee))
                Reading(
                    stringResource(R.string.paper_preview_margin),
                    PaperFormat.money(priced.price * size / rules.leverage),
                )
            } else {
                val resting = if (type.needsLimit) limitPrice else null
                Text(
                    text = stringResource(
                        if (type.needsStop && !type.needsLimit) R.string.paper_rule_stop else R.string.paper_preview_resting,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (resting != null) {
                    val fee = PaperFills.feeFor(PaperFills.resting(resting), size, rules)
                    Reading(stringResource(R.string.paper_preview_fill), PaperFormat.price(resting))
                    Reading(stringResource(R.string.paper_preview_fee), PaperFormat.money(fee))
                    Reading(
                        stringResource(R.string.paper_preview_margin),
                        PaperFormat.money(resting * size / rules.leverage),
                    )
                }
            }
            if (quote.quotedBook.not()) {
                Text(
                    text = stringResource(R.string.paper_fill_assumed),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.Warning,
                )
            }
        }
    }
}

@Composable
internal fun Reading(label: String, value: String, tone: Color = CoineProColors.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = tone)
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(CoineProShapes.small)
            .clickable(onClick = onToggle)
            .background(if (on) CoineProColors.SurfaceElevated else Color.Transparent, CoineProShapes.small)
            .padding(horizontal = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Icon(
            painter = painterResource(if (on) CoineProIcons.Success else CoineProIcons.Info),
            contentDescription = null,
            tint = if (on) CoineProColors.Buy else CoineProColors.TextMuted,
            modifier = Modifier.padding(vertical = CoineProSpacing.Half),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (on) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
        )
    }
}

/** Strips the bidirectional isolates a formatter adds, for text going back into an input field. */
internal object BidiStrip {
    fun plain(value: String): String = com.coinepro.core.common.BidiText.strip(value).replace(",", "")
}

internal val PaperOrderType.needsLimit: Boolean
    get() = this == PaperOrderType.LIMIT || this == PaperOrderType.STOP_LIMIT

internal val PaperOrderType.needsStop: Boolean
    get() = this == PaperOrderType.STOP || this == PaperOrderType.STOP_LIMIT

/**
 * A number a reader typed.
 *
 * Persian digits fold to Latin because the keyboard gives Persian ones on a Persian device, and the
 * Arabic decimal separator folds to a dot for the same reason. Anything that is not a positive
 * finite number comes back null, which is what disarms the button rather than throwing at the fill.
 */
internal fun String.asNumber(): Double? = foldDigitsToLatin()
    .trim()
    .replace('٫', '.')
    .replace(",", "")
    .toDoubleOrNull()
    ?.takeIf { it.isFinite() && it > 0.0 }

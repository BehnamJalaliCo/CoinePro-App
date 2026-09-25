package com.coinepro.feature.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.coinepro.core.chart.ChartOrderLine
import com.coinepro.core.chart.OrderLineKind
import com.coinepro.core.chart.decimalsFor
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.execution.LiveOrder
import com.coinepro.core.execution.LiveOrderCheck
import com.coinepro.core.execution.LiveOrderRequest
import com.coinepro.core.execution.LiveOrderStatus
import com.coinepro.core.execution.LiveOrderType
import com.coinepro.core.execution.LiveOutcome
import com.coinepro.core.execution.LivePosition
import com.coinepro.core.execution.LiveSide
import com.coinepro.core.execution.LiveTradeController
import com.coinepro.core.execution.LiveTradeState
import com.coinepro.core.execution.PositionSide
import com.coinepro.core.execution.ProtectionState

/** Where a live ticket opens from: the side, type and price a gesture on the chart already chose. */
data class LiveTicketSeed(val side: LiveSide, val type: LiveOrderType, val price: Double?)

/**
 * The live book on the chart (5.18.0): LBank positions and resting orders as lines, and what a
 * drag of one of them means. Pure, like [ChartTradeLines], so the meaning of a drag is a test.
 */
object LiveTradeLines {

    fun of(state: LiveTradeState, symbol: String, words: TradeLineWords, venue: String): List<ChartOrderLine> = buildList {
        state.positionsFor(symbol).forEachIndexed { index, position ->
            position.entryPrice?.let { entry ->
                add(ChartOrderLine("$POSITION$index", entry, OrderLineKind.ENTRY, "$venue ${words.entry}"))
            }
        }
        state.workingFor(symbol).forEach { order ->
            val price = order.price ?: return@forEach
            val side = if (order.side == LiveSide.BUY) words.buy else words.sell
            add(ChartOrderLine("$ORDER${order.id}", price, OrderLineKind.WORKING, "$venue $side"))
        }
    }

    /** The live order a dragged line names, or null when the line is not one. */
    fun orderIdOf(lineId: String): Long? = lineId.takeIf { it.startsWith(ORDER) }?.removePrefix(ORDER)?.toLongOrNull()

    fun isLive(lineId: String): Boolean = lineId.startsWith(PREFIX)

    private const val PREFIX = "live:"
    private const val POSITION = "${PREFIX}pos:"
    private const val ORDER = "${PREFIX}ord:"
}

private fun number(text: String): Double? = text.foldDigitsToLatin().replace(",", "").trim().toDoubleOrNull()

/**
 * The live order ticket and the reader's LBank book for this symbol.
 *
 * Real money, so the flow is two steps and nothing is sent by the first: «review» turns the fields
 * into the exact order and prints it as a sentence, and only «confirm» sends it. The ticket runs
 * the server's own guards first ([LiveOrderCheck]) so a typo is caught before a round trip.
 */
@Composable
fun LiveTradeSheetBody(
    controller: LiveTradeController,
    symbol: String,
    livePrice: Double?,
    seed: LiveTicketSeed?,
) {
    val state by controller.state.collectAsState()
    LaunchedEffect(controller, symbol) { controller.refresh() }

    var side by rememberSaveable(seed) { mutableStateOf(seed?.side ?: LiveSide.BUY) }
    var type by rememberSaveable(seed) { mutableStateOf(seed?.type ?: LiveOrderType.MARKET) }
    var quantity by rememberSaveable { mutableStateOf("") }
    val decimals = decimalsFor(seed?.price ?: livePrice ?: 1.0)
    var price by rememberSaveable(seed) { mutableStateOf(seed?.price?.let { formatPrice(it, decimals).replace(",", "") } ?: "") }
    var stop by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var reduceOnly by rememberSaveable { mutableStateOf(false) }
    var review by remember { mutableStateOf<LiveOrderRequest?>(null) }
    val money: (Double?) -> String = { value -> value?.let { BidiText.isolateLtr(formatPrice(it, 2)) } ?: "—" }
    val priceText: (Double?) -> String = { value -> value?.let { BidiText.isolateLtr(formatPrice(it, decimals)) } ?: "—" }

    val request = number(quantity)?.let { size ->
        controller.draft(
            symbol = symbol,
            side = side,
            type = type,
            quantity = size,
            price = number(price).takeIf { type == LiveOrderType.LIMIT },
            reduceOnly = reduceOnly,
            stopLoss = number(stop).takeIf { !reduceOnly },
            takeProfit = number(target).takeIf { !reduceOnly },
        )
    }
    val problem = request?.let { LiveOrderCheck.check(it, livePrice) }

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
        Text(
            text = stringResource(R.string.live_ticket_real),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.Warning,
        )
        state.account?.let { account ->
            Text(
                text = stringResource(R.string.live_account_line, money(account.available), money(account.equity)),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextSecondary,
            )
            if (!account.tradingEnabled) {
                Text(stringResource(R.string.live_trading_paused), style = MaterialTheme.typography.bodySmall, color = CoineProColors.Sell)
            }
        }

        CoineProSegmentedControl(
            options = listOf(LiveSide.BUY to stringResource(R.string.chart_trade_buy), LiveSide.SELL to stringResource(R.string.chart_trade_sell)),
            selected = side,
            onSelect = { side = it; review = null },
        )
        CoineProSegmentedControl(
            options = listOf(
                LiveOrderType.MARKET to stringResource(R.string.live_type_market),
                LiveOrderType.LIMIT to stringResource(R.string.live_type_limit),
            ),
            selected = type,
            onSelect = { type = it; review = null },
        )
        CoineProTextField(
            value = quantity,
            onValueChange = { quantity = it; review = null },
            label = stringResource(R.string.live_quantity, symbol.removeSuffix("USDT")),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        if (type == LiveOrderType.LIMIT) {
            CoineProTextField(
                value = price,
                onValueChange = { price = it; review = null },
                label = stringResource(R.string.live_price),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Switch(
                checked = reduceOnly,
                onCheckedChange = { reduceOnly = it; review = null },
                modifier = Modifier.semantics { contentDescription = "live-reduce-only" },
            )
            Text(stringResource(R.string.live_reduce_only), style = MaterialTheme.typography.bodyMedium)
        }
        if (!reduceOnly) {
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                CoineProTextField(
                    value = stop,
                    onValueChange = { stop = it; review = null },
                    label = stringResource(R.string.chart_trade_stop),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                CoineProTextField(
                    value = target,
                    onValueChange = { target = it; review = null },
                    label = stringResource(R.string.chart_trade_target),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        problem?.let {
            Text(text = stringResource(problemText(it)), style = MaterialTheme.typography.bodySmall, color = CoineProColors.Sell)
        }

        val pending = review
        if (pending == null) {
            CoineProPrimaryButton(
                text = stringResource(R.string.live_review),
                onClick = { review = request },
                enabled = request != null && problem == null && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.live_review_line,
                        stringResource(if (pending.side == LiveSide.BUY) R.string.chart_trade_buy else R.string.chart_trade_sell),
                        BidiText.isolateLtr(pending.quantity.toString()),
                        symbol,
                        if (pending.type == LiveOrderType.LIMIT) priceText(pending.price) else stringResource(R.string.live_type_market),
                        priceText(pending.stopLoss),
                        priceText(pending.takeProfit),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                CoineProPrimaryButton(
                    text = stringResource(R.string.live_confirm),
                    onClick = {
                        controller.place(pending)
                        review = null
                    },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(text = stringResource(R.string.live_edit), onClick = { review = null }, modifier = Modifier.weight(1f))
            }
        }

        if (state.busy) {
            Text(stringResource(R.string.live_sending), style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
        }
        state.outcome?.let { outcome ->
            Text(
                text = outcomeText(outcome),
                style = MaterialTheme.typography.bodyMedium,
                color = outcomeColor(outcome),
            )
        }

        val positions = state.positionsFor(symbol)
        if (positions.isNotEmpty()) {
            Text(stringResource(R.string.live_positions), style = MaterialTheme.typography.titleSmall)
            positions.forEach { position -> LivePositionCard(position, priceText, money, controller) }
        }
        val working = state.orders.filter { it.symbol.equals(symbol, ignoreCase = true) && it.status != LiveOrderStatus.CANCELLED }.take(8)
        if (working.isNotEmpty()) {
            Text(stringResource(R.string.live_orders), style = MaterialTheme.typography.titleSmall)
            working.forEach { order -> LiveOrderRow(order, priceText, controller) }
        }
    }
}

@Composable
private fun LivePositionCard(
    position: LivePosition,
    priceText: (Double?) -> String,
    money: (Double?) -> String,
    controller: LiveTradeController,
) {
    var stop by rememberSaveable(position.symbol, position.side) { mutableStateOf("") }
    var target by rememberSaveable(position.symbol, position.side) { mutableStateOf("") }
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Text(
                text = stringResource(
                    R.string.live_position_line,
                    stringResource(if (position.side == PositionSide.LONG) R.string.live_long else R.string.live_short),
                    BidiText.isolateLtr(position.quantity.toString()),
                    priceText(position.entryPrice),
                    money(position.unrealizedPnl),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if ((position.unrealizedPnl ?: 0.0) >= 0.0) CoineProColors.Buy else CoineProColors.Sell,
            )
            position.liquidationPrice?.let {
                Text(stringResource(R.string.live_liquidation, priceText(it)), style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                CoineProTextField(
                    value = stop,
                    onValueChange = { stop = it },
                    label = stringResource(R.string.chart_trade_stop),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                CoineProTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = stringResource(R.string.chart_trade_target),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.live_set_protection),
                    onClick = { controller.protect(position.symbol, position.side, number(stop), number(target)) },
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.live_close_half),
                    onClick = { controller.close(position.symbol, position.side, position.quantity / 2) },
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.live_close),
                    onClick = { controller.close(position.symbol, position.side) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LiveOrderRow(order: LiveOrder, priceText: (Double?) -> String, controller: LiveTradeController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = stringResource(
                R.string.live_order_line,
                stringResource(if (order.side == LiveSide.BUY) R.string.chart_trade_buy else R.string.chart_trade_sell),
                BidiText.isolateLtr((order.quantity ?: 0.0).toString()),
                if (order.type == LiveOrderType.LIMIT) priceText(order.price) else stringResource(R.string.live_type_market),
                stringResource(statusText(order.status)),
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (order.canCancel) {
            CoineProSecondaryButton(text = stringResource(R.string.live_cancel), onClick = { controller.cancel(order.id) })
        }
    }
}

private fun problemText(problem: LiveOrderCheck.Problem): Int = when (problem) {
    LiveOrderCheck.Problem.QUANTITY -> R.string.live_problem_quantity
    LiveOrderCheck.Problem.PRICE_MISSING -> R.string.live_problem_price
    LiveOrderCheck.Problem.PRICE_BAND -> R.string.live_problem_band
    LiveOrderCheck.Problem.STOP_SIDE -> R.string.live_problem_stop
    LiveOrderCheck.Problem.TARGET_SIDE -> R.string.live_problem_target
    LiveOrderCheck.Problem.CLOSING_WITH_PROTECTION -> R.string.live_problem_closing
}

internal fun statusText(status: LiveOrderStatus): Int = when (status) {
    LiveOrderStatus.FILLED -> R.string.live_status_filled
    LiveOrderStatus.OPEN, LiveOrderStatus.PARTIAL -> R.string.live_status_open
    LiveOrderStatus.CANCELLED -> R.string.live_status_cancelled
    LiveOrderStatus.REJECTED -> R.string.live_status_rejected
    LiveOrderStatus.FAILED -> R.string.live_status_failed
    LiveOrderStatus.QUEUED, LiveOrderStatus.SUBMITTED, LiveOrderStatus.CANCEL_REQUESTED, LiveOrderStatus.UNKNOWN ->
        R.string.live_status_unknown
}

@Composable
private fun outcomeText(outcome: LiveOutcome): String = when (outcome) {
    is LiveOutcome.Order -> {
        val status = stringResource(statusText(outcome.order.status))
        val reason = outcome.order.errorMessage?.takeIf {
            outcome.order.status == LiveOrderStatus.REJECTED || outcome.order.status == LiveOrderStatus.FAILED
        }
        val protection = when (outcome.order.protection) {
            ProtectionState.PENDING -> stringResource(R.string.live_protection_pending)
            ProtectionState.ARMED -> stringResource(R.string.live_protection_armed)
            ProtectionState.FAILED -> stringResource(R.string.live_protection_failed)
            ProtectionState.NONE -> null
        }
        listOfNotNull(status, reason?.let { "— $it" }, protection).joinToString(" ")
    }
    LiveOutcome.Done -> stringResource(R.string.live_done)
    is LiveOutcome.Refused -> outcome.message ?: stringResource(R.string.live_refused)
    LiveOutcome.Unreachable -> stringResource(R.string.live_unreachable)
}

@Composable
private fun outcomeColor(outcome: LiveOutcome) = when (outcome) {
    is LiveOutcome.Order -> when (outcome.order.status) {
        LiveOrderStatus.FILLED, LiveOrderStatus.OPEN, LiveOrderStatus.PARTIAL -> CoineProColors.Buy
        LiveOrderStatus.REJECTED, LiveOrderStatus.FAILED -> CoineProColors.Sell
        else -> CoineProColors.Warning
    }
    LiveOutcome.Done -> CoineProColors.Buy
    is LiveOutcome.Refused -> CoineProColors.Sell
    LiveOutcome.Unreachable -> CoineProColors.Warning
}

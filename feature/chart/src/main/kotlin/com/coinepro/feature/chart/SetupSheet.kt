package com.coinepro.feature.chart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.coinepro.core.chart.ChartOrder
import com.coinepro.core.chart.TradeFromChart
import com.coinepro.core.chart.TradeSide
import com.coinepro.core.chart.decimalsFor
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.designsystem.CoineProCard
import androidx.compose.ui.res.stringResource
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField

/**
 * The setup on the chart, as numbers.
 *
 * The `longshort` tool has always drawn three lines. This is the other half: what those lines are
 * worth. A reader dragging a stop around is deciding how much they are prepared to lose, and until
 * now the app made them read that off a price axis and do the arithmetic themselves — which is
 * exactly the arithmetic people get wrong under pressure.
 *
 * The numbers come from `TradeFromChart`, the port of the web terminal's own trade maths, and they
 * are read off the drawing rather than kept beside it. A second copy would be the one that
 * disagrees with the lines, and numbers that disagree with the picture are worse than no numbers.
 *
 * Size is asked for as a **risk amount**, never as a lot size. Risk decides size; size does not
 * decide risk. The reverse question — "what do I lose on one lot" — is the same arithmetic and
 * builds the opposite habit.
 */
@Composable
internal fun SetupSheetBody(
    order: ChartOrder,
    symbol: String,
    livePrice: Double?,
    /**
     * Takes the setup as a paper trade: side, entry and size.
     *
     * The order ticket that can honestly exist today. Neither backend serves a free-form order —
     * TradeYar executes against a *published signal* and CoinePro-FX mirrors a copy account — so a
     * button here that claimed to place a real trade would be a button that cannot. What it can do
     * is open the same position with no money, at the entry the reader drew, sized by the risk
     * they entered. `docs/REQUEST4_ACCOUNT_DELETION.md` asks both servers for the real route.
     */
    onPaperTrade: (
        (buy: Boolean, entry: Double, size: Double, stopLoss: Double, takeProfit: Double) -> Unit
    )? = null,
) {
    var riskInput by rememberSaveable { mutableStateOf("") }
    val risk = riskInput.foldDigitsToLatin().trim().toDoubleOrNull()

    // One precision for every price on this sheet, taken from the entry. Per-number precision
    // would print the entry to two places and a small stop distance to six, and a reader comparing
    // them down a column would be comparing different scales.
    val decimals = decimalsFor(order.entry)
    val price: (Double) -> String = { BidiText.isolateLtr(formatPrice(it, decimals)) }

    val ratio = TradeFromChart.riskReward(order)
    val valid = TradeFromChart.isValid(order)
    val riskDistance = kotlin.math.abs(order.entry - order.stopLoss)
    val rewardDistance = kotlin.math.abs(order.takeProfit - order.entry)

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
        Text(
            text = if (order.side == TradeSide.BUY) "موقعیت خرید" else "موقعیت فروش",
            style = MaterialTheme.typography.titleMedium,
            color = if (order.side == TradeSide.BUY) CoineProColors.Buy else CoineProColors.Sell,
        )

        if (!valid) {
            // The geometry check is not a formality. A buy whose target sits below its entry is a
            // stop and a target dragged past each other, and every number under it would be a
            // confident answer to a question the reader did not ask.
            Text(
                text = "چیدمان خط‌ها با جهت معامله نمی‌خواند. حد ضرر و هدف را جابه‌جا کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.Warning,
            )
        }

        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                Line("ورود", price(order.entry))
                Line("حد ضرر", price(order.stopLoss), CoineProColors.Sell)
                Line("هدف", price(order.takeProfit), CoineProColors.Buy)
            }
        }

        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                Line("فاصلهٔ ریسک", price(riskDistance))
                Line("فاصلهٔ ریوارد", price(rewardDistance))
                Line(
                    "پیپ تا حد ضرر",
                    BidiText.isolateLtr(MarketNumberFormatter.price(TradeFromChart.stopPips(order, symbol), 1)),
                )
                Line(
                    label = "ریسک به ریوارد",
                    // Null rather than a number when there is no risk to divide by: a stop sitting
                    // on the entry has no ratio, and anything printed would be read as a real one.
                    value = ratio?.let { BidiText.isolateLtr("1 : ${MarketNumberFormatter.price(it, 2)}") } ?: "—",
                    colour = ratio?.let { if (it >= 2) CoineProColors.Buy else CoineProColors.Warning }
                        ?: CoineProColors.TextMuted,
                )
            }
        }

        CoineProTextField(
            value = riskInput,
            onValueChange = { riskInput = it },
            label = "چقدر حاضرید روی این معامله از دست بدهید؟",
            modifier = Modifier.fillMaxWidth(),
        )
        if (risk != null && risk > 0 && valid) {
            val size = TradeFromChart.positionSize(order, risk)
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                    Line("حجم (واحد)", BidiText.isolateLtr(MarketNumberFormatter.price(size.units, 2)))
                    Line("حجم (لات استاندارد)", BidiText.isolateLtr(MarketNumberFormatter.price(size.lots, 4)))
                }
            }
            Text(
                text = "لات استاندارد بر پایهٔ قرارداد ۱۰۰٬۰۰۰ واحدی حساب می‌شود. اگر کارگزار شما اندازهٔ دیگری دارد، از ماشین‌حساب حجم در «ابزارها» استفاده کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }

        if (risk != null && risk > 0 && valid && onPaperTrade != null) {
            val size = TradeFromChart.positionSize(order, risk)
            CoineProPrimaryButton(
                text = stringResource(R.string.setup_paper_trade),
                onClick = {
                    onPaperTrade(
                        order.side == TradeSide.BUY,
                        order.entry,
                        size.units,
                        // Both lines the reader drew. The sheet computed them and threw them
                        // away, so a setup taken as a paper trade arrived with no stop at all —
                        // which is how a stop ends up not being set: by having to be re-entered.
                        order.stopLoss,
                        order.takeProfit,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.setup_paper_trade_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }

        livePrice?.let { price ->
            val open = TradeFromChart.unrealised(order, price, symbol)
            Line(
                "فاصلهٔ قیمت فعلی تا ورود",
                BidiText.isolateLtr(MarketNumberFormatter.price(open.pips, 1) + " پیپ"),
            )
        }
    }
}

@Composable
private fun Line(label: String, value: String, colour: Color = CoineProColors.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colour,
            fontWeight = FontWeight.Medium,
        )
    }
}

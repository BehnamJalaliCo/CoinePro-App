package com.coinepro.feature.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.pageAccent
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketType

/**
 * [market] is the platform a calculator only makes sense on. Pips and contract sizes are broker
 * concepts, and a fee-per-side spot calculator is an exchange one; offering either to the wrong
 * platform is offering arithmetic whose inputs that reader will never have. Null means it applies
 * to both — risk, geometry and compounding are the same maths everywhere.
 */
private enum class ToolId(
    @StringRes val titleRes: Int,
    @StringRes val eyebrowRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val formulaRes: Int,
    val market: MarketType? = null,
) {
    RISK(R.string.tool_risk_title, R.string.tool_risk_eyebrow, R.string.tool_risk_body, R.string.tool_risk_formula),
    POSITION_SIZE(
        R.string.tool_position_title,
        R.string.tool_position_eyebrow,
        R.string.tool_position_body,
        R.string.tool_position_formula,
        MarketType.FOREX,
    ),
    RISK_REWARD(R.string.tool_rr_title, R.string.tool_rr_eyebrow, R.string.tool_rr_body, R.string.tool_rr_formula),
    DRAWDOWN(R.string.tool_drawdown_title, R.string.tool_drawdown_eyebrow, R.string.tool_drawdown_body, R.string.tool_drawdown_formula),
    PROFIT(
        R.string.tool_profit_title,
        R.string.tool_profit_eyebrow,
        R.string.tool_profit_body,
        R.string.tool_profit_formula,
        MarketType.FOREX,
    ),
    PIP(
        R.string.tool_pip_title,
        R.string.tool_pip_eyebrow,
        R.string.tool_pip_body,
        R.string.tool_pip_formula,
        MarketType.FOREX,
    ),
    CRYPTO_PNL(
        R.string.tool_crypto_title,
        R.string.tool_crypto_eyebrow,
        R.string.tool_crypto_body,
        R.string.tool_crypto_formula,
        MarketType.CRYPTO,
    ),
    COMPOUND(R.string.tool_compound_title, R.string.tool_compound_eyebrow, R.string.tool_compound_body, R.string.tool_compound_formula),
    ;

    fun servesMarket(marketType: MarketType): Boolean = market == null || market == marketType
}

private val riskTools = listOf(ToolId.RISK, ToolId.POSITION_SIZE, ToolId.RISK_REWARD, ToolId.DRAWDOWN)
private val pnlTools = listOf(ToolId.PROFIT, ToolId.PIP, ToolId.CRYPTO_PNL, ToolId.COMPOUND)

@Composable
fun ToolsScreen(
    platform: MarketPlatform = MarketPlatform.TRADEYAR,
    /**
     * The three that need a signed-in session.
     *
     * Nullable, and null is what a guest gets. Every other card on this screen is local to the
     * device — paper trading, the journal, NamaScript — and works with no account at all, which is
     * why the toolkit is one of the surfaces the guest experience opens rather than gates. A card
     * that led to a 401 worded as an outage would be the one broken thing on an otherwise honest
     * screen.
     */
    onOpenConnections: (() -> Unit)? = null,
    onOpenNews: (() -> Unit)? = null,
    onOpenCalendar: (() -> Unit)? = null,
    onOpenHeatmap: (() -> Unit)? = null,
    onOpenScreener: (() -> Unit)? = null,
    /** Opens the closed-trade history. Null on a build with no portfolio screen. */
    onOpenPortfolio: (() -> Unit)? = null,
    /** Opens the academy. Null on a platform that has none — TradeYar. */
    onOpenAcademy: (() -> Unit)? = null,
    /** Opens the trading journal. Local to the device and available on both platforms. */
    onOpenJournal: (() -> Unit)? = null,
    /** Opens paper trading. Local, and the only thing a reader can do on day one. */
    onOpenPaperTrade: (() -> Unit)? = null,
    /** Opens the NamaScript studio. Local, and needs neither an account nor a connection. */
    onOpenScript: (() -> Unit)? = null,
) {
    // Everything closed. The page opened with the risk calculator already unfolded, which put a
    // form with three empty fields between the reader and the list of the other seven — a screen
    // that has decided for you which tool you came for. The quick chips above are how a reader who
    // wants that one gets to it in a tap.
    var expanded by remember { mutableStateOf<ToolId?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ToolkitHeader(
                expanded = expanded,
                onQuickOpen = { expanded = it },
            )
        }
        item { CoineProTeachingStrip(TeachingSurface.TOOLS) }
        item { SectionHeader(stringResource(R.string.tools_risk_group), stringResource(R.string.tools_risk_group_body)) }
        items(riskTools.filter { it.servesMarket(platform.marketType) }, key = ToolId::name) { tool ->
            CalculatorCard(
                tool = tool,
                expanded = expanded == tool,
                onToggle = { expanded = if (expanded == tool) null else tool },
            )
        }
        item { SectionHeader(stringResource(R.string.tools_pnl_group), stringResource(R.string.tools_pnl_group_body)) }
        items(pnlTools.filter { it.servesMarket(platform.marketType) }, key = ToolId::name) { tool ->
            CalculatorCard(
                tool = tool,
                expanded = expanded == tool,
                onToggle = { expanded = if (expanded == tool) null else tool },
            )
        }
        item {
            OperationalTools(
                onOpenNews = onOpenNews,
                onOpenCalendar = onOpenCalendar,
                onOpenHeatmap = onOpenHeatmap,
                onOpenScreener = onOpenScreener,
                onOpenConnections = onOpenConnections,
                onOpenPortfolio = onOpenPortfolio,
                onOpenJournal = onOpenJournal,
                onOpenPaperTrade = onOpenPaperTrade,
                onOpenScript = onOpenScript,
                onOpenAcademy = onOpenAcademy,
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ToolkitHeader(expanded: ToolId?, onQuickOpen: (ToolId) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.tools_eyebrow), color = CoineProColors.Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.tools_headline), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.tools_note),
            color = CoineProColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill(8.toPersianDigits(), stringResource(R.string.tools_f_calculators), CoineProColors.Gold, Modifier.weight(1f))
            // «هیچ», not «۰». The Persian zero is a small circle, and one of them alone at tile
            // size reads as a status dot rather than as a number — the tile said nothing where it
            // was meant to say the strongest thing on the screen: no order ever leaves here. It is
            // a standing claim rather than a count, so it is a word.
            MetricPill(
                stringResource(R.string.tools_f_orders_none),
                stringResource(R.string.tools_f_orders_sent),
                CoineProColors.Buy,
                Modifier.weight(1f),
            )
            MetricPill(stringResource(R.string.tools_local), stringResource(R.string.tools_f_calculation), CoineProColors.Silver, Modifier.weight(1f))
        }
        Text(stringResource(R.string.tools_quick_open), color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickChip(stringResource(R.string.tools_f_risk), expanded == ToolId.RISK) { onQuickOpen(ToolId.RISK) }
            QuickChip(stringResource(R.string.tools_f_position), expanded == ToolId.POSITION_SIZE) { onQuickOpen(ToolId.POSITION_SIZE) }
            QuickChip(stringResource(R.string.tools_f_r_r), expanded == ToolId.RISK_REWARD) { onQuickOpen(ToolId.RISK_REWARD) }
            QuickChip(stringResource(R.string.tools_f_crypto_pnl), expanded == ToolId.CRYPTO_PNL) { onQuickOpen(ToolId.CRYPTO_PNL) }
        }
    }
}

@Composable
private fun MetricPill(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = CoineProColors.SurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) CoineProColors.pageAccent.copy(alpha = 0.18f) else CoineProColors.SurfaceElevated,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) CoineProColors.pageAccent.copy(alpha = 0.65f) else CoineProColors.Border),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CalculatorCard(tool: ToolId, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, if (expanded) CoineProColors.Gold.copy(alpha = 0.5f) else CoineProColors.Border),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(tool.eyebrowRes),
                        color = CoineProColors.Accent,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = stringResource(tool.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(tool.descriptionRes),
                        color = CoineProColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    color = if (expanded) CoineProColors.Gold.copy(alpha = 0.16f) else CoineProColors.Surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (expanded) CoineProColors.Gold.copy(alpha = 0.45f) else CoineProColors.Border),
                ) {
                    Text(
                        stringResource(if (expanded) R.string.tools_close else R.string.tools_open),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (expanded) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = CoineProColors.Border)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormulaStrip(stringResource(tool.formulaRes))
                    when (tool) {
                        ToolId.RISK -> RiskCalculatorContent()
                        ToolId.POSITION_SIZE -> PositionSizeContent()
                        ToolId.RISK_REWARD -> RiskRewardContent()
                        ToolId.DRAWDOWN -> DrawdownContent()
                        ToolId.PROFIT -> ProfitContent()
                        ToolId.PIP -> PipContent()
                        ToolId.CRYPTO_PNL -> CryptoPnlContent()
                        ToolId.COMPOUND -> CompoundContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun FormulaStrip(formula: String) {
    Surface(color = CoineProColors.Surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, CoineProColors.Border)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.tools_formula), color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            FinancialText(formula, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RiskCalculatorContent() {
    var capital by remember { mutableStateOf("") }
    var riskPercent by remember { mutableStateOf("") }
    val result = calculateDoublePair(capital, riskPercent) { a, b -> TraderToolsCalculator.risk(a, b) }
    NumericField(stringResource(R.string.tools_f_account_capital), capital, { capital = it }, stringResource(R.string.tools_f_account_currency))
    NumericField(stringResource(R.string.tools_f_risk), riskPercent, { riskPercent = it }, "%")
    val l_tools_f_capital_after_full_risk = stringResource(R.string.tools_f_capital_after_full_risk)
    val l_tools_f_risk_budget = stringResource(R.string.tools_f_risk_budget)
    CalculationResultPanel(result) { value ->
        val risk = value as RiskResult
        listOf(l_tools_f_risk_budget to TraderToolsFormat.money(risk.riskAmount), l_tools_f_capital_after_full_risk to TraderToolsFormat.money(risk.capitalAfterRisk))
    }
    Assumption(stringResource(R.string.tools_a_risk))
    ResetRow { capital = ""; riskPercent = "" }
}

@Composable
private fun PositionSizeContent() {
    var risk by remember { mutableStateOf("") }
    var stopPips by remember { mutableStateOf("") }
    var pipValue by remember { mutableStateOf("") }
    val result = calculateTriple(risk, stopPips, pipValue) { a, b, c -> TraderToolsCalculator.positionSize(a, b, c) }
    NumericField(stringResource(R.string.tools_f_risk_amount), risk, { risk = it }, stringResource(R.string.tools_f_account_currency))
    NumericField(stringResource(R.string.tools_f_stop_loss_distance), stopPips, { stopPips = it }, stringResource(R.string.tools_f_pips))
    NumericField(stringResource(R.string.tools_f_pip_value_per_standard_lot), pipValue, { pipValue = it }, stringResource(R.string.tools_f_currency_pip_lot))
    val l_tools_f_position_size = stringResource(R.string.tools_f_position_size)
    val l_tools_f_risk_amount = stringResource(R.string.tools_f_risk_amount)
    CalculationResultPanel(result) { value ->
        val sized = value as PositionSizeResult
        listOf(l_tools_f_position_size to "${TraderToolsFormat.decimal(sized.lots, 4)} lots", l_tools_f_risk_amount to TraderToolsFormat.money(sized.monetaryRisk))
    }
    Assumption(stringResource(R.string.tools_a_position))
    ResetRow { risk = ""; stopPips = ""; pipValue = "" }
}

@Composable
private fun RiskRewardContent() {
    var entry by remember { mutableStateOf("") }
    var stop by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(TradeDirection.LONG) }
    DirectionSelector(direction) { direction = it }
    NumericField(stringResource(R.string.tools_f_entry), entry, { entry = it }, stringResource(R.string.tools_f_price))
    NumericField(stringResource(R.string.tools_f_stop_loss), stop, { stop = it }, stringResource(R.string.tools_f_price))
    NumericField(stringResource(R.string.tools_f_take_profit), target, { target = it }, stringResource(R.string.tools_f_price))
    val result = calculateTriple(entry, stop, target) { a, b, c -> TraderToolsCalculator.riskReward(a, b, c, direction) }
    val l_tools_f_reward_distance = stringResource(R.string.tools_f_reward_distance)
    val l_tools_f_reward_risk = stringResource(R.string.tools_f_reward_risk)
    val l_tools_f_risk_distance = stringResource(R.string.tools_f_risk_distance)
    CalculationResultPanel(result) { value ->
        val rr = value as RiskRewardResult
        listOf(l_tools_f_risk_distance to TraderToolsFormat.decimal(rr.riskDistance, 5), l_tools_f_reward_distance to TraderToolsFormat.decimal(rr.rewardDistance, 5), l_tools_f_reward_risk to "${TraderToolsFormat.decimal(rr.ratio, 2)} R")
    }
    Assumption(stringResource(R.string.tools_a_rr))
    ResetRow { entry = ""; stop = ""; target = ""; direction = TradeDirection.LONG }
}

@Composable
private fun DrawdownContent() {
    var balance by remember { mutableStateOf("") }
    var lossPercent by remember { mutableStateOf("") }
    var losses by remember { mutableStateOf("") }
    NumericField(stringResource(R.string.tools_f_starting_balance), balance, { balance = it }, stringResource(R.string.tools_f_account_currency))
    NumericField(stringResource(R.string.tools_f_loss_per_trade), lossPercent, { lossPercent = it }, "%")
    IntegerField(stringResource(R.string.tools_f_consecutive_losses), losses, { losses = it }, stringResource(R.string.tools_f_trades))
    val result: ToolCalculation<*>? = if (balance.isBlank() || lossPercent.isBlank() || losses.isBlank()) null else {
        val a = balance.toDoubleOrNull(); val b = lossPercent.toDoubleOrNull(); val c = losses.toIntOrNull()
        if (a == null || b == null || c == null) ToolCalculation.Invalid(R.string.tools_input, R.string.tools_invalid) else TraderToolsCalculator.drawdown(a, b, c)
    }
    val l_tools_f_drawdown = stringResource(R.string.tools_f_drawdown)
    val l_tools_f_ending_balance = stringResource(R.string.tools_f_ending_balance)
    val l_tools_f_recovery_required = stringResource(R.string.tools_f_recovery_required)
    CalculationResultPanel(result) { value ->
        val d = value as DrawdownResult
        listOf(l_tools_f_ending_balance to TraderToolsFormat.money(d.endingBalance), l_tools_f_drawdown to TraderToolsFormat.percent(d.drawdownPercent), l_tools_f_recovery_required to TraderToolsFormat.percent(d.recoveryPercent))
    }
    Assumption(stringResource(R.string.tools_a_drawdown))
    ResetRow { balance = ""; lossPercent = ""; losses = "" }
}

@Composable
private fun ProfitContent() {
    var entry by remember { mutableStateOf("") }
    var exit by remember { mutableStateOf("") }
    var lots by remember { mutableStateOf("") }
    var contract by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(TradeDirection.LONG) }
    DirectionSelector(direction) { direction = it }
    NumericField(stringResource(R.string.tools_f_entry), entry, { entry = it }, stringResource(R.string.tools_f_price))
    NumericField(stringResource(R.string.tools_f_exit), exit, { exit = it }, stringResource(R.string.tools_f_price))
    NumericField(stringResource(R.string.tools_f_lots), lots, { lots = it }, stringResource(R.string.tools_f_standard_lots))
    NumericField(stringResource(R.string.tools_f_contract_size), contract, { contract = it }, stringResource(R.string.tools_f_units_lot))
    val result: ToolCalculation<*>? = calculateQuad(entry, exit, lots, contract) { a, b, c, d -> TraderToolsCalculator.profit(a, b, c, d, direction) }
    val l_tools_f_estimated_pnl = stringResource(R.string.tools_f_estimated_pnl)
    val l_tools_f_signed_price_move = stringResource(R.string.tools_f_signed_price_move)
    CalculationResultPanel(result) { value ->
        val profit = value as ProfitResult
        listOf(l_tools_f_estimated_pnl to TraderToolsFormat.money(profit.pnl), l_tools_f_signed_price_move to TraderToolsFormat.decimal(profit.priceMove, 5))
    }
    Assumption(stringResource(R.string.tools_a_metal))
    ResetRow { entry = ""; exit = ""; lots = ""; contract = ""; direction = TradeDirection.LONG }
}

@Composable
private fun PipContent() {
    var entry by remember { mutableStateOf("") }
    var exit by remember { mutableStateOf("") }
    var lots by remember { mutableStateOf("") }
    var pipSize by remember { mutableStateOf("") }
    var pipValue by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(TradeDirection.LONG) }
    DirectionSelector(direction) { direction = it }
    NumericField(stringResource(R.string.tools_f_entry), entry, { entry = it }, stringResource(R.string.tools_f_price))
    NumericField(stringResource(R.string.tools_f_exit), exit, { exit = it }, stringResource(R.string.tools_f_price))
    NumericField(stringResource(R.string.tools_f_lots), lots, { lots = it }, stringResource(R.string.tools_f_standard_lots))
    NumericField(stringResource(R.string.tools_f_pip_size), pipSize, { pipSize = it }, stringResource(R.string.tools_f_price_units_pip))
    NumericField(stringResource(R.string.tools_f_pip_value_per_lot), pipValue, { pipValue = it }, stringResource(R.string.tools_f_currency_pip_lot))
    val result: ToolCalculation<*>? = if (listOf(entry, exit, lots, pipSize, pipValue).any(String::isBlank)) null else {
        val values = listOf(entry, exit, lots, pipSize, pipValue).map(String::toDoubleOrNull)
        if (values.any { it == null }) ToolCalculation.Invalid(R.string.tools_input, R.string.tools_invalid)
        else TraderToolsCalculator.pips(values[0]!!, values[1]!!, values[2]!!, values[3]!!, values[4]!!, direction)
    }
    val l_tools_f_estimated_pnl = stringResource(R.string.tools_f_estimated_pnl)
    val l_tools_f_signed_pips = stringResource(R.string.tools_f_signed_pips)
    CalculationResultPanel(result) { value ->
        val pip = value as PipResult
        listOf(l_tools_f_signed_pips to TraderToolsFormat.decimal(pip.pips, 1), l_tools_f_estimated_pnl to TraderToolsFormat.money(pip.pnl))
    }
    Assumption(stringResource(R.string.tools_a_pip))
    ResetRow { entry = ""; exit = ""; lots = ""; pipSize = ""; pipValue = ""; direction = TradeDirection.LONG }
}

@Composable
private fun CryptoPnlContent() {
    var entry by remember { mutableStateOf("") }
    var exit by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(TradeDirection.LONG) }
    DirectionSelector(direction) { direction = it }
    NumericField(stringResource(R.string.tools_f_entry), entry, { entry = it }, "USDT")
    NumericField(stringResource(R.string.tools_f_exit), exit, { exit = it }, "USDT")
    NumericField(stringResource(R.string.tools_f_quantity), quantity, { quantity = it }, stringResource(R.string.tools_f_base_asset))
    NumericField(stringResource(R.string.tools_f_fee_per_side), fee, { fee = it }, "%")
    val result: ToolCalculation<*>? = calculateQuad(entry, exit, quantity, fee) { a, b, c, d -> TraderToolsCalculator.cryptoPnl(a, b, c, d, direction) }
    val l_tools_f_fees = stringResource(R.string.tools_f_fees)
    val l_tools_f_gross_pnl = stringResource(R.string.tools_f_gross_pnl)
    val l_tools_f_net_pnl = stringResource(R.string.tools_f_net_pnl)
    val l_tools_f_return = stringResource(R.string.tools_f_return)
    CalculationResultPanel(result) { value ->
        val pnl = value as CryptoPnlResult
        listOf(l_tools_f_gross_pnl to TraderToolsFormat.money(pnl.grossPnl, "USDT "), l_tools_f_fees to TraderToolsFormat.money(pnl.fees, "USDT "), l_tools_f_net_pnl to TraderToolsFormat.money(pnl.netPnl, "USDT "), l_tools_f_return to TraderToolsFormat.percent(pnl.returnPercent))
    }
    Assumption(stringResource(R.string.tools_a_crypto))
    ResetRow { entry = ""; exit = ""; quantity = ""; fee = ""; direction = TradeDirection.LONG }
}

@Composable
private fun CompoundContent() {
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var periods by remember { mutableStateOf("") }
    NumericField(stringResource(R.string.tools_f_principal), principal, { principal = it }, stringResource(R.string.tools_f_account_currency))
    NumericField(stringResource(R.string.tools_f_return_per_period), rate, { rate = it }, "%")
    IntegerField(stringResource(R.string.tools_f_periods), periods, { periods = it }, stringResource(R.string.tools_u_periods))
    val result: ToolCalculation<*>? = if (principal.isBlank() || rate.isBlank() || periods.isBlank()) null else {
        val a = principal.toDoubleOrNull(); val b = rate.toDoubleOrNull(); val c = periods.toIntOrNull()
        if (a == null || b == null || c == null) ToolCalculation.Invalid(R.string.tools_input, R.string.tools_invalid) else TraderToolsCalculator.compound(a, b, c)
    }
    val l_tools_f_ending_balance = stringResource(R.string.tools_f_ending_balance)
    val l_tools_f_net_change = stringResource(R.string.tools_f_net_change)
    CalculationResultPanel(result) { value ->
        val c = value as CompoundResult
        listOf(l_tools_f_ending_balance to TraderToolsFormat.money(c.endingBalance), l_tools_f_net_change to TraderToolsFormat.money(c.profit))
    }
    Assumption(stringResource(R.string.tools_a_compound))
    ResetRow { principal = ""; rate = ""; periods = "" }
}

@Composable
private fun NumericField(label: String, value: String, onValueChange: (String) -> Unit, unit: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(unit, color = CoineProColors.TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CoineProColors.Gold,
            unfocusedBorderColor = CoineProColors.Border,
            focusedContainerColor = CoineProColors.Surface,
            unfocusedContainerColor = CoineProColors.Surface,
        ),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun IntegerField(label: String, value: String, onValueChange: (String) -> Unit, unit: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(unit, color = CoineProColors.TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CoineProColors.Gold,
            unfocusedBorderColor = CoineProColors.Border,
            focusedContainerColor = CoineProColors.Surface,
            unfocusedContainerColor = CoineProColors.Surface,
        ),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun DirectionSelector(direction: TradeDirection, onChange: (TradeDirection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tools_direction), color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionPill(stringResource(R.string.tools_long), direction == TradeDirection.LONG, CoineProColors.Buy, Modifier.weight(1f)) { onChange(TradeDirection.LONG) }
            DirectionPill(stringResource(R.string.tools_short), direction == TradeDirection.SHORT, CoineProColors.Sell, Modifier.weight(1f)) { onChange(TradeDirection.SHORT) }
        }
    }
}

@Composable
private fun DirectionPill(label: String, selected: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) accent.copy(alpha = 0.14f) else CoineProColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.65f) else CoineProColors.Border),
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 12.dp),
            color = if (selected) accent else CoineProColors.TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun CalculationResultPanel(result: ToolCalculation<*>?, rows: (Any) -> List<Pair<String, String>>) {
    when (result) {
        null -> Surface(color = CoineProColors.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CoineProColors.Border)) {
            Text(stringResource(R.string.tools_enter_all), modifier = Modifier.padding(16.dp), color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        is ToolCalculation.Invalid -> Surface(color = CoineProColors.Sell.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CoineProColors.Sell.copy(alpha = 0.35f))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(result.fieldRes), color = CoineProColors.Sell, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(result.messageRes, stringResource(result.fieldRes)), color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        is ToolCalculation.Success<*> -> ResultRows(rows(requireNotNull(result.value)))
    }
}

@Composable
private fun ResultRows(rows: List<Pair<String, String>>) {
    Surface(color = CoineProColors.Gold.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CoineProColors.Gold.copy(alpha = 0.34f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tools_result), color = CoineProColors.Gold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = CoineProColors.Border.copy(alpha = 0.7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(row.first, modifier = Modifier.weight(1f), color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(10.dp))
                    FinancialText(row.second, color = CoineProColors.TextPrimary, style = CoineProTextStyles.TileFigure)
                }
            }
        }
    }
}

@Composable
private fun FinancialText(
    text: String,
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight? = null,
) {
    Text(text = text, color = color, style = style.copy(textDirection = TextDirection.Ltr), fontWeight = fontWeight)
}

@Composable
private fun Assumption(text: String) {
    Text(stringResource(R.string.tools_assumption, text), color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun ResetRow(onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onReset) { Text(stringResource(R.string.tools_reset)) }
    }
}

@Composable
private fun OperationalTools(
    onOpenNews: (() -> Unit)?,
    onOpenCalendar: (() -> Unit)?,
    onOpenHeatmap: (() -> Unit)? = null,
    onOpenScreener: (() -> Unit)? = null,
    onOpenConnections: (() -> Unit)?,
    onOpenPortfolio: (() -> Unit)?,
    onOpenAcademy: (() -> Unit)?,
    onOpenJournal: (() -> Unit)?,
    onOpenPaperTrade: (() -> Unit)?,
    onOpenScript: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tools_connected), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.tools_connected_body), color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        // First of the four, because it is the only one that needs nothing: no account, no
        // connection, no network. A reader can start keeping a journal on the day they install.
        onOpenPaperTrade?.let {
            OperationalCard(
                title = stringResource(R.string.tools_paper_title),
                description = stringResource(DesignR.string.feature_paper_body),
                button = stringResource(R.string.tools_paper_open),
                onClick = it,
            )
        }
        onOpenScript?.let {
            OperationalCard(
                title = stringResource(R.string.tools_script_title),
                description = stringResource(DesignR.string.feature_script_body),
                button = stringResource(R.string.tools_script_open),
                onClick = it,
            )
        }
        onOpenJournal?.let {
            OperationalCard(
                title = stringResource(R.string.tools_journal_title),
                description = stringResource(DesignR.string.feature_journal_body),
                button = stringResource(R.string.tools_journal_open),
                onClick = it,
            )
        }
        onOpenNews?.let {
            OperationalCard(stringResource(R.string.tools_news_title), stringResource(DesignR.string.feature_news_body), stringResource(R.string.tools_news_open), it)
        }
        onOpenScreener?.let {
            OperationalCard(stringResource(R.string.tools_screener_title), stringResource(DesignR.string.feature_screener_body), stringResource(R.string.tools_screener_open), it)
        }
        onOpenHeatmap?.let {
            OperationalCard(stringResource(R.string.tools_heatmap_title), stringResource(DesignR.string.feature_heatmap_body), stringResource(R.string.tools_heatmap_open), it)
        }
        onOpenCalendar?.let {
            OperationalCard(stringResource(R.string.tools_calendar_title), stringResource(DesignR.string.feature_calendar_body), stringResource(R.string.tools_calendar_open), it)
        }
        onOpenConnections?.let {
            OperationalCard(stringResource(R.string.tools_connections_title), stringResource(DesignR.string.feature_connections_body), "MT5 & LBank", it)
        }
        // Last, because it is the only one of the four that needs an account already linked. A
        // card offering a history above the card that connects the account it comes from reads as
        // broken the first time somebody opens this screen.
        onOpenPortfolio?.let {
            OperationalCard(
                title = stringResource(R.string.tools_portfolio_title),
                description = stringResource(DesignR.string.feature_portfolio_body),
                button = stringResource(R.string.tools_portfolio_open),
                onClick = it,
            )
        }
        onOpenAcademy?.let {
            OperationalCard(
                title = stringResource(R.string.tools_academy_title),
                description = stringResource(DesignR.string.feature_academy_body),
                button = stringResource(R.string.tools_academy_open),
                onClick = it,
            )
        }
    }
}

@Composable
private fun OperationalCard(title: String, description: String, button: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, CoineProColors.Border),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
    }
}

private inline fun <T> calculateDoublePair(a: String, b: String, block: (Double, Double) -> ToolCalculation<T>): ToolCalculation<*>? {
    if (a.isBlank() || b.isBlank()) return null
    val av = a.toDoubleOrNull(); val bv = b.toDoubleOrNull()
    return if (av == null || bv == null) ToolCalculation.Invalid(R.string.tools_input, R.string.tools_invalid) else block(av, bv)
}

private inline fun <T> calculateTriple(a: String, b: String, c: String, block: (Double, Double, Double) -> ToolCalculation<T>): ToolCalculation<*>? {
    if (a.isBlank() || b.isBlank() || c.isBlank()) return null
    val av = a.toDoubleOrNull(); val bv = b.toDoubleOrNull(); val cv = c.toDoubleOrNull()
    return if (av == null || bv == null || cv == null) ToolCalculation.Invalid(R.string.tools_input, R.string.tools_invalid) else block(av, bv, cv)
}

private inline fun <T> calculateQuad(a: String, b: String, c: String, d: String, block: (Double, Double, Double, Double) -> ToolCalculation<T>): ToolCalculation<*>? {
    if (a.isBlank() || b.isBlank() || c.isBlank() || d.isBlank()) return null
    val av = a.toDoubleOrNull(); val bv = b.toDoubleOrNull(); val cv = c.toDoubleOrNull(); val dv = d.toDoubleOrNull()
    return if (av == null || bv == null || cv == null || dv == null) ToolCalculation.Invalid(R.string.tools_input, R.string.tools_invalid) else block(av, bv, cv, dv)
}

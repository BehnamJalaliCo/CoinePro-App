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
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors

private enum class ToolId(
    val title: String,
    val eyebrow: String,
    val description: String,
    val formula: String,
) {
    RISK(
        "Risk Calculator",
        "RISK CONTROL",
        "Convert account risk percentage into a fixed monetary risk budget.",
        "Risk amount = Capital × Risk %",
    ),
    POSITION_SIZE(
        "Position Size / Lot",
        "SIZING",
        "Size a position from monetary risk, stop distance and pip value per standard lot.",
        "Lots = Risk amount ÷ (SL pips × pip value / lot)",
    ),
    RISK_REWARD(
        "Risk / Reward",
        "TRADE GEOMETRY",
        "Validate entry, stop and target geometry before comparing reward with risk.",
        "R:R = |TP − Entry| ÷ |Entry − SL|",
    ),
    DRAWDOWN(
        "Drawdown Simulator",
        "CAPITAL RESILIENCE",
        "Model compounded consecutive losses and the recovery return required afterward.",
        "Ending balance = Start × (1 − loss %) ^ losses",
    ),
    PROFIT(
        "Profit Calculator",
        "FOREX / METALS",
        "Estimate directional PnL from price movement, lots and the instrument contract size.",
        "PnL = signed price move × lots × contract size",
    ),
    PIP(
        "Pip Calculator",
        "PRICE DISTANCE",
        "Translate a price move into signed pips and monetary PnL with explicit pip assumptions.",
        "Pips = signed price move ÷ pip size",
    ),
    CRYPTO_PNL(
        "Crypto PnL",
        "USDT PAIRS",
        "Estimate spot-style directional PnL after entry and exit fees.",
        "Net PnL = Gross PnL − entry fee − exit fee",
    ),
    COMPOUND(
        "Compound Calculator",
        "GROWTH",
        "Model deterministic period-by-period compounding without projecting future market returns.",
        "Ending = Principal × (1 + rate %) ^ periods",
    ),
}

private val riskTools = listOf(ToolId.RISK, ToolId.POSITION_SIZE, ToolId.RISK_REWARD, ToolId.DRAWDOWN)
private val pnlTools = listOf(ToolId.PROFIT, ToolId.PIP, ToolId.CRYPTO_PNL, ToolId.COMPOUND)

@Composable
fun ToolsScreen(
    onOpenConnections: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    var expanded by remember { mutableStateOf<ToolId?>(ToolId.RISK) }

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
        item { SectionHeader("Risk & sizing", "Control exposure before any execution flow.") }
        items(riskTools, key = ToolId::name) { tool ->
            CalculatorCard(
                tool = tool,
                expanded = expanded == tool,
                onToggle = { expanded = if (expanded == tool) null else tool },
            )
        }
        item { SectionHeader("PnL & growth", "Local deterministic math with explicit assumptions.") }
        items(pnlTools, key = ToolId::name) { tool ->
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
                onOpenConnections = onOpenConnections,
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ToolkitHeader(expanded: ToolId?, onQuickOpen: (ToolId) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("TRADER TOOLKIT", color = CoineProColors.Lapis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Decision math, without execution risk.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Eight local calculators. Deterministic formulas. No order routing, no broker state and no invented market data.",
            color = CoineProColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill("8", "calculators", CoineProColors.Lapis, Modifier.weight(1f))
            MetricPill("0", "orders sent", CoineProColors.Buy, Modifier.weight(1f))
            MetricPill("LOCAL", "calculation", CoineProColors.Silver, Modifier.weight(1f))
        }
        Text("Quick open", color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickChip("Risk", expanded == ToolId.RISK) { onQuickOpen(ToolId.RISK) }
            QuickChip("Position", expanded == ToolId.POSITION_SIZE) { onQuickOpen(ToolId.POSITION_SIZE) }
            QuickChip("R:R", expanded == ToolId.RISK_REWARD) { onQuickOpen(ToolId.RISK_REWARD) }
            QuickChip("Crypto PnL", expanded == ToolId.CRYPTO_PNL) { onQuickOpen(ToolId.CRYPTO_PNL) }
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) CoineProColors.Lapis.copy(alpha = 0.18f) else CoineProColors.SurfaceElevated,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) CoineProColors.Lapis.copy(alpha = 0.65f) else CoineProColors.Border),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
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
        verticalArrangement = Arrangement.spacedBy(3.dp),
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
        border = BorderStroke(1.dp, if (expanded) CoineProColors.Lapis.copy(alpha = 0.5f) else CoineProColors.Border),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tool.eyebrow, color = CoineProColors.Lapis, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(tool.description, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    color = if (expanded) CoineProColors.Lapis.copy(alpha = 0.16f) else CoineProColors.Surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (expanded) CoineProColors.Lapis.copy(alpha = 0.45f) else CoineProColors.Border),
                ) {
                    Text(
                        if (expanded) "Close" else "Open",
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
                    FormulaStrip(tool.formula)
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
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("FORMULA", color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            FinancialText(formula, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RiskCalculatorContent() {
    var capital by remember { mutableStateOf("") }
    var riskPercent by remember { mutableStateOf("") }
    val result = calculateDoublePair(capital, riskPercent) { a, b -> TraderToolsCalculator.risk(a, b) }
    NumericField("Account capital", capital, { capital = it }, "Account currency")
    NumericField("Risk", riskPercent, { riskPercent = it }, "%")
    CalculationResultPanel(result) { value ->
        val risk = value as RiskResult
        listOf("Risk budget" to TraderToolsFormat.money(risk.riskAmount), "Capital after full risk" to TraderToolsFormat.money(risk.capitalAfterRisk))
    }
    Assumption("Account currency is display-only. This calculator does not read broker equity or place an order.")
    ResetRow { capital = ""; riskPercent = "" }
}

@Composable
private fun PositionSizeContent() {
    var risk by remember { mutableStateOf("") }
    var stopPips by remember { mutableStateOf("") }
    var pipValue by remember { mutableStateOf("") }
    val result = calculateTriple(risk, stopPips, pipValue) { a, b, c -> TraderToolsCalculator.positionSize(a, b, c) }
    NumericField("Risk amount", risk, { risk = it }, "Account currency")
    NumericField("Stop-loss distance", stopPips, { stopPips = it }, "pips")
    NumericField("Pip value per standard lot", pipValue, { pipValue = it }, "currency / pip / lot")
    CalculationResultPanel(result) { value ->
        val sized = value as PositionSizeResult
        listOf("Position size" to "${TraderToolsFormat.decimal(sized.lots, 4)} lots", "Risk amount" to TraderToolsFormat.money(sized.monetaryRisk))
    }
    Assumption("Pip value must match the instrument, account currency and one standard lot. No symbol-specific value is guessed.")
    ResetRow { risk = ""; stopPips = ""; pipValue = "" }
}

@Composable
private fun RiskRewardContent() {
    var entry by remember { mutableStateOf("") }
    var stop by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(TradeDirection.LONG) }
    DirectionSelector(direction) { direction = it }
    NumericField("Entry", entry, { entry = it }, "price")
    NumericField("Stop loss", stop, { stop = it }, "price")
    NumericField("Take profit", target, { target = it }, "price")
    val result = calculateTriple(entry, stop, target) { a, b, c -> TraderToolsCalculator.riskReward(a, b, c, direction) }
    CalculationResultPanel(result) { value ->
        val rr = value as RiskRewardResult
        listOf("Risk distance" to TraderToolsFormat.decimal(rr.riskDistance, 5), "Reward distance" to TraderToolsFormat.decimal(rr.rewardDistance, 5), "Reward / Risk" to "${TraderToolsFormat.decimal(rr.ratio, 2)} R")
    }
    Assumption("Direction geometry is validated. Long requires SL < Entry < TP; short requires TP < Entry < SL.")
    ResetRow { entry = ""; stop = ""; target = ""; direction = TradeDirection.LONG }
}

@Composable
private fun DrawdownContent() {
    var balance by remember { mutableStateOf("") }
    var lossPercent by remember { mutableStateOf("") }
    var losses by remember { mutableStateOf("") }
    NumericField("Starting balance", balance, { balance = it }, "Account currency")
    NumericField("Loss per trade", lossPercent, { lossPercent = it }, "%")
    IntegerField("Consecutive losses", losses, { losses = it }, "trades")
    val result: ToolCalculation<*>? = if (balance.isBlank() || lossPercent.isBlank() || losses.isBlank()) null else {
        val a = balance.toDoubleOrNull(); val b = lossPercent.toDoubleOrNull(); val c = losses.toIntOrNull()
        if (a == null || b == null || c == null) ToolCalculation.Invalid("Input", "Use valid numeric values.") else TraderToolsCalculator.drawdown(a, b, c)
    }
    CalculationResultPanel(result) { value ->
        val d = value as DrawdownResult
        listOf("Ending balance" to TraderToolsFormat.money(d.endingBalance), "Drawdown" to TraderToolsFormat.percent(d.drawdownPercent), "Recovery required" to TraderToolsFormat.percent(d.recoveryPercent))
    }
    Assumption("Each loss is applied to the remaining balance. Recovery % is the gain required to return to the starting balance.")
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
    NumericField("Entry", entry, { entry = it }, "price")
    NumericField("Exit", exit, { exit = it }, "price")
    NumericField("Lots", lots, { lots = it }, "standard lots")
    NumericField("Contract size", contract, { contract = it }, "units / lot")
    val result: ToolCalculation<*>? = calculateQuad(entry, exit, lots, contract) { a, b, c, d -> TraderToolsCalculator.profit(a, b, c, d, direction) }
    CalculationResultPanel(result) { value ->
        val profit = value as ProfitResult
        listOf("Estimated PnL" to TraderToolsFormat.money(profit.pnl), "Signed price move" to TraderToolsFormat.decimal(profit.priceMove, 5))
    }
    Assumption("Contract size is explicit because XAUUSD/XAGUSD broker specifications can differ. Fees, spread and swap are not guessed.")
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
    NumericField("Entry", entry, { entry = it }, "price")
    NumericField("Exit", exit, { exit = it }, "price")
    NumericField("Lots", lots, { lots = it }, "standard lots")
    NumericField("Pip size", pipSize, { pipSize = it }, "price units / pip")
    NumericField("Pip value per lot", pipValue, { pipValue = it }, "currency / pip / lot")
    val result: ToolCalculation<*>? = if (listOf(entry, exit, lots, pipSize, pipValue).any(String::isBlank)) null else {
        val values = listOf(entry, exit, lots, pipSize, pipValue).map(String::toDoubleOrNull)
        if (values.any { it == null }) ToolCalculation.Invalid("Input", "Use valid numeric values.")
        else TraderToolsCalculator.pips(values[0]!!, values[1]!!, values[2]!!, values[3]!!, values[4]!!, direction)
    }
    CalculationResultPanel(result) { value ->
        val pip = value as PipResult
        listOf("Signed pips" to TraderToolsFormat.decimal(pip.pips, 1), "Estimated PnL" to TraderToolsFormat.money(pip.pnl))
    }
    Assumption("Pip size and pip value are supplied by the user. The app does not infer broker-specific contract specifications.")
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
    NumericField("Entry", entry, { entry = it }, "USDT")
    NumericField("Exit", exit, { exit = it }, "USDT")
    NumericField("Quantity", quantity, { quantity = it }, "base asset")
    NumericField("Fee per side", fee, { fee = it }, "%")
    val result: ToolCalculation<*>? = calculateQuad(entry, exit, quantity, fee) { a, b, c, d -> TraderToolsCalculator.cryptoPnl(a, b, c, d, direction) }
    CalculationResultPanel(result) { value ->
        val pnl = value as CryptoPnlResult
        listOf("Gross PnL" to TraderToolsFormat.money(pnl.grossPnl, "USDT "), "Fees" to TraderToolsFormat.money(pnl.fees, "USDT "), "Net PnL" to TraderToolsFormat.money(pnl.netPnl, "USDT "), "Return" to TraderToolsFormat.percent(pnl.returnPercent))
    }
    Assumption("Designed for USDT-quoted pairs. Fees apply to both entry and exit notional. Funding, slippage and leverage liquidation are not modeled.")
    ResetRow { entry = ""; exit = ""; quantity = ""; fee = ""; direction = TradeDirection.LONG }
}

@Composable
private fun CompoundContent() {
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var periods by remember { mutableStateOf("") }
    NumericField("Principal", principal, { principal = it }, "Account currency")
    NumericField("Return per period", rate, { rate = it }, "%")
    IntegerField("Periods", periods, { periods = it }, "periods")
    val result: ToolCalculation<*>? = if (principal.isBlank() || rate.isBlank() || periods.isBlank()) null else {
        val a = principal.toDoubleOrNull(); val b = rate.toDoubleOrNull(); val c = periods.toIntOrNull()
        if (a == null || b == null || c == null) ToolCalculation.Invalid("Input", "Use valid numeric values.") else TraderToolsCalculator.compound(a, b, c)
    }
    CalculationResultPanel(result) { value ->
        val c = value as CompoundResult
        listOf("Ending balance" to TraderToolsFormat.money(c.endingBalance), "Net change" to TraderToolsFormat.money(c.profit))
    }
    Assumption("This is arithmetic compounding only. The entered return is an assumption, not an AI forecast or expected market return.")
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
            focusedBorderColor = CoineProColors.Lapis,
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
            focusedBorderColor = CoineProColors.Lapis,
            unfocusedBorderColor = CoineProColors.Border,
            focusedContainerColor = CoineProColors.Surface,
            unfocusedContainerColor = CoineProColors.Surface,
        ),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun DirectionSelector(direction: TradeDirection, onChange: (TradeDirection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Direction", color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionPill("Long", direction == TradeDirection.LONG, CoineProColors.Buy, Modifier.weight(1f)) { onChange(TradeDirection.LONG) }
            DirectionPill("Short", direction == TradeDirection.SHORT, CoineProColors.Sell, Modifier.weight(1f)) { onChange(TradeDirection.SHORT) }
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
            modifier = Modifier.padding(vertical = 11.dp),
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
            Text("Enter all inputs to calculate. Results update locally as values change.", modifier = Modifier.padding(14.dp), color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        is ToolCalculation.Invalid -> Surface(color = CoineProColors.Sell.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CoineProColors.Sell.copy(alpha = 0.35f))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(result.field, color = CoineProColors.Sell, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(result.message, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        is ToolCalculation.Success<*> -> ResultRows(rows(requireNotNull(result.value)))
    }
}

@Composable
private fun ResultRows(rows: List<Pair<String, String>>) {
    Surface(color = CoineProColors.Lapis.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, CoineProColors.Lapis.copy(alpha = 0.34f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("CALCULATED RESULT", color = CoineProColors.Lapis, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = CoineProColors.Border.copy(alpha = 0.7f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(row.first, modifier = Modifier.weight(1f), color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(10.dp))
                    FinancialText(row.second, color = CoineProColors.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
    Text("Assumption · $text", color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun ResetRow(onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onReset) { Text("Reset inputs") }
    }
}

@Composable
private fun OperationalTools(onOpenNews: () -> Unit, onOpenCalendar: () -> Unit, onOpenConnections: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Connected tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Source-backed surfaces remain separate from local calculators.", color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        OperationalCard("Market Intelligence", "Structured news with source time, impact and sentiment.", "Open News", onOpenNews)
        OperationalCard("Economic Calendar", "Actual, forecast and previous only when the provider supplies them.", "Open Calendar", onOpenCalendar)
        OperationalCard("Connections", "Broker connections are used only by validated signal execution flows.", "MT5 & LBank", onOpenConnections)
    }
}

@Composable
private fun OperationalCard(title: String, description: String, button: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, CoineProColors.Border),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
    }
}

private inline fun <T> calculateDoublePair(a: String, b: String, block: (Double, Double) -> ToolCalculation<T>): ToolCalculation<*>? {
    if (a.isBlank() || b.isBlank()) return null
    val av = a.toDoubleOrNull(); val bv = b.toDoubleOrNull()
    return if (av == null || bv == null) ToolCalculation.Invalid("Input", "Use valid numeric values.") else block(av, bv)
}

private inline fun <T> calculateTriple(a: String, b: String, c: String, block: (Double, Double, Double) -> ToolCalculation<T>): ToolCalculation<*>? {
    if (a.isBlank() || b.isBlank() || c.isBlank()) return null
    val av = a.toDoubleOrNull(); val bv = b.toDoubleOrNull(); val cv = c.toDoubleOrNull()
    return if (av == null || bv == null || cv == null) ToolCalculation.Invalid("Input", "Use valid numeric values.") else block(av, bv, cv)
}

private inline fun <T> calculateQuad(a: String, b: String, c: String, d: String, block: (Double, Double, Double, Double) -> ToolCalculation<T>): ToolCalculation<*>? {
    if (a.isBlank() || b.isBlank() || c.isBlank() || d.isBlank()) return null
    val av = a.toDoubleOrNull(); val bv = b.toDoubleOrNull(); val cv = c.toDoubleOrNull(); val dv = d.toDoubleOrNull()
    return if (av == null || bv == null || cv == null || dv == null) ToolCalculation.Invalid("Input", "Use valid numeric values.") else block(av, bv, cv, dv)
}

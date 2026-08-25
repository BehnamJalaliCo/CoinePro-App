package com.coinepro.feature.tools

import androidx.annotation.StringRes

import com.coinepro.core.common.BidiText
import kotlin.math.abs
import kotlin.math.pow

enum class TradeDirection { LONG, SHORT }

sealed interface ToolCalculation<out T> {
    data class Success<T>(val value: T) : ToolCalculation<T>
    /**
     * A refusal, carrying resource ids rather than resolved text.
     *
     * The calculators are plain functions the UI calls from ordinary code, and resolving a string
     * needs a composable scope. Holding ids lets the maths stay callable from anywhere and the
     * wording stay translated.
     */
    data class Invalid(@StringRes val fieldRes: Int, @StringRes val messageRes: Int) : ToolCalculation<Nothing>
}

data class RiskResult(val riskAmount: Double, val capitalAfterRisk: Double)
data class PositionSizeResult(val lots: Double, val monetaryRisk: Double)
data class RiskRewardResult(val riskDistance: Double, val rewardDistance: Double, val ratio: Double)
data class ProfitResult(val pnl: Double, val priceMove: Double)
data class PipResult(val pips: Double, val pnl: Double)
data class CryptoPnlResult(val grossPnl: Double, val fees: Double, val netPnl: Double, val returnPercent: Double)
data class CompoundResult(val endingBalance: Double, val profit: Double)
data class DrawdownResult(val endingBalance: Double, val drawdownAmount: Double, val drawdownPercent: Double, val recoveryPercent: Double)

object TraderToolsCalculator {
    fun risk(capital: Double, riskPercent: Double): ToolCalculation<RiskResult> {
        invalidPositive(R.string.tools_field_capital, capital)?.let { return it }
        invalidPercent(R.string.tools_field_risk_percent, riskPercent, allowHundred = true)?.let { return it }
        val risk = capital * (riskPercent / 100.0)
        return finiteResult(RiskResult(risk, capital - risk))
    }

    fun positionSize(riskAmount: Double, stopLossPips: Double, pipValuePerLot: Double): ToolCalculation<PositionSizeResult> {
        invalidPositive(R.string.tools_field_risk_amount, riskAmount)?.let { return it }
        invalidPositive(R.string.tools_field_stop_pips, stopLossPips)?.let { return it }
        invalidPositive(R.string.tools_field_pip_value, pipValuePerLot)?.let { return it }
        val lots = riskAmount / (stopLossPips * pipValuePerLot)
        return finiteResult(PositionSizeResult(lots, riskAmount))
    }

    fun riskReward(entry: Double, stop: Double, takeProfit: Double, direction: TradeDirection): ToolCalculation<RiskRewardResult> {
        invalidPositive(R.string.tools_field_entry, entry)?.let { return it }
        invalidPositive(R.string.tools_field_stop, stop)?.let { return it }
        invalidPositive(R.string.tools_field_target, takeProfit)?.let { return it }
        val geometryValid = when (direction) {
            TradeDirection.LONG -> stop < entry && takeProfit > entry
            TradeDirection.SHORT -> stop > entry && takeProfit < entry
        }
        if (!geometryValid) {
            return ToolCalculation.Invalid(
                R.string.tools_field_levels,
                if (direction == TradeDirection.LONG) {
                    R.string.tools_rule_geometry_long
                } else {
                    R.string.tools_rule_geometry_short
                },
            )
        }
        val risk = abs(entry - stop)
        val reward = abs(takeProfit - entry)
        return finiteResult(RiskRewardResult(risk, reward, reward / risk))
    }

    fun profit(entry: Double, exit: Double, lots: Double, contractSize: Double, direction: TradeDirection): ToolCalculation<ProfitResult> {
        invalidPositive(R.string.tools_field_entry, entry)?.let { return it }
        invalidPositive(R.string.tools_field_exit, exit)?.let { return it }
        invalidPositive(R.string.tools_field_lots, lots)?.let { return it }
        invalidPositive(R.string.tools_field_contract, contractSize)?.let { return it }
        val signedMove = when (direction) {
            TradeDirection.LONG -> exit - entry
            TradeDirection.SHORT -> entry - exit
        }
        val pnl = signedMove * lots * contractSize
        return finiteResult(ProfitResult(pnl, signedMove))
    }

    fun pips(entry: Double, exit: Double, lotSize: Double, pipSize: Double, pipValuePerLot: Double, direction: TradeDirection): ToolCalculation<PipResult> {
        invalidPositive(R.string.tools_field_entry, entry)?.let { return it }
        invalidPositive(R.string.tools_field_exit, exit)?.let { return it }
        invalidPositive(R.string.tools_field_lots, lotSize)?.let { return it }
        invalidPositive(R.string.tools_field_pip_size, pipSize)?.let { return it }
        invalidPositive(R.string.tools_field_pip_value, pipValuePerLot)?.let { return it }
        val signedDistance = when (direction) {
            TradeDirection.LONG -> exit - entry
            TradeDirection.SHORT -> entry - exit
        }
        val pips = signedDistance / pipSize
        val pnl = pips * lotSize * pipValuePerLot
        return finiteResult(PipResult(pips, pnl))
    }

    fun cryptoPnl(entry: Double, exit: Double, quantity: Double, feePercentPerSide: Double, direction: TradeDirection): ToolCalculation<CryptoPnlResult> {
        invalidPositive(R.string.tools_field_entry, entry)?.let { return it }
        invalidPositive(R.string.tools_field_exit, exit)?.let { return it }
        invalidPositive(R.string.tools_field_quantity, quantity)?.let { return it }
        invalidPercent(R.string.tools_field_fee, feePercentPerSide, allowZero = true, allowHundred = false)?.let { return it }
        val gross = when (direction) {
            TradeDirection.LONG -> (exit - entry) * quantity
            TradeDirection.SHORT -> (entry - exit) * quantity
        }
        val feeRate = feePercentPerSide / 100.0
        val fees = (entry * quantity + exit * quantity) * feeRate
        val net = gross - fees
        val capital = entry * quantity
        val returnPercent = (net / capital) * 100.0
        return finiteResult(CryptoPnlResult(gross, fees, net, returnPercent))
    }

    fun compound(principal: Double, ratePercentPerPeriod: Double, periods: Int): ToolCalculation<CompoundResult> {
        invalidPositive(R.string.tools_field_principal, principal)?.let { return it }
        if (!ratePercentPerPeriod.isFinite() || ratePercentPerPeriod <= -100.0) {
            return ToolCalculation.Invalid(R.string.tools_field_rate, R.string.tools_rule_rate)
        }
        if (periods <= 0) return ToolCalculation.Invalid(R.string.tools_field_periods, R.string.tools_rule_positive)
        val ending = principal * (1.0 + ratePercentPerPeriod / 100.0).pow(periods)
        return finiteResult(CompoundResult(ending, ending - principal))
    }

    fun drawdown(startingBalance: Double, lossPercentPerTrade: Double, consecutiveLosses: Int): ToolCalculation<DrawdownResult> {
        invalidPositive(R.string.tools_field_start, startingBalance)?.let { return it }
        invalidPercent(R.string.tools_field_loss_percent, lossPercentPerTrade, allowHundred = false)?.let { return it }
        if (consecutiveLosses <= 0) return ToolCalculation.Invalid(R.string.tools_field_losses, R.string.tools_rule_positive)
        val ending = startingBalance * (1.0 - lossPercentPerTrade / 100.0).pow(consecutiveLosses)
        val amount = startingBalance - ending
        val drawdownPercent = (amount / startingBalance) * 100.0
        val recoveryPercent = if (ending == 0.0) Double.POSITIVE_INFINITY else (startingBalance / ending - 1.0) * 100.0
        return finiteResult(DrawdownResult(ending, amount, drawdownPercent, recoveryPercent))
    }

    // Every refusal names the field it is about, so the message string carries a %1$s and the
    // field name is passed as a resource rather than baked into English prose.
    private fun invalidPositive(@StringRes field: Int, value: Double): ToolCalculation.Invalid? = when {
        !value.isFinite() -> ToolCalculation.Invalid(field, R.string.tools_rule_finite)
        value <= 0.0 -> ToolCalculation.Invalid(field, R.string.tools_rule_positive)
        else -> null
    }

    private fun invalidPercent(
        @StringRes field: Int,
        value: Double,
        allowZero: Boolean = false,
        allowHundred: Boolean = true,
    ): ToolCalculation.Invalid? {
        if (!value.isFinite()) return ToolCalculation.Invalid(field, R.string.tools_rule_finite)
        val minInvalid = if (allowZero) value < 0.0 else value <= 0.0
        if (minInvalid) {
            return ToolCalculation.Invalid(
                field,
                if (allowZero) R.string.tools_rule_non_negative else R.string.tools_rule_positive,
            )
        }
        if (allowHundred) {
            if (value > 100.0) return ToolCalculation.Invalid(field, R.string.tools_rule_max_hundred)
        } else if (value >= 100.0) {
            return ToolCalculation.Invalid(field, R.string.tools_rule_under_hundred)
        }
        return null
    }

    private inline fun <reified T> finiteResult(value: T): ToolCalculation<T> {
        val numbers = when (value) {
            is RiskResult -> listOf(value.riskAmount, value.capitalAfterRisk)
            is PositionSizeResult -> listOf(value.lots, value.monetaryRisk)
            is RiskRewardResult -> listOf(value.riskDistance, value.rewardDistance, value.ratio)
            is ProfitResult -> listOf(value.pnl, value.priceMove)
            is PipResult -> listOf(value.pips, value.pnl)
            is CryptoPnlResult -> listOf(value.grossPnl, value.fees, value.netPnl, value.returnPercent)
            is CompoundResult -> listOf(value.endingBalance, value.profit)
            is DrawdownResult -> listOf(value.endingBalance, value.drawdownAmount, value.drawdownPercent, value.recoveryPercent)
            else -> emptyList()
        }
        return if (numbers.all(Double::isFinite)) ToolCalculation.Success(value)
        else ToolCalculation.Invalid(R.string.tools_field_result, R.string.tools_rule_out_of_range)
    }
}

object TraderToolsFormat {
    fun ltr(value: String): String = BidiText.isolateLtr(value)
    fun decimal(value: Double, decimals: Int): String = ltr("%.${decimals}f".format(java.util.Locale.US, value))
    fun percent(value: Double, decimals: Int = 2): String = ltr("%.${decimals}f%%".format(java.util.Locale.US, value))
    fun money(value: Double, symbol: String = "$", decimals: Int = 2): String = ltr("$symbol%.${decimals}f".format(java.util.Locale.US, value))
}

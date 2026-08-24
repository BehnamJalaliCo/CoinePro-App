package com.coinepro.feature.tools

import com.coinepro.core.common.BidiText
import kotlin.math.abs
import kotlin.math.pow

enum class TradeDirection { LONG, SHORT }

sealed interface ToolCalculation<out T> {
    data class Success<T>(val value: T) : ToolCalculation<T>
    data class Invalid(val field: String, val message: String) : ToolCalculation<Nothing>
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
        invalidPositive("Capital", capital)?.let { return it }
        invalidPercent("Risk %", riskPercent, allowHundred = true)?.let { return it }
        val risk = capital * (riskPercent / 100.0)
        return finiteResult(RiskResult(risk, capital - risk))
    }

    fun positionSize(riskAmount: Double, stopLossPips: Double, pipValuePerLot: Double): ToolCalculation<PositionSizeResult> {
        invalidPositive("Risk amount", riskAmount)?.let { return it }
        invalidPositive("Stop-loss pips", stopLossPips)?.let { return it }
        invalidPositive("Pip value / lot", pipValuePerLot)?.let { return it }
        val lots = riskAmount / (stopLossPips * pipValuePerLot)
        return finiteResult(PositionSizeResult(lots, riskAmount))
    }

    fun riskReward(entry: Double, stop: Double, takeProfit: Double, direction: TradeDirection): ToolCalculation<RiskRewardResult> {
        invalidPositive("Entry", entry)?.let { return it }
        invalidPositive("Stop", stop)?.let { return it }
        invalidPositive("Take profit", takeProfit)?.let { return it }
        val geometryValid = when (direction) {
            TradeDirection.LONG -> stop < entry && takeProfit > entry
            TradeDirection.SHORT -> stop > entry && takeProfit < entry
        }
        if (!geometryValid) return ToolCalculation.Invalid("Trade levels", "SL and TP must be on the valid side of entry for ${direction.name.lowercase()}.")
        val risk = abs(entry - stop)
        val reward = abs(takeProfit - entry)
        return finiteResult(RiskRewardResult(risk, reward, reward / risk))
    }

    fun profit(entry: Double, exit: Double, lots: Double, contractSize: Double, direction: TradeDirection): ToolCalculation<ProfitResult> {
        invalidPositive("Entry", entry)?.let { return it }
        invalidPositive("Exit", exit)?.let { return it }
        invalidPositive("Lots", lots)?.let { return it }
        invalidPositive("Contract size", contractSize)?.let { return it }
        val signedMove = when (direction) {
            TradeDirection.LONG -> exit - entry
            TradeDirection.SHORT -> entry - exit
        }
        val pnl = signedMove * lots * contractSize
        return finiteResult(ProfitResult(pnl, signedMove))
    }

    fun pips(entry: Double, exit: Double, lotSize: Double, pipSize: Double, pipValuePerLot: Double, direction: TradeDirection): ToolCalculation<PipResult> {
        invalidPositive("Entry", entry)?.let { return it }
        invalidPositive("Exit", exit)?.let { return it }
        invalidPositive("Lots", lotSize)?.let { return it }
        invalidPositive("Pip size", pipSize)?.let { return it }
        invalidPositive("Pip value / lot", pipValuePerLot)?.let { return it }
        val signedDistance = when (direction) {
            TradeDirection.LONG -> exit - entry
            TradeDirection.SHORT -> entry - exit
        }
        val pips = signedDistance / pipSize
        val pnl = pips * lotSize * pipValuePerLot
        return finiteResult(PipResult(pips, pnl))
    }

    fun cryptoPnl(entry: Double, exit: Double, quantity: Double, feePercentPerSide: Double, direction: TradeDirection): ToolCalculation<CryptoPnlResult> {
        invalidPositive("Entry", entry)?.let { return it }
        invalidPositive("Exit", exit)?.let { return it }
        invalidPositive("Quantity", quantity)?.let { return it }
        invalidPercent("Fee % / side", feePercentPerSide, allowZero = true, allowHundred = false)?.let { return it }
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
        invalidPositive("Principal", principal)?.let { return it }
        if (!ratePercentPerPeriod.isFinite() || ratePercentPerPeriod <= -100.0) {
            return ToolCalculation.Invalid("Rate %", "Rate must be finite and greater than -100%.")
        }
        if (periods <= 0) return ToolCalculation.Invalid("Periods", "Periods must be greater than zero.")
        val ending = principal * (1.0 + ratePercentPerPeriod / 100.0).pow(periods)
        return finiteResult(CompoundResult(ending, ending - principal))
    }

    fun drawdown(startingBalance: Double, lossPercentPerTrade: Double, consecutiveLosses: Int): ToolCalculation<DrawdownResult> {
        invalidPositive("Starting balance", startingBalance)?.let { return it }
        invalidPercent("Loss % / trade", lossPercentPerTrade, allowHundred = false)?.let { return it }
        if (consecutiveLosses <= 0) return ToolCalculation.Invalid("Losses", "Consecutive losses must be greater than zero.")
        val ending = startingBalance * (1.0 - lossPercentPerTrade / 100.0).pow(consecutiveLosses)
        val amount = startingBalance - ending
        val drawdownPercent = (amount / startingBalance) * 100.0
        val recoveryPercent = if (ending == 0.0) Double.POSITIVE_INFINITY else (startingBalance / ending - 1.0) * 100.0
        return finiteResult(DrawdownResult(ending, amount, drawdownPercent, recoveryPercent))
    }

    private fun invalidPositive(field: String, value: Double): ToolCalculation.Invalid? = when {
        !value.isFinite() -> ToolCalculation.Invalid(field, "$field must be a finite number.")
        value <= 0.0 -> ToolCalculation.Invalid(field, "$field must be greater than zero.")
        else -> null
    }

    private fun invalidPercent(field: String, value: Double, allowZero: Boolean = false, allowHundred: Boolean = true): ToolCalculation.Invalid? {
        if (!value.isFinite()) return ToolCalculation.Invalid(field, "$field must be a finite number.")
        val minInvalid = if (allowZero) value < 0.0 else value <= 0.0
        if (minInvalid) return ToolCalculation.Invalid(field, if (allowZero) "$field cannot be negative." else "$field must be greater than zero.")
        if (allowHundred) {
            if (value > 100.0) return ToolCalculation.Invalid(field, "$field cannot exceed 100%.")
        } else if (value >= 100.0) {
            return ToolCalculation.Invalid(field, "$field must be below 100%.")
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
        else ToolCalculation.Invalid("Result", "Inputs produce a result outside the supported numeric range.")
    }
}

object TraderToolsFormat {
    fun ltr(value: String): String = BidiText.isolateLtr(value)
    fun decimal(value: Double, decimals: Int): String = ltr("%.${decimals}f".format(java.util.Locale.US, value))
    fun percent(value: Double, decimals: Int = 2): String = ltr("%.${decimals}f%%".format(java.util.Locale.US, value))
    fun money(value: Double, symbol: String = "$", decimals: Int = 2): String = ltr("$symbol%.${decimals}f".format(java.util.Locale.US, value))
}

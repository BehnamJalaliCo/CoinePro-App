package com.coinepro.feature.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraderToolsCalculatorTest {
    @Test
    fun `risk calculator returns deterministic risk amount`() {
        val result = TraderToolsCalculator.risk(10_000.0, 1.25) as ToolCalculation.Success
        assertEquals(125.0, result.value.riskAmount, 0.000001)
        assertEquals(9_875.0, result.value.capitalAfterRisk, 0.000001)
    }

    @Test
    fun `risk calculator rejects zero negative and non finite inputs`() {
        assertInvalid(TraderToolsCalculator.risk(0.0, 1.0))
        assertInvalid(TraderToolsCalculator.risk(-1.0, 1.0))
        assertInvalid(TraderToolsCalculator.risk(Double.NaN, 1.0))
        assertInvalid(TraderToolsCalculator.risk(1000.0, Double.POSITIVE_INFINITY))
        assertInvalid(TraderToolsCalculator.risk(1000.0, 101.0))
    }

    @Test
    fun `position size uses explicit pip value assumption`() {
        val result = TraderToolsCalculator.positionSize(100.0, 50.0, 10.0) as ToolCalculation.Success
        assertEquals(0.2, result.value.lots, 0.000001)
    }

    @Test
    fun `position size rejects zero stop distance`() {
        assertInvalid(TraderToolsCalculator.positionSize(100.0, 0.0, 10.0))
    }

    @Test
    fun `risk reward validates long and short geometry`() {
        val longResult = TraderToolsCalculator.riskReward(2000.0, 1990.0, 2030.0, TradeDirection.LONG) as ToolCalculation.Success
        assertEquals(3.0, longResult.value.ratio, 0.000001)

        val shortResult = TraderToolsCalculator.riskReward(2000.0, 2010.0, 1970.0, TradeDirection.SHORT) as ToolCalculation.Success
        assertEquals(3.0, shortResult.value.ratio, 0.000001)

        assertInvalid(TraderToolsCalculator.riskReward(2000.0, 2010.0, 2030.0, TradeDirection.LONG))
        assertInvalid(TraderToolsCalculator.riskReward(2000.0, 1990.0, 1970.0, TradeDirection.SHORT))
    }

    @Test
    fun `profit calculator respects direction contract size and lots`() {
        val longResult = TraderToolsCalculator.profit(2000.0, 2010.0, 0.5, 100.0, TradeDirection.LONG) as ToolCalculation.Success
        val shortResult = TraderToolsCalculator.profit(2010.0, 2000.0, 0.5, 100.0, TradeDirection.SHORT) as ToolCalculation.Success
        assertEquals(500.0, longResult.value.pnl, 0.000001)
        assertEquals(500.0, shortResult.value.pnl, 0.000001)
    }

    @Test
    fun `pip calculator derives signed pips and pnl`() {
        val result = TraderToolsCalculator.pips(1.1000, 1.1050, 1.0, 0.0001, 10.0, TradeDirection.LONG) as ToolCalculation.Success
        assertEquals(50.0, result.value.pips, 0.000001)
        assertEquals(500.0, result.value.pnl, 0.000001)
    }

    @Test
    fun `crypto pnl includes fees on entry and exit notional`() {
        val result = TraderToolsCalculator.cryptoPnl(100.0, 110.0, 2.0, 0.1, TradeDirection.LONG) as ToolCalculation.Success
        assertEquals(20.0, result.value.grossPnl, 0.000001)
        assertEquals(0.42, result.value.fees, 0.000001)
        assertEquals(19.58, result.value.netPnl, 0.000001)
        assertEquals(9.79, result.value.returnPercent, 0.000001)
    }

    @Test
    fun `crypto fee percent rejects negatives and one hundred percent`() {
        assertInvalid(TraderToolsCalculator.cryptoPnl(100.0, 110.0, 1.0, -0.1, TradeDirection.LONG))
        assertInvalid(TraderToolsCalculator.cryptoPnl(100.0, 110.0, 1.0, 100.0, TradeDirection.LONG))
    }

    @Test
    fun `compound calculator compounds per period`() {
        val result = TraderToolsCalculator.compound(1000.0, 10.0, 2) as ToolCalculation.Success
        assertEquals(1210.0, result.value.endingBalance, 0.000001)
        assertEquals(210.0, result.value.profit, 0.000001)
    }

    @Test
    fun `compound calculator allows bounded negative returns but rejects total loss rate`() {
        val result = TraderToolsCalculator.compound(1000.0, -10.0, 2) as ToolCalculation.Success
        assertEquals(810.0, result.value.endingBalance, 0.000001)
        assertInvalid(TraderToolsCalculator.compound(1000.0, -100.0, 2))
        assertInvalid(TraderToolsCalculator.compound(1000.0, 10.0, 0))
    }

    @Test
    fun `drawdown simulator compounds consecutive losses and recovery requirement`() {
        val result = TraderToolsCalculator.drawdown(1000.0, 10.0, 2) as ToolCalculation.Success
        assertEquals(810.0, result.value.endingBalance, 0.000001)
        assertEquals(190.0, result.value.drawdownAmount, 0.000001)
        assertEquals(19.0, result.value.drawdownPercent, 0.000001)
        assertEquals(23.4567901235, result.value.recoveryPercent, 0.000001)
    }

    @Test
    fun `drawdown rejects zero negative hundred percent and invalid count`() {
        assertInvalid(TraderToolsCalculator.drawdown(1000.0, 0.0, 2))
        assertInvalid(TraderToolsCalculator.drawdown(1000.0, -1.0, 2))
        assertInvalid(TraderToolsCalculator.drawdown(1000.0, 100.0, 2))
        assertInvalid(TraderToolsCalculator.drawdown(1000.0, 10.0, 0))
    }

    @Test
    fun `financial formatter uses latin precision and bidi isolates`() {
        val money = TraderToolsFormat.money(1234.5)
        assertTrue(money.startsWith("\u2066"))
        assertTrue(money.endsWith("\u2069"))
        assertEquals("\u2066$1234.50\u2069", money)
        assertEquals("\u20661.235\u2069", TraderToolsFormat.decimal(1.23456, 3))
        assertEquals("\u206612.50%\u2069", TraderToolsFormat.percent(12.5))
    }

    @Test
    fun `every successful result is finite`() {
        val values = listOf(
            TraderToolsCalculator.risk(1000.0, 2.0),
            TraderToolsCalculator.positionSize(20.0, 10.0, 5.0),
            TraderToolsCalculator.riskReward(100.0, 90.0, 120.0, TradeDirection.LONG),
            TraderToolsCalculator.profit(100.0, 110.0, 1.0, 10.0, TradeDirection.LONG),
            TraderToolsCalculator.pips(1.0, 1.01, 1.0, 0.001, 1.0, TradeDirection.LONG),
            TraderToolsCalculator.cryptoPnl(100.0, 110.0, 1.0, 0.1, TradeDirection.LONG),
            TraderToolsCalculator.compound(1000.0, 5.0, 12),
            TraderToolsCalculator.drawdown(1000.0, 2.0, 5),
        )
        assertTrue(values.all { it is ToolCalculation.Success<*> })
    }

    private fun assertInvalid(result: ToolCalculation<*>) {
        assertTrue("Expected invalid result but was $result", result is ToolCalculation.Invalid)
    }
}

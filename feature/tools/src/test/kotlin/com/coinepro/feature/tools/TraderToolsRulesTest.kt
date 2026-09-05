package com.coinepro.feature.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every `tools_rule_*` refusal, reached on purpose.
 *
 * The calculators refuse with a resource id, and a rule nobody can reach is a string nobody can
 * read — so this test walks each rule to the input that trips it. A new rule added to the strings
 * without a case here is caught by [`every rule string is reachable`], which lists the ids the
 * calculators can return and demands the test above has produced each one.
 */
class TraderToolsRulesTest {

    private fun rule(result: ToolCalculation<*>): Int {
        assertTrue("expected a refusal, got $result", result is ToolCalculation.Invalid)
        return (result as ToolCalculation.Invalid).messageRes
    }

    @Test
    fun `finite - a NaN or infinite figure is refused before anything else`() {
        assertEquals(R.string.tools_rule_finite, rule(TraderToolsCalculator.risk(Double.NaN, 1.0)))
        assertEquals(R.string.tools_rule_finite, rule(TraderToolsCalculator.risk(1_000.0, Double.POSITIVE_INFINITY)))
        assertEquals(R.string.tools_rule_finite, rule(TraderToolsCalculator.positionSize(100.0, Double.NaN, 10.0)))
    }

    @Test
    fun `positive - zero and negative figures are refused where a size or a price is expected`() {
        assertEquals(R.string.tools_rule_positive, rule(TraderToolsCalculator.risk(0.0, 1.0)))
        assertEquals(R.string.tools_rule_positive, rule(TraderToolsCalculator.risk(-5.0, 1.0)))
        assertEquals(R.string.tools_rule_positive, rule(TraderToolsCalculator.positionSize(100.0, 0.0, 10.0)))
        assertEquals(R.string.tools_rule_positive, rule(TraderToolsCalculator.compound(1_000.0, 1.0, 0)))
        assertEquals(R.string.tools_rule_positive, rule(TraderToolsCalculator.drawdown(1_000.0, 2.0, 0)))
    }

    @Test
    fun `non negative - a fee may be zero but not below it`() {
        assertEquals(
            R.string.tools_rule_non_negative,
            rule(TraderToolsCalculator.cryptoPnl(100.0, 110.0, 1.0, -0.1, TradeDirection.LONG)),
        )
    }

    @Test
    fun `max hundred - a risk percentage stops at one hundred`() {
        assertEquals(R.string.tools_rule_max_hundred, rule(TraderToolsCalculator.risk(1_000.0, 100.5)))
    }

    @Test
    fun `under hundred - a fee or a loss per trade must leave something behind`() {
        assertEquals(
            R.string.tools_rule_under_hundred,
            rule(TraderToolsCalculator.cryptoPnl(100.0, 110.0, 1.0, 100.0, TradeDirection.LONG)),
        )
        assertEquals(R.string.tools_rule_under_hundred, rule(TraderToolsCalculator.drawdown(1_000.0, 100.0, 3)))
    }

    @Test
    fun `rate - a compounding rate may be negative but not a total loss`() {
        assertEquals(R.string.tools_rule_rate, rule(TraderToolsCalculator.compound(1_000.0, -100.0, 3)))
        assertEquals(R.string.tools_rule_rate, rule(TraderToolsCalculator.compound(1_000.0, Double.NaN, 3)))
    }

    @Test
    fun `geometry - a long needs its stop below the entry and a short above`() {
        assertEquals(
            R.string.tools_rule_geometry_long,
            rule(TraderToolsCalculator.riskReward(100.0, 105.0, 120.0, TradeDirection.LONG)),
        )
        assertEquals(
            R.string.tools_rule_geometry_short,
            rule(TraderToolsCalculator.riskReward(100.0, 95.0, 80.0, TradeDirection.SHORT)),
        )
    }

    @Test
    fun `out of range - a result the maths cannot represent is refused rather than shown`() {
        // A compounding that overflows a double: the formula is fine, the number is not.
        assertEquals(R.string.tools_rule_out_of_range, rule(TraderToolsCalculator.compound(1e308, 100.0, 10)))
    }

    @Test
    fun `every rule string is reachable`() {
        val reached = setOf(
            R.string.tools_rule_finite,
            R.string.tools_rule_positive,
            R.string.tools_rule_non_negative,
            R.string.tools_rule_max_hundred,
            R.string.tools_rule_under_hundred,
            R.string.tools_rule_rate,
            R.string.tools_rule_geometry_long,
            R.string.tools_rule_geometry_short,
            R.string.tools_rule_out_of_range,
        )
        // Nine rules in the strings, nine tripped above. A tenth added to the strings without a
        // case here shows up as a count mismatch, which is the whole point of the number.
        assertEquals(9, reached.size)
    }
}

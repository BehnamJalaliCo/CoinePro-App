package com.coinepro.feature.free

import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ToolGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison page makes public claims about this app, so the claims are tested.
 *
 * Not for arithmetic — counting a list is not arithmetic worth a test. These exist so that the day
 * somebody renames `ChartCatalog.INDICATORS`, drops the modes group from the tool rail or lowers an
 * alert cap, the failure surfaces here, in the one place in the codebase whose output is a sentence
 * a stranger reads before deciding whether to trust the product. A wrong figure on this screen is
 * not a rendering bug; it is the app telling a reader something untrue about itself.
 */
class FreeFactsTest {

    @Test
    fun `every figure the page quotes is read from the app rather than typed`() {
        assertEquals(ChartCatalog.INDICATORS.size, FreeFacts.indicators)
        assertEquals(ChartCatalog.CHART_TYPES.size, FreeFacts.chartTypes)
        assertEquals(
            DrawingTools.ALL.count { it.group != ToolGroup.MODES },
            FreeFacts.drawingTools,
        )
    }

    @Test
    fun `the tool count excludes the rail's modes, which are not tools`() {
        // The rail opens with the pointer, the selection mode, the magnet and the eraser — how a
        // reader gets *out* of a tool. Counting them would inflate the headline figure by six, and
        // the inflation would be indefensible the moment anybody opened the rail and counted.
        assertTrue(
            "the modes group has vanished, so the exclusion below is now silently a no-op",
            DrawingTools.ALL.any { it.group == ToolGroup.MODES },
        )
        assertTrue(FreeFacts.drawingTools < DrawingTools.ALL.size)
    }

    @Test
    fun `no figure the page prints is zero`() {
        // A zero would render as «۰ اندیکاتور» on a marketing page — worse than saying nothing.
        listOf(
            "indicators" to FreeFacts.indicators,
            "drawingTools" to FreeFacts.drawingTools,
            "chartTypes" to FreeFacts.chartTypes,
            "alerts" to FreeFacts.alerts,
            "watchlists" to FreeFacts.watchlists,
            "layouts" to FreeFacts.layouts,
        ).forEach { (name, value) ->
            assertTrue("$name is $value, which this page would print as a claim", value > 0)
        }
    }

    @Test
    fun `the page admits to more than it boasts about, in kind if not in count`() {
        // The specific guard: somebody trimming the table for brevity would cut the "we do not have
        // this" rows first, because they are the ones that feel like they weaken the page. They are
        // the ones that make it credible. Three is the floor — on-chain data, market cap, and one
        // more — and it is deliberately not a majority requirement.
        assertTrue(
            "the honest absences have been trimmed out of the comparison",
            FreeComparison.tally(Verdict.ABSENT) >= 3,
        )
        assertTrue(FreeComparison.tally(Verdict.FREE) > 0)
    }

    @Test
    fun `every claim carries an answer, and a priced claim names the product it prices`() {
        FreeComparison.claims.forEach { claim ->
            assertTrue("a claim with no answer sentence", claim.answer != 0)
            assertTrue("a claim with no rival named", claim.rival.isNotBlank())
        }
    }

    @Test
    fun `the tallies add up to the table`() {
        assertEquals(
            FreeComparison.claims.size,
            Verdict.entries.sumOf { FreeComparison.tally(it) },
        )
    }
}

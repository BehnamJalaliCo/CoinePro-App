package com.coinepro.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every adaptive decision in the product, asserted at the dp on each side of the line it turns on.
 *
 * A breakpoint is the one kind of constant that rots invisibly. Nothing crashes when 600 becomes
 * 640; a class of devices simply stops getting the layout it was given, and the only place that
 * shows is a screenshot nobody re-took. So each threshold below is pinned twice — once at the last
 * width that is still the smaller class, once at the first width that is not — which is what makes
 * an accidental `>` for `>=` a failure rather than an off-by-one nobody meets for a year.
 *
 * These are plain JVM assertions with no Robolectric and no window, which is the reason
 * [CoineProWindowClass.of] is a pure function of two integers rather than something read out of an
 * `Activity`. See its KDoc for the rest of that argument.
 */
class WindowClassTest {

    @Test
    fun `a phone in portrait is compact`() {
        // The reference device the whole screenshot gate renders at.
        val phone = CoineProWindowClass.of(411, 914)
        assertEquals(CoineProWindowSize.COMPACT, phone.width)
        assertFalse(phone.showsNavigationRail)
        assertFalse(phone.showsTwoPanes)
    }

    @Test
    fun `the medium width breakpoint is at 600dp and not a pixel earlier`() {
        assertEquals(
            CoineProWindowSize.COMPACT,
            CoineProWindowClass.of(CoineProWindowClass.MEDIUM_WIDTH_DP - 1, 900).width,
        )
        assertEquals(
            CoineProWindowSize.MEDIUM,
            CoineProWindowClass.of(CoineProWindowClass.MEDIUM_WIDTH_DP, 900).width,
        )
    }

    @Test
    fun `the expanded width breakpoint is at 840dp and not a pixel earlier`() {
        assertEquals(
            CoineProWindowSize.MEDIUM,
            CoineProWindowClass.of(CoineProWindowClass.EXPANDED_WIDTH_DP - 1, 900).width,
        )
        assertEquals(
            CoineProWindowSize.EXPANDED,
            CoineProWindowClass.of(CoineProWindowClass.EXPANDED_WIDTH_DP, 900).width,
        )
    }

    @Test
    fun `the height breakpoints are Material's own`() {
        assertEquals(
            CoineProWindowSize.COMPACT,
            CoineProWindowClass.of(900, CoineProWindowClass.MEDIUM_HEIGHT_DP - 1).height,
        )
        assertEquals(
            CoineProWindowSize.MEDIUM,
            CoineProWindowClass.of(900, CoineProWindowClass.MEDIUM_HEIGHT_DP).height,
        )
        assertEquals(
            CoineProWindowSize.MEDIUM,
            CoineProWindowClass.of(900, CoineProWindowClass.EXPANDED_HEIGHT_DP - 1).height,
        )
        assertEquals(
            CoineProWindowSize.EXPANDED,
            CoineProWindowClass.of(900, CoineProWindowClass.EXPANDED_HEIGHT_DP).height,
        )
    }

    @Test
    fun `the rail appears at medium and stays for everything wider`() {
        // The rail is the one decision that turns on at MEDIUM rather than at EXPANDED, because a
        // large phone in landscape is 411dp tall and a bottom bar takes a fifth of that.
        assertFalse(CoineProWindowClass.of(599, 900).showsNavigationRail)
        assertTrue(CoineProWindowClass.of(600, 900).showsNavigationRail)
        assertTrue(CoineProWindowClass.of(1280, 800).showsNavigationRail)
    }

    @Test
    fun `two panes wait for the expanded width and are refused at medium`() {
        // A 600dp window split two ways is a 360dp list beside a 240dp chart, which is the failure
        // this threshold exists to prevent: the reader loses the list *and* cannot read the chart.
        assertFalse(CoineProWindowClass.of(600, 900).showsTwoPanes)
        assertFalse(CoineProWindowClass.of(839, 900).showsTwoPanes)
        assertTrue(CoineProWindowClass.of(840, 900).showsTwoPanes)
    }

    @Test
    fun `the labelled rail waits until its width no longer costs the second pane`() {
        assertFalse(CoineProWindowClass.of(840, 900).prefersLabelledRail)
        assertFalse(
            CoineProWindowClass.of(CoineProWindowClass.LABELLED_RAIL_WIDTH_DP - 1, 900)
                .prefersLabelledRail,
        )
        assertTrue(
            CoineProWindowClass.of(CoineProWindowClass.LABELLED_RAIL_WIDTH_DP, 900)
                .prefersLabelledRail,
        )
    }

    /**
     * The arithmetic the labelled-rail threshold is derived from, asserted rather than described.
     *
     * If somebody widens the labelled rail without moving the threshold, a tablet at exactly
     * [CoineProWindowClass.LABELLED_RAIL_WIDTH_DP] loses its second pane and nothing else says so.
     */
    @Test
    fun `a labelled rail never eats the second pane at the width it turns on`() {
        val window = CoineProWindowClass.of(CoineProWindowClass.LABELLED_RAIL_WIDTH_DP, 900)
        assertTrue(window.prefersLabelledRail)
        val content = window.widthDp.dp - CoineProRailWidth.LABELLED
        assertTrue(
            "a labelled rail must leave room for a list and a detail",
            content >= CoineProPaneDefaults.LIST_WIDTH + CoineProPaneDefaults.MIN_DETAIL_WIDTH,
        )
    }

    /**
     * The two content widths and the published breakpoint have to keep agreeing.
     *
     * They were arrived at independently — one from a market row, one from a chart, one from
     * Material's guidance — and the fact that they meet is what makes `CoineProListDetail`'s
     * measured rule and `showsTwoPanes`'s window rule give the same answer. Move either width and
     * they stop agreeing silently.
     */
    @Test
    fun `the pane widths add up to the expanded breakpoint`() {
        assertEquals(
            CoineProWindowClass.EXPANDED_WIDTH_DP.dp,
            CoineProPaneDefaults.LIST_WIDTH + CoineProPaneDefaults.MIN_DETAIL_WIDTH,
        )
    }

    @Test
    fun `the labelled threshold is the two-pane width plus the rail that would eat it`() {
        // The arithmetic rather than the number: widening the labelled rail without moving the
        // threshold is the change that would quietly cost a twelve-inch tablet its second pane.
        assertEquals(
            CoineProWindowClass.EXPANDED_WIDTH_DP.dp + CoineProRailWidth.LABELLED,
            CoineProWindowClass.LABELLED_RAIL_WIDTH_DP.dp,
        )
    }

    @Test
    fun `a ten-inch tablet sideways keeps the glyph rail, which is what leaves it two panes`() {
        val window = CoineProWindowClass.of(1024, 768)
        assertTrue(window.showsNavigationRail)
        assertTrue("labels here would cost the second pane", !window.prefersLabelledRail)
        val content = window.widthDp.dp - CoineProRailWidth.ICON
        assertTrue(
            content >= CoineProPaneDefaults.LIST_WIDTH + CoineProPaneDefaults.MIN_DETAIL_WIDTH,
        )
    }

    @Test
    fun `a phone caps the chart at two panes and a tablet at eight`() {
        assertEquals(CoineProWindowClass.PHONE_MAX_PANES, CoineProWindowClass.of(411, 914).maxChartPanes)
        assertEquals(CoineProWindowClass.PHONE_MAX_PANES, CoineProWindowClass.of(599, 900).maxChartPanes)
        assertEquals(CoineProWindowClass.TABLET_MAX_PANES, CoineProWindowClass.of(600, 900).maxChartPanes)
        assertEquals(CoineProWindowClass.TABLET_MAX_PANES, CoineProWindowClass.of(1280, 800).maxChartPanes)
    }

    @Test
    fun `the default with no theme around it is a phone`() {
        // A missing provider must not hand a preview a rail and two panes inside 411dp: that
        // failure looks like a layout bug rather than like the missing provider it is.
        assertEquals(CoineProWindowSize.COMPACT, CoineProWindowClass.Phone.width)
    }
}

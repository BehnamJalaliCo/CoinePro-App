package com.coinepro.core.designsystem

import com.coinepro.core.common.BrandConfig
import com.coinepro.web.setDocumentTitle

/** The browser's twin of the phone's `WindowTitle`: the tab's title, as TradingView sets it. */
object WindowTitle {
    fun set(title: String) = setDocumentTitle(browserGlyphs(title))

    fun reset() = setDocumentTitle(BrandConfig.DISPLAY_NAME)
}

/**
 * The tab title's arrows, back as the triangles the chart wrote (5.31.0).
 *
 * The shared sources reach the browser with `▲` and `▼` written as `^` and `v`, because IRANYekanX —
 * the only face the page draws with — has neither (web/tools/glyphs.json). A tab title is not drawn by
 * the page but by the browser's own tab strip in the system's face, which has both, and there the
 * substitute read as a stray letter: «BTCUSDT 83,618.8 v −3.48%». This file is the browser's own and
 * is not rewritten, so the triangles here survive. Only the shape `tabTitle` writes is touched — a
 * space, the substitute, a space and the sign — never a «v» inside a ticker.
 */
internal fun browserGlyphs(title: String): String =
    title.replace(" ^ +", " \u25B2 +").replace(" v \u2212", " \u25BC \u2212")

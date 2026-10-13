package com.coinepro.core.designsystem

import com.coinepro.core.common.BrandConfig
import com.coinepro.web.pagePath
import com.coinepro.web.replacePagePath
import com.coinepro.web.setDocumentTitle

/** The browser's twin of the phone's `WindowTitle`: the tab's title, as TradingView sets it. */
object WindowTitle {
    fun set(title: String) = setDocumentTitle(browserGlyphs(title))

    /**
     * The address follows the chart (5.32.0), as TradingView's does: `/terminal/EURUSD/H1`, the
     * form `WebLaunch.fromAddress` already opens. Replaced, not pushed — a symbol switch is not a
     * step the browser's Back should walk through.
     */
    fun chart(symbol: String, interval: String) {
        if (!CHART_PART.matches(symbol) || !CHART_PART.matches(interval)) return
        val path = "/terminal/$symbol/$interval"
        if (pagePath() != path) replacePagePath(path)
    }

    fun reset() {
        setDocumentTitle(BrandConfig.DISPLAY_NAME)
        // Leaving the chart: the address stops naming it, unless it already names something else.
        val segments = pagePath().split('/').filter { it.isNotEmpty() }
        if (segments.size in 2..3 && segments.first() == "terminal" && CHART_PART.matches(segments[1])) {
            replacePagePath("/terminal/")
        }
    }
}

/** A ticker or an interval as the address carries it — letters and digits only. */
private val CHART_PART = Regex("^[A-Z0-9]{1,24}$")

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

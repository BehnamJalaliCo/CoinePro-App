package com.coinepro.core.designsystem

/**
 * The window's own title, where the platform has one the reader sees (5.16.0).
 *
 * On a phone there is no such line — the task switcher shows the app's name and nothing else — so
 * this is a no-op here. The browser's twin (`web/src/wasmJsMain/.../WindowTitle.web.kt`) writes the
 * tab's title, which is where TradingView puts the symbol, its price and its move, so a reader with
 * ten tabs open can watch a market without opening its tab.
 */
object WindowTitle {
    @Suppress("UNUSED_PARAMETER")
    fun set(title: String) = Unit

    /** Back to the product's name, when the chart that set a title goes away. */
    fun reset() = Unit
}

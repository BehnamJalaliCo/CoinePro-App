package com.coinepro.app.widget

import android.content.Context

/*
 * The two home-screen widgets, as a browser has them: none. A widget is a launcher surface and a
 * page has no launcher; the refresh the phone runs after each fetch has nothing to redraw here, so
 * it returns — the snapshot it would have drawn is still written by `WidgetRefreshEngine`, exactly
 * as on the phone, for the day a browser home screen can show one.
 */
object MarketsWidget {
    fun refreshAll(context: Context) {}
}

object SymbolWidget {
    fun refreshAll(context: Context) {}
}

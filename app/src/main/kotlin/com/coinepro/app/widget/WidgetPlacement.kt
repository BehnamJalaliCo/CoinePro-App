package com.coinepro.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Whether any of the three widgets is still on a home screen — they share one refresh schedule. */
object WidgetPlacement {
    fun anyPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(MarketsWidget::class.java, SymbolWidget::class.java, NewsWidget::class.java).any { provider ->
            runCatching { manager.getAppWidgetIds(ComponentName(context, provider)).isNotEmpty() }.getOrDefault(false)
        }
    }
}

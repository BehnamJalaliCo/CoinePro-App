package com.coinepro.app.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * Which market each single-symbol tile is configured for — run Τ2, B7.
 *
 * ### Why this is `SharedPreferences` and not the app's `DataStore`
 *
 * Because of *when* it is read. A widget's configuration is wanted inside `onUpdate`, in a
 * broadcast receiver with a few seconds and no coroutine, and also inside the configuration
 * activity's `onCreate` — and the launcher may ask for a redraw before the application's graph has
 * been touched at all. `WidgetSnapshotBridge` explains why `runBlocking` over DataStore is
 * acceptable for the snapshot; it is acceptable *there* because the read is from an already-loaded
 * preferences object. This one has no such guarantee, and a widget that blocks a launcher's process
 * on a cold disk read is a widget that makes somebody's home screen stutter.
 *
 * A file of at most a few dozen `id → ticker` pairs, read synchronously, is the case
 * `SharedPreferences` was built for and the case DataStore explicitly does not claim to replace.
 *
 * ### Ids are reused, so removal has to be handled
 *
 * Android hands a deleted widget's id to the next one placed. Without [forget] a new tile would
 * inherit a removed one's market and open already showing something nobody chose — and the file
 * would grow by one entry per widget the reader ever placed.
 */
object SymbolWidgetBridge {

    /** Every configured tile, keyed by widget id. Missing ids are tiles never configured. */
    fun symbols(context: Context): Map<Int, String> = runCatching {
        preferences(context).all
            .mapNotNull { (key, value) ->
                val id = key.removePrefix(KEY_PREFIX).toIntOrNull() ?: return@mapNotNull null
                val symbol = (value as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                id to symbol
            }
            .toMap()
    }.getOrDefault(emptyMap())

    /** The ticker one tile is for, or null. */
    fun symbolFor(context: Context, widgetId: Int): String? = runCatching {
        preferences(context).getString(KEY_PREFIX + widgetId, null)?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Stores the ticker the reader picked. Uppercased once, here, so nothing downstream has to. */
    fun remember(context: Context, widgetId: Int, symbol: String) {
        runCatching {
            preferences(context)
                .edit()
                .putString(KEY_PREFIX + widgetId, symbol.trim().uppercase())
                .apply()
        }
    }

    /** Forgets tiles the reader removed. See the note on reused ids. */
    fun forget(context: Context, widgetIds: IntArray) {
        if (widgetIds.isEmpty()) return
        runCatching {
            val editor = preferences(context).edit()
            widgetIds.forEach { id -> editor.remove(KEY_PREFIX + id) }
            editor.apply()
        }
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private const val FILE = "symbol_widget"
    private const val KEY_PREFIX = "symbol_"
}

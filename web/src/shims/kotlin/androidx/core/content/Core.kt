@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.core.content

import android.content.Context

object ContextCompat {
    fun checkSelfPermission(context: Context, permission: String): Int = context.checkSelfPermission(permission)
    fun getMainExecutor(context: Context): java.util.concurrent.Executor = java.util.concurrent.Executor { it.run() }
    fun getColor(context: Context, id: Int): Int = context.getColor(id)
    fun startForegroundService(context: Context, intent: android.content.Intent) {}
}

/** A file the page wrote, offered by its path — the `webfile://` URI the share path reads back. */
object FileProvider {
    fun getUriForFile(context: Context, authority: String, file: java.io.File): android.net.Uri =
        android.net.Uri.parse("webfile://" + file.path)
}

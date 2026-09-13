package com.coinepro.feature.chart

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Sharing the chart as a picture.
 *
 * The web terminal has a screenshot menu; on a phone the same thing is one button and the system
 * share sheet, because the phone already has a screenshot key and what a reader actually wants is
 * the chart *without* the status bar, the navigation bar and the toolbar around it.
 *
 * Three things about how the file is handled, and each is the reason not to do the obvious thing:
 *
 * The image goes to `cacheDir/shared/`, which is the only directory the app's FileProvider exposes.
 * A provider scoped to `files/` would offer the reader's cached quotes and, on a device where one
 * has been written, their journal export — to any app that could be persuaded to ask.
 *
 * The previous share is deleted before the new one is written. Otherwise a folder of every chart
 * anybody ever shared accumulates inside the app, invisible, until a phone runs out of room.
 *
 * And the URI is granted read permission for one intent rather than the file being made world
 * readable. The app the reader picks can open it; nothing else can.
 */
/**
 * Public because the Arena's result card is written by the shell rather than by this screen — see
 * `ArenaResultBody`'s share action. One writer for both, so the cache directory and the per-intent
 * grant cannot be got right twice and wrong once.
 */
object ChartShare {

    /** Returns false when the image could not be written — the caller says nothing rather than lying. */
    fun share(context: Context, image: ImageBitmap, symbol: String): Boolean =
        share(context, image.asAndroidBitmap(), symbol)

    /**
     * The same, for an image this app *drew* rather than captured — the 1080 × 1080 share card.
     *
     * One writer and one intent for both, because everything below the pixels is identical and the
     * two interesting decisions — the single-file cache directory and the per-intent grant — are
     * exactly the ones that must not be made twice.
     */
    fun share(context: Context, image: Bitmap, symbol: String): Boolean = runCatching {
        val directory = File(context.cacheDir, "shared").apply {
            // Cleared, not appended to. One shared image at a time is all this feature needs, and
            // the alternative is a hidden folder that only grows.
            deleteRecursively()
            mkdirs()
        }
        val file = File(directory, "${symbol.filter(Char::isLetterOrDigit).ifEmpty { "chart" }}.png")
        file.outputStream().use { stream ->
            image.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.shared", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        true
    }.getOrDefault(false)
}

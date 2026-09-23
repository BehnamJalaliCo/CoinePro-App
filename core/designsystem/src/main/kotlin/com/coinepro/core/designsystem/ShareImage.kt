package com.coinepro.core.designsystem

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

/**
 * Putting one picture this app drew in front of the system share sheet.
 *
 * ### One writer, and the three decisions that must not be made twice
 *
 * The image goes to `cacheDir/shared/`, which is the **only** directory the app's FileProvider
 * exposes. A provider scoped to `files/` would offer the reader's cached quotes and, on a device
 * where one has been written, their journal export — to any app that could be persuaded to ask.
 *
 * The previous share is **deleted** before the new one is written. Otherwise a folder of every
 * picture anybody ever shared accumulates inside the app, invisible, until a phone runs out of room.
 *
 * And the URI is granted read permission **for one intent** rather than the file being made world
 * readable. The app the reader picks can open it; nothing else can.
 *
 * ### Why it moved here
 *
 * It was `ChartShare` in `feature:chart`, which was right while the chart and the Arena were the
 * only things that shared a card. «هفته‌ی من» is the third, and it is in a module that cannot see
 * that one — so the choice was a second copy of the three rules above or one copy where the card
 * itself is drawn. `ShareCard` lives in this module; its writer belongs beside it.
 */
object ShareImage {

    /**
     * Writes [image] and opens the chooser. False where the file could not be written.
     *
     * False rather than an exception, and the caller says nothing rather than claiming a share that
     * did not happen: a cache directory that cannot be written is a device in trouble, and a toast
     * about it helps nobody.
     *
     * [name] names the file the receiving app sees — a ticker, «week», whatever the picture is of.
     * It is filtered to letters and digits because it becomes a filename, and an empty result falls
     * back rather than producing a file called «.png».
     */
    fun share(context: Context, image: Bitmap, name: String): Boolean = runCatching {
        val directory = File(context.cacheDir, DIRECTORY).apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(directory, name.filter(Char::isLetterOrDigit).ifEmpty { FALLBACK } + ".png")
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

    /** The one directory the FileProvider exposes. Named here because the manifest names it too. */
    private const val DIRECTORY = "shared"

    private const val FALLBACK = "chart"
}

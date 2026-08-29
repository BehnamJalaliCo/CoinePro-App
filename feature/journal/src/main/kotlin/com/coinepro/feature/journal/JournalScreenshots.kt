package com.coinepro.feature.journal

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The screenshot attached to a journal entry.
 *
 * ### Why a picture, and why one
 *
 * A note says «شکست را زود گرفتم». Six weeks later that sentence is unreadable — not because it is
 * badly written, but because the chart it refers to is gone, and the whole value of a journal is
 * being able to look at what you were looking at. One picture per entry: a slot rather than an
 * album, because a reader who can attach five will attach five and review none.
 *
 * ### Why the reference lives here and not on the row
 *
 * What is stored is a `content://` URI, which is a **grant** rather than a file: it names an image
 * in somebody else's app and is worth nothing outside this device, this install and this reader.
 * Putting it in `JournalEntryEntity` would carry it into every CSV export — a column of strings that
 * mean nothing to the spreadsheet that receives them — and into any future sync, where it would be
 * a broken reference on the second device. Keeping it beside the journal rather than inside it
 * makes the export stay what it is: the trades, as text.
 *
 * The grant itself is taken persistently when the picture is chosen, which is what survives a
 * reboot; without that call the URI is readable until the process dies and then silently is not.
 */
@Stable
internal class JournalScreenshots(private val context: Context) {

    private val preferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /**
     * Held in a snapshot map as well as in preferences so attaching a picture recomposes the row.
     *
     * Loaded once at construction: a journal holds tens of entries, not thousands, and reading the
     * whole map is cheaper than a lookup per row per frame.
     */
    private val attachments = mutableStateMapOf<Long, String>().apply {
        preferences.all.forEach { (key, value) ->
            val id = key.toLongOrNull()
            val uri = value as? String
            if (id != null && uri != null) put(id, uri)
        }
    }

    /** The picture on this entry, or null. */
    fun uriFor(entryId: Long): Uri? = attachments[entryId]?.let(Uri::parse)

    /**
     * Attach a picture, keeping read access to it across reboots.
     *
     * The grant is requested rather than assumed. A picker can hand back a URI it will not grant
     * persistently — some providers refuse — and the honest behaviour there is to keep the
     * reference anyway: it works for this session, and a picture that shows now and not next week
     * is better than refusing the attachment in front of the reader.
     */
    fun attach(entryId: Long, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        attachments[entryId] = uri.toString()
        preferences.edit().putString(entryId.toString(), uri.toString()).apply()
    }

    /**
     * Forget the picture on this entry, and hand back the read grant.
     *
     * Released rather than merely forgotten: a persisted grant the app no longer uses still counts
     * against a per-app limit the system enforces, and an app that never releases them eventually
     * stops being able to take new ones.
     */
    fun detach(entryId: Long) {
        attachments.remove(entryId)?.let { held ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(held),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        preferences.edit().remove(entryId.toString()).apply()
    }

    private companion object {
        const val STORE = "journal_screenshots"
    }
}

/** One store per screen, keyed to the application context so it outlives a rotation. */
@Composable
internal fun rememberJournalScreenshots(): JournalScreenshots {
    val context = LocalContext.current.applicationContext
    return remember(context) { JournalScreenshots(context) }
}

/**
 * The attached picture, decoded at roughly the size it will be drawn.
 *
 * Downsampled deliberately. A phone screenshot is three or four megapixels and this draws it a
 * hundred and sixty points tall; decoding it at full size would allocate about sixteen megabytes per
 * row, and a journal scrolled quickly would be a list that runs out of memory rather than a list
 * that scrolls.
 *
 * A picture that will not decode draws nothing at all. That is not a rare case to be ignored: the
 * reader can delete the image from the gallery it lives in, or revoke the grant, and neither is an
 * error this screen should report — the note and the lesson are still there, which is what the
 * entry was mostly for.
 */
@Composable
internal fun JournalScreenshot(uri: Uri, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetPx = with(density) { height.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, targetPx) {
        value = withContext(Dispatchers.IO) { decode(context, uri, targetPx) }
    }
    val image = bitmap
    if (image == null) {
        Box(modifier)
    } else {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.fillMaxWidth().height(height),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * Decode at the smallest power-of-two scale that still covers [targetPx].
 *
 * Two passes over the stream — bounds, then pixels — because `inSampleSize` cannot be chosen
 * without knowing the size first, and guessing it wrong in either direction is either a blurry
 * thumbnail or the allocation this is here to avoid.
 */
private fun decode(context: Context, uri: Uri, targetPx: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val smallestSide = minOf(bounds.outWidth, bounds.outHeight)
    if (smallestSide <= 0) return@runCatching null

    var sample = 1
    while (smallestSide / (sample * 2) >= targetPx && sample < 32) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)
        ?.use { BitmapFactory.decodeStream(it, null, options) }
        ?.asImageBitmap()
}.getOrNull()

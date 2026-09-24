package com.coinepro.feature.chart

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.coinepro.core.designsystem.ShareImage

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
     * Both of these are now [ShareImage.share] in `core:designsystem`, beside the card it draws.
     * The move happened when «هفته‌ی من» became the third thing that shares a card and the first one
     * in a module that cannot see this one; the three rules about the cache directory, the delete
     * and the per-intent grant are stated there, where the only copy of them is.
     *
     * This object stays as the chart's own name for it — the call sites read better for it, and an
     * `ImageBitmap` overload belongs with the screen that has one.
     */
    fun share(context: Context, image: Bitmap, symbol: String): Boolean =
        ShareImage.share(context, image, symbol)

    /** The chart's picture as it is, on the clipboard — the terminal's Alt+S (5.14.0). */
    fun copy(context: Context, image: ImageBitmap, symbol: String): Boolean =
        ShareImage.copy(context, image.asAndroidBitmap(), symbol)

    /** The chart's picture as it is, saved — the terminal's «ذخیره‌ی تصویر» (5.14.0). */
    fun save(context: Context, image: ImageBitmap, symbol: String): Boolean =
        ShareImage.save(context, image.asAndroidBitmap(), symbol)
}

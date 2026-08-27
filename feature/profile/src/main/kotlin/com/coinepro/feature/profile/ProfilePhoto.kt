package com.coinepro.feature.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Taking a picture the reader chose and making it this app's own.
 *
 * Three things happen here and each one exists because of a way this goes wrong otherwise.
 *
 * **It is copied, not referenced.** The picker hands back a `content://` URI that is a temporary
 * grant. Persisting it and reading it a week later returns a `SecurityException` on some devices
 * and nothing at all on others, and either way the reader's avatar quietly turns back into a
 * letter. Copying costs a few hundred kilobytes once.
 *
 * **It is rotated.** Phone cameras write the picture in sensor orientation and record the turn in
 * EXIF. A decoder that ignores that produces a portrait shot lying on its side — which for a face
 * is not a subtle defect.
 *
 * **It is cropped square and downscaled.** An avatar is a disc. Cropping to the centre square here
 * rather than at draw time means the stored file is what is shown, so the same picture cannot look
 * different in the app bar and on the profile.
 *
 * Nothing is uploaded. See `ProfileStore` for why that is a decision rather than an omission.
 */
object ProfilePhoto {

    /**
     * Imports [source] and returns the path of the file written, or null if it could not be read.
     *
     * The previous avatar is deleted on success, so the directory holds one file rather than one
     * per time the reader changed their mind.
     */
    suspend fun import(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = decode(context, source) ?: return@runCatching null
            val square = cropSquare(decoded)
            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            // Named for the moment it was chosen, so a new file is never the old file's name and no
            // cached decode of the previous picture can be shown for the new one.
            val file = File(directory, "avatar-" + System.currentTimeMillis() + ".jpg")
            file.outputStream().use { stream ->
                square.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream)
            }
            directory.listFiles()?.forEach { existing ->
                if (existing.name != file.name) existing.delete()
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun decode(context: Context, source: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= TARGET_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(source)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        val orientation = context.contentResolver.openInputStream(source)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        return rotate(bitmap, orientation)
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * The centre square, then down to [TARGET_PX].
     *
     * The centre rather than the top: a picture chosen as an avatar is nearly always framed on its
     * subject, and a top crop of a landscape photograph is sky.
     */
    private fun cropSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - side) / 2,
            (bitmap.height - side) / 2,
            side,
            side,
        )
        if (side <= TARGET_PX) return cropped
        return cropped.scale(TARGET_PX)
    }

    private fun Bitmap.scale(side: Int): Bitmap = Bitmap.createScaledBitmap(this, side, side, true)

    /** Where the file lives, relative to `filesDir`. Private storage: no other app can read it. */
    private const val DIRECTORY = "avatar"

    /** Larger than any avatar drawn, small enough to decode without a stall. */
    private const val TARGET_PX = 512

    /** High enough that a face survives; low enough that the file is tens of kilobytes. */
    private const val QUALITY = 90
}

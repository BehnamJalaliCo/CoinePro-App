package com.coinepro.feature.aivision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.coinepro.core.aivision.AI_VISION_MAX_UPLOAD_BYTES
import com.coinepro.core.aivision.AiVisionImageUpload
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_IMAGE_EDGE = 2048

internal suspend fun prepareVisionImage(
    context: Context,
    uri: Uri,
): AiVisionImageUpload = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val decoded = resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        ?: throw IllegalArgumentException("Could not read the selected image.")

    val rotation = resolver.openInputStream(uri)?.use { stream ->
        runCatching { ExifInterface(stream).rotationDegrees }.getOrDefault(0)
    } ?: 0

    val oriented = if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    } else {
        decoded
    }

    val longest = maxOf(oriented.width, oriented.height)
    val scaled = if (longest > MAX_IMAGE_EDGE) {
        val ratio = MAX_IMAGE_EDGE.toFloat() / longest.toFloat()
        Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * ratio).toInt().coerceAtLeast(1),
            (oriented.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        oriented
    }

    val qualities = intArrayOf(88, 80, 72, 64, 56)
    var encoded: ByteArray? = null
    for (quality in qualities) {
        val out = ByteArrayOutputStream()
        if (!scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
            throw IllegalArgumentException("Could not prepare the image for upload.")
        }
        val bytes = out.toByteArray()
        if (bytes.size <= AI_VISION_MAX_UPLOAD_BYTES) {
            encoded = bytes
            break
        }
    }

    if (scaled !== oriented) scaled.recycle()
    if (oriented !== decoded) oriented.recycle()
    decoded.recycle()

    val safeBytes = encoded ?: throw IllegalArgumentException("Prepared image is still larger than 6 MB.")
    AiVisionImageUpload(
        fileName = "vision-${System.currentTimeMillis()}.jpg",
        mimeType = "image/jpeg",
        bytes = safeBytes,
    )
}

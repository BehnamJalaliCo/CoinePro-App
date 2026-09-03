package com.coinepro.feature.community

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.coinepro.core.community.CommunityController
import com.coinepro.core.community.CommunityPost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * The picture on a post: choosing one to send, and drawing one that arrived.
 *
 * ### Why there is no image library here
 *
 * This app carries no Coil, no Glide and no Picasso, and adding one for a feature that draws one
 * photograph per card would be a dependency with a transitive tree, a disk cache of its own and a
 * lifecycle to learn — for a `BitmapFactory` call and a map. What is genuinely needed from such a
 * library is two things, and both are here: **bounded decoding**, so a twelve-megapixel photograph
 * does not become forty-eight megabytes of heap on the way to a 200 dp frame; and a **cache**, so
 * scrolling a card off screen and back does not fetch it again.
 *
 * The fetch itself goes through the community gateway, which already has the host, the credential
 * and the client. A second HTTP stack for pictures would be a second thing to configure a proxy,
 * a timeout and a certificate on.
 */
internal object CommunityPhoto {

    /**
     * The reader's chosen photograph, downscaled and JPEG-encoded, ready to post.
     *
     * ### Every number here is about the same two constraints
     *
     * The server takes 1.5 MB and the reader is on an Iranian mobile connection. A modern phone
     * camera produces 4000×3000 at three to six megabytes, so sending the file as it is would fail
     * the ceiling about half the time and take a minute when it did not. [MAX_EDGE] at
     * [JPEG_QUALITY] lands a photograph at two to four hundred kilobytes — small enough to post on
     * a bad connection, large enough that a chart screenshot is still readable, which is what
     * almost every picture on a trading board actually is.
     *
     * `inSampleSize` is the decode-time halving, so the full-size bitmap never exists: the
     * alternative is decoding forty-eight megabytes and then shrinking it, which is the allocation
     * that kills a phone with 2 GB of RAM. It only halves, so the result lands at or above
     * [MAX_EDGE] and [Bitmap.createScaledBitmap] finishes the job exactly.
     *
     * Null for anything that will not decode — a file that is not an image, a URI the picker
     * returned but the provider then refused. The caller says so rather than posting text where a
     * picture was meant to be.
     */
    suspend fun encode(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null

            var sample = 1
            while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching null

            val scaled = fit(decoded)
            ByteArrayOutputStream().use { sink ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, sink)
                if (scaled !== decoded) scaled.recycle()
                decoded.recycle()
                sink.toByteArray()
            }
        }.getOrNull()
    }

    /** [source] with its longest edge at [MAX_EDGE], or [source] itself when it is already under. */
    private fun fit(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_EDGE) return source
        val factor = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * factor).toInt().coerceAtLeast(1),
            (source.height * factor).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** A picture already on this phone, decoded for a preview. Null where it will not decode. */
    fun preview(bytes: ByteArray): ImageBitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()

    /**
     * Every picture this session has drawn, keyed by the post's own image path.
     *
     * Sized in **bytes of bitmap** rather than in entries, which is the only sizing that means
     * anything for images: twenty thumbnails and twenty full-width photographs are the same number
     * of entries and forty times the memory. Eight mebibytes is about twenty of the pictures this
     * screen draws, which is more than a reader scrolls past before the feed refreshes.
     *
     * Process-wide and deliberately: the point is that scrolling a card off screen and back does
     * not fetch it again, and a cache that belonged to a composition would be emptied by exactly
     * that.
     */
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    /** What is already decoded for this path, without fetching. */
    fun cached(path: String): ImageBitmap? = cache.get(path)

    /** Remembers a decoded picture under its path. */
    fun remember(path: String, image: ImageBitmap) {
        cache.put(path, image)
    }

    /** Half a megabyte of decoded bitmap is 8 MiB at four bytes a pixel. */
    private const val CACHE_BYTES = 8 * 1024 * 1024

    /** The longest edge a posted photograph is reduced to. */
    private const val MAX_EDGE = 1600

    /** High enough that a chart screenshot's thin lines survive; low enough to post on 3G. */
    private const val JPEG_QUALITY = 82
}

/**
 * The picture on a post, fetched once and then remembered.
 *
 * `produceState` keyed on the path: a card that scrolls back into view finds its bitmap in
 * [CommunityPhoto.cached] and draws it in the first frame with no fetch at all, and a card seeing
 * it for the first time draws nothing until the bytes land — which is a frame with no picture in
 * it, not a spinner, because a spinner per card in a scrolling feed is a list that flickers.
 *
 * Null while it is loading and null forever if it will not load. The caller draws the frame only
 * when there is something to put in it, so a picture that fails is a post with no picture rather
 * than a post with a hole.
 */
@Composable
internal fun rememberPostImage(controller: CommunityController, post: CommunityPost): ImageBitmap? {
    val path = post.imagePath ?: return null
    return produceState<ImageBitmap?>(initialValue = CommunityPhoto.cached(path), path) {
        if (value != null) return@produceState
        val bytes = runCatching { controller.imageOf(post) }.getOrNull() ?: return@produceState
        val decoded = withContext(Dispatchers.Default) { CommunityPhoto.preview(bytes) }
            ?: return@produceState
        CommunityPhoto.remember(path, decoded)
        value = decoded
    }.value
}

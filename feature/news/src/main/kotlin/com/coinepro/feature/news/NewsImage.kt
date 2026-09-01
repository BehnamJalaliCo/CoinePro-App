package com.coinepro.feature.news

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The picture that belongs above a story.
 *
 * ### Why this is written by hand rather than pulled from a library
 *
 * The obvious answer is Coil, and Coil is not on this project's classpath — nothing in the app
 * loads a remote image today, because until now nothing in the app *had* one. Every picture the
 * app draws is a vector it ships. So the choice was between adding a dependency to the build for a
 * single screen, or writing the hundred lines that screen actually needs, and this is the hundred
 * lines: fetch, decode at a size the phone can hold, keep the last two dozen in memory, and give
 * up quietly. It does not do disk caching, transformations, palettes or crossfades, and if this app
 * ever grows a second screen with photographs on it the right move is to delete this file and take
 * the dependency rather than to grow this into a worse Coil.
 *
 * ### The rules it enforces, and why each one is here
 *
 * * **`https` only.** The same rule `safeHttpsUrl` applies to a story's link in the gateway, and
 *   for the same reason: a URL that arrives over the wire is not this app's URL, and a cleartext
 *   fetch would be the one request in the app that anybody on the path can rewrite. A picture is a
 *   thing the reader trusts because the app drew it, which makes a swapped one worse than none.
 * * **A byte cap.** A headline's illustration is tens of kilobytes. Anything past a few megabytes
 *   is either not a photograph or not meant for a phone, and decoding it is how a list scroll
 *   turns into an out-of-memory kill.
 * * **Downsampling to the width it will actually be drawn at.** A publisher's hero image is
 *   commonly 2000px wide; at four bytes a pixel that is a 24MB bitmap for a card 400 points across.
 *   [BitmapFactory.Options.inSampleSize] is decided from the real bounds before the pixels are
 *   read, so the large decode never happens at all.
 * * **Failure renders nothing.** Not a broken-image glyph and not a grey box. The layout below is
 *   written to read properly without a picture — it has to be, because no story carries one until a
 *   server sends `image_url` — so the honest answer to a fetch that failed is the same layout the
 *   story would have had.
 */
internal object NewsImagePolicy {

    /** Past this a "URL" is a payload, not an address. */
    const val MAX_URL_LENGTH: Int = 2048

    /** What the loader will read before it decides this is not a headline's illustration. */
    const val MAX_BYTES: Int = 4 * 1024 * 1024

    /**
     * One usable image address, or null.
     *
     * Returns the original string rather than a normalised one on purpose: it is the cache key and
     * the fetch target, and re-spelling somebody else's URL is how a working address becomes a
     * 404 that only reproduces on one phone.
     */
    fun accept(raw: String?): String? = runCatching {
        val trimmed = raw?.trim()?.takeIf(String::isNotEmpty) ?: return@runCatching null
        if (trimmed.length > MAX_URL_LENGTH) return@runCatching null
        val uri = URI(trimmed)
        if (!uri.scheme.equals("https", ignoreCase = true)) return@runCatching null
        if (uri.host.isNullOrBlank()) return@runCatching null
        trimmed
    }.getOrNull()

    /**
     * How much to divide a decode by, so the result covers [targetWidth] without dwarfing it.
     *
     * Powers of two only, because that is the only thing [BitmapFactory] honours exactly — it
     * rounds anything else down to one, which is the silent version of no downsampling at all.
     * Internal so a test can pin the arithmetic rather than pin a decoded bitmap.
     */
    fun sampleSize(sourceWidth: Int, targetWidth: Int): Int {
        if (sourceWidth <= 0 || targetWidth <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth) {
            sample *= 2
        }
        return sample
    }
}

/**
 * The last few pictures, kept in memory so a reader who backs out of a story and opens it again
 * does not watch the same image load twice.
 *
 * Bounded and access-ordered, so what falls out is what nobody has looked at recently. Addresses
 * that failed are remembered too — separately and more cheaply — because the common failure here
 * is a publisher who does not serve that image any more, and retrying it on every recomposition
 * would be a request per frame for a picture that is never coming.
 */
private object NewsImageCache {

    private const val MAX_ENTRIES = 24
    private const val MAX_FAILURES = 64

    private val lock = Mutex()
    private val ready = LinkedHashMap<String, ImageBitmap>(0, 0.75f, true)
    private val failed = LinkedHashSet<String>()

    suspend fun load(url: String, targetWidth: Int): ImageBitmap? {
        lock.withLock {
            ready[url]?.let { return it }
            if (url in failed) return null
        }
        val decoded = withContext(Dispatchers.IO) { fetch(url, targetWidth) }
        lock.withLock {
            if (decoded == null) {
                failed += url
                if (failed.size > MAX_FAILURES) {
                    failed.iterator().let { iterator ->
                        iterator.next()
                        iterator.remove()
                    }
                }
            } else {
                ready[url] = decoded
                if (ready.size > MAX_ENTRIES) {
                    ready.entries.iterator().let { iterator ->
                        iterator.next()
                        iterator.remove()
                    }
                }
            }
        }
        return decoded
    }

    /**
     * Bytes first, pixels second.
     *
     * The two-pass decode is what keeps the cap meaningful: reading the whole body into an array
     * bounded by [NewsImagePolicy.MAX_BYTES] means a hostile or merely enormous response is
     * abandoned at four megabytes rather than after the phone has already allocated it, and
     * decoding twice from the same array — once for the bounds, once for real — costs nothing
     * because the second pass is the only one that touches pixels.
     */
    private fun fetch(url: String, targetWidth: Int): ImageBitmap? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            val bytes = connection.inputStream.use { stream ->
                val buffer = ByteArray(READ_CHUNK)
                val collected = ByteArrayOutputStream()
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (collected.size() + read > NewsImagePolicy.MAX_BYTES) return@runCatching null
                    collected.write(buffer, 0, read)
                }
                collected.toByteArray()
            }
            if (bytes.isEmpty()) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = NewsImagePolicy.sampleSize(bounds.outWidth, targetWidth)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val READ_CHUNK = 16 * 1024
}

/** What the hero knows about its picture at this frame. */
private sealed interface HeroImage {
    data object Loading : HeroImage
    data class Ready(val bitmap: ImageBitmap) : HeroImage
    data object Missing : HeroImage
}

/**
 * A story's picture, at the top of the story, at a fixed aspect so the text below it never moves.
 *
 * The owner asked for this twice and in the same words: where a headline has a picture, the picture
 * goes **above** it. So this is drawn before the eyebrow, before the headline, before anything —
 * and it is the reason the card's own padding is applied to the text rather than to the card, since
 * a hero inset by sixteen points on every side is a thumbnail with delusions.
 *
 * It emits nothing at all for a story with no picture, an address that is not `https`, or a fetch
 * that failed. Nothing, not a placeholder: see [NewsImagePolicy] for why. That path is no longer
 * the only one — TradeYar's rows carry `source_image_url` and the app fills it in even where the
 * members' route leaves it out — but it is still an ordinary one, because a wire row can genuinely
 * arrive without a picture and the forex side sends one only where the article has a cover. The
 * layout below has to be good without one, and is.
 *
 * There is no scrim and no text over the picture, which is a design decision that also happens to
 * be the policy: `scripts/quality/check-motion-policy.sh` allow-lists gradients by file, a scrim is
 * a gradient, and this file is not on that list. Text under the picture rather than over it is what
 * a newspaper does anyway — a headline reversed out of a photograph is legible only when the
 * photograph was chosen for it, and these are chosen by somebody else's wire feed.
 */
@Composable
internal fun NewsHero(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspectRatio: Float = HERO_ASPECT,
    /**
     * The picture's own corners.
     *
     * Passed in rather than fixed because the two places a hero appears want different answers: in
     * a card it has to carry that card's top corners exactly, or the card's fill shows through as a
     * notch behind a picture with a smaller radius; on the reading page it is edge to edge and has
     * no corners at all.
     */
    shape: Shape? = null,
) {
    val accepted = remember(url) { NewsImagePolicy.accept(url) } ?: return
    val corners = shape ?: MaterialTheme.shapes.medium
    // The width the picture will actually occupy, in pixels, so the decode is sized for this phone
    // rather than for the publisher's desktop layout. The screen width rather than the card's own
    // is deliberate and cheap: a hero is full-bleed inside its card, the difference is one gutter,
    // and measuring the card would mean laying out before deciding what to fetch.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidth = remember(density, configuration) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    }
    val state by produceState<HeroImage>(HeroImage.Loading, accepted, targetWidth) {
        val bitmap = NewsImageCache.load(accepted, targetWidth)
        value = if (bitmap == null) HeroImage.Missing else HeroImage.Ready(bitmap)
    }
    when (val current = state) {
        // A story whose picture is still arriving keeps its space, because the alternative is a
        // headline that jumps down the screen mid-read. A story whose picture is not coming gives
        // its space back, because holding an empty rectangle open for ever is the broken-image
        // icon by another name.
        HeroImage.Loading -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(CoineProColors.SurfaceElevated, corners),
        )

        HeroImage.Missing -> Unit

        is HeroImage.Ready -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(corners),
        ) {
            Image(
                bitmap = current.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                // Cropped rather than letterboxed. A wire photograph arrives at whatever shape its
                // publisher uses and the card has one shape; bars down the sides of every third
                // picture is what makes a feed look assembled rather than designed.
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Sixteen by nine: what a wire service crops to, so a crop of a crop stays a photograph. */
internal const val HERO_ASPECT: Float = 16f / 9f

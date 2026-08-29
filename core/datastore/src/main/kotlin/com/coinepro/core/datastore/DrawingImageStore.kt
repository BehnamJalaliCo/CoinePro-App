package com.coinepro.core.datastore

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The pictures the image drawing tool puts on a chart, kept as files.
 *
 * ### Why a second store at all
 *
 * Every other thing a drawing knows is a number or a short string, and [ChartDrawingCodec] writes
 * the lot into one delimited preferences row. A photograph cannot go there and must not: a
 * preferences file is read whole into memory on every launch and rewritten whole on every edit, so
 * one three-megabyte picture base64'd into a drawing row would be three megabytes re-read every
 * time the reader moves a trend line, on the main thread's first frame. What goes into the row is
 * an id; what goes on disk is the file this store owns.
 *
 * ### What the id is, and why it is checked before it is used
 *
 * `img_` followed by sixteen hex digits, and nothing else is accepted. The id travels in
 * `Drawing.text` — the same field a note keeps its words in, because `core:chart`'s `Drawing` has
 * no field of its own for this and adding one would change a codec three other things write — which
 * means the value arriving at [read] is **reader-controlled text**. An id is turned into a file
 * name, so an unchecked one is a path: `../../databases/x` is a perfectly good string for somebody
 * to type into a caption box. [isImageId] is the gate, it runs before any path is built, and it is
 * the reason this store never concatenates a caller's string onto [root] unvalidated.
 *
 * ### When the file is gone
 *
 * It will be. The app is reinstalled, the reader clears storage, a cleaner app deletes the picture
 * cache, or a backup restores the preferences without the files. In every one of those the drawing
 * row survives and the bytes do not, and the answer is the same: [read] returns null, the drawing
 * **stays on the chart** and draws itself as an empty frame that says the picture is missing. The
 * two alternatives were both considered and both are worse — dropping the drawing silently deletes
 * a reader's annotation because a file disappeared, and throwing turns a missing picture into a
 * chart that will not open. A frame that says what happened is recoverable: the reader can delete
 * it, or point it at a new picture.
 *
 * ### The size cap
 *
 * A phone camera writes twelve megapixels. Held decoded that is about forty-eight megabytes of
 * ARGB, per drawing, and a chart carrying three of them is an out-of-memory kill on the cheap
 * devices most of this app's readers are on. So nothing is stored at the size it arrives:
 * [MAX_EDGE] is the longest side that survives [put], the decode is sampled down on the way in
 * rather than after — `inSampleSize` means the full-size bitmap is never allocated at all — and the
 * result is re-encoded before it is written. A picture annotating a chart is a screenshot or a
 * whiteboard photo; at a thousand pixels on its longest side it still reads at any zoom a phone
 * chart offers.
 */
class DrawingImageStore(private val root: File) {

    /**
     * Store one picture and answer with the id the drawing should carry, or null if it is not one.
     *
     * Null covers three honest refusals — bytes too large to be worth holding, bytes no decoder
     * recognises, and a write that failed — and the caller's answer to all three is the same: do
     * not attach anything to the drawing. Silently storing a broken file would produce a drawing
     * that points at bytes nothing can draw, which is the state this store exists to avoid.
     *
     * The write goes to a `.part` file and is renamed into place, so a kill mid-write leaves a
     * fragment that [sweep] removes rather than a half-picture under a live id.
     */
    suspend fun put(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_SOURCE_BYTES) return@withContext null
        val encoded = downscale(bytes) ?: return@withContext null
        val id = newId()
        if (!root.isDirectory && !root.mkdirs()) return@withContext null
        val part = File(root, id + PART_SUFFIX)
        val target = File(root, id)
        val written = runCatching { part.writeBytes(encoded) }.isSuccess
        if (!written || !part.renameTo(target)) {
            part.delete()
            return@withContext null
        }
        id
    }

    /**
     * The stored bytes, still encoded, or null when there is nothing there.
     *
     * Encoded rather than decoded because this module has no business making an `ImageBitmap`: the
     * decoded form belongs to the chart layer, which owns the cache that holds it and knows how
     * many it is willing to keep. Null is the whole missing-file contract — see the class note.
     */
    suspend fun read(id: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(id) ?: return@withContext null
        if (!file.isFile) return@withContext null
        runCatching { file.readBytes() }.getOrNull()
    }

    /** Drop one picture. Answers whether there was one to drop. */
    suspend fun forget(id: String): Boolean = withContext(Dispatchers.IO) {
        val file = fileFor(id) ?: return@withContext false
        file.isFile && file.delete()
    }

    /**
     * Delete every stored picture no drawing refers to any more, and answer how many went.
     *
     * Needed because nothing else can do it. A drawing is deleted through `DrawingActions`, which
     * knows nothing about files, and an erased chart takes its rows with it and leaves the pictures
     * — so without a sweep the only way a reader recovers that space is by reinstalling. [keep] is
     * the set of ids currently referenced by *every* drawing the app can still show, which is why
     * the caller collects it rather than this store guessing at it: a picture referenced only by a
     * mark on a saved layout is still in use.
     *
     * Anything in [root] that is neither a valid id nor a `.part` fragment is left alone. This
     * directory is this store's, but deleting a file it cannot account for is how a sweep turns
     * into a bug report about somebody else's data.
     */
    suspend fun sweep(keep: Set<String>): Int = withContext(Dispatchers.IO) {
        val files = root.listFiles() ?: return@withContext 0
        var removed = 0
        for (file in files) {
            val name = file.name
            val stale = when {
                name.endsWith(PART_SUFFIX) -> true
                isImageId(name) -> name !in keep
                else -> false
            }
            if (stale && file.isFile && file.delete()) removed++
        }
        removed
    }

    /** The file an id names, or null when the string is not an id this store wrote. */
    private fun fileFor(id: String): File? = if (isImageId(id)) File(root, id) else null

    /**
     * Decode sampled, scale to fit, re-encode.
     *
     * Two decodes and not one: the first reads the header only (`inJustDecodeBounds`) to find out
     * how big the picture is, which is the only way to choose a sample size without allocating the
     * full bitmap first — the thing this whole function exists to avoid. The sample is a power of
     * two, which is all `BitmapFactory` honours, so it lands *at or above* the cap and the exact
     * fit is done afterwards on an already-small bitmap.
     *
     * PNG only where the picture has transparency. A screenshot of a chart is a photograph as far
     * as JPEG is concerned and compresses ten to one; a PNG of the same thing is the reason a
     * "small" annotation store fills a phone.
     */
    private fun downscale(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        }
        val decoded = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
            .getOrNull() ?: return null
        val fitted = fitToEdge(decoded, MAX_EDGE)
        val out = ByteArrayOutputStream()
        val format = if (fitted.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val ok = runCatching { fitted.compress(format, QUALITY, out) }.getOrDefault(false)
        if (fitted !== decoded) fitted.recycle()
        decoded.recycle()
        return if (ok) out.toByteArray() else null
    }

    /** The bitmap at or under [maxEdge] on its longest side, or the same one when it already is. */
    private fun fitToEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxEdge || longest <= 0) return source
        val scale = maxEdge.toFloat() / longest
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        return runCatching { Bitmap.createScaledBitmap(source, width, height, true) }.getOrDefault(source)
    }

    companion object {

        /**
         * The longest side a stored picture keeps, in pixels.
         *
         * A thousand-and-twenty-four is about four megabytes decoded, which is a number a phone can
         * hold a few of. It is also more than a phone chart can show: the plot is at most eleven
         * hundred pixels wide on a large device, and a picture drawn into a corner of it is a few
         * hundred. Storing more than the screen can draw costs memory and buys nothing.
         */
        const val MAX_EDGE = 1024

        /**
         * The largest file this store will look at.
         *
         * Not a limit on what a camera produces — twenty-four megabytes is a very large photograph
         * — but a floor under the failure: past this the bytes are refused before anything is
         * decoded, so an absurd file is a "no" rather than an allocation the process dies on.
         */
        const val MAX_SOURCE_BYTES = 24 * 1024 * 1024

        /** What every id starts with, so a caption and a picture reference are told apart on sight. */
        const val ID_PREFIX = "img_"

        private const val ID_DIGITS = 16
        private const val QUALITY = 85
        private const val PART_SUFFIX = ".part"

        private val ID = Regex("^$ID_PREFIX[0-9a-f]{$ID_DIGITS}$")

        private val RANDOM = SecureRandom()

        /**
         * Whether a string is one of this store's ids.
         *
         * The path guard, and the only thing standing between reader-typed text and a file name.
         * `core:chart` carries the same rule for the same string — `DrawingImages.idIn` — because
         * that module does not depend on this one, in the same way [StoredDrawing] carries its own
         * copy of the drawing defaults rather than depending on `Drawing`. Both are pinned by
         * tests; if they ever disagree, a picture stops loading rather than a path escaping.
         */
        fun isImageId(value: String?): Boolean = value != null && ID.matches(value)

        /**
         * A fresh id.
         *
         * Random rather than sequential or content-hashed. Sequential would need a counter that
         * survives a reinstall to avoid colliding with restored files; content-hashed would tell a
         * reader who could list the directory that two charts carry the same picture. Sixty-four
         * random bits collide never, at the handful of pictures a chart carries.
         */
        fun newId(): String {
            val bytes = ByteArray(ID_DIGITS / 2)
            RANDOM.nextBytes(bytes)
            return ID_PREFIX + bytes.joinToString("") { "%02x".format(it) }
        }

        /**
         * The power-of-two sample that brings the longest side to at or just above [maxEdge].
         *
         * *At or above*, deliberately: sampling to just under the cap throws away detail the exact
         * scale afterwards cannot get back, and one extra step of a power-of-two ladder is up to
         * half the resolution. Nonsense dimensions answer one, which is the no-op, because a
         * decoder that reported a zero-width picture is a file this store is about to refuse
         * anyway and not a reason to divide by it.
         */
        fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
            if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
            var sample = 1
            while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
            return sample
        }
    }
}

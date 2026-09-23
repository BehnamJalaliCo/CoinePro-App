@file:Suppress("unused")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package java.util

/** `java.util.Locale`, as far as the shared code asks: a tag, a language, and the constants. */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun displayNameJs(type: String, code: String, locale: String): String? =
    js("(function(){ try { var n = new Intl.DisplayNames([locale || 'en'], { type: type, fallback: 'none' }).of(code); return n || null; } catch (e) { return null; } })()")

class Locale(val language: String, val country: String = "", val variant: String = "") {
    fun getLanguage(): String = language
    fun getCountry(): String = country
    fun toLanguageTag(): String = if (country.isEmpty()) language else "$language-$country"
    fun getDisplayLanguage(): String = displayNameJs("language", language, getDefault().toLanguageTag()) ?: language
    fun getDisplayLanguage(inLocale: Locale): String = displayNameJs("language", language, inLocale.toLanguageTag()) ?: language
    fun getDisplayCountry(): String = getDisplayCountry(getDefault())
    fun getDisplayCountry(inLocale: Locale): String =
        if (country.isEmpty()) "" else displayNameJs("region", country, inLocale.toLanguageTag()) ?: country
    fun getDisplayName(): String = getDisplayLanguage()
    override fun equals(other: Any?): Boolean = other is Locale && other.language == language && other.country == country
    override fun hashCode(): Int = language.hashCode() * 31 + country.hashCode()
    override fun toString(): String = if (country.isEmpty()) language else "${language}_$country"

    companion object {
        val US = Locale("en", "US")
        val ENGLISH = Locale("en")
        val UK = Locale("en", "GB")
        val ROOT = Locale("")
        val GERMANY = Locale("de", "DE")
        private var current: Locale = Locale("fa")
        fun getDefault(): Locale = current
        fun setDefault(locale: Locale) { current = locale }
        fun forLanguageTag(tag: String): Locale = tag.split('-', '_').let { Locale(it[0], it.getOrElse(1) { "" }) }
        fun getAvailableLocales(): Array<Locale> = arrayOf(US, Locale("fa"))
        /** ISO 3166 alpha-2 codes, as the JVM lists them: every code `Intl` can name. */
        fun getISOCountries(): Array<String> = isoCountries
        private val isoCountries: Array<String> by lazy {
            val out = ArrayList<String>()
            for (a in 'A'..'Z') for (b in 'A'..'Z') {
                val code = "$a$b"
                val name = displayNameJs("region", code, "en")
                if (name != null && name != code) out += code
            }
            out.toTypedArray()
        }
    }
}

private fun randomUuidJs(): String =
    js("(window.crypto && crypto.randomUUID) ? crypto.randomUUID() : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) { var r = Math.random() * 16 | 0; return (c == 'x' ? r : (r & 3 | 8)).toString(16); })")

class UUID private constructor(private val text: String) : Comparable<UUID> {
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is UUID && other.text == text
    override fun hashCode(): Int = text.hashCode()
    override fun compareTo(other: UUID): Int = text.compareTo(other.text)

    companion object {
        fun randomUUID(): UUID = UUID(randomUuidJs())
        fun fromString(s: String): UUID = UUID(s.lowercase())
        fun nameUUIDFromBytes(bytes: ByteArray): UUID {
            var h = 1125899906842597L
            bytes.forEach { h = 31 * h + it }
            val hex = h.toULong().toString(16).padStart(16, '0') + (h * 31).toULong().toString(16).padStart(16, '0')
            return UUID("${hex.substring(0, 8)}-${hex.substring(8, 12)}-3${hex.substring(13, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}")
        }
    }
}

object Objects {
    fun hash(vararg values: Any?): Int = values.contentHashCode()
    fun equals(a: Any?, b: Any?): Boolean = a == b
    fun <T> requireNonNull(value: T?): T = value ?: throw NullPointerException()
    fun <T> requireNonNull(value: T?, message: String): T = value ?: throw NullPointerException(message)
    fun hashCode(o: Any?): Int = o?.hashCode() ?: 0
    fun isNull(o: Any?): Boolean = o == null
    fun nonNull(o: Any?): Boolean = o != null
}

object Base64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    class Encoder internal constructor(private val url: Boolean, private val pad: Boolean) {
        fun encodeToString(src: ByteArray): String = encode(src).decodeToString()
        fun encode(src: ByteArray): ByteArray {
            val a = if (url) URL_ALPHABET else ALPHABET
            val out = StringBuilder()
            var i = 0
            while (i < src.size) {
                val b0 = src[i].toInt() and 0xff
                val b1 = if (i + 1 < src.size) src[i + 1].toInt() and 0xff else -1
                val b2 = if (i + 2 < src.size) src[i + 2].toInt() and 0xff else -1
                out.append(a[b0 shr 2])
                out.append(a[((b0 and 3) shl 4) or (if (b1 < 0) 0 else b1 shr 4)])
                if (b1 >= 0) out.append(a[((b1 and 15) shl 2) or (if (b2 < 0) 0 else b2 shr 6)]) else if (pad) out.append('=')
                if (b2 >= 0) out.append(a[b2 and 63]) else if (pad) out.append('=')
                i += 3
            }
            return out.toString().encodeToByteArray()
        }
        fun withoutPadding(): Encoder = Encoder(url, false)
    }

    class Decoder internal constructor() {
        fun decode(src: String): ByteArray {
            val clean = src.trim().replace('-', '+').replace('_', '/').trimEnd('=')
            val out = ArrayList<Byte>()
            var buffer = 0
            var bits = 0
            for (c in clean) {
                val v = ALPHABET.indexOf(c)
                if (v < 0) continue
                buffer = (buffer shl 6) or v
                bits += 6
                if (bits >= 8) { bits -= 8; out.add(((buffer shr bits) and 0xff).toByte()) }
            }
            return out.toByteArray()
        }
        fun decode(src: ByteArray): ByteArray = decode(src.decodeToString())
    }

    fun getEncoder(): Encoder = Encoder(url = false, pad = true)
    fun getUrlEncoder(): Encoder = Encoder(url = true, pad = true)
    fun getMimeEncoder(): Encoder = Encoder(url = false, pad = true)
    fun getDecoder(): Decoder = Decoder()
    fun getUrlDecoder(): Decoder = Decoder()
    fun getMimeDecoder(): Decoder = Decoder()
}

open class Random(seed: Long = 0) {
    private val source = kotlin.random.Random(seed)
    open fun nextInt(): Int = source.nextInt()
    open fun nextInt(bound: Int): Int = source.nextInt(bound)
    open fun nextLong(): Long = source.nextLong()
    open fun nextDouble(): Double = source.nextDouble()
    open fun nextBoolean(): Boolean = source.nextBoolean()
    open fun nextBytes(bytes: ByteArray) { source.nextBytes(bytes) }
}

class NoSuchElementException(message: String? = null) : RuntimeException(message)
class ConcurrentModificationException(message: String? = null) : RuntimeException(message)

class Enumeration<T>(items: Iterable<T>) {
    private val iterator = items.iterator()
    fun hasMoreElements(): Boolean = iterator.hasNext()
    fun nextElement(): T = iterator.next()
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun localFieldJs(millis: Double, field: Int): Int = js(
    "(function(){ var d = new Date(millis); switch (field) { case 1: return d.getFullYear(); case 2: return d.getMonth(); case 5: return d.getDate(); case 7: return d.getDay() + 1; case 11: return d.getHours(); case 12: return d.getMinutes(); case 13: return d.getSeconds(); case 14: return d.getMilliseconds(); default: return 0; } })()",
)

/** `java.util.Calendar` in the browser's own zone — the fields the phone reads, from `Date`. */
class Calendar private constructor(var timeInMillis: Long) {
    fun get(field: Int): Int = localFieldJs(timeInMillis.toDouble(), field)
    val time: Date get() = Date(timeInMillis)
    fun add(field: Int, amount: Int) {
        timeInMillis += when (field) {
            DAY_OF_MONTH, DAY_OF_YEAR -> amount * 86_400_000L
            HOUR_OF_DAY, HOUR -> amount * 3_600_000L
            MINUTE -> amount * 60_000L
            SECOND -> amount * 1_000L
            MILLISECOND -> amount.toLong()
            else -> 0L
        }
    }
    companion object {
        const val YEAR = 1
        const val MONTH = 2
        const val DAY_OF_MONTH = 5
        const val DAY_OF_YEAR = 6
        const val DAY_OF_WEEK = 7
        const val HOUR = 10
        const val HOUR_OF_DAY = 11
        const val MINUTE = 12
        const val SECOND = 13
        const val MILLISECOND = 14
        fun getInstance(): Calendar = Calendar(com.coinepro.web.jvm.nowMillisJs().toLong())
        fun getInstance(zone: Any?): Calendar = getInstance()
    }
}

class Date(val time: Long = com.coinepro.web.jvm.nowMillisJs().toLong()) {
    fun getTime(): Long = time
}

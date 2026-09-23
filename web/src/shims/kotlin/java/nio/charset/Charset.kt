package java.nio.charset

/** UTF-8 is the only charset the shared code names, and the only one a page's text is in. */
class Charset private constructor(private val canonical: String) {
    fun name(): String = canonical
    fun displayName(): String = canonical
    override fun toString(): String = canonical
    companion object {
        fun forName(name: String): Charset = Charset(name.uppercase())
        fun defaultCharset(): Charset = StandardCharsets.UTF_8
    }
}

object StandardCharsets {
    val UTF_8: Charset = Charset.forName("UTF-8")
    val US_ASCII: Charset = Charset.forName("US-ASCII")
    val ISO_8859_1: Charset = Charset.forName("ISO-8859-1")
    val UTF_16: Charset = Charset.forName("UTF-16")
}

class CharacterCodingException : java.io.IOException()

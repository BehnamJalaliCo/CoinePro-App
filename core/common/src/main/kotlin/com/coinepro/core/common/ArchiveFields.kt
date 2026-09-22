package com.coinepro.core.common

/**
 * A record of free text, written so that nothing a reader typed can break it.
 *
 * ### Why not a separator
 *
 * Every other codec in this app joins fields with an ASCII control character and drops any value
 * that contains one — which is right for an id, a timeframe or an indicator name, because none of
 * those is prose and a value carrying a separator is corrupt rather than unusual.
 *
 * It is wrong for a journal entry. The note and the lesson are the parts a reader wrote by hand,
 * and «drop the field that has a separator in it» means losing exactly the sentence they cared
 * about, silently, on the one record in this app that cannot be refetched from anywhere. Pasting
 * from a chat app is enough to do it: those carry bidi marks and the occasional `\u001E` nobody
 * can see.
 *
 * So there is no separator at all. Every field is `<length>:<value>`, one after another with
 * nothing between them, and the reader tells the parser how far to go. Any character is a legal
 * value, including a newline, a control character and the colon itself.
 *
 * ### Positional rather than keyed
 *
 * The caller writes a fixed list and reads it back by index, because a key would be a second
 * definition of the record's shape and the shape lives in the store that owns it. A field added
 * later appends; a build that has not heard of it reads the ones it knows and stops.
 *
 * Pure Kotlin, so the web build reads what the phone wrote.
 */
object ArchiveFields {

    fun write(values: List<String>): String = buildString {
        for (value in values) append(value.length).append(':').append(value)
    }

    /**
     * The fields in [text], or an empty list when it is not a field block.
     *
     * Empty rather than partial: half a journal entry is a note with its ending missing, which
     * reads as something the reader did not write. A record that will not parse is dropped whole
     * and the import says how many it took, which is a number they can check.
     */
    fun read(text: String): List<String> {
        val values = mutableListOf<String>()
        var cursor = 0
        while (cursor < text.length) {
            val colon = text.indexOf(':', cursor)
            if (colon <= cursor) return emptyList()
            val length = text.substring(cursor, colon).toIntOrNull()?.takeIf { it >= 0 } ?: return emptyList()
            val start = colon + 1
            val end = start + length
            if (end > text.length) return emptyList()
            values += text.substring(start, end)
            cursor = end
        }
        return values
    }

    /** The field at [index], or empty — so a record from a later build is read as far as it goes. */
    fun List<String>.field(index: Int): String = getOrNull(index).orEmpty()
}

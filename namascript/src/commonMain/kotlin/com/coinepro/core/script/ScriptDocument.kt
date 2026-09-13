package com.coinepro.core.script

/**
 * **A script the reader owns** — «ذخیره به نام خودم» (run Σ, S3 C).
 *
 * ### What makes this different from a preset
 *
 * A preset is the app's; this is the reader's. Which means it has the things a possession has and a
 * fixture does not: a name they chose, a sentence saying what it is for, a colour they will
 * recognise in a list, tags, and — the part that matters most the first time they break it — a
 * [history]. Five revisions, because a reader who pasted an assistant's answer over a script that
 * worked needs the old one back, and because a reader who has to *think* about saving will not.
 *
 * ### What it deliberately is not
 *
 * It is not a file format anybody else parses, and it is not a transport. [ScriptFile] writes it as
 * text so a reader can e-mail themselves a script, and [ScriptLink] addresses one on the web — but
 * the link carries an **id and nothing else**. A link that carried source would be a link that runs
 * code on the strength of having been tapped, and a reader who taps a link in a group chat has not
 * agreed to that. What an id buys is that the code arrives from the community service, where it can
 * be shown to the reader before it is added to anything.
 *
 * Pure Kotlin, so the same document survives into the web build.
 */
data class ScriptDocument(
    /** Stable identity, and what a share link addresses. */
    val id: String,
    val name: String,
    /** One line, under the name in My scripts. */
    val description: String,
    val source: String,
    /** The line's colour, as the app's `0xAARRGGBB`. */
    val colour: Long = DEFAULT_COLOUR,
    val tags: List<String> = emptyList(),
    /** Whether it belongs in its own panel rather than over the price. */
    val ownPane: Boolean = false,
    /** Values the reader set for the script's own `input(...)` declarations, by title. */
    val defaults: Map<String, Double> = emptyMap(),
    /** Where it came from — a preset id, a library id, a template id, or null for a paste. */
    val origin: String? = null,
    val updatedAt: Long = 0L,
    /** Earlier revisions, newest first, at most [REVISIONS]. */
    val history: List<ScriptRevision> = emptyList(),
) {

    /**
     * This document with a new [source], the old one pushed onto the history.
     *
     * An edit that changed nothing is not a revision: a reader who opens a script, looks at it and
     * closes it has not used up one of their five. That is not an optimisation — five revisions of
     * an identical script is a history that has lost the thing it was kept for.
     */
    fun edited(source: String, at: Long): ScriptDocument {
        if (source == this.source) return copy(updatedAt = at)
        return copy(
            source = source,
            updatedAt = at,
            history = (listOf(ScriptRevision(this.source, updatedAt)) + history).take(REVISIONS),
        )
    }

    /** This document rolled back to [revision], which itself becomes the newest history entry. */
    fun restored(revision: ScriptRevision, at: Long): ScriptDocument = edited(revision.source, at)

    /** The name, trimmed to something a list can show, with a fallback for a reader who typed none. */
    fun displayName(english: Boolean): String =
        name.trim().takeIf { it.isNotEmpty() } ?: if (english) "Untitled script" else "اسکریپت بی‌نام"

    companion object {
        /** How many earlier revisions are kept. The brief's number. */
        const val REVISIONS: Int = 5

        /** The app's gold, for a reader who chose no colour. */
        const val DEFAULT_COLOUR: Long = 0xFFD8A848

        /** How long a name may be before a list stops being able to show it. */
        const val NAME_LIMIT: Int = 60

        const val DESCRIPTION_LIMIT: Int = 140

        /**
         * A new document for [source], with everything a reader has not chosen yet defaulted.
         *
         * The id is derived from the source and the clock rather than drawn at random, so the same
         * script saved twice in the same millisecond is the same id — which is what stops a
         * double-tap on «ذخیره» from producing two entries a reader then has to tell apart.
         */
        fun of(
            source: String,
            name: String,
            at: Long,
            description: String = "",
            origin: String? = null,
        ): ScriptDocument = ScriptDocument(
            id = idFor(source, at),
            name = name.trim().take(NAME_LIMIT),
            description = description.trim().replace('\n', ' ').take(DESCRIPTION_LIMIT),
            source = source,
            origin = origin,
            updatedAt = at,
        )

        /**
         * An id for [source] saved at [at]: eleven characters of the alphabet a URL carries unescaped.
         *
         * Not a hash anybody should rely on for identity across devices — two readers who write the
         * same script in the same millisecond collide — because identity across devices is the
         * community service's to assign when a script is shared. This one has to be stable for a
         * double tap and short enough to read out loud.
         */
        fun idFor(source: String, at: Long): String {
            var hash = 0xCBF29CE484222325UL.toLong()
            for (character in source) {
                hash = (hash xor character.code.toLong()) * 0x100000001B3L
            }
            hash = hash xor at
            return buildString {
                var remaining = hash
                repeat(ID_LENGTH) {
                    append(ID_ALPHABET[((remaining ushr (it * 5)) and 0x1FL).toInt()])
                    remaining = remaining xor (remaining ushr 7)
                }
            }
        }

        private const val ID_LENGTH = 11

        /**
         * Thirty-two characters, and the four that are read wrong aloud are not among them.
         *
         * No `l` or `1`, no `o` or `0`: an id goes in a message and then into somebody's mouth —
         * «it's pro-chart dot com slash s slash…» — and a pair a listener cannot tell apart is a
         * link that arrives at nothing. Exactly thirty-two so five bits of the hash choose one
         * without a modulo bending the distribution.
         */
        private const val ID_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
    }
}

/** One earlier version of a script, kept so an edit can be undone after the fact. */
data class ScriptRevision(val source: String, val at: Long)

/**
 * The `.nama` file: a header a person can read, then the script.
 *
 * ### Why text and not JSON
 *
 * Because the file is opened by the reader as often as by the app — it goes in a message, a note, a
 * repository — and the first thing they see should be the script rather than a wrapper. The header
 * is comment lines, so a `.nama` file with its header intact is *still a runnable script*: paste the
 * whole thing into the studio and it works, header and all. That property is the reason for the
 * format, and `ScriptDocumentTest` holds it.
 */
object ScriptFile {

    const val EXTENSION: String = "nama"

    /** The format's own version, in the first line, so a later reader knows what it has. */
    const val VERSION: Int = 1

    private const val PREFIX = "//"
    private const val SEPARATOR = "// ---"

    fun write(document: ScriptDocument): String = buildString {
        appendLine("$PREFIX nama $VERSION")
        appendLine("$PREFIX id: ${document.id}")
        appendLine("$PREFIX name: ${oneLine(document.name)}")
        if (document.description.isNotBlank()) appendLine("$PREFIX description: ${oneLine(document.description)}")
        appendLine("$PREFIX colour: ${document.colour.toString(16).uppercase()}")
        if (document.tags.isNotEmpty()) appendLine("$PREFIX tags: ${document.tags.joinToString(", ") { oneLine(it) }}")
        appendLine("$PREFIX pane: ${if (document.ownPane) "own" else "price"}")
        for ((title, value) in document.defaults) appendLine("$PREFIX input: ${oneLine(title)} = $value")
        document.origin?.let { appendLine("$PREFIX origin: ${oneLine(it)}") }
        appendLine(SEPARATOR)
        append(document.source.trimEnd())
        appendLine()
    }

    /**
     * Reads a `.nama` file, or returns null when it is not one.
     *
     * Null and not an exception, and null rather than a half-read document: a file that is not this
     * format is far more likely to be a *bare script* somebody saved with the wrong extension, and
     * the caller's right answer to that is to treat the whole thing as source. Guessing which half
     * of a malformed header to believe would be worse than not reading it.
     */
    fun read(text: String): ScriptDocument? {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim()?.startsWith("$PREFIX nama ") != true) return null
        val separator = lines.indexOfFirst { it.trim() == SEPARATOR }
        if (separator < 0) return null
        var id = ""
        var name = ""
        var description = ""
        var colour = ScriptDocument.DEFAULT_COLOUR
        var tags = emptyList<String>()
        var ownPane = false
        var origin: String? = null
        val defaults = LinkedHashMap<String, Double>()
        for (line in lines.subList(1, separator)) {
            val body = line.trim().removePrefix(PREFIX).trim()
            val key = body.substringBefore(':', missingDelimiterValue = "").trim()
            val value = body.substringAfter(':', missingDelimiterValue = "").trim()
            when (key) {
                "id" -> id = value
                "name" -> name = value
                "description" -> description = value
                "colour" -> colour = value.toLongOrNull(16) ?: colour
                "tags" -> tags = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                "pane" -> ownPane = value == "own"
                "origin" -> origin = value.takeIf { it.isNotEmpty() }
                "input" -> {
                    val title = value.substringBeforeLast('=').trim()
                    val number = value.substringAfterLast('=').trim().toDoubleOrNull()
                    if (title.isNotEmpty() && number != null) defaults[title] = number
                }
            }
        }
        val source = lines.subList(separator + 1, lines.size).joinToString("\n").trim()
        if (id.isEmpty() || source.isEmpty()) return null
        return ScriptDocument(
            id = id,
            name = name,
            description = description,
            source = source,
            colour = colour,
            tags = tags,
            ownPane = ownPane,
            defaults = defaults,
            origin = origin,
        )
    }

    /** A header value that cannot break the header: one line, no colon-swallowing, bounded. */
    private fun oneLine(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').trim().take(ScriptDocument.DESCRIPTION_LIMIT)
}

/**
 * `pro-chart.com/s/<id>` — a script's address (run Σ, S3 C and D).
 *
 * ### The id is the whole payload, and that is the point
 *
 * A link that carried the source would be an invitation to run a stranger's code on the strength of
 * a tap, from a message nobody can vouch for. This one carries an identifier; the app fetches the
 * script from the community service, shows it to the reader, and *then* offers to add it. Every
 * step after the tap is something the reader can see and refuse.
 *
 * [idOf] is written the way the app's other link readers are: it refuses anything that is not the
 * shape an id has, rather than sanitising it, because a link that needed cleaning up was not one of
 * ours and quietly opening a *different* script than it named would be worse than opening none.
 */
object ScriptLink {

    /** The brand host. Mirrored from `BrandConfig.WEB_HOST`, which `:namascript` cannot see. */
    const val HOST: String = "pro-chart.com"

    const val PATH: String = "s"

    fun of(id: String): String = "https://$HOST/$PATH/$id"

    /** The script id in [url], or null when it is not one of ours. */
    fun idOf(url: String): String? {
        val withoutScheme = url.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
        if (!withoutScheme.startsWith("$HOST/")) return null
        val rest = withoutScheme.removePrefix("$HOST/")
        if (!rest.startsWith("$PATH/")) return null
        val id = rest.removePrefix("$PATH/").substringBefore('?').substringBefore('#').trimEnd('/')
        return id.takeIf { ID.matches(it) }
    }

    /** The id as [ScriptDocument.idFor] writes it, and nothing else. */
    private val ID = Regex("^[a-km-np-z2-9]{6,32}$")
}

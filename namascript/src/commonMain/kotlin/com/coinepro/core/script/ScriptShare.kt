package com.coinepro.core.script

/**
 * **Sharing a script on the board** — the post's body, and reading one back (run Σ, S3 item D).
 *
 * ### Why the post carries the code and not a link
 *
 * The brief asks for «share → a community post with the code, a chart snapshot and a one-tap
 * install», and the obvious shape for the install is an address: `pro-chart.com/s/<id>`, which
 * `ScriptLink` already spells. Two things rule it out, and the second is the one that settles it.
 *
 * First, there is no service behind that address. A link that opens nothing is worse than no link.
 *
 * Second — and this holds even after the service exists — **the board refuses links.** Every post
 * goes through a server-side block on URLs, phone numbers and messenger handles, each with its own
 * Persian sentence. A share that put an address in the body would be refused at the door, and the
 * reader would be left holding a post they cannot publish and a rule they did not break on purpose.
 *
 * So the post carries the script itself, as the `.nama` document `ScriptFile` already writes — a
 * comment-headed file which is *also* runnable source, which is why that format was chosen. The
 * install is then a local operation: the app recognises a post whose body carries a document, and
 * offers to open it in the studio. Nothing is fetched, nothing runs on a tap, and the code the
 * reader installs is the code they can read in the post above the button.
 *
 * ### What is deliberately not here
 *
 * No signature, no author claim, no «verified» mark. A post's author is the board's, and a script
 * in a post is exactly as trustworthy as the person who wrote the post — which the reader can see.
 * A badge here would be the app vouching for code it has not run.
 */
object ScriptShare {

    /**
     * The body of a post that shares [document].
     *
     * A sentence a reader scrolling the board can act on, then the document. The sentence is first
     * because the board shows the beginning of a post in the feed and cuts it at two hundred
     * characters: a post that opened with `// nama 1` would be, in every list it appears in, a
     * wall of header.
     */
    fun post(document: ScriptDocument, english: Boolean = false): String = buildString {
        appendLine(if (english) "A script of mine: ${document.name}" else "یک اسکریپت از من: ${document.name}")
        if (document.description.isNotBlank()) appendLine(document.description)
        appendLine()
        append(ScriptFile.write(document))
    }

    /** Whether [body] carries a script somebody can open. */
    fun carries(body: String): Boolean = scriptIn(body) != null

    /**
     * A post split into what the author wrote and the file they attached.
     *
     * The screen needs the two halves separately because they are read in opposite directions: the
     * prose is Persian and lays out right-to-left, the file is code and must not. Rendering the
     * whole post as one paragraph is what produced «1 nama //» on the board — see
     * `CoineProCodeBlock` for the four decisions a code block actually needs.
     *
     * `code` is null on an ordinary post, and then `prose` is the whole of it. The split is on the
     * text as written: nothing is re-serialised, so what the reader sees is what the author sent,
     * character for character.
     */
    fun split(body: String): Post {
        val start = body.indexOf(HEADER)
        if (start < 0 || ScriptFile.read(body.substring(start)) == null) return Post(body, null)
        return Post(prose = body.substring(0, start).trimEnd(), code = body.substring(start))
    }

    /** One post, in its two halves. See [split]. */
    data class Post(val prose: String, val code: String?)

    /**
     * The script inside a post, or null.
     *
     * The document need not start the post: a reader may write three paragraphs about what they
     * were thinking and paste the file under them, which is the best kind of post on this board and
     * would be a pity to refuse. So the header is looked for anywhere in the text and the document
     * is read from there to the end.
     *
     * Read rather than trusted: `ScriptFile.read` answers null on anything that is not the format,
     * so a post that merely quotes the first line of one does not produce half a script.
     */
    fun scriptIn(body: String): ScriptDocument? {
        val start = body.indexOf(HEADER)
        if (start < 0) return null
        return ScriptFile.read(body.substring(start))
    }

    /**
     * What the board will refuse in [body], as the keys of the rules it enforces.
     *
     * Checked here so the studio's own button can say «this has a link in it» before the round
     * trip, rather than the reader discovering the rule from a rejection. The same reasoning the
     * composer already uses for the length bounds.
     *
     * Not a claim to match the server exactly — it cannot, and it must not pretend to. The server
     * decides; this only catches the three that are obvious, and a body this finds nothing in can
     * still be refused for a reason this build has never heard of. That is why the refusal path
     * keeps the reader's text in the composer.
     */
    fun refusals(body: String): List<String> = buildList {
        if (URL.containsMatchIn(body)) add(LINK)
        if (PHONE.containsMatchIn(body)) add(NUMBER)
        if (HANDLE.containsMatchIn(body)) add(HANDLE_KEY)
    }

    /** A link — `http://…`, a bare `example.com/…`, or a `www.` */
    const val LINK: String = "link"

    /** A phone number, Iranian mobile or otherwise: ten digits or more in a row. */
    const val NUMBER: String = "number"

    /** A messenger handle — `@somebody`. */
    const val HANDLE_KEY: String = "handle"

    private const val HEADER = "// nama "

    private val URL = Regex("""(https?://|www\.)\S+|\b[a-z0-9-]+\.(com|ir|org|net|me|io)\b""", RegexOption.IGNORE_CASE)

    private val PHONE = Regex("""[0-9۰-۹]{10,}""")

    // `//@version` is a comment an assistant's script arrives with and is not a handle, so the `@`
    // has to be preceded by something that is not a letter of a word — which is what the boundary
    // below says, and why this is not simply `@\w+`.
    private val HANDLE = Regex("""(^|[\s(،,])@[A-Za-z][A-Za-z0-9_]{3,}""")
}

package com.coinepro.core.diagnostics

/**
 * Strips credentials out of anything on its way into the log.
 *
 * ### Why this runs at the write and not at the export
 *
 * The obvious place to redact is the moment a file is produced, and it is the wrong place. An
 * export is one of four ways an entry leaves this app — it is also drawn on the panel, copied to
 * the clipboard one line at a time, and written into the crash file that survives the process. A
 * scrubber that only ran at export would leave a bearer token sitting in the ring for the other
 * three to hand out, and it would leave it on disk, where the log now persists.
 *
 * So the rule is stronger than "do not export secrets": **a secret never enters the log at all**.
 * [AppLog] passes every message, every field value and every error string through here before the
 * entry is constructed, which means the ring, the file, the clipboard and the export are all safe
 * because the same single check made them so. The export runs it a second time anyway — see
 * [DiagnosticExport] — not because the first pass is doubted but because the export also carries
 * text that never went through the log, such as a base URL the build was configured with.
 *
 * ### What it looks for
 *
 * Two kinds, because credentials arrive in two shapes.
 *
 * The first is a value that is recognisable on its own: a JWT, an `Authorization: Bearer …` header,
 * a Google API key, an OpenAI key, a GitHub token, an AWS access key id. These have a fixed prefix
 * or a fixed structure and can be matched wherever they appear, including in the middle of a
 * sentence somebody wrote by hand.
 *
 * The second is a value that is only a secret because of the word in front of it — `token=…`,
 * `password: …`, `apiKey=…`. Here the *name* is the signal and it is deliberately kept: an operator
 * reading `token=[redacted]` learns that a token was involved, which is most of what the line was
 * worth, and learns nothing that is worth stealing. This is the same rule the working agreement
 * states for the backend repositories — print the variable's name, never its value.
 *
 * ### What it deliberately does not do
 *
 * It does not redact long opaque strings on shape alone. A certificate fingerprint, a symbol id, a
 * correlation id and a base64 avatar hash are all forty characters of noise, and a rule that ate
 * them would produce a log whose every interesting field reads `[redacted]` — which is a log
 * nobody can diagnose from, and an operator who then turns redaction off. Where a genuine
 * identifier has to be correlated across lines, [AppLog.redact] keeps its shape and drops its
 * content on purpose.
 */
object Redaction {

    /** What replaces a value. Bracketed and lower-case so it can never be mistaken for one. */
    const val PLACEHOLDER: String = "[redacted]"

    /**
     * Credentials recognisable from their own shape.
     *
     * Each is anchored on a prefix or a structure that nothing else in this app produces, so a
     * match is a credential rather than a coincidence. The JWT pattern is first because it is the
     * one this app actually handles: both servers issue them and both put them in a header.
     */
    private val SELF_EVIDENT: List<Regex> = listOf(
        // A JWT: three base64url segments. The `eyJ` prefix is `{"` encoded, which is what makes a
        // JWT identifiable without parsing it.
        Regex("""eyJ[A-Za-z0-9_-]{6,}\.[A-Za-z0-9_-]{6,}(?:\.[A-Za-z0-9_-]+)?"""),
        // The header as it is written, whatever the scheme. Case-insensitive because
        // `bearer`/`Bearer` both occur in exception messages produced by different libraries.
        Regex("""(?i)\b(?:bearer|basic|token)\s+[A-Za-z0-9._~+/=-]{8,}"""),
        Regex("""AIza[0-9A-Za-z_-]{20,}"""),
        Regex("""sk-(?:proj-)?[A-Za-z0-9_-]{16,}"""),
        Regex("""gh[pousr]_[A-Za-z0-9]{16,}"""),
        Regex("""(?:AKIA|ASIA)[0-9A-Z]{16}"""),
        Regex("""xox[baprs]-[A-Za-z0-9-]{10,}"""),
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----"""),
    )

    /**
     * A named value: the word decides, and the word survives.
     *
     * `password`, not `passwordPolicy`: the name is bounded on both sides so a field called
     * `tokenCount` — a perfectly ordinary number this panel shows — is not mistaken for a token.
     */
    private val NAMED = Regex(
        """(?i)\b(pass(?:word|wd)?|pwd|secret|token|api[_-]?key|apikey|access[_-]?key|""" +
            """refresh[_-]?token|id[_-]?token|authorization|auth[_-]?header|credential|""" +
            """private[_-]?key|client[_-]?secret|otp|passcode|pin)\b\s*[=:]\s*"?([^\s",;&}\])]{1,})"?""",
    )

    /**
     * The same names again, as a whole field key rather than as a word inside a sentence.
     *
     * A field arrives as a key and a value that were never one string, so `token=abc` — the shape
     * [NAMED] recognises — is never formed and the value goes through untouched. This was a real
     * hole rather than a theoretical one: `fields = mapOf("refresh_token" to token)` is exactly how
     * a call site would naturally write it, and it is the most likely way a credential would ever
     * have reached the log.
     */
    private val NAMED_KEY = Regex(
        """(?i)^(?:[a-z0-9]+[._-])?(pass(?:word|wd)?|pwd|secret|token|api[_-]?key|apikey|""" +
            """access[_-]?key|refresh[_-]?token|id[_-]?token|authorization|auth[_-]?header|""" +
            """credential|private[_-]?key|client[_-]?secret|otp|passcode|pin)$""",
    )

    /**
     * An address is a person, not a machine.
     *
     * A reader's email is in the sign-in path and in a support handoff, and it is the one value in
     * this app that identifies the human rather than the install. It is redacted for privacy
     * rather than for security, which is why it is here and not in the list above.
     */
    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    /** Every rule, over one string. Safe on any input, including one that is already clean. */
    fun scrub(value: String): String {
        if (value.isEmpty()) return value
        var result = value
        for (pattern in SELF_EVIDENT) {
            result = pattern.replace(result, PLACEHOLDER)
        }
        // The name is rebuilt from the match rather than kept by a back-reference through the
        // separator, so `token: abc` and `token=abc` both come out in one canonical shape an
        // operator can grep for.
        result = NAMED.replace(result) { match -> match.groupValues[1] + "=" + PLACEHOLDER }
        result = EMAIL.replace(result, PLACEHOLDER)
        return result
    }

    /** The nullable overload, named rather than overloaded — the two erase to one JVM signature. */
    fun scrubOrNull(value: String?): String? = value?.let(::scrub)

    /**
     * A field map, keys kept and values scrubbed.
     *
     * The key is scrubbed too, because a field key is written by a call site and a call site can
     * be wrong: `fields = mapOf(token to "3")` puts the credential in the key. Cheap to check,
     * and the one place a careful rule would otherwise have a hole in it.
     */
    fun scrub(fields: Map<String, String>): Map<String, String> =
        if (fields.isEmpty()) {
            fields
        } else {
            fields.entries.associate { (key, value) ->
                val scrubbedKey = scrub(key)
                scrubbedKey to if (NAMED_KEY.matches(key)) PLACEHOLDER else scrub(value)
            }
        }
}

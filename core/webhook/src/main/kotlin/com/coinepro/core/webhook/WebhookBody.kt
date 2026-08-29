package com.coinepro.core.webhook

/**
 * What one fired alert says on the wire, and what content type it is sent as — [142].
 *
 * ### The content-type rule is TradingView's, and it is right
 *
 * They send `application/json` when the alert message parses as JSON and `text/plain` when it does
 * not. That single rule is what lets one field serve two completely different readers: somebody
 * whose receiver is a bot writes `{"side":"buy","symbol":"BTCUSDT"}` and gets structured data;
 * somebody whose receiver is a chat room writes «طلا به ۲۶۰۰ رسید» and gets a sentence. Any other
 * design needs a format switch in the editor that most readers would get wrong once.
 *
 * The claim has to be *true*, though, which is why [looksLikeJson] actually parses rather than
 * checking for a leading brace. A body announced as `application/json` that turns out to be a
 * sentence with a `{` in it is a 400 from the receiver at three in the morning, and the reader
 * would have no way to know why.
 *
 * ### And when the reader wrote nothing
 *
 * A default envelope, composed by [WebhookEvent.defaultBody]: the alert, the market, the price and
 * the time, as JSON. That is the body most receivers want and none of them can reconstruct from a
 * sentence.
 */
object WebhookBody {

    const val JSON: String = "application/json"
    const val TEXT: String = "text/plain"

    /** The content type [body] should be sent as. */
    fun contentTypeOf(body: String): String = if (looksLikeJson(body)) JSON else TEXT

    /**
     * Whether [body] is a well-formed JSON object or array.
     *
     * ### Why this is written out rather than delegated
     *
     * `org.json` is on the Android classpath but is a stub on the JVM, so every unit test of this
     * rule would have to run on a device — for a decision that is one header. Gson would work and
     * is a dependency this module otherwise does not need, for the same one header. What is needed
     * is not a parser that *builds* anything: it is a scanner that says yes or no, and that is
     * eighty lines with no allocation and no dependency.
     *
     * Objects and arrays only, deliberately. A bare `12` and a bare `"hello"` are technically valid
     * JSON documents and nobody's webhook receiver treats them as JSON — a reader whose alert
     * message is the number `12` means the text `12`.
     */
    fun looksLikeJson(body: String): Boolean {
        val text = body.trim()
        if (text.length < 2) return false
        if (text[0] != '{' && text[0] != '[') return false
        val scanner = Scanner(text)
        if (!scanner.value()) return false
        scanner.whitespace()
        return scanner.done
    }

    /**
     * A one-pass recursive scanner over a JSON document.
     *
     * It answers one question — is this well formed — so it keeps no result and builds no tree. The
     * recursion is bounded by [MAX_DEPTH] rather than by the stack: an alert body is written by a
     * person, and a document nested two hundred deep is either a mistake or an attempt to overflow
     * something, and neither should be answered by a crash.
     */
    private class Scanner(private val text: String) {
        private var at = 0
        private var depth = 0

        val done: Boolean get() = at >= text.length

        fun whitespace() {
            while (at < text.length && text[at].isWhitespace()) at++
        }

        /** One value of any kind, leaving [at] just past it. False on anything malformed. */
        fun value(): Boolean {
            whitespace()
            if (done) return false
            return when (text[at]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                else -> number()
            }
        }

        private fun obj(): Boolean {
            if (++depth > MAX_DEPTH) return false
            at++
            whitespace()
            if (!done && text[at] == '}') {
                at++
                depth--
                return true
            }
            while (true) {
                whitespace()
                if (done || text[at] != '"') return false
                if (!string()) return false
                whitespace()
                if (done || text[at] != ':') return false
                at++
                if (!value()) return false
                whitespace()
                if (done) return false
                when (text[at]) {
                    ',' -> at++
                    '}' -> {
                        at++
                        depth--
                        return true
                    }
                    else -> return false
                }
            }
        }

        private fun array(): Boolean {
            if (++depth > MAX_DEPTH) return false
            at++
            whitespace()
            if (!done && text[at] == ']') {
                at++
                depth--
                return true
            }
            while (true) {
                if (!value()) return false
                whitespace()
                if (done) return false
                when (text[at]) {
                    ',' -> at++
                    ']' -> {
                        at++
                        depth--
                        return true
                    }
                    else -> return false
                }
            }
        }

        private fun string(): Boolean {
            at++
            while (at < text.length) {
                when (val char = text[at]) {
                    '"' -> {
                        at++
                        return true
                    }
                    '\\' -> {
                        at++
                        if (at >= text.length) return false
                        if (text[at] == 'u') {
                            if (at + 4 >= text.length) return false
                            for (offset in 1..4) {
                                if (!isHex(text[at + offset])) return false
                            }
                            at += 4
                        } else if (text[at] !in ESCAPES) {
                            return false
                        }
                        at++
                    }
                    // A raw control character inside a string is invalid JSON, and it is exactly
                    // what a pasted multi-line alert message contains.
                    else -> if (char.code < 0x20) return false else at++
                }
            }
            return false
        }

        private fun literal(word: String): Boolean {
            if (!text.startsWith(word, at)) return false
            at += word.length
            return true
        }

        private fun number(): Boolean {
            val start = at
            if (!done && text[at] == '-') at++
            var digits = 0
            while (at < text.length && text[at].isDigit()) {
                at++
                digits++
            }
            if (digits == 0) return false
            if (at < text.length && text[at] == '.') {
                at++
                var decimals = 0
                while (at < text.length && text[at].isDigit()) {
                    at++
                    decimals++
                }
                if (decimals == 0) return false
            }
            if (at < text.length && (text[at] == 'e' || text[at] == 'E')) {
                at++
                if (at < text.length && (text[at] == '+' || text[at] == '-')) at++
                var exponent = 0
                while (at < text.length && text[at].isDigit()) {
                    at++
                    exponent++
                }
                if (exponent == 0) return false
            }
            return at > start
        }

        private fun isHex(char: Char): Boolean =
            char.isDigit() || char in 'a'..'f' || char in 'A'..'F'

        private companion object {
            const val MAX_DEPTH = 32
            val ESCAPES = charArrayOf('"', '\\', '/', 'b', 'f', 'n', 'r', 't')
        }
    }
}

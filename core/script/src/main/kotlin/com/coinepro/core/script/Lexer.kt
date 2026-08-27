package com.coinepro.core.script

/**
 * NamaScript — the app's own scripting language, named after بازارنما.
 *
 * ## Why a language at all
 *
 * Every indicator this app ships is a decision somebody else made: fourteen periods, two standard
 * deviations, close rather than typical price. A reader who wants the same idea with different
 * numbers, or two ideas combined, has no way to say so — and "add another toggle" does not scale
 * past the third request. A small language is the honest answer to an unbounded number of small
 * questions.
 *
 * ## Why it is evaluated over whole series rather than bar by bar
 *
 * The obvious design is Pine's: run the script once per bar and let every function keep rolling
 * state keyed by its call site. It is also the design that produces the subtlest bugs in the
 * language it comes from — a function called inside a branch accumulates a different history than
 * one called outside it, and nothing about the text says so.
 *
 * NamaScript evaluates each expression **once, over the whole series**. `close` is an array,
 * `ta.sma(close, 14)` is an array, `close > ta.sma(close, 14)` is an array of booleans, and a
 * scalar broadcasts against any of them. There is no per-call-site state to get wrong, no bar loop
 * to reason about, and every function in the existing indicator library — already written against
 * whole arrays — is usable without a wrapper.
 *
 * The cost is that imperative per-bar loops are not expressible. In exchange, `iff` and `[n]` cover
 * what those loops are actually used for, and the result of a script is a pure function of its
 * input. That trade is worth stating because somebody who knows Pine will look for `var` and
 * `for`, and their absence is a decision rather than an omission.
 */
internal enum class TokenType {
    NUMBER, STRING, IDENT,
    TRUE, FALSE,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, NEQ, LT, GT, LTE, GTE,
    AND, OR, NOT,
    ASSIGN, REASSIGN,
    QUESTION, COLON, COMMA, DOT,
    LPAREN, RPAREN, LBRACKET, RBRACKET,
    NEWLINE, EOF,
}

internal data class Token(
    val type: TokenType,
    val text: String,
    /** 1-based, so an error message names the line a reader sees in the editor's gutter. */
    val line: Int,
    val column: Int,
    val number: Double = 0.0,
)

/**
 * Refused before anything runs, with a position.
 *
 * A script error that says only "syntax error" is a script error the reader cannot act on. Every
 * failure in this package carries the line and column it happened at, and the editor puts a caret
 * there.
 */
class ScriptError(
    message: String,
    val line: Int = 0,
    val column: Int = 0,
) : Exception(if (line > 0) "خط $line: $message" else message) {
    /** The message without the line prefix, for a caller that renders the position itself. */
    val bare: String = message
}

/**
 * Text to tokens.
 *
 * Newlines are significant — a statement ends at one — because a language for people who are not
 * programmers should not require a terminator they will forget. Blank lines and comments collapse,
 * so an ordinary reader never meets the rule.
 */
internal class Lexer(private val source: String) {

    private var position = 0
    private var line = 1
    private var column = 1

    fun scan(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            skipBlanksAndComments()
            if (position >= source.length) break
            tokens += next()
        }
        // One terminator, always, so the parser never has to test for the end of input separately.
        tokens += Token(TokenType.EOF, "", line, column)
        return tokens
    }

    private fun skipBlanksAndComments() {
        while (position < source.length) {
            val character = source[position]
            when {
                character == '/' && position + 1 < source.length && source[position + 1] == '/' -> {
                    while (position < source.length && source[position] != '\n') advance()
                }
                // A line continuation: a trailing backslash joins the next line, for a long
                // condition that would otherwise run off the side of a phone.
                character == '\\' && position + 1 < source.length && source[position + 1] == '\n' -> {
                    advance(); advance()
                }
                character == ' ' || character == '\t' || character == '\r' -> advance()
                else -> return
            }
        }
    }

    private fun next(): Token {
        val startLine = line
        val startColumn = column
        val character = source[position]

        if (character == '\n') {
            advance()
            return Token(TokenType.NEWLINE, "\\n", startLine, startColumn)
        }
        if (character.isDigit() || (character == '.' && position + 1 < source.length && source[position + 1].isDigit())) {
            return number(startLine, startColumn)
        }
        if (character.isLetter() || character == '_') return identifier(startLine, startColumn)
        if (character == '"' || character == '\'') return string(character, startLine, startColumn)

        return operator(startLine, startColumn)
    }

    private fun number(startLine: Int, startColumn: Int): Token {
        val start = position
        while (position < source.length && (source[position].isDigit() || source[position] == '.')) advance()
        val text = source.substring(start, position)
        val value = text.toDoubleOrNull()
            ?: throw ScriptError("«$text» عدد معتبری نیست", startLine, startColumn)
        return Token(TokenType.NUMBER, text, startLine, startColumn, value)
    }

    private fun identifier(startLine: Int, startColumn: Int): Token {
        val start = position
        while (position < source.length && (source[position].isLetterOrDigit() || source[position] == '_')) advance()
        val text = source.substring(start, position)
        val type = when (text) {
            "true" -> TokenType.TRUE
            "false" -> TokenType.FALSE
            "and" -> TokenType.AND
            "or" -> TokenType.OR
            "not" -> TokenType.NOT
            else -> TokenType.IDENT
        }
        return Token(type, text, startLine, startColumn)
    }

    private fun string(quote: Char, startLine: Int, startColumn: Int): Token {
        advance() // the opening quote
        val builder = StringBuilder()
        while (position < source.length && source[position] != quote) {
            if (source[position] == '\n') {
                throw ScriptError("رشته بسته نشده است", startLine, startColumn)
            }
            if (source[position] == '\\' && position + 1 < source.length) {
                advance()
                builder.append(
                    when (source[position]) {
                        'n' -> '\n'
                        't' -> '\t'
                        else -> source[position]
                    },
                )
                advance()
                continue
            }
            builder.append(source[position])
            advance()
        }
        if (position >= source.length) throw ScriptError("رشته بسته نشده است", startLine, startColumn)
        advance() // the closing quote
        return Token(TokenType.STRING, builder.toString(), startLine, startColumn)
    }

    private fun operator(startLine: Int, startColumn: Int): Token {
        fun make(type: TokenType, length: Int): Token {
            val text = source.substring(position, position + length)
            repeat(length) { advance() }
            return Token(type, text, startLine, startColumn)
        }

        val rest = source.length - position
        val two = if (rest >= 2) source.substring(position, position + 2) else ""
        when (two) {
            "==" -> return make(TokenType.EQ, 2)
            "!=" -> return make(TokenType.NEQ, 2)
            "<=" -> return make(TokenType.LTE, 2)
            ">=" -> return make(TokenType.GTE, 2)
            ":=" -> return make(TokenType.REASSIGN, 2)
        }
        return when (source[position]) {
            '+' -> make(TokenType.PLUS, 1)
            '-' -> make(TokenType.MINUS, 1)
            '*' -> make(TokenType.STAR, 1)
            '/' -> make(TokenType.SLASH, 1)
            '%' -> make(TokenType.PERCENT, 1)
            '<' -> make(TokenType.LT, 1)
            '>' -> make(TokenType.GT, 1)
            '=' -> make(TokenType.ASSIGN, 1)
            '?' -> make(TokenType.QUESTION, 1)
            ':' -> make(TokenType.COLON, 1)
            ',' -> make(TokenType.COMMA, 1)
            '.' -> make(TokenType.DOT, 1)
            '(' -> make(TokenType.LPAREN, 1)
            ')' -> make(TokenType.RPAREN, 1)
            '[' -> make(TokenType.LBRACKET, 1)
            ']' -> make(TokenType.RBRACKET, 1)
            else -> throw ScriptError("نویسهٔ ناشناخته «${source[position]}»", startLine, startColumn)
        }
    }

    private fun advance() {
        if (source[position] == '\n') {
            line++
            column = 1
        } else {
            column++
        }
        position++
    }
}

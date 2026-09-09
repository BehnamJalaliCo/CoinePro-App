package com.coinepro.core.script

/**
 * Tokens to a tree.
 *
 * A plain recursive-descent parser with the usual precedence ladder. Two things about it are worth
 * knowing:
 *
 * **Newlines end statements.** The alternative is a semicolon somebody forgets, or significant
 * indentation, which is worse on a phone keyboard. Blank lines are skipped everywhere, so the rule
 * is invisible until a reader splits an expression across two lines — and for that there is a
 * trailing backslash.
 *
 * **Arguments may be named.** `ta.sma(close, 14)` and `ta.sma(source = close, length = 14)` are the
 * same call. Named arguments matter more here than in most languages because these functions take
 * several numbers of the same type, and `ta.bb(close, 20, 2)` is unreadable a week later.
 */
internal class Parser(private val tokens: List<Token>) {

    private var position = 0

    fun parse(): Program {
        val statements = mutableListOf<Statement>()
        skipNewlines()
        while (!check(TokenType.EOF)) {
            statements += statement()
            // A statement must be followed by a line break or the end of the script. Without this,
            // `a = 1 b = 2` parses as something surprising instead of failing where it is written.
            if (!check(TokenType.EOF) && !check(TokenType.NEWLINE)) {
                val token = peek()
                throw ScriptError("پس از پایان دستور، «${token.text}» انتظار نمی‌رفت", "Did not expect “${token.text}” after the end of the statement", token.line, token.column)
            }
            skipNewlines()
        }
        return Program(statements)
    }

    private fun statement(): Statement {
        val token = peek()
        if (token.type == TokenType.IDENT && position + 1 < tokens.size) {
            val next = tokens[position + 1].type
            if (next == TokenType.ASSIGN || next == TokenType.REASSIGN) {
                advance()
                val declare = advance().type == TokenType.ASSIGN
                return Assignment(token.text, declare, expression(), token.line, token.column)
            }
        }
        return ExpressionStatement(expression(), token.line, token.column)
    }

    private fun expression(): Expr = conditional()

    private fun conditional(): Expr {
        val condition = logicalOr()
        if (!match(TokenType.QUESTION)) return condition
        val marker = previous()
        val whenTrue = expression()
        expect(TokenType.COLON, "«:» برای بخش دوم شرط لازم است", "“:” is needed before the second half of the condition")
        val whenFalse = expression()
        return Conditional(condition, whenTrue, whenFalse, marker.line, marker.column)
    }

    private fun logicalOr(): Expr = leftAssociative(::logicalAnd, TokenType.OR)
    private fun logicalAnd(): Expr = leftAssociative(::equality, TokenType.AND)
    private fun equality(): Expr = leftAssociative(::comparison, TokenType.EQ, TokenType.NEQ)
    private fun comparison(): Expr =
        leftAssociative(::term, TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE)
    private fun term(): Expr = leftAssociative(::factor, TokenType.PLUS, TokenType.MINUS)
    private fun factor(): Expr =
        leftAssociative(::unary, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)

    private fun leftAssociative(next: () -> Expr, vararg operators: TokenType): Expr {
        var left = next()
        while (operators.any { check(it) }) {
            val operator = advance()
            val right = next()
            left = Binary(operator.type, left, right, operator.line, operator.column)
        }
        return left
    }

    private fun unary(): Expr {
        if (check(TokenType.MINUS) || check(TokenType.NOT)) {
            val operator = advance()
            return Unary(operator.type, unary(), operator.line, operator.column)
        }
        return postfix()
    }

    private fun postfix(): Expr {
        var target = primary()
        while (check(TokenType.LBRACKET)) {
            val bracket = advance()
            val bars = expression()
            expect(TokenType.RBRACKET, "«]» بسته نشده است", "“]” is never closed")
            target = Offset(target, bars, bracket.line, bracket.column)
        }
        return target
    }

    private fun primary(): Expr {
        val token = peek()
        return when {
            match(TokenType.NUMBER) -> NumberLiteral(previous().number, token.line, token.column)
            match(TokenType.STRING) -> StringLiteral(previous().text, token.line, token.column)
            match(TokenType.TRUE) -> BoolLiteral(true, token.line, token.column)
            match(TokenType.FALSE) -> BoolLiteral(false, token.line, token.column)
            match(TokenType.LPAREN) -> {
                val inner = expression()
                expect(TokenType.RPAREN, "«)» بسته نشده است", "“)” is never closed")
                inner
            }
            match(TokenType.IDENT) -> qualified(previous())
            else -> throw ScriptError("عبارت ناتمام است — «${token.text}» انتظار نمی‌رفت", "The expression is incomplete — did not expect “${token.text}”", token.line, token.column)
        }
    }

    /** `name`, `name(...)` or `namespace.name(...)`. */
    private fun qualified(first: Token): Expr {
        var namespace: String? = null
        var name = first.text
        if (check(TokenType.DOT)) {
            advance()
            val member = expect(TokenType.IDENT, "پس از «.» نام لازم است", "A name is needed after “.”")
            namespace = name
            name = member.text
            // `color.gold` is a constant rather than a call, and reads better than `color.gold()`.
            if (!check(TokenType.LPAREN)) {
                return Identifier("$namespace.$name", first.line, first.column)
            }
        }
        if (!check(TokenType.LPAREN)) return Identifier(name, first.line, first.column)

        advance()
        val arguments = mutableListOf<Argument>()
        if (!check(TokenType.RPAREN)) {
            do {
                skipNewlines()   // a long argument list may be split across lines
                arguments += argument()
                skipNewlines()
            } while (match(TokenType.COMMA))
        }
        expect(TokenType.RPAREN, "«)» بسته نشده است", "“)” is never closed")
        return Call(namespace, name, arguments, first.line, first.column)
    }

    private fun argument(): Argument {
        if (check(TokenType.IDENT) && position + 1 < tokens.size &&
            tokens[position + 1].type == TokenType.ASSIGN
        ) {
            val name = advance().text
            advance()
            return Argument(name, expression())
        }
        return Argument(null, expression())
    }

    private fun skipNewlines() {
        while (check(TokenType.NEWLINE)) advance()
    }

    private fun check(type: TokenType) = peek().type == type
    private fun peek() = tokens[position]
    private fun previous() = tokens[position - 1]

    private fun advance(): Token {
        if (position < tokens.size - 1) position++
        return tokens[position - 1]
    }

    private fun match(type: TokenType): Boolean {
        if (!check(type)) return false
        advance()
        return true
    }

    private fun expect(type: TokenType, message: String, messageEn: String): Token {
        if (!check(type)) {
            val token = peek()
            throw ScriptError(message, messageEn, token.line, token.column)
        }
        return advance()
    }
}

package com.coinepro.core.script

/** Every node carries the position it was parsed at, so a runtime error can point at it too. */
internal sealed interface Node {
    val line: Int
    val column: Int
}

internal sealed interface Expr : Node

internal data class NumberLiteral(val value: Double, override val line: Int, override val column: Int) : Expr
internal data class StringLiteral(val value: String, override val line: Int, override val column: Int) : Expr
internal data class BoolLiteral(val value: Boolean, override val line: Int, override val column: Int) : Expr

/** A bare name: a variable the script declared, or one of the built-in series. */
internal data class Identifier(val name: String, override val line: Int, override val column: Int) : Expr

internal data class Unary(
    val operator: TokenType,
    val operand: Expr,
    override val line: Int,
    override val column: Int,
) : Expr

internal data class Binary(
    val operator: TokenType,
    val left: Expr,
    val right: Expr,
    override val line: Int,
    override val column: Int,
) : Expr

/** `condition ? a : b`, broadcast element-wise. The only branching the language has. */
internal data class Conditional(
    val condition: Expr,
    val whenTrue: Expr,
    val whenFalse: Expr,
    override val line: Int,
    override val column: Int,
) : Expr

/**
 * `close[1]` — the value this many bars ago.
 *
 * A shift rather than an index: `close[1]` at bar 0 has no value, and the result is absent there
 * rather than wrapping around or clamping to the first bar. Clamping is how a script silently
 * reports a crossover on the very first bar of every series it is ever run on.
 */
internal data class Offset(
    val target: Expr,
    val bars: Expr,
    override val line: Int,
    override val column: Int,
) : Expr

internal data class Argument(val name: String?, val value: Expr)

/** `ta.sma(close, 14)` — [namespace] is null for a bare call like `iff(...)`. */
internal data class Call(
    val namespace: String?,
    val name: String,
    val arguments: List<Argument>,
    override val line: Int,
    override val column: Int,
) : Expr {
    val qualified: String get() = if (namespace == null) name else "$namespace.$name"
}

internal sealed interface Statement : Node

/** `x = expr` introduces a name; `x := expr` replaces one that already exists. */
internal data class Assignment(
    val name: String,
    val declare: Boolean,
    val value: Expr,
    override val line: Int,
    override val column: Int,
) : Statement

/** A call evaluated for its effect — `plot(...)`, `hline(...)`, `signal(...)`. */
internal data class ExpressionStatement(
    val expression: Expr,
    override val line: Int,
    override val column: Int,
) : Statement

internal data class Program(val statements: List<Statement>)

package com.coinepro.core.script

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.Indicators
import com.coinepro.core.chart.Line
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Runs a parsed script over a series of bars.
 *
 * The evaluation model is in [Lexer]'s note: every expression is computed once, over the whole
 * series, and scalars broadcast. So this class is mostly a table of built-ins and a set of
 * broadcasting operators, and almost none of it is about bars.
 *
 * ## What stops a bad script
 *
 * A script is written by the reader and runs on the reader's phone, so it cannot be trusted to
 * terminate or to be small:
 *
 * * there are no loops in the language, so a script cannot spin;
 * * [MAX_NODES] caps how much work one run may do, counted in evaluated nodes, so a deeply nested
 *   expression cannot take a second per redraw;
 * * [MAX_PLOTS] and [MAX_LOG_LINES] cap the output, because a script that plots in a hundred
 *   colours is a script that makes the chart useless rather than one that is expressive.
 */
internal class Interpreter(
    private val series: CandleSeries,
    private val overrides: Map<String, Double> = emptyMap(),
) {
    private val size = series.bars.size
    private val variables = HashMap<String, Value>()
    private val plots = mutableListOf<ScriptPlot>()
    private val levels = mutableListOf<ScriptLevel>()
    private val markers = mutableListOf<ScriptMarker>()
    private val inputs = mutableListOf<ScriptInput>()
    private val log = mutableListOf<String>()
    private var setup: ScriptSetup? = null
    private var budget = MAX_NODES

    fun run(program: Program): ScriptResult {
        for (statement in program.statements) {
            when (statement) {
                is Assignment -> {
                    if (!statement.declare && statement.name !in variables) {
                        throw ScriptError(
                            "«${statement.name}» هنوز تعریف نشده — برای تعریف از «=» استفاده کنید",
                            statement.line,
                            statement.column,
                        )
                    }
                    if (statement.declare && statement.name in BUILTIN_SERIES) {
                        throw ScriptError(
                            "«${statement.name}» یک نام درون‌ساخته است و نمی‌شود دوباره تعریفش کرد",
                            statement.line,
                            statement.column,
                        )
                    }
                    variables[statement.name] = evaluate(statement.value)
                }
                is ExpressionStatement -> evaluate(statement.expression)
            }
        }
        return ScriptResult(
            plots = plots.toList(),
            levels = levels.toList(),
            markers = markers.toList(),
            setup = setup,
            inputs = inputs.toList(),
            log = log.toList(),
        )
    }

    /* ------------------------------------------------------------------ evaluation */

    private fun evaluate(expression: Expr): Value {
        if (--budget < 0) {
            throw ScriptError("اسکریپت بیش از حد پیچیده است", expression.line, expression.column)
        }
        return when (expression) {
            is NumberLiteral -> Value.Num(expression.value)
            is StringLiteral -> Value.Text(expression.value)
            is BoolLiteral -> Value.Flag(expression.value)
            is Identifier -> identifier(expression)
            is Unary -> unary(expression)
            is Binary -> binary(expression)
            is Conditional -> conditional(expression)
            is Offset -> offset(expression)
            is Call -> Builtins.call(this, expression)
        }
    }

    private fun identifier(node: Identifier): Value {
        variables[node.name]?.let { return it }
        builtinSeries(node.name)?.let { return it }
        COLOURS[node.name]?.let { return Value.Colour(it) }
        throw ScriptError("«${node.name}» تعریف نشده است", node.line, node.column)
    }

    private fun builtinSeries(name: String): Value? = when (name) {
        "open" -> Value.NumberSeries(Line.of(size) { series.open[it] })
        "high" -> Value.NumberSeries(Line.of(size) { series.high[it] })
        "low" -> Value.NumberSeries(Line.of(size) { series.low[it] })
        "close" -> Value.NumberSeries(Line.of(size) { series.close[it] })
        // Absent rather than zero where the feed does not report volume. A volume study drawn from
        // fabricated zeros looks like a market nobody traded.
        "volume" -> Value.NumberSeries(Line.of(size) { series.bars[it].v })
        "hl2" -> Value.NumberSeries(Line.of(size) { series.bars[it].mid })
        "hlc3" -> Value.NumberSeries(Line.of(size) { series.bars[it].typical })
        "ohlc4" -> Value.NumberSeries(
            Line.of(size) { (series.open[it] + series.high[it] + series.low[it] + series.close[it]) / 4 },
        )
        "time" -> Value.NumberSeries(Line.of(size) { series.bars[it].t.toDouble() })
        "bar_index" -> Value.NumberSeries(Line.of(size) { it.toDouble() })
        "n" -> Value.Num(size.toDouble())
        else -> null
    }

    private fun unary(node: Unary): Value {
        val operand = evaluate(node.operand)
        return when (node.operator) {
            TokenType.MINUS -> when (operand) {
                is Value.Num -> Value.Num(-operand.value)
                is Value.NumberSeries -> Value.NumberSeries(map(operand.line) { -it })
                else -> throw ScriptError("منفی کردن روی ${operand.typeName} معنا ندارد", node.line, node.column)
            }
            TokenType.NOT -> when (operand) {
                is Value.Flag -> Value.Flag(!operand.value)
                is Value.FlagSeries -> Value.FlagSeries(map(operand.line) { if (it != 0.0) 0.0 else 1.0 })
                else -> throw ScriptError("«not» روی ${operand.typeName} معنا ندارد", node.line, node.column)
            }
            else -> throw ScriptError("عملگر یکانی ناشناخته", node.line, node.column)
        }
    }

    private fun binary(node: Binary): Value {
        val left = evaluate(node.left)
        val right = evaluate(node.right)
        return when (node.operator) {
            TokenType.PLUS -> arithmetic(left, right, node) { a, b -> a + b }
            TokenType.MINUS -> arithmetic(left, right, node) { a, b -> a - b }
            TokenType.STAR -> arithmetic(left, right, node) { a, b -> a * b }
            // Division by zero yields an absent value rather than an infinity. An infinity poisons
            // every later calculation and draws a chart with no visible range at all.
            TokenType.SLASH -> arithmetic(left, right, node) { a, b -> if (b == 0.0) Double.NaN else a / b }
            TokenType.PERCENT -> arithmetic(left, right, node) { a, b -> if (b == 0.0) Double.NaN else a % b }
            TokenType.LT -> compare(left, right, node) { a, b -> a < b }
            TokenType.GT -> compare(left, right, node) { a, b -> a > b }
            TokenType.LTE -> compare(left, right, node) { a, b -> a <= b }
            TokenType.GTE -> compare(left, right, node) { a, b -> a >= b }
            TokenType.EQ -> equality(left, right, node, same = true)
            TokenType.NEQ -> equality(left, right, node, same = false)
            TokenType.AND -> logical(left, right, node) { a, b -> a && b }
            TokenType.OR -> logical(left, right, node) { a, b -> a || b }
            else -> throw ScriptError("عملگر ناشناخته", node.line, node.column)
        }
    }

    private inline fun arithmetic(left: Value, right: Value, node: Node, crossinline operation: (Double, Double) -> Double): Value {
        if (left is Value.Num && right is Value.Num) return Value.Num(operation(left.value, right.value))
        val a = numberLine(left, node)
        val b = numberLine(right, node)
        return Value.NumberSeries(zip(a, b, operation))
    }

    private inline fun compare(left: Value, right: Value, node: Node, crossinline operation: (Double, Double) -> Boolean): Value {
        if (left is Value.Num && right is Value.Num) return Value.Flag(operation(left.value, right.value))
        val a = numberLine(left, node)
        val b = numberLine(right, node)
        return Value.FlagSeries(zip(a, b) { x, y -> if (operation(x, y)) 1.0 else 0.0 })
    }

    private fun equality(left: Value, right: Value, node: Node, same: Boolean): Value {
        if (left is Value.Text && right is Value.Text) {
            return Value.Flag((left.value == right.value) == same)
        }
        if (left is Value.Flag && right is Value.Flag) {
            return Value.Flag((left.value == right.value) == same)
        }
        return compare(left, right, node) { a, b -> (a == b) == same }
    }

    private inline fun logical(left: Value, right: Value, node: Node, crossinline operation: (Boolean, Boolean) -> Boolean): Value {
        if (left is Value.Flag && right is Value.Flag) {
            return Value.Flag(operation(left.value, right.value))
        }
        val a = flagLine(left, node)
        val b = flagLine(right, node)
        // Absent on either side makes the result absent, not false. "Not yet decided" and "decided
        // to be false" are different, and collapsing them fires a condition during warm-up.
        return Value.FlagSeries(zip(a, b) { x, y -> if (operation(x != 0.0, y != 0.0)) 1.0 else 0.0 })
    }

    private fun conditional(node: Conditional): Value {
        val condition = evaluate(node.condition)
        val whenTrue = evaluate(node.whenTrue)
        val whenFalse = evaluate(node.whenFalse)
        if (condition is Value.Flag) return if (condition.value) whenTrue else whenFalse

        val flags = flagLine(condition, node)
        // Both branches are evaluated whatever the condition says. There is nothing to short-circuit
        // — the branches are whole series, and each bar takes its own side.
        val a = numberLine(whenTrue, node)
        val b = numberLine(whenFalse, node)
        return Value.NumberSeries(
            Line.of(size) { index ->
                val decided = flags[index] ?: return@of null
                if (decided != 0.0) a[index] else b[index]
            },
        )
    }

    private fun offset(node: Offset): Value {
        val target = evaluate(node.target)
        val bars = (evaluate(node.bars) as? Value.Num)
            ?: throw ScriptError("تعداد کندل‌های عقب‌تر باید یک عدد ثابت باشد", node.line, node.column)
        val shift = bars.value.roundToLong().toInt()
        if (shift < 0) throw ScriptError("عقب رفتن با عدد منفی معنا ندارد", node.line, node.column)
        // Absent before the series begins. Clamping to bar zero is what makes a script report a
        // crossover on the first bar of every chart it is ever run on.
        fun shifted(line: Line) = Line.of(size) { index ->
            if (index - shift < 0) null else line[index - shift]
        }
        return when (target) {
            is Value.NumberSeries -> Value.NumberSeries(shifted(target.line))
            is Value.FlagSeries -> Value.FlagSeries(shifted(target.line))
            is Value.Num, is Value.Flag -> target      // a constant is the same at every bar
            else -> throw ScriptError("«[]» روی ${target.typeName} معنا ندارد", node.line, node.column)
        }
    }

    /* ------------------------------------------------------------------ coercion */

    fun numberLine(value: Value, node: Node): Line = when (value) {
        is Value.Num -> constantLine(size, value.value)
        is Value.NumberSeries -> value.line
        is Value.Flag -> constantLine(size, if (value.value) 1.0 else 0.0)
        is Value.FlagSeries -> value.line
        else -> throw ScriptError("اینجا عدد لازم است، نه ${value.typeName}", node.line, node.column)
    }

    fun flagLine(value: Value, node: Node): Line = when (value) {
        is Value.Flag -> constantLine(size, if (value.value) 1.0 else 0.0)
        is Value.FlagSeries -> value.line
        is Value.Num -> constantLine(size, if (value.value != 0.0) 1.0 else 0.0)
        is Value.NumberSeries -> value.line.asFlags()
        else -> throw ScriptError("اینجا شرط لازم است، نه ${value.typeName}", node.line, node.column)
    }

    fun scalar(value: Value, node: Node, what: String): Double = when (value) {
        is Value.Num -> value.value
        // A series where a single number is required is almost always a mistake worth naming: a
        // length that varies per bar is not a length.
        else -> throw ScriptError("$what باید یک عدد ثابت باشد، نه ${value.typeName}", node.line, node.column)
    }

    fun period(value: Value, node: Node, what: String): Int {
        val number = scalar(value, node, what)
        val rounded = number.roundToLong().toInt()
        if (rounded < 1) throw ScriptError("$what باید دست‌کم ۱ باشد", node.line, node.column)
        if (rounded > size.coerceAtLeast(1) * 4) {
            throw ScriptError("$what از طول نمودار بسیار بزرگ‌تر است", node.line, node.column)
        }
        return rounded
    }

    /* ------------------------------------------------------------------ helpers */

    private inline fun map(line: Line, crossinline operation: (Double) -> Double): Line =
        Line.of(size) { index -> line[index]?.let(operation)?.takeIf(Double::isFinite) }

    private inline fun zip(a: Line, b: Line, crossinline operation: (Double, Double) -> Double): Line =
        Line.of(size) { index ->
            val left = a[index] ?: return@of null
            val right = b[index] ?: return@of null
            operation(left, right).takeIf(Double::isFinite)
        }

    /* ------------------------------------------------------------------ output */

    /** How many plots have been added, so an untitled one can name itself. */
    val plotCount: Int get() = plots.size

    fun addPlot(plot: ScriptPlot, node: Node) {
        if (plots.size >= MAX_PLOTS) {
            throw ScriptError("بیش از $MAX_PLOTS خط قابل رسم نیست", node.line, node.column)
        }
        plots += plot
    }

    fun addLevel(level: ScriptLevel) {
        if (levels.size < MAX_PLOTS) levels += level
    }

    fun addMarker(marker: ScriptMarker) {
        if (markers.size < MAX_PLOTS) markers += marker
    }

    fun addInput(input: ScriptInput) {
        inputs += input
    }

    fun setSetup(value: ScriptSetup) {
        setup = value
    }

    fun addLog(message: String) {
        if (log.size < MAX_LOG_LINES) log += message
    }

    fun override(name: String): Double? = overrides[name]

    /** Argument evaluation, exposed so [Builtins] can evaluate lazily and in the caller's order. */
    internal fun evaluateArgument(expression: Expr): Value = evaluate(expression)

    val barCount: Int get() = size
    val candles: CandleSeries get() = series

    internal companion object {
        const val MAX_NODES = 250_000
        const val MAX_PLOTS = 12
        const val MAX_LOG_LINES = 40

        val BUILTIN_SERIES = setOf(
            "open", "high", "low", "close", "volume",
            "hl2", "hlc3", "ohlc4", "time", "bar_index", "n",
        )

        /**
         * The palette a script may name.
         *
         * Restricted to the app's own colours rather than accepting arbitrary hex, so a script
         * cannot draw a line in a colour that means something else here — red and green carry
         * direction throughout this app, and a script painting a moving average red would be
         * saying something it does not mean.
         */
        val COLOURS = mapOf(
            "color.gold" to 0xFFD8A848,
            "color.silver" to 0xFFDBDBDB,
            "color.buy" to 0xFF00B15C,
            "color.sell" to 0xFFF6465D,
            "color.blue" to 0xFF2962FF,
            "color.grey" to 0xFF848E9C,
            "color.white" to 0xFFF0F1F2,
            "color.orange" to 0xFFF0B90B,
            // Aliases for the two a reader reaches for by instinct. They resolve to the same
            // values as buy and sell, because a script that draws its own green differently from
            // the app's green is a script whose chart no longer matches the one beside it.
            "color.green" to 0xFF00B15C,
            "color.red" to 0xFFF6465D,
            "color.purple" to 0xFF9B7BE0,
            "color.teal" to 0xFF4FB3A5,
        )
    }
}

package com.coinepro.core.script

/**
 * The static types a NamaScript expression can have.
 *
 * Six values and a wildcard. [ANY] is what the checker says when it cannot decide without running
 * — a built-in whose result depends on its argument, say — and it never produces an error on its
 * own: the checker refuses only what the interpreter would certainly refuse, with the same code,
 * so a script the editor marks clean runs, and a script it marks red would not have.
 */
enum class ScriptType(val fa: String, val en: String) {
    NUM("عدد", "a number"),
    TEXT("رشته", "text"),
    FLAG("درست/نادرست", "true/false"),
    COLOUR("رنگ", "a colour"),
    NUM_SERIES("سری عددی", "a number series"),
    FLAG_SERIES("سری شرطی", "a condition series"),
    ANY("هر نوع", "anything"),
    ;

    val isNumeric: Boolean get() = this == NUM || this == NUM_SERIES || this == FLAG || this == FLAG_SERIES || this == ANY
    val isSeries: Boolean get() = this == NUM_SERIES || this == FLAG_SERIES
}

/**
 * What one pass over the tree learned, for the runner.
 *
 * [maxLookback] is the largest constant length or history offset the script names, an upper
 * bound on how far back any bar's value can depend; [incremental] says whether every function
 * the script calls is *windowed* — a script with `ta.cum`, `ta.obv` or `ta.psar` depends on all
 * history and is re-run whole. See [IncrementalRunner].
 */
data class ScriptAnalysis(
    val maxLookback: Int,
    val incremental: Boolean,
    val usesSecurity: Boolean,
    val types: Map<String, ScriptType>,
)

/**
 * The typed pass: resolves every name and every function before anything runs, infers a type for
 * every expression, and refuses with the interpreter's own codes what the interpreter would
 * refuse — so the editor's squiggle arrives on the keystroke, not on the run.
 */
internal class TypeChecker {

    private val variables = LinkedHashMap<String, ScriptType>()
    private var maxLookback = 0
    private var incremental = true
    private var usesSecurity = false

    fun check(program: Program): ScriptAnalysis {
        for (statement in program.statements) {
            when (statement) {
                is Assignment -> {
                    if (!statement.declare && statement.name !in variables) {
                        throw ScriptError(
                            "«${statement.name}» هنوز تعریف نشده — برای تعریف از «=» استفاده کنید",
                            "“${statement.name}” is not defined yet — define it with “=”",
                            statement.line, statement.column, code = "E303",
                        )
                    }
                    if (statement.declare && statement.name in Interpreter.BUILTIN_SERIES) {
                        throw ScriptError(
                            "«${statement.name}» یک نام درون‌ساخته است و نمی‌شود دوباره تعریفش کرد",
                            "“${statement.name}” is a built-in name and cannot be redefined",
                            statement.line, statement.column, code = "E302",
                        )
                    }
                    variables[statement.name] = typeOf(statement.value)
                }
                is ExpressionStatement -> typeOf(statement.expression)
            }
        }
        return ScriptAnalysis(maxLookback, incremental, usesSecurity, variables.toMap())
    }

    private fun typeOf(expression: Expr): ScriptType = when (expression) {
        is NumberLiteral -> ScriptType.NUM
        is StringLiteral -> ScriptType.TEXT
        is BoolLiteral -> ScriptType.FLAG
        is Identifier -> identifier(expression)
        is Unary -> unary(expression)
        is Binary -> binary(expression)
        is Conditional -> conditional(expression)
        is Offset -> offset(expression)
        is Call -> call(expression)
    }

    private fun identifier(node: Identifier): ScriptType {
        variables[node.name]?.let { return it }
        when (node.name) {
            "n" -> return ScriptType.NUM
            "confirmed" -> return ScriptType.FLAG_SERIES
            in Interpreter.BUILTIN_SERIES -> return ScriptType.NUM_SERIES
        }
        if (node.name in Interpreter.COLOURS) return ScriptType.COLOUR
        if (node.name in Interpreter.CONSTANTS) return ScriptType.NUM
        throw ScriptError("«${node.name}» تعریف نشده است", "“${node.name}” is not defined", node.line, node.column, code = "E301")
    }

    private fun unary(node: Unary): ScriptType {
        val operand = typeOf(node.operand)
        return when (node.operator) {
            TokenType.MINUS -> when (operand) {
                ScriptType.NUM -> ScriptType.NUM
                ScriptType.NUM_SERIES -> ScriptType.NUM_SERIES
                ScriptType.ANY -> ScriptType.ANY
                else -> throw ScriptError("منفی کردن روی ${operand.fa} معنا ندارد", "Cannot negate ${operand.en}", node.line, node.column, code = "E201")
            }
            TokenType.NOT -> when (operand) {
                ScriptType.FLAG -> ScriptType.FLAG
                ScriptType.FLAG_SERIES -> ScriptType.FLAG_SERIES
                ScriptType.ANY -> ScriptType.ANY
                else -> throw ScriptError("«not» روی ${operand.fa} معنا ندارد", "“not” does not apply to ${operand.en}", node.line, node.column, code = "E201")
            }
            else -> throw ScriptError("عملگر یکانی ناشناخته", "Unknown unary operator", node.line, node.column, code = "E108")
        }
    }

    private fun binary(node: Binary): ScriptType {
        val left = typeOf(node.left)
        val right = typeOf(node.right)
        // `"text" + x`: words joined to a number, a flag or more words (SPEC §3).
        if (node.operator == TokenType.PLUS && (left == ScriptType.TEXT || right == ScriptType.TEXT)) {
            for (side in listOf(left, right)) {
                if (side == ScriptType.COLOUR) throw ScriptError("رنگ را نمی‌شود به متن چسباند", "A colour cannot be joined to text", node.line, node.column, code = "E203")
            }
            return ScriptType.TEXT
        }
        return when (node.operator) {
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT -> {
                if (left == ScriptType.NUM && right == ScriptType.NUM) return ScriptType.NUM
                numeric(left, node); numeric(right, node)
                if (left == ScriptType.ANY || right == ScriptType.ANY) ScriptType.ANY else ScriptType.NUM_SERIES
            }
            TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE -> {
                if (left == ScriptType.NUM && right == ScriptType.NUM) return ScriptType.FLAG
                numeric(left, node); numeric(right, node)
                if (left == ScriptType.ANY || right == ScriptType.ANY) ScriptType.ANY else ScriptType.FLAG_SERIES
            }
            TokenType.EQ, TokenType.NEQ -> {
                if (left == ScriptType.TEXT && right == ScriptType.TEXT) return ScriptType.FLAG
                if (left == ScriptType.FLAG && right == ScriptType.FLAG) return ScriptType.FLAG
                if (left == ScriptType.NUM && right == ScriptType.NUM) return ScriptType.FLAG
                numeric(left, node); numeric(right, node)
                if (left == ScriptType.ANY || right == ScriptType.ANY) ScriptType.ANY else ScriptType.FLAG_SERIES
            }
            TokenType.AND, TokenType.OR -> {
                if (left == ScriptType.FLAG && right == ScriptType.FLAG) return ScriptType.FLAG
                flag(left, node); flag(right, node)
                if (left == ScriptType.ANY || right == ScriptType.ANY) ScriptType.ANY else ScriptType.FLAG_SERIES
            }
            else -> throw ScriptError("عملگر ناشناخته", "Unknown operator", node.line, node.column, code = "E108")
        }
    }

    private fun conditional(node: Conditional): ScriptType {
        val condition = typeOf(node.condition)
        val a = typeOf(node.whenTrue)
        val b = typeOf(node.whenFalse)
        if (condition == ScriptType.FLAG) return if (a == b) a else ScriptType.ANY
        flag(condition, node)
        numeric(a, node); numeric(b, node)
        return if (a == ScriptType.ANY || b == ScriptType.ANY) ScriptType.ANY else ScriptType.NUM_SERIES
    }

    private fun offset(node: Offset): ScriptType {
        val target = typeOf(node.target)
        val bars = node.bars
        if (bars is NumberLiteral) {
            noteLookback(bars.value.toInt())
        } else if (bars is Unary && bars.operator == TokenType.MINUS && bars.operand is NumberLiteral) {
            throw ScriptError("عقب رفتن با عدد منفی معنا ندارد", "Cannot look back a negative number of bars", node.line, node.column, code = "E202")
        } else if (typeOf(bars).isSeries) {
            throw ScriptError("تعداد کندل‌های عقب‌تر باید یک عدد ثابت باشد", "The number of bars back must be a constant", node.line, node.column, code = "E202")
        }
        return when (target) {
            ScriptType.TEXT, ScriptType.COLOUR -> throw ScriptError("«[]» روی ${target.fa} معنا ندارد", "“[]” does not apply to ${target.en}", node.line, node.column, code = "E202")
            else -> target
        }
    }

    private fun call(node: Call): ScriptType {
        val name = node.qualified
        if (name == "request.security") {
            usesSecurity = true
            incremental = false
            if (node.arguments.size < 2) {
                throw ScriptError("«request.security» به تایم‌فریم و یک عبارت نیاز دارد", "“request.security” needs a timeframe and an expression", node.line, node.column, code = "E210")
            }
            typeOf(node.arguments[0].value)
            // The expression is typed in the other context, which has the same built-ins.
            return typeOf(node.arguments[1].value)
        }
        if (name !in Builtins.NAMES) {
            throw ScriptError("تابع «$name» وجود ندارد", "There is no function “$name”", node.line, node.column, code = "E304")
        }
        if (name in CUMULATIVE || name in WHOLE_RUN) incremental = false
        for (argument in node.arguments) {
            val value = argument.value
            typeOf(value)
            if (value is NumberLiteral && (name.startsWith("ta.") || name.startsWith("input"))) noteLookback(value.value.toInt())
        }
        return RETURNS[name] ?: when {
            name.startsWith("ta.") -> ScriptType.NUM_SERIES
            name.startsWith("math.") -> ScriptType.ANY
            else -> ScriptType.ANY
        }
    }

    private fun numeric(type: ScriptType, node: Node) {
        if (!type.isNumeric) throw ScriptError("اینجا عدد لازم است، نه ${type.fa}", "A number is needed here, not ${type.en}", node.line, node.column, code = "E203")
    }

    private fun flag(type: ScriptType, node: Node) {
        if (!type.isNumeric) throw ScriptError("اینجا شرط لازم است، نه ${type.fa}", "A condition is needed here, not ${type.en}", node.line, node.column, code = "E204")
    }

    private fun noteLookback(value: Int) {
        if (value in 1..MAX_TRACKED_LOOKBACK && value > maxLookback) maxLookback = value
    }

    internal companion object {
        /** Functions whose value at a bar depends on every bar before it; a tail cannot re-run them. */
        val CUMULATIVE = setOf(
            "ta.cum", "ta.obv", "ta.ad", "ta.pvt", "ta.vwap", "ta.barssince", "ta.valuewhen",
            "ta.psar", "ta.supertrend", "ta.supertrend_trend", "ta.vstop", "ta.mcginley", "ta.kama",
        )

        /** Return types where the namespace alone does not say. */
        val RETURNS: Map<String, ScriptType> = mapOf(
            "ta.crossover" to ScriptType.FLAG_SERIES, "ta.crossunder" to ScriptType.FLAG_SERIES,
            "ta.rising" to ScriptType.FLAG_SERIES, "ta.falling" to ScriptType.FLAG_SERIES,
            "input" to ScriptType.NUM, "input.int" to ScriptType.NUM, "input.float" to ScriptType.NUM,
            "input.bool" to ScriptType.FLAG, "input.string" to ScriptType.TEXT, "input.timeframe" to ScriptType.TEXT,
            "input.source" to ScriptType.NUM_SERIES, "input.color" to ScriptType.COLOUR,
            "color.new" to ScriptType.COLOUR,
            "plot" to ScriptType.NUM, "hline" to ScriptType.NUM, "marker" to ScriptType.NUM, "plotshape" to ScriptType.NUM,
            "plotchar" to ScriptType.NUM, "bgcolor" to ScriptType.NUM, "signal" to ScriptType.NUM, "log" to ScriptType.FLAG,
            "alertcondition" to ScriptType.FLAG, "nz" to ScriptType.ANY, "iff" to ScriptType.ANY,
            // 4.61.0
            "na" to ScriptType.FLAG_SERIES,
            "str.tostring" to ScriptType.TEXT, "str.upper" to ScriptType.TEXT, "str.lower" to ScriptType.TEXT,
            "str.replace_all" to ScriptType.TEXT, "str.format" to ScriptType.TEXT, "str.length" to ScriptType.NUM,
            "str.contains" to ScriptType.FLAG, "str.startswith" to ScriptType.FLAG, "str.endswith" to ScriptType.FLAG,
            "label.new" to ScriptType.NUM, "line.new" to ScriptType.NUM, "box.new" to ScriptType.NUM,
            "strategy.entry" to ScriptType.NUM, "strategy.close" to ScriptType.NUM, "strategy.close_all" to ScriptType.NUM,
        )

        /**
         * Functions whose output is placed by absolute bar or replayed over every bar: an object at
         * bar 10 or a trade list cannot be spliced from a tail, so the script is re-run whole.
         */
        val WHOLE_RUN = setOf("label.new", "line.new", "box.new", "strategy.entry", "strategy.close", "strategy.close_all")

        /** A length larger than this is not a lookback anybody meant; it is left to the runtime's E206. */
        const val MAX_TRACKED_LOOKBACK = 5_000
    }
}

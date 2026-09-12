package com.coinepro.core.script

import com.coinepro.core.chart.currentTimeMillis
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
    /** The sandbox's clock budget; the default is what a phone gets, a benchmark may ask for more. */
    private val timeBudgetMillis: Long = MAX_MILLIS,
    /**
     * What `bar_index` counts from and what `n` reports — the whole chart's numbers when the
     * [IncrementalRunner] evaluates only its tail, so a script cannot tell the two apart.
     */
    private val indexBase: Int = 0,
    private val totalBars: Int = series.bars.size,
) {
    private val size = series.bars.size
    private val variables = HashMap<String, Value>()
    private val plots = mutableListOf<ScriptPlot>()
    private val levels = mutableListOf<ScriptLevel>()
    private val markers = mutableListOf<ScriptMarker>()
    private val inputs = mutableListOf<ScriptInput>()
    private val log = mutableListOf<String>()
    private var setup: ScriptSetup? = null
    private val verdicts = mutableListOf<ScriptVerdict>()
    private val backgrounds = mutableListOf<ScriptBackground>()
    private val alerts = mutableListOf<ScriptAlert>()
    private val drawings = mutableListOf<ScriptDrawing>()
    private val orders = mutableListOf<StrategyOrder>()
    private var budget = MAX_NODES
    private val startedAt = currentTimeMillis()

    /**
     * The memory budget: how many bar-cells the script's variables and plots hold at once. A
     * series is one cell per bar; a script that keeps three hundred of them over twenty thousand
     * bars holds six million, which is fine, and one that keeps three thousand does not — see
     * [MAX_RETAINED_CELLS] and E407.
     */
    private var retainedCells = 0L

    fun run(program: Program): ScriptResult {
        for (statement in program.statements) {
            when (statement) {
                is Assignment -> {
                    if (!statement.declare && statement.name !in variables) {
                        throw ScriptError(
                            "«${statement.name}» هنوز تعریف نشده — برای تعریف از «=» استفاده کنید",
                            "“${statement.name}” is not defined yet — define it with “=”",
                            statement.line,
                            statement.column, code = "E303")
                    }
                    if (statement.declare && statement.name in BUILTIN_SERIES) {
                        throw ScriptError(
                            "«${statement.name}» یک نام درون‌ساخته است و نمی‌شود دوباره تعریفش کرد",
                            "“${statement.name}” is a built-in name and cannot be redefined",
                            statement.line,
                            statement.column, code = "E302")
                    }
                    val value = evaluate(statement.value)
                    variables[statement.name] = if (statement.persistent) held(value) else value
                    retain(value, statement)
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
            backgrounds = backgrounds.toList(),
            alerts = alerts.toList(),
            drawings = drawings.toList(),
            strategy = simulate(),
            verdicts = verdicts.toList(),
            elapsedMillis = currentTimeMillis() - startedAt,
        )
    }

    /**
     * `var x = expr`: the value on the first bar where the expression is present, held on every
     * bar. A scalar is already the same on every bar and is left alone.
     */
    private fun held(value: Value): Value {
        fun first(line: Line): Line {
            for (index in 0 until size) if (line.isPresent(index)) return constantLine(size, line.raw(index))
            return Line(DoubleArray(size), BooleanArray(size))
        }
        return when (value) {
            is Value.NumberSeries -> Value.NumberSeries(first(value.line))
            is Value.FlagSeries -> Value.FlagSeries(first(value.line))
            else -> value
        }
    }

    private fun retain(value: Value, node: Node) {
        if (value is Value.NumberSeries || value is Value.FlagSeries) retainedCells += size
        if (retainedCells > MAX_RETAINED_CELLS) {
            throw ScriptError("اسکریپت سری‌های زیادی نگه می‌دارد", "The script holds too many series", node.line, node.column, code = "E407")
        }
    }

    /* ------------------------------------------------------------------ strategy */

    /**
     * The orders, replayed bar by bar.
     *
     * One position at a time (Pine's default of no pyramiding). A signal on bar *i* fills at the
     * open of bar *i + 1*; an entry in the opposite direction closes the position and opens the
     * new one on the same open; a close order for the open position's id — or `close_all` —
     * closes it. Whatever is still open on the last bar is reported open, marked at the last
     * close, and kept out of the closed-trade figures.
     */
    private fun simulate(): ScriptStrategyReport? {
        if (orders.isEmpty()) return null
        val trades = mutableListOf<ScriptTrade>()
        var openId: String? = null
        var openDirection = 0
        var entryBar = -1
        var entryPrice = 0.0
        for (bar in 1 until size) {
            val signalBar = bar - 1
            var closeNow = false
            var newDirection = 0
            var newId: String? = null
            for (order in orders) {
                if (!order.flags.flagAt(signalBar)) continue
                if (order.direction == 0) {
                    if (order.id == null || order.id == openId) closeNow = true
                } else {
                    newDirection = order.direction
                    newId = order.id
                }
            }
            val fill = series.open[bar]
            if (openDirection != 0 && (closeNow || (newDirection != 0 && newDirection != openDirection))) {
                trades += ScriptTrade(openId.orEmpty(), openDirection > 0, entryBar, bar, entryPrice, fill)
                openDirection = 0
            }
            if (newDirection != 0 && openDirection == 0) {
                openDirection = newDirection
                openId = newId
                entryBar = bar
                entryPrice = fill
            }
        }
        if (openDirection != 0) {
            trades += ScriptTrade(openId.orEmpty(), openDirection > 0, entryBar, size - 1, entryPrice, series.close[size - 1], open = true)
        }
        val closed = trades.filter { !it.open }
        var net = 0.0
        var peak = 0.0
        var drawdown = 0.0
        var grossWin = 0.0
        var grossLoss = 0.0
        for (trade in closed) {
            val r = trade.returnPercent
            net += r
            if (r >= 0) grossWin += r else grossLoss -= r
            if (net > peak) peak = net
            if (peak - net > drawdown) drawdown = peak - net
        }
        return ScriptStrategyReport(
            trades = trades,
            netPercent = net,
            winRate = if (closed.isEmpty()) 0.0 else closed.count { it.returnPercent > 0 }.toDouble() / closed.size,
            profitFactor = if (grossLoss > 0) grossWin / grossLoss else null,
            maxDrawdownPercent = drawdown,
        )
    }

    /* ------------------------------------------------------------------ evaluation */

    private fun evaluate(expression: Expr): Value {
        if (--budget < 0) {
            throw ScriptError("اسکریپت بیش از حد پیچیده است", "The script is too complex", expression.line, expression.column, code = "E401")
        }
        // The wall clock, read once every thousand nodes: the node budget bounds the work, this
        // bounds the *time*, which on a slow phone or a very long chart is what the reader feels.
        if (budget and CLOCK_MASK == 0 && currentTimeMillis() - startedAt > timeBudgetMillis) {
            throw ScriptError("اجرای اسکریپت بیش از حد طول کشید", "The script took too long to run", expression.line, expression.column, code = "E406")
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
            is Call -> if (expression.qualified == "request.security") security(expression) else Builtins.call(this, expression)
        }
    }

    /**
     * `request.security(timeframe, expression)`: the expression evaluated over the chart's bars
     * bucketed to a coarser timeframe, mapped back **confirmed** — see [Timeframes].
     */
    private fun security(node: Call): Value {
        if (node.arguments.size < 2) {
            throw ScriptError("«request.security» به تایم‌فریم و یک عبارت نیاز دارد", "“request.security” needs a timeframe and an expression", node.line, node.column, code = "E210")
        }
        val frame = evaluate(node.arguments[0].value)
        val text = (frame as? Value.Text)?.value
            ?: throw ScriptError("تایم‌فریم باید متن باشد: \"240\" یا \"H4\"", "The timeframe must be text: \"240\" or \"H4\"", node.line, node.column, code = "E210")
        val wanted = Timeframes.seconds(text)
            ?: throw ScriptError("تایم‌فریم «$text» شناخته نشد", "Timeframe “$text” is not recognised", node.line, node.column, code = "E210")
        val base = Timeframes.baseSeconds(series)
        val expression = node.arguments[1].value
        if (base <= 0L || wanted == base) return evaluate(expression)
        if (wanted < base || wanted % base != 0L) {
            throw ScriptError(
                "تایم‌فریم باید مضربی از تایم‌فریم چارت باشد و از آن درشت‌تر",
                "The timeframe must be a whole multiple of the chart's, and coarser",
                node.line, node.column, code = "E210",
            )
        }
        val aggregation = Timeframes.aggregate(series, wanted)
        val inner = Interpreter(aggregation.series, overrides, timeBudgetMillis)
        val value = inner.evaluate(expression)
        fun mapped(line: Line): Line = Line.of(size) { index ->
            val bucket = aggregation.bucketOf[index]
            val visible = if (aggregation.closesBucket[index]) bucket else bucket - 1
            if (visible < 0) null else line[visible]
        }
        return when (value) {
            is Value.NumberSeries -> Value.NumberSeries(mapped(value.line))
            is Value.FlagSeries -> Value.FlagSeries(mapped(value.line))
            else -> value
        }
    }

    private fun identifier(node: Identifier): Value {
        variables[node.name]?.let { return it }
        builtinSeries(node.name)?.let { return it }
        COLOURS[node.name]?.let { return Value.Colour(it) }
        CONSTANTS[node.name]?.let { return Value.Num(it) }
        throw ScriptError("«${node.name}» تعریف نشده است", "“${node.name}” is not defined", node.line, node.column, code = "E301")
    }

    /** The built-in series, built once per run: `close` read forty times is one array, not forty. */
    private val builtinCache = HashMap<String, Value>()

    private fun builtinSeries(name: String): Value? {
        builtinCache[name]?.let { return it }
        val value: Value = when (name) {
            "open" -> Value.NumberSeries(rawLine(series.open))
            "high" -> Value.NumberSeries(rawLine(series.high))
            "low" -> Value.NumberSeries(rawLine(series.low))
            "close" -> Value.NumberSeries(rawLine(series.close))
            "volume" -> Value.NumberSeries(Line.of(size) { series.bars[it].v })
            "hl2" -> Value.NumberSeries(Line.of(size) { series.bars[it].mid })
            "hlc3" -> Value.NumberSeries(Line.of(size) { series.bars[it].typical })
            "ohlc4" -> Value.NumberSeries(
                Line.of(size) { (series.open[it] + series.high[it] + series.low[it] + series.close[it]) / 4 },
            )
            "time" -> Value.NumberSeries(Line.of(size) { series.bars[it].t.toDouble() })
            "bar_index" -> Value.NumberSeries(Line.of(size) { (indexBase + it).toDouble() })
            "n" -> Value.Num(totalBars.toDouble())
            "confirmed" -> Value.FlagSeries(Line.of(size) { index -> if (index < size - 1) 1.0 else 0.0 })
            // Absent on every bar: what `nz(na, 0)` fills and what a comparison with it never decides.
            "na" -> Value.NumberSeries(Line(DoubleArray(size), BooleanArray(size)))
            else -> return null
        }
        builtinCache[name] = value
        return value
    }

    private fun rawLine(source: DoubleArray): Line {
        val present = BooleanArray(size)
        for (index in 0 until size) present[index] = source[index].isFinite()
        return Line(source.copyOf(), present)
    }

    private fun unary(node: Unary): Value {
        val operand = evaluate(node.operand)
        return when (node.operator) {
            TokenType.MINUS -> when (operand) {
                is Value.Num -> Value.Num(-operand.value)
                is Value.NumberSeries -> Value.NumberSeries(map(operand.line) { -it })
                else -> throw ScriptError("منفی کردن روی ${operand.typeName} معنا ندارد", "Cannot negate ${operand.typeNameEn}", node.line, node.column, code = "E201")
            }
            TokenType.NOT -> when (operand) {
                is Value.Flag -> Value.Flag(!operand.value)
                is Value.FlagSeries -> Value.FlagSeries(map(operand.line) { if (it != 0.0) 0.0 else 1.0 })
                else -> throw ScriptError("«not» روی ${operand.typeName} معنا ندارد", "“not” does not apply to ${operand.typeNameEn}", node.line, node.column, code = "E201")
            }
            else -> throw ScriptError("عملگر یکانی ناشناخته", "Unknown unary operator", node.line, node.column, code = "E108")
        }
    }

    private fun binary(node: Binary): Value {
        val left = evaluate(node.left)
        val right = evaluate(node.right)
        // `"text" + x` joins: a label's words and the number beside them (SPEC §3).
        if (node.operator == TokenType.PLUS && (left is Value.Text || right is Value.Text)) {
            return Value.Text(asText(left, node) + asText(right, node))
        }
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
            else -> throw ScriptError("عملگر ناشناخته", "Unknown operator", node.line, node.column, code = "E108")
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
            ?: throw ScriptError("تعداد کندل‌های عقب‌تر باید یک عدد ثابت باشد", "The number of bars back must be a constant", node.line, node.column, code = "E202")
        val shift = bars.value.roundToLong().toInt()
        if (shift < 0) throw ScriptError("عقب رفتن با عدد منفی معنا ندارد", "Cannot look back a negative number of bars", node.line, node.column, code = "E202")
        // Absent before the series begins. Clamping to bar zero is what makes a script report a
        // crossover on the first bar of every chart it is ever run on.
        fun shifted(line: Line) = Line.of(size) { index ->
            if (index - shift < 0) null else line[index - shift]
        }
        return when (target) {
            is Value.NumberSeries -> Value.NumberSeries(shifted(target.line))
            is Value.FlagSeries -> Value.FlagSeries(shifted(target.line))
            is Value.Num, is Value.Flag -> target      // a constant is the same at every bar
            else -> throw ScriptError("«[]» روی ${target.typeName} معنا ندارد", "“[]” does not apply to ${target.typeNameEn}", node.line, node.column, code = "E202")
        }
    }

    /* ------------------------------------------------------------------ coercion */

    /** A value as words: text as it is, a number in the price style, a series by its last bar. */
    fun asText(value: Value, node: Node): String = when (value) {
        is Value.Text -> value.value
        is Value.Num -> scriptNumberText(value.value)
        is Value.Flag -> if (value.value) "درست" else "نادرست"
        is Value.NumberSeries -> lastPresent(value.line)?.let(::scriptNumberText) ?: "na"
        is Value.FlagSeries -> lastPresent(value.line)?.let { if (it != 0.0) "درست" else "نادرست" } ?: "na"
        else -> throw ScriptError("اینجا متن لازم است، نه ${value.typeName}", "Text is needed here, not ${value.typeNameEn}", node.line, node.column, code = "E208")
    }

    /** The last bar's value, or the last present one before it — what a series means as a single number. */
    fun lastPresent(line: Line): Double? {
        for (index in size - 1 downTo 0) if (line.isPresent(index)) return line.raw(index)
        return null
    }

    /** A number where one is wanted and a series was allowed: a constant as itself, a series by its last bar. */
    fun scalarOrLast(value: Value, node: Node): Double? = when (value) {
        is Value.Num -> value.value
        is Value.Flag -> if (value.value) 1.0 else 0.0
        is Value.NumberSeries -> lastPresent(value.line)
        is Value.FlagSeries -> lastPresent(value.line)
        else -> throw ScriptError("اینجا عدد لازم است، نه ${value.typeName}", "A number is needed here, not ${value.typeNameEn}", node.line, node.column, code = "E203")
    }

    /** A bar index the chart holds, from a script's number: rounded, `bar_index`-based, clamped to the series. */
    fun barOf(value: Value, node: Node): Int {
        val number = scalarOrLast(value, node) ?: return size - 1
        return (number.roundToLong().toInt() - indexBase).coerceIn(0, size - 1)
    }

    fun numberLine(value: Value, node: Node): Line = when (value) {
        is Value.Num -> constantLine(size, value.value)
        is Value.NumberSeries -> value.line
        is Value.Flag -> constantLine(size, if (value.value) 1.0 else 0.0)
        is Value.FlagSeries -> value.line
        else -> throw ScriptError("اینجا عدد لازم است، نه ${value.typeName}", "A number is needed here, not ${value.typeNameEn}", node.line, node.column, code = "E203")
    }

    fun flagLine(value: Value, node: Node): Line = when (value) {
        is Value.Flag -> constantLine(size, if (value.value) 1.0 else 0.0)
        is Value.FlagSeries -> value.line
        is Value.Num -> constantLine(size, if (value.value != 0.0) 1.0 else 0.0)
        is Value.NumberSeries -> value.line.asFlags()
        else -> throw ScriptError("اینجا شرط لازم است، نه ${value.typeName}", "A condition is needed here, not ${value.typeNameEn}", node.line, node.column, code = "E204")
    }

    fun scalar(value: Value, node: Node, what: String, whatEn: String): Double = when (value) {
        is Value.Num -> value.value
        // A series where a single number is required is almost always a mistake worth naming: a
        // length that varies per bar is not a length.
        else -> throw ScriptError("$what باید یک عدد ثابت باشد، نه ${value.typeName}", "$whatEn must be a constant, not ${value.typeNameEn}", node.line, node.column, code = "E205")
    }

    fun period(value: Value, node: Node, what: String, whatEn: String): Int {
        val number = scalar(value, node, what, whatEn)
        val rounded = number.roundToLong().toInt()
        if (rounded < 1) throw ScriptError("$what باید دست‌کم ۱ باشد", "$whatEn must be at least 1", node.line, node.column, code = "E206")
        if (rounded > size.coerceAtLeast(1) * 4) {
            throw ScriptError("$what از طول چارت بسیار بزرگ‌تر است", "$whatEn is far longer than the chart", node.line, node.column, code = "E206")
        }
        return rounded
    }

    /* ------------------------------------------------------------------ helpers */

    /*
     * The two loops every arithmetic line runs through, on the raw arrays.
     *
     * `Line.of` takes a `(Int) -> Double?` and boxes a Double per bar; over the plan's benchmark —
     * three hundred lines, twenty thousand bars — that was six million boxes per run and the
     * reason evaluation took a second. These read presence and value straight from the line and
     * write two arrays, and the constructor takes them as they are.
     */
    private inline fun map(line: Line, crossinline operation: (Double) -> Double): Line {
        val values = DoubleArray(size)
        val present = BooleanArray(size)
        for (index in 0 until size) {
            if (line.isPresent(index)) {
                val value = operation(line.raw(index))
                if (value.isFinite()) {
                    values[index] = value
                    present[index] = true
                }
            }
        }
        return Line(values, present)
    }

    private inline fun zip(a: Line, b: Line, crossinline operation: (Double, Double) -> Double): Line {
        val values = DoubleArray(size)
        val present = BooleanArray(size)
        for (index in 0 until size) {
            if (a.isPresent(index) && b.isPresent(index)) {
                val value = operation(a.raw(index), b.raw(index))
                if (value.isFinite()) {
                    values[index] = value
                    present[index] = true
                }
            }
        }
        return Line(values, present)
    }

    /* ------------------------------------------------------------------ output */

    /** How many plots have been added, so an untitled one can name itself. */
    val plotCount: Int get() = plots.size

    fun addPlot(plot: ScriptPlot, node: Node) {
        if (plots.size >= MAX_PLOTS) {
            throw ScriptError("بیش از $MAX_PLOTS خط قابل رسم نیست", "No more than $MAX_PLOTS lines can be plotted", node.line, node.column, code = "E402")
        }
        plots += plot
        retain(Value.NumberSeries(plot.values), node)
    }

    /** A label, line or box. Past [MAX_OBJECTS] the rest are dropped rather than refused, like levels. */
    fun addDrawing(drawing: ScriptDrawing) {
        if (drawings.size < MAX_OBJECTS) drawings += drawing
    }

    fun addOrder(order: StrategyOrder) {
        if (orders.size < MAX_OBJECTS) orders += order
    }

    fun addLevel(level: ScriptLevel) {
        if (levels.size < MAX_PLOTS) levels += level
    }

    fun addMarker(marker: ScriptMarker) {
        if (markers.size < MAX_PLOTS) markers += marker
    }

    fun addBackground(background: ScriptBackground) {
        if (backgrounds.size < MAX_PLOTS) backgrounds += background
    }

    fun addAlert(alert: ScriptAlert) {
        if (alerts.size < MAX_PLOTS) alerts += alert
    }

    fun addInput(input: ScriptInput) {
        inputs += input
    }

    fun setSetup(value: ScriptSetup) {
        setup = value
    }

    /** One short-form `signal(...)`. Capped with the plots, for the same reason. */
    fun addVerdict(verdict: ScriptVerdict) {
        if (verdicts.size < MAX_PLOTS) verdicts += verdict
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

        /** The sandbox's clock budget for one run, and how often it is read (every 1024 nodes). */
        const val MAX_MILLIS = 2_000L
        const val CLOCK_MASK = 0x3FF
        const val MAX_LOG_LINES = 40

        /** Labels, lines, boxes and orders a run may place; the rest are dropped silently. */
        const val MAX_OBJECTS = 40

        /** Bar-cells the variables and plots of one run may hold at once: 8 M ≈ 72 MB. E407 past it. */
        const val MAX_RETAINED_CELLS = 8_000_000L

        val BUILTIN_SERIES = setOf(
            "open", "high", "low", "close", "volume",
            "hl2", "hlc3", "ohlc4", "time", "bar_index", "n", "confirmed", "na",
        )

        /** Named numbers: the two directions `strategy.entry` takes. */
        val CONSTANTS = mapOf(
            "strategy.long" to 1.0,
            "strategy.short" to -1.0,
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

/**
 * One `strategy.entry` or `strategy.close` call: which bars it fires on, and what it does.
 *
 * [direction] is 1 for a long entry, −1 for a short, 0 for a close; a close with a null [id] is
 * `strategy.close_all`.
 */
internal class StrategyOrder(val id: String?, val direction: Int, val flags: Line)

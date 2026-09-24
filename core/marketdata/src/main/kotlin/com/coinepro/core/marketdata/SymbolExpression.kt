package com.coinepro.core.marketdata

import java.time.ZoneId

/**
 * A chart of an expression over symbols: `EURUSD/GBPUSD`, `XAUUSD*2`, `US30-US500`,
 * `(BTCUSDT+ETHUSDT)/2` — TradingView's spread and ratio charts (5.14.0).
 *
 * A port of `symbolExpr.js` in Pro-Chart's terminal, with its grammar and its rules: `+ - * /`,
 * parentheses and precedence, unary minus, decimal constants, and symbols of letters and digits
 * starting with a letter. The bars are built on the times every symbol shares, each of open, high,
 * low and close through the expression separately, and then high and low are taken as the extremes
 * of the four — a ratio can turn a bar upside down, and a candle whose high is under its low is a
 * drawing error, not a reading.
 */
class SymbolExpression private constructor(private val root: Node) {

    /** Every symbol the expression reads, upper-cased, in first-seen order. */
    val symbols: List<String> = LinkedHashSet<String>().also { collect(root, it) }.toList()

    /** Whether there is any arithmetic at all; a bare symbol is not an expression. */
    val isExpression: Boolean get() = root !is Node.Symbol

    /** The value for one set of prices by symbol; null for a missing price or a division by zero. */
    fun evaluate(prices: Map<String, Double>): Double? = eval(root, prices)

    /**
     * The expression's bars from each symbol's, aligned on the times all of them have. Volume is
     * zero: the volume of a ratio means nothing, and the chart hides a volume band of zeros.
     */
    fun combine(bars: Map<String, List<OhlcBar>>): List<OhlcBar> {
        if (symbols.isEmpty()) return emptyList()
        val byTime = symbols.map { symbol -> (bars[symbol] ?: emptyList()).associateBy { it.t } }
        val times = byTime.first().keys.filter { t -> byTime.all { t in it } }.sorted()
        val out = ArrayList<OhlcBar>(times.size)
        for (t in times) {
            fun field(pick: (OhlcBar) -> Double): Double? =
                evaluate(symbols.withIndex().associate { (k, symbol) -> symbol to pick(byTime[k].getValue(t)) })
            val o = field { it.o } ?: continue
            val c = field { it.c } ?: continue
            val corners = listOfNotNull(o, c, field { it.h }, field { it.l })
            val closed = symbols.indices.all { byTime[it].getValue(t).closed }
            out += OhlcBar(t = t, o = o, h = corners.max(), l = corners.min(), c = c, v = 0.0, closed = closed)
        }
        return out
    }

    private sealed interface Node {
        data class Constant(val value: Double) : Node
        data class Symbol(val name: String) : Node
        data class Operation(val op: Char, val left: Node, val right: Node) : Node
    }

    companion object {
        /** The expression in [text], or null when it does not parse. */
        fun parse(text: String): SymbolExpression? {
            val tokens = tokenize(text.trim()) ?: return null
            if (tokens.isEmpty()) return null
            val parser = Parser(tokens)
            val root = parser.expression() ?: return null
            if (!parser.done) return null
            return SymbolExpression(root)
        }

        /** Whether [text] is an expression rather than one symbol — what routes a load. */
        fun isExpression(text: String): Boolean = parse(text)?.isExpression == true

        private fun collect(node: Node, into: MutableSet<String>) {
            when (node) {
                is Node.Symbol -> into += node.name
                is Node.Operation -> { collect(node.left, into); collect(node.right, into) }
                is Node.Constant -> Unit
            }
        }

        private fun eval(node: Node, prices: Map<String, Double>): Double? = when (node) {
            is Node.Constant -> node.value
            is Node.Symbol -> prices[node.name]?.takeIf { it.isFinite() }
            is Node.Operation -> {
                val l = eval(node.left, prices)
                val r = eval(node.right, prices)
                if (l == null || r == null) {
                    null
                } else {
                    when (node.op) {
                        '+' -> l + r
                        '-' -> l - r
                        '*' -> l * r
                        else -> if (r == 0.0) null else l / r
                    }?.takeIf { it.isFinite() }
                }
            }
        }

        private sealed interface Token {
            data class Op(val c: Char) : Token
            data class Number(val value: Double) : Token
            data class Name(val value: String) : Token
        }

        private fun tokenize(s: String): List<Token>? {
            val out = ArrayList<Token>()
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                when {
                    ch == ' ' -> i++
                    ch in "+-*/()" -> { out += Token.Op(ch); i++ }
                    ch.isDigit() || ch == '.' -> {
                        var j = i
                        while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                        out += Token.Number(s.substring(i, j).toDoubleOrNull() ?: return null)
                        i = j
                    }
                    ch in 'A'..'Z' || ch in 'a'..'z' -> {
                        var j = i
                        while (j < s.length && (s[j] in 'A'..'Z' || s[j] in 'a'..'z' || s[j] in '0'..'9')) j++
                        out += Token.Name(s.substring(i, j).uppercase())
                        i = j
                    }
                    else -> return null
                }
            }
            return out
        }

        private class Parser(private val tokens: List<Token>) {
            private var pos = 0
            val done: Boolean get() = pos == tokens.size
            private fun peekOp(): Char? = (tokens.getOrNull(pos) as? Token.Op)?.c

            fun expression(): Node? {
                var node = term() ?: return null
                while (peekOp() == '+' || peekOp() == '-') {
                    val op = peekOp()!!
                    pos++
                    node = Node.Operation(op, node, term() ?: return null)
                }
                return node
            }

            private fun term(): Node? {
                var node = factor() ?: return null
                while (peekOp() == '*' || peekOp() == '/') {
                    val op = peekOp()!!
                    pos++
                    node = Node.Operation(op, node, factor() ?: return null)
                }
                return node
            }

            private fun factor(): Node? {
                val token = tokens.getOrNull(pos) ?: return null
                return when {
                    token is Token.Number -> { pos++; Node.Constant(token.value) }
                    token is Token.Name -> { pos++; Node.Symbol(token.value) }
                    token is Token.Op && token.c == '(' -> {
                        pos++
                        val inner = expression() ?: return null
                        if (peekOp() != ')') return null
                        pos++
                        inner
                    }
                    token is Token.Op && token.c == '-' -> {
                        pos++
                        Node.Operation('-', Node.Constant(0.0), factor() ?: return null)
                    }
                    else -> null
                }
            }
        }
    }
}

/**
 * A gateway that charts [SymbolExpression]s: a symbol with arithmetic in it is loaded one symbol at
 * a time from [inner] and combined; anything else goes straight through, untouched.
 *
 * History is not paged for an expression — each symbol's pages end at different places and a
 * spread missing half its left edge is worse than a shorter one — which is also the terminal's rule
 * («اسپرد: بارگذاریِ تنبلِ تاریخ غیرفعال»).
 */
class SymbolExpressionGateway(private val inner: CandleGateway) : CandleGateway {
    override val sourceName: String get() = inner.sourceName
    override val nativeTimeframes: List<Timeframe> get() = inner.nativeTimeframes
    override val sourceLimitMax: Int get() = inner.sourceLimitMax

    override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
        val expression = SymbolExpression.parse(symbol)?.takeIf { it.isExpression }
            ?: return inner.load(symbol, timeframe, limit, before)
        if (before != null) return CandlePage(symbol, timeframe, emptyList(), hasMore = false)
        val bars = expression.symbols.associateWith { inner.load(it, timeframe, limit, null).candles }
        return CandlePage(symbol, timeframe, expression.combine(bars), hasMore = false)
    }

    override suspend fun load(
        symbol: String,
        interval: ChartInterval,
        limit: Int,
        before: Long?,
        zone: ZoneId,
    ): CandlePage {
        val expression = SymbolExpression.parse(symbol)?.takeIf { it.isExpression }
            ?: return inner.load(symbol, interval, limit, before, zone)
        val pages = expression.symbols.associateWith { if (before != null) null else inner.load(it, interval, limit, null, zone) }
        val first = pages.values.firstOrNull()
        val timeframe = first?.timeframe ?: Timeframe.H1
        if (first == null) return CandlePage(symbol, timeframe, emptyList(), hasMore = false, interval = interval)
        val combined = expression.combine(pages.mapValues { it.value?.candles ?: emptyList() })
        return CandlePage(symbol, timeframe, combined, hasMore = false, interval = interval)
    }
}

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web.jvm

import com.coinepro.core.chart.formatFixed
import kotlin.math.abs

/**
 * `String.format` as the JVM does it, for the conversions the shared code writes: `%s %d %f %e %x
 * %X %c %b %n %%`, with `-`, `+`, `0`, `,`, space and `(` flags, width, precision and `n$` argument
 * positions. Latin digits and a `.` whatever the locale argument says — the only locale the phone
 * formats figures in is `Locale.US`, and the house rule is that market figures stay Latin.
 */
fun formatJvm(format: String, vararg args: Any?): String {
    val out = StringBuilder(format.length + 16)
    var next = 0
    var i = 0
    while (i < format.length) {
        val c = format[i]
        if (c != '%') { out.append(c); i++; continue }
        var j = i + 1
        // argument index
        var index: Int? = null
        val numStart = j
        while (j < format.length && format[j].isDigit()) j++
        if (j < format.length && format[j] == '$' && j > numStart) { index = format.substring(numStart, j).toInt() - 1; j++ } else j = numStart
        // flags
        var left = false; var plus = false; var zero = false; var comma = false; var space = false; var paren = false
        loop@ while (j < format.length) {
            when (format[j]) {
                '-' -> left = true; '+' -> plus = true; '0' -> zero = true; ',' -> comma = true
                ' ' -> space = true; '(' -> paren = true; '#' -> {}
                else -> break@loop
            }
            j++
        }
        val wStart = j
        while (j < format.length && format[j].isDigit()) j++
        val width = if (j > wStart) format.substring(wStart, j).toInt() else 0
        var precision: Int? = null
        if (j < format.length && format[j] == '.') {
            val pStart = ++j
            while (j < format.length && format[j].isDigit()) j++
            precision = format.substring(pStart, j).toIntOrNull() ?: 0
        }
        if (j >= format.length) { out.append(format.substring(i)); break }
        val conv = format[j]
        if (conv == '%') { out.append('%'); i = j + 1; continue }
        if (conv == 'n') { out.append('\n'); i = j + 1; continue }
        val arg = args.getOrNull(index ?: next++)
        var body = when (conv) {
            'd' -> {
                val v = (arg as? Number)?.toLong() ?: 0L
                var s = abs(v).toString().let { if (v == Long.MIN_VALUE) it.removePrefix("-") else it }
                if (comma) s = group(s)
                sign(v < 0, s, plus, space, paren)
            }
            'f' -> {
                val v = (arg as? Number)?.toDouble() ?: 0.0
                if (v.isNaN()) "NaN" else if (v.isInfinite()) (if (v > 0) "Infinity" else "-Infinity") else {
                    var s = formatFixed(abs(v), precision ?: 6)
                    if (comma) s = group(s.substringBefore('.')) + (if ('.' in s) "." + s.substringAfter('.') else "")
                    val negative = v < 0 && s.any { it in '1'..'9' }
                    sign(negative, s, plus, space, paren)
                }
            }
            'e', 'E' -> {
                val v = (arg as? Number)?.toDouble() ?: 0.0
                val s = expJs(abs(v), precision ?: 6).let { if (conv == 'E') it.uppercase() else it }
                sign(v < 0, s, plus, space, paren)
            }
            'x', 'X' -> ((arg as? Number)?.toLong() ?: 0L).let { v ->
                (if (v < 0) (v.toULong()).toString(16) else v.toString(16)).let { if (conv == 'X') it.uppercase() else it }
            }
            'o' -> ((arg as? Number)?.toLong() ?: 0L).toString(8)
            'c' -> when (arg) { is Char -> arg.toString(); is Number -> arg.toInt().toChar().toString(); else -> arg.toString() }
            'b', 'B' -> (if (arg is Boolean) arg.toString() else (arg != null).toString())
            's', 'S' -> arg.toString().let { s -> precision?.let { s.take(it) } ?: s }.let { if (conv == 'S') it.uppercase() else it }
            else -> arg.toString()
        }
        if (body.length < width) {
            body = when {
                left -> body.padEnd(width)
                zero && conv in "dfeExX" -> {
                    val signChar = body.firstOrNull()?.takeIf { it == '-' || it == '+' || it == ' ' }
                    if (signChar != null) signChar + body.drop(1).padStart(width - 1, '0') else body.padStart(width, '0')
                }
                else -> body.padStart(width)
            }
        }
        out.append(body)
        i = j + 1
    }
    return out.toString()
}

private fun expJs(v: Double, digits: Int): String = expRaw(v, digits).let { raw ->
    // JS writes 1.5e+3; the JVM writes 1.500000e+03.
    val mantissa = raw.substringBefore('e')
    val exp = raw.substringAfter('e')
    val sign = if (exp.startsWith('-')) "-" else "+"
    mantissa + "e" + sign + exp.trimStart('+', '-').padStart(2, '0')
}

private fun expRaw(v: Double, digits: Int): String = js("v.toExponential(digits)")

private fun group(digits: String): String {
    val sb = StringBuilder()
    digits.reversed().forEachIndexed { i, ch -> if (i > 0 && i % 3 == 0) sb.append(','); sb.append(ch) }
    return sb.reverse().toString()
}

private fun sign(negative: Boolean, s: String, plus: Boolean, space: Boolean, paren: Boolean): String = when {
    negative && paren -> "($s)"
    negative -> "-$s"
    plus -> "+$s"
    space -> " $s"
    else -> s
}

fun nanoTimeJs(): Double = js("(typeof performance !== 'undefined' ? performance.now() : Date.now()) * 1e6")
fun nowMillisJs(): Double = js("Date.now()")

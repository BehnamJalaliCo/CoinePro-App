@file:Suppress("unused")

package java.text

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

class DecimalFormatSymbols(val locale: Locale = Locale.US) {
    var decimalSeparator: Char = '.'
    var groupingSeparator: Char = ','
    var minusSign: Char = '-'
    var zeroDigit: Char = '0'
    var percent: Char = '%'
    companion object {
        fun getInstance(locale: Locale): DecimalFormatSymbols = DecimalFormatSymbols(locale)
        fun getInstance(): DecimalFormatSymbols = DecimalFormatSymbols()
    }
}

enum class RoundingModeCompat { HALF_EVEN, HALF_UP, DOWN, UP, FLOOR, CEILING }

abstract class NumberFormat {
    abstract fun format(number: Double): String
    open fun format(number: Long): String = format(number.toDouble())
    open fun format(number: Any?): String = when (number) {
        is Double -> format(number)
        is Float -> format(number.toDouble())
        is Long -> format(number)
        is Int -> format(number.toLong())
        is Number -> format(number.toDouble())
        else -> number.toString()
    }
    var maximumFractionDigits: Int = 3
    var minimumFractionDigits: Int = 0
    var isGroupingUsed: Boolean = true
    open fun parse(source: String): Number = source.replace(",", "").toDouble()

    companion object {
        fun getInstance(locale: Locale): NumberFormat = DecimalFormat("#,##0.###")
        fun getInstance(): NumberFormat = DecimalFormat("#,##0.###")
        fun getNumberInstance(locale: Locale): NumberFormat = DecimalFormat("#,##0.###")
        fun getNumberInstance(): NumberFormat = DecimalFormat("#,##0.###")
        fun getIntegerInstance(locale: Locale): NumberFormat = DecimalFormat("#,##0")
        fun getPercentInstance(locale: Locale): NumberFormat = DecimalFormat("#,##0%")
    }
}

/**
 * `DecimalFormat` for the pattern shapes the shared code writes: `#,##0.00`, `0.###`, `0.00%`,
 * `+#,##0.00;-#,##0.00` and friends. Half-even rounding, like the JDK's default.
 */
class DecimalFormat(pattern: String = "#,##0.###", private var symbols: DecimalFormatSymbols = DecimalFormatSymbols()) : NumberFormat() {
    private var positivePrefix = ""
    private var positiveSuffix = ""
    private var negativePrefix: String? = null
    private var negativeSuffix: String? = null
    private var grouping = 0
    private var minInteger = 1
    private var percent = false
    var roundingMode: java.math.RoundingMode = java.math.RoundingMode.HALF_EVEN

    init { applyPattern(pattern) }

    fun applyPattern(pattern: String) {
        val parts = pattern.split(';')
        parseOne(parts[0], positive = true)
        if (parts.size > 1) parseOne(parts[1], positive = false) else { negativePrefix = null; negativeSuffix = null }
    }

    private fun parseOne(p: String, positive: Boolean) {
        val start = p.indexOfFirst { it == '#' || it == '0' || it == ',' || it == '.' }
        val end = p.indexOfLast { it == '#' || it == '0' || it == ',' || it == '.' }
        val prefix = if (start < 0) p else p.substring(0, start).replace("'", "")
        val suffix = if (end < 0) "" else p.substring(end + 1).replace("'", "")
        if (!positive) { negativePrefix = prefix; negativeSuffix = suffix; return }
        positivePrefix = prefix
        positiveSuffix = suffix
        percent = suffix.contains('%')
        if (start < 0) return
        val body = p.substring(start, end + 1)
        val intPart = body.substringBefore('.')
        val fracPart = if ('.' in body) body.substringAfter('.') else ""
        val lastComma = intPart.lastIndexOf(',')
        grouping = if (lastComma >= 0) intPart.length - lastComma - 1 else 0
        isGroupingUsed = grouping > 0
        minInteger = intPart.count { it == '0' }
        minimumFractionDigits = fracPart.count { it == '0' }
        maximumFractionDigits = fracPart.length
    }

    fun setDecimalFormatSymbols(s: DecimalFormatSymbols) { symbols = s }
    fun getDecimalFormatSymbols(): DecimalFormatSymbols = symbols
    fun setPositivePrefix(p: String) { positivePrefix = p }
    fun setNegativePrefix(p: String) { negativePrefix = p }

    private fun roundHalfEven(value: Double, digits: Int): Double {
        val factor = 10.0.pow(digits)
        val scaled = value * factor
        val floor = kotlin.math.floor(scaled)
        val diff = scaled - floor
        val r = when (roundingMode) {
            java.math.RoundingMode.HALF_UP -> if (diff >= 0.5 - 1e-9) floor + 1 else floor
            java.math.RoundingMode.DOWN, java.math.RoundingMode.FLOOR -> floor
            java.math.RoundingMode.UP, java.math.RoundingMode.CEILING -> if (diff > 1e-12) floor + 1 else floor
            else -> when {
                diff > 0.5 + 1e-9 -> floor + 1
                diff < 0.5 - 1e-9 -> floor
                else -> if (floor % 2.0 == 0.0) floor else floor + 1
            }
        }
        return r / factor
    }

    override fun format(number: Double): String {
        if (number.isNaN()) return "NaN"
        if (number.isInfinite()) return if (number > 0) "∞" else "-∞"
        val value = if (percent) number * 100 else number
        val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
        val rounded = roundHalfEven(abs(value), maximumFractionDigits)
        var text = com.coinepro.core.chart.formatFixed(rounded, maximumFractionDigits)
        var intPart = text.substringBefore('.')
        var frac = if ('.' in text) text.substringAfter('.') else ""
        while (frac.length > minimumFractionDigits && frac.endsWith('0')) frac = frac.dropLast(1)
        intPart = intPart.padStart(minInteger, '0')
        if (minInteger == 0 && intPart == "0") intPart = ""
        if (isGroupingUsed && grouping > 0) {
            val sb = StringBuilder()
            intPart.reversed().forEachIndexed { i, c -> if (i > 0 && i % grouping == 0) sb.append(symbols.groupingSeparator); sb.append(c) }
            intPart = sb.reverse().toString()
        }
        text = if (frac.isEmpty()) intPart else intPart + symbols.decimalSeparator + frac
        val isZero = rounded == 0.0
        return if (negative && !isZero) (negativePrefix ?: (symbols.minusSign + positivePrefix)) + text + (negativeSuffix ?: positiveSuffix)
        else positivePrefix + text + positiveSuffix
    }
}

open class ParseException(message: String?, val errorOffset: Int) : Exception(message)

class SimpleDateFormat(private val pattern: String, locale: Locale = Locale.US) {
    fun format(date: Any): String = java.time.format.DateTimeFormatter.ofPattern(pattern).withZone(java.time.ZoneId.systemDefault())
        .format(if (date is Long) java.time.Instant.ofEpochMilli(date) else date)
}

object Normalizer {
    enum class Form { NFC, NFD, NFKC, NFKD }
    fun normalize(src: CharSequence, form: Form): String = src.toString()
}

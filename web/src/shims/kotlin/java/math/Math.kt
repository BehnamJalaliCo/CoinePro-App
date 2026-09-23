@file:Suppress("unused")

package java.math

enum class RoundingMode { UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY }

class MathContext(val precision: Int, val roundingMode: RoundingMode = RoundingMode.HALF_UP) {
    companion object {
        val DECIMAL64 = MathContext(16, RoundingMode.HALF_EVEN)
        val DECIMAL128 = MathContext(34, RoundingMode.HALF_EVEN)
        val UNLIMITED = MathContext(0, RoundingMode.HALF_UP)
    }
}

/**
 * A decimal as the JVM keeps one: an unscaled integer and a scale, `unscaled × 10^-scale`, exact.
 *
 * Held in a `Long` rather than an arbitrary-precision integer, which covers every value the shared
 * code builds — prices and ticks from a double's shortest representation, seventeen significant
 * digits at most — and keeps `precision`, `scale` and `stripTrailingZeros` exactly the JVM's, which
 * is what the order book's ladder reads its decade from.
 */
class BigDecimal private constructor(private val unscaled: Long, private val scaleDigits: Int) : Number(), Comparable<BigDecimal> {
    constructor(text: String) : this(parse(text).first, parse(text).second)
    constructor(v: Double) : this(v.toString())
    constructor(v: Int) : this(v.toLong(), 0)
    constructor(v: Long) : this(v, 0)

    fun scale(): Int = scaleDigits
    fun unscaledValue(): Long = unscaled
    fun precision(): Int = if (unscaled == 0L) 1 else kotlin.math.abs(unscaled).toString().length
    fun signum(): Int = unscaled.sign

    private fun aligned(o: BigDecimal): Triple<Long, Long, Int> {
        val s = maxOf(scaleDigits, o.scaleDigits)
        return Triple(unscaled * pow10L(s - scaleDigits), o.unscaled * pow10L(s - o.scaleDigits), s)
    }

    fun add(o: BigDecimal): BigDecimal = aligned(o).let { (a, b, s) -> BigDecimal(a + b, s) }
    fun subtract(o: BigDecimal): BigDecimal = aligned(o).let { (a, b, s) -> BigDecimal(a - b, s) }
    fun multiply(o: BigDecimal): BigDecimal = BigDecimal(unscaled * o.unscaled, scaleDigits + o.scaleDigits)
    fun multiply(o: BigDecimal, mc: MathContext): BigDecimal = multiply(o).round(mc)
    fun negate(): BigDecimal = BigDecimal(-unscaled, scaleDigits)
    fun abs(): BigDecimal = BigDecimal(kotlin.math.abs(unscaled), scaleDigits)
    fun plus(): BigDecimal = this

    /** Exact division; throws when the quotient has no terminating expansion, as the JVM does. */
    fun divide(o: BigDecimal): BigDecimal {
        if (o.unscaled == 0L) throw ArithmeticException("Division by zero")
        var num = unscaled
        var scale = scaleDigits - o.scaleDigits
        var guard = 0
        while (num % o.unscaled != 0L) {
            if (guard++ > 18) throw ArithmeticException("Non-terminating decimal expansion; no exact representable decimal result.")
            num *= 10; scale++
        }
        return BigDecimal(num / o.unscaled, scale).let { if (it.scaleDigits < 0) it.setScale(0) else it }
    }

    fun divide(o: BigDecimal, scale: Int, mode: RoundingMode): BigDecimal {
        if (o.unscaled == 0L) throw ArithmeticException("Division by zero")
        // unscaled/10^s1 ÷ o/10^s2 = (unscaled × 10^(scale - s1 + s2)) / o, at `scale`.
        val shift = scale - scaleDigits + o.scaleDigits
        val (num, den) = if (shift >= 0) (unscaled * pow10L(shift)) to o.unscaled else unscaled to (o.unscaled * pow10L(-shift))
        return BigDecimal(roundedDivide(num, den, mode), scale)
    }

    fun divide(o: BigDecimal, mode: RoundingMode): BigDecimal = divide(o, scaleDigits, mode)
    fun divide(o: BigDecimal, mc: MathContext): BigDecimal =
        runCatching { divide(o) }.getOrElse { valueOf(toDouble() / o.toDouble()) }.round(mc)

    fun remainder(o: BigDecimal): BigDecimal = aligned(o).let { (a, b, s) -> BigDecimal(a % b, s) }

    fun setScale(scale: Int, mode: RoundingMode = RoundingMode.UNNECESSARY): BigDecimal = when {
        scale == scaleDigits -> this
        scale > scaleDigits -> BigDecimal(unscaled * pow10L(scale - scaleDigits), scale)
        else -> {
            val den = pow10L(scaleDigits - scale)
            if (mode == RoundingMode.UNNECESSARY && unscaled % den != 0L) throw ArithmeticException("Rounding necessary")
            BigDecimal(roundedDivide(unscaled, den, mode), scale)
        }
    }

    fun round(mc: MathContext): BigDecimal {
        val drop = precision() - mc.precision
        if (mc.precision == 0 || drop <= 0) return this
        val rounded = setScale(scaleDigits - drop, mc.roundingMode)
        // A carry can add a digit (9.99 → 10.0); drop it again.
        return if (rounded.precision() > mc.precision) rounded.setScale(rounded.scaleDigits - 1, mc.roundingMode) else rounded
    }

    fun stripTrailingZeros(): BigDecimal {
        if (unscaled == 0L) return BigDecimal(0, 0)
        var u = unscaled
        var s = scaleDigits
        while (u % 10 == 0L) { u /= 10; s-- }
        return BigDecimal(u, s)
    }

    fun scaleByPowerOfTen(n: Int): BigDecimal = BigDecimal(unscaled, scaleDigits - n)
    fun movePointLeft(n: Int): BigDecimal = BigDecimal(unscaled, scaleDigits + n).let { if (it.scaleDigits < 0) it.setScale(0) else it }
    fun movePointRight(n: Int): BigDecimal = BigDecimal(unscaled, scaleDigits - n).let { if (it.scaleDigits < 0) it.setScale(0) else it }
    fun max(o: BigDecimal): BigDecimal = if (this >= o) this else o
    fun min(o: BigDecimal): BigDecimal = if (this <= o) this else o

    fun toPlainString(): String {
        if (scaleDigits <= 0) return (unscaled * pow10L(-scaleDigits)).toString()
        val digits = kotlin.math.abs(unscaled).toString().padStart(scaleDigits + 1, '0')
        val sign = if (unscaled < 0) "-" else ""
        return sign + digits.dropLast(scaleDigits) + "." + digits.takeLast(scaleDigits)
    }

    override fun toByte(): Byte = toLong().toByte()
    override fun toShort(): Short = toLong().toShort()
    override fun toInt(): Int = toLong().toInt()
    override fun toLong(): Long = if (scaleDigits <= 0) unscaled * pow10L(-scaleDigits) else unscaled / pow10L(scaleDigits)
    override fun toFloat(): Float = toDouble().toFloat()
    override fun toDouble(): Double = toPlainString().toDouble()
    fun intValueExact(): Int = toInt()
    fun longValueExact(): Long = toLong()
    override fun compareTo(other: BigDecimal): Int = aligned(other).let { (a, b, _) -> a.compareTo(b) }
    override fun equals(other: Any?): Boolean = other is BigDecimal && other.unscaled == unscaled && other.scaleDigits == scaleDigits
    override fun hashCode(): Int = unscaled.hashCode() * 31 + scaleDigits
    override fun toString(): String = toPlainString()

    companion object {
        val ZERO = BigDecimal(0L, 0)
        val ONE = BigDecimal(1L, 0)
        val TWO = BigDecimal(2L, 0)
        val TEN = BigDecimal(10L, 0)
        const val ROUND_HALF_UP = 4
        fun valueOf(v: Double): BigDecimal = BigDecimal(v.toString())
        fun valueOf(v: Long): BigDecimal = BigDecimal(v, 0)
        fun valueOf(unscaled: Long, scale: Int): BigDecimal = BigDecimal(unscaled, scale)

        private fun pow10L(n: Int): Long { var r = 1L; repeat(n) { r *= 10 }; return r }

        private fun parse(raw: String): Pair<Long, Int> {
            val text = raw.trim()
            val mantissa = text.substringBefore('e').substringBefore('E')
            val exponent = if (mantissa.length < text.length) text.substring(mantissa.length + 1).toInt() else 0
            val whole = mantissa.substringBefore('.')
            val fraction = mantissa.substringAfter('.', "")
            val unscaled = (whole + fraction).let { if (it == "-" || it == "+" || it.isEmpty()) "0" else it }.toLong()
            return unscaled to (fraction.length - exponent)
        }

        private fun roundedDivide(num: Long, den: Long, mode: RoundingMode): Long {
            val q = num / den
            val r = num % den
            if (r == 0L) return q
            val positive = (num < 0) == (den < 0)
            val away = if (positive) q + 1 else q - 1
            val twice = kotlin.math.abs(r) * 2
            val absDen = kotlin.math.abs(den)
            return when (mode) {
                RoundingMode.UP -> away
                RoundingMode.DOWN -> q
                RoundingMode.CEILING -> if (positive) away else q
                RoundingMode.FLOOR -> if (positive) q else away
                RoundingMode.HALF_UP -> if (twice >= absDen) away else q
                RoundingMode.HALF_DOWN -> if (twice > absDen) away else q
                RoundingMode.HALF_EVEN -> if (twice > absDen || (twice == absDen && q % 2 != 0L)) away else q
                RoundingMode.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
            }
        }
    }
}

private val Long.sign: Int get() = if (this > 0) 1 else if (this < 0) -1 else 0

class BigInteger(private val v: Long) : Number(), Comparable<BigInteger> {
    constructor(text: String) : this(text.toLong())
    override fun toByte() = v.toByte(); override fun toShort() = v.toShort(); override fun toInt() = v.toInt()
    override fun toLong() = v; override fun toFloat() = v.toFloat(); override fun toDouble() = v.toDouble()
    override fun compareTo(other: BigInteger): Int = v.compareTo(other.v)
    override fun toString(): String = v.toString()
    fun toString(radix: Int): String = v.toString(radix)
    companion object { fun valueOf(v: Long) = BigInteger(v); val ZERO = BigInteger(0); val ONE = BigInteger(1) }
}

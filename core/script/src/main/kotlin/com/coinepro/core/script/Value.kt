package com.coinepro.core.script

import com.coinepro.core.chart.Line

/**
 * What an expression evaluates to.
 *
 * Two kinds, and the difference is the whole model: a **scalar** has one value for the entire
 * script, and a **series** has one value per bar. Every operator broadcasts a scalar against a
 * series, so `close * 2` and `close > ta.sma(close, 20)` both work without the script saying
 * anything about bars.
 *
 * Booleans are kept as their own series type rather than as ones and zeros. `plot(close > 100)`
 * should be a type error a reader is told about, not a chart of 1s and 0s they have to work out
 * for themselves.
 */
internal sealed interface Value {

    data class Num(val value: Double) : Value
    data class Text(val value: String) : Value
    data class Flag(val value: Boolean) : Value

    /** ARGB. Kept apart from [Num] so a colour cannot be added to a price. */
    data class Colour(val argb: Long) : Value

    /** One number per bar, with the absences the indicator library already tracks. */
    data class NumberSeries(val line: Line) : Value

    /** One condition per bar. Absent where it could not be decided — a warm-up, a missing bar. */
    data class FlagSeries(val line: Line) : Value

    /** What a caller says this value is, when a message has to name it. */
    val typeName: String
        get() = when (this) {
            is Num -> "عدد"
            is Text -> "رشته"
            is Flag -> "درست/نادرست"
            is Colour -> "رنگ"
            is NumberSeries -> "سری عددی"
            is FlagSeries -> "سری شرطی"
        }

    /** The same, for a message in English. */
    val typeNameEn: String
        get() = when (this) {
            is Num -> "a number"
            is Text -> "text"
            is Flag -> "true/false"
            is Colour -> "a colour"
            is NumberSeries -> "a number series"
            is FlagSeries -> "a condition series"
        }
}

/** Truthy where the value is present and non-zero. The one place a number becomes a condition. */
internal fun Line.asFlags(): Line = Line.of(size) { index -> this[index]?.let { if (it != 0.0) 1.0 else 0.0 } }

internal fun constantLine(size: Int, value: Double): Line = Line.of(size) { value }

/**
 * A flag series read at one bar.
 *
 * Absent reads as false rather than as an error, because the alternative is every script guarding
 * its own warm-up. A condition that could not be decided has not fired.
 */
internal fun Line.flagAt(index: Int): Boolean = this[index]?.let { it != 0.0 } == true

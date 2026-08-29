package com.coinepro.feature.screener.model

import com.coinepro.core.symbols.SymbolSearch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How a numeric threshold compares a market's value against the reader's number.
 *
 * [BETWEEN] is the only one that reads a second number, and it is deliberately an operator rather
 * than two filters. A reader asking for a move of two to five percent is asking one question; as
 * two filters it is two rows in the sheet that can be edited into contradicting each other, and a
 * screen that says «تغییر > ۵» and «تغییر < ۲» on two lines has no honest way to explain why it
 * matches nothing.
 */
enum class NumericOp(val symbol: String, val label: String) {
    GT(">", "بیشتر از"),
    GTE("≥", "بیشتر یا برابر"),
    LT("<", "کمتر از"),
    LTE("≤", "کمتر یا برابر"),
    BETWEEN("↔", "بین"),
    EQ("=", "برابر با"),
    ;

    /**
     * Apply this operator, with [bound] as the second end of a [BETWEEN].
     *
     * Pure and total: a non-finite [actual] — which is what a division by a zero low produces — is
     * never a match, whatever the operator, because `NaN > 0` and `NaN < 0` are both false and a
     * reader would otherwise see a market vanish from both halves of a threshold.
     */
    fun matches(actual: Double, value: Double, bound: Double? = null): Boolean {
        if (!actual.isFinite()) return false
        return when (this) {
            GT -> actual > value
            GTE -> actual >= value
            LT -> actual < value
            LTE -> actual <= value
            EQ -> approximatelyEqual(actual, value)
            // Inclusive at both ends. A reader typing 2 and 5 means "two to five" and expects a
            // market that moved exactly five percent to be in it; an exclusive bound is the kind of
            // off-by-one nobody can see and everybody argues about.
            //
            // The bounds are ordered rather than trusted in the order they arrive, because a range
            // control has two handles and either can be dragged past the other. A reversed pair
            // still means the span between the two numbers.
            //
            // A missing second bound is a range of zero width, which is [EQ] — the same answer the
            // control gives when both handles sit on the same number. Refusing it instead would
            // mean a filter that can be constructed but never evaluated.
            BETWEEN -> {
                val other = bound ?: return approximatelyEqual(actual, value)
                actual >= min(value, other) && actual <= max(value, other)
            }
        }
    }

    /** Whether this operator reads [ScreenerFilter.Numeric.bound]. Drives what the sheet shows. */
    val takesSecondValue: Boolean get() = this == BETWEEN

    companion object {
        /**
         * The tolerance [EQ] allows, relative to the larger of the two numbers.
         *
         * Equality on a Double is never asked for literally. Every figure a screener holds has been
         * through a division — a percentage change, a distance from a high, a moving average — and
         * `(2.6 - 2.5) / 2.5 * 100` is not `4.0` in binary floating point. A reader who types 4 and
         * gets nothing back has been told the truth by the machine and a lie by the product.
         *
         * Relative rather than absolute because the same filter has to work on a Bitcoin price near
         * ninety thousand and a currency pair near one. A fixed 1e-9 is far too tight at the top of
         * that range — the gap between representable doubles there is already larger — and far too
         * loose at the bottom of a satoshi-priced coin.
         */
        const val EQ_RELATIVE_EPSILON: Double = 1e-9

        /** Equality within [EQ_RELATIVE_EPSILON], scaled to the magnitude being compared. */
        fun approximatelyEqual(a: Double, b: Double): Boolean {
            if (!a.isFinite() || !b.isFinite()) return false
            val scale = max(1.0, max(abs(a), abs(b)))
            return abs(a - b) <= EQ_RELATIVE_EPSILON * scale
        }
    }
}

/**
 * One condition a market has to satisfy to stay in the list.
 *
 * ### Every one of these is free
 *
 * There is no cap on how many filters a screen may hold, no cap on how many screens may be saved,
 * no filter reserved for a paid tier and no indicator behind a lock — [IndicatorFilter] least of
 * all, which is the one the obvious competitor sells. That is a deliberate product decision and it
 * is written here, in the model, rather than only in a changelog: a future change that adds a
 * `requiresMembership` flag to this file is changing the product's mind, and should have to say so.
 *
 * ### [matches] is pure
 *
 * No clock, no network, no cached state — a row in, a boolean out. That is what makes a screener
 * testable at all, and it is also what lets the controller re-run the whole filter set over a
 * thousand rows on every keystroke without a coroutine.
 *
 * ### A value the row does not have is never a match
 *
 * A market whose bar has not been read yet has a null change, a null volume and no indicator
 * readings. Every subclass answers false for those rather than true, so a threshold shows the
 * markets that are known to pass it rather than the markets that have not yet been shown to fail.
 * The reverse — treating unknown as passing — fills the screen with rows that vanish one by one as
 * the data lands, which reads as the app losing results.
 */
sealed interface ScreenerFilter {

    /** Whether [row] survives this condition. Pure; safe to call inside a sort or a recomposition. */
    fun matches(row: ScreenerRow): Boolean

    /**
     * A threshold on a numeric field.
     *
     * [bound] is read only by [NumericOp.BETWEEN] and is null for every other operator. It is a
     * property of the filter rather than of the operator because an operator is an enum constant
     * shared by every filter in the app, and a range's second number belongs to the one range.
     */
    data class Numeric(
        val field: ScreenerField,
        val op: NumericOp,
        val value: Double,
        val bound: Double? = null,
    ) : ScreenerFilter {
        /**
         * False for a categorical [field], because [ScreenerRow.valueOf] answers null for one.
         *
         * That is the honest outcome and not a silent failure: a numeric threshold on «دسته» is a
         * question with no meaning, and the filter sheet never offers it. It cannot crash, though,
         * because a saved screen written by a later build could carry one.
         */
        override fun matches(row: ScreenerRow): Boolean {
            val actual = row.valueOf(field) ?: return false
            return op.matches(actual, value, bound)
        }
    }

    /**
     * A set of allowed values for a categorical field — market, asset class, quote currency.
     *
     * ### An empty set matches everything
     *
     * This is the choice worth documenting, because both answers are defensible and the wrong one
     * is invisible until a reader hits it. The set is what the reader has *ticked*, and a chip row
     * with nothing ticked is the state every filter sheet opens in. Reading that as "allow none"
     * would mean a freshly opened sheet empties the list before the reader has done anything, and
     * the only way back is to tick every chip — so the control would appear to be broken by being
     * untouched.
     *
     * Read as "no restriction", an empty set is the absence of a filter, which is exactly what an
     * untouched control is. A reader who genuinely wants nothing has an easier route: they close
     * the screener.
     *
     * Comparison is on the upper-cased value, because [ScreenerRow.textOf] upper-cases and a set
     * built from a chip row carries whatever the chip's id happened to be.
     */
    data class Category(val field: ScreenerField, val values: Set<String>) : ScreenerFilter {
        override fun matches(row: ScreenerRow): Boolean {
            if (values.isEmpty()) return true
            val actual = row.textOf(field) ?: return false
            return values.any { it.uppercase() == actual }
        }
    }

    /**
     * A free-text condition on the market's name, ranked by `core:symbols`.
     *
     * Routed through [SymbolSearch.match] rather than a `contains` over the ticker, and the
     * difference is the whole reason that object exists: a Persian reader typing «بیت‌کوین» gets
     * nothing from a substring filter on `BTCUSDT`, and `BTC` typed into one puts `WBTCUSDT` and
     * `BTCUSDT` on equal footing. Here the score is discarded — a filter answers yes or no, and the
     * screener's order is the reader's chosen sort, not a relevance ranking — but the *matching* is
     * the same four-kind matcher the search screen uses, so the two screens agree about what the
     * word «طلا» finds.
     *
     * A blank query matches everything, for the same reason an empty [Category] set does: an empty
     * search box is not a search.
     */
    data class TextMatch(val query: String) : ScreenerFilter {
        override fun matches(row: ScreenerRow): Boolean {
            if (query.isBlank()) return true
            return SymbolSearch.match(row.meta, query) != null
        }
    }

    /**
     * A threshold on an indicator reading — [109], and the one the competition charges for.
     *
     * ### Why this is not just `Numeric` on a derived field
     *
     * [Numeric] on, say, [ScreenerField.RSI] uses that field's declared period and nothing else.
     * This one carries its own [period], so a reader can ask for a two-bar RSI under ten *and* a
     * fourteen-bar RSI over fifty in the same screen — two conditions on the same indicator, which
     * is how a mean-reversion screen is actually written and which a field-keyed filter cannot
     * express at all.
     *
     * A null [period] means the indicator's own default, resolved through
     * [ScreenerIndicatorId.normalisedKey] so that it addresses the same reading the resolver filed.
     *
     * ### Free
     *
     * No membership check, no row limit, no "upgrade to filter by RSI". See the interface note.
     */
    data class IndicatorFilter(
        val indicatorId: String,
        val period: Int?,
        val op: NumericOp,
        val value: Double,
        /** The second end of a [NumericOp.BETWEEN], as on [Numeric]. */
        val bound: Double? = null,
    ) : ScreenerFilter {
        /** The [ScreenerRow.indicators] key this filter reads. */
        val key: String get() = ScreenerIndicatorId.normalisedKey(indicatorId, period)

        override fun matches(row: ScreenerRow): Boolean {
            val actual = row.indicators[key] ?: return false
            return op.matches(actual, value, bound)
        }
    }

    companion object {
        /**
         * Whether every filter in [filters] accepts [row].
         *
         * Conjunction, with no way to express an alternative, and that is on purpose for now: a
         * screener whose conditions can be OR-ed needs a grouping control, and a phone sheet that
         * asks a reader to build a boolean tree is a worse product than one that asks them to save
         * two screens. Saving screens is free and unlimited, so the second screen costs nothing.
         */
        fun allMatch(filters: List<ScreenerFilter>, row: ScreenerRow): Boolean =
            filters.all { it.matches(row) }

        /** The indicator readings [filters] needs before they can answer, as normalised keys. */
        fun indicatorKeys(filters: List<ScreenerFilter>): Set<String> = buildSet {
            filters.forEach { filter ->
                when (filter) {
                    is IndicatorFilter -> add(filter.key)
                    is Numeric -> filter.field.indicatorKey?.let(::add)
                    else -> Unit
                }
            }
        }
    }
}

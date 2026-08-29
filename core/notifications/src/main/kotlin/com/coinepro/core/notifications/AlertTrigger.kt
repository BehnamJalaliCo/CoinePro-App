package com.coinepro.core.notifications

/**
 * How a number is compared to a level.
 *
 * ### The boundary is the whole reason this is an enum and not a lambda
 *
 * Every argument about a price alert is an argument about the equals case. A reader who asks to be
 * told when Bitcoin is *above* 65,000 and gets nothing at exactly 65,000 thinks the app is broken;
 * a reader who asks for a *crossing* of 65,000 and is told about it while the price has sat above
 * that line for a week thinks the same thing. The two questions are different and this app answers
 * them separately rather than picking one and calling it "above".
 *
 * So, stated once, and the tests assert exactly these:
 *
 * * [GREATER_THAN] and [LESS_THAN] are **strict**. At the level itself, neither fires. They read a
 *   single sample and describe where the price *is*.
 * * The three crossings are **inclusive on arrival**: coming from below, touching the level counts
 *   as having crossed it. They read two samples and describe what the price *did*.
 *
 * The older [LocalAlertCondition.ABOVE] and [LocalAlertCondition.BELOW] are inclusive, because they
 * were written before this distinction existed and changing them would silently alter alerts people
 * already have. That inconsistency is deliberate and is the reason it is written down here.
 */
enum class PriceOp(val id: String) {
    /** Reached the level from either side. Inclusive: touching it is crossing it. */
    CROSSING("crossing"),

    /** Came from below and reached the level. */
    CROSSING_UP("crossing_up"),

    /** Came from above and reached the level. */
    CROSSING_DOWN("crossing_down"),

    /** Strictly above the level. Sitting exactly on it is not above it. */
    GREATER_THAN("greater_than"),

    /** Strictly below the level. */
    LESS_THAN("less_than"),
    ;

    /**
     * Whether this comparison needs the previous sample to mean anything.
     *
     * The crossings do, and that is not a detail a caller can shrug off: without a previous sample
     * there is no direction, and inventing one — treating the first reading after a restart as the
     * far side of the level — fires every crossing alert the reader owns the moment the app wakes.
     */
    val needsPrevious: Boolean
        get() = this == CROSSING || this == CROSSING_UP || this == CROSSING_DOWN

    /**
     * Applies the comparison.
     *
     * Returns false rather than guessing when a crossing has no previous sample. That is the
     * conservative answer in both directions: a missed crossing costs one notification, and an
     * invented one costs the reader's trust in every notification after it.
     */
    fun matches(previous: Double?, current: Double, level: Double): Boolean = when (this) {
        GREATER_THAN -> current > level
        LESS_THAN -> current < level
        CROSSING_UP -> previous != null && previous < level && current >= level
        CROSSING_DOWN -> previous != null && previous > level && current <= level
        CROSSING -> CROSSING_UP.matches(previous, current, level) ||
            CROSSING_DOWN.matches(previous, current, level)
    }

    companion object {
        fun fromId(id: String?): PriceOp? = entries.firstOrNull { it.id == id }
    }
}

/**
 * How a price is compared to a band rather than to a line.
 *
 * A channel is two levels, and the four questions people ask about one are not interchangeable.
 * "Tell me when it leaves the range" is a one-shot event; "tell me while it is in the range" is a
 * state. An app that offers only the state makes the reader build the event out of two alerts.
 *
 * Both bounds are **inside** the channel. A price sitting exactly on the low is in the range, not
 * out of it, which is what anybody drawing a box on a chart means by the box.
 */
enum class ChannelOp(val id: String) {
    /** Was outside the band and is now in it. */
    ENTERING("entering"),

    /** Was inside the band and is now out of it. */
    EXITING("exiting"),

    /** Is in the band, bounds included. A state, so it is true on every sample while it holds. */
    INSIDE("inside"),

    /** Is not in the band. */
    OUTSIDE("outside"),
    ;

    /** Whether this needs the previous sample. The two transitions do; the two states do not. */
    val needsPrevious: Boolean
        get() = this == ENTERING || this == EXITING

    companion object {
        fun fromId(id: String?): ChannelOp? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What kind of move a [AlertTrigger.Move] measures.
 *
 * The percentage pair exists because that is how people actually talk about a market — "tell me if
 * it drops three percent" — and the absolute pair exists because that is how people talk about the
 * instrument they are actually holding. Offering one and not the other makes somebody do arithmetic
 * before they can ask their question, which is the same complaint that put the percent conditions
 * into [LocalAlertCondition].
 */
enum class MoveOp(val id: String) {
    /** Rose by at least the amount, in price. */
    UP("up"),

    /** Fell by at least the amount, in price. */
    DOWN("down"),

    /** Rose by at least the amount, as a percentage of where it started. */
    UP_PERCENT("up_percent"),

    /** Fell by at least that percentage. */
    DOWN_PERCENT("down_percent"),
    ;

    /** Whether the number the reader types is a percentage rather than a price. */
    val isPercent: Boolean
        get() = this == UP_PERCENT || this == DOWN_PERCENT

    companion object {
        fun fromId(id: String?): MoveOp? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What an alert watches for.
 *
 * ### Why this is a sealed interface and not more fields on [LocalPriceAlert]
 *
 * [LocalAlertCondition] is a flat enum of six questions, all of them about a single price against a
 * single number. That shape cannot hold "between 64,000 and 66,000", or "when RSI(14) crosses 70",
 * or "when it touches the trend line I drew", because those conditions carry different data — two
 * levels, an indicator and its period, the identity of a drawing. A wider enum would mean every
 * alert carrying every field, most of them null, and every reader of an alert having to know which
 * combination is meaningful.
 *
 * So the condition is a type, each case carries exactly what it needs, and the compiler enforces
 * that a channel alert has two bounds.
 *
 * ### [evaluate] is pure and total, and that is the load-bearing property
 *
 * No clock, no store, no indicator engine, no I/O. Every input arrives as a parameter, including
 * time where time matters, and every case returns a boolean for every input rather than throwing on
 * a shape it did not expect. Two things follow, and both are why this design was chosen:
 *
 * * The interesting cases — the equals boundary, a missing previous sample, a series that is too
 *   short — are unit tests rather than a device, a market move and somebody watching.
 * * The same function runs in the foreground evaluator and in the background worker. An alert that
 *   fires while the app is open and not while it is closed is the single most common complaint
 *   about alerts in this market, and sharing one pure function is how it is avoided.
 *
 * ### What `previous`, `current` and `series` actually are
 *
 * They are deliberately unnamed as to *what* they measure, because two of the cases do not measure
 * price:
 *
 * * For [Price], [Channel] and [Move] they are the instrument's price — the last sample, this
 *   sample, and the recent closes oldest-first.
 * * For [Indicator] they are the **indicator's own output**, not the price. The caller computes
 *   RSI, MACD or a moving average and hands its value in. This module does not depend on the chart
 *   engine and must not; an alert that recomputed an indicator differently from the chart the
 *   reader is looking at would be worse than no alert.
 * * For [DrawingTouch] `previous` and `current` are the price, and `series` carries the drawn
 *   line's own price level at those two moments — a line is not a constant, so the level has to
 *   travel with the sample.
 */
sealed interface AlertTrigger {

    /** Stable key for storage. Never localise it and never reuse one for a different case. */
    val id: String

    /**
     * Whether this trigger is satisfied by this sample.
     *
     * Pure and total: same inputs, same answer, no exception for any input. Where the trigger needs
     * something the caller did not supply — a previous sample for a crossing, a long enough series
     * for a multi-bar move — the answer is false. Refusing to fire is the only safe default; a
     * notification that should not have been sent cannot be recalled.
     */
    fun evaluate(previous: Double?, current: Double, series: DoubleArray?): Boolean

    /**
     * A price against one level.
     *
     * The plain case, and still the most used one. Its whole subtlety is in [PriceOp], which is
     * where the boundary rules are written down.
     */
    data class Price(val op: PriceOp, val value: Double) : AlertTrigger {
        override val id: String get() = ID

        override fun evaluate(previous: Double?, current: Double, series: DoubleArray?): Boolean =
            op.matches(previous, current, value)

        companion object {
            const val ID = "price"
        }
    }

    /**
     * A price against a band.
     *
     * [low] must not be above [high]; a channel with its bounds swapped is a caller bug rather than
     * a reader's choice, and silently sorting them would let a screen ship with its two fields
     * wired the wrong way round and nobody noticing. The stored form checks the pair before it
     * builds one, so a corrupt row is dropped rather than thrown.
     */
    data class Channel(val op: ChannelOp, val low: Double, val high: Double) : AlertTrigger {

        init {
            require(low <= high) { "A channel's low ($low) cannot be above its high ($high)." }
        }

        override val id: String get() = ID

        /** Whether a sample is in the band. Both bounds count as in. */
        fun contains(value: Double): Boolean = value in low..high

        override fun evaluate(previous: Double?, current: Double, series: DoubleArray?): Boolean {
            val inside = contains(current)
            return when (op) {
                ChannelOp.INSIDE -> inside
                ChannelOp.OUTSIDE -> !inside
                ChannelOp.ENTERING -> previous != null && !contains(previous) && inside
                ChannelOp.EXITING -> previous != null && contains(previous) && !inside
            }
        }

        companion object {
            const val ID = "channel"
        }
    }

    /**
     * A move of a given size over a window of bars.
     *
     * ### Where the window comes from
     *
     * [bars] counts back from the end of `series`, which is the recent closes oldest-first. One bar
     * means "against the last close", which is also what `previous` is, so a caller that has no
     * series but does have the last sample still gets the answer. More than one bar needs the
     * series, and if it is too short the trigger does not fire — a five-bar move measured over two
     * bars is a different question, and answering a different question is worse than saying nothing.
     *
     * ### Not the same as [LocalAlertCondition.PERCENT_UP]
     *
     * That one measures from the price when the alert was created and never re-bases. This one
     * measures over a rolling window, so it keeps working for months. Both are wanted, for
     * different reasons: the first is "from where I bought", the second is "something is happening
     * right now".
     */
    data class Move(val op: MoveOp, val amount: Double, val bars: Int = 1) : AlertTrigger {

        init {
            require(bars >= 1) { "A move is measured over at least one bar, not $bars." }
        }

        override val id: String get() = ID

        override fun evaluate(previous: Double?, current: Double, series: DoubleArray?): Boolean {
            val from = when {
                series != null && series.size >= bars -> series[series.size - bars]
                bars == 1 -> previous
                else -> null
            } ?: return false
            return when (op) {
                MoveOp.UP -> current - from >= amount
                MoveOp.DOWN -> from - current >= amount
                MoveOp.UP_PERCENT -> from > 0.0 && (current - from) / from * 100.0 >= amount
                MoveOp.DOWN_PERCENT -> from > 0.0 && (from - current) / from * 100.0 >= amount
            }
        }

        companion object {
            const val ID = "move"
        }
    }

    /**
     * An indicator's own output against a level.
     *
     * ### The caller computes the indicator, and that is not laziness
     *
     * `core:notifications` does not depend on `core:chart` and must not start. If it did, this
     * module would grow a second implementation of every indicator the chart draws, and the day the
     * two disagreed by a rounding step the reader would get an alert about a crossing that is not
     * visible on their screen. Instead the chart's own engine produces the value and hands it in as
     * `current`, so the alert fires on exactly the number the reader can see.
     *
     * [indicatorId] is the chart catalogue's id and [period] its length where it has one — null for
     * an indicator with no single period, or where the alert should use whatever the reader has the
     * chart set to.
     */
    data class Indicator(
        val indicatorId: String,
        val period: Int?,
        val op: PriceOp,
        val value: Double,
    ) : AlertTrigger {

        init {
            require(indicatorId.isNotBlank()) { "An indicator trigger needs an indicator id." }
            require(period == null || period >= 1) { "An indicator period cannot be $period." }
        }

        override val id: String get() = ID

        /** `previous` and `current` are the indicator's output here, not the price. */
        override fun evaluate(previous: Double?, current: Double, series: DoubleArray?): Boolean =
            op.matches(previous, current, value)

        companion object {
            const val ID = "indicator"
        }
    }

    /**
     * Price reaching a line the reader drew.
     *
     * ### Why the level arrives in `series`
     *
     * A trend line is not a price, it is a price *per moment*. Storing the level in the trigger
     * would freeze it at the instant the alert was made, and the alert would then fire on a
     * horizontal line that the reader can see is not horizontal. So the caller resolves the
     * drawing's level at each sample — that is the chart engine's job, it owns the geometry — and
     * passes `[levelBefore, levelNow]`, or a single `[levelNow]` where it has only one.
     *
     * A touch is a sign change of `price − level`, in either direction, with zero counting as a
     * touch. That is the same inclusive rule [PriceOp]'s crossings use, for the same reason: a
     * reader who drew a line and watched the candle land exactly on it has seen the event.
     */
    data class DrawingTouch(val drawingId: String) : AlertTrigger {

        init {
            require(drawingId.isNotBlank()) { "A drawing trigger needs a drawing id." }
        }

        override val id: String get() = ID

        override fun evaluate(previous: Double?, current: Double, series: DoubleArray?): Boolean {
            if (series == null || series.isEmpty()) return false
            val levelNow = series[series.size - 1]
            val now = current - levelNow
            if (now == 0.0) return true
            // With one sample there is no direction, so only an exact landing counts. Documented
            // rather than papered over: the caller that wants touches to be reliable passes two.
            val before = previous?.minus(if (series.size >= 2) series[series.size - 2] else levelNow)
                ?: return false
            return before == 0.0 || (before < 0.0) != (now < 0.0)
        }

        companion object {
            const val ID = "drawing_touch"
        }
    }

    /**
     * Several conditions on one symbol, all of which must hold.
     *
     * ### ANDed, capped at five, and on one symbol
     *
     * AND rather than OR because two ORed conditions are two alerts, and the reader can already
     * make two alerts. AND is the thing they cannot express otherwise — "RSI above 70 *and* price
     * above the channel" is one question with one answer, and splitting it into two alerts produces
     * two notifications neither of which is the thing being asked about.
     *
     * Five because the cap has to be somewhere and because a six-condition alert has stopped being
     * an alert and become a strategy; the store is a delimited preference, not a rules engine. Zero
     * is rejected for a blunter reason: an empty AND is vacuously true, so it would fire on the
     * first tick, forever, and look exactly like a bug in the price feed.
     *
     * Nesting is rejected too. A tree of ANDs is still an AND, and allowing one would make the
     * stored form recursive for no gain the reader can see.
     *
     * ### Two ways to evaluate it, and the second is usually the right one
     *
     * [evaluate] applies the *same* sample to every condition, which is correct only when every
     * condition reads price. As soon as one of them is an [Indicator] or a [DrawingTouch] the
     * conditions need different numbers, and the caller should use [evaluateAll] to supply each one
     * its own.
     */
    data class MultiCondition(val conditions: List<AlertTrigger>) : AlertTrigger {

        init {
            require(conditions.isNotEmpty()) {
                "A multi-condition alert with no conditions is always true; it would never stop firing."
            }
            require(conditions.size <= MAX_CONDITIONS) {
                "At most $MAX_CONDITIONS conditions, not ${conditions.size}."
            }
            require(conditions.none { it is MultiCondition }) {
                "Conditions do not nest; a multi-condition inside a multi-condition is still one AND."
            }
        }

        override val id: String get() = ID

        override fun evaluate(previous: Double?, current: Double, series: DoubleArray?): Boolean =
            conditions.all { it.evaluate(previous, current, series) }

        /**
         * Evaluates each condition against the sample that condition is about.
         *
         * The three lambdas are looked up per condition, so an indicator condition can be handed
         * the indicator's output while a price condition beside it is handed the price. Still pure:
         * the lambdas are the caller's, and nothing here reads a clock or a store.
         */
        fun evaluateAll(
            current: (AlertTrigger) -> Double,
            previous: (AlertTrigger) -> Double? = { null },
            series: (AlertTrigger) -> DoubleArray? = { null },
        ): Boolean = conditions.all { it.evaluate(previous(it), current(it), series(it)) }

        companion object {
            const val ID = "multi"

            /** The cap, and the reason for it is in this class's own documentation. */
            const val MAX_CONDITIONS = 5
        }
    }
}

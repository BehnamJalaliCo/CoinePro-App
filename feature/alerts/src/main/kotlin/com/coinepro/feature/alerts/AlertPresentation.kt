package com.coinepro.feature.alerts

import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertSound
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.AuditEvent
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.MoveOp
import com.coinepro.core.notifications.PriceOp
import com.coinepro.core.symbols.SymbolClassifier

/**
 * The words this feature speaks, in Persian, outside the resource files.
 *
 * ### Why these are not string resources
 *
 * Everything a screen says in its own voice — a title, a button, an empty state — is in
 * `strings.xml` and has an English counterpart. What is here instead is the **vocabulary of the
 * domain**: the verb for a comparison, the name of a repeat policy, the name of an event in a log.
 * Three reasons that split is deliberate, and the same one `Timeframe.label` and `ChartCatalog`
 * already follow:
 *
 * * [AlertSentence] is a pure function and is unit-tested as one. A renderer that needed a
 *   `Context` to say «بالای» could only be tested on a device, and the digit rule below — the one
 *   that has already caused a bug in this repository — is exactly what needs asserting off-device.
 * * The sentence is assembled from four or five of these fragments at once. Held as separate
 *   resources they would be translated separately by somebody who never sees the whole sentence,
 *   which is how «عبور رو به بالا از» becomes grammatically wrong next to a channel.
 * * A comparison operator is not product copy. It is the name of a thing the evaluator does, and it
 *   changes only when the evaluator does.
 */
object AlertVocabulary {

    /** How a price is compared to a level, as the verb of a sentence rather than as a label. */
    fun priceOp(op: PriceOp): String = when (op) {
        PriceOp.CROSSING -> "عبور از"
        PriceOp.CROSSING_UP -> "عبور رو به بالا از"
        PriceOp.CROSSING_DOWN -> "عبور رو به پایین از"
        PriceOp.GREATER_THAN -> "بالای"
        PriceOp.LESS_THAN -> "زیر"
    }

    /** The short form, for a chip in the editor where the sentence is not yet a sentence. */
    fun priceOpChip(op: PriceOp): String = when (op) {
        PriceOp.CROSSING -> "عبور"
        PriceOp.CROSSING_UP -> "عبور به بالا"
        PriceOp.CROSSING_DOWN -> "عبور به پایین"
        PriceOp.GREATER_THAN -> "بالاتر"
        PriceOp.LESS_THAN -> "پایین‌تر"
    }

    /** What a price does with respect to a band. */
    fun channelOp(op: ChannelOp): String = when (op) {
        ChannelOp.ENTERING -> "ورود به محدودهٔ"
        ChannelOp.EXITING -> "خروج از محدودهٔ"
        ChannelOp.INSIDE -> "داخل محدودهٔ"
        ChannelOp.OUTSIDE -> "بیرون از محدودهٔ"
    }

    /** The chip form of [channelOp], without the noun the sentence supplies. */
    fun channelOpChip(op: ChannelOp): String = when (op) {
        ChannelOp.ENTERING -> "ورود"
        ChannelOp.EXITING -> "خروج"
        ChannelOp.INSIDE -> "داخل"
        ChannelOp.OUTSIDE -> "بیرون"
    }

    /** The direction and unit of a move. */
    fun moveOp(op: MoveOp): String = when (op) {
        MoveOp.UP -> "رشد به اندازهٔ"
        MoveOp.DOWN -> "افت به اندازهٔ"
        MoveOp.UP_PERCENT -> "رشد"
        MoveOp.DOWN_PERCENT -> "افت"
    }

    /** The chip form of [moveOp]. The percentage pair names its unit; the absolute pair cannot. */
    fun moveOpChip(op: MoveOp): String = when (op) {
        MoveOp.UP -> "رشد قیمتی"
        MoveOp.DOWN -> "افت قیمتی"
        MoveOp.UP_PERCENT -> "رشد درصدی"
        MoveOp.DOWN_PERCENT -> "افت درصدی"
    }

    /**
     * How often the alert may speak.
     *
     * Worded in bars rather than in minutes because that is the unit the policy is actually in —
     * see `AlertFrequency`. «یک‌بار در هر کندل» and «یک‌بار در بستن کندل» are different promises and
     * the second is the one people ask for by name, so neither is shortened into the other.
     */
    fun frequency(value: AlertFrequency): String = when (value) {
        AlertFrequency.ONCE -> "یک‌بار"
        AlertFrequency.ONCE_PER_BAR -> "یک‌بار در هر کندل"
        AlertFrequency.ONCE_PER_BAR_CLOSE -> "یک‌بار در بستن کندل"
        AlertFrequency.EVERY_TIME -> "هر بار"
    }

    /** How one alert is allowed to reach the reader. */
    fun channel(value: AlertChannel): String = when (value) {
        AlertChannel.PUSH -> "اعلان"
        AlertChannel.IN_APP -> "داخل برنامه"
        AlertChannel.SOUND -> "صدا"
        AlertChannel.VIBRATE -> "لرزش"
    }

    /**
     * What one line of the audit log records.
     *
     * [AuditEvent.FIRED] and [AuditEvent.DELIVERED] are worded so that a reader can tell them apart
     * at a glance, because telling them apart is the whole point of the log: the first is the app
     * deciding, the second is the notification actually arriving, and the gap between them is where
     * every «هشدارها کار نمی‌کند» complaint lives.
     */
    fun auditEvent(event: AuditEvent): String = when (event) {
        AuditEvent.CREATED -> "ساخته شد"
        AuditEvent.EDITED -> "ویرایش شد"
        AuditEvent.ARMED -> "پایش آغاز شد"
        AuditEvent.FIRED -> "شرط برقرار شد"
        AuditEvent.DELIVERED -> "اعلان رسید"
        AuditEvent.DELIVERY_FAILED -> "اعلان نرسید"
        AuditEvent.SNOOZED -> "به تعویق افتاد"
        AuditEvent.EXPIRED -> "منقضی شد"
        AuditEvent.DELETED -> "حذف شد"
    }
}

/**
 * How loud one alert is, as three named choices rather than a slider.
 *
 * ### Why three steps and not a continuous control
 *
 * `AlertSound` is a fraction from silent to full and the delivery layer changes *output* — from the
 * notification stream to the alarm stream — at one documented threshold. A slider over that is a
 * control whose one meaningful position is invisible: the reader drags, the number moves, and
 * somewhere near the end the behaviour changes with nothing on screen to say where. Three named
 * steps put the escalation where it belongs, in a word the reader chooses.
 *
 * ### And why the loud one is at the very top
 *
 * [LOUD] is [AlertSound.MAX_LEVEL], comfortably past [AlertSound.LOUD_THRESHOLD], because a step
 * that sat *at* the threshold would depend on whether the comparison is inclusive — and this is the
 * one setting in the app where getting that wrong is silent: the alert fires, the notification
 * posts, and it goes out on the ordinary channel at the ordinary volume. The review this whole area
 * answers is *a beep is not enough to alert someone busy at work*, and that failure is exactly a
 * beep.
 */
enum class AlertLoudness(val level: Float) {
    /** Audible if the phone is in your hand. For an alert the reader wants recorded, not announced. */
    QUIET(0.35f),

    /** What every alert has always been, and still the default. Behaves like the app's other notifications. */
    NORMAL(AlertSound.DEFAULT_LEVEL),

    /** Full, on the alarm output. The one the reader picks for the level they have waited weeks for. */
    LOUD(AlertSound.MAX_LEVEL),
    ;

    /** Whether this step reaches the alarm output. True for exactly one of the three. */
    val isLoud: Boolean get() = AlertSound.isLoud(level)

    companion object {

        /**
         * The step nearest a stored level.
         *
         * Nearest rather than exact, because a level can arrive from a build that offered different
         * steps, or from a hand-edited preference. Answering with the closest named step is what
         * lets the control show a position for any number instead of showing none for most of them.
         */
        fun of(level: Float): AlertLoudness {
            val clamped = AlertSound.coerce(level)
            return entries.minByOrNull { kotlin.math.abs(it.level - clamped) } ?: NORMAL
        }
    }
}

/**
 * One indicator a reader may put an alert on.
 *
 * ### Why this list is here rather than taken from the chart catalogue
 *
 * `core:chart` owns fifty-odd indicators and this module does not depend on it, for the same reason
 * `AlertTrigger.Indicator` does not: an alerts module that pulled in the chart engine would end up
 * with a second implementation of every study, and the day the two disagreed by a rounding step the
 * reader would get an alert about a crossing that is not on the chart in front of them.
 *
 * So this is a *menu*, not an engine. The [id]s are the chart catalogue's own ids, so an alert made
 * here and evaluated by the chart-aware evaluator names the same study; [ticker] is what a terminal
 * prints and is what goes into the readable sentence, because «RSI(14)» is what a trader reads and
 * «شاخص قدرت نسبی (۱۴)» is what they have to translate back.
 *
 * The set is short on purpose. Fifty studies in an alert picker is a search problem the reader did
 * not come here to solve; these are the ones people actually set alerts on.
 */
data class AlertIndicator(
    /** The chart catalogue's id. Never localised, never invented here. */
    val id: String,
    /** The Latin short form, e.g. `RSI`. Goes into the sentence, isolated as one run. */
    val ticker: String,
    /** The Persian name, for the picker where there is room to say it properly. */
    val label: String,
    /** The lookback it is usually computed at, or null for a study with no single period. */
    val defaultPeriod: Int?,
)

/** The indicators offered in the editor, and the arithmetic of the period stepper. */
object AlertIndicators {

    /** Shortest lookback worth computing. One bar is not an average. */
    const val MIN_PERIOD = 2

    /**
     * Longest offered.
     *
     * Two hundred rather than a rounder fifty: the two-hundred-period moving average is a level
     * people genuinely watch, and a stepper that stopped short of it would be a picker that cannot
     * express the most-quoted setting in the market it serves.
     */
    const val MAX_PERIOD = 200

    /** What the editor starts on. */
    const val DEFAULT_ID = "rsi"

    /** The menu, ordered as a reader would look for them: momentum first, then trend. */
    val ALL: List<AlertIndicator> = listOf(
        AlertIndicator("rsi", "RSI", "شاخص قدرت نسبی", 14),
        AlertIndicator("macd", "MACD", "مکدی", null),
        AlertIndicator("stochastic", "Stoch", "استوکاستیک", 14),
        AlertIndicator("cci", "CCI", "شاخص کانال کالا", 20),
        AlertIndicator("williams", "%R", "ویلیامز", 14),
        AlertIndicator("mom", "Mom", "مومنتوم", 10),
        AlertIndicator("roc", "ROC", "نرخ تغییر", 9),
        AlertIndicator("atr", "ATR", "میانگین دامنهٔ واقعی", 14),
        AlertIndicator("adx", "ADX", "شاخص میانگین جهت‌دار", 14),
        AlertIndicator("ema", "EMA", "میانگین متحرک نمایی", 20),
        AlertIndicator("sma", "SMA", "میانگین متحرک ساده", 20),
        AlertIndicator("vwap", "VWAP", "میانگین وزنی حجم", null),
    )

    /** The entry for [id], or null for an id from a build that offered more than this one. */
    fun of(id: String): AlertIndicator? = ALL.firstOrNull { it.id == id }

    /**
     * The period [id] should start at.
     *
     * Null both for a study with no period and for one this build does not know — in both cases the
     * editor hides the stepper rather than showing it at a number nobody chose.
     */
    fun defaultPeriodOf(id: String): Int? = of(id)?.defaultPeriod

    /** A period moved by the stepper, clamped rather than refused. */
    fun coercePeriod(value: Int): Int = value.coerceIn(MIN_PERIOD, MAX_PERIOD)
}

/**
 * An alert's condition, written as a sentence a Persian reader can check at a glance.
 *
 * ### Why a sentence and not a row of fields
 *
 * The list is the screen where somebody with eleven alerts finds the one that is wrong. A row of
 * labelled fields — symbol, operator, value, unit — makes them reassemble the meaning four times a
 * screen. «BTC/USDT بالای 68,500» is read once. That is the whole reason this function exists, and
 * it is why it produces one string rather than a structure a layout has to arrange.
 *
 * ### The digits are Latin and the words are Persian, deliberately
 *
 * Every number in here is a market figure — a level, a band, a percentage, an indicator period —
 * and a market figure is what the reader compares against MetaTrader or LBank. The device locale is
 * Persian, so `String.format` and `DecimalFormat` will emit «۶۸٬۵۰۰» unless told otherwise; every
 * number below goes through [MarketNumberFormatter], which fixes `Locale.US` for exactly that
 * reason, and is wrapped in a bidirectional isolate so a Latin run inside a right-to-left sentence
 * does not reorder. Both halves of that are asserted by the tests under a Persian default locale.
 *
 * ### Pure, and no `Context`
 *
 * Nothing here touches resources, a clock or a store. It is the part of this feature that must be
 * right, so it is the part that is a unit test rather than a screenshot.
 */
object AlertSentence {

    /** Between the conditions of a multi-condition alert. They are ANDed; the word says so. */
    private const val AND = " و "

    /**
     * The whole sentence for one alert: what it is about, then what it waits for.
     *
     * Reads [LocalPriceAlert.trigger] where the alert has one and falls back to the flat
     * [LocalPriceAlert.condition] where it does not. Both shapes exist on real phones — every alert
     * stored before triggers existed is the second — and a list that could only render the new
     * shape would show the reader's older alerts as blanks.
     */
    fun render(alert: LocalPriceAlert, watchlistName: (String) -> String? = { null }): String {
        val trigger = alert.trigger
        val predicate = if (trigger != null) {
            predicate(trigger)
        } else {
            predicate(alert.condition, alert.value)
        }
        return subject(alert.effectiveScope, watchlistName) + " " + predicate
    }

    /**
     * What the alert is about.
     *
     * A symbol is written the way a terminal writes it — `BTC/USDT`, not `BTCUSDT` — because the
     * pair is the thing the reader recognises, and it is isolated so the slash does not flip.
     * A watchlist is named where the caller can name it and falls back to the plain noun where the
     * list has since been deleted, which is a real state: the alert survives its list.
     */
    fun subject(scope: AlertScope, watchlistName: (String) -> String? = { null }): String =
        when (scope) {
            is AlertScope.Symbol -> BidiText.isolateLtr(SymbolClassifier.classify(scope.ticker).pretty)
            is AlertScope.Watchlist -> {
                val named = watchlistName(scope.listId)
                if (named.isNullOrBlank()) "فهرست دنبال‌شده" else "فهرست $named"
            }
        }

    /**
     * What the alert waits for, without naming the instrument.
     *
     * Split from [subject] because a multi-condition alert names its instrument once and then
     * states two or three things about it; repeating the ticker between them would read as two
     * alerts printed on one line.
     */
    fun predicate(trigger: AlertTrigger): String = when (trigger) {
        is AlertTrigger.Price ->
            AlertVocabulary.priceOp(trigger.op) + " " + number(trigger.value)

        is AlertTrigger.Channel ->
            AlertVocabulary.channelOp(trigger.op) + " " +
                number(trigger.low) + " تا " + number(trigger.high)

        is AlertTrigger.Move -> {
            val amount = if (trigger.op.isPercent) percent(trigger.amount) else number(trigger.amount)
            AlertVocabulary.moveOp(trigger.op) + " " + amount
        }

        is AlertTrigger.Indicator ->
            indicatorName(trigger.indicatorId, trigger.period) + " " +
                AlertVocabulary.priceOp(trigger.op) + " " + number(trigger.value)

        // The drawing's own name belongs to the chart, which owns the geometry; from here the
        // honest statement is that the alert watches a line the reader drew, not which one.
        is AlertTrigger.DrawingTouch -> "برخورد با ترسیم روی نمودار"

        is AlertTrigger.MultiCondition ->
            trigger.conditions.joinToString(AND) { predicate(it) }
    }

    /**
     * The same, for an alert stored before triggers existed.
     *
     * The two 24-hour conditions read the feed's own daily change rather than anything this app
     * measures, so they say so — «تغییر ۲۴ ساعته» with the twenty-four in Persian digits, because
     * that number is prose rather than a market figure.
     */
    fun predicate(condition: LocalAlertCondition, value: Double): String = when (condition) {
        LocalAlertCondition.ABOVE -> "بالای " + number(value)
        LocalAlertCondition.BELOW -> "زیر " + number(value)
        LocalAlertCondition.PERCENT_UP -> "رشد " + percent(value) + " از زمان ساخت"
        LocalAlertCondition.PERCENT_DOWN -> "افت " + percent(value) + " از زمان ساخت"
        LocalAlertCondition.CHANGE_24H_OVER -> "رشد ۲۴ ساعته بیش از " + percent(value)
        LocalAlertCondition.CHANGE_24H_UNDER -> "افت ۲۴ ساعته بیش از " + percent(value)
    }

    /**
     * `RSI(14)` — the study and its lookback as one Latin run.
     *
     * One isolate around the whole thing rather than one around each part: the parentheses belong
     * to the Latin run, and isolating only the number leaves them free to swap ends of it in a
     * right-to-left paragraph.
     */
    fun indicatorName(indicatorId: String, period: Int?): String {
        val ticker = AlertIndicators.of(indicatorId)?.ticker ?: indicatorId.uppercase()
        return BidiText.isolateLtr(if (period == null) ticker else "$ticker($period)")
    }

    /**
     * A price or a level, grouped, Latin, isolated.
     *
     * [MarketNumberFormatter.priceAuto] rather than a fixed two decimals, because this list holds
     * instruments four orders of magnitude apart and a level of 0.000031 rendered at two decimals
     * reads as an outage rather than as a price.
     */
    fun number(value: Double): String = MarketNumberFormatter.priceAuto(value)

    /**
     * A percentage, at the two decimals the rest of the app uses.
     *
     * The `%` has to be inside the isolate with the digits. Appended after one, it is a separate
     * run and lands on the wrong end of the number in a right-to-left line — which is why the raw
     * text is taken back out of [MarketNumberFormatter]'s own isolate and re-wrapped once.
     */
    fun percent(value: Double): String =
        BidiText.isolateLtr(BidiText.strip(MarketNumberFormatter.price(value, decimals = 2)) + "%")
}

package com.coinepro.core.chart

import androidx.annotation.DrawableRes
import java.util.Locale
import com.coinepro.core.designsystem.R as DesignR

/**
 * The chart types a reader can choose, and the indicators they can add.
 *
 * Almost every entry carries a [helpId], and that is the point of this file existing at all rather
 * than the pickers hard-coding their own lists. The «؟» content is the web terminal's, keyed by its
 * own ids, so the mapping from "the thing this app offers" to "the thing the help explains" has to
 * be written down once and be checkable — a picker that silently offers a tool with no help, or
 * points at the wrong entry, is exactly the kind of gap nobody notices until a user does.
 *
 * `ChartCatalogTest` asserts that every id here exists in the shipped catalogue, and pins the
 * thirteen indicators that have no entry so a fourteenth cannot join them unnoticed.
 */
data class ChartTypeOption(
    val type: ChartType,
    /** Persian label. The web terminal's own name for it. */
    val label: String,
    /** The «؟» entry id in the help catalogue. */
    val helpId: String,
    /**
     * TradingView's own glyph for this chart type.
     *
     * There is one for every type, drawn as a tiny picture of the thing — two candles, a Renko
     * staircase, a Kagi zigzag. A list of eleven chart types with no pictures asks the reader to
     * know what "لاین‌بریک" looks like before they can choose it, which defeats the point of
     * offering it.
     */
    @DrawableRes val icon: Int,
)

/**
 * One indicator a reader can switch on.
 *
 * [pane] decides where it draws. An indicator on the price scale — a moving average, a band, a
 * trailing stop — goes over the candles; one on its own scale must not, because plotting RSI's 0-100
 * against a gold price of 2,600 collapses the price axis to a line.
 */
data class IndicatorOption(
    val id: String,
    val label: String,
    /**
     * The «؟» entry id, or null where the shipped catalogue has no entry.
     *
     * Null rather than a guess. Thirteen of the eighty-three point at nothing — the web terminal's
     * help was written before those indicators were added to it — and a «؟» that opens an empty
     * sheet is a worse answer than no «؟». `ChartCatalogTest` pins exactly which thirteen, so the
     * gap can only close, never quietly widen.
     */
    val helpId: String?,
    val pane: IndicatorPane,
    /** ARGB. Several indicators draw more than one line and pick shades around this. */
    val colour: Long,
    /**
     * A glyph for the *kind* of indicator, not for the indicator itself.
     *
     * Nobody publishes a distinct icon per indicator — TradingView included, which lists them as
     * plain text. Twenty copies of one generic symbol would say nothing, so these say what shape
     * the thing draws: a line, a channel, an oscillating wave, a histogram, a ruler, a direction.
     * That is the distinction a reader scanning the list is actually making.
     */
    @DrawableRes val icon: Int,
)

/**
 * The one number a reader is allowed to change on an indicator, and the range it may take.
 *
 * ### Why only one
 *
 * Most indicators here have several parameters — MACD has three, Bollinger has a period and a
 * multiplier, Ichimoku has three spans. Exposing all of them would mean a form per indicator and
 * fifty forms to design, most of which nobody would ever open. Exposing *the lookback* covers what
 * readers actually change: «EMA 20» is not the same tool as «EMA 200», and until this existed the
 * app offered only the first, forever, with the number baked into the label as a literal.
 *
 * Indicators whose shape is not a single lookback — MACD, Ichimoku, VWAP, the structure studies —
 * are absent from this table on purpose, and the picker shows them no stepper rather than a
 * stepper that changes nothing.
 *
 * ### The bounds
 *
 * [min] is 2 almost everywhere, because a one-bar average is the price. [max] is 400, which is
 * past the longest period anybody uses (the 200-day average) with room above it, and short enough
 * that the result is not a line of nulls on a series of 500 bars.
 */
data class IndicatorPeriod(val default: Int, val min: Int = 2, val max: Int = 400)

enum class IndicatorPane {
    /** Drawn over the candles, on the price axis. */
    PRICE,

    /** Drawn in its own pane below, on its own scale. */
    SEPARATE,

    /**
     * Drawn over the candles, but not as a value per bar.
     *
     * Its own pane rather than sharing [PRICE] because it is a different kind of answer. A moving
     * average says "the average here is 2,614"; a support study says "2,614 is a level" and a swing
     * study says "this bar mattered". They needed two drawing shapes the chart did not have —
     * [PriceLevel] and [ChartMarker] — and they are reached through [ChartCatalog.structureFor]
     * rather than [ChartCatalog.overlayFor], so keeping them apart is the type system agreeing.
     */
    STRUCTURE,
}

/** What a structure study draws: any mix of lines, horizontal levels and per-bar marks. */
data class StructureOverlay(
    val lines: List<ChartLine> = emptyList(),
    val levels: List<PriceLevel> = emptyList(),
    val markers: List<ChartMarker> = emptyList(),
) {
    val isEmpty: Boolean get() = lines.isEmpty() && levels.isEmpty() && markers.isEmpty()
}

/**
 * Which bars a window-scoped study measures over.
 *
 * ### Why this type exists at all
 *
 * A volume profile answers "where did the trading happen in *this* stretch of chart". Computed over
 * every bar the app happens to have loaded, it answers a question nobody asked: the reader is
 * looking at four hours of a coin and the point of control comes from a week they cannot see, drawn
 * as a line across the part they can. That is what `volumeprofile_ind` did — three flat lines from
 * the whole series — and it is what [Visible] fixes.
 *
 * ### Why the default is still the whole series
 *
 * Because a caller with no viewport must say something, and silence is worse. [WHOLE_SERIES] is
 * spelled out at every call site that takes it, so a profile over everything is a decision in the
 * code rather than an omission — and a grep for it finds the callers that still have to be given a
 * viewport.
 */
data class BarWindow(val firstIndex: Int, val lastIndex: Int) {

    /**
     * This window as real indices into a series of [size] bars, or null when it selects nothing.
     *
     * Clamped rather than validated: a viewport is a live thing, and a range that briefly runs past
     * the end of a series that has just been replaced must not throw in the middle of a frame.
     */
    fun clampedTo(size: Int): IntRange? {
        if (size <= 0) return null
        val first = firstIndex.coerceIn(0, size - 1)
        val last = lastIndex.coerceIn(0, size - 1)
        return if (first > last) null else first..last
    }

    companion object {
        /** Every bar loaded. A fallback, and named so that choosing it is visible in the diff. */
        val WHOLE_SERIES = BarWindow(0, Int.MAX_VALUE)

        /** The bars on screen — `ChartViewport.firstVisible` and `lastVisible`. */
        fun visible(firstIndex: Int, lastIndex: Int) = BarWindow(firstIndex, lastIndex)
    }
}

object ChartCatalog {

    /**
     * The eleven chart types, in the order the picker shows them.
     *
     * Ordinary first, price-driven last. Somebody opening this list is nine times in ten switching
     * between candles and Heikin-Ashi; Renko and Point & Figure are deliberate choices that people
     * go looking for.
     */
    /**
     * The chart types a feed can actually draw.
     *
     * Footprint and TPO both read volume per price row, and the MT5 forex feed reports none, so on
     * that platform they are not offered at all. A caller that wants the raw table uses
     * [CHART_TYPES]; a caller that is about to show a reader a number or a list uses this.
     */
    fun chartTypesFor(hasVolume: Boolean): List<ChartTypeOption> =
        if (hasVolume) CHART_TYPES else CHART_TYPES.filter { it.type !in VOLUME_ONLY_TYPES }

    /** How many types [chartTypesFor] would offer. */
    fun chartTypeCount(hasVolume: Boolean): Int = chartTypesFor(hasVolume).size

    /** The two types that are meaningless without a volume column. */
    val VOLUME_ONLY_TYPES: Set<ChartType> = setOf(ChartType.FOOTPRINT, ChartType.TPO)

    val CHART_TYPES: List<ChartTypeOption> = listOf(
        ChartTypeOption(ChartType.CANDLES, "کندل", "candles", DesignR.drawable.tv_chart_candles),
        ChartTypeOption(ChartType.HOLLOW, "کندل توخالی", "hollow", DesignR.drawable.tv_chart_hollow),
        ChartTypeOption(ChartType.HEIKIN_ASHI, "هایکین‌آشی", "heikin", DesignR.drawable.tv_chart_heikin),
        ChartTypeOption(ChartType.BARS, "میله‌ای (OHLC)", "bars", DesignR.drawable.tv_chart_bars),
        ChartTypeOption(ChartType.LINE, "خطی", "line", DesignR.drawable.tv_chart_line),
        ChartTypeOption(ChartType.AREA, "ناحیه‌ای", "area", DesignR.drawable.tv_chart_area),
        ChartTypeOption(ChartType.RENKO, "رنکو", "renko", DesignR.drawable.tv_chart_renko),
        ChartTypeOption(ChartType.RANGE, "رنج", "range", DesignR.drawable.tv_chart_range),
        ChartTypeOption(ChartType.LINE_BREAK, "لاین‌بریک", "linebreak", DesignR.drawable.tv_chart_linebreak),
        ChartTypeOption(ChartType.KAGI, "کاگی", "kagi", DesignR.drawable.tv_chart_kagi),
        ChartTypeOption(ChartType.POINT_AND_FIGURE, "نقطه و رقم", "pnf", DesignR.drawable.tv_chart_pnf),

        // ── The same bars, rendered to answer a different question ──────────────────
        //
        // Six of these seven are the feed untouched with added geometry rather than a new
        // aggregation, which is why they carry no `ChartTypeConfig` of their own beyond a base
        // level or a row count. FOOTPRINT and TPO are the exception in a way that matters at the
        // picker: both read volume per price row, and the MT5 forex feed reports no volume at all,
        // so `ChartTransforms.footprint` returns an empty list there rather than a wall of zeros.
        // The picker hides them on that feed; see `ChartTypePicker`.
        ChartTypeOption(ChartType.BASELINE, "خط پایه", "baseline", DesignR.drawable.tv_chart_baseline),
        ChartTypeOption(ChartType.HLC_AREA, "ناحیهٔ HLC", "hlcarea", DesignR.drawable.tv_chart_hlcarea),
        ChartTypeOption(ChartType.STEP_LINE, "پلکانی", "step", DesignR.drawable.tv_chart_step),
        ChartTypeOption(ChartType.LINE_MARKERS, "خطی با نشانگر", "lwm", DesignR.drawable.tv_chart_lwm),
        ChartTypeOption(ChartType.VOLUME_CANDLES, "کندل حجمی", "volcandles", DesignR.drawable.tv_chart_volcandles),
        ChartTypeOption(ChartType.FOOTPRINT, "فوت‌پرینت", "footprint", DesignR.drawable.tv_chart_footprint),
        ChartTypeOption(ChartType.TPO, "پروفایل زمانی", "tpo", DesignR.drawable.tv_chart_tpo),
    )

    /**
     * The indicators a feed can actually compute.
     *
     * The same shape as [chartTypesFor] and for the same reason: fourteen of the rows below are
     * arithmetic on a volume column, the MT5 forex feed reports none, and an indicator offered on a
     * feed that cannot compute it is a switch that draws nothing when tapped. A caller that wants
     * the raw table uses [INDICATORS]; a caller that is about to put a list in front of a reader
     * uses this.
     */
    fun indicatorsFor(hasVolume: Boolean): List<IndicatorOption> =
        if (hasVolume) INDICATORS else INDICATORS.filterNot { it.id in VOLUME_ONLY_INDICATORS }

    /** How many indicators [indicatorsFor] would offer. */
    fun indicatorCount(hasVolume: Boolean): Int = indicatorsFor(hasVolume).size

    /**
     * The indicators that are arithmetic on a volume column and nothing else.
     *
     * Zero is not the same claim as absent, and this set is where that distinction is written down
     * once. A money-flow index computed from a volume array of zeros is not "no money flowing"; it
     * is a reading of a column the feed never sent, and drawn as a flat line at fifty it looks like
     * a market in perfect balance. [overlayFor] and [paneFor] both consult this and return nothing
     * rather than a fabricated line, and the picker hides the rows entirely through
     * [indicatorsFor].
     */
    val VOLUME_ONLY_INDICATORS: Set<String> = setOf(
        "vwap", "obv", "adline", "chaikinOsc", "eom", "forceIndex", "klinger", "pvt",
        "vwma", "volumeprofile_ind", "mfi", "cmf", "pvo", "netvolume",
    )

    /**
     * The eighty-three indicators the engine computes, grouped the way a trader thinks about them.
     *
     * Twenty-six draw on the price, forty-nine in a pane of their own and eight as structure. That
     * is far past the point where a list can be scanned, which is why the picker grew a search
     * field and a pane filter before this list grew past twenty. The order is the useful one and
     * not an alphabet: within each pane, the ones most readers reach for first.
     */
    val INDICATORS: List<IndicatorOption> = listOf(
        // Trend — on the price.
        IndicatorOption("sma", "میانگین متحرک ساده", "ma", IndicatorPane.PRICE, 0xFFD8A848, DesignR.drawable.tv_chart_line),
        IndicatorOption("ema", "میانگین متحرک نمایی", "ema", IndicatorPane.PRICE, 0xFF6E8BE0, DesignR.drawable.tv_chart_line),
        IndicatorOption("wma", "میانگین متحرک وزنی", "wma", IndicatorPane.PRICE, 0xFF9B7BE0, DesignR.drawable.tv_chart_line),
        IndicatorOption("hma", "میانگین متحرک هال", "hma", IndicatorPane.PRICE, 0xFF4FB3A5, DesignR.drawable.tv_chart_line),
        IndicatorOption("bollinger", "باند بولینگر", "bb", IndicatorPane.PRICE, 0xFF8E9BAE, DesignR.drawable.tv_tool_flatchannel),
        IndicatorOption("keltner", "کانال کلتنر", "keltner", IndicatorPane.PRICE, 0xFF7FA3C7, DesignR.drawable.tv_tool_flatchannel),
        IndicatorOption("donchian", "کانال دونچیان", "donchian", IndicatorPane.PRICE, 0xFFB08BC7, DesignR.drawable.tv_tool_flatchannel),
        IndicatorOption("ichimoku", "ایچیموکو", "ichimoku", IndicatorPane.PRICE, 0xFFC77F9B, DesignR.drawable.tv_chart_hlcarea),
        IndicatorOption("supertrend", "سوپرترند", "supertrend", IndicatorPane.PRICE, 0xFF00B15C, DesignR.drawable.tv_tool_trend),
        IndicatorOption("vwap", "میانگین وزنی حجم", "vwap", IndicatorPane.PRICE, 0xFFE0A85C, DesignR.drawable.tv_chart_volcandles),

        // Momentum and volatility — their own scale.
        IndicatorOption("rsi", "شاخص قدرت نسبی", "rsi", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_tool_sine),
        IndicatorOption("macd", "مکدی", "macd", IndicatorPane.SEPARATE, 0xFF6E8BE0, DesignR.drawable.tv_chart_columns),
        IndicatorOption("stochastic", "استوکاستیک", "stoch", IndicatorPane.SEPARATE, 0xFF4FB3A5, DesignR.drawable.tv_tool_sine),
        IndicatorOption("cci", "شاخص کانال کالا", "cci", IndicatorPane.SEPARATE, 0xFF9B7BE0, DesignR.drawable.tv_tool_sine),
        IndicatorOption("williams", "ویلیامز R%", "willr", IndicatorPane.SEPARATE, 0xFFC77F9B, DesignR.drawable.tv_tool_sine),
        IndicatorOption("atr", "میانگین دامنهٔ واقعی", "atr", IndicatorPane.SEPARATE, 0xFF8E9BAE, DesignR.drawable.tv_ruler),
        IndicatorOption("adx", "شاخص میانگین جهت‌دار", "adx", IndicatorPane.SEPARATE, 0xFFE0A85C, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("choppiness", "شاخص چاپینس", "choppiness", IndicatorPane.SEPARATE, 0xFF7FA3C7, DesignR.drawable.tv_ruler),
        IndicatorOption("vortex", "ورتکس", "vortex", IndicatorPane.SEPARATE, 0xFFB08BC7, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("obv", "حجم متعادل", "obv", IndicatorPane.SEPARATE, 0xFF00B15C, DesignR.drawable.tv_chart_columns),

        // ── The second thirty, from indicators_ext_a.js and indicators_ext_b.js ─────────────
        // Trend, on the price.
        IndicatorOption("smma", "میانگین وایلدر (SMMA)", "smma", IndicatorPane.PRICE, 0xFF84CC16, DesignR.drawable.tv_chart_line),
        IndicatorOption("zlema", "میانگین با تأخیر صفر", "zlema", IndicatorPane.PRICE, 0xFF2DD4BF, DesignR.drawable.tv_chart_line),
        IndicatorOption("kama", "میانگین تطبیقی کافمن", "kama", IndicatorPane.PRICE, 0xFFFB923C, DesignR.drawable.tv_chart_line),
        IndicatorOption("t3", "میانگین T3 تیلسون", "t3", IndicatorPane.PRICE, 0xFFE879F9, DesignR.drawable.tv_chart_line),
        IndicatorOption("mcginley", "مک‌گینلی داینامیک", "mcginley", IndicatorPane.PRICE, 0xFFFACC15, DesignR.drawable.tv_chart_line),
        IndicatorOption("linreg", "منحنی رگرسیون خطی", "linreg", IndicatorPane.PRICE, 0xFF38BDF8, DesignR.drawable.tv_tool_trend),
        IndicatorOption("lsma", "میانگین کمترین‌مربعات", "lsma", IndicatorPane.PRICE, 0xFFD8A848, DesignR.drawable.tv_tool_trend),
        IndicatorOption("envelopes", "پاکت درصدی", "envelopes", IndicatorPane.PRICE, 0xFFB08BC7, DesignR.drawable.tv_tool_flatchannel),

        // Volatility, on its own scale.
        IndicatorOption("stddev", "انحراف معیار", "stddev", IndicatorPane.SEPARATE, 0xFFFB7185, DesignR.drawable.tv_ruler),
        IndicatorOption("hv", "نوسان تاریخی", "hv", IndicatorPane.SEPARATE, 0xFFF59E0B, DesignR.drawable.tv_ruler),
        IndicatorOption("chaikinVol", "نوسان چایکین", "chaikinVol", IndicatorPane.SEPARATE, 0xFF0EA5E9, DesignR.drawable.tv_ruler),
        IndicatorOption("bbpercent", "باند بولینگر ٪B", "bbpercent", IndicatorPane.SEPARATE, 0xFF22D3EE, DesignR.drawable.tv_tool_sine),
        IndicatorOption("bbw", "پهنای باند بولینگر", "bbw", IndicatorPane.SEPARATE, 0xFF8E9BAE, DesignR.drawable.tv_ruler),

        // Momentum, on its own scale.
        IndicatorOption("mom", "مومنتوم", "mom", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_tool_sine),
        IndicatorOption("roc", "نرخ تغییر", "roc", IndicatorPane.SEPARATE, 0xFFF472B6, DesignR.drawable.tv_tool_sine),
        IndicatorOption("trix", "تریکس (TRIX)", "trix", IndicatorPane.SEPARATE, 0xFF34D399, DesignR.drawable.tv_tool_sine),
        IndicatorOption("ac", "شتاب‌دهنده", "ac", IndicatorPane.SEPARATE, 0xFF22D3EE, DesignR.drawable.tv_chart_columns),
        IndicatorOption("uo", "اسیلاتور غایی", "uo", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_tool_sine),
        IndicatorOption("fisher", "تبدیل فیشر", "fisher", IndicatorPane.SEPARATE, 0xFFFB923C, DesignR.drawable.tv_tool_sine),
        IndicatorOption("crsi", "کانرز RSI", "crsi", IndicatorPane.SEPARATE, 0xFFE879F9, DesignR.drawable.tv_tool_sine),
        IndicatorOption("smiErgodic", "SMI ارگودیک", "smiErgodic", IndicatorPane.SEPARATE, 0xFF38BDF8, DesignR.drawable.tv_tool_sine),
        IndicatorOption("smi", "مومنتوم استوکاستیک", "smi", IndicatorPane.SEPARATE, 0xFFFACC15, DesignR.drawable.tv_tool_sine),
        IndicatorOption("bop", "توازن قدرت", "bop", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_chart_columns),

        // Volume, on its own scale.
        IndicatorOption("adline", "تجمع و توزیع", "adline", IndicatorPane.SEPARATE, 0xFF0EA5E9, DesignR.drawable.tv_chart_volcandles),
        IndicatorOption("chaikinOsc", "اسیلاتور چایکین", "chaikinOsc", IndicatorPane.SEPARATE, 0xFFF97316, DesignR.drawable.tv_chart_columns),
        IndicatorOption("eom", "سهولت حرکت", "eom", IndicatorPane.SEPARATE, 0xFF84CC16, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("forceIndex", "شاخص نیرو", "forceIndex", IndicatorPane.SEPARATE, 0xFFFB7185, DesignR.drawable.tv_chart_columns),
        IndicatorOption("klinger", "اسیلاتور کلینگر", "klinger", IndicatorPane.SEPARATE, 0xFFA855F7, DesignR.drawable.tv_chart_volcandles),
        IndicatorOption("pvt", "روند قیمت-حجم", "pvt", IndicatorPane.SEPARATE, 0xFF10B981, DesignR.drawable.tv_chart_volcandles),

        // ── The third pack, from indicators_ext_b.js and indicators_ext_c.js ────────────────
        // Trend and trailing stops, on the price.
        IndicatorOption("sar", "پارابولیک سار (SAR)", "psar", IndicatorPane.PRICE, 0xFFE0A85C, DesignR.drawable.tv_tool_dot),
        IndicatorOption("alligator", "تمساح ویلیامز (Alligator)", "alligator", IndicatorPane.PRICE, 0xFF6E8BE0, DesignR.drawable.tv_tool_doublecurve),
        IndicatorOption("vwma", "میانگین متحرک وزن‌دار حجم (VWMA)", "vwma", IndicatorPane.PRICE, 0xFF10B981, DesignR.drawable.tv_chart_volcandles),
        IndicatorOption("tema", "میانگین نمایی سه‌گانه (TEMA)", "tema", IndicatorPane.PRICE, 0xFF2DD4BF, DesignR.drawable.tv_chart_line),
        IndicatorOption("dema", "میانگین نمایی دوگانه (DEMA)", "dema", IndicatorPane.PRICE, 0xFF38BDF8, DesignR.drawable.tv_chart_line),
        IndicatorOption("chandekroll", "حد ضرر چاند-کرول", "chandeKroll", IndicatorPane.PRICE, 0xFFB08BC7, DesignR.drawable.tv_tool_longshort),
        IndicatorOption("volstop", "حد ضرر نوسانی", "volstop", IndicatorPane.PRICE, 0xFFF97316, DesignR.drawable.tv_tool_trend),
        IndicatorOption("volumeprofile_ind", "پروفایل حجم", "volumeProfile", IndicatorPane.PRICE, 0xFF94A3B8, DesignR.drawable.tv_tool_volumeprofile),

        // Momentum and trend strength, on their own scale.
        IndicatorOption("stochrsi", "استوکاستیک RSI", "stochrsi", IndicatorPane.SEPARATE, 0xFF4FB3A5, DesignR.drawable.tv_tool_sine),
        IndicatorOption("tsi", "شاخص قدرت واقعی (TSI)", "tsi", IndicatorPane.SEPARATE, 0xFFE879F9, DesignR.drawable.tv_tool_sine),
        IndicatorOption("aroon", "آرون (Aroon)", "aroon", IndicatorPane.SEPARATE, 0xFF84CC16, DesignR.drawable.tv_tool_arrowdir),
        // Points at the ADX entry, which is not a stand-in: that entry explains the +DI/−DI pair
        // and the ADX built on them as one system, which is exactly what this row draws. DMI is
        // offered separately because a trader running it as a crossover system wants its own
        // lookback, not the one their trend filter is on.
        IndicatorOption("dmi", "شاخص حرکت جهت‌دار (DMI)", "adx", IndicatorPane.SEPARATE, 0xFFE0A85C, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("ppo", "اسیلاتور درصدی قیمت (PPO)", "ppo", IndicatorPane.SEPARATE, 0xFF6E8BE0, DesignR.drawable.tv_chart_columns),
        IndicatorOption("dpo", "اسیلاتور قیمت بدون روند (DPO)", "dpo", IndicatorPane.SEPARATE, 0xFF9B7BE0, DesignR.drawable.tv_tool_sine),
        IndicatorOption("kst", "مجموع نرخ تغییر (KST)", "kst", IndicatorPane.SEPARATE, 0xFFFACC15, DesignR.drawable.tv_tool_sine),
        IndicatorOption("cmo", "اسیلاتور مومنتوم چاند (CMO)", "cmo", IndicatorPane.SEPARATE, 0xFFF472B6, DesignR.drawable.tv_tool_sine),
        IndicatorOption("coppock", "منحنی کاپاک (Coppock)", "coppock", IndicatorPane.SEPARATE, 0xFF34D399, DesignR.drawable.tv_tool_curve),
        IndicatorOption("rvi", "شاخص سرزندگی نسبی (RVI)", "rvi", IndicatorPane.SEPARATE, 0xFF22D3EE, DesignR.drawable.tv_tool_sine),
        IndicatorOption("woodiescci", "سی‌سی‌آی وودیز (Woodies CCI)", "woodiescci", IndicatorPane.SEPARATE, 0xFFA855F7, DesignR.drawable.tv_tool_sine),
        IndicatorOption("massindex", "شاخص جرم (Mass Index)", "massIndex", IndicatorPane.SEPARATE, 0xFF7FA3C7, DesignR.drawable.tv_ruler),
        IndicatorOption("ao", "اسیلاتور شگفت‌انگیز (AO)", "ao", IndicatorPane.SEPARATE, 0xFFD8A848, DesignR.drawable.tv_chart_columns),
        IndicatorOption("correlation", "ضریب همبستگی", "correlation", IndicatorPane.SEPARATE, 0xFF8E9BAE, DesignR.drawable.tv_tool_sync),

        // Volume, on its own scale. Every one of these is in [VOLUME_ONLY_INDICATORS] and is not
        // offered at all on a feed that reports no volume.
        IndicatorOption("mfi", "شاخص جریان نقدینگی", "mfi", IndicatorPane.SEPARATE, 0xFF0EA5E9, DesignR.drawable.tv_chart_volcandles),
        IndicatorOption("cmf", "جریان نقدینگی چایکین", "cmf", IndicatorPane.SEPARATE, 0xFFFB923C, DesignR.drawable.tv_chart_volcandles),
        IndicatorOption("pvo", "اسیلاتور درصدی حجم (PVO)", "pvo", IndicatorPane.SEPARATE, 0xFFFB7185, DesignR.drawable.tv_chart_columns),
        IndicatorOption("netvolume", "حجم خالص", "netVolume", IndicatorPane.SEPARATE, 0xFFF0B90B, DesignR.drawable.tv_chart_columns),

        // ── Structure: levels and marks rather than a value per bar ─────────────────────────
        IndicatorOption("pivots", "پیووت (۵ روش)", null, IndicatorPane.STRUCTURE, 0xFF94A3B8, DesignR.drawable.tv_tool_hline),
        IndicatorOption("swings", "نقاط چرخش", null, IndicatorPane.STRUCTURE, 0xFFF59E0B, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("fractals", "فرکتال ویلیامز", null, IndicatorPane.STRUCTURE, 0xFFF0B90B, DesignR.drawable.tv_tool_arrowdir),
        IndicatorOption("zigzag", "زیگزاگ", null, IndicatorPane.STRUCTURE, 0xFFF59E0B, DesignR.drawable.tv_tool_trend),
        IndicatorOption("autofib", "فیبوناچی خودکار", null, IndicatorPane.STRUCTURE, 0xFF22D3EE, DesignR.drawable.tv_tool_fib),
        IndicatorOption("sr", "حمایت و مقاومت", null, IndicatorPane.STRUCTURE, 0xFFF59E0B, DesignR.drawable.tv_tool_hline),
        IndicatorOption("supplydemand", "نواحی عرضه و تقاضا", null, IndicatorPane.STRUCTURE, 0xFF00B15C, DesignR.drawable.tv_tool_rect),
        // Structure rather than a pane of its own, because it is not a level and not a value: it
        // is a regime reading, one verdict per bar, and the thing it belongs beside is the candle
        // it describes. See [CHOP_ZONE_COLOURS].
        IndicatorOption("chopzone", "ناحیهٔ چاپ (Chop Zone)", "chopzone", IndicatorPane.STRUCTURE, 0xFF7FA3C7, DesignR.drawable.tv_layout_grid),
    )

    /**
     * What a structure study draws for a series.
     *
     * Separate from [overlayFor] because the return type is genuinely different, and a single
     * function returning an object where four of five fields are always empty would hide that.
     */
    fun structureFor(option: IndicatorOption, series: CandleSeries): StructureOverlay {
        if (option.pane != IndicatorPane.STRUCTURE || series.isEmpty) return StructureOverlay()
        return when (option.id) {
            "pivots" -> StructureOverlay(lines = Structure.pivots(series))
            "swings" -> StructureOverlay(markers = Structure.swings(series))
            "fractals" -> StructureOverlay(markers = Structure.fractals(series))
            "zigzag" -> Structure.zigzag(series).let { (line, markers) ->
                StructureOverlay(lines = listOf(line), markers = markers)
            }
            "autofib" -> StructureOverlay(levels = Structure.autoFibonacci(series))
            "sr" -> StructureOverlay(levels = Structure.supportResistance(series))
            "supplydemand" -> StructureOverlay(levels = Structure.supplyDemand(series))
            // One coloured mark per bar, hung under the low, so the row of them reads as a band
            // along the foot of the candles. A `ChartLine` cannot express it — a line has one
            // colour for its whole length and the colour here *is* the reading — and a
            // `PriceLevel` cannot either, because the verdict changes bar to bar.
            "chopzone" -> StructureOverlay(markers = chopZoneMarks(series))
            else -> StructureOverlay()
        }
    }

    /**
     * What a separate-pane indicator draws, on its own scale.
     *
     * The third of the three shapes an indicator can take, beside [overlayFor] and [structureFor],
     * and the last one the chart learned. Until it existed, switching on an RSI in the picker did
     * nothing at all: the option was in the catalogue, the arithmetic was in [Indicators], and
     * nothing joined them because there was nowhere on the canvas to put a second scale.
     *
     * The reference levels each pane declares — RSI's 30 and 70, the zero line under a momentum
     * oscillator — are part of the indicator, not decoration. An RSI without them is a wiggle.
     */
    fun paneFor(
        option: IndicatorOption,
        series: CandleSeries,
        /** The reader's lookback, or null for this indicator's own default. See [PERIODS]. */
        period: Int? = null,
        /**
         * The second instrument, for the one indicator that measures two series against each other.
         *
         * Only `correlation` reads it, and it is null on every other call — which is why it is the
         * last parameter with a default rather than a second entry point. A correlation with
         * nothing to correlate against is not a correlation of zero, so the pane comes back empty;
         * see the `correlation` branch below.
         */
        comparison: ComparisonSeries? = null,
    ): ChartPane? {
        if (option.pane != IndicatorPane.SEPARATE || series.isEmpty) return null
        // A volume study on a feed that reports no volume has no values, not values of zero.
        if (option.id in VOLUME_ONLY_INDICATORS && !series.hasVolume) return null
        val n = periodFor(option.id, period)
        val open = series.open
        val high = series.high
        val low = series.low
        val close = series.close
        val volume = series.volume
        val colour = option.colour
        val second = shade(colour)
        fun pane(
            title: String,
            vararg lines: ChartLine,
            levels: List<PriceLevel> = emptyList(),
            histogram: ChartLine? = null,
        ) = ChartPane(title = title, lines = lines.toList(), levels = levels, histogram = histogram)

        return when (option.id) {
            "rsi" -> pane(
                "RSI $n",
                ChartLine(Indicators.rsi(close, n), colour, label = "RSI"),
                levels = listOf(band(70.0), band(50.0, faint = true), band(30.0)),
            )
            "macd" -> Indicators.macd(close).let { macd ->
                pane(
                    "MACD 12/26/9",
                    ChartLine(macd.macd, colour, label = "MACD"),
                    ChartLine(macd.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                    histogram = ChartLine(macd.histogram, colour),
                )
            }
            "stochastic" -> Indicators.stochastic(high, low, close).let { stoch ->
                pane(
                    "Stochastic 14/3",
                    ChartLine(stoch.k, colour, label = "%K"),
                    ChartLine(stoch.d, second, label = "%D"),
                    levels = listOf(band(80.0), band(20.0)),
                )
            }
            "cci" -> pane(
                "CCI $n",
                ChartLine(Indicators.cci(high, low, close, n), colour),
                levels = listOf(band(100.0), band(0.0, faint = true), band(-100.0)),
            )
            "williams" -> pane(
                "Williams %R $n",
                ChartLine(Indicators.williamsR(high, low, close, n), colour),
                levels = listOf(band(-20.0), band(-80.0)),
            )
            "atr" -> pane("ATR $n", ChartLine(Indicators.atr(high, low, close, n), colour))
            "adx" -> Indicators.adx(high, low, close, n).let { adx ->
                pane(
                    "ADX $n",
                    ChartLine(adx.adx, colour, label = "ADX"),
                    ChartLine(adx.plusDi, 0xFF00B15C, label = "+DI"),
                    ChartLine(adx.minusDi, 0xFFF6465D, label = "−DI"),
                    // Twenty-five is Wilder's own threshold for "there is a trend here at all",
                    // and reading ADX without it is reading a number with no scale.
                    levels = listOf(band(25.0)),
                )
            }
            "choppiness" -> pane(
                "Choppiness $n",
                ChartLine(Indicators.choppiness(high, low, close, n), colour),
                levels = listOf(band(61.8), band(38.2)),
            )
            "vortex" -> Indicators.vortex(high, low, close).let { vortex ->
                pane(
                    "Vortex 14",
                    ChartLine(vortex.plus, 0xFF00B15C, label = "VI+"),
                    ChartLine(vortex.minus, 0xFFF6465D, label = "VI−"),
                    levels = listOf(band(1.0, faint = true)),
                )
            }
            "obv" -> pane("OBV", ChartLine(Indicators.obv(close, volume), colour))
            "stddev" -> pane("StdDev 20", ChartLine(IndicatorsExt.stdDev(close, 20), colour))
            "hv" -> pane("HV 10", ChartLine(IndicatorsExt.historicalVolatility(close), colour))
            "chaikinVol" -> pane(
                "Chaikin Vol 10",
                ChartLine(IndicatorsExt.chaikinVolatility(high, low), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "bbpercent" -> pane(
                "%B 20",
                ChartLine(IndicatorsExt.bollingerPercent(close), colour),
                levels = listOf(band(1.0), band(0.0)),
            )
            "bbw" -> pane("BBW 20", ChartLine(IndicatorsExt.bollingerWidth(close), colour))
            "mom" -> pane(
                "Momentum 10",
                ChartLine(IndicatorsExt.momentum(close, 10), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "roc" -> pane(
                "ROC 12",
                ChartLine(IndicatorsExt.rateOfChange(close, 12), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "trix" -> IndicatorsExt.trix(close).let { trix ->
                pane(
                    "TRIX 18/9",
                    ChartLine(trix.line, colour, label = "TRIX"),
                    ChartLine(trix.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "ac" -> pane(
                "Accelerator",
                levels = listOf(band(0.0, faint = true)),
                histogram = ChartLine(IndicatorsExt.accelerator(high, low), colour),
            )
            "uo" -> pane(
                "Ultimate 7/14/28",
                ChartLine(IndicatorsExt.ultimateOscillator(high, low, close), colour),
                levels = listOf(band(70.0), band(30.0)),
            )
            "fisher" -> IndicatorsExt.fisherTransform(high, low).let { fisher ->
                pane(
                    "Fisher 9",
                    ChartLine(fisher.line, colour, label = "Fisher"),
                    ChartLine(fisher.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "crsi" -> pane(
                "Connors RSI 3/2/100",
                ChartLine(IndicatorsExt.connorsRsi(close), colour),
                levels = listOf(band(90.0), band(10.0)),
            )
            "smiErgodic" -> IndicatorsExt.smiErgodic(close).let { smi ->
                pane(
                    "SMI Ergodic 20/5/5",
                    ChartLine(smi.line, colour, label = "SMI"),
                    ChartLine(smi.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "smi" -> pane(
                "Stochastic Momentum 10",
                ChartLine(IndicatorsExt.stochasticMomentum(high, low, close), colour),
                levels = listOf(band(40.0), band(0.0, faint = true), band(-40.0)),
            )
            "bop" -> pane(
                "Balance of Power",
                levels = listOf(band(0.0, faint = true)),
                histogram = ChartLine(IndicatorsExt.balanceOfPower(open, high, low, close), colour),
            )
            "adline" -> pane(
                "A/D Line",
                ChartLine(IndicatorsExt.accumulationDistribution(high, low, close, volume), colour),
            )
            "chaikinOsc" -> pane(
                "Chaikin Osc 3/10",
                levels = listOf(band(0.0, faint = true)),
                histogram = ChartLine(IndicatorsExt.chaikinOscillator(high, low, close, volume), colour),
            )
            "eom" -> pane(
                "Ease of Movement 14",
                ChartLine(IndicatorsExt.easeOfMovement(high, low, volume), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "forceIndex" -> pane(
                "Force Index 13",
                ChartLine(IndicatorsExt.forceIndex(close, volume), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "klinger" -> IndicatorsExt.klinger(high, low, close, volume).let { klinger ->
                pane(
                    "Klinger 34/55/13",
                    ChartLine(klinger.line, colour, label = "KVO"),
                    ChartLine(klinger.signal, second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "pvt" -> pane("PVT", ChartLine(IndicatorsExt.priceVolumeTrend(close, volume), colour))

            // ── The third pack's own-scale entries ────────────────────────────────────────
            "stochrsi" -> IndicatorsExtB.stochasticRsi(close, n, n).let { stoch ->
                pane(
                    "Stoch RSI $n",
                    ChartLine(stoch.k.asLine(), colour, label = "%K"),
                    ChartLine(stoch.d.asLine(), second, label = "%D"),
                    levels = listOf(band(80.0), band(20.0)),
                )
            }
            "tsi" -> IndicatorsExtB.trueStrengthIndex(close, n).let { tsi ->
                pane(
                    "TSI $n/13/13",
                    ChartLine(tsi.tsi.asLine(), colour, label = "TSI"),
                    ChartLine(tsi.signal.asLine(), second, label = "سیگنال"),
                    levels = listOf(band(25.0), band(0.0, faint = true), band(-25.0)),
                )
            }
            "aroon" -> IndicatorsExtB.aroon(high, low, n).let { aroon ->
                pane(
                    "Aroon $n",
                    ChartLine(aroon.up.asLine(), 0xFF00B15C, label = "Aroon Up"),
                    ChartLine(aroon.down.asLine(), 0xFFF6465D, label = "Aroon Down"),
                    // Seventy and thirty rather than the eighty/twenty of an RSI: Aroon counts
                    // bars since the extreme, and a reading above seventy means the window's high
                    // was set in its most recent third.
                    levels = listOf(band(70.0), band(50.0, faint = true), band(30.0)),
                )
            }
            "dmi" -> IndicatorsExtB.directionalMovement(high, low, close, n).let { dmi ->
                pane(
                    "DMI $n",
                    ChartLine(dmi.plusDi.asLine(), 0xFF00B15C, label = "+DI"),
                    ChartLine(dmi.minusDi.asLine(), 0xFFF6465D, label = "−DI"),
                    ChartLine(dmi.adx.asLine(), colour, widthDp = 0.9f, label = "ADX"),
                    levels = listOf(band(25.0)),
                )
            }
            "ppo" -> IndicatorsExtB.ppo(close).let { ppo ->
                pane(
                    "PPO 12/26/9",
                    ChartLine(ppo.oscillator.asLine(), colour, label = "PPO"),
                    ChartLine(ppo.signal.asLine(), second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                    histogram = ChartLine(ppo.histogram.asLine(), colour),
                )
            }
            "pvo" -> IndicatorsExtB.pvo(volume).let { pvo ->
                pane(
                    "PVO 12/26/9",
                    ChartLine(pvo.oscillator.asLine(), colour, label = "PVO"),
                    ChartLine(pvo.signal.asLine(), second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                    histogram = ChartLine(pvo.histogram.asLine(), colour),
                )
            }
            "ao" -> pane(
                "Awesome Oscillator 5/34",
                levels = listOf(band(0.0, faint = true)),
                histogram = ChartLine(IndicatorsExtB.awesomeOscillator(high, low).asLine(), colour),
            )
            "mfi" -> pane(
                "MFI $n",
                ChartLine(IndicatorsExtB.moneyFlowIndex(high, low, close, volume, n).asLine(), colour),
                levels = listOf(band(80.0), band(50.0, faint = true), band(20.0)),
            )
            "cmf" -> pane(
                "CMF $n",
                ChartLine(IndicatorsExtB.chaikinMoneyFlow(high, low, close, volume, n).asLine(), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "dpo" -> pane(
                "DPO $n",
                ChartLine(IndicatorsExtC.detrendedPriceOscillator(close, n).asLine(), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "kst" -> IndicatorsExtC.knowSureThing(close).let { kst ->
                pane(
                    "KST 10/15/20/30",
                    ChartLine(kst.kst.asLine(), colour, label = "KST"),
                    ChartLine(kst.signal.asLine(), second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "massindex" -> pane(
                "Mass Index $n/9",
                ChartLine(IndicatorsExtC.massIndex(high, low, n).asLine(), colour),
                // The reversal bulge, which is the only thing the indicator is read for: it has to
                // rise through 27 and then fall back below 26.5, and without both lines drawn the
                // second half of that sentence is invisible.
                levels = listOf(band(27.0), band(26.5, faint = true)),
            )
            "cmo" -> pane(
                "CMO $n",
                ChartLine(IndicatorsExtC.chandeMomentumOscillator(close, n).asLine(), colour),
                levels = listOf(band(50.0), band(0.0, faint = true), band(-50.0)),
            )
            "coppock" -> pane(
                "Coppock 14/11/10",
                ChartLine(IndicatorsExtC.coppockCurve(close).asLine(), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "netvolume" -> pane(
                "Net Volume",
                ChartLine(IndicatorsExtC.netVolume(close, volume).asLine(), colour),
                levels = listOf(band(0.0, faint = true)),
            )
            "rvi" -> IndicatorsExtC.relativeVigorIndex(open, high, low, close, n).let { rvi ->
                pane(
                    "RVI $n",
                    ChartLine(rvi.rvi.asLine(), colour, label = "RVI"),
                    ChartLine(rvi.signal.asLine(), second, label = "سیگنال"),
                    levels = listOf(band(0.0, faint = true)),
                )
            }
            "woodiescci" -> IndicatorsExtC.woodiesCci(high, low, close, n).let { woodies ->
                pane(
                    "Woodies CCI $n/6",
                    ChartLine(woodies.cci.asLine(), colour, label = "CCI $n"),
                    ChartLine(woodies.turbo.asLine(), second, label = "Turbo 6"),
                    levels = listOf(band(100.0), band(0.0, faint = true), band(-100.0)),
                )
            }
            "correlation" -> {
                // The one indicator here that is not a function of this symbol alone.
                //
                // With no second series loaded there is nothing to correlate against, and every
                // available wrong answer is worse than none: zero would claim the two assets move
                // independently, a flat line at one would claim they are the same bet, and either
                // is a statement about a comparison the reader never made. So the pane comes back
                // with its title and no line — and with a height of zero, so it costs no canvas
                // and the chart degrades to nothing rather than to an empty strip.
                val other = comparison?.values?.takeIf { it.size == close.size }
                if (other == null) {
                    ChartPane(title = "Correlation $n", heightRatio = 0f)
                } else {
                    pane(
                        "Correlation $n · ${comparison.label}",
                        ChartLine(
                            IndicatorsExtC.correlationCoefficient(close, other, n).asLine(),
                            comparison.colour,
                            label = comparison.label,
                        ),
                        levels = listOf(band(0.5), band(0.0, faint = true), band(-0.5)),
                    )
                }
            }
            else -> null
        }
    }

    /**
     * A reference line inside a pane.
     *
     * [faint] is for the ones that mark a *centre* rather than a threshold — zero on a momentum
     * oscillator, fifty on an RSI. Drawn at the same weight as the thresholds they compete with
     * them for attention, and the thresholds are the ones a reader is watching for.
     */
    private fun band(value: Double, faint: Boolean = false): PriceLevel =
        PriceLevel(value, if (faint) 0xFF5E6673 else 0xFF848E9C, label = null)

    /**
     * A companion colour for an indicator's second line.
     *
     * Derived from the first rather than picked, so a pane with a signal line always reads as one
     * indicator in two shades instead of two indicators sharing a box. Halving each channel towards
     * black is enough separation to tell them apart and not enough to look unrelated.
     */
    private fun shade(argb: Long): Long {
        val alpha = argb and 0xFF000000L
        val red = ((argb shr 16 and 0xFF) * 55 / 100) shl 16
        val green = ((argb shr 8 and 0xFF) * 55 / 100) shl 8
        val blue = (argb and 0xFF) * 55 / 100
        return alpha or red or green or blue
    }

    /**
     * A `DoubleArray` from the newer packs as a [Line].
     *
     * [IndicatorsExtB] and [IndicatorsExtC] hand back bare arrays with `Double.NaN` in the warm-up,
     * because half of what they compute is fed to the other half and a presence mask is a place to
     * lose a bar at every hand-off. This is the single edge where that convention is converted to
     * the one the renderer reads, and it is the only place `isFinite` should appear on the way in:
     * a NaN that survives into a [Line] is drawn as a real value at whatever `Double.NaN` maps to
     * on the price axis, which is nowhere useful.
     */
    private fun DoubleArray.asLine(): Line = Line.of(size) { this[it].takeIf(Double::isFinite) }

    /**
     * One price repeated across every bar, so a horizontal level can be drawn as a [ChartLine].
     *
     * [PriceLevel] is the better shape for a level and is what [structureFor] returns, but
     * [overlayFor]'s contract is a list of lines and the volume profile has to say its three prices
     * through it — the rows themselves travel on [ChartLine.profile]. Cheap enough at a few hundred
     * bars, and honest: the value really is the same on every bar.
     */
    private fun flat(size: Int, price: Double): Line =
        Line.of(size) { if (price.isFinite()) price else null }

    /** The window the chop zone normalises its slope over. Fixed; see the note under [PERIODS]. */
    private const val CHOP_ZONE_PERIOD = 30

    /** How many price rows the volume profile indicator buckets the window into. */
    private const val VOLUME_PROFILE_ROWS = 24

    /**
     * The chop zone's eight colours, steepest rise first and steepest fall last.
     *
     * `IndicatorsExtC.chopZone` returns a palette index and no palette, on purpose: the arithmetic
     * has no business knowing what a colour is. So the ramp lives here, beside the option it
     * belongs to, and this is what each index means:
     *
     * * `0` — rising at 5° or more. Strong uptrend.
     * * `1` — rising between 2.14° and 5°.
     * * `2` — rising between 0.71° and 2.14°.
     * * `3` — rising by less than 0.71°: flat, tilted up. Chop.
     * * `4` — falling by less than 0.71°: flat, tilted down. Chop.
     * * `5` — falling between 0.71° and 2.14°.
     * * `6` — falling between 2.14° and 5°.
     * * `7` — falling at 5° or more. Strong downtrend.
     *
     * ### Why it is not red to green
     *
     * The original is, and roughly one man in twelve cannot read it. This ramp runs blue to orange
     * instead — the axis that red/green deficiency leaves intact — and carries the same reading a
     * second time in lightness: the two extremes are the darkest colours and the two chop buckets
     * the palest. So the band still says «trending» versus «going nowhere» in greyscale, on a
     * washed-out screen in sunlight, and to a reader who sees no difference between the ends of the
     * red/green axis at all. Somebody who cannot tell rise from fall can still tell *chop* from
     * *trend*, which is the one thing this indicator exists to say.
     */
    val CHOP_ZONE_COLOURS: List<Long> = listOf(
        0xFF15427F, // 0 — steep rise: darkest blue.
        0xFF2563EB, // 1
        0xFF60A5FA, // 2
        0xFFB6D4F2, // 3 — chop, tilted up: palest blue.
        0xFFF0DCBE, // 4 — chop, tilted down: palest sand.
        0xFFE0A45C, // 5
        0xFFC2700C, // 6
        0xFF7A3210, // 7 — steep fall: darkest orange.
    )

    /**
     * The chop zone as one coloured mark per bar, hung under the low.
     *
     * Under rather than over, and at the low rather than at a value of its own, because the study
     * has no value on the price axis at all — it is a verdict about the bar, and the only place a
     * verdict belongs is against the bar it judges. Warm-up bars carry `-1` and are skipped rather
     * than drawn in a neutral colour, which would read as a real "no trend" verdict.
     */
    private fun chopZoneMarks(series: CandleSeries): List<ChartMarker> {
        val zones = IndicatorsExtC.chopZone(series.high, series.low, series.close, CHOP_ZONE_PERIOD)
        return buildList {
            for (index in zones.indices) {
                val zone = zones[index]
                if (zone !in CHOP_ZONE_COLOURS.indices) continue
                add(
                    ChartMarker(
                        time = series.time[index],
                        price = series.low[index],
                        above = false,
                        colour = CHOP_ZONE_COLOURS[zone],
                        glyph = MarkerGlyph.CIRCLE,
                    ),
                )
            }
        }
    }

    /**
     * The volume profile behind the `volumeprofile_ind` row, for a range of bars.
     *
     * Public because a renderer that wants the rows on their own terms — the fixed-range and
     * anchored profile tools do — should not have to go through an indicator row to get them. A
     * profile is a histogram measured across the price axis, one bar per price row growing sideways
     * from the edge, and every type [overlayFor] can return is one value per *bar*; the study's own
     * row therefore carries the three prices as lines and the buckets as [ChartLine.profile].
     *
     * Null where there is nothing to draw: an empty series, a feed with no volume column, or a
     * window in which nothing traded. A profile of zeros would be drawn as equal bars at every
     * price, which is a claim that the market traded evenly across its whole range.
     *
     * [window] is the point of the whole study. A visible-range profile measured over bars the
     * reader cannot see is not a visible-range profile; see [BarWindow].
     */
    fun volumeProfileFor(
        series: CandleSeries,
        /**
         * Which bars to measure. Hand it the viewport's own range and the profile follows the
         * reader's screen; [BarWindow.WHOLE_SERIES] measures everything loaded.
         */
        window: BarWindow = BarWindow.WHOLE_SERIES,
        rows: Int = VOLUME_PROFILE_ROWS,
    ): VolumeProfile? {
        if (series.isEmpty || !series.hasVolume) return null
        val range = window.clampedTo(series.size) ?: return null
        val profile = IndicatorsExtC.volumeProfile(
            high = series.high,
            low = series.low,
            close = series.close,
            open = series.open,
            volume = series.volume,
            fromIndex = range.first,
            toIndex = range.last,
            rows = rows,
        )
        return profile.takeIf {
            it.pocIndex >= 0 && it.valueAreaLow >= 0 && it.valueAreaHigh >= 0
        }
    }

    /**
     * Defaults for the two regression curves.
     *
     * A hundred bars for the curve and twenty-five for the projected average, which are the web
     * terminal's — the same series under two periods is two different indicators to a reader, and
     * two products that disagree about which is which are worse than one that offers only one.
     */
    private const val LINREG_PERIOD = 100
    private const val LSMA_PERIOD = 25

    /**
     * The default lookback for every indicator that has exactly one, keyed by id.
     *
     * These are the numbers that used to be literals inside [overlayFor] and [paneFor] — the same
     * values, moved to one place so the picker can show them and a reader can change them. Anything
     * absent has no single lookback and is not offered a stepper.
     *
     * `ChartCatalogTest` holds this table against the two builders, so an indicator that gains a
     * period here and does not read it there is a failing test rather than a stepper that moves a
     * number nothing looks at.
     */
    val PERIODS: Map<String, IndicatorPeriod> = mapOf(
        // Price-scale averages and bands.
        "sma" to IndicatorPeriod(20),
        "ema" to IndicatorPeriod(20),
        "wma" to IndicatorPeriod(20),
        "hma" to IndicatorPeriod(20),
        "smma" to IndicatorPeriod(14),
        "zlema" to IndicatorPeriod(21),
        "kama" to IndicatorPeriod(10),
        "t3" to IndicatorPeriod(10),
        "mcginley" to IndicatorPeriod(14),
        // A hundred and twenty-five, the web terminal's, and the two are different tools to a
        // reader rather than one tool at two settings — so each keeps its own default.
        "linreg" to IndicatorPeriod(LINREG_PERIOD),
        "lsma" to IndicatorPeriod(LSMA_PERIOD),
        "bollinger" to IndicatorPeriod(20),
        "donchian" to IndicatorPeriod(20),
        "envelopes" to IndicatorPeriod(20),
        // Own-pane oscillators.
        "rsi" to IndicatorPeriod(14),
        "cci" to IndicatorPeriod(20),
        "williams" to IndicatorPeriod(14),
        "atr" to IndicatorPeriod(14),
        "adx" to IndicatorPeriod(14),
        "choppiness" to IndicatorPeriod(14),
        // ── The third pack ──────────────────────────────────────────────────────────────
        // On the price.
        "vwma" to IndicatorPeriod(20),
        "tema" to IndicatorPeriod(20),
        "dema" to IndicatorPeriod(20),
        "chandekroll" to IndicatorPeriod(10),
        "volstop" to IndicatorPeriod(20),
        // On their own scale. The lookback is the one that moves the reading; where a second
        // number exists — Woodie's turbo six, the Mass Index's nine-bar smoothing, the Stochastic
        // RSI's 3/3 — it is the method rather than the setting, and the label prints it so a
        // reader can see it is fixed rather than merely absent.
        "stochrsi" to IndicatorPeriod(14),
        "tsi" to IndicatorPeriod(25),
        "aroon" to IndicatorPeriod(14),
        "dmi" to IndicatorPeriod(14),
        "dpo" to IndicatorPeriod(20),
        "cmo" to IndicatorPeriod(9),
        "massindex" to IndicatorPeriod(25),
        "rvi" to IndicatorPeriod(10),
        "woodiescci" to IndicatorPeriod(14),
        "mfi" to IndicatorPeriod(14),
        "cmf" to IndicatorPeriod(20),
        // Two bars is a correlation of exactly ±1 on any pair, which is arithmetic rather than a
        // reading, so this one starts at five.
        "correlation" to IndicatorPeriod(20, min = 5),
    )

    /*
     * Deliberately absent from the table above, and each for a stated reason rather than an
     * oversight:
     *
     * `ppo`, `pvo` and `coppock` are three-parameter indicators in the same family as MACD, which
     * is absent for exactly this reason — a stepper labelled "period" on a fast/slow/signal triple
     * moves one third of the tool and the picture barely changes.
     *
     * `sar` has no lookback at all: its two settings are the acceleration step and its ceiling,
     * both fractions, and an integer stepper cannot express either.
     *
     * `alligator`, `ao`, `kst` and `netvolume` are defined by fixed period sets — 13/8/5, 5/34,
     * 10/15/20/30 — that are the indicator rather than a setting on it.
     *
     * `volumeprofile_ind` is parameterised by row count and window, not by a lookback, and
     * `chopzone` by a thirty-bar normalisation window that changes the colours without changing
     * what they mean. Both would be steppers a reader could move without learning anything.
     */

    /** The lookback [id] can be given, or null where it has no single one. */
    fun periodOf(id: String): IndicatorPeriod? = PERIODS[id]

    /**
     * The period an indicator should actually be computed with.
     *
     * [chosen] is the reader's, when they have moved the stepper. Null falls back to the default,
     * and anything out of range is clamped rather than refused — a stored value from an older
     * build with wider bounds must not produce an empty chart.
     */
    private fun periodFor(id: String, chosen: Int?): Int {
        val bounds = PERIODS[id] ?: return chosen ?: 0
        return (chosen ?: bounds.default).coerceIn(bounds.min, bounds.max)
    }

    /**
     * The indicators whose Persian name or ticker contains [query].
     *
     * Deliberately a plain substring rather than the ranked matcher `core:symbols` uses. That one
     * scores subsequences, so «ما» would drag in half the list through letters it merely passes
     * through; here the reader is filtering a list they can already see, and a filter that returns
     * things they did not ask for reads as broken. Both the Persian label and the Latin id match, so
     * «rsi» and «قدرت» find the same row.
     */
    fun matchingIndicators(query: String): List<IndicatorOption> {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return INDICATORS
        return INDICATORS.filter {
            needle in it.label.lowercase(Locale.ROOT) || needle in it.id.lowercase(Locale.ROOT)
        }
    }

    /**
     * Compute one indicator's price-scale lines for a series.
     *
     * Only the [IndicatorPane.PRICE] ones, because those are the ones the chart draws over the
     * candles. A separate-pane indicator needs its own axis and is a different drawing problem.
     */
    fun overlayFor(
        option: IndicatorOption,
        series: CandleSeries,
        /** The reader's lookback, or null for this indicator's own default. See [PERIODS]. */
        period: Int? = null,
        /**
         * The bars a window-scoped study measures over.
         *
         * Read by `volumeprofile_ind` and by nothing else, because it is the only entry here whose
         * answer depends on what is on screen rather than on the series. A caller that draws a
         * chart passes its viewport's range; one that has no viewport — the alert engine scanning a
         * series in the background — leaves the default, and gets a profile over everything, which
         * is the right answer for a question asked with no screen attached.
         */
        window: BarWindow = BarWindow.WHOLE_SERIES,
    ): List<ChartLine> {
        if (option.pane != IndicatorPane.PRICE || series.isEmpty) return emptyList()
        // Same rule as [paneFor]: no volume column, no volume-weighted line. See
        // [VOLUME_ONLY_INDICATORS] for why zero is not an acceptable substitute.
        if (option.id in VOLUME_ONLY_INDICATORS && !series.hasVolume) return emptyList()
        val close = series.close
        val high = series.high
        val low = series.low
        // Resolved once, and every label below is built from it rather than written as a literal.
        // The label and the maths reading two different numbers is the one failure a period
        // control can have that a reader cannot see.
        val n = periodFor(option.id, period)
        return when (option.id) {
            "sma" -> listOf(ChartLine(Indicators.sma(close, n), option.colour, label = "SMA $n"))
            "ema" -> listOf(ChartLine(Indicators.ema(close, n), option.colour, label = "EMA $n"))
            "wma" -> listOf(ChartLine(Indicators.wma(close, n), option.colour, label = "WMA $n"))
            "hma" -> listOf(ChartLine(Indicators.hma(close, n), option.colour, label = "HMA $n"))
            "bollinger" -> Indicators.bollinger(close, n).let { band ->
                // The basis is drawn thinner than its edges: it is a reference, and at equal weight
                // it competes with the two lines a reader is actually watching for a touch.
                listOf(
                    ChartLine(band.upper, option.colour, label = "BB $n"),
                    ChartLine(band.basis, option.colour, widthDp = 0.9f),
                    ChartLine(band.lower, option.colour),
                )
            }
            "keltner" -> Indicators.keltner(high, low, close).let { band ->
                listOf(
                    ChartLine(band.upper, option.colour, label = "KC"),
                    ChartLine(band.basis, option.colour, widthDp = 0.9f),
                    ChartLine(band.lower, option.colour),
                )
            }
            "donchian" -> Indicators.donchian(high, low, n).let { band ->
                listOf(
                    ChartLine(band.upper, option.colour, label = "DC $n"),
                    ChartLine(band.basis, option.colour, widthDp = 0.9f),
                    ChartLine(band.lower, option.colour),
                )
            }
            "ichimoku" -> Indicators.ichimoku(high, low).let { cloud ->
                listOf(
                    ChartLine(cloud.tenkan, option.colour, label = "Ichimoku"),
                    ChartLine(cloud.kijun, 0xFF6E8BE0),
                    ChartLine(cloud.spanA, 0xFF00B15C, widthDp = 0.9f),
                    ChartLine(cloud.spanB, 0xFFF6465D, widthDp = 0.9f),
                )
            }
            "supertrend" -> Indicators.supertrend(high, low, close).let { result ->
                // Broken at each flip rather than drawn as one continuous line.
                //
                // A SuperTrend jumps from below the price to above it when the trend turns, and a
                // line that carries the pen across that jump draws a vertical stroke through the
                // candles that no terminal draws and that reads as a real move. The values are
                // untouched — they are parity-checked against the web app — and only the gap is
                // added, which is exactly what a null in a `Line` means.
                val trend = result.trend
                val split = Line.of(series.size) { index ->
                    val flipped = index > 0 &&
                        trend.isPresent(index) &&
                        trend.isPresent(index - 1) &&
                        trend.raw(index) != trend.raw(index - 1)
                    if (flipped) null else result.line[index]
                }
                listOf(ChartLine(split, option.colour, widthDp = 1.6f, label = "SuperTrend"))
            }
            "vwap" -> listOf(
                ChartLine(
                    Indicators.vwap(high, low, close, series.volume),
                    option.colour,
                    label = "VWAP",
                ),
            )

            // ── The second thirty's price-scale entries ────────────────────────────────────
            "smma" -> listOf(ChartLine(IndicatorsExt.smma(close, n), option.colour, label = "SMMA $n"))
            "zlema" -> listOf(ChartLine(IndicatorsExt.zlema(close, n), option.colour, label = "ZLEMA $n"))
            "kama" -> listOf(ChartLine(IndicatorsExt.kama(close, n), option.colour, label = "KAMA $n"))
            "t3" -> listOf(ChartLine(IndicatorsExt.t3(close, n), option.colour, label = "T3 $n"))
            "mcginley" -> listOf(ChartLine(IndicatorsExt.mcginley(close, n), option.colour, label = "McGinley $n"))
            "linreg" -> listOf(
                ChartLine(IndicatorsExt.linearRegression(close, n), option.colour, label = "LinReg $n"),
            )
            "lsma" -> listOf(
                ChartLine(IndicatorsExt.linearRegression(close, n), option.colour, label = "LSMA $n"),
            )
            "envelopes" -> IndicatorsExt.envelopes(close, n).let { band ->
                listOf(
                    ChartLine(band.upper, option.colour, label = "Env $n"),
                    ChartLine(band.basis, option.colour, widthDp = 0.9f),
                    ChartLine(band.lower, option.colour),
                )
            }

            // ── The third pack's price-scale entries ──────────────────────────────────────
            "sar" -> IndicatorsExtB.parabolicSar(high, low).let { sar ->
                // Broken where the stop changes sides, for the reason SuperTrend is: the SAR jumps
                // from under the price to over it in one bar, and a pen carried across that jump
                // draws a vertical stroke through the candles that never happened. The values are
                // untouched; only the join is removed.
                val split = Line.of(series.size) { index ->
                    val value = sar[index]
                    if (!value.isFinite()) return@of null
                    val previous = if (index > 0) sar[index - 1] else Double.NaN
                    val flipped = previous.isFinite() &&
                        (value > close[index]) != (previous > close[index - 1])
                    if (flipped) null else value
                }
                listOf(ChartLine(split, option.colour, widthDp = 1.4f, label = "SAR 0.02/0.2"))
            }
            "alligator" -> IndicatorsExtB.alligator(high, low).let { gator ->
                // The three arrays already carry their 8/5/3-bar forward displacement, so nothing
                // here shifts them again. Shifting twice is invisible on screen — the lines still
                // fan and still cross — and it is the failure this comment exists to prevent.
                listOf(
                    ChartLine(gator.jaw.asLine(), 0xFF6E8BE0, label = "Alligator"),
                    ChartLine(gator.teeth.asLine(), 0xFFF6465D),
                    ChartLine(gator.lips.asLine(), 0xFF00B15C),
                )
            }
            "vwma" -> listOf(
                ChartLine(
                    IndicatorsExtB.vwma(close, series.volume, n).asLine(),
                    option.colour,
                    label = "VWMA $n",
                ),
            )
            "tema" -> listOf(
                ChartLine(IndicatorsExtB.tema(close, n).asLine(), option.colour, label = "TEMA $n"),
            )
            "dema" -> listOf(
                ChartLine(IndicatorsExtB.dema(close, n).asLine(), option.colour, label = "DEMA $n"),
            )
            "chandekroll" -> IndicatorsExtC.chandeKrollStop(high, low, close, n).let { stop ->
                // Both stops are drawn, in the colours of the side each protects, because which one
                // matters depends on a position this module knows nothing about.
                listOf(
                    ChartLine(stop.longStop.asLine(), 0xFF00B15C, label = "Chande Kroll $n"),
                    ChartLine(stop.shortStop.asLine(), 0xFFF6465D),
                )
            }
            "volstop" -> IndicatorsExtC.volatilityStop(high, low, close, n).let { result ->
                // Broken at each flip, exactly as the SAR is, and for the same reason. The side is
                // the thing that changes: `isLong` turning over is the stop crossing the candles.
                val split = Line.of(series.size) { index ->
                    val value = result.stop[index]
                    if (!value.isFinite()) return@of null
                    val flipped = index > 0 &&
                        result.stop[index - 1].isFinite() &&
                        result.isLong[index] != result.isLong[index - 1]
                    if (flipped) null else value
                }
                listOf(ChartLine(split, option.colour, widthDp = 1.4f, label = "Volatility Stop $n"))
            }
            "volumeprofile_ind" -> volumeProfileFor(series, window).let { profile ->
                // Three prices *and* the histogram they were read off — item 54.
                //
                // The three lines are the reading people want: the point of control and the two
                // edges of the value area, each drawn as a level that runs the width of the chart.
                // But for a long time they were all this study drew, because a `ChartLine` is one
                // value per bar and a profile is one bar per *price row* — the wrong shape — so the
                // rows this function had already bucketed were computed and dropped on the floor.
                // An indicator called «پروفایل حجم» that draws no profile is the defect.
                //
                // The rows now ride along on the point-of-control line through [ChartLine.profile],
                // and the canvas draws them as the histogram. The line is the natural carrier: it
                // is the profile's headline, so switching that legend row off takes the bars with
                // it rather than leaving a histogram with nothing naming it.
                if (profile == null) {
                    emptyList()
                } else {
                    val control = (profile.rowLow[profile.pocIndex] + profile.rowHigh[profile.pocIndex]) / 2
                    // The label names the window when it is the reader's screen, because a profile
                    // over the visible range and one over the whole series are different answers
                    // and a reader looking at one flat line has no other way to tell which they
                    // have. Left as a bare "POC" for the whole series, which is what the study was
                    // before it learned about the viewport.
                    val label = if (window == BarWindow.WHOLE_SERIES) "POC" else "POC · محدودهٔ دید"
                    listOf(
                        ChartLine(
                            flat(series.size, control),
                            option.colour,
                            widthDp = 1.4f,
                            label = label,
                            profile = profile,
                        ),
                        ChartLine(flat(series.size, profile.rowHigh[profile.valueAreaHigh]), option.colour, widthDp = 0.9f, dashed = true),
                        ChartLine(flat(series.size, profile.rowLow[profile.valueAreaLow]), option.colour, widthDp = 0.9f, dashed = true),
                    )
                }
            }
            else -> emptyList()
        }
    }
}
